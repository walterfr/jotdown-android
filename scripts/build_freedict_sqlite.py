#!/usr/bin/env python3
"""
Build Jotdown offline translation dictionaries from FreeDict archives.

Expected inputs:
  - freedict-eng-por-0.3.stardict.tar.xz
  - freedict-por-eng-0.1.1.dictd.tar.bz2

Outputs:
  - dict_en.db
  - dict_pt.db
  - DICTIONARY_NOTICE.txt
"""

from __future__ import annotations

import argparse
import gzip
import html
import re
import shutil
import sqlite3
import struct
import tarfile
import tempfile
from pathlib import Path


DICTD_BASE64 = "./0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"


def decode_dictd_number(value: str) -> int:
    total = 0
    for char in value:
        total = total * 64 + DICTD_BASE64.index(char)
    return total


def clean_text(value: str) -> str:
    value = html.unescape(value)
    value = re.sub(r"<br\s*/?>", "\n", value, flags=re.I)
    value = re.sub(r"</p\s*>", "\n", value, flags=re.I)
    value = re.sub(r"<[^>]+>", "", value)
    value = re.sub(r"[ \t]+", " ", value)
    value = re.sub(r"\n{3,}", "\n\n", value)
    return value.strip()


def split_wordtype(word: str) -> tuple[str, str | None]:
    match = re.match(r"^(.*?)\s+\(([^)]+)\)\s*$", word.strip())
    if match:
        return match.group(1).strip(), match.group(2).strip()
    return word.strip(), None


def create_db(output: Path, rows: list[tuple[str, str | None, str | None, str | None]]) -> None:
    if output.exists():
        output.unlink()

    connection = sqlite3.connect(output)
    try:
        connection.execute(
            """
            CREATE TABLE entries (
                word TEXT NOT NULL,
                wordtype TEXT,
                definition TEXT,
                translation TEXT
            )
            """
        )
        connection.execute("CREATE INDEX idx_entries_word ON entries(word COLLATE NOCASE)")
        connection.executemany(
            "INSERT INTO entries(word, wordtype, definition, translation) VALUES (?, ?, ?, ?)",
            rows,
        )
        connection.commit()
    finally:
        connection.close()


def extract_archive(archive: Path, target: Path) -> None:
    with tarfile.open(archive) as tar:
        tar.extractall(target)


def find_one(root: Path, suffixes: tuple[str, ...]) -> Path:
    matches = [path for path in root.rglob("*") if path.name.endswith(suffixes)]
    if not matches:
        raise FileNotFoundError(f"No file ending with {suffixes} found in {root}")
    return matches[0]


def read_stardict_dict(dict_path: Path) -> bytes:
    if dict_path.name.endswith(".dz"):
        with gzip.open(dict_path, "rb") as handle:
            return handle.read()
    return dict_path.read_bytes()


def convert_stardict(archive: Path, output: Path) -> int:
    with tempfile.TemporaryDirectory() as temp_name:
        temp = Path(temp_name)
        extract_archive(archive, temp)

        idx_path = find_one(temp, (".idx", ".idx.gz"))
        dict_path = find_one(temp, (".dict", ".dict.dz"))
        dict_data = read_stardict_dict(dict_path)
        
        if idx_path.name.endswith(".gz"):
            with gzip.open(idx_path, "rb") as handle:
                idx_data = handle.read()
        else:
            idx_data = idx_path.read_bytes()

        rows: list[tuple[str, str | None, str | None, str | None]] = []
        pos = 0
        while pos < len(idx_data):
            end = idx_data.index(b"\0", pos)
            raw_word = idx_data[pos:end].decode("utf-8", errors="replace")
            offset, size = struct.unpack(">II", idx_data[end + 1 : end + 9])
            pos = end + 9

            word, wordtype = split_wordtype(raw_word)
            translation = clean_text(dict_data[offset : offset + size].decode("utf-8", errors="replace"))
            if word and translation:
                rows.append((word, wordtype, None, translation))

        create_db(output, rows)
        return len(rows)


def read_dictd_dict(dict_path: Path) -> bytes:
    if dict_path.name.endswith(".dz"):
        with gzip.open(dict_path, "rb") as handle:
            return handle.read()
    return dict_path.read_bytes()


def convert_dictd(archive: Path, output: Path) -> int:
    with tempfile.TemporaryDirectory() as temp_name:
        temp = Path(temp_name)
        extract_archive(archive, temp)

        index_path = find_one(temp, (".index",))
        dict_path = find_one(temp, (".dict", ".dict.dz"))
        dict_data = read_dictd_dict(dict_path)

        rows: list[tuple[str, str | None, str | None, str | None]] = []
        for line in index_path.read_text("utf-8", errors="replace").splitlines():
            parts = line.split("\t")
            if len(parts) < 3:
                continue

            raw_word, encoded_offset, encoded_size = parts[:3]
            offset = decode_dictd_number(encoded_offset)
            size = decode_dictd_number(encoded_size)
            word, wordtype = split_wordtype(raw_word)
            translation = clean_text(dict_data[offset : offset + size].decode("utf-8", errors="replace"))
            if word and translation:
                rows.append((word, wordtype, None, translation))

        create_db(output, rows)
        return len(rows)


def write_notice(output_dir: Path) -> None:
    notice = """Jotdown Offline Dictionaries

Dictionary data source:
- FreeDict English-Portuguese 0.3
- FreeDict Portuguese-English 0.1.1

FreeDict project:
https://freedict.org/

Downloads:
https://download.freedict.org/dictionaries/eng-por/0.3/freedict-eng-por-0.3.stardict.tar.xz
https://download.freedict.org/dictionaries/por-eng/0.1.1/freedict-por-eng-0.1.1.dictd.tar.bz2

The FreeDict project provides free/open bilingual dictionaries. Keep this
notice with redistributed database files and comply with the license terms
shipped in the original FreeDict archives.
"""
    (output_dir / "DICTIONARY_NOTICE.txt").write_text(notice, encoding="utf-8")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--eng-por", required=True, type=Path)
    parser.add_argument("--por-eng", required=True, type=Path)
    parser.add_argument("--out", required=True, type=Path)
    args = parser.parse_args()

    args.out.mkdir(parents=True, exist_ok=True)
    en_count = convert_stardict(args.eng_por, args.out / "dict_en.db")
    pt_count = convert_dictd(args.por_eng, args.out / "dict_pt.db")
    write_notice(args.out)

    print(f"dict_en.db: {en_count} entries")
    print(f"dict_pt.db: {pt_count} entries")
    print(f"output: {args.out}")


if __name__ == "__main__":
    main()
