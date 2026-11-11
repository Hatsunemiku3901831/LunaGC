"""Independent review of the two retained hot-insert execution reports."""
import hashlib
import json
import struct
import unittest
from unittest.mock import patch
from pathlib import Path
from tools.migration72 import proud_actual_adoption_hot_insert as subject

PRIVATE = Path('local/migration72/d02-resume-20261111b/proud-hot-insert')
CASES = [(221,1,22101),(0x80000001,0xffffffff,0xfedcba98),(0xffffffff,0x80000000,0x80000001)]

class HotInsertReview(unittest.TestCase):
 @classmethod
 def setUpClass(cls):
  cls.raw = [(PRIVATE/f'{n}.private.json').read_bytes() for n in ('fresh3','fresh4')]
  cls.report = json.loads(cls.raw[0])

 def test_fresh_results_are_identical(self):
  self.assertEqual(self.raw[0],self.raw[1])
  self.assertEqual(hashlib.sha256(self.raw[0]).hexdigest(),'cb3bb72bafce6dce81f313f98523ac9a022bed1f5813def514cc4f482e1064fb')
  self.assertTrue(self.report['originalHotPairInsertExecuted'])

 def test_independent_contents_and_state(self):
  self.assertEqual(len(self.report['cases']),3)
  for result,(group,level,row) in zip(self.report['cases'],CASES):
   value=(level<<32)|group
   hashed=(((group*397)&0xffffffff)^level)&0x7fffffff
   entry=struct.pack('<IIQI',hashed,0xffffffff,value,row)
   buckets=[0,0,0]; buckets[hashed%3]=1
   self.assertEqual(bytes.fromhex(result['data']['entry60BHex']),entry+bytes(40))
   self.assertEqual(bytes.fromhex(result['data']['buckets12BHex']),struct.pack('<III',*buckets))
   self.assertEqual(result['data']['dictionaryDWORDs'],{'+38':1,'+3c':0,'+40':0xffffffff,'+44':1})
   self.assertTrue(all(result['checks'].values()))
   self.assertEqual(result['stop'],['original-return'])
   self.assertEqual(result['calls'],['0x154d400ab'])
   self.assertEqual(result['modelsInvoked'],[])
   self.assertEqual(len(result['trace']),122)
   self.assertEqual(result['trace'][-1],'0x154d400bb')

 def test_only_declared_data_writes_occurred(self):
  for result,(group,level,row) in zip(self.report['cases'],CASES):
   hashed=(((group*397)&0xffffffff)^level)&0x7fffffff
   writes=[(a['pc'],int(a['address'],16),a['size'],int(a['value'],16) & ((1 << (a['size']*8))-1)) for a in result['accesses'] if a['kind']=='write' and int(a['address'],16)<0x6d0100000]
   self.assertEqual(writes,[('0x1593e7c16',0x6d0001044,4,1),('0x1593e80bc',0x6d0001038,4,1),('0x1593e80e4',0x6d0003020,4,hashed),('0x1593e80eb',0x6d0003024,4,0xffffffff),('0x1593e80f3',0x6d0003028,8,(level<<32)|group),('0x1593e80fb',0x6d0003030,4,row),('0x1593e8100',0x6d0002020+4*(hashed%3),4,1)])

 def test_scope_remains_unapproved(self):
  for name in ('actualProudRowConstructed','actualProudMapProducerExecuted','actualPairLookupHit','naturalInterfaceDispatch','promoteThresholdObserved','javaFormulaProven','resourceReady','completeAvatar','d02Complete'):
   self.assertFalse(self.report[name],name)

 def test_input_drift_stops_before_emulator(self):
  with patch.object(subject,'sha',return_value='drift'):
   with self.assertRaisesRegex(ValueError,'source drift'):
    subject.verify_inputs()

if __name__=='__main__': unittest.main()
