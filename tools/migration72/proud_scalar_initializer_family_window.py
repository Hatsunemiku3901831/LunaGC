"""Collect the pinned nine-member initializer family, bounded and offline."""
import argparse
import hashlib
import io
import json
import pickle
import struct
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "local/migration72/d02-resume-20261111c/proud-scalar-initializer-source/family-source-window"
REQUEST = ROOT / "local/migration72/d02-resume-20261111c/proud-scalar-initializer-source/family-window-request.private.json"
PRIOR = "local/migration72/d02-resume-20261111c/proud-setter-semantics/review.private.json"
EXPECTED = {131980: 0x149a75020, 131983: 0x149a75080, 131984: 0x149a750e0, 131985: 0x149a752b0, 131986: 0x149a75330, 131987: 0x149a75390, 131989: 0x149a753f0, 131991: 0x149a75450, 131993: 0x149a754b0}
EXCLUDED = (0x149a75140, 0x1405336c0)
CAP = {"local/migration72/python/capstone/__init__.py": (43853, "94571d1314355e261aff0ddfc94985fe8761232ddff6874181fd21efa9fab47f"), "local/migration72/python/capstone/x86.py": (2816, "08cbb0a02ff152bcf1fbd7c4f8850b3ade38c25b9f0a6a9b900a55ee75e87c67"), "local/migration72/python/capstone/x86_const.py": (45756, "83cf4550d9256f5f9b8e218b9b732e46d83ef602b463a01d6eeb778deb28e040"), "local/migration72/python/capstone/lib/capstone.dll": (None, "76958e18380023a68fd1714fa2e01c594cc6db1955a07ad6937b66e66dc5d6c3"), "tools/game_path.ps1": (3506, "9d89da73dbb8479507eaf00fbe12f523b3472ca05e4aaa20602e2eb196cefa3b")}


def require(ok, message):
    if not ok:
        raise ValueError(message)


def sha(data):
    return hashlib.sha256(data).hexdigest()


def check(path, size, digest):
    h = hashlib.sha256(); count = 0
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1048576), b""):
            h.update(chunk); count += len(chunk)
    require((size is None or count == size) and h.hexdigest() == digest, "pin mismatch: " + path.name)
    return {"bytes": count, "sha256": h.hexdigest()}


def save(path, obj):
    with path.open("xb") as f:
        f.write((json.dumps(obj, sort_keys=True, ensure_ascii=False, indent=2) + "\n").encode())


class DataOnly(pickle.Unpickler):
    def find_class(self, module, name):
        raise ValueError("metadata pickle globals forbidden")


def declarations(owner, candidates):
    require(owner["index"] == 17408 and len(owner["methods"]) == 17 and len(owner["fields"]) == 3, "owner shape")
    fields = {f["index"]: f for f in owner["fields"]}
    require(fields[82448]["rawOffset"] == "0x400e970" and fields[82448]["type"]["kind"] == "szarray" and fields[82448]["type"]["elementType"]["kind"] == "uint8" and fields[82449]["rawOffset"] == "0x2007720" and fields[82449]["type"]["kind"] == "uint32" and fields[82450]["rawOffset"] == "0x1000000" and fields[82450]["type"]["typeDefinition"] == 280, "field declaration")
    methods = {m["index"]: m for m in owner["methods"]}
    require({m["index"]: int(m["registeredAddress"], 16) for m in candidates} == EXPECTED and len(candidates) == 9, "candidate entries")
    require({i: int(m["registeredAddress"], 16) for i, m in methods.items() if m["registeredAddress"] != "0x0" and i != 131995} == EXPECTED and methods[131995]["registeredAddress"] == "0x149a75140", "family or TLS drift")
    require(len([m for m in methods.values() if m["registeredAddress"] == "0x0"]) == 7, "generic exclusion")
    for c in candidates:
        m = methods[c["index"]]
        require(all(c[k] == v for k, v in m.items()) and m["flags"] == "0x96" and m["parameterCount"] == 0 and m["virtualSlot"] == 65535, "ordinary declaration drift")


def source_gate():
    check(REQUEST, 13307, "7b9d4bac21de80805534b04867d96b57b5957766d8502b6a2e8480488a7d6cd8")
    req = json.loads(REQUEST.read_bytes())
    pins = dict(req["fixedSources"])
    prior = json.loads((ROOT / PRIOR).read_bytes())
    require(len(prior["pins"]) == 17, "prior pin count")
    for name, pin in prior["pins"].items():
        if name in pins:
            require(pins[name] == pin, "conflicting pins")
        pins[name] = pin
    for name, (size, digest) in CAP.items():
        pins[name] = {"bytes": size, "sha256": digest}
    verified = {name: check(ROOT / name, pin["bytes"], pin["sha256"]) for name, pin in sorted(pins.items())}
    with (ROOT / "local/migration72/d02-resume-20261108/r3-equipment/metadata.private.pkl").open("rb") as f:
        records, _ = DataOnly(f).load()
    owner = records[17408]
    declarations(owner, req["candidates"])
    exact = [(r["index"], f["index"]) for r in records for f in r["fields"] if f["rawOffset"] in ("0x400e970", "0x2007720")]
    require(sorted(exact) == [(17408, 82448), (17408, 82449)], "unique owner")
    business = json.loads((ROOT / "local/migration72/analysis/business-methods.json").read_bytes())
    require(all(business[hex(addr)][:3] == [17408, owner["name"], index] for index, addr in EXPECTED.items()), "business declaration index")
    context = json.loads((ROOT / "local/migration72/d02-active/main/scene-runtime-context-third.json").read_bytes())
    require(int(context["declarations"]["primitiveBase"], 16) + 0x7720 == 0x145872d30, "primitive binding")
    sig = json.loads((ROOT / "local/migration72/evidence/client72-signatures.json").read_bytes())
    generic = [m for m in sig["methods"] if m["declaringType"] == 17408]
    require(sig["report"]["returnTypesStatus"] == "not-recovered" and len(generic) == 7 and all(m["registeredAddress"] == "0x0" for m in generic), "signature exclusion")
    return req, verified


class FilePE:
    def __init__(self, f):
        self.f = f; h = self.read(0, 4096); nt = struct.unpack_from("<I", h, 0x3c)[0]
        require(h[:2] == b"MZ" and h[nt:nt+4] == b"PE\0\0" and struct.unpack_from("<H", h, nt+4)[0] == 0x8664 and struct.unpack_from("<H", h, nt+24)[0] == 0x20b, "PE header")
        self.base = struct.unpack_from("<Q", h, nt+48)[0]
        require(self.base == 0x140000000, "image base")
        count = struct.unpack_from("<H", h, nt+6)[0]; opt = struct.unpack_from("<H", h, nt+20)[0]
        table = self.read(nt+24+opt, count*40); self.sections = []
        for i in range(count):
            off = i*40; rva, size, raw = struct.unpack_from("<III", table, off+12); flags = struct.unpack_from("<I", table, off+36)[0]
            self.sections.append({"begin": self.base+rva, "end": self.base+rva+size, "raw": raw, "flags": flags})
        self.pdata_rva, self.pdata_size = struct.unpack_from("<II", h, nt+24+0x70+3*8)
    def read(self, offset, size):
        self.f.seek(offset); raw = self.f.read(size); require(len(raw) == size, "short file read"); return raw
    def section(self, va, size, executable=False):
        sections = [s for s in self.sections if s["begin"] <= va and va+size <= s["end"] and (not executable or s["flags"] & 0x20000000)]
        require(len(sections) == 1, "missing/ambiguous file-backed executable section")
        return sections[0]
    def at(self, va, size, executable=False):
        section = self.section(va, size, executable)
        return self.read(section["raw"] + va - section["begin"], size)


def aot_check(cell, slots):
    pointer = int.from_bytes(cell, "little")
    require(len(cell) == 8 and len(slots) == 9, "AOT evidence count")
    require(all(len(slots[i]) == 8 and int.from_bytes(slots[i], "little") == address for i, address in EXPECTED.items()), "ordinary AOT mismatch")
    return pointer


def find_pdata(table, base, directory_rva):
    require(len(table) % 12 == 0, "unaligned pdata")
    result = {i: {"exact": [], "containing": []} for i in EXPECTED}
    for n, (begin, end, unwind) in enumerate(struct.iter_unpack("<III", table)):
        for index, target in EXPECTED.items():
            relative = target - base
            if begin == relative or begin <= relative < end:
                row = {"begin": hex(base+begin), "endExclusive": hex(base+end), "size": end-begin, "unwindRVA": hex(unwind), "recordRVA": hex(directory_rva+n*12), "recordHex": table[n*12:n*12+12].hex()}
                if begin == relative: result[index]["exact"].append(row)
                if begin <= relative < end: result[index]["containing"].append(row)
    return result


def admit(diagnostics, section):
    rows = []
    for i, entry in EXPECTED.items():
        d = diagnostics[i]
        require(len(d["exact"]) == 1 and d["exact"] == d["containing"], "pdata missing/ambiguous " + str(i))
        row = d["exact"][0]; end = int(row["endExclusive"], 16)
        require(int(row["begin"], 16) == entry and end > entry and not any(entry <= e < end for e in EXCLUDED), "invalid or excluded extent")
        section(entry, end-entry, True)
        rows.append(row)
    ordered = sorted(rows, key=lambda r: int(r["begin"], 16))
    require(all(int(a["endExclusive"], 16) <= int(b["begin"], 16) for a, b in zip(ordered, ordered[1:])), "overlapping extent")
    return rows


def budget(rows):
    sizes = [r["size"] for r in rows]
    return len(rows) <= 9 and all(0 < n <= 512 for n in sizes) and sum(sizes) <= 2048


def decode(code, entry):
    sys.path.insert(0, str(ROOT / "local/migration72/python"))
    import capstone
    from capstone.x86_const import X86_OP_IMM, X86_OP_MEM, X86_REG_RIP
    require(Path(capstone._cs._name).resolve() == (ROOT / "local/migration72/python/capstone/lib/capstone.dll").resolve(), "capstone DLL")
    cs = capstone.Cs(capstone.CS_ARCH_X86, capstone.CS_MODE_64); cs.detail = True
    rows = []; cursor = entry
    for ins in cs.disasm(code, entry):
        raw = bytes(ins.bytes); require(ins.address == cursor and code[cursor-entry:cursor-entry+len(raw)] == raw, "decode gap")
        call = ins.group(capstone.CS_GRP_CALL); branch = ins.group(capstone.CS_GRP_JUMP)
        target = hex(ins.operands[0].imm) if (call or branch) and ins.operands and ins.operands[0].type == X86_OP_IMM else None
        memory = []
        for op in ins.operands:
            if op.type == X86_OP_MEM:
                access = "address-only" if ins.mnemonic == "lea" else ("read-write" if op.access == 3 else "read" if op.access == 1 else "write" if op.access == 2 else "unknown")
                rip = ins.address+ins.size+op.mem.disp if op.mem.base == X86_REG_RIP else None
                memory.append({"base": ins.reg_name(op.mem.base) or None, "index": ins.reg_name(op.mem.index) or None, "displacement": hex(op.mem.disp), "ripTarget": hex(rip) if rip is not None else None, "access": access, "rawAccess": op.access, "rootBindingProven": False})
        rows.append({"address": hex(ins.address), "size": ins.size, "hex": raw.hex(), "mnemonic": ins.mnemonic, "operands": ins.op_str, "call": call, "branch": branch, "directTarget": target, "memory": memory})
        cursor += ins.size
    require(cursor == entry+len(code), "incomplete decode")
    return rows


def index_rows(rows):
    hits = {"seed": [], "e970": [], "root": [], "edges": [], "refusedEdges": []}
    for row in rows:
        for mem in row["memory"]:
            item = {"address": row["address"], "mnemonic": row["mnemonic"], "operand": mem}
            if mem["ripTarget"] == "0x145872d30": hits["seed"].append(item)
            if mem["displacement"] == "0xe970": hits["e970"].append(item)
            if mem["ripTarget"] == "0x14586b600": hits["root"].append(item)
            if mem["ripTarget"] in tuple(hex(x) for x in EXCLUDED): hits["refusedEdges"].append(item)
        if row["call"] or row["branch"]:
            edge = {"address": row["address"], "call": row["call"], "branch": row["branch"], "target": row["directTarget"]}
            hits["edges"].append(edge)
            if row["directTarget"] in tuple(hex(x) for x in EXCLUDED): hits["refusedEdges"].append(edge)
    return hits


def collect(dest):
    result = {"task": "E-01d1/R3-PROUD-SCALAR-INITIALIZER-FAMILY-SOURCE", "stage": "source-gate", "bodiesRead": 0, "nativeCPU": 0, "live": 0, "callerBytes": 0, "calleeBodies": 0, "xdataBytes": 0, "globalContentBytes": 0, "actualEncoded": None, "actualGetterU32": None, "actual41105": False, "writerProven": False, "resourceReady": False, "d02Complete": False, "toolSHA256": sha(Path(__file__).read_bytes())}
    try:
        req, result["pins"] = source_gate()
        game = Path(subprocess.check_output(["powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", str(ROOT / "tools/game_path.ps1")], text=True).strip())
        result["stage"] = "five-stock"; result["stock"] = {p: check(game/p, v["bytes"], v["sha256"]) for p,v in req["fixedClientInputs"].items()}
        with (game / "YuanShen.exe").open("rb") as f:
            pe = FilePE(f); result["stage"] = "ordinary-aot"
            cell = pe.at(0x1422f9950, 8); pointer = int.from_bytes(cell, "little")
            slots = {i: pe.at(pointer+i*8, 8) for i in EXPECTED}
            aot_check(cell, slots)
            (dest/"aot-cell.bin").write_bytes(cell)
            for i, slot in slots.items(): (dest/f"slot-{i}.bin").write_bytes(slot)
            result["aot"] = {"cellVA": "0x1422f9950", "cellHex": cell.hex(), "pointer": hex(pointer), "slots": {str(i): {"va": hex(pointer+i*8), "hex": b.hex()} for i,b in slots.items()}, "bytesRead": 80}
            result["stage"] = "pdata-before-body"
            require((pe.pdata_rva, pe.pdata_size) == (0x1a4e6a40, 14344080), "exception directory drift")
            table = pe.at(pe.base+pe.pdata_rva, pe.pdata_size)
            diagnostics = find_pdata(table, pe.base, pe.pdata_rva)
            result["exceptionDirectory"] = {"rva": hex(pe.pdata_rva), "bytesRead": len(table), "sha256": sha(table), "diagnostics": diagnostics}
            save(dest/"boundary.private.json", result)
            rows = admit(diagnostics, pe.section)
            for i, row in zip(EXPECTED, rows): (dest/f"pdata-{i}.bin").write_bytes(bytes.fromhex(row["recordHex"]))
            result["selected"] = rows
            result["budget"] = {"perBodyMax": 512, "sumMax": 2048, "actualBytes": sum(r["size"] for r in rows), "perBodyInstructionMax": 128, "sumInstructionMax": 512, "admitted": budget(rows)}
            save(dest/"pre-body.private.json", result)
            if not budget(rows):
                result.update(stage="budget-stop", passed=False, reason="complete extents exceed byte budget")
                save(dest/"result.private.json", result)
                print(json.dumps({"stage": result["stage"], "bodiesRead": 0, "budget": result["budget"]}))
                return 2
            total = 0; bodies = []
            for (i, entry), row in zip(EXPECTED.items(), rows):
                result["stage"] = "body"; code = pe.at(entry, row["size"], True); (dest/f"code-{i}.bin").write_bytes(code); result["bodiesRead"] += 1
                decoded = decode(code, entry); total += len(decoded)
                require(len(decoded) <= 128 and total <= 512, "instruction budget exceeded")
                save(dest/f"asm-{i}.private.json", decoded)
                hits = index_rows(decoded)
                bodies.append({"index": i, "entry": hex(entry), "bytes": len(code), "sha256": sha(code), "instructions": len(decoded), "indexHits": hits, "dependentSemanticsFrozen": bool(hits["refusedEdges"])})
            result.update(stage="complete", passed=True, bodies=bodies, instructionCount=total)
            save(dest/"result.private.json", result)
            print(json.dumps({"stage": "complete", "bodiesRead": len(bodies), "bytes": result["budget"]["actualBytes"], "instructions": total}))
            return 0
    except Exception as exc:
        result.update(passed=False, errorType=type(exc).__name__, error=str(exc))
        save(dest/"failure.private.json", result)
        raise


def audit(first, second, name):
    a, b = OUT/first, OUT/second
    names = sorted(p.name for p in a.iterdir())
    require(names == sorted(p.name for p in b.iterdir()), "artifact names differ")
    data = {n: (a/n).read_bytes() for n in names}
    require(all(data[n] == (b/n).read_bytes() for n in names), "fresh artifacts differ")
    result = json.loads(data["result.private.json"]); pre = json.loads(data["pre-body.private.json"])
    require(pre["bodiesRead"] == 0 and result["nativeCPU"] == result["live"] == result["globalContentBytes"] == 0, "scope mismatch")
    cell = data["aot-cell.bin"]; slots = {i: data[f"slot-{i}.bin"] for i in EXPECTED}; pointer = aot_check(cell, slots)
    require(result["aot"]["pointer"] == hex(pointer), "AOT pointer mismatch")
    for i, row in zip(EXPECTED, result["selected"]):
        raw = data[f"pdata-{i}.bin"]; begin, end, unwind = struct.unpack("<III", raw)
        require(raw.hex() == row["recordHex"] and begin + 0x140000000 == EXPECTED[i] and end-begin == row["size"] and hex(unwind) == row["unwindRVA"], "pdata artifact mismatch")
    if result["stage"] == "budget-stop":
        require(not budget(result["selected"]) and result["bodiesRead"] == 0 and not any(n.startswith(("code-", "asm-")) for n in names), "budget stop mismatch")
    else:
        require(result["stage"] == "complete" and budget(result["selected"]) and result["bodiesRead"] == 9, "completion mismatch")
        for body in result["bodies"]:
            code = data[f"code-{body['index']}.bin"]
            require(len(code) == body["bytes"] and sha(code) == body["sha256"] and decode(code, int(body["entry"],16)) == json.loads(data[f"asm-{body['index']}.private.json"]), "body artifact mismatch")
    save(OUT/name, {"passed": True, "freshFilesEqual": True, "stage": result["stage"], "currentPEReads": 0, "scope": "pure artifact audit; same decoder", "artifacts": {n: {"bytes": len(v), "sha256": sha(v)} for n,v in data.items()}})


def main():
    p = argparse.ArgumentParser(); p.add_argument("--output", required=True); p.add_argument("--audit", nargs=2); args = p.parse_args()
    require(Path(args.output).name == args.output and not (OUT/args.output).exists(), "fresh basename required")
    OUT.mkdir(parents=True, exist_ok=True)
    if args.audit:
        require(args.output.startswith("audit-") and args.output.endswith(".private.json") and all(n in ("fresh1", "fresh2", "main-third") for n in args.audit), "audit basename")
        audit(*args.audit, args.output); return 0
    require(args.output in ("fresh1", "fresh2", "main-third"), "output basename")
    dest = OUT/args.output; dest.mkdir(); return collect(dest)


if __name__ == "__main__":
    sys.exit(main())
