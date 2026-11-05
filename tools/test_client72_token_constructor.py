import copy
import json
from pathlib import Path
import unittest
from tools.validate_protocol72 import validate_token_constructor


class TokenConstructorTests(unittest.TestCase):
    def setUp(self):
        root=Path(__file__).resolve().parents[1]/'protocol/cn-7.2'
        self.doc,self.profile,self.constructor,self.wire=[json.loads((root/name).read_text('utf8')) for name in
            ('mappings.json','profile.json','token-constructor-observations.json','token-wire-observations.json')]

    def errors(self):return validate_token_constructor(self.doc,self.profile,self.constructor,self.wire)

    def test_current_bounded_observations_match_mapping(self):self.assertEqual([],self.errors())

    def test_crypto_boundary_cannot_be_claimed_executed_or_semantically_known(self):
        for key,value in [('nativeExecuted',True),('semanticStatus','verified'),('tag',10)]:
            original=copy.deepcopy(self.constructor)
            self.constructor['opaqueSeedBoundary'][key]=value
            self.assertTrue(self.errors());self.constructor=original
        self.constructor['completion']='complete';self.assertTrue(self.errors())

    def test_sdk_key_tag_type_missing_and_duplicate_bindings_fail(self):
        for key,value in [('sourceKey','info_get_channel_id'),('tag',12),('type','string')]:
            original=copy.deepcopy(self.constructor);self.constructor['fieldBindings'][0][key]=value
            self.assertTrue(self.errors());self.constructor=original
        self.constructor['fieldBindings']*=2;self.assertTrue(self.errors())
        self.constructor['fieldBindings']=[];self.assertTrue(self.errors())

    def test_raw_source_drift_or_unwarranted_semantics_fails(self):
        self.constructor['rawSessionBindings'][0]['sessionOffset']=0x98;self.assertTrue(self.errors())
        self.setUp();self.constructor['rawSessionBindings'][0]['semanticStatus']='verified';self.assertTrue(self.errors())
        self.setUp();self.constructor['rawOtherBindings'].pop();self.assertTrue(self.errors())
        self.setUp();field=next(f for s in self.doc['schemas'] if s['semanticName']=='GetPlayerTokenReq'
                                for f in s['fields'] if f['tag']==623)
        field['evidence'].append('token-sdk-source');self.assertTrue(self.errors())

    def test_target_hash_and_wire_disagreement_fail(self):
        self.constructor['inputSHA256'].pop('YuanShen.exe');self.assertTrue(self.errors())
        self.setUp();next(f for f in self.wire['fields'] if f['tag']==10)['clientField']='wrong'
        self.assertTrue(self.errors())


if __name__=='__main__':unittest.main()
