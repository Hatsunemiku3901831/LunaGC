"""Second allocator and Default inputs, purely read-only and epoch-local."""

import json

from . import client72_d02_tls_inputs as core
from . import client72_d02_tls_pool_inputs as pool
from . import client72_d02_avatar_block_inputs as head
from . import client72_d02_avatar_ctor_inputs as ctor

PROFILE = "avatar-r1-pool-head-ctor-second"
SPEC = "docs/CLIENT_72_D02_SECOND_ALLOCATOR_SPEC_20261110.md"
AUDIT = "local/migration72/d02-resume-20261110/a-second-allocation/audit-first.private.json"
PINS = {SPEC: "86c9c6501dd80c4a7475e0e33da9e9a21eb00fc4c720728bc460f3fdc7acfb59",
        AUDIT: "17d81c40a4c674e201d24af7f1880325a316f408c8f97ee564b266dabe42e646",
        "local/migration72/d02-resume-20261110b/a-gc/gc-helper.code.bin":
            "34287f706e27541c8119091aa9e55c7e2916610935f90c9b74992856b9654b31",
        "local/migration72/d02-resume-20261110b/a-gc/contract-main-third.private.json":
            "ff87585ad28ea59ad0fbe7f62a6e5bee9f5fd074f5ca7343e555abcc869b952c",
        "local/migration72/d02-resume-20261110b/a-first-pop-copy/main-third.private.json":
            "be49a4e995e1f9c9ab65cea6810e044ccfebd280a1ff89fd2de79ec1699b0e74"}


def verify_sources(root=core.ROOT):
    previous = ctor.verify_sources(root)
    for path, expected in PINS.items():
        if ctor._file_sha256(root / path) != expected:
            raise ValueError("Frozen second allocation source differs: " + path)
    audit = json.loads((root / AUDIT).read_bytes())
    if len(audit["inputPins"]) != 29:
        raise ValueError("Frozen second allocation input count differs")
    for path, pin in audit["inputPins"].items():
        if ctor._file_sha256(root / path) != pin["sha256"]:
            raise ValueError("Audited second allocation source differs: " + path)
    return {"previousSourceChecks": previous, "secondSourcePins": dict(PINS),
            "auditedSecondInputPins": audit["inputPins"], "liveApproved": False,
            "replayApproved": False, "capturedCopyWriteApproved": False}


def collect(read, module, teb, *, now=core.utc):
    ledger, result = core.start_collection(read, module, teb, now=now, profile=ctor.PROFILE)
    ledger.first_read_limit, ledger.first_byte_limit, ledger.read_limit, ledger.byte_limit = 44, 1184, 88, 2368
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
              "second": {}, "default": {"pointeeRead": False}, "reusedSpans": reused,
              "arrayBodyRead": False, "secondAllocationReturned": False, "canReplayPop": False,
              "capturedCopyWriteApproved": False, "nativeCPUExecuted": False, "wholeConstructorComplete": False}
    result.update(schema="client72-tls-r1-pool-head-ctor-second-v1", profile=PROFILE,
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
        _second(ledger, capture, inputs)
        _default(ledger, module, inputs["default"])
        inputs["status"] = "second-and-default-input-candidate"
    except core.ReadStop as failure:
        result.update(failure=str(failure), failureStage=stage)
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


def _second(ledger, first, inputs):
    graph, second = inputs["hashSet"], inputs["second"]
    path = graph["allocationInterpretation"]
    size, bin_index = path["requestU16"], path["binU32"]
    second.update(requestU16=size, binU32=bin_index, status="partial", nextNodeRead=False)
    if size > 208:
        raise core.ReadStop("second:request-size-unsupported")
    cell = path["candidateRecordPointerCell"]
    record = int.from_bytes(ledger.get("second.recordCell", cell, 8, source_reader="0x140563d34"), "little")
    second["recordPointer"] = record
    if not record:
        raise core.ReadStop("second:record-null")
    same = record == first["recordPointer"]
    if same and bin_index != 2:
        raise core.ReadStop("second:same-record-different-bin-unsupported")
    if same:
        length, address = first["observedLengthU64"], first["nextPointerObserved"]
        second["branch"] = "same-record-bin2"
    else:
        second["branch"] = "independent-record"
        length = int.from_bytes(ledger.get("second.blockLength", record + 0x18, 8, source_reader="0x140563d6f"), "little")
        second["observedLengthU64"] = length
        if not max(size, 56) <= length <= 208:
            raise core.ReadStop("second:length-unsupported")
        address = int.from_bytes(ledger.get("second.headCell", record + 0x20, 8, source_reader="0x140563d39"), "little")
        second["headPointer"] = address
        if not address:
            raise core.ReadStop("second:head-null")
        second["lockCounterU16"] = int.from_bytes(ledger.get("second.lockCounter", record + 0x10, 2, source_reader="0x140563d46"), "little")
    second.update(observedLengthU64=length, headPointer=address)
    if not max(size, 56) <= length <= 208:
        raise core.ReadStop("second:length-unsupported")
    if not address:
        raise core.ReadStop("second:head-null")
    first_address, first_length = first["headPointer"], first["observedLengthU64"]
    if max(address, first_address) < min(address + length, first_address + first_length):
        raise core.ReadStop("second:head-overlap-unsupported")
    raw = ledger.get("second.completeHead", address, length, source_reader="0x140563d4b")
    second.update(headBytesHex=raw.hex(), headSHA256=core.digest(raw), nextPointerObserved=int.from_bytes(raw[:8], "little"),
                  oldFlags=raw[8], status="second-block-input-candidate")
    if raw[8] & 8 or first["notifyByte"] & 1 or graph["classCA"] & 1 or graph["classC7"] == 0:
        raise core.ReadStop("second:unknown-post-pop-edge")


def _default(ledger, module, result):
    u = int.from_bytes(ledger.get("default.usage", module + 0x594fd78, 8, source_reader="0x157d97698"), "little")
    result["usagePointer"] = u
    if not u:
        raise core.ReadStop("default:usage-null")
    p = int.from_bytes(ledger.get("default.pointer", u + 0x38, 8, source_reader="0x157d9769f"), "little")
    result["pointer"] = p
    if not p:
        raise core.ReadStop("default:pointer-null")
    value = int.from_bytes(ledger.get("default.value", p, 8, source_reader="0x157d976a3"), "little")
    result["value"] = value
    if not value:
        result["status"] = "cold-unclosed"
        raise core.ReadStop("default:cold-unclosed")
    result["status"] = "opaque-nonzero-input-candidate"
