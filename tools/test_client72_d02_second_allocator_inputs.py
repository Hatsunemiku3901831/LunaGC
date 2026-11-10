"""Artificial read graphs only; no native execution or live memory."""

from copy import deepcopy
from pathlib import Path
import struct
import unittest
from unittest.mock import patch

from tools import client72_d02_second_allocator_inputs as second
from tools import client72_d02_tls_inputs as core
from tools.test_client72_d02_tls_inputs import MODULE, TEB, CONTEXT
from tools.test_client72_d02_avatar_block_inputs import RECORD, HEAD, NEXT, THREAD_STATE
from tools.test_client72_d02_avatar_ctor_inputs import ArtificialCtorMemory, K2

R2, H2, U, P, D = [0xdef0000000000000 + n * 0x1000 for n in range(5)]


class SecondMemory(ArtificialCtorMemory):
    def __init__(self, length1=208, length2=208, *, same=False, size=56, shared=False):
        super().__init__(length1, shared=shared, request=32 if same else size)
        if same:
            raw = bytearray(self.spans[HEAD])
            struct.pack_into('<Q', raw, 0, NEXT)
            self.put(HEAD, raw)
            self.put(NEXT, bytes([3, 4, 5, 6, 7, 8, 9, 10, 0]) + bytes(length1 - 9))
        else:
            self.qword(CONTEXT + 0x68, R2)
            self.qword(R2 + 0x18, length2)
            self.qword(R2 + 0x20, H2)
            self.put(R2 + 0x10, struct.pack('<H', 0xffff))
            self.put(H2, bytes([7, 6, 5, 4, 3, 2, 1, 0, 0]) + bytes(max(length2, 9) - 9))
        self.qword(MODULE + 0x594fd78, U)
        self.qword(U + 0x38, P)
        self.qword(P, D)


class SecondTests(unittest.TestCase):
    def run_graph(self, mem):
        return second.collect(mem.read, MODULE, TEB, now=lambda: 'fixed')

    def test_proven_first_clear16_keeps_original_flags_and_later_frontiers(self):
        mem = SecondMemory(length1=32, length2=56)
        raw = bytearray(mem.spans[HEAD]); raw[8] = 8; mem.put(HEAD, raw)
        initial = deepcopy(mem.spans)
        result = self.run_graph(mem)
        self.assertEqual(result['status'], 'candidate')
        self.assertTrue(result['allocationInputs']['knownClear16Branch'])
        self.assertEqual(result['allocationInputs']['oldFlags'], 8)
        self.assertEqual(mem.spans, initial)
        self.assertEqual(result['budget']['reads'], 88)
        mem = SecondMemory(length1=32, length2=56)
        raw = bytearray(mem.spans[HEAD]); raw[8] = 8; mem.put(HEAD, raw)
        mem.put(THREAD_STATE + 0xa0, b'\x01')
        result = self.run_graph(mem)
        self.assertEqual(result['status'], 'partial')
        self.assertEqual(result['allocationInputs']['potentialUnreviewedEdge']['site'], '0x140563e01')
        self.assertNotIn((MODULE + 0x5897cf0, 8), mem.calls)

    def test_distinct_max_and_same_record_exact_budgets(self):
        for same, expected in ((False, (44, 1184, 88, 2368)), (True, (40, 1158, 80, 2316))):
            mem = SecondMemory(same=same)
            initial = deepcopy(mem.spans)
            result = self.run_graph(mem)
            self.assertEqual(result['status'], 'candidate')
            budget = result['budget']
            self.assertEqual(tuple(budget[k] for k in ('firstPassReads', 'firstPassBytes', 'reads', 'requestedBytes')), expected)
            self.assertEqual(mem.calls[:expected[0]], mem.calls[expected[0]:])
            self.assertEqual(mem.spans, initial)
            self.assertNotIn((D, 8), mem.calls)
            self.assertEqual(result['ctorInputs']['default']['value'], D)
            self.assertFalse(result['ctorInputs']['nativeCPUExecuted'])
            self.assertFalse(result['ctorInputs']['wholeConstructorComplete'])
            self.assertEqual(result['inputConsistency']['verifiedSpans'], expected[0])

    def test_every_first_and_final_failure_and_drift(self):
        for same in (False, True):
            self.check_every_read_failure(same)

    def check_every_read_failure(self, same):
        baseline = self.run_graph(SecondMemory(same=same))
        count = baseline['budget']['firstPassReads']
        for n, row in enumerate(baseline['records'][:count], 1):
            for fault in ((b'\x12', {'kind': 'failure'}), (b'', None)):
                mem = SecondMemory(same=same); mem.faults[n] = fault
                result = self.run_graph(mem)
                self.assertEqual(result['status'], 'partial', n)
                self.assertEqual(result['budget']['firstPassReads'], n)
                self.assertEqual(result['records'][n-1]['bytesHex'], fault[0].hex())
            raw = bytearray.fromhex(row['bytesHex']); raw[0] ^= 1
            for fault in ((bytes(raw), None), (b'', {'kind': 'failure'})):
                mem = SecondMemory(same=same); mem.faults[count+n] = fault
                result = self.run_graph(mem)
                self.assertEqual(result['status'], 'partial', n)
                self.assertEqual(len(mem.calls), count+n)
                self.assertEqual(result['inputConsistency']['status'], 'partial')

    def test_null_unsupported_alias_and_default_frontiers(self):
        cases = [(CONTEXT + 0x68, 0, 'record-null'), (R2 + 0x18, 55, 'length-unsupported'),
                 (R2 + 0x18, 209, 'length-unsupported'), (R2 + 0x18, core.U64, 'length-unsupported'),
                 (R2 + 0x20, 0, 'head-null'), (R2 + 0x20, HEAD, 'head-overlap'),
                 (R2 + 0x20, HEAD + 1, 'head-overlap'), (CONTEXT + 0x68, RECORD, 'different-bin'),
                 (MODULE + 0x594fd78, 0, 'usage-null'), (U + 0x38, 0, 'pointer-null'), (P, 0, 'cold-unclosed')]
        for address, value, failure in cases:
            mem = SecondMemory(); mem.qword(address, value)
            result = self.run_graph(mem)
            self.assertEqual(result['status'], 'partial')
            self.assertIn(failure, result['failure'])
            self.assertNotIn((0, 8), mem.calls)
        for size in (0, 209, 0x8001):
            mem = SecondMemory(size=size)
            result = self.run_graph(mem)
            self.assertEqual(result['status'], 'partial')
            self.assertNotIn((CONTEXT + 0x68, 8), mem.calls)
        mem = SecondMemory(same=True); mem.qword(HEAD, 0)
        self.assertEqual(self.run_graph(mem)['status'], 'partial')

    def test_same_record_requires_length_and_nonoverlap(self):
        for length, nxt in ((55, NEXT), (56, 0), (56, HEAD), (56, HEAD+1)):
            mem = SecondMemory(length1=length, same=True)
            raw = bytearray(mem.spans[HEAD]); struct.pack_into('<Q', raw, 0, nxt); mem.put(HEAD, raw)
            self.assertEqual(self.run_graph(mem)['status'], 'partial')
        mem = SecondMemory(length1=56, same=True)
        result = self.run_graph(mem)
        self.assertEqual(result['status'], 'candidate')
        self.assertEqual(result['ctorInputs']['second']['nextPointerObserved'], 0x0a09080706050403)
        self.assertNotIn((0x0a09080706050403, 8), mem.calls)

    def test_request_boundaries_and_overlap_mismatch(self):
        for size in (1, 16, 32, 33, 56, 207, 208):
            mem = SecondMemory(size=size)
            z = 64 - (size | 0x80).bit_length()
            index = (((size - 1) >> ((60-z) & 63)) - 8*z + 0x1c1) & 0xffffffff
            if index == 2:
                # The first record cell is already fixed to R1.
                mem = SecondMemory(same=True)
                header = bytearray(mem.spans[K2]); struct.pack_into('<H', header, 0xb4, size); mem.put(K2, header)
            else:
                mem.qword(CONTEXT + 0x28 + index * 16, R2)
            result = self.run_graph(mem)
            self.assertEqual(result['status'], 'candidate', size)
            self.assertEqual(result['ctorInputs']['second']['requestU16'], size)
        mem = SecondMemory()
        # Different-width overlap must agree byte for byte with an earlier span.
        mem.qword(R2 + 0x20, HEAD + 208)
        mem.put(HEAD + 208, bytes(208))
        result = self.run_graph(mem)
        self.assertEqual(result['status'], 'candidate')
        mem = SecondMemory()
        mem.qword(MODULE + 0x594fd78, H2)
        mem.qword(H2 + 0x38, P)
        result = self.run_graph(mem)
        self.assertIn('overlap-drift', result['failure'])
        self.assertNotIn((P, 8), mem.calls)

    def test_minimum_lengths_untagged_and_64bit_values(self):
        mem = SecondMemory(length1=32, length2=56)
        raw = bytearray(mem.spans[K2]); raw[0xca] = 0x60; mem.put(K2, raw)
        mem.qword(P, core.U64)
        result = self.run_graph(mem)
        self.assertEqual(result['status'], 'candidate')
        self.assertEqual(result['ctorInputs']['default']['value'], core.U64)
        self.assertEqual(result['ctorInputs']['hashSet']['allocationInterpretation']['tagBitUsed'], 0)
        self.assertEqual((result['budget']['firstPassReads'], result['budget']['firstPassBytes']), (44, 856))
        self.assertIn((H2, 56), mem.calls)

    def test_unknown_edges_stop_and_shared_class_spans_deduplicate(self):
        mem = SecondMemory(shared=True)
        result = self.run_graph(mem)
        self.assertEqual(result['status'], 'candidate')
        self.assertEqual(result['budget']['firstPassReads'], 41)
        for which in ('first', 'second', 'ca', 'c7'):
            mem = SecondMemory()
            if which in ('first', 'second'):
                addr = HEAD if which == 'first' else H2
                raw = bytearray(mem.spans[addr]); raw[8] = 8; mem.put(addr, raw)
            else:
                raw = bytearray(mem.spans[K2]); raw[0xca if which == 'ca' else 0xc7] = 0x63 if which == 'ca' else 0; mem.put(K2, raw)
            result = self.run_graph(mem)
            self.assertEqual(result['status'], 'partial')
            self.assertNotIn((MODULE + 0x594fd78, 8), mem.calls)

    def test_source_gate_and_registry(self):
        proof = second.verify_sources()
        self.assertEqual(len(proof['auditedSecondInputPins']), 29)
        old = core.READ_PROFILES.copy()
        self.run_graph(SecondMemory())
        self.assertEqual(core.READ_PROFILES, old)
        original = second.ctor._file_sha256
        target = core.ROOT / second.SPEC
        with patch.object(second.ctor, '_file_sha256', side_effect=lambda path: '0'*64 if path == target else original(path)):
            with self.assertRaises(ValueError): second.verify_sources()


if __name__ == '__main__':
    unittest.main()
