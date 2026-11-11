import copy
import hashlib
import tempfile
import unittest
from pathlib import Path
from tools.migration72 import proud_gate_comparison_projection as s

class ProjectionTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.contract,cls.all,cls.active=s.load_sources()

    def test_all_605_leaves_and_four_source_joins(self):
        report=s.project(self.contract,self.all,self.active)
        self.assertEqual((len(report['rows']),report['scope']['slots'],len(report['fourSampleJoin'])),(121,605,4))
        for original,row in zip(self.all['samples'],report['rows']):
            field=next(x for x in original['fields'] if x['fieldIndex']==105355)
            for observed, projected in zip(field['value'],row['javaFields']['inherentProudSkillOpens']):
                leaves={x['fieldIndex']:x['value'] for x in observed}
                self.assertEqual(projected,{'needAvatarPromoteLevel':leaves[404853],'proudSkillGroupId':leaves[404854]})
            self.assertEqual(set(row['javaFields']),{'id','inherentProudSkillOpens'})
            for element in row['javaFields']['inherentProudSkillOpens']:
                self.assertEqual(set(element),{'needAvatarPromoteLevel','proudSkillGroupId'})
        self.assertTrue(all(not x for x in report['acceptance'].values()))

    def test_preserve_zeros_duplicates_and_unknown_presence(self):
        sample=copy.deepcopy(self.all['samples'][0]);f=next(x for x in sample['fields'] if x['fieldIndex']==105355)
        f['value'][1]=copy.deepcopy(f['value'][0]);f['getters'][2]['value']=f['getters'][0]['value'];f['getters'][3]['value']=f['getters'][1]['value']
        result=s.snapshot(sample,'manual',0)
        self.assertEqual(result['javaFields']['inherentProudSkillOpens'][0],result['javaFields']['inherentProudSkillOpens'][1])
        self.assertEqual(result['javaFields']['inherentProudSkillOpens'][3],{'proudSkillGroupId':0,'needAvatarPromoteLevel':0})
        self.assertEqual(result['rawSlots'][3]['group']['inputPresence'],'unknown')
        active=s.snapshot(self.active['samples'][0],'active',0)
        self.assertEqual(active['rawSlots'][0]['group']['inputPresence'],'unknown (not specified by report)')

    def test_reject_missing_null_wrong_fields_and_types(self):
        for mutation in ('field','null','element','value','index','owner','offset','type','getter'):
            sample=copy.deepcopy(self.all['samples'][0]);f=next(x for x in sample['fields'] if x['fieldIndex']==105355);leaf=f['value'][0][0]
            if mutation=='field':sample['fields'].remove(f)
            elif mutation=='null':f['value']=None
            elif mutation=='element':f['value'][0]=None
            elif mutation=='value':del leaf['value']
            elif mutation=='index':leaf['fieldIndex']=404855
            elif mutation=='owner':leaf['owner']=123
            elif mutation=='offset':leaf['offset']='0x14'
            elif mutation=='type':leaf['type']['typeDefinition']=123
            else:f['getters'][0]['value']+=1
            with self.subTest(mutation=mutation),self.assertRaises((ValueError,KeyError)):s.snapshot(sample,'manual',0)

    def test_unsigned_and_java_limit_errors_retain_raw(self):
        for value in (2147483648,4294967295,4294967296,-1):
            sample=copy.deepcopy(self.all['samples'][0]);f=next(x for x in sample['fields'] if x['fieldIndex']==105355);f['value'][0][0]['value']=value
            with self.subTest(value=value),self.assertRaisesRegex(ValueError,str(value)):s.snapshot(sample,'manual',0)

    def test_active_source_or_value_mismatch_rejected(self):
        for mutation in ('source','value'):
            active=copy.deepcopy(self.active);sample=active['samples'][0]
            if mutation=='source':sample['source']['begin']+=1
            else:
                field=next(x for x in sample['fields'] if x['fieldIndex']==105355);field['value'][0][0]['value']+=1;field['getters'][0]['value']+=1
            with self.subTest(mutation=mutation),self.assertRaises(ValueError):s.project(self.contract,self.all,active)

    def test_pin_drift_and_create_new(self):
        with tempfile.TemporaryDirectory() as d:
            p=Path(d)/'x';p.write_bytes(b'abc');h=hashlib.sha256(b'abc').hexdigest()
            self.assertEqual(s.checked(p,3,h),b'abc')
            with self.assertRaises(ValueError):s.checked(p,4,h)
            p.write_bytes(b'abd')
            with self.assertRaises(ValueError):s.checked(p,3,h)
            q=Path(d)/'o';s.save(q,{'ok':True})
            with self.assertRaises(FileExistsError):s.save(q,{'ok':False})

if __name__=='__main__':unittest.main()
