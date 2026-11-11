import copy
import hashlib
import json
import struct
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
from tools.migration72 import scene_mplevel_actual_gadget_bin_target as s


class TargetTests(unittest.TestCase):
 def test_seven_ranges_and_bounds(self):
  req=json.loads((s.ROOT/s.REQUEST).read_bytes());ends=[0]*772
  for row in req['blocks']:
   i=row['index'];ends[i-1]=row['rawBegin']-13737;ends[i]=row['rawEndExclusive']-13737
  info={'compressedEnds':ends,'dataOffset':13737};s.ranges_gate(req['blocks'],info)
  for change in ('before','after','range','order'):
   rows=copy.deepcopy(req['blocks'])
   if change=='before':rows[0]['index']=764
   if change=='after':rows[-1]['index']=772
   if change=='range':rows[0]['rawEndExclusive']+=1
   if change=='order':rows[0],rows[1]=rows[1],rows[0]
   with self.assertRaises(ValueError):s.ranges_gate(rows,info)

 def test_extract_actual_count_and_nonzero_tail(self):
  raw=struct.pack('<I',5)+b'hello'+b'\x34\x12\x56';window=b'prefix'+raw+b'suffix';obj={'start':106,'endExclusive':106+len(raw),'bytes':len(raw)}
  actual,payload,tail,report=s.extract(window,100,obj,{'fileSize':5})
  self.assertEqual(actual,raw);self.assertEqual(payload,b'hello');self.assertEqual(tail,b'\x34\x12\x56')
  self.assertEqual((report['count'],report['countRawHex'],report['tailHex']),(5,'05000000','341256'))

 def test_window_and_count_rejections(self):
  window=struct.pack('<I',5)+b'hello'+b'xyz';obj={'start':100,'endExclusive':112,'bytes':12}
  for wrong in (dict(obj,start=99,bytes=13),dict(obj,endExclusive=113,bytes=13),dict(obj,bytes=11)):
   with self.assertRaisesRegex(ValueError,'object not fully'):s.extract(window,100,wrong,{'fileSize':5})
  with self.assertRaisesRegex(ValueError,'array count truncated'):s.extract(b'abc',100,{'start':100,'endExclusive':103,'bytes':3},{'fileSize':0})
  with self.assertRaisesRegex(ValueError,'count/fileSize'):s.extract(struct.pack('<I',9)+b'ab',100,{'start':100,'endExclusive':106,'bytes':6},{'fileSize':9})
  with self.assertRaisesRegex(ValueError,'count/fileSize'):s.extract(window,100,obj,{'fileSize':4})

 def test_source_hash_and_header_drift(self):
  raw=b'h'*13737+b'x'*(26119089-13737)
  source={'bytes':len(raw),'sha256':hashlib.sha256(raw).hexdigest(),'md5':hashlib.md5(raw).hexdigest()}
  with tempfile.TemporaryDirectory() as d:
   base=Path(d);header=base/s.HDR/'header-prefix.bin';header.parent.mkdir(parents=True);header.write_bytes(raw[:13737]);p=base/'fixture';p.write_bytes(raw)
   with patch.object(s,'ROOT',base):
    self.assertEqual(len(s.physical_prefix(p,source)),26119089)
    header.write_bytes(b'z'+raw[1:13737])
    with self.assertRaisesRegex(ValueError,'header prefix'):s.physical_prefix(p,source)
    header.write_bytes(raw[:13737]);p.write_bytes(raw[:-1])
    with self.assertRaisesRegex(ValueError,'physical source drift'):s.physical_prefix(p,source)

if __name__=='__main__':unittest.main()
