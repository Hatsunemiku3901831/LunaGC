import copy
import json
from pathlib import Path
import unittest
from tools.trace_client72_send_flow import slot_method
from tools.validate_protocol72 import validate_send_flow


class SendFlowTests(unittest.TestCase):
    def setUp(self):
        root=Path(__file__).resolve().parents[1]/'protocol/cn-7.2'
        self.doc,self.profile,self.flow=[json.loads((root/name).read_text('utf8')) for name in
                                      ('mappings.json','profile.json','send-flow-observations.json')]

    def errors(self):return validate_send_flow(self.doc,self.profile,self.flow)

    def test_observed_send_handoff_matches_known_requests(self):self.assertEqual([],self.errors())

    def test_native_virtual_slot_rejects_static_duplicate_or_different_target(self):
        method=dict(virtualSlot=4,registeredAddress='0x123',flags='0x86')
        self.assertEqual(method,slot_method(dict(methods=[method]),4,0x123))
        for rows in ([],[method,method],[dict(method,flags='0x96')],[dict(method,registeredAddress='0x456')]):
            with self.assertRaises(ValueError):slot_method(dict(methods=rows),4,0x123)

    def test_frame_or_live_execution_cannot_be_claimed(self):
        for key,value in [('frameBuilderExecuted',True),('runtimeStatus','verified'),('connectedStop','0x151214d20')]:
            original=copy.deepcopy(self.flow);self.flow[key]=value
            self.assertTrue(self.errors());self.flow=original

    def test_interface_parameter_and_source_hash_tampering_fail(self):
        self.flow['transportParameterBindings'][0]['envelopeTypeIndex']=22292;self.assertTrue(self.errors())
        self.setUp();self.flow['transportParameterBindings'].pop();self.assertTrue(self.errors())
        self.setUp();self.flow['inputSHA256'].pop('YuanShen.exe');self.assertTrue(self.errors())

    def test_command_source_or_request_set_drift_fails(self):
        self.flow['requestBindings'][0]['cmdId']=23172;self.assertTrue(self.errors())
        self.setUp();self.flow['requestBindings'][1]['commandGetter']='0x1500c4a50';self.assertTrue(self.errors())
        self.setUp();self.flow['requestBindings'][1]=self.flow['requestBindings'][0];self.assertTrue(self.errors())


if __name__=='__main__':unittest.main()
