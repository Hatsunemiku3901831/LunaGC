import struct
import unittest
from tools import client72_d02_proud_base_parser_window as tool

class BaseParserWindowTests(unittest.TestCase):
 def test_source_identity(self):
  identity=tool.source_gate()
  self.assertEqual(identity['method']['index'],493918)
  self.assertEqual(identity['returnType'],'unknown')
 def test_unique_exact_and_containing(self):
  rva=tool.ENTRY-0x140000000
  row=struct.pack('<III',rva,rva+100,7)
  self.assertEqual(tool.choose(tool.extent(row,tool.ENTRY,0x140000000,0x1000))['size'],100)
  for raw in (b'',row+row,struct.pack('<III',rva-1,rva+1,8)+row):
   with self.assertRaisesRegex(ValueError,'unique'): tool.choose(tool.extent(raw,tool.ENTRY,0x140000000,0x1000))
 def test_extent_budget(self):
  tool.bound(4096)
  for n in (0,4097):
   with self.assertRaisesRegex(ValueError,'4096'): tool.bound(n)
 def test_slot_binding(self):
  tool.bind_slot(tool.ENTRY.to_bytes(8,'little'))
  with self.assertRaisesRegex(ValueError,'pointer mismatch'): tool.bind_slot(bytes(8))
 def test_decode_limits(self):
  with self.assertRaisesRegex(ValueError,'decode incomplete'): tool.decode(bytes.fromhex('900f'))
  with self.assertRaisesRegex(ValueError,'1024'): tool.decode(b'\x90'*1025)
 def test_inventory(self):
  result=tool.decode(bytes.fromhex('488b0185c07401c3c3'))
  self.assertEqual(len(result['memoryOperands']),1)
  self.assertEqual(len(result['branches']),1)
  calls=tool.decode(bytes.fromhex('e800000000ffd0'))['calls']
  self.assertEqual(len(calls),2)
  self.assertIsNone(calls[1]['target'])
if __name__=='__main__': unittest.main()
import json
import tempfile
from pathlib import Path
class AuditTamperTests(unittest.TestCase):
 def test_rejects_prebody_drift_even_when_copies_match(self):
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
