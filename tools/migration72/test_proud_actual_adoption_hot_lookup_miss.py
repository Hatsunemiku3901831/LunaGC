"""Independently check retained original Store and three lookup reports."""
import hashlib
import json
from pathlib import Path
import struct
import unittest
from unittest.mock import patch
from tools.migration72 import proud_actual_adoption_hot_lookup_miss as subject

DIR=Path('local/migration72/d02-resume-20261111b/proud-hot-lookup-miss')
SENTINEL='44332211a5a5a5a588776655'
CLEARED='443322110000000088776655'
QUERIES=((221,2,87739,1,109),(221,3,87738,0,87),(221,0x80000002,87739,1,104))

class LookupMissReview(unittest.TestCase):
 @classmethod
 def setUpClass(cls):
  cls.a=(DIR/'fresh5.private.json').read_bytes(); cls.b=(DIR/'fresh6.private.json').read_bytes()
  cls.report=json.loads(cls.a); cls.store=cls.report['results'][0]; cls.lookups=cls.report['results'][1:]

 def test_two_new_processes_are_identical(self):
  self.assertEqual(self.a,self.b)
  self.assertEqual(hashlib.sha256(self.a).hexdigest(),'d287c635f023695aaf2ce472487f415bcbdd53fa2f5e7ddb864ad372c5d512c9')
  self.assertTrue(self.report['originalConditionalHotLookupMissExecuted'])
  self.assertEqual(self.report['sourceCount'],71)

 def test_original_store_yields_full_map_and_seven_writes(self):
  s=self.store; after=s['after']; entry=struct.pack('<IIQI',87736,0xffffffff,0x1000000dd,22101)
  self.assertEqual(bytes.fromhex(after['entries'])[0x20:],entry+bytes(40))
  self.assertEqual(bytes.fromhex(after['buckets'])[0x20:],struct.pack('<III',0,1,0))
  d=bytes.fromhex(after['dictionary']); self.assertEqual(struct.unpack_from('<IIII',d,0x38),(1,0,0xffffffff,1))
  self.assertEqual(len(s['trace']),122); self.assertEqual(s['trace'][-1],'0x154d400bb'); self.assertEqual(s['calls'],['0x154d400ab'])
  writes=[(a['pc'],int(a['address'],16),a['size'],int(a['value'],16)&((1<<(8*a['size']))-1)) for a in s['accesses'] if a['kind']=='write' and int(a['address'],16)<0x6d0100000]
  self.assertEqual(writes,[('0x1593e7c16',0x6d0001044,4,1),('0x1593e80bc',0x6d0001038,4,1),('0x1593e80e4',0x6d0003020,4,87736),('0x1593e80eb',0x6d0003024,4,0xffffffff),('0x1593e80f3',0x6d0003028,8,0x1000000dd),('0x1593e80fb',0x6d0003030,4,22101),('0x1593e8100',0x6d0002024,4,1)])

 def test_three_separate_misses_and_full_keys(self):
  self.assertEqual(len(self.lookups),3)
  for c,(group,level,expected_hash,bucket,count) in zip(self.lookups,QUERIES):
   self.assertEqual((((group*397)&0xffffffff)^level)&0x7fffffff,expected_hash)
   self.assertEqual(expected_hash%3,bucket)
   self.assertEqual(c['before']['out'],SENTINEL); self.assertEqual(c['after']['out'],CLEARED)
   self.assertEqual(c['indexEntryPairs'],[hex((level<<32)|group)])
   self.assertEqual(c['indexHelperEAX'],['0xffffffff'])
   self.assertEqual(int(c['registers']['rax'],16)&0xff,0)
   self.assertEqual(len(c['trace']),count); self.assertEqual(c['trace'][-1],'0x154d401ac')
   self.assertEqual(c['calls'],['0x154d400e4','0x154d4016f'])
   self.assertEqual(c['stops'],['original-return']); self.assertEqual(c['modelsInvoked'],[])
   self.assertTrue(all(c['checks'].values()))
   for part in ('dictionary','buckets','entries'):
    self.assertEqual(c['before'][part],self.store['after'][part]); self.assertEqual(c['after'][part],self.store['after'][part])

 def test_prepare_and_output_are_original_writes(self):
  for index,c in enumerate(self.lookups):
   writes=[(a['pc'],int(a['address'],16),a['size'],int(a['value'],16)) for a in c['accesses'] if a['kind']=='write' and int(a['address'],16)<0x6d0100000]
   expected=([('0x154d4030e',0x6d0000028,1,1)] if index==0 else [])+[('0x154d40195',0x6d0004004,4,0)]
   self.assertEqual(writes,expected)
   self.assertEqual(bytes.fromhex(c['before']['wrapper'])[0x28],0 if index==0 else 1)
   self.assertEqual(bytes.fromhex(c['after']['wrapper'])[0x28],1)
   self.assertEqual('0x154d4030e' in c['trace'],index==0)

 def test_drift_stops_before_cpu(self):
  with patch.object(subject,'sha',return_value='drift'):
   with self.assertRaisesRegex(ValueError,'contract drift'): subject.inputs()

 def test_no_parent_readiness_claims(self):
  for key in ('actualPairMapHit','actualProudMapProducerExecuted','actualProudRowConstructed','naturalInterfaceDispatch','promoteThresholdObserved','resourceReady','completeAvatar','d02Complete'):
   self.assertFalse(self.report[key],key)

if __name__=='__main__': unittest.main()
