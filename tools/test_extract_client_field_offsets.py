"""Verified layout tables, bounds and explicit unresolved offsets for client metadata."""

import contextlib
import copy
import io
import json
from pathlib import Path
import struct
import sys
import tempfile
import unittest
from unittest.mock import patch

import extract_client_type_names as extraction


class FieldOffsetMetadataTests(unittest.TestCase):
    def setUp(self):
        self.binary = bytearray(extraction.BODY_OFFSET)
        self.metadata = bytearray(0x900)
        self.records = [{"index": 0, "name": "Packet", "fieldCount": 2,
                         "fields": [{"name": "Second", "type": {"attributes": "0x1"}},
                                    {"name": "First", "type": {"attributes": "0x1"}}],
                         "methods": [{"name": "Writer", "address": "0x14001234"}]}]
        self.report = {"existing": "kept"}
        for at, key, target in ((0x198, 0x5303F399, 0x300), (0x1A4, 0x0D8C5D0A, 0x500),
                                (0x1E4, 0x67102427, 0x700)):
            struct.pack_into("<I", self.binary, at, (target - extraction.BODY_OFFSET) ^ key)
        struct.pack_into("<i", self.metadata, 0x300, 0)
        struct.pack_into("<III", self.metadata, 0x500, 0, 0x08000038, 0)
        struct.pack_into("<II", self.metadata, 0x700, 0x28, 0x18)

    def recover(self):
        with patch.object(extraction, "pe_file_offset", return_value=0):
            extraction.recover_field_offsets(bytes(self.binary), bytes(self.metadata), self.records, self.report)

    def test_fields_use_the_layout_table_instead_of_name_or_declared_order(self):
        self.recover()
        self.assertEqual(["0x28", "0x18"], [field["offset"] for field in self.records[0]["fields"]])
        self.assertEqual(["instance", "instance"], [field["offsetKind"] for field in self.records[0]["fields"]])
        self.assertEqual(1, self.report["fieldLayoutTypeCount"])
        self.assertEqual(2, self.report["instanceFieldOffsetCount"])

    def test_static_offsets_are_identified_as_static_storage(self):
        self.records[0]["fields"][0]["type"]["attributes"] = "0x31"
        struct.pack_into("<I", self.metadata, 0x700, 0)
        self.recover()
        self.assertEqual("static", self.records[0]["fields"][0]["offsetKind"])
        self.assertEqual("0x0", self.records[0]["fields"][0]["offset"])
        self.assertEqual(1, self.report["staticFieldOffsetCount"])

    def test_runtime_flags_are_preserved_without_claiming_an_object_offset(self):
        struct.pack_into("<I", self.metadata, 0x700, 0x01000005)
        self.recover()
        field = self.records[0]["fields"][0]
        self.assertEqual("0x1000005", field["rawOffset"])
        self.assertIsNone(field["offset"])
        self.assertEqual("runtime", field["offsetKind"])
        self.assertEqual(1, self.report["runtimeFieldOffsetCount"])

    def test_negative_one_layout_remains_explicitly_unresolved(self):
        struct.pack_into("<i", self.metadata, 0x300, -1)
        self.recover()
        self.assertEqual("runtime", self.records[0]["fieldLayout"]["kind"])
        self.assertTrue(all(field["offset"] is None for field in self.records[0]["fields"]))
        self.assertEqual(2, self.report["runtimeFieldOffsetCount"])

    def test_an_invalid_negative_layout_index_is_rejected(self):
        struct.pack_into("<i", self.metadata, 0x300, -2)
        with self.assertRaisesRegex(ValueError, "Invalid field layout index"):
            self.recover()

    def test_lookup_span_must_fit_metadata(self):
        self.metadata = self.metadata[:0x303]
        with self.assertRaisesRegex(ValueError, "field layout lookup.*exceeds metadata"):
            self.recover()

    def test_layout_record_must_fit_metadata(self):
        self.metadata = self.metadata[:0x50B]
        with self.assertRaisesRegex(ValueError, "field layout record.*exceeds metadata"):
            self.recover()

    def test_offset_span_must_fit_metadata(self):
        self.metadata = self.metadata[:0x707]
        with self.assertRaisesRegex(ValueError, "field offset span.*exceeds metadata"):
            self.recover()

    def test_member_metadata_is_required(self):
        self.records[0].pop("fields")
        with self.assertRaisesRegex(ValueError, "Field metadata is required"):
            self.recover()

    def test_field_count_must_agree_with_the_decoded_member_table(self):
        self.records[0]["fieldCount"] = 3
        with self.assertRaisesRegex(ValueError, "Inconsistent field count"):
            self.recover()

    def test_bad_later_layout_does_not_leave_a_partial_success(self):
        self.records.append(copy.deepcopy(self.records[0])); self.records[1]["index"] = 1
        struct.pack_into("<i", self.metadata, 0x304, 999999)
        previous = copy.deepcopy(self.records); report = copy.deepcopy(self.report)
        with self.assertRaises(ValueError):
            self.recover()
        self.assertEqual(previous, self.records); self.assertEqual(report, self.report)

    def test_empty_types_do_not_require_an_offset_span(self):
        self.records[0].update(fieldCount=0, fields=[])
        self.recover()
        self.assertIsNone(self.records[0]["fieldLayout"])
        self.assertEqual(0, self.report["instanceFieldOffsetCount"])

    def test_existing_member_names_methods_and_report_are_preserved(self):
        previous = copy.deepcopy(self.records)
        self.recover()
        self.assertEqual(previous[0]["methods"], self.records[0]["methods"])
        self.assertEqual("kept", self.report["existing"])
        self.assertEqual([field["name"] for field in previous[0]["fields"]],
                         [field["name"] for field in self.records[0]["fields"]])

    def test_cli_requires_field_metadata_before_reading_the_client(self):
        with patch.object(sys, "argv", ["extract", "unused", "--include-field-offsets"]), \
                contextlib.redirect_stderr(io.StringIO()) as errors:
            with self.assertRaises(SystemExit) as result:
                extraction.main()
        self.assertEqual(2, result.exception.code)
        self.assertIn("--include-field-offsets requires --include-fields", errors.getvalue())

    def test_cli_invokes_offset_recovery_and_writes_the_extended_records(self):
        with tempfile.TemporaryDirectory() as directory:
            client = Path(directory) / "client"; client.mkdir(); output = Path(directory) / "analysis.json"
            with patch.object(sys, "argv", ["extract", str(client), "--include-fields", "--include-field-offsets", "--output", str(output)]), \
                    patch.object(extraction, "read_verified", return_value=b"verified"), \
                    patch.object(extraction, "recover_types", return_value=(self.records, self.report)), \
                    patch.object(extraction, "recover_members"), \
                    patch.object(extraction, "recover_field_offsets") as offsets, \
                    contextlib.redirect_stdout(io.StringIO()):
                extraction.main()
            offsets.assert_called_once_with(b"verified", b"verified", self.records, self.report)
            self.assertEqual(self.records, json.loads(output.read_text(encoding="utf-8")))

    def test_new_cli_option_preserves_the_client_output_protection(self):
        with tempfile.TemporaryDirectory() as directory:
            client = Path(directory); output = client / "analysis.json"
            with patch.object(sys, "argv", ["extract", str(client), "--include-fields", "--include-field-offsets", "--output", str(output)]), \
                    patch.object(extraction, "read_verified") as reader:
                with self.assertRaisesRegex(ValueError, "outside the client directory"):
                    extraction.main()
            reader.assert_not_called(); self.assertFalse(output.exists())


if __name__ == "__main__":
    unittest.main()
