import copy
import json
import struct
import tempfile
import unittest
from pathlib import Path
from tools.migration72 import scene_mplevel_actual_gadget_bin_index as s


def string(v):
 b=v.encode();out=struct.pack('<I',len(b))+b
 return out+bytes((-len(out))&3)

def index(records):
 out=string('fixture')+struct.pack('<I',len(records))
 for name,fid,pid,size in records:out+=string(name)+struct.pack('<iQQ',fid,pid,size)
 return out


class IndexTests(unittest.TestCase):
 def test_large_count_not_old_sample_limit(self):
  raw=string('')+struct.pack('<I',176150)+(struct.pack('<I',0)+struct.pack('<iQQ',0,1,2))*176150
  result=s.parse_index(raw);self.assertEqual(result['count'],176150);self.assertEqual(result['end'],len(raw))

 def test_extent_count_string_and_exact_eof(self):
  raw=index([('44935541',0,1,2)]);self.assertEqual(s.parse_index(raw)['count'],1)
  for wrong in (raw[:-1],raw+b'\0',string('')+struct.pack('<I',2)+(struct.pack('<I',0)+bytes(20)),string('')+struct.pack('<I',1)+struct.pack('<I',1000)+bytes(20)):
   with self.assertRaises(ValueError):s.parse_index(wrong)

 def test_exact_unique_local_full_64bit(self):
  obj={'pathID':-1,'classID':1208,'typeHash':s.EXPECTED_TYPE,'start':0,'endExclusive':9,'bytes':9}
  collision={'pathID':0xffffffff,'classID':1208,'typeHash':s.EXPECTED_TYPE,'start':20,'endExclusive':29,'bytes':9}
  parsed=s.parse_index(index([('44935541',0,0xffffffffffffffff,3)]));entry,target=s.match_target(parsed,{'objects':[collision,obj]})
  self.assertEqual(target['pathID'],-1);self.assertEqual(entry['pathID'],0xffffffffffffffff)
  for records in ([('other',0,1,3)],[('44935541',0,1,3),('44935541',0,1,3)],[('44935541',1,0xffffffffffffffff,3)]):
   with self.assertRaises(ValueError):s.match_target(s.parse_index(index(records)),{'objects':[obj]})
  for wrong in (dict(obj,classID=1210),dict(obj,typeHash='0'*32)):
   with self.assertRaisesRegex(ValueError,'class/type'):s.match_target(parsed,{'objects':[wrong]})
  with self.assertRaisesRegex(ValueError,'missing or ambiguous'):s.match_target(parsed,{'objects':[collision]})

 def test_only_actual_window_and_filesize(self):
  obj={'start':100,'endExclusive':109,'bytes':9};entry={'fileSize':3};window=struct.pack('<I',3)+b'abc'+b'zz'
  info,raw,payload=s.target_bytes(entry,obj,window,100);self.assertTrue(info['objectFullyAvailable']);self.assertEqual(payload,b'abc');self.assertEqual(raw,window);self.assertEqual(info['trailingBytes'],2)
  info,raw,payload=s.target_bytes(entry,obj,window[:4],100);self.assertTrue(info['headerAvailable']);self.assertFalse(info['objectFullyAvailable']);self.assertIsNone(raw)
  info,raw,payload=s.target_bytes(entry,obj,window,200);self.assertFalse(info['headerAvailable'])
  for bad in ({'fileSize':4},{'fileSize':99}):
   with self.assertRaisesRegex(ValueError,'count/fileSize'):s.target_bytes(bad,obj,window,100)

 def test_range_checks_and_availability(self):
  req=json.loads((s.ROOT/s.REQUEST).read_bytes());ends=[0]*87
  for row in req['blocks']:
   i=row['index'];ends[i-1]=row['rawBegin']-13737;ends[i]=row['rawEndExclusive']-13737
  info={'compressedEnds':ends,'dataOffset':13737};s.ranges_gate(req['blocks'],info)
  for change in ('index','range','order'):
   rows=copy.deepcopy(req['blocks'])
   if change=='index':rows[0]['index']=42
   if change=='range':rows[-1]['rawEndExclusive']+=1
   if change=='order':rows[0],rows[1]=rows[1],rows[0]
   with self.assertRaises(ValueError):s.ranges_gate(rows,info)
  info={'blockBytes':128,'uncompressedBytes':128*100,'dataOffset':28,'compressedEnds':[10*(i+1) for i in range(100)]}
  self.assertEqual([r['index'] for r in s.target_plan({'start':127,'endExclusive':129},info)],[0,1])
  for i,expected in ((26,'metadata-prefix'),(27,'unavailable'),(43,'index-window'),(86,'index-window'),(87,'unavailable')):
   self.assertEqual(s.target_plan({'start':i*128,'endExclusive':i*128+1},info)[0]['availability'],expected)

 def test_source_drift(self):
  with tempfile.TemporaryDirectory() as d:
   p=Path(d)/'x';p.write_bytes(b'abc');s.verify(p,3,s.sha(b'abc'))
   with self.assertRaisesRegex(ValueError,'pin mismatch'):s.verify(p,3,s.sha(b'abd'))

if __name__=='__main__':unittest.main()
