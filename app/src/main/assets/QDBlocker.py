"""QDBlocker phone filter. No network server, file log, credentials or raw-body storage.

Load using the official PCAPdroid-mitm custom-addon screen. Rules come from
the certificate-pinned Android provider; QDBlocker need not remain foreground.
"""
import json
import re
import time

HOST = "druidv6.if.qidian.com"
ROUTE = "/argus/api/v1/message/pullsocialunreadmessage"
PARAGRAPH_ROUTES = frozenset((
    "/argus/api/v2/chapterreview/getparagraphscomments",
    "/argus/api/v1/chapterreview/getchapterreviewdetail",
    "/argus/api/v1/chapterreview/getparagraphsallimgreviews",
    "/argus/api/v1/chapterreview/getparagraphsaudiocomments",
))


def paragraph_outline(payload, names, uids):
    """Bounded field/relationship observation; no text, URLs or unrelated IDs."""
    fields, rows, matches = [], [], []
    budget = [3000]
    def walk(value, path, depth):
        if depth > 9 or budget[0] <= 0:
            return
        budget[0] -= 1
        if isinstance(value, dict):
            uid = value.get("UserId")
            target = (not isinstance(uid, bool) and isinstance(uid, (str, int)) and str(uid) in uids) or (isinstance(value.get("UserName"), str) and value["UserName"] in names)
            if target and len(matches) < 30:
                matches.append(path)
            if "UserId" in value and ("Id" in value or "RootReviewId" in value) and len(rows) < 35:
                root = value.get("RootReviewId")
                rows.append({"path": path, "target": bool(target), "root_is_self": root == value.get("Id"),
                             "root_is_zero": root in (0, "0"),
                             "flags": {k: value[k] for k in ("Floor", "ShowType", "ReviewType", "IsReplyReview", "Category", "ReviewCount")
                                       if isinstance(value.get(k), int)},
                             "lists": {str(k)[:60]: len(v) for k, v in value.items() if isinstance(v, list)}})
            for k, child in list(value.items())[:120]:
                child_path = path + "." + str(k)[:60]
                if len(fields) < 160 and (depth <= 2 or "[0]" in child_path):
                    fields.append({"path": child_path, "type": type(child).__name__, **({"count": len(child)} if isinstance(child, list) else {})})
                walk(child, child_path, depth + 1)
        elif isinstance(value, list):
            for i, child in enumerate(value[:100]):
                walk(child, path + "[" + str(i) + "]", depth + 1)
    walk(payload, "$", 0)
    return {"fields": fields, "authors": rows, "target_paths": matches}


def parse_rules(text):
    names, uids = set(), set()
    for entry in text.splitlines()[:500]:
        entry = entry.strip()
        if entry.startswith("uid:") and re.fullmatch(r"[1-9][0-9]{0,19}", entry[4:]):
            uids.add(entry[4:])
        elif entry.startswith("name:") and 0 < len(entry[5:]) <= 80:
            names.add(entry[5:])
    return names, uids


def filter_rows(payload, names, uids):
    if not isinstance(payload, dict) or payload.get("Result") != 0:
        return 0
    data = payload.get("Data")
    if not isinstance(data, dict) or not isinstance(data.get("MsgList"), list):
        return 0

    def blocked(row):
        if not isinstance(row, dict) or not isinstance(row.get("UserInfo"), dict):
            return False
        author = row["UserInfo"]
        uid = author.get("FromId")
        by_id = not isinstance(uid, bool) and isinstance(uid, (str, int)) and str(uid) in uids
        # Each explicit name rule is independent; users are told about homonyms.
        return by_id or (isinstance(author.get("Name"), str) and author["Name"] in names)

    original = data["MsgList"]
    kept = [row for row in original if not blocked(row)]
    removed = len(original) - len(kept)
    if removed:
        data["MsgList"] = kept
    return removed


def numeric_id(value):
    if isinstance(value, bool) or not isinstance(value, (str, int)):
        return None
    text = str(value)
    return text if re.fullmatch(r"[1-9][0-9]{0,19}", text) else None


def paragraph_author_blocked(row, names, uids):
    return numeric_id(row.get("UserId")) in uids or (isinstance(row.get("UserName"), str) and row["UserName"] in names)


def filter_paragraph_rows(payload, names, uids, blocked_roots=None):
    """Observed flat DataList: root has RootReviewId == Id; replies point to it.

    A blocked reply never blacklists its parent. Removing a blocked root also
    removes its attached replies, even if the reply precedes the root in a page.
    """
    if not isinstance(payload, dict) or type(payload.get("Result")) is not int or payload["Result"] != 0:
        return 0
    data = payload.get("Data")
    if not isinstance(data, dict) or not isinstance(data.get("DataList"), list):
        return 0
    original = data["DataList"]
    roots = blocked_roots if blocked_roots is not None else set()
    for row in original:
        if not isinstance(row, dict):
            continue
        own_id = numeric_id(row.get("Id"))
        if (own_id is not None and numeric_id(row.get("RootReviewId")) == own_id
                and paragraph_author_blocked(row, names, uids)):
            roots.add(own_id)

    kept, removed_replies = [], {}
    for row in original:
        if not isinstance(row, dict) or numeric_id(row.get("Id")) is None:
            kept.append(row)
            continue
        own_id, root = numeric_id(row.get("Id")), numeric_id(row.get("RootReviewId"))
        if paragraph_author_blocked(row, names, uids) or (root is not None and root in roots):
            if root is not None and root != own_id:
                removed_replies.setdefault(root, set()).add(own_id)
        else:
            kept.append(row)

    removed = len(original) - len(kept)
    if not removed:
        return 0
    for row in kept:
        if not isinstance(row, dict):
            continue
        own_id = numeric_id(row.get("Id"))
        count = row.get("ReviewCount")
        if own_id is not None and numeric_id(row.get("RootReviewId")) == own_id and type(count) is int:
            row["ReviewCount"] = max(0, count - len(removed_replies.get(own_id, ())))
    data["DataList"] = kept
    # Only adjust whole-page counters when the original count exactly described
    # this returned list. Preserve server cursors and larger historical totals.
    for key in ("TotalCount", "TextCount"):
        if type(data.get(key)) is int and data[key] == len(original):
            data[key] = len(kept)
    return removed


class AndroidBridge:
    def __init__(self):
        from java import jclass
        self.Bundle = jclass("android.os.Bundle")
        self.uri = jclass("android.net.Uri").parse("content://com.haoyang.qdblocker.probe")
        context = jclass("com.chaquo.python.Python").getPlatform().getApplication()
        self.resolver = context.getContentResolver()

    def config(self):
        bundle = self.resolver.call(self.uri, "network_config", None, None)
        return bundle.getBoolean("enabled", False), str(bundle.getString("rules", "")), bundle.getBoolean("paragraphs", True)

    def event(self, kind, removed=0):
        bundle = self.Bundle()
        bundle.putString("kind", kind)
        bundle.putInt("removed", removed)
        self.resolver.call(self.uri, "network_event", None, bundle)


class PhoneFilter:
    def __init__(self, bridge=None):
        self.bridge = bridge
        self.next_config = 0
        self.next_alive = 0
        self.enabled = False
        self.names, self.uids = set(), set()
        self.paragraphs = True
        self.blocked_roots = set()
        self.roots_expire = 0
        self.last_error = 0
        self.next_diagnostic = {}
        self.observed_paragraph_routes = set()

    def diagnostic(self, kind):
        now = time.monotonic()
        self.refresh()
        if self.bridge is not None and now >= self.next_diagnostic.get(kind, 0):
            self.next_diagnostic[kind] = now + 30
            try:
                self.bridge.event(kind)
            except Exception:
                pass

    def client_connected(self, client):
        now = time.monotonic()
        if now >= self.next_diagnostic.get("client_seen", 0):
            self.next_diagnostic["client_seen"] = now + 30
            print("QDBlocker local proxy accepted a connection")

    def server_connect_error(self, data):
        print("QDBlocker upstream connection error")

    def tls_established_client(self, data):
        if (data.context.client.sni or data.context.server.sni or "").lower().rstrip(".") == HOST:
            self.diagnostic("tls_ready")

    def tls_failed_client(self, data):
        if (data.context.client.sni or data.context.server.sni or "").lower().rstrip(".") == HOST:
            self.diagnostic("tls_failed")

    def error(self, flow):
        if getattr(flow.request, "pretty_host", flow.request.host).lower().rstrip(".") == HOST:
            self.diagnostic("upstream_failed")

    def request(self, flow):
        if (getattr(flow.request, "pretty_host", flow.request.host).lower().rstrip(".") == HOST
                and flow.request.path.split("?", 1)[0].lower() == ROUTE):
            self.diagnostic("message_request")

    def refresh(self):
        now = time.monotonic()
        if now < self.next_config:
            return
        self.next_config = now + 2
        try:
            if self.bridge is None:
                self.bridge = AndroidBridge()
            self.enabled, rules, self.paragraphs = self.bridge.config()
            names, uids = parse_rules(rules)
            if (names, uids) != (self.names, self.uids) or now >= self.roots_expire:
                self.blocked_roots.clear()
                self.roots_expire = now + 900
            self.names, self.uids = names, uids
            if now >= self.next_alive:
                self.bridge.event("ready" if self.next_alive == 0 else "alive")
                self.next_alive = now + 60
        except Exception as error:
            # Fail open if rules cannot be read. Never print exception contents.
            self.enabled = False
            self.bridge = None
            if now - self.last_error > 30:
                print("QDBlocker configuration unavailable: " + type(error).__name__)
                self.last_error = now

    def running(self):
        self.refresh()

    def response(self, flow):
        request = flow.request
        host = getattr(request, "pretty_host", request.host).lower().rstrip(".")
        route = request.path.split("?", 1)[0].lower()
        paragraph = route in PARAGRAPH_ROUTES
        if host != HOST or (route != ROUTE and not paragraph) or flow.response.status_code != 200:
            return
        self.refresh()
        if not self.enabled:
            return
        try:
            body = flow.response.get_content(strict=False) or b""
            if len(body) > 2_000_000:
                return
            payload = json.loads(body)
            if paragraph:
                if not self.paragraphs:
                    return
                if len(self.blocked_roots) > 512:
                    self.blocked_roots.clear()
                removed = filter_paragraph_rows(payload, self.names, self.uids, self.blocked_roots)
            else:
                removed = filter_rows(payload, self.names, self.uids)
            if removed:
                flow.response.content = json.dumps(payload, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
                for header in ("etag", "content-md5", "digest"):
                    flow.response.headers.pop(header, None)
                try:
                    self.bridge.event("paragraph_filtered" if paragraph else "filtered", removed)
                except Exception:
                    pass
        except (ValueError, UnicodeError):
            pass


addons = [PhoneFilter()]
