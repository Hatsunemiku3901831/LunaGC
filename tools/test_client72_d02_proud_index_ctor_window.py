"""Offline identity, budget and provenance guards for the single index .cctor."""
import copy
import json
import struct
import unittest
from tools import client72_d02_proud_index_ctor_window as subject


class ProudIndexCtorWindowTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.manifest, cls.fixed = subject.verify_sources()
        cls.prior = json.loads(cls.fixed[subject.PRIOR])['selectedDeclarations']

    def test_exact_field_type_and_unique_static_constructor(self):
        identity = subject.static_identity(self.fixed)
        self.assertEqual((identity['ownerIndex'], identity['field']['index'], identity['field']['rawOffset'],
                          identity['method']['index'], identity['method']['name'], identity['method']['parameterCount']),
                         (80063, 400190, '0x4021f58', 663526, '.cctor', 0))
        self.assertTrue(identity['initializationCandidateOnly'])
        self.assertEqual(identity['field']['type']['instanceIndex'], 5071)

    def test_each_source_drift_or_missing_refuses(self):
        files = {subject.ROOT/path:raw for path,raw in self.fixed.items()}
        files[subject.PRIVATE/'frozen-inputs.private.json'] = (subject.PRIVATE/'frozen-inputs.private.json').read_bytes()
        for target in files:
            for missing in (False, True):
                def changed(path):
                    if path == target:
                        if missing:
                            raise FileNotFoundError(path)
                        return bytes([files[path][0]^1])+files[path][1:]
                    return files[path]
                with self.subTest(path=target, missing=missing), self.assertRaises((ValueError, FileNotFoundError)):
                    subject.verify_sources(changed)

    def test_wrong_field_tuple_or_duplicate_cctor_refuses(self):
        owner = dict(index=subject.OWNER, name='owned-test-projection', fields=[self.prior['rowIndexField']],
                     methods=[self.prior['indexOwnerStaticConstructorCandidate']])
        subject.bind_identity([owner], dict(methods=[]), self.prior)
        for mutation in ('field', 'tuple', 'duplicate', 'signature'):
            records = copy.deepcopy([owner]); prior = copy.deepcopy(self.prior); signature = dict(methods=[])
            if mutation == 'field':
                records[0]['fields'][0]['rawOffset']='0x4021f60'
            elif mutation == 'tuple':
                records[0]['fields'][0]['type']['instanceIndex']=5072
                prior['rowIndexField']=records[0]['fields'][0]
            elif mutation == 'duplicate':
                records[0]['methods'].append(copy.deepcopy(records[0]['methods'][0]))
            else:
                signature['methods']=[dict(index=subject.METHOD)]
            with self.subTest(mutation=mutation),self.assertRaises(ValueError):
                subject.bind_identity(records,signature,prior)

    def test_unique_pdata_and_approved_byte_instruction_limits(self):
        base = 0x140000000
        raw = struct.pack('<III', subject.ENTRY-base, subject.ENTRY-base+32, 0x1234)
        diagnostic = subject.extent(raw, subject.ENTRY, base, 0x1000)
        self.assertEqual(subject.select_extent(diagnostic)['size'],32)
        overlap = struct.pack('<III', subject.ENTRY-base-16, subject.ENTRY-base+16, 0x1234)
        for case in [dict(exact=[],containing=[]), dict(exact=diagnostic['exact'],containing=[]),
                     dict(exact=diagnostic['exact'],containing=diagnostic['containing']*2),
                     subject.extent(raw+overlap,subject.ENTRY,base,0x1000)]:
            with self.subTest(case=case),self.assertRaisesRegex(ValueError,'pdata'):
                subject.select_extent(case)
        for size in (-1,0,2049,8192):
            with self.subTest(size=size),self.assertRaisesRegex(ValueError,'2048B'):
                subject.enforce_bound(size)
        subject.enforce_bound(2048)
        lines=[dict(address=hex(subject.ENTRY+i),hex='90') for i in range(513)]
        with self.assertRaisesRegex(ValueError,'512'):
            subject.validate_decode(b'\x90'*513,lines,subject.ENTRY+513)

    def test_wrong_aot_target_cell_or_slot_report_refuses(self):
        cell = bytes.fromhex('d0548a4201000000')
        slot = bytes.fromhex('c0db7a4d01000000')
        subject.verify_aot(cell,slot)
        for bad_cell,bad_slot in [(b'\0'*8,slot),(cell,(subject.ENTRY+1).to_bytes(8,'little')),(cell,slot[:7])]:
            with self.subTest(cell=bad_cell,slot=bad_slot),self.assertRaisesRegex(ValueError,'AOT'):
                subject.verify_aot(bad_cell,bad_slot)
        report=dict(aot=dict(slot=hex(subject.SLOT+8),cellHex=cell.hex(),slotHex=slot.hex()))
        with self.assertRaisesRegex(ValueError,'AOT'):
            subject.verify_aot(cell,slot,report)

    def test_decode_requires_complete_original_ordered_bytes(self):
        code=b'\x90\xc3'
        lines=[dict(address=hex(subject.ENTRY),hex='90'),dict(address=hex(subject.ENTRY+1),hex='c3')]
        subject.validate_decode(code,lines,subject.ENTRY+2)
        wrong=copy.deepcopy(lines);wrong[1]['hex']='90'
        gap=copy.deepcopy(lines);gap[1]['address']=hex(subject.ENTRY+2)
        for case in (lines[:1],list(reversed(lines)),[],wrong,gap):
            with self.subTest(case=case),self.assertRaises(ValueError):
                subject.validate_decode(code,case,subject.ENTRY+2)

    def test_candidate_is_not_forged_caller_or_closed_producer(self):
        folder=subject.PRIVATE/'ctor1'
        if folder.exists():
            files={name:(folder/name).read_bytes() for name in subject.FILES}
            report=subject.validate_window(files)
            self.assertNotIn('caller',report)
            for mutation in ('caller','indexProducerClosed','field400190StoreProven','closedAotTuplePlanExecuted','parserExecuted','resourceReady'):
                bad=dict(files);changed=copy.deepcopy(report)
                if mutation=='caller':changed['caller']=dict(hex='e800000000')
                else:changed[mutation]=True
                bad['extraction.private.json']=json.dumps(changed).encode()
                with self.subTest(mutation=mutation),self.assertRaises(ValueError):
                    subject.validate_window(bad)
        else:
            self.assertIn('no E8 caller asserted',subject.static_identity(self.fixed)['callerEvidence'])


if __name__ == '__main__':
    unittest.main()
