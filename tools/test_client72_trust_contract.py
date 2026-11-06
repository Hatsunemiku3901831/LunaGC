import copy
import json
from pathlib import Path
import unittest

from tools.trace_client72_trust_contract import validate_summary


class NativeTrustContractTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.report = json.loads((Path(__file__).resolve().parents[1] /
                                 'protocol/cn-7.2/trust-contract-observations.json').read_text('utf8'))

    def test_real_native_supply_results_match_all_three_crypto_operations(self):
        self.assertEqual(validate_summary(self.report), [])
        rows = self.report['results']
        self.assertEqual({check['operation'] for row in rows for check in row['keyChecks']}, {100, 101, 103})
        for kind in ('seed', 'response', 'dispatch'):
            self.assertTrue(any(row['kind'] == kind and row['accepted'] for row in rows))

    def test_key_trust_gate_precedes_a_mathematically_valid_body_signature(self):
        row = next(row for row in self.report['results'] if row['name'] == 'response-bad-public-signature')
        self.assertTrue(row['bodySignatureIntegerOracle'])
        self.assertFalse(row['keyChecks'][-1]['native'])
        self.assertFalse(row['bodySignatureAccepted'])
        self.assertIsNone(row['sessionKeySHA256'])

    def test_a_trusted_but_wrong_server_key_does_not_accept_the_server_body(self):
        row = next(row for row in self.report['results'] if row['name'] == 'response-wrong-server-key')
        self.assertTrue(all(check['native'] and check['oracle'] for check in row['keyChecks']))
        self.assertFalse(row['bodySignatureIntegerOracle'])
        self.assertFalse(row['accepted'])
        self.assertIsNone(row['sessionKeySHA256'])

    def test_unknown_key_receives_no_local_key_signature_or_root(self):
        row = next(row for row in self.report['results'] if row['name'] == 'seed-unknown-key')
        event = row['events'][0]
        self.assertFalse(event['sourceRecognized'])
        self.assertFalse(event['supplied'])
        self.assertEqual(event['copyChangedRegisters'], [])
        self.assertEqual(event['checkChangedRegisters'], [])
        self.assertEqual(event['checkChangedStackArguments'], [])
        self.assertEqual((row['ioctlReturn'], row['base64Length']), (-7, 0))

    def test_return_override_and_live_claims_are_rejected(self):
        for key, value in [('runtimeStatus', 'passed'), ('hookInstalled', True),
                           ('verificationForced', True), ('targetPrivateKeyExported', True), ('inputSHA256', {})]:
            with self.subTest(key=key):
                report = copy.deepcopy(self.report)
                report[key] = value
                self.assertTrue(validate_summary(report))
        for key, value in [('copyChangedRegisters', ['rax']), ('checkChangedRegisters', ['r8', 'rax']),
                           ('checkChangedStackArguments', [5, 6]), ('returnRegistersAndFlagsUntouched', False),
                           ('rawLength', 270), ('allocationLength', 268)]:
            with self.subTest(key=key):
                report = copy.deepcopy(self.report)
                report['results'][0]['events'][0][key] = value
                self.assertTrue(validate_summary(report))

    def test_native_oracle_or_acceptance_results_cannot_be_promoted(self):
        for case in ('seed-wrong-root', 'response-bad-private-signature', 'dispatch-bad-body-signature'):
            report = copy.deepcopy(self.report)
            row = next(row for row in report['results'] if row['name'] == case)
            row['accepted'] = True
            self.assertTrue(validate_summary(report))
        report = copy.deepcopy(self.report)
        report['results'][0]['keyChecks'][0]['oracle'] = False
        self.assertTrue(validate_summary(report))


if __name__ == '__main__':
    unittest.main()
