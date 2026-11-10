"""Freeze/build/check the independent owned-thread probe; never inspect a target.

prepare is read-only discovery plus new private files. build and run-two are
explicit separate steps. Only run-two executes the probe, at most twice, without
retry. JSONL/raw streams and real invocation metadata are retained on failure.
"""

from __future__ import annotations

import argparse
import ctypes
from datetime import datetime, timezone
import hashlib
import json
import ntpath
import os
from pathlib import Path
import struct
import subprocess
import sys


REPO = Path(__file__).resolve().parents[1]
PRIVATE = REPO / "local/migration72/d02-resume-20261110/a-tls-preparation"
OWNED = PRIVATE / "owned-teb"
SPEC = "docs/CLIENT_72_D02_TLS_INPUT_SPEC_20261110.md"
SPEC_SHA = "81c85ace479805daf2b41eeacb2311b09d2b08d2f24dfd3ac7f654bc43b3cfe3"
INPUT_SHA = "953252c196051d1b7ee697d54c44ba7e02ec1427ce605f5e48dca81cba9af94e"
SDK_VERSION = "10.0.26100.0"
PHNT_COMMIT = "53fbbdc5b5d2b08761db1c7b26bfa8c820924356"
SDK_PINS = {
    "um/processthreadsapi.h": "436ae4cbb017d1cdb381aaf2119a3f89b04bae257fd4c881ef251daa02533540",
    "um/winternl.h": "a43424486349c38f697c009512dfe4eb8fca733d7665afab42f50752170b9785",
    "um/winnt.h": "8693f0ad4ada355c912d40ca41ce929b480e92291bda2f9227acf56736bffbff",
    "shared/basetsd.h": "d35b73f885d7ef7dfd27fce3762c190c1c952a21e10a3945bcb3d1860c673b12",
}
PHNT_PINS = {
    "manifest.json": "0bb50a41fe14f61b26ac69603f0c92a597bdaf3e2c69ecf2c00089bf645e5941",
    "result.json": "382be666a0f91353495d474d0091a7ac9578c91973df025613a008304e8c646d",
    "request-ledger.json": "0d1c62d6dc1de854ce929270fea0282356728b779099fa25f125c17d27091a53",
    "responses/04-file-ntpsapi_h.body": "3477b3fa901715c5ad139ae154f86d6c36f3415962731d6c0de0dd3c8417f4d8",
    "responses/05-file-ntpebteb_h.body": "9869d45323706a6bf7cb3ed168b4ca6560aef194f1c6a4d37aaad74a356ad248",
    "responses/06-file-LICENSE.body": "ad2ab542c56c606e4c19d66a7f3cdcd3ef83beffb638e7c3893b22c7a6a6c0df",
}
DENIED_FLAGS = (
    "gsBaseRegisterReadDirectly", "liveApproved", "replayApproved", "capturedWriteApproved",
    "R1Only13_340Approved", "pool15_82Approved", "naturalAvatarAllocatorThreadObserved",
)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    prepare = commands.add_parser("prepare", help="pin existing inputs and create an unexecuted build plan")
    prepare.add_argument("--output", type=Path, required=True)
    for command in ("build", "run-two"):
        child = commands.add_parser(command)
        child.add_argument("--plan", type=Path, required=True)
    validate = commands.add_parser("validate", help="validate an already captured result; no probe execution")
    validate.add_argument("--stdout", type=Path, required=True)
    commands.add_parser("layout", help="pure Python candidate layout only, never an OS ABI result")
    args = parser.parse_args()
    if args.command == "prepare":
        result = prepare_plan(args.output)
    elif args.command == "build":
        result = build(args.plan)
    elif args.command == "run-two":
        result = run_two(args.plan)
    elif args.command == "validate":
        result = {"passed": True, "validation": validate_stdout(args.stdout.read_bytes())}
    else:
        result = {"candidateLayoutOnly": True, "osAbiValidated": False, "layout": candidate_layout()}
    print(json.dumps(result, ensure_ascii=False, indent=2))
    return 0 if result.get("passed", True) else 1


def utc() -> str:
    return datetime.now(timezone.utc).isoformat()


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def write_json(path: Path, value: object) -> None:
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def pin(path: Path, expected: str | None = None) -> dict:
    actual = sha256(path)
    if expected is not None and actual != expected:
        raise ValueError(f"pin mismatch: {path}")
    return {"path": str(path.resolve()), "bytes": path.stat().st_size, "sha256": actual}


def private_output(path: Path) -> Path:
    resolved = path.resolve()
    if not resolved.is_relative_to(OWNED.resolve()) or resolved == OWNED.resolve():
        raise ValueError("output must be a new child directory of a-tls-preparation/owned-teb")
    return resolved


def capture(argv: list[str], cwd: Path, prefix: Path, timeout: int) -> dict:
    started = utc()
    timed_out = False
    with subprocess.Popen(argv, cwd=cwd, stdout=subprocess.PIPE, stderr=subprocess.PIPE) as process:
        try:
            stdout, stderr = process.communicate(timeout=timeout)
        except subprocess.TimeoutExpired:
            timed_out = True
            process.kill()  # Only the immediate child created by this invocation.
            stdout, stderr = process.communicate()
        result = {
            "argv": argv, "cwd": str(cwd), "startedUtc": started, "endedUtc": utc(),
            "pid": process.pid, "exitCode": process.returncode, "timeoutSeconds": timeout,
            "timedOut": timed_out, "timeoutAction": "kill-owned-child" if timed_out else "none",
        }
    stdout_path, stderr_path = prefix.with_suffix(".stdout"), prefix.with_suffix(".stderr")
    stdout_path.write_bytes(stdout)
    stderr_path.write_bytes(stderr)
    result["stdout"] = pin(stdout_path)
    result["stderr"] = pin(stderr_path)
    write_json(prefix.with_suffix(".invocation.json"), result)
    return result


def discover_toolchain(output: Path) -> dict:
    if os.name != "nt":
        raise ValueError("prepare/build/run-two require Windows; layout and validation are offline")
    program_files = Path(os.environ["ProgramFiles(x86)"])
    vswhere = program_files / "Microsoft Visual Studio/Installer/vswhere.exe"
    discovery = capture([
        str(vswhere), "-latest", "-products", "*", "-requires",
        "Microsoft.VisualStudio.Component.VC.Tools.x86.x64", "-property", "installationPath",
    ], output, output / "vswhere", 10)
    if discovery["exitCode"] != 0 or discovery["timedOut"]:
        raise ValueError("vswhere discovery failed; retained invocation has no fallback")
    installation = Path(Path(discovery["stdout"]["path"]).read_text(encoding="utf-8-sig").strip())
    vc = installation / "VC"
    version_file = vc / "Auxiliary/Build/Microsoft.VCToolsVersion.default.txt"
    version = version_file.read_text(encoding="utf-8-sig").strip()
    tools = vc / "Tools/MSVC" / version
    sdk = program_files / "Windows Kits/10"
    include = sdk / "Include" / SDK_VERSION
    required = {
        "vswhere": vswhere, "vcvars64": vc / "Auxiliary/Build/vcvars64.bat",
        "vcvarsall": vc / "Auxiliary/Build/vcvarsall.bat", "vcToolsVersionFile": version_file,
        "compiler": tools / "bin/Hostx64/x64/cl.exe", "linker": tools / "bin/Hostx64/x64/link.exe",
        "compilerFrontEnd": tools / "bin/Hostx64/x64/c1xx.dll",
        "compilerBackEnd": tools / "bin/Hostx64/x64/c2.dll",
        "kernel32Lib": sdk / "Lib" / SDK_VERSION / "um/x64/kernel32.lib",
        "ucrtLib": sdk / "Lib" / SDK_VERSION / "ucrt/x64/libucrt.lib",
        "staticCrtLib": tools / "lib/x64/libcmt.lib",
    }
    return {
        "discovery": discovery, "msvcVersion": version, "sdkVersion": SDK_VERSION,
        "includeRoot": str(include), "hostArchitecture": "x64", "targetArchitecture": "x64",
        "files": {name: pin(path) for name, path in required.items()},
    }


def prepare_plan(output: Path) -> dict:
    output = private_output(output)
    output.mkdir(parents=True, exist_ok=False)
    sources = {name: pin(REPO / "tools" / name) for name in (
        "client72_d02_owned_teb_probe.cpp", "client72_d02_owned_teb_abi.py",
        "test_client72_d02_owned_teb_abi.py",
    )}
    inputs = {
        "spec": pin(REPO / SPEC, SPEC_SHA),
        "implementationInput": pin(PRIVATE / "owned-abi-implementation.private.json", INPUT_SHA),
    }
    phnt = PRIVATE / "abi-retrieval-20261110T001446Z"
    inputs.update({f"phnt:{name}": pin(phnt / name, expected) for name, expected in PHNT_PINS.items()})
    toolchain = discover_toolchain(output)
    include = Path(toolchain["includeRoot"])
    inputs.update({f"sdk:{name}": pin(include / name, expected) for name, expected in SDK_PINS.items()})
    snapshots = output / "source"
    snapshots.mkdir()
    for name, source in sources.items():
        target = snapshots / name
        target.write_bytes(Path(source["path"]).read_bytes())
        source["snapshot"] = pin(target, source["sha256"])
    build_directory = output / "build"
    executable = build_directory / "owned_teb_probe.exe"
    compiler = toolchain["files"]["compiler"]["path"]
    compiler_argv = [
        compiler, "/nologo", "/std:c++17", "/EHsc", "/W4", "/Od", "/MT", "/FC", "/Bv",
        "/showIncludes", "/DWIN32_LEAN_AND_MEAN", "/DNOMINMAX",
        f"/Fo{build_directory / 'owned_teb_probe.obj'}", f"/Fe{executable}",
        str(snapshots / "client72_d02_owned_teb_probe.cpp"),
        "/link", "/MACHINE:X64", "/INCREMENTAL:NO", f"/PDB:{build_directory / 'owned_teb_probe.pdb'}",
    ]
    # cmd.exe executes a fixed private script. No caller-provided shell fragments,
    # complete environment dump, downloads, installs, or shared build outputs.
    command = subprocess.list2cmdline(compiler_argv)
    script = output / "build-owned.cmd"
    script.write_text(
        "@echo off\r\nsetlocal\r\nset VSLANG=1033\r\n"
        f'call "{toolchain["files"]["vcvars64"]["path"]}" {SDK_VERSION} '
        f'-vcvars_ver={toolchain["msvcVersion"]}\r\n'
        "if errorlevel 1 exit /b %errorlevel%\r\n"
        'if not "%VSCMD_ARG_TGT_ARCH%"=="x64" exit /b 81\r\n'
        f'if not "%WindowsSDKVersion%"=="{SDK_VERSION}\\" exit /b 82\r\n'
        f'cd /d "{build_directory}"\r\n{command}\r\nexit /b %errorlevel%\r\n',
        encoding="utf-8", newline="",
    )
    plan = {
        "schema": "owned-teb-build-plan-v1", "createdUtc": utc(), "output": str(output),
        "sources": sources, "inputs": inputs, "phntCommit": PHNT_COMMIT,
        "toolchain": toolchain, "compilerArgv": compiler_argv, "buildScript": pin(script),
        "buildDirectory": str(build_directory), "executable": str(executable),
        "buildArgv": [str(Path(os.environ["SystemRoot"]) / "System32/cmd.exe"), "/d", "/c", str(script)],
        "probeArgv": [str(executable)], "plannedFreshProcesses": 2,
        "plannedQueriesPerFresh": 3, "plannedTotalQueries": 6,
        "probeTimeoutSeconds": 30, "candidateLayoutOnly": candidate_layout(),
        "ownedExecuted": False, **{flag: False for flag in DENIED_FLAGS},
    }
    plan_path = output / "plan.json"
    write_json(plan_path, plan)
    return {"passed": True, "preparedOnly": True, "plan": pin(plan_path), "compilerArgv": compiler_argv}


def load_plan(path: Path) -> dict:
    path = path.resolve()
    plan = json.loads(path.read_text(encoding="utf-8"))
    if plan["schema"] != "owned-teb-build-plan-v1" or path.parent != private_output(Path(plan["output"])):
        raise ValueError("wrong plan schema or private output directory")
    for source in plan["sources"].values():
        pin(Path(source["path"]), source["sha256"])
        pin(Path(source["snapshot"]["path"]), source["sha256"])
    for item in [*plan["inputs"].values(), *plan["toolchain"]["files"].values(), plan["buildScript"]]:
        pin(Path(item["path"]), item["sha256"])
    return plan


def build(path: Path) -> dict:
    plan = load_plan(path)
    directory = Path(plan["buildDirectory"])
    directory.mkdir(exist_ok=False)
    invocation = capture(plan["buildArgv"], directory, directory / "compiler", 30)
    result = {"schema": "owned-teb-build-v1", "plan": pin(path), "invocation": invocation,
              "compilerArgv": plan["compilerArgv"], "passed": False}
    executable = Path(plan["executable"])
    if executable.exists():
        result["executable"] = pin(executable)
    if not invocation["timedOut"] and invocation["exitCode"] == 0 and executable.exists():
        # /showIncludes proves the compiler consumed the pinned SDK headers.
        combined = Path(invocation["stdout"]["path"]).read_bytes() + Path(invocation["stderr"]["path"]).read_bytes()
        expected = [entry["path"] for name, entry in plan["inputs"].items() if name.startswith("sdk:")]
        result.update(sdk_include_evidence(combined, expected))
        result["passed"] = all(result["sdkIncluded"].values())
    write_json(directory / "build-report.json", result)
    return result


def sdk_include_evidence(raw: bytes, expected: list[str]) -> dict:
    """Count only anchored MSVC English or locally observed UTF-8 Chinese records."""
    prefixes = (b"Note: including file: ", "注意: 包含文件: ".encode("utf-8"))
    includes = []
    for line in raw.splitlines():
        for prefix in prefixes:
            if line.startswith(prefix):
                includes.append(line[len(prefix):].strip().decode("utf-8"))
                break
    # Windows path semantics apply even when these offline tests run elsewhere.
    # normpath collapses the duplicated separators in the captured SDK paths.
    normalized = {ntpath.normcase(ntpath.normpath(item)) for item in includes}
    return {
        "actualIncludePaths": includes,
        "sdkIncluded": {name: ntpath.normcase(ntpath.normpath(name)) in normalized for name in expected},
    }


def run_two(path: Path) -> dict:
    plan = load_plan(path)
    build_report_path = Path(plan["buildDirectory"]) / "build-report.json"
    build_report = json.loads(build_report_path.read_text(encoding="utf-8"))
    if build_report["passed"] is not True:
        raise ValueError("build did not pass; no probe execution")
    if build_report["plan"]["sha256"] != sha256(path):
        raise ValueError("build report belongs to another plan")
    pin(Path(plan["executable"]), build_report["executable"]["sha256"])
    directory = Path(plan["output"]) / "runs"
    directory.mkdir(exist_ok=False)
    result = {
        "schema": "owned-teb-two-fresh-v1", "plan": pin(path), "buildReport": pin(build_report_path),
        "plannedFreshProcesses": 2, "plannedTotalQueries": 6, "runs": [], "passed": False,
        **{flag: False for flag in DENIED_FLAGS},
    }
    for index in (1, 2):
        invocation = capture(plan["probeArgv"], directory, directory / f"fresh-{index}", 30)
        entry = {"fresh": index, "invocation": invocation, "passed": False}
        result["runs"].append(entry)
        # Failed processes are still parsed when possible, preserving diagnostics.
        try:
            entry["validation"] = validate_stdout(Path(invocation["stdout"]["path"]).read_bytes())
            entry["passed"] = invocation["exitCode"] == 0 and not invocation["timedOut"]
            if entry["validation"]["pid"] != invocation["pid"]:
                raise ValueError("reported PID differs from the actually created child")
        except (ValueError, KeyError, TypeError, UnicodeError) as error:
            entry["validationError"] = str(error)
            entry["passed"] = False
        write_json(directory / "two-fresh-report.json", result)
        if not entry["passed"]:
            break
    result["executedFreshProcesses"] = len(result["runs"])
    result["validatedQueryCount"] = sum(entry.get("validation", {}).get("queryCount", 0) for entry in result["runs"])
    result["passed"] = len(result["runs"]) == 2 and all(entry["passed"] for entry in result["runs"])
    if result["passed"]:
        first, second = (entry["validation"] for entry in result["runs"])
        result["freshDifferences"] = {name: first[name] != second[name] for name in (
            "pid", "mainTid", "workerTid", "mainTeb", "workerTeb",
        )}
        result["conclusion"] = "This machine's two native-x64 owned-thread runs match the candidate class0 layout and independent TEB/identity/GS58 relations only."
    write_json(directory / "two-fresh-report.json", result)
    return result


def candidate_layout() -> dict:
    """Fixed-width candidate C layout, not API execution or OS ABI evidence."""
    class ClientId(ctypes.Structure):
        _fields_ = [("UniqueProcess", ctypes.c_uint64), ("UniqueThread", ctypes.c_uint64)]

    class FiberData(ctypes.Union):
        _fields_ = [("FiberData", ctypes.c_uint64), ("Version", ctypes.c_uint32)]

    class NtTib(ctypes.Structure):
        _fields_ = [("ExceptionList", ctypes.c_uint64), ("StackBase", ctypes.c_uint64),
                    ("StackLimit", ctypes.c_uint64), ("SubSystemTib", ctypes.c_uint64),
                    ("Fiber", FiberData), ("ArbitraryUserPointer", ctypes.c_uint64), ("Self", ctypes.c_uint64)]

    class Tbi(ctypes.Structure):
        _fields_ = [("ExitStatus", ctypes.c_int32), ("TebBaseAddress", ctypes.c_uint64),
                    ("ClientId", ClientId), ("AffinityMask", ctypes.c_uint64),
                    ("Priority", ctypes.c_int32), ("BasePriority", ctypes.c_int32)]

    class TebPrefix(ctypes.Structure):
        _fields_ = [("NtTib", NtTib), ("EnvironmentPointer", ctypes.c_uint64), ("ClientId", ClientId),
                    ("ActiveRpcHandle", ctypes.c_uint64), ("ThreadLocalStoragePointer", ctypes.c_uint64)]

    return {
        "pointerSize": 8, "kaffinitySize": 8, "longSize": 4, "ntstatusSize": 4, "kprioritySize": 4,
        "pointerAlign": 8, "kaffinityAlign": 8, "longAlign": 4, "ntstatusAlign": 4, "kpriorityAlign": 4,
        "clientIdSize": ctypes.sizeof(ClientId), "clientIdAlign": ctypes.alignment(ClientId),
        "clientIdProcessOffset": ClientId.UniqueProcess.offset, "clientIdThreadOffset": ClientId.UniqueThread.offset,
        "ntTibSize": ctypes.sizeof(NtTib), "ntTibAlign": ctypes.alignment(NtTib), "ntTibSelfOffset": NtTib.Self.offset,
        "tbiSize": ctypes.sizeof(Tbi), "tbiAlign": ctypes.alignment(Tbi),
        "exitStatusOffset": Tbi.ExitStatus.offset, "tebOffset": Tbi.TebBaseAddress.offset,
        "clientIdOffset": Tbi.ClientId.offset, "affinityOffset": Tbi.AffinityMask.offset,
        "priorityOffset": Tbi.Priority.offset, "basePriorityOffset": Tbi.BasePriority.offset,
        "tebPrefixSize": ctypes.sizeof(TebPrefix), "tebPrefixAlign": ctypes.alignment(TebPrefix),
        "environmentPointerOffset": TebPrefix.EnvironmentPointer.offset, "tebClientIdOffset": TebPrefix.ClientId.offset,
        "activeRpcHandleOffset": TebPrefix.ActiveRpcHandle.offset,
        "ntTibOffset": TebPrefix.NtTib.offset, "tlsPointerOffset": TebPrefix.ThreadLocalStoragePointer.offset,
    }


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(message)


def hex64(value: str) -> int:
    require(isinstance(value, str) and len(value) == 18 and value.startswith("0x"), "expected exact 64-bit hex string")
    return int(value, 16)


def validate_query(query: dict) -> None:
    require(query["attempted"] is True, "query was not attempted")
    require(query["informationClass"] == 0, "wrong information class")
    require(query["bufferLength"] == 48 and query["returnLength"] == 48, "wrong query length")
    require(query["status"] == 0 and query["statusHex"] == "0x00000000", "query NTSTATUS is not exact success")
    require(isinstance(query["lastErrorObserved"], int), "missing query GetLastError observation")
    raw = bytes.fromhex(query["rawHex"])
    require(len(raw) == 48 and len(query["rawHex"]) == 96, "query must retain all 48 raw bytes")
    decoded = struct.unpack("<i4xQQQQii", raw)
    printed = (query["exitStatus"], hex64(query["teb"]), hex64(query["clientPid"]),
               hex64(query["clientTid"]), hex64(query["affinityMask"]), query["priority"], query["basePriority"])
    require(decoded == printed, "printed query fields differ from retained raw bytes")


def validate_stdout(raw: bytes) -> dict:
    records = [json.loads(line) for line in raw.decode("utf-8").splitlines() if line.strip()]
    require(all(isinstance(item, dict) for item in records), "records must be JSON objects")
    require(len(records) >= 2 and records[-1].get("record") == "result", "missing final result record")
    require(all(item.get("record") == "phase" for item in records[:-1]), "unexpected or duplicate result records")
    report = records[-1]
    require(report["schema"] == "owned-teb-abi-v1", "wrong probe schema")
    require(report["passed"] is True and report["failure"] == "none" and report["stage"] == "done", "probe failed")
    require(report["nativeArchitecture"] == 9, "not native AMD64")
    require(report["layout"] == candidate_layout(), "candidate layout mismatch")
    require(all(report[flag] is False for flag in DENIED_FLAGS), "scope flag was incorrectly promoted")
    require(report["queryCount"] == 3 and report["relationsPassed"] is True, "expected exactly three related queries")
    require(report["queryCountExact"] is True, "query count is incomplete")
    require(report["workerJoined"] is True, "worker was not joined")
    require(report["waitLimitMs"] == 5000, "wait limit differs")
    main, worker = report["mainSelf"], report["workerSelf"]
    for name, self in (("main", main), ("worker", worker)):
        require(self["attempted"] is True and isinstance(self["pid"], int) and isinstance(self["tid"], int), f"missing {name} identity")
        require(self["pid"] > 0 and self["tid"] > 0 and hex64(self["sdkNtCurrentTeb"]) > 0, f"zero {name} identity")
        require(self["ntTibSelf"] == self["sdkNtCurrentTeb"], f"{name} NT_TIB.Self differs")
        require(self["gs58"] == self["sdkTeb58"], f"{name} GS58 differs")
        require(hex64(self["pseudoHandle"]) == 0xfffffffffffffffe, f"{name} current-thread pseudo handle differs")
        times = self["times"]
        require(times["api"]["attempted"] is True and hex64(times["api"]["result"]) != 0, f"{name} GetThreadTimes failed")
        require(hex64(times["creation"]) > 0, f"{name} creation time invalid")
        # Exit FILETIME for a still-running thread is not part of the contract.
        for field in ("exit", "kernel", "user"):
            hex64(times[field])
        validate_query(self["query"])
        query = self["query"]
        require(query["teb"] == self["sdkNtCurrentTeb"], f"{name} query TEB differs from SDK")
        require(hex64(query["clientPid"]) == self["pid"] and hex64(query["clientTid"]) == self["tid"], f"{name} query identity differs")
    require(main["pid"] == worker["pid"] and main["tid"] != worker["tid"], "threads are not distinct in the same process")
    require(main["sdkNtCurrentTeb"] != worker["sdkNtCurrentTeb"], "threads share a TEB")
    validate_query(report["mainWorkerQuery"])
    cross = report["mainWorkerQuery"]
    require(cross["teb"] == worker["sdkNtCurrentTeb"] and hex64(cross["clientPid"]) == worker["pid"]
            and hex64(cross["clientTid"]) == worker["tid"], "original worker handle query identity differs")
    api = report["api"]
    for name in ("GetModuleHandleW", "GetProcAddress", "CreateEventW-ready", "CreateEventW-release", "CreateThread",
                 "GetThreadId", "GetProcessIdOfThread", "workerReadySignal", "releaseSignal", "GetExitCodeThread",
                 "CloseHandle-worker", "CloseHandle-ready", "CloseHandle-release"):
        require(api[name]["attempted"] is True and hex64(api[name]["result"]) != 0, f"API failed: {name}")
        require(isinstance(api[name]["lastError"], int), f"missing GetLastError: {name}")
    for name, expected in (("readyWait", 0), ("workerAliveWait", 258), ("workerReleaseWait", 0), ("join", 0)):
        require(api[name]["attempted"] is True and hex64(api[name]["result"]) == expected, f"wait failed: {name}")
        require(isinstance(api[name]["lastError"], int), f"missing wait GetLastError: {name}")
    require(hex64(api["GetThreadId"]["result"]) == worker["tid"] == report["createdWorkerTid"], "CreateThread/GetThreadId mismatch")
    require(hex64(api["GetProcessIdOfThread"]["result"]) == worker["pid"], "GetProcessIdOfThread mismatch")
    times = report["workerHandleTimes"]
    require(times["api"]["attempted"] is True and hex64(times["api"]["result"]) != 0, "handle GetThreadTimes failed")
    require(times["creation"] == worker["times"]["creation"], "worker creation time differs")
    for field in ("exit", "kernel", "user"):
        hex64(times[field])
    require(report["workerExitCode"] == 0 and report["workerFailure"] == "none", "worker failed or cleanup failed")
    return {"queryCount": 3, "pid": main["pid"], "mainTid": main["tid"], "workerTid": worker["tid"],
            "mainTeb": main["sdkNtCurrentTeb"], "workerTeb": worker["sdkNtCurrentTeb"],
            "tlsZeroObserved": [hex64(self["gs58"]) == 0 for self in (main, worker)],
            "scope": "owned threads only; no live, collector, pool or replay authorization"}


if __name__ == "__main__":
    sys.exit(main())
