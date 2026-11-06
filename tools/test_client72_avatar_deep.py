"""Independent assertions for new bounded Avatar consumer observations."""
import hashlib
import json
import os
from pathlib import Path
import unittest

from tools.client72_avatar_deep import EVIDENCE_SHA
from tools.extract_client72 import HASHES


class AvatarDeepTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.root = Path(__file__).resolve().parents[1]
        path = Path(os.environ.get('CLIENT72_AVATAR_DEEP_OBSERVATIONS',
            cls.root / 'protocol/cn-7.2/avatar-deep-observations.json'))
        cls.report = json.loads(path.read_text('utf8'))
        cls.rows = {row['name']: row for row in cls.report['native']}

    def test_fixed_inputs_and_no_complete_claim(self):
        self.assertEqual(HASHES, self.report['inputSHA256'])
        self.assertEqual(EVIDENCE_SHA, self.report['inputContracts'])
        self.assertEqual('F-03d3f4d2', self.report['task'])
        self.assertEqual(23, len(self.rows))
        for key in ('completeConstructor', 'completeAvatarHandler', 'targetProtocolReady'):
            self.assertIs(False, self.report[key])
        self.assertEqual('not-tested', self.report['runtimeStatus'])
        for name, digest in self.report['sourceSHA256'].items():
            self.assertEqual(digest, hashlib.sha256((self.root / name).read_bytes()).hexdigest(), name)

    def test_exact_generic_targets_remain_unbound_overloads(self):
        aot = self.report['concreteAOT']
        self.assertEqual({(2147, 5917, 15590), (2034, 12373, 14777), (69137, 5278, 572243)},
                         {(row['owner'], row['classInstance'], row['methodDefinition']) for row in aot})
        self.assertFalse({15591, 14778} & {row['methodDefinition'] for row in aot})
        parents = {row['index']: row['parent'] for row in self.report['owners']}
        self.assertEqual((69137, 5278), (parents[34054]['typeDefinition'], parents[34054]['instanceIndex']))
        self.assertEqual(63831, parents[34054]['arguments'][0]['typeDefinition'])
        self.assertEqual(1255, parents[69137]['typeDefinition'])

    def test_constructor_does_not_clear_dirty_payload(self):
        dirty = self.rows['61980-ctor-dirty']
        self.assertEqual('0100c03f7856341201000080fffffffffeffffff', dirty['after'])
        self.assertEqual(dirty['before'], dirty['after'])
        self.assertEqual('00' * 20, self.rows['61980-ctor-fresh']['after'])
        self.assertEqual('0x620000100', dirty['trace']['stop'])
        self.assertEqual([], dirty['trace']['models'])

    def test_reset_and_copy_erase_old_payload(self):
        for header in (0, 1):
            row = self.rows['61980-reset-header-' + str(header)]
            self.assertEqual('00' * 20, row['after'])
            self.assertIn('0x14a42bd90', row['trace']['nativeEntries'])
            self.assertEqual([], row['trace']['models'])
        for mode in ('zero', 'nonzero'):
            row = self.rows['61980-copy-' + mode]
            self.assertEqual(row['incoming'], row['after'])
            self.assertEqual([], row['trace']['models'])

    def test_error_boundary_stops_before_array_model(self):
        row = self.rows['61980-reset-header-2']
        self.assertEqual('0x14a42bdbe', row['trace']['stop'])
        self.assertEqual(row['before'], row['after'])
        self.assertNotIn('0x140514ee0', row['trace']['models'])
        self.assertEqual([], row['trace']['models'])

    def test_actual_pool_wrapper_retains_dirty_fields_except_handler24(self):
        fresh = self.rows['61980-handler-pool-fresh']
        dirty = self.rows['61980-handler-pool-dirty']
        self.assertEqual('00' * 20, fresh['after'])
        self.assertEqual('0100c03f785634120100008000000000feffffff', dirty['after'])
        for row in (fresh, dirty):
            self.assertEqual((10, 1), (row['header11'], row['header14']))
            self.assertEqual([61980], [request['type']['typeDefinition'] for request in row['poolRequests']])
            self.assertTrue({'0x14d4480c0', '0x14a42bd90', '0x1495c9d00', '0x1495c9d60'} <= set(row['trace']['nativeEntries']))
            self.assertFalse({'0x14d4480c0', '0x14a42bd90', '0x1495c9d00', '0x1495c9d60'} & set(row['trace']['models']))

    def test_empty_equipment_still_requests_pools_and_clears_old_list(self):
        for mode in ('fresh', 'old'):
            row = self.rows['empty-equipment-refresh-' + mode]
            self.assertEqual(0, row['clearedCount'])
            self.assertEqual([1222, 1255], [request['type']['typeDefinition'] for request in row['poolRequests']])
            self.assertEqual('0x151843490', row['trace']['stop'])
            self.assertNotIn('0x151843490', row['trace']['models'])

    def test_actual_relic_field_stores_keep_full_guids_and_zero(self):
        for level, value in ((42, 73), (0, 0), (0xffffffff, 0x80000001)):
            row = self.rows['relic-assignment-' + str(level)]
            self.assertEqual((20001, 1, level, value),
                (row['itemId'], row['slot'], row['decodedLevel'], row['decodedValue']))
            self.assertEqual(9223372036854775809, row['ownerGuid'])
            self.assertEqual(18446744073709551615, row['outputGuid'])
            self.assertTrue(row['inventoryPointerStored'])
            self.assertEqual('0x151845920', row['trace']['stop'])
            self.assertNotIn('0x15184a5f0', row['trace']['models'])

    def test_skill_prefix_clears_both_encoded_dictionaries(self):
        fresh = self.rows['skill-dictionaries-clear-fresh']
        old = self.rows['skill-dictionaries-clear-old']
        self.assertEqual([0, 0], fresh['counts'])
        self.assertEqual([], fresh['clears'])
        self.assertEqual([0, 0], old['counts'])
        self.assertEqual([{'width': 4, 'index': 0, 'count': 4}, {'width': 40, 'index': 0, 'count': 3}] * 2, old['clears'])
        self.assertEqual('0x151848096', old['trace']['stop'])

    def test_depot68_pair_is_distinct_from_6c(self):
        for name, expected in (('nonzero', (701, 42)), ('max-values', (0xffffffff, 0xffffffff))):
            row = self.rows['depot68-tag13-tail-' + name]
            self.assertEqual(expected, tuple(row['encodedArguments'][key] for key in ('key', 'value')))
            self.assertNotEqual(row['ignoredKey6c'], row['encodedArguments']['key'])
            self.assertEqual([0x80000001], row['queries'])
            self.assertEqual('0x15184946b', row['trace']['stop'])
            self.assertIs(False, row['dictionaryInsertionExecuted'])
            self.assertNotIn('0x1593cff80', row['trace']['models'])

    def test_wire_omission_explicit_zero_last_zero_and_missing_row_skip_pair(self):
        for name in ('omitted', 'explicit-zero', 'last-zero', 'zero-key', 'missing-depot'):
            row = self.rows['depot68-tag13-tail-' + name]
            self.assertIsNone(row['encodedArguments'])
            self.assertEqual('0x15184949c', row['trace']['stop'])
            self.assertIs(False, row['dictionaryInsertionExecuted'])
        for name in ('omitted', 'explicit-zero', 'last-zero'):
            self.assertEqual(0, self.rows['depot68-tag13-tail-' + name]['inputTag13'])


if __name__ == '__main__':
    unittest.main()
