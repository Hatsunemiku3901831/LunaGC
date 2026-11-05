import copy
import json
from pathlib import Path
import unittest
from tools.trace_client72_dispatch_coroutine import validate_summary


class DispatchCoroutineTests(unittest.TestCase):
    def setUp(self):
        self.report=json.loads((Path(__file__).resolve().parents[1]/'protocol/cn-7.2/dispatch-coroutine-observations.json').read_text('utf8'))

    def test_native_resumed_path_evidence(self):
        self.assertEqual(validate_summary(self.report),[])

    def test_resumed_path_cannot_claim_initial_http_or_live_success(self):
        for key,value in [('wholeCoroutineExecuted',True),('runtimeStatus','passed'),('semanticStatus','verified')]:
            report=copy.deepcopy(self.report); report[key]=value
            self.assertTrue(validate_summary(report))
        report=copy.deepcopy(self.report); report['binding']['stateOffset']=72
        self.assertTrue(validate_summary(report))

    def test_state_or_source_cannot_be_swapped(self):
        for index,key,value in [(0,'stateAfter',2),(1,'state',2),(2,'nativePlaintextSHA256','wrong'),
                                (0,'sessionPointerPreserved',False),(1,'tokenFieldWire','aa3c00')]:
            report=copy.deepcopy(self.report); report['results'][index][key]=value
            self.assertTrue(validate_summary(report),key)

    def test_native_config_and_crypto_cannot_be_skipped(self):
        for key,value in [('configReturns',[]),('keyChecks',[]),('readerCalls',0),('sdkEntries',0)]:
            report=copy.deepcopy(self.report); report['results'][0][key]=value
            self.assertTrue(validate_summary(report),key)

    def test_error_routes_cannot_reach_store_or_token(self):
        for index,key,value in [(3,'errorMarker',''),(4,'stop','0x14713c680'),(5,'storeExecuted',True),
                                (6,'cmdId',24604),(7,'readerCalls',1)]:
            report=copy.deepcopy(self.report); report['results'][index][key]=value
            self.assertTrue(validate_summary(report),key)


if __name__=='__main__': unittest.main()
