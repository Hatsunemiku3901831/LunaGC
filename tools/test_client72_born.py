"""Independent wire/protoc contract regressions for the B-02d handoff."""
import copy
import json
from pathlib import Path
import unittest
from tools.trace_client72_born import request_wire, response_wire, protoc
from tools.client72_reader import WireInput
from tools.trace_client72_token import parse_wire
from tools.validate_protocol72 import validate, validate_observations

ROOT=Path(__file__).resolve().parents[1]

class BornContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compiler=next((ROOT/'local/gradle/caches/modules-2/files-2.1/com.google.protobuf/protoc/3.18.1').rglob('*.exe'))

    def test_request_vectors(self):
        self.assertEqual(request_wire(10000005,'Aether').hex(),'22064165746865725885ade204')
        self.assertEqual(request_wire(0xffffffff,'x').hex(),'22017858ffffffff0f')
        self.assertEqual(request_wire(0,''),b'')
        for bad in (-1,1<<32,True):
            with self.assertRaises(ValueError):request_wire(bad,'x')

    def test_signed_response_vectors(self):
        self.assertEqual(response_wire(132).hex(),'788401')
        self.assertEqual(response_wire(116).hex(),'7874')
        self.assertEqual(response_wire(-1).hex(),'78ffffffffffffffffff01')
        self.assertEqual(response_wire(-2147483648).hex(),'7880808080f8ffffffff01')
        for bad in (-2147483649,2147483648,False):
            with self.assertRaises(ValueError):response_wire(bad)

    def test_protoc_decodes_native_request_vector_and_unicode(self):
        decoded=protoc(self.compiler,'decode','SetPlayerBornDataReq',bytes.fromhex('22064165746865725885ade204')).decode()
        self.assertIn('avatar_id: 10000005',decoded);self.assertIn('nick_name: "Aether"',decoded)
        raw=request_wire(10000007,'旅人🚀\0')
        text=protoc(self.compiler,'decode','SetPlayerBornDataReq',raw)
        self.assertEqual(protoc(self.compiler,'encode','SetPlayerBornDataReq',text),raw)
        self.assertEqual(parse_wire(raw)[0][2],'旅人🚀\0'.encode())

    def test_response_duplicate_wrong_wire_and_unknown(self):
        decoded=protoc(self.compiler,'decode','SetPlayerBornDataRsp',bytes.fromhex('78017884017a01780801')).decode()
        self.assertIn('retcode: 132',decoded)
        self.assertIn('15: "x"',decoded)
        self.assertIn('1: 1',decoded)

    def test_notification_empty_is_target_specific_and_malformed_model_rejects(self):
        self.assertEqual(protoc(self.compiler,'encode','DoSetPlayerBornDataNotify',b''),b'')
        self.assertIn('1: 1',protoc(self.compiler,'decode','DoSetPlayerBornDataNotify',b'\x08\x01').decode())
        for wire in (b'\x08\x80',b'\x12\x03x',b'\x00',b'\x08'+b'\x80'*10):
            inp=WireInput(wire)
            with self.assertRaises(ValueError):
                while inp.tag():inp.skip()
            with self.assertRaises(ValueError):protoc(self.compiler,'decode','DoSetPlayerBornDataNotify',wire)

    def test_mapping_drift_and_unrelated_unknowns(self):
        def load(n):return json.loads((ROOT/'protocol/cn-7.2'/n).read_text('utf8'))
        document=load('mappings.json');profile=load('profile.json');observed=load('born-observations.json')
        self.assertFalse(validate(document,profile))
        self.assertTrue(validate(document,profile,ready=True))
        # Isolate the trio to check the existing independent observation guard.
        names={r['semanticName'] for r in observed['roles']}
        for k in ('opcodes','messages','schemas'):document[k]=[r for r in document[k] if r['semanticName'] in names]
        command=load('commands.json');empty=dict(inputSHA256=observed['inputSHA256'],messages=[])
        self.assertFalse(validate_observations(document,profile,command,empty,observations=[('born',observed)]))
        changed=copy.deepcopy(document)
        next(r for r in changed['schemas'] if r['semanticName']=='SetPlayerBornDataReq')['fields'][1]['tag']=2
        self.assertTrue(validate_observations(changed,profile,command,empty,observations=[('born',observed)]))
        changed=copy.deepcopy(document);changed['opcodes'][0]['cmdId']=123
        self.assertTrue(validate_observations(changed,profile,command,empty,observations=[('born',observed)]))
        self.assertEqual(observed['runtimeStatus'],'not-tested')

if __name__=='__main__':unittest.main()
