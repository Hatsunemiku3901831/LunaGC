"""Independent literal and synthetic gates for the fixed fourteen-byte slice."""
import io
import json
import struct
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import proud_ec8_store_slice_window as mod


def pair(producer=0x140123456,slot=0x145744ec8):
    return bytes.fromhex('488d05')+struct.pack('<i',producer-(mod.BEGIN+7))+bytes.fromhex('488905')+struct.pack('<i',slot-mod.END)


def fake_pe(base=0x140000000,flags=0x20000000,extra=False):
    blob=bytearray(4608); blob[:2]=b'MZ'; struct.pack_into('<I',blob,0x3c,0x80); blob[0x80:0x84]=b'PE\0\0'
    struct.pack_into('<H',blob,0x84,0x8664); struct.pack_into('<H',blob,0x86,2 if extra else 1); struct.pack_into('<H',blob,0x94,0xf0)
    struct.pack_into('<H',blob,0x98,0x20b); struct.pack_into('<Q',blob,0x80+48,base)
    for i in range(2 if extra else 1):
        off=0x80+24+0xf0+i*40; struct.pack_into('<III',blob,off+12,0x1126800,256,4096); struct.pack_into('<I',blob,off+36,flags)
    blob[4096+0x12:4096+0x20]=pair()
    return bytes(blob)


class SliceTest(unittest.TestCase):
    def test_literal_oracle_and_decoder(self):
        raw=pair(); literal=mod.oracle(raw); rows=mod.decode(raw); assessment=mod.assess(raw,rows)
        self.assertTrue(assessment['localBindingProvenUnderFallthrough']); self.assertEqual(literal['producerRegisterNumber'],0); self.assertEqual(literal['storeRegisterNumber'],0)
        self.assertEqual(literal['producerAddress'],'0x140123456'); self.assertEqual(literal['storeAddress'],'0x145744ec8')
        self.assertFalse(assessment['globalBindingProven']); self.assertIsNone(assessment['currentCallbackTarget']); self.assertFalse(assessment['naturalExecution'])
        self.assertEqual(mod.BEGIN+7+int.from_bytes(raw[3:7],'little',signed=True),0x140123456)
        self.assertEqual(mod.END+int.from_bytes(raw[10:14],'little',signed=True),0x145744ec8)

    def test_wrong_forms_are_inconclusive(self):
        raw=pair(); cases=[]
        for pos,value in ((1,0x8b),(0,0x40),(0,0x64),(0,0x67),(9,0x0d),(10,raw[10]^1),(7,0xe9)):
            b=bytearray(raw); b[pos]=value; cases.append(bytes(b))
        cases.extend([raw[:-1],raw+b'\x90',b'\x0f'*14])
        for code in cases:
            with self.subTest(code=code.hex()): self.assertFalse(mod.assess(code,mod.decode(code))['localBindingProvenUnderFallthrough'])

    def test_mapping_limits(self):
        mapping=mod.metadata(io.BytesIO(fake_pe())); self.assertEqual(mapping['sliceFileOffset'],4114); self.assertEqual(mapping['readCalls'],2); self.assertLessEqual(mapping['bytesRead'],8192); self.assertLessEqual(mapping['distinctBytes'],mapping['bytesRead'])
        for blob in (fake_pe(base=0x150000000),fake_pe(flags=0),fake_pe(extra=True)):
            with self.assertRaises(ValueError): mod.metadata(io.BytesIO(blob))

    def test_identity_and_mapping_failure_before_slice(self):
        mod.OUT.mkdir(parents=True,exist_ok=True)
        for mode in ('pin','stock','base','nonexec','ambiguous'):
            with self.subTest(mode=mode), tempfile.TemporaryDirectory(dir=mod.OUT) as tmp:
                root=Path(tmp); dest=root/'out'; dest.mkdir(); base=0x150000000 if mode=='base' else 0x140000000
                (root/'YuanShen.exe').write_bytes(fake_pe(base=base,flags=0 if mode=='nonexec' else 0x20000000,extra=mode=='ambiguous'))
                source=patch.object(mod,'source_gate',side_effect=ValueError('pin') if mode=='pin' else None,return_value=({'stockIdentity':{'YuanShen.exe':{'bytes':1,'sha256':'x'}}},{}))
                with source,patch.object(mod.subprocess,'check_output',return_value=str(root)),patch.object(mod,'check',side_effect=ValueError('stock') if mode=='stock' else None,return_value={}):
                    with self.assertRaises(ValueError): mod.collect(dest)
                report=json.loads((dest/'failure.private.json').read_bytes()); self.assertEqual(report['sliceRead'],0); self.assertEqual(report['sliceReadCalls'],0); self.assertFalse((dest/'slice.bin').exists())


if __name__=='__main__': unittest.main()
