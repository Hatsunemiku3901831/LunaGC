"""Conditional projection of two frozen Proud scalar tokens; no target execution."""
import argparse
import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "local/migration72/d02-resume-20261111c/proud-setter-semantics/conditional-row-projection"
PINS = {
    "local/migration72/d02-resume-20261111b/proud-gate/row-bridge/conditional-fields-main-third.private.json": (5123, "91f62c32da64a025ea08db4c95d16d2b470c1368a3025dd01b748adc6046f94a"),
    "local/migration72/d02-active/plain/initialization-resources-elevated5.json": (36621, "c5e6b05778f6326891065f120b966f4a8135b8601f097c30f6477724066a765d"),
    "local/migration72/d02-resume-20261108/r3-mapping/main-third.json": (10370640, "d990c2c469e5a9d3ee40351e4dfe5223ae49afaa855abe5d912427139c66307e"),
    "local/migration72/d02-resume-20261111b/proud-gate/scalar-setter-window/main-third/code.bin": (157, "5c7bd52bc54dd9ebfa01137bd2b56c741eb5f9bb2ad05331158a9c85aa20d3dc"),
    "local/migration72/d02-resume-20261111b/proud-gate/scalar-setter-window/main-third/code.asm.private.json": (9940, "dec833d145c810912894b66eca752cd65a257b7e46ee67f3366d5ce088062a6f"),
    "local/migration72/analysis/scene-helper-14aef9db0.txt": (1384, "8b5b2f341f887eef750e7ed4b062556a04148b75cce0de4b15d8022beee004f5"),
    "tools/client72_avatar_natural_tables.py": (44027, "920395dfd4817523951d2c766167ca481d8f6b2b64288980fee2cade2ae9ecad"),
    "local/migration72/d02-resume-20261111c/proud-setter-semantics/review.private.json": (11502, "b440b24c5729992e569a3c62d25ccd5b7f7c798bd8ac39ab0a95663e63ea09ed"),
    "local/migration72/d02-resume-20261111c/proud-setter-semantics/fixture-oracle.private.json": (4795, "d75e0c38ed5d4fdf7a907268582ce327629086a9012ca33d0927babda1a8923e"),
    "local/migration72/d02-resume-20261111c/proud-setter-semantics/terra-request.private.json": (9413, "680af648e75a3057b816ad64d6803755b13be28f54085ab0ae63d8ac90f0eac1"),
}
BRIDGE, SAMPLE, MAPPING = list(PINS)[:3]
EXPECTED = [
    (203376, "0x90", "0x30", [218, 222], "P", 0x4000000, "2db5fdf6", 56, 221),
    (203373, "0x84", "0x38", [226, 230], "rawH", 0x800, "7c7c4543", 59, 1),
]
PROVENANCE = {"naturalInverseSHA256": "8717ded77f717cf44988dee2720b99bbd60c751426b130f6166a2ffc379a57f6", "seedRawLEHex": "2e000a3b", "seedU32Hex": "3b0a002e", "forwardReconstructedSHA256": "2eed64897eabc0ebc89c970de74665c05325c4b55c2ab259ef685f49b44c7931", "combinedFixtureSHA256": "2c01c4289cd5c8e363f8b9f7d02446ea2bbad008ecc5f752d91e3c8769cfc76a", "forwardActuallyObserved": False, "historicalSampleAtomic": False}


def verify_blob(data, pin):
    if len(data) != pin[0] or hashlib.sha256(data).hexdigest() != pin[1]:
        raise ValueError("input size/SHA mismatch")


def validate_bridge(bridge):
    if bridge["key"] != 22101 or len(bridge["fields"]) != 2:
        raise ValueError("bridge key or field count")
    for field, expected in zip(bridge["fields"], EXPECTED):
        token, managed = field["fileToken"], field["managedField"]
        actual = (managed["index"], managed["offset"], field["nativeDataOffset"], token["span"], token["presence"]["source"], token["presence"]["mask"], token["rawHex"], token["reportTokenIndex"], token["setterInputU32"])
        if actual != expected or field["managedOwner"] != 41105 or managed["typeDefinition"] != 58209 or token["presence"]["present"] is not True:
            raise ValueError("bridge field, token or presence mismatch")
        if field["actualEncoded"] is not None or field["actualGetterU32"] is not None or field["actualManagedObject"] is not False:
            raise ValueError("bridge actual state mismatch")
    if bridge["actualEncoded"] is not None or bridge["actualGetterU32"] is not None or bridge["actualManagedObject"] is not False:
        raise ValueError("bridge actual state mismatch")
    if (int.from_bytes(bytes.fromhex(bridge["fields"][0]["fileToken"]["rawHex"]), "little") ^ 0xf6fdb5f0) != 221 or ((int.from_bytes(bytes.fromhex(bridge["fields"][1]["fileToken"]["rawHex"]), "little") + 0xbcba8385) & 0xffffffff) != 1:
        raise ValueError("token transform mismatch")


def load_inputs():
    blobs = {}
    for path, pin in PINS.items():
        data = (ROOT / path).read_bytes()
        verify_blob(data, pin)
        blobs[path] = data
    bridge = json.loads(blobs[BRIDGE])
    validate_bridge(bridge)
    sample, mapping = json.loads(blobs[SAMPLE]), json.loads(blobs[MAPPING])
    reads = {row["label"]: row for row in sample["reads"] if row["label"].startswith("resourceDecode.")}
    raw = {label: bytes.fromhex(row["bytesHex"]) for label, row in reads.items()}
    for label, data in raw.items():
        if len(data) != reads[label]["length"] or hashlib.sha256(data).hexdigest() != reads[label]["sha256"] or reads[label]["error"] is not None:
            raise ValueError("snapshot read mismatch")
    array = int.from_bytes(raw["resourceDecode.permutation"], "little")
    inverse = [raw[f"resourceDecode.quarter{i}"] for i in range(4)]
    if sample["atomicSnapshot"] is not False or reads["resourceDecode.length"]["address"] != array + 0x18 or int.from_bytes(raw["resourceDecode.length"], "little") != 2048:
        raise ValueError("snapshot header mismatch")
    if any(len(q) != 256 or len(set(q)) != 256 or reads[f"resourceDecode.quarter{i}"]["address"] != array + 0x420 + i * 256 for i, q in enumerate(inverse)):
        raise ValueError("inverse quarter mismatch")
    forward = [bytes(q.index(i) for i in range(256)) for q in inverse]
    seed = int.from_bytes(raw["resourceDecode.seed"], "little")
    codec = mapping["native"]["scalarCodec"]
    if (hashlib.sha256(b"".join(inverse)).hexdigest(), hashlib.sha256(b"".join(forward)).hexdigest(), hashlib.sha256(b"".join(forward + inverse)).hexdigest(), raw["resourceDecode.seed"].hex(), f"{seed:08x}") != (PROVENANCE["naturalInverseSHA256"], PROVENANCE["forwardReconstructedSHA256"], PROVENANCE["combinedFixtureSHA256"], PROVENANCE["seedRawLEHex"], PROVENANCE["seedU32Hex"]) or (codec["inverseSHA256"], codec["forwardReconstructedSHA256"], codec["tableSHA256"], codec["seedHex"]) != (PROVENANCE["naturalInverseSHA256"], PROVENANCE["forwardReconstructedSHA256"], PROVENANCE["combinedFixtureSHA256"], PROVENANCE["seedRawLEHex"]):
        raise ValueError("fixture provenance mismatch")
    return bridge, forward, inverse, seed


def setter(value, forward, seed):
    return int.from_bytes(bytes(forward[i][b] for i, b in enumerate(value.to_bytes(4, "big"))), "big") ^ seed


def getter(encoded, inverse, seed):
    return int.from_bytes(bytes(inverse[i][b] for i, b in enumerate((encoded ^ seed).to_bytes(4, "big"))), "big")


def static_stop(kind, value, length):
    if length is None:
        return {"stopPC": "0x14aefa127" if kind == "setter" else "0x14aef9e4e", "payloadRead": False, "seedRead": False, "storedRead": False, "targetWrite": False, "errorCall": "0x1404c2be0"}
    setter_mode = kind == "setter"
    pcs = ["0x14aefa0b7", "0x14aefa0cc", "0x14aefa0db", "0x14aefa0ea"] if setter_mode else ["0x14aef9de7", "0x14aef9dfb", "0x14aef9e0a", "0x14aef9e19"]
    indices = [(0 if setter_mode else 0x400) + i * 0x100 + b for i, b in enumerate(value.to_bytes(4, "big"))]
    stop = next((pc for index, pc in zip(indices, pcs) if index >= length), None)
    return {"stopPC": stop, "payloadRead": stop is None, "seedRead": not setter_mode or stop is None, "storedRead": not setter_mode, "targetWrite": setter_mode and stop is None, "errorCall": "0x1404c2650" if stop else None}


def project(bridge, forward, inverse, seed):
    fields = []
    for source in bridge["fields"]:
        token = source["fileToken"]
        value = token["setterInputU32"]
        encoded = setter(value, forward, seed)
        fields.append({"owner": source["managedOwner"], "fieldIndex": source["managedField"]["index"], "managedOffset": source["managedField"]["offset"], "nativeOffset": source["nativeDataOffset"], "span": token["span"], "presenceSource": token["presence"]["source"], "presenceMask": token["presence"]["mask"], "present": token["presence"]["present"], "rawHex": token["rawHex"], "setterInputU32": value, "conditionalDecodedU32": value, "fixtureEncodedHex": f"{encoded:08x}", "fixtureGetterU32": getter(encoded, inverse, seed), "actualEncoded": None, "actualGetterU32": None, "actualManagedObject": False, "proofPCs": source["proofPCs"]})
    return {"task": "E-01d1/R3-PROUD-CONDITIONAL-SCALAR-ROW-PROJECTION", "key": 22101, "fields": fields, "sourcePins": {path: {"bytes": n, "sha256": h} for path, (n, h) in PINS.items()}, "codecProvenance": PROVENANCE, "arithmeticConditions": ["all selected setter/getter bounds and physical memory conditions pass", "read DWORD is the unchanged setter/copy output", "ks==kg", "Gg_j[Fs_j[x_j]]==x_j for all four selected bytes"], "unobservedOperationalConditions": ["natural forward contents and writer", "Proud-time codec epoch/seed relation", "reader backend/cursor", "actual wrapper handle", "QueryWrapper out0 and lifetime", "actual41105 row/representation", "other required row/config fields"], "scope": {"actualEncoded": None, "actualGetterU32": None, "actualManagedObject": False, "actualQueryWrapperBaseOrLifetimeObserved": False, "actualPairMapHit": False, "currentAdoptionProven": False, "resourceReady": False, "d02Complete": False, "targetCPU": 0, "live": 0, "PERead": 0, "globalRead": 0}}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", required=True, choices=("fresh1.private.json", "fresh2.private.json", "main-third.private.json"))
    args = parser.parse_args()
    bridge, forward, inverse, seed = load_inputs()
    result = project(bridge, forward, inverse, seed)
    OUT.mkdir(parents=True, exist_ok=True)
    with (OUT / args.output).open("x", encoding="utf-8", newline="\n") as output:
        output.write(json.dumps(result, ensure_ascii=False, indent=2, sort_keys=True) + "\n")


if __name__ == "__main__":
    main()
