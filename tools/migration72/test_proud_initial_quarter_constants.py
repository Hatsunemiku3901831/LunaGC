"""Independent source order, mapping gate and permutation tests."""
import copy
import io
import struct
import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import proud_initial_quarter_constants as mod


class ConstantTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.req=json.loads(mod.REQUEST.read_bytes()); cls.body=mod.BODY.read_bytes(); cls.asm=json.loads(mod.ASM.read_bytes())

    def test_literal_16_load_store_order_and_drift(self):
        expected=[(0x1408b95b7,0x1422502f0,0x1408b95be,0),(0x1408b95c4,0x1439725a0,0x1408b95cb,16),(0x1408b95d1,0x1439725b0,0x1408b95d8,32),(0x1408b95de,0x1439725c0,0x1408b95e5,48),(0x1408b95eb,0x1439725d0,0x1408b95f2,64),(0x1408b95f8,0x1439725e0,0x1408b95ff,80),(0x1408b9553,0x1439725f0,0x1408b9605,96),(0x1408b955b,0x143972600,0x1408b960b,112),(0x1408b9563,0x143972610,0x1408b9611,128),(0x1408b956a,0x143972620,0x1408b961a,144),(0x1408b9571,0x143972630,0x1408b9623,160),(0x1408b9579,0x143972640,0x1408b962c,176),(0x1408b9581,0x143972650,0x1408b9635,192),(0x1408b9589,0x143972660,0x1408b963e,208),(0x1408b9591,0x143972670,0x1408b9647,224),(0x1408b9599,0x143972680,0x1408b9650,240)]
        rows=self.req['originalLoadStoreOrder']; self.assertEqual([(int(r['loadSite'],16),int(r['dataAddress'],16),int(r['storeSite'],16),r['quarterByteOffset']) for r in rows],expected)
        mod.validate_source(self.body,self.asm,rows)
        for mode in ('order','address','width','register','raw'):
            order=copy.deepcopy(rows); asm=copy.deepcopy(self.asm); body=self.body
            if mode=='order': order[0],order[1]=order[1],order[0]
            if mode=='address': order[1]['dataAddress']='0x1439725a1'
            if mode=='width': order[0]['bytes']=15
            if mode=='register': next(x for x in asm['instructions'] if x['address']=='0x1408b9605')['operands']= 'xmmword ptr [r13 + rbx + 0x60], xmm0'
            if mode=='raw': body=bytes([body[0]^1])+body[1:0x117]+bytes([body[0x117]^1])+body[0x118:]
            with self.subTest(mode=mode),self.assertRaises(ValueError): mod.validate_source(body,asm,order)

    def test_permutation_and_duplicates_never_repaired(self):
        identity=bytes(range(256)); result=mod.analyze(identity)
        self.assertTrue(result['fileImageInitialBytesArePermutation']); self.assertTrue(result['identity']); self.assertEqual(result['histogram'],[1]*256)
        rotated=identity[1:]+identity[:1]; self.assertTrue(mod.analyze(rotated)['fileImageInitialBytesArePermutation']); self.assertFalse(mod.analyze(rotated)['identity'])
        broken=identity[:-1]+bytes([0]); result=mod.analyze(broken)
        self.assertFalse(result['fileImageInitialBytesArePermutation']); self.assertEqual(result['duplicates'],[{'byte':0,'count':2}]); self.assertEqual(result['missing'],[255]); self.assertEqual(result['sha256'],mod.sha(broken))
        with self.assertRaises(ValueError): mod.analyze(identity[:-1])

    def test_preferred_base_rejected(self):
        header=bytearray(4096); header[:2]=b'MZ'; struct.pack_into('<I',header,0x3c,0x80); header[0x80:0x84]=b'PE\0\0'
        struct.pack_into('<H',header,0x84,0x8664); struct.pack_into('<H',header,0x98,0x20b); struct.pack_into('<Q',header,0x80+48,0x150000000)
        with self.assertRaisesRegex(ValueError,'PE base'): mod.FilePE(io.BytesIO(header))

    def test_mapping_ambiguity_nonreadable_and_short_file(self):
        pe=mod.FilePE.__new__(mod.FilePE); start=mod.SPANS[0][0]
        pe.sections=[{'begin':start,'end':start+16,'raw':10,'flags':0x40000000}]
        self.assertEqual(pe.mapping(start,16,26)['fileOffset'],10)
        with self.assertRaises(ValueError): pe.mapping(start,16,25)
        pe.sections[0]['flags']=0
        with self.assertRaises(ValueError): pe.mapping(start,16,100)
        pe.sections[0]['flags']=0x40000000; pe.sections.append({'begin':start+15,'end':start+20,'raw':50,'flags':0x40000000})
        with self.assertRaises(ValueError): pe.mapping(start,16,100)

    def test_pre_data_and_exact_two_reads(self):
        mod.OUT.mkdir(parents=True,exist_ok=True)
        for mode in ('success','mapping','stock','source'):
            with self.subTest(mode=mode),tempfile.TemporaryDirectory(dir=mod.OUT) as tmp:
                root=Path(tmp); dest=root/'out'; dest.mkdir(); image=bytearray(512); image[100:116]=bytes(range(16)); image[200:440]=bytes(range(16,256)); (root/'YuanShen.exe').write_bytes(image)
                class PE:
                    meta={'readCalls':2,'bytesRead':4616,'distinctBytes':4096}
                    def __init__(self,f): pass
                    def mapping(self,va,n,file_size):
                        if mode=='mapping': raise ValueError('mapping failure')
                        return {'begin':hex(va),'bytes':n,'fileOffset':100 if n==16 else 200}
                req={'stockIdentity':{'YuanShen.exe':{'bytes':512,'sha256':'x'}},'originalLoadStoreOrder':self.req['originalLoadStoreOrder']}
                with patch.object(mod,'source_gate',side_effect=ValueError('source failure') if mode=='source' else None,return_value=(req,{})),patch.object(mod.subprocess,'check_output',return_value=str(root)),patch.object(mod,'check',side_effect=ValueError('stock failure') if mode=='stock' else None,return_value={}),patch.object(mod,'FilePE',PE):
                    if mode=='success': self.assertEqual(mod.collect(dest),0)
                    else:
                        with self.assertRaises(ValueError): mod.collect(dest)
                report=json.loads((dest/('result.private.json' if mode=='success' else 'failure.private.json')).read_bytes())
                if mode=='success':
                    pre=json.loads((dest/'pre-data.private.json').read_bytes()); self.assertEqual((pre['staticDataRead'],pre['staticDataReadCalls']),(0,0)); self.assertEqual((report['staticDataRead'],report['staticDataReadCalls']),(256,2)); self.assertEqual((dest/'C.bin').read_bytes(),bytes(range(256)))
                    self.assertTrue(report['predicate']['fileImageInitialBytesArePermutation']); self.assertTrue(all(report[k] is None for k in ('current','natural','providerPurity','runtimeConstantsUnmodified','actualEncoded'))); self.assertFalse(report['actualRow'])
                else: self.assertEqual(report['staticDataRead'],0); self.assertEqual(report['staticDataReadCalls'],0); self.assertFalse((dest/'span16.bin').exists())


if __name__=='__main__': unittest.main()
