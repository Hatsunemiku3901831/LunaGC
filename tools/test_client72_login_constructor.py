import copy
import json
from pathlib import Path
import unittest
from tools.trace_client72_login_constructor import fixture_uint32
from tools.validate_protocol72 import validate_login_constructor


class LoginConstructorTests(unittest.TestCase):
    def setUp(self):
        root=Path(__file__).resolve().parents[1]/'protocol/cn-7.2'
        self.doc,self.profile,self.constructor,self.wire=[json.loads((root/name).read_text('utf8')) for name in
            ('mappings.json','profile.json','login-constructor-observations.json','login-wire-observations.json')]

    def errors(self):return validate_login_constructor(self.doc,self.profile,self.constructor,self.wire)

    def test_bounded_decimal_model_handles_uint32_edges_and_failures(self):
        for text,result in [('0',(True,0)),('4294967295',(True,0xffffffff)),('4294967296',(False,0)),
                            ('bad',(False,0)),('',(False,0)),('１２',(False,0))]:
            self.assertEqual(fixture_uint32(text),result)

    def test_numeric_model_refuses_unbounded_or_non_string_inputs(self):
        for value in (None,123,'1'*33):
            with self.assertRaises(ValueError):fixture_uint32(value)

    def test_current_constructor_source_evidence_matches_wire(self):self.assertEqual([],self.errors())

    def test_source_key_wire_and_boundary_tampering_fail(self):
        for key,value in [('sourceKey','info_get_token'),('tag',1),('status','candidate'),('type','bytes')]:
            original=copy.deepcopy(self.constructor);self.constructor['fieldBindings'][0][key]=value
            self.assertTrue(self.errors());self.constructor=original
        self.constructor['stop']='0x151213ac6';self.assertTrue(self.errors())
        self.setUp();self.constructor['inputSHA256'].pop('YuanShen.exe');self.assertTrue(self.errors())

    def test_missing_duplicate_and_unobserved_sdk_claims_fail(self):
        self.constructor['fieldBindings'].pop();self.assertTrue(self.errors())
        self.setUp();self.constructor['fieldBindings'][1]=self.constructor['fieldBindings'][0];self.assertTrue(self.errors())
        self.setUp();schema=next(s for s in self.doc['schemas'] if s['semanticName']=='PlayerLoginReq')
        field=next(f for f in schema['fields'] if f['tag']==624)
        field['evidence'].append('login-sdk-source');self.assertTrue(self.errors())


if __name__=='__main__':unittest.main()
