import copy
import json
from pathlib import Path
import unittest
from tools.extract_client72 import HASHES
from tools.trace_client72_stage_timing import validate_fixtures

ROOT = Path(__file__).resolve().parents[1]


class StageTimingTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.report = json.loads((ROOT / 'protocol/cn-7.2/stage-timing-observations.json').read_text('utf8'))

    def rows(self):
        return copy.deepcopy(self.report['observations'])

    def test_stage_declaration_symbol_and_bounded_scope(self):
        self.assertEqual(self.report['inputSHA256'], HASHES)
        self.assertEqual(self.report['stageDeclaration']['enumTypeIndex'], 85949)
        self.assertEqual(next(v['value'] for v in self.report['stageEnum']['values'] if v['symbol'] == 'OnPlayerEnterSceneNotify'), 10)
        self.assertEqual(self.report['timingEntry'], '0x14c8ae9a0')
        self.assertEqual(self.report['fixtureCount'], 13)
        self.assertEqual(self.report['runtimeStatus'], 'not-tested')
        self.assertFalse(self.report['targetProtocolReady'])
        validate_fixtures(self.rows())

    def test_native_float_clock_conversion_and_exact_slot(self):
        rows = {r['name']: r for r in self.rows()}
        self.assertEqual(rows['enabled-scene-stage']['arrayBits'][10], 0x42F64000)
        self.assertEqual(rows['last-stage-float-rounding']['arrayBits'][15], 0x4B800000)
        self.assertEqual(rows['zero-stage-zero-clock']['arrayBits'][0], 0)
        self.assertEqual([r['writes'][0]['index'] for r in rows.values() if r.get('writes')], [10, 0, 15])

    def test_disabled_negative_and_bounds_skip_clock(self):
        for row in self.rows():
            if row['name'] in ('ifix-boundary', 'enabled-null-array') or row.get('writes'): continue
            self.assertEqual(row['clockCalls'], [])
            self.assertIsNone(row['error'])
            self.assertEqual(set(row['arrayBits']) - {row['sentinelBits']}, set())

    def test_null_array_is_native_error_and_cannot_be_replaced_with_success(self):
        rows = self.rows(); row = next(r for r in rows if r['name'] == 'enabled-null-array')
        self.assertEqual(row['error'], 'native-null-array')
        row['error'] = None
        with self.assertRaises(ValueError): validate_fixtures(rows)

    def test_ifix_body_and_missing_case_cannot_be_claimed(self):
        rows = self.rows(); rows[-1]['executed'] = True
        with self.assertRaises(ValueError): validate_fixtures(rows)
        rows = self.rows(); rows[1] = copy.deepcopy(rows[0])
        with self.assertRaises(ValueError): validate_fixtures(rows)

    def test_wrong_timestamp_or_writing_without_clock_fails(self):
        rows = self.rows(); rows[0]['arrayBits'][10] = 0
        with self.assertRaises(ValueError): validate_fixtures(rows)
        rows = self.rows(); rows[0]['clockCalls'] = []
        with self.assertRaises(ValueError): validate_fixtures(rows)


if __name__ == '__main__': unittest.main()
