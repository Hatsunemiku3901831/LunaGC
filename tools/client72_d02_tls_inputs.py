"""Fixed TLS/R1 input graph: read callbacks only, no process or CPU backend."""

from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import struct

ROOT = Path(__file__).resolve().parents[1]
PRIVATE = "local/migration72/d02-resume-20261110/a-tls-preparation/"
OWNED = PRIVATE + "owned-teb/implementation-20261110-sol-xhigh-v3/"
SPEC_SHA = "81c85ace479805daf2b41eeacb2311b09d2b08d2f24dfd3ac7f654bc43b3cfe3"
FRESH_SHA = "73e55971e1ab3785e9a13b7619bedda89da9a81fbcd548c740bfec90e60a6754"
OWNED_PROOF_SHA = "829fde6fea17a4b6733a0e92776bed5f508a5e5fec5e7ec431875c02258c2f79"
SOURCE_PINS = {
    "docs/CLIENT_72_D02_TLS_INPUT_SPEC_20261110.md": SPEC_SHA,
    "tools/observe_client72_uninstrumented.py": "e0b4134e6bb7be8c758b3a7bad3884a8246b25c6d65cd8ca3c91d0941ed33c6e",
    "tools/client72_d02_owned_teb_probe.cpp": "db45f225650dae36298cd3833a82d033f63869b3f47a6c2a6c4660ad8ac0f0b0",
    "tools/client72_d02_owned_teb_abi.py": "e5e6d0c6d3604de7509922cefba235326eee53ad3827099712a4550d98a5bcfd",
    "local/migration72/d02-parallel-20261109/fresh-successor-replay/first.private.json": FRESH_SHA,
    OWNED + "runs/two-fresh-report.json": OWNED_PROOF_SHA,
    OWNED + "main-independent-review.private.json": "88d59287628a883f62a372acfc40e902076455fc27fa4ccbb2e4c37bc80ebd46",
}
FIRST_READS, FIRST_BYTES, MAX_READS, MAX_BYTES, MAX_SINGLE = 13, 340, 26, 680, 208
READ_PROFILES = {"avatar-r1": (13, 340, 26, 680), "avatar-r1-pool": (18, 369, 36, 738),
                 "avatar-r1-pool-head": (28, 622, 56, 1244)}
U64 = (1 << 64) - 1


def utc():
    return datetime.now(timezone.utc).isoformat()


def digest(raw):
    return hashlib.sha256(raw).hexdigest()


def verify_sources(root=ROOT):
    """File-only gate. The previous epoch supplies rules, never new heap bytes."""
    loaded = {}
    for path, expected in SOURCE_PINS.items():
        raw = (root / path).read_bytes()
        if digest(raw) != expected:
            raise ValueError("Frozen source differs: " + path)
        if path.endswith(".json"):
            loaded[path] = json.loads(raw)
    proof = loaded[OWNED + "runs/two-fresh-report.json"]
    review = loaded[OWNED + "main-independent-review.private.json"]
    if (proof["passed"] is not True or proof["executedFreshProcesses"] != 2 or proof["validatedQueryCount"] != 6
            or review["passed"] is not True or review["twoFreshProofSHA256"] != OWNED_PROOF_SHA):
        raise ValueError("Owned ABI proof or independent review differs")
    fresh = loaded["local/migration72/d02-parallel-20261109/fresh-successor-replay/first.private.json"]
    rows = {row["ordinal"]: row for row in fresh["hostBudget"]["records"]}
    if [rows[n]["width"] for n in range(27, 36)] != [8, 208, 32, 16, 8, 16, 8, 8, 8]:
        raise ValueError("Frozen R1 read source differs")
    base = int(fresh["moduleBase"], 16)
    if [int(rows[n]["address"], 16) - base for n in (27, 33, 35)] != [0x588ca20, 0x5bc8f18, 0x5bc96f8]:
        raise ValueError("Frozen R1 root source differs")
    raw = {n: bytes.fromhex(rows[n]["bytesHex"]) for n in range(27, 36)}
    address = {n: int(rows[n]["address"], 16) for n in range(27, 36)}
    definition, instance, method, backref = struct.unpack("<I4xQQQ", raw[29])
    count, array = struct.unpack("<I4xQ", raw[30])
    bits = struct.unpack_from("<I", raw[32], 8)[0]
    if (int.from_bytes(raw[27], "little") != address[28]
            or struct.unpack_from("<Q", raw[28], 0x68)[0] != address[29]
            or (definition, instance, method, backref) != (1255, address[30], 0, address[28])
            or (count, array) != (1, address[31]) or int.from_bytes(raw[31], "little") != address[32]
            or (bits >> 16) & 255 != 9 or bits & 0x40000000
            or int.from_bytes(raw[33], "little") + 1255 * 8 != address[34]):
        raise ValueError("Frozen source.audit27-35 pointer/field relationships differ")
    return {"sourcePins": dict(SOURCE_PINS), "ownedABIValidatedOnly": True,
            "r1SourceAuditOrdinals": list(range(27, 36)), "r1SourceFieldRelationshipsVerified": True, "oldHeapImported": False,
            "liveApproved": False, "replayApproved": False}


class ReadStop(Exception):
    pass


class ReadLedger:
    def __init__(self, read, now, *, profile="avatar-r1"):
        if profile not in READ_PROFILES:
            raise ValueError("Unknown fixed TLS read profile")
        self.first_read_limit, self.first_byte_limit, self.read_limit, self.byte_limit = READ_PROFILES[profile]
        self.read, self.now = read, now
        self.records, self.successful = [], {}
        self.requested = self.first_reads = self.first_bytes = 0
        self.final = {"status": "not-started", "uniqueSuccessfulSpans": 0, "verifiedSpans": 0, "records": []}

    def get(self, name, address, width, phase="first", *, source_reader=None):
        if (not 0 < width <= MAX_SINGLE or len(self.records) >= self.read_limit or self.requested + width > self.byte_limit
                or (phase == "first" and (self.first_reads >= self.first_read_limit or self.first_bytes + width > self.first_byte_limit))):
            raise ReadStop("read-budget")
        self.requested += width
        if phase == "first":
            self.first_reads += 1
            self.first_bytes += width
        begin = self.now()
        called = 0 < address <= U64 and address + width <= 1 << 64
        if not called:
            raw, error = b"", {"kind": "address-outside-uint64"}
        else:
            try:
                raw, error = self.read(address, width)
            except OSError as failure:
                raw, error = b"", {"kind": "read-os-error", "winerror": getattr(failure, "winerror", None), "errno": failure.errno}
        if not isinstance(raw, bytes):
            raise ValueError("Read callback must return bytes and an error record")
        if error is None and len(raw) != width:
            error = {"kind": "wrong-read-length", "actual": len(raw)}
        row = {"ordinal": len(self.records) + 1, "name": name, "phase": phase, "address": address,
               "requested": width, "bytesRead": len(raw), "bytesHex": raw.hex(), "error": error,
               "backendCalled": called, "beginUTC": begin, "endUTC": self.now()}
        self.records.append(row)
        if source_reader is not None:
            row["sourceReader"] = source_reader
        if error is not None:
            raise ReadStop(name + ":unreadable")
        if phase == "first":
            for (old_address, old_width), old in self.successful.items():
                start, end = max(address, old_address), min(address + width, old_address + old_width)
                if start < end and raw[start-address:end-address] != old["raw"][start-old_address:end-old_address]:
                    row["error"] = {"kind": "overlap-drift"}
                    raise ReadStop(name + ":overlap-drift")
            self.successful.setdefault((address, width), {"raw": raw, "ordinal": row["ordinal"], "name": name,
                                                         "sourceReader": source_reader})
        return raw

    def pointer(self, name, address):
        value = int.from_bytes(self.get(name, address, 8), "little")
        if value == 0:
            raise ReadStop(name + ":null-pointer")
        return value

    def repeat_final(self):
        self.final["uniqueSuccessfulSpans"] = len(self.successful)
        self.final["status"] = "repeated-unchanged"
        for (address, width), first in self.successful.items():
            row = {"firstOrdinal": first["ordinal"], "address": address, "requested": width, "status": "partial"}
            self.final["records"].append(row)
            try:
                raw = self.get(first["name"], address, width, "final", source_reader=first["sourceReader"])
                row["finalOrdinal"] = self.records[-1]["ordinal"]
                if raw != first["raw"]:
                    row["status"] = "changed"
                    raise ReadStop(first["name"] + ":final-drift")
                row["status"] = "unchanged"
                self.final["verifiedSpans"] += 1
            except ReadStop as failure:
                self.final.update(status="partial", failure=str(failure))
                break

    def finish(self, result):
        self.repeat_final()
        if result["failure"] is None and self.final["status"] == "repeated-unchanged":
            result["status"] = "candidate"
        elif result["failure"] is None:
            result["failure"] = self.final["failure"]
        result.update(records=self.records, inputConsistency=self.final, endUTC=self.now(),
                      budget={"reads": len(self.records), "requestedBytes": self.requested,
                              "backendReads": sum(row["backendCalled"] for row in self.records),
                              "firstPassReads": self.first_reads, "firstPassBytes": self.first_bytes,
                              "maxFirstPassReads": self.first_read_limit, "maxFirstPassBytes": self.first_byte_limit,
                              "maxReads": self.read_limit, "maxBytes": self.byte_limit, "maxSingleRead": MAX_SINGLE})
        return result


def collect(read, module, teb, *, now=utc):
    """Collect this epoch only. A TEB must come from the retained-primary adapter."""
    ledger, result = start_collection(read, module, teb, now=now)
    try:
        _collect_tls(ledger, module, teb, result["tls"])
        _collect_r1(ledger, module, result["r1"])
    except ReadStop as failure:
        result["failure"] = str(failure)
    return ledger.finish(result)


def start_collection(read, module, teb, *, now=utc, profile="avatar-r1"):
    """Shared entrance for the two fixed TLS/R1 consumers; one final per ledger."""
    if (not callable(read) or not callable(now) or type(module) is not int or type(teb) is not int
            or not 0 < module <= U64 - 0x5eef084 or not 0 < teb <= U64 - 0x60):
        raise ValueError("Read callback, clock and full64 module/TEB are required")
    ledger = ReadLedger(read, now, profile=profile)
    result = {"schema": "client72-tls-r1-inputs-v1", "status": "partial", "failure": None,
              "beginUTC": now(), "moduleBase": module, "teb": teb, "tls": {}, "r1": {},
              "requiresRetainedPrimaryProof": True, "candidateGraphOnly": True,
              "nonAtomic": True, "noWrites": True, "noNativeCPU": True,
              "naturalAvatarAllocatorThreadObserved": False, "canReplayPop": False, "oldHeapImported": False,
              "gsBaseRegisterReadDirectly": False, "liveApproved": False, "replayApproved": False}
    return ledger, result


def _collect_tls(ledger, module, teb, result):
    result["index"] = int.from_bytes(ledger.get("tls.index", module + 0x5eef080, 4), "little")
    result["vector"] = ledger.pointer("tls.vector", teb + 0x58)
    result["moduleTLS"] = ledger.pointer("tls.module", result["vector"] + result["index"] * 8)
    # Null is preserved by the ledger and stops at the original caller boundary.
    result["context"] = ledger.pointer("tls.context", result["moduleTLS"] + 0x19d0)


def _collect_r1(ledger, module, result):
    pointer = ledger.pointer("r1.listClass", module + 0x588ca20)
    result["listClass"] = pointer
    header = ledger.get("r1.listHeader", pointer, 208)
    bits = struct.unpack_from("<I", header, 0x18)[0]
    result.update(headerHex=header.hex(), typeBits=bits, typeKind=(bits >> 16) & 255,
                  byRef=bool(bits & 0x40000000), caObserved=header[0xca], c8Observed=header[0xc8],
                  sizeB4Observed=struct.unpack_from("<I", header, 0xb4)[0])
    if result["typeKind"] != 21 or result["byRef"]:
        raise ReadStop("r1:class-type-mismatch")
    generic = struct.unpack_from("<Q", header, 0x68)[0]
    result["genericClass"] = generic
    if generic == 0:
        raise ReadStop("r1:generic-class-null")
    raw = ledger.get("r1.genericClass", generic, 32)
    definition, instance, method, backref = struct.unpack("<I4xQQQ", raw)
    result.update(definition=definition, classInst=instance, methodInst=method, backref=backref)
    if definition != 1255 or method != 0 or backref != pointer or instance == 0:
        raise ReadStop("r1:generic-identity-mismatch")
    count, array = struct.unpack("<I4xQ", ledger.get("r1.classInst", instance, 16))
    result.update(argumentCount=count, argumentArray=array)
    if count != 1 or array == 0:
        raise ReadStop("r1:class-inst-mismatch")
    descriptor = ledger.pointer("r1.argument0", array)
    result["descriptor"] = descriptor
    raw = ledger.get("r1.uint32Descriptor", descriptor, 16)
    data, bits = struct.unpack_from("<QI", raw)
    result.update(descriptorHex=raw.hex(), argumentDataObserved=data, argumentBits=bits,
                  argumentKind=(bits >> 16) & 255, argumentByRef=bool(bits & 0x40000000))
    if result["argumentKind"] != 9 or result["argumentByRef"]:
        raise ReadStop("r1:uint32-type-mismatch")
    result["ordinaryRoot"] = ledger.pointer("r1.ordinaryRoot", module + 0x5bc8f18)
    result["ordinarySlot1255"] = ledger.pointer("r1.ordinarySlot1255", result["ordinaryRoot"] + 1255 * 8)
    result["comparisonCell"] = ledger.pointer("r1.comparisonCell", module + 0x5bc96f8)
    result["observedEqualities"] = {
        "ordinarySlotEqualsClosedList": result["ordinarySlot1255"] == pointer,
        "ordinarySlotEqualsComparisonCell": result["ordinarySlot1255"] == result["comparisonCell"],
        "closedListEqualsComparisonCell": pointer == result["comparisonCell"],
    }
