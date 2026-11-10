"""Artificial byte fixtures only: no captured heap, Windows API or native CPU."""

import struct
import unittest

from tools import client72_d02_tls_inputs as card

MODULE, TEB = 0x1234500000000000, 0x2345600000000000
VECTOR, TLS, CONTEXT = 0x3456700000000000, 0x4567800000000000, 0x5678900000000000
LIST, GC, INST, ARRAY, TYPE, ROOT = [0x6789a00000000000 + n * 0x1000 for n in range(6)]
ORDINARY, COMPARISON = 0x789ab00000000000, 0x89abc00000000000


class ArtificialMemory:
    def __init__(self, index=0):
        self.spans, self.calls = {}, []
        self.faults = {}
        self.put(MODULE + 0x5eef080, struct.pack("<I", index))
        self.qword(TEB + 0x58, VECTOR)
        self.qword(VECTOR + index * 8, TLS)
        self.qword(TLS + 0x19d0, CONTEXT)
        self.qword(MODULE + 0x588ca20, LIST)
        header = bytearray(208)
        struct.pack_into("<I", header, 0x18, 21 << 16)
        struct.pack_into("<Q", header, 0x68, GC)
        struct.pack_into("<I", header, 0xb4, 32)
        header[0xc8], header[0xca] = 1, 0x31
        self.put(LIST, header)
        self.put(GC, struct.pack("<I4xQQQ", 1255, INST, 0, LIST))
        self.put(INST, struct.pack("<I4xQ", 1, ARRAY))
        self.qword(ARRAY, TYPE)
        self.put(TYPE, struct.pack("<QI4x", 319, 9 << 16))
        self.qword(MODULE + 0x5bc8f18, ROOT)
        self.qword(ROOT + 1255 * 8, ORDINARY)
        self.qword(MODULE + 0x5bc96f8, COMPARISON)

    def put(self, address, data):
        self.spans[address] = bytes(data)

    def qword(self, address, value):
        self.put(address, struct.pack("<Q", value))

    def read(self, address, width):
        self.calls.append((address, width))
        ordinal = len(self.calls)
        if ordinal in self.faults:
            failure = self.faults[ordinal]
            if isinstance(failure, OSError):
                raise failure
            return failure
        data = self.spans.get(address, b"")
        return data[:width], None if len(data) >= width else {"kind": "artificial-missing-span"}


class TLSInputsArtificialTests(unittest.TestCase):
    def test_full_zero_index_13_340_and_unified_final_26_680(self):
        memory = ArtificialMemory()
        result = card.collect(memory.read, MODULE, TEB)
        self.assertEqual(result["status"], "candidate")
        self.assertEqual((result["budget"]["firstPassReads"], result["budget"]["firstPassBytes"]), (13, 340))
        self.assertEqual((result["budget"]["reads"], result["budget"]["requestedBytes"]), (26, 680))
        self.assertEqual([width for _, width in memory.calls[:13]], [4, 8, 8, 8, 8, 208, 32, 16, 8, 16, 8, 8, 8])
        self.assertEqual(memory.calls[:13], memory.calls[13:])
        self.assertEqual(result["tls"], {"index": 0, "vector": VECTOR, "moduleTLS": TLS, "context": CONTEXT})
        self.assertEqual(result["r1"]["ordinarySlot1255"], ORDINARY)
        self.assertNotEqual(result["r1"]["ordinarySlot1255"], result["r1"]["listClass"])
        self.assertEqual(result["inputConsistency"]["verifiedSpans"], 13)
        self.assertFalse(result["naturalAvatarAllocatorThreadObserved"])
        self.assertFalse(result["canReplayPop"])

    def test_nonzero_index_and_actual_flags_size_remain_observations(self):
        memory = ArtificialMemory(index=31)
        header = bytearray(memory.spans[LIST])
        header[0xc8], header[0xca] = 0, 0x08
        struct.pack_into("<I", header, 0xb4, 96)
        memory.put(LIST, header)
        result = card.collect(memory.read, MODULE, TEB)
        self.assertEqual(result["status"], "candidate")
        self.assertEqual((result["r1"]["c8Observed"], result["r1"]["caObserved"], result["r1"]["sizeB4Observed"]), (0, 8, 96))
        self.assertEqual(memory.calls[2], (VECTOR + 31 * 8, 8))

    def test_null_pointer_stops_expansion_and_repeats_successful_spans_only(self):
        for address, first_reads in ((TEB + 0x58, 2), (VECTOR, 3), (TLS + 0x19d0, 4),
                                     (MODULE + 0x588ca20, 5), (ROOT + 1255 * 8, 12)):
            with self.subTest(address=hex(address)):
                memory = ArtificialMemory()
                memory.qword(address, 0)
                result = card.collect(memory.read, MODULE, TEB)
                self.assertEqual(result["status"], "partial")
                self.assertEqual(result["budget"]["firstPassReads"], first_reads)
                self.assertEqual(len(memory.calls), first_reads * 2)
                self.assertEqual(result["records"][first_reads-1]["bytesHex"], "00" * 8)
                self.assertEqual(result["inputConsistency"]["status"], "repeated-unchanged")

    def test_short_error_and_os_error_keep_raw_and_never_retry_failed_span(self):
        for fault in ((b"\x12\x34", None), (b"\x12", {"winerror": 299}), OSError(5, "artificial")):
            with self.subTest(fault=fault):
                memory = ArtificialMemory()
                memory.faults[3] = fault
                result = card.collect(memory.read, MODULE, TEB)
                self.assertEqual(result["status"], "partial")
                self.assertEqual(result["budget"]["firstPassReads"], 3)
                self.assertEqual(len(memory.calls), 5)
                self.assertEqual(memory.calls.count((VECTOR, 8)), 1)
                self.assertIsNotNone(result["records"][2]["error"])

    def test_definition_method_backref_count_kind_byref_failures(self):
        failures = [(GC, struct.pack("<I4xQQQ", 1256, INST, 0, LIST)),
                    (GC, struct.pack("<I4xQQQ", 1255, INST, 1, LIST)),
                    (GC, struct.pack("<I4xQQQ", 1255, INST, 0, ORDINARY)),
                    (INST, struct.pack("<I4xQ", 2, ARRAY)),
                    (TYPE, struct.pack("<QI4x", 319, 11 << 16)),
                    (TYPE, struct.pack("<QI4x", 319, (9 << 16) | 0x40000000))]
        for address, data in failures:
            with self.subTest(data=data.hex()):
                memory = ArtificialMemory()
                memory.put(address, data)
                result = card.collect(memory.read, MODULE, TEB)
                self.assertEqual(result["status"], "partial")
                self.assertLess(result["budget"]["firstPassReads"], 13)

    def test_final_drift_or_failure_stops_at_first_failure(self):
        for fault in ((struct.pack("<I", 1), None), (b"", {"winerror": 5})):
            memory = ArtificialMemory()
            memory.faults[14] = fault
            result = card.collect(memory.read, MODULE, TEB)
            self.assertEqual(result["status"], "partial")
            self.assertEqual(len(memory.calls), 14)
            self.assertEqual(result["inputConsistency"]["verifiedSpans"], 0)
            self.assertEqual(result["records"][13]["bytesHex"], fault[0].hex())

    def test_uint64_overflow_is_recorded_without_backend_read(self):
        memory = ArtificialMemory()
        memory.qword(TEB + 0x58, card.U64 - 3)
        result = card.collect(memory.read, MODULE, TEB)
        self.assertEqual(result["status"], "partial")
        invalid = result["records"][2]
        self.assertFalse(invalid["backendCalled"])
        self.assertEqual(invalid["error"]["kind"], "address-outside-uint64")
        self.assertLessEqual(result["budget"]["reads"], 26)
        self.assertLessEqual(result["budget"]["requestedBytes"], 680)

    def test_first_read_and_single_read_caps_reject_before_callback(self):
        memory = ArtificialMemory()
        ledger = card.ReadLedger(memory.read, lambda: "artificial-clock")
        for _ in range(13):
            ledger.get("artificial-index", MODULE + 0x5eef080, 4)
        with self.assertRaises(card.ReadStop):
            ledger.get("fourteenth-first-read", MODULE + 0x5eef080, 4)
        self.assertEqual(len(memory.calls), 13)
        with self.assertRaises(card.ReadStop):
            card.ReadLedger(memory.read, lambda: "artificial-clock").get("oversized", LIST, 209)
        self.assertEqual(len(memory.calls), 13)


if __name__ == "__main__":
    unittest.main()
