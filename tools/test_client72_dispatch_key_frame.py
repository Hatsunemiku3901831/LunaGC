import copy
import json
from pathlib import Path
from types import SimpleNamespace
import unittest
from unittest.mock import Mock

from tools.trace_client72_dispatch_key_frame import dispatch_wire, xor_bytes, validate_summary
from tools.trace_client72_dispatch_config import DispatchConfig72


class DispatchKeyFrameTests(unittest.TestCase):
    def setUp(self):
        self.report=json.loads((Path(__file__).resolve().parents[1]/
                               'protocol/cn-7.2/dispatch-key-frame-observations.json').read_text('utf8'))

    def test_native_dispatch_and_frame_evidence(self):
        self.assertEqual(validate_summary(self.report),[])

    def test_independent_wire_and_xor_boundary(self):
        self.assertEqual(xor_bytes(bytes.fromhex('0001020304'),b'\x01\x80').hex(),'0181038305')
        self.assertEqual(dispatch_wire(b'abc',b'').hex(),
            '7206736f757263651a0062177b22646f776e6c6f61645468726561644e756d223a377d'
            '6a167b2273686f77657863657074696f6e223a747275657d5a03616263')

    def test_config_reads_mutated_memory_instead_of_cipher_snapshot(self):
        raw='{"source":"中文🚀"}'.encode('utf-8')
        uc=Mock(); uc.mem_read.return_value=raw
        e=SimpleNamespace(reg=lambda k:0x100 if k=='rcx' else 0x200,
                          read=lambda p:len(raw),uc=uc)
        runtime=SimpleNamespace(e=e,encoding=0x100,array_values={0x200:b'\xb2\xff'},string=lambda v:v)
        self.assertEqual(DispatchConfig72.get_string(runtime),'{"source":"中文🚀"}')
        uc.mem_read.assert_called_once_with(0x220,len(raw))

    def test_scope_cannot_be_promoted(self):
        for key,value in [('runtimeStatus','passed'),('semanticStatus','verified'),
                          ('schedulerExecuted',True),('frameKeySupplied',True),('models',[])]:
            report=copy.deepcopy(self.report); report[key]=value
            self.assertTrue(validate_summary(report),key)

    def test_key_source_and_clear_drift_rejected(self):
        for index,key,value in [(0,'keySHA256','0'*64),(0,'rsaBlocks',1),(0,'sourceSHA256','0'*64),
                                (1,'configKeyCleared',[False,True]),(2,'keyLength',4096),(3,'keyCalls',3)]:
            report=copy.deepcopy(self.report); report['results'][index][key]=value
            self.assertTrue(validate_summary(report),key)

    def test_frame_continuity_and_boundaries_rejected(self):
        for key,value in [('keyPointerPreserved',False),('allTokenFieldsMatch',False),('keyWrap',False),
                          ('sentMs',0x44),('plainFrame',True),('seedIndependentOracle',False)]:
            report=copy.deepcopy(self.report); report['results'][1]['frame'][key]=value
            self.assertTrue(validate_summary(report),key)


if __name__=='__main__':unittest.main()
