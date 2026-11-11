import hashlib
import struct
import tempfile
import unittest
from pathlib import Path
from tools.migration72 import scene_mplevel_actual_configmeta_fields_header as h
from tools.client72_resource_blob import parse_header


class HeaderTests(unittest.TestCase):
 def test_stream_identity_and_signature(self):
  raw=bytes.fromhex('426c62038d35000005000000')+bytes(13725)
  source={'bytes':len(raw),'sha256':hashlib.sha256(raw).hexdigest(),'md5':hashlib.md5(raw).hexdigest()}
  with tempfile.TemporaryDirectory() as d:
   p=Path(d)/'fixture';p.write_bytes(raw)
   prefix,proof=h.stream_source(p,source)
   self.assertEqual(prefix,raw);self.assertEqual(proof['sha256'],source['sha256'])
   p.write_bytes(raw[:-1])
   with self.assertRaisesRegex(ValueError,'physical source drift'):h.stream_source(p,source)
   altered=bytes([raw[0]^1])+raw[1:];p.write_bytes(altered)
   changed={'bytes':len(altered),'sha256':hashlib.sha256(altered).hexdigest(),'md5':hashlib.md5(altered).hexdigest()}
   with self.assertRaisesRegex(ValueError,'header signature mismatch'):h.stream_source(p,changed)

 def test_parser_file_tail_and_bounds(self):
  header=bytearray(128);struct.pack_into('<II',header,0,200,8);header[0x14:0x16]=bytes([3,4]);struct.pack_into('<II',header,0x18,1,1)
  for off,target in ((0x20,72),(0x28,76),(0x30,92)):struct.pack_into('<q',header,off,target-off)
  struct.pack_into('<I',header,72,44);struct.pack_into('<IIq',header,76,0,8,16);struct.pack_into('<I',header,92,1);header[100:102]=b'x\0'
  parsed=parse_header(bytes(header),220,128)
  self.assertEqual((parsed['logicalBlobBytes'],parsed['fileTailBytes'],parsed['content'][0]['name']),(200,20,'x'))
  with self.assertRaises(ValueError):parse_header(bytes(header),199,128)
  header[72:76]=bytes(4)
  with self.assertRaises(ValueError):parse_header(bytes(header),220,128)

if __name__=='__main__':unittest.main()
