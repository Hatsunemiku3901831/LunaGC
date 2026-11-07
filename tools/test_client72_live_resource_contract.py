import json
import os
import unittest
from pathlib import Path


class LiveResourceContractTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        path = Path(os.environ.get('CLIENT72_LIVE_RESOURCE_REPORT', 'local/migration72/d02-active/avatar/live-resources/natural-first.json'))
        cls.report = json.loads(path.read_bytes())
        cls.native = {r['name']: r for r in cls.report['native']}

    def test_natural_sample_is_hash_bound_and_all_seven_caches_uncreated(self):
        s = self.report['snapshot']
        self.assertEqual(s['sampleSHA256'], 'c5e6b05778f6326891065f120b966f4a8135b8601f097c30f6477724066a765d')
        self.assertEqual(s['readCount'], 93)
        self.assertTrue(s['allReadHashesValid'])
        self.assertTrue(s['rootNonNull'])
        self.assertEqual(set(s['caches'].values()), {'not-created-at-this-observation'})
        self.assertEqual(len(s['caches']), 7)
        self.assertFalse(s['actualResourceRowCaptured'])

    def test_weapon_identity_has_literal_and_actual_static_offset(self):
        row = next(t for t in self.report['static']['tables'] if t['name'] == 'Weapon')
        self.assertEqual((row['owner'], row['rowType'], row['staticOffset'], row['query']), (71245, 87097, '0x34900', '0x154177600'))
        self.assertEqual(row['pathLiteral']['value'], 'Data/_ExcelBinOutput/WeaponExcelConfigData')
        self.assertEqual((row['pathLiteral']['index'], row['pathLiteral']['cell']), (80414, '0x145b66f10'))
        self.assertEqual(row['queryKeys'], [11101])

    def test_nonzero_and_full_high_bit_key_preserve_full_value(self):
        for name in ('nonzero-hit', 'high-bit-key-hit'):
            row = self.native[name]
            self.assertEqual(row['returnedIndex'], 0)
            self.assertEqual(row['value'], 0xfedcba9876543210)
        self.assertEqual(self.native['same-hash-wrong-key-miss']['returnedIndex'], 0xffffffff)

    def test_collision_is_traversed_and_empty_is_distinct(self):
        row = self.native['collision-second-hit']
        self.assertEqual(row['returnedIndex'], 1)
        self.assertEqual(row['trace']['executedAddresses']['0x1578b67e8'], 2)
        self.assertEqual(self.native['empty-bucket-miss']['returnedIndex'], 0xffffffff)

    def test_cycle_and_unknown_comparer_never_become_miss(self):
        self.assertEqual(self.native['cycle-rejected']['trace']['stop'], '0x159ca53c0')
        row = self.native['custom-comparer-unresolved']['trace']
        self.assertEqual(row['stop'], '0x1578b671a')
        self.assertNotIn('0x1578b6842', row['executedAddresses'])

    def test_control_decoder_uses_independent_quadrant_formula(self):
        for raw in (0, 0x12345678, 0x80000000, 0xffffffff):
            row = self.native['decode-' + hex(raw)]
            x = raw ^ 0xa1b2c3d4
            value = 0
            for q in range(4):
                value = (value << 8) | (((x >> (24 - 8 * q) & 255) * 7 + q * 37 + 11) & 255)
            self.assertEqual(row['actual'], value)

    def test_actual_table_and_seed_natively_decode_only_controlled_fields(self):
        s = self.report['snapshot']
        self.assertEqual((s['decodeArrayLength'], s['seed']), (2048, 0x3b0a002e))
        self.assertEqual(s['inverseWindowSHA256'], '8717ded77f717cf44988dee2720b99bbd60c751426b130f6166a2ffc379a57f6')
        self.assertTrue(s['allFourQuartersArePermutations'])
        for raw, expected in ((0, 3565698174), (0x12345678, 2454901053), (0x80000000, 3716693118), (0xffffffff, 3482244539)):
            row = self.native['natural-decoder-controlled-field-' + hex(raw)]
            self.assertEqual(row['actual'], expected)
            self.assertFalse(row['actualResourceField'])

    def test_native_code_has_no_success_models_and_scope_stays_closed(self):
        self.assertEqual(len(self.native), 17)
        self.assertTrue(all(not r['trace']['models'] for r in self.native.values()))
        for key in ('actualResourceRevisionVerified', 'completeAvatarHandler', 'targetProtocolReady'):
            self.assertFalse(self.report[key])

    def test_truncated_and_null_decode_inputs_are_rejected(self):
        for name, stop in (('counterfactual-truncated-decoder', '0x1404c2650'), ('counterfactual-null-decoder', '0x1404c2be0')):
            row = self.native[name]
            self.assertEqual(row['trace']['stop'], stop)
            self.assertFalse(row['actualNaturalFailure'])


if __name__ == '__main__':
    unittest.main()
