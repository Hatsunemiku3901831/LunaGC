import copy
import json
from pathlib import Path
import unittest
from tools.validate_protocol72 import validate_request_identities


class RequestIdentityTests(unittest.TestCase):
    def setUp(self):
        root=Path(__file__).resolve().parents[1]/'protocol/cn-7.2'
        self.doc,self.profile,self.identity,self.enums,self.responses=[json.loads((root/name).read_text('utf8')) for name in
            ('mappings.json','profile.json','request-identity-observations.json','enum-observations.json','login-semantic-observations.json')]

    def errors(self):return validate_request_identities(self.doc,self.profile,self.identity,self.enums,self.responses)

    def test_current_stage_role_evidence_consistent(self):self.assertEqual([],self.errors())

    def test_stage_parameter_and_target_inputs_cannot_be_substituted(self):
        self.identity['stageDeclaration']['enumTypeIndex']=20469;self.assertTrue(self.errors())
        self.setUp();self.identity['inputSHA256'].pop('YuanShen.exe');self.assertTrue(self.errors())

    def test_request_response_stage_values_and_command_are_independent(self):
        for key,value in [('stageValue',9),('responseStageValue',8),('cmdId',27808),('clientType','CLDBKDNJHFG'),
                          ('responseHandler','0x151221d70'),('selfClassCell','0x1458c3df0'),('getter','0x0')]:
            original=copy.deepcopy(self.identity);self.identity['roles'][0][key]=value
            self.assertTrue(self.errors());self.identity=original

    def test_missing_duplicate_or_extra_role_rejected(self):
        self.identity['roles'].pop();self.assertTrue(self.errors())
        self.setUp();self.identity['roles'][1]=self.identity['roles'][0];self.assertTrue(self.errors())
        self.setUp();self.identity['roles'].append(copy.deepcopy(self.identity['roles'][0]));self.assertTrue(self.errors())

    def test_missing_enum_and_changed_symbol_cannot_retain_identity(self):
        self.enums['enums']=[e for e in self.enums['enums'] if e['typeIndex']!=85949];self.assertTrue(self.errors())
        self.setUp();enum=next(e for e in self.enums['enums'] if e['typeIndex']==85949)
        next(v for v in enum['values'] if v['symbol']=='OnPlayerTokenReq')['value']=7
        self.assertTrue(self.errors())


if __name__=='__main__':unittest.main()
