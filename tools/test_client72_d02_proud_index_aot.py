import struct
import unittest
from tools.client72_d02_proud_index_aot import pick_candidates, select_rows

class IndexAotTests(unittest.TestCase):
    def records(self):
        records=[{'methods':[]} for _ in range(80064)]
        records[80063]['fields']=[{'index':400190,'rawOffset':'0x4021f58','type':{'attributes':'0x31','typeDefinition':18192,'instanceIndex':5071,'arguments':[{'kind':'uint32'},{'kind':'valuetype','typeDefinition':75077},{'kind':'valuetype','typeDefinition':47647},{'kind':'valuetype','typeDefinition':25030}]}}]
        records[18192]['methods']=[{'index':1},{'index':2}]
        return records
    def signatures(self):
        return {'methods':[{'index':1,'parameters':[{'typeIndex':1},{'typeIndex':2}]},{'index':2,'parameters':[{'typeIndex':1},{'typeIndex':3}]}],
                'typeReferences':{'1':{'kind':'var','genericParameterPosition':0,'byRef':False},'2':{'kind':'var','genericParameterPosition':1,'byRef':True},'3':{'kind':'var','genericParameterPosition':1,'byRef':False}}}
    def test_signature_distinguishes_ref_from_value(self):
        self.assertEqual(pick_candidates(self.records(),self.signatures())[1],[1])
    def test_field_tuple_drift_rejected(self):
        r=self.records(); r[80063]['fields'][0]['type']['instanceIndex']=50463
        with self.assertRaises(ValueError): pick_candidates(r,self.signatures())
    def test_tuple_and_method_inst_retained(self):
        specs=b''.join(struct.pack('<iii',*v) for v in [(1,5071,-1),(1,50463,-1),(2,5071,12)])
        table=b''.join(struct.pack('<ii6s',*v,b'123456') for v in [(2,0),(3,1),(4,2)])
        selected,rows=select_rows(table,specs,{1:(18192,{}),2:(18192,{})},[1],5)
        self.assertEqual(selected,[(0,2,0,1,5071,-1)])
        self.assertEqual(rows[-1],(2,4,2,2,5071,12))
    def test_budget_rejected_before_pointer_read(self):
        specs=struct.pack('<iii',1,5071,-1)
        table=b''.join(struct.pack('<ii6s',i,0,b'123456') for i in range(33))
        with self.assertRaisesRegex(ValueError,'budget'): select_rows(table,specs,{1:(18192,{})},[1],33)
    def test_out_of_range_reference_rejected(self):
        with self.assertRaisesRegex(ValueError,'bounds'): select_rows(struct.pack('<ii6s',1,0,b'123456'),struct.pack('<iii',1,5071,-1),{1:(18192,{})},[1],1)

if __name__=='__main__': unittest.main()
