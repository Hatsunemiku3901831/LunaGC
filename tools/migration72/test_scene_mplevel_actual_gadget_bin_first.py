import hashlib
import struct
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
from tools.migration72 import scene_mplevel_actual_gadget_bin_first as s


def aligned(v):
 b=v.encode();x=struct.pack('<I',len(b))+b
 return x+bytes((-len(x))&3)


def fixture(names=('44935541',), available=512):
 info={'blockBytes':128,'uncompressedBytes':1024,'dataOffset':28,'compressedEnds':[10,20,30,40,50,60,70,80], 'content':[{'size':1024}]}
 idx=aligned('index')+struct.pack('<I',len(names))
 for name in names:idx+=aligned(name)+struct.pack('<iQQ',0,0xffffffffffffffff,36)
 r=bytearray(20);r+=b'2017.4.30f1\0'+struct.pack('<iB',19,0)+struct.pack('<i',2)
 r+=struct.pack('<iBh',1210,0,-1)+bytes.fromhex('375926f2689845ddfc700437e60d9774')
 r+=struct.pack('<iBh',1208,0,-1)+bytes.fromhex('0451de58691ec5b6e08bba3d9b5b85fa')
 r+=struct.pack('<i',2)
 for pid,start,length,typ in ((7,0,len(idx),0),(-1,644,40,1)):
  r+=bytes((-len(r))&3)+struct.pack('<qIIi',pid,start,length,typ)
 r+=struct.pack('<i',0)+struct.pack('<i',0)+b'\0';meta=len(r)-20
 r+=bytes(max(0,256-len(r)))
 if available>256:r+=idx
 r=r[:available]+bytes(max(0,available-len(r)))
 struct.pack_into('>4I',r,0,meta,1024,17,256)
 return bytes(r),info


class FirstBlockTests(unittest.TestCase):
 def test_only_index_zero_and_short_prefix(self):
  s.index_gate(0)
  for i in (-1,1,3398):
   with self.assertRaises(ValueError):s.index_gate(i)
  with self.assertRaisesRegex(ValueError,'serialized header truncated'):s.prefix_directory(bytes(19),{})

 def test_metadata_incomplete_does_not_parse_unavailable(self):
  info={'content':[{'size':1024}],'uncompressedBytes':1024,'blockBytes':128,'dataOffset':28,'compressedEnds':[10,20,30,40,50,60,70,80]}
  raw=struct.pack('>4I',400,1024,17,500)+bytes(112)
  result=s.prefix_directory(raw,info)
  self.assertEqual((result['status'],result['metadataEnd'],result['requiredPrefixBlocks']),('metadata-prefix-incomplete',420,4))
  self.assertEqual([b['index'] for b in result['nextBlocks']],[1,2,3])
  self.assertNotIn('objects',result)

 def test_range_edges_and_ceil(self):
  _,info=fixture()
  self.assertEqual([x['index'] for x in s.block_plan(127,128,info)],[0])
  self.assertEqual([x['index'] for x in s.block_plan(127,129,info)],[0,1])
  self.assertEqual([x['index'] for x in s.block_plan(128,256,info)],[1])
  for a,b in ((0,0),(-1,1),(0,1025)):
   with self.assertRaises(ValueError):s.block_plan(a,b,info)

 def test_full64_entry_and_unavailable_target(self):
  raw,info=fixture();report=s.prefix_directory(raw,info)
  self.assertEqual(report['status'],'target-planned');self.assertEqual(report['target']['pathID'],-1)
  self.assertFalse(report['target']['fullyAvailable']);self.assertEqual(report['entry']['pathID'],0xffffffffffffffff)
  self.assertEqual([x['index'] for x in report['targetBlocks']],[7])
  raw,info=fixture(available=256);report=s.prefix_directory(raw,info)
  self.assertEqual(report['status'],'index-object-incomplete');self.assertNotIn('target',report)

 def test_missing_and_duplicate_entry_stop(self):
  for names in (('other',),('44935541','44935541')):
   raw,info=fixture(names);result=s.prefix_directory(raw,info)
   self.assertEqual(result['status'],'entry-missing-or-ambiguous');self.assertNotIn('target',result)

 def test_physical_source_header_and_short_drift(self):
  header=b'h'*13737;raw=header+b'b'*60990
  source={'bytes':len(raw),'sha256':hashlib.sha256(raw).hexdigest(),'md5':hashlib.md5(raw).hexdigest()}
  with tempfile.TemporaryDirectory() as d:
   base=Path(d);h=base/s.HEADER/'header-prefix.bin';h.parent.mkdir(parents=True);h.write_bytes(header)
   f=base/'block';f.write_bytes(raw)
   with patch.object(s,'ROOT',base):
    self.assertEqual(s.source_prefix(f,source),raw)
    h.write_bytes(b'x'+header[1:])
    with self.assertRaisesRegex(ValueError,'header prefix drift'):s.source_prefix(f,source)
    h.write_bytes(header);f.write_bytes(raw[:-1])
    with self.assertRaisesRegex(ValueError,'physical source drift'):s.source_prefix(f,source)

 def test_identity_and_independent_lz4_bounds(self):
  with tempfile.TemporaryDirectory() as d:
   p=Path(d)/'x';p.write_bytes(b'fixed');s.check(p,5,s.sha(b'fixed'))
   with self.assertRaises(ValueError):s.check(p,5,s.sha(b'wrong'))
  self.assertEqual(s.lz4_block(b'\x50hello',5),b'hello')
  for raw,n in ((b'\x50hell',5),(b'\x50hello',4),(b'\x00\x00\x00',4)):
   with self.assertRaises(ValueError):s.lz4_block(raw,n)

if __name__=='__main__':unittest.main()
