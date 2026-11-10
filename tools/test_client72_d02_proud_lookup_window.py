import json
import struct
import tempfile
import unittest
from pathlib import Path
from tools import client72_d02_proud_lookup_window as tool

class LookupWindowTests(unittest.TestCase):
 def test_exact_and_overlapping_pdata(self):
  rva=tool.ENTRY-0x140000000
  single=struct.pack('<III',rva,rva+100,7)
  diag=tool.extent(single,tool.ENTRY,0x140000000,0x1000)
  self.assertEqual(tool.choose(diag)['size'],100)
  duplicate=tool.extent(single+single,tool.ENTRY,0x140000000,0x1000)
  with self.assertRaisesRegex(ValueError,'unique'): tool.choose(duplicate)
  overlap=tool.extent(struct.pack('<III',rva-1,rva+1,8)+single,tool.ENTRY,0x140000000,0x1000)
  with self.assertRaisesRegex(ValueError,'unique'): tool.choose(overlap)
 def test_extent_budget(self):
  tool.bound(4096)
  for n in (0,4097):
   with self.assertRaisesRegex(ValueError,'4096'): tool.bound(n)
 def test_slot_binding(self):
  cell=bytes.fromhex('b00c474201000000'); slot=bytes.fromhex('c0c4ff5701000000');tool.bind_slot(cell,slot)
  with self.assertRaisesRegex(ValueError,'pointer mismatch'): tool.bind_slot(cell,bytes(8))
 def test_decode_incomplete_and_instruction_budget(self):
  with self.assertRaisesRegex(ValueError,'decode incomplete'): tool.decode(bytes.fromhex('900f'))
  with self.assertRaisesRegex(ValueError,'1024'): tool.decode(bytes.fromhex('90')*1025)
 def test_decode_preserves_explicit_memory_and_branch(self):
  result=tool.decode(bytes.fromhex('488b0185c07401c3c3'))
  self.assertEqual(len(result['memoryOperands']),1);self.assertEqual(len(result['branches']),1)
 def test_audit_rejects_prebody_drift(self):
  with tempfile.TemporaryDirectory(dir=tool.PRIVATE) as temp:
   root=Path(temp)
   for folder in ('a','b'):
    d=root/folder;d.mkdir()
    for name in tool.FILES:
     raw=(tool.PRIVATE/'final1'/name).read_bytes()
     if name=='pre-body.private.json':
      obj=json.loads(raw);obj['aot']['slotHex']='00';raw=json.dumps(obj).encode()
     (d/name).write_bytes(raw)
   with self.assertRaisesRegex(ValueError,'pre-body provenance'): tool.audit(root/'a',root/'b',root/'out.json')
if __name__=='__main__': unittest.main()
