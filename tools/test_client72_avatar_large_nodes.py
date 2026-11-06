import json
import os
from pathlib import Path
from types import SimpleNamespace
import unittest
from tools.extract_client72 import HASHES
from tools.trace_client72_avatar_large_nodes import TYPES, CHILDREN, verify


class AvatarLargeNodesRawTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        root = Path(__file__).resolve().parents[1]
        path = Path(os.environ.get('CLIENT72_AVATAR_LARGE_NODES_OBSERVATIONS', root / 'protocol/cn-7.2/avatar-large-nodes-observations.json'))
        cls.report = json.loads(path.read_text('utf8')); cls.cases = {x['name']: x for x in cls.report['fixtures']}
        cls.messages = {x['typeIndex']: x for x in cls.report['messages']}

    def test_fixed_inputs_scope_and_all_36_fields_nonzero(self):
        self.assertEqual(HASHES, self.report['inputSHA256']); self.assertEqual('F-03d3e6', self.report['task'])
        self.assertEqual(TYPES, set(self.messages)); self.assertEqual(36, sum(len(m['fields']) for m in self.messages.values()))
        self.assertEqual(256, self.report['scan']['nativeFixtures']); self.assertEqual(CHILDREN, set(self.report['dependencyScope']['nestedMessageDependencies']))
        self.assertFalse(self.report['fullComplexRawClosure']); self.assertFalse(self.report['fullAvatarRawClosure']); self.assertFalse(self.report['targetProtocolReady'])
        for index, message in self.messages.items(): self.assertEqual({f['clientField'] for f in message['fields']}, set(self.cases['all-fields-' + str(index)]['result']))

    def test_message_list_aot_and_signed_codec_initializer(self):
        rows = self.report['aotBindings']; self.assertEqual(6, len(rows))
        readers = [x for x in rows if x['method'] == 'FNNADLCNGFO']; self.assertEqual({51614, 68918, 21518}, {x['arguments'][0]['typeDefinition'] for x in readers})
        self.assertTrue(all(x['address'] == '0x158dc41f0' for x in readers))
        self.assertEqual([58639], [x['typeIndex'] for x in self.report['initializers']])
        signed = next(x for x in self.report['initializers'][0]['factoryArguments'] if x.get('kind') == 'int32'); self.assertEqual(146, signed['tag'])

    def test_three_same_shape_children_stay_independent(self):
        r = self.cases['all-fields-64949']['result']
        self.assertEqual([(-1, 2), (-3, 4), (-5, 6)], [(r[n]['CJMJBINEBAG'], r[n]['ALOKJBKPLGA']) for n in ('HHAFHFEPLPD', 'GFKJKLIHGNK', 'EDKKGCIAMOA')])
        r = self.cases['three-same-shape-children-merge-independently']['result']
        self.assertEqual({'CJMJBINEBAG': -1, 'ALOKJBKPLGA': 22}, r['HHAFHFEPLPD']); self.assertEqual({'ALOKJBKPLGA': 4}, r['GFKJKLIHGNK']); self.assertEqual({'CJMJBINEBAG': -5, 'ALOKJBKPLGA': 6}, r['EDKKGCIAMOA'])

    def test_signed_list_mixed_and_actual_native_stores(self):
        field = next(f for f in self.messages[58639]['fields'] if f['clientField'] == 'MLKKEOCOAKN')
        self.assertEqual((18, 24, [0, 2]), (field['tag'], field['objectOffset'], field['acceptedWireTypes']))
        row = self.cases['signed-mixed-packed-unpacked']; self.assertEqual({'MLKKEOCOAKN': [-1, -2147483648, 2147483647, 0]}, row['result'])
        self.assertEqual(['0xffffffff', '0x80000000', '0x7fffffff', '0x0'], [x['valueBits'] for x in row['signedListStores']])
        self.assertTrue(all(x['source'] == '0x1568b8bc4' and x['size'] == 4 for x in row['signedListStores']))
        self.assertEqual({'MLKKEOCOAKN': [-17]}, self.cases['signed-empty-packed-keeps-prior']['result']); self.assertEqual({'MLKKEOCOAKN': [-1]}, self.cases['signed-list-wrong-fixed32-skipped']['result'])

    def test_nonempty_lists_preserve_distinct_values_and_append(self):
        r = self.cases['all-fields-58639']['result']; self.assertEqual(16, len(r['ELLMCOFKKPA'][0])); self.assertEqual({'NOEEBKJAFAG': 1, 'ANKEOLKBECN': 2}, r['ELLMCOFKKPA'][1])
        self.assertEqual(44, r['BGGPFKNPLGL'][1]['OGJOJBLIGCB']); self.assertEqual(7, r['AOMDFCPMDOP'][1]['CJMJBINEBAG'])
        r = self.cases['large-list-append-and-empty-element']['result']; self.assertEqual(2, len(r['ELLMCOFKKPA'])); self.assertEqual({}, r['ELLMCOFKKPA'][1])

    def test_three_uint64_fields_have_full_high_bits(self):
        r = self.cases['all-fields-64949']['result']; self.assertEqual((9007199254740993, 9223372036854775808, 18446744073709551615), tuple(r[n] for n in ('HOPABANFAJI', 'NIHNNHMGENL', 'OOKFBIPCHBO')))
        r = self.cases['three-u64-high-bits-swapped']['result']; self.assertEqual((18446744073709551615, 9007199254740993, 9223372036854775808), tuple(r[n] for n in ('HOPABANFAJI', 'NIHNNHMGENL', 'OOKFBIPCHBO')))

    def test_zero_empty_and_precise_errors(self):
        for index in TYPES:
            self.assertEqual({}, self.cases['empty-' + str(index)]['result']); self.assertEqual({}, self.cases['duplicate-zero-' + str(index)]['result'])
        self.assertEqual({'HHAFHFEPLPD': {}, 'GFKJKLIHGNK': {}, 'EDKKGCIAMOA': {}}, self.cases['all-three-explicit-empty-children']['result'])
        rejected = self.report['rejections']; self.assertEqual(9, len(rejected)); self.assertEqual({'explicit-scope-refusal': 1, 'explicit-input-model': 4, 'native-error-entry': 4}, {scope: sum(x['scope'] == scope for x in rejected) for scope in {x['scope'] for x in rejected}})
        self.assertTrue(all('Native error branch:' in x['error'] for x in rejected if x['scope'] == 'native-error-entry'))

    def test_result_drift_rejected(self):
        with self.assertRaisesRegex(ValueError, 'large node native result differs'):
            verify(SimpleNamespace(run_graph=lambda *args: {'result': {'invented': True}}))


if __name__ == '__main__': unittest.main()
