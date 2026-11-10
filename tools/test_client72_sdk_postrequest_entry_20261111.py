"""Offline boundary regressions; no PE, process, hook or target execution."""
import struct
import unittest
from unittest import mock

from tools import client72_sdk_postrequest_entry_20261111 as entry


class PostRequestEntryBoundaryTests(unittest.TestCase):
    def test_only_exact_and_unique_containing_extent_is_accepted(self):
        records = struct.pack('<III', 0x100, 0x200, 0x300)
        row = entry.select_extent(entry.extent_diagnostic(records, 0x100))
        self.assertEqual(row, (0, 0x100, 0x200, 0x300))
        for raw, address in ((records, 0x101), (records, 0x200),
                             (records + records, 0x100),
                             (records + struct.pack('<III', 0x80, 0x180, 0x400), 0x100)):
            with self.subTest(address=address, bytes=len(raw)):
                with self.assertRaisesRegex(ValueError, 'exception-extent-not-unique'):
                    entry.select_extent(entry.extent_diagnostic(raw, address))

    def test_approved_cap_is_inclusive_and_cannot_read_past_it(self):
        for size, accepted in ((8192, True), (8193, False), (0, False), (-1, False)):
            raw = struct.pack('<III', 0x100, 0x100 + size, 0x300)
            diagnostic = entry.extent_diagnostic(raw, 0x100)
            if accepted:
                self.assertEqual(entry.select_extent(diagnostic)[2], 0x2100)
            else:
                with self.assertRaises(ValueError):
                    entry.select_extent(diagnostic)

    def test_misaligned_exception_directory_is_rejected(self):
        with self.assertRaisesRegex(ValueError, 'exception-directory-misaligned'):
            entry.extent_diagnostic(b'\x00' * 13, 0x100)

    def test_instruction_coverage_does_not_split_a_boundary(self):
        lengths = [2, 2, 2, 2, 1, 1, 1, 1, 4, 5]
        position, assembly = 0x1992C890, []
        for width in lengths:
            assembly.append({'rva': hex(position), 'size': width})
            position += width
        self.assertEqual(entry.minimum_coverage(assembly, 14)['coveredBytes'], 16)
        self.assertEqual(entry.minimum_coverage(assembly, 14)['resumeRVA'], '0x1992c8a0')
        self.assertEqual(entry.minimum_coverage(assembly, 5)['coveredBytes'], 6)

    def test_source_drift_stops_before_import_or_pe(self):
        with mock.patch.object(entry.Path, 'read_bytes', return_value=b'drift'):
            with self.assertRaisesRegex(ValueError, 'frozen-source-differs'):
                entry.verify_pins()


if __name__ == '__main__':
    unittest.main()
