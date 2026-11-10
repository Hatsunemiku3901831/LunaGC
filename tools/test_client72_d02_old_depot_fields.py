"""Focused source and row-gate regressions for the saved Depot sampler."""
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

from tools import client72_d02_old_depot_fields as subject


class DepotFieldsGateTests(unittest.TestCase):
    def setUp(self):
        self.rows = [dict(key=i, source=dict(begin=i, end=i + 1, sha256=str(i))) for i in range(171)]

    def test_identical_rows_pass(self):
        subject.check_rows(self.rows, [dict(row) for row in self.rows])

    def test_duplicate_and_changed_source_are_rejected(self):
        duplicate = self.rows[:-1] + [self.rows[0]]
        with self.assertRaises(ValueError):
            subject.check_rows(duplicate, self.rows)
        changed = list(self.rows)
        changed[4] = dict(key=4, source=dict(begin=4, end=6, sha256='4'))
        with self.assertRaises(ValueError):
            subject.check_rows(changed, self.rows)

    def test_changed_row_order_is_rejected(self):
        altered = list(self.rows)
        altered[2], altered[3] = altered[3], altered[2]
        with self.assertRaises(ValueError):
            subject.check_rows(altered, self.rows)

    def test_pin_mismatch_is_rejected(self):
        with tempfile.TemporaryDirectory(dir=subject.OUT) as directory:
            path = Path(directory) / 'pin.bin'
            path.write_bytes(b'wrong')
            with self.assertRaisesRegex(ValueError, 'Pinned input differs'):
                subject.read_pinned(path, '0' * 64)

    def test_output_escape_and_existing_evidence_are_rejected(self):
        for output in ('../escape.json', 'source-audit.private.json'):
            run = subprocess.run([sys.executable, str(subject.ROOT / 'tools/client72_d02_old_depot_fields.py'),
                                  '--output', output], cwd=subject.ROOT, capture_output=True, text=True)
            self.assertNotEqual(run.returncode, 0)
            self.assertIn('output must be a new JSON basename', run.stderr)


if __name__ == '__main__':
    unittest.main()
