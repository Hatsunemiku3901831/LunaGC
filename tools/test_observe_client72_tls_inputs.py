"""Artificial launch/API callbacks only; never instantiate a Windows backend."""

from copy import deepcopy
import ctypes as C
from pathlib import Path
import struct
from types import SimpleNamespace
import unittest
from unittest.mock import Mock, patch

from tools import client72_d02_tls_inputs as inputs
from tools import observe_client72_tls_inputs as card
from tools.test_client72_d02_tls_inputs import ArtificialMemory, MODULE, TEB


def basic(teb=TEB, pid=37, tid=38, priority=8):
    raw = struct.pack("<i4xQQQQii", 259, teb, pid, tid, 0xfedcba9876543210, priority, -2)
    return {"attempted": True, "informationClass": 0, "bufferLength": 48, "returnLength": 48,
            "status": 0, "statusHex": "0x00000000", "lastErrorObserved": 0, "rawHex": raw.hex()}


class ArtificialBackend:
    artificial = True

    def __init__(self):
        self.primary = {"source": "launch-primary-thread", "processHandle": 919, "threadHandle": 818,
                        "pid": 37, "tid": 38, "target": r"C:\artificial\YuanShen.exe", "cwd": r"C:\artificial", "creationFlags": 0}
        self.launch_result = {"artificialFixture": True, "creationFlags": 0, "result": 1}
        self.memory, self.closed, self.calls = ArtificialMemory(), [], []
        self.basics = [basic(), basic(priority=11)]
        self.alive = [258, 258]
        self.fail = {}

    def answer(self, name, value):
        self.calls.append(name)
        if name in self.fail:
            failure = self.fail[name]
            if isinstance(failure, OSError):
                raise failure
            return deepcopy(failure)
        return value

    def thread_id(self):
        return self.answer("GetThreadId", {"attempted": True, "result": 38, "lastError": 0})

    def thread_pid(self):
        return self.answer("GetProcessIdOfThread", {"attempted": True, "result": 37, "lastError": 0})

    def thread_times(self):
        return self.answer("GetThreadTimes", {"attempted": True, "result": 1, "lastError": 0,
            "creation": 0x01dc123456789abc, "exit": 0xfedcba9876543210, "kernel": 13, "user": 27})

    def thread_alive(self):
        return self.answer("WaitForSingleObject", {"attempted": True, "result": self.alive.pop(0), "lastError": 0, "timeoutMs": 0})

    def thread_basic(self):
        return self.answer("NtQueryInformationThread", deepcopy(self.basics.pop(0)))

    def read(self, handle, address, width):
        if handle != 919:
            raise AssertionError("Read did not use the original process handle")
        return self.memory.read(address, width)

    def close_primary(self):
        self.closed.append(818)
        return self.answer("CloseHandle-primary", {"attempted": True, "result": 1, "lastError": 0, "handle": 818})


def epoch():
    return {"epochId": "artificial-not-natural", "pid": 37, "image": r"C:\artificial\YuanShen.exe",
            "processCreationTime": 0x01dc123456789000,
            "module": {"name": "YuanShen.exe", "path": r"C:\artificial\YuanShen.exe", "base": MODULE, "size": 0x6000000},
            "stockInputPins": dict(card.monitor.EXPECTED),
            "implementationPins": {name: inputs.digest((inputs.ROOT / name).read_bytes()) for name in (
                "tools/client72_d02_tls_inputs.py", "tools/observe_client72_tls_inputs.py")}}


class RetainedPrimaryArtificialTests(unittest.TestCase):
    def setUp(self):
        self.backend, self.epoch = ArtificialBackend(), epoch()
        self.identities = [{name: deepcopy(self.epoch[name]) for name in ("pid", "image", "processCreationTime", "module")} for _ in range(2)]
        self.identity_calls = []
        gate = patch.object(inputs, "verify_sources", return_value={"artificialFixture": True, "sourcePins": dict(inputs.SOURCE_PINS)})
        self.gate = gate.start()
        self.addCleanup(gate.stop)

    def identity(self, process, pid):
        self.identity_calls.append((process, pid))
        return self.identities.pop(0)

    def run_sample(self):
        return card.sample_primary(self.backend, self.epoch, self.identity)

    def test_complete_budget_full_identity_and_dynamic_priority(self):
        result = self.run_sample()
        self.assertEqual(result["status"], "candidate")
        self.assertTrue(result["artificialFixture"])
        self.assertEqual(result["apiCounts"], card.API_LIMITS)
        self.assertEqual(result["threadBasicOutputBytes"], 96)
        self.assertEqual(result["collection"]["budget"]["requestedBytes"], 680)
        self.assertEqual(self.backend.closed, [818])
        self.assertEqual(self.identity_calls, [(919, 37), (919, 37)])
        self.assertTrue(result["endIdentityVerified"])
        self.assertNotEqual(result["threadBefore"]["rawHex"], result["threadAfter"]["rawHex"])
        self.assertFalse(result["naturalAvatarAllocatorThreadObserved"])
        self.assertFalse(result["canReplayPop"])

    def test_before_process_identity_failure_closes_without_thread_or_memory_api(self):
        self.identities[0]["module"]["base"] += 1
        result = self.run_sample()
        self.assertEqual(result["failureStage"], "identity-before")
        self.assertEqual(result["identityObservations"][0]["value"]["module"]["base"], MODULE + 1)
        self.assertEqual(self.backend.calls, ["CloseHandle-primary"])
        self.assertEqual(self.backend.memory.calls, [])
        self.assertEqual(self.backend.closed, [818])

    def test_after_creation_module_or_image_change_is_partial_and_preserved(self):
        for field in ("processCreationTime", "image", "module"):
            with self.subTest(field=field):
                self.setUp()
                if field == "module":
                    self.identities[-1][field]["size"] += 1
                elif field == "image":
                    self.identities[-1][field] += ".other"
                else:
                    self.identities[-1][field] += 1
                result = self.run_sample()
                self.assertEqual(result["status"], "partial")
                self.assertEqual(result["failureStage"], "identity-after")
                self.assertEqual(len(result["identityObservations"]), 2)
                self.assertEqual(self.backend.closed, [818])

    def test_basic_status_length_and_raw_identity_fail_without_fallback(self):
        for query in ({**basic(), "status": -1073741820, "statusHex": "0xc0000004"},
                      {**basic(), "returnLength": 47}, {**basic(), "rawHex": "00" * 47},
                      basic(pid=99), basic(tid=99), basic(teb=0)):
            with self.subTest(query=query):
                self.setUp()
                self.backend.basics[0] = query
                result = self.run_sample()
                self.assertEqual(result["status"], "partial")
                self.assertEqual(result["apiCounts"]["NtQueryInformationThread"], 1)
                self.assertEqual(self.backend.memory.calls, [])
                kept = next(row for row in result["apiRecords"] if row["name"] == "NtQueryInformationThread")
                self.assertEqual(kept["result"]["rawHex"], query["rawHex"])
                self.assertEqual(self.backend.closed, [818])

    def test_changed_teb_after_or_thread_exit_after_is_partial(self):
        self.backend.basics[-1] = basic(teb=TEB + 0x1000)
        result = self.run_sample()
        self.assertEqual(result["reason"], "primary-TEB-changed")
        self.assertEqual(result["threadAfter"]["decoded"]["teb"], TEB + 0x1000)
        self.assertEqual(self.backend.closed, [818])
        self.setUp()
        self.backend.alive[-1] = 0
        result = self.run_sample()
        self.assertEqual(result["reason"], "primary-not-alive-after")
        self.assertEqual(result["apiCounts"]["WaitForSingleObject"], 2)
        self.assertEqual(self.backend.closed, [818])

    def test_handle_api_failure_or_os_error_preserves_error_and_stops(self):
        for name, failure in (("GetThreadId", {"attempted": True, "result": 0, "lastError": 6}),
                              ("GetProcessIdOfThread", {"attempted": True, "result": 99, "lastError": 0}),
                              ("GetThreadTimes", OSError(5, "artificial"))):
            with self.subTest(name=name):
                self.setUp()
                self.backend.fail[name] = failure
                result = self.run_sample()
                self.assertEqual(result["status"], "partial")
                self.assertEqual(self.backend.memory.calls, [])
                self.assertEqual(self.backend.closed, [818])
                self.assertEqual(result["apiCounts"]["NtQueryInformationThread"], 0)

    def test_final_read_failure_and_close_failure_retained_together(self):
        self.backend.memory.faults[14] = (b"", {"winerror": 5})
        self.backend.fail["CloseHandle-primary"] = {"attempted": True, "result": 0, "lastError": 6, "handle": 818}
        result = self.run_sample()
        self.assertEqual(result["status"], "partial")
        self.assertEqual(result["failureStage"], "collection")
        self.assertEqual(result["collection"]["budget"]["reads"], 14)
        self.assertEqual(result["primaryClose"]["lastError"], 6)
        self.assertFalse(result["primaryCloseVerified"])
        self.assertEqual(self.backend.closed, [818])

    def test_close_failure_alone_forces_partial_after_complete_collection(self):
        self.backend.fail["CloseHandle-primary"] = {"attempted": True, "result": 0, "lastError": 6, "handle": 818}
        result = self.run_sample()
        self.assertEqual(result["status"], "partial")
        self.assertEqual(result["failureStage"], "cleanup")
        self.assertEqual(result["collection"]["status"], "candidate")
        self.assertTrue(result["endIdentityVerified"])
        self.assertEqual(result["primaryClose"]["lastError"], 6)
        self.assertEqual(self.backend.closed, [818])

    def test_after_query_error_keeps_raw_without_another_memory_attempt(self):
        self.backend.basics[-1].update(status=-1073741820, statusHex="0xc0000004")
        result = self.run_sample()
        self.assertEqual(result["status"], "partial")
        self.assertEqual(result["failureStage"], "thread-after")
        self.assertEqual(result["threadAfter"]["status"], -1073741820)
        self.assertEqual(len(result["threadAfter"]["rawHex"]), 96)
        self.assertEqual(len(self.backend.memory.calls), 26)
        self.assertEqual(result["apiCounts"]["NtQueryInformationThread"], 2)
        self.assertEqual(self.backend.closed, [818])

    def test_exit_before_snapshot_does_not_read_or_reselect(self):
        self.backend.alive[0] = 0
        result = self.run_sample()
        self.assertEqual(result["reason"], "primary-not-alive-before")
        self.assertEqual(result["apiCounts"]["NtQueryInformationThread"], 0)
        self.assertEqual(self.backend.memory.calls, [])
        self.assertEqual(self.backend.closed, [818])

    def test_source_pin_or_epoch_refusal_still_closes_original_primary(self):
        self.gate.side_effect = ValueError("artificial source mismatch")
        result = self.run_sample()
        self.assertEqual(result["failureStage"], "entry")
        self.assertEqual(self.backend.calls, ["CloseHandle-primary"])
        self.setUp()
        self.epoch["implementationPins"] = {}
        result = self.run_sample()
        self.assertEqual(result["failureStage"], "entry")
        self.assertEqual(self.backend.calls, ["CloseHandle-primary"])


class LaunchWrapperArtificialTests(unittest.TestCase):
    def base(self, success=True):
        kernel = SimpleNamespace(**{name: Mock() for name in (
            "GetThreadId", "GetProcessIdOfThread", "GetThreadTimes", "ReadProcessMemory", "WaitForSingleObject", "CloseHandle")})
        kernel.CloseHandle.return_value = 1

        def create(target, command, process_security, thread_security, inherit, flags, environment, cwd, startup, info):
            self.launch_arguments = (target, command.value, inherit, flags, environment, cwd)
            if success:
                output = C.cast(info, C.POINTER(card.monitor.PROCESS_INFORMATION)).contents
                output.hProcess, output.hThread, output.dwProcessId, output.dwThreadId = 919, 818, 37, 38
            return int(success)

        kernel.CreateProcessW = create
        return SimpleNamespace(k=kernel, nt=Mock(), close=Mock())

    def test_flags_zero_original_thread_retained_two_return_interface_and_close_once(self):
        base = self.base()
        wrapper = card.RetainedPrimaryBackend(base)
        self.assertEqual(wrapper.launch(Path(r"C:\artificial\YuanShen.exe"), Path(r"C:\artificial")), (919, 37))
        self.assertEqual(self.launch_arguments[2:5], (False, 0, None))
        self.assertEqual(wrapper.primary["threadHandle"], 818)
        self.assertEqual(wrapper.primary["tid"], 38)
        base.k.CloseHandle.assert_not_called()
        self.assertTrue(wrapper.launch_result["processInformationRawHex"])
        wrapper.close_primary()
        wrapper.close(919)
        base.k.CloseHandle.assert_called_once_with(818)
        base.close.assert_called_once_with(919)
        with self.assertRaises(ValueError):
            wrapper.launch(Path(r"C:\artificial\YuanShen.exe"), Path(r"C:\artificial"))

    def test_failed_launch_keeps_raw_result_without_primary_or_close(self):
        base = self.base(success=False)
        wrapper = card.RetainedPrimaryBackend(base)
        with self.assertRaises(OSError):
            wrapper.launch(Path(r"C:\artificial\YuanShen.exe"), Path(r"C:\artificial"))
        self.assertIsNone(wrapper.primary)
        self.assertEqual(wrapper.launch_result["result"], 0)
        self.assertTrue(wrapper.launch_result["processInformationRawHex"])
        base.k.CloseHandle.assert_not_called()


if __name__ == "__main__":
    unittest.main()
