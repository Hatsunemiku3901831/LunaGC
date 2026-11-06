"""Independent wire and bounded Avatar consumer contract checks."""
import copy
import json
from pathlib import Path
import unittest

from tools.client72_avatar_contract import avatar_wire, validate_report


class AvatarWireTests(unittest.TestCase):
    def test_independent_high_guid_wire(self):
        self.assertEqual('08e907108180808080808010288180808010',
            avatar_wire(1001,9007199254740993,[4294967297],[]).hex())

    def test_independent_property_map_wire(self):
        self.assertEqual('080110021a090807120508a11f102a',
            avatar_wire(1,2,[],[(7,4001,2,42,0)]).hex())

    def test_invalid_identity_boundaries(self):
        for aid,guid in [(True,1),(-1,1),(1,1<<64)]:
            with self.assertRaises(ValueError):avatar_wire(aid,guid,[],[])


class AvatarEvidenceTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.report=json.loads((Path(__file__).resolve().parents[1]/
            'protocol/cn-7.2/avatar-contract-observations.json').read_text('utf8'))

    def test_committed_contract(self):
        self.assertEqual([],validate_report(self.report))

    def test_current_avatar_and_full_runtime_remain_unverified(self):
        report=copy.deepcopy(self.report)
        report['unresolvedTags']['30509'].remove(3)
        report['completeAvatarHandler']=True
        errors=validate_report(report)
        self.assertIn('Unknown Avatar fields promoted',errors)
        self.assertIn('Avatar contract scope changed',errors)

    def test_only_three_property_ids_are_approved(self):
        report=copy.deepcopy(self.report)
        report['propertyIds']['10013']='PROP_PLAYER_LEVEL'
        self.assertIn('Unapproved Avatar property enum added',validate_report(report))

    def test_outer_map_key_is_not_property_id(self):
        report=copy.deepcopy(self.report)
        report['fixtures'][0]['models'][0]['properties']['properties'][0]['type']=7
        self.assertIn('Avatar property ID/value/encoding changed',validate_report(report))

    def test_id_codec_must_execute(self):
        report=copy.deepcopy(self.report)
        report['fixtures'][0]['models'][0]['identity']['trace']['models'].append('0x14b1046f0')
        self.assertIn('Native Avatar ID/resource codec replaced with fixture',validate_report(report))

    def test_full_equipment_guid_is_preserved(self):
        report=copy.deepcopy(self.report)
        report['fixtures'][0]['models'][0]['equipment']['delivered'][0]['guid']=1
        self.assertIn('Avatar equipment GUID source changed',validate_report(report))

    def test_scope_refusal_cannot_be_native_error(self):
        report=copy.deepcopy(self.report)
        report['rejections'][-2]['scope']='native-reader-error-helper'
        self.assertIn('Avatar rejection scope changed',validate_report(report))


if __name__=='__main__':unittest.main()
