#!/usr/bin/env python3
"""Generate a compact offline ECDICT SQLite asset for MomoFlow.

Source: skywind3000/ECDICT, MIT. Content pinned to an upstream commit.
Run in GitHub Actions BEFORE Gradle so the APK contains real definitions.
"""
import argparse
import csv
import re
import sqlite3
import tempfile
import urllib.request
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[1]
UPSTREAM_COMMIT = "39cfb9672c0906b63569e0453c2e6abfb9ab182a"
SOURCE_URL = (
    "https://raw.githubusercontent.com/skywind3000/ECDICT/"
    + UPSTREAM_COMMIT + "/ecdict.mini.csv"
)
DEFAULT_OUTPUT = REPO_ROOT / "app/src/main/assets/ecdict.db"
IMPORTANT_TAGS = {"gk", "zk", "cet4", "cet6", "ky", "toefl", "ielts", "gre", "oxford"}
WORD_PATTERN = re.compile(r"[A-Za-z][A-Za-z' .-]{0,63}")


def freq_rank(value):
    try:
        rank = int(value or "0")
        return rank if rank > 0 else None
    except ValueError:
        return None


def keep(row):
    spelling = row.get("word", "").strip()
    meaning = row.get("translation", "").strip()
    if not spelling or not meaning or not WORD_PATTERN.fullmatch(spelling):
        return False
    tags = set((row.get("tag") or "").lower().split())
    bnc = freq_rank(row.get("bnc", ""))
    frq = freq_rank(row.get("frq", ""))
    return bool(tags & IMPORTANT_TAGS) or (bnc is not None and bnc <= 80000) or (
        frq is not None and frq <= 80000
    )


def generate(input_file, output_file):
    output_file.parent.mkdir(parents=True, exist_ok=True)
    if output_file.exists():
        output_file.unlink()
    with sqlite3.connect(output_file) as db:
        db.execute("CREATE TABLE lexicon (word TEXT PRIMARY KEY COLLATE NOCASE, translation TEXT NOT NULL, phonetic TEXT)")
        db.execute("PRAGMA journal_mode=OFF")
        db.execute("PRAGMA synchronous=OFF")
        batch = []
        count = 0
        with open(input_file, "r", encoding="utf-8-sig", newline="") as handle:
            for row in csv.DictReader(handle):
                if not keep(row):
                    continue
                spelling = row["word"].strip().lower()
                meaning = re.sub(r"\s+", " ", row["translation"]).strip()[:360]
                phonetic = (row.get("phonetic") or "").strip()[:100]
                batch.append((spelling, meaning, phonetic))
                if len(batch) == 1000:
                    db.executemany("INSERT OR REPLACE INTO lexicon VALUES (?, ?, ?)", batch)
                    count += len(batch)
                    batch.clear()
            if batch:
                db.executemany("INSERT OR REPLACE INTO lexicon VALUES (?, ?, ?)", batch)
                count += len(batch)
        db.commit()
        real_count = db.execute("SELECT COUNT(*) FROM lexicon").fetchone()[0]
        important = db.execute("SELECT translation FROM lexicon WHERE word='important'").fetchone()
    print(f"ECDICT offline asset: {real_count} words ({count} rows), {output_file.stat().st_size} bytes")
    if real_count < 5000 or important is None:
        raise RuntimeError("Offline dictionary appears incomplete: important/core word missing")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", type=Path, help="Local ECDICT mini CSV (for testing)")
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    args = parser.parse_args()
    if args.input:
        generate(args.input, args.output)
        return
    with tempfile.TemporaryDirectory(prefix="momoflow-ecdict-") as directory:
        temp_file = Path(directory) / "ecdict.mini.csv"
        request = urllib.request.Request(SOURCE_URL, headers={"User-Agent": "MomoFlow-Android-Build"})
        with urllib.request.urlopen(request, timeout=120) as response, temp_file.open("wb") as target:
            while True:
                chunk = response.read(1024 * 1024)
                if not chunk:
                    break
                target.write(chunk)
        generate(temp_file, args.output)


if __name__ == "__main__":
    main()
