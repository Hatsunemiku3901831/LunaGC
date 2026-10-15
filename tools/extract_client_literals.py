#!/usr/bin/env python3
"""Recover literal strings from the exact client supported by extract_client_type_names.

The profile follows native 0x140524547 (prefix offsets) and 0x140526bd0 (bytes).
An automatic scan reports its stopping condition; its length is not a registration count.
Client files are read-only. Generated text belongs in the ignored debug directory.
"""

import argparse
import json
from pathlib import Path

import extract_client_type_names as profile


MASK32 = (1 << 32) - 1
MAX_INDEX = 0x1FFFFFFF
MAX_LITERAL_BYTES = 1 << 20


def _offset(encoded: int, index: int) -> int:
    mixed = ((index * 0x9825 ^ 0x250621A3) * 0x76C8023D) & profile.MASK64
    mixed = (((mixed >> 12) * 0x5E626528) & profile.MASK64) >> 15
    key = ((mixed + 0x3483F478) & MASK32) ^ 0xA0D4507F
    return (encoded + key + 0x81555D04) & MASK32


def _decrypt(cipher: bytes, index: int) -> bytes:
    key = (index * 0x34044C3B94B40C) & profile.MASK64
    key = ((key ^ 0x2C165A1E0D09C29E) * 0x4D613E5575A12594) & profile.MASK64
    key = ((key ^ 0x1B34FA773FEE107E) * 0x29140A6156B9041E) & profile.MASK64
    plain = bytearray()
    for position in range(0, len(cipher), 8):
        block = cipher[position:position + 8]
        word = int.from_bytes(block, "little") ^ key
        plain.extend(word.to_bytes(8, "little")[:len(block)])
        key = (key + 0x70BA8080760AC800) & profile.MASK64
    return bytes(plain)


class LiteralTable:
    def __init__(self, metadata: bytes, table_offset: int, data_base: int):
        if not (0 <= table_offset <= len(metadata) - 8 and 0 <= data_base <= len(metadata)):
            raise ValueError("Literal layout exceeds metadata")
        self.metadata = metadata
        self.table_offset = table_offset
        self.data_base = data_base

    def decode(self, index: int) -> dict:
        if type(index) is not int or not 0 <= index <= MAX_INDEX:
            raise ValueError("Literal index must be a nonnegative 29-bit integer")
        position = self.table_offset + index * 4
        if position + 8 > len(self.metadata):
            raise ValueError(f"Literal {index}: prefix table exceeds metadata")
        start = _offset(profile.u32(self.metadata, position), index)
        end = _offset(profile.u32(self.metadata, position + 4), index + 1)
        if end < start or end - start > MAX_LITERAL_BYTES:
            raise ValueError(f"Literal {index}: invalid or oversized prefix range")
        if self.data_base + end > len(self.metadata):
            raise ValueError(f"Literal {index}: ciphertext exceeds metadata")
        plain = _decrypt(self.metadata[self.data_base + start:self.data_base + end], index)
        result = {"index": index, "utf8Valid": True}
        try:
            result["value"] = plain.decode("utf-8")
        except UnicodeDecodeError:
            result.update(value=plain.decode("utf-8", errors="replace"),
                          utf8Valid=False, rawHex=plain.hex())
        return result

    def scan(self, limit: int = 1_000_000) -> tuple[list, dict]:
        if type(limit) is not int or not 1 <= limit <= 1_000_000:
            raise ValueError("Scan limit must be between 1 and 1000000")
        records = []
        for index in range(limit):
            try:
                records.append(self.decode(index))
            except ValueError as error:
                if not records:
                    raise
                return records, {"kind": "invalid-record", "index": index, "reason": str(error)}
        return records, {"kind": "record-limit", "index": limit}


def _signed32(value: int) -> int:
    return value - (1 << 32) if value & (1 << 31) else value


def client_layout(binary: bytes) -> dict:
    header = profile.pe_file_offset(binary, profile.HEADER_VA, profile.BODY_OFFSET)
    table = _signed32((profile.u32(binary, header + 0x88) + 0xF3C0B2FE) & MASK32)
    data = _signed32(profile.u32(binary, header + 0x13C) ^ 0x28B1D785)
    return {"tableOffset": profile.BODY_OFFSET + table, "dataBase": profile.BODY_OFFSET + data}


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--binary", type=Path, required=True)
    parser.add_argument("--metadata", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--index", type=lambda value: int(value, 0), action="append")
    mode.add_argument("--all", action="store_true", help="scan contiguous structurally valid records")
    parser.add_argument("--contains", help="filter recovered text; case insensitive")
    parser.add_argument("--limit", type=int, default=1_000_000, help="automatic scan bound")
    args = parser.parse_args(argv)
    try:
        for source in (args.binary, args.metadata):
            if args.output.resolve() == source.resolve() or (
                    args.output.exists() and source.exists() and args.output.samefile(source)):
                raise ValueError("Output must differ from input files and their aliases")
        binary = profile.read_verified(args.binary, profile.BINARY_SHA256)
        metadata = profile.read_verified(args.metadata, profile.METADATA_SHA256)
        layout = client_layout(binary)
        table = LiteralTable(metadata, layout["tableOffset"], layout["dataBase"])
        if args.all:
            records, stop = table.scan(args.limit)
        else:
            records = [table.decode(index) for index in dict.fromkeys(args.index)]
            stop = {"kind": "selected-indices"}
        recovered = len(records)
        invalid_utf8 = sum(not row["utf8Valid"] for row in records)
        if args.contains is not None:
            records = [row for row in records if args.contains.casefold() in row["value"].casefold()]
        report = {"binarySha256": profile.BINARY_SHA256,
                  "metadataSha256": profile.METADATA_SHA256, **layout,
                  "recovered": recovered, "exported": len(records),
                  "invalidUtf8": invalid_utf8, "stop": stop,
                  "scope": "Fixed-client literal strings; scan length is not a native registration count."}
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps({"profile": report, "literals": records},
                                         ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        print(json.dumps(report))
    except (OSError, ValueError) as error:
        parser.error(str(error))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
