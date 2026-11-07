"""Independent literals and state assertions for the bounded entry report."""
import hashlib
import json
import os
from pathlib import Path
import unittest


class SceneEntryClosureTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.report = json.loads(Path(os.environ['CLIENT72_SCENE_ENTRY_REPORT']).read_text('utf8'))
        cls.floats = {row['name']: row for row in cls.report['floatFixtures']}
        cls.tokens = {row['name']: row for row in cls.report['tokenFixtures']}

    def test_fixed_five_inputs_and_source_identity(self):
        profile = json.loads(Path('protocol/cn-7.2/profile.json').read_text('utf8'))
        self.assertEqual(set(self.report['inputs']['files']),set(profile['files']))
        for name,row in profile['files'].items():
            self.assertEqual(self.report['inputs']['files'][name]['sha256'],row['sha256'])
        for path,expected in self.report['sourceSHA256'].items():
            self.assertEqual(hashlib.sha256(Path(path).read_bytes()).hexdigest(),expected,path)

    def test_scope_does_not_promote_whole_handler_or_natural_state(self):
        for name in ('fullNetworkHandler','fullSceneEntityEffects','productionRequiredFieldSetApproved',
                     'targetProtocolReady','naturalAbilityHashRegistryLoaded'):
            self.assertIs(self.report[name],False)
        self.assertEqual(self.report['runtimeStatus'],'not-tested')

    def test_exact_float_dictionary_and_iterator_declarations(self):
        audit = self.report['declarations']
        self.assertEqual(audit['floatDictionaryField']['offset'],0x110)
        self.assertEqual(audit['floatDictionaryField']['type']['instanceIndex'],5971)
        insert = next(x for x in audit['floatDictionaryAot'] if x['method']=='TryInsert')
        self.assertEqual((insert['address'],[x['kind'] for x in insert['arguments']]),
                         ('0x1553c8af0',['uint32','float']))
        methods = {x['method']:x for x in audit['sharedIteratorAot']}
        self.assertEqual(methods['MoveNext']['address'],'0x1572292b0')
        self.assertEqual(methods['get_Current']['address'],'0x1572294c0')
        self.assertEqual(audit['listType']['type']['arguments'][0]['typeDefinition'],80154)

    def test_float_inputs_are_native_and_preserve_uint64_parent(self):
        for row in self.floats.values():
            self.assertEqual(row['criticalModeledAddresses'],[])
            self.assertEqual(hashlib.sha256(bytes.fromhex(row['wire'])).hexdigest(),row['wireSHA256'])
            self.assertIn('688180808080808010',row['wire'])
            for gateway in row['modeledUnboxGateways']:
                self.assertIn('synthetic managed',gateway['scope'])
            for args in row['nativeInsertArguments']:
                self.assertEqual(args['behavior'],1)
        row=self.floats['two-nonzero-values']
        for address in ('0x158492e10','0x1572292b0','0x1572294c0','0x1553c8af0'):
            self.assertIn(address,row['trace']['executedAddresses'])

    def test_nonzero_collisions_and_high_bit_use_complete_keys(self):
        row=self.floats['same-masked-hash-high-bit']
        self.assertIn('0881808080081500000040',row['wire'])
        self.assertEqual(row['result'],[{'key':1,'floatBits':0x3f800000},
                                        {'key':0x80000001,'floatBits':0x40000000}])
        self.assertEqual(self.floats['two-nonzero-values']['result'],
                         [{'key':1,'floatBits':0x3fa00000},{'key':8,'floatBits':0xc0200000}])

    def test_repeated_key_overwrite_and_exact_float_boundary_bits(self):
        row=self.floats['duplicate-last-value-wins']
        self.assertEqual(row['result'],[{'key':7,'floatBits':0xc0200000},
                                        {'key':0xffffffff,'floatBits':0x7f7fffff}])
        self.assertEqual(len(row['nativeInsertArguments']),3)
        self.assertEqual(self.floats['zero-subnormal-negative-zero']['result'],
            [{'key':0,'floatBits':0},{'key':1,'floatBits':1},{'key':0xffffffff,'floatBits':0x80000000}])

    def test_clear_and_entity_flag_order_are_not_equivalent(self):
        empty=self.floats['empty-clears-old-state']
        self.assertEqual((empty['result'],empty['version']),([],12))
        protected=self.floats['protected-entity-keeps-old-state']
        self.assertEqual(protected['result'],[{'key':99,'floatBits':0x3f000000}])
        self.assertEqual((protected['version'],protected['clearCalls'],protected['nativeInsertArguments']),(11,[],[]))
        self.assertEqual(protected['entityLookupIds'],[0x80000077])
        unprotected=self.floats['unprotected-entity-replaces-old-state']
        self.assertEqual(unprotected['result'],[{'key':9,'floatBits':0x3fa00000}])
        self.assertEqual(unprotected['entityLookupIds'],[0x80000077])

    def test_null_and_growth_boundaries_keep_partial_effects(self):
        null_list=self.floats['null-list-clears-before-error']
        self.assertEqual((null_list['trace']['stop'],null_list['result'],null_list['version']),('0x1404c2be0',[],12))
        null_dictionary=self.floats['null-dictionary-native-error']
        self.assertEqual((null_dictionary['trace']['stop'],null_dictionary['version']),('0x1404c2be0',11))
        growth=self.floats['growth-stops-at-real-resize']
        self.assertEqual(growth['trace']['stop'],'0x159c329a0')
        self.assertEqual(len(growth['nativeInsertArguments']),9)
        self.assertEqual([x['key'] for x in growth['result']],list(range(8)))

    def test_root_tag7_is_untransformed_named_loading_token(self):
        self.assertTrue(self.tokens['high-bit']['wire'].startswith('388180808008'))
        for name,token in [('zero',0),('one',1),('high-bit',0x80000001),('uint32-max',0xffffffff)]:
            row=self.tokens[name]
            self.assertEqual((row['rawToken'],row['namedDiagnosticTokens']),(token,[token]))
            self.assertEqual(row['trace']['stop'],'0x14cd5fa0f')
            self.assertIs(row['diagnosticPointerMatchesCell'],True)
            for address in ('0x14828fea0','0x148290060','0x14cd5f7b0','0x1578b66f0'):
                self.assertIn(address,row['trace']['executedAddresses'])
            self.assertNotIn('0x1578b66f0',row['trace']['models'])
        literal=self.report['declarations']['tokenDiagnosticLiteral'][0]
        self.assertEqual((literal['index'],literal['value']),
            (52998,'[Loading] LoadingTask GetLoadingTask is Null token:'))

    def test_token_omission_wrong_wire_duplicate_and_mismatch(self):
        for name in ('omitted','wrong-wire-skipped'):
            self.assertEqual(self.tokens[name]['namedDiagnosticTokens'],[0])
        self.assertEqual(self.tokens['duplicate-last-wins']['namedDiagnosticTokens'],[0x80000001])
        mismatch=self.tokens['token-mismatch']
        self.assertEqual((mismatch['rawToken'],mismatch['activeToken']),(91,92))
        self.assertEqual(mismatch['trace']['stop'],'0x14fed6aa0')
        self.assertEqual(mismatch['namedDiagnosticTokens'],[])

    def test_actual_scene_table_whole_parser_eof_and_native_key_queries(self):
        table=self.report['sceneTable']
        self.assertEqual((table['count'],table['cursor'],table['payloadBytes']),(2431,280285,280285))
        self.assertEqual(len(table['rows']),2431)
        self.assertEqual(len({x['key'] for x in table['rows']}),2431)
        self.assertEqual(table['payloadSHA256'],'2964028cba6cd66b4c04e86472411d10756643e230c1b6b5c34206ab7a0e0ce0')
        for address in ('0x147bb17e0','0x14e3ced00','0x1578b6a20','0x1405070c0'):
            self.assertIn(address,table['trace']['executedAddresses'])
            self.assertNotIn(address,table['trace']['models'])
        self.assertEqual([(q['key'],q['storedKey']) for q in table['nativeKeyQueries']],[(3,3),(4,4),(0xffffffff,None)])
        for query in table['nativeKeyQueries']:
            self.assertNotIn('0x1578b66f0',query['trace']['models'])
        self.assertIs(table['actualRevisionSelected'],False)
        self.assertIs(table['naturalSceneScriptStageObserved'],False)

    def test_scene3_enum_is_distinct_from_natural_stage(self):
        table=self.report['sceneTable']
        row=next(x for x in table['selectedRows'] if x['key']==3)
        fields={x['name']:x for x in row['fields']}
        self.assertEqual((fields['KDLEOKGKEAN']['offset'],fields['KDLEOKGKEAN']['value']),(0x7c,3))
        self.assertEqual((fields['IELLPMKLAIO']['offset'],fields['IELLPMKLAIO']['value']),(0x84,1))
        enum=next(x for x in table['rowEnums'] if x['typeIndex']==80117)
        self.assertEqual(next(x['value'] for x in enum['values'] if x['symbol']=='SCENE_WORLD'),1)
        self.assertEqual(fields['OGEKKNHOAPE']['value'],'Level_BigWorld')
        self.assertEqual(fields['NEALMAKPJGK']['value'],'BigWorld_LevelStreaming')
        self.assertEqual(fields['FKCMBGJCOKC']['value'],'Data/Environment/EnviroSystemProfile/BigWorld/ESP_Md_General')
        self.assertIs(table['naturalSceneScriptStageObserved'],False)

    def test_native_scratch_lifetime_and_truncated_resource_boundary(self):
        table=self.report['sceneTable']
        self.assertEqual(len(table['tlsScratchRequests']),1007)
        self.assertEqual(table['tlsSlabUsedAtEnd'],0)
        self.assertTrue(all(x['usedBefore']==0 and 0<x['count']<=4096 for x in table['tlsScratchRequests']))
        truncated=table['truncatedFirstRow']
        self.assertEqual((truncated['inputBytes'],truncated['cursor'],truncated['trace']['stop']),
                         (4,4,'0x1404c2650'))


if __name__=='__main__':
    unittest.main()
