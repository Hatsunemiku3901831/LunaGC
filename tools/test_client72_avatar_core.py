import json
import os
from pathlib import Path
from types import SimpleNamespace
import unittest
from tools.extract_client72 import HASHES
from tools.trace_client72_avatar_core import ROOT, VALUE, TYPES, BLOCKED, verify


class AvatarCoreRawTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        root = Path(__file__).resolve().parents[1]
        path = Path(os.environ.get('CLIENT72_AVATAR_CORE_OBSERVATIONS', root / 'protocol/cn-7.2/avatar-core-observations.json'))
        cls.report = json.loads(path.read_text('utf8')); cls.cases = {x['name']: x for x in cls.report['fixtures']}
        cls.messages = {x['typeIndex']: x for x in cls.report['messages']}

    def test_hash_bound_scope_remains_partial_and_unnamed(self):
        self.assertEqual(HASHES, self.report['inputSHA256'])
        self.assertEqual('F-03d3b', self.report['task'])
        self.assertEqual(TYPES, set(self.messages))
        self.assertEqual(53, sum(len(x['fields']) for x in self.messages.values()))
        self.assertEqual(37, len(self.messages[ROOT]['fields']))
        self.assertEqual('unresolved', self.report['semanticIdentityStatus'])
        self.assertFalse(self.report['fullAvatarRawClosure'])
        self.assertFalse(self.report['targetProtocolReady'])
        self.assertEqual(sorted(BLOCKED), self.report['blockedTypeIndices'])

    def test_actual_red_and_codec_selected_green(self):
        reproduction = self.report['codecConflict']; red = reproduction['red']
        self.assertEqual((3, 10, '0x156d35a40'), (red['requestedTag'], red['selectedByEntryTag'], red['reader']))
        self.assertIn('Wrong map codec', red['error'])
        self.assertEqual({'IKIFIBAEDGD': {'7': {'NOEEBKJAFAG': 1}}}, reproduction['green']['result'])
        selected = [x for x in reproduction['green']['entered'] if x.get('kind') == 'codec-selected-context']
        self.assertEqual([(3, VALUE)], [(x['mapTag'], x['child']) for x in selected])
        rows = self.report['aotBindings']
        for instance, child in [(9505, VALUE), (12396, 66100)]:
            row = next(x for x in rows if (x['owner'], x['classInstance'], x['method']) == (66210, instance, 'FNNADLCNGFO'))
            self.assertEqual('0x156d35a40', row['address'])
            self.assertEqual(child, row['arguments'][1]['typeDefinition'])

    def test_full_bounded_scan_includes_tags_above_32_and_packed_empty(self):
        scan = self.report['scan']; self.assertEqual(1536, scan['nativeFixtures'])
        fields = {f['tag']: f for f in self.messages[ROOT]['fields']}
        self.assertEqual(('EIBCGPEOJLB', 96), (fields[38]['clientField'], fields[38]['objectOffset']))
        self.assertEqual([0, 2], fields[38]['acceptedWireTypes'])
        packed = next(x for x in scan['fields'] if (x['typeIndex'], x['tag'], x['wireType']) == (ROOT, 38, 2))
        self.assertTrue(any(x.get('kind') == 'native-list-field' and x['objectOffset'] == 96 and x['key'] == 306 for x in packed['entered']))
        self.assertTrue(all(x['semanticStatus'] == 'unresolved' for m in self.messages.values() for x in m['fields']))

    def test_all_outer_fields_and_four_leaf_contents_are_nonempty(self):
        row = self.cases['all-37-outer-fields-with-allowed-nonempty-children']; result = row['result']
        self.assertEqual({f['clientField'] for f in self.messages[ROOT]['fields']}, set(result))
        self.assertEqual(37, len(result))
        self.assertEqual(5, len(result['BNHMCGLAMLE']))
        self.assertEqual(3, len(result['HLAIJJEMIHE']['7']))
        self.assertEqual(2, len(result['CMGAIFKIOJP']))
        self.assertEqual(2, len(result['DLDNCCJCHOB'][0]))
        self.assertEqual(9007199254740993, result['FBLKPHOCBIK'])
        self.assertEqual({'7': -1.25}, result['ADJINCFKMML'])
        self.assertTrue(any(x.get('typeIndex') == 27246 for x in row['entered']))

    def test_float_native_abi_bits_and_duplicate_store(self):
        rows = self.report['aotBindings']
        adapter = next(x for x in rows if (x['owner'], x['classInstance'], x['method']) == (17964, 5971, 'MOMMACBEMEK'))
        self.assertEqual('0x1551d29e0', adapter['address'])
        self.assertEqual(['uint32', 'float'], [x['kind'] for x in adapter['arguments']])
        row = self.cases['float-duplicate-negative-zero']
        self.assertEqual([{'key': 7, 'valueBits': '0x80000000'}], row['floatMapNodes'])
        self.assertTrue(any(x['source'] == '0x1551dc2c4' and x['nodeOffset'] == 48 and x['size'] == 4 for x in row['mapStores']))
        self.assertEqual({'ADJINCFKMML': {'7': -1.25, '4294967295': 3.5}}, self.cases['float-negative-two-keys']['result'])
        self.assertEqual({'ADJINCFKMML': {'8': -2.5}}, self.cases['float-entry-duplicate-keys-values-unknown']['result'])

    def test_shared_maps_interleave_and_replace_without_cross_context(self):
        row = self.cases['message-maps-interleave-same-key-replace']
        self.assertEqual({'IKIFIBAEDGD': {'7': {'BNJKCHONGGE_float': -1.25}}, 'HLAIJJEMIHE': {'7': {'AICLAOPOLPO': 3}}}, row['result'])
        selected = [x['mapTag'] for x in row['entered'] if x.get('kind') == 'codec-selected-context']
        self.assertEqual([3, 10, 3, 10], selected)
        self.assertTrue(row['mapStores'])
        self.assertEqual({'PJGIPBJMBOP': {'7': 0}, 'JOJLFAOOLMH': {'4294967295': 4294967295}}, self.cases['two-u32-maps-duplicate-zero-and-upper']['result'])

    def test_uint64_list_scalar_signed_and_explicit_zero_boundaries(self):
        self.assertEqual({'AODNDDBPMFB': [0, 9007199254740993, 18446744073709551615]}, self.cases['u64-packed-unpacked-high-upper-zero']['result'])
        self.assertEqual({'ABAAMEKEIFJ': [0, 1, 4294967295]}, self.cases['u32-packed-unpacked-upper-zero']['result'])
        self.assertEqual({'DADPEGADCAC': -1}, self.cases['enum-signed-underlying-raw-boundary']['result'])
        enum = next(f for f in self.messages[ROOT]['fields'] if f['tag'] == 16)
        self.assertEqual(('int32', 35709), (enum['type'], enum['enumTypeIndex']))
        self.assertEqual({'BNJKCHONGGE_int64': 0}, self.cases['prop-value-oneof-zero']['result'])
        self.assertEqual({'AEJAFPNBBHO': -9223372036854775808}, self.cases['prop-value-independent-int64-signed-min']['result'])
        self.assertEqual({}, self.cases['root-scalar-wrong-wire-duplicate-zero']['result'])

    def test_complex_children_and_native_error_entries_stay_distinct(self):
        rejected = self.report['rejections']; self.assertEqual(11, len(rejected))
        self.assertEqual(4, sum(x['scope'] == 'explicit-scope-refusal' for x in rejected))
        self.assertEqual(4, sum(x['scope'] == 'explicit-input-model' for x in rejected))
        native = [x for x in rejected if x['scope'] == 'native-error-entry']
        self.assertEqual({'negative-length', 'truncated-message', 'recursion-limit'}, {x['error'].split('Native error branch: ')[1] for x in native})
        for typ in BLOCKED:
            self.assertTrue(any(str(typ) in x['error'] for x in rejected if x['scope'] == 'explicit-scope-refusal'))

    def test_result_drift_rejected(self):
        with self.assertRaisesRegex(ValueError, 'core native result differs'):
            verify(SimpleNamespace(run_graph=lambda *args: {'result': {'invented': True}}))


if __name__ == '__main__': unittest.main()
