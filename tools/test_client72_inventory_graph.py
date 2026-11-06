import json
import os
from pathlib import Path
from types import SimpleNamespace
import unittest
from tools.extract_client72 import HASHES
from tools.trace_client72_player_store import EXPECTED_CLOSURE, ROOT, ITEM
from tools.trace_client72_inventory_containers import SOURCE_SHA
from tools.trace_client72_inventory_graph import CONTAINERS_SHA, verify


class CompleteInventoryGraphTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        root = Path(__file__).resolve().parents[1]
        path = Path(os.environ.get('CLIENT72_INVENTORY_GRAPH_OBSERVATIONS', root / 'protocol/cn-7.2/inventory-graph-observations.json'))
        cls.report = json.loads(path.read_text('utf8')); cls.cases = {x['name']: x for x in cls.report['fixtures']}

    def test_fixed_sources_full_raw_scope_and_unresolved_identity(self):
        self.assertEqual(HASHES, self.report['inputSHA256'])
        self.assertEqual(SOURCE_SHA, self.report['outerEvidenceSHA256'])
        self.assertEqual(CONTAINERS_SHA, self.report['containerEvidenceSHA256'])
        self.assertTrue(self.report['fullStoreRawClosure'])
        self.assertFalse(self.report['targetProtocolReady'])
        self.assertEqual('unresolved', self.report['semanticIdentityStatus'])
        self.assertEqual('not-tested', self.report['runtimeStatus'])

    def test_complete_fifteen_types_fortyseven_fields_and_all_oneofs(self):
        messages = {m['typeIndex']: m for m in self.report['messages']}
        self.assertEqual(EXPECTED_CLOSURE, set(messages))
        self.assertEqual(47, sum(len(m['fields']) for m in messages.values()))
        self.assertEqual(1920, self.report['scan']['nativeFixtures'])
        self.assertEqual({5, 6, 7, 8, 9, 10}, {f['tag'] for f in messages[ITEM]['fields'] if f.get('oneof')})
        self.assertEqual({2, 3, 4}, {f['tag'] for f in messages[28471]['fields'] if f.get('oneof')})
        self.assertEqual({1, 2}, {f['tag'] for f in messages[44169]['fields'] if f.get('oneof')})
        self.assertNotIn('empty-envelope-only', json.dumps(self.report['fixtures']))
        self.assertNotIn('untracedType', json.dumps(self.report['fixtures']))

    def test_root_list_covers_all_six_nonempty_item_branches(self):
        row = self.cases['root-six-nonempty-items']; values = row['result']['MBEEKCPJBEL']
        self.assertEqual(6, len(values))
        for tag, item in zip(range(5, 11), values):
            self.assertEqual(42, item['PJAGIOHBKLD'])
            self.assertEqual(9007199254740993, item['FBLKPHOCBIK'])
            self.assertTrue(item['FLPGMLOFAKP_' + str(tag)])
        self.assertEqual(6, sum(x.get('typeIndex') == ITEM for x in row['entered']))
        self.assertTrue({ROOT, ITEM, 28471, 28473, 44169, 64947, 61085, 14095}.issubset({x.get('typeIndex') for x in row['entered']}))

    def test_native_map_copy_and_list_copy_preserve_then_merge_wire(self):
        row = self.cases['same-item-ten-map-overwrite-list-append']
        self.assertEqual({'FLPGMLOFAKP_10': {'EIBDIEHEAKF': [1, 128, 128], 'JMGKMMFNABP': {'7': 0}}}, row['result'])
        entries = {x['entry'] for x in row['entered']}
        self.assertTrue({'0x15893c8b0', '0x15893c340', '0x15893d3a0', '0x158db7cd0', '0x15893e4d0'}.issubset(entries))
        self.assertTrue(row['mapStores'])
        nested = self.cases['same-item-six-nested-merge-and-bool-zero']['result']['FLPGMLOFAKP_6']
        self.assertNotIn('FAPABAJBMAK', nested)
        self.assertEqual([1, 128, 0], nested['FLPGMLOFAKP_2']['FLGGIMBHFOO'])
        self.assertEqual({'7': 0}, nested['FLPGMLOFAKP_2']['JMGKMMFNABP'])
        self.assertNotIn('MNCFEDPDEAG', nested['FLPGMLOFAKP_2'])

    def test_oneof_switches_return_empty_and_explicit_zero(self):
        self.assertEqual({'PNGOHOKGKHP_2': {}}, self.cases['tps-switch-and-return-empty']['result'])
        self.assertEqual({'FLPGMLOFAKP_2': {}}, self.cases['equip-switch-and-return-empty']['result'])
        self.assertEqual({'FLPGMLOFAKP_5': {}}, self.cases['item-switch-all-and-return-empty']['result'])
        self.assertEqual({'PNGOHOKGKHP_2': {'ALNBHHEJLKP': {'7': 0}}}, self.cases['same-tps-two-map-zero-counter-zero']['result'])
        for tag in (7, 9): self.assertEqual({'FLPGMLOFAKP_' + str(tag): {}}, self.cases['same-item-' + ('seven' if tag == 7 else 'nine') + '-zero']['result'])
        self.assertEqual({'PNGOHOKGKHP_3': {}}, self.cases['same-tps-three-zero']['result'])

    def test_nested_retention_zero_counts_and_u64_upper(self):
        self.assertNotIn('GNHFNECPAHD', self.cases['same-item-eight-keep-map-explicit-zero-count']['result']['FLPGMLOFAKP_8'])
        changed = self.cases['same-item-five-tps-type-switch']['result']['FLPGMLOFAKP_5']['PNGOHOKGKHP']
        self.assertEqual({'IJPBJCNCDEC': True, 'PNGOHOKGKHP_3': {'BHOHKJOCNOL': 99}}, changed)
        upper = self.cases['root-outer-upper-and-high-guid']['result']
        self.assertEqual(18446744073709551615, upper['MBEEKCPJBEL'][0]['FBLKPHOCBIK'])
        self.assertEqual(4294967295, upper['MBEEKCPJBEL'][0]['PJAGIOHBKLD'])
        self.assertEqual(4294967295, upper['PAKLDABINMP'])

    def test_native_error_entry_stops_separate_from_input_models(self):
        rows = {x['name']: x for x in self.report['rejections']}
        for name, helper in [('recursion-limit', '0x1509cb410'), ('negative-length', '0x1509cafa0'), ('truncated-nested', '0x1509cb360')]:
            self.assertEqual('native-error-branch', rows[name]['scope'])
            self.assertTrue(rows[name]['error'].startswith(helper + ': Native error branch:'))
        self.assertEqual(3, sum(x['scope'] == 'explicit-input-model' for x in rows.values()))

    def test_result_drift_is_rejected(self):
        with self.assertRaisesRegex(ValueError, 'full native graph differs'):
            verify(SimpleNamespace(run_graph=lambda *args: {'result': {}}))


if __name__ == '__main__': unittest.main()
