import copy
import json
from pathlib import Path
import unittest
from tools.validate_protocol72 import validate_login_semantics
from tools.trace_client72_login_semantics import static_address, static_field, field_at


class LoginSemanticsTests(unittest.TestCase):
    def setUp(self):
        root=Path(__file__).resolve().parents[1]/'protocol/cn-7.2'
        self.doc,self.profile,self.semantics,self.literals,self.enums,self.wire=[json.loads((root/n).read_text('utf8')) for n in
            ('mappings.json','profile.json','login-semantic-observations.json','login-literal-observations.json','enum-observations.json','login-wire-observations.json')]

    def errors(self):return validate_login_semantics(self.doc,self.profile,self.semantics,self.literals,self.enums,self.wire)

    def test_current_evidence_is_consistent(self):self.assertEqual([],self.errors())

    def test_handler_label_type_and_opcode_cannot_be_substituted(self):
        for key,value in [('cmdId',1),('clientType','Wrong'),('literalIndex',46140),('declarationCheck',None)]:
            original=copy.deepcopy(self.semantics)
            self.semantics['roles'][0][key]=value
            self.assertTrue(self.errors());self.semantics=original

    def test_semantic_wire_and_name_tampering_rejected(self):
        for key,value in [('tag',1),('semanticField','guessed_name'),('type','uint64'),('status','candidate')]:
            original=copy.deepcopy(self.semantics);self.semantics['fieldBindings'][0][key]=value
            self.assertTrue(self.errors());self.semantics=original

    def test_enum_cases_are_independent_of_symbol_order(self):
        enum=next(e for e in self.enums['enums'] if e['clientType']=='HGPBEGGCNED')
        enum['values'].reverse();self.assertEqual([],self.errors())
        enum['values'][0]['value']=99;self.assertTrue(self.errors())

    def test_evidence_hashes_and_missing_field_bindings_rejected(self):
        self.enums['inputSHA256']['YuanShen.exe']='0'*64;self.assertTrue(self.errors())
        self.setUp();self.semantics['fieldBindings'].pop(0);self.assertTrue(self.errors())

    def test_missing_role_or_language_domain_cannot_retain_confirmation(self):
        self.semantics['roles'].pop(0);self.assertTrue(self.errors())
        self.setUp();self.enums['enums']=[e for e in self.enums['enums'] if e['clientType']!='DCIIBLHGNIN']
        self.assertTrue(self.errors())

    def test_instance_and_named_static_storage_fail_closed(self):
        self.assertEqual(static_address(0x20019a4),0x14586cfb4)
        with self.assertRaises(ValueError):static_address(0x1000010)
        row=dict(index=1,name='Owner',fields=[dict(name='named',rawOffset='0x4000020',offsetKind='runtime')])
        self.assertEqual(static_field({1:row},0x4000020,'Owner','named')['name'],'named')
        with self.assertRaises(ValueError):static_field({1:row},0x4000020,'Owner','guessed')
        with self.assertRaises(ValueError):field_at({1:row},1,32)


if __name__=='__main__':unittest.main()
