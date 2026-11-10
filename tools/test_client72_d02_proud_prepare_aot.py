import struct
import unittest
from tools import client72_d02_proud_prepare_aot as tool

class PrepareAotTests(unittest.TestCase):
 def test_target_zero_one_and_multiple_preserved(self):
  rows=[{'pointerIndex':1},{'pointerIndex':2}]
  pointers={'1':{'address':'0x0'},'2':{'address':'0x1'}}
  self.assertEqual(tool.target_matches(rows,pointers),[])
  pointers['1']['address']=hex(tool.ENTRY);self.assertEqual(tool.target_matches(rows,pointers),rows[:1])
  pointers['2']['address']=hex(tool.ENTRY);self.assertEqual(tool.target_matches(rows,pointers),rows)
 def test_fixed_sixteen_and_repeated_pointer_rejected(self):
  rows=[{'pointerIndex':i} for i in range(15)]+[{'pointerIndex':84537}]
  self.assertEqual(len(tool.ensure_selection(rows,rows)),16)
  dup=rows[:-1]+[{'pointerIndex':14}]
  with self.assertRaisesRegex(ValueError,'not unique'):tool.ensure_selection(dup,dup)
  with self.assertRaisesRegex(ValueError,'differ'):tool.ensure_selection(rows,rows[:-1])
 def test_scan_same_pointer_other_instance_retained(self):
  specs=b''.join(struct.pack('<iii',*x) for x in [(1,5071,-1),(2,900,42),(1,5071,3)])
  table=b''.join(struct.pack('<ii6s',*x,b'abcdef') for x in [(4,0),(4,1),(7,2)])
  methods={1:(18192,{'name':'one'}),2:(999,{'name':'two'})}
  selected,shared=tool.extract_rows(table,specs,methods,{4,7})
  self.assertEqual([r['methodInstance'] for r in selected],[-1,3]);self.assertEqual([(r['owner'],r['classInstance']) for r in shared],[(18192,5071),(999,900),(18192,5071)])
 def test_table_reference_out_of_bounds(self):
  table=struct.pack('<ii6s',1,1,b'abcdef');spec=struct.pack('<iii',1,5071,-1)
  with self.assertRaisesRegex(ValueError,'bounds'):tool.extract_rows(table,spec,{1:(18192,{'name':'n'})})
 def test_attach_preserves_declaration_and_parameter_type(self):
  row={'methodDefinition':1,'pointerIndex':5};method={'name':'n','parameterCount':1};sig={'parameters':[{'typeIndex':9}]}
  output=tool.attach_rows([row],{'5':{'qword':'00'}},{1:(18192,method)},{1:sig},{'9':{'kind':'var'}})
  self.assertEqual(output[0]['parameterTypes'],[{'kind':'var'}]);self.assertEqual(output[0]['pointer']['qword'],'00')
if __name__=='__main__':unittest.main()
