"""Artificial original-span fixtures only: no Windows backend, CPU or live heap."""

from copy import deepcopy
from pathlib import Path
import struct
import sys
from types import ModuleType
import unittest
from unittest.mock import patch

import tools
from tools import client72_d02_avatar_block_inputs as block
from tools import client72_d02_tls_inputs as core
from tools import client72_d02_tls_pool_inputs as pool
from tools import observe_client72_tls_inputs as adapter
from tools.test_client72_d02_tls_inputs import MODULE, TEB, TLS, CONTEXT, LIST, ROOT
from tools.test_client72_d02_tls_pool_inputs import ArtificialPoolMemory, POOL_POINTER
from tools.test_observe_client72_tls_inputs import ArtificialBackend, epoch

RECORD, HEAD, THREAD_STATE, NEXT = [0xbcde000000000000 + n * 0x1000 for n in range(4)]
BEFORE = core.ROOT / block.BEFORE
CAPTURE_AUDIT = block.CAPTURE_PRIVATE + "audit-first.private.json"


class ArtificialBlockMemory(ArtificialPoolMemory):
    def __init__(self, length=32, *, next_pointer=0, old_flags=0, notify=0, ca=0x62, c7=1, low3=0):
        super().__init__(bound=core.U64)
        header = bytearray(self.spans[LIST])
        header[0xc7], header[0xca] = c7, ca
        struct.pack_into("<I", header, 8, low3)
        self.put(LIST, header)
        self.put(TLS + 0x19cc, b"\x00")
        self.qword(CONTEXT + 0x48, RECORD)
        self.qword(RECORD + 0x18, length)
        self.qword(RECORD + 0x20, HEAD)
        self.put(RECORD + 0x10, struct.pack("<H", 0))
        self.qword(CONTEXT + 8, 0)
        self.put(MODULE + 0x5699680, b"\x00")
        self.qword(TLS + 0x19e0, THREAD_STATE)
        self.put(THREAD_STATE + 0xa0, bytes([notify]))
        # Invalid lengths never allocate their claimed size, even in this fixture.
        size = length if 32 <= length <= 208 else 32
        raw = bytearray((index * 37 + 19) & 255 for index in range(size))
        struct.pack_into("<Q", raw, 0, next_pointer)
        raw[8] = old_flags
        self.original_block = bytes(raw)
        self.put(HEAD, raw)


class BlockArtificialTests(unittest.TestCase):
    def test_all_177_accepted_lengths_exact_one_head_span_then_unified_final(self):
        for length in range(32, 209):
            with self.subTest(length=length):
                memory = ArtificialBlockMemory(length)
                original = deepcopy(memory.spans)
                result = block.collect(memory.read, MODULE, TEB)
                capture, budget = result["allocationInputs"], result["budget"]
                self.assertEqual(result["status"], "candidate")
                self.assertEqual((budget["firstPassReads"], budget["firstPassBytes"]), (28, 414 + length))
                self.assertEqual((budget["reads"], budget["requestedBytes"]), (56, 828 + 2 * length))
                self.assertEqual((budget["maxReads"], budget["maxBytes"], budget["maxSingleRead"]), (56, 1244, 208))
                self.assertEqual(memory.calls[22], (HEAD, length))
                self.assertEqual(memory.calls[:28], memory.calls[28:])
                self.assertEqual(memory.calls.count((HEAD, length)), 2)
                self.assertNotIn((HEAD, 8), memory.calls)
                self.assertNotIn((HEAD + 8, 1), memory.calls)
                self.assertEqual(capture["headBytesHex"], memory.original_block.hex())
                self.assertEqual(capture["headSHA256"], core.digest(memory.original_block))
                self.assertEqual(capture["nextPointerObserved"], 0)
                self.assertTrue(capture["rawBlockHasNonzeroBytes"])
                self.assertEqual(memory.spans, original)
                self.assertTrue(capture["stableInputBytes"])
                self.assertEqual(capture["readOnlyStopBefore"], "0x140563d2d")
                for flag in ("nextNodeRead", "objectsAllocated", "nativeCPUExecuted", "externalCallExecuted",
                             "capturedWriteExecuted", "capturedCopyWriteApproved", "canReplayPop",
                             "naturalAvatarAllocatorThreadObserved", "naturalHeapOwnershipProven", "wholeConstructorComplete"):
                    self.assertIs(capture[flag], False)
                self.assertEqual(capture["approvedWriteSpans"], [])

    def test_out_of_policy_u64_lengths_preserved_without_head_or_pool_read(self):
        for length in (0, 1, 31, 209, 1 << 32, 1 << 63, core.U64):
            with self.subTest(length=length):
                memory = ArtificialBlockMemory(length)
                result = block.collect(memory.read, MODULE, TEB)
                self.assertEqual(result["status"], "partial")
                self.assertEqual(result["allocationInputs"]["observedLengthU64"], length)
                self.assertEqual(result["budget"]["firstPassReads"], 16)
                self.assertNotIn((RECORD + 0x20, 8), memory.calls)
                self.assertNotIn((HEAD, 32), memory.calls)
                self.assertNotIn((TLS + 0x1990, 8), memory.calls)

    def test_request_is_native_u16_and_tag_comes_from_actual_low_bits(self):
        for low3 in range(8):
            memory = ArtificialBlockMemory(low3=low3)
            header = bytearray(memory.spans[LIST])
            struct.pack_into("<I", header, 0xb4, 0xabcd0020)
            memory.put(LIST, header)
            result = block.collect(memory.read, MODULE, TEB)
            self.assertEqual(result["status"], "candidate")
            capture = result["allocationInputs"]
            self.assertEqual(capture["requestU16"], 32)
            self.assertEqual(result["r1"]["sizeB4U32LegacyDisplay"], 0xabcd0020)
            self.assertEqual(result["r1"]["sizeB4NativeU16"], 32)
            self.assertEqual(capture["derivedTagBit"], int(low3 != 1))
            self.assertEqual(capture["taggedClassDerived"], LIST | int(low3 != 1))
        memory = ArtificialBlockMemory()
        header = bytearray(memory.spans[LIST])
        struct.pack_into("<H", header, 0xb4, 33)
        memory.put(LIST, header)
        result = block.collect(memory.read, MODULE, TEB)
        self.assertEqual(result["status"], "partial")
        self.assertEqual(result["budget"]["firstPassReads"], 13)
        self.assertEqual(result["allocationInputs"]["requestU16"], 33)

    def test_source_supported_tagged_branch_only_no_flags_forced(self):
        for ca, site in ((2, "0x1405336eb"), (0x60, "0x14053373d")):
            memory = ArtificialBlockMemory(ca=ca)
            original = memory.spans[LIST]
            result = block.collect(memory.read, MODULE, TEB)
            self.assertEqual(result["status"], "partial")
            self.assertEqual(result["allocationInputs"]["unsupportedBranchStopBefore"], site)
            self.assertEqual(result["budget"]["firstPassReads"], 13)
            self.assertEqual(memory.spans[LIST], original)
        memory = ArtificialBlockMemory()
        memory.qword(ROOT + 1255 * 8, int.from_bytes(memory.spans[MODULE + 0x5bc96f8], "little"))
        result = block.collect(memory.read, MODULE, TEB)
        self.assertEqual(result["status"], "partial")
        self.assertEqual(result["allocationInputs"]["unsupportedBranchStopBefore"], "0x140533784")

    def test_record_is_pointed_to_and_zero_state_flags_counters_are_legal(self):
        memory = ArtificialBlockMemory()
        memory.put(RECORD + 0x10, struct.pack("<H", 0xffff))
        memory.qword(CONTEXT + 8, core.U64)
        result = block.collect(memory.read, MODULE, TEB)
        capture = result["allocationInputs"]
        self.assertEqual(capture["recordPointer"], RECORD)
        self.assertNotEqual(RECORD, CONTEXT + 0x48)
        self.assertEqual(memory.calls[14:18], [(CONTEXT + 0x48, 8), (RECORD + 0x18, 8), (RECORD + 0x20, 8), (RECORD + 0x10, 2)])
        self.assertEqual(capture["lockCounterU16"], 0xffff)
        self.assertEqual(capture["counterU64"], core.U64)
        self.assertEqual((capture["initialStateByte"], capture["globalHeaderByte"], capture["notifyByte"], capture["oldFlags"]), (0, 0, 0, 0))

    def test_zero_dependencies_stop_and_thread_state_null_is_not_notify_zero(self):
        for address, first in ((CONTEXT + 0x48, 15), (RECORD + 0x20, 17), (TLS + 0x19e0, 21)):
            memory = ArtificialBlockMemory()
            memory.qword(address, 0)
            result = block.collect(memory.read, MODULE, TEB)
            self.assertEqual(result["status"], "partial")
            self.assertEqual(result["budget"]["firstPassReads"], first)
            self.assertFalse(result["allocationInputs"]["rawHeadCaptured"])
            self.assertNotIn((HEAD, 32), memory.calls)
            self.assertNotIn((TLS + 0x1990, 8), memory.calls)
            if address == TLS + 0x19e0:
                self.assertNotIn("notifyByte", result["allocationInputs"])

    def test_nonzero_next_is_recorded_but_never_followed(self):
        memory = ArtificialBlockMemory(next_pointer=NEXT)
        result = block.collect(memory.read, MODULE, TEB)
        self.assertEqual(result["allocationInputs"]["nextPointerObserved"], NEXT)
        self.assertFalse(any(address == NEXT for address, _ in memory.calls))

    def test_every_new_capture_span_short_api_and_os_error_retained_no_retry(self):
        for ordinal in range(14, 24):
            for fault in ((b"", None), (b"\x12", {"winerror": 299}), OSError(5, "artificial")):
                with self.subTest(ordinal=ordinal, fault=fault):
                    memory = ArtificialBlockMemory()
                    memory.faults[ordinal] = fault
                    result = block.collect(memory.read, MODULE, TEB)
                    self.assertEqual(result["status"], "partial")
                    self.assertEqual(result["budget"]["firstPassReads"], ordinal)
                    failed = result["records"][ordinal-1]
                    self.assertIsNotNone(failed["error"])
                    self.assertIn("sourceReader", failed)
                    self.assertEqual(memory.calls.count((failed["address"], failed["requested"])), 1)
                    self.assertFalse(result["allocationInputs"]["rawHeadCaptured"])

    def test_every_final_position_drift_or_failure_stops_without_retracking(self):
        reference = ArtificialBlockMemory()
        baseline = block.collect(reference.read, MODULE, TEB)
        for index, first in enumerate(baseline["records"][:28], 1):
            changed = bytearray.fromhex(first["bytesHex"])
            changed[0] ^= 1
            for fault in ((bytes(changed), None), (b"", {"winerror": 299})):
                with self.subTest(final=index, error=fault[1]):
                    memory = ArtificialBlockMemory()
                    memory.faults[28 + index] = fault
                    result = block.collect(memory.read, MODULE, TEB)
                    self.assertEqual(result["status"], "partial")
                    self.assertEqual(len(memory.calls), 28 + index)
                    self.assertEqual(result["inputConsistency"]["verifiedSpans"], index - 1)
                    self.assertFalse(result["allocationInputs"]["stableInputBytes"])
                    self.assertFalse(result["canReplayPop"])

    def test_each_existing_pool_span_failure_keeps_the_completed_head_raw(self):
        for ordinal in range(24, 29):
            memory = ArtificialBlockMemory()
            memory.faults[ordinal] = (b"", {"winerror": 299})
            result = block.collect(memory.read, MODULE, TEB)
            self.assertEqual(result["status"], "partial")
            self.assertEqual(result["failureStage"], "pool")
            self.assertEqual(result["budget"]["firstPassReads"], ordinal)
            self.assertTrue(result["allocationInputs"]["rawHeadCaptured"])
            self.assertEqual(result["allocationInputs"]["headBytesHex"], memory.original_block.hex())
            self.assertFalse(result["allocationInputs"]["stableInputBytes"])

    def test_overlap_conflict_preserves_complete_returned_raw_and_is_partial(self):
        memory = ArtificialBlockMemory()
        memory.qword(RECORD + 0x20, CONTEXT + 8)
        conflicting = struct.pack("<Q", 99) + memory.original_block[8:]
        memory.faults[23] = (conflicting, None)
        result = block.collect(memory.read, MODULE, TEB)
        self.assertEqual(result["status"], "partial")
        self.assertEqual(result["records"][22]["bytesHex"], conflicting.hex())
        self.assertEqual(result["records"][22]["error"]["kind"], "overlap-drift")
        self.assertNotIn((TLS + 0x1990, 8), memory.calls)

    def test_unreviewed_edges_described_without_execution_or_six_write_grants(self):
        for parameters, site in (({"old_flags": 8}, "0x140563d85"), ({"notify": 1}, "0x140563e01"),
                                 ({"ca": 0x63}, "0x1405337f3"), ({"c7": 0}, "0x140525e2a"), ({}, "0x15183fe92")):
            memory = ArtificialBlockMemory(**parameters)
            original = deepcopy(memory.spans)
            result = block.collect(memory.read, MODULE, TEB)
            capture = result["allocationInputs"]
            self.assertEqual(capture["potentialUnreviewedEdge"]["site"], site)
            self.assertEqual(capture["readOnlyStopBefore"], "0x140563d2d")
            self.assertFalse(capture["externalCallExecuted"])
            self.assertFalse(capture["capturedWriteExecuted"])
            self.assertEqual(capture["approvedWriteSpans"], [])
            self.assertEqual(memory.spans, original)

    def test_fixed_third_profile_and_old_caps(self):
        self.assertEqual(set(core.READ_PROFILES), {"avatar-r1", "avatar-r1-pool", "avatar-r1-pool-head", "avatar-r1-pool-head-ctor"})
        self.assertEqual(core.READ_PROFILES["avatar-r1"], (13, 340, 26, 680))
        self.assertEqual(core.READ_PROFILES["avatar-r1-pool"], (18, 369, 36, 738))
        self.assertEqual(core.READ_PROFILES["avatar-r1-pool-head"], (28, 622, 56, 1244))
        with self.assertRaises(ValueError):
            core.ReadLedger(lambda *_: (b"", None), lambda: "artificial", profile="unbounded")

    @unittest.skipUnless((BEFORE / "client72_d02_tls_inputs.py").is_file(), "private before source unavailable")
    def test_old_two_profiles_complete_outputs_and_sequences_equal_before(self):
        core_path, pool_path = BEFORE / "client72_d02_tls_inputs.py", BEFORE / "client72_d02_tls_pool_inputs.py"
        raw_core, raw_pool = core_path.read_bytes(), pool_path.read_bytes()
        self.assertEqual(core.digest(raw_core), "a1449c67c1aaeb4a739e7f57c25bf08b44b412881256ffe116bd1151f1bcca3d")
        self.assertEqual(core.digest(raw_pool), "f67194e87977a79c4f68fdbd1610d7d417c9aa76f484113b6a92cc094477a5d2")
        old_core = ModuleType("artificial_frozen_core")
        old_core.__file__ = str(core_path)
        exec(compile(raw_core, str(core_path), "exec"), old_core.__dict__)
        old_pool = {"__name__": "artificial_frozen_pool", "__file__": str(pool_path), "__package__": "tools"}
        with patch.object(tools, "client72_d02_tls_inputs", old_core), patch.dict(sys.modules, {"tools.client72_d02_tls_inputs": old_core}):
            exec(compile(raw_pool, str(pool_path), "exec"), old_pool)
        for previous, current, first_reads in ((old_core.collect, core.collect, 13), (old_pool["collect"], pool.collect, 18)):
            for fault in (None, 3, first_reads + 1):
                old_memory, new_memory = ArtificialBlockMemory(), ArtificialBlockMemory()
                if fault:
                    for memory in (old_memory, new_memory):
                        memory.faults[fault] = (b"", {"winerror": 5})
                before = previous(old_memory.read, MODULE, TEB, now=lambda: "artificial-same-clock")
                after = current(new_memory.read, MODULE, TEB, now=lambda: "artificial-same-clock")
                self.assertEqual(before, after)
                self.assertEqual(old_memory.calls, new_memory.calls)

    @unittest.skipUnless((core.ROOT / CAPTURE_AUDIT).is_file(),
                         "private frozen source audit unavailable")
    def test_source_gate_contract_audit_and_historical_source_drift_rejected(self):
        result = block.verify_sources()
        self.assertEqual(result["historicalSourceLocations"], block.HISTORICAL_SOURCES)
        original_read = Path.read_bytes
        for relative in (block.CONTRACT, CAPTURE_AUDIT, block.HISTORICAL_SOURCES["tools/client72_d02_tls_inputs.py"]):
            target = core.ROOT / relative

            def drift(path, target=target):
                raw = original_read(path)
                return raw + b"\n" if path == target else raw

            with self.subTest(relative=relative), patch.object(Path, "read_bytes", drift):
                with self.assertRaises(ValueError):
                    block.verify_sources()


class BlockAdapterArtificialTests(unittest.TestCase):
    def sample(self, *, include_pool=True, include_allocator_inputs=True, omit_block_pin=False, change_after=False):
        backend, incoming = ArtificialBackend(), epoch()
        backend.memory = ArtificialBlockMemory()
        if include_pool is True:
            name = "tools/client72_d02_tls_pool_inputs.py"
            incoming["implementationPins"][name] = core.digest((core.ROOT / name).read_bytes())
        if include_allocator_inputs is True and not omit_block_pin:
            name = "tools/client72_d02_avatar_block_inputs.py"
            incoming["implementationPins"][name] = core.digest((core.ROOT / name).read_bytes())
        expected = {name: deepcopy(incoming[name]) for name in ("pid", "image", "processCreationTime", "module")}
        identities = [deepcopy(expected), deepcopy(expected)]
        if change_after:
            identities[-1]["module"]["size"] += 1
        with patch.object(block, "verify_sources", return_value={"artificialFixture": True}), \
                patch.object(pool, "verify_sources", return_value={"artificialFixture": True}), \
                patch.object(core, "verify_sources", return_value={"artificialFixture": True}):
            result = adapter.sample_primary(backend, incoming, lambda process, pid: identities.pop(0),
                include_pool=include_pool, include_allocator_inputs=include_allocator_inputs)
        return backend, result

    def test_four_pins_same_primary_api_limits_and_l32_budget(self):
        backend, result = self.sample()
        self.assertEqual(result["status"], "candidate")
        self.assertTrue(result["artificialFixture"])
        self.assertEqual(len(result["implementationPins"]), 4)
        self.assertEqual(result["apiCounts"], adapter.API_LIMITS)
        self.assertEqual(result["threadBasicOutputBytes"], 96)
        self.assertEqual((result["collection"]["budget"]["firstPassReads"], result["collection"]["budget"]["firstPassBytes"]), (28, 446))
        self.assertEqual((result["collection"]["budget"]["reads"], result["collection"]["budget"]["requestedBytes"]), (56, 892))
        self.assertEqual(backend.closed, [818])

    def test_illegal_combination_nonboolean_or_missing_fourth_pin_closes_without_api(self):
        for parameters in ({"include_pool": False}, {"include_allocator_inputs": 1}, {"omit_block_pin": True}):
            backend, result = self.sample(**parameters)
            self.assertEqual(result["status"], "partial")
            self.assertEqual(result["failureStage"], "entry")
            self.assertEqual(result["apiCounts"]["NtQueryInformationThread"], 0)
            self.assertEqual(backend.memory.calls, [])
            self.assertEqual(backend.closed, [818])

    def test_after_identity_change_preserves_full_raw_capture_and_close(self):
        backend, result = self.sample(change_after=True)
        self.assertEqual(result["status"], "partial")
        self.assertEqual(result["failureStage"], "identity-after")
        self.assertTrue(result["collection"]["allocationInputs"]["rawHeadCaptured"])
        self.assertEqual(result["apiCounts"], adapter.API_LIMITS)
        self.assertEqual(backend.closed, [818])


if __name__ == "__main__":
    unittest.main()
