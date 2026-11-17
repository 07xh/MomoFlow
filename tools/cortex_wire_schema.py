#!/usr/bin/env python3
"""Offline protobuf *structure-only* inspection of personally captured Cortex HAR."""
import argparse
import base64
import binascii
import json
from pathlib import Path
from cortex_trace_audit import recognized

MAX_BYTES = 4 * 1024 * 1024
MAX_FIELDS = 2000
PROTOS = {"application/x-protobuf", "application/protobuf",
          "application/vnd.google.protobuf", "application/octet-stream",
          "application/grpc", "application/grpc+proto"}
WIRES = {0: "varint", 1: "fixed64", 2: "length-delimited", 5: "fixed32"}


class WireError(ValueError):
    pass


def read_varint(data, at):
    result = 0
    for i in range(10):
        if at >= len(data):
            raise WireError("truncated varint")
        b = data[at]
        at += 1
        if i == 9 and b > 1:
            raise WireError("overflow")
        result |= (b & 127) << (i * 7)
        if not b & 128:
            return result, at
    raise WireError("overflow")


def parse_wire(data, depth=0):
    """Only field numbers and wire types escape this function; no field values."""
    if not data or len(data) > MAX_BYTES:
        raise WireError("empty/oversize")
    pos, field_count, seen = 0, 0, {}
    while pos < len(data):
        field_count += 1
        if field_count > MAX_FIELDS:
            raise WireError("too many fields")
        key, pos = read_varint(data, pos)
        num, wire = key >> 3, key & 7
        if not 1 <= num <= 536870911 or wire not in WIRES:
            raise WireError("bad field")
        info = seen.setdefault((num, wire),
                               {"field_number": num, "wire_type": WIRES[wire], "count": 0})
        info["count"] += 1
        if wire == 0:
            _, pos = read_varint(data, pos)
        elif wire in (1, 5):
            pos += 8 if wire == 1 else 4
        else:
            length, pos = read_varint(data, pos)
            if length > len(data) - pos:
                raise WireError("bad length")
            chunk = data[pos:pos + length]
            pos += length
            if chunk and depth < 3:
                try:
                    children = parse_wire(chunk, depth + 1)
                except WireError:
                    children = []
                if children:
                    info["possible_nested"] = children
        if pos > len(data):
            raise WireError("truncated fixed width")
    return [seen[k] for k in sorted(seen)]


def extract_body(post, mime):
    if not isinstance(post, dict) or not isinstance(post.get("text"), str):
        return "body-missing", None
    if post.get("encoding", post.get("_encoding")) != "base64":
        return "encoding-not-declared", None
    text = post["text"]
    if len(text) > MAX_BYTES * 2:
        return "body-too-large", None
    try:
        data = base64.b64decode(text, validate=True)
    except (binascii.Error, ValueError):
        return "invalid-base64", None
    if not data or len(data) > MAX_BYTES:
        return "body-too-large-or-empty", None
    if mime in ("application/grpc", "application/grpc+proto"):
        if len(data) < 5 or data[0] != 0 or int.from_bytes(data[1:5], "big") != len(data) - 5:
            return "grpc-frame-unsupported", None
        data = data[5:]
    return "available", data


def report(har):
    entries = har.get("log", {}).get("entries") if isinstance(har, dict) else None
    if not isinstance(entries, list):
        raise ValueError("HAR missing log.entries array")
    matched = []
    for i, entry in enumerate(entries):
        if not isinstance(entry, dict) or not isinstance(entry.get("request"), dict):
            continue
        req = entry["request"]
        path = recognized(req.get("url"))
        if path is None:
            continue
        post = req.get("postData")
        mime = post.get("mimeType", "") if isinstance(post, dict) else ""
        mime = mime.split(";", 1)[0].strip().lower() if isinstance(mime, str) else ""
        item = {"har_index": i, "path": path}
        if mime not in PROTOS:
            item["state"] = "not-protobuf-media-type"
        else:
            state, raw = extract_body(post, mime)
            if raw is None:
                item["state"] = state
            else:
                try:
                    item["fields"] = parse_wire(raw)
                    item["state"] = "unverified-wire-structure"
                except WireError:
                    item["state"] = "invalid-wire-format"
        matched.append(item)
    return {
        "notice": "Structure ONLY, possible nesting is speculative. No authentication, enum numbers, field meanings or live requests verified.",
        "matched": len(matched), "requests": matched,
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("har", type=Path)
    parser.add_argument("--out", type=Path)
    args = parser.parse_args()
    if args.har.stat().st_size > 64 * 1024 * 1024:
        parser.error("HAR exceeds 64 MiB")
    result = json.dumps(report(json.loads(args.har.read_text(encoding="utf-8-sig"))),
                        ensure_ascii=False, indent=2) + "\n"
    if args.out:
        args.out.write_text(result, encoding="utf-8")
        print("Sanitized structural summary saved")
    else:
        print(result, end="")


if __name__ == "__main__":
    main()
