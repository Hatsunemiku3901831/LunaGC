import copy
import struct
import tempfile
import unittest
from pathlib import Path
from tools.migration72 import scene_mplevel_actual_configmeta_fields as s


def source_declarations():
 fields=[{'index':i,'name':'f'+str(i),'type':{'kind':'szarray','attributes':'0x6','elementType':{'kind':'valuetype','typeDefinition':77122}}} for i in range(386045,386227)]
 pair=[{'index':386227,'name':'origin','type':{'kind':'uint64','attributes':'0x6'}},{'index':386228,'name':'bin','type':{'kind':'uint64','attributes':'0x6'}}]
 records={77121:{'parent':{'typeDefinition':78929},'fieldCount':182,'fieldStart':386045,'fields':fields},77122:{'fieldCount':2,'fields':pair},78929:{'fieldCount':0,'fields':[]}}
 source={'selectedDeclarations':{'gadgetPathHashArray':fields[7], 'pathHash':{'fields':pair}}}
 return records,source


class FieldTests(unittest.TestCase):
 def test_array_stride_unsigned_and_eof(self):
  fields=[{'index':1,'name':'one'},{'index':2,'name':'two'}]
  raw=bytes(44)+struct.pack('<iQQi',1,0xffffffffffffffff,0xce23884493554189,0)
  rows=s.walk(raw,fields);self.assertEqual(rows[0]['pairs'],[['0xffffffffffffffff','0xce23884493554189']]);self.assertEqual(rows[1]['count'],0)
  for wrong in (raw[:-1],raw+b'\0',bytes(44)+struct.pack('<i',-1),bytes(44)+struct.pack('<i',2)+bytes(16)):
   with self.assertRaises(ValueError):s.walk(wrong,fields)

 def test_declaration_order_attributes_index(self):
  records,src=source_declarations();s.declarations(records,src)
  for change in ('order','attributes','index','pair','parent'):
   r=copy.deepcopy(records);a=copy.deepcopy(src)
   if change=='order':r[77121]['fields'][0],r[77121]['fields'][1]=r[77121]['fields'][1],r[77121]['fields'][0]
   if change=='attributes':r[77121]['fields'][0]['type']['attributes']='0x16'
   if change=='index':a['selectedDeclarations']['gadgetPathHashArray']['index']=386053
   if change=='pair':r[77122]['fields'][1]['type']['kind']='uint32'
   if change=='parent':r[78929]['fieldCount']=1
   with self.assertRaises(ValueError):s.declarations(r,a)

 def test_index_ambiguity_variant_and_origin(self):
  key=0xce23884493554189
  row={'index':'31049741','offset':206928,'end':206942,'recordSHA256':'551d1828e00afdc6d998b0aa48349bc9c378e3257deaff7c3df18292648bb109','variants':[(0,1)]}
  loc={'31049740':[],'31049741':[(24230448,1,0,0,0,7)]}
  self.assertEqual(s.choose([row],loc,key)['assetEntryHex'],'44935541')
  for matches,locations,k in [([row,row],loc,key),([dict(row,variants=[(0,1),(1,1)])],loc,key),([row],{'31049740':[(24230448,2,0,0,0,7)],'31049741':loc['31049741']},key),([row],loc,0x644cf04bac35cb7f)]:
   with self.assertRaises(ValueError):s.choose(matches,locations,k)

 def test_pin(self):
  with tempfile.TemporaryDirectory() as d:
   p=Path(d)/'x';p.write_bytes(b'fixed');s.check(p,5,s.sha(b'fixed'))
   with self.assertRaises(ValueError):s.check(p,5,s.sha(b'wrong'))

if __name__=='__main__':unittest.main()
