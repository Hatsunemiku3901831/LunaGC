"""Independent assertions for freshly generated cold Avatar native evidence."""
import json
import os
import unittest
from pathlib import Path


class AvatarColdContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.report = json.loads(Path(os.environ.get('CLIENT72_AVATAR_COLD_REPORT',
            'local/migration72/d02-active/avatar/cold-final-first.json')).read_text('utf8'))
        cls.rows = {row['name']: row for row in cls.report['native']}

    def test_exact_rgctx_retains_enum20060(self):
        d = self.report['declarations']
        self.assertEqual(20060, d['targetType']['typeDefinition'])
        slots = d['equalityContext']
        self.assertEqual([2, 1, 2], [row['kind'] for row in slots])
        self.assertEqual(['0x806d795', '0x406d794', '0x806d794'], [row['word'] for row in slots])
        self.assertEqual(1234, slots[0]['type']['typeDefinition'])
        self.assertEqual(20060, slots[0]['type']['arguments'][0]['typeDefinition'])
        self.assertEqual([20060, 20060], [row['type']['typeDefinition'] for row in slots[1:]])

    def test_helper_identity_and_lock_imports_are_independent(self):
        d = self.report['declarations']
        helper = next(row for row in d['methods'] if row['method']['index'] == 10494)
        self.assertEqual('ComparerHelpers', helper['ownerName'])
        self.assertEqual('CreateDefaultEqualityComparer', helper['method']['name'])
        self.assertEqual({'AcquireSRWLockShared': '0x143bac0f8', 'ReleaseSRWLockShared': '0x143bac708'}, d['lockImports'])

    def test_cache_hits_preserve_full_64_bit_value(self):
        for name in ('cache-hit', 'cache-equivalent-type-different-pointer-attributes', 'cache-collision-hit'):
            row = self.rows[name]
            self.assertTrue(row['found'])
            self.assertEqual(0xFEDCBA9876543210, row['value'])
            self.assertEqual([], row['trace']['models'])
            self.assertEqual('0x620000100', row['trace']['stop'])

    def test_type_hash_has_independent_integer_formula(self):
        rows = self.rows['type-hash-key-semantics']['hashes']
        expected = (17 * 0x1cfaa2db * 0x1cfaa2db + 20060) & 0xffffffffffffffff
        self.assertEqual(expected, rows['enum']['value'])
        self.assertEqual(expected, rows['attributes']['value'])
        self.assertEqual((expected + 64) & 0xffffffffffffffff, rows['collision']['value'])
        self.assertEqual((expected + 0x1cfaa2db) & 0xffffffffffffffff, rows['byref']['value'])
        for row in rows.values():
            self.assertEqual([], row['trace']['models'])

    def test_collision_probes_another_entry(self):
        trace = self.rows['cache-collision-hit']['trace']
        self.assertEqual(2, trace['executedAddresses']['0x1404f0820'])
        self.assertEqual(2, trace['executedAddresses']['0x14053c209'])
        self.assertEqual(1, self.rows['cache-hit']['trace']['executedAddresses']['0x1404f0820'])

    def test_missing_and_byref_distinction_preserve_output(self):
        for name in ('cache-collision-miss', 'cache-byref-distinct-miss', 'cache-empty-miss'):
            row = self.rows[name]
            self.assertFalse(row['found'])
            self.assertEqual(0x123456789, row['value'])
            self.assertEqual([], row['trace']['models'])

    def test_cold_getter_reaches_typed_helper_with_no_comparer(self):
        row = self.rows['cold-comparer-reaches-exact-helper-input']
        self.assertTrue(row['helperArgumentIsReflection'])
        self.assertEqual((284, 20060), (row['reflectionObjectClass'], row['reflectionTypeIndex']))
        self.assertTrue(row['defaultCacheStillNull'])
        self.assertEqual('0x159b6e720', row['trace']['stop'])
        self.assertEqual(['acquire', 'release'], row['lockCalls'])
        self.assertTrue(row['lockBalanced'])
        self.assertEqual(['0x620001000', '0x620001010'], row['trace']['models'])
        self.assertTrue({'0x157d7fefa', '0x140517680', '0x15a4d6b60', '0x140538230',
                         '0x14053c1c0', '0x1404f0740', '0x1404f0820'}.issubset(row['trace']['executedAddresses']))
        self.assertNotIn('0x159b6e729', row['trace']['executedAddresses'])

    def test_reflection_cache_miss_is_not_successful_creation(self):
        row = self.rows['cold-reflection-cache-before-allocation']
        self.assertEqual('0x1405382a9', row['trace']['stop'])
        self.assertTrue(row['defaultCacheStillNull'])
        self.assertEqual(['acquire', 'release'], row['lockCalls'])
        self.assertTrue(row['lockBalanced'])
        self.assertNotIn('0x1405336c0', row['trace']['models'])
        self.assertNotIn('0x14053c330', row['trace']['executedAddresses'])

    def test_typecode_selection_has_exact_enum_variants_and_fallback(self):
        expected = {0: 1237, 5: 1240, 6: 1239, 7: 1241, 8: 1239, 9: 1239,
                    10: 1239, 11: 1242, 12: 1242, 13: 1237, 0xffffffff: 1237}
        for code, target in expected.items():
            row = self.rows['comparer-selector-' + str(code)]
            self.assertEqual(target, row['selectedType'])
            self.assertEqual([], row['trace']['models'])
        self.assertEqual('EnumEqualityComparer`1', self.rows['comparer-selector-9']['selectedName'])
        self.assertEqual('0x691330', self.rows['comparer-selector-9']['descriptorOffset'])

    def test_no_cold_constructor_or_protocol_promotion(self):
        self.assertEqual('8b014f64b5dc12fbc476e0c362b053abfdd7a1378cd282e4ad81057621dbd6b5', self.report['inputSHA256']['YuanShen.exe'])
        for name in ('completeColdComparer', 'completeConstructor', 'completeAvatarHandler', 'targetProtocolReady'):
            self.assertFalse(self.report[name])
        self.assertEqual('not-tested', self.report['runtimeStatus'])
        self.assertEqual(20, len(self.rows))


if __name__ == '__main__':
    unittest.main()
