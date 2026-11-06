"""Independent Equip wire and bounded native-consumer evidence checks."""
import copy
import json
from pathlib import Path
import unittest

from tools.client72_equip_contract import equip_item, fixture_cases, validate_report


class EquipWireTests(unittest.TestCase):
    def test_independent_weapon_wire(self):
        self.assertEqual('08e907108180808080808010320d1209080310fa01180222001801',
            equip_item(1001, 9007199254740993, 2, bytes.fromhex('080310fa0118022200'), True).hex())

    def test_empty_equip_is_distinct_from_empty_weapon(self):
        self.assertEqual('080110023200', equip_item(1, 2, 0, b'', False).hex())
        self.assertEqual('0801100232021200', equip_item(1, 2, 2, b'', False).hex())

    def test_invalid_encoder_inputs(self):
        for values in [(True, 2, 2), (1, -1, 2), (1, 1 << 64, 2), (1, 2, 3)]:
            with self.assertRaises(ValueError): equip_item(*values, b'', False)

    def test_samples_include_affix_default_and_wrong_branch(self):
        names = {x['name'] for x in fixture_cases()}
        self.assertTrue({'weapon-empty-affix-default', 'weapon-unrelated-affix',
                         'reliquary-then-weapon', 'same-guid-overwrite'} <= names)


class EquipEvidenceTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.report=json.loads((Path(__file__).resolve().parents[1]/
            'protocol/cn-7.2/equip-contract-observations.json').read_text('utf8'))

    def test_committed_native_contract(self):
        self.assertEqual([],validate_report(self.report))

    def test_unknown_and_runtime_cannot_be_promoted(self):
        report=copy.deepcopy(self.report)
        report['unresolvedTags']['74063'].remove(2)
        report['targetProtocolReady']=True
        errors=validate_report(report)
        self.assertIn('Unknown Equip field promoted',errors)
        self.assertIn('Equip evidence scope changed',errors)

    def test_affix_level_cannot_be_old_refinement_minus_one(self):
        report=copy.deepcopy(self.report)
        row=report['fixtures'][0]
        row['consumers'][0]['selectedAffixes'][0][1]-=1
        row['roleAssignments'][0]['result']['pairs'][0][1]-=1
        errors=validate_report(report)
        self.assertIn('Native affix selection changed',errors)
        self.assertIn('Actor slot/level/affix transfer changed',errors)

    def test_native_codec_cannot_be_replaced_with_model(self):
        report=copy.deepcopy(self.report)
        report['fixtures'][0]['roleAssignments'][0]['native']['assignment']['models'].append('0x14b1046f0')
        self.assertIn('Actor native integer/affix codec changed',validate_report(report))

    def test_native_guid_and_error_scope_are_required(self):
        report=copy.deepcopy(self.report)
        report['fixtures'][0]['consumers'][0]['guidIndex']['nativeEntries'].remove('0x156f6f850')
        report['rejections'][-1]['scope']='native-reader-error-helper'
        errors=validate_report(report)
        self.assertIn('Native GUID insertion changed',errors)
        self.assertIn('Equip error scope changed',errors)

    def test_unknown_list_values_survive_clone(self):
        report=copy.deepcopy(self.report)
        row=next(x for x in report['fixtures'] if x['name']=='unknown-reliquary-fields-retained')
        row['constructions'][0]['result']['rawBody']['IHKPPDPBEML']=[99]
        self.assertIn('Native Equip clone values changed',validate_report(report))


if __name__ == '__main__': unittest.main()
