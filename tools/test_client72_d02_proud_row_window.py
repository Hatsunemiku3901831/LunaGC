import struct
import unittest
from tools import client72_d02_proud_row_window as subject


class Gates(unittest.TestCase):
    def test_source_drift_rejected(self):
        with self.assertRaisesRegex(ValueError, 'source-drift'):
            subject.checked(b'changed', '0'*64, 'fixture')

    def test_unique_exact_extent(self):
        table = struct.pack('<III', 0x100, 0x200, 0x300)
        diagnostic = subject.extent(table, 0x1100, 0x1000, 0x400)
        self.assertEqual(subject.select_extent(diagnostic)['size'], 0x100)

    def test_ambiguous_exact_and_containing_rejected(self):
        table = struct.pack('<III', 0x100, 0x200, 0x300) + struct.pack('<III', 0x80, 0x180, 0x301)
        with self.assertRaisesRegex(ValueError, 'ambiguous'):
            subject.select_extent(subject.extent(table, 0x1100, 0x1000, 0x400))

    def test_nonexact_rejected(self):
        table = struct.pack('<III', 0x80, 0x200, 0x300)
        with self.assertRaisesRegex(ValueError, 'non-exact'):
            subject.select_extent(subject.extent(table, 0x1100, 0x1000, 0x400))

    def test_oversize_reported_by_boundary(self):
        table = struct.pack('<III', 0x100, 0x901, 0x300)
        row = subject.select_extent(subject.extent(table, 0x1100, 0x1000, 0x400))
        self.assertEqual(row['size'], 2049)
        with self.assertRaisesRegex(ValueError, '2049 bytes'):
            subject.enforce_bound(row['size'])
        subject.enforce_bound(2048)


if __name__ == '__main__':
    unittest.main()
