import copy
import json
import struct
import tempfile
import unittest
from pathlib import Path
from tools.migration72 import proud_gate_comparison_scalar_setter_window as s


class ScalarWindowTests(unittest.TestCase):
 def test_saved_direct_call_and_displacement(self):
  s.check_call(s.CALL_BYTES)
  for raw in (bytes.fromhex('e82fcffe00'),b'\x90'*5,s.CALL_BYTES[:-1]):
   with self.assertRaises(ValueError):s.check_call(raw)

 def test_unique_pdata_exact_containing_and_budget(self):
  base=0x140000000;begin=s.ENTRY-base;end=s.END-base
  record=struct.pack('<III',begin,end,0x1234);d=s.extent(record,base,0x1000);self.assertEqual(s.choose(d)['size'],157)
  for table in (b'',record+record,struct.pack('<III',begin-1,begin+1,0)+record,struct.pack('<III',begin,end+1,0)):
   with self.assertRaises(ValueError):s.choose(s.extent(table,base,0x1000))
  with self.assertRaisesRegex(ValueError,'unaligned'):s.extent(b'x',base,0)

 def test_unique_ordinary_method_and_uint32_signature(self):
  target=json.loads((s.ROOT/s.REQUEST).read_bytes())['target']
  records=[{'index':58209,'methods':[copy.deepcopy(target['declaration'])]}]
  signatures={'methods':[copy.deepcopy(target['signature'])],'typeReferences':{'124':copy.deepcopy(target['resolvedParameters'][0])}}
  s.binding(records,signatures,target)
  for which in ('duplicate','owner','method','signature','parameter'):
   r=copy.deepcopy(records);q=copy.deepcopy(signatures)
   if which=='duplicate':r.append(copy.deepcopy(r[0]))
   if which=='owner':r[0]['index']=1
   if which=='method':r[0]['methods'][0]['registeredAddress']='0x0'
   if which=='signature':q['methods'][0]['declaringType']=1
   if which=='parameter':q['typeReferences']['124']['kind']='int32'
   with self.assertRaises(ValueError):s.binding(r,q,target)

 def test_contiguous_decode_edges_and_instruction_cap(self):
  rows=s.decode(bytes.fromhex('e800000000750190c3'))
  self.assertEqual([x['size'] for x in rows],[5,2,1,1]);self.assertTrue(rows[0]['call']);self.assertEqual(rows[0]['directTarget'],hex(s.ENTRY+5));self.assertTrue(rows[1]['branch'])
  with self.assertRaisesRegex(ValueError,'incomplete decode'):s.decode(b'\x0f')
  with self.assertRaisesRegex(ValueError,'instruction budget'):s.decode(b'\x90'*129)

 def test_file_pin_size_or_hash_drift(self):
  with tempfile.TemporaryDirectory() as d:
   p=Path(d)/'fixed';p.write_bytes(b'fixed');s.check(p,5,s.sha(b'fixed'))
   for n,h in ((4,s.sha(b'fixed')),(5,s.sha(b'other'))):
    with self.assertRaisesRegex(ValueError,'pin mismatch'):s.check(p,n,h)

if __name__=='__main__':unittest.main()
