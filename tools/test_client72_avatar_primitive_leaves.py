import json
import os
from pathlib import Path
from types import SimpleNamespace
import unittest
from tools.extract_client72 import HASHES
from tools.trace_client72_avatar_primitive_leaves import TYPES, verify


class AvatarPrimitiveLeavesRawTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        root = Path(__file__).resolve().parents[1]
        path = Path(os.environ.get('CLIENT72_AVATAR_PRIMITIVE_LEAVES_OBSERVATIONS', root / 'protocol/cn-7.2/avatar-primitive-leaves-observations.json'))
        cls.report = json.loads(path.read_text('utf8')); cls.cases = {x['name']: x for x in cls.report['fixtures']}
        cls.messages = {x['typeIndex']: x for x in cls.report['messages']}

    def test_fixed_inputs_scope_and_all_43_fields_nonzero(self):
        self.assertEqual(HASHES, self.report['inputSHA256'])
        self.assertEqual('F-03d3e3', self.report['task'])
        self.assertEqual(TYPES, set(self.messages))
        self.assertEqual(43, sum(len(m['fields']) for m in self.messages.values()))
        self.assertEqual(1408, self.report['scan']['nativeFixtures'])
        self.assertEqual([], self.report['dependencyScope']['nestedMessageDependencies'])
        self.assertFalse(self.report['fullComplexRawClosure']); self.assertFalse(self.report['fullAvatarRawClosure'])
        self.assertFalse(self.report['targetProtocolReady']); self.assertEqual('unresolved', self.report['semanticIdentityStatus'])
        for index, message in self.messages.items():
            self.assertEqual({f['clientField'] for f in message['fields']}, set(self.cases['all-fields-' + str(index)]['result']))

    def test_float_concrete_aot_and_typed_native_packed_flag_prefix(self):
        rows = self.report['aotBindings']; self.assertEqual(13, len(rows))
        self.assertEqual('0x1571dc270', next(x for x in rows if (x['owner'], x['classInstance'], x['method']) == (35584, 78, 'FNNADLCNGFO'))['address'])
        add = next(x for x in rows if x['address'] == '0x1571dc010')
        self.assertEqual((35584, 78, 'Add', 'float'), (add['owner'], add['classInstance'], add['method'], add['arguments'][0]['kind']))
        prefix = self.report['floatHelperPrefix']
        self.assertEqual(('0x159229a20', '0x159229a51', '0x159229a4d', 1), (prefix['entry'], prefix['stopBefore'], prefix['flagStore'], prefix['observedPackedFlag']))
        types = self.report['floatTypeUsage']
        self.assertEqual((45077, 35584), (types[0]['type']['typeDefinition'], types[1]['type']['typeDefinition']))
        self.assertEqual('float', types[2]['type']['elementType']['kind'])

    def test_float_wire_types_native_add_and_actual_four_byte_stores(self):
        field = next(f for f in self.messages[12876]['fields'] if f['clientField'] == 'HMODGFNMPML')
        self.assertEqual((3, 32, [2, 5]), (field['tag'], field['objectOffset'], field['acceptedWireTypes']))
        row = self.cases['float-mixed-packed-unpacked-append']
        self.assertEqual({'HMODGFNMPML': [-1.25, 2.5, -0.0, 0.0]}, row['result'])
        self.assertEqual(['0xbfa00000', '0x40200000', '0x80000000', '0x0'], row['floatListBits'][0]['bits'])
        self.assertEqual(4, sum(x.get('kind') == 'native-float-list-add' for x in row['entered']))
        self.assertEqual([0, 4, 8, 12], [x['arrayByteOffset'] for x in row['floatListStores']])
        self.assertTrue(all(x['size'] == 4 and x['source'] == '0x1571dc0c9' for x in row['floatListStores']))

    def test_float_zero_extremes_empty_packed_and_wrong_wire(self):
        row = self.cases['float-packed-extremes-zero-and-negative-zero']
        self.assertEqual(['0x3fa00000', '0xc0200000', '0x0', '0x80000000', '0x7f7fffff', '0x1'], row['floatListBits'][0]['bits'])
        self.assertEqual({'HMODGFNMPML': [-1.25]}, self.cases['float-empty-packed-preserves-prior']['result'])
        self.assertEqual({'HMODGFNMPML': [-1.25, 0.0]}, self.cases['float-wrong-varint-wire-is-skipped']['result'])
        packed = next(x for x in self.report['scan']['fields'] if (x['typeIndex'], x['tag'], x['wireType']) == (12876, 3, 2))
        self.assertTrue(any(x.get('kind') == 'native-list-field' and x['objectOffset'] == 32 and x['key'] == 26 for x in packed['entered']))

    def test_capacity_64_is_native_and_65_is_only_fixture_boundary(self):
        row = self.cases['float-fixture-capacity-64']
        self.assertEqual(64, len(row['result']['HMODGFNMPML']))
        self.assertEqual(-63.0, row['result']['HMODGFNMPML'][-1])
        self.assertEqual(list(range(0, 256, 4)), [x['arrayByteOffset'] for x in row['floatListStores']])
        rejection = next(x for x in self.report['rejections'] if x['scope'] == 'fixture-model-boundary')
        self.assertIn('Unmodeled native call 0x140514ee0', rejection['error'])

    def test_seven_u32_lists_independent_values_and_packed_unpacked(self):
        self.assertEqual({'ABBODGKNDEP': [0, 1, 128], 'KGMKPEBKEFF': [0, 2, 255]}, self.cases['u32-mixed-18849-two-lists']['result'])
        self.assertEqual({'PFBKEGPBKAN': [0, 2, 128], 'CCPOLEPGAEB': [0, 3, 255]}, self.cases['u32-mixed-31113-two-lists']['result'])
        for index, name in [(12876, 'MDAMHOLGONG'), (65248, 'HGFBPFCEACB'), (66229, 'FEKDEEENNBB')]:
            self.assertEqual({name: [0, 1, 4294967295]}, self.cases['u32-mixed-' + str(index)]['result'])
        u32 = [f for m in self.messages.values() for f in m['fields'] if f['cardinality'] == 'repeated' and f['type'] == 'uint32']
        self.assertEqual(7, len(u32)); self.assertTrue(all(f['acceptedWireTypes'] == [0, 2] for f in u32))

    def test_signed_scalars_same_shape_types_zero_and_precise_rejections(self):
        large = self.cases['all-fields-51614']['result']; self.assertEqual(16, len(large))
        self.assertEqual((-33, -44, -55, -66, -77, -88), tuple(large[x] for x in ['JCGPKLKCINE', 'OOKGFNCMOMD', 'AEPFCKJJOMN', 'CMACDPNAIAF', 'GEODCFEFNLP', 'NEBNDBIGKIA']))
        self.assertEqual({'CJMJBINEBAG': -17, 'ALOKJBKPLGA': 18}, self.cases['all-fields-48088']['result'])
        self.assertEqual({'CJMJBINEBAG': -2147483648, 'ALOKJBKPLGA': 2147483647}, self.cases['all-fields-68918']['result'])
        self.assertEqual({}, self.cases['signed-bool-u32-duplicate-zero']['result'])
        rejected = self.report['rejections']; self.assertEqual(7, len(rejected))
        self.assertEqual(1, sum(x['scope'] == 'explicit-scope-refusal' for x in rejected))
        self.assertEqual(5, sum(x['scope'] == 'explicit-input-model' for x in rejected))
        self.assertEqual(1, sum(x['scope'] == 'fixture-model-boundary' for x in rejected))

    def test_result_drift_rejected(self):
        with self.assertRaisesRegex(ValueError, 'primitive leaf native result differs'):
            verify(SimpleNamespace(run_graph=lambda *args: {'result': {'invented': True}}))


if __name__ == '__main__': unittest.main()
