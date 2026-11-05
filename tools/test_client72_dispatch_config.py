import copy
import json
from pathlib import Path
import unittest
from tools.trace_client72_dispatch_config import validate_summary


class DispatchConfigTests(unittest.TestCase):
    def setUp(self):
        self.report=json.loads((Path(__file__).resolve().parents[1]/'protocol/cn-7.2/dispatch-config-observations.json').read_text('utf8'))

    def test_native_config_evidence(self):
        self.assertEqual(validate_summary(self.report),[])

    def test_scope_cannot_claim_client_or_unknown_semantics(self):
        for key,value in [('wholeDispatchExecuted',True),('runtimeStatus','passed'),('semanticStatus','verified')]:
            report=copy.deepcopy(self.report); report[key]=value
            self.assertTrue(validate_summary(report))

    def test_rejects_lost_list_scalar_or_region_value(self):
        for index,key,value in [(1,'configReturns',[]),(2,'events',[]),(0,'regionGlobals',None),
                                (3,'plainWire',''),(0,'sessionB0Unchanged',False)]:
            report=copy.deepcopy(self.report); report['results'][index][key]=value
            self.assertTrue(validate_summary(report),key)
        report=copy.deepcopy(self.report)
        report['results'][2]['configReturns'][1]['values']['secureOptionalResourcePolicyPlugins']=[2147483647]
        self.assertTrue(validate_summary(report))
        report=copy.deepcopy(self.report)
        report['results'][3]['configReturns'][1]['values']['downloadThreadNum']=2147483648
        self.assertTrue(validate_summary(report))

    def test_json_failure_cannot_continue_or_emit_success_event(self):
        for index,key,value in [(4,'events',[dict(id=919,mode=1)]),(5,'jsonHelperRejected',False),
                                (4,'configCalls',[]),(5,'stop',self.report['stop'])]:
            report=copy.deepcopy(self.report); report['results'][index][key]=value
            self.assertTrue(validate_summary(report),key)


if __name__=='__main__': unittest.main()
