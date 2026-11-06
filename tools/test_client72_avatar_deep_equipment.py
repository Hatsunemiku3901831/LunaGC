"""Independent slot-query ordering, full GUID and fallback assertions."""
import hashlib
import json
import os
from pathlib import Path
import unittest

from tools.client72_avatar_deep_equipment import INPUTS
from tools.extract_client72 import HASHES


class AvatarEquipmentLookupTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.root=Path(__file__).resolve().parents[1]
        cls.report=json.loads(Path(os.environ.get('CLIENT72_AVATAR_EQUIPMENT_OBSERVATIONS',
            cls.root/'protocol/cn-7.2/avatar-deep-equipment-observations.json')).read_text('utf8'))
        cls.rows={row['name']:row for row in cls.report['native']['fixtures']}

    def test_fixed_inputs_and_exact_partial_scope(self):
        self.assertEqual(HASHES,self.report['inputSHA256']);self.assertEqual(INPUTS,self.report['inputContracts'])
        for name,digest in self.report['sourceSHA256'].items():self.assertEqual(digest,hashlib.sha256((self.root/name).read_bytes()).hexdigest())
        self.assertEqual(9,len(self.rows));self.assertTrue(self.report['completeSlotLookup'])
        for flag in ('completeEquipmentAssignment','completeAvatarHandler','completeConstructor','targetProtocolReady'):self.assertFalse(self.report[flag])
        self.assertEqual('not-tested',self.report['runtimeStatus'])

    def test_exact_equipment_hierarchy_and_native_registration(self):
        hierarchy={row['typeIndex']:row['parent']['typeDefinition'] for row in self.report['declarations']['hierarchy']}
        self.assertEqual(25683,hierarchy[51522]);self.assertEqual(25683,hierarchy[87122])
        rows=self.report['native']['registrations']
        self.assertEqual([(51522,0xffffffffffffffff,6),(51522,0x200000001,6),(87122,0x100000001,1)],
            [(row['type'],row['guid'],row['slot']) for row in rows])
        for row in rows:
            self.assertEqual('0x148aa58d0',row['setter']['entry'])
            self.assertEqual('0x14e9b2a80',row['registration']['entry'])
            self.assertIn('0x156f6f850',row['registration']['nativeEntries'])
            self.assertNotIn('0x156f6f850',row['registration']['models'])

    def test_missing_and_wrong_model_class_are_skipped(self):
        row=self.rows['lookup-missing-and-wrong-class-before-weapon']
        self.assertEqual([999,0x8000000000000001,0xffffffffffffffff,0x100000001],row['guids'])
        self.assertEqual(0xffffffffffffffff,row['foundGuid'])
        self.assertEqual('0x620000100',row['trace']['stop'])

    def test_slot_match_preserves_high_guid_and_first_order(self):
        self.assertEqual(0x100000001,self.rows['lookup-reliquary-slot']['foundGuid'])
        self.assertEqual(0x200000001,self.rows['lookup-first-matching-weapon']['foundGuid'])
        for name in ('missing-slot','empty'):self.assertIsNone(self.rows['lookup-'+name]['foundGuid'])
        for name,row in self.rows.items():
            if not name.startswith('lookup-'):continue
            self.assertEqual('0x151845920',row['trace']['entry'])
            self.assertNotIn('0x151845920',row['trace']['models'])
            self.assertNotIn('0x14e9b2c60',row['trace']['models'])

    def test_nonzero_guid_directly_overwrites_without_slot_query(self):
        row=self.rows['guid-fallback-explicit-high']
        self.assertEqual(0x8000000000000099,row['stored'])
        self.assertNotIn('0x151845920',row['trace']['nativeEntries'])
        self.assertEqual([],row['trace']['models'])

    def test_zero_guid_fallback_selects_weapon_or_clears_old_value(self):
        self.assertEqual(0x200000001,self.rows['guid-fallback-zero-finds-first']['stored'])
        self.assertEqual(0,self.rows['guid-fallback-zero-no-weapon']['stored'])
        for name in ('zero-finds-first','zero-no-weapon'):
            self.assertIn('0x151845920',self.rows['guid-fallback-'+name]['trace']['nativeEntries'])
            self.assertEqual('0x620000100',self.rows['guid-fallback-'+name]['trace']['stop'])

    def test_null_model_list_is_distinct_from_empty(self):
        self.assertEqual('0x1518459da',self.rows['null-list-before-native-error']['trace']['stop'])
        self.assertEqual([],self.rows['null-list-before-native-error']['trace']['models'])
        self.assertEqual('0x620000100',self.rows['lookup-empty']['trace']['stop'])


if __name__=='__main__':unittest.main()
