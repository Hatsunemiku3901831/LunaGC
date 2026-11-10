import copy
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from tools import client72_d02_all_saved_depot_refs as subject


class AllSavedDepotTests(unittest.TestCase):
    def test_selection_rejects_missing_duplicate_and_drift(self):
        rows = [dict(avatarId=i, currentDepotId=i + 200, depotSource={'sha256': 'ab' * 32}) for i in range(1, 122)]
        calculated = {'avatars': rows, 'missingActiveAvatarIds': [], 'resourceReady': False}
        acceptance = {'oldAccountActualAvatarCount': 121}
        with patch('tools.client72_d02_old_avatar_coverage.analyze', return_value=calculated):
            self.assertEqual(len(subject.select_samples({}, copy.deepcopy(calculated), {}, acceptance)), 121)
            changed = copy.deepcopy(calculated)
            changed['avatars'][0]['currentDepotId'] += 1
            with self.assertRaisesRegex(ValueError, 'Coverage differs'):
                subject.select_samples({}, changed, {}, acceptance)
            rows[1]['currentDepotId'] = rows[0]['currentDepotId']
            with self.assertRaisesRegex(ValueError, 'incomplete or duplicated'):
                subject.select_samples({}, copy.deepcopy(calculated), {}, acceptance)
            rows[1]['currentDepotId'] += 1
            rows[1]['depotSource'] = None
            with self.assertRaisesRegex(ValueError, 'incomplete or duplicated'):
                subject.select_samples({}, copy.deepcopy(calculated), {}, acceptance)

    def test_positive_fields_nested_order_zero_null_and_presence(self):
        nested = [[{'fieldIndex': 404853, 'value': 0}, {'fieldIndex': 404854, 'value': 2**40}],
                  [{'fieldIndex': 404853, 'value': 3}, {'fieldIndex': 404854, 'value': 2**40}], None]
        samples = [{'fields': [{'fieldIndex': 105355, 'value': nested},
                               {'fieldIndex': 105357, 'value': [0, 9, 9, None]}]}]
        subject.mark_presence(nested)
        self.assertEqual(nested[0][0]['inputPresence'], 'unknown')
        self.assertEqual(subject.positive_fields(samples), [
            {'fieldIndex': 105357, 'values': [{'value': 9, 'occurrences': 2}]},
            {'fieldIndex': 404853, 'values': [{'value': 3, 'occurrences': 1}]},
            {'fieldIndex': 404854, 'values': [{'value': 2**40, 'occurrences': 2}]}])
        self.assertIsNone(nested[-1])

    def test_whole_row_order_duplicates_and_source(self):
        rows = [dict(key=i, source={'begin': i, 'end': i + 1}) for i in range(171)]
        subject.check_rows(rows, copy.deepcopy(rows))
        for altered in (rows[:-1] + [rows[0]], list(reversed(rows)),
                        rows[:10] + [dict(key=10, source={'begin': 10, 'end': 99})] + rows[11:]):
            with self.assertRaises(ValueError):
                subject.check_rows(altered, rows)

    def test_hash_and_output_boundaries(self):
        with tempfile.TemporaryDirectory(dir=subject.OUT) as directory:
            path = Path(directory) / 'pin.bin'
            path.write_bytes(b'wrong')
            with self.assertRaisesRegex(ValueError, 'Pinned input differs'):
                subject.read_pinned(path, '0' * 64)
        for output in ('../escape.json', 'plan.zh.md'):
            run = subprocess.run([sys.executable, str(subject.ROOT / 'tools/client72_d02_all_saved_depot_refs.py'),
                                  '--output', output], cwd=subject.ROOT, capture_output=True, text=True)
            self.assertNotEqual(run.returncode, 0)
            self.assertIn('output must be a new JSON basename', run.stderr)


if __name__ == '__main__':
    unittest.main()
