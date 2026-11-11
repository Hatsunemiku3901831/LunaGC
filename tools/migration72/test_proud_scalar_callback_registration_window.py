"""Literal and synthetic tests of the registration-source boundary."""
import json
import struct
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import proud_scalar_callback_registration_window as mod


class RegistrationWindowTest(unittest.TestCase):
    def setUp(self):
        self.base=0x140000000; self.relative=mod.ENTRY-self.base
        self.record=struct.pack('<III',self.relative,self.relative+200,0x1000)
        self.section=lambda *args: {'flags':0x20000000}

    def test_pdata_missing_duplicate_overlap_and_boundary(self):
        self.assertEqual(mod.admit(mod.find_pdata(self.record,self.base,0x2000),self.section)['size'],200)
        cases=[b'',self.record*2,self.record+struct.pack('<III',self.relative-1,self.relative+1,2),self.record+struct.pack('<III',self.relative+100,self.relative+210,2),struct.pack('<III',self.relative,self.relative,1)]
        for table in cases:
            with self.subTest(table=table),self.assertRaises(ValueError): mod.admit(mod.find_pdata(table,self.base,0x2000),self.section)
        with self.assertRaises(ValueError): mod.admit(mod.find_pdata(self.record,self.base,0x2000),lambda *args: (_ for _ in ()).throw(ValueError('non-executable')))

    def test_independent_literal_rip_and_source_register(self):
        # mov rax, 0x12345678; mov [rip+disp32], rax; ret
        prefix=bytes.fromhex('48c7c078563412'); site=mod.ENTRY+len(prefix); disp=0x145744ec8-(site+7)
        store=bytes.fromhex('488905')+struct.pack('<i',disp); code=prefix+store+b'\xc3'
        rows=mod.decode(code); self.assertEqual(b''.join(bytes.fromhex(x['hex']) for x in rows),code)
        self.assertEqual(site+7+int.from_bytes(store[-4:],'little',signed=True),0x145744ec8)
        hit=mod.index(rows)['ec8Stores'][0]; self.assertEqual(hit['instruction']['operands'][1]['name'],'rax')
        self.assertEqual(hit['precedingInstructions'][-1]['operands'][1]['value'],'0x12345678')
        self.assertFalse(mod.index(rows)['assignedValueProven'])
        self.assertEqual(mod.index(mod.decode(b'\xc3'))['ec8StoreStatus'],'not-found')
        with self.assertRaises(ValueError): mod.decode(b'\x0f')
        with self.assertRaisesRegex(ValueError,'instruction budget'): mod.decode(b'\x90'*65)

    def test_known_refusal_site_is_only_recorded(self):
        code=b'\xe8'+struct.pack('<i',0x149a75140-(mod.ENTRY+5)); ix=mod.index(mod.decode(code))
        self.assertTrue(ix['dependentSemanticsFrozen']); self.assertEqual(ix['refusedEdges'][0]['directTarget'],'0x149a75140')

    def gate_case(self,mode):
        mod.OUT.mkdir(parents=True,exist_ok=True)
        with tempfile.TemporaryDirectory(dir=mod.OUT) as tmp:
            root=Path(tmp); (root/'YuanShen.exe').write_bytes(b''); dest=root/'out'; dest.mkdir()
            record=struct.pack('<III',self.relative,self.relative+(321 if mode=='budget' else 100),0x1000)
            if mode=='missing': record=b''
            if mode=='duplicate': record=record*2
            if mode=='overlap': record+=struct.pack('<III',self.relative+50,self.relative+120,0x2000)
            class PE:
                base=0x140000000; pdata_rva=0x1a4e6a40; pdata_size=14344080; body_reads=0
                def __init__(self,f): pass
                def section(self,*args):
                    if mode=='nonexec': raise ValueError('not executable')
                    return {}
                def at(self,va,n,executable=False):
                    if va==self.base+self.pdata_rva: return record
                    PE.body_reads+=1; return bytes(n)
            real_sha=mod.sha
            def sha_override(b): return '5895dbeef8fe13458720889e318ba64f3fc9c5d3ca03bf01b0adc70eee16c08d' if b is record else real_sha(b)
            with patch.object(mod,'source_gate',return_value=({'stockIdentity':{}},{})),patch.object(mod.subprocess,'check_output',return_value=str(root)),patch.object(mod,'FilePE',PE),patch.object(mod,'sha',side_effect=sha_override):
                if mode=='nonexec':
                    with self.assertRaises(ValueError): mod.collect(dest)
                    report=json.loads((dest/'failure.private.json').read_bytes())
                else:
                    self.assertEqual(mod.collect(dest),2); report=json.loads((dest/'result.private.json').read_bytes())
            self.assertEqual(PE.body_reads,0); self.assertEqual(report['bodiesRead'],0); self.assertFalse((dest/'code.bin').exists())
            if mode in ('missing','duplicate','overlap'): self.assertEqual(report['stage'],'boundary-stop')
            if mode=='budget': self.assertEqual(report['stage'],'budget-stop')

    def test_gate_failures_never_read_body(self):
        for mode in ('missing','duplicate','overlap','nonexec','budget'):
            with self.subTest(mode=mode): self.gate_case(mode)


if __name__=='__main__': unittest.main()
