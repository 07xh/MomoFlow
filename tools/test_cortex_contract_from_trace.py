import base64
import json
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from cortex_contract_from_trace import derive_contract, EvidenceError


def vi(n):
    b = bytearray()
    while n > 127:
        b.append((n & 127) | 128)
        n >>= 7
    b.append(n)
    return bytes(b)


def field(tag, val, wire=0):
    if wire == 2:
        return vi(tag * 8 + 2) + vi(len(val)) + val
    return vi(tag * 8) + vi(val)


def sample(mark, response, voc):
    encoded = base64.b64encode(field(13, field(7, voc) + field(4, response), 2)).decode()
    return {"mark": mark, "voc_id": str(voc), "oplog_b64": encoded,
            "http_body": {"oplog": encoded, "checkpoint": 42}}


def example():
    return {"source": "device_runtime_capture",
            "wasm_enum_numbers": {"FAMILIAR": 11, "UNCERTAIN": 9, "FORGET": 17},
            "samples": [sample("FAMILIAR", 11, 8001),
                        sample("UNCERTAIN", 9, 8002),
                        sample("FORGET", 17, 8003)]}


class CortexEvidenceTests(unittest.TestCase):
    def test_derives_nondefault_tags_and_redacts_ids(self):
        result = derive_contract(example())
        self.assertEqual(result["wasm_oplog"]["wrapper_field"], 13)
        self.assertEqual(result["wasm_oplog"]["voc_id_field"], 7)
        self.assertEqual(result["wasm_oplog"]["response_field"], 4)
        self.assertFalse(result["ready_for_production_write"])
        self.assertNotIn("8001", json.dumps(result))
        self.assertNotIn("oplog_b64", json.dumps(result))

    def test_selftest_cannot_be_used(self):
        s = example()
        s["source"] = "_selftest_dump.json"
        with self.assertRaises(EvidenceError):
            derive_contract(s)

    def test_requires_matching_http_body(self):
        s = example()
        s["samples"][1]["http_body"]["oplog"] = "AAAA"
        with self.assertRaises(EvidenceError):
            derive_contract(s)

    def test_requires_three_distinct_captures(self):
        s = example()
        s["samples"].pop()
        with self.assertRaises(EvidenceError):
            derive_contract(s)

    def test_rejects_wrong_response(self):
        s = example()
        s["wasm_enum_numbers"]["FORGET"] = 16
        with self.assertRaises(EvidenceError):
            derive_contract(s)

    def test_requires_checkpoint(self):
        s = example()
        del s["samples"][0]["http_body"]["checkpoint"]
        with self.assertRaises(EvidenceError):
            derive_contract(s)

    def test_rejects_truncated_protobuf(self):
        s = example()
        val = base64.b64encode(b"\\x82\\x80").decode()
        s["samples"][0]["oplog_b64"] = val
        s["samples"][0]["http_body"]["oplog"] = val
        with self.assertRaises(EvidenceError):
            derive_contract(s)

    def test_inconsistent_wire_fields_rejected(self):
        s = example()
        val = base64.b64encode(field(13, field(8, 8003) + field(4, 17), 2)).decode()
        s["samples"][2]["oplog_b64"] = val
        s["samples"][2]["http_body"]["oplog"] = val
        with self.assertRaises(EvidenceError):
            derive_contract(s)


if __name__ == "__main__":
    unittest.main()
