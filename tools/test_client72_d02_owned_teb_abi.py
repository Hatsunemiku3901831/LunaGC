"""Offline layout/validator regression only. All API records here are synthetic."""

from copy import deepcopy
import json
import ntpath
import struct
import unittest

from tools import client72_d02_owned_teb_abi as abi


def hx(value):
    return f"0x{value:016x}"


def api_result(value=1):
    return {"attempted": True, "result": hx(value), "lastError": 0}


def times(creation):
    return {"api": api_result(), "creation": hx(creation), "exit": hx(0), "kernel": hx(13), "user": hx(27)}


def query(pid, tid, teb, priority=8):
    # Nonzero 64-bit values above 2**53 and a nonzero padding sentinel remain exact.
    raw = bytearray(struct.pack("<i4xQQQQii", 259, teb, pid, tid, 0xfedcba9876543210, priority, -2))
    raw[4:8] = b"\x13\x57\x9b\xdf"
    return {"attempted": True, "informationClass": 0, "bufferLength": 48, "returnLength": 48,
            "status": 0, "statusHex": "0x00000000", "lastErrorObserved": 0, "rawHex": raw.hex(),
            "exitStatus": 259, "teb": hx(teb), "clientPid": hx(pid), "clientTid": hx(tid),
            "affinityMask": hx(0xfedcba9876543210), "priority": priority, "basePriority": -2}


def synthetic_report():
    pid = 400
    main_tid, worker_tid = 401, 402
    main_teb, worker_teb = 0x123456789abcd000, 0x23456789abcde000
    creation = 0x01dc00123456789a

    def self_observation(tid, teb, tls, created):
        return {"attempted": True, "pid": pid, "tid": tid, "pseudoHandle": hx(0xfffffffffffffffe),
                "sdkNtCurrentTeb": hx(teb), "ntTibSelf": hx(teb), "gs58": hx(tls), "sdkTeb58": hx(tls),
                "times": times(created), "query": query(pid, tid, teb)}

    api = {name: api_result() for name in (
        "GetModuleHandleW", "GetProcAddress", "CreateEventW-ready", "CreateEventW-release", "CreateThread",
        "workerReadySignal", "releaseSignal", "GetExitCodeThread", "CloseHandle-worker", "CloseHandle-ready", "CloseHandle-release",
    )}
    api.update({"GetThreadId": api_result(worker_tid), "GetProcessIdOfThread": api_result(pid),
                "readyWait": api_result(0), "workerAliveWait": api_result(258),
                "workerReleaseWait": api_result(0), "join": api_result(0)})
    return {"record": "result", "schema": "owned-teb-abi-v1", "passed": True, "failure": "none", "stage": "done",
            "nativeArchitecture": 9, "queryCount": 3, "queryCountExact": True, "workerJoined": True, "relationsPassed": True, "waitLimitMs": 5000,
            "layout": abi.candidate_layout(), "mainSelf": self_observation(main_tid, main_teb, 0, creation - 100),
            "workerSelf": self_observation(worker_tid, worker_teb, 0x3456789abcdef000, creation),
            # Priorities intentionally differ; full raw equality must never be required.
            "mainWorkerQuery": query(pid, worker_tid, worker_teb, priority=10), "workerHandleTimes": times(creation),
            "createdWorkerTid": worker_tid, "workerExitCode": 0, "workerFailure": "none", "api": api,
            **{name: False for name in abi.DENIED_FLAGS}}


def encoded(report):
    return (json.dumps({"record": "phase", "stage": "done"}) + "\n" + json.dumps(report) + "\n").encode("utf-8")


class OwnedTebOfflineTests(unittest.TestCase):
    def test_english_chinese_include_records_normalize_windows_paths(self):
        expected = [rf"C:\SDK\Include\{abi.SDK_VERSION}\{name.replace('/', chr(92))}" for name in abi.SDK_PINS]
        for prefix in (b"Note: including file: ", "注意: 包含文件: ".encode("utf-8")):
            with self.subTest(prefix=prefix):
                raw = b"\r\n".join(prefix + b"   " + name.upper().replace("\\", "\\\\").encode("utf-8") for name in expected)
                evidence = abi.sdk_include_evidence(raw, expected)
                self.assertEqual(len(evidence["actualIncludePaths"]), 4)
                self.assertTrue(all(evidence["sdkIncluded"].values()))

    def test_unknown_include_prefix_or_filename_substring_is_not_evidence(self):
        expected = [rf"C:\SDK\Include\{abi.SDK_VERSION}\{name.replace('/', chr(92))}" for name in abi.SDK_PINS]
        for prefix in (b"Unknown: including file: ", b"diagnostic Note: including file: ",
                       "未知: 包含文件: ".encode("utf-8"), b""):
            with self.subTest(prefix=prefix):
                raw = b"\n".join(prefix + name.encode("utf-8") for name in expected)
                self.assertFalse(any(abi.sdk_include_evidence(raw, expected)["sdkIncluded"].values()))
        wrong_paths = b"\n".join(b"Note: including file: " + (name + ".untrusted").encode("utf-8") for name in expected)
        self.assertFalse(any(abi.sdk_include_evidence(wrong_paths, expected)["sdkIncluded"].values()))

    @unittest.skipUnless((abi.OWNED / "implementation-20261110-sol-xhigh-v2/build/compiler.stdout").is_file(),
                         "private original compiler stdout is not available in this checkout")
    def test_actual_v2_compiler_stdout_and_transformed_negative_cases(self):
        directory = abi.OWNED / "implementation-20261110-sol-xhigh-v2"
        original = directory / "build/compiler.stdout"
        abi.pin(original, "ef077cecea6115b442d80c0bb092fef31956818504e4179f37ff7e280a44eac2")
        abi.pin(directory / "plan.json", "738344c4e27a5baf8cab8f02e1ffd0ce237222d222860b7a9bad11aefd126732")
        plan = json.loads((directory / "plan.json").read_text(encoding="utf-8"))
        expected = [item["path"] for key, item in plan["inputs"].items() if key.startswith("sdk:")]
        self.assertEqual(len(expected), 4)
        raw = original.read_bytes()
        chinese = "注意: 包含文件: ".encode("utf-8")
        self.assertIn(chinese, raw)
        self.assertIn(b"\\\\include", raw)
        for sample in (raw, raw.replace(chinese, b"Note: including file: ")):
            with self.subTest(english=sample != raw):
                self.assertTrue(all(abi.sdk_include_evidence(sample, expected)["sdkIncluded"].values()))
        unknown = raw.replace(chinese, "未知: 包含文件: ".encode("utf-8"))
        self.assertFalse(any(abi.sdk_include_evidence(unknown, expected)["sdkIncluded"].values()))
        changed = abi.sdk_include_evidence(raw.replace(b"winternl.h", b"winternl.h.untrusted"), expected)
        self.assertEqual([ntpath.basename(name) for name, present in changed["sdkIncluded"].items() if not present], ["winternl.h"])

    def test_candidate_default_x64_layout_is_only_a_candidate(self):
        layout = abi.candidate_layout()
        self.assertEqual((layout["tbiSize"], layout["tbiAlign"]), (48, 8))
        self.assertEqual([layout[name] for name in ("exitStatusOffset", "tebOffset", "clientIdOffset",
                                                   "affinityOffset", "priorityOffset", "basePriorityOffset")],
                         [0, 8, 16, 32, 40, 44])
        self.assertEqual((layout["ntTibSize"], layout["ntTibSelfOffset"], layout["tlsPointerOffset"]), (56, 48, 88))

    def test_nonzero_64bit_raw_and_different_priorities_preserved(self):
        report = synthetic_report()
        # A live thread's exit FILETIME has no required value; retain without judging it.
        report["mainSelf"]["times"]["exit"] = hx(0x456789abcdef0123)
        report["workerSelf"]["times"]["exit"] = hx(0x56789abcdef01234)
        report["workerHandleTimes"]["exit"] = hx(0x6789abcdef012345)
        result = abi.validate_stdout(encoded(report))
        self.assertEqual(result["queryCount"], 3)
        self.assertEqual(result["tlsZeroObserved"], [True, False])
        self.assertNotEqual(report["mainWorkerQuery"]["rawHex"], report["workerSelf"]["query"]["rawHex"])

    def test_each_abi_or_identity_violation_is_rejected(self):
        cases = [
            (("mainSelf", "query", "status"), -1073741820),
            (("mainSelf", "query", "statusHex"), "0xc0000004"),
            (("mainSelf", "query", "returnLength"), 47),
            (("mainWorkerQuery", "informationClass"), 11),
            (("mainWorkerQuery", "rawHex"), "00" * 47),
            (("mainWorkerQuery", "clientTid"), hx(99)),
            (("mainSelf", "sdkNtCurrentTeb"), hx(0)),
            (("workerSelf", "ntTibSelf"), hx(123)),
            (("workerSelf", "sdkTeb58"), hx(123)),
            (("workerSelf", "times", "api", "result"), hx(0)),
            (("workerHandleTimes", "creation"), hx(0x01dc00123456789b)),
            (("createdWorkerTid",), 999),
            (("api", "GetProcessIdOfThread", "result"), hx(999)),
            (("api", "workerAliveWait", "result"), hx(0)),
            (("api", "join", "result"), hx(258)),
            (("api", "CloseHandle-worker", "result"), hx(0)),
            (("workerJoined",), False),
            (("queryCount",), 4),
            (("queryCountExact",), False),
            (("layout", "tbiAlign"), 1),
            (("layout", "tlsPointerOffset"), 80),
            (("nativeArchitecture",), 12),
            (("gsBaseRegisterReadDirectly",), True),
            (("liveApproved",), True),
            (("capturedWriteApproved",), True),
            (("naturalAvatarAllocatorThreadObserved",), True),
            (("workerExitCode",), 1),
            (("passed",), False),
        ]
        for path, value in cases:
            with self.subTest(path=path):
                report = deepcopy(synthetic_report())
                current = report
                for name in path[:-1]:
                    current = current[name]
                current[path[-1]] = value
                with self.assertRaises(ValueError):
                    abi.validate_stdout(encoded(report))

    def test_raw_bytes_are_authoritative_even_when_printed_identity_matches(self):
        report = synthetic_report()
        raw = bytearray.fromhex(report["mainWorkerQuery"]["rawHex"])
        raw[8] ^= 1
        report["mainWorkerQuery"]["rawHex"] = raw.hex()
        with self.assertRaisesRegex(ValueError, "retained raw"):
            abi.validate_stdout(encoded(report))

    def test_incomplete_or_multiple_result_records_rejected(self):
        complete = encoded(synthetic_report())
        for raw in (b"", b"{}\n", b"[]\n", b"not-json\n", complete + complete,
                    b'{"record":"phase","stage":"cleanup"}\n'):
            with self.subTest(raw=raw[:30]):
                with self.assertRaises(ValueError):
                    abi.validate_stdout(raw)

    def test_full_width_hex_is_required(self):
        for value in ("0x1", "ffffffffffffffff", "0x10000000000000000", "0xgggggggggggggggg"):
            with self.subTest(value=value):
                with self.assertRaises(ValueError):
                    abi.hex64(value)


if __name__ == "__main__":
    unittest.main()
