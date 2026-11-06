import json
import os
from pathlib import Path
from types import SimpleNamespace
import unittest
from tools.extract_client72 import HASHES
from tools.trace_client72_avatar_complex_outer import ROOT, TYPES, verify


class AvatarComplexOuterRawTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        root = Path(__file__).resolve().parents[1]
        path = Path(os.environ.get('CLIENT72_AVATAR_COMPLEX_OUTER_OBSERVATIONS', root / 'protocol/cn-7.2/avatar-complex-outer-observations.json'))
        cls.report = json.loads(path.read_text('utf8')); cls.cases = {x['name']: x for x in cls.report['fixtures']}
        cls.messages = {x['typeIndex']: x for x in cls.report['messages']}

    def test_fixed_three_inputs_and_partial_scope(self):
        self.assertEqual(HASHES, self.report['inputSHA256'])
        self.assertEqual('F-03d3e1', self.report['task'])
        self.assertEqual(TYPES, set(self.messages))
        self.assertEqual(18, sum(len(m['fields']) for m in self.messages.values()))
        self.assertEqual(18, self.report['dependencyScope']['metadataStorageFields'])
        self.assertEqual(384, self.report['scan']['nativeFixtures'])
        self.assertEqual([34045], self.report['blockedTypeIndices'])
        self.assertFalse(self.report['fullComplexRawClosure'])
        self.assertFalse(self.report['fullAvatarRawClosure'])
        self.assertFalse(self.report['targetProtocolReady'])
        self.assertEqual('unresolved', self.report['semanticIdentityStatus'])

    def test_independent_string_uint32_aot_does_not_reuse_signed_map(self):
        aot = self.report['aotBindings']; self.assertEqual(7, len(aot))
        for instance, key in [(3472, 'string'), (3473, 'object')]:
            row = next(x for x in aot if (x['owner'], x['classInstance'], x['method']) == (17961, instance, 'FNNADLCNGFO'))
            self.assertEqual('0x154d585c0', row['address'])
            self.assertEqual([key, 'uint32'], [x['kind'] for x in row['arguments']])
        for owner, method, address in [(17961, 'set_Item', '0x154d5a490'), (17964, 'MOMMACBEMEK', '0x155f989e0'), (17964, '.ctor', '0x155f98970')]:
            self.assertEqual(address, next(x for x in aot if (x['owner'], x['classInstance'], x['method']) == (owner, 3473, method))['address'])
        self.assertEqual((16, 24), (self.report['measuredStringMapLayout']['keyNodeOffset'], self.report['measuredStringMapLayout']['uint32ValueNodeOffset']))
        row = self.cases['64132-three-nonzero-fields']
        entries = {x['entry'] for x in row['entered'] if x.get('kind') == 'native-string-u32-map'}
        self.assertTrue({'0x154d585c0', '0x154d5a490', '0x155f989e0', '0x155f98970'} <= entries)

    def test_unsigned_upper_unicode_nul_and_native_duplicate_store(self):
        self.assertEqual({'JMPEPGDDKPD': {'A': 4294967295}}, self.cases['string-map-value-upper-is-unsigned']['result'])
        self.assertEqual({'JMPEPGDDKPD': {'中🌙\0': 11}}, self.cases['string-map-unicode-emoji-nul-key']['result'])
        row = self.cases['string-map-same-key-duplicate-zero']
        self.assertEqual({'JMPEPGDDKPD': {'A': 0}}, row['result'])
        self.assertTrue(any(x['source'] == '0x154d5a5e1' and x['nodeOffset'] == 24 and x['size'] == 4 for x in row['mapStores']))
        self.assertEqual({'JMPEPGDDKPD': {'B': 12}}, self.cases['string-map-entry-duplicates-and-unknown']['result'])

    def test_map_default_key_value_and_zero_are_preserved(self):
        for name, result in [('string-map-empty-key-upper-value', {'': 4294967295}), ('string-map-key-only', {'A': 0}),
                             ('string-map-value-only', {'': 11}), ('string-map-empty-entry', {'': 0})]:
            self.assertEqual({'JMPEPGDDKPD': result}, self.cases[name]['result'])
        self.assertEqual({'GDLHHFGKOGO': {'JMPEPGDDKPD': {'A': 0, 'B': 11}}}, self.cases['53824-nested-string-map-merge-and-zero']['result'])
        self.assertEqual({'JMGKMMFNABP': {'7': 0}}, self.cases['53824-u32-map-duplicate-zero']['result'])

    def test_all_12_outer_fields_and_both_nonempty_leaves(self):
        row = self.cases['53824-all-12-fields-nonempty-leaves']; result = row['result']
        self.assertEqual({f['clientField'] for f in self.messages[53824]['fields']}, set(result))
        self.assertEqual(12, len(result))
        self.assertEqual({'JMPEPGDDKPD': {'A': 300}, 'BGNAFEDFIHF': 22, 'HBBLAFDHAOL': True}, result['GDLHHFGKOGO'])
        self.assertEqual([{'BAHECKLMKGJ': 11, 'HDKEBNDMFLF': 22, 'ELPEMEBJKNO': 33}, {'BAHECKLMKGJ': 44, 'HDKEBNDMFLF': 55, 'ELPEMEBJKNO': 66}], result['LOLPFGGFGLH'])
        self.assertEqual(9007199254740993, result['FBLKPHOCBIK'])
        self.assertEqual({'untracedType': 34045, 'content': 'empty-envelope-only'}, result['KDKAEHEEMMJ'])
        self.assertEqual(18446744073709551615, self.cases['53824-upper-u64-and-u32']['result']['FBLKPHOCBIK'])

    def test_avatar_info_composition_preserves_existing_contexts_and_actual_class_codec_check(self):
        row = self.cases['AvatarInfo-all-37-fields-with-complex-outer']; result = row['result']
        self.assertEqual(ROOT, row['typeIndex']); self.assertEqual(37, len(result))
        self.assertEqual({}, result['AMLLHJJIMNI'][0])
        self.assertEqual(12, len(result['AMLLHJJIMNI'][1]))
        self.assertEqual({'7': -1.25}, result['ADJINCFKMML'])
        self.assertEqual(9, len(result['JCKNHCDJFCE']['CLFDBPJOFLI']))
        self.assertTrue(TYPES <= {x['typeIndex'] for x in row['entered'] if 'typeIndex' in x})

    def test_rejections_and_explicit_zero_are_honest_about_model_boundary(self):
        rejected = self.report['rejections']; self.assertEqual(9, len(rejected))
        self.assertEqual(1, sum(x['scope'] == 'explicit-scope-refusal' for x in rejected))
        self.assertEqual(4, sum(x['scope'] == 'explicit-input-model' for x in rejected))
        self.assertEqual(4, sum(x['scope'] == 'native-error-entry' for x in rejected))
        self.assertEqual({}, self.cases['53824-duplicate-zero-and-wrong-wire']['result'])
        self.assertEqual({'BGNAFEDFIHF': 4294967295}, self.cases['64132-upper-scalar-bool-normalization-and-zero']['result'])
        self.assertTrue(all(f['semanticStatus'] == 'unresolved' for m in self.messages.values() for f in m['fields']))

    def test_result_drift_rejected(self):
        with self.assertRaisesRegex(ValueError, 'complex outer native result differs'):
            verify(SimpleNamespace(run_graph=lambda *args: {'result': {'invented': True}}))


if __name__ == '__main__': unittest.main()
