"""Fixed caller, source and truthful pre-body/post-read gate tests."""
import copy
import io
import json
import struct
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import proud_querywrapper_borrow_window as mod


class BorrowWindowTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.raw=(mod.GETTER/'code.bin').read_bytes(); cls.rows=json.loads((mod.GETTER/'instructions.private.json').read_bytes())

    def test_saved_caller_and_logical_physical_distinction(self):
        mod.validate_caller(self.raw,self.rows)
        setup=self.raw[0x66:0x73]; self.assertEqual(setup,bytes.fromhex('488d4c24204889fae8fd9925fe'))
        self.assertEqual(0x148ba97ee+5+struct.unpack('<i',setup[-4:])[0],0x146e031f0)
        self.assertEqual([x['asm'] for x in self.rows if 0x148ba97e6<=int(x['pc'],16)<0x148ba97f3],['lea rcx, [rsp + 0x20]','mov rdx, rdi','call 0x146e031f0'])
        bad=bytearray(self.raw); bad[0x6f]^=1
        with self.assertRaises(ValueError): mod.validate_caller(bytes(bad),self.rows)
        rows=copy.deepcopy(self.rows); next(x for x in rows if x['pc']=='0x148ba97eb')['asm']='mov rcx, rdi'
        with self.assertRaises(ValueError): mod.validate_caller(self.raw,rows)

    def test_pdata_unique_overlap_boundary_and_file(self):
        base=0x140000000; rel=mod.ENTRY-base; raw=struct.pack('<III',rel,rel+100,0x1000)
        section=lambda va,size,executable:{'raw':0,'begin':va}
        self.assertEqual(mod.admit(mod.find_pdata(raw,base,0x2000),section,100)['size'],100)
        for table in (b'',raw*2,raw+struct.pack('<III',rel-1,rel+1,1),raw+struct.pack('<III',rel+50,rel+120,2),struct.pack('<III',rel,rel,1)):
            with self.subTest(raw=table.hex()),self.assertRaises(ValueError): mod.admit(mod.find_pdata(table,base,0x2000),section,1000)
        valid=mod.find_pdata(raw,base,0x2000)
        with self.assertRaises(ValueError): mod.admit(valid,lambda *args:(_ for _ in ()).throw(ValueError('nonexec')),1000)
        with self.assertRaises(ValueError): mod.admit(valid,section,99)

    def test_filepe_at_returns_bytes_and_offset(self):
        pe=mod.FilePE.__new__(mod.FilePE); pe.f=io.BytesIO(b'0123456789'); pe.sections=[{'begin':0x1000,'end':0x1008,'raw':1,'flags':0x20000000}]
        self.assertEqual(pe.at(0x1002,3,True),(b'345',3))
        with self.assertRaises(ValueError): pe.at(0x1007,2,True)

    def test_v2_exact_pdata_requires_all_frozen_fields(self):
        row={'begin':'0x146e031f0','endExclusive':'0x146e03410','size':544,'recordHex':'f031e0061034e006fcbbcc03','recordRVA':'0x1a6ce888','unwindRVA':'0x3ccbbfc'}
        self.assertTrue(mod.v2_exact(row))
        for key,value in [('size',545),('endExclusive','0x146e03411'),('recordHex','00'*12),('recordRVA','0x1a6ce894'),('unwindRVA','0x0')]:
            changed=dict(row); changed[key]=value
            with self.subTest(field=key): self.assertFalse(mod.v2_exact(changed))

    def test_decode_and_refused_edge(self):
        rows,state=mod.decode(bytes.fromhex('90c3')); self.assertEqual(state,'complete'); self.assertEqual(b''.join(bytes.fromhex(x['hex']) for x in rows),bytes.fromhex('90c3'))
        _,state=mod.decode(b'\x0f'); self.assertEqual(state,'incomplete-decode')
        rows,state=mod.decode(b'\x90'*257); self.assertEqual((len(rows),state),(257,'instruction-cap'))
        edge={'address':'0x1','call':True,'jump':False,'directTarget':'0x1405336c0','ripReferences':[]}; self.assertTrue(mod.source_index([edge])['dependentSemanticsFrozen'])

    def run_gate(self,mode):
        mod.OUT.mkdir(parents=True,exist_ok=True)
        with tempfile.TemporaryDirectory(dir=mod.OUT) as tmp:
            root=Path(tmp); (root/'YuanShen.exe').write_bytes(b''); dest=root/'out'; dest.mkdir(); pointer=0x1428a54d0; table=bytes(14344080)
            n=545 if mode=='bytecap' else 544; rel=mod.ENTRY-0x140000000; record=struct.pack('<III',rel,rel+n,0x3ccbbfc)
            row={'begin':hex(mod.ENTRY),'endExclusive':hex(mod.ENTRY+n),'size':n,'unwindRVA':'0x3ccbbfc','recordRVA':'0x1a6ce894' if mode=='rvadrift' else '0x1a6ce888','recordHex':record.hex()}; diag={'exact':[row],'containing':[row],'overlapping':[row]}
            class PE:
                base=0x140000000; pdata_rva=0x1a4e6a40; pdata_size=14344080; body_reads=0; meta={'readCalls':2,'bytesRead':4616,'distinctBytes':4096}
                def __init__(self,f): pass
                def section(self,va,size,executable): return {'raw':0,'begin':va}
                def at(self,va,size,executable=False):
                    if va==0x1422f9950: return pointer.to_bytes(8,'little'),10
                    if va==pointer+83889*8: return (0 if mode=='aot' else mod.ENTRY).to_bytes(8,'little'),20
                    if va==self.base+self.pdata_rva: return table,30
                    PE.body_reads+=1; return b'\x90'*(size-1 if mode=='short' else size),40
            real_sha=mod.sha
            def hash_override(b): return '5895dbeef8fe13458720889e318ba64f3fc9c5d3ca03bf01b0adc70eee16c08d' if b is table else real_sha(b)
            req={'stockIdentity':{'YuanShen.exe':{'bytes':10000,'sha256':'x'}}}
            if mode=='boundary': diag={'exact':[],'containing':[],'overlapping':[]}
            with patch.object(mod,'source_gate',return_value=(req,{})),patch.object(mod.subprocess,'check_output',return_value=str(root)),patch.object(mod,'check',return_value={}),patch.object(mod,'FilePE',PE),patch.object(mod,'find_pdata',return_value=diag),patch.object(mod,'sha',side_effect=hash_override):
                if mode=='aot':
                    with self.assertRaises(ValueError): mod.collect(dest)
                    report=json.loads((dest/'failure.private.json').read_bytes())
                else:
                    self.assertEqual(mod.collect(dest),2); report=json.loads((dest/'result.private.json').read_bytes())
            if mode in ('aot','boundary','bytecap','rvadrift'): self.assertEqual((report['bodyRead'],PE.body_reads),(0,0)); self.assertFalse((dest/'code.bin').exists())
            if mode=='short': self.assertEqual((report['stage'],report['bodyRead'],PE.body_reads),('short-body',543,1))
            if mode=='instrcap': self.assertEqual((report['stage'],report['bodyRead'],PE.body_reads),('decode-stop',544,1)); self.assertEqual(report['body']['decodeState'],'instruction-cap')

    def test_gates_and_truthful_after_read_counts(self):
        for mode in ('aot','boundary','bytecap','rvadrift','short','instrcap'):
            with self.subTest(mode=mode): self.run_gate(mode)

    def test_source_or_stock_drift_before_body(self):
        mod.OUT.mkdir(parents=True,exist_ok=True)
        for mode in ('source','stock'):
            with self.subTest(mode=mode),tempfile.TemporaryDirectory(dir=mod.OUT) as tmp:
                root=Path(tmp); dest=root/'out'; dest.mkdir(); req={'stockIdentity':{'YuanShen.exe':{'bytes':1,'sha256':'x'}}}
                with patch.object(mod,'source_gate',side_effect=ValueError('pin') if mode=='source' else None,return_value=(req,{})),patch.object(mod.subprocess,'check_output',return_value=str(root)),patch.object(mod,'check',side_effect=ValueError('stock')):
                    with self.assertRaises(ValueError): mod.collect(dest)
                report=json.loads((dest/'failure.private.json').read_bytes()); self.assertEqual(report['bodyRead'],0); self.assertFalse((dest/'code.bin').exists())


if __name__=='__main__': unittest.main()
