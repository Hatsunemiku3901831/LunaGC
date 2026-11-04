"""Check binary inventory mechanics with synthetic bytes, never client mutations."""

import struct
import unittest

from tools.audit_client import pattern_regex, pe_ranges


class ClientAuditTest(unittest.TestCase):
    def test_wildcards_match_newlines_and_literal_regex_metacharacters(self):
        pattern = pattern_regex("2E ?? 5B ? 5C")
        self.assertIsNotNone(pattern.search(b"prefix.\n[\0\\suffix"))
        self.assertIsNone(pattern.search(b"prefixX\n[\0\\suffix"))

    def test_empty_or_all_wildcard_signature_is_rejected(self):
        for pattern in ("", "? ??"):
            with self.assertRaises(ValueError):
                pattern_regex(pattern)

    def test_raw_offsets_are_distinct_from_virtual_addresses(self):
        binary = bytearray(1024)
        binary[:2] = b"MZ"
        struct.pack_into("<I", binary, 0x3C, 0x80)
        binary[0x80:0x84] = b"PE\0\0"
        struct.pack_into("<H", binary, 0x86, 1)
        struct.pack_into("<H", binary, 0x94, 0xF0)
        struct.pack_into("<I", binary, 0x80 + 28, 32)
        struct.pack_into("<I", binary, 0x80 + 44, 0x1010)
        section = 0x80 + 24 + 0xF0
        binary[section:section + 8] = b"il2cpp\0\0"
        struct.pack_into("<III", binary, section + 12, 0x1000, 128, 512)
        self.assertEqual({"il2cpp": [(512, 640, 0x1000)],
                          "code": [(528, 560, 0x1010)]}, pe_ranges(binary))
        struct.pack_into("<I", binary, section + 16, 10000)
        with self.assertRaises(ValueError):
            pe_ranges(binary)


if __name__ == "__main__":
    unittest.main()
