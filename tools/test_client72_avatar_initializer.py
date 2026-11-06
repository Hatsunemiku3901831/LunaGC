"""Assertions for actual ctor fragments and bounded omission/flush consumers."""
import hashlib
import json
import os
from pathlib import Path
import unittest

from tools.client72_avatar_initializer import EVIDENCE_SHA, NATIVE
from tools.extract_client72 import HASHES


class AvatarInitializerTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.root=Path(__file__).resolve().parents[1]
        path=Path(os.environ.get('CLIENT72_AVATAR_INITIALIZER_OBSERVATIONS',cls.root/'protocol/cn-7.2/avatar-initializer-observations.json'))
        cls.report=json.loads(path.read_text('utf8'));cls.rows={r['name']:r for r in cls.report['fixtures']}

    def test_fixed_inputs_sources_and_no_complete_ctor_or_entry_claim(self):
        self.assertEqual(HASHES,self.report['inputSHA256']);self.assertEqual(EVIDENCE_SHA,self.report['inputContracts'])
        self.assertEqual('F-03d3f4d1',self.report['task']);self.assertEqual(7,len(self.rows))
        self.assertEqual(28,sum(len(r['constructor']) for r in self.rows.values()))
        self.assertEqual(70,sum(len(r['omissions']) for r in self.rows.values()))
        for flag in ('completeConstructor','completeAvatarHandler','targetProtocolReady'):self.assertIs(False,self.report[flag])
        self.assertEqual('not-established',self.report['businessRequiredForEntry']);self.assertEqual('not-tested',self.report['runtimeStatus'])
        for name,digest in self.report['sourceSHA256'].items():
            self.assertEqual(digest,hashlib.sha256((self.root/name).read_bytes()).hexdigest())
        self.assertEqual(4,len(self.report['candidateSource']['tableSHA256']))

    def test_concrete_zero_argument_aot_does_not_approve_indirect_overloads(self):
        rows=self.report['declarations']['concreteAOT']
        for owner,instance,address in ((2147,5917,'0x156fbc020'),(2034,12373,'0x1570f5f20'),(69137,5278,'0x155b229f0')):
            self.assertEqual({address},{r['address'] for r in rows if (r['owner'],r['classInstance'])==(owner,instance)})
        self.assertTrue(any('15183ff11' in s for s in self.report['unresolved']))
        self.assertTrue(any('15183ff71' in s for s in self.report['unresolved']))
        for entry,function in self.report['declarations']['functions'].items():
            self.assertEqual('PE-exception-table',function['extent']);self.assertEqual(64,len(function['nativeBytesSHA256']))

    def test_actual_defaults_and_twelve_typed_container_stores(self):
        row=self.rows['fresh-omitted'];snapshot=row['snapshot']
        self.assertEqual((1,1),(snapshot['default284'],snapshot['default2b0']))
        expected={'0x68':1255,'0x70':1255,'0xa0':2147,'0x88':1255,'0x1f0':1255,'0xb8':1255,
                  '0x1f8':1222,'0x200':1222,'0x1d8':1255,'0xb0':65349,'0x60':1255,'0x1e0':1222}
        self.assertEqual(expected,{o:c['type']['typeDefinition'] for o,c in snapshot['containers'].items()})
        for container in snapshot['containers'].values():
            self.assertTrue(container['nonNull'])
            if container['type']['typeDefinition']==1255:
                self.assertEqual(0,container['count']);self.assertTrue(container['arrayNonNull']);self.assertEqual(0,container['arrayLength'])
        writes=[w for fragment in row['constructor'] for w in fragment['writes']]
        self.assertIn((0x284,1,'0x15183fe6c'),[(w['offset'],w['value'],w['source']) for w in writes])
        self.assertIn((0x2b0,1,'0x15183fe76'),[(w['offset'],w['value'],w['source']) for w in writes])
        self.assertTrue({int(o,16) for o in expected}<={w['offset'] for w in writes})

    def test_costume_subconstructors_actually_create_nested_types(self):
        row=self.rows['fresh-omitted'];snapshot=row['snapshot'];trace=row['constructor'][-1]['trace']
        self.assertEqual((87323,77936),(snapshot['costumeLayerType'],snapshot['costumeStateType']))
        self.assertEqual([{'type':48287,'listTypes':[13270,17072]}]*2,snapshot['costumeSets'])
        self.assertTrue({'0x14ff00c60','0x15152d870'}<=set(trace['nativeEntries']))
        self.assertFalse({'0x14ff00c60','0x15152d870'}&set(trace['models']))

    def test_constructor_gaps_are_exact_stops_and_not_fake_initialized_fields(self):
        row=self.rows['fresh-omitted']
        self.assertEqual(['0x15183ff11','0x15183ff71','0x15183ffb4','0x151840331'],[r['trace']['stop'] for r in row['constructor']])
        self.assertEqual({'0x1e8':0,'0x1c0':0,'0x50':0,'0x1c8':0,'0x40':0},row['snapshot']['unresolvedUnwrittenOffsets'])
        self.assertTrue({'0x15183fe60','0x157d97690','0x157d7fe90'}<=set(row['constructor'][0]['trace']['nativeEntries']))
        self.assertEqual('0x15184003d',row['constructor'][-1]['trace']['entry'])

    def test_empty_nonnull_collections_clear_old_stores_by_native_ranges(self):
        rows={r['name']:r for r in self.rows['old-values-omitted']['omissions']}
        for name in ('tag24-u32-set','tag36-u32-set'):
            self.assertEqual([{'width':12,'index':0,'count':3},{'width':4,'index':0,'count':4}],rows[name]['clears'])
            self.assertEqual(0,rows[name]['count']);self.assertEqual(0xffffffff,rows[name]['header']['0x30'])
            self.assertEqual(0,rows[name]['header']['0x2c']);self.assertEqual(0,rows[name]['header']['0x34'])
        pair=rows['tag22-encoded-pairs']
        self.assertEqual([{'width':4,'index':0,'count':4},{'width':40,'index':0,'count':3}],pair['clears'])
        self.assertEqual(0,pair['count']);self.assertEqual(0xffffffff,pair['header']['0x3c'])

    def test_omitted_map_preserves_existing_dictionary_while_scalar_zero_replaces(self):
        rows={r['name']:r for r in self.rows['old-values-omitted']['omissions']}
        self.assertTrue(rows['tag10-empty-preserves-existing-dictionary']['preserved'])
        for offset in ('0xd4','0xe8','0xf4'):
            self.assertEqual((999,0,0),tuple(rows['scalar-'+offset][k] for k in ('prior','value','stored')))
        nonzero={r['name']:r for r in self.rows['nonzero-scalars']['omissions']}
        for offset in ('0xd4','0xe8','0xf4'):
            self.assertEqual((999,42,42),tuple(nonzero['scalar-'+offset][k] for k in ('prior','value','stored')))

    def test_actual_avatar_slot18_and_missing_entity_preserve_high_u32_query(self):
        slot=self.report['declarations']['entityIdSlot18']
        self.assertEqual((18,'0x15184e020'),(slot['virtualSlot'],slot['registeredAddress']))
        self.assertEqual([],self.rows['fresh-omitted']['flush']['sceneQueries'])
        for name,key in (('missing-entity-high-id',0x80000001),('missing-entity-max-id',0xffffffff),('unusable-existing-entity',0x100001)):
            flush=self.rows[name]['flush'];self.assertEqual([key],flush['sceneQueries'])
            self.assertEqual('0x620000100',flush['trace']['stop'])
            self.assertTrue({'0x152f7ec20','0x15184e020','0x1517ab3a0'}<=set(flush['trace']['nativeEntries']))
            self.assertFalse({'0x152f7ec20','0x15184e020','0x1517ab3a0'}&set(flush['trace']['models']))

    def test_high_guids_survive_actual_identity_prefix(self):
        for name,guid in (('fresh-omitted',9007199254740993),('old-values-omitted',9223372036854775809)):
            self.assertEqual(guid,self.rows[name]['identity']['guid'])
            self.assertEqual(1001,self.rows[name]['identity']['id'])

    def test_optional_tail_returns_without_inventing_costume_or_entry_semantics(self):
        rows={r['name']:r for r in self.rows['fresh-omitted']['omissions']}
        self.assertEqual('0x15184e4b5',rows['tag26-absent-child']['trace']['stop'])
        for entry in ('0x151850370','0x151845140'):
            self.assertEqual('0x620000100',rows['optional-tail-'+entry]['trace']['stop'])
            self.assertEqual([],rows['optional-tail-'+entry]['trace']['models'])

    def test_connected_handler_prefix_stops_at_real_unresolved_equipment_calls(self):
        for name,row in self.rows.items():
            trace=row['wholePrefix'];self.assertEqual('0x15184e100',trace['entry'])
            self.assertEqual('0x15184a5f0' if name=='equipment-unknown-boundary' else '0x15183f290',trace['stop'])
            self.assertTrue({'0x15184e100','0x15184ec00'}<=set(trace['nativeEntries']))
            if not row['equips']:self.assertIn('0x15183eae0',trace['nativeEntries'])
            self.assertFalse(({hex(x) for x in NATIVE}|{'0x15183f290','0x15184a5f0'})&set(trace['models']))

    def test_cold_comparer_allocation_null_ifix_and_entity_boundaries_are_explicit(self):
        errors={r['name']:r for r in self.report['rejections']};self.assertEqual(7,len(errors))
        expected={'allocation-failure':'0x1404c2be0','cold-u32-comparer':'0x157d976ae',
                  'missing-enum-ctor-context':'0x140524010','null-target-set':'0x1404c2be0',
                  'null-wire-list-differs-from-omission':'0x620000100','flush-ifix':'0x152f7ece3',
                  'usable-entity-component-boundary':'0x152f7ec86'}
        self.assertEqual(expected,{k:v['trace']['stop'] for k,v in errors.items()})
        self.assertIn('preserves-old-count',errors['null-wire-list-differs-from-omission']['scope'])
        self.assertIn('before-unknown',errors['cold-u32-comparer']['scope'])
        self.assertEqual([],errors['flush-ifix']['trace']['models'])


if __name__=='__main__':unittest.main()
