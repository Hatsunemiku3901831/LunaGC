"""Second allocator and Default inputs, purely read-only and epoch-local."""

import json

from . import client72_d02_tls_inputs as core
from . import client72_d02_tls_pool_inputs as pool
from . import client72_d02_avatar_block_inputs as head
from . import client72_d02_avatar_ctor_inputs as ctor
from . import client72_d02_second_allocator_inputs as previous

PROFILE = "avatar-r1-pool-head-ctor-second-gc-default"
SPEC = "local/migration72/d02-resume-20261110b/a-gc-default-inputs/implementation-spec.zh.md"
AUDIT = "local/migration72/d02-resume-20261110b/a-second-gc64/contract-main-third.private.json"
PINS = {SPEC: "3d8ec2365e381f88523a154c47e2c4da5ddf4f20bf1bdbe2ac6137e86169cb25",
        AUDIT: "665a737fc7815d5468d8c424ed9f9be090191ff98c63d11576536e95638afe3a",
        "tools/client72_d02_second_allocator_inputs.py": "4e1e5f901ac944d7f906e0feae6b3cf6d99b3f3a507dac6fbbc020d8a310bd38"}


def verify_sources(root=core.ROOT):
    earlier = previous.verify_sources(root)
    for path, expected in PINS.items():
        if ctor._file_sha256(root / path) != expected:
            raise ValueError("Frozen GC Default source differs: " + path)
    audit = json.loads((root / AUDIT).read_bytes())
    if len(audit["inputPins"]) != 10:
        raise ValueError("Frozen GC64 input count differs")
    for path, pin in audit["inputPins"].items():
        if ctor._file_sha256(root / path) != pin["sha256"]:
            raise ValueError("Audited GC64 source differs: " + path)
    return {"previousSourceChecks": earlier, "gcDefaultSourcePins": dict(PINS),
            "auditedGC64InputPins": audit["inputPins"], "liveApproved": False,
            "replayApproved": False, "capturedCopyWriteApproved": False}


def collect(read, module, teb, *, now=core.utc):
    ledger, result = core.start_collection(read, module, teb, now=now, profile=ctor.PROFILE)
    ledger.first_read_limit, ledger.first_byte_limit, ledger.read_limit, ledger.byte_limit = 49, 1209, 98, 2418
    original_get = ledger.get
    reused = []

    def get(name, address, width, phase="first", *, source_reader=None):
        first = ledger.successful.get((address, width)) if phase == "first" else None
        if first is not None:
            reused.append({"name": name, "address": address, "requested": width,
                           "firstOrdinal": first["ordinal"], "sha256": core.digest(first["raw"])})
            return first["raw"]
        return original_get(name, address, width, phase, source_reader=source_reader)

    ledger.get = get
    capture = {"status": "partial", "failure": None, "readOnlyStopBefore": None,
               "rawHeadCaptured": False, "nextNodeRead": False, "objectsAllocated": False,
               "nativeCPUExecuted": False, "externalCallExecuted": False, "capturedWriteExecuted": False,
               "capturedCopyWriteApproved": False, "approvedWriteSpans": [], "canReplayPop": False,
               "naturalAvatarAllocatorThreadObserved": False, "naturalHeapOwnershipProven": False,
               "wholeConstructorComplete": False, "constructorDirectCallSites": 20, "constructorUsageRoots": 12,
               "nextConstructorDataRoot": "B+5b74628/8 at15183fe92",
               "nextAllocationClassRoot": "B+5897cf0/8 at15183fea1 -> call15183fea8"}
    pool_result = {"status": "partial", "failure": None, "stopBefore": None,
                   "threadSource": "launch-primary-thread", "naturalPoolAllocatorThreadObserved": False,
                   "nativeCPUExecuted": False, "capturedWritesApproved": False, "canReplayPop": False}
    inputs = {"status": "partial", "items": {"arrayIdentityProved": False, "pointeeRead": False,
              "copyWriteApproved": False}, "hashSet": {"identityProved": False, "reusedSpans": [],
              "staticBinding": {"definition": 2147, "genericInstanceIndex": 11, "argumentKind": "UInt32",
                                "methodDefinition": 15590, "entry": "0x157d97690"}},
              "second": {}, "gcHelper": {"status": "partial"}, "default": {"status": "partial", "pointeeRead": False}, "reusedSpans": reused,
              "arrayBodyRead": False, "secondAllocationReturned": False, "canReplayPop": False,
              "capturedCopyWriteApproved": False, "nativeCPUExecuted": False, "wholeConstructorComplete": False}
    result.update(schema="client72-tls-r1-pool-head-ctor-second-gc-default-v1", profile=PROFILE,
                  allocationInputs=capture, pool=pool_result, ctorInputs=inputs,
                  naturalPoolAllocatorThreadObserved=False)
    stage = "avatar-r1"
    try:
        core._collect_tls(ledger, module, teb, result["tls"])
        core._collect_r1(ledger, module, result["r1"])
        stage = "allocation-inputs"
        head._class_branch(result["r1"], capture)
        head._capture_head(ledger, module, result["tls"], capture)
        stage = "pool"
        pool._collect_pool(ledger, module, result["tls"]["moduleTLS"], pool_result)
        stage = "ctor-inputs"
        _first_clear16_successor(capture)
        if capture["potentialUnreviewedEdge"]["site"] != "0x15183fe92":
            raise core.ReadStop("second:first-pop-unknown-edge")
        ctor._items(ledger, module, capture, inputs)
        ctor._hashset(ledger, module, result["r1"], result["tls"]["context"], inputs["hashSet"])
        try:
            previous._second(ledger, capture, inputs)
        except core.ReadStop as failure:
            if (str(failure) != "second:unknown-post-pop-edge"
                    or not inputs["second"].get("oldFlags", 0) & 8
                    or capture["notifyByte"] & 1 or inputs["hashSet"]["classCA"] & 1
                    or inputs["hashSet"]["classC7"] == 0):
                raise
            inputs["second"]["postPopStop"] = str(failure)
            _gc_helper(ledger, module, inputs["second"], inputs["gcHelper"])
        else:
            inputs["gcHelper"].update(status="not-required", reason="second-old-flags-bit08-clear")
        gc_partial = inputs["gcHelper"]["status"] == "partial"
        try:
            previous._default(ledger, module, inputs["default"])
        except core.ReadStop as failure:
            if str(failure) not in ("default:usage-null", "default:pointer-null", "default:cold-unclosed"):
                raise
            inputs["default"]["failure"] = str(failure)
        if gc_partial or "failure" in inputs["default"]:
            result["failure"] = inputs["gcHelper"].get("failure") or inputs["default"].get("failure")
            result["failureStage"] = stage
            inputs["failure"] = result["failure"]
        else:
            inputs["status"] = "second-gc-default-input-candidate"
    except core.ReadStop as failure:
        result.update(failure=str(failure), failureStage=stage)
        if str(failure).startswith("gcHelper."):
            inputs["gcHelper"]["failure"] = str(failure)
        elif str(failure).startswith("default."):
            inputs["default"]["failure"] = str(failure)
        (pool_result if stage == "pool" else inputs if stage == "ctor-inputs" else capture)["failure"] = str(failure)
    head.finish_head_inputs(ledger, result)
    inputs["stableInputBytes"] = result["status"] == "candidate"
    return result


def _first_clear16_successor(capture):
    if (capture["potentialUnreviewedEdge"]["site"] != "0x140563d85"
            or capture["observedLengthU64"] != 32):
        return
    capture["knownClear16Branch"] = True
    if capture["notifyByte"] & 1:
        edge = {"site": "0x140563e01", "target": "0x14056c050", "reason": "notify-body-unknown"}
    elif capture["classCA"] & 1:
        edge = {"site": "0x1405337f3", "target": "0x14055ecf0", "reason": "registration-body-unknown"}
    elif capture["classC7"] == 0:
        edge = {"site": "0x140525e2a", "reason": "post-pop-first-unprovided-global-read", "moduleRVA": "0x5bc90f0"}
    else:
        edge = {"site": "0x15183fe92", "reason": "constructor-next-unprovided-array-usage", "moduleRVA": "0x5b74628"}
    capture["potentialUnreviewedEdge"] = edge



def _gc_helper(ledger, module, second, result):
    address, length = second["headPointer"], second["observedLengthU64"]
    result.update(headPointer=address, observedLengthU64=length, oldFlags=second["oldFlags"],
                  caller="0x140563d85", rcx=address + 16, edx=0, r8=length - 16,
                  nativeCPUExecuted=False, capturedWriteExecuted=False,
                  naturalReturnObserved=False, naturalGCOwnershipProven=False)
    if length != 64 or address % 32:
        result.update(failure="gc-helper:length-or-alignment-unsupported")
        return
    index = int.from_bytes(ledger.get("gcHelper.index", module + 0x56afdd0, 4, source_reader="0x141fdfa86"), "little")
    t0 = int.from_bytes(ledger.get("gcHelper.threshold0", module + 0x56afdd8, 8, source_reader="0x141fdfc70" if index < 3 else "0x141fdfa93"), "little")
    result.update(indexU32=index, threshold0U64=t0)
    if index < 3:
        side, table, expected, table_pc, jump_pc = "SSE", 0x6d19140, 0x01fdfd35, "0x141fdfcfd", "0x141fdfd08"
        check_flag = 48 > t0
        tail_pc = "0x141fdfc80"
    else:
        side, table, expected, table_pc, jump_pc = "AVX", 0x6d190f4, 0x01fdfb94, "0x141fdfb49", "0x141fdfb54"
        check_flag = False
        tail_pc = "0x141fdfaac"
        if 48 > t0:
            t1 = int.from_bytes(ledger.get("gcHelper.threshold1", module + 0x56afde0, 8, source_reader="0x141fdfa9c"), "little")
            result["threshold1U64"] = t1
            check_flag = 48 <= t1
    result["dispatch"] = side
    if check_flag:
        flag = ledger.get("gcHelper.flag", module + 0x5eeeb08, 1, source_reader="0x141fdfc79" if side == "SSE" else "0x141fdfaa5")[0]
        result["flagU8"] = flag
        if flag & 2:
            result.update(failure="gc-helper:unsupported-external-tail", stopPC=tail_pc, target="0x141fdf9a0")
            return
    value = int.from_bytes(ledger.get("gcHelper.table", module + table, 4, source_reader=table_pc), "little")
    result.update(tableAddress=module + table, tableDWORD=value, expectedTableDWORD=expected, stopPC=jump_pc)
    if value != expected:
        result["failure"] = "gc-helper:unexpected-table-target"
        return
    result.update(status="window-input-candidate", targetRVA=value)
