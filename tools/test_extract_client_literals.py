"""Native ciphertext fixtures and bounded, read-only literal exports."""

import contextlib
import io
import json
import os
import struct
import tempfile
import unittest
from pathlib import Path

import extract_client_literals as extraction


VECTORS = json.loads((Path(__file__).parent / "fixtures/client-literals-71.json").read_text("utf-8"))["vectors"]


def fixture(vector):
    data_base = 4 * (vector["index"] + 2) + 16
    data = bytearray(data_base + vector["end"])
    struct.pack_into("<II", data, vector["index"] * 4,
                     vector["encodedStart"], vector["encodedEnd"])
    data[data_base + vector["start"]:] = bytes.fromhex(vector["ciphertext"])
    return data, data_base


class LiteralExtractionTests(unittest.TestCase):
    def test_real_ciphertext_including_long_empty_and_replacement_character_literals(self):
        for vector in VECTORS:
            with self.subTest(index=vector["index"]):
                data, base = fixture(vector)
                result = extraction.LiteralTable(bytes(data), 0, base).decode(vector["index"])
                self.assertEqual(vector["value"], result["value"])
                self.assertEqual(vector["utf8Valid"], result["utf8Valid"])
                if not vector["utf8Valid"]:
                    self.assertEqual(vector["end"] - vector["start"], len(bytes.fromhex(result["rawHex"])))

    def test_invalid_utf8_is_explicit_and_preserves_the_decoded_bytes(self):
        data, base = fixture(VECTORS[0])
        data[base] ^= 0x80  # Native plaintext 0x69 becomes the incomplete UTF-8 byte 0xe9.
        row = extraction.LiteralTable(bytes(data), 0, base).decode(0)
        self.assertFalse(row["utf8Valid"])
        self.assertEqual("e9", row["rawHex"])
        self.assertEqual("\ufffd", row["value"])

    def test_negative_boolean_and_noninteger_indices_are_rejected(self):
        data, base = fixture(VECTORS[0])
        table = extraction.LiteralTable(bytes(data), 0, base)
        for index in (-1, True, 1.5, "0", 0x20000000):
            with self.subTest(index=index), self.assertRaises(ValueError):
                table.decode(index)

    def test_a_missing_second_offset_is_rejected(self):
        with self.assertRaises(ValueError):
            extraction.LiteralTable(b"\0" * 4, 0, 4)

    def test_a_truncated_ciphertext_is_not_returned_as_partial_text(self):
        data, base = fixture(VECTORS[2])
        with self.assertRaises(ValueError):
            extraction.LiteralTable(bytes(data[:-1]), 0, base).decode(VECTORS[2]["index"])

    def test_invalid_layout_offsets_are_rejected(self):
        for table, base in ((-1, 0), (0, -1), (40, 0), (0, 40)):
            with self.subTest(table=table, base=base), self.assertRaises(ValueError):
                extraction.LiteralTable(b"\0" * 16, table, base)

    def test_invalid_prefix_ranges_are_rejected(self):
        data, base = fixture(VECTORS[0])
        struct.pack_into("<II", data, 0, 0, 0)
        with self.assertRaises(ValueError):
            extraction.LiteralTable(bytes(data), 0, base).decode(0)

    def test_scanning_stops_at_the_first_invalid_record(self):
        data, base = fixture(VECTORS[0])
        rows, stop = extraction.LiteralTable(bytes(data), 0, base).scan()
        self.assertEqual([0], [row["index"] for row in rows])
        self.assertEqual(1, stop["index"])
        self.assertEqual("invalid-record", stop["kind"])

    def test_a_scan_limit_is_reported_as_a_limit(self):
        data, base = fixture(VECTORS[0])
        rows, stop = extraction.LiteralTable(bytes(data), 0, base).scan(limit=1)
        self.assertEqual(1, len(rows))
        self.assertEqual("record-limit", stop["kind"])
        self.assertEqual(1, stop["index"])

    def test_an_invalid_first_record_does_not_produce_an_empty_success(self):
        with self.assertRaises(ValueError):
            extraction.LiteralTable(b"\0" * 16, 0, 8).scan()

    def test_an_unsupported_client_does_not_write_an_export(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            binary, metadata, output = root / "game.exe", root / "global.dat", root / "out.json"
            binary.write_bytes(b"unsupported exe")
            metadata.write_bytes(b"unsupported metadata")
            with contextlib.redirect_stderr(io.StringIO()) as error, self.assertRaises(SystemExit):
                extraction.main(["--binary", str(binary), "--metadata", str(metadata),
                                 "--output", str(output), "--index", "0"])
            self.assertIn("Unsupported client file", error.getvalue())
            self.assertFalse(output.exists())
            self.assertEqual(b"unsupported exe", binary.read_bytes())

    def test_the_output_cannot_replace_an_input_or_its_hardlink(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            binary, metadata, alias = root / "game.exe", root / "global.dat", root / "alias.json"
            binary.write_bytes(b"original")
            metadata.write_bytes(b"metadata")
            os.link(binary, alias)
            for output in (binary, metadata, alias):
                with contextlib.redirect_stderr(io.StringIO()) as error, self.assertRaises(SystemExit):
                    extraction.main(["--binary", str(binary), "--metadata", str(metadata),
                                     "--output", str(output), "--all"])
                self.assertIn("Output must differ from input", error.getvalue())
            self.assertEqual(b"original", binary.read_bytes())
            self.assertEqual(b"metadata", metadata.read_bytes())


if __name__ == "__main__":
    unittest.main()
