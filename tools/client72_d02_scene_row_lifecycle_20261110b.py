"""Frozen-evidence Scene-row audit. No PE, process, native CPU, or Java access."""
import argparse
import hashlib
import io
import json
import pickle
import struct
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PRIVATE = "local/migration72/d02-resume-20261110b/scene/"
META = "local/migration72/d02-resume-20261108/r3-equipment/metadata.private.pkl"
RUNTIME = "local/migration72/d02-active/main/scene-runtime-context-third.json"
ENTRY = "local/migration72/d02-active/main/scene-entry-third.json"
OLD = "local/migration72/d02-resume-20261110/scene-row-consumer-window/code.bin"
PINS = {
    META: "df52eb70fe8e70dfecd1059e898d15e288cfa98bcefc78bcaabf753ae150349f",
    RUNTIME: "bf9efd6225cd2836286b3a301bb2ea2f33dab6c6ddfb103d53aeed8f10db81d4",
    ENTRY: "917a5ad0505c6e90e57c4080f0521e26589fadd0ccfb29eb3257363b8f2a9780",
    OLD: "1a3a905d174604d2edb9c531d86569364f232240de6a5d036369feda86d8c0ec",
    "protocol/cn-7.2/profile.json": "a3b9eda630942ba8479f713fc1692c7eb7b05198ae382dfe878ecc963051fd5d",
    PRIVATE + "one-window-request.private.json": "bc81381cba28b16b10096373f9830df7b6ecdcf1f4916433212c63f86f166027",
    PRIVATE + "extract_row_body.private.py": "c3325782fb5972014163806285be532eabf5d1f993fea0b4d8d8807fff5d4942",
    PRIVATE + "resolve_row_usages.private.py": "cfd9fbcdd89daf46677a312fca78302260468aae460bf2668709c970a21b36c5",
    PRIVATE + "row-body/code.bin": "8670d03e37b4155990e0f8fa4dc07238dc090d0143378df352abe61a2d583f65",
    PRIVATE + "row-body/code.asm": "170faa269332449cc6de612e7e79bb4edc4b3538faaeabd9d74d69469051887d",
    PRIVATE + "row-body/pdata.bin": "7e370b146cd71483aa41f65a566afe37e0d1da2677ffaa454a63d36db0cb4aef",
    PRIVATE + "row-body/aot-slot.bin": "21b0beaa16e3b3f0b654f9e51710b113aadc930f4208017f824a811b031f2aea",
    PRIVATE + "row-body/aot-pointer-cell.bin": "68f5f1908ac9f789c3fc0b5e71c5b8af00962605561f7b8ea2cc4718f00a83da",
    PRIVATE + "row-body/caller.bin": "16c69b3367b4362c030e6602cda18940893559ee7b4691950ccceba7f0131043",
    PRIVATE + "row-body/extraction.private.json": "7d5a30ee605c52dfab4381331905d6ef2d18c554bf944f57816cccc3b93405a5",
    PRIVATE + "method-usages.private.json": "619d60b5d4bd568f70dabbf7c26c9c86a534896b590e1c53204f67a41550b243",
}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    output = args.output.resolve()
    if output.parent != (ROOT / PRIVATE).resolve() or output.exists():
        parser.error("Use a fresh output directly in this card's private directory")
    raw, sources = read_sources()
    report = audit(raw)
    report["sources"] = sources
    report["toolSHA256"] = hashlib.sha256(Path(__file__).read_bytes()).hexdigest()
    data = (json.dumps(report, sort_keys=True, indent=2, ensure_ascii=False) + "\n").encode()
    with output.open("xb") as stream:
        stream.write(data)
    print(json.dumps(dict(passed=True, checks=len(report["checks"]), bytes=len(data),
                          sha256=hashlib.sha256(data).hexdigest())))


class PureData(pickle.Unpickler):
    def find_class(self, module, name):
        raise ValueError(("Non-data metadata pickle", module, name))


def read_sources():
    raw, sources = {}, {}
    for relative, expected in PINS.items():
        data = (ROOT / relative).read_bytes()
        actual = hashlib.sha256(data).hexdigest()
        if actual != expected:
            raise ValueError("Fixed source drift: " + relative)
        raw[relative] = data
        sources[relative] = dict(bytes=len(data), sha256=actual)
    return raw, sources


def audit(raw):
    checks = []

    def check(value, label):
        if not value:
            raise ValueError(label)
        checks.append(label)

    records, cells = PureData(io.BytesIO(raw[META])).load()
    runtime = json.loads(raw[RUNTIME])
    entry = json.loads(raw[ENTRY])
    proof = json.loads(raw[PRIVATE + "row-body/extraction.private.json"])
    usage_proof = json.loads(raw[PRIVATE + "method-usages.private.json"])
    profile = json.loads(raw["protocol/cn-7.2/profile.json"])
    body = raw[PRIVATE + "row-body/code.bin"]
    rows = asm_rows(raw[PRIVATE + "row-body/code.asm"])
    start, end, unwind = struct.unpack("<III", raw[PRIVATE + "row-body/pdata.bin"])
    check((start, end, unwind) == (0x8D6EBF0, 0x8D6EE39, 0x3F12CA4), "exact-pdata")
    check(len(body) == end - start == 585 and end <= 0x8D6EE40, "complete-body-within-592-cap")
    check(b"".join(bytes.fromhex(i["bytes"]) for i in rows) == body, "asm-complete-raw-body")
    check(len(rows) == 120 and all(int(b["address"], 16) == int(a["address"], 16) + len(bytes.fromhex(a["bytes"]))
                                  for a, b in zip(rows, rows[1:])), "asm-contiguous")
    check(proof["sourceRequestSHA256"] == PINS[PRIVATE + "one-window-request.private.json"] and
          proof["uniqueMetadataEntry"] == [[28174, 218981]], "source-request-and-unique-ordinary-binding")
    check(struct.unpack("<Q", raw[PRIVATE + "row-body/aot-slot.bin"])[0] == 0x148D6EBF0 and
          struct.unpack("<Q", raw[PRIVATE + "row-body/aot-pointer-cell.bin"])[0] == 0x1428A54D0,
          "ordinary-aot-original-qwords")
    caller = raw[PRIVATE + "row-body/caller.bin"]
    check(caller.hex() == "e835e1fcff" and 0x148DA0ABB + struct.unpack_from("<i", caller, 1)[0] == 0x148D6EBF0,
          "original-direct-caller-rel32")
    check(raw[OLD][0x7f:0x8b].hex() == "4889f1488b55d0e835e1fcff", "original-receiver-row-argument-flow")
    check(proof["inputs"] == {k: dict(bytes=v["size"], sha256=v["sha256"]) for k, v in profile["files"].items()}
          == usage_proof["inputs"], "five-fixed-input-identities")
    check(proof["nativeCPU"] == proof["liveReads"] == proof["xdataBytesRead"] == 0 and
          usage_proof["nativeCPU"] == usage_proof["liveReads"] == usage_proof["newCodeWindows"] == 0,
          "one-code-window-no-cpu-live-xdata")

    by_site = {int(i["address"], 16): i for i in rows}
    check(by_site[0x148D6EBF9]["text"] == "mov rsi, rdx" and
          by_site[0x148D6EBFC]["text"] == "mov r14, rcx", "original-row-receiver-retained")
    check(by_site[0x148D6ED36]["bytes"] == "4989b640010000", "original-pointer-store-entity140")
    row_field = field_at(records[28174], 0x140)
    check(row_field["index"] == 138373 and row_field["type"]["typeDefinition"] == 27264 and
          records[46314]["parent"]["typeDefinition"] == 28174 and
          records[40472]["parent"]["typeDefinition"] == 27264, "typed-template-reference-field-and-parents")
    row_reads = [i for i in rows if "[rsi" in i["text"]]
    check([i["text"] for i in row_reads] == ["mov rax, qword ptr [rsi]",
          "mov eax, dword ptr [rsi + 0x18]", "mov eax, dword ptr [rsi + 0x18]"], "only-class-and-encoded-id-row-reads")
    check(not any("[rsi + 0x50]" in i["text"] or "[rsi + 0x58]" in i["text"] for i in rows),
          "no-default-item-or-mplevel-consumption")
    for cell, tid in ((0x1458A1300, 52115), (0x1458A47C8, 40472), (0x14589B538, 54132), (0x14589DE38, 78883)):
        check(cells[cell]["type"]["typeDefinition"] == tid, "exact-type-usage:" + hex(cell))
    check(by_site[0x148D6EC71]["text"] == by_site[0x148D6ECD9]["text"] == "call 0x14aef9db0",
          "encoded-row18-decoder-sites")
    id_field = field_at(records[27264], 0x18)
    check(id_field["index"] == 134163 and id_field["type"]["typeDefinition"] == 58209, "encoded-template-id-field")
    gadget_cache = next(f for f in records[54132]["fields"] if f["rawOffset"] == "0x40237d0")
    other_cache = next(f for f in records[78883]["fields"] if f["rawOffset"] == "0x4025e30")
    check(gadget_cache["type"]["typeDefinition"] == other_cache["type"]["typeDefinition"] == 13732 and
          (gadget_cache["type"]["instanceIndex"], other_cache["type"]["instanceIndex"]) == (19433, 46664),
          "custom-cache-domains-not-standard-dictionary")
    check({u["cell"] for u in usage_proof["usages"]} == {"0x145a0b138", "0x145a16a38"} and
          all(u["ownerType"] == 13732 and u["methodDefinition"] == 101367 and u["method"] == "DMEFOIHDAIK"
              for u in usage_proof["usages"]) and
          all(u["address"] == "0x154db6810" and u["methodDefinition"] == 101367 for u in usage_proof["aot"]),
          "exact-shared-custom-method-not-remove-alias")
    check(next(m for m in records[13732]["methods"] if m["name"] == "Remove")["index"] == 101368,
          "remove-is-a-distinct-method")
    check(by_site[0x148D6EC8C]["text"] == "cmp byte ptr [rip - 0x34f2132], 0" and
          by_site[0x148D6EC93]["text"] == "je 0x148d6ed36" and
          by_site[0x148D6ED31]["text"] == "call 0x154db6810", "conditional-gadget-cache-call-before-store")

    world = next(m for m in runtime["declarations"]["methods"] if m["entry"] == "0x15154bf20")
    manager = next(m for m in entry["declarations"]["native"] if m["entry"] == "0x14828ba00")
    check(complete_window(world) == world["codeSHA256"] and complete_window(manager) == manager["codeSHA256"],
          "existing-factory-manager-complete-window-hashes")
    ws = {int(i["address"], 16): text(i) for i in world["instructions"]}
    ms = {int(i["address"], 16): text(i) for i in manager["instructions"]}
    check(ws[0x15154C01B] == "call 0x148da0a30" and ws[0x15154C07E] == "call qword ptr [rax + 0x460]"
          and ws[0x15154C10F] == "call 0x151539a40" and ws[0x15154C12E] == "call 0x147c4b850"
          and ws[0x15154C157] == "mov dword ptr [rdx + 0x280], r13d", "factory-static-order-after-template-binding")
    override = next(m for m in records[46314]["methods"] if m["virtualSlot"] == 114)
    check(override["index"] == 384166 and override["registeredAddress"] == "0x1483df040" and
          (0x460 - 0xD0) // 8 == 114, "declared-slot114-is-not-observed-vtable")
    check(ms[0x14828BAB6] == "call 0x148d813d0" and ms[0x14828BAF0] == "call 0x1547052a0"
          and ms[0x14828BB13] == "call 0x147586f70" and ms[0x14828BB4B] == "call 0x1547052a0"
          and ms[0x14828BB77] == "call 0x14773fbc0" and ms[0x14828BBB1] == "call 0x15154bf20",
          "existing-entity-and-create-paths-kept-distinct")
    row = next(r for r in runtime["gadgetRows"] if r["key"] == 90000004)
    values = {v["field"]["offset"]: v["value"] for v in row["values"]}
    check(values[0x18] == 90000004 and values[0x20] == 9999 and values[0x50] == "Default_Item"
          and values[0x58] == "MPLevel", "original-world-row-id-secondary-field-and-keys-distinct")
    return dict(
        task="F-03d3s2-SCENE-ROW-LIFECYCLE", evidenceKind="static-only", checks=checks,
        body=dict(entry="0x148d6ebf0", end="0x148d6ee39", codeBytes=585, instructions=rows,
                  codeSHA256=PINS[PRIVATE + "row-body/code.bin"], pdataHex=raw[PRIVATE + "row-body/pdata.bin"].hex()),
        templateReference=dict(owner=28174, inheritedBy=46314, field=row_field,
                               store="0x148d6ed36", value="original row pointer; null also stored on ordinary path"),
        conditionalCache=dict(gadgetOwner=54132, gadgetFlagModuleRva="0x587cb61", field=gadget_cache,
                              otherOwner=78883, otherField=other_cache, usages=usage_proof["usages"],
                              aot=usage_proof["aot"], semantics="unknown; not Remove/retain/recycle by name"),
        staticFactoryOrder=["pool slot4", "IAutoAllocRecycle slot0 then slot2", "MPLevel lookup",
                            "wrapper prelude148d8e3f0 then original-row binding148d6ebf0",
                            "row20 decoded/reencoded; config/two arguments stored", "actual virtual slot114",
                            "151539a40", "147c4b850", "scene+280 store"],
        minimumUnclosed=dict(prelude="148d8e3f0 behavior not read", allocation="natural caller TLS/allocator not executed",
                            conditionalCache="method101367 body and natural flag not observed",
                            postRow="actual46314 vtable slot114 not observed; metadata candidate1483df040",
                            resources="original MPLevel registration group/string[] and config ability content absent",
                            lifecycle="old entity update/components and complete remove/replace/notifications unclosed"),
        newCodeWindows=1, nativeCPU=0, liveReads=0, newJavaImplementationUnlocked=False,
        field50SemanticsApproved=False, completeScene=False, resourceReady=False, d02Passed=False)


def asm_rows(raw):
    rows = []
    for line in raw.decode().splitlines():
        address, code, instruction = line.split(maxsplit=2)
        rows.append(dict(address=hex(int(address, 16)), bytes=code, text=instruction.rstrip()))
    return rows


def field_at(owner, offset):
    field, = [f for f in owner["fields"] if f.get("offset") == offset]
    return field


def text(instruction):
    return instruction.get("text", instruction.get("mnemonic", "") + " " + instruction.get("operands", "")).rstrip()


def complete_window(method):
    position = int(method["entry"], 16)
    code = bytearray()
    for instruction in method["instructions"]:
        if int(instruction["address"], 16) != position:
            raise ValueError("Noncontiguous frozen window: " + method["entry"])
        raw = bytes.fromhex(instruction["bytes"])
        position += len(raw)
        code.extend(raw)
    if position != int(method.get("end", method.get("extentEnd")), 16):
        raise ValueError("Incomplete frozen window: " + method["entry"])
    return hashlib.sha256(code).hexdigest()


if __name__ == "__main__":
    main()
