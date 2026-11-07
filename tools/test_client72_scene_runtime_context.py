"""Independent assertions for the fixed-file Scene runtime-context report."""
import hashlib
import json
import os
import unittest
from pathlib import Path

from .client72_scene_runtime_context import decode_primary_reference, JAVA_INPUTS, SNAPSHOT_SHA


class SceneRuntimeContextTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        path=Path(os.environ.get('CLIENT72_SCENE_RUNTIME_REPORT',
            'local/migration72/d02-active/scene-runtime-context-astra/final-a.json'))
        cls.report=json.loads(path.read_text(encoding='utf8'))

    def test_inputs_and_exact_sources(self):
        r=self.report
        for path,digest in r['sourceSHA256'].items():
            self.assertEqual(hashlib.sha256(Path(path).read_bytes()).hexdigest(),digest,path)
        self.assertIn(SNAPSHOT_SHA,r['sourceSHA256'].values())
        self.assertEqual(r['javaSourceSHA256'],JAVA_INPUTS)
        for path,digest in JAVA_INPUTS.items():
            self.assertEqual(hashlib.sha256(Path(path).read_bytes()).hexdigest(),digest)
        for path,digest in r['observations']['sources'].items():
            self.assertEqual(hashlib.sha256(Path(path).read_bytes()).hexdigest(),digest)

    def test_native_hash_known_nonzero_unicode_and_long_values(self):
        cases={x['text']:x for x in self.report['native']['hashCases']}
        expected={'':0,'A':65,'AB':8581,'ABC':1124178,'Default':1178079449,
                  'Ability_Avatar_Dive_Team':4039962699,'TeamAbility_MoonPhase':2301684124,
                  '\\u80fd\\u529b_\\U0001f600':1180612212,'\\ud800':55296}
        for text,value in expected.items():self.assertEqual(cases[text]['value'],value,text)
        self.assertEqual(len(cases['A'*257]['utf16Hex']),1028)
        self.assertEqual(len(cases),12)

    def test_cctor_defaults_are_distinct_slots(self):
        init=self.report['native']['initialization']
        self.assertEqual(init['lookupDefault'],'')
        self.assertEqual(init['overrideDefault'],'Default')
        self.assertEqual(init['trace']['stop'],'0x1500d62bc')
        fields={x['rawOffset'] for x in self.report['declarations']['staticFields']}
        self.assertEqual(fields,{'0x400c608','0x400c628','0x400c698','0x400c6d0'})

    def test_resource_method_arguments_are_registered_carrier_types(self):
        usages={x['cell']:x for x in self.report['declarations']['genericUsages']}
        carrier=usages['0x145983438'];external=usages['0x1459833f8']
        self.assertEqual((carrier['methodInstance'],carrier['methodArguments'][0]['typeDefinition']),(58257,8026))
        self.assertEqual(carrier['methodArguments'][0]['name'],'MiHoYoBinData')
        self.assertEqual(external['methodArguments'][0]['name'],'ExternalResources')
        self.assertEqual(external['argumentRecord']['typeIndices'],[430788])
        self.assertEqual(external['argumentRecord']['count'],1)

    def test_native_registration_lookup_and_missing(self):
        r=self.report['native']['registrations'][0]
        self.assertEqual([(x['key'],x['text']) for x in r['reverse']],
            [(1178079449,'Default'),(4039962699,'Ability_Avatar_Dive_Team')])
        self.assertEqual([x['result'] for x in r['queries']],['Default','Ability_Avatar_Dive_Team',None])
        self.assertEqual(r['queries'][-1]['trace']['stop'],'0x1500d872c')
        executed=r['registrations'][1]['trace']['executedAddresses']
        self.assertIn('0x1578b6a20',executed)

    def test_duplicate_and_real_hash_collision_partial_effect(self):
        duplicate=self.report['native']['registrations'][1]
        self.assertEqual(duplicate['reverse'],[{'key':2301684124,'text':'TeamAbility_MoonPhase'}])
        collision=self.report['native']['registrations'][3]
        self.assertEqual(collision['reverse'],[{'key':8646,'text':'A\x83'}])
        self.assertEqual(collision['registrations'][-1]['trace']['stop'],'0x1500d57aa')
        self.assertEqual([x['name'] for x in collision['forwardCache'] if x['operation']=='Add'],['A\x83','B\0'])

    def test_zero_hash_direct_default_not_missing_nonzero(self):
        row=self.report['native']['registrations'][4]
        self.assertEqual(row['queries'][0]['key'],0)
        self.assertEqual(row['queries'][0]['result'],'')
        self.assertNotIn('0x1578b66f0',row['queries'][0]['trace']['executedAddresses'])

    def test_locator_uses_exact_unique_current_entries(self):
        rows={x['name']:x for x in self.report['resources']['targets']}
        self.assertEqual(rows['Gadget']['entryName'],'d65ab32f')
        self.assertEqual(rows['GadgetPrimaryIndex']['entryName'],'237dd3a0')
        self.assertEqual(rows['Gadget']['bytes'],2944342)
        self.assertEqual(rows['GadgetPrimaryIndex']['bytes'],163245)
        for row in rows.values():
            self.assertEqual(len(row['indexMatches']),1)
            self.assertEqual(row['indexMatches'][0]['variants'],[[0,178]])

    def test_primary_entire_native_table_and_queries(self):
        p=self.report['primaryIndex']
        self.assertEqual((p['count'],p['cursor'],p['end']),(31682,163245,2944342))
        self.assertEqual(len(p['rows']),31682)
        self.assertEqual(len({x[0] for x in p['rows']}),31682)
        self.assertEqual([x['range'] for x in p['queries']],
            [[90000004,1451356,54],[90000003,1451287,69],[50011101,432396,92],None])
        self.assertEqual(p['trace']['executedAddresses']['0x1553c0930'],31682)
        boundary=self.report['primaryBoundary']
        self.assertEqual((boundary['bytes'],boundary['cursor'],boundary['rows']),(3,3,[]))
        self.assertEqual(boundary['trace']['stop'],'0x1404c2650')

    def test_independent_index_encoding_and_rejection(self):
        self.assertEqual(decode_primary_reference(bytes.fromhex('040104020903')),[(1,4,2),(9,6,3)])
        for raw in (b'',b'\x01',b'\x02',bytes.fromhex('0401040209'),bytes.fromhex('00ff'),b'\x80'*5,
                    bytes.fromhex('028080808010')):
            with self.subTest(raw=raw.hex()),self.assertRaises(ValueError):decode_primary_reference(raw)

    def test_actual_rows_include_inherited_and_u64_fields(self):
        rows={x['key']:x for x in self.report['gadgetRows']}
        values={key:{x['field']['offset']:x['value'] for x in row['values']} for key,row in rows.items()}
        self.assertEqual(values[90000004][0x18],90000004)
        self.assertEqual(values[90000004][0x20],9999)
        self.assertEqual(values[90000004][0x58],'MPLevel')
        self.assertEqual(values[90000003][0x40],10510483980407350969)
        self.assertEqual(values[50011101][0x40],8431959023108435415)
        self.assertEqual(values[50011101][0x58],'Weapon_Sword_Metal')
        for row in rows.values():self.assertEqual(row['cursor'],row['length'])
        self.assertEqual(rows[90000004]['truncated']['trace']['stop'],'0x1404c2650')
        self.assertEqual(rows[90000004]['truncated']['cursor'],53)

    def test_world_factory_actual_row_stops_before_pool_allocation(self):
        row=self.report['gadgetRows'][0]['factory']
        self.assertFalse(row['poolProvided'])
        self.assertEqual(row['factory']['stop'],'0x1404c2be0')
        executed=row['factory']['executedAddresses']
        for address in ('0x15154bf20','0x152e10fc0','0x152e110c0','0x1578b66f0','0x15154bfb0'):
            self.assertIn(address,executed)
        self.assertNotIn('0x15154bfc6',executed)
        writer=self.report['native']['poolWriter']
        self.assertEqual(writer['initializeAbsent']['stop'],'0x146ecd850')

    def test_no_runtime_or_readiness_promotion(self):
        r=self.report
        self.assertFalse(r['completeSceneInfoHandler'])
        self.assertFalse(r['naturalAbilityRegistryObserved'])
        self.assertFalse(r['targetProtocolReady'])
        self.assertEqual(r['runtimeStatus'],'not-tested')
        self.assertEqual(r['observations']['readCount'],99)
        for name in ('abilityRegistry','objectPool','gadgetConfigRegistry'):
            self.assertEqual(r['observations'][name],'not-sampled')


if __name__=='__main__':unittest.main()
