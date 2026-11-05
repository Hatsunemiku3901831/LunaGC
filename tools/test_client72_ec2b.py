import copy
import hashlib
import json
from pathlib import Path
import struct
import unittest

from tools.client72_ec2b import initial_rng_bytes,LIVE_RNG_SHA256
from tools.trace_client72_ec2b import validate_summary


class Ec2bTests(unittest.TestCase):
    def setUp(self):
        self.report=json.loads((Path(__file__).resolve().parents[1]/
                                'protocol/cn-7.2/ec2b-observations.json').read_text('utf8'))

    def test_native_results_and_pair(self):
        self.assertEqual(validate_summary(self.report),[])

    def test_independent_rng_matches_live_snapshot(self):
        raw=initial_rng_bytes()
        self.assertEqual(len(raw),5000)
        self.assertEqual(raw[:24].hex(),'7002000067000000a44708771b911ef67b165d8dc19e0f40')
        self.assertEqual(struct.unpack_from('<I',raw,4996)[0],0xffffffff)
        self.assertEqual(raw[2500:4996],bytes(2496))
        self.assertEqual(hashlib.sha256(raw).hexdigest(),LIVE_RNG_SHA256)

    def test_runtime_or_dispatch_claim_is_not_allowed(self):
        for key,value in [('runtimeStatus','verified'),('dispatchIntegrated',True),
                          ('nativeCipherExecuted',False),('nativeRngInitialization',False),('models',[])]:
            r=copy.deepcopy(self.report);r[key]=value;self.assertTrue(validate_summary(r))

    def test_fixed_pair_or_input_drift_is_rejected(self):
        r=copy.deepcopy(self.report);r['fixtureSHA256']['dispatchSeed.bin']='0'*64
        self.assertTrue(validate_summary(r))
        r=copy.deepcopy(self.report);r['results'][0]['outputSHA256']='0'*64
        self.assertTrue(validate_summary(r))

    def test_native_rejection_does_not_write_output(self):
        for index in (5,6,7,8,9,10,12,14,15):
            r=copy.deepcopy(self.report);r['results'][index]['accepted']=True
            self.assertTrue(validate_summary(r))
            r=copy.deepcopy(self.report);r['results'][index]['unchangedOnReject']=False
            self.assertTrue(validate_summary(r))

    def test_short_declared_input_does_not_claim_physical_truncation(self):
        r=copy.deepcopy(self.report);r['results'][11]['backingInputLength']=1
        self.assertTrue(validate_summary(r))
        r=copy.deepcopy(self.report);r['results'].pop()
        self.assertTrue(validate_summary(r))


if __name__=='__main__':unittest.main()
