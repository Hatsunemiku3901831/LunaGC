"""Synthetic caller, batch-boundary and truthful read-count tests."""
import copy
import json
import struct
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import proud_current_adoption_source_windows as mod


def callers():
    a=bytes.fromhex('8bce488d95b00000004533c0')+b'\xe8'+struct.pack('<i',0x14d7ac240-(0x1518488ca+5))
    b=bytes.fromhex('488b8d000100008bd041b001')+b'\xe8'+struct.pack('<i',0x151846d90-(0x151848a97+5))
    return [a,b]


def diagnostics():
    rows={}
    for i,target in mod.TARGETS.items():
        size={663528:100,138266:556,138165:0x155e}[i]
        raw=struct.pack('<III',target-0x140000000,target-0x140000000+size,0x4870f64 if i==138165 else 0x1000)
        row={'begin':hex(target),'endExclusive':hex(target+size),'size':size,'unwindRVA':hex(0x4870f64 if i==138165 else 0x1000),'recordRVA':'0x1abf8400' if i==138165 else hex(0x1a4e6a40+i*12),'recordHex':raw.hex()}
        rows[i]={'exact':[row],'containing':[row],'overlapping':[row]}
    return rows


class AdoptionTest(unittest.TestCase):
    def setUp(self): self.cs=mod.decoder(); self.callers=callers()

    def test_literal_caller_and_r8b(self):
        for i,raw in enumerate(self.callers):
            rows=mod.caller_check(raw,i,self.cs); self.assertEqual(len(rows),4)
            site=mod.CALLERS[i][1]; self.assertEqual(site+5+struct.unpack('<i',raw[-4:])[0],mod.CALLERS[i][2])
        bad=bytearray(self.callers[0]); bad[-1]^=1
        with self.assertRaises(ValueError): mod.caller_check(bytes(bad),0,self.cs)
        with self.assertRaises(ValueError): mod.caller_check(self.callers[1][:-1],1,self.cs)
        # mov r8d,1 is six bytes and cannot satisfy the R8B-only 17-byte setup.
        wrong=self.callers[1][:9]+bytes.fromhex('41b801000000')+self.callers[1][-5:]
        with self.assertRaises(ValueError): mod.caller_check(wrong,1,self.cs)

    def test_batch_pdata_and_caps(self):
        d=diagnostics(); section=lambda va,size,exec:{'raw':0,'begin':va}
        selected=mod.admit(d,section,10000); self.assertEqual(selected[138266]['size'],556); self.assertEqual(sum(selected[i]['size'] for i in (663528,138266)),656)
        for mode in ('missing','duplicate','containing','overlap','nonexec','selector','helpercap','caller','filesize'):
            diag=copy.deepcopy(d); fn=section; limit=10000
            if mode=='missing': diag[663528]['exact']=[]
            if mode=='duplicate': diag[138266]['exact'].append(diag[138266]['exact'][0])
            if mode=='containing': diag[663528]['containing']=[]
            if mode=='overlap': diag[138266]['overlapping'].append({'begin':'0x1'})
            if mode=='nonexec': fn=lambda *args: (_ for _ in ()).throw(ValueError('not executable'))
            if mode=='selector':
                for row in diag[138266]['exact']+diag[138266]['containing']+diag[138266]['overlapping']: row['size']=557; row['endExclusive']=hex(mod.TARGETS[138266]+557)
            if mode=='helpercap':
                for row in diag[663528]['exact']+diag[663528]['containing']+diag[663528]['overlapping']: row['size']=4097; row['endExclusive']=hex(mod.TARGETS[663528]+4097)
            if mode=='caller':
                for row in diag[138165]['exact']+diag[138165]['containing']+diag[138165]['overlapping']: row['recordHex']='00'*12
            if mode=='filesize': limit=99
            with self.subTest(mode=mode),self.assertRaises(ValueError): mod.admit(diag,fn,limit)
        self.assertEqual(4096+556,4652); self.assertEqual(4652+34+32+36,4754)

    def test_decode_and_refused_edge(self):
        rows,state=mod.decode(bytes.fromhex('90c3'),mod.TARGETS[663528],1024,self.cs); self.assertEqual(state,'complete'); self.assertEqual(b''.join(bytes.fromhex(x['hex']) for x in rows),bytes.fromhex('90c3'))
        _,state=mod.decode(b'\x0f',mod.TARGETS[663528],1024,self.cs); self.assertEqual(state,'incomplete-decode')
        rows,state=mod.decode(b'\x90'*257,mod.TARGETS[138266],256,self.cs); self.assertEqual((len(rows),state),(257,'instruction-cap'))
        edge={'address':'0x1','call':True,'jump':False,'directTarget':'0x1405336c0','ripReferences':[]}; self.assertTrue(mod.source_index([edge])['dependentSemanticsFrozen'])

    def run_gate(self,mode):
        mod.OUT.mkdir(parents=True,exist_ok=True)
        with tempfile.TemporaryDirectory(dir=mod.OUT) as tmp:
            root=Path(tmp); (root/'YuanShen.exe').write_bytes(b''); dest=root/'out'; dest.mkdir(); pointer=0x1428a54d0; table=bytes(14344080); chosen={i:d['exact'][0] for i,d in diagnostics().items()}
            if mode=='instrcap': chosen[663528]['size']=1100
            class PE:
                base=0x140000000; pdata_rva=0x1a4e6a40; pdata_size=14344080; body_reads=0; meta={'readCalls':2,'bytesRead':4616,'distinctBytes':4096}
                def __init__(self,f): pass
                def section(self,*args): return {'raw':10 if mode=='interval' and args[0]==mod.TARGETS[663528] else 10000 if args[0]==mod.TARGETS[663528] else 20000,'begin':args[0]}
                def at(self,va,size,executable=False):
                    if va==0x1422f9950: return pointer.to_bytes(8,'little'),10
                    for n,(i,target) in enumerate(mod.TARGETS.items()):
                        if va==pointer+i*8: return (0 if mode=='aot' and i==663528 else target).to_bytes(8,'little'),30+8*n
                    for i,(begin,_,_) in enumerate(mod.CALLERS):
                        if va==begin: return self_callers[i],100+20*i
                    if va==self.base+self.pdata_rva: return table,0
                    PE.body_reads+=1
                    return (b'\x90'*(size-1 if mode=='short' else size)),0
            self_callers=self.callers
            real_sha=mod.sha
            def hash_override(b): return '5895dbeef8fe13458720889e318ba64f3fc9c5d3ca03bf01b0adc70eee16c08d' if b is table else real_sha(b)
            req={'stockIdentity':{'YuanShen.exe':{'bytes':100000,'sha256':'x'}}}
            admission=patch.object(mod,'admit',side_effect=ValueError('one target boundary') if mode=='batch' else None,return_value=chosen)
            with patch.object(mod,'source_gate',return_value=(req,{})),patch.object(mod.subprocess,'check_output',return_value=str(root)),patch.object(mod,'check',return_value={}),patch.object(mod,'FilePE',PE),patch.object(mod,'find_pdata',return_value=diagnostics()),patch.object(mod,'sha',side_effect=hash_override),admission:
                if mode=='aot':
                    with self.assertRaises(ValueError): mod.collect(dest)
                    report=json.loads((dest/'failure.private.json').read_bytes())
                else:
                    self.assertEqual(mod.collect(dest),2); report=json.loads((dest/'result.private.json').read_bytes())
            if mode in ('batch','aot','interval'): self.assertEqual((report['bodyRead'],PE.body_reads),(0,0)); self.assertFalse(list(dest.glob('code-*')))
            if mode=='interval': self.assertEqual(report['pdataEvidenceBytes'],36); self.assertEqual(report['stage'],'batch-gate-stop')
            if mode=='short': self.assertEqual((report['bodyRead'],PE.body_reads),(99,1)); self.assertEqual(report['stage'],'short-body')
            if mode=='instrcap': self.assertEqual((report['bodyRead'],PE.body_reads),(1100,1)); self.assertEqual((report['stage'],report['reason']),('decode-stop','instruction-cap'))

    def test_batch_and_short_read_are_truthful(self):
        for mode in ('batch','aot','interval','short','instrcap'):
            with self.subTest(mode=mode): self.run_gate(mode)


if __name__=='__main__': unittest.main()
