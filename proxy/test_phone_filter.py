import copy
import importlib.util
import json
from pathlib import Path
from types import SimpleNamespace
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location("phone_filter", Path(__file__).resolve().parents[1] / "app/src/main/assets/QDBlocker.py")
phone = importlib.util.module_from_spec(spec)
spec.loader.exec_module(phone)


class Bridge:
    enabled = True
    paragraphs = True
    rules = "uid:123\nname:blocked"
    def __init__(self): self.events = []
    def config(self): return self.enabled, self.rules, self.paragraphs
    def event(self, kind, removed=0): self.events.append((kind, removed))


class PhoneFilterTests(unittest.TestCase):
    def paragraph_payload(self):
        return {"Result": 0, "Data": {"DataList": [
            {"Id": 11, "RootReviewId": 11, "UserId": 789, "UserName": "me", "ReviewType": 1, "ShowType": 51, "ReviewCount": 1, "Content": "MY_ROOT"},
            {"Id": 12, "RootReviewId": 11, "UserId": 123, "UserName": "blocked", "ReviewType": 2, "Content": "BLOCKED_REPLY", "ImageDetail": "private_image"},
            {"Id": 13, "RootReviewId": 13, "UserId": 456, "UserName": "other", "ReviewType": 1, "ReviewCount": 0, "Content": "OTHER_ROOT"}],
            "TotalCount": 3, "TextCount": 3, "NextCursor": "KEEP_CURSOR"}}

    def test_blocked_reply_preserves_own_root_and_other_root(self):
        payload = self.paragraph_payload()
        self.assertEqual(phone.filter_paragraph_rows(payload, set(), {"123"}), 1)
        data = payload["Data"]
        self.assertEqual([r["Id"] for r in data["DataList"]], [11, 13])
        self.assertEqual(data["DataList"][0]["Content"], "MY_ROOT")
        self.assertEqual(data["DataList"][0]["ReviewCount"], 0)
        self.assertEqual(data["NextCursor"], "KEEP_CURSOR")
        self.assertEqual(data["TotalCount"], 2)
        self.assertNotIn("private_image", json.dumps(payload))

    def test_blocked_root_removes_whole_thread_even_out_of_order(self):
        payload = self.paragraph_payload()
        payload["Data"]["DataList"][0]["UserId"] = 123
        payload["Data"]["DataList"][1]["UserId"] = 789
        payload["Data"]["DataList"].reverse()
        self.assertEqual(phone.filter_paragraph_rows(payload, set(), {"123"}), 2)
        self.assertEqual([r["Id"] for r in payload["Data"]["DataList"]], [13])

    def test_reference_to_blocked_user_does_not_remove_my_root(self):
        payload = self.paragraph_payload()
        root = payload["Data"]["DataList"][0]
        root.update(RelatedUserId=123, RelatedUser="blocked", RefferContent="blocked", Content="blocked")
        self.assertEqual(phone.filter_paragraph_rows(payload, {"blocked"}, {"123"}), 1)
        self.assertEqual(payload["Data"]["DataList"][0]["Id"], 11)

    def test_paragraph_pagination_larger_total_is_preserved(self):
        payload = self.paragraph_payload(); payload["Data"]["TotalCount"] = 100
        phone.filter_paragraph_rows(payload, set(), {"123"})
        self.assertEqual(payload["Data"]["TotalCount"], 100)

    def test_blocked_root_cache_expires_on_rule_change(self):
        bridge = Bridge(); addon = phone.PhoneFilter(bridge); addon.refresh()
        payload = self.paragraph_payload(); payload["Data"]["DataList"][0]["UserId"] = 123
        phone.filter_paragraph_rows(payload, addon.names, addon.uids, addon.blocked_roots)
        orphan = {"Result": 0, "Data": {"DataList": [{"Id": 25, "RootReviewId": 11, "UserId": 456}]}}
        self.assertEqual(phone.filter_paragraph_rows(copy.deepcopy(orphan), addon.names, addon.uids, addon.blocked_roots), 1)
        bridge.rules = "uid:999"; addon.next_config = 0; addon.refresh()
        self.assertEqual(phone.filter_paragraph_rows(orphan, addon.names, addon.uids, addon.blocked_roots), 0)

    def test_paragraph_response_integration(self):
        bridge = Bridge(); addon = phone.PhoneFilter(bridge)
        for route in phone.PARAGRAPH_ROUTES:
            flow = self.flow(path=route)
            body = json.dumps(self.paragraph_payload()).encode()
            flow.response.get_content = lambda strict: body
            addon.response(flow)
            self.assertEqual([r["Id"] for r in json.loads(flow.response.content)["Data"]["DataList"]], [11, 13])
        self.assertIn(("paragraph_filtered", 1), bridge.events)

    def test_paragraph_observation_omits_content_and_other_ids(self):
        payload = {"Data": {"DataList": [{"Id": 555111, "UserId": 222999, "UserName": "PRIVATE_NAME", "Content": "PRIVATE_BODY", "RootReviewId": 555111},
                                         {"Id": 555222, "UserId": 123, "UserName": "blocked", "RootReviewId": 555111}]}}
        before = copy.deepcopy(payload)
        outline = phone.paragraph_outline(payload, {"blocked"}, {"123"})
        self.assertEqual(outline["target_paths"], ["$.Data.DataList[1]"])
        text = json.dumps(outline)
        for secret in ("PRIVATE_NAME", "PRIVATE_BODY", "555111", "222999"):
            self.assertNotIn(secret, text)
        self.assertEqual(payload, before)

    def test_disabled_paragraphs_never_change_response(self):
        bridge = Bridge(); addon = phone.PhoneFilter(bridge)
        bridge.paragraphs = False
        flow = self.flow(path="/argus/api/v2/chapterreview/getparagraphscomments")
        payload = {"Result": 0, "Data": {"DataList": [{"Id": 11, "UserId": 789, "Content": "own comment"},
                                                        {"Id": 12, "UserId": 123, "Content": "blocked reply"}]}}
        body = json.dumps(payload).encode()
        flow.response.content = body
        flow.response.get_content = lambda strict: body
        before_headers = flow.response.headers.copy()
        with patch("builtins.print"):
            addon.response(flow)
        self.assertEqual(flow.response.content, body)
        self.assertEqual(flow.response.headers, before_headers)

    def payload(self):
        return {"Result": 0, "Data": {"MsgList": [
            {"UserInfo": {"FromId": 123, "Name": "renamed"}, "Content": "secret1"},
            {"UserInfo": {"FromId": 456, "Name": "blocked"}, "Content": "secret2"},
            {"UserInfo": {"FromId": 789, "Name": "other"}, "Content": "blocked"},
            {"Content": "blocked"}], "UnRead": 9}}

    def flow(self, host=phone.HOST, path=phone.ROUTE):
        body = json.dumps(self.payload()).encode()
        return SimpleNamespace(request=SimpleNamespace(host=host, pretty_host=host, path=path),
            response=SimpleNamespace(status_code=200, raw_content=body, content=body,
                headers={"etag": "old"}, get_content=lambda strict: body))

    def test_author_rules_do_not_match_body(self):
        payload = self.payload()
        before = copy.deepcopy(payload)
        self.assertEqual(phone.filter_rows(payload, {"blocked"}, {"123"}), 2)
        self.assertEqual(payload["Data"]["MsgList"], before["Data"]["MsgList"][2:])
        self.assertEqual(payload["Data"]["UnRead"], 9)

    def test_response_rewrite_and_minimal_telemetry(self):
        bridge = Bridge(); addon = phone.PhoneFilter(bridge); flow = self.flow()
        addon.response(flow)
        self.assertEqual(len(json.loads(flow.response.content)["Data"]["MsgList"]), 2)
        self.assertNotIn("etag", flow.response.headers)
        self.assertIn(("filtered", 2), bridge.events)
        self.assertNotIn("secret", repr(bridge.events))

    def test_disabled_and_other_endpoints_untouched(self):
        bridge = Bridge(); bridge.enabled = False
        for flow in [self.flow(), self.flow(path="/other"), self.flow(host="unrelated.example")]:
            original = flow.response.content
            phone.PhoneFilter(bridge).response(flow)
            self.assertEqual(flow.response.content, original)

    def test_live_rule_change(self):
        bridge = Bridge(); addon = phone.PhoneFilter(bridge); addon.refresh()
        bridge.rules = "uid:789"; addon.next_config = 0
        flow = self.flow(); addon.response(flow)
        rows = json.loads(flow.response.content)["Data"]["MsgList"]
        self.assertEqual(len(rows), 3)
        self.assertEqual(rows[0]["UserInfo"]["FromId"], 123)

    def test_failed_bridge_fails_open(self):
        class Broken(Bridge):
            def config(self): raise RuntimeError("PRIVATE_EXCEPTION")
        addon = phone.PhoneFilter(Broken()); flow = self.flow()
        original = flow.response.content; addon.response(flow)
        self.assertEqual(flow.response.content, original)

    def test_rules_validate_uid_and_bound_count(self):
        names, uids = phone.parse_rules("uid:123\nuid:-4\nuid:0\nname:blocked\nuid:bad")
        self.assertEqual(names, {"blocked"}); self.assertEqual(uids, {"123"})

    def test_error_envelopes_untouched(self):
        payload = self.payload(); payload["Result"] = -3
        before = copy.deepcopy(payload)
        self.assertEqual(phone.filter_rows(payload, {"blocked"}, {"123"}), 0)
        self.assertEqual(payload, before)


if __name__ == "__main__": unittest.main()
