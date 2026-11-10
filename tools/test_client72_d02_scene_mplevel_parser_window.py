import struct
import unittest
from unittest.mock import patch
from tools import client72_d02_scene_mplevel_parser_window as tool

class WindowTests(unittest.TestCase):
 def test_identity(self):
  result=tool.source_gate()
  self.assertEqual((result['method']['index'],result['readerType']['typeDefinition'],result['returnType']),(587454,42005,'unknown'))
  self.assertFalse(result['oldCallerOriginalBytesAvailable'])
 def test_caller_setup_and_target(self):
  data=bytearray(817)
  for pc,raw in ((0x14aa99c2b,'4889f1'),(0x14aa99c2e,'4889c2'),(0x14aa99c31,'e87a30c100')):
   b=bytes.fromhex(raw); data[pc-tool.CALLER:pc-tool.CALLER+len(b)]=b
  with patch.object(tool,'CALLER_SHA',tool.digest(data)):
   self.assertEqual(tool.check_caller(data)['e8Evidence'],'fresh caller bytes')
   data[0x14aa99c31-tool.CALLER+1] ^= 1
   with patch.object(tool,'CALLER_SHA',tool.digest(data)), self.assertRaisesRegex(ValueError,'setup/E8'):
    tool.check_caller(data)
 def test_extent_uniqueness_and_bound(self):
  base=0x140000000; rva=tool.ENTRY-base; row=struct.pack('<III',rva,rva+50,4)
  self.assertEqual(tool.choose(tool.extent(row,tool.ENTRY,base,0))['size'],50)
  for raw in (b'',row+row,struct.pack('<III',rva-1,rva+1,5)+row,struct.pack('<III',rva-1,rva+50,5)):
   with self.subTest(raw=raw), self.assertRaisesRegex(ValueError,'unique'): tool.choose(tool.extent(raw,tool.ENTRY,base,0))
  with self.assertRaisesRegex(ValueError,'unaligned'): tool.extent(b'bad',tool.ENTRY,base,0)
  tool.bound(2048)
  for size in (0,2049):
   with self.assertRaisesRegex(ValueError,'2048'): tool.bound(size)
 def test_slot_and_decode(self):
  tool.bind_slot(tool.ENTRY.to_bytes(8,'little'))
  for raw in (bytes(8),tool.ENTRY.to_bytes(8,'little')+b'\0'):
   with self.assertRaisesRegex(ValueError,'pointer mismatch'): tool.bind_slot(raw)
  with self.assertRaisesRegex(ValueError,'decode incomplete'): tool.decode(bytes.fromhex('900f'))
  with self.assertRaisesRegex(ValueError,'512'): tool.decode(b'\x90'*513)
  result=tool.decode(bytes.fromhex('488b0185c07401c3c3'))
  self.assertEqual((len(result['memoryOperands']),len(result['branches'])),(1,1))
  self.assertIsNone(tool.decode(bytes.fromhex('ffd0'))['calls'][0]['target'])

if __name__=='__main__': unittest.main()
