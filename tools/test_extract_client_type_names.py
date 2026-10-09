"""Bounded inheritance decoding and malformed metadata rejection."""

import copy
import struct
import unittest
from unittest.mock import patch

import extract_client_type_names as extraction


class ParentMetadataTests(unittest.TestCase):
    def setUp(self):
        self.binary = bytearray(0x500)
        self.metadata = bytearray(4 * extraction.TYPE_STRIDE)
        self.records = [
            {"index": 0, "namespace": "System", "name": "Object"},
            {"index": 1, "namespace": "System", "name": "ValueType"},
            {"index": 2, "namespace": "Example", "name": "Derived"},
            {"index": 3, "namespace": "Example", "name": "GenericDerived"},
        ]
        self.report = {"typeOffset": "0x0"}
        struct.pack_into("<I", self.binary, 0x80 + 0x30, 4 ^ 0x28A0FA2D)
        struct.pack_into("<Q", self.binary, 0x80 + 0x50, 0x1000)
        self.reference(0, 28, 0)
        self.reference(1, 18, 1)
        self.reference(2, 21, 0)
        self.reference(3, 18, 2)
        for index, parent in enumerate((None, 0, 1, 2)):
            self.parent(index, parent)

    def reference(self, index, kind, data):
        struct.pack_into("<QI", self.binary, 0x200 + index * 16, data, kind << 16)

    def parent(self, index, reference):
        value = 0xFFFFFFFF if reference is None else reference
        struct.pack_into("<I", self.metadata, index * extraction.TYPE_STRIDE + 0x2C,
                         value ^ 0x5FCD8771)

    def recover(self, startup=None):
        def mapping(binary, address, size=1):
            if address == extraction.METADATA_REGISTRATION_VA and size == 0x58:
                return 0x80
            if address == 0x1000 and size == 64:
                return 0x200
            raise ValueError("Unexpected reference table address or span")
        with patch.object(extraction, "pe_file_offset", side_effect=mapping):
            extraction.recover_parents(bytes(self.binary), bytes(self.metadata),
                                       self.records, self.report, startup=startup)

    def test_roots_object_and_direct_parents_have_explicit_definitions(self):
        self.recover()
        self.assertIsNone(self.records[0]["parent"])
        self.assertEqual(0, self.records[1]["parent"]["typeDefinition"])
        self.assertEqual("Object", self.records[1]["parent"]["name"])
        self.assertEqual(1, self.records[2]["parent"]["typeDefinition"])
        self.assertEqual("System", self.records[2]["parent"]["namespace"])
        self.assertEqual(3, self.report["parentCount"])
        self.assertEqual(2, self.report["resolvedParentCount"])

    def test_generic_reference_is_preserved_when_startup_was_not_supplied(self):
        self.recover()
        self.assertEqual({"kind": "genericinst", "typeIndex": 2, "data": "0x0"},
                         self.records[3]["parent"])
        self.assertEqual(1, self.report["genericParentCount"])

    def test_startup_resolver_preserves_generic_arguments(self):
        resolved = {"typeDefinition": 1, "name": "ValueType", "namespace": "System",
                    "arguments": [{"kind": "uint32"}]}
        with patch.object(extraction, "GenericTypes") as constructor:
            constructor.return_value.resolve.side_effect = lambda value: {**value, **resolved}
            self.recover(startup=b"verified startup")
            constructor.assert_called_once_with(bytes(self.binary), b"verified startup", self.records)
        self.assertEqual(resolved["arguments"], self.records[3]["parent"]["arguments"])
        self.assertEqual(3, self.report["resolvedParentCount"])

    def test_reference_must_fit_the_registered_table(self):
        self.parent(2, 4)
        with self.assertRaisesRegex(ValueError, "Invalid parent reference"):
            self.recover()

    def test_definition_must_fit_the_type_table(self):
        self.reference(1, 18, len(self.records))
        with self.assertRaisesRegex(ValueError, "Invalid parent definition"):
            self.recover()

    def test_primitive_parent_is_rejected(self):
        self.reference(1, 12, 0)
        with self.assertRaisesRegex(ValueError, "Unsupported parent kind"):
            self.recover()

    def test_object_parent_cannot_resolve_to_an_arbitrary_type(self):
        self.records[0]["name"] = "Other"
        with self.assertRaisesRegex(ValueError, "exactly one System.Object"):
            self.recover()

    def test_ambiguous_object_definition_is_rejected(self):
        self.records[2].update(namespace="System", name="Object")
        with self.assertRaisesRegex(ValueError, "exactly one System.Object"):
            self.recover()

    def test_self_parent_is_rejected(self):
        self.parent(2, 3)
        with self.assertRaisesRegex(ValueError, "Cyclic parent definitions"):
            self.recover()

    def test_cycle_across_multiple_types_is_rejected(self):
        self.parent(1, 3)
        with self.assertRaisesRegex(ValueError, "Cyclic parent definitions"):
            self.recover()

    def test_resolved_generic_parent_participates_in_cycle_checks(self):
        with patch.object(extraction, "GenericTypes") as constructor:
            constructor.return_value.resolve.side_effect = lambda value: {**value, "typeDefinition": 3}
            with self.assertRaisesRegex(ValueError, "Cyclic parent definitions"):
                self.recover(startup=b"verified startup")

    def test_truncated_metadata_is_rejected(self):
        self.metadata = self.metadata[:3 * extraction.TYPE_STRIDE]
        with self.assertRaisesRegex(ValueError, "exceeds metadata"):
            self.recover()

    def test_unmapped_reference_span_is_rejected(self):
        struct.pack_into("<Q", self.binary, 0x80 + 0x50, 0xFFFF)
        with self.assertRaisesRegex(ValueError, "reference table address or span"):
            self.recover()

    def test_existing_member_tables_are_not_replaced(self):
        for record in self.records:
            record.update(fields=[{"name": "Existing"}], methods=[{"address": "0x1234"}])
        previous = copy.deepcopy(self.records)
        self.recover()
        for before, after in zip(previous, self.records):
            for key in before:
                self.assertEqual(before[key], after[key])


if __name__ == "__main__":
    unittest.main()
