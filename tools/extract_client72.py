"""Hash-bound CN 7.2 static metadata extraction, without modifying the client.

Names, fields, registered method pointers and literal-return candidates only.
No CmdID or business/wire mapping is asserted. Evidence and remaining work:
docs/CLIENT_72_EXTRACTION.md. Uses only Python's standard library.
"""
import argparse
from collections import Counter
import functools
import hashlib
import json
from pathlib import Path
import struct

MASK32, MASK64 = (1 << 32) - 1, (1 << 64) - 1
HEADER_VA, BODY, STRIDE = 0x142806A80, 0x210, 70
METADATA_REGISTRATION, CODE_REGISTRATION = 0x1428A53C8, 0x1422F98E0
HASHES = {
    "YuanShen.exe": "8b014f64b5dc12fbc476e0c362b053abfdd7a1378cd282e4ad81057621dbd6b5",
    "YuanShen_Data/Managed/Metadata/global-metadata.dat": "45dc7a44dc9fc187146c52f6c33de57e2d1c1dcfec57f8736431553460f6ef2a",
    "YuanShen_Data/Managed/Metadata/startup-metadata.dat": "b314810840ca6f495822c9c44483dde2394b9f09ed3c6b13c2782092f04d5105",
}
KINDS = {1: "void", 2: "bool", 3: "char", 4: "int8", 5: "uint8", 6: "int16",
         7: "uint16", 8: "int32", 9: "uint32", 10: "int64", 11: "uint64", 12: "float",
         13: "double", 14: "string", 15: "ptr", 16: "byref", 17: "valuetype", 18: "class",
         19: "var", 20: "array", 21: "genericinst", 22: "typedbyref", 24: "intptr",
         25: "uintptr", 27: "fnptr", 28: "object", 29: "szarray", 30: "mvar"}


def span(data, offset, size):
    if offset < 0 or size < 0 or offset + size > len(data):
        raise ValueError(f"Out of bounds: offset={offset:#x}, size={size}")


def number(data, offset, fmt="I"):
    span(data, offset, struct.calcsize("<" + fmt))
    return struct.unpack_from("<" + fmt, data, offset)[0]


def read_verified(path, expected):
    data = path.read_bytes()
    actual = hashlib.sha256(data).hexdigest()
    if actual != expected:
        raise ValueError(f"Unsupported {path.name}: SHA-256 {actual}")
    return data


class PE:
    def __init__(self, data):
        self.data = data
        pe = number(data, 0x3C)
        span(data, pe, 24)
        if data[:2] != b"MZ" or data[pe:pe + 4] != b"PE\0\0" or number(data, pe + 4, "H") != 0x8664:
            raise ValueError("Expected AMD64 PE")
        optional = number(data, pe + 20, "H")
        if optional < 0x70 or number(data, pe + 24, "H") != 0x20B:
            raise ValueError("Expected PE32+ optional header")
        span(data, pe + 24, optional)
        self.base = number(data, pe + 48, "Q")
        self.sections = []
        for i in range(number(data, pe + 6, "H")):
            entry = pe + 24 + optional + i * 40
            span(data, entry, 40)
            rva, size, offset = struct.unpack_from("<III", data, entry + 12)
            span(data, offset, size)
            self.sections.append((self.base + rva, size, offset, number(data, entry + 36)))

    def offset(self, address, size=1, *, executable=False):
        if size < 0:
            raise ValueError("Negative span")
        for start, raw_size, raw, flags in self.sections:
            if start <= address and address + size <= start + raw_size and (not executable or flags & 0x20000000):
                return raw + address - start
        raise ValueError(f"Address {address:#x} is not file-backed {'code' if executable else 'data'}")


class Names:
    def __init__(self, data, base):
        span(data, base, 0)
        self.data, self.base = data, base

    @functools.lru_cache(maxsize=None)
    def decode(self, index):
        if not 0 <= index <= MASK32:
            raise ValueError("Name index outside uint32")
        if index == MASK32:
            return ""
        length, offset = index >> 24, index & 0xFFFFFF
        count = (length + 7) // 8
        start = self.base + offset
        span(self.data, start, count * 8)
        key = (((offset * 0x3EEED96861341169 & MASK64) ^ 0x30F39D3F643E5C2D)
               * 0xDD6ACDB15078B176 + 0x34847BCE8197A491) & MASK64
        plain = b"".join((number(self.data, start + i * 8, "Q") ^
                          ((key + i * 0x11E1A912269F936D) & MASK64)).to_bytes(8, "little")
                         for i in range(count))
        name = plain[:length].decode("utf-8")
        if any(ord(char) < 32 for char in name):
            raise ValueError(f"Control character in name {index:#x}")
        return name


def literal_u16(code):
    if len(code) >= 5 and code[:2] == b"\x66\xb8" and code[4] == 0xC3:
        return number(code, 2, "H")
    return None


def method_extent(size, count):
    # The target table has 741021 * 26 bytes plus 2 bytes of 4-byte alignment.
    # Its allocation routine divides the padded size; type spans give the count.
    if size != (count * 26 + 3) // 4 * 4:
        raise ValueError("Method count does not match aligned table length")
    return count


def extract(binary, metadata, *, members=False):
    pe = PE(binary)
    h = pe.offset(HEADER_VA, BODY)
    if binary[h:h + 4] != b"MHY\0" or metadata[:4] != b"MHY\0":
        raise ValueError("Missing MHY header")
    table = BODY + ((number(binary, h + 0x64) + 0xA7932555) & MASK32)
    size = number(binary, h + 0xBC) ^ 0x1E6FC854
    strings = BODY + (number(binary, h + 0x70) ^ 0x18202CCC)
    if not size or size % STRIDE:
        raise ValueError("Invalid type table size")
    span(metadata, table, size)
    names = Names(metadata, strings)
    records = []
    for i in range(size // STRIDE):
        entry = table + i * STRIDE
        name = (number(metadata, entry + 0x18) + 0xA285A06D) & MASK32
        namespace = number(metadata, entry + 0x14) ^ 0x149E96F1
        records.append(dict(index=i, name=names.decode(name), namespace=names.decode(namespace),
                            nameIndex=hex(name), namespaceIndex=hex(namespace)))
    anchors = {(r["namespace"], r["name"]) for r in records}
    if not {( "", "<Module>"), ("System", "RuntimeType"), ("System", "Object"),
            ("MoleMole", "MonoCrucibleEndPage")}.issubset(anchors):
        raise ValueError("Independent runtime/UI anchors missing")
    report = dict(typeCount=len(records), typeOffset=hex(table), typeBytes=size, stringBase=hex(strings))
    if members:
        recover_members(pe, metadata, h, table, names, records, report)
    return records, report


def recover_members(pe, metadata, h, table, names, records, report):
    binary = pe.data
    field_base = BODY + (number(binary, h + 0x194) ^ 0x536B48B9)
    method_base = BODY + (number(binary, h + 0xB0) ^ 0x51324A1F)
    method_bytes = (number(binary, h + 0x1C) + 0x85490F40) & MASK32
    method_count = sum((number(metadata, table + r["index"] * STRIDE + 0x3A, "H") + 0xBF1A) & 0xFFFF
                       for r in records)
    method_extent(method_bytes, method_count)
    span(metadata, method_base, method_bytes)
    mr = pe.offset(METADATA_REGISTRATION, 0x90)
    refs_count = (number(binary, mr + 0x8C) + 0xCE67355C) & MASK32
    refs = pe.offset(number(binary, mr, "Q"), refs_count * 16)
    cr = pe.offset(CODE_REGISTRATION, 0x78)
    pointers = pe.offset(number(binary, cr + 0x70, "Q"), method_bytes // 26 * 8)
    fields_seen = methods_seen = 0
    references, candidates = {}, Counter()
    for record in records:
        entry = table + record["index"] * STRIDE
        fs = number(metadata, entry + 8) ^ 0x72239B10
        fc = (number(metadata, entry + 0x38, "H") + 0x3664) & 0xFFFF
        ms = number(metadata, entry + 0xC) ^ 0x5ABB91BD
        mc = (number(metadata, entry + 0x3A, "H") + 0xBF1A) & 0xFFFF
        if (fc and fs != fields_seen) or (mc and ms != methods_seen):
            raise ValueError(f"Noncontiguous member span for type {record['index']}")
        if fc:
            span(metadata, field_base + fs * 8, fc * 8)
        if mc and ms + mc > method_bytes // 26:
            raise ValueError("Method span exceeds table")
        record.update(fieldStart=fs, fieldCount=fc, fields=[], methodStart=ms, methodCount=mc, methods=[])
        for i in range(fs, fs + fc):
            key = (((((i * 0xAE93) ^ 0x112D0196) * 0x380B5207 & MASK64) >> 12)
                   * 0x4F038C54 + 0x2C41E5A3) & MASK32
            f = field_base + i * 8
            name_index = ((number(metadata, f) ^ 0x2C691D30) - key) & MASK32
            ref = ((number(metadata, f + 4) ^ 0x7A3E6276) - key) & MASK32
            if not 0 <= ref < refs_count:
                raise ValueError(f"Invalid field reference {i}: {ref}")
            if ref not in references:
                value = number(binary, refs + ref * 16, "Q")
                bits = number(binary, refs + ref * 16 + 8)
                kind = (bits >> 16) & 0xFF
                if kind not in KINDS:
                    raise ValueError(f"Unknown type kind {kind}")
                reference = dict(kind=KINDS[kind], attributes=hex(bits & 0xFFFF), data=hex(value))
                if kind in (17, 18):
                    if value >= len(records):
                        raise ValueError("Invalid type definition reference")
                    reference.update(typeDefinition=value, name=records[value]["name"], namespace=records[value]["namespace"])
                references[ref] = reference
            record["fields"].append(dict(index=i, name=names.decode(name_index), nameIndex=hex(name_index),
                                          typeIndex=ref, type=references[ref]))
        for i in range(ms, ms + mc):
            key = (((i * 0x1639) ^ 0x5E01C03B) * 0x106B250C17F34CCB + 0x069FFDCBE218FAF4) & MASK64
            m = method_base + i * 26
            name_index = (number(metadata, m + 8) - key + 0xDE01C441) & MASK32
            address = number(binary, pointers + i * 8, "Q")
            count = ((metadata[m + 0x18] ^ 0xF9) - key) & 0xFF
            method = dict(index=i, name=names.decode(name_index), nameIndex=hex(name_index),
                          registeredAddress=hex(address), parameterCount=count)
            if address:
                offset = pe.offset(address, executable=True)
                value = literal_u16(binary[offset:offset + 5]) if count == 0 else None
                if value is not None:
                    method["literalU16Candidate"] = value
                    candidates[method["name"]] += 1
            record["methods"].append(method)
        fields_seen += fc
        methods_seen += mc
    method_extent(method_bytes, methods_seen)
    report.update(fieldCount=fields_seen, methodCount=methods_seen, typeReferenceCount=refs_count,
                  fieldOffset=hex(field_base), methodOffset=hex(method_base),
                  literalU16CandidatesByMethod=dict(sorted(candidates.items())))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--client", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--members", action="store_true")
    args = parser.parse_args()
    root, output = args.client.resolve(), args.output.resolve()
    if output.is_relative_to(root):
        parser.error("Write outside the read-only client directory")
    files = [read_verified(root / name, sha) for name, sha in HASHES.items()]
    records, report = extract(files[0], files[1], members=args.members)
    report.update(inputSHA256=HASHES, scope="Static metadata; not CmdID, business names, wire schema, or runtime ABI",
                  limitations=["Generic/array arguments and inheritance not recovered",
                               "Registered base method pointers; runtime overrides not resolved",
                               "Literal u16 returns are unclassified candidates, not validated packet IDs"])
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("w", encoding="utf-8") as stream:
        json.dump({"report": report, "types": records}, stream, ensure_ascii=False, separators=(",", ":"))
        stream.write("\n")
    print(json.dumps(report, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
