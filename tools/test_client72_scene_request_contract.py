import copy
import json
from pathlib import Path
import unittest

from tools.client72_scene_request_contract import VECTORS, decode_request_token, validate_summary


class SceneRequestContractTests(unittest.TestCase):
    def setUp(self):
        self.report = json.loads((Path(__file__).resolve().parents[1] /
            'protocol/cn-7.2/scene-request-contract-observations.json').read_text('utf8'))

    def test_fixed_native_boundary_report(self):
        self.assertEqual(validate_summary(self.report), [])

    def test_four_inverse_literals_and_neighbors(self):
        for index, cases in VECTORS.items():
            for logical, raw, _ in cases:
                self.assertEqual(decode_request_token(index, raw), logical)
        self.assertEqual(decode_request_token(63999, 0), 42557)
        self.assertEqual(decode_request_token(74076, 0), 64180)
        with self.assertRaises(ValueError):
            decode_request_token(17278, 0)

    def test_request_omission_cannot_be_promoted_to_logical_zero(self):
        report = copy.deepcopy(self.report)
        report['inverseBindings'][2]['omittedLogicalToken'] = 0
        self.assertTrue(validate_summary(report))

    def test_raw_zero_was_omitted_by_native_writer(self):
        for row in self.report['fixtures']:
            if row['rawToken'] == 0:
                self.assertFalse(row['tokenFieldEmitted'])
                self.assertEqual(row['wire'], '090100000000004043' if row['typeIndex'] == 74076 else '')
        report = copy.deepcopy(self.report)
        report['fixtures'][4]['wire'] = '6000'
        self.assertTrue(validate_summary(report))

    def test_scope_hash_and_boundary_tampering_rejected(self):
        for key, value in [('targetProtocolReady', True), ('runtimeStatus', 'passed'), ('inputSHA256', {})]:
            report = copy.deepcopy(self.report)
            report[key] = value
            self.assertTrue(validate_summary(report))
        report = copy.deepcopy(self.report)
        report['fixtures'][0]['trace']['stop'] = '0x15120d731'
        self.assertTrue(validate_summary(report))


if __name__ == '__main__':
    unittest.main()
