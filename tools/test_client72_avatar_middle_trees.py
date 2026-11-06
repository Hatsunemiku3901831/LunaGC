import json
import os
from pathlib import Path
from types import SimpleNamespace
import unittest
from tools.extract_client72 import HASHES
from tools.trace_client72_avatar_middle_trees import TYPES, CHILDREN, verify


class AvatarMiddleTreesRawTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        root = Path(__file__).resolve().parents[1]
        path = Path(os.environ.get('CLIENT72_AVATAR_MIDDLE_TREES_OBSERVATIONS', root / 'protocol/cn-7.2/avatar-middle-trees-observations.json'))
        cls.report = json.loads(path.read_text('utf8')); cls.cases = {x['name']: x for x in cls.report['fixtures']}
        cls.messages = {x['typeIndex']: x for x in cls.report['messages']}

    def test_fixed_inputs_scope_and_all_24_fields_nonzero(self):
        self.assertEqual(HASHES, self.report['inputSHA256']); self.assertEqual('F-03d3e5', self.report['task'])
        self.assertEqual(TYPES, set(self.messages)); self.assertEqual(24, sum(len(m['fields']) for m in self.messages.values()))
        self.assertEqual(640, self.report['scan']['nativeFixtures']); self.assertEqual(CHILDREN, set(self.report['dependencyScope']['nestedMessageDependencies']))
        self.assertFalse(self.report['fullComplexRawClosure']); self.assertFalse(self.report['fullAvatarRawClosure']); self.assertFalse(self.report['targetProtocolReady'])
        for index, message in self.messages.items(): self.assertEqual({f['clientField'] for f in message['fields']}, set(self.cases['all-fields-' + str(index)]['result']))

    def test_five_concrete_message_list_aot_and_initializers(self):
        rows = self.report['aotBindings']; self.assertEqual(10, len(rows))
        readers = [x for x in rows if x['method'] == 'FNNADLCNGFO']; self.assertEqual(5, len(readers))
        self.assertEqual({21103, 89623, 19829, 66229, 12876}, {x['arguments'][0]['typeDefinition'] for x in readers})
        self.assertTrue(all(x['address'] == '0x158dc41f0' for x in readers))
        self.assertEqual({45040, 63939, 87867}, {x['typeIndex'] for x in self.report['initializers']})

    def test_int64_exact_signed_limits_and_high_bits(self):
        field = next(f for f in self.messages[63939]['fields'] if f['clientField'] == 'ANKEOLKBECN')
        self.assertEqual((1, 32, 'int64', [0]), (field['tag'], field['objectOffset'], field['type'], field['acceptedWireTypes']))
        for value in (-9223372036854775808, 9223372036854775807, -1, 9007199254740993): self.assertEqual({'ANKEOLKBECN': value}, self.cases['int64-' + str(value)]['result'])
        self.assertEqual({}, self.cases['int64-duplicate-zero']['result']); self.assertEqual({}, self.cases['63939-wrong-int64-fixed64-skipped']['result'])

    def test_distinct_nonempty_child_lists_and_both_u64_fields(self):
        r = self.cases['all-fields-87867']['result']; self.assertEqual(18, len(r))
        self.assertEqual((9007199254740993, 18446744073709551615), (r['HOPABANFAJI'], r['NIHNNHMGENL']))
        self.assertEqual([7], r['NKBMECCPKPJ'][1]['MLKKEOCOAKN']); self.assertEqual(7, r['BGKOAMHLPPK'][1]['HOPABANFAJI'])
        self.assertEqual({'PJAJDHBBMGB': 11, 'IAHPPLKIIBK': 12}, r['PLPNCLOFNIC'][1])
        self.assertEqual({'HGFBPFCEACB': [1, 128]}, r['HPHMLMEMKLB'])
        self.assertEqual({'HOPABANFAJI': 18446744073709551615, 'NIHNNHMGENL': 9223372036854775808}, self.cases['87867-two-u64-high-bits-swapped']['result'])
        self.assertEqual({}, self.cases['87867-duplicate-signed-bool-u64-zero']['result'])

    def test_nested_float_and_signed_lists_use_real_add_stores(self):
        row = self.cases['all-fields-63939']; children = row['result']['OCJGKKLACBO']
        self.assertEqual([1.25, -2.5, 0.0, -0.0], children[0]['HMODGFNMPML']); self.assertEqual([-1.25], children[1]['HMODGFNMPML'])
        self.assertEqual(['0x3fa00000', '0xc0200000', '0x0', '0x80000000'], row['floatListBits'][0]['bits'])
        self.assertEqual(5, len(row['floatListStores'])); self.assertTrue(all(x['source'] == '0x1571dc0c9' for x in row['floatListStores']))
        row = self.cases['all-fields-87867']; self.assertEqual(6, len(row['signedListStores'])); self.assertTrue(all(x['source'] == '0x1568b8bc4' for x in row['signedListStores']))
        self.assertEqual({'KPDMPKFJHED': [0, 1, 4294967295]}, self.cases['87867-u32-packed-unpacked']['result'])

    def test_two_same_shape_subtrees_merge_with_independent_lists(self):
        row = self.cases['all-fields-57431']['result']; self.assertEqual(-2147483648, row['DHLPKMNMONN']['OCICCDJIIEO']['KMBPJIKEDGD'])
        self.assertEqual(11, row['HAAGBOCHHBH']['GPDEGHEDMHM'][0]['KMBPJIKEDGD'])
        row = self.cases['57431-repeated-singular-merges-and-list-appends']['result']
        self.assertEqual([4, 128, 1, 2], row['DHLPKMNMONN']['OCICCDJIIEO']['FEKDEEENNBB']); self.assertEqual(3, len(row['HAAGBOCHHBH']['GPDEGHEDMHM']))
        self.assertEqual({}, row['HAAGBOCHHBH']['GPDEGHEDMHM'][-1]); self.assertEqual({'DHLPKMNMONN': {}, 'HAAGBOCHHBH': {}}, self.cases['57431-explicit-empty-children']['result'])

    def test_precise_error_scope_and_empty_graphs(self):
        rejected = self.report['rejections']; self.assertEqual(8, len(rejected))
        self.assertEqual({'explicit-scope-refusal': 1, 'explicit-input-model': 3, 'native-error-entry': 4}, {scope: sum(x['scope'] == scope for x in rejected) for scope in {x['scope'] for x in rejected}})
        self.assertTrue(all('Native error branch:' in x['error'] for x in rejected if x['scope'] == 'native-error-entry'))
        for index in TYPES: self.assertEqual({}, self.cases['empty-' + str(index)]['result'])

    def test_result_drift_rejected(self):
        with self.assertRaisesRegex(ValueError, 'middle subtree native result differs'):
            verify(SimpleNamespace(run_graph=lambda *args: {'result': {'invented': True}}))


if __name__ == '__main__': unittest.main()
