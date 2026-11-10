import json
import struct
import tempfile
import unittest
from pathlib import Path
from tools import client72_d02_proud_native_17208_array_window as tool

class WindowTests(unittest.TestCase):
 def test_source_identity(self):
  identity=tool.source_gate()
  self.assertEqual(identity['method']['index'],130225)
  self.assertEqual(identity['returnType'],'unknown')
  self.assertEqual(identity['callerField']['offset'],0x50)
  self.assertNotEqual(int(identity['rawH'],16)&0x800000,0)
 def test_pdata_exact_containment_and_ambiguity(self):
  rva=tool.ENTRY-0x140000000
  row=struct.pack('<III',rva,rva+100,7)
  self.assertEqual(tool.choose(tool.extent(row,tool.ENTRY,0x140000000,0x1000))['size'],100)
  for raw in (b'',row+row,struct.pack('<III',rva-1,rva+1,8)+row,struct.pack('<III',rva-1,rva+100,8)):
   with self.subTest(raw=raw), self.assertRaisesRegex(ValueError,'unique'):
    tool.choose(tool.extent(raw,tool.ENTRY,0x140000000,0x1000))
  with self.assertRaisesRegex(ValueError,'unaligned'): tool.extent(b'bad',tool.ENTRY,0x140000000,0)
 def test_budget_and_slot(self):
  tool.bound(8192)
  for n in (0,8193):
   with self.assertRaisesRegex(ValueError,'8192'): tool.bound(n)
  tool.bind_slot(tool.ENTRY.to_bytes(8,'little'))
  for raw in (bytes(8),tool.ENTRY.to_bytes(8,'little')+b'\0'):
   with self.assertRaisesRegex(ValueError,'pointer mismatch'): tool.bind_slot(raw)
 def test_decode_complete_and_mechanical_inventory(self):
  with self.assertRaisesRegex(ValueError,'decode incomplete'): tool.decode(bytes.fromhex('900f'))
  with self.assertRaisesRegex(ValueError,'2048'): tool.decode(b'\x90'*2049)
  result=tool.decode(bytes.fromhex('488b0185c07401c3c3'))
  self.assertEqual((len(result['memoryOperands']),len(result['branches'])),(1,1))
  calls=tool.decode(bytes.fromhex('e800000000ffd0'))['calls']
  self.assertEqual(len(calls),2)
  self.assertIsNone(calls[1]['target'])
 def test_matching_tampered_copies_rejected(self):
  with tempfile.TemporaryDirectory(dir=tool.PRIVATE) as temp:
   root=Path(temp)
   for folder in ('a','b'):
    d=root/folder; d.mkdir()
    for name in tool.FILES:
     raw=(tool.PRIVATE/'fresh1'/name).read_bytes()
     if name=='pre-body.private.json':
      obj=json.loads(raw); obj['aot']['slotHex']='00'; raw=json.dumps(obj).encode()
     (d/name).write_bytes(raw)
   with self.assertRaisesRegex(ValueError,'pre-body provenance'): tool.audit(root/'a',root/'b',root/'out.json')

if __name__=='__main__': unittest.main()
