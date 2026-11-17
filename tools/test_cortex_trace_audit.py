"""Local tests for redaction, path scoping and offline structural audit."""
import json
import unittest
from cortex_trace_audit import audit, recognized


class CortexTraceAuditTest(unittest.TestCase):
    def test_only_target_https_paths_are_visible(self):
        self.assertEqual("/cortex/api/v1/study/oplog", recognized("https://cortex.maimemo.com/cortex/api/v1/study/oplog?token=never_print"))
        self.assertIsNone(recognized("http://cortex.maimemo.com/cortex/api/v1/study/oplog"))
        self.assertIsNone(recognized("https://evil.example.com/cortex/api/v1/study/oplog"))
        self.assertIsNone(recognized("https://cortex.maimemo.com/cortex/api/v1/study/oplog/secret-path"))

    def test_redacts_all_headers_and_sensitive_values(self):
        secret = "TEST_SECRET_PRIVATE_DO_NOT_PRINT"
        data = {"log": {"entries": [{
            "request": {"url": "https://cortex.maimemo.com/cortex/api/v1/study/oplog?user="+secret,
                        "method": "POST", "headers": [{"name": "Authorization", "value": secret}, {"name": "X-Sign", "value": secret}],
                        "postData": {"mimeType": "application/json; boundary=" + secret,
                                     "text": json.dumps({"checkpoint": secret, "voc_id": secret, "authorization": secret,
                                                         "handle_study_response": {"response": secret, "unknown": secret}})}},
            "response": {"status": 200, "content": {"text": secret}},
        }]}}
        output = json.dumps(audit(data), ensure_ascii=False)
        self.assertNotIn(secret, output)
        self.assertNotIn("\"authorization\":", output.lower())
        self.assertIn("has_authorization", output)
        self.assertIn("handle_study_response", output)
        self.assertIn("/cortex/api/v1/study/oplog", output)

    def test_har_order_and_binary_unknown_schema(self):
        data = {"log": {"entries": [
            {"request": {"url": "https://cortex.maimemo.com/cortex/api/v1/study/checkpoints", "method": "POST"}, "response": {"status": 200}},
            {"request": {"url": "https://cortex.maimemo.com/cortex/api/v1/study/oplog", "method": "POST", "postData": {"mimeType": "application/x-protobuf", "text": "not printable"}}, "response": {"status": 204}},
        ]}}
        report = audit(data)
        self.assertEqual(2, report["relevant_entries"])
        self.assertTrue(report["calls_in_capture_order"][1]["request_body_present"])
        self.assertIsNone(report["calls_in_capture_order"][1]["json_shape"])

    def test_invalid_har_fails(self):
        with self.assertRaises(ValueError):
            audit({"log": {}})


if __name__ == "__main__":
    unittest.main()
