"""7.2 decoder vectors and bounds, independent of any encoder in the tool."""
import hashlib
from pathlib import Path
import struct
import tempfile
import unittest

from tools.extract_client72 import Names, PE, read_verified, literal_u16, method_extent, method_flags_slot


class Client72Test(unittest.TestCase):
    def test_method_flags_slot_independent_target_words(self):
        # Actual target words, not values produced by an inverse encoder.
        for index, flags, slot, expected in ((562051, 0x736D, 0x4D05, (0x9C4, 22)),
                                            (533477, 0x9DBF, 0xE057, (0x8C4, 22)),
                                            (533473, 0x63B5, 0x6646, (0xC6, 17)),
                                            (533467, 0x1B27, 0x55E6, (0x1886, 65535))):
            self.assertEqual(expected, method_flags_slot(index, flags, slot))
        with self.assertRaises(ValueError):
            method_flags_slot(-1, 0, 0)

    def test_method_table_includes_alignment_not_an_extra_method(self):
        self.assertEqual(741021, method_extent(19266548, 741021))
        self.assertEqual(2, method_extent(52, 2))
        for size in (19266546, 19266549, 19266552):
            with self.assertRaises(ValueError):
                method_extent(size, 741021)

    def test_independent_name_vectors_from_target(self):
        # Source SHA-256/profile and instruction evidence: docs/CLIENT_72_EXTRACTION.md.
        for index, cipher, expected in (
            (0x0B007ECC, "55128e6ede46b67d0d8a1a1ab8b34397", "RuntimeType"),
            (0x060030B9, "ca6797ea6f35ac3e", "System"),
            (0x135E3B23, "b8cfddecfa745b090b563fcf8ec16b2caea097fcd2cbe7ba", "MonoCrucibleEndPage"),
        ):
            data = bytearray((index & 0xFFFFFF) + len(bytes.fromhex(cipher)))
            data[index & 0xFFFFFF:] = bytes.fromhex(cipher)
            self.assertEqual(expected, Names(data, 0).decode(index))

    def test_name_bounds_sentinel_and_invalid_index(self):
        names = Names(b"", 0)
        self.assertEqual("", names.decode(0xFFFFFFFF))
        self.assertEqual("", names.decode(0))
        for index in (-1, 1 << 32, 0x01000000):
            with self.assertRaises(ValueError):
                names.decode(index)

    def test_hash_mismatch_is_not_bypassed(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "input"
            path.write_bytes(b"abc")
            self.assertEqual(b"abc", read_verified(path, hashlib.sha256(b"abc").hexdigest()))
            with self.assertRaisesRegex(ValueError, "SHA-256"):
                read_verified(path, "0" * 64)

    def binary(self):
        data = bytearray(1024)
        data[:2] = b"MZ"
        struct.pack_into("<I", data, 0x3C, 0x80)
        data[0x80:0x84] = b"PE\0\0"
        struct.pack_into("<HH", data, 0x84, 0x8664, 1)
        struct.pack_into("<H", data, 0x94, 0xF0)
        struct.pack_into("<H", data, 0x98, 0x20B)
        struct.pack_into("<Q", data, 0xB0, 0x140000000)
        section = 0x188
        struct.pack_into("<IIII", data, section + 8, 256, 0x1000, 128, 512)
        struct.pack_into("<I", data, section + 36, 0x60000020)
        return data

    def test_pe_rejects_virtual_tail_and_cross_section_reads(self):
        pe = PE(self.binary())
        self.assertEqual(512, pe.offset(0x140001000, 128, executable=True))
        for address, size in ((0x140001080, 1), (0x14000107F, 2), (0, 1), (0x140001000, -1)):
            with self.assertRaises(ValueError):
                pe.offset(address, size)

    def test_pe_rejects_non_code_and_malformed_headers(self):
        data = self.binary()
        struct.pack_into("<I", data, 0x188 + 36, 0x40000040)
        with self.assertRaises(ValueError):
            PE(data).offset(0x140001000, executable=True)
        for data in (b"", b"MZ" + b"\0" * 60, self.binary()[:600]):
            with self.assertRaises(ValueError):
                PE(data)

    def test_constant_getter_is_not_claimed_as_opcode(self):
        self.assertEqual(65535, literal_u16(bytes.fromhex("66b8ffffc3")))
        for code in (b"", bytes.fromhex("b801000000c3"), bytes.fromhex("66b8010090")):
            self.assertIsNone(literal_u16(code))


if __name__ == "__main__":
    unittest.main()
