import copy
import json
from pathlib import Path
import unittest

from tools.trace_client72_frame import expected_frame, validate_summary


class FrameTests(unittest.TestCase):
    def setUp(self):
        self.report = json.loads((Path(__file__).resolve().parents[1] /
                                  'protocol/cn-7.2/frame-observations.json').read_text('utf-8'))

    def test_native_observations(self):
        self.assertEqual([], validate_summary(self.report))

    def test_independent_big_endian_empty_and_nonzero_vectors(self):
        self.assertEqual(expected_frame(0,b'',0,0,None).hex(), '4567000000000000000089ab')
        self.assertEqual(expected_frame(0x8000,b'\xde\xad',1,128,None).hex(),
                         '456780000005000000021801308001dead89ab')
        self.assertEqual(expected_frame(0,b'',0,0,b'\x01\x80').hex(), '44e70180018001800180882b')

    def test_large_body_length_is_four_bytes(self):
        frame = expected_frame(65535,bytes(65536),0,0,None)
        self.assertEqual(frame[:10].hex(),'4567ffff000000010000')
        self.assertEqual(len(frame),65548)

    def test_cannot_claim_payload_or_live_execution(self):
        for key, value in [('runtimeStatus','passed'),('payloadWriterExecuted',True),
                           ('wholeHandlerReturned',True),('frameBuilderExecuted',False),('models',[])]:
            report = copy.deepcopy(self.report)
            report[key] = value
            self.assertTrue(validate_summary(report))

    def test_boundary_coverage_cannot_be_dropped(self):
        for name,key,value in [('max-command','command',32767),('body-u32','payloadLength',65535),
                               ('initial-key-wrap','selector',0),('max-command','sentMs',1)]:
            report = copy.deepcopy(self.report)
            next(r for r in report['results'] if r['name']==name)[key] = value
            self.assertTrue(validate_summary(report))

    def test_rejection_and_missing_key_are_not_successful_encryption(self):
        for name,key,value in [('missing-message','frameProduced',True),('missing-stream','frameLength',12),
                               ('readonly-stream','stop','0x620000100'),
                               ('missing-selected-session-key','frameSHA256','0'*64)]:
            report = copy.deepcopy(self.report)
            next(r for r in report['results'] if r['name']==name)[key] = value
            self.assertTrue(validate_summary(report))


if __name__ == '__main__': unittest.main()
