import json
import os
from pathlib import Path
from types import SimpleNamespace
import unittest
from tools.extract_client72 import HASHES
from tools.trace_client72_avatar_items import ROOT, NEW, INVENTORY_SHA, verify


class AvatarItemsRawTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        root = Path(__file__).resolve().parents[1]
        path = Path(os.environ.get('CLIENT72_AVATAR_ITEMS_OBSERVATIONS', root / 'protocol/cn-7.2/avatar-items-observations.json'))
        cls.report = json.loads(path.read_text('utf8')); cls.cases = {x['name']: x for x in cls.report['fixtures']}
        cls.messages = {x['typeIndex']: x for x in cls.report['messages']}

    def test_hash_bound_dependency_and_wire_counts_exclude_store_root(self):
        self.assertEqual(HASHES, self.report['inputSHA256'])
        self.assertEqual(INVENTORY_SHA, self.report['inventoryEvidenceSHA256'])
        audit = self.report['dependencyAudit']
        self.assertEqual((16, 45, 14), (audit['typeCount'], audit['instanceStorageFields'], audit['reusedTypeCount']))
        self.assertEqual(sorted(NEW), audit['newTypeIndices'])
        self.assertEqual([28471, 44169, 65333], audit['objectOwners'])
        self.assertNotIn(68537, self.messages)
        self.assertEqual(16, len(self.messages))
        self.assertEqual(50, sum(len(m['fields']) for m in self.messages.values()))
        self.assertEqual(6, sum(len(self.messages[i]['fields']) for i in NEW))
        self.assertEqual(2048, self.report['scan']['nativeFixtures'])
        self.assertTrue(self.report['fullSelectedItemRawClosure'])
        self.assertFalse(self.report['fullAvatarRawClosure'])
        self.assertFalse(self.report['targetProtocolReady'])
        self.assertEqual('unresolved', self.report['semanticIdentityStatus'])
        self.assertEqual([53824], self.report['blockedTypeIndices'])

    def test_two_same_item_list_shapes_and_leaf_have_native_bindings(self):
        fields = {f['tag']: f for f in self.messages[16352]['fields']}
        self.assertEqual(('CLFDBPJOFLI', 32, 65333), (fields[4]['clientField'], fields[4]['objectOffset'], fields[4]['nestedTypeIndex']))
        self.assertEqual(('BAKDLDCFCIF', 40, 65333), (fields[2]['clientField'], fields[2]['objectOffset'], fields[2]['nestedTypeIndex']))
        self.assertEqual(('BBMFJFKAECB', 24, 71717), (fields[3]['clientField'], fields[3]['objectOffset'], fields[3]['nestedTypeIndex']))
        leaf = {f['tag']: (f['clientField'], f['objectOffset']) for f in self.messages[71717]['fields']}
        self.assertEqual({1: ('JEDCIENABEB', 28), 2: ('JCPAOHDANBO', 24)}, leaf)
        aot = next(x for x in self.report['aotBindings'] if x['method'] == 'FNNADLCNGFO')
        self.assertEqual((25060, 10366, '0x158dc41f0', 65333), (aot['owner'], aot['classInstance'], aot['address'], aot['arguments'][0]['typeDefinition']))

    def test_each_list_has_six_nonempty_branches_with_distinct_id_and_uint64(self):
        result = self.cases['16352-all-fields-two-distinct-lists-six-branches-each']['result']
        self.assertEqual(4, len(result))
        left, right = result['CLFDBPJOFLI'], result['BAKDLDCFCIF']
        self.assertEqual([105, 106, 107, 108, 109, 110], [x['PJAGIOHBKLD'] for x in left])
        self.assertEqual([205, 206, 207, 208, 209, 210], [x['PJAGIOHBKLD'] for x in right])
        self.assertEqual([9007199254740998, 9007199254740999, 9007199254741000, 9007199254741001, 9007199254741002, 9007199254741003], [x['FBLKPHOCBIK'] for x in left])
        self.assertEqual([18446744073709551610, 18446744073709551609, 18446744073709551608, 18446744073709551607, 18446744073709551606, 18446744073709551605], [x['FBLKPHOCBIK'] for x in right])
        for index, tag in enumerate((5, 6, 7, 8, 9, 10)):
            self.assertTrue(left[index]['FLPGMLOFAKP_' + str(tag)])
            self.assertTrue(right[index]['FLPGMLOFAKP_' + str(tag)])

    def test_avatar_info_full_composition_preserves_maps_lists_and_unknown_complex_child(self):
        row = self.cases['AvatarInfo-all-37-fields-three-nonempty-child-trees']
        self.assertEqual(ROOT, row['typeIndex']); result = row['result']
        self.assertEqual(37, len(result))
        self.assertEqual(9, len(result['JCKNHCDJFCE']['CLFDBPJOFLI']))
        self.assertEqual(9, len(result['JCKNHCDJFCE']['BAKDLDCFCIF']))
        self.assertEqual(55, result['CKDDHADKCBK']['CBIONIIBDIN']['CNGEAFGELMF'][0]['KIIGMHLMLMA'])
        self.assertEqual(72, result['PKPEFNIHPLA']['PHCBOKNHIHH'])
        self.assertEqual(9007199254740993, result['FBLKPHOCBIK'])
        self.assertEqual({'7': -1.25}, result['ADJINCFKMML'])
        self.assertEqual([{'untracedType': 53824, 'content': 'empty-envelope-only'}], result['AMLLHJJIMNI'])
        self.assertTrue(set(self.messages) <= {x['typeIndex'] for x in row['entered'] if 'typeIndex' in x})

    def test_reused_oneofs_and_real_map_list_copy_helpers(self):
        self.assertEqual(30, sum(x.startswith('reused-') for x in self.cases))
        oneof = [f for f in self.messages[65333]['fields'] if f.get('oneof')]
        self.assertEqual([5, 6, 7, 8, 9, 10], [f['discriminatorValue'] for f in oneof])
        result = self.cases['reused-item-switch-all-and-return-empty']['result']
        self.assertEqual({'FLPGMLOFAKP_5': {}}, result)
        row = self.cases['reused-same-item-six-nested-merge-and-bool-zero']
        merged = row['result']['FLPGMLOFAKP_6']['FLPGMLOFAKP_2']
        self.assertEqual({'7': 0}, merged['JMGKMMFNABP'])
        self.assertEqual([1, 128, 0], merged['FLGGIMBHFOO'])
        self.assertNotIn('MNCFEDPDEAG', merged)
        self.assertTrue(any(x.get('kind') == 'native-copy-helper' for x in row['entered']))
        self.assertTrue(row['mapStores'])

    def test_empty_fresh_list_elements_nested_merge_and_zero(self):
        self.assertEqual({'CLFDBPJOFLI': [{}, {}], 'BAKDLDCFCIF': [{}]}, self.cases['16352-explicit-empty-items-in-each-list']['result'])
        self.assertEqual({'BBMFJFKAECB': {'JEDCIENABEB': 2}}, self.cases['16352-singular-leaf-merge-and-zero']['result'])
        self.assertEqual({}, self.cases['71717-duplicate-zero-and-wrong-wire']['result'])
        self.assertEqual({}, self.cases['16352-scalar-wrong-wire-duplicate-zero']['result'])
        self.assertEqual({'CLFDBPJOFLI': [{'PJAGIOHBKLD': 1, 'FBLKPHOCBIK': 2}, {'PJAGIOHBKLD': 5, 'FBLKPHOCBIK': 6}], 'BAKDLDCFCIF': [{'PJAGIOHBKLD': 3, 'FBLKPHOCBIK': 4}]}, self.cases['16352-interleaved-lists-append']['result'])

    def test_rejections_distinguish_model_native_error_entry_and_remaining_scope(self):
        rejected = self.report['rejections']; self.assertEqual(10, len(rejected))
        self.assertEqual(1, sum(x['scope'] == 'explicit-scope-refusal' for x in rejected))
        self.assertEqual(4, sum(x['scope'] == 'explicit-input-model' for x in rejected))
        self.assertEqual(5, sum(x['scope'] == 'native-error-entry' for x in rejected))
        native = [x for x in rejected if x['scope'] == 'native-error-entry']
        self.assertEqual({'negative-length', 'truncated-message', 'recursion-limit'}, {x['error'].split('Native error branch: ')[1] for x in native})
        self.assertTrue(all(f['semanticStatus'] == 'unresolved' for m in self.messages.values() for f in m['fields']))

    def test_result_drift_rejected(self):
        with self.assertRaisesRegex(ValueError, 'Avatar Item native result differs'):
            verify(SimpleNamespace(run_graph=lambda *args: {'result': {'invented': True}}))


if __name__ == '__main__': unittest.main()
