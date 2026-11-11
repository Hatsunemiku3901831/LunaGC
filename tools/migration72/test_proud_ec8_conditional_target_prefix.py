"""Literal control-flow and one-byte-read tests for the bounded prefix."""
import io
import json
import struct
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import proud_ec8_conditional_target_prefix as mod


def fake_pe(base=0x140000000,flags=0x20000000,extra=False):
    blob=bytearray(4608); blob[:2]=b'MZ'; struct.pack_into('<I',blob,0x3c,0x80); blob[0x80:0x84]=b'PE\0\0'
    struct.pack_into('<H',blob,0x84,0x8664); struct.pack_into('<H',blob,0x86,2 if extra else 1); struct.pack_into('<H',blob,0x94,0xf0)
    struct.pack_into('<H',blob,0x98,0x20b); struct.pack_into('<Q',blob,0x80+48,base)
    for i in range(2 if extra else 1):
        off=0x80+24+0xf0+i*40; struct.pack_into('<III',blob,off+12,0x8b9780,256,4096); struct.pack_into('<I',blob,off+36,flags)
    return bytes(blob)


class PrefixTest(unittest.TestCase):
    def collect_synthetic(self,code,limit=None,cs=None):
        calls=[]
        def read(address): calls.append(address); return code[address-mod.ENTRY:address-mod.ENTRY+1]
        bound=len(code) if limit is None else limit
        result=mod.acquire(read,lambda a: a<mod.ENTRY+bound,cs or mod.decoder())
        self.assertEqual(calls,list(range(mod.ENTRY,mod.ENTRY+len(result['raw']))))
        self.assertEqual(len(result['reads']),len(result['raw'])); self.assertTrue(all(x['bytes']==1 for x in result['reads']))
        self.assertEqual(b''.join(bytes.fromhex(x['hex']) for x in result['instructions'])+bytes.fromhex(result['pendingTailHex']),result['raw'])
        return result,calls

    def test_first_transfer_stops_every_form(self):
        cases=[(bytes.fromhex('e804000000'),'0x1408b978a'),(bytes.fromhex('e9fcffffff'),'0x1408b9782'),(bytes.fromhex('ebfe'),'0x1408b9781'),(bytes.fromhex('7504'),'0x1408b9787'),(bytes.fromhex('0f8504000000'),'0x1408b978b'),(bytes.fromhex('c3'),None),(bytes.fromhex('c21000'),None),(bytes.fromhex('ff1504000000'),None)]
        for transfer,target in cases:
            with self.subTest(code=transfer.hex()):
                result,calls=self.collect_synthetic(b'\x90'+transfer+b'\xcc')
                self.assertEqual(result['stopReason'],'first-transfer'); self.assertEqual(result['raw'],b'\x90'+transfer); self.assertEqual(len(calls),1+len(transfer)); self.assertEqual(result['edge']['directTarget'],target)
                if transfer.startswith(bytes.fromhex('ff15')): self.assertEqual(result['edge']['ripOperandAddresses'],['0x1408b978b'])

    def test_multibyte_caps_and_pending(self):
        result,_=self.collect_synthetic(bytes.fromhex('4889d8c3cc')); self.assertEqual(result['raw'].hex(),'4889d8c3'); self.assertEqual([len(bytes.fromhex(x['pendingHex'])) for x in result['attempts']],[1,2,3,1])
        result,_=self.collect_synthetic(b'\x90'*20); self.assertEqual(result['stopReason'],'instruction-cap'); self.assertEqual(len(result['raw']),16)
        result,_=self.collect_synthetic(b'\x66'*15+b'\x90'); self.assertEqual(result['stopReason'],'instruction-buffer-cap'); self.assertEqual(len(bytes.fromhex(result['pendingTailHex'])),15)
        result,_=self.collect_synthetic((b'\x66'*14+b'\x90')*5); self.assertEqual(result['stopReason'],'byte-cap'); self.assertEqual(len(result['raw']),64); self.assertEqual(len(bytes.fromhex(result['pendingTailHex'])),4)
        result,_=self.collect_synthetic(b'\x0f',limit=1); self.assertEqual(result['stopReason'],'mapping-end'); self.assertEqual(result['pendingTailHex'],'0f')
        result,_=self.collect_synthetic(bytes.fromhex('0f0bcc')); self.assertEqual(result['stopReason'],'unsupported-flow'); self.assertEqual(result['raw'].hex(),'0f0b')

    def test_refused_data_edge_stops_immediately(self):
        disp=0x1405336c0-(mod.ENTRY+7)
        result,calls=self.collect_synthetic(bytes.fromhex('488d05')+struct.pack('<i',disp)+b'\x90')
        self.assertEqual(result['stopReason'],'known-refused-dependency'); self.assertTrue(result['dependentSemanticsFrozen']); self.assertEqual(len(calls),7)

    def test_decode_gap_stops(self):
        class Ins:
            address=mod.ENTRY; size=2; bytes=b'\x90\x90'
        class Bad:
            def disasm(self,raw,address,count): return [Ins()]
        result,_=self.collect_synthetic(b'\x90\xcc',cs=Bad()); self.assertEqual(result['stopReason'],'decode-gap-or-trailing'); self.assertEqual(result['raw'],b'\x90')

    def test_anchor_and_mapping(self):
        a={'assessment':{'localBindingProvenUnderFallthrough':True,'producerAddress':'0x1408b9780','globalBindingProven':False,'currentCallbackTarget':None,'naturalExecution':False}}
        raw=bytes.fromhex('488d05672f79ff488905a8e66104'); mod.anchor_check(raw,a)
        with self.assertRaises(ValueError): mod.anchor_check(raw[:-1]+b'\x00',a)
        m=mod.metadata(io.BytesIO(fake_pe())); self.assertEqual(m['readCalls'],2); self.assertLessEqual(m['bytesRead'],8192)
        for blob in (fake_pe(base=0x150000000),fake_pe(flags=0),fake_pe(extra=True)):
            with self.assertRaises(ValueError): mod.metadata(io.BytesIO(blob))

    def test_identity_gate_has_zero_prefix(self):
        mod.OUT.mkdir(parents=True,exist_ok=True)
        for mode in ('pin','stock','base','nonexec','ambiguous'):
            with self.subTest(mode=mode),tempfile.TemporaryDirectory(dir=mod.OUT) as tmp:
                root=Path(tmp); dest=root/'out'; dest.mkdir(); (root/'YuanShen.exe').write_bytes(fake_pe(base=0x150000000 if mode=='base' else 0x140000000,flags=0 if mode=='nonexec' else 0x20000000,extra=mode=='ambiguous'))
                source=patch.object(mod,'source_gate',side_effect=ValueError('pin') if mode=='pin' else None,return_value=({'stockIdentity':{'YuanShen.exe':{'bytes':4608,'sha256':'x'}}},{}))
                with source,patch.object(mod.subprocess,'check_output',return_value=str(root)),patch.object(mod,'check',side_effect=ValueError('stock') if mode=='stock' else None,return_value={}):
                    with self.assertRaises(ValueError): mod.collect(dest)
                report=json.loads((dest/'failure.private.json').read_bytes()); self.assertEqual(report['prefixRead'],0); self.assertFalse((dest/'prefix.bin').exists())


if __name__=='__main__': unittest.main()
