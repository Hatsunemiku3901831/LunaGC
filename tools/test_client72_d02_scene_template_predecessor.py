"""Offline source/extent and semantic-boundary checks; no target execution."""
import copy
import json
import unittest
from unittest import mock

from tools import client72_d02_scene_template_predecessor as current


class SceneTemplatePredecessorTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.files = {name: current.pinned(current.OUT / 'predecessor1' / name, expected)
                     for name, expected in current.WINDOW_PINS.items()}

    def test_real_reference_clear_does_not_approve_destruction(self):
        result = current.audit()
        self.assertEqual(138373, result['fields']['entityTemplate']['index'])
        self.assertEqual(134163, result['fields']['encodedTemplateId']['index'])
        self.assertEqual(0, result['ordinaryFlow']['clearValue'])
        self.assertEqual('0x148d8e53c', result['ordinaryFlow']['clearSite'])
        self.assertFalse(result['oldTemplateObjectDestroyedProven'])
        self.assertFalse(result['componentDestructionProven'])
        self.assertFalse(result['normalReturnValueTypeRecovered'])
        self.assertFalse(result['slot114ActualTargetObserved'])

    def test_source_drift_stops_before_window_or_metadata(self):
        first = next(iter(current.PINS))
        with mock.patch.dict(current.PINS, {first: '0' * 64}):
            with mock.patch.object(current, 'window', side_effect=AssertionError('unexpected body processing')):
                with self.assertRaisesRegex(ValueError, 'fixed-source-drift'):
                    current.audit()

    def test_code_drift_is_rejected(self):
        files = dict(self.files)
        files['code.bin'] = bytes([files['code.bin'][0] ^ 1]) + files['code.bin'][1:]
        with self.assertRaisesRegex(ValueError, 'original-instructions'):
            current.window(files)

    def test_neighbor_or_padded_extent_is_rejected(self):
        files = dict(self.files)
        report = json.loads(files['extraction.private.json'])
        report['endExclusive'] = '0x148d8e650'
        files['extraction.private.json'] = json.dumps(report).encode()
        with self.assertRaisesRegex(ValueError, 'extent'):
            current.window(files)

    def test_duplicate_containing_pdata_is_rejected(self):
        files = dict(self.files)
        report = json.loads(files['extraction.private.json'])
        diagnostic = report['exceptionDirectory']['diagnostic']
        diagnostic['containing'].append(copy.deepcopy(diagnostic['containing'][0]))
        files['extraction.private.json'] = json.dumps(report).encode()
        with self.assertRaisesRegex(ValueError, 'nonunique-exact-pdata'):
            current.window(files)

    def test_wrong_original_aot_is_rejected(self):
        files = dict(self.files)
        files['slot.bin'] = (0x148DA0A30).to_bytes(8, 'little')
        with self.assertRaisesRegex(ValueError, 'original-aot'):
            current.window(files)


if __name__ == '__main__':
    unittest.main()
