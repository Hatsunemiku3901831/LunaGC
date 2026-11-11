"""Bounded FindEntry source gate and evidence tests; no target execution."""
import copy
import json
import struct
import unittest
import tempfile
from unittest.mock import patch
from pathlib import Path
import proud_standard_cache_findentry_window as m

class WindowTest(unittest.TestCase):
    def test_caller(self):
        raw=(m.CALLER/'code-663528.bin').read_bytes()
        asm=json.loads((m.CALLER/'asm-663528.private.json').read_bytes())
        m.validate_caller(raw,asm)
        bad=bytearray(raw); bad[0x15d]^=1
        with self.assertRaises(ValueError): m.validate_caller(bytes(bad),asm)
    def test_pdata_unique_exact_and_fixed_extent(self):
        rel=m.ENTRY-m.BASE; record=struct.pack('<III',rel,rel+663,0x1000)
        section=lambda *a:{'raw':0,'begin':m.ENTRY}
        self.assertEqual(m.admit(m.find_pdata(record,m.BASE,0x2000),section,663)['size'],663)
        for b in (b'',record*2,record+struct.pack('<III',rel+1,rel+3,1),struct.pack('<III',rel,rel+662,1)):
            with self.subTest(b=b.hex()),self.assertRaises(ValueError): m.admit(m.find_pdata(b,m.BASE,0x2000),section,10000)
        with self.assertRaises(ValueError): m.admit(m.find_pdata(record,m.BASE,0x2000),section,662)
    def test_decode_boundaries_and_cap(self):
        rows,state=m.decode(b'\x90\xc3'); self.assertEqual(state,'complete')
        self.assertEqual(b''.join(bytes.fromhex(x['hex']) for x in rows),b'\x90\xc3')
        self.assertEqual(m.decode(b'\x0f')[1],'incomplete-decode')
        rows,state=m.decode(b'\x90'*385); self.assertEqual((len(rows),state),(384,'instruction-cap'))
    def test_complete_body_hash_and_short(self):
        with self.assertRaisesRegex(ValueError,'short'): m.body_gate(b'x'*662)
        with self.assertRaisesRegex(ValueError,'hash'): m.body_gate(b'x'*663)
    def test_declarations_detect_changes(self):
        req=json.loads(m.REQUEST.read_bytes()); f=req['typedField']['field']; method=req['aotProvenance']['openGenericMethodDeclaration']
        records=[{'methods':[]} for _ in range(80064)]
        records[80063]={'name':req['typedField']['ownerName'],'fields':[copy.deepcopy(f)],'methods':[]}
        records[1222]={'methods':[copy.deepcopy(method)]}
        m.declaration(records,req)
        records[80063]['fields'][0]['type']['arguments'][0]['kind']='int32'
        with self.assertRaises(ValueError): m.declaration(records,req)
        records[80063]['fields'][0]=copy.deepcopy(f); records[2]['methods']=[{'registeredAddress':hex(m.ENTRY)}]
        with self.assertRaises(ValueError): m.declaration(records,req)

    def test_collect_stops_with_truthful_body_counts(self):
        m.OUT.mkdir(parents=True,exist_ok=True)
        table=bytes(14344080); expected='5895dbeef8fe13458720889e318ba64f3fc9c5d3ca03bf01b0adc70eee16c08d'; real_sha=m.sha
        rel=m.ENTRY-m.BASE; record=struct.pack('<III',rel,rel+663,1)
        diag=m.find_pdata(record,m.BASE,0x1a4e6a40)
        for mode in ('source','boundary','short','hash'):
            with self.subTest(mode=mode),tempfile.TemporaryDirectory(dir=m.OUT) as tmp:
                root=Path(tmp); (root/'YuanShen.exe').write_bytes(b''); dest=root/'out'; dest.mkdir()
                class PE:
                    base=m.BASE; pdata_rva=0x1a4e6a40; pdata_size=14344080; calls=0; meta={'bytesRead':4096}
                    def __init__(self,f): pass
                    def section(self,*args): return {'raw':0,'begin':m.ENTRY}
                    def at(self,va,size,executable=False):
                        if va==self.base+self.pdata_rva: return table,0
                        PE.calls+=1; return b'x'*(662 if mode=='short' else 663),0
                class Base: FilePE=PE
                req={'stockIdentity':{'YuanShen.exe':{'bytes':1000,'sha256':'x'}}}
                gate=ValueError('source failure') if mode=='source' else None
                current={'exact':[],'containing':[],'overlapping':[]} if mode=='boundary' else diag
                with patch.object(m,'source_gate',side_effect=gate,return_value=(req,{},Base)),patch.object(m.subprocess,'check_output',return_value=str(root)),patch.object(m,'check',return_value={}),patch.object(m,'find_pdata',return_value=current),patch.object(m,'sha',side_effect=lambda b:expected if b is table else real_sha(b)):
                    if mode=='source':
                        with self.assertRaises(ValueError): m.collect(dest)
                        result=json.loads((dest/'failure.private.json').read_bytes())
                    else:
                        self.assertEqual(m.collect(dest),2); result=json.loads((dest/'result.private.json').read_bytes())
                expected_count={'source':0,'boundary':0,'short':662,'hash':663}[mode]
                self.assertEqual(result['bodyRead'],expected_count)
                self.assertEqual(PE.calls,0 if expected_count==0 else 1)
                self.assertEqual((dest/'code.bin').exists(),expected_count>0)
                if mode!='source': self.assertEqual(json.loads((dest/'pre-body.private.json').read_bytes())['bodyRead'],0)

if __name__=='__main__': unittest.main()
