"""Synthetic and literal boundary checks for the single-source collector."""
import json
import struct
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import proud_scalar_table_builder_window as mod


class WindowTest(unittest.TestCase):
    def setUp(self):
        self.base=0x140000000; self.relative=mod.ENTRY-self.base
        self.record=struct.pack('<III',self.relative,self.relative+100,0x1000)
        self.section=lambda va,size,executable: {'flags':0x20000000}

    def test_caller_exact_and_drift(self):
        setup=bytes.fromhex('488d0d6cdadffbe8475c9ff7488b0d3063dffb48898170e90000'); site=bytes.fromhex('e8475c9ff7')
        mod.caller_check(setup,site)
        self.assertEqual(mod.SITE+5+struct.unpack('<i',site[1:])[0],0x14146af10)
        for s,t in ((setup+b'x',site),(setup,b'\xe8'+bytes(4)),(bytes(26),site)):
            with self.assertRaises(ValueError): mod.caller_check(s,t)

    def test_pdata_uniqueness_overlap_and_bounds(self):
        valid=mod.find_pdata(self.record,self.base,0x2000)
        self.assertEqual(mod.admit(valid,self.section)['size'],100)
        cases=[b'',self.record*2,self.record+struct.pack('<III',self.relative-1,self.relative+1,1),self.record+struct.pack('<III',self.relative+50,self.relative+120,1),struct.pack('<III',self.relative,self.relative,1)]
        for table in cases:
            with self.subTest(table=table),self.assertRaises(ValueError): mod.admit(mod.find_pdata(table,self.base,0x2000),self.section)
        with self.assertRaises(ValueError): mod.admit(valid,lambda *args: (_ for _ in ()).throw(ValueError('not executable')))

    def test_decode_coverage_index_and_stop(self):
        # RIP-relative indirect CALL; reference is recorded, never dereferenced.
        code=bytes.fromhex('ff15b21f3004c3')
        rows=mod.decode(code)
        self.assertEqual(b''.join(bytes.fromhex(x['hex']) for x in rows),code)
        ix=mod.index(rows); self.assertTrue(ix['edges'][0]['call']); self.assertIsNone(ix['edges'][0]['directTarget']); self.assertEqual(len(ix['ripReferences']),1)
        self.assertFalse(ix['dependentSemanticsFrozen'])
        call=b'\xe8'+struct.pack('<i',0x149a75140-(mod.ENTRY+5))
        hit=mod.index(mod.decode(call))
        self.assertTrue(hit['dependentSemanticsFrozen']); self.assertEqual(hit['refusedEdges'][0]['directTarget'],'0x149a75140')
        with self.assertRaises(ValueError): mod.decode(b'\x0f')
        with self.assertRaisesRegex(ValueError,'instruction budget'): mod.decode(b'\x90'*129)

    def gate_case(self,mode):
        mod.OUT.mkdir(parents=True,exist_ok=True)
        with tempfile.TemporaryDirectory(dir=mod.OUT) as tmp:
            root=Path(tmp); (root/'YuanShen.exe').write_bytes(b''); dest=root/'out'; dest.mkdir()
            setup=bytes.fromhex(mod.SETUP_HEX)
            if mode=='caller': setup=bytes(26)
            size=257 if mode=='budget' else 100
            record=struct.pack('<III',self.relative,self.relative+size,0x1000)
            if mode=='missing': record=b''
            if mode=='duplicate': record=record*2
            if mode=='overlap': record+=struct.pack('<III',self.relative+50,self.relative+150,0x2000)
            class PE:
                base=0x140000000; pdata_rva=0x1a4e6a40; pdata_size=14344080; body_reads=0
                def __init__(self,f): pass
                def section(self,*args):
                    if mode=='nonexec': raise ValueError('non executable')
                    return {}
                def at(self,va,n,executable=False):
                    if va==mod.SETUP: return setup
                    if va==self.base+self.pdata_rva: return record
                    PE.body_reads+=1; return bytes(n)
            real_sha=mod.sha
            def hash_override(b): return '5895dbeef8fe13458720889e318ba64f3fc9c5d3ca03bf01b0adc70eee16c08d' if b is record else real_sha(b)
            with patch.object(mod,'source_gate',return_value=({'fixedClientInputs':{}},{})), patch.object(mod.subprocess,'check_output',return_value=str(root)), patch.object(mod,'FilePE',PE), patch.object(mod,'sha',side_effect=hash_override):
                with self.assertRaises(ValueError): mod.collect(dest)
            report=json.loads((dest/'failure.private.json').read_bytes()); self.assertEqual(report['bodiesRead'],0); self.assertEqual(PE.body_reads,0); self.assertFalse((dest/'code.bin').exists())
            if mode=='budget': self.assertFalse(json.loads((dest/'pre-body.private.json').read_bytes())['budget']['admitted'])

    def test_gate_failures_before_body(self):
        for mode in ('caller','missing','duplicate','overlap','nonexec','budget'):
            with self.subTest(mode=mode): self.gate_case(mode)


if __name__=='__main__': unittest.main()
