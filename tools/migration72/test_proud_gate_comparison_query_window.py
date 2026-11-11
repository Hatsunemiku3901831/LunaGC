import copy
import hashlib
import io
import json
import struct
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
from tools.migration72 import proud_gate_comparison_query_window as s

class Gates(unittest.TestCase):
    def test_source_pins_and_index_identity(self):
        with tempfile.TemporaryDirectory() as d:
            p=Path(d)/'p';p.write_bytes(b'abc');h=hashlib.sha256(b'abc').hexdigest()
            self.assertEqual(s.checked(p,h,3)['bytes'],3)
            with self.assertRaises(ValueError): s.checked(p,h,4)
            p.write_bytes(b'abd')
            with self.assertRaises(ValueError): s.checked(p,h,3)
        with patch.object(s,'checked'), patch.object(Path,'read_bytes',side_effect=[b'{"sources": {}}',b'a',b'b',b'a']):
            with self.assertRaisesRegex(ValueError,'index bytes differ'): s.source_gate()

    def test_caller_and_query_declarations_and_all_parameters(self):
        decl={'owner':65145,'method':540714,'ownerName':'FJFACDLAHPK','methodName':'NPJHCCCOBAB','registeredAddress':hex(s.ENTRY),'parameterCount':5,'flags':'0x86','virtualSlot':65535,'parameters':[{'index':429956+i,'typeIndex':t,'kind':'class','typeDefinition':d,'byRef':False} for i,(t,d) in enumerate([(541028,32044),(70109,29191),(77815,51565),(32975,28174),(186371,58582)])]}
        query={'owner':63488,'method':526531,'ownerName':'GLAOBEOLFPE','methodName':'DIELFCLGHPF','registeredAddress':hex(s.QUERY),'parameterCount':1,'flags':'0x96','virtualSlot':65535,'parameter':{'index':417495,'typeIndex':124,'kind':'uint32','byRef':False}}
        candidate={'synthetic':'selected'}; req={'declaration':decl,'queryDeclaration':query,'boundedSelection':{'selectedCandidate':candidate}}
        index={'candidates':[{} for _ in range(73)],'totalCandidates':73};index['candidates'][17]=candidate
        records=[{'index':i,'methods':[]} for i in range(65146)]
        signatures={'methods':[],'typeReferences':{}}
        for d in (decl,query):
            params=d.get('parameters',[d.get('parameter')])
            records[d['owner']].update(name=d['ownerName'],methods=[{'index':d['method'],'name':d['methodName'],'registeredAddress':d['registeredAddress'],'parameterCount':d['parameterCount'],'flags':d['flags'],'virtualSlot':d['virtualSlot']}])
            signatures['methods'].append({'index':d['method'],'declaringType':d['owner'],'name':d['methodName'],'registeredAddress':d['registeredAddress'],'parameters':[{'index':p['index'],'typeIndex':p['typeIndex']} for p in params]})
            for p in params: signatures['typeReferences'][str(p['typeIndex'])]={k:v for k,v in p.items() if k in ('kind','typeDefinition','byRef')}
        s.verify_declaration(index,records,signatures,req)
        for p in decl['parameters']+[query['parameter']]:
            typ=signatures['typeReferences'][str(p['typeIndex'])]; typ['byRef']=True
            with self.assertRaisesRegex(ValueError,'parameter type'): s.verify_declaration(index,records,signatures,req)
            typ['byRef']=False
        signatures['typeReferences']['124']['kind']='int32'
        with self.assertRaisesRegex(ValueError,'parameter type'): s.verify_declaration(index,records,signatures,req)
        signatures['typeReferences']['124']['kind']='uint32'
        records[0]['methods'].append(dict(records[65145]['methods'][0]))
        with self.assertRaisesRegex(ValueError,'not unique'): s.verify_declaration(index,records,signatures,req)

    def test_two_slots_identity_order_and_signed_rel32(self):
        cell=bytes.fromhex('d0548a4201000000'); caller=bytes.fromhex('9017624a01000000'); query=bytes.fromhex('f0706d4d01000000')
        s.check_aot(cell,caller+query)
        for a,b in ((bytes(8),caller+query),(cell,query+caller),(cell,caller+bytes(8)),(cell,caller)):
            with self.assertRaises(ValueError): s.check_aot(a,b)
        e8=bytes.fromhex('e8f1570b03');s.check_site(e8)
        self.assertEqual(s.rel32_target(b'\xe8'+struct.pack('<i',-20),100),85)
        for raw,target in ((e8,s.QUERY+1),(b'\xe9'+e8[1:],s.QUERY),(e8[:-1]+b'\x04',s.QUERY)):
            with self.assertRaises(ValueError): s.check_site(raw,target=target)

    def test_pdata_uniqueness_overlap_and_budget(self):
        rel=s.ENTRY-s.BASE
        def row(b,e): return struct.pack('<III',b,e,1)
        raw=row(rel,rel+1024)
        self.assertEqual(s.select_pdata(raw,0)['exact'][0]['size'],1024)
        for table in (b'',raw+raw,row(rel-1,rel+1024),raw+row(rel-1,rel+1),raw+row(rel+1,rel+500),row(rel,rel+1025),b'bad'):
            with self.assertRaises(ValueError): s.select_pdata(table,0)

    def test_file_backed_and_executable(self):
        sec=[(100,10,20,0x20000000)]
        self.assertEqual(s.offset(sec,100,10,True),20)
        for rows,address,length in ((sec,100,11),([(100,10,20,0)],100,1),(sec+sec,100,1)):
            with self.assertRaises(ValueError): s.offset(rows,address,length,True)

    def test_decode_boundary_limit_gap_and_site(self):
        self.assertEqual(len(s.decode(b'\x90'*512,0,0,b'\x90')['lines']),512)
        with self.assertRaisesRegex(ValueError,'budget'): s.decode(b'\x90'*513,0,0,b'\x90')
        with self.assertRaisesRegex(ValueError,'whole instruction'): s.decode(bytes.fromhex('b801000000'),0,1,b'\x01')
        with self.assertRaisesRegex(ValueError,'whole instruction'): s.decode(b'\x90',0,0,b'\xcc')
        with self.assertRaisesRegex(ValueError,'incomplete'): s.decode(b'\x90\x0f',0,0,b'\x90')

    def test_failed_site_never_reads_body_and_preserves_raw(self):
        with tempfile.TemporaryDirectory() as d:
            root=Path(d);client=root/'client';client.mkdir();(client/'YuanShen.exe').write_bytes(b'');dest=root/'out';dest.mkdir()
            row={'recordRVA':'0x1a893c9c','recordHex':'9017620aab1a620a70510d04','begin':hex(s.ENTRY),'endExclusive':hex(s.END),'unwindRVA':'0x40d5170','size':795}
            diag={'exact':[row],'containing':[row],'siteContaining':[row]};reads=[]
            def fake_read(f,off,length):
                reads.append((off,length))
                if length==8 and off==0x1422f9950:return bytes.fromhex('d0548a4201000000')
                if length==8 and off==0x142cc5620:return bytes.fromhex('9017624a01000000')
                if length==8 and off==0x142ca9ae8:return bytes.fromhex('f0706d4d01000000')
                if length==14344080:return b'table'
                if length==5:return b'wrong'
                raise AssertionError('body read before site gate')
            def fake_offset(sections,address,length,executable=False):return {s.ENTRY:0x9dc9f90,s.SITE:0x9dca0fa}.get(address,address)
            real_sha=s.sha
            def fake_sha(data):return '5895dbeef8fe13458720889e318ba64f3fc9c5d3ca03bf01b0adc70eee16c08d' if data==b'table' else real_sha(data)
            with patch.object(s,'source_gate',return_value={'fixedClientInputs':{}}),patch.object(s.subprocess,'check_output',return_value=str(client)),patch.object(s,'pe_info',return_value=((0x1a4e6a40,14344080),[])),patch.object(s,'read_at',side_effect=fake_read),patch.object(s,'offset',side_effect=fake_offset),patch.object(s,'select_pdata',return_value=diag),patch.object(s,'sha',side_effect=fake_sha):
                with self.assertRaisesRegex(ValueError,'site is not E8'):s.collect(dest)
            self.assertNotIn((0x9dc9f90,795),reads)
            self.assertEqual((dest/'candidate-site.bin').read_bytes(),b'wrong')
            self.assertEqual((dest/'aot-slot.bin').read_bytes().hex(),'9017624a01000000f0706d4d01000000')
            self.assertEqual(json.loads((dest/'failure.private.json').read_bytes())['stage'],'pdata-before-body')
            self.assertFalse((dest/'code.bin').exists())

    def test_short_read_and_create_new(self):
        with self.assertRaisesRegex(ValueError,'short read'):s.read_at(io.BytesIO(b'a'),0,2)
        with tempfile.TemporaryDirectory() as d:
            p=Path(d)/'x';s.save(p,b'first')
            with self.assertRaises(FileExistsError):s.save(p,b'second')
            self.assertEqual(p.read_bytes(),b'first')

if __name__=='__main__':unittest.main()
