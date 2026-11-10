"""Pure pin and policy tests; no emulator is loaded or executed."""
import contextlib
import io
import json
from pathlib import Path
import struct
import tempfile
import unittest
from unittest.mock import patch

from tools import client72_d02_clear48_owned as owned


class OwnedGates(unittest.TestCase):
    def test_frozen_sources_and_exact_contract(self):
        pins, code = owned.load_sources()
        self.assertEqual(len(code),904); self.assertEqual(len(pins),16)
        audit=json.loads((owned.ROOT/owned.AUDIT).read_bytes())
        self.assertEqual(len(audit['inputPins']),10)
        for name, pin in audit['inputPins'].items(): self.assertEqual(pins[name],pin)
        self.assertEqual(owned.ENTRY+len(code),owned.END)
        self.assertEqual(owned.H % 32,0); self.assertEqual(owned.SP % 16,8)
        self.assertEqual(owned.INITIAL,bytes(range(1,65)))
        self.assertEqual((owned.LIMIT,owned.TIMEOUT_US),(5000,5_000_000))

    def test_source_and_audit_input_drift_rejected(self):
        pins,_=owned.load_sources(); original=owned.digest
        for name in pins:
            raw=(owned.ROOT/name).read_bytes()
            with self.subTest(name=name), patch.object(owned,'digest',side_effect=lambda b: '0'*64 if b == raw else original(b)):
                with self.assertRaises(ValueError): owned.load_sources()

    def test_exact_artificial_controls_and_read_policy(self):
        for name, expected in [('SSE',[(0x56afdd0,4,2),(0x56afdd8,8,48),(0x6d19140,4,0x01fdfd35)]),
                               ('AVX',[(0x56afdd0,4,3),(0x56afdd8,8,47),(0x56afde0,8,48),
                                       (0x5eeeb08,1,0),(0x6d190f4,4,0x01fdfb94)])]:
            controls=owned.case_data(name)
            self.assertEqual([(a-owned.BASE,len(b),int.from_bytes(b,'little')) for a,b,_ in controls],expected)
            for address,raw,pc in controls:
                self.assertTrue(owned.allowed_read(controls,pc,address,len(raw)))
                self.assertFalse(owned.allowed_read(controls,pc,address-1,len(raw)))
                self.assertFalse(owned.allowed_read(controls,pc,address,len(raw)+1))
                self.assertFalse(owned.allowed_read(controls,pc+1,address,len(raw)))
                self.assertFalse(owned.allowed_read(controls,pc,address,0))
            self.assertFalse(owned.allowed_read(controls,0,owned.H,8))
            self.assertFalse(owned.allowed_read(controls,0,owned.SP,8))
        with self.assertRaises(ValueError): owned.case_data('unknown')

    def test_original_pc_and_owned_write_boundaries(self):
        expected={'SSE':[(0x141fdfd35,owned.H+32,16),(0x141fdfd3c,owned.H+48,16),(0x141fdfd43,owned.H+16,16)],
                  'AVX':[(0x141fdfb94,owned.H+32,32),(0x141fdfb9b,owned.H+16,32)]}
        for name, rows in expected.items():
            policy=owned.write_policy(name)
            self.assertEqual([(pc,*span) for pc,span in policy.items()],rows)
            for pc,address,width in rows:
                self.assertTrue(owned.allowed_write(policy,pc,address,width))
                self.assertTrue(owned.allowed_write(policy,pc,address+8,8))
                self.assertFalse(owned.allowed_write(policy,pc,address-1,width))
                self.assertFalse(owned.allowed_write(policy,pc,address,width+1))
                self.assertFalse(owned.allowed_write(policy,pc+1,address,width))
                self.assertFalse(owned.allowed_write(policy,pc,address,0))
            self.assertFalse(owned.allowed_write(policy,rows[0][0],owned.H,8))
        with self.assertRaises(ValueError): owned.write_policy('unknown')

    def test_output_scope_and_exclusive_create(self):
        parent=owned.ROOT/owned.PRIVATE
        with tempfile.TemporaryDirectory(dir=parent) as directory:
            path=Path(directory)/'new.json'
            self.assertEqual(owned.output_path(path),path.resolve())
            path.write_text('preserved',encoding='utf8')
            with self.assertRaises(ValueError): owned.output_path(path)
            self.assertEqual(path.read_text(encoding='utf8'),'preserved')
            with self.assertRaises(ValueError): owned.output_path(owned.ROOT/'outside.json')

    def test_source_gate_failure_preserves_new_output_without_backend(self):
        parent=owned.ROOT/owned.PRIVATE
        with tempfile.TemporaryDirectory(dir=parent) as directory:
            path=Path(directory)/'failure.json'
            with patch.object(owned,'load_sources',side_effect=ValueError('fixture source drift')), \
                 patch.object(owned,'OwnedCase',side_effect=AssertionError('backend forbidden')) as backend:
                with contextlib.redirect_stdout(io.StringIO()):
                    self.assertEqual(owned.main(['--output',str(path)]),1)
                backend.assert_not_called()
            result=json.loads(path.read_text(encoding='utf8'))
            self.assertFalse(result['passed']); self.assertEqual(result['error']['message'],'fixture source drift')
            self.assertEqual(result['cases'],[])
            self.assertFalse(result['permissions']['capturedCopyWriteApproved'])
            original=path.read_bytes()
            with self.assertRaises(ValueError): owned.main(['--output',str(path)])
            self.assertEqual(path.read_bytes(),original)

    def test_unreturned_result_never_validates(self):
        with self.assertRaisesRegex(ValueError,'return'):
            owned.verify_execution({'case':'SSE','logicalWrites':[],'returned':False,'failure':{'reason':'unsupported'},
                'backendError':'unsupported','rax':'0x0','finalRSP':'0x0','originalRet':None,'path':[]})


if __name__=='__main__': unittest.main()
