import copy
import json
from pathlib import Path
import unittest

from tools.trace_client72_frame_reader import fixture_cases, validate_summary


class FrameReaderTests(unittest.TestCase):
    def setUp(self):
        self.report = json.loads((Path(__file__).resolve().parents[1] /
                                  'protocol/cn-7.2/frame-reader-observations.json').read_text('utf8'))

    def test_native_receive_evidence(self):
        self.assertEqual(validate_summary(self.report),[])

    def test_fixed_input_vectors_and_u32_length(self):
        cases = {c['name']:c for c in fixture_cases()}
        self.assertEqual(cases['empty']['plain'].hex(),'4567000000000000000089ab')
        self.assertEqual(cases['body-u32']['plain'][:10].hex(),'45678000000c00010000')
        self.assertEqual(cases['duplicate-header']['plain'].hex(),
                         '45670001000800000003180118023003300464757089ab')

    def test_no_payload_or_live_claim(self):
        for key,value in [('runtimeStatus','passed'),('actualFactoryExecuted',True),
                          ('payloadReaderExecuted',True),('wholeHandlerReturned',True),('models',[])]:
            report = copy.deepcopy(self.report);report[key]=value
            self.assertTrue(validate_summary(report))

    def test_native_error_codes_and_bad_header_stops(self):
        for name in ('bad-start','bad-end','truncated-frame','short-frame','null-array','wrong-key',
                     'truncated-header-varint','invalid-header-tag'):
            report = copy.deepcopy(self.report)
            next(r for r in report['results'] if r['name']==name)['returnCode']=1
            self.assertTrue(validate_summary(report))

    def test_body_before_trailer_and_trailing_length(self):
        for name,key,value in [('bad-end','bodyConsumed',False),('bad-end','readerReleased',True),
                               ('trailing-bytes','packetLength',999),('body-u32','bodySHA256','0'*64)]:
            report = copy.deepcopy(self.report)
            next(r for r in report['results'] if r['name']==name)[key]=value
            self.assertTrue(validate_summary(report))

    def test_key_selection_and_uint64_header_evidence(self):
        for name,key,value in [('alternate-wrap','selector',0),('plaintext-flag','xorIterations',1),
                               ('max-command-header','sentMs',0xffffffff),('duplicate-header','sequence',1)]:
            report = copy.deepcopy(self.report)
            next(r for r in report['results'] if r['name']==name)[key]=value
            self.assertTrue(validate_summary(report))


if __name__=='__main__':unittest.main()
