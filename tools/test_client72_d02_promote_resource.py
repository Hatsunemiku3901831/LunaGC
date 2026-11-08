"""Real zero/nonzero Promote rows, float/enum/reference rejection and curve shape."""
import copy
import json
from pathlib import Path
import struct
import unittest

from tools import client72_d02_promote_resource as subject


class PromoteResourceTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        capture = subject.RAW / 'probe02.json'
        raw = subject.card.base.read_verified(capture,'23733329e6fc6c0503cefe7fbc38baca18d9e4c4627a66a78b2f173d35ac1294')
        cls.tables = {x['name']:x for x in json.loads(raw)['tables']}
        baseline = json.loads(subject.card.base.read_verified(subject.equip.OLD_REPORT,
            subject.equip.PINS[subject.equip.OLD_REPORT]))
        cls.enums = {x['typeIndex']:x for x in baseline['tables'][0]['enumSymbols']}
        cls.enums[58570] = dict(typeIndex=58570,values=[dict(value=0,symbol='ARITH_NONE'),
            dict(value=1,symbol='ARITH_ADD'),dict(value=2,symbol='ARITH_MULTI'),
            dict(value=3,symbol='ARITH_SUB'),dict(value=4,symbol='ARITH_DIVIDE')])
        cls.java_fight = {x['symbol']:x['value'] for x in cls.enums[32419]['values']}

    def row(self,name,phase):
        group = subject.SPECS[name]['group']
        return copy.deepcopy(next(x for x in self.tables[name]['rows'] if x.get('group')==group and x['phase']==phase))

    def project(self,row,name='WeaponPromote',items=frozenset()):
        return subject.project_promote_row(row,name,self.enums,self.java_fight,items)

    def prop_value(self,row,index=0):
        props = next(x['value'] for x in row['fields'] if x['offset']=='0x10')
        return next(x['value'] for x in props[index] if x['offset']=='0x4')

    def curve_table(self):
        return copy.deepcopy(self.tables['AvatarCurve'])

    def curves(self,table):
        return subject.project_curves(table,self.enums,{'GROW_CURVE_HP_S4','GROW_CURVE_ATTACK_S4'})

    def test_weapon_zero_phase_preserves_actual_five_props_three_slots(self):
        out = self.project(self.row('WeaponPromote',0))
        self.assertEqual(out['key'],11101<<8)
        self.assertEqual([x['propType'] for x in out['javaFields']['addProps']],
            ['FIGHT_PROP_BASE_ATTACK','FIGHT_PROP_CRITICAL','FIGHT_PROP_CRITICAL_HURT',
             'FIGHT_PROP_CHARGE_EFFICIENCY','FIGHT_PROP_ELEMENT_MASTERY'])
        self.assertEqual(out['javaFields']['costItems'],[dict(id=0,count=0)]*3)
        self.assertTrue(all(struct.pack('<f',x['value'])==bytes(4) for x in out['javaFields']['addProps']))
        self.assertEqual(out['onLoadSourceModel']['addProps'],[])
        self.assertEqual(out['onLoadSourceModel']['costItems'],[])
        self.assertTrue(out['completeBirthInputRow'])

    def test_avatar_zero_phase_preserves_actual_four_props_four_slots(self):
        out = self.project(self.row('AvatarPromote',0),'AvatarPromote')
        self.assertEqual(out['key'],12<<8)
        self.assertEqual([x['propType'] for x in out['javaFields']['addProps']],
            ['FIGHT_PROP_BASE_HP','FIGHT_PROP_BASE_DEFENSE','FIGHT_PROP_BASE_ATTACK','FIGHT_PROP_ATTACK_PERCENT'])
        self.assertEqual(out['javaFields']['costItems'],[dict(id=0,count=0)]*4)
        self.assertFalse(out['allDeclaredGameFieldsMapped'])
        self.assertIn('requiredPlayerLevel',out['omittedNotAssertedAsSourceDefaults'])

    def test_actual_nonzero_weapon_phase_one_and_nested_materials(self):
        out = self.project(self.row('WeaponPromote',1),items={114001,112014,112011})
        self.assertEqual(struct.pack('<f',out['javaFields']['addProps'][0]['value']).hex(),'33333b41')
        self.assertEqual(out['javaFields']['costItems'],
            [dict(id=114001,count=1),dict(id=112014,count=1),dict(id=112011,count=1)])
        self.assertEqual(len(out['onLoadSourceModel']['addProps']),1)
        self.assertFalse(out['completeBirthInputRow'])

    def test_actual_nonzero_avatar_phase_two_preserves_zero_id_nonzero_count(self):
        out = self.project(self.row('AvatarPromote',2),'AvatarPromote',{104102,100024,112005})
        self.assertEqual(out['javaFields']['costItems'][1],dict(id=0,count=2))
        self.assertEqual(len(out['onLoadSourceModel']['costItems']),3)
        self.assertEqual(len(out['onLoadSourceModel']['addProps']),4)

    def test_missing_positive_material_is_rejected(self):
        with self.assertRaisesRegex(ValueError,'Missing positive material'):
            self.project(self.row('WeaponPromote',1),items={114001,112014})

    def test_unknown_fight_enum_is_rejected(self):
        row = self.row('WeaponPromote',0)
        row['fields'][0]['value'][0][0]['value']['value']=0x7fffffff
        with self.assertRaisesRegex(ValueError,'Unknown or nonmatching'):
            self.project(row)

    def test_java_named_enum_value_mismatch_is_rejected(self):
        java = self.java_fight | {'FIGHT_PROP_BASE_ATTACK':999}
        with self.assertRaisesRegex(ValueError,'Unknown or nonmatching'):
            subject.project_promote_row(self.row('WeaponPromote',0),'WeaponPromote',self.enums,java,set())

    def test_float32_recorded_bits_mismatch_is_rejected(self):
        row=self.row('WeaponPromote',1)
        self.prop_value(row)['bits']='413b3334'
        with self.assertRaisesRegex(ValueError,'differs from its recorded bits'):
            self.project(row)

    def test_nonfinite_float_is_rejected(self):
        row=self.row('WeaponPromote',0)
        self.prop_value(row).update(bits='7f800000',float32=float('inf'))
        with self.assertRaisesRegex(ValueError,'nonfinite'):
            self.project(row)

    def test_null_array_cannot_stand_in_for_actual_empty_or_nonempty(self):
        row=self.row('WeaponPromote',0)
        row['fields'][0]['value']=None
        with self.assertRaisesRegex(ValueError,'nonnull actual arrays'):
            self.project(row)

    def test_composite_phase_overflow_is_rejected(self):
        row=self.row('WeaponPromote',0)
        row['phase']=256
        next(f for f in row['fields'] if f['offset']=='0x2c')['value']=256
        with self.assertRaisesRegex(ValueError,'composite key'):
            self.project(row)

    def test_64_bit_resource_id_is_not_silently_narrowed(self):
        row=self.row('WeaponPromote',0)
        row['group']=0x8000000100000001
        next(f for f in row['fields'] if f['offset']=='0x24')['value']=row['group']
        with self.assertRaisesRegex(ValueError,'Java int'):
            self.project(row)

    def test_inherited_actual_native_high_guid_remains_64_bit(self):
        raw=subject.card.base.read_verified(subject.EQUIPMENT_REPORT,subject.PINS[subject.EQUIPMENT_REPORT])
        evidence=json.loads(raw)['consumers']['initialWeapon']
        self.assertEqual(len(evidence),3)
        for case in evidence:
            self.assertTrue(case['guidPreserved'])
            self.assertEqual(int(case['syntheticGuidDecimal']),0x8000000100000001)
            self.assertEqual(case['syntheticGuidBits'],'8000000100000001')
            self.assertFalse(case['fullProducerExecuted'])

    def test_actual_contiguous_100_levels_four_ordered_multiplier_curves(self):
        out=self.curves(self.curve_table())
        self.assertEqual([x['key'] for x in out],list(range(1,101)))
        self.assertTrue(all(x['completeDeclaredJavaInputRow'] for x in out))
        self.assertEqual([x['type'] for x in out[0]['javaFields']['curveInfos']],
            ['GROW_CURVE_HP_S4','GROW_CURVE_ATTACK_S4','GROW_CURVE_HP_S5','GROW_CURVE_ATTACK_S5'])
        self.assertTrue(all(x['value']==1.0 for x in out[0]['javaFields']['curveInfos']))
        self.assertTrue(all(x['arith']=='ARITH_MULTI' for r in out for x in r['javaFields']['curveInfos']))

    def test_missing_growth_reference_is_rejected(self):
        table=self.curve_table()
        table['rows'][19]['fields'][0]['value'].pop(1)
        with self.assertRaisesRegex(ValueError,'Missing positive Avatar growth'):
            self.curves(table)

    def test_duplicate_curve_is_rejected(self):
        table=self.curve_table()
        table['rows'][0]['fields'][0]['value'].append(copy.deepcopy(table['rows'][0]['fields'][0]['value'][0]))
        with self.assertRaisesRegex(ValueError,'Duplicate curve'):
            self.curves(table)

    def test_non_multiplier_curve_is_rejected(self):
        table=self.curve_table()
        table['rows'][0]['fields'][0]['value'][0][1]['value']['value']=1
        with self.assertRaisesRegex(ValueError,'MULTI'):
            self.curves(table)

    def test_unknown_curve_enum_is_rejected(self):
        table=self.curve_table()
        table['rows'][0]['fields'][0]['value'][0][0]['value']['value']=0xfffffffe
        with self.assertRaisesRegex(ValueError,'Unknown or nonmatching'):
            self.curves(table)

    def test_noncontiguous_level_is_rejected(self):
        table=self.curve_table()
        table['rows'].pop(49)
        with self.assertRaisesRegex(ValueError,'contiguous actual levels'):
            self.curves(table)

    def test_level_field_cache_key_mismatch_is_rejected(self):
        table=self.curve_table()
        table['rows'][0]['fields'][1]['value']=2
        with self.assertRaisesRegex(ValueError,'native cache key'):
            self.curves(table)


if __name__ == '__main__':
    unittest.main()
