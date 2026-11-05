import copy
import json
from pathlib import Path
from types import SimpleNamespace
import unittest

from tools.trace_client72_handler_login_frame import object_proto_text,validate_summary


class HandlerLoginFrameTests(unittest.TestCase):
    def setUp(self):
        self.report=json.loads((Path(__file__).resolve().parents[1]/
                               'protocol/cn-7.2/handler-login-frame-observations.json').read_text('utf8'))

    def test_native_frames_and_independent_comparisons(self):
        self.assertEqual(validate_summary(self.report),[])

    def test_snapshot_preserves_utf8_signed_64bit_and_nested_values(self):
        rows=[('signed',24,'int32'),('large',32,'uint64'),('text',40,'string'),
              ('nested',48,'message'),('blob',56,'bytes')]
        fields=[dict(clientField=n,objectOffset=o,type=t,cardinality='singular',nestedTypeIndex=2) for n,o,t in rows]
        schemas={1:dict(fields=fields),2:dict(fields=[dict(clientField='flag',objectOffset=24,type='bool',cardinality='singular')])}
        memory={(0x1018,'i'):-123,(0x1020,'Q'):0x200000044,(0x1028,'Q'):0x2000,
                (0x1030,'Q'):0x3000,(0x1038,'Q'):0x4000,(0x3018,'B'):1,
                (0x4018,'Q'):0x5000,(0x5018,'Q'):3}
        def read_bytes(address,size):
            self.assertEqual((address,size),(0x5020,3));return b'\0\xff\x80'
        e=SimpleNamespace(read=lambda p,fmt='Q':memory[p,fmt],uc=SimpleNamespace(mem_read=read_bytes))
        ctor=SimpleNamespace(text=lambda p:'中\0🚀' if p==0x2000 else self.fail('wrong string pointer'))
        self.assertEqual(object_proto_text(e,ctor,schemas,1,0x1000),
            r'signed: -123 large: 8589934660 text: "\344\270\255\000\360\237\232\200" nested { flag: true } blob: "\000\377\200"')

    def test_no_client_or_whole_handler_claim(self):
        for key,value in [('runtimeStatus','passed'),('wholeHandlerReturned',True),('frameKeySupplied',True),
                          ('continuousHandlerToFrameReturn',False),('models',[]),('protocSHA256','')]:
            r=copy.deepcopy(self.report);r[key]=value;self.assertTrue(validate_summary(r),key)

    def test_frame_pointer_and_wire_drift_rejected(self):
        for key,value in [('keyPointerPreserved',False),('protocPayloadMatch',False),('independentFrameMatch',False),
                          ('boundNativeTypes',[]),('length',0),('selector',1)]:
            r=copy.deepcopy(self.report);r['results'][0]['frame'][key]=value;self.assertTrue(validate_summary(r),key)

    def test_unicode_key_cycle_and_header_boundaries(self):
        for key,value in [('keyWrap',False),('tokenUTF8Match',False),('tokenByteLength',4200),('sentMs',68)]:
            r=copy.deepcopy(self.report);r['results'][3]['frame'][key]=value;self.assertTrue(validate_summary(r),key)

    def test_rejected_or_delayed_handlers_cannot_send(self):
        for index in range(4,10):
            r=copy.deepcopy(self.report);r['results'][index]['frameReturned']=True;self.assertTrue(validate_summary(r))
        r=copy.deepcopy(self.report);r['results'][-1]['javaByteComparison']=True;self.assertTrue(validate_summary(r))
        r=copy.deepcopy(self.report);r['results'][0]['nativeKeyChecks']=[];self.assertTrue(validate_summary(r))


if __name__=='__main__':unittest.main()
