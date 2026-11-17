#!/usr/bin/env python3
"""Offline, secret-minimizing HAR summary for MomoFlow Cortex protocol research.

This utility never makes HTTP requests and never saves bodies, headers, or tokens.
Use only captures of your own authorized MaiMemo study activity.
"""
import argparse
import json
import re
from pathlib import Path
from urllib.parse import urlsplit

HOSTS = {"cortex.maimemo.com", "tc-apis.maimemo.com"}
ENDPOINT_NAMES = {
    "oplog", "merge/memo", "download", "checkpoints", "overview", "alg_config",
}
SYNC_NAMES = {"oplog", "checkpoints", "download", "durations"}
ALLOWED_KEYS = {
    "checkpoint", "oplog", "ops", "operation", "operations", "handle_study_response",
    "voc_id", "response", "responses", "result", "results", "payload", "data",
    "memo", "merge", "version", "study", "study_response", "study_time",
    "timestamp", "request", "records", "items", "id", "count", "type",
}
SENSITIVE_HEADERS = {"authorization", "cookie", "set-cookie", "x-sign", "headersign", "x-header-sign"}


def recognized(url):
    try:
        parsed = urlsplit(url)
    except (TypeError, ValueError):
        return None
    if parsed.scheme != "https" or (parsed.hostname or "").lower() not in HOSTS:
        return None
    path = parsed.path
    if path in {prefix + ending for prefix in ("/cortex/api/v1/study/", "/api/v1/study/") for ending in ENDPOINT_NAMES}:
        return path
    if path in {prefix + ending for prefix in ("/cortex/sync/", "/sync/") for ending in SYNC_NAMES}:
        return path
    return None


def safe_shape(value, depth=0):
    """Record only allowlisted structural keys and types, never data values."""
    if isinstance(value, dict):
        if depth >= 3:
            return {"type": "object", "field_count": len(value)}
        visible = {}
        for key, member in value.items():
            if isinstance(key, str) and key.lower() in ALLOWED_KEYS:
                visible[key.lower()] = safe_shape(member, depth + 1)
        return {"type": "object", "field_count": len(value), "known_fields": visible}
    if isinstance(value, list):
        return {"type": "array", "length": len(value),
                "item_shape": safe_shape(value[0], depth + 1) if value else None}
    if value is None:
        return "null"
    if isinstance(value, bool):
        return "boolean"
    if isinstance(value, (int, float)):
        return "number"
    if isinstance(value, str):
        return "string"
    return "unknown"


def audit(data):
    entries = data.get("log", {}).get("entries") if isinstance(data, dict) else None
    if not isinstance(entries, list):
        raise ValueError("HAR 文件缺少 log.entries 数组")
    matches = []
    for index, entry in enumerate(entries):
        if not isinstance(entry, dict):
            continue
        request = entry.get("request") or {}
        response = entry.get("response") or {}
        if not isinstance(request, dict) or not isinstance(response, dict):
            continue
        path = recognized(request.get("url"))
        if path is None:
            continue
        headers = request.get("headers") or []
        names = {h.get("name", "").strip().lower() for h in headers if isinstance(h, dict)}
        post_data = request.get("postData") or {}
        content_type = post_data.get("mimeType") or ""
        # MIME parameters might include confidential boundary strings; discard them.
        mime = content_type.split(";", 1)[0].strip().lower()
        if not re.fullmatch(r"[a-z0-9.+-]+/[a-z0-9.+-]+", mime):
            mime = "unknown"
        shape = None
        body = post_data.get("text")
        if mime == "application/json" and isinstance(body, str):
            try:
                shape = safe_shape(json.loads(body))
            except (ValueError, TypeError):
                shape = {"type": "invalid-json"}
        matches.append({
            "har_index": index,
            "method": str(request.get("method", "")).upper() if str(request.get("method", "")).upper() in {"GET", "POST", "PUT", "PATCH", "DELETE"} else "OTHER",
            "path": path,
            "status": response.get("status") if type(response.get("status")) is int else None,
            "content_type": mime,
            "has_authorization": "authorization" in names,
            "has_cookie": "cookie" in names,
            "has_signature_header": bool(names.intersection(SENSITIVE_HEADERS - {"authorization", "cookie", "set-cookie"})),
            "request_body_present": bool(body) or bool(post_data.get("params")),
            "json_shape": shape,
        })
    return {
        "note": "Offline redacted structural summary only; no requests were sent and no API behavior was verified.",
        "scanned_entries": len(entries),
        "relevant_entries": len(matches),
        "calls_in_capture_order": matches,
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("har", type=Path, help="Local HAR from your own account; keep the original private")
    parser.add_argument("--out", type=Path, help="Write redacted report; omit to print JSON")
    args = parser.parse_args()
    result = audit(json.loads(args.har.read_text(encoding="utf-8-sig")))
    rendered = json.dumps(result, ensure_ascii=False, indent=2) + "\n"
    if args.out:
        args.out.write_text(rendered, encoding="utf-8")
        print(f"Redacted structural report written to {args.out}")
    else:
        print(rendered, end="")


if __name__ == "__main__":
    main()
