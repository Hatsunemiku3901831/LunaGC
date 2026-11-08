"""Independent assertions for the narrow D02 resource bridge and its refusals."""
import copy
import hashlib
import json
import os
import unittest
from pathlib import Path

from tools import client72_d02_resource_bridge as bridge


class ConversionBoundaryTests(unittest.TestCase):
    def test_java_int_id_boundary_does_not_truncate_unsigned_keys(self):
        self.assertEqual(bridge.java_id(0x7fffffff), 2147483647)
        self.assertEqual(bridge.java_id(0, allow_zero=True), 0)
        for value in (0, -1, 0x80000001, 0xffffffff, 1 << 64, 1.0, True, None):
            with self.subTest(value=value), self.assertRaises(ValueError):
                bridge.java_id(value)

    def test_enum_alignment_requires_both_name_and_number(self):
        target = [dict(symbol='Wind', value=7, fieldIndex=1),
                  dict(symbol='Fire', value=2, fieldIndex=2),
                  dict(symbol='NewElement', value=0, fieldIndex=3)]
        mapping = bridge.align_elements(target, '    Wind(\n        7, A, B),\n    Fire(1, A, B),\n    None(0, A, B);')
        self.assertEqual([row['accepted'] for row in mapping], [True, False, False])
        self.assertEqual(bridge.java_element(7, mapping), 'Wind')
        for value in (0, 2, 10, 255, 777):
            with self.subTest(value=value), self.assertRaises(ValueError):
                bridge.java_element(value, mapping)

    def test_pinned_source_mutation_refuses_before_extraction(self):
        with self.assertRaisesRegex(ValueError, 'Pinned input changed'):
            bridge.read_pinned(Path(__file__), '0' * 64)


class NativeBridgeReportTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        path = Path(os.environ.get('CLIENT72_D02_RESOURCE_BRIDGE_REPORT',
            bridge.REPO / bridge.OUTPUT_ROOT / 'first.json'))
        cls.report = json.loads(path.read_text('utf8'))
        cls.native = cls.report['native']
        cls.tables = {row['name']: row for row in cls.native['parsedTables']}
        cls.roles = {row['depotId']: row for row in cls.native['roleCases']}

    def test_actual_three_whole_payloads_reach_exact_eof(self):
        expected = {'Avatar':(169,64384,'466525f21b9e3f6dbbc90df7d050bfc8faf06d1f595d6317872bc5a1d2ed71c9'),
                    'AvatarSkillDepot':(171,26024,'fda5ac5875581bf6c04a269333197d41decea728ecc9f075e4add7780745b14b'),
                    'AvatarSkill':(1176,141702,'a5d2fdc6bbc32ea408c51e90c04777b771e9ab4fec4c35fc74186b6dc3c236fe')}
        self.assertEqual(set(self.tables), set(expected))
        for name, (count, size, digest) in expected.items():
            row = self.tables[name]
            self.assertEqual((row['count'],row['cursor'],row['payloadBytes'],row['payloadSHA256']),
                             (count,size,size,digest))
            self.assertEqual(row['trace']['stop'], '0x620000100')
            self.assertEqual(len(row['rows']), count)
            cursor = 4
            for actual in row['rows']:
                self.assertEqual(actual['source']['begin'], cursor)
                self.assertLess(cursor, actual['source']['end'])
                cursor = actual['source']['end']
            self.assertEqual(cursor, size)

    def test_same_cache_named_consumer_keeps_default_and_saved_depot_distinct(self):
        cases = {row['avatarId']:row for row in self.native['namedElementCases']}
        for avatar, depot in ((10000005,501),(10000007,701)):
            case = cases[avatar]
            calls = [row for row in case['calls'] if row['entry']=='0x1578b66f0']
            self.assertEqual([int(row['rdx'],16) for row in calls], [avatar,depot,0])
            self.assertEqual(case['returned'],0)
            self.assertEqual(case['trace']['stop'],'0x620000100')
        missing = cases[0xffffffff]
        self.assertEqual([int(row['rdx'],16) for row in missing['calls'] if row['entry']=='0x1578b66f0'],[0xffffffff])
        self.assertEqual(missing['returned'],0)
        self.assertEqual(cases[10000136]['returned'],4)

    def test_actual_nonzero_energy_missing_resource_reaches_native_error(self):
        case=self.native['missingEnergySkill']
        self.assertEqual((case['depotId'],case['field6c']),(704,10068))
        self.assertEqual((case['trace']['entry'],case['trace']['stop']),('0x15345c42d','0x1404c2be0'))
        self.assertIn('0x1578b66f0',case['trace']['executedAddresses'])
        self.assertNotIn('0x15345f3d0',case['trace']['executedAddresses'])
        self.assertEqual(case['trace']['models'],[])
        self.assertTrue(case['originalCacheRestored'])

    def test_native_regular_skill_roles_preserve_zero_positions(self):
        self.assertEqual(self.roles[501]['regularSkills'],[100540,0,0,0])
        self.assertEqual(self.roles[701]['regularSkills'],[100550,0,0,0])
        self.assertEqual(self.roles[501]['roles'],{'100540':0})
        self.assertEqual(self.roles[701]['roles'],{'100550':0})
        self.assertEqual(self.roles[704]['regularSkills'],[100553,10067,0,0])
        self.assertEqual(self.roles[704]['roles'],{'10067':1,'10068':4,'100553':0})
        for row in self.roles.values():
            self.assertEqual(row['roleAssignment']['entry'],'0x15345c306')
            self.assertEqual(row['roleAssignment']['stop'],'0x15345c4fd')
            self.assertIn('0x15345f3d0',row['roleAssignment']['executedAddresses'])
            self.assertNotIn('0x153455fa0',row['roleAssignment']['executedAddresses'])
            self.assertEqual(row['roleAssignment']['models'],[])

    def test_actual_absent_flag_and_nonzero_energy_are_different(self):
        for depot in (501,701):
            row=self.roles[depot]
            self.assertFalse(row['scalarPresence']['field6c'])
            self.assertEqual(row['field6c'],0)
            self.assertIsNone(row['energySlot4Id'])
            self.assertEqual(row['uiConsumerPrefix']['stop'],'0x620000100')
        row=self.roles[704]
        self.assertTrue(row['scalarPresence']['field6c'])
        self.assertEqual((row['field6c'],row['energySlot4Id']),(10068,10068))
        self.assertEqual(row['uiConsumerPrefix']['stop'],'0x15345e2aa')
        self.assertEqual(row['uiSelectedPointer'],row['energySlot4Pointer'])

    def test_native_enumeration_and_slot_write_are_not_success_models(self):
        for row in self.roles.values():
            trace=row['selection']
            self.assertEqual((trace['entry'],trace['stop']),('0x15345a7f0','0x620000100'))
            self.assertIn('0x1593af830',trace['executedAddresses'])
            self.assertIn('0x15345ac6a',trace['executedAddresses'])
            self.assertNotIn('0x1593af830',trace['models'])
            self.assertNotIn('0x1578b66f0',trace['models'])
            self.assertFalse(row['fullAvailabilityExecuted'])

    def test_named_ui_anchor_is_exact_and_only_static(self):
        sources={row['entry']:row for row in self.native['staticConsumers']}
        caller=sources['0x14cf26b80']
        self.assertEqual(caller['metadata']['owner'],24885)
        self.assertEqual(caller['metadata']['ownerName'],'MonoMPTeamBtn')
        self.assertEqual(caller['metadata']['method']['name'],'UpdateEnergySkillAvailable')
        instructions={row['address']:row['instruction'] for row in caller['instructions']}
        self.assertEqual(instructions['0x14cf26d4c'],'call 0x15345e260')
        callee={row['address']:row['instruction'] for row in sources['0x15345e260']['instructions']}
        self.assertEqual(callee['0x15345e29d'],'mov rdi, qword ptr [rax + 0x40]')
        producer={row['address']:row['instruction'] for row in sources['0x15345c2d0']['instructions']}
        self.assertEqual(producer['0x15345c4ef'],'mov r8d, 4')
        self.assertEqual(producer['0x15345c4f8'],'call 0x15345f3d0')

    def test_element_enum_has_ten_exact_pairs_and_nine_unmapped_values(self):
        mapping=self.report['enumMap']
        self.assertEqual([(r['targetSymbol'],r['targetValue']) for r in mapping if r['accepted']],
                         [('None',0),('Fire',1),('Water',2),('Grass',3),('Electric',4),
                          ('Ice',5),('Frozen',6),('Wind',7),('Rock',8),('AntiFire',9)])
        self.assertEqual([r['targetValue'] for r in mapping if not r['accepted']],list(range(10,19)))
        self.assertNotIn(255,[r['targetValue'] for r in mapping])

    def test_projection_fragments_have_precise_destinations(self):
        fragments={(r['table'],r['key']):r['javaFields'] for r in self.report['javaProjectionFragments']}
        self.assertEqual(fragments['Avatar',10000005],{'id':10000005,'skillDepotId':501})
        self.assertEqual(fragments['AvatarSkillDepot',701],{'id':701,'skills':[100550,0,0,0],'energySkill':0})
        self.assertEqual(fragments['AvatarSkill',10068],{'id':10068,'costElemType':'Wind'})
        self.assertNotIn('initialWeapon',str(fragments))
        self.assertTrue(all(row['fragmentOnly'] for row in self.report['javaProjectionFragments']))

    def test_missing_positive_skill_reference_is_refused(self):
        tables=copy.deepcopy(self.native['parsedTables'])
        skill=next(t for t in tables if t['name']=='AvatarSkill')
        skill['rows']=[r for r in skill['rows'] if r['key']!=10068]
        with self.assertRaisesRegex(ValueError,'Positive actual skill reference is absent'):
            bridge.project_fields(tables,self.report['enumMap'])

    def test_missing_field_is_not_filled_with_java_zero_default(self):
        tables=copy.deepcopy(self.native['parsedTables'])
        depot=next(t for t in tables if t['name']=='AvatarSkillDepot')
        depot['selected'][0]['fields']=[r for r in depot['selected'][0]['fields'] if r['offset']!='0x6c']
        with self.assertRaises(KeyError):
            bridge.project_fields(tables,self.report['enumMap'])

    def test_unknown_element_is_not_mapped_to_none(self):
        tables=copy.deepcopy(self.native['parsedTables'])
        skill=next(t for t in tables if t['name']=='AvatarSkill')
        next(r for r in skill['selected'][0]['fields'] if r['offset']=='0x54')['value']['value']=777
        with self.assertRaisesRegex(ValueError,'No exact named Java ElementType'):
            bridge.project_fields(tables,self.report['enumMap'])

    def test_source_unsigned64_hashes_remain_lossless_and_are_not_resource_ids(self):
        avatar=next(r for r in self.tables['Avatar']['selected'] if r['key']==10000005)
        value=next(r['value'] for r in avatar['fields'] if r['offset']=='0x28')
        self.assertEqual(value['bits'],'adfb90ae5bdec0ac')
        self.assertEqual(int(value['decimal']),int('adfb90ae5bdec0ac',16))
        self.assertGreater(int(value['decimal']),1 << 63)

    def test_no_resource_ready_or_natural_constructor_permission(self):
        for name in ('nativeCurrentConstructorExecuted','javaResourcesLoaded','resourceReady',
                     'completeAvatarHandler','targetProtocolReady','d02Complete'):
            self.assertIs(self.report[name],False)
        self.assertIsNone(self.report['revisions']['activeSelectedRevision'])
        self.assertFalse(self.report['revisions']['activeRevisionVerified'])
        files=self.report['revisions']['bundledRevisionFiles']
        self.assertEqual({name:row['text'] for name,row in files.items()},
                         {'res_revision':'49008628','data_revision':'49008628','silence_revision':'48722240'})
        self.assertEqual(self.report['revisions']['candidateSource']['versionLabel'],
                         'CNRELWin7.2.0_R49086080_S49210135_D49230962')
        self.assertFalse(self.native['completeSkillFactory'])
        self.assertEqual(len(self.native['payloadSHA256']),6)


if __name__=='__main__':
    unittest.main()
