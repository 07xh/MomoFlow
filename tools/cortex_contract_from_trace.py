#!/usr/bin/env python3
"""Derive a redacted Cortex wire profile from three PAIRED device captures.

Input JSON is private and stays on your device:
{
  "source":"device_runtime_capture",
  "wasm_enum_numbers":{"FAMILIAR":1,"UNCERTAIN":2,"FORGET":4},
  "samples":[
    {"mark":"FAMILIAR","voc_id":"12345","oplog_b64":"...",
     "http_body":{"checkpoint":10,"oplog":"..."}}
  ]
}
Supply exactly one real pair for each of FAMILIAR, UNCERTAIN, FORGET.
Never commit the raw input; this CLI does not send any requests.
"""
import argparse
import base64
import binascii
import json
from pathlib import Path

MARKS = ("FAMILIAR", "UNCERTAIN", "FORGET")


class EvidenceError(ValueError):
    pass


def read_varint(buf, pos):
    value = 0
    for i in range(10):
        if pos >= len(buf):
            raise EvidenceError("truncated varint")
        n = buf[pos]
        pos += 1
        if i == 9 and n > 1:
            raise EvidenceError("varint overflow")
        value |= (n & 127) << (7 * i)
        if not n & 128:
            return value, pos
    raise EvidenceError("overlong varint")


def decode_fields(buf):
    if not buf or len(buf) > 128 * 1024:
        raise EvidenceError("missing or oversized protobuf")
    fields, pos = [], 0
    while pos < len(buf):
        tag, pos = read_varint(buf, pos)
        field, wire = tag >> 3, tag & 7
        if not (1 <= field <= 536870911) or wire not in (0, 2):
            raise EvidenceError("unsupported protobuf tag")
        if wire == 0:
            value, pos = read_varint(buf, pos)
        else:
            size, pos = read_varint(buf, pos)
            if size > len(buf) - pos:
                raise EvidenceError("truncated length-delimited protobuf")
            value = buf[pos:pos + size]
            pos += size
        fields.append((field, wire, value))
        if len(fields) > 32:
            raise EvidenceError("too many protobuf fields")
    return fields


def decode_operation(b64, expected_voc_id, expected_response):
    try:
        raw = base64.b64decode(b64, validate=True)
    except (binascii.Error, ValueError):
        raise EvidenceError("invalid base64") from None
    outer = decode_fields(raw)
    if len(outer) != 1 or outer[0][1] != 2:
        raise EvidenceError("expected one length-delimited oplog operation")
    inside = decode_fields(outer[0][2])
    if len(inside) != 2 or any(w != 0 for _, w, _ in inside):
        raise EvidenceError("expected two inner varint fields")
    choices = [(a, b) for a, _, val in inside for b, _, code in inside
               if a != b and val == expected_voc_id and code == expected_response]
    if len(choices) != 1:
        raise EvidenceError("unable to identify unique voc_id / response fields")
    return (outer[0][0], *choices[0])


def derive_contract(data):
    if data.get("source") != "device_runtime_capture" or data.get("synthetic", False):
        raise EvidenceError("requires declared non-synthetic runtime capture")
    enums = data.get("wasm_enum_numbers")
    if not isinstance(enums, dict) or set(enums) != set(MARKS):
        raise EvidenceError("all three wasm numeric response codes required")
    if any(type(enums[k]) is not int or not (0 < enums[k] <= 10000) for k in MARKS):
        raise EvidenceError("invalid enum value")
    if len(set(enums.values())) != 3:
        raise EvidenceError("enum numbers overlap")
    samples = data.get("samples")
    if not isinstance(samples, list) or len(samples) != 3:
        raise EvidenceError("exactly three paired captures required")
    marks, tags, shapes = set(), set(), set()
    for sample in samples:
        if not isinstance(sample, dict):
            raise EvidenceError("invalid operation sample")
        mark = sample.get("mark")
        if mark not in MARKS or mark in marks:
            raise EvidenceError("missing or duplicate mark")
        marks.add(mark)
        word = sample.get("voc_id")
        if not isinstance(word, str) or not word.isdecimal():
            raise EvidenceError("voc_id must be uint64 decimal string")
        vid = int(word)
        if not (0 < vid < 2**64):
            raise EvidenceError("voc_id out of range")
        b64 = sample.get("oplog_b64")
        body = sample.get("http_body")
        if not isinstance(b64, str) or not isinstance(body, dict):
            raise EvidenceError("real paired bytes and network body required")
        if body.get("oplog") != b64:
            raise EvidenceError("captured body oplog mismatch")
        if type(body.get("checkpoint")) is not int or body["checkpoint"] < 0:
            raise EvidenceError("checkpoint format not validated")
        shapes.add(tuple(sorted((k, type(v).__name__) for k, v in body.items())))
        tags.add(decode_operation(b64, vid, enums[mark]))
    if marks != set(MARKS) or len(tags) != 1 or len(shapes) != 1:
        raise EvidenceError("inconsistent runtime fields")
    wrap, voc, response = next(iter(tags))
    return {
        "kind": "cortex_wire_evidence_v1",
        "source": "device_runtime_capture",
        "sample_count": len(samples),
        "wasm_oplog": {
            "wrapper_field": wrap, "voc_id_field": voc,
            "response_field": response, "enum_numbers": {k: enums[k] for k in MARKS}
        },
        "http": {
            "method": "POST", "path": "/api/v1/study/oplog",
            "json_fields": dict(next(iter(shapes)))
        },
        "verified_scope": "structural-only; no server write/readback proven",
        "ready_for_production_write": False
    }


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("trace", type=Path)
    p.add_argument("--out", type=Path, default=Path("cortex_wire_profile.redacted.json"))
    args = p.parse_args()
    try:
        data = json.loads(args.trace.read_text(encoding="utf-8"))
        profile = derive_contract(data)
    except (OSError, ValueError, json.JSONDecodeError) as e:
        print("NOT VERIFIED:", e)
        return 2
    args.out.write_text(json.dumps(profile, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print("STRUCTURE VERIFIED; not a live write:", args.out)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
