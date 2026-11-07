"""Independent decoded-header interval/error tests, using synthetic structures."""
import struct
import unittest

try:
    from .client72_resource_blob import parse_header
except ImportError:
    from client72_resource_blob import parse_header


def fixture():
    # Two compressed blocks partition 11 bytes; one 20-byte logical content.
    raw = bytearray(96)
    struct.pack_into('<II', raw, 0, 135, 4)
    raw[0x14:0x16] = bytes((3, 4))
    struct.pack_into('<II', raw, 0x18, 2, 1)
    struct.pack_into('<q', raw, 0x20, 24)  # block ends at 56
    struct.pack_into('<q', raw, 0x28, 24)  # entry at 64
    struct.pack_into('<q', raw, 0x30, -40)  # bitset at 8
    struct.pack_into('<II', raw, 56, 5, 11)
    struct.pack_into('<IIq', raw, 64, 0, 20, 8)  # name at 80
    raw[80:85] = b'item\0'
    return raw


class BlobHeaderTests(unittest.TestCase):
    def test_valid_partition_and_container_tail(self):
        parsed = parse_header(fixture(), 1000, 96)
        self.assertEqual(parsed['logicalBlobBytes'], 135)
        self.assertEqual(parsed['fileTailBytes'], 865)
        self.assertEqual(parsed['uncompressedBytes'], 20)
        self.assertEqual(parsed['compressedEnds'], [5, 11])
        self.assertEqual(parsed['content'][0]['name'], 'item')
        self.assertFalse(parsed['content'][0]['flag'])

    def test_bitset_and_nonzero_content_offset(self):
        raw = fixture()
        struct.pack_into('<I', raw, 8, 1)
        struct.pack_into('<II', raw, 64, 7, 13)
        item = parse_header(raw, 135, 96)['content'][0]
        self.assertEqual((item['offset'], item['size'], item['flag']), (7, 13, True))

    def test_truncation_and_header_extent(self):
        for raw, size in ((fixture()[:-1], 96), (fixture(), 95), (bytes(64), 64)):
            with self.subTest(size=size), self.assertRaises(ValueError):
                parse_header(raw, 1000, size)

    def test_block_counts_exponent_and_tail_bounds(self):
        for offset, fmt, value in ((24, 'I', 0), (24, 'I', 100001), (28, 'I', 0),
                                   (28, 'I', 100001), (21, 'B', 32), (4, 'I', 0), (4, 'I', 17)):
            raw = fixture()
            struct.pack_into('<'+fmt, raw, offset, value)
            with self.subTest(offset=offset, value=value), self.assertRaises(ValueError):
                parse_header(raw, 1000, 96)

    def test_file_and_compressed_partition_bounds(self):
        for total, first, last in ((134, 5, 11), (136, 5, 11), (135, 0, 11), (135, 11, 11), (135, 12, 11)):
            raw = fixture()
            struct.pack_into('<I', raw, 0, total)
            struct.pack_into('<II', raw, 56, first, last)
            with self.subTest(total=total, first=first), self.assertRaises(ValueError):
                parse_header(raw, 1000, 96)
        with self.assertRaises(ValueError):
            parse_header(fixture(), 134, 96)

    def test_signed_relative_table_bounds(self):
        for offset, value in ((32, -33), (32, 62), (40, 55), (48, -49), (48, 46)):
            raw = fixture()
            struct.pack_into('<q', raw, offset, value)
            with self.subTest(offset=offset), self.assertRaises(ValueError):
                parse_header(raw, 1000, 96)

    def test_content_extent_and_name_bounds(self):
        for offset, fmt, value in ((64, 'I', 1), (68, 'I', 21), (72, 'q', -73), (72, 'q', 24)):
            raw = fixture()
            struct.pack_into('<'+fmt, raw, offset, value)
            with self.subTest(offset=offset), self.assertRaises(ValueError):
                parse_header(raw, 1000, 96)

    def test_missing_name_terminator(self):
        raw = fixture()
        raw[80:] = b'x'*16
        with self.assertRaises(ValueError):
            parse_header(raw, 1000, 96)


if __name__ == '__main__':
    unittest.main()
