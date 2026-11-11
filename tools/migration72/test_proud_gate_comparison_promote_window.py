import copy
import hashlib
import io
import json
import struct
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
from tools.migration72 import proud_gate_comparison_promote_window as s

class Gates(unittest.TestCase):
    def test_source_hash_drift(self):
        with tempfile.TemporaryDirectory() as d:
            p=Path(d)/'p';p.write_bytes(b'abc');h=hashlib.sha256(b'abc').hexdigest()
            self.assertEqual(s.checked(p,h,3)['bytes'],3)
            with self.assertRaises(ValueError):s.checked(p,h,4)
            p.write_bytes(b'abd')
            with self.assertRaises(ValueError):s.checked(p,h,3)

    def test_join_typed_fields_and_unique_declarations(self):
        records=[{'index':i,'methods':[],'fields':[]} for i in range(25487)]
        ds=[]; sig={'methods':[],'typeReferences':{'124':{'kind':'uint32','byRef':False}}}
        for owner,mid,address,n,flags in ((25486,198316,s.ENTRY,0,'0x81'),(18163,138176,0x15184adf0,0,'0x886'),(22917,176922,0x14af8f5c0,2,'0x96')):
            name='m'+str(mid); own='o'+str(owner)
            method={'index':mid,'name':name,'nameIndex':'0x1','registeredAddress':hex(address),'parameterCount':n,'flags':flags,'virtualSlot':65535}
            ds.append(dict(method,owner=owner,ownerName=own));records[owner].update(name=own,methods=[method])
            if n:sig['methods'].append({'index':mid,'name':name,'registeredAddress':hex(address),'declaringType':owner,'parameters':[{'typeIndex':124},{'typeIndex':124}]})
        records[25486]['fields']=[{'index':125344,'rawOffset':'0x230','offsetKind':'instance','type':{'kind':'class','typeDefinition':18163,'byRef':False}},{'index':125350,'rawOffset':'0x25c','offsetKind':'instance','type':{'kind':'uint32','byRef':False}}]
        f1={'owner':25486,'field':125344,'offset':'0x230','kind':'class','typeDefinition':18163};f2={'owner':25486,'field':125350,'offset':'0x25c','kind':'uint32'}
        candidates=[{'address':hex(i),'containingRecords':[],'registeredOwners':[]} for i in range(73)]
        candidates[33]={'address':hex(s.SITE),'containingRecords':[{'begin':hex(s.ENTRY)}],'registeredOwners':[]}
        candidates[40]={'address':'0x151da3f17','containingRecords':[{'begin':'0x151da35a0'}],'registeredOwners':[]}
        contexts=[{'function':[5+i,6+i],'before':[],'method':[],'siteRva':i} for i in range(37)]
        for start in (s.ENTRY-s.BASE,0x151da35a0-s.BASE):
            for i in range(2):contexts.append({'function':[start,start+10],'before':[],'method':[],'siteRva':start+i})
        matches=[]
        for c in candidates:
            for x in contexts:
                if any(int(r['begin'],16)==s.BASE+x['function'][0] for r in c['containingRecords']):matches.append({'candidate':c['address'],'oldContext':x['before'],'oldDeclaredOwnerMethod':x['method'],'oldFunctionRVAs':x['function'],'oldSiteRVA':x['siteRva'],'originalPdata':c['containingRecords'],'registeredOwners':c['registeredOwners']})
        prior={'matches':matches,'uniqueFunctionStarts':[hex(s.ENTRY),'0x151da35a0'],'newPEReads':0}
        req={'declarations':ds,'selectedThisField':f1,'phaseCandidateField':f2,'selection':{'selectedCandidate':candidates[33],'selectedOriginalContexts':contexts[37:39]}}
        idx={'totalCandidates':73,'candidates':candidates}
        s.verify_sources(idx,contexts,prior,records,sig,req)
        records[25486]['fields'][0]['type']['typeDefinition']=1
        with self.assertRaisesRegex(ValueError,'field type'):s.verify_sources(idx,contexts,prior,records,sig,req)
        records[25486]['fields'][0]['type']['typeDefinition']=18163
        records[0]['methods'].append(dict(records[25486]['methods'][0]))
        with self.assertRaisesRegex(ValueError,'not unique'):s.verify_sources(idx,contexts,prior,records,sig,req)
        records[0]['methods'].clear();contexts[37]['function'][0]+=1
        with self.assertRaisesRegex(ValueError,'join differs'):s.verify_sources(idx,contexts,prior,records,sig,req)

    def test_three_slots_and_all_e8_order_targets(self):
        cell=bytes.fromhex('d0548a4201000000');slots=bytes.fromhex('60d5bd4c01000000f0ad845101000000c0f5f84a01000000')
        s.check_aot(cell,slots)
        for a,b in ((bytes(8),slots),(cell,slots[8:16]+slots[:8]+slots[16:]),(cell,slots[:16]+bytes(8)),(cell,slots[:16])):
            with self.assertRaises(ValueError):s.check_aot(a,b)
        calls=b''.join(bytes.fromhex(x[2]) for x in s.CALLS);targets=(0x15184adf0,0x14af8f5c0)
        s.check_calls(calls,targets)
        self.assertEqual(s.rel32_target(b'\xe8'+struct.pack('<i',-20),100),85)
        for i in range(3):
            bad=bytearray(calls);bad[i*5+1]^=1
            with self.assertRaises(ValueError):s.check_calls(bad,targets)
        for bad in (calls[5:10]+calls[:5]+calls[10:],calls[:10],b'\xe9'+calls[1:]):
            with self.assertRaises(ValueError):s.check_calls(bad,targets)
        with self.assertRaises(ValueError):s.check_calls(calls,(targets[0]+1,targets[1]))

    def test_pdata_uniqueness_all_sites_and_budget(self):
        rel=s.ENTRY-s.BASE
        def row(b,e):return struct.pack('<III',b,e,1)
        raw=row(rel,rel+4096);self.assertEqual(s.select_pdata(raw,0)['exact'][0]['size'],4096)
        for table in (b'',raw+raw,row(rel-1,rel+4096),raw+row(rel-1,rel+1),raw+row(rel+1,rel+2000),row(rel,rel+4097),b'bad'):
            with self.assertRaises(ValueError):s.select_pdata(table,0)

    def test_file_backed_executable_short_and_create_new(self):
        sec=[(100,10,20,0x20000000)];self.assertEqual(s.offset(sec,100,10,True),20)
        for rows,address,length in ((sec,100,11),([(100,10,20,0)],100,1),(sec+sec,100,1)):
            with self.assertRaises(ValueError):s.offset(rows,address,length,True)
        with self.assertRaisesRegex(ValueError,'short read'):s.read_at(io.BytesIO(b'a'),0,2)
        with tempfile.TemporaryDirectory() as d:
            p=Path(d)/'x';s.save(p,b'first')
            with self.assertRaises(FileExistsError):s.save(p,b'second')
            self.assertEqual(p.read_bytes(),b'first')

    def test_decode_budget_gaps_and_each_instruction_boundary(self):
        self.assertEqual(len(s.decode(b'\x90'*1024,0,0,b'\x90')['lines']),1024)
        with self.assertRaisesRegex(ValueError,'budget'):s.decode(b'\x90'*1025,0,0,b'\x90')
        with self.assertRaisesRegex(ValueError,'incomplete'):s.decode(b'\x90\x0f',0,0,b'\x90')
        code=bytearray(b'\x90'*2034)
        code[s.SITE-s.ENTRY:s.SITE-s.ENTRY+7]=bytes.fromhex('488bb9b8120200')
        for site,_,raw in s.CALLS:code[site-s.ENTRY:site-s.ENTRY+5]=bytes.fromhex(raw)
        # Synthetic filler exceeds the instruction cap; isolate the boundary checks with an explicit larger cap.
        s.decode(code,max_instructions=2048)
        for site,_,_ in s.CALLS:
            bad=bytearray(code);bad[site-s.ENTRY-1]=0x66
            with self.assertRaises(ValueError):s.decode(bad,max_instructions=2048)
        bad=bytearray(code);bad[s.SITE-s.ENTRY-1]=0x66
        with self.assertRaises(ValueError):s.decode(bad,max_instructions=2048)

    def test_prebody_failure_stops_and_preserves_stage(self):
        for fail in ('cell','slot','pdata','candidate','calls'):
            with self.subTest(fail=fail),tempfile.TemporaryDirectory() as d:
                root=Path(d);client=root/'client';client.mkdir();(client/'YuanShen.exe').write_bytes(b'');dest=root/'out';dest.mkdir();reads=[]
                row={'recordRVA':'0x1a9c25b8','recordHex':'60d5bd0c52ddbd0ccc263704','begin':hex(s.ENTRY),'endExclusive':hex(s.END),'unwindRVA':'0x43726cc','size':2034}
                diag={'exact':[row],'containing':[row],'sites':{hex(x):[row] for x in [s.SITE]+[c[0] for c in s.CALLS]}}
                baseoff=0xc385d60
                mapping={0x1422f9950:bytes.fromhex('d0548a4201000000'),0x142a28a30:bytes.fromhex('60d5bd4c01000000'),0x1429b32d0:bytes.fromhex('f0ad845101000000'),0x1429feda0:bytes.fromhex('c0f5f84a01000000'),s.SITE-s.ENTRY+baseoff:bytes.fromhex('488bb9b8120200')}
                for site,_,raw in s.CALLS:mapping[site-s.ENTRY+baseoff]=bytes.fromhex(raw)
                if fail=='cell':mapping[0x1422f9950]=bytes(8)
                if fail=='slot':mapping[0x1429b32d0]=bytes(8)
                if fail=='candidate':mapping[s.SITE-s.ENTRY+baseoff]=bytes(7)
                if fail=='calls':mapping[s.CALLS[1][0]-s.ENTRY+baseoff]=bytes(5)
                def read(f,off,length):
                    reads.append((off,length))
                    if length==14344080:return b'table'
                    if (off,length)==(baseoff,2034):raise AssertionError('body read before gate')
                    return mapping[off]
                def offset(sections,address,length,executable=False):return baseoff+address-s.ENTRY if s.ENTRY<=address<s.END else address
                real_sha=s.sha
                def sha(data):return '5895dbeef8fe13458720889e318ba64f3fc9c5d3ca03bf01b0adc70eee16c08d' if data==b'table' else real_sha(data)
                def pdata(*args):
                    if fail=='pdata':raise ValueError('ambiguous pdata')
                    return diag
                with patch.object(s,'source_gate',return_value={'fixedClientInputs':{}}),patch.object(s.subprocess,'check_output',return_value=str(client)),patch.object(s,'pe_info',return_value=((0x1a4e6a40,14344080),[])),patch.object(s,'read_at',side_effect=read),patch.object(s,'offset',side_effect=offset),patch.object(s,'select_pdata',side_effect=pdata),patch.object(s,'sha',side_effect=sha):
                    with self.assertRaises(ValueError):s.collect(dest)
                self.assertNotIn((baseoff,2034),reads);self.assertFalse((dest/'code.bin').exists())
                failure=json.loads((dest/'failure.private.json').read_bytes())
                self.assertEqual(failure['stage'],'actual-aot' if fail in ('cell','slot') else 'pdata-before-body')
                if fail=='calls':self.assertEqual(len((dest/'provenance-calls.bin').read_bytes()),15)

if __name__=='__main__':unittest.main()
