"""Scoped Qidian observer and opt-in local message filter; no raw body storage."""
from __future__ import annotations

import json
import logging
from logging.handlers import RotatingFileHandler
from pathlib import Path
import re
import time
from urllib.parse import urlsplit, parse_qs

ROOT = Path(__file__).resolve().parent.parent
STATE = ROOT / "work" / "proxy"
DOMAINS = ("qidian.com", "qdmm.com", "yuewen.com")
NAME_KEYS = {"username", "nickname", "authorname", "sendername", "displayname"}
ID_KEYS = {"id", "uid", "userid", "authorid", "senderid", "fromid", "reviewid", "commentid", "replyid", "parentid", "rootid"}
CONTENT_ROUTE = re.compile(r"comment|review|paragraph|reply|message|mention|interaction|notice", re.I)


def allowed_host(host: str) -> bool:
    host = host.lower().rstrip(".")
    return any(host == domain or host.endswith("." + domain) for domain in DOMAINS)


def key_name(key: str) -> str:
    return key.lower().replace("_", "")


def flow_host(flow) -> str:
    # SOCKS requests can retain the numeric destination; prefer the HTTP authority.
    for host in (getattr(flow.request, "pretty_host", ""), flow.request.host,
                 getattr(getattr(flow, "client_conn", None), "sni", "")):
        if host and allowed_host(host):
            return host.lower().rstrip(".")
    return ""


def safe_path(url: str) -> str:
    # Query values, fragments and long IDs never reach the diagnostic log.
    path = urlsplit(url).path
    path = re.sub(r"\d{4,}", ":id", path)
    return re.sub(r"[^A-Za-z0-9_/:.\-]", "_", path)[:180]


def numeric_ids(record: dict) -> dict:
    result = {}
    for key, value in record.items():
        if key_name(str(key)) in ID_KEYS and not isinstance(value, bool):
            if isinstance(value, (str, int)) and re.fullmatch(r"[0-9]{1,20}", str(value)):
                result[str(key)[:64]] = str(value)
    return result


def filter_message_rows(payload, names: set[str], uids: set[str]) -> int:
    """Only the observed message envelope; keep cursors and other metadata intact."""
    if not isinstance(payload, dict) or not isinstance(payload.get("Data"), dict):
        return 0
    rows = payload["Data"].get("MsgList")
    if not isinstance(rows, list):
        return 0

    def blocked(row):
        if not isinstance(row, dict) or not isinstance(row.get("UserInfo"), dict):
            return False
        author = row["UserInfo"]
        uid = author.get("FromId")
        if not isinstance(uid, bool) and isinstance(uid, (str, int)) and re.fullmatch(r"[0-9]{1,20}", str(uid)):
            return str(uid) in uids
        return isinstance(author.get("Name"), str) and author["Name"] in names

    kept = [row for row in rows if not blocked(row)]
    removed = len(rows) - len(kept)
    if removed:
        payload["Data"]["MsgList"] = kept
    return removed


def paging_summary(payload, path: str) -> dict:
    query = parse_qs(urlsplit(path).query)
    request = {}
    for key, values in query.items():
        normalized = key_name(key)
        if normalized in {"pg", "pz", "pageindex", "pagesize", "page", "limit"} and re.fullmatch(r"[0-9]{1,6}", values[0]):
            request[normalized] = int(values[0])
        elif "cursor" in normalized or normalized in {"lastid", "startid"}:
            request[normalized + "_present"] = bool(values[0])
    data = payload.get("Data") if isinstance(payload, dict) else None
    fields = {}
    if isinstance(data, dict):
        for key, value in list(data.items())[:60]:
            item = {"type": type(value).__name__}
            if isinstance(value, list):
                item["count"] = len(value)
            elif key_name(key) in {"hasmore", "ismore", "pageindex", "pagesize", "totalcount", "total", "count"} and isinstance(value, (bool, int)):
                item["value"] = value
            fields[str(key)[:64]] = item
    return {"request_paging": request, "query_keys": [str(k)[:64] for k in list(query)[:40]], "data_fields": fields}


def request_shape(request) -> dict:
    body = request.get_content(strict=False) or b""
    shape = {"body_bytes": len(body)}
    if body and len(body) <= 64000:
        try:
            data = json.loads(body)
            if isinstance(data, dict):
                shape["body_keys"] = [str(k)[:64] for k in list(data)[:40]]
                shape["body_paging"] = {str(k)[:64]: v for k, v in data.items()
                                        if key_name(k) in {"pageindex", "pagesize", "page", "limit"}
                                        and isinstance(v, int) and not isinstance(v, bool)}
        except (ValueError, UnicodeError):
            shape["body_format"] = "opaque"
    shape["paging_header_names"] = [str(k)[:64] for k in request.headers.keys()
                                     if any(x in key_name(k) for x in ("page", "cursor", "param"))][:20]
    return shape


def summarize(payload, target_names: set[str]) -> dict:
    schema = []
    matches = []
    budget = [2500]

    def walk(value, path: str, depth: int, parent=None):
        if depth > 9 or budget[0] <= 0:
            return
        budget[0] -= 1
        if isinstance(value, dict):
            # Observed message rows store the sender specifically in UserInfo.Name.
            author_keys = NAME_KEYS | ({"name"} if path.lower().endswith(".userinfo") else set())
            matched_keys = [str(k)[:64] for k, v in value.items()
                            if key_name(str(k)) in author_keys and isinstance(v, str) and v in target_names]
            if matched_keys and len(matches) < 30:
                ids = numeric_ids(value)
                if isinstance(parent, dict):
                    ids.update({"parent." + k: v for k, v in numeric_ids(parent).items()})
                matches.append({"path": path, "name_fields": matched_keys, "candidate_ids": ids})
            for key, child in list(value.items())[:100]:
                child_path = path + "." + str(key)[:64]
                if len(schema) < 100:
                    schema.append({"path": child_path, "type": type(child).__name__,
                                   **({"count": len(child)} if isinstance(child, list) else {})})
                walk(child, child_path, depth + 1, value)
        elif isinstance(value, list):
            for index, child in enumerate(value[:150]):
                walk(child, f"{path}[{index}]", depth + 1, parent)

    walk(payload, "$", 0)
    return {"schema": schema, "target_matches": matches, "scan_truncated": budget[0] <= 0}


class QidianCapture:
    def __init__(self):
        self.logger = None
        self.count = 0
        self.paging_probe_sent = False

    def running(self):
        STATE.mkdir(parents=True, exist_ok=True)
        self.logger = logging.getLogger("qdblocker.capture.private")
        self.logger.propagate = False
        self.logger.setLevel(logging.INFO)
        if not self.logger.handlers:
            handler = RotatingFileHandler(STATE / "events.jsonl", maxBytes=2_000_000,
                                          backupCount=2, encoding="utf-8")
            handler.setFormatter(logging.Formatter("%(message)s"))
            self.logger.addHandler(handler)
        self.emit({"event": "proxy_ready", "mode": "config_per_response", "domains": DOMAINS})

    def emit(self, event):
        if self.logger:
            event["time"] = time.strftime("%Y-%m-%dT%H:%M:%S%z")
            self.logger.info(json.dumps(event, ensure_ascii=False, separators=(",", ":")))

    def response(self, flow):
        host = flow_host(flow)
        if not host:
            return
        self.count += 1
        endpoint = safe_path(flow.request.path)
        kind = flow.response.headers.get("content-type", "").split(";", 1)[0][:80]
        event = {"event": "response", "sequence": self.count, "host": host,
                 "endpoint": endpoint, "method": flow.request.method,
                 "status": flow.response.status_code, "content_type": kind,
                 "wire_bytes": len(flow.response.raw_content or b"")}
        # Parse bounded JSON in memory only. No request body/header or response body is saved.
        if CONTENT_ROUTE.search(endpoint) or "json" in kind:
            try:
                body = flow.response.get_content(strict=False) or b""
                if len(body) <= 2_000_000:
                    payload = json.loads(body)
                    if getattr(flow, "metadata", {}).get("qdblocker_paging_probe"):
                        data = payload.get("Data", {}) if isinstance(payload, dict) else {}
                        rows = data.get("MsgList") if isinstance(data, dict) else None
                        result = payload.get("Result") if isinstance(payload, dict) else None
                        self.emit({"event": "paging_probe", "status": flow.response.status_code,
                                   "result": result if isinstance(result, int) else None,
                                   "row_count": len(rows) if isinstance(rows, list) else None,
                                   "paging": paging_summary(payload, flow.request.path)["request_paging"]})
                        return
                    event["json"] = True
                    if CONTENT_ROUTE.search(endpoint):
                        config_path = ROOT / "proxy" / "capture-config.json"
                        config = json.loads(config_path.read_text(encoding="utf-8"))
                        names = set(config["target_names"])
                        event.update(summarize(payload, names))
                        if endpoint.lower() == "/argus/api/v1/message/pullsocialunreadmessage":
                            event.update(paging_summary(payload, flow.request.path))
                            event["request_shape"] = request_shape(flow.request)
                            if config.get("pagination_probe") and not self.paging_probe_sent:
                                self.paging_probe_sent = True
                                try:
                                    from mitmproxy import ctx
                                    page = int(flow.request.query["pg"])
                                    probe = flow.copy()
                                    probe.response = None
                                    probe.request.query["pg"] = str(page + 1)
                                    probe.metadata["qdblocker_paging_probe"] = True
                                    ctx.master.commands.call("replay.client", [probe])
                                except (KeyError, ValueError, RuntimeError) as error:
                                    self.emit({"event": "paging_probe_unavailable", "category": type(error).__name__})
                        if (config.get("mode") == "filter_messages" and flow.response.status_code == 200
                                and host == "druidv6.if.qidian.com"
                                and endpoint.lower() == "/argus/api/v1/message/pullsocialunreadmessage"):
                            removed = filter_message_rows(payload, names, {str(x) for x in config.get("target_uids", [])})
                            if removed:
                                flow.response.content = json.dumps(payload, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
                                for header in ("etag", "content-md5", "digest"):
                                    flow.response.headers.pop(header, None)
                                event["filtered_rows"] = removed
                                event["filter_scope"] = "local_response_only"
                else:
                    event["json"] = "body_too_large"
            except (ValueError, UnicodeError, OSError, KeyError):
                event["json"] = False
        self.emit(event)

    def http_connect(self, flow):
        if allowed_host(flow.request.host):
            self.emit({"event": "https_connect", "host": flow.request.host})

    def tls_established_client(self, data):
        host = data.context.client.sni or data.context.server.sni or ""
        if allowed_host(host):
            self.emit({"event": "client_tls_ready", "host": host})

    def error(self, flow):
        host = flow_host(flow)
        if not host:
            return
        message = str(flow.error).lower()
        category = "tls" if any(x in message for x in ("tls", "ssl", "certificate")) else "connection"
        self.emit({"event": "request_error", "host": host,
                   "endpoint": safe_path(flow.request.path), "category": category})

    def request(self, flow):
        host = flow_host(flow)
        if host:
            self.emit({"event": "request", "host": host, "endpoint": safe_path(flow.request.path),
                       "method": flow.request.method})

    def tls_failed_client(self, data):
        host = data.context.client.sni or data.context.server.sni or ""
        if allowed_host(host):
            self.emit({"event": "client_tls_failed", "host": host})


addons = [QidianCapture()]
