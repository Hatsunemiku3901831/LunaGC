import copy
import json
from pathlib import Path
import unittest

from tools.client72_player_data_contract import fixture_cases, validate_report


class PlayerDataContractTests(unittest.TestCase):
    def setUp(self):
        self.report = json.loads((Path(__file__).resolve().parents[1] /
            'protocol/cn-7.2/player-data-contract-observations.json').read_text('utf8'))

    def test_native_dataflow_contract(self):
        self.assertEqual(validate_report(self.report), [])

    def test_purpose_inference_does_not_claim_original_name_or_live_state(self):
        for key, value in [('identityBasis', 'Asta-name'), ('runtimeStatus', 'passed'),
                           ('originalClientMessageNameRecovered', True), ('targetProtocolReady', True),
                           ('wholeHandlerReturned', True), ('models', [])]:
            report = copy.deepcopy(self.report); report[key] = value
            self.assertTrue(validate_report(report), key)
        report = copy.deepcopy(self.report); report['results'][0]['sdkEvent'] = 'unrelated-event'
        self.assertTrue(validate_report(report))

    def test_nickname_requires_sdk_pointer_and_independent_refresh_event(self):
        for key, value in [('nicknamePointerPreserved', False), ('sdkRoleName', 'invented')]:
            report = copy.deepcopy(self.report); report['results'][0][key] = value
            self.assertTrue(validate_report(report), key)
        report = copy.deepcopy(self.report); report['nicknameEvent']['value'] = 841
        self.assertTrue(validate_report(report))

    def test_property_id_comes_from_child_and_only_int64_branch_is_consumed(self):
        cases = {c['name']: c for c in fixture_cases()}
        self.assertIn('0807', cases['level-different-map-key']['wire'].hex())
        report = copy.deepcopy(self.report)
        report['results'][0]['properties'][0]['type'] = 7
        self.assertTrue(validate_report(report))
        report = copy.deepcopy(self.report)
        row = next(r for r in report['results'] if r['name'] == 'float-not-int64')
        row['properties'][0]['rawInt64'] = 2
        self.assertTrue(validate_report(report))

    def test_native_time_delay_clamp_and_overflow_must_remain_visible(self):
        for name in ('delay-max', 'delay-too-old', 'future-receipt', 'timestamp-overflow-wrap'):
            report = copy.deepcopy(self.report)
            row = next(r for r in report['results'] if r['name'] == name)
            row['adjustedMilliseconds'] += 1
            self.assertTrue(validate_report(report), name)
        report = copy.deepcopy(self.report)
        next(r for r in report['results'] if r['name'] == 'timestamp-date-range-error')['stop'] = 'success'
        self.assertTrue(validate_report(report))

    def test_unknown_raw_fields_and_independent_child_int64_are_not_named(self):
        report = copy.deepcopy(self.report); report['unresolvedTags'] = []
        self.assertTrue(validate_report(report))
        report = copy.deepcopy(self.report)
        next(r for r in report['results'] if r['name'] == 'independent-tag4-only')['properties'][0]['rawInt64'] = 987
        self.assertTrue(validate_report(report))
        report = copy.deepcopy(self.report)
        report['fieldBindings'].append(dict(typeIndex=79263, tag=5, semanticField='region_id', status='verified'))
        self.assertTrue(validate_report(report))

    def test_property_arithmetic_and_real_business_methods_cannot_be_replaced(self):
        report = copy.deepcopy(self.report)
        next(r for r in report['results'] if r['name'] == 'scaled-property')['properties'][0]['storedDouble'] = 123456789
        self.assertTrue(validate_report(report))
        for entry in ('0x1507bc0e0', '0x152f7f670', '0x152f7ed50', '0x152178db0'):
            report = copy.deepcopy(self.report); report['results'][0]['models'].append(entry)
            self.assertTrue(validate_report(report), entry)


if __name__ == '__main__':
    unittest.main()
