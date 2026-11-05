import copy
import json
from pathlib import Path
import unittest
from tools.validate_protocol72 import validate_token_complete


class CompleteTokenMappingTests(unittest.TestCase):
    def setUp(self):
        root=Path(__file__).resolve().parents[1]/'protocol/cn-7.2'
        self.document=json.loads((root/'mappings.json').read_text('utf8'))
        self.profile=json.loads((root/'profile.json').read_text('utf8'))
        self.complete=json.loads((root/'token-complete-observations.json').read_text('utf8'))
        self.wire=json.loads((root/'token-wire-observations.json').read_text('utf8'))

    def validate(self):return validate_token_complete(self.document,self.profile,self.complete,self.wire)

    def test_target_mapping_matches_native_constructor_crypto_and_writer(self):
        self.assertEqual(self.validate(),[])

    def test_wrong_command_wire_or_return_boundary_cannot_keep_verified_mapping(self):
        original=copy.deepcopy(self.complete)
        for key,value in [('cmdId',23172),('helperNativeExecuted',False),('frameBuilderExecuted',True),('runtimeStatus','passed')]:
            self.complete=copy.deepcopy(original);self.complete[key]=value;self.assertTrue(self.validate())
        self.complete=copy.deepcopy(original);self.complete['seedBinding']['tag']=651;self.assertTrue(self.validate())
        self.complete=copy.deepcopy(original);self.complete['inputSHA256']={};self.assertTrue(self.validate())

    def test_rejection_cannot_be_reported_as_success_or_no_send(self):
        for key,value in [('ioctlReturn',0),('seedWireLength',344),('constructorStillSendsOnEmptySeed',False)]:
            altered=copy.deepcopy(self.complete);altered['results'][4][key]=value
            self.assertTrue(validate_token_complete(self.document,self.profile,altered,self.wire))
        field=next(f for s in self.document['schemas'] if s['semanticName']=='GetPlayerTokenReq'
                   for f in s['fields'] if f['tag']==623)
        field['semanticField']='key_id';self.assertTrue(self.validate())


if __name__=='__main__':unittest.main()
