import json
import os
import unittest
from pathlib import Path


class ColdCompletionTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        path = Path(os.environ.get('CLIENT72_COLD_COMPLETION_REPORT', 'local/migration72/d02-active/avatar/cold-completion/deep-first.json'))
        cls.report = json.loads(path.read_bytes())
        cls.native = {r['name']: r for r in cls.report['native']}

    def test_provider4_raw_hashes_and_types(self):
        s = self.report['snapshot']
        self.assertEqual(s['sampleSHA256'], '5a233bd0048f38f449e890268510e00d74870c770f58a416a82ceffe9faf0b9b')
        self.assertEqual(s['readCount'], 57)
        self.assertTrue(s['allReadHashesValid'])
        self.assertEqual((s['enumClassType'], s['underlyingClassType'], s['underlyingKind']), (20060, 251, 8))
        self.assertEqual((s['runtimeTypeObjectClass'], s['runtimeTypeObjectClassKind']), (284, 18))

    def test_actual_flags_and_offsets_are_preserved(self):
        s = self.report['snapshot']
        self.assertEqual(s['actualEnumFlagsCA'], 0x6c)
        self.assertEqual(s['actualUnderlyingOffset'], 0x1017928)
        self.assertTrue(all(v & 1 for v in s['initializedBits'].values()))

    def test_native_underlying_and_canonical_not_conflated(self):
        for name, typ, kind in (('natural-enum-underlying', 251, 8), ('natural-enum-canonical', 351, 17), ('natural-int32-unchanged', 251, 8)):
            row = self.native[name]
            self.assertTrue(row['expectedPointerReturned'])
            self.assertEqual((row['returnedType'], row['returnedKind']), (typ, kind))
            self.assertEqual(row['trace']['stop'], '0x620000100')
        self.assertIn('0x14052de91', self.native['natural-enum-canonical']['trace']['executedAddresses'])

    def test_byref_counterfactual_preserves_identity(self):
        self.assertTrue(self.native['counterfactual-byref-retains-descriptor']['originalPointerRetained'])

    def test_helper_stops_before_unsampled_class_cell(self):
        row = self.native['natural-runtime-type-helper-before-unobserved-class-cell']
        self.assertEqual((row['actualRuntimeTypeClass'], row['actualWrappedType']), (284, 20060))
        self.assertEqual(row['trace']['stop'], '0x159b6e72e')
        self.assertNotIn('0x159b6e735', row['trace']['executedAddresses'])

    def test_no_default_comparer_or_formal_protocol_claim(self):
        s = self.report['snapshot']
        self.assertFalse(s['defaultComparerRead'])
        self.assertEqual((s['getterSlot'], s['constructorSlot']), (0, 0))
        self.assertTrue(all(not r['trace']['models'] for r in self.native.values()))
        for key in ('completeColdComparer', 'naturalAvatarConstructorObserved', 'completeAvatarHandler', 'targetProtocolReady'):
            self.assertFalse(self.report[key])

    def test_typecode_is_produced_by_native_enum_underlying_path(self):
        row = self.native['natural-enum-typecode-internal']
        self.assertEqual(row['typeCode'], 9)
        self.assertIn('0x1404e0faf', row['trace']['executedAddresses'])
        self.assertEqual(self.native['natural-int32-typecode-internal']['typeCode'], 9)
        self.assertEqual(self.native['counterfactual-byref-typecode-internal']['typeCode'], 1)
        self.assertFalse(row['naturalVirtualDispatch'])

    def test_native_typecode_selects_exact_definition_with_declared_virtual_slot(self):
        declared = self.report['declarations']
        self.assertEqual(declared['expectedRuntimeTypeCell']['type']['typeDefinition'], 284)
        self.assertEqual(declared['typeCodeVirtualMethod']['index'], 2790)
        self.assertEqual(declared['typeCodeVirtualMethod']['virtualSlot'], 21)
        self.assertEqual(declared['typeCodeVtableOffset'], '0x178')
        row = self.native['natural-typecode-fed-to-selector']
        self.assertEqual((row['typeCode'], row['selectedType']), (9, 1239))
        self.assertEqual(row['trace']['stop'], '0x159b6e9a2')
        self.assertFalse(row['continuousHelperExecution'])


if __name__ == '__main__':
    unittest.main()
