import copy
import json
import tempfile
import unittest
from pathlib import Path

from tools.migration72 import scene_mplevel_actual_codec9_prefix as subject


class PrefixGatesTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.req=json.loads((subject.ROOT/subject.REQUEST).read_bytes())
        paths=list(cls.req['fixedInputs'])
        cls.blob=(subject.ROOT/paths[0]).read_bytes()
        cls.report=json.loads((subject.ROOT/paths[1]).read_bytes())
        cls.header=(subject.ROOT/paths[2]).read_bytes()

    def test_fixed_inputs_and_parsed_intervals(self):
        info=subject.input_gate(self.blob,self.header,self.report,self.req)['parsed']
        self.assertEqual((info['compressionTag'],info['compressedEnds']),(9,[212058,284418]))
        with self.assertRaisesRegex(ValueError,'blob mismatch'):
            subject.input_gate(self.blob[:-1],self.header,self.report,self.req)
        with self.assertRaisesRegex(ValueError,'header mismatch'):
            subject.input_gate(self.blob,bytes([self.header[0]^1])+self.header[1:],self.report,self.req)
        wrong=copy.deepcopy(self.report);wrong['header']['parsed']['compressedEnds'][1]-=1
        with self.assertRaisesRegex(ValueError,'header report mismatch'):
            subject.input_gate(self.blob,self.header,wrong,self.req)

    def test_out_of_range_index_rejected(self):
        for i in (0,1):subject.index_gate(i)
        for i in (-1,2):
            with self.assertRaisesRegex(ValueError,'index outside'):subject.index_gate(i)

    def test_file_pin_rejects_source_and_table_drift(self):
        with tempfile.TemporaryDirectory() as d:
            p=Path(d)/'fixed';p.write_bytes(b'fixed')
            subject.check(p,5,subject.sha(b'fixed'))
            for n,h in ((4,subject.sha(b'fixed')),(5,subject.sha(b'other'))):
                with self.assertRaisesRegex(ValueError,'source drift'):subject.check(p,n,h)

    def test_direct_and_indirect_call_instruction_detection(self):
        self.assertEqual(subject.is_call(bytes.fromhex('e800000000')),'direct')
        for raw in (bytes.fromhex('ff10'),bytes.fromhex('48ff1500000000')):
            self.assertEqual(subject.is_call(raw),'indirect')
        for raw in (bytes.fromhex('c3'),bytes.fromhex('ffe0'),bytes.fromhex('488b00')):
            self.assertFalse(subject.is_call(raw))

    def test_write_boundary_intersections(self):
        protected=((100,116),(244,500))
        for address,size in ((99,2),(115,2),(243,2),(499,2)):
            self.assertTrue(subject.intersects(address,size,protected))
        for address,size in ((116,128),(84,16),(500,1)):
            self.assertFalse(subject.intersects(address,size,protected))


if __name__=='__main__':unittest.main()
