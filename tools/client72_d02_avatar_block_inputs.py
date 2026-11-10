"""One original head span plus fixed TLS/R1/Pool inputs; no execution or writes."""

import json
import struct

from . import client72_d02_tls_inputs as core
from . import client72_d02_tls_pool_inputs as pool

CONTRACT = "docs/CLIENT_72_D02_AVATAR_CAPTURE_ALLOCATION_SPEC_20261110.md"
CONTRACT_SHA = "04b6cf78d9df9bc8d7c02362deac342fd3566f87896b4b6e4b1adf2f59a46bc7"
CAPTURE_PRIVATE = "local/migration72/d02-resume-20261110/a-avatar-capture-contract/"
BEFORE = core.PRIVATE + "avatar-block-implementation/source-before/"
SOURCE_PINS = {
    CONTRACT: CONTRACT_SHA,
    CAPTURE_PRIVATE + "implementation-spec.private.json": "90efa46bd0ff724bb26b5955732cf2be886c86277c0e2aabc09d828b82a55362",
    CAPTURE_PRIVATE + "delivery.private.json": "8d5baa53bfc936d9867e92ce490e38961bb79f09903c4d9f774d11fb6b5b5e3e",
    CAPTURE_PRIVATE + "audit-first.private.json": "c522c0fc42d7eb79aa122a5bed980dd3f1a345287c1371f8c89a402731f31e14",
    CAPTURE_PRIVATE + "audit-repeat.private.json": "c522c0fc42d7eb79aa122a5bed980dd3f1a345287c1371f8c89a402731f31e14",
}
HISTORICAL_SOURCES = {
    "tools/client72_d02_tls_inputs.py": BEFORE + "client72_d02_tls_inputs.py",
    "tools/observe_client72_tls_inputs.py": BEFORE + "observe_client72_tls_inputs.py",
}


def verify_sources(root=core.ROOT):
    previous = pool.verify_sources(root)
    loaded = {}
    for path, expected in SOURCE_PINS.items():
        raw = (root / path).read_bytes()
        if core.digest(raw) != expected:
            raise ValueError("Frozen Avatar block source differs: " + path)
        if path.endswith(".json"):
            loaded[path] = json.loads(raw)
    spec = loaded[CAPTURE_PRIVATE + "implementation-spec.private.json"]
    if (spec["acceptedRequestSize"] != {"source": "K+b4", "bytes": 2, "value": 32}
            or spec["maxMergedIncludingFinal"] != {"reads": 56, "bytes": 1244, "maxSingle": 208}):
        raise ValueError("Avatar block request or fixed budget differs")
    audit = loaded[CAPTURE_PRIVATE + "audit-first.private.json"]
    # The audit predates this source epoch. Its two historical tools are checked
    # against exact before snapshots, never silently rebound to the changed tools.
    for path, pin in audit["inputPins"].items():
        actual_path = HISTORICAL_SOURCES.get(path, path)
        if core.digest((root / actual_path).read_bytes()) != pin["sha256"]:
            raise ValueError("Audited allocation source differs: " + path)
    return {"previousSourceChecks": previous, "avatarBlockSourcePins": dict(SOURCE_PINS),
            "auditedInputPins": audit["inputPins"], "historicalSourceLocations": dict(HISTORICAL_SOURCES),
            "currentFourToolPinsRequiredInEpoch": True, "oldHeapImported": False,
            "liveApproved": False, "replayApproved": False, "capturedCopyWriteApproved": False}


def collect(read, module, teb, *, now=core.utc):
    ledger, result = core.start_collection(read, module, teb, now=now, profile="avatar-r1-pool-head")
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
    result.update(schema="client72-tls-r1-pool-head-v1", profile="avatar-r1-pool-head",
                  allocationInputs=capture, pool=pool_result, naturalPoolAllocatorThreadObserved=False)
    stage = "avatar-r1"
    try:
        core._collect_tls(ledger, module, teb, result["tls"])
        core._collect_r1(ledger, module, result["r1"])
        stage = "allocation-inputs"
        _class_branch(result["r1"], capture)
        _capture_head(ledger, module, result["tls"], capture)
        stage = "pool"
        pool._collect_pool(ledger, module, result["tls"]["moduleTLS"], pool_result)
    except core.ReadStop as failure:
        result["failure"] = str(failure)
        result["failureStage"] = stage
        if stage == "pool":
            pool_result["failure"] = str(failure)
        else:
            capture["failure"] = str(failure)
    ledger.finish(result)
    capture["stableInputBytes"] = result["status"] == "candidate"
    pool_result["stableInputBytes"] = result["status"] == "candidate"
    return result


def _class_branch(r1, result):
    header = bytes.fromhex(r1["headerHex"])
    request = struct.unpack_from("<H", header, 0xb4)[0]
    # Only this new profile changes the display label. The two previous profiles
    # retain their historical U32 field and exact output semantics.
    r1["sizeB4U32LegacyDisplay"] = r1.pop("sizeB4Observed")
    r1["sizeB4NativeU16"] = request
    low3 = struct.unpack_from("<I", header, 8)[0] & 7
    tag = int(low3 != 1)
    klass, ca, c7 = r1["listClass"], header[0xca], header[0xc7]
    result.update(classPointer=klass, requestU16=request, classB4Bytes=header[0xb4:0xb6].hex(),
                  classCA=ca, classC7=c7, classPlus8Low3=low3, derivedTagBit=tag,
                  taggedClassDerived=klass | tag, maskedTaggedClassEqualsClass=((klass | tag) & ~7) == klass)
    if not ca & 0x20:
        result["unsupportedBranchStopBefore"] = "0x1405336eb"
        raise core.ReadStop("allocation:CA20-unset-initialization-path-unprovided")
    if r1["ordinarySlot1255"] == r1["comparisonCell"]:
        result["unsupportedBranchStopBefore"] = "0x140533784"
        raise core.ReadStop("allocation:class-replacement-call-unprovided")
    if not ca & 2:
        result["unsupportedBranchStopBefore"] = "0x14053373d"
        raise core.ReadStop("allocation:tagged-List-branch-not-selected")
    if request != 32:
        result["unsupportedBranchStopBefore"] = "0x1405337d8"
        raise core.ReadStop("allocation:request32-only-policy")
    result["readOnlyStopBefore"] = "0x140563d2d"


def _capture_head(ledger, module, tls, result):
    context, module_tls = tls["context"], tls["moduleTLS"]
    result["initialStateByte"] = ledger.get("allocation.state19cc", module_tls + 0x19cc, 1,
        source_reader={"site": "0x140563d2d", "role": "pre-write-origin"})[0]
    record = int.from_bytes(ledger.get("allocation.recordCell", context + 0x48, 8,
        source_reader="0x140563d34"), "little")
    result["recordPointer"] = record
    if record == 0:
        raise core.ReadStop("allocation:record-pointer-null")
    length = int.from_bytes(ledger.get("allocation.blockLength", record + 0x18, 8,
        source_reader="0x140563d6f"), "little")
    result["observedLengthU64"] = length
    result["lengthPolicy"] = {"min": 32, "max": 208, "maximumIsPolicyOnly": True}
    if not 32 <= length <= 208:
        raise core.ReadStop("allocation:observed-length-outside-capture-policy")
    head = int.from_bytes(ledger.get("allocation.headCell", record + 0x20, 8,
        source_reader="0x140563d39"), "little")
    result["headPointer"] = head
    if head == 0:
        result["potentialUnreviewedEdge"] = {"site": "0x140563df5", "target": "0x140563e10", "kind": "tail-jump"}
        raise core.ReadStop("allocation:head-null-slow-edge")
    result["lockCounterU16"] = int.from_bytes(ledger.get("allocation.lockCounter", record + 0x10, 2,
        source_reader={"site": "0x140563d46", "role": "read-modify-write-origin"}), "little")
    result["counterU64"] = int.from_bytes(ledger.get("allocation.counter", context + 8, 8,
        source_reader={"site": "0x140563d73", "role": "read-modify-write-origin"}), "little")
    result["globalHeaderByte"] = ledger.get("allocation.headerByte", module + 0x5699680, 1,
        source_reader="0x140563d64")[0]
    state = int.from_bytes(ledger.get("allocation.threadState", module_tls + 0x19e0, 8,
        source_reader="0x140563d9d"), "little")
    result["threadStatePointer"] = state
    if state == 0:
        raise core.ReadStop("allocation:thread-state-null-not-notify-clear")
    result["notifyByte"] = ledger.get("allocation.notifyByte", state + 0xa0, 1,
        source_reader="0x140563dab")[0]
    result["headSpanRequest"] = {"address": head, "requested": length}
    raw = ledger.get("allocation.completeHead", head, length,
        source_reader={"sites": ["0x140563d4b", "0x140563d60"], "role": "complete-span-length-from-R+18"})
    result.update(rawHeadCaptured=True, headBytesHex=raw.hex(), headSHA256=core.digest(raw),
                  nextPointerObserved=int.from_bytes(raw[:8], "little"), oldFlags=raw[8],
                  rawBlockHasNonzeroBytes=any(raw), status="single-head-input-candidate")
    _potential_edges(result)


def _potential_edges(result):
    # Predictions describe frozen control flow only. No original instruction,
    # state/header/counter write, external body or post-pop function is executed.
    if result["oldFlags"] & 8:
        edge = {"site": "0x140563d85", "target": "0x141fdf9c0", "reason": "old-head-bit08-clear-body-unknown"}
    elif result["notifyByte"] & 1:
        edge = {"site": "0x140563e01", "target": "0x14056c050", "reason": "notify-body-unknown"}
    elif result["classCA"] & 1:
        edge = {"site": "0x1405337f3", "target": "0x14055ecf0", "reason": "registration-body-unknown"}
    elif result["classC7"] == 0:
        edge = {"site": "0x140525e2a", "reason": "post-pop-first-unprovided-global-read", "moduleRVA": "0x5bc90f0"}
    else:
        edge = {"site": "0x15183fe92", "reason": "constructor-next-unprovided-array-usage", "moduleRVA": "0x5b74628"}
    result["potentialUnreviewedEdge"] = edge
