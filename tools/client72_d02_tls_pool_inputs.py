"""Merged TLS/R1 + conditional Pool gate. Read callbacks only; no CPU or writes."""

import json

from . import client72_d02_tls_inputs as core

CONTRACT = "docs/CLIENT_72_D02_POOL_TLS_CONTRACT_20261110.md"
CONTRACT_SHA = "a6d6810a629a518cde1288c5137b281d217f76e9d4ea759cfc7147b69ebc68ba"
POOL_PRIVATE = "local/migration72/d02-resume-20261110/a-pool-tls/"
POOL_SOURCE_PINS = {
    CONTRACT: CONTRACT_SHA,
    POOL_PRIVATE + "delivery.private.json": "2425b69e8717889089ba4d113f8a7c0d81764cbc7e72504beaa1efc8676911d2",
    POOL_PRIVATE + "implementation-spec.private.json": "79143840889a8bba84bef9ea94f3a2f44ed04cc07d11f895df50375acaf62f9f",
    POOL_PRIVATE + "audit-first.private.json": "4fd7d618c280339d98e58b26f3b8223bb6651412f3408670a306d9965601ef8f",
    POOL_PRIVATE + "audit-repeat.private.json": "4fd7d618c280339d98e58b26f3b8223bb6651412f3408670a306d9965601ef8f",
}


def verify_sources(root=core.ROOT):
    avatar = core.verify_sources(root)
    loaded = {}
    for path, expected in POOL_SOURCE_PINS.items():
        raw = (root / path).read_bytes()
        if core.digest(raw) != expected:
            raise ValueError("Frozen Pool source differs: " + path)
        if path.endswith(".json"):
            loaded[path] = json.loads(raw)
    specification = loaded[POOL_PRIVATE + "implementation-spec.private.json"]
    if specification["futureMergedIncludingFinalCap"] != {"reads": 36, "bytes": 738, "maxSingle": 208}:
        raise ValueError("Frozen Pool merged budget differs")
    return {"avatarSourceChecks": avatar, "poolSourcePins": dict(POOL_SOURCE_PINS),
            "poolContractSHA256": CONTRACT_SHA,
            "poolSourceStrength": "frozen instruction text and recorded code SHA; machine-code bytes not rechecked",
            "naturalPoolAllocatorThreadObserved": False, "liveApproved": False, "replayApproved": False}


def collect(read, module, teb, *, now=core.utc):
    ledger, result = core.start_collection(read, module, teb, now=now, profile="avatar-r1-pool")
    pool = {"status": "partial", "failure": None, "stopBefore": None, "threadSource": "launch-primary-thread",
            "naturalPoolAllocatorThreadObserved": False, "allocatorReturned": False,
            "headArrayRead": False, "nodeRead": False, "loopExecuted": False,
            "nativeCPUExecuted": False, "externalCallExecuted": False, "capturedWriteExecuted": False,
            "capturedWritesApproved": False, "canReplayPop": False}
    result.update(schema="client72-tls-r1-pool-inputs-v1", profile="avatar-r1-pool", pool=pool,
                  naturalPoolAllocatorThreadObserved=False)
    try:
        core._collect_tls(ledger, module, teb, result["tls"])
        core._collect_r1(ledger, module, result["r1"])
        _collect_pool(ledger, module, result["tls"]["moduleTLS"], pool)
    except core.ReadStop as failure:
        result["failure"] = str(failure)
        pool["failure"] = str(failure) if ledger.first_reads > 13 else "avatar-r1-incomplete"
    ledger.finish(result)
    pool["stableInputBytes"] = result["status"] == "candidate"
    return result


def _collect_pool(ledger, module, module_tls, result):
    # Same transaction's first three spans, not a previous collection's TLS data.
    result["sharedSpans"] = [
        {"firstOrdinal": 1, "name": "tls.index", "sourceReader": "0x14050a8ae"},
        {"firstOrdinal": 2, "name": "tls.vector", "sourceReader": "0x14050a8b4"},
        {"firstOrdinal": 3, "name": "tls.module", "sourceReader": "0x14050a8bd"},
    ]
    pointer = int.from_bytes(ledger.get("pool.pointer1990", module_tls + 0x1990, 8,
                                       source_reader="0x14050a8c1"), "little")
    result["pointer1990"] = pointer
    if pointer == 0:
        global_qword = int.from_bytes(ledger.get("pool.globalQword", module + 0x5bc87d8, 8,
                                                source_reader="0x14050a9d7"), "little")
        result["globalQwordObserved"] = global_qword
        if global_qword == 0:
            result["branch"] = "P-null-globalQ-zero"
            _boundary(result, module, "0x14050ab6e", "external-call", target="0x1405336c0")
            return
        byte = ledger.get("pool.globalByte", module + 0x5bc7701, 1, source_reader="0x14050a9e5")[0]
        result.update(globalByteObserved=byte, branch="P-null-globalQ-nonzero")
        if byte == 0:
            _boundary(result, module, "0x14050a9f5", "external-call", target="0x14050a210")
        else:
            _boundary(result, module, "0x14050ab6e", "external-call", target="0x1405336c0")
        return

    # P+0 is consumed before flags; zero U32 is legal and has no assigned business name.
    result["u32ObservedAt0"] = int.from_bytes(ledger.get("pool.u32At0", pointer, 4,
                                                       source_reader="0x14050a8d1"), "little")
    flags = ledger.get("pool.flagsAt8", pointer + 8, 1, source_reader="0x14050a8d4")[0]
    result["flagsAt8"] = flags
    if flags & 1 == 0:
        result["branch"] = "P-nonnull-bit0-clear"
        _boundary(result, module, "0x14050aac8", "external-call", target="0x14050a210")
        return
    head_array = int.from_bytes(ledger.get("pool.headArrayAt18", pointer + 0x18, 8,
                                          source_reader="0x14050a8df"), "little")
    result["headArrayAt18"] = head_array
    if head_array == 0:
        result["branch"] = "P-nonnull-bit0-set-headArray-zero"
        _boundary(result, module, "0x14050aac0", "captured-data-write", write_address=pointer + 8)
        return
    # This unsigned U64 is a loop bound, not a pointer, allocation size or read cap.
    bound = int.from_bytes(ledger.get("pool.unsignedBoundAt20", pointer + 0x20, 8,
                                     source_reader="0x14050a8ea"), "little")
    result.update(unsignedBoundAt20=bound, branch="P-nonnull-bit0-set-headArray-nonzero")
    if bound == 0:
        _boundary(result, module, "0x14050aac0", "captured-data-write", write_address=pointer + 8)
    else:
        _boundary(result, module, "0x14050a8f7", "uncaptured-global-read")
        result["nextUncapturedRead"] = {"address": module + 0x5bc7758, "requested": 8, "executed": False}


def _boundary(result, module, site, kind, *, target=None, write_address=None):
    result.update(status="branch-diagnostic", stopBefore=site,
                  stopBeforeRuntime=module + int(site, 16) - 0x140000000, stopKind=kind)
    if target is not None:
        result["externalTarget"] = target
    if write_address is not None:
        result["pendingWrite"] = {"address": write_address, "width": 1, "instructionValue": 0,
                                  "approved": False, "executed": False}
