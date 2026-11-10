"""Fixed ctor items value and HashSet<UInt32> inputs; no allocation or writes."""

import hashlib
import json
import struct

from . import client72_d02_tls_inputs as core
from . import client72_d02_avatar_block_inputs as head

PROFILE = "avatar-r1-pool-head-ctor"
CONTRACT = "docs/CLIENT_72_D02_AVATAR_CTOR_NEXT_INPUTS_20261110.md"
PRIVATE = "local/migration72/d02-resume-20261110/a-avatar-ctor-next-inputs/"
SOURCE_PINS = {
    CONTRACT: "e8f89a0eaf344c2c90798eb941f73e24a765f0175a80c97189be6cdda75568f1",
    PRIVATE + "implementation-spec.private.json": "e02a3c9ca6ac39c7b4b70dd46034d58cd2d1c2387a6957f445ebc2997564a9e4",
    PRIVATE + "delivery.private.json": "2798916a8e1d40313245fb40200c077a742cc0fc2b09d3d82b9ef66348220dfa",
    PRIVATE + "audit-first.private.json": "8612bd1ed5f8446a554415512e09f0cf3d289f824baaab4cdcae1db4dd5d0703",
    PRIVATE + "audit-repeat.private.json": "8612bd1ed5f8446a554415512e09f0cf3d289f824baaab4cdcae1db4dd5d0703",
}


def verify_sources(root=core.ROOT):
    previous = head.verify_sources(root)
    loaded = {}
    for path, expected in SOURCE_PINS.items():
        raw = (root / path).read_bytes()
        if core.digest(raw) != expected:
            raise ValueError("Frozen ctor source differs: " + path)
        if path.endswith(".json"):
            loaded[path] = json.loads(raw)
    spec = loaded[PRIVATE + "implementation-spec.private.json"]
    if (spec["deltaIncludingFinalMax"] != {"reads": 16, "bytes": 608}
            or spec["staticAOT"]["methodDefinition"] != 15590 or spec["staticAOT"]["classInstance"] != 11):
        raise ValueError("Frozen ctor graph or AOT identity differs")
    audit = loaded[PRIVATE + "audit-first.private.json"]
    for path, pin in audit["inputPins"].items():
        # This includes the large metadata cache: hash only, never unpickle or
        # import its runtime data into this epoch's read graph.
        if _file_sha256(root / path) != pin["sha256"]:
            raise ValueError("Audited ctor source differs: " + path)
    return {"previousSourceChecks": previous, "ctorSourcePins": dict(SOURCE_PINS),
            "auditedCtorInputPins": audit["inputPins"], "metadataCacheHashedOnly": True,
            "currentFiveToolPinsRequiredInEpoch": True, "oldHeapImported": False,
            "arrayIdentityProved": False, "liveApproved": False, "replayApproved": False,
            "capturedCopyWriteApproved": False}


def _file_sha256(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def collect(read, module, teb, *, now=core.utc):
    ledger, result = head.begin_head_inputs(read, module, teb, now=now, profile=PROFILE)
    inputs = {"status": "partial", "items": {"arrayIdentityProved": False, "pointeeRead": False,
               "copyWriteApproved": False}, "hashSet": {"identityProved": False, "reusedSpans": [],
               "staticBinding": {"definition": 2147, "genericInstanceIndex": 11, "argumentKind": "UInt32",
                                 "methodDefinition": 15590, "entry": "0x157d97690"}},
              "secondRecordRead": False, "secondHeadRead": False, "nextLinkRead": False,
              "arrayBodyRead": False, "secondAllocationReturned": False, "canReplayPop": False,
              "capturedCopyWriteApproved": False, "nativeCPUExecuted": False,
              "wholeConstructorComplete": False, "nextDefaultSource": "B+594fd78/8 at157d97698"}
    result.update(schema="client72-tls-r1-pool-head-ctor-v1", profile=PROFILE, ctorInputs=inputs)
    if result["failure"] is None:
        try:
            _items(ledger, module, result["allocationInputs"], inputs)
            _hashset(ledger, module, result["r1"], result["tls"]["context"], inputs["hashSet"])
            inputs["status"] = "value-and-identity-candidate"
        except core.ReadStop as failure:
            result.update(failure=str(failure), failureStage="ctor-inputs")
            inputs["failure"] = str(failure)
    head.finish_head_inputs(ledger, result)
    inputs["stableInputBytes"] = result["status"] == "candidate"
    return result


def _items(ledger, module, allocation, result):
    raw = ledger.get("ctor.itemsCell", module + 0x5b74628, 8, source_reader="0x15183fe92")
    value = int.from_bytes(raw, "little")
    result["items"].update(value=value, itemsNonNull=value != 0, rawHex=raw.hex(), readStatus="read")
    result["firstPendingConstructorWrite"] = {"site": "0x15183fe99", "address": allocation["headPointer"] + 0x10,
        "width": 8, "sourceValue": value, "approved": False, "executed": False}
    if allocation["potentialUnreviewedEdge"]["site"] == "0x15183fe92":
        allocation["potentialUnreviewedEdge"] = {"site": "0x15183fe99", "reason": "items-store-H+10-not-approved"}


def _hashset(ledger, module, r1, context, result):
    klass = int.from_bytes(ledger.get("ctor.hashSetUsage", module + 0x5897cf0, 8,
                                    source_reader="0x15183fea1"), "little")
    result["classPointer"] = klass
    if klass == 0:
        result["usageProducer"] = "runtime usage writer/trigger unknown; static destination only"
        _blocked(result, "0x15183fea8", "class-usage-null-no-fallback")
    header = ledger.get("ctor.hashSetHeader", klass, 208, source_reader={"contract": CONTRACT, "span": 1})
    bits = struct.unpack_from("<I", header, 0x18)[0]
    generic = struct.unpack_from("<Q", header, 0x68)[0]
    result.update(headerHex=header.hex(), typeBits=bits, kind=(bits >> 16) & 255, byRef=bool(bits & 0x40000000),
        classC7=header[0xc7], classC8=header[0xc8], classCA=header[0xca],
        observedB4U16=struct.unpack_from("<H", header, 0xb4)[0],
        classPlus8Low3=struct.unpack_from("<I", header, 8)[0] & 7, genericClassPointer=generic)
    if result["kind"] != 21 or result["byRef"] or generic == 0:
        raise core.ReadStop("ctor:HashSet-class-header-identity-failed")
    raw = ledger.get("ctor.hashSetGenericClass", generic, 32, source_reader={"contract": CONTRACT, "span": 2})
    definition, instance, method, backref = struct.unpack("<I4xQQQ", raw)
    result.update(definition=definition, classInst=instance, methodInst=method, backref=backref)
    if definition != 2147 or method != 0 or backref != klass or instance == 0:
        raise core.ReadStop("ctor:HashSet-generic-identity-failed")
    shared = instance == r1["classInst"]
    result["actualClassInstEqualsList"] = shared
    count, array = struct.unpack("<I4xQ", _argument_span(ledger, "ctor.hashSetInst", instance, 16, shared, result))
    result.update(argumentCount=count, argumentArray=array)
    if count != 1 or array == 0:
        raise core.ReadStop("ctor:HashSet-class-inst-identity-failed")
    descriptor = int.from_bytes(_argument_span(ledger, "ctor.hashSetArgument", array, 8, shared, result), "little")
    result["descriptor"] = descriptor
    if descriptor == 0:
        raise core.ReadStop("ctor:HashSet-argument-null")
    raw = _argument_span(ledger, "ctor.hashSetDescriptor", descriptor, 16, shared, result)
    data, bits = struct.unpack_from("<QI", raw)
    result.update(argumentOpaqueData=data, argumentBits=bits, argumentKind=(bits >> 16) & 255,
                  argumentByRef=bool(bits & 0x40000000), descriptorHex=raw.hex())
    if result["argumentKind"] != 9 or result["argumentByRef"]:
        raise core.ReadStop("ctor:HashSet-UInt32-identity-failed")
    result.update(identityProved=True, identityStatus="HashSet-UInt32-runtime-graph-verified")
    result["sharedR1Sources"] = {
        "ordinaryRoot": {"value": r1["ordinaryRoot"], "firstOrdinal": ledger.successful[(module + 0x5bc8f18, 8)]["ordinal"]},
        "comparisonCell": {"value": r1["comparisonCell"], "firstOrdinal": ledger.successful[(module + 0x5bc96f8, 8)]["ordinal"]},
    }
    ordinary = int.from_bytes(ledger.get("ctor.ordinary2147", r1["ordinaryRoot"] + 2147 * 8, 8,
                                         source_reader="0x15a4d6bf1"), "little")
    result.update(ordinarySlot=ordinary, ordinaryHit=ordinary != 0,
                  ordinaryEqualsComparison=ordinary == r1["comparisonCell"])
    _allocation_path(context, result)


def _argument_span(ledger, name, address, width, shared, result):
    if shared:
        first = ledger.successful[(address, width)]
        result["reusedSpans"].append({"address": address, "requested": width, "firstOrdinal": first["ordinal"],
                                      "sha256": core.digest(first["raw"])})
        return first["raw"]
    return ledger.get(name, address, width, source_reader={"contract": CONTRACT, "field": name})


def _allocation_path(context, result):
    # Header identity remains valid even when these observed values select a
    # blocked producer/resolver or a different original allocation branch.
    if not result["classCA"] & 0x20:
        _blocked(result, "0x1405336eb", "CA20-unset-initialization-input-unprovided")
    if not result["ordinaryHit"]:
        result["nextProducerInput"] = {"addressRVA": "0x5bc95a8", "requested": 8, "read": False,
            "nextUnknownIATSite": "0x15a4d6c0f", "iatCellRVA": "0x3bac1f0"}
        _blocked(result, "0x15a4d6c03", "ordinary2147-null-VM-miss-producer-unprovided")
    if result["ordinaryEqualsComparison"]:
        _blocked(result, "0x140533784", "ordinary-comparison-selected-class-resolver")
    size = result["observedB4U16"]
    if size == 0:
        _blocked(result, "0x14053380b", "zero-request-original-branch")
    tagged_path = bool(result["classCA"] & 2)
    tag = int(result["classPlus8Low3"] != 1) if tagged_path else 0
    result["allocationInterpretation"] = {"status": "original-K2-size-tag-path", "requestU16": size,
        "taggedPath": tagged_path, "tagBitUsed": tag, "classArgument": result["classPointer"] | tag,
        "requestForcedTo56": False, "recordCellRead": False}
    if size > 0x8000:
        _blocked(result, "0x140563df5", "request-above-small-threshold-slow-tail")
    z = 64 - (size | 0x80).bit_length()
    shift = (60 - z) & 63
    bin_index = (((size - 1) >> shift) - 8 * z + 0x1c1) & 0xffffffff
    offset = 0x28 + bin_index * 0x10
    result["allocationInterpretation"].update(clz=z, shift=shift, binU32=bin_index,
        recordPointerCellOffset=offset, candidateRecordPointerCell=context + offset,
        secondRecordPointerKnown=False, readOnlyNativeStopBefore="0x140563d2d")
    if result["classCA"] & 1:
        result["postPopPotentialStop"] = {"site": "0x1405337f3", "target": "0x14055ecf0"}
    elif result["classC7"] == 0:
        result["postPopPotentialStop"] = {"site": "0x140525e2a", "unprovidedGlobalRVA": "0x5bc90f0"}
    result["nextMissingStorage"] = {"sourceReader": "0x140563d34", "address": context + offset,
                                   "requested": 8, "read": False, "newCaptureApprovalRequired": True}


def _blocked(result, site, reason):
    result["allocationStop"] = {"stopBefore": site, "reason": reason, "executed": False}
    raise core.ReadStop("ctor:" + reason)
