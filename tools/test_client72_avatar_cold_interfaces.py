import json
import os
import unittest
from pathlib import Path


class ColdInterfacesTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        path = Path(os.environ.get('CLIENT72_COLD_INTERFACES_REPORT', 'local/migration72/d02-active/avatar/cold-interfaces/first.json'))
        cls.report = json.loads(path.read_bytes())
        cls.rows = {r['name']: r for r in cls.report['native']}

    def test_fixed_five_inputs_and_actual_sampler(self):
        self.assertEqual(len(self.report['inputSHA256']), 5)
        self.assertEqual(self.report['snapshot']['sampleSHA256'], '5a233bd0048f38f449e890268510e00d74870c770f58a416a82ceffe9faf0b9b')
        self.assertEqual(self.report['samplerSourceSHA256'], '2dfc86a6bd9f99ae72cd380c1315ba6d5f6d3968ff892a5eb7c089f4c1fc65ab')
        self.assertEqual(self.report['snapshot']['readCount'], 57)
        self.assertTrue(self.report['snapshot']['allReadHashesValid'])
        self.assertFalse(self.report['snapshot']['defaultComparerRead'])

    def test_independent_parent_and_interface_declarations(self):
        rows = self.report['declarations']['parentChain']
        self.assertEqual([r['type'] for r in rows], [20060, 204, 405, 371])
        self.assertEqual([r['interfaceCount'] for r in rows], [0, 3, 0, 0])
        self.assertEqual(rows[1]['interfaceReferences'], [60193, 444914, 444915])
        self.assertEqual([t['typeDefinition'] for t in rows[1]['interfaces']], [241, 248, 243])
        self.assertEqual([t['name'] for t in rows[1]['interfaces']], ['IComparable', 'IFormattable', 'IConvertible'])

    def test_native_clear_and_generic_flag_producers(self):
        prepared = {r['type']: r for r in self.report['classPreparation']}
        for index in (20060, 241, 248, 243):
            row = prepared[index]
            self.assertTrue(row['clearAllD0BytesZero'])
            self.assertEqual(row['genericInstPointer'], 0)
            self.assertEqual(row['genericContainerEncoded'], '0x8709')
            self.assertFalse(row['genericDefinitionFlag'])
            self.assertIn('0x15a4d6f94', row['clear']['executedAddresses'])
            self.assertIn('0x15a4d72db', row['flag']['executedAddresses'])
            self.assertFalse(row['clear']['models'])
            self.assertFalse(row['flag']['models'])

    def test_exact_type_array_and_default_producers(self):
        d = self.report['declarations']
        self.assertEqual((d['typeArrayCell']['cell'], d['typeArrayCell']['sourceIndex']), ('0x145886658', 444046))
        self.assertEqual(d['typeArrayCell']['type']['elementType']['typeDefinition'], 314)
        self.assertEqual((d['defaultTypeProducer']['cell'], d['defaultTypeProducer']['type']), ('0x145bc97a8', 314))
        self.assertEqual((d['defaultArrayProducer']['cell'], d['defaultArrayProducer']['type']), ('0x145bc9868', 117))

    def test_complete_get_interfaces_keeps_full_pointers_and_guard(self):
        row = self.rows['get-interfaces-complete-prepared-caches']
        self.assertEqual(row['trace']['stop'], '0x620000100')
        self.assertGreater(row['result'], 0xffffffff)
        self.assertEqual((row['arrayCount'], set(row['interfaceTypes'])), (3, {241, 248, 243}))
        self.assertTrue(row['allFullPointersPreserved'])
        self.assertEqual(row['tailCanary'], 0xfedcba9876543210)
        counts = row['trace']['executedAddresses']
        self.assertEqual((counts['0x1404dd560'], counts['0x140538230']), (4, 6))
        self.assertEqual((counts['0x141a2e150'], counts['0x141cbfe48']), (4, 4))

    def test_full_has_iequatable_executes_provider_and_each_predicate(self):
        row = self.rows['has-iequatable-complete-prepared-caches']
        self.assertEqual(row['result'], 0)
        counts = row['trace']['executedAddresses']
        self.assertEqual(counts['0x1404dd170'], 1)
        self.assertEqual(counts['0x159b6ec60'], 3)
        order = [c['entry'] for c in row['trace']['callOrder']]
        self.assertLess(order.index('0x1404dd170'), order.index('0x1404e9e60'))
        self.assertLess(order.index('0x1404e9e60'), order.index('0x140514ee0'))
        self.assertLess(order.index('0x140538230'), order.index('0x159b6ec60'))
        self.assertNotIn('0x1404dd170', row['trace']['models'])

    def test_nested_duplicate_and_empty_controls(self):
        duplicate = self.rows['counterfactual-ancestor-duplicate-interface']
        empty = self.rows['counterfactual-empty-interface-parents']
        self.assertEqual((duplicate['arrayCount'], duplicate['setNodeCalls']), (3, 4))
        self.assertEqual((empty['arrayCount'], empty['setNodeCalls']), (0, 1))

    def test_null_and_generic_interface_take_distinct_failure_frontiers(self):
        self.assertEqual(self.rows['null-runtime-type-rejected']['trace']['stop'], '0x1404c2be0')
        trace = self.rows['counterfactual-generic-interface-before-definition-provider']['trace']
        self.assertEqual(trace['stop'], '0x159b6e646')
        self.assertNotIn('0x159b6e698', trace['executedAddresses'])

    def test_type_array_and_reflection_misses_stop_before_creation(self):
        row = self.rows['type-array-factory-miss-before-array-base-state']
        self.assertEqual(row['trace']['stop'], '0x1404ea15f')
        self.assertEqual(row['elementType'], 314)
        self.assertNotIn('0x140514ee0', row['trace']['executedAddresses'])
        self.assertTrue(row['lockBalanced'])
        row = self.rows['interface-runtime-type-cache-miss-before-allocation']
        self.assertEqual(row['trace']['stop'], '0x1405382a9')
        self.assertEqual(row['missedType'], 241)
        self.assertNotIn('0x1405336c0', row['trace']['executedAddresses'])
        self.assertTrue(row['lockBalanced'])

    def test_hierarchy_is_natively_produced_and_wrong_classes_rejected(self):
        row = self.rows['runtime-type-hierarchy-native-producer']
        self.assertEqual((row['depth'], row['chain']), (5, [371, 513, 314, 531, 284]))
        self.assertEqual(row['trace']['executedAddresses']['0x14051f280'], 5)
        for expected, actual, result in ((314, 284, True), (284, 314, False), (314, 371, False)):
            row = self.rows[f'hierarchy-assignment-{expected}-from-{actual}']
            self.assertEqual(row['actual'], result)
            self.assertFalse(row['completeAssignabilityFunction'])
            self.assertFalse(row['trace']['models'])

    def test_array_cache_collision_compares_twice(self):
        row = self.rows['counterfactual-array-cache-collision-hit']
        self.assertTrue(row['returnedCorrectArrayClass'])
        self.assertEqual(row['keyCompareCalls'], 2)
        self.assertTrue(row['lockBalanced'])

    def test_generic_factory_has_exact_nonzero_type_inputs_and_no_fake_write(self):
        row = self.rows['generic-comparer-factory-before-type-class-initialization-lock']
        self.assertEqual((row['definitionType'], row['argumentType'], row['requestedArrayLength']), (1239, 20060, 1))
        self.assertEqual((row['assignmentExpectedType'], row['assignmentActualType']), (314, 284))
        self.assertEqual(row['trace']['stop'], '0x14051b7f0')
        self.assertTrue(row['arrayElementStillNull'])
        self.assertNotIn('0x159b6ebb5', row['trace']['executedAddresses'])
        self.assertNotIn('0x1404e0c70', row['trace']['executedAddresses'])

    def test_locks_and_model_scope(self):
        self.assertEqual(set(self.report['modelCatalog'].values()), {'allocate_node', 'free_node', 'allocate_array', 'allocate_hierarchy', 'memset', 'memcpy', 'acquire', 'release'})
        for row in self.rows.values():
            locks = row['trace']['lockOrder']
            self.assertEqual(len(locks) % 2, 0)
            for a, b in zip(locks[::2], locks[1::2]):
                self.assertEqual((a['action'], b['action'], a['lock']), ('acquire', 'release', b['lock']))

    def test_no_natural_ready_or_whole_cold_promotion(self):
        self.assertEqual(len(self.rows), 14)
        self.assertTrue(self.report['completeGetInterfacesUnderDeclaredModels'])
        self.assertTrue(self.report['completeHasIEquatableUnderDeclaredModels'])
        for name in ('completeColdComparer', 'naturalInterfacesObserved', 'completeAvatarHandler', 'targetProtocolReady'):
            self.assertFalse(self.report[name])
        self.assertEqual(self.report['firstColdBlocker']['stop'], '0x1404ea15f')


if __name__ == '__main__':
    unittest.main()
