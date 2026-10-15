import copy
import json
import unittest
from types import SimpleNamespace
from qd_capture import allowed_host, safe_path, summarize, QidianCapture, flow_host, filter_message_rows


class CaptureTest(unittest.TestCase):
    def test_filter_only_author_and_preserve_cursor(self):
        rows = [{"UserInfo": {"FromId": 123, "Name": "renamed"}},
                {"UserInfo": {"FromId": 456, "Name": "测试用户"}},
                {"Content": "测试用户", "UserInfo": {"FromId": 789}},
                {"UserInfo": {"Name": "测试用户"}}, {"Name": "测试用户"}]
        payload = {"Data": {"MsgList": rows, "Cursor": "original", "Count": 55}}
        self.assertEqual(filter_message_rows(payload, {"测试用户"}, {"123"}), 2)
        self.assertEqual(payload["Data"]["MsgList"], [rows[1], rows[2], rows[4]])
        self.assertEqual(payload["Data"]["Cursor"], "original")
        self.assertEqual(payload["Data"]["Count"], 55)
        self.assertEqual(filter_message_rows({"Data": {"DataList": rows}}, {"测试用户"}, {"123"}), 0)

    def test_observed_message_sender_name(self):
        payload = {"Data": {"MsgList": [{"FromId": 123, "UserInfo": {"Name": "测试用户", "FromId": 123}},
                                      {"Name": "测试用户", "Content": "测试用户"}]}}
        result = summarize(payload, {"测试用户"})
        self.assertEqual(len(result["target_matches"]), 1)
        self.assertEqual(result["target_matches"][0]["path"], "$.Data.MsgList[0].UserInfo")
        self.assertEqual(result["target_matches"][0]["candidate_ids"]["FromId"], "123")

    def test_socks_numeric_destination_uses_authority(self):
        flow = SimpleNamespace(request=SimpleNamespace(host="192.0.2.1", pretty_host="magev6.if.qidian.com"))
        self.assertEqual(flow_host(flow), "magev6.if.qidian.com")
        flow.request.pretty_host = "unrelated.example"
        self.assertEqual(flow_host(flow), "")

    def test_domain_boundary(self):
        self.assertTrue(allowed_host("magev6.if.qidian.com"))
        self.assertFalse(allowed_host("qidian.com.evil.example"))
        self.assertFalse(allowed_host("notqidian.com"))

    def test_privacy_and_exact_author_matching(self):
        data = {"Data": {"List": [
            {"UserName": "测试用户", "UserId": 12345, "Content": "PRIVATE_BODY", "Token": "SECRET"},
            {"UserName": "OTHER_PERSON", "Content": "测试用户"},
            {"UserName": "测试用户的朋友", "Content": "OTHER_BODY"}]}}
        snapshot = copy.deepcopy(data)
        result = summarize(data, {"测试用户"})
        self.assertEqual(len(result["target_matches"]), 1)
        self.assertEqual(result["target_matches"][0]["candidate_ids"], {"UserId": "12345"})
        output = json.dumps(result, ensure_ascii=False)
        for secret in ("PRIVATE_BODY", "SECRET", "OTHER_PERSON", "OTHER_BODY"):
            self.assertNotIn(secret, output)
        self.assertEqual(data, snapshot)

    def test_urls_omit_query_and_ids(self):
        self.assertEqual(safe_path("/review/123456?token=SECRET#fragment"), "/review/:id")

    def test_response_does_not_modify_payload(self):
        addon = QidianCapture()
        captured = []
        addon.emit = captured.append
        body = b'{"Data":{"List":[]}}'
        response = SimpleNamespace(headers={"content-type": "application/json"},
                                   status_code=200, raw_content=body,
                                   get_content=lambda strict: body)
        flow = SimpleNamespace(request=SimpleNamespace(host="mage.if.qidian.com",
                               path="/api/review/list?token=SECRET", method="GET"), response=response)
        addon.response(flow)
        self.assertEqual(response.raw_content, body)
        self.assertNotIn("SECRET", json.dumps(captured))
        self.assertTrue(captured[0]["json"])


if __name__ == "__main__":
    unittest.main()
