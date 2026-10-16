"""Native encrypted usage records and class/method/string cache separation."""

import contextlib
import io
import os
import struct
import tempfile
import unittest
from pathlib import Path

import extract_client_usage_types as extraction


# Eight-byte records copied from the SHA-verified 7.1 metadata, not encoder round trips.
VECTORS = [
    (30310, 0x7B135905, 0xF918EF1B, 5, 11275, 11275),
    (44804, 0xDCA2D6CE, 0xFB880697, 6, 11275, 16510),
    (705642, 0x7DE9E674, 0x7472A0E9, 1, 11259, 258458),
    (705647, 0x924B3DB3, 0x61D378CE, 1, 11275, 231180),
    (739134, 0xE69D2ED4, 0xED86DF68, 6, 18681, 21881),
]


class UsageTypeTests(unittest.TestCase):
    def test_native_class_usage_records_including_wrapping_64_bit_products(self):
        for index, destination, source, kind, decoded_dest, decoded_source in VECTORS[2:4]:
            with self.subTest(index=index):
                self.assertEqual(
                    (index, kind, decoded_dest, decoded_source),
                    extraction.decode_pair(index, destination, source),
                )

    def test_real_colliding_string_and_method_destinations_retain_their_kinds(self):
        for index, destination, source, kind, decoded_dest, decoded_source in [*VECTORS[:2], VECTORS[4]]:
            with self.subTest(index=index):
                self.assertEqual(
                    (index, kind, decoded_dest, decoded_source),
                    extraction.decode_pair(index, destination, source),
                )

    def test_a_factory_is_not_resolved_from_an_equal_number_in_another_cache(self):
        records = [(705647, 1, 11275, 231180), (30310, 5, 11275, 11275), (44804, 6, 11275, 16510)]
        self.assertEqual({11275: (231180, [705647])}, extraction.collect_type_uses(records, {11275}))

    def test_a_missing_class_usage_does_not_return_an_empty_success(self):
        with self.assertRaisesRegex(ValueError, "Missing"):
            extraction.collect_type_uses([(30310, 5, 11275, 11275)], {11275})

    def test_repeated_identical_class_initializations_keep_their_provenance(self):
        records = [(7, 1, 12, 25), (9, 1, 12, 25)]
        self.assertEqual({12: (25, [7, 9])}, extraction.collect_type_uses(records, {12}))

    def test_conflicting_class_initializations_are_rejected(self):
        with self.assertRaisesRegex(ValueError, "Ambiguous"):
            extraction.collect_type_uses([(7, 1, 12, 25), (9, 1, 12, 26)], {12})

    def test_a_registered_class_reference_resolves_the_named_definition(self):
        binary = bytearray(48)
        struct.pack_into("<QI", binary, 32, 1, 18 << 16)
        types = [{"index": 0, "name": "Other", "namespace": ""},
                 {"index": 1, "name": "Request", "namespace": "Proto"}]
        self.assertEqual({"kind": "class", "typeDefinition": 1, "name": "Request", "namespace": "Proto"},
                         extraction.resolve_reference(bytes(binary), 0, 3, types, 2))

    def test_references_must_fit_both_the_registration_and_the_backing_bytes(self):
        for index, count, data in [(3, 3, b"\0" * 48), (0, 1, b"\0" * 8), (-1, 1, b"\0" * 16)]:
            with self.subTest(index=index, count=count), self.assertRaises(ValueError):
                extraction.resolve_reference(data, 0, count, [], index)

    def test_primitive_generic_and_out_of_range_definitions_cannot_be_named_classes(self):
        for kind, definition in [(9, 0), (21, 0), (18, 2)]:
            binary = struct.pack("<QI", definition, kind << 16) + b"\0" * 4
            with self.subTest(kind=kind, definition=definition), self.assertRaises(ValueError):
                extraction.resolve_reference(binary, 0, 1, [{"index": 0, "name": "Only", "namespace": ""}], 0)

    def test_invalid_pair_inputs_are_rejected(self):
        for values in [(-1, 0, 0), (True, 0, 0), (1.5, 0, 0), (0, -1, 0), (0, 0, 0x100000000)]:
            with self.subTest(values=values), self.assertRaises(ValueError):
                extraction.decode_pair(*values)

    def test_a_literal_packet_getter_returns_the_native_16_bit_value(self):
        self.assertEqual(27235, extraction.literal_packet_id(bytes.fromhex("66b8636ac3")))

    def test_nonliteral_truncated_and_extended_getters_are_not_packet_ids(self):
        for code in (b"", bytes.fromhex("66b8636a"), bytes.fromhex("b8636a0000"), bytes.fromhex("66b8636ac390")):
            with self.subTest(code=code):
                self.assertIsNone(extraction.literal_packet_id(code))

    def test_nonbyte_packet_getters_are_rejected(self):
        with self.assertRaises(ValueError):
            extraction.literal_packet_id("66b8636ac3")

    def test_native_constructor_and_packet_getter_name_ciphertexts(self):
        for index, encoded, expected in [
            (286508, 0xD4338C9D, 0x05000022),
            (286514, 0xDB4D531D, 0x0B187A72),
            (336427, 0xA9DF7C15, 0x05000022),
            (336431, 0xE7C93FC5, 0x0B187A72),
        ]:
            with self.subTest(index=index):
                self.assertEqual(expected, extraction.method_name_index(index, encoded))

    def test_an_unsupported_client_keeps_the_existing_report_and_inputs(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            client = root / "client"
            client.mkdir()
            binary, metadata, output = client / "game.exe", client / "global.dat", root / "out.json"
            binary.write_bytes(b"unsupported exe")
            metadata.write_bytes(b"unsupported metadata")
            output.write_bytes(b"previous report")
            with contextlib.redirect_stderr(io.StringIO()) as error, self.assertRaises(SystemExit):
                extraction.main(["--binary", str(binary), "--metadata", str(metadata),
                                 "--cell", "0x1457b4058", "--output", str(output)])
            self.assertIn("Unsupported client file", error.getvalue())
            self.assertEqual(b"previous report", output.read_bytes())
            self.assertEqual(b"unsupported exe", binary.read_bytes())
            self.assertEqual(b"unsupported metadata", metadata.read_bytes())

    def test_outputs_cannot_replace_inputs_or_startup_metadata_through_hardlinks(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            client = root / "client"
            client.mkdir()
            binary, metadata = client / "game.exe", client / "global.dat"
            startup = client / "startup-metadata.dat"
            binary.write_bytes(b"exe")
            metadata.write_bytes(b"metadata")
            startup.write_bytes(b"startup")
            for index, source in enumerate((binary, metadata, startup)):
                alias = root / f"alias-{index}.json"
                os.link(source, alias)
                for output in (source, alias):
                    with contextlib.redirect_stderr(io.StringIO()) as error, self.assertRaises(SystemExit):
                        extraction.main(["--binary", str(binary), "--metadata", str(metadata),
                                         "--cell", "0x1457b4058", "--output", str(output)])
                    self.assertIn("Output must differ", error.getvalue())
            self.assertEqual(b"exe", binary.read_bytes())
            self.assertEqual(b"metadata", metadata.read_bytes())
            self.assertEqual(b"startup", startup.read_bytes())

    def test_outputs_cannot_be_created_inside_the_client_directory(self):
        with tempfile.TemporaryDirectory() as folder:
            client = Path(folder)
            binary, metadata, output = client / "game.exe", client / "global.dat", client / "out.json"
            binary.write_bytes(b"exe")
            metadata.write_bytes(b"metadata")
            with contextlib.redirect_stderr(io.StringIO()) as error, self.assertRaises(SystemExit):
                extraction.main(["--binary", str(binary), "--metadata", str(metadata),
                                 "--cell", "0x1457b4058", "--output", str(output)])
            self.assertIn("outside the client directory", error.getvalue())
            self.assertFalse(output.exists())


if __name__ == "__main__":
    unittest.main()
