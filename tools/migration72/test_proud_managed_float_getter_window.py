import io
import struct
import sys
import unittest
from pathlib import Path
from tools.migration72 import proud_managed_float_getter_window as w
sys.path.insert(0,str(w.ROOT/'local/migration72/python'))
import capstone

class DecodeSpy:
    def __init__(self):
        self.cs=capstone.Cs(capstone.CS_ARCH_X86,capstone.CS_MODE_64); self.cs.detail=True; self.count=None; self.yielded=0
    def disasm(self,code,entry,count):
        self.count=count
        for ins in self.cs.disasm(code,entry,count=count):
            self.yielded+=1
            yield ins

class OfflineTests(unittest.TestCase):
    def test_cap_is_passed_to_engine_and_129th_never_decoded(self):
        spy=DecodeSpy(); rows,state=w.decode(b'\x90'*129,spy)
        self.assertEqual((len(rows),state,spy.count,spy.yielded),(128,'instruction-cap',128,128))
        spy=DecodeSpy(); rows,state=w.decode(b'\x90'*128,spy)
        self.assertEqual((len(rows),state,spy.count,spy.yielded),(128,'complete',128,128))
    def test_incomplete_instruction_and_physical_return(self):
        spy=DecodeSpy(); rows,state=w.decode(b'\x0f',spy); self.assertEqual((rows,state),([], 'incomplete-decode'))
        spy=DecodeSpy(); rows,state=w.decode(bytes.fromhex('660f6ec0c3'),spy)
        self.assertEqual(state,'complete'); self.assertEqual([(r['mnemonic'],r['operands']) for r in rows],[('movd','xmm0, eax'),('ret','')])
    def test_unique_pdata_and_overlap_or_extent_stop(self):
        base=0x140000000; rva=w.ENTRY-base; table=struct.pack('<III',rva,rva+166,0x1234)
        d=w.find_pdata(table,base,0x2000); section=lambda a,n,e:{'raw':0x100,'begin':w.ENTRY}
        row=w.admit(d,section,0x1000); self.assertEqual((row['size'],row['recordRVA']),(166,'0x2000'))
        overlap=table+struct.pack('<III',rva+10,rva+20,0x55)
        with self.assertRaisesRegex(ValueError,'ambiguous'): w.admit(w.find_pdata(overlap,base,0x2000),section,0x1000)
        oversized=struct.pack('<III',rva,rva+257,0x1234)
        with self.assertRaisesRegex(ValueError,'over cap'): w.admit(w.find_pdata(oversized,base,0x2000),section,0x1000)
        with self.assertRaisesRegex(ValueError,'outside file'): w.admit(d,section,0x101)
        with self.assertRaisesRegex(ValueError,'ambiguous'): w.admit(w.find_pdata(b'',base,0x2000),section,0x1000)
    def test_filepe_tuple_shape_and_short_read_ledger(self):
        class PE:
            def at(self,*args): return b'abc',17
        report={'reads':[],'aotBytes':0,'bodyRead':0}
        with self.assertRaisesRegex(ValueError,'short body'): w.read_at(PE(),w.ENTRY,5,'body',report,True)
        self.assertEqual((report['bodyRead'],report['reads'][0]['requested'],report['reads'][0]['returned'],report['reads'][0]['hex']),(3,5,3,'616263'))
        class Wrong:
            def at(self,*args): return b'12345678'
        with self.assertRaisesRegex(ValueError,'shape'): w.read_at(Wrong(),w.CELL,8,'aot-cell',report)
    def test_metadata_declaration_and_alias_gate(self):
        method={'index':454810,'name':'GOCKGKFKOID','registeredAddress':hex(w.ENTRY),'flags':'0x886','parameterCount':0,'virtualSlot':65535}
        field={'index':271929,'type':{'kind':'uint32'},'offset':16,'offsetKind':'instance'}
        owner={'index':54724,'name':'EBGBPCMHIGK','methods':[method],'fields':[field]}
        class Records:
            def __getitem__(self,index): return owner
            def __iter__(self): return iter([owner])
        self.assertEqual(w.verify_metadata(Records())['logicalReturn'],'not-recovered')
        field['type']['kind']='float32'
        with self.assertRaisesRegex(ValueError,'stored field'): w.verify_metadata(Records())
    def test_relative_and_rip_are_indexed_without_following(self):
        spy=DecodeSpy(); code=bytes.fromhex('e800000000488b0501000000c3'); rows,state=w.decode(code,spy)
        self.assertEqual(state,'complete'); self.assertEqual(rows[0]['directTarget'],hex(w.ENTRY+5)); self.assertEqual(rows[1]['ripReferences'][0]['address'],hex(w.ENTRY+13))
    def test_listing_does_not_define_boundary(self):
        rows=[{'address':hex(w.ENTRY),'mnemonic':'ret','operands':''}]
        r=w.listing_compare(rows,f'{w.ENTRY:x}: ret\n{w.ENTRY+1:x}: nop\n'); self.assertTrue(r['listingIsNotBoundary']); self.assertEqual(r['listingOnly'],[hex(w.ENTRY+1)])
if __name__=='__main__': unittest.main()
