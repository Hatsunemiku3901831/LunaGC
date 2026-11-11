import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
from tools.migration72 import scene_mplevel_actual_payload_host as s


class HostTests(unittest.TestCase):
 def test_qword_overflow_and_all_short_tails(self):
  key=['bf','bf57','bf5754','bf57541a','bf57541ae2','bf57541ae243','bf57541ae2438f','bf57541ae2438f75']
  child=['8b','8ba8','8ba8c5','8ba8c53d','8ba8c53d5a','8ba8c53d5a66','8ba8c53d5a66c2','8ba8c53d5a66c222']
  for constant,expected in ((s.KEY_ADD,key),(s.CHILD_ADD,child)):
   for width,hex_expected in enumerate(expected,1):
    raw=b'\xff'*width;decoded=s.words(raw,constant)
    self.assertEqual(decoded.hex(),hex_expected);self.assertEqual(s.words(decoded,constant,True),raw)

 def test_order_offsets_zero_lengths_duplicates_and_non_ascii(self):
  raw=bytes.fromhex('6a34c40b07c157fff945b59c2a312431ffffc157fffb45b59c')
  parsed=s.parse(raw);self.assertEqual(parsed['endOffset'],25);self.assertTrue(parsed['exactEOF'])
  first,second=parsed['groups'];self.assertEqual((first['start'],first['key']['bytesOffset'],first['childCountOffset'],first['end']),(5,7,8,18))
  self.assertEqual([(x['lengthOffset'],x['bytesOffset'],x['byteLength'],x['end']) for x in first['children']],[(12,14,0,14),(14,16,2,18)])
  self.assertEqual((second['start'],second['end'],second['childCount']),(18,25,0))
  self.assertEqual(first['key']['decodedHex'],'bf');self.assertIsNone(first['key']['ascii']);self.assertIsNone(first['children'][1]['ascii'])
  self.assertEqual(s.summarize(parsed)['duplicateKeys'],[{'decodedHex':'bf','count':2}]);self.assertEqual(s.inverse(parsed),raw)
  empty=bytes.fromhex('6a33c40b07c057fb45b59c');one=s.parse(empty)
  self.assertEqual((one['groups'][0]['key']['byteLength'],one['groups'][0]['childCount'],one['endOffset']),(0,0,11));self.assertEqual(s.inverse(one),empty)

 def test_branch_stops_without_empty_dictionary(self):
  for raw,cursor,reason in ((b'\x00',1,'prefix-clear'),(bytes.fromhex('6a32c40b07'),5,'outer-zero')):
   result=s.parse(raw);self.assertEqual((result['status'],result['cursor'],result['reason']),('unresolved-shared-empty-branch',cursor,reason));self.assertNotIn('groups',result)
   with self.assertRaisesRegex(ValueError,'unresolved'):s.inverse(result)

 def test_precise_short_read_and_cursor(self):
  raw=bytes.fromhex('6a34c40b07c157fff945b59c2a312431ffffc157fffb45b59c')
  for sample,expected in ((b'',(0,1,0)),(b'\x6a\x00',(1,4,1)),(raw[:17],(16,2,1)),(raw[:-1],(21,4,3))):
   with self.assertRaises(s.ReadError) as e:s.parse(sample)
   self.assertEqual((e.exception.cursor,e.exception.needed,e.exception.remaining),expected)

 def test_pin_failure_before_parse(self):
  with tempfile.TemporaryDirectory() as d:
   root=Path(d);(root/'x').write_bytes(b'abc');dest=root/'out';dest.mkdir()
   with patch.object(s,'ROOT',root),patch.object(s,'PINS',{'x':(3,s.sha(b'abd'))}),patch.object(s,'parse') as parse:
    with self.assertRaisesRegex(ValueError,'pin mismatch'):s.collect(dest)
    parse.assert_not_called();self.assertTrue((dest/'failure.private.json').exists())

 def test_fixed_actual_sample_and_inverse(self):
  data,_=s.ingress();payload=data[s.TARGET+'/payload.bin'];parsed=s.parse(payload);summary=s.summarize(parsed)
  group=s.actual_gate(payload,parsed,summary,json.loads(data[s.ORACLE]))
  self.assertEqual((group['ordinal'],len(group['children'])),(544,21));self.assertEqual(s.inverse(parsed),payload)

if __name__=='__main__':unittest.main()
