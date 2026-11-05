import copy
import json
from pathlib import Path
import unittest
from tools.trace_client72_auth_tail import validate_summary

class AuthTailTests(unittest.TestCase):
    def setUp(self):
        self.summary=json.loads((Path(__file__).resolve().parents[1]/'protocol/cn-7.2/auth-tail-observations.json').read_text())

    def test_native_summary_is_bounded_and_consistent(self):
        self.assertEqual(validate_summary(self.summary),[])

    def test_optional_source_or_exact_one_flags_cannot_drift(self):
        for key,value in [('session',{}),('exactOneFlags',[1,1]),('b0Unchanged',False),('passed',False)]:
            report=copy.deepcopy(self.summary);report['results'][0][key]=value
            self.assertTrue(validate_summary(report))
        report=copy.deepcopy(self.summary);report['sessionBindings'][0]['sessionOffset']=0xb0
        self.assertTrue(validate_summary(report))

    def test_bounded_observation_cannot_claim_protocol_or_runtime_success(self):
        for key,value in [('semanticPromotions',['country']),('wholeCallbackExecuted',True),('runtimeStatus','passed'),('inputSHA256',{})]:
            report=copy.deepcopy(self.summary);report[key]=value
            self.assertTrue(validate_summary(report))

if __name__=='__main__':unittest.main()
