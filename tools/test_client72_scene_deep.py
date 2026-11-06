"""Independent checks of the bounded target-execution evidence and its limits."""
import hashlib
import json
from pathlib import Path
import unittest

from tools.client72_scene_deep import EVIDENCE, retention_cases


class SceneDeepTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.report=json.loads(Path('protocol/cn-7.2/scene-deep-observations.json').read_text('utf8'))
        cls.rows={r['name']:r for r in cls.report['retentionFixtures']}

    def test_pins_and_scope(self):
        self.assertEqual(self.report['inputContracts'],EVIDENCE)
        for name,digest in EVIDENCE.items(): self.assertEqual(hashlib.sha256((Path('protocol/cn-7.2')/name).read_bytes()).hexdigest(),digest)
        for key in ('fullNetworkHandler','fullSceneEntityEffects','targetProtocolReady','completeSceneInfoRawClosure','productionRequiredFieldSetApproved','secondaryWholeClassWeaponNameApproved'):
            self.assertIs(self.report[key],False)

    def test_target_slots_and_shared_dispose_are_named_separately(self):
        slots=self.report['declarations']['virtualSlots']['34045']
        self.assertEqual(slots['11']['registeredAddress'],'0x147ccf320')
        self.assertEqual(slots['10']['registeredAddress'],'0x147ccf3b0')
        dispose=[r for r in self.report['declarations']['aot'] if r['owner']==1256]
        self.assertEqual([(r['method'],r['address'],r['arguments'][0]['kind']) for r in dispose],[('Dispose','0x154fb0700','object')])
        for row in self.rows.values(): self.assertEqual(row['criticalModeledAddresses'],[])

    def test_nonempty_children_increment_once_and_repeat_keeps_counts(self):
        row=self.rows['two-nonempty-child-lists']
        self.assertEqual([(x['typeIndex'],x['count']) for x in row['after']['objects'] if x['typeIndex'] in (34045,77771)],[(34045,2),(77771,2),(77771,2)])
        again=self.rows['same-pointer-repeat']
        self.assertEqual(again['first'],again['after'])
        self.assertEqual(row['storedPointer'],row['abilityPointer'])
        self.assertIn('0x158dc1870',row['criticalNativeEntries'])
        self.assertIn('0x147ccf320',row['criticalNativeEntries'])
        self.assertIn('0x154fb0700',row['criticalNativeEntries'])

    def test_replacement_and_null_have_distinct_release_behavior(self):
        replace=self.rows['replace-existing-reference']; clear=self.rows['null-does-not-release-old-reference']
        self.assertNotEqual(replace['abilityPointer'],replace['replacementPointer'])
        old=next(x for x in replace['after']['objects'] if x['pointer']==replace['abilityPointer'])
        new=next(x for x in replace['after']['objects'] if x['pointer']==replace['replacementPointer'])
        self.assertEqual((old['count'],new['count']),(1,2))
        self.assertEqual(replace['storedPointer'],replace['replacementPointer'])
        self.assertEqual(clear['storedPointer'],'0x0')
        self.assertEqual(clear['first'],clear['after'])
        pool=self.rows['replace-last-reference-pool-enabled-boundary']
        self.assertEqual(pool['secondTrace']['stop'],'0x140514ee0')
        self.assertEqual(pool['storedPointer'],pool['abilityPointer'])

    def test_counter_and_native_failure_boundaries(self):
        count=self.rows['counter-int32-max-wrap']
        self.assertEqual(next(x['count'] for x in count['after']['objects'] if x['typeIndex']==34045),-2147483648)
        self.assertEqual(self.rows['uninitialized-child-list-null-error']['trace']['stop'],'0x1404c2be0')
        self.assertEqual(self.rows['lazy-input-flag-stops-before-materialize']['trace']['stop'],'0x14a440c40')
        self.assertEqual(self.rows['full-list-avatar-retained-secondary-copy-boundary']['trace']['stop'],'0x154c795c4')

    def test_independent_wire_and_high_guid(self):
        self.assertEqual(retention_cases()[1]['ability'],'080112051d0000a03f32051d000020c0')
        row=self.rows['two-nonempty-child-lists']
        avatar=row['reader']['result']['DIGAMAKGCPA'][0]
        self.assertEqual((avatar['OKMEHFHNFCG'],avatar['NPKKCHABEJH'],avatar['FDDDFIAINCE'],avatar['IEDJNHILJBF']),
            (9007199254740993,18446744073709551615,2147483649,4294967295))
        merged=self.rows['duplicate-wire-avatar-ability-merge']['reader']['result']['DIGAMAKGCPA'][0]['LBJMODOPOCP']
        self.assertEqual([v['GCOLEBCPBNP'] for v in merged['AOGEMELMKBO']],[1.25,3.5])
        self.assertEqual(merged['COADAPGCGKK'][0]['GCOLEBCPBNP'],-2.5)

    def test_world_beyond_and_dungeon_are_not_collapsed(self):
        field=self.report['declarations']['stageField']
        self.assertEqual((field['ownerName'],field['name'],field['offset']),('SceneScriptData','stageType',112))
        symbols={x['symbol']:x['value'] for x in self.report['declarations']['stageEnum']['values']}
        self.assertEqual((symbols['World'],symbols['Dungeon'],symbols['BeyondGameplay']),(0,1,5))
        gates={x['name']:x for x in self.report['stageFixtures']}
        self.assertIs(gates['World']['result'],False); self.assertIs(gates['BeyondGameplay']['result'],True)
        self.assertIsNone(gates['Dungeon']['result']); self.assertEqual(gates['Dungeon']['trace']['stop'],'0x14cd5edc0')
        handler={x['name']:x for x in self.report['handlerFixtures']}
        self.assertEqual(handler['world-skips-complex-object']['trace']['stop'],'0x1507e4900')
        self.assertEqual(handler['beyond-enters-complex-consumer']['trace']['stop'],'0x1496a7750')

    def test_missing_nonzero_hash_registry_is_a_real_error_boundary(self):
        rows=self.report['handlerPrefixFixtures']
        self.assertEqual([r['trace']['stop'] for r in rows],['0x1404c2be0','0x1404c2be0','0x1507e44f0'])
        self.assertEqual([r['outputCount'] for r in rows],[0,0,1])
        self.assertEqual(rows[2]['tupleValues'],[0,0,4294967295])
        self.assertIn('[ABILITY HASH]',self.report['declarations']['abilityHashLiteral'][0]['value'])

    def test_entity_replacement_order_and_handler_cleanup(self):
        rows=self.report['lifecycleFixtures']
        self.assertEqual([(r['modelType'],r['usableOldEntity'],r['storedEntityIdAtStop']) for r in rows],
            [(18163,False,92),(18163,True,91),(51522,False,92),(51522,True,92)])
        for row in rows: self.assertEqual(row['sceneQueries'],[91])
        suffix=self.report['handlerSuffixFixture']
        self.assertEqual((suffix['prior']['count'],suffix['after']['count']),(2,0))
        self.assertEqual(suffix['after']['pointers'],['0x0','0x0'])
        self.assertEqual(suffix['clearCalls'][0]['elementWidth'],8)

    def test_wire_errors_remain_separate_from_managed_boundaries(self):
        rows=self.report['wireRejections']
        self.assertEqual(len(rows),3)
        self.assertEqual({r['name'] for r in rows},{'invalid-root-tag','truncated-child','u64-overflow'})
        self.assertEqual({r['scope'] for r in rows},{'native-error-entry','explicit-primitive-input-model'})

    def test_weapon_role_requires_the_exact_typed_factory_branch(self):
        self.assertIs(self.report['secondaryWeaponFactoryRoleApproved'],True)
        rows={x['role']:x for x in self.report['weaponRoleCrossEvidence']}
        weapon=rows['weapon-input-branch']; relic=rows['reliquary-input-branch']
        self.assertEqual((weapon['sourceParameter']['type']['typeDefinition'],weapon['binding']['cell'],weapon['binding']['arguments'][0]['typeDefinition']),
            (29353,'0x145994388',51522))
        self.assertEqual(relic['binding']['arguments'][0]['typeDefinition'],87122)
        self.assertIs(self.report['secondaryWholeClassWeaponNameApproved'],False)


if __name__=='__main__': unittest.main()
