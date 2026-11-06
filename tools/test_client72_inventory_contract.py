"""Independent wire and bounded inventory contract regression checks."""
import unittest
import copy
import json
from pathlib import Path
from tools.client72_inventory_contract import material_item, store_wire, validate_result, fixture_cases, validate_report


class InventoryContractTests(unittest.TestCase):
    def test_independent_material_wire(self):
        self.assertEqual('08e9071081808080808080102a02082a',
                         material_item(1001, 9007199254740993, 42).hex())
        self.assertEqual('121008e9071081808080808080102a02082a7001',
                         store_wire([material_item(1001, 9007199254740993, 42)], 1).hex())

    def test_zero_material_still_selects_oneof(self):
        self.assertEqual('080010002a00', material_item(0, 0, 0).hex())

    def test_integer_domains(self):
        for values in [(True, 1, 1), (-1, 1, 1), (1, 1 << 64, 1), (1, 1, 1 << 32)]:
            with self.assertRaises(ValueError): material_item(*values)

    def test_fixture_scope(self):
        cases = fixture_cases()
        self.assertGreaterEqual(len(cases), 10)
        self.assertTrue(any(c['name'] == 'high-guid-collision' for c in cases))
        self.assertTrue(any(c['name'] == 'same-guid-overwrite' for c in cases))

    def test_validation_detects_wrong_count_and_guid(self):
        expected = dict(id=1001, guid=9007199254740993, count=42, category=2)
        validate_result(expected, dict(expected))
        for field in ('count', 'guid', 'id', 'category'):
            wrong = dict(expected); wrong[field] += 1
            with self.assertRaises(ValueError): validate_result(expected, wrong)


class InventoryEvidenceTests(unittest.TestCase):
    def setUp(self):
        self.report = json.loads((Path(__file__).resolve().parents[1] /
            'protocol/cn-7.2/inventory-contract-observations.json').read_text('utf8'))

    def test_native_evidence_contract(self):
        self.assertEqual([], validate_report(self.report))

    def test_scope_and_unknown_fields_cannot_be_promoted(self):
        for key, value in [('runtimeStatus', 'passed'), ('completeHandlerExecuted', True),
                           ('originalClientMessageNameRecovered', True), ('identityBasis', 'Asta-name'),
                           ('targetProtocolReady', True), ('models', []), ('unresolvedTags', {})]:
            row = copy.deepcopy(self.report); row[key] = value
            self.assertTrue(validate_report(row), key)
        row = copy.deepcopy(self.report)
        row['fieldBindings'].append(dict(typeIndex=68537, tag=8, semanticField='weight_limit'))
        self.assertTrue(validate_report(row))

    def test_native_business_entry_cannot_be_replaced_by_model(self):
        for entry in ('0x14e43a630', '0x150ed35e0', '0x148fed420', '0x14a1d8440'):
            row = copy.deepcopy(self.report)
            row['fixtures'][0]['applications'][0]['native']['prefix']['models'].append(entry)
            self.assertTrue(validate_report(row), entry)
        row = copy.deepcopy(self.report)
        row['fixtures'][0]['applications'][0]['guidIndex']['mutation']['models'].append('0x156f6f850')
        self.assertTrue(validate_report(row))

    def test_values_collision_and_zero_selection_are_checked(self):
        for key in ('id', 'guid', 'count', 'category', 'delta'):
            row = copy.deepcopy(self.report)
            row['fixtures'][0]['applications'][0]['result'][key] += 1
            self.assertTrue(validate_report(row), key)
        row = copy.deepcopy(self.report)
        next(x for x in row['fixtures'] if x['name'] == 'high-guid-collision')['applications'][1]['guidIndex']['entries'] = 1
        self.assertTrue(validate_report(row))
        row = copy.deepcopy(self.report)
        next(x for x in row['fixtures'] if x['name'] == 'zero-count')['applications'][0]['result']['selectedOperation'] = 'insert'
        self.assertTrue(validate_report(row))


if __name__ == '__main__': unittest.main()
