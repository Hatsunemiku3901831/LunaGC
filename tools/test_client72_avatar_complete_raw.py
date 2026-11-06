import json
import os
from pathlib import Path
from types import SimpleNamespace
import unittest
from tools.extract_client72 import HASHES
from tools.trace_client72_avatar_complete_raw import SOURCE_HASHES, verify


class AvatarCompleteRawTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        root = Path(__file__).resolve().parents[1]
        path = Path(os.environ.get('CLIENT72_AVATAR_COMPLETE_RAW_OBSERVATIONS', root / 'protocol/cn-7.2/avatar-complete-raw-observations.json'))
        cls.report = json.loads(path.read_text('utf8')); cls.cases = {x['name']: x for x in cls.report['fixtures']}
        cls.fields = {x['clientField']: x for x in cls.report['messages'][0]['fields']}

    def test_fixed_inputs_sources_and_only_raw_closure_promoted(self):
        self.assertEqual(HASHES, self.report['inputSHA256']); self.assertEqual(SOURCE_HASHES, self.report['sourceEvidenceSHA256'])
        self.assertEqual('F-03d3e8', self.report['task']); self.assertTrue(self.report['fullComplexRawClosure']); self.assertTrue(self.report['fullAvatarRawClosure'])
        self.assertFalse(self.report['targetProtocolReady']); self.assertEqual('not-tested', self.report['runtimeStatus']); self.assertEqual('unresolved', self.report['semanticIdentityStatus'])
        self.assertEqual([], self.report['blockedTypeIndices']); self.assertEqual(65, len(self.report['closureMessages']))
        self.assertEqual((372, 237), (self.report['closureWireFields'], self.report['complexWireFields']))

    def test_12_storage_slots_become_11_wire_with_tag100_and_no_discriminator_fields(self):
        self.assertEqual(12, self.report['dependencyScope']['metadataStorageFields']); self.assertEqual(11, len(self.fields)); self.assertEqual(512, self.report['scan']['nativeFixtures'])
        self.assertEqual({1, 2, 3, 4, 6, 7, 8, 9, 10, 11, 100}, {x['tag'] for x in self.fields.values()})
        self.assertNotIn('PNOEONGHLPL', self.fields); self.assertNotIn('BFCOKBPMILJ', self.fields)
        field = self.fields['IDBPHIMMHJP_100']; self.assertEqual((100, 40, 92, 100, 18849), (field['tag'], field['objectOffset'], field['discriminatorOffset'], field['discriminatorValue'], field['nestedTypeIndex']))
        for tag in (1, 2):
            field = self.fields['IPECOOBLNDN_' + str(tag)]; self.assertEqual((tag, 56, 100, tag), (field['tag'], field['objectOffset'], field['discriminatorOffset'], field['discriminatorValue']))
        covered = set().union(*(set(c['result']) for c in self.report['fixtures'] if c['typeIndex'] == 51050)); self.assertEqual(set(self.fields), covered)

    def test_complete_graph_enters_all_65_and_all_34_complex_types(self):
        closures = self.report['closureValidation']; self.assertEqual([34, 62, 65], [x['messageCount'] for x in closures])
        self.assertEqual(set(x['typeIndex'] for x in self.report['closureMessages']), set(closures[-1]['enteredMessageTypeIndices']))
        for name, count in [('53824-complete-all-nonempty', 12), ('AvatarInfo-complete-all-37-nonempty', 37), ('Avatar-complete-all-10-nonempty', 10)]:
            row = self.cases[name]; self.assertEqual(count, len(row['result'])); self.assertNotIn('untracedType', json.dumps(row['result']))
        root = self.cases['Avatar-complete-all-10-nonempty']['result']; avatar = root['JGECGMPNGBD'][0]
        self.assertEqual(9, len(avatar['JCKNHCDJFCE']['CLFDBPJOFLI'])); self.assertEqual(9, len(avatar['JCKNHCDJFCE']['BAKDLDCFCIF']))
        complex_node = avatar['AMLLHJJIMNI'][0]['KDKAEHEEMMJ']['PPDFGNJDAAE'][0]
        self.assertEqual(22, len(complex_node['GACCOGGKOOC'])); self.assertEqual(18, len(complex_node['KFELKFDIMCO'])); self.assertEqual(2, len(complex_node['KCBHOJACCKP']))

    def test_dual_objects_boxed_zero_switch_and_nonempty_tag100_independent(self):
        self.assertEqual({'IPECOOBLNDN_1': 4294967295}, self.cases['boxed-u32-first-upper']['result'])
        self.assertEqual({'IPECOOBLNDN_2': 7}, self.cases['boxed-u32-second-nonzero']['result'])
        self.assertEqual({'IPECOOBLNDN_1': 0}, self.cases['boxed-u32-first-explicit-zero']['result']); self.assertEqual({'IPECOOBLNDN_2': 0}, self.cases['boxed-u32-second-explicit-zero']['result'])
        self.assertEqual({'IPECOOBLNDN_1': 0}, self.cases['boxed-switch-first-second-first-zero']['result'])
        row = self.cases['two-objects-switch-independent']['result']; self.assertEqual(22, row['IPECOOBLNDN_2']); self.assertEqual([1, 128, 0, 4294967295], row['IDBPHIMMHJP_100']['ABBODGKNDEP'])

    def test_repeated_tag100_uses_actual_copy_and_four_native_range_copies(self):
        row = self.cases['tag100-repeated-copy-merge-and-empty-preserves']
        self.assertEqual({'IDBPHIMMHJP_100': {'ABBODGKNDEP': [1, 128, 7], 'KGMKPEBKEFF': [2, 255, 8]}}, row['result'])
        self.assertEqual(2, sum(x.get('kind') == 'native-18849-copy' and x['entry'] == '0x151509de0' for x in row['entered']))
        self.assertEqual(4, sum(x.get('kind') == 'native-copy-helper' and x['entry'] == '0x158db7cd0' for x in row['entered']))
        binding = self.report['nativeCopyBinding']; self.assertEqual('0x151509de0', binding['method']['registeredAddress']); self.assertEqual('0x158db87c0', binding['listRangeWrapper']['address']); self.assertEqual('0x158db7cd0', binding['nativeListRangeTarget'])
        self.assertEqual({'IDBPHIMMHJP_100': {}}, self.cases['tag100-explicit-empty-active']['result'])

    def test_nonempty_float_list_keeps_negative_zero_and_native_stores(self):
        row = self.cases['complex-float-packed-unpacked-bits']
        self.assertEqual([['0xbfa00000', '0x40200000', '0x80000000', '0x7f7fffff', '0x1']], row['complexFloatListBits'])
        self.assertEqual(5, len(row['floatListStores'])); self.assertTrue(all(x['source'] == '0x1571dc0c9' and x['size'] == 4 for x in row['floatListStores']))
        self.assertEqual({'KGDFJBJMNNJ': [-1.25]}, self.cases['complex-float-empty-packed-preserves']['result'])
        self.assertEqual((11, [2, 5]), (self.fields['KGDFJBJMNNJ']['tag'], self.fields['KGDFJBJMNNJ']['acceptedWireTypes']))
        self.assertEqual({'FEKDEEENNBB': [0, 1, 4294967295]}, self.cases['complex-u32-packed-unpacked']['result'])

    def test_complete_root_keeps_team_avatar_string_maps_and_item_copy(self):
        row = self.cases['root-team-map-and-avatar-maps-interleave']['result']
        self.assertEqual({'7': {'MGIDEJKCAKD': 'B'}}, row['CNCCIILAJNJ']); avatar = row['JGECGMPNGBD'][0]
        self.assertEqual({'7': {'NOEEBKJAFAG': 1}}, avatar['IKIFIBAEDGD']); self.assertEqual({'7': -1.25}, avatar['ADJINCFKMML']); self.assertEqual({'7': {'GMKNIJBNFLK': 3, 'ABAAMEKEIFJ': [4]}}, avatar['HLAIJJEMIHE'])
        row = self.cases['reused-same-item-six-nested-merge-and-bool-zero']['result']['FLPGMLOFAKP_6']['FLPGMLOFAKP_2']
        self.assertEqual({'7': 0}, row['JMGKMMFNABP']); self.assertEqual([1, 128, 0], row['FLGGIMBHFOO'])
        row = self.cases['Avatar-complete-all-10-nonempty']['result']['JGECGMPNGBD'][0]['AMLLHJJIMNI'][0]['GDLHHFGKOGO']
        self.assertEqual({'A': 300}, row['JMPEPGDDKPD'])

    def test_nested_64bit_fields_remain_exact(self):
        row = self.cases['51050-all-active-fields-nonempty']['result']
        self.assertEqual(-9223372036854775808, row['KCBHOJACCKP'][0]['ANKEOLKBECN']); self.assertEqual(9007199254740993, row['KCBHOJACCKP'][1]['ANKEOLKBECN'])
        self.assertEqual(18446744073709551615, row['KFELKFDIMCO']['NIHNNHMGENL'])
        row = self.cases['full-root-two-avatar-nonempty-list']['result']['JGECGMPNGBD']; self.assertEqual(2, len(row)); self.assertEqual({'NLNJDCDLHHE': 22, 'FBLKPHOCBIK': 18446744073709551615}, row[1])

    def test_errors_remain_native_entry_vs_explicit_input_models(self):
        rejected = self.report['rejections']; self.assertEqual(9, len(rejected)); self.assertEqual({'explicit-input-model': 4, 'native-error-entry': 5}, {scope: sum(x['scope'] == scope for x in rejected) for scope in {x['scope'] for x in rejected}})
        self.assertTrue(all('Native error branch:' in x['error'] for x in rejected if x['scope'] == 'native-error-entry'))
        self.assertEqual({}, self.cases['complex-unknown-wrong-wire-skipped']['result']); self.assertEqual({}, self.cases['complex-scalar-duplicate-zero']['result'])
        for index in (51050, 53824, 48178, 30509): self.assertEqual({}, self.cases['empty-' + str(index)]['result'])

    def test_result_drift_rejected(self):
        with self.assertRaisesRegex(ValueError, 'complete raw native result differs'):
            verify(SimpleNamespace(run_graph=lambda *args: {'result': {'invented': True}}))


if __name__ == '__main__': unittest.main()
