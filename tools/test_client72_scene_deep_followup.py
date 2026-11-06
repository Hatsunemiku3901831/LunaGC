"""Independent assertions for actual Scene CopyFrom and ability-hash lookup."""
import hashlib
import json
from pathlib import Path
import unittest


class SceneDeepFollowupTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.r=json.loads(Path('protocol/cn-7.2/scene-deep-followup-observations.json').read_text('utf8'))
        cls.c={x['name']:x for x in cls.r['copyFixtures']}
        cls.h={x['name']:x for x in cls.r['registryFixtures']}

    def test_reviewed_first_card_and_scope_remain_fixed(self):
        digest=hashlib.sha256(Path('protocol/cn-7.2/scene-deep-observations.json').read_bytes()).hexdigest()
        self.assertEqual(self.r['firstSceneEvidenceSHA256'],digest)
        for key in ('targetProtocolReady','fullNetworkHandler','fullSceneEntityEffects','completeNonemptyListCopy','naturalAbilityHashRegistryLoaded'):
            self.assertIs(self.r[key],False)
        self.assertEqual(self.r['coveredNonemptyListTypes'],[77771])

    def test_exact_open_context_inflates_to_concrete_clone_interface(self):
        rows={x['contextOffset']:x for x in self.r['copyClassContext']['rows']}
        self.assertEqual((rows[88]['typeRef'],rows[88]['inflatedType']['typeDefinition']),(686353,89084))
        self.assertEqual((rows[40]['typeRef'],rows[40]['inflatedType']['typeDefinition']),(686352,1256))
        for row in rows.values():self.assertEqual(row['inflatedType']['arguments'][0]['typeDefinition'],77771)
        interface=self.r['copyClassContext']['interfaceMethods']
        self.assertEqual([(x['name'],x['parameterCount']) for x in interface],[('GFJDJODGIDB',0)])
        self.assertEqual(self.r['copyClassContext']['concreteClone']['name'],'GFJDJODGIDB')

    def test_copy_contexts_retain_method_and_argument_identities(self):
        self.assertEqual({r['methodDefinition'] for r in self.r['methodContexts']},{507465,507471})
        self.assertEqual({r['methodArguments'][0]['typeDefinition'] for r in self.r['methodContexts']},{77771,70808,51162,51050})

    def test_bool_zero_duplicate_and_wrong_wire_are_actually_copied(self):
        self.assertEqual(self.c['nonempty-bool-copy']['copyResult'],{'FAGKAADHKDI':True})
        for name in ('explicit-zero-copy','duplicate-bool-last-zero','wrong-wire-bool-skipped'):
            row=self.c[name];self.assertEqual(row['copyResult'],{});self.assertNotEqual(row['sourcePointer'],row['copyPointer'])
        omitted=self.c['omitted-secondary-child'];self.assertEqual((omitted['sourcePointer'],omitted['copyPointer']),('0x0','0x0'))
        self.assertIsNone(omitted['copyResult'])
        for row in self.c.values():self.assertEqual(row['trace']['stop'],'0x620000100');self.assertEqual(row['criticalModeledAddresses'],[])

    def test_two_same_shape_child_lists_copy_values_into_new_objects(self):
        row=self.c['two-nonempty-lists-cloned']
        self.assertEqual(row['copyResult']['AOGEMELMKBO'][0]['GCOLEBCPBNP'],1.25)
        self.assertEqual(row['copyResult']['COADAPGCGKK'][0]['GCOLEBCPBNP'],-2.5)
        self.assertEqual(row['copyResult'],row['sourceAfter'])
        self.assertEqual(len(row['childCopies']),2)
        for child in row['childCopies']:self.assertNotEqual(child['sourcePointer'],child['copyPointer'])
        self.assertIn('0x158dc2140',row['nativeEntries']);self.assertIn('0x147ccfc70',row['nativeEntries'])
        clone=self.r['copyClassContext']['concreteClone']['registeredAddress'];self.assertIn(clone,row['nativeEntries'])

    def test_reused_copy_clears_old_values_with_real_clear_selection(self):
        row=self.c['reused-copy-clears-old-bool-and-lists']
        self.assertEqual(row['clearing']['trace']['stop'],'0x620000100')
        self.assertEqual(row['clearing']['after'],{})
        calls=row['clearing']['clearCalls'];self.assertEqual([(x['count'],x['elementWidth']) for x in calls],[(1,8),(1,8)])
        self.assertTrue(row['sourceAfter']['FAGKAADHKDI'])

    def test_concrete_dictionary_wrappers_and_shared_find_execute(self):
        aot={x['method']:x for x in self.r['dictionaryAot']}
        self.assertEqual((aot['ContainsKey']['classInstance'],aot['get_Item']['classInstance']),(4742,4742))
        self.assertEqual(aot['FindEntry']['classInstance'],1541)
        self.assertEqual(aot['FindEntry']['address'],'0x1578b66f0')
        for row in self.h.values():
            self.assertIn('0x1578b66f0',row['nativeDictionaryEntries'])
            self.assertFalse(set(row['trace']['models'])&{x['address'] for x in aot.values()})

    def test_high_bit_collision_bucket_chain_and_order(self):
        high=self.h['same-hash-mask-distinct-high-bit']
        self.assertEqual([x['key'] for x in high['controlledTable']],[1,2147483649])
        self.assertEqual(high['controlledTable'][0]['bucket'],high['controlledTable'][1]['bucket'])
        self.assertEqual([(x['first'],x['second'],x['value']) for x in high['tuples']],[('high','low',4294967295)])
        collision=self.h['same-bucket-collision-chain']
        self.assertEqual(collision['controlledTable'][0]['bucket'],collision['controlledTable'][1]['bucket'])
        self.assertEqual([(x['first'],x['second']) for x in collision['tuples']],[('second','first')])
        ordered=self.h['list-order-and-repeat']
        self.assertEqual([(x['first'],x['second'],x['value']) for x in ordered['tuples']],[('二','一',12),('一','二',13),('一','一',14)])
        self.assertEqual(ordered['tuples'][2]['firstPointer'],ordered['tuples'][2]['secondPointer'])

    def test_default_and_error_preparation_are_not_confused_with_table_absence(self):
        for name in ('zero-uses-controlled-default','missing-key-uses-existing-controlled-default'):
            row=self.h[name];self.assertEqual(row['trace']['stop'],'0x1507e44f0');self.assertEqual(row['tuples'][0]['second'],'controlled-default')
        for name in ('missing-key-without-default-error-branch','registered-empty-name-error-branch'):
            row=self.h[name];self.assertEqual(row['trace']['stop'],'0x1500d872c');self.assertEqual(row['tuples'],[])


if __name__=='__main__':unittest.main()
