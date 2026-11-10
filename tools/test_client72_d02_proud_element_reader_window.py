import json
import struct
import tempfile
import unittest
from pathlib import Path
from tools import client72_d02_proud_element_reader_window as tool

class WindowTests(unittest.TestCase):
 def test_source_identity(self):
  identity=tool.source_gate()
  self.assertEqual(identity['method']['index'],439383)
  self.assertEqual(identity['returnType'],'unknown')
 def test_unique_exact_containing_and_boundary(self):
  rva=tool.ENTRY-0x140000000
  row=struct.pack('<III',rva,rva+285,7)
  selected=tool.choose(tool.extent(row,tool.ENTRY,0x140000000,0x1000))
  tool.bound(selected)
  for raw in (b'',row+row,struct.pack('<III',rva-1,rva+1,8)+row):
   with self.assertRaisesRegex(ValueError,'unique'): tool.choose(tool.extent(raw,tool.ENTRY,0x140000000,0x1000))
  selected['endExclusive']=hex(tool.END+1)
  with self.assertRaisesRegex(ValueError,'boundary mismatch'): tool.bound(selected)
 def test_aot_and_body_identity(self):
  tool.bind_slot(tool.ENTRY.to_bytes(8,'little'))
  with self.assertRaisesRegex(ValueError,'pointer mismatch'): tool.bind_slot(bytes(8))
  with self.assertRaisesRegex(ValueError,'body identity'): tool.bind_body(bytes(285))
 def test_decode_continuity_limit_and_inventory(self):
  with self.assertRaisesRegex(ValueError,'decode incomplete'): tool.decode(bytes.fromhex('900f'))
  with self.assertRaisesRegex(ValueError,'128'): tool.decode(b'\x90'*129)
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
