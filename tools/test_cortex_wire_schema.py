"""Synthetic-only tests for Cortex protobuf wire redaction."""
import base64
import json
import unittest
from cortex_wire_schema import WireError, parse_wire, report


def make_har(body, *, host="cortex.maimemo.com", mime="application/x-protobuf",
             encoding="base64"):
    return {"log": {"entries": [{
        "request": {
            "url": f"https://{host}/cortex/api/v1/study/oplog?secret=HIDDEN",
            "headers": [{"name": "Authorization", "value": "HIDDEN"}],
            "postData": {"mimeType": mime, "encoding": encoding,
                         "text": base64.b64encode(body).decode("ascii")}},
        "response": {"status": 200, "content": {"text": "HIDDEN"}},
    }]}}


class TestWire(unittest.TestCase):
    def test_nested_structure(self):
        body = bytes([8, 3, 18, 4, 8, 1, 16, 2, 29, 2, 3, 4, 5])
        fields = parse_wire(body)
        self.assertEqual([1, 2, 3], [f["field_number"] for f in fields])
        self.assertEqual([1, 2], [f["field_number"] for f in fields[1]["possible_nested"]])

    def test_output_never_copies_secret_or_varint_value(self):
        secret = b"DO_NOT_PRINT_TOKEN"
        body = bytes([8, 127, 18, len(secret)]) + secret
        output = json.dumps(report(make_har(body)))
        self.assertIn("unverified-wire-structure", output)
        self.assertNotIn(secret.decode("ascii"), output)
        self.assertNotIn("HIDDEN", output)
        self.assertNotIn("127", output)

    def test_requires_explicit_binary_encoding(self):
        self.assertEqual("encoding-not-declared",
            report(make_har(bytes([8, 1]), encoding="raw"))["requests"][0]["state"])

    def test_invalid_payloads_rejected(self):
        for data in (bytes([8]), bytes([19]), bytes([18, 8, 1])):
            with self.assertRaises(WireError):
                parse_wire(data)

    def test_other_host_excluded(self):
        self.assertEqual(0, report(make_har(bytes([8, 1]), host="example.net"))["matched"])

    def test_grpc_framing(self):
        body = bytes([8, 3])
        framed = bytes([0, 0, 0, 0, len(body)]) + body
        self.assertEqual("unverified-wire-structure",
            report(make_har(framed, mime="application/grpc"))["requests"][0]["state"])
        self.assertEqual("grpc-frame-unsupported",
            report(make_har(bytes([0, 0, 0, 0, 9]) + body, mime="application/grpc"))["requests"][0]["state"])

    def test_malformed_har_rejected(self):
        with self.assertRaises(ValueError):
            report({"log": {"entries": "bad"}})


if __name__ == "__main__":
    unittest.main()
