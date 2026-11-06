"""Independent mask, resource-selection and exact factory binding assertions."""
import hashlib
import json
import os
from pathlib import Path
import unittest

from tools.client72_avatar_deep_suffix import EVIDENCE_SHA
from tools.extract_client72 import HASHES


class AvatarDeepSuffixTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.root = Path(__file__).resolve().parents[1]
        path = Path(os.environ.get('CLIENT72_AVATAR_DEEP_SUFFIX_OBSERVATIONS',
            cls.root / 'protocol/cn-7.2/avatar-deep-suffix-observations.json'))
        cls.report = json.loads(path.read_text('utf8'))
        cls.rows = {row['name']: row for row in cls.report['native']}

    def test_fixed_inputs_and_frozen_batch_dependency(self):
        self.assertEqual(HASHES, self.report['inputSHA256'])
        self.assertEqual(EVIDENCE_SHA, self.report['inputContracts'])
        for name, digest in EVIDENCE_SHA.items():
            self.assertEqual(digest, hashlib.sha256((self.root / 'protocol/cn-7.2' / name).read_bytes()).hexdigest())
        for name, digest in self.report['sourceSHA256'].items():
            self.assertEqual(digest, hashlib.sha256((self.root / name).read_bytes()).hexdigest())
        self.assertEqual(17, len(self.rows))
        for flag in ('completeConstructor', 'completeAvatarHandler', 'targetProtocolReady'):
            self.assertIs(False, self.report[flag])
        self.assertEqual('not-tested', self.report['runtimeStatus'])

    def test_exact_weapon_and_reliquary_factory_types_differ(self):
        rows = {row['role']: row for row in self.report['factoryCallsites']}
        for role, source, model, spec, cell in (
            ('weapon-input-branch', 29353, 51522, 23017, '0x145994388'),
            ('reliquary-input-branch', 52700, 87122, 77834, '0x145a30858')):
            row = rows[role]
            self.assertEqual(source, row['sourceParameter']['type']['typeDefinition'])
            self.assertEqual(model, row['binding']['arguments'][0]['typeDefinition'])
            self.assertEqual(294224, row['binding']['methodDefinition'])
            self.assertEqual(spec, row['binding']['methodSpec'])
            self.assertEqual(cell, row['binding']['cell'])
        self.assertEqual('0x15184a683', rows['weapon-input-branch']['factoryCall'])
        self.assertEqual('0x15184a702', rows['reliquary-input-branch']['factoryCall'])

    def test_native_static_list_fragment_and_target_symbols(self):
        row = self.rows['tag29-cctor-six-store-fragment']
        self.assertEqual([2, 5, 6, 7, 8, 16], row['values'])
        self.assertTrue(row['staticPointerStored'])
        self.assertFalse(row['completeStaticConstructor'])
        self.assertEqual('0x15184dcf9', row['trace']['entry'])
        self.assertEqual('0x15184dedc', row['trace']['stop'])
        self.assertEqual([], row['trace']['models'])
        symbols = {item['value']: item['symbol'] for item in self.report['enum20060']['values']}
        self.assertEqual(['Fishing', 'PlayLyre', 'PlayQin', 'PlayDrum', 'PlayFrenchHorn', 'PlayNatlanKeyBoard'],
            [symbols[value] for value in row['values']])

    def test_mask_uses_index_not_enum_numeric_bit(self):
        keys = [2, 5, 6, 7, 8, 16]
        for mask in (0, 1, 2, 32, 63, 64, 0x80000000, 0xffffffff):
            row = self.rows['tag29-mask-' + str(mask)]
            self.assertEqual(keys, [pair['key'] for pair in row['pairs']])
            self.assertEqual([bool(mask & (1 << index)) for index in range(6)], [pair['value'] for pair in row['pairs']])
            self.assertFalse(row['insertionExecuted'])
            self.assertFalse(row['effectsExecuted'])
            for pair in row['pairs']:
                self.assertEqual('0x151840ff9', pair['trace']['stop'])
                self.assertEqual([], pair['trace']['models'])
        self.assertEqual([True, False, False, False, False, False],
            [pair['value'] for pair in self.rows['tag29-mask-1']['pairs']])

    def test_high_bits_do_not_select_any_of_six_modes(self):
        for mask in (64, 0x80000000):
            self.assertEqual([False] * 6, [pair['value'] for pair in self.rows['tag29-mask-' + str(mask)]['pairs']])
        self.assertEqual([True] * 6, [pair['value'] for pair in self.rows['tag29-mask-4294967295']['pairs']])

    def test_helper_clears_before_unexecuted_insert(self):
        row = self.rows['tag29-helper-clears-before-first-insert']
        self.assertEqual(0, row['count'])
        self.assertEqual([{'width': 4, 'index': 0, 'count': 4}, {'width': 16, 'index': 0, 'count': 3}], row['clears'])
        self.assertEqual('0x151840ff9', row['trace']['stop'])
        self.assertNotIn('0x15582ba40', row['trace']['models'])

    def test_tag38_omission_and_unmatched_use_row_defaults(self):
        for name in ('omitted', 'unmatched', 'zero'):
            self.assertEqual([501, 502], self.rows['tag38-' + name]['output'])
        self.assertEqual([], self.rows['tag38-omitted']['inputs'])
        self.assertEqual([0], self.rows['tag38-zero']['inputs'])

    def test_tag38_resource_group_order_and_first_match(self):
        self.assertEqual([7002, 7001], self.rows['tag38-two-matches']['output'])
        self.assertEqual([7003, 7001], self.rows['tag38-first-match']['output'])
        self.assertEqual([0xffffffff, 502], self.rows['tag38-high-id']['output'])
        self.assertIn({'cache': '0x1cd98', 'key': 0xffffffff}, self.rows['tag38-high-id']['queries'])

    def test_tag38_actual_complete_consumer_and_native_queries(self):
        for name in ('omitted', 'two-matches', 'first-match', 'unmatched', 'zero', 'high-id'):
            row = self.rows['tag38-' + name]
            self.assertEqual('0x15184fe30', row['trace']['entry'])
            self.assertEqual('0x620000100', row['trace']['stop'])
            native = {'0x15184fe30', '0x15437ada0', '0x15184d840'}
            if name != 'omitted':
                native.add('0x1473673b0')
            self.assertTrue(native <= set(row['trace']['nativeEntries']))
            self.assertFalse(native & set(row['trace']['models']))
            self.assertIn({'cache': '0x13b78', 'key': 1001}, row['queries'])

    def test_empty_primary_cache_clears_old_output(self):
        row = self.rows['tag38-primary-empty-clears-old']
        self.assertEqual(0, row['count'])
        self.assertEqual('0x620000100', row['trace']['stop'])
        self.assertIn('0x15437ada0', row['trace']['nativeEntries'])

    def test_constructor_resolver_gap_is_still_explicit(self):
        self.assertIn('0x140524010', self.report['functions'])
        self.assertIn('0x1404ee6f0', self.report['functions'])
        self.assertTrue(any('class+68' in text and 'context+8' in text for text in self.report['unresolved']))
        self.assertFalse(any('0x140524010' in row.get('trace', {}).get('nativeEntries', []) for row in self.rows.values()))


if __name__ == '__main__':
    unittest.main()
