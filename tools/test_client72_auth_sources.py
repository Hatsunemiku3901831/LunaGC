import copy
import json
from pathlib import Path
import unittest
from tools.validate_protocol72 import validate_auth_sources


class AuthSourceMappingTests(unittest.TestCase):
    def setUp(self):
        root=Path(__file__).resolve().parents[1]/'protocol/cn-7.2'
        self.document=json.loads((root/'mappings.json').read_text('utf8'))
        self.profile=json.loads((root/'profile.json').read_text('utf8'))
        self.auth=json.loads((root/'auth-sources-observations.json').read_text('utf8'))
        self.wire=json.loads((root/'token-wire-observations.json').read_text('utf8'))

    def test_verified_credentials_have_target_source_and_wire_provenance(self):
        self.assertEqual(validate_auth_sources(self.document,self.profile,self.auth,self.wire),[])

    def test_account_uid_token_channel_or_guest_cannot_be_swapped(self):
        for index,key,value in [(0,'source','data.combo_token'),(1,'semanticField','token'),(2,'type','int32'),(4,'tag',7)]:
            row=copy.deepcopy(self.auth);row['fieldBindings'][index][key]=value
            self.assertTrue(validate_auth_sources(self.document,self.profile,row,self.wire))
        row=copy.deepcopy(self.auth);row['declarations']['lazyClassType']=4346
        self.assertTrue(validate_auth_sources(self.document,self.profile,row,self.wire))

    def test_rejected_or_partial_callback_cannot_claim_complete_runtime_success(self):
        for key,value in [('wholeCallbackExecuted',True),('nativeSeedHelperExecuted',False),('runtimeStatus','passed'),('cmdId',23172)]:
            row=copy.deepcopy(self.auth);row[key]=value
            self.assertTrue(validate_auth_sources(self.document,self.profile,row,self.wire))
        for index in range(4,9):
            row=copy.deepcopy(self.auth);row['results'][index]['accepted']=True
            self.assertTrue(validate_auth_sources(self.document,self.profile,row,self.wire))


if __name__=='__main__':unittest.main()
