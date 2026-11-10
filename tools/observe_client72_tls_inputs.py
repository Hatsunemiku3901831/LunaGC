"""Retained launch-primary adapter; no live CLI or stock-launch authorization.

The mainline helper owns fresh-stock/ready/temporary-DLL exclusion gates and the
process identity callback. Importing this module calls no Windows APIs.
"""

import ctypes as C
from ctypes import wintypes as W
import ntpath
from pathlib import Path
import struct

from . import client72_d02_tls_inputs as card
from . import observe_client72_uninstrumented as monitor

API_LIMITS = {"GetThreadId": 1, "GetProcessIdOfThread": 1, "GetThreadTimes": 1,
              "WaitForSingleObject": 2, "NtQueryInformationThread": 2, "CloseHandle-primary": 1}


class RetainedPrimaryBackend:
    """Wrap an existing monitor backend; retain only this normal launch's handle."""
    artificial = False

    def __init__(self, base):
        self.base, self.k, self.nt = base, base.k, base.nt
        self.primary = None
        self.launch_result = None
        self.primary_close = None
        for name in ("GetThreadId", "GetProcessIdOfThread"):
            function = getattr(self.k, name)
            function.argtypes, function.restype = [W.HANDLE], C.c_uint32
        self.k.GetThreadTimes.argtypes = [W.HANDLE] + [C.POINTER(W.FILETIME)] * 4
        self.k.GetThreadTimes.restype = W.BOOL
        self.k.ReadProcessMemory.argtypes = [W.HANDLE, C.c_void_p, C.c_void_p, C.c_size_t, C.POINTER(C.c_size_t)]
        self.k.ReadProcessMemory.restype = W.BOOL
        if self.nt is not None:
            self.nt.argtypes = [W.HANDLE, C.c_uint32, C.c_void_p, C.c_uint32, C.POINTER(C.c_uint32)]
            self.nt.restype = C.c_int32

    def __getattr__(self, name):
        return getattr(self.base, name)

    def launch(self, target, cwd):
        if self.launch_result is not None:
            raise ValueError("A second launch cannot replace the original primary handle")
        startup = monitor.STARTUPINFOW()
        startup.cb = C.sizeof(startup)
        info = monitor.PROCESS_INFORMATION()
        command = C.create_unicode_buffer('"' + str(target) + '"')
        C.set_last_error(0)
        ok = self.k.CreateProcessW(str(target), command, None, None, False, 0, None, str(cwd),
                                   C.byref(startup), C.byref(info))
        self.launch_result = {"api": "CreateProcessW", "result": int(ok), "lastError": C.get_last_error(),
                              "creationFlags": 0, "arguments": [], "beginSource": "this-call-only",
                              "processInformationRawHex": C.string_at(C.byref(info), C.sizeof(info)).hex()}
        if not ok:
            raise OSError(self.launch_result["lastError"], "CreateProcessW failed")
        self.primary = {"source": "launch-primary-thread", "processHandle": int(info.hProcess),
                        "threadHandle": int(info.hThread), "pid": int(info.dwProcessId), "tid": int(info.dwThreadId),
                        "target": str(target), "cwd": str(cwd), "creationFlags": 0}
        return info.hProcess, info.dwProcessId

    def thread_id(self):
        C.set_last_error(0)
        value = self.k.GetThreadId(self.primary["threadHandle"])
        return {"attempted": True, "result": int(value), "lastError": C.get_last_error()}

    def thread_pid(self):
        C.set_last_error(0)
        value = self.k.GetProcessIdOfThread(self.primary["threadHandle"])
        return {"attempted": True, "result": int(value), "lastError": C.get_last_error()}

    def thread_times(self):
        times = [W.FILETIME() for _ in range(4)]
        C.set_last_error(0)
        ok = self.k.GetThreadTimes(self.primary["threadHandle"], *[C.byref(time) for time in times])
        error = C.get_last_error()
        return {"attempted": True, "result": int(ok), "lastError": error,
                **{name: (time.dwHighDateTime << 32) | time.dwLowDateTime
                   for name, time in zip(("creation", "exit", "kernel", "user"), times)}}

    def thread_alive(self):
        C.set_last_error(0)
        value = self.k.WaitForSingleObject(self.primary["threadHandle"], 0)
        return {"attempted": True, "result": int(value), "lastError": C.get_last_error(), "timeoutMs": 0}

    def thread_basic(self):
        if self.nt is None:
            return {"attempted": False, "error": "NtQueryInformationThread-unavailable"}
        raw, length = C.create_string_buffer(b"\xcc" * 48, 48), C.c_uint32(0)
        C.set_last_error(0)
        status = self.nt(self.primary["threadHandle"], 0, raw, 48, C.byref(length))
        return {"attempted": True, "informationClass": 0, "bufferLength": 48, "returnLength": length.value,
                "status": int(status), "statusHex": f"0x{status & 0xffffffff:08x}",
                "lastErrorObserved": C.get_last_error(), "rawHex": raw.raw.hex()}

    def read(self, process, address, width):
        raw, count = C.create_string_buffer(width), C.c_size_t(0)
        C.set_last_error(0)
        ok = self.k.ReadProcessMemory(process, address, raw, width, C.byref(count))
        error = C.get_last_error()
        return raw.raw[:count.value], None if ok and count.value == width else {
            "kind": "ReadProcessMemory-failed", "result": int(ok), "lastError": error, "bytesRead": count.value}

    def close_primary(self):
        if self.primary_close is None:
            C.set_last_error(0)
            ok = self.k.CloseHandle(self.primary["threadHandle"])
            self.primary_close = {"attempted": True, "result": int(ok), "lastError": C.get_last_error(),
                                  "handle": self.primary["threadHandle"]}
        return self.primary_close

    def close(self, handle):
        # Also release the primary if the outer monitor fails before sampling.
        if self.primary is not None and handle == self.primary["processHandle"]:
            self.close_primary()
        return self.base.close(handle)


class SampleStop(Exception):
    pass


def sample_primary(backend, epoch, identity, *, now=card.utc, include_pool=False, include_allocator_inputs=False,
                   include_ctor_inputs=False, include_second_allocator_inputs=False):
    """Use only the original handles. The caller supplies full fresh identity.

identity(hProcess, pid) -> {pid, image, processCreationTime, module:{name,path,base,size}}.
epoch adds epochId and the five actually verified stockInputPins. This function
does not launch, open a process/thread, choose a thread, or authorize a live run.
"""
    result = {"schema": "client72-retained-primary-tls-v1", "status": "partial", "failureStage": None,
              "reason": None, "beginUTC": now(), "apiRecords": [], "apiLimits": dict(API_LIMITS),
              "identityObservations": [], "threadRole": "launch-primary-thread", "threadBasicOutputMaxBytes": 96,
              "naturalAvatarAllocatorThreadObserved": False, "canReplayPop": False, "noWrites": True,
              "noNativeCPU": True, "liveApproved": False, "replayApproved": False,
              "artificialFixture": bool(backend.artificial), "primaryCloseVerified": False,
              "endIdentityVerified": False, "launchAPIResult": backend.launch_result}
    counts = {name: 0 for name in API_LIMITS}
    primary = backend.primary
    stage = "entry"

    def call(name, function):
        if counts[name] >= API_LIMITS[name]:
            raise SampleStop("primary-api-budget")
        counts[name] += 1
        record = {"ordinal": len(result["apiRecords"]) + 1, "name": name, "stage": stage, "beginUTC": now()}
        result["apiRecords"].append(record)
        try:
            record["result"] = function()
        except OSError as error:
            record["result"] = {"attempted": True, "result": 0, "error": "api-os-error",
                                "winerror": getattr(error, "winerror", None), "errno": error.errno}
        record["endUTC"] = now()
        return record["result"]

    try:
        _validate_entry(primary, epoch, identity, now, include_pool, include_allocator_inputs, include_ctor_inputs,
                        include_second_allocator_inputs)
        if include_second_allocator_inputs:
            from . import client72_d02_second_allocator_inputs as selected
            result.update(profile=selected.PROFILE, naturalPoolAllocatorThreadObserved=False)
        elif include_ctor_inputs:
            from . import client72_d02_avatar_ctor_inputs as selected
            result.update(profile="avatar-r1-pool-head-ctor", naturalPoolAllocatorThreadObserved=False)
        elif include_allocator_inputs:
            from . import client72_d02_avatar_block_inputs as selected
            result.update(profile="avatar-r1-pool-head", naturalPoolAllocatorThreadObserved=False)
        elif include_pool:
            from . import client72_d02_tls_pool_inputs as selected
            result.update(profile="avatar-r1-pool", naturalPoolAllocatorThreadObserved=False)
        else:
            selected = card
        result.update(epochId=epoch["epochId"], launchPrimary=dict(primary),
                      stockInputPins=dict(epoch["stockInputPins"]), sourceChecks=selected.verify_sources(),
                      implementationPins=dict(epoch["implementationPins"]))
        stage = "identity-before"
        _observe_identity(identity, primary, epoch, result, stage, now)
        stage = "thread-identity"
        tid = call("GetThreadId", backend.thread_id)
        if tid.get("result") != primary["tid"]:
            raise SampleStop("primary-GetThreadId-mismatch")
        pid = call("GetProcessIdOfThread", backend.thread_pid)
        if pid.get("result") != primary["pid"]:
            raise SampleStop("primary-GetProcessIdOfThread-mismatch")
        times = call("GetThreadTimes", backend.thread_times)
        if not times.get("result") or not 0 < times["creation"] <= card.U64:
            raise SampleStop("primary-GetThreadTimes-failed")
        result["primaryThreadTimes"] = times
        stage = "thread-before"
        alive = call("WaitForSingleObject", backend.thread_alive)
        if alive.get("result") != 258:
            raise SampleStop("primary-not-alive-before")
        before = call("NtQueryInformationThread", backend.thread_basic)
        teb = _basic_identity(before, primary)
        result["threadBefore"] = before
        stage = "collection"
        result["collection"] = selected.collect(lambda address, width: backend.read(primary["processHandle"], address, width),
                                             epoch["module"]["base"], teb, now=now)
        stage = "thread-after"
        after = call("NtQueryInformationThread", backend.thread_basic)
        result["threadAfter"] = after
        if _basic_identity(after, primary) != teb:
            raise SampleStop("primary-TEB-changed")
        alive = call("WaitForSingleObject", backend.thread_alive)
        if alive.get("result") != 258:
            raise SampleStop("primary-not-alive-after")
        stage = "identity-after"
        _observe_identity(identity, primary, epoch, result, stage, now)
        result["endIdentityVerified"] = True
        result["status"] = result["collection"]["status"]
        if result["status"] != "candidate":
            result.update(failureStage="collection", reason=result["collection"]["failure"])
    except (SampleStop, ValueError, KeyError, TypeError) as error:
        result.update(status="partial", failureStage=stage, reason=str(error))
    except OSError as error:
        result.update(status="partial", failureStage=stage, reason="identity-os-error",
                      error={"winerror": getattr(error, "winerror", None), "errno": error.errno})
    finally:
        stage = "cleanup"
        if primary is not None:
            closed = call("CloseHandle-primary", backend.close_primary)
            result["primaryClose"] = closed
            result["primaryCloseVerified"] = bool(closed.get("result"))
            if not result["primaryCloseVerified"]:
                if result["reason"] is None:
                    result.update(failureStage=stage, reason="primary-close-failed")
                result["status"] = "partial"
        actual_counts = {name: sum(row["name"] == name and row["result"].get("attempted") is True
                                   for row in result["apiRecords"]) for name in API_LIMITS}
        result.update(apiCounts=actual_counts, apiRequests=counts, endUTC=now(),
                      threadBasicOutputBytes=sum(len(row["result"].get("rawHex", "")) // 2
                          for row in result["apiRecords"] if row["name"] == "NtQueryInformationThread"))
    return result


def _validate_entry(primary, epoch, identity, now, include_pool, include_allocator_inputs, include_ctor_inputs,
                    include_second_allocator_inputs):
    if any(type(value) is not bool for value in
           (include_pool, include_allocator_inputs, include_ctor_inputs, include_second_allocator_inputs)):
        raise ValueError("Explicit booleans must select a fixed TLS profile")
    if include_second_allocator_inputs and not (include_pool and include_allocator_inputs and include_ctor_inputs):
        raise ValueError("Second allocator inputs require the combined ctor profile")
    if include_ctor_inputs and not (include_pool and include_allocator_inputs):
        raise ValueError("Ctor inputs require the combined head and Pool profile")
    if include_allocator_inputs and not include_pool:
        raise ValueError("Allocator inputs require the combined Pool profile")
    if primary is None or primary["source"] != "launch-primary-thread" or primary["creationFlags"] != 0:
        raise ValueError("Only this normal CreateProcessW primary handle is accepted")
    if not callable(identity) or not callable(now) or not isinstance(epoch["epochId"], str) or not epoch["epochId"]:
        raise ValueError("A fresh epoch and identity callback are required")
    for name in ("processHandle", "threadHandle", "pid", "tid"):
        if type(primary[name]) is not int or primary[name] <= 0:
            raise ValueError("Incomplete launch identity: " + name)
    if (epoch["pid"] != primary["pid"] or epoch["stockInputPins"] != monitor.EXPECTED
            or type(epoch["processCreationTime"]) is not int or not 0 < epoch["processCreationTime"] <= card.U64
            or ntpath.normcase(ntpath.normpath(epoch["image"])) != ntpath.normcase(ntpath.normpath(primary["target"]))):
        raise ValueError("Epoch process or fixed stock identity differs")
    source_names = ["tools/client72_d02_tls_inputs.py", "tools/observe_client72_tls_inputs.py"]
    if include_pool:
        source_names.append("tools/client72_d02_tls_pool_inputs.py")
    if include_allocator_inputs:
        source_names.append("tools/client72_d02_avatar_block_inputs.py")
    if include_ctor_inputs:
        source_names.append("tools/client72_d02_avatar_ctor_inputs.py")
    if include_second_allocator_inputs:
        source_names.append("tools/client72_d02_second_allocator_inputs.py")
    expected_pins = {name: card.digest((card.ROOT / name).read_bytes()) for name in source_names}
    if epoch["implementationPins"] != expected_pins:
        raise ValueError("Fresh helper implementation pins differ")
    module = epoch["module"]
    if (module["name"].casefold() != "yuanshen.exe" or type(module["base"]) is not int
            or type(module["size"]) is not int or not 0 < module["base"] <= card.U64 - 0x5eef084
            or module["size"] < 0x5eef084
            or ntpath.normcase(ntpath.normpath(module["path"])) != ntpath.normcase(ntpath.normpath(primary["target"]))):
        raise ValueError("Epoch main module identity differs")


def _observe_identity(callback, primary, epoch, result, stage, now):
    row = {"stage": stage, "beginUTC": now()}
    result["identityObservations"].append(row)
    row["value"] = callback(primary["processHandle"], primary["pid"])
    row["endUTC"] = now()
    expected = {name: epoch[name] for name in ("pid", "image", "processCreationTime", "module")}
    if row["value"] != expected:
        raise SampleStop("process-image-creation-module-identity-differed")


def _basic_identity(output, primary):
    if (output.get("attempted") is not True or output.get("informationClass") != 0
            or output.get("bufferLength") != 48 or output.get("returnLength") != 48
            or output.get("status") != 0 or output.get("statusHex") != "0x00000000"):
        raise SampleStop("ThreadBasic-status-or-length-failed")
    raw = bytes.fromhex(output["rawHex"])
    if len(raw) != 48:
        raise SampleStop("ThreadBasic-raw-length-failed")
    exit_status, teb, pid, tid, affinity, priority, base_priority = struct.unpack("<i4xQQQQii", raw)
    output["decoded"] = {"exitStatus": exit_status, "teb": teb, "pid": pid, "tid": tid,
                         "affinityMask": affinity, "priority": priority, "basePriority": base_priority}
    if pid != primary["pid"] or tid != primary["tid"] or not 0 < teb <= card.U64 - 0x60:
        raise SampleStop("ThreadBasic-primary-identity-failed")
    return teb
