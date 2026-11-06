"""Provenance and scope regression for the bounded response contract."""
import copy
import json
from pathlib import Path
import unittest

from tools.client72_response_contract import validate_report


class ResponseContractTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.report = json.loads((Path(__file__).resolve().parents[1] /
            'protocol/cn-7.2/response-contract-observations.json').read_text('utf8'))

    def test_native_report(self):
        self.assertEqual(validate_report(self.report), [])

    def test_upstream_name_cannot_replace_named_target(self):
        report = copy.deepcopy(self.report)
        report['fieldBindings'][1]['source']['field'] = 'SILENCE_DATA_MD5'
        self.assertTrue(validate_report(report))

    def test_wrong_wire_tag_rejected(self):
        report = copy.deepcopy(self.report)
        report['fieldBindings'][1]['tag'] = 4
        self.assertTrue(validate_report(report))

    def test_no_live_acceptance_promotion(self):
        report = copy.deepcopy(self.report)
        report['wholeHandlerExecuted'] = True
        report['runtimeStatus'] = 'passed'
        self.assertTrue(validate_report(report))

    def test_duplicate_and_boundary_evidence_required(self):
        for name in ('uint32-max', 'duplicate-resource-configs', 'missing-resource-configs'):
            report = copy.deepcopy(self.report)
            report['fixtures'] = [f for f in report['fixtures'] if f['name'] != name]
            self.assertTrue(validate_report(report))

    def test_hash_mismatch_rejected(self):
        report = copy.deepcopy(self.report)
        report['inputSHA256']['YuanShen.exe'] = '0' * 64
        self.assertTrue(validate_report(report))


if __name__ == '__main__':
    unittest.main()
