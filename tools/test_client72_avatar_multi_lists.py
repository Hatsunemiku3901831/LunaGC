import json
import os
from pathlib import Path
from types import SimpleNamespace
import unittest
from tools.extract_client72 import HASHES
from tools.trace_client72_avatar_multi_lists import TYPES, CHILDREN, verify


class AvatarMultiListsRawTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        root = Path(__file__).resolve().parents[1]
        path = Path(os.environ.get('CLIENT72_AVATAR_MULTI_LISTS_OBSERVATIONS', root / 'protocol/cn-7.2/avatar-multi-lists-observations.json'))
        cls.report = json.loads(path.read_text('utf8')); cls.cases = {x['name']: x for x in cls.report['fixtures']}
        cls.messages = {x['typeIndex']: x for x in cls.report['messages']}

    def test_fixed_inputs_scope_and_all_29_fields_nonzero(self):
        self.assertEqual(HASHES, self.report['inputSHA256']); self.assertEqual('F-03d3e7', self.report['task'])
        self.assertEqual(TYPES, set(self.messages)); self.assertEqual(29, sum(len(m['fields']) for m in self.messages.values()))
        self.assertEqual(256, self.report['scan']['nativeFixtures']); self.assertEqual(CHILDREN, set(self.report['dependencyScope']['nestedMessageDependencies']))
        self.assertFalse(self.report['fullComplexRawClosure']); self.assertFalse(self.report['fullAvatarRawClosure']); self.assertFalse(self.report['targetProtocolReady'])
        for index, message in self.messages.items(): self.assertEqual({f['clientField'] for f in message['fields']}, set(self.cases['all-fields-' + str(index)]['result']))

    def test_six_message_list_aot_and_two_initializers(self):
        rows = self.report['aotBindings']; self.assertEqual(12, len(rows))
        readers = [x for x in rows if x['method'] == 'FNNADLCNGFO']; self.assertEqual({68918, 58639, 64949, 67498, 31113, 63008}, {x['arguments'][0]['typeDefinition'] for x in readers})
        self.assertTrue(all(x['address'] == '0x158dc41f0' for x in readers)); self.assertEqual(TYPES, {x['typeIndex'] for x in self.report['initializers']})

    def test_three_same_shape_large_lists_independent_nonempty_and_append(self):
        r = self.cases['all-fields-22404']['result']
        for name, values in [('HIKFHOJMFCA', [101, 102]), ('DPPEDFMHBAI', [202, 203]), ('NNIPFIBDCEN', [303, 304])]:
            self.assertEqual(values, [x['ANKEOLKBECN'] for x in r[name]]); self.assertEqual(18, len(r[name][0]))
        r = self.cases['three-same-shape-lists-append-independently']['result']
        self.assertEqual([{'ANKEOLKBECN': 101}, {}], r['HIKFHOJMFCA']); self.assertEqual([{'ANKEOLKBECN': 202}, {'ANKEOLKBECN': 204}], r['DPPEDFMHBAI']); self.assertEqual([{'ANKEOLKBECN': 303}], r['NNIPFIBDCEN'])

    def test_two_same_shape_leaf_lists_independent_nonempty_and_append(self):
        r = self.cases['all-fields-22404']['result']; self.assertEqual([15, 17], [x['AFKJIKCEOKA'] for x in r['JNIMENFGFHC']]); self.assertEqual([25, 27], [x['AFKJIKCEOKA'] for x in r['GNKIDBAOAKI']])
        r = self.cases['two-same-shape-leaf-lists-append-independently']['result']
        self.assertEqual([11, 13], [x['AFKJIKCEOKA'] for x in r['JNIMENFGFHC']]); self.assertEqual([21], [x['AFKJIKCEOKA'] for x in r['GNKIDBAOAKI']])

    def test_nested_signed_lists_concrete_add_and_both_direct_fields(self):
        for index, tag, name in [(63008, 7, 'MLKKEOCOAKN'), (22404, 21, 'DELNNOKFLAP')]:
            field = next(f for f in self.messages[index]['fields'] if f['clientField'] == name); self.assertEqual((tag, [0, 2]), (field['tag'], field['acceptedWireTypes']))
            row = self.cases['signed-packed-unpacked-' + str(index)]; self.assertEqual({name: [-1, -2147483648, 2147483647, 0]}, row['result'])
            self.assertEqual(4, len(row['signedListStores'])); self.assertTrue(all(x['source'] == '0x1568b8bc4' for x in row['signedListStores']))
            self.assertEqual({name: [-17]}, self.cases['signed-empty-packed-' + str(index)]['result'])
        r = self.cases['all-fields-22404']['result']['KJGJGKIHMCO'][0]; self.assertEqual(7, len(r)); self.assertEqual(18, len(r['MKFKJIAMCKJ'][0]))

    def test_uint64_high_bits_u32_and_singular_merge(self):
        r = self.cases['all-fields-22404']['result']; self.assertEqual((9007199254740993, 18446744073709551615), (r['HOPABANFAJI'], r['NIHNNHMGENL']))
        self.assertEqual({'HOPABANFAJI': 18446744073709551615, 'NIHNNHMGENL': 9223372036854775808}, self.cases['two-uint64-high-bits-swapped']['result'])
        self.assertEqual({'KPDMPKFJHED': [0, 1, 4294967295]}, self.cases['u32-packed-unpacked']['result'])
        self.assertEqual({'CDAJPPGBAJC': {'ILJLGHBLKMP': 77, 'PBIBHHJEOPE': 4294967295, 'GOKKMOKPCEE': -2147483648}}, self.cases['singular-leaf-merges-with-zero']['result'])

    def test_precise_errors_and_zero(self):
        rejected = self.report['rejections']; self.assertEqual(9, len(rejected)); self.assertEqual({'explicit-scope-refusal': 1, 'explicit-input-model': 4, 'native-error-entry': 4}, {scope: sum(x['scope'] == scope for x in rejected) for scope in {x['scope'] for x in rejected}})
        self.assertTrue(all('Native error branch:' in x['error'] for x in rejected if x['scope'] == 'native-error-entry'))
        for index in TYPES: self.assertEqual({}, self.cases['empty-' + str(index)]['result'])
        self.assertEqual({}, self.cases['duplicate-signed-bool-u64-zero']['result'])

    def test_result_drift_rejected(self):
        with self.assertRaisesRegex(ValueError, 'multi list native result differs'):
            verify(SimpleNamespace(run_graph=lambda *args: {'result': {'invented': True}}))


if __name__ == '__main__': unittest.main()
