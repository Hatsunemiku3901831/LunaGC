import copy
import json
from pathlib import Path
import unittest
from tools.trace_client72_handler_login import validate_summary


class HandlerLoginTests(unittest.TestCase):
    def setUp(self):
        self.report=json.loads((Path(__file__).resolve().parents[1]/'protocol/cn-7.2/handler-login-observations.json').read_text('utf8'))

    def test_continuous_handler_constructor_and_send_evidence(self):
        self.assertEqual(validate_summary(self.report),[])

    def test_offline_prefix_cannot_claim_live_or_whole_handler_success(self):
        for key,value in [('runtimeStatus','passed'),('wholeHandlerReturned',True),('frameBuilderExecuted',True),
                          ('continuousLoginExecuted',False),('trustInputReplacements',[])]:
            r=copy.deepcopy(self.report);r[key]=value;self.assertTrue(validate_summary(r))

    def test_login_level_and_package_numbers_cannot_be_confused(self):
        for enum_name,symbol,value in [('WorldType','Login',2),('PackageType','Full',3),('PackageType','PlayGo',5)]:
            r=copy.deepcopy(self.report)
            item=next(v for enum in r['declarations']['environmentEnums'] if enum['clientType']==enum_name
                      for v in enum['values'] if v['symbol']==symbol)
            item['value']=value;self.assertTrue(validate_summary(r))
        r=copy.deepcopy(self.report);r['declarations']['callback']['methodDefinition']=467729
        self.assertTrue(validate_summary(r))

    def test_pointer_identity_and_native_callback_cannot_be_omitted(self):
        for key,value in [('delegates',[]),('tokenHashPointersPreserved',False),('requestUnchanged',False),('stages',[6])]:
            r=copy.deepcopy(self.report);r['results'][0][key]=value;self.assertTrue(validate_summary(r))

    def test_header_and_hash_wire_cannot_be_replaced(self):
        r=copy.deepcopy(self.report);r['results'][1]['headerWire']='18013001'
        self.assertTrue(validate_summary(r))
        r=copy.deepcopy(self.report);r['results'][1]['wireFields']['1996']=''
        self.assertTrue(validate_summary(r))
        r=copy.deepcopy(self.report);r['results'][2]['wireFields']['1']='61'
        self.assertTrue(validate_summary(r))

    def test_conditional_and_error_routes_cannot_send_login(self):
        for index in range(3,9):
            r=copy.deepcopy(self.report);r['results'][index]['requestSent']=True
            self.assertTrue(validate_summary(r))
        r=copy.deepcopy(self.report);r['results'][8]['javaByteComparison']=True
        self.assertTrue(validate_summary(r))
        r=copy.deepcopy(self.report);r['results'][3]['inputs']['threshold']=0.0
        self.assertTrue(validate_summary(r))


if __name__=='__main__':unittest.main()
