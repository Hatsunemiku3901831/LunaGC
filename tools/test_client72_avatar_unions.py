import json
import os
from pathlib import Path
from types import SimpleNamespace
import unittest
from tools.extract_client72 import HASHES
from tools.trace_client72_avatar_unions import ROOT, TYPES, verify


class AvatarUnionsRawTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        root = Path(__file__).resolve().parents[1]
        path = Path(os.environ.get('CLIENT72_AVATAR_UNIONS_OBSERVATIONS', root / 'protocol/cn-7.2/avatar-unions-observations.json'))
        cls.report = json.loads(path.read_text('utf8')); cls.cases = {x['name']: x for x in cls.report['fixtures']}
        cls.messages = {x['typeIndex']: x for x in cls.report['messages']}

    def test_fixed_inputs_count_and_partial_scope(self):
        self.assertEqual(HASHES, self.report['inputSHA256'])
        self.assertEqual('F-03d3e2', self.report['task'])
        self.assertEqual(TYPES, set(self.messages))
        self.assertEqual(36, sum(len(m['fields']) for m in self.messages.values()))
        self.assertEqual(36, self.report['dependencyScope']['metadataStorageFields'])
        self.assertEqual(896, self.report['scan']['nativeFixtures'])
        self.assertEqual([51050], self.report['blockedTypeIndices'])
        self.assertFalse(self.report['fullComplexRawClosure'])
        self.assertFalse(self.report['fullAvatarRawClosure'])
        self.assertFalse(self.report['targetProtocolReady'])
        self.assertEqual('unresolved', self.report['semanticIdentityStatus'])

    def test_boxed_string_u32_source_and_discriminator_stores(self):
        self.assertEqual('0x145886170', self.report['boxedPrimitiveUsage']['cell'])
        self.assertEqual('uint32', self.report['boxedPrimitiveUsage']['type']['kind'])
        fields = {f['tag']: f for f in self.messages[85344]['fields']}
        self.assertEqual(('string', 24, 32, 1), (fields[1]['type'], fields[1]['objectOffset'], fields[1]['discriminatorOffset'], fields[1]['discriminatorValue']))
        self.assertEqual(('uint32', 24, 32, 2), (fields[2]['type'], fields[2]['objectOffset'], fields[2]['discriminatorOffset'], fields[2]['discriminatorValue']))
        self.assertEqual({'NOEEBKJAFAG_uint32': 4294967295}, self.cases['boxed-u32-upper']['result'])
        stores = self.cases['boxed-u32-explicit-zero']['stores']
        self.assertTrue(any(x['objectOffset'] == 24 and x['source'] == '0x151e51364' for x in stores))
        self.assertTrue(any(x['objectOffset'] == 32 and x['source'] == '0x151e51368' for x in stores))

    def test_union_switch_empty_zero_and_unicode_nul_presence(self):
        self.assertEqual({'NOEEBKJAFAG_string': '中🌙\0'}, self.cases['boxed-string-unicode-nul']['result'])
        self.assertEqual({'NOEEBKJAFAG_string': ''}, self.cases['boxed-string-explicit-empty']['result'])
        self.assertEqual({'NOEEBKJAFAG_uint32': 0}, self.cases['boxed-u32-explicit-zero']['result'])
        self.assertEqual({'NOEEBKJAFAG_string': ''}, self.cases['union-switch-string-u32-string-empty']['result'])
        self.assertEqual({'COECLMLNMMA': {'NOEEBKJAFAG_uint32': 7}}, self.cases['70808-repeated-union-field-switch']['result'])

    def test_float32_negative_zero_extremes_and_actual_stores(self):
        row = self.cases['80101-negative-float-negative-zero']
        self.assertEqual({'MDDPGNIHKAF': -1.25, 'EBDPHJGGAFO': -0.0}, row['result'])
        bits = {x['clientField']: x['valueBits'] for x in row['floatScalarBits']}
        self.assertEqual({'MDDPGNIHKAF': '0xbfa00000', 'EBDPHJGGAFO': '0x80000000'}, bits)
        self.assertEqual({(24, 4, '0x14ee7dc28'), (28, 4, '0x14ee7dc58')}, {(x['objectOffset'], x['size'], x['source']) for x in row['stores']})
        extremes = self.cases['80101-max-finite-min-subnormal']
        self.assertEqual({'MDDPGNIHKAF': '0x7f7fffff', 'EBDPHJGGAFO': '0x1'}, {x['clientField']: x['valueBits'] for x in extremes['floatScalarBits']})
        self.assertEqual({}, self.cases['80101-duplicate-explicit-zero']['result'])

    def test_signed_enum_same_shape_unions_and_upper_nonempty_fields(self):
        scalar = self.cases['62912-signed-enum-u32-boundaries']['result']
        self.assertEqual((-2147483648, -1, 4294967295), (scalar['DCLPPKIHMBC'], scalar['OKDKGHLCKDL'], scalar['HBNNOCOEFEE']))
        large = self.cases['51162-all-15-fields-nonempty']['result']; self.assertEqual(15, len(large))
        self.assertEqual({'NOEEBKJAFAG_string': 'A'}, large['FDCIMEECAIM'])
        self.assertEqual({'NOEEBKJAFAG_uint32': 7}, large['DNCDDOAIGAO'])
        self.assertEqual(-3.5, large['NPJOJHAIMEE'])
        upper = self.cases['34045-all-six-fields-nonempty-upper-tree']['result']; self.assertEqual(6, len(upper))
        self.assertEqual(-1.25, upper['COADAPGCGKK'][0]['GCOLEBCPBNP'])
        self.assertEqual(3.5, upper['AOGEMELMKBO'][0]['GCOLEBCPBNP'])
        self.assertEqual([{'untracedType': 51050, 'content': 'empty-envelope-only'}], upper['PPDFGNJDAAE'])

    def test_concrete_aot_list_contexts_and_avatar_info_composition(self):
        self.assertEqual(8, len(self.report['aotBindings']))
        for instance, child in [(5883, 77771), (27561, 70808), (27562, 51162), (27563, 51050)]:
            row = next(x for x in self.report['aotBindings'] if (x['classInstance'], x['method']) == (instance, 'FNNADLCNGFO'))
            self.assertEqual(('0x158dc41f0', child), (row['address'], row['arguments'][0]['typeDefinition']))
        row = self.cases['AvatarInfo-all-37-fields-with-upper-union-tree']; result = row['result']
        self.assertEqual(ROOT, row['typeIndex']); self.assertEqual(37, len(result))
        self.assertEqual(3, len(result['AMLLHJJIMNI']))
        self.assertEqual({}, result['AMLLHJJIMNI'][1]['KDKAEHEEMMJ'])
        self.assertEqual(6, len(result['AMLLHJJIMNI'][2]['KDKAEHEEMMJ']))
        self.assertEqual(9, len(result['JCKNHCDJFCE']['CLFDBPJOFLI']))
        self.assertEqual({'7': -1.25}, result['ADJINCFKMML'])
        self.assertTrue(TYPES <= {x['typeIndex'] for x in row['entered'] if 'typeIndex' in x})

    def test_append_zero_and_precisely_scoped_rejections(self):
        lists = self.cases['34045-two-same-shape-lists-append-independently']['result']
        self.assertEqual((2, 1), (len(lists['COADAPGCGKK']), len(lists['AOGEMELMKBO'])))
        self.assertEqual({}, self.cases['51162-float-bool-u32-and-int32-duplicate-zero']['result'])
        rejected = self.report['rejections']; self.assertEqual(9, len(rejected))
        self.assertEqual(1, sum(x['scope'] == 'explicit-scope-refusal' for x in rejected))
        self.assertEqual(4, sum(x['scope'] == 'explicit-input-model' for x in rejected))
        self.assertEqual(4, sum(x['scope'] == 'native-error-entry' for x in rejected))
        self.assertTrue(all(f['semanticStatus'] == 'unresolved' for m in self.messages.values() for f in m['fields']))

    def test_result_drift_rejected(self):
        with self.assertRaisesRegex(ValueError, 'union native result differs'):
            verify(SimpleNamespace(run_graph=lambda *args: {'result': {'invented': True}}))


if __name__ == '__main__': unittest.main()
