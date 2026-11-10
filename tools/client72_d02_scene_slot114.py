"""Audit the exact frozen Scene slot114 provenance gap; no PE or live access."""
import argparse
import hashlib
import io
import json
import pickle
import struct
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PRIVATE = "local/migration72/d02-resume-20261110b/scene-slot114/"
PROFILE = "protocol/cn-7.2/profile.json"
META = "local/migration72/d02-resume-20261108/r3-equipment/metadata.private.pkl"
FACTORY = "local/migration72/d02-active/main/scene-runtime-context-third.json"
SNAPSHOT = "local/migration72/d02-active/main/entry-context-tool/live-resume-20261108-2.json"
POOL = "local/migration72/d02-resume-20261108/scene-pool/first.json"
ROW = "local/migration72/d02-resume-20261110b/scene/row-body/"
PINS = {
    PROFILE: "a3b9eda630942ba8479f713fc1692c7eb7b05198ae382dfe878ecc963051fd5d",
    META: "df52eb70fe8e70dfecd1059e898d15e288cfa98bcefc78bcaabf753ae150349f",
    FACTORY: "bf9efd6225cd2836286b3a301bb2ea2f33dab6c6ddfb103d53aeed8f10db81d4",
    SNAPSHOT: "119f8a0b3c3ecc8829eda9bf7422c3646c643afa34ac041ea69b5607a02a7384",
    POOL: "3a650294fbd979a775f98e01db480a85bc6784edff157113c98aa0e10ebcc811",
    ROW + "code.bin": "8670d03e37b4155990e0f8fa4dc07238dc090d0143378df352abe61a2d583f65",
    ROW + "extraction.private.json": "7d5a30ee605c52dfab4381331905d6ef2d18c554bf944f57816cccc3b93405a5",
}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    output = args.output.resolve()
    if output.parent != (ROOT / PRIVATE).resolve() or output.exists():
        parser.error("Choose a fresh output directly in this card's private directory")
    raw = read_sources()
    report = audit(raw)
    report["sources"] = {p: dict(bytes=len(b), sha256=digest(b)) for p, b in raw.items()}
    report["toolSHA256"] = digest(Path(__file__).read_bytes())
    data = (json.dumps(report, sort_keys=True, indent=2, ensure_ascii=False) + "\n").encode()
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("xb") as stream:
        stream.write(data)
    print(json.dumps(dict(passed=True, result=report["result"], sourcePins=len(raw),
                          checks=len(report["checks"]), bytes=len(data), sha256=digest(data))))


def digest(data):
    return hashlib.sha256(data).hexdigest()


def read_sources():
    raw = {}
    for relative, expected in PINS.items():
        data = (ROOT / relative).read_bytes()
        if digest(data) != expected:
            raise ValueError("Frozen source changed: " + relative)
        raw[relative] = data
    return raw


class PureData(pickle.Unpickler):
    def find_class(self, module, name):
        raise ValueError(("Non-data metadata pickle", module, name))


def audit(raw):
    checks = []

    def check(condition, label):
        if not condition:
            raise ValueError(label)
        checks.append(label)

    profile = json.loads(raw[PROFILE])
    records, cells = PureData(io.BytesIO(raw[META])).load()
    factory = json.loads(raw[FACTORY])
    snapshot = json.loads(raw[SNAPSHOT])
    pool = json.loads(raw[POOL])
    row = json.loads(raw[ROW + "extraction.private.json"])
    frozen_inputs = {k: v["sha256"] for k, v in profile["files"].items()}
    check(set(frozen_inputs) == {"binary", "metadata", "startup", "sdk", "astrolabe"}, "fixed-five-input-set")
    check({k: v["sha256"] for k, v in snapshot["inputChecks"]["files"].items()}
          == pool["fixedInputs"] == frozen_inputs, "frozen-capture-and-pool-input-identities")
    check(row["inputs"] == {k: dict(bytes=v["size"], sha256=v["sha256"])
                             for k, v in profile["files"].items()}, "row-original-five-input-identities")
    check(len(raw[ROW + "code.bin"]) == row["codeBytes"] == 585 and
          row["codeSHA256"] == PINS[ROW + "code.bin"] and
          raw[ROW + "code.bin"][0x146:0x14d].hex() == "4989b640010000", "original-template-store-field138373")

    world, = [m for m in factory["declarations"]["methods"] if m["entry"] == "0x15154bf20"]
    position, body = int(world["entry"], 16), bytearray()
    for instruction in world["instructions"]:
        if int(instruction["address"], 16) != position:
            raise ValueError("Noncontiguous frozen factory")
        code = bytes.fromhex(instruction["bytes"])
        body.extend(code)
        position += len(code)
    check(position == int(world["end"], 16) and digest(body) == world["codeSHA256"], "complete-frozen-factory-body")
    by_site = {int(i["address"], 16): i for i in world["instructions"]}
    expected_flow = {
        0x15154BFB9: "488b1588bb34f4",  # requested class usage in RDX
        0x15154BFC6: "ff90f0000000",  # actual pool slot4
        0x15154BFCC: "4889c6",  # returned object retained in RSI
        0x15154C06F: "488b06",  # actual returned object's class
        0x15154C072: "4889f1",  # this=RSI
        0x15154C075: "4489ea",  # first original uint32
        0x15154C078: "4589f0",  # frozen static uint32
        0x15154C07B: "4531c9",  # original zero fourth register
        0x15154C07E: "ff9060040000",  # actual class slot114
    }
    check(all(by_site[site]["bytes"] == code for site, code in expected_flow.items()), "exact-object-class-slot114-argument-flow")
    check(cells[0x145897B48]["type"]["typeDefinition"] == 46314 and
          (0x460 - 0xD0) // 8 == 114, "requested-class-usage-and-slot-coordinate")
    check(not any(i["text"].startswith("mov rsi,") or i["text"].startswith("lea rsi,")
                  for i in world["instructions"] if 0x15154BFCC < int(i["address"], 16) < 0x15154C07E),
          "returned-object-register-not-replaced-before-slot114")

    child, = [m for m in records[46314]["methods"] if m["virtualSlot"] == 114]
    parent, = [m for m in records[28174]["methods"] if m["virtualSlot"] == 114]
    check(records[46314]["parent"]["typeDefinition"] == 28174 and
          (child["index"], child["registeredAddress"], child["parameterCount"])
          == (384166, "0x1483df040", 2) and
          (parent["index"], parent["registeredAddress"], parent["parameterCount"])
          == (219197, "0x148d85fc0", 2), "declared-child-override-and-distinct-parent-candidate")

    reads = snapshot["reads"]
    names = {r["name"]: r for r in reads}
    check(len(reads) == len(names) == 36, "entire-frozen-capture-has-36-unique-read-records")
    spans = []
    for capture in reads:
        value = bytes.fromhex(capture.get("bytesHex") or "")
        if capture["error"] is None:
            if len(value) != capture["requested"] or digest(value) != capture["sha256"]:
                raise ValueError("Incomplete frozen read: " + capture["name"])
            spans.append((capture["address"], capture["address"] + len(value), capture["name"]))
    check(len(spans) == 36, "all-36-raw-read-lengths-and-sha256-validated")
    cell, header = names["pool.entityClass46314.cell"], names["pool.entityClass46314.header"]
    head = bytes.fromhex(header["bytesHex"])
    klass, = struct.unpack("<Q", bytes.fromhex(cell["bytesHex"]))
    check(cell["address"] == snapshot["mainModule"]["base"] + 0x5897B48 and
          header["address"] == klass and len(head) == 0xD0, "captured-requested-class-cell-to-complete208-byte-head")
    check(struct.unpack_from("<Q", head, 0x10)[0] == 46314 and
          (struct.unpack_from("<I", head, 0x18)[0] >> 16) & 255 == 18, "captured-requested-class-id46314-kind18")
    target = klass + 0x460
    overlaps = [(name, hex(start), hex(end)) for start, end, name in spans
                if start < target + 8 and end > target]
    check(not overlaps, "no-byte-of-requested-class-slot114-in-any-frozen-read")
    check(snapshot["atomicSnapshot"] is False and snapshot["worldInitializationVerified"] is False and
          pool["allocatorCompleted"] is False and pool["worldFactoryCompleted"] is False and
          pool["naturalCallerObserved"] is False and pool["actualModePrefix"]["allocatorReturned"] is False,
          "no-returned-entity-or-natural-factory-provenance-in-frozen-results")
    check(pool["actualModePrefix"]["stop"]["reader"] == "0x14050a8ae" and
          pool["actualModePrefix"]["stop"]["instructionExecuted"] is False,
          "prior-prefix-stopped-before-first-missing-loader-tls-read")
    check(by_site[0x15154C10F]["text"] == "call 0x151539a40" and
          by_site[0x15154C12E]["text"] == "call 0x147c4b850", "later-factory-calls-depend-on-unresolved-slot-return")

    return dict(
        task="F-03d3s2-SCENE-SLOT114", result="exact-provenance-gap", evidenceKind="frozen-static-and-historical-capture-audit",
        checks=checks, frozenInputs=frozen_inputs,
        factory=dict(entry=world["entry"], codeSHA256=world["codeSHA256"], callSite="0x15154c07e",
                     objectSource="RSI retains the actual pool slot4 return; class read at15154c06f",
                     arguments=["RCX=RSI", "EDX=R13D original first uint32", "R8D=R14D frozen static uint32", "R9D=0"]),
        metadataCandidates=dict(requestedClass=46314, child=child, parent=parent, actualVtableBindingObserved=False),
        capturedClass=dict(sourceScope="requested class, not returned entity", cellSHA256=cell["sha256"],
                           headerSHA256=header["sha256"], headerBytes=208, slotOffset="0x460", slotBytes=8,
                           slotCapturedBytes=0, fullCaptureReadCount=len(spans), overlappingReadRecords=overlaps),
        minimumMissing=dict(
            runtime="Actual15154c07e invocation identity/frame and returned entity RSI; raw[RSI]8B -> actual klass, class header208B and raw[klass+460]8B in that same invocation/epoch",
            dataReadBudget=dict(reads=3, bytes=224, excludes="event/frame provenance, fixed-file/module identity and observer lifecycle must be separately designed/admitted"),
            staticAlternative="Only exact existing class/vtable initialization bytes, if independently sourced, can replace runtime binding; metadata virtualSlot and same name cannot",
            bodyGate="After exact binding, recheck current GAME_PATH complete five input hashes, exact AOT/caller and unique pdata begin/end; submit one-body cap before reading; no candidate body read here"),
        boundaries=dict(newPEBodies=0, nativeCPU=0, liveReads=0, javaImplementationUnlocked=False,
                        exactImplementationResolved=False, naturalDynamicTypeResolved=False, completeScene=False,
                        resourceReady=False, d02Passed=False))


if __name__ == "__main__":
    main()
