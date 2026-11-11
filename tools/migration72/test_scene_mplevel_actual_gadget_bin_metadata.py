import copy
import json
import struct
import tempfile
import unittest
from pathlib import Path
from tools.migration72 import scene_mplevel_actual_gadget_bin_metadata as s


def fake_directory(platform=-2,engine='2017.4.30f1\n2'):
 r=bytearray(20);r+=engine.encode()+b'\0'+struct.pack('<iB',platform,0)+struct.pack('<i',2)
 for k in (1210,1208):r+=struct.pack('<iBh',k,0,-1)+bytes.fromhex(s.SUPPORTED[k])
 r+=struct.pack('<i',2)
 for pid,start,length,typ in ((-1,0,32,0),(0x1ffffffff,128,24,1)):
  r+=bytes((-len(r))&3)+struct.pack('<qIIi',pid,start,length,typ)
 r+=struct.pack('<ii',1,0)+bytes((-len(r)-8)&3)+struct.pack('<q',-1)
 r+=struct.pack('<i',0)+b'\0';meta=len(r)-20
 r+=bytes(512-len(r));struct.pack_into('>4I',r,0,meta,1024,17,256)
 info={'blockBytes':128,'uncompressedBytes':1024,'dataOffset':28,'compressedEnds':[10,20,30,40,50,60,70,80]}
 return bytes(r),info


class MetadataTests(unittest.TestCase):
 def test_26_ranges_and_indices(self):
  x=json.loads((s.ROOT/s.RANGES).read_bytes())
  ends=[x['blocks'][0]['compressedBeginRelativeToData']]+[r['compressedEndRelativeToData'] for r in x['blocks']]
  info={'compressedEnds':ends,'dataOffset':13737};s.ranges_gate(x['blocks'],info)
  for change in ('zero','27','raw','order'):
   wrong=copy.deepcopy(x['blocks'])
   if change=='zero':wrong[0]['index']=0
   if change=='27':wrong[-1]['index']=27
   if change=='raw':wrong[0]['rawEndExclusive']+=1
   if change=='order':wrong[0],wrong[1]=wrong[1],wrong[0]
   with self.assertRaises(ValueError):s.ranges_gate(wrong,info)

 def test_exact_engine_platform_and_64bit(self):
  raw,info=fake_directory();d=s.directory(raw,1024,info)
  self.assertEqual((d['engine'],d['platform'],d['metadataEnd']),('2017.4.30f1\n2',-2,20+d['metadataBytes']))
  self.assertEqual([o['pathID'] for o in d['objects']],[-1,0x1ffffffff]);self.assertEqual(d['scripts'][0]['pathID'],-1)
  for platform,engine in ((19,'2017.4.30f1\n2'),(-2,'2017.4.30f1'),(-2,'2017.4.30f1\n3')):
   wrong,_=fake_directory(platform,engine)
   with self.assertRaisesRegex(ValueError,'engine/platform/tree'):s.directory(wrong,1024,info)

 def test_metadata_crossing_type_extent_duplicates_and_eof(self):
  raw,info=fake_directory();d=s.directory(raw,1024,info)
  with self.assertRaisesRegex(ValueError,'metadata crosses'):s.directory(raw[:40],1024,info)
  wrong=bytearray(raw);wrong[d['types'][0]['offset']+7]^=1
  with self.assertRaisesRegex(ValueError,'unsupported type'):s.directory(wrong,1024,info)
  first,second=d['objects']
  for off,fmt,value in ((second['recordOffset'],'<q',first['pathID']),(second['recordOffset']+8,'<I',0),(second['recordOffset']+8,'<I',9999),(second['recordOffset']+16,'<i',3)):
   wrong=bytearray(raw);struct.pack_into(fmt,wrong,off,value)
   with self.assertRaises(ValueError):s.directory(wrong,1024,info)
  wrong=bytearray(raw);struct.pack_into('>I',wrong,0,d['metadataBytes']+1)
  with self.assertRaisesRegex(ValueError,'unconsumed metadata'):s.directory(wrong,1024,info)

 def test_index_range_boundaries(self):
  _,info=fake_directory()
  self.assertEqual([x['index'] for x in s.plan(127,128,info,128)],[0])
  p=s.plan(127,129,info,128);self.assertEqual([x['index'] for x in p],[0,1]);self.assertEqual([x['alreadyAvailable'] for x in p],[True,False])
  for a,b in ((0,0),(-1,1),(0,1025)):
   with self.assertRaises(ValueError):s.plan(a,b,info,128)

 def test_pin_drift(self):
  with tempfile.TemporaryDirectory() as d:
   p=Path(d)/'x';p.write_bytes(b'abc');s.verify(p,3,s.sha(b'abc'))
   with self.assertRaisesRegex(ValueError,'pin mismatch'):s.verify(p,3,s.sha(b'abd'))

if __name__=='__main__':unittest.main()
