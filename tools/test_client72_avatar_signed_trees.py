import json
import os
from pathlib import Path
from types import SimpleNamespace
import unittest
from tools.extract_client72 import HASHES
from tools.trace_client72_avatar_signed_trees import TYPES, verify


class AvatarSignedTreesRawTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        root = Path(__file__).resolve().parents[1]
        path = Path(os.environ.get('CLIENT72_AVATAR_SIGNED_TREES_OBSERVATIONS', root / 'protocol/cn-7.2/avatar-signed-trees-observations.json'))
        cls.report = json.loads(path.read_text('utf8')); cls.cases = {x['name']: x for x in cls.report['fixtures']}
        cls.messages = {x['typeIndex']: x for x in cls.report['messages']}

    def test_fixed_inputs_three_types_all_40_fields_nonzero(self):
        self.assertEqual(HASHES, self.report['inputSHA256']); self.assertEqual('F-03d3e4', self.report['task'])
        self.assertEqual(TYPES, set(self.messages)); self.assertEqual(40, sum(len(m['fields']) for m in self.messages.values()))
        self.assertEqual(384, self.report['scan']['nativeFixtures']); self.assertEqual([68918], self.report['dependencyScope']['nestedMessageDependencies'])
        self.assertFalse(self.report['fullComplexRawClosure']); self.assertFalse(self.report['fullAvatarRawClosure'])
        self.assertFalse(self.report['targetProtocolReady']); self.assertEqual('unresolved', self.report['semanticIdentityStatus'])
        for index, message in self.messages.items():
            self.assertEqual({f['clientField'] for f in message['fields']}, set(self.cases['all-fields-' + str(index)]['result']))

    def test_concrete_signed_aot_and_native_flag_prefix(self):
        rows = self.report['aotBindings']; self.assertEqual(12, len(rows))
        reader = next(x for x in rows if x['owner'] == 35584 and x['method'] == 'FNNADLCNGFO')
        self.assertEqual((6, '0x1568b8d60', 'int32'), (reader['classInstance'], reader['address'], reader['arguments'][0]['kind']))
        add = next(x for x in rows if x['address'] == '0x1568b8b10'); self.assertEqual('Add', add['method'])
        prefix = self.report['signedHelperPrefix']
        self.assertEqual(('0x155b36190', '0x155b361c0', '0x155b361bd', 1), (prefix['entry'], prefix['stopBefore'], prefix['flagStore'], prefix['observedPackedFlag']))
        types = self.report['signedTypeUsage']; self.assertEqual((45077, 35584), (types[0]['type']['typeDefinition'], types[1]['type']['typeDefinition']))
        self.assertEqual('int32', types[2]['type']['elementType']['kind']); self.assertEqual('int32', types[3]['type']['kind'])
        factory = next(f for x in self.report['initializers'] for f in x['factoryArguments'] if f.get('kind') == 'int32')
        self.assertEqual((74, 32, 44), (factory['tag'], factory['readerOffset'], factory['tagOffset']))

    def test_signed_packed_unpacked_and_native_four_byte_stores(self):
        field = next(f for f in self.messages[89623]['fields'] if f['clientField'] == 'MLKKEOCOAKN')
        self.assertEqual((9, 40, [0, 2]), (field['tag'], field['objectOffset'], field['acceptedWireTypes']))
        row = self.cases['signed-packed-unpacked-append']; self.assertEqual({'MLKKEOCOAKN': [-1, -2147483648, 2147483647, 0, 128]}, row['result'])
        self.assertEqual(5, sum(x.get('kind') == 'native-int32-list-add' for x in row['entered']))
        self.assertEqual([0, 4, 8, 12, 16], [x['arrayByteOffset'] for x in row['signedListStores']])
        self.assertEqual(['0xffffffff', '0x80000000', '0x7fffffff', '0x0', '0x80'], [x['valueBits'] for x in row['signedListStores']])
        self.assertTrue(all(x['size'] == 4 and x['source'] == '0x1568b8bc4' for x in row['signedListStores']))

    def test_signed_zero_wrong_wire_empty_and_negative_length(self):
        self.assertEqual({'MLKKEOCOAKN': [-17]}, self.cases['signed-empty-packed-preserves-prior']['result'])
        self.assertEqual({'MLKKEOCOAKN': [-17]}, self.cases['signed-wrong-wire-skipped']['result'])
        self.assertEqual({'MLKKEOCOAKN': [-1]}, self.cases['signed-five-byte-low-bits']['result'])
        row = self.cases['signed-negative-packed-length-no-append']; self.assertEqual({}, row['result']); self.assertEqual([], row['signedListStores'])
        self.assertTrue(any(x.get('entry') == '0x1568b8d60' for x in row['entered']))
        packed = next(x for x in self.report['scan']['fields'] if (x['typeIndex'], x['tag'], x['wireType']) == (89623, 9, 2))
        self.assertTrue(any(x.get('kind') == 'native-signed-tree-list-field' and x['objectOffset'] == 40 and x['key'] == 74 for x in packed['entered']))

    def test_same_shape_children_and_list_do_not_cross_and_merge(self):
        r = self.cases['same-shape-children-merge-independently']['result']
        self.assertEqual({'CJMJBINEBAG': -11, 'ALOKJBKPLGA': 55}, r['HHAFHFEPLPD'])
        self.assertEqual({'CJMJBINEBAG': -33, 'ALOKJBKPLGA': 44}, r['FANOIJAHMKN'])
        r = self.cases['same-shape-list-and-children-remain-distinct']['result']
        self.assertEqual({'ALOKJBKPLGA': 2}, r['HHAFHFEPLPD']); self.assertEqual({'CJMJBINEBAG': -3, 'ALOKJBKPLGA': 4}, r['PODNCONEGKP'])
        self.assertEqual([{'CJMJBINEBAG': -5, 'ALOKJBKPLGA': 6}, {'CJMJBINEBAG': -7, 'ALOKJBKPLGA': 8}, {}], r['AOMDFCPMDOP'])
        self.assertEqual({'HHAFHFEPLPD': {}, 'FANOIJAHMKN': {}}, self.cases['singular-explicit-empty-both-children']['result'])

    def test_full_64bit_three_slots_and_distinct_scalar_slots(self):
        r = self.cases['all-fields-19829']['result']
        self.assertEqual((9007199254740993, 9223372036854775808, 18446744073709551615), tuple(r[x] for x in ('HOPABANFAJI', 'NIHNNHMGENL', 'OOKFBIPCHBO')))
        r = self.cases['uint64-three-distinct-nonzero']['result']
        self.assertEqual((18446744073709551615, 9007199254740993, 9223372036854775808), tuple(r[x] for x in ('HOPABANFAJI', 'NIHNNHMGENL', 'OOKFBIPCHBO')))
        r = self.cases['all-fields-21103']['result']; self.assertEqual(15, len(r)); self.assertEqual((11, 176, -2147483648), (r['PJAJDHBBMGB'], r['ODKDJGDHHMI'], r['HPLBLGJLIFF']))
        self.assertEqual({}, self.cases['scalar-signed-bool-u64-duplicate-zero']['result'])
        for index, field in [(19829, 'HCMGPBLEPNC'), (89623, 'FEJJCDNNECH')]: self.assertEqual({field: [0, 1, 4294967295]}, self.cases['u32-packed-unpacked-' + str(index)]['result'])

    def test_capacity_64_and_precise_native_vs_model_errors(self):
        row = self.cases['signed-fixture-capacity-64']; self.assertEqual(64, len(row['result']['MLKKEOCOAKN'])); self.assertEqual(-63, row['result']['MLKKEOCOAKN'][-1])
        self.assertEqual(list(range(0, 256, 4)), [x['arrayByteOffset'] for x in row['signedListStores']])
        rejected = self.report['rejections']; self.assertEqual(10, len(rejected))
        self.assertEqual({'explicit-scope-refusal': 1, 'explicit-input-model': 4, 'native-error-entry': 4, 'fixture-model-boundary': 1}, {scope: sum(x['scope'] == scope for x in rejected) for scope in {x['scope'] for x in rejected}})
        self.assertTrue(all('Native error branch:' in x['error'] for x in rejected if x['scope'] == 'native-error-entry'))
        boundary = next(x for x in rejected if x['scope'] == 'fixture-model-boundary'); self.assertIn('Unmodeled native call 0x140514ee0', boundary['error'])

    def test_result_drift_rejected(self):
        with self.assertRaisesRegex(ValueError, 'signed subtree native result differs'):
            verify(SimpleNamespace(run_graph=lambda *args: {'result': {'invented': True}}))


if __name__ == '__main__': unittest.main()
