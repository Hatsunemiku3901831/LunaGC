import json
import os
from pathlib import Path
from types import SimpleNamespace
import unittest
from tools.extract_client72 import HASHES
from tools.trace_client72_avatar_trees import ROOT, TYPES, BLOCKED, verify


class AvatarTreesRawTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        root = Path(__file__).resolve().parents[1]
        path = Path(os.environ.get('CLIENT72_AVATAR_TREES_OBSERVATIONS', root / 'protocol/cn-7.2/avatar-trees-observations.json'))
        cls.report = json.loads(path.read_text('utf8')); cls.cases = {x['name']: x for x in cls.report['fixtures']}
        cls.messages = {x['typeIndex']: x for x in cls.report['messages']}

    def test_fixed_input_and_bounded_native_closure(self):
        self.assertEqual(HASHES, self.report['inputSHA256'])
        self.assertEqual('F-03d3c', self.report['task'])
        self.assertEqual(TYPES, set(self.messages))
        audit = self.report['dependencyAudit']
        self.assertEqual((6, 18), (audit['typeCount'], audit['instanceStorageFields']))
        self.assertEqual(TYPES, {x['typeIndex'] for x in audit['types']})
        self.assertEqual(18, sum(len(x['fields']) for x in self.messages.values()))
        self.assertEqual(768, self.report['scan']['nativeFixtures'])
        self.assertTrue(self.report['fullSelectedTreeRawClosure'])
        self.assertFalse(self.report['fullAvatarRawClosure'])
        self.assertFalse(self.report['targetProtocolReady'])
        self.assertEqual('unresolved', self.report['semanticIdentityStatus'])
        self.assertEqual(sorted(BLOCKED), self.report['blockedTypeIndices'])

    def test_same_shape_fields_distinct_offsets_and_nonzero_content(self):
        fields = {f['tag']: f for f in self.messages[27196]['fields']}
        self.assertEqual(('GONJACBHGEP', 24, 76885), (fields[2]['clientField'], fields[2]['objectOffset'], fields[2]['nestedTypeIndex']))
        self.assertEqual(('CBIONIIBDIN', 32, 76885), (fields[3]['clientField'], fields[3]['objectOffset'], fields[3]['nestedTypeIndex']))
        result = self.cases['27196-two-same-shape-fields-distinct-nonzero']['result']
        self.assertEqual({'KIIGMHLMLMA': 11, 'DODCEPDNAIM': 22}, result['GONJACBHGEP']['CNGEAFGELMF'][0])
        self.assertEqual({'KIIGMHLMLMA': 55, 'DODCEPDNAIM': 66}, result['CBIONIIBDIN']['CNGEAFGELMF'][0])
        self.assertEqual({'MHAIENEPEIP': 33, 'OACOOBKGAKO': 44}, result['GONJACBHGEP']['AIEEBDFPNPJ'][0])
        self.assertEqual({'MHAIENEPEIP': 77, 'OACOOBKGAKO': 88}, result['CBIONIIBDIN']['AIEEBDFPNPJ'][0])

    def test_message_lists_have_concrete_aot_and_native_dispatch(self):
        rows = self.report['aotBindings']
        for instance, child in [(36273, 71429), (12616, 18373), (23297, 77263)]:
            row = next(x for x in rows if (x['owner'], x['classInstance'], x['method']) == (25060, instance, 'FNNADLCNGFO'))
            self.assertEqual('0x158dc41f0', row['address'])
            self.assertEqual(child, row['arguments'][0]['typeDefinition'])
        fixture = self.cases['76885-two-lists-two-independent-leaves']
        self.assertEqual(2, len(fixture['result']['CNGEAFGELMF']))
        self.assertEqual(2, len(fixture['result']['AIEEBDFPNPJ']))
        self.assertEqual({71429, 18373}, {x['typeIndex'] for x in fixture['entered'] if x.get('typeIndex') in (71429, 18373)})

    def test_repeated_singular_message_merges_instead_of_replacing_lists(self):
        result = self.cases['27196-repeated-submessage-merges-and-appends']['result']
        self.assertEqual({'GONJACBHGEP': {'CNGEAFGELMF': [{'KIIGMHLMLMA': 1, 'DODCEPDNAIM': 2}, {'KIIGMHLMLMA': 5, 'DODCEPDNAIM': 6}], 'AIEEBDFPNPJ': [{'MHAIENEPEIP': 3, 'OACOOBKGAKO': 4}]}}, result)
        self.assertEqual({'GONJACBHGEP': {}, 'CBIONIIBDIN': {}}, self.cases['27196-two-explicit-empty-objects']['result'])

    def test_u32_lists_packed_unpacked_zero_upper_and_independent_fields(self):
        result = self.cases['66308-all-three-lists-and-nonempty-child']['result']
        self.assertEqual([3, 0, 4294967295], result['MLGIMHKCCHG'])
        self.assertEqual([4, 0, 4294967295], result['DCOFEJBIBGN'])
        self.assertEqual([5, 0, 4294967295], result['AAAMKIEOGNJ'])
        self.assertEqual([0, 128, 4294967295], result['JEONOFFKPNB'][0]['NCKEHOOOKGN'])
        self.assertEqual({'MLGIMHKCCHG': [0, 1, 128], 'DCOFEJBIBGN': [0, 2, 128], 'AAAMKIEOGNJ': [0, 3, 128]}, self.cases['66308-three-packed-unpacked-lists']['result'])
        self.assertEqual([0, 1, 4294967295], self.cases['77263-packed-unpacked-upper-zero']['result']['NCKEHOOOKGN'])
        packed = next(x for x in self.report['scan']['fields'] if (x['typeIndex'], x['tag'], x['wireType']) == (66308, 4, 2))
        self.assertTrue(any(x.get('kind') == 'native-list-field' and x['objectOffset'] == 24 and x['key'] == 34 for x in packed['entered']))

    def test_avatar_info_nonempty_integration_keeps_existing_core_and_unknown_children(self):
        row = self.cases['AvatarInfo-all-37-fields-with-both-new-nonempty-trees']
        self.assertEqual(ROOT, row['typeIndex'])
        result = row['result']; self.assertEqual(37, len(result))
        self.assertEqual(55, result['CKDDHADKCBK']['CBIONIIBDIN']['CNGEAFGELMF'][0]['KIIGMHLMLMA'])
        self.assertEqual(72, result['PKPEFNIHPLA']['PHCBOKNHIHH'])
        self.assertEqual(9007199254740993, result['FBLKPHOCBIK'])
        self.assertEqual({'7': -1.25}, result['ADJINCFKMML'])
        self.assertEqual({'untracedType': 16352, 'content': 'empty-envelope-only'}, result['JCKNHCDJFCE'])
        self.assertEqual([{'untracedType': 53824, 'content': 'empty-envelope-only'}], result['AMLLHJJIMNI'])
        self.assertTrue(TYPES <= {x['typeIndex'] for x in row['entered'] if 'typeIndex' in x})

    def test_zero_fresh_list_items_and_errors_have_precise_scope(self):
        self.assertEqual({'MHAIENEPEIP': 4294967295}, self.cases['18373-upper-and-duplicate-zero']['result'])
        self.assertEqual({}, self.cases['27196-scalar-wrong-wire-duplicate-zero']['result'])
        self.assertEqual({}, self.cases['66308-scalars-wrong-wire-duplicate-zero']['result'])
        self.assertEqual({'JEONOFFKPNB': [{}, {'BMMLCJLAODF': 11, 'GIFDBBABDOD': 22}, {}]}, self.cases['66308-message-list-fresh-empty-and-nonempty-items']['result'])
        rejected = self.report['rejections']; self.assertEqual(10, len(rejected))
        self.assertEqual(2, sum(x['scope'] == 'explicit-scope-refusal' for x in rejected))
        self.assertEqual(4, sum(x['scope'] == 'explicit-input-model' for x in rejected))
        self.assertEqual(4, sum(x['scope'] == 'native-error-entry' for x in rejected))
        native = [x for x in rejected if x['scope'] == 'native-error-entry']
        self.assertEqual({'negative-length', 'truncated-message', 'recursion-limit'}, {x['error'].split('Native error branch: ')[1] for x in native})

    def test_result_drift_rejected(self):
        with self.assertRaisesRegex(ValueError, 'tree native result differs'):
            verify(SimpleNamespace(run_graph=lambda *args: {'result': {'invented': True}}))


if __name__ == '__main__': unittest.main()
