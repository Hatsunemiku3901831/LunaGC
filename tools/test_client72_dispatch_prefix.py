import copy
import json
from pathlib import Path
import unittest
from tools.trace_client72_dispatch_prefix import validate_summary

class DispatchPrefixTests(unittest.TestCase):
    def setUp(self):
        self.report=json.loads((Path(__file__).resolve().parents[1]/'protocol/cn-7.2/dispatch-prefix-observations.json').read_text('utf8'))

    def test_native_crypto_parser_summary_is_consistent(self):
        self.assertEqual(validate_summary(self.report),[])

    def test_local_trust_or_bounded_parser_cannot_claim_live_success(self):
        for key,value in [('runtimeStatus','passed'),('wholeDispatchExecuted',True),('semanticStatus','verified'),('sourceTypeIndex',37481)]:
            report=copy.deepcopy(self.report);report[key]=value
            self.assertTrue(validate_summary(report))
        report=copy.deepcopy(self.report);report['results'][0]['localTrust']=False
        self.assertTrue(validate_summary(report))

    def test_error_signature_and_source_wire_cannot_be_promoted_or_swapped(self):
        for index,key,value in [(0,'nativePlaintextSHA256','wrong'),(1,'plainWire',''),(3,'accepted',True),(4,'readerCalls',1),(5,'postParsePointerPreserved',True)]:
            report=copy.deepcopy(self.report);report['results'][index][key]=value
            self.assertTrue(validate_summary(report))
        report=copy.deepcopy(self.report);report['results'][0]['keyChecks'][0]['native']=False
        self.assertTrue(validate_summary(report))

if __name__=='__main__':unittest.main()
