"""Independent saved caller, logical signature and bounded source gate tests."""
import copy
import importlib.util
import io
import json
import struct
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import proud_nativewrapper_ctor_window as mod


class CtorWindowTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.raw=(mod.CALLER/'code.bin').read_bytes(); cls.asm=json.loads((mod.CALLER/'code.asm.private.json').read_bytes())

    def test_saved_literal_caller_and_physical_registers(self):
        mod.validate_caller(self.raw,self.asm)
        setup=self.raw[0xd1:0xee]; self.assertEqual(setup,bytes.fromhex('0f57c00f294424400f29442430488d4c24304189f84189c1e892090000'))
        self.assertEqual(0x146e032d9+5+struct.unpack('<i',setup[-4:])[0],0x146e03c70)
        bad=bytearray(self.raw); bad[0xe9]^=1
        with self.assertRaises(ValueError): mod.validate_caller(bytes(bad),self.asm)
        bad=copy.deepcopy(self.asm); next(x for x in bad['instructions'] if x['address']=='0x146e032d6')['operands']='r9d, ecx'
        with self.assertRaises(ValueError): mod.validate_caller(self.raw,bad)

    def test_logical_ctor_types_are_not_physical_output(self):
        method={'index':83916,'name':'.ctor','registeredAddress':'0x146e03c70','flags':'0x1886','parameterCount':3,'virtualSlot':65535}
        records=[{'methods':[]} for _ in range(11713)]; records[11712]={'index':11712,'name':'NativeWrapper','namespace':'MoleMole.NativeConfig','methods':[method]}
        sig={'report':{'returnTypesStatus':'not-recovered'},'methods':[{'index':83916,'declaringType':11712,'name':'.ctor','registeredAddress':'0x146e03c70','parameters':[{'typeIndex':1},{'typeIndex':2},{'typeIndex':2}]}],'typeReferences':{'1':{'kind':'class','name':'INativePage','namespace':'MoleMole.NativeConfig','typeDefinition':11466,'byRef':False},'2':{'kind':'int32','byRef':False}}}
        mod.declaration(records,sig)
        for mutate in ('size','owner','return'):
            r=copy.deepcopy(records); s=copy.deepcopy(sig)
            if mutate=='size': s['typeReferences']['2']['kind']='uint32'
            if mutate=='owner': r[11712]['name']='Other'
            if mutate=='return': s['report']['returnTypesStatus']='recovered'
            with self.subTest(mutate=mutate),self.assertRaises(ValueError): mod.declaration(r,s)

    def test_pinned_old_filepe_at_contract(self):
        mod.check(mod.OLD_TOOL,18168,'2d9a5bb612a62299b858e20efedc8dd082bf7bca0eb3db43fe5cc4f43b0d9f6a')
        spec=importlib.util.spec_from_file_location('validated_filepe_test',mod.OLD_TOOL); old=importlib.util.module_from_spec(spec); spec.loader.exec_module(old)
        pe=old.FilePE.__new__(old.FilePE); pe.f=io.BytesIO(b'0123456789'); pe.sections=[{'begin':0x1000,'end':0x1008,'raw':1,'flags':0x20000000}]
        self.assertEqual(pe.at(0x1002,3,True),(b'345',3))
        with self.assertRaises(ValueError): pe.at(0x1007,2,True)

    def test_pdata_unique_and_decode(self):
        base=0x140000000; rel=mod.ENTRY-base; raw=struct.pack('<III',rel,rel+100,0x1000); section=lambda va,size,executable:{'raw':0,'begin':va}
        self.assertEqual(mod.admit(mod.find_pdata(raw,base,0x2000),section,100)['size'],100)
        for table in (b'',raw*2,raw+struct.pack('<III',rel-1,rel+1,1),raw+struct.pack('<III',rel+50,rel+120,2),struct.pack('<III',rel,rel,1)):
            with self.subTest(raw=table.hex()),self.assertRaises(ValueError): mod.admit(mod.find_pdata(table,base,0x2000),section,1000)
        valid=mod.find_pdata(raw,base,0x2000)
        with self.assertRaises(ValueError): mod.admit(valid,lambda *args:(_ for _ in ()).throw(ValueError('nonexec')),1000)
        with self.assertRaises(ValueError): mod.admit(valid,section,99)
        rows,state=mod.decode(bytes.fromhex('90c3')); self.assertEqual(state,'complete'); self.assertEqual(b''.join(bytes.fromhex(x['hex']) for x in rows),bytes.fromhex('90c3'))
        _,state=mod.decode(b'\x0f'); self.assertEqual(state,'incomplete-decode')
        rows,state=mod.decode(b'\x90'*257); self.assertEqual((len(rows),state),(257,'instruction-cap'))
        edge={'address':'0x1','call':True,'jump':False,'directTarget':'0x1405336c0','ripReferences':[]}; self.assertTrue(mod.source_index([edge])['dependentSemanticsFrozen'])

    def run_gate(self,mode):
        mod.OUT.mkdir(parents=True,exist_ok=True)
        with tempfile.TemporaryDirectory(dir=mod.OUT) as tmp:
            root=Path(tmp); (root/'YuanShen.exe').write_bytes(b''); dest=root/'out'; dest.mkdir(); pointer=0x1428a54d0; table=bytes(14344080)
            n=513 if mode=='bytecap' else 257 if mode=='instrcap' else 100; rel=mod.ENTRY-0x140000000; record=struct.pack('<III',rel,rel+n,0x1000)
            row={'begin':hex(mod.ENTRY),'endExclusive':hex(mod.ENTRY+n),'size':n,'unwindRVA':'0x1000','recordRVA':'0x1a4e6a40','recordHex':record.hex()}; diag={'exact':[row],'containing':[row],'overlapping':[row]}
            class PE:
                base=0x140000000; pdata_rva=0x1a4e6a40; pdata_size=14344080; body_reads=0; meta={'readCalls':2,'bytesRead':4616,'distinctBytes':4096}
                def __init__(self,f): pass
                def section(self,va,size,executable): return {'raw':0,'begin':va}
                def at(self,va,size,executable=False):
                    if va==0x1422f9950: return pointer.to_bytes(8,'little'),10
                    if va==pointer+83916*8: return (0 if mode=='aot' else mod.ENTRY).to_bytes(8,'little'),20
                    if va==self.base+self.pdata_rva: return table,30
                    PE.body_reads+=1; return b'\x90'*(size-1 if mode=='short' else size),40
            real_sha=mod.sha
            def hash_override(b): return '5895dbeef8fe13458720889e318ba64f3fc9c5d3ca03bf01b0adc70eee16c08d' if b is table else real_sha(b)
            req={'stockIdentity':{'YuanShen.exe':{'bytes':10000,'sha256':'x'}}}
            if mode=='boundary': diag={'exact':[],'containing':[],'overlapping':[]}
            with patch.object(mod,'source_gate',return_value=(req,{},PE)),patch.object(mod.subprocess,'check_output',return_value=str(root)),patch.object(mod,'check',return_value={}),patch.object(mod,'find_pdata',return_value=diag),patch.object(mod,'sha',side_effect=hash_override):
                if mode=='aot':
                    with self.assertRaises(ValueError): mod.collect(dest)
                    report=json.loads((dest/'failure.private.json').read_bytes())
                else:
                    self.assertEqual(mod.collect(dest),2); report=json.loads((dest/'result.private.json').read_bytes())
            if mode in ('aot','boundary','bytecap'): self.assertEqual((report['bodyRead'],PE.body_reads),(0,0)); self.assertFalse((dest/'code.bin').exists())
            if mode=='short': self.assertEqual((report['stage'],report['bodyRead'],PE.body_reads),('short-body',99,1))
            if mode=='instrcap': self.assertEqual((report['stage'],report['bodyRead'],PE.body_reads),('decode-stop',257,1)); self.assertEqual(report['body']['decodeState'],'instruction-cap')
            self.assertTrue(all(report[k] is None for k in ('actualB','actualO','actualLease','actualRow')))

    def test_gates_and_truthful_read_counts(self):
        for mode in ('aot','boundary','bytecap','short','instrcap'):
            with self.subTest(mode=mode): self.run_gate(mode)

    def test_source_and_stock_drift_before_body(self):
        mod.OUT.mkdir(parents=True,exist_ok=True)
        for mode in ('source','stock'):
            with self.subTest(mode=mode),tempfile.TemporaryDirectory(dir=mod.OUT) as tmp:
                root=Path(tmp); dest=root/'out'; dest.mkdir(); req={'stockIdentity':{'YuanShen.exe':{'bytes':1,'sha256':'x'}}}
                with patch.object(mod,'source_gate',side_effect=ValueError('pin') if mode=='source' else None,return_value=(req,{},object)),patch.object(mod.subprocess,'check_output',return_value=str(root)),patch.object(mod,'check',side_effect=ValueError('stock')):
                    with self.assertRaises(ValueError): mod.collect(dest)
                report=json.loads((dest/'failure.private.json').read_bytes()); self.assertEqual(report['bodyRead'],0); self.assertFalse((dest/'code.bin').exists())


if __name__=='__main__': unittest.main()
