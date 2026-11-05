import copy
import json
from pathlib import Path
import unittest

from tools.trace_client72_token_frame import TokenFrame72, validate_summary


class TokenFrameTests(unittest.TestCase):
    def setUp(self):
        self.report=json.loads((Path(__file__).resolve().parents[1]/
                                'protocol/cn-7.2/token-frame-observations.json').read_text('utf8'))

    def test_continuous_native_evidence(self):
        self.assertEqual(validate_summary(self.report),[])

    def test_opaque_payload_callbacks_cannot_be_used(self):
        for callback in (TokenFrame72.payload_size,TokenFrame72.payload_write):
            with self.assertRaises(ValueError):callback(None)

    def test_missing_native_stage_or_live_claim_fails(self):
        for key,value in [('continuousTokenFrame',False),('payloadWriterExecuted',False),
                          ('payloadSizeExecuted',False),('seedHelperExecuted',False),
                          ('socketExecuted',True),('runtimeStatus','passed'),('wholeHandlerReturned',True)]:
            report=copy.deepcopy(self.report);report[key]=value
            self.assertTrue(validate_summary(report))

    def test_original_request_and_seed_pointers_are_required(self):
        for key in ('requestPointerPreserved','requestUnchanged','seedPointerPreserved','allFieldWireComparison'):
            report=copy.deepcopy(self.report);report['results'][0][key]=False
            self.assertTrue(validate_summary(report))

    def test_error_seed_cannot_be_replaced_with_success(self):
        for index in (4,5):
            report=copy.deepcopy(self.report);report['results'][index]['seedWireLength']=344
            self.assertTrue(validate_summary(report))
            report=copy.deepcopy(self.report);report['results'][index]['ioctlReturn']=0
            self.assertTrue(validate_summary(report))

    def test_unicode_key_wrap_and_key_selector_remain_covered(self):
        for index,key,value in [(2,'encodingCalls',{}),(3,'frameLength',4096),(0,'selector',0),(1,'headerWire','')]:
            report=copy.deepcopy(self.report);report['results'][index][key]=value
            self.assertTrue(validate_summary(report))


if __name__=='__main__':unittest.main()
