"""Artificial merged TLS/Pool bytes and Python callbacks, never live/native heap."""

from copy import deepcopy
import struct
import unittest
from unittest.mock import patch

from tools import client72_d02_tls_inputs as core
from tools import client72_d02_tls_pool_inputs as pool
from tools import observe_client72_tls_inputs as adapter
from tools.test_client72_d02_tls_inputs import ArtificialMemory, MODULE, TEB, TLS, CONTEXT
from tools.test_observe_client72_tls_inputs import ArtificialBackend, epoch

POOL_POINTER, HEAD_ARRAY = 0x9abc000000000000, 0xabcde00000000000
BEFORE = core.ROOT / core.PRIVATE / "pool-collector-implementation/source-before/client72_d02_tls_inputs.py"


class ArtificialPoolMemory(ArtificialMemory):
    def __init__(self, *, pointer=POOL_POINTER, u32=0, flags=1, head=HEAD_ARRAY, bound=1, global_qword=0, global_byte=0):
        super().__init__()
        self.qword(TLS + 0x1990, pointer)
        if pointer:
            self.put(pointer, struct.pack("<I", u32))
            self.put(pointer + 8, bytes([flags]))
            self.qword(pointer + 0x18, head)
            self.qword(pointer + 0x20, bound)
        self.qword(MODULE + 0x5bc87d8, global_qword)
        self.put(MODULE + 0x5bc7701, bytes([global_byte]))


class PoolMergedArtificialTests(unittest.TestCase):
    def test_all_conditional_branches_exact_addresses_budgets_and_stop_before(self):
        cases = [
            ({"pointer": 0}, [(TLS + 0x1990, 8), (MODULE + 0x5bc87d8, 8)], 356, "0x14050ab6e", "external-call"),
            ({"pointer": 0, "global_qword": core.U64}, [(TLS + 0x1990, 8), (MODULE + 0x5bc87d8, 8), (MODULE + 0x5bc7701, 1)], 357, "0x14050a9f5", "external-call"),
            ({"pointer": 0, "global_qword": 1, "global_byte": 255}, [(TLS + 0x1990, 8), (MODULE + 0x5bc87d8, 8), (MODULE + 0x5bc7701, 1)], 357, "0x14050ab6e", "external-call"),
            ({"flags": 0xfe}, [(TLS + 0x1990, 8), (POOL_POINTER, 4), (POOL_POINTER + 8, 1)], 353, "0x14050aac8", "external-call"),
            ({"head": 0}, [(TLS + 0x1990, 8), (POOL_POINTER, 4), (POOL_POINTER + 8, 1), (POOL_POINTER + 0x18, 8)], 361, "0x14050aac0", "captured-data-write"),
            ({"bound": 0}, [(TLS + 0x1990, 8), (POOL_POINTER, 4), (POOL_POINTER + 8, 1), (POOL_POINTER + 0x18, 8), (POOL_POINTER + 0x20, 8)], 369, "0x14050aac0", "captured-data-write"),
            ({"bound": core.U64}, [(TLS + 0x1990, 8), (POOL_POINTER, 4), (POOL_POINTER + 8, 1), (POOL_POINTER + 0x18, 8), (POOL_POINTER + 0x20, 8)], 369, "0x14050a8f7", "uncaptured-global-read"),
        ]
        for parameters, added, first_bytes, stop, kind in cases:
            with self.subTest(parameters=parameters):
                memory = ArtificialPoolMemory(**parameters)
                result = pool.collect(memory.read, MODULE, TEB)
                first_reads = 13 + len(added)
                self.assertEqual(result["status"], "candidate")
                self.assertEqual(result["profile"], "avatar-r1-pool")
                self.assertEqual(memory.calls[13:first_reads], added)
                self.assertEqual(memory.calls[:first_reads], memory.calls[first_reads:])
                self.assertEqual((result["budget"]["firstPassReads"], result["budget"]["firstPassBytes"]), (first_reads, first_bytes))
                self.assertEqual((result["budget"]["reads"], result["budget"]["requestedBytes"]), (first_reads * 2, first_bytes * 2))
                self.assertEqual((result["budget"]["maxReads"], result["budget"]["maxBytes"], result["budget"]["maxSingleRead"]), (36, 738, 208))
                self.assertEqual(result["pool"]["stopBefore"], stop)
                self.assertEqual(result["pool"]["stopKind"], kind)
                self.assertTrue(result["pool"]["stableInputBytes"])
                self.assertNotIn((HEAD_ARRAY, 8), memory.calls)
                self.assertNotIn((MODULE + 0x5bc7758, 8), memory.calls)
                self.assertNotIn((MODULE + 0x5bc7798, 8), memory.calls)
                self.assertNotIn((POOL_POINTER + 0x40, 8), memory.calls)
                for flag in ("naturalPoolAllocatorThreadObserved", "allocatorReturned", "headArrayRead", "nodeRead",
                             "loopExecuted", "nativeCPUExecuted", "externalCallExecuted", "capturedWriteExecuted",
                             "capturedWritesApproved", "canReplayPop"):
                    self.assertIs(result["pool"][flag], False)
                if kind == "captured-data-write":
                    self.assertEqual(result["pool"]["pendingWrite"]["address"], POOL_POINTER + 8)
                    self.assertFalse(result["pool"]["pendingWrite"]["approved"])

    def test_zero_u32_flags_and_full_unsigned_bound_are_legal_not_pointers(self):
        for bound in (0, 1, 1 << 32, 1 << 63, core.U64):
            memory = ArtificialPoolMemory(bound=bound, head=core.U64, u32=0, flags=255)
            result = pool.collect(memory.read, MODULE, TEB)
            self.assertEqual(result["status"], "candidate")
            self.assertEqual(result["pool"]["unsignedBoundAt20"], bound)
            self.assertEqual(result["pool"]["headArrayAt18"], core.U64)
            self.assertEqual(result["pool"]["u32ObservedAt0"], 0)
            self.assertEqual(len(memory.calls), 36)
        memory = ArtificialPoolMemory(u32=0xffffffff, flags=0)
        result = pool.collect(memory.read, MODULE, TEB)
        self.assertEqual(result["pool"]["u32ObservedAt0"], 0xffffffff)
        self.assertEqual(result["pool"]["flagsAt8"], 0)
        self.assertNotIn((POOL_POINTER + 0x18, 8), memory.calls)

    def test_pool1990_is_independent_from_avatar19d0_and_reader_provenance_survives_final(self):
        memory = ArtificialPoolMemory()
        result = pool.collect(memory.read, MODULE, TEB)
        self.assertEqual(result["tls"]["context"], CONTEXT)
        self.assertEqual(result["pool"]["pointer1990"], POOL_POINTER)
        self.assertNotEqual(CONTEXT, POOL_POINTER)
        self.assertEqual([row["firstOrdinal"] for row in result["pool"]["sharedSpans"]], [1, 2, 3])
        first = result["records"][13:18]
        final = result["records"][31:36]
        self.assertEqual([row["sourceReader"] for row in first],
                         ["0x14050a8c1", "0x14050a8d1", "0x14050a8d4", "0x14050a8df", "0x14050a8ea"])
        self.assertEqual([row["sourceReader"] for row in first], [row["sourceReader"] for row in final])
        self.assertEqual([row["phase"] for row in result["records"]], ["first"] * 18 + ["final"] * 18)

    def test_short_api_error_and_os_error_keep_original_raw_without_retry(self):
        for ordinal in range(14, 19):
            for failure in ((b"\x12", None), (b"\x12", {"winerror": 299}), OSError(5, "artificial")):
                with self.subTest(ordinal=ordinal, failure=failure):
                    memory = ArtificialPoolMemory()
                    # The flag is one byte: an empty short read is needed at that site.
                    if ordinal == 16 and isinstance(failure, tuple) and failure[1] is None:
                        failure = (b"", None)
                    memory.faults[ordinal] = failure
                    result = pool.collect(memory.read, MODULE, TEB)
                    self.assertEqual(result["status"], "partial")
                    self.assertEqual(result["budget"]["firstPassReads"], ordinal)
                    failed = result["records"][ordinal-1]
                    self.assertIsNotNone(failed["error"])
                    self.assertIn("sourceReader", failed)
                    self.assertEqual(memory.calls.count((failed["address"], failed["requested"])), 1)
                    self.assertLessEqual(result["budget"]["reads"], 36)
                    self.assertLessEqual(result["budget"]["requestedBytes"], 738)

    def test_final_pool_drift_stops_once_and_never_promotes_diagnostic_to_stable(self):
        memory = ArtificialPoolMemory()
        memory.faults[32] = (bytes(8), None)
        result = pool.collect(memory.read, MODULE, TEB)
        self.assertEqual(result["status"], "partial")
        self.assertEqual(len(memory.calls), 32)
        self.assertFalse(result["pool"]["stableInputBytes"])
        self.assertEqual(result["records"][-1]["name"], "pool.pointer1990")
        self.assertEqual(result["records"][-1]["bytesHex"], "00" * 8)

    def test_null_pool_branch_short_globals_are_not_classified_as_real_zero(self):
        for global_qword, ordinal in ((0, 15), (1, 16)):
            memory = ArtificialPoolMemory(pointer=0, global_qword=global_qword)
            memory.faults[ordinal] = (b"", None)
            result = pool.collect(memory.read, MODULE, TEB)
            self.assertEqual(result["status"], "partial")
            self.assertEqual(result["budget"]["firstPassReads"], ordinal)
            self.assertIsNone(result["pool"]["stopBefore"])
            self.assertNotIn("branch", result["pool"])
            self.assertIsNotNone(result["records"][ordinal-1]["error"])
            if ordinal == 15:
                self.assertNotIn((MODULE + 0x5bc7701, 1), memory.calls)

    def test_avatar_partial_does_not_expand_pool_or_substitute_its_field(self):
        memory = ArtificialPoolMemory()
        memory.qword(TLS + 0x19d0, 0)
        result = pool.collect(memory.read, MODULE, TEB)
        self.assertEqual(result["status"], "partial")
        self.assertEqual(result["budget"]["firstPassReads"], 4)
        self.assertNotIn((TLS + 0x1990, 8), memory.calls)
        self.assertIsNone(result["pool"]["stopBefore"])

    def test_only_two_profiles_and_fixed_caps(self):
        with self.assertRaises(ValueError):
            core.ReadLedger(lambda *_: (b"", None), lambda: "artificial", profile="arbitrary")
        memory = ArtificialPoolMemory()
        ledger = core.ReadLedger(memory.read, lambda: "artificial", profile="avatar-r1-pool")
        for _ in range(18):
            ledger.get("artificial", MODULE + 0x5eef080, 4)
        with self.assertRaises(core.ReadStop):
            ledger.get("nineteenth", MODULE + 0x5eef080, 4)
        self.assertEqual(len(memory.calls), 18)
        with self.assertRaises(core.ReadStop):
            core.ReadLedger(memory.read, lambda: "artificial", profile="avatar-r1-pool").get("oversized", POOL_POINTER, 209)

    @unittest.skipUnless(BEFORE.is_file(), "private frozen pre-extension source is unavailable")
    def test_default_avatar_outputs_equal_frozen_source_complete_and_failures(self):
        raw = BEFORE.read_bytes()
        self.assertEqual(core.digest(raw), "be7544d48a6907288c9cadb3db9a50859bf819b5caee9be28c9043d4259afc37")
        namespace = {"__name__": "artificial_frozen_avatar_reference", "__file__": str(BEFORE)}
        exec(compile(raw, str(BEFORE), "exec"), namespace)
        for fault in (None, 3, 14):
            before_memory, current_memory = ArtificialPoolMemory(), ArtificialPoolMemory()
            if fault:
                for memory in (before_memory, current_memory):
                    memory.faults[fault] = (b"", {"winerror": 5})
            previous = namespace["collect"](before_memory.read, MODULE, TEB, now=lambda: "artificial-same-clock")
            current = core.collect(current_memory.read, MODULE, TEB, now=lambda: "artificial-same-clock")
            self.assertEqual(previous, current)
            self.assertEqual(before_memory.calls, current_memory.calls)


class PoolAdapterArtificialTests(unittest.TestCase):
    def sample(self, *, include_pool=True, omit_pool_pin=False, change_after=False, memory_fault=None):
        backend = ArtificialBackend()
        backend.memory = ArtificialPoolMemory(bound=core.U64)
        incoming = epoch()
        if include_pool is True and not omit_pool_pin:
            name = "tools/client72_d02_tls_pool_inputs.py"
            incoming["implementationPins"][name] = core.digest((core.ROOT / name).read_bytes())
        expected = {name: deepcopy(incoming[name]) for name in ("pid", "image", "processCreationTime", "module")}
        identities = [deepcopy(expected), deepcopy(expected)]
        if change_after:
            identities[-1]["processCreationTime"] += 1
        if memory_fault:
            backend.memory.faults[14] = memory_fault
        with patch.object(pool, "verify_sources", return_value={"artificialFixture": True}), \
                patch.object(core, "verify_sources", return_value={"artificialFixture": True}):
            result = adapter.sample_primary(backend, incoming, lambda process, pid: identities.pop(0), include_pool=include_pool)
        return backend, result

    def test_true_uses_combined_budget_same_api_limits_and_third_implementation_pin(self):
        backend, result = self.sample()
        self.assertEqual(result["status"], "candidate")
        self.assertEqual(result["collection"]["budget"]["reads"], 36)
        self.assertEqual(result["collection"]["budget"]["requestedBytes"], 738)
        self.assertEqual(result["apiCounts"], adapter.API_LIMITS)
        self.assertEqual(result["threadBasicOutputBytes"], 96)
        self.assertEqual(backend.closed, [818])
        self.assertIn("tools/client72_d02_tls_pool_inputs.py", result["implementationPins"])
        self.assertFalse(result["naturalPoolAllocatorThreadObserved"])

    def test_default_false_remains_original_13_340_without_pool_reads(self):
        backend, result = self.sample(include_pool=False)
        self.assertEqual(result["status"], "candidate")
        self.assertEqual(result["collection"]["budget"]["firstPassReads"], 13)
        self.assertEqual(result["collection"]["budget"]["requestedBytes"], 680)
        self.assertNotIn("pool", result["collection"])
        self.assertNotIn((TLS + 0x1990, 8), backend.memory.calls)
        self.assertEqual(result["apiCounts"], adapter.API_LIMITS)

    def test_missing_new_pin_or_nonboolean_profile_refuses_before_api_and_closes(self):
        for parameters in ({"omit_pool_pin": True}, {"include_pool": 1}):
            backend, result = self.sample(**parameters)
            self.assertEqual(result["status"], "partial")
            self.assertEqual(result["failureStage"], "entry")
            self.assertEqual(result["apiCounts"]["NtQueryInformationThread"], 0)
            self.assertEqual(backend.memory.calls, [])
            self.assertEqual(backend.closed, [818])

    def test_new_profile_identity_and_pool_read_failures_keep_original_lifecycle(self):
        for parameters in ({"change_after": True}, {"memory_fault": (b"\x12", {"winerror": 299})}):
            backend, result = self.sample(**parameters)
            self.assertEqual(result["status"], "partial")
            self.assertEqual(result["apiCounts"], adapter.API_LIMITS)
            self.assertEqual(backend.closed, [818])
            self.assertTrue(result["primaryCloseVerified"])


if __name__ == "__main__":
    unittest.main()
