"""Independent anchor and synthetic complete-window gate tests."""
import copy
import json
import struct
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import proud_callback_tail_window as mod


def anchor():
    raw=bytes.fromhex('e91bfdffff'); reads=[{'address':hex(0x1408b9780+i),'requestedBytes':1,'bytes':1,'hex':raw[i:i+1].hex()} for i in range(5)]
    attempts=[{'address':'0x1408b9780','pendingHex':raw[:i+1].hex(),'decoded':i==4} for i in range(5)]
    result={'prefixRead':5,'codeReadCalls':5,'prefixEndExclusive':'0x1408b9785','source':{'reads':reads,'attempts':attempts,'stopReason':'first-transfer','pendingTailHex':'','instructions':[{}]}}
    slice_raw=bytes.fromhex('488d05672f79ff488905a8e66104'); slice_result={'assessment':{'localBindingProvenUnderFallthrough':True,'globalBindingProven':False,'currentCallbackTarget':None,'naturalExecution':False}}
    return raw,result,slice_raw,slice_result


class TailTest(unittest.TestCase):
    def setUp(self):
        self.base=0x140000000; self.relative=mod.ENTRY-self.base; self.record=struct.pack('<III',self.relative,self.relative+100,0x1000)
        self.section=lambda *args: {'raw':0,'begin':mod.ENTRY}

    def test_literal_e9_and_ledger_drift(self):
        raw,result,slice_raw,slice_result=anchor(); mod.anchor_check(raw,result,slice_raw,slice_result)
        self.assertEqual(0x1408b9785+struct.unpack('<i',raw[1:])[0],0x1408b94a0)
        for field in ('raw','read','scope','slice'):
            r=copy.deepcopy(result); sr=copy.deepcopy(slice_result); p=raw; sl=slice_raw
            if field=='raw': p=bytes(5)
            if field=='read': r['source']['reads'][4]['address']='0x1408b9785'
            if field=='scope': sr['assessment']['naturalExecution']=True
            if field=='slice': sl=bytes(14)
            with self.subTest(field=field),self.assertRaises(ValueError): mod.anchor_check(p,r,sl,sr)

    def test_pdata_boundary_cases(self):
        valid=mod.find_pdata(self.record,self.base,0x2000); self.assertEqual(mod.admit(valid,self.section,1000)['size'],100)
        cases=[b'',self.record*2,self.record+struct.pack('<III',self.relative-1,self.relative+1,1),self.record+struct.pack('<III',self.relative+50,self.relative+120,2),struct.pack('<III',self.relative,self.relative,1)]
        for table in cases:
            with self.subTest(table=table),self.assertRaises(ValueError): mod.admit(mod.find_pdata(table,self.base,0x2000),self.section,1000)
        with self.assertRaises(ValueError): mod.admit(valid,lambda *args: (_ for _ in ()).throw(ValueError('nonexec')),1000)
        with self.assertRaises(ValueError): mod.admit(valid,self.section,99)

    def test_decoder_exact_and_overflow(self):
        rows,state=mod.decode(bytes.fromhex('90c3')); self.assertEqual(state,'complete'); self.assertEqual(b''.join(bytes.fromhex(x['hex']) for x in rows),bytes.fromhex('90c3'))
        _,state=mod.decode(b'\x0f'); self.assertEqual(state,'incomplete-decode')
        rows,state=mod.decode(b'\x90'*513); self.assertEqual(state,'instruction-cap'); self.assertEqual(len(rows),513)
        edge={'address':'0x1','call':True,'jump':False,'directTarget':'0x1405336c0','ripReferences':[]}
        self.assertTrue(mod.source_index([edge])['dependentSemanticsFrozen'])

    def run_gate(self,mode):
        mod.OUT.mkdir(parents=True,exist_ok=True)
        with tempfile.TemporaryDirectory(dir=mod.OUT) as tmp:
            root=Path(tmp); (root/'YuanShen.exe').write_bytes(b''); dest=root/'out'; dest.mkdir()
            n=1025 if mode=='bytecap' else 513 if mode=='instrcap' else 100
            record=struct.pack('<III',self.relative,self.relative+n,0x1000)
            table=record+bytes(14344080-len(record))
            class PE:
                base=0x140000000; pdata_rva=0x1a4e6a40; pdata_size=14344080; body_reads=0; meta={'readCalls':2,'bytesRead':4616,'distinctBytes':4096}
                def __init__(self,f): pass
                def section(self,*args): return {'raw':0,'begin':mod.ENTRY}
                def at(self,va,size,executable=False):
                    if va==self.base+self.pdata_rva: return table
                    PE.body_reads+=1; return b'\x90'*(size-1 if mode=='short' else size)
            real_sha=mod.sha
            def sha_override(b): return '5895dbeef8fe13458720889e318ba64f3fc9c5d3ca03bf01b0adc70eee16c08d' if b is table else real_sha(b)
            req={'stockIdentity':{'YuanShen.exe':{'bytes':100000,'sha256':'x'}}}
            with patch.object(mod,'source_gate',return_value=(req,{})),patch.object(mod.subprocess,'check_output',return_value=str(root)),patch.object(mod,'check',return_value={}),patch.object(mod,'FilePE',PE),patch.object(mod,'sha',side_effect=sha_override):
                self.assertEqual(mod.collect(dest),2)
            result=json.loads((dest/'result.private.json').read_bytes()); pre=json.loads((dest/'pre-body.private.json').read_bytes()); self.assertEqual(pre['bodyRead'],0)
            if mode=='bytecap': self.assertEqual((result['stage'],result['bodyRead'],PE.body_reads),('budget-stop',0,0)); self.assertFalse((dest/'code.bin').exists())
            if mode=='short': self.assertEqual((result['stage'],result['bodyRead'],PE.body_reads),('short-body',99,1)); self.assertEqual(len((dest/'code.bin').read_bytes()),99)
            if mode=='instrcap': self.assertEqual((result['stage'],result['bodyRead'],PE.body_reads),('decode-stop',513,1)); self.assertEqual(result['body']['decodeState'],'instruction-cap')

    def test_truthful_body_counts_on_cap_and_short_read(self):
        for mode in ('bytecap','short','instrcap'):
            with self.subTest(mode=mode): self.run_gate(mode)

    def test_section_partial_overlap_and_pin_checks(self):
        pe=mod.FilePE.__new__(mod.FilePE); pe.sections=[{'begin':mod.ENTRY,'end':mod.ENTRY+100,'raw':0,'flags':0x20000000}]
        self.assertEqual(pe.section(mod.ENTRY,100,True)['begin'],mod.ENTRY)
        pe.sections.append({'begin':mod.ENTRY+90,'end':mod.ENTRY+120,'raw':0,'flags':0x20000000})
        with self.assertRaises(ValueError): pe.section(mod.ENTRY,100,True)
        mod.OUT.mkdir(parents=True,exist_ok=True)
        with tempfile.TemporaryDirectory(dir=mod.OUT) as tmp:
            path=Path(tmp)/'decoder.bin'; path.write_bytes(b'x')
            with self.assertRaises(ValueError): mod.check(path,1,'0'*64)

    def test_stock_failure_before_body(self):
        mod.OUT.mkdir(parents=True,exist_ok=True)
        with tempfile.TemporaryDirectory(dir=mod.OUT) as tmp:
            root=Path(tmp); dest=root/'out'; dest.mkdir()
            req={'stockIdentity':{'YuanShen.exe':{'bytes':1,'sha256':'0'*64}}}
            with patch.object(mod,'source_gate',return_value=(req,{})),patch.object(mod.subprocess,'check_output',return_value=str(root)),patch.object(mod,'check',side_effect=ValueError('stock mismatch')):
                with self.assertRaises(ValueError): mod.collect(dest)
            result=json.loads((dest/'failure.private.json').read_bytes()); self.assertEqual(result['bodyRead'],0); self.assertFalse((dest/'code.bin').exists())

    def test_source_failure_before_body(self):
        mod.OUT.mkdir(parents=True,exist_ok=True)
        with tempfile.TemporaryDirectory(dir=mod.OUT) as tmp:
            dest=Path(tmp)/'out'; dest.mkdir()
            with patch.object(mod,'source_gate',side_effect=ValueError('identity drift')):
                with self.assertRaises(ValueError): mod.collect(dest)
            result=json.loads((dest/'failure.private.json').read_bytes()); self.assertEqual(result['bodyRead'],0); self.assertFalse((dest/'code.bin').exists())


if __name__=='__main__': unittest.main()
