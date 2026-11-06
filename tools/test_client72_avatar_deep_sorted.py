"""Independent exact comparer/tree binding and constructor-effect assertions."""
import hashlib
import json
import os
from pathlib import Path
import unittest

from tools.client72_avatar_deep_sorted import INPUTS, ENTRIES
from tools.extract_client72 import HASHES


class AvatarSortedConstructorTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.root = Path(__file__).resolve().parents[1]
        cls.report = json.loads(Path(os.environ.get('CLIENT72_AVATAR_SORTED_OBSERVATIONS',
            cls.root / 'protocol/cn-7.2/avatar-deep-sorted-observations.json')).read_text('utf8'))
        cls.rows = {row['name']: row for row in cls.report['native']}

    def test_hashes_and_limited_completion(self):
        self.assertEqual(HASHES, self.report['inputSHA256'])
        self.assertEqual(INPUTS, self.report['inputContracts'])
        for name, digest in self.report['sourceSHA256'].items():
            self.assertEqual(digest, hashlib.sha256((self.root / name).read_bytes()).hexdigest())
        self.assertEqual(7, len(self.rows))
        self.assertTrue(self.report['completeSelectedSortedConstructor'])
        for flag in ('completeConstructor', 'completeAvatarHandler', 'targetProtocolReady'):
            self.assertFalse(self.report[flag])
        self.assertEqual('not-tested', self.report['runtimeStatus'])

    def test_exact_context_method_edges(self):
        rows = {(row['owner'], row['slot']): row for row in self.report['declarations']['contexts']}
        self.assertEqual(14879, rows[2034, 3]['methodDefinition'])
        self.assertEqual(14886, rows[2034, 5]['methodDefinition'])
        self.assertEqual(10271, rows[2046, 0]['methodDefinition'])
        self.assertEqual(14994, rows[2049, 1]['methodDefinition'])
        self.assertEqual(2046, rows[2034, 2]['type']['typeDefinition'])
        self.assertEqual(2049, rows[2034, 4]['type']['typeDefinition'])
        self.assertEqual(1217, rows[2034, 4]['type']['arguments'][0]['typeDefinition'])

    def test_actual_types_preserve_original_reference_argument(self):
        declared = self.report['declarations']
        self.assertEqual([32758, 51041], [arg['typeDefinition'] for arg in declared['actualArguments']])
        self.assertEqual(('System', 'ValueType'), (declared['keyTypeParent']['namespace'], declared['keyTypeParent']['name']))
        for row in declared['contexts']:
            if row['owner'] == 2034 and row['slot'] == 2:
                self.assertEqual([32758, 51041], [arg['typeDefinition'] for arg in row['type']['arguments']])
        for mode in ('fresh', 'old'):
            self.assertEqual([32758, 51041], [arg['typeDefinition'] for arg in self.rows['avatar-sorted-fragment-' + mode]['actualRootArguments']])

    def test_five_registered_constructor_bodies(self):
        self.assertEqual({14780:'0x1570f65b0', 14879:'0x158878110', 10271:'0x1560f5700', 14886:'0x157601a80', 14994:'0x156381bf0'},
            {row['methodDefinition']:row['address'] for row in self.report['declarations']['canonicalAOT']})
        self.assertEqual(1230, self.report['declarations']['comparer']['type']['typeDefinition'])
        self.assertEqual(32758, self.report['declarations']['comparer']['type']['arguments'][0]['typeDefinition'])

    def test_supplied_and_cached_comparer_complete_native_chain(self):
        for mode in ('supplied', 'warm-default'):
            row = self.rows['sorted-ctor-' + mode]
            self.assertEqual((2049, 2046), (row['treeType'], row['pairType']))
            self.assertTrue(row['comparerPreserved'])
            self.assertEqual('0x620000100', row['trace']['stop'])
            self.assertTrue(set(map(hex, ENTRIES)) <= set(row['trace']['nativeEntries']))
            self.assertEqual(['0x1405336c0'] * 2, row['trace']['models'])
            self.assertFalse(set(map(hex, ENTRIES)) & set(row['trace']['models']))

    def test_avatar_middle_stores_real_sorted_object_for_fresh_and_old(self):
        for mode in ('fresh', 'old'):
            row = self.rows['avatar-sorted-fragment-' + mode]
            self.assertEqual((2034, 2049, 2046), (row['rootType'], row['treeType'], row['pairType']))
            self.assertTrue(row['comparerPreserved'])
            self.assertEqual(('0x15183ff1a', '0x15183ff7a'), (row['trace']['entry'], row['trace']['stop']))
            self.assertEqual(['0x1405336c0'] * 4, row['trace']['models'])

    def test_cold_cache_and_missing_context_do_not_fake_success(self):
        for name, stop in (('cold-comparer-before-reflection', '0x158878154'),
                           ('missing-tree-base-context-before-resolver', '0x157601ac3')):
            row = self.rows[name]
            self.assertTrue(row['rootUnwritten'])
            self.assertEqual(stop, row['trace']['stop'])
            self.assertNotIn('0x140524010', row['trace']['models'])
            self.assertNotIn('0x140517680', row['trace']['models'])
        row = self.rows['null-root-before-native-error-helper']
        self.assertEqual('0x1570f6675', row['trace']['stop'])
        self.assertEqual([], row['trace']['models'])


if __name__ == '__main__':
    unittest.main()
