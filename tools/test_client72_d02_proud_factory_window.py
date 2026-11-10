"""Pure offline guards for the single factory-window mechanical collector."""
import copy
import json
import struct
import unittest
from tools import client72_d02_proud_factory_window as subject


class ProudFactoryWindowTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.manifest, cls.fixed = subject.verify_sources()

    def test_source_closure_and_exact_static_definition(self):
        report = subject.bind_static(self.fixed)
        self.assertEqual((report['typeIndex'], report['vtableDefinition']['count'],
                          report['vtableDefinition']['index'], report['method']['declaration']['index']),
                         (63831, 11, 773626, 529228))
        self.assertFalse(report['naturalVtableObserved'])
        self.assertFalse(report['bodyTaken'])

    def test_each_frozen_source_drift_or_missing_refuses(self):
        files = {subject.ROOT/path:raw for path,raw in self.fixed.items()}
        files[subject.PRIVATE/'frozen-inputs.private.json'] = (subject.PRIVATE/'frozen-inputs.private.json').read_bytes()
        for target in files:
            for missing in (False, True):
                def changed(path):
                    if path == target:
                        if missing:
                            raise FileNotFoundError(path)
                        return b'\x00'+files[path][1:]
                    return files[path]
                with self.subTest(path=target, missing=missing), self.assertRaises((ValueError, FileNotFoundError)):
                    subject.verify_sources(changed)

    def test_unique_exact_and_containing_pdata_required(self):
        base = 0x140000000
        first = struct.pack('<III', subject.ENTRY-base, subject.ENTRY-base+32, 0x1234)
        diagnostic = subject.extent(first, subject.ENTRY, base, 0x1000)
        self.assertEqual(subject.select_extent(diagnostic)['size'], 32)
        cases = [dict(exact=[], containing=[]),
                 dict(exact=diagnostic['exact'], containing=[]),
                 dict(exact=diagnostic['exact'], containing=diagnostic['containing']*2)]
        overlap = struct.pack('<III', subject.ENTRY-base-16, subject.ENTRY-base+16, 0x1234)
        cases.append(subject.extent(first+overlap, subject.ENTRY, base, 0x1000))
        for case in cases:
            with self.subTest(case=case), self.assertRaisesRegex(ValueError, 'pdata'):
                subject.select_extent(case)

    def test_approved_byte_and_instruction_limits_are_enforced(self):
        for size in (-1, 0, 1025, 2048):
            with self.subTest(size=size), self.assertRaisesRegex(ValueError, '1024B'):
                subject.enforce_bound(size)
        subject.enforce_bound(1)
        subject.enforce_bound(1024)
        lines = [dict(address=hex(subject.ENTRY+index), hex='90', mnemonic='nop', operands='') for index in range(257)]
        with self.assertRaisesRegex(ValueError, '256'):
            subject.validate_decode(b'\x90'*257, lines, subject.ENTRY+257)

    def test_wrong_aot_target_cell_or_slot_report_refuses(self):
        cell = bytes.fromhex('d0548a4201000000')
        slot = bytes.fromhex('40bbf04901000000')
        subject.verify_aot(cell, slot)
        for bad_cell,bad_slot in [(b'\0'*8,slot), (cell,(subject.ENTRY+1).to_bytes(8,'little')), (cell,slot[:7])]:
            with self.subTest(cell=bad_cell,slot=bad_slot), self.assertRaisesRegex(ValueError, 'AOT'):
                subject.verify_aot(bad_cell,bad_slot)
        report=dict(aot=dict(slot=hex(subject.SLOT+8),cellHex=cell.hex(),slotHex=slot.hex()))
        with self.assertRaisesRegex(ValueError,'AOT'):
            subject.verify_aot(cell,slot,report)

    def test_decode_requires_original_complete_ordered_bytes(self):
        code=b'\x90\xc3'
        lines=[dict(address=hex(subject.ENTRY),hex='90',mnemonic='nop',operands=''),
               dict(address=hex(subject.ENTRY+1),hex='c3',mnemonic='ret',operands='')]
        subject.validate_decode(code,lines,subject.ENTRY+2)
        cases=[lines[:1], list(reversed(lines)), []]
        corrupt=copy.deepcopy(lines);corrupt[1]['hex']='90';cases.append(corrupt)
        gap=copy.deepcopy(lines);gap[1]['address']=hex(subject.ENTRY+2);cases.append(gap)
        for case in cases:
            with self.subTest(case=case),self.assertRaises(ValueError):
                subject.validate_decode(code,case,subject.ENTRY+2)

    def test_indirect_caller_cannot_be_promoted_to_e8_or_natural(self):
        raw=self.fixed[subject.CALLER_SOURCE]
        self.assertEqual(raw[0x52:0x64],subject.CALLER_SETUP)
        self.assertEqual(raw[0x5a:0x60],bytes.fromhex('ff9018010000'))
        self.assertNotEqual(raw[0x5a],0xe8)
        path=subject.PRIVATE/'factory1'
        if path.exists():
            files={name:(path/name).read_bytes() for name in subject.FILES}
            subject.validate_window(files)
            for mutate in ('caller','natural','typed-return','parser','resource'):
                bad=dict(files);report=json.loads(bad['extraction.private.json'])
                if mutate=='caller':report['caller']['callHex']='e800000000'
                elif mutate=='natural':report['naturalVtableObserved']=True
                elif mutate=='typed-return':report['factoryReturnTyped41105Proven']=True
                elif mutate=='parser':report['parserExecuted']=True
                else:report['resourceReady']=True
                bad['extraction.private.json']=json.dumps(report).encode()
                with self.subTest(mutate=mutate),self.assertRaises(ValueError):
                    subject.validate_window(bad)


if __name__ == '__main__':
    unittest.main()
