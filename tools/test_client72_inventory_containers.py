import json
import os
from pathlib import Path
import unittest
from types import SimpleNamespace
from tools.extract_client72 import HASHES
from tools.trace_client72_inventory_containers import TYPES, SOURCE_SHA, verify


class InventoryPrimitiveContainersTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        root = Path(__file__).resolve().parents[1]
        path = Path(os.environ.get('CLIENT72_INVENTORY_CONTAINER_OBSERVATIONS', root / 'protocol/cn-7.2/inventory-container-observations.json'))
        cls.report = json.loads(path.read_text('utf8')); cls.cases = {x['name']: x for x in cls.report['fixtures']}

    def test_input_source_and_scope_boundaries(self):
        self.assertEqual(HASHES, self.report['inputSHA256'])
        self.assertEqual(SOURCE_SHA, self.report['outerEvidenceSHA256'])
        self.assertEqual('unresolved', self.report['semanticIdentityStatus'])
        self.assertFalse(self.report['fullStoreRawClosure'])
        self.assertEqual(TYPES, {x['typeIndex'] for x in self.report['messages']})

    def test_all_twenty_two_fields_and_packed_unpacked_branches(self):
        self.assertEqual(768, self.report['scan']['nativeFixtures'])
        fields = [f for m in self.report['messages'] for f in m['fields']]
        self.assertEqual(22, len(fields))
        self.assertEqual(5, sum(f['cardinality'] == 'map' for f in fields))
        self.assertEqual(5, sum(f['cardinality'] == 'repeated' for f in fields))
        self.assertTrue(all(f['acceptedWireTypes'] == [0, 2] for f in fields if f['cardinality'] == 'repeated'))
        self.assertTrue(all(f['semanticStatus'] == 'unresolved' for f in fields))

    def test_concrete_u32_u32_aot_is_not_shared_message_map(self):
        rows = self.report['primitiveMapAOT']
        expected = {(17961, 'FNNADLCNGFO'): '0x15893d460', (17961, 'set_Item'): '0x15893e4d0',
                    (17964, 'MOMMACBEMEK'): '0x158935d30', (17964, '.ctor'): '0x158935cd0'}
        for key, address in expected.items():
            row = next(x for x in rows if (x['owner'], x['method']) == key)
            self.assertEqual(address, row['address']); self.assertEqual(3723, row['classInstance'])
            self.assertEqual(['uint32', 'uint32'], [a['kind'] for a in row['arguments']])

    def test_real_cctor_factory_tags_and_native_static_stores(self):
        rows = {x['typeIndex']: x for x in self.report['initializers']}
        self.assertEqual([18, 8, 16], rows[14095]['factoryTags'])
        self.assertEqual([42, 58, 66], rows[74063]['factoryTags'])
        self.assertEqual([8, 16, 50], rows[64947]['factoryTags'])
        self.assertEqual([(0x228d0, 26), (0x228d8, 18)], [(x['staticOffset'], x['tag']) for x in rows[14095]['stores']])

    def test_independent_maps_defaults_upper_and_zero_overwrite(self):
        row = self.cases['map-nonzero-61085']
        self.assertEqual('0a040807100b', row['wire']); self.assertEqual({'ALNBHHEJLKP': {'7': 11}}, row['result'])
        zero = self.cases['map-duplicate-zero-61085']
        self.assertEqual({'ALNBHHEJLKP': {'7': 0}}, zero['result'])
        self.assertTrue(zero['mapStores'])
        self.assertTrue(any(x['source'] == '0x15893e565' for x in zero['mapStores']))
        self.assertEqual({'ALNBHHEJLKP': {'4294967295': 4294967295, '0': 0}}, self.cases['map-upper-and-default-61085']['result'])
        self.assertEqual({'ALNBHHEJLKP': {'7': 0}}, self.cases['map-key-only']['result'])
        self.assertEqual({'ALNBHHEJLKP': {'0': 11}}, self.cases['map-value-only']['result'])
        self.assertEqual({'ALNBHHEJLKP': {'8': 12}}, self.cases['map-entry-duplicates-and-unknown']['result'])

    def test_list_append_keeps_zero_and_full_width(self):
        self.assertEqual({'EIBDIEHEAKF': [1, 128, 255, 0]}, self.cases['list-packed-unpacked-append']['result'])
        self.assertEqual({'EIBDIEHEAKF': [0, 4294967295]}, self.cases['list-zero-and-upper']['result'])
        self.assertEqual({'FOFCJMPCEBB': [1, 128], 'IMEGOOADEBF': [2, 128], 'IHKPPDPBEML': [3, 128]}, self.cases['three-lists']['result'])
        self.assertTrue(any(x.get('entry') == '0x158db8e10' for x in self.cases['three-lists']['entered']))

    def test_raw_scalars_empty_and_error_models(self):
        self.assertIs(True, self.cases['all-six-64947']['result']['MNCFEDPDEAG'])
        self.assertEqual({'NJAGFJINHMA': 2}, self.cases['scalar-duplicate-wrong-wire-and-bool-zero']['result'])
        self.assertTrue(all(self.cases['empty-' + str(i)]['result'] == {} for i in TYPES))
        self.assertEqual(3, len(self.report['rejections']))
        self.assertTrue(all(x['scope'] == 'explicit-input-model' for x in self.report['rejections']))

    def test_result_drift_cannot_publish(self):
        with self.assertRaisesRegex(ValueError, 'primitive native result differs'):
            verify(SimpleNamespace(run_graph=lambda *args: {'result': {}}))


if __name__ == '__main__': unittest.main()
