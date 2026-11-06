"""Mutation checks for the native-backed F-03 three-layer integration."""
import json
from pathlib import Path
import unittest

from tools.validate_protocol72 import validate, validate_scene_fusion, validate_enter_scene_fusion


class FusionMappingTest(unittest.TestCase):
    def setUp(self):
        directory=Path(__file__).resolve().parents[1]/'protocol/cn-7.2'
        self.document,self.profile,*self.tables=[json.loads((directory/name).read_text('utf8')) for name in (
            'mappings.json','profile.json','scene-handshake-observations.json',
            'store-weight-contract-observations.json','initialization-wire-observations.json')]
        self.enter,self.domain=[json.loads((directory/name).read_text('utf8')) for name in (
            'enter-scene-contract-observations.json','cmd1017-domain-observations.json')]

    def check(self):
        return (validate_scene_fusion(self.document,self.profile,*self.tables)
                +validate_enter_scene_fusion(self.document,self.profile,self.enter,self.domain))

    def schema(self,name):
        return next(r for r in self.document['schemas'] if r['semanticName']==name)

    def test_reviewed_slice_is_consistent_but_entire_protocol_not_ready(self):
        self.assertEqual([],self.check())
        self.assertEqual([],validate(self.document,self.profile))
        self.assertTrue(validate(self.document,self.profile,ready=True))

    def test_fixed_target_and_live_status_cannot_drift(self):
        self.tables[0]['inputSHA256']['YuanShen.exe']='0'*64
        self.tables[1]['runtimeStatus']='passed'
        self.assertEqual(2,len(self.check()))

    def test_opcode_and_type_are_independent_claims(self):
        next(r for r in self.document['opcodes'] if r['semanticName']=='EnterSceneReadyReq')['cmdId']=20324
        next(r for r in self.document['messages'] if r['semanticName']=='SceneInitFinishRsp')['clientType']='OLD'
        self.assertEqual(2,len(self.check()))

    def test_native_tag_nested_and_value_codec_are_checked(self):
        self.schema('EnterSceneReadyReq')['fields'][0]['objectOffset']=28
        self.schema('SceneInitFinishRsp')['fields'][0]['applicationEncoding']={'applicationToWire':'identity'}
        next(f for f in self.schema('EnterSceneDoneRsp')['fields'] if f['tag']==12)['clientNestedType']='OldVector'
        self.assertEqual(3,len(self.check()))

    def test_unknown_fields_cannot_be_promoted_or_renamed(self):
        self.schema('PostEnterSceneReq')['fields'][0].update(semanticField='time',status='verified')
        next(f for f in self.schema('StoreWeightLimitNotify')['fields'] if f['tag']==2)['semanticField']='weight_limit'
        self.assertEqual(2,len(self.check()))

    def test_capacity_enum_and_native_purpose_are_checked(self):
        next(f for f in self.schema('StoreWeightLimitNotify')['fields'] if f['tag']==6)['enumValues']['StorePack']=7
        next(f for f in self.schema('StoreWeightLimitNotify')['fields'] if f['tag']==14)['semanticField']='weight_limit'
        self.assertEqual(2,len(self.check()))

    def test_notify_token_chain_cannot_be_replaced_by_old_identity_encoding(self):
        next(f for f in self.schema('PlayerEnterSceneNotify')['fields'] if f['tag']==10)['applicationEncoding']={}
        self.assertTrue(any('Notify' in e for e in self.check()))

    def test_enter_named_values_enum_and_unknown_fields_are_independent(self):
        fields=self.schema('PlayerEnterSceneNotify')['fields']
        next(f for f in fields if f['tag']==7)['semanticField']='current_scene_id'
        next(f for f in fields if f['tag']==8)['enumValues']['EnterSelf']=999
        next(f for f in fields if f['tag']==15).update(status='verified',semanticField='prev_pos')
        self.assertEqual(3,len(self.check()))

    def test_embedded_vector_axes_cannot_be_reordered(self):
        self.schema('Vector')['fields'][1]['semanticField']='z'
        self.assertTrue(any('axis mismatch' in e for e in self.check()))

    def test_1017_normal_domain_does_not_remove_global_static_duplicate(self):
        self.domain['globalStaticDuplicateRetained']=False
        self.domain['domains'][1]['direction']='S2C'
        self.assertTrue(any('global duplicate' in e for e in self.check()))


if __name__=='__main__':unittest.main()
