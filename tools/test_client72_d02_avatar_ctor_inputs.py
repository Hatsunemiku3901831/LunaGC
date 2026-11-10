"""Artificial ctor-value/class graphs; no natural heap, native CPU or writes."""

from copy import deepcopy
from contextlib import ExitStack
from pathlib import Path
import struct
import sys
from types import ModuleType
import unittest
from unittest.mock import patch

import tools
from tools import client72_d02_tls_inputs as core
from tools import client72_d02_tls_pool_inputs as pool
from tools import client72_d02_avatar_block_inputs as head
from tools import client72_d02_avatar_ctor_inputs as ctor
from tools import observe_client72_tls_inputs as adapter
from tools.test_client72_d02_tls_inputs import MODULE, TEB, CONTEXT, ROOT, INST
from tools.test_client72_d02_avatar_block_inputs import ArtificialBlockMemory
from tools.test_observe_client72_tls_inputs import ArtificialBackend, epoch

K2, GC2, INST2, ARGS2, TYPE2, ORDINARY2, ITEMS = [0xcdef000000000000 + n * 0x1000 for n in range(7)]
BEFORE = core.ROOT / core.PRIVATE / "avatar-ctor-implementation/source-before"


class ArtificialCtorMemory(ArtificialBlockMemory):
    def __init__(self, length=32, *, shared=False, items=ITEMS, request=56, ca=0x62, c7=1, c8=0, low3=3):
        super().__init__(length)
        self.qword(MODULE + 0x5b74628, items)
        self.qword(MODULE + 0x5897cf0, K2)
        raw = bytearray(208)
        struct.pack_into("<I", raw, 8, 0x12345000 | low3)
        struct.pack_into("<I", raw, 0x18, 21 << 16)
        struct.pack_into("<Q", raw, 0x68, GC2)
        struct.pack_into("<H", raw, 0xb4, request)
        raw[0xc7], raw[0xc8], raw[0xca] = c7, c8, ca
        self.put(K2, raw)
        self.put(GC2, struct.pack("<I4xQQQ", 2147, INST if shared else INST2, 0, K2))
        if not shared:
            self.put(INST2, struct.pack("<I4xQ", 1, ARGS2))
            self.qword(ARGS2, TYPE2)
            self.put(TYPE2, struct.pack("<QI4x", core.U64 - 13, 9 << 16))
        self.qword(ROOT + 2147 * 8, ORDINARY2)


class CtorArtificialTests(unittest.TestCase):
    def test_worst_case_l32_l208_and_single_unified_final(self):
        for length, first_bytes, total_bytes in ((32, 750, 1500), (208, 926, 1852)):
            memory = ArtificialCtorMemory(length)
            original = deepcopy(memory.spans)
            result = ctor.collect(memory.read, MODULE, TEB)
            self.assertEqual(result["status"], "candidate")
            self.assertEqual((result["budget"]["firstPassReads"], result["budget"]["firstPassBytes"]), (36, first_bytes))
            self.assertEqual((result["budget"]["reads"], result["budget"]["requestedBytes"]), (72, total_bytes))
            self.assertEqual((result["budget"]["maxReads"], result["budget"]["maxBytes"], result["budget"]["maxSingleRead"]), (72, 1852, 208))
            self.assertEqual(memory.calls[:36], memory.calls[36:])
            self.assertEqual(memory.calls[28:36], [(MODULE + 0x5b74628, 8), (MODULE + 0x5897cf0, 8),
                (K2, 208), (GC2, 32), (INST2, 16), (ARGS2, 8), (TYPE2, 16), (ROOT + 2147 * 8, 8)])
            graph = result["ctorInputs"]["hashSet"]
            self.assertTrue(graph["identityProved"])
            self.assertEqual(graph["staticBinding"]["genericInstanceIndex"], 11)
            self.assertNotEqual(graph["classInst"], 11)
            self.assertEqual(graph["argumentOpaqueData"], core.U64 - 13)
            self.assertEqual(graph["ordinarySlot"], ORDINARY2)
            self.assertNotEqual(graph["ordinarySlot"], graph["backref"])
            self.assertEqual(memory.spans, original)
            self.assertFalse(result["ctorInputs"]["secondAllocationReturned"])
            for name in ("secondRecordRead", "secondHeadRead", "nextLinkRead", "arrayBodyRead", "canReplayPop", "capturedCopyWriteApproved", "nativeCPUExecuted", "wholeConstructorComplete"):
                self.assertFalse(result["ctorInputs"][name])
            self.assertNotIn((CONTEXT + 0x68, 8), memory.calls)

    def test_items_zero_and_full_qwords_are_values_without_array_identity(self):
        for value in (0, 1, 1 << 63, core.U64):
            memory = ArtificialCtorMemory(items=value)
            result = ctor.collect(memory.read, MODULE, TEB)
            self.assertEqual(result["status"], "candidate")
            items = result["ctorInputs"]["items"]
            self.assertEqual(items["value"], value)
            self.assertEqual(items["itemsNonNull"], value != 0)
            self.assertEqual(items["rawHex"], struct.pack("<Q", value).hex())
            self.assertFalse(items["arrayIdentityProved"])
            self.assertFalse(items["pointeeRead"])
            self.assertEqual(result["allocationInputs"]["potentialUnreviewedEdge"]["site"], "0x15183fe99")
            self.assertFalse(result["ctorInputs"]["firstPendingConstructorWrite"]["approved"])
            self.assertNotIn((value, 8), memory.calls)

    def test_actual_shared_context_reuses_only_same_ledger_three_spans(self):
        for length, first_bytes, total_bytes in ((32, 710, 1420), (208, 886, 1772)):
            memory = ArtificialCtorMemory(length, shared=True)
            result = ctor.collect(memory.read, MODULE, TEB)
            self.assertEqual(result["status"], "candidate")
            self.assertEqual((result["budget"]["firstPassReads"], result["budget"]["firstPassBytes"]), (33, first_bytes))
            self.assertEqual((result["budget"]["reads"], result["budget"]["requestedBytes"]), (66, total_bytes))
            graph = result["ctorInputs"]["hashSet"]
            self.assertTrue(graph["actualClassInstEqualsList"])
            self.assertEqual([entry["firstOrdinal"] for entry in graph["reusedSpans"]], [8, 9, 10])
            self.assertEqual([entry["requested"] for entry in graph["reusedSpans"]], [16, 8, 16])
            self.assertEqual(memory.calls.count((INST, 16)), 2)
            self.assertEqual([entry["firstOrdinal"] for entry in graph["sharedR1Sources"].values()], [11, 13])
        distinct = ctor.collect(ArtificialCtorMemory().read, MODULE, TEB)
        self.assertEqual(distinct["ctorInputs"]["hashSet"]["staticBinding"]["genericInstanceIndex"], 11)
        self.assertFalse(distinct["ctorInputs"]["hashSet"]["actualClassInstEqualsList"])
        self.assertEqual(distinct["ctorInputs"]["hashSet"]["reusedSpans"], [])

    def test_shared_span_final_drift_invalidates_both_class_input_candidate(self):
        memory = ArtificialCtorMemory(shared=True)
        memory.faults[33 + 8] = (bytes(16), None)
        result = ctor.collect(memory.read, MODULE, TEB)
        self.assertEqual(result["status"], "partial")
        self.assertEqual(len(memory.calls), 41)
        self.assertFalse(result["ctorInputs"]["stableInputBytes"])
        self.assertTrue(result["ctorInputs"]["hashSet"]["identityProved"])

    def test_false_type_method_backref_count_kind_and_byref_never_substitute(self):
        cases = [(K2, 0x18, "<I", 18 << 16), (K2, 0x18, "<I", (21 << 16) | 0x40000000),
                 (GC2, 0, "<I", 1255), (GC2, 16, "<Q", 1), (GC2, 24, "<Q", ORDINARY2),
                 (INST2, 0, "<I", 2), (TYPE2, 8, "<I", 14 << 16), (TYPE2, 8, "<I", (9 << 16) | 0x40000000)]
        for address, offset, fmt, value in cases:
            with self.subTest(address=address, value=value):
                memory = ArtificialCtorMemory()
                raw = bytearray(memory.spans[address])
                struct.pack_into(fmt, raw, offset, value)
                memory.put(address, raw)
                result = ctor.collect(memory.read, MODULE, TEB)
                self.assertEqual(result["status"], "partial")
                self.assertFalse(result["ctorInputs"]["hashSet"]["identityProved"])
                self.assertNotIn((ROOT + 2147 * 8, 8), memory.calls)

    def test_class_zero_and_ordinary_zero_have_distinct_source_frontiers(self):
        memory = ArtificialCtorMemory()
        memory.qword(MODULE + 0x5897cf0, 0)
        result = ctor.collect(memory.read, MODULE, TEB)
        self.assertEqual(result["status"], "partial")
        self.assertEqual(result["ctorInputs"]["hashSet"]["classPointer"], 0)
        self.assertEqual(result["ctorInputs"]["hashSet"]["allocationStop"]["stopBefore"], "0x15183fea8")
        self.assertNotIn((K2, 208), memory.calls)
        memory = ArtificialCtorMemory()
        memory.qword(ROOT + 2147 * 8, 0)
        result = ctor.collect(memory.read, MODULE, TEB)
        graph = result["ctorInputs"]["hashSet"]
        self.assertEqual(result["status"], "partial")
        self.assertTrue(graph["identityProved"])
        self.assertEqual(graph["ordinarySlot"], 0)
        self.assertEqual(graph["allocationStop"]["stopBefore"], "0x15a4d6c03")
        self.assertEqual(graph["nextProducerInput"]["nextUnknownIATSite"], "0x15a4d6c0f")
        self.assertNotIn((MODULE + 0x5bc95a8, 8), memory.calls)

    def test_ordinary_comparison_resolver_and_initialization_are_not_fast_size_claims(self):
        memory = ArtificialCtorMemory()
        memory.qword(ROOT + 2147 * 8, int.from_bytes(memory.spans[MODULE + 0x5bc96f8], "little"))
        result = ctor.collect(memory.read, MODULE, TEB)
        graph = result["ctorInputs"]["hashSet"]
        self.assertTrue(graph["identityProved"])
        self.assertEqual(graph["allocationStop"]["stopBefore"], "0x140533784")
        self.assertNotIn("allocationInterpretation", graph)
        memory = ArtificialCtorMemory(ca=2)
        result = ctor.collect(memory.read, MODULE, TEB)
        graph = result["ctorInputs"]["hashSet"]
        self.assertTrue(graph["identityProved"])
        self.assertEqual(graph["classCA"], 2)
        self.assertEqual(graph["allocationStop"]["stopBefore"], "0x1405336eb")

    def test_actual_u16_flags_tags_and_small_bin_values_not_fixed_56(self):
        for size, bin_index in ((16, 1), (32, 2), (40, 3), (56, 4), (80, 5), (256, 16), (0x8000, 72)):
            memory = ArtificialCtorMemory(request=size)
            result = ctor.collect(memory.read, MODULE, TEB)
            graph = result["ctorInputs"]["hashSet"]
            self.assertEqual(result["status"], "candidate")
            path = graph["allocationInterpretation"]
            self.assertEqual(path["requestU16"], size)
            self.assertEqual(path["binU32"], bin_index)
            self.assertEqual(path["candidateRecordPointerCell"], CONTEXT + 0x28 + bin_index * 16)
            self.assertFalse(path["recordCellRead"])
            self.assertFalse(path["secondRecordPointerKnown"])
            self.assertFalse(path["requestForcedTo56"])
        for ca, low3, tag in ((0x62, 1, 0), (0x62, 7, 1), (0x60, 7, 0)):
            result = ctor.collect(ArtificialCtorMemory(ca=ca, low3=low3, c7=0, c8=0).read, MODULE, TEB)
            self.assertEqual(result["status"], "candidate")
            graph = result["ctorInputs"]["hashSet"]
            self.assertEqual(graph["classCA"], ca)
            self.assertEqual((graph["classC7"], graph["classC8"]), (0, 0))
            self.assertEqual(graph["allocationInterpretation"]["tagBitUsed"], tag)
            self.assertEqual(graph["postPopPotentialStop"]["site"], "0x140525e2a")

    def test_zero_and_large_request_observations_stop_original_branches(self):
        for size, site in ((0, "0x14053380b"), (0x8001, "0x140563df5"), (0xffff, "0x140563df5")):
            memory = ArtificialCtorMemory(request=size)
            result = ctor.collect(memory.read, MODULE, TEB)
            graph = result["ctorInputs"]["hashSet"]
            self.assertEqual(result["status"], "partial")
            self.assertTrue(graph["identityProved"])
            self.assertEqual(graph["observedB4U16"], size)
            self.assertEqual(graph["allocationStop"]["stopBefore"], site)
            self.assertNotIn("nextMissingStorage", graph)

    def test_each_new_span_short_api_error_stops_and_preserves_received_bytes(self):
        for ordinal in range(29, 37):
            for fault in ((b"", None), (b"\x12", {"winerror": 299})):
                memory = ArtificialCtorMemory()
                memory.faults[ordinal] = fault
                result = ctor.collect(memory.read, MODULE, TEB)
                self.assertEqual(result["status"], "partial")
                self.assertEqual(result["budget"]["firstPassReads"], ordinal)
                failed = result["records"][ordinal - 1]
                self.assertEqual(failed["bytesHex"], fault[0].hex())
                self.assertIsNotNone(failed["error"])
                self.assertEqual(memory.calls.count((failed["address"], failed["requested"])), 1)

    def test_each_new_final_position_drift_or_failure_is_partial_without_reexpansion(self):
        baseline = ctor.collect(ArtificialCtorMemory().read, MODULE, TEB)
        for ordinal, first in enumerate(baseline["records"][28:36], 29):
            raw = bytearray.fromhex(first["bytesHex"])
            raw[0] ^= 1
            for fault in ((bytes(raw), None), (b"", {"winerror": 299})):
                memory = ArtificialCtorMemory()
                memory.faults[36 + ordinal] = fault
                result = ctor.collect(memory.read, MODULE, TEB)
                self.assertEqual(result["status"], "partial")
                self.assertEqual(len(memory.calls), 36 + ordinal)
                self.assertFalse(result["ctorInputs"]["stableInputBytes"])

    def test_one_four_profile_registry_exact_old_caps_unknown_rejected(self):
        self.assertEqual(core.READ_PROFILES, {"avatar-r1": (13, 340, 26, 680),
            "avatar-r1-pool": (18, 369, 36, 738), "avatar-r1-pool-head": (28, 622, 56, 1244),
            "avatar-r1-pool-head-ctor": (36, 926, 72, 1852)})
        with self.assertRaises(ValueError):
            core.ReadLedger(lambda *_: (b"", None), lambda: "artificial", profile="arbitrary")

    @unittest.skipUnless((BEFORE / "client72_d02_avatar_block_inputs.py").is_file(), "private before source unavailable")
    def test_all_three_previous_profile_reports_and_requests_equal_before(self):
        names = ["client72_d02_tls_inputs", "client72_d02_tls_pool_inputs", "client72_d02_avatar_block_inputs"]
        expected = ["2d8bae92b40b1544d79aa8c17406667b1accfe6493f685c30ee448bc26c91dd7",
                    "f67194e87977a79c4f68fdbd1610d7d417c9aa76f484113b6a92cc094477a5d2",
                    "5c33b82ec97f06ea044d1dd87912fa38add8cd55eec51c5e4a3a29b8b6f77deb"]
        frozen = {}
        for name, sha in zip(names, expected):
            path = BEFORE / (name + ".py")
            raw = path.read_bytes()
            self.assertEqual(core.digest(raw), sha)
            module = ModuleType("tools." + name)
            module.__package__, module.__file__ = "tools", str(core.ROOT / "tools" / (name + ".py"))
            with ExitStack() as stack:
                for key, value in frozen.items():
                    stack.enter_context(patch.object(tools, key, value))
                stack.enter_context(patch.dict(sys.modules, {"tools." + key: value for key, value in frozen.items()}))
                exec(compile(raw, str(path), "exec"), module.__dict__)
            frozen[name] = module
        for old, new, first_reads in ((frozen[names[0]].collect, core.collect, 13),
                (frozen[names[1]].collect, pool.collect, 18), (frozen[names[2]].collect, head.collect, 28)):
            for fault in (None, 3, first_reads + 1):
                previous, current = ArtificialCtorMemory(), ArtificialCtorMemory()
                if fault:
                    for memory in (previous, current):
                        memory.faults[fault] = (b"", {"winerror": 5})
                old_result = old(previous.read, MODULE, TEB, now=lambda: "artificial-same-clock")
                new_result = new(current.read, MODULE, TEB, now=lambda: "artificial-same-clock")
                self.assertEqual(old_result, new_result)
                self.assertEqual(previous.calls, current.calls)

    @unittest.skipUnless((core.ROOT / (ctor.PRIVATE + "audit-first.private.json")).is_file(), "private audit unavailable")
    def test_source_gate_frozen_contract_and_audited_digest_drift_rejected(self):
        result = ctor.verify_sources()
        self.assertTrue(result["metadataCacheHashedOnly"])
        original_read = Path.read_bytes
        target = core.ROOT / ctor.CONTRACT

        def drift(path):
            raw = original_read(path)
            return raw + b"\n" if path == target else raw

        with patch.object(Path, "read_bytes", drift):
            with self.assertRaises(ValueError):
                ctor.verify_sources()
        original_hash = ctor._file_sha256
        metadata = core.ROOT / "local/migration72/d02-resume-20261108/r3-equipment/metadata.private.pkl"
        with patch.object(ctor, "_file_sha256", side_effect=lambda path: "00" * 32 if path == metadata else original_hash(path)):
            with self.assertRaises(ValueError):
                ctor.verify_sources()


class CtorAdapterArtificialTests(unittest.TestCase):
    def sample(self, *, include_pool=True, include_allocator_inputs=True, include_ctor_inputs=True, omit_pin=False):
        backend, incoming = ArtificialBackend(), epoch()
        backend.memory = ArtificialCtorMemory()
        for enabled, name in ((include_pool, "tools/client72_d02_tls_pool_inputs.py"),
                (include_allocator_inputs, "tools/client72_d02_avatar_block_inputs.py"),
                (include_ctor_inputs is True and not omit_pin, "tools/client72_d02_avatar_ctor_inputs.py")):
            if enabled is True:
                incoming["implementationPins"][name] = core.digest((core.ROOT / name).read_bytes())
        identity = {name: deepcopy(incoming[name]) for name in ("pid", "image", "processCreationTime", "module")}
        with patch.object(ctor, "verify_sources", return_value={"artificialFixture": True}):
            result = adapter.sample_primary(backend, incoming, lambda process, pid: deepcopy(identity),
                include_pool=include_pool, include_allocator_inputs=include_allocator_inputs, include_ctor_inputs=include_ctor_inputs)
        return backend, result

    def test_five_pins_same_primary_budget_and_no_array_or_record_reads(self):
        backend, result = self.sample()
        self.assertEqual(result["status"], "candidate")
        self.assertTrue(result["artificialFixture"])
        self.assertEqual(len(result["implementationPins"]), 5)
        self.assertEqual(result["apiCounts"], adapter.API_LIMITS)
        self.assertEqual(result["threadBasicOutputBytes"], 96)
        self.assertEqual(result["collection"]["budget"]["requestedBytes"], 1500)
        self.assertNotIn((CONTEXT + 0x68, 8), backend.memory.calls)
        self.assertNotIn((ITEMS, 8), backend.memory.calls)
        self.assertEqual(backend.closed, [818])

    def test_invalid_combination_nonboolean_or_missing_fifth_pin_refuses_before_api(self):
        for parameters in ({"include_pool": False}, {"include_allocator_inputs": False},
                           {"include_ctor_inputs": 1}, {"omit_pin": True}):
            backend, result = self.sample(**parameters)
            self.assertEqual(result["status"], "partial")
            self.assertEqual(result["failureStage"], "entry")
            self.assertEqual(result["apiCounts"]["NtQueryInformationThread"], 0)
            self.assertEqual(backend.memory.calls, [])
            self.assertEqual(backend.closed, [818])


if __name__ == "__main__":
    unittest.main()
