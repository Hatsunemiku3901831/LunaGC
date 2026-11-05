import copy
import json
from pathlib import Path
import unittest
from tools.trace_client72_dispatch_session import validate_summary


class DispatchSessionTests(unittest.TestCase):
    def setUp(self):
        self.report=json.loads((Path(__file__).resolve().parents[1]/'protocol/cn-7.2/dispatch-session-observations.json').read_text('utf8'))

    def test_continuous_native_source_evidence(self):
        self.assertEqual(validate_summary(self.report),[])

    def test_no_semantic_or_actual_client_promotion(self):
        for key,value in [('semanticStatus','verified'),('runtimeStatus','passed'),('wholeDispatchExecuted',True),
                          ('secondCoroutineAssociation','verified'),('nativeSeedHelperReused',False)]:
            report=copy.deepcopy(self.report); report[key]=value
            self.assertTrue(validate_summary(report),key)
        report=copy.deepcopy(self.report); report['typeBindings'][5]['kind']='string'
        self.assertTrue(validate_summary(report))

    def test_rejects_lost_empty_or_64bit_text_wire_and_pointer(self):
        for index,key,value in [(0,'sessionPointerPreserved',False),(0,'tokenFieldWire','aa3c0130'),
                                (1,'tokenFieldWire','aa3c00'),(2,'cmdId',23172)]:
            report=copy.deepcopy(self.report); report['results'][index][key]=value
            self.assertTrue(validate_summary(report),key)

    def test_rejects_skipped_sdk_notice_or_container_cleanup(self):
        for key,value in [('sdkOutputs',[]),('sdkEntries',0),('noticeCalls',[]),('nativeCastCalls',0),('dictionaryReleases',[])]:
            report=copy.deepcopy(self.report); report['results'][0][key]=value
            self.assertTrue(validate_summary(report),key)

    def test_error_and_missing_session_cannot_write_or_send(self):
        for index,key,value in [(3,'storeExecuted',True),(4,'sdkEntries',1),(5,'jsonHelperRejected',False),
                                (3,'cmdId',24604),(4,'sessionUnchanged',False)]:
            report=copy.deepcopy(self.report); report['results'][index][key]=value
            self.assertTrue(validate_summary(report),key)


if __name__=='__main__': unittest.main()
