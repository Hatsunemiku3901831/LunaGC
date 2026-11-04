"""Hash-bound CN 7.2 static metadata extraction, without modifying the client.

Names, fields, generic arguments, parents, static layouts and method pointers.
Literal-return candidates do not assert CmdID or business/wire mappings. See:
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


def method_flags_slot(index, encoded_flags, encoded_slot):
    """Metadata setup at 0x14051E5DA..0x14051E667; FFFF is nonvirtual slot."""
    if type(index) is not int or not 0 <= index <= MASK32 or any(
            type(v) is not int or not 0 <= v <= 0xFFFF for v in (encoded_flags, encoded_slot)):
        raise ValueError('Invalid method index/word')
    key = (((index * 0x1639) ^ 0x5E01C03B) * 0x106B250C17F34CCB + 0x069FFDCBE218FAF4) & MASK64
    return ((encoded_flags ^ 0xDC05) - key) & 0xFFFF, (encoded_slot - key + 0x58B5) & 0xFFFF


def extract(binary, metadata, *, members=False, startup=None, structures=False):
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
    if members or structures:
        recover_members(pe, metadata, h, table, names, records, report)
    if structures:
        if startup is None:
            raise ValueError('Verified startup metadata is required for structures')
        resolver = TypeResolver(pe, startup, records, h)
        for record in records:
            for field in record['fields']:
                field['type'] = resolver.resolve_index(field['typeIndex'])
        recover_parents72(metadata, table, records, resolver, report)
        recover_layouts72(pe, metadata, h, records, report)
        report.update(genericClassCount=resolver.generic_count,
                      resolvedTypeReferenceCount=len(resolver.cache),
                      genericTableOffset=hex(resolver.generic_base))
    return records, report


class TypeResolver:
    """7.2 startup (instance, definition) rows; registered argument pointers.

    Evidence: 0x14053F377..0x14053F476, Type::GetClass at 0x140517680.
    VAR/MVAR remain parameter indices; they are not invented concrete types.
    """
    def __init__(self, pe, startup, records, header, *, registration=METADATA_REGISTRATION):
        self.pe, self.startup, self.records = pe, startup, records
        b = pe.data
        reg = pe.offset(registration, 0x90)
        self.references = number(b, reg, 'Q')
        self.reference_count = (number(b, reg + 0x8C) + 0xCE67355C) & MASK32
        self.reference_base = pe.offset(self.references, self.reference_count * 16)
        self.instances = number(b, reg + 0x70, 'Q')
        self.generic_base = (number(b, header + 0xD0) + 0xE1492096) & MASK32
        size = (number(b, header + 0x44) + 0xE42E197F) & MASK32
        if not size or size % 8:
            raise ValueError('Invalid startup generic table size')
        span(startup, self.generic_base, size)
        self.generic_count = size // 8
        self.cache, self.arguments = {}, {}
        for i in range(self.generic_count):
            instance, definition = struct.unpack_from('<II', startup, self.generic_base + i * 8)
            if definition >= len(records):
                raise ValueError('Generic definition outside type table')
            if instance in self.arguments:
                continue
            entry = pe.offset(self.instances + instance * 16, 16)
            count, pointer = struct.unpack_from('<QQ', b, entry)
            if not 0 < count <= 255:
                raise ValueError('Invalid generic argument count')
            start = pe.offset(pointer, count * 8)
            self.arguments[instance] = [self.pointer_index(number(b, start + j * 8, 'Q')) for j in range(count)]

    def pointer_index(self, pointer):
        if not self.references <= pointer < self.references + self.reference_count * 16 or (pointer - self.references) % 16:
            raise ValueError('Argument/element pointer is not a registered type reference')
        return (pointer - self.references) // 16

    def resolve_index(self, index, visiting=frozenset()):
        if not 0 <= index < self.reference_count or index in visiting or len(visiting) >= 64:
            raise ValueError(f'Invalid or recursive type reference {index}')
        if index in self.cache:
            return self.cache[index]
        entry = self.reference_base + index * 16
        data, bits = struct.unpack_from('<QI', self.pe.data, entry)
        kind = (bits >> 16) & 0xFF
        if kind not in KINDS:
            raise ValueError(f'Unknown reference kind {kind}')
        result = dict(kind=KINDS[kind], attributes=hex(bits & 0xFFFF), data=hex(data))
        path = visiting | {index}
        definition = None
        if kind in (17, 18):
            definition = data
        elif kind == 28:
            matches = [r['index'] for r in self.records if (r['namespace'], r['name']) == ('System', 'Object')]
            if len(matches) != 1:
                raise ValueError('Object requires one System.Object definition')
            definition = matches[0]
        elif kind == 21:
            if data >= self.generic_count:
                raise ValueError('Generic index outside startup table')
            instance, definition = struct.unpack_from('<II', self.startup, self.generic_base + data * 8)
            result.update(genericIndex=data, instanceIndex=instance,
                          arguments=[self.resolve_index(i, path) for i in self.arguments[instance]])
        elif kind in (15, 16, 29):
            result['elementType'] = self.resolve_index(self.pointer_index(data), path)
            if kind == 29:
                result['rank'] = 1
        elif kind == 20:
            entry = self.pe.offset(data, 16)
            rank = self.pe.data[entry + 8]
            if not 1 <= rank <= 32:
                raise ValueError('Invalid array rank')
            result.update(rank=rank, elementType=self.resolve_index(self.pointer_index(number(self.pe.data, entry, 'Q')), path),
                          boundsStatus='not-recovered')
        if definition is not None:
            if definition >= len(self.records):
                raise ValueError('Definition outside type table')
            row = self.records[definition]
            result.update(typeDefinition=definition, name=row['name'], namespace=row['namespace'])
        self.cache[index] = result
        return result


def recover_parents72(metadata, table, records, resolver, report):
    """Parent type index: type[0] + A0A35767, observed at 0x15A4D746E."""
    parents, pending = {}, []
    for row in records:
        index = (number(metadata, table + row['index'] * STRIDE) + 0xA0A35767) & MASK32
        if index == MASK32:
            pending.append(None)
            continue
        parent = resolver.resolve_index(index)
        if parent['kind'] not in ('class', 'genericinst', 'object'):
            raise ValueError('Invalid parent reference kind')
        parents[row['index']] = parent['typeDefinition']
        pending.append(dict(parent, typeIndex=index))
    checked = set()
    for start in parents:
        path, current = set(), start
        while current in parents and current not in checked:
            if current in path:
                raise ValueError(f'Cyclic parent graph at {current}')
            path.add(current)
            current = parents[current]
        checked.update(path)
    for row, parent in zip(records, pending):
        row['parent'] = parent
    report.update(parentCount=len(parents), genericParentCount=sum(p is not None and p['kind'] == 'genericinst' for p in pending))


def recover_layouts72(pe, metadata, header, records, report):
    """Static layout inputs from 0x14051C76F..0x14051C8D2, not wire tags.

    Generic-definition zeroes and high-bit runtime markers are not usable object
    offsets. Literal constants have no storage offset. Keep raw words for review.
    """
    b = pe.data
    lookup = BODY + ((number(b, header + 0x168) + 0xB01C236D) & MASK32)
    layouts = BODY + ((number(b, header + 0x14C) + 0xDE642132) & MASK32)
    offsets = BODY + ((number(b, header + 0x154) + 0x936A8A40) & MASK32)
    counts, pending = Counter(), []
    for row in records:
        if row.get('fieldCount') != len(row.get('fields', [])):
            raise ValueError('Inconsistent fields for layout')
        if not row['fields']:
            pending.append((None, []))
            continue
        index = number(metadata, lookup + row['index'] * 4, 'i')
        if index < -1:
            raise ValueError('Invalid layout index')
        layout = dict(kind='runtime', index=index)
        values = []
        if index != -1:
            entry = layouts + index * 12
            span(metadata, entry, 12)
            a, c, first = struct.unpack_from('<III', metadata, entry)
            span(metadata, offsets + first * 4, len(row['fields']) * 4)
            layout = dict(kind='metadata', index=index, rawSizeAndFlags=[hex(a), hex(c)], offsetIndex=first)
            counts['layoutTypes'] += 1
        for j, field in enumerate(row['fields']):
            raw = None if index == -1 else number(metadata, offsets + (first + j) * 4)
            attrs = int(field['type']['attributes'], 16)
            kind = ('literal' if attrs & 0x40 else 'runtime' if raw is None or raw >= 0x1000000
                    else 'static' if attrs & 0x10 else 'runtime' if raw == 0 else 'instance')
            values.append(dict(offset=raw if kind in ('instance', 'static') else None,
                               rawOffset=hex(raw) if raw is not None else None, offsetKind=kind))
            counts[kind] += 1
        pending.append((layout, values))
    for row, (layout, values) in zip(records, pending):
        row['fieldLayout'] = layout
        for field, value in zip(row['fields'], values):
            field.update(value)
    report.update(layoutCounts=dict(counts), layoutLookup=hex(lookup), layoutRecords=hex(layouts), fieldOffsets=hex(offsets))


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
            flags, slot = method_flags_slot(i, number(metadata, m + 0x10, 'H'), number(metadata, m + 0x12, 'H'))
            method = dict(index=i, name=names.decode(name_index), nameIndex=hex(name_index),
                          registeredAddress=hex(address), parameterCount=count, flags=hex(flags), virtualSlot=slot)
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
    parser.add_argument("--structures", action="store_true", help="Include members, generic/array types, parents and static field layouts")
    args = parser.parse_args()
    root, output = args.client.resolve(), args.output.resolve()
    if output.is_relative_to(root):
        parser.error("Write outside the read-only client directory")
    files = [read_verified(root / name, sha) for name, sha in HASHES.items()]
    records, report = extract(files[0], files[1], members=args.members, startup=files[2], structures=args.structures)
    report.update(inputSHA256=HASHES, scope="Static metadata; not CmdID, business names, wire schema, or runtime ABI",
                  limitations=(["Structures not requested"] if not args.structures else ["Multidimensional array bounds, closed generic object layouts and runtime ABI not verified"]) + [
                               "Registered base method pointers; runtime overrides not resolved",
                               "Literal u16 returns are unclassified candidates, not validated packet IDs"])
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("w", encoding="utf-8") as stream:
        json.dump({"report": report, "types": records}, stream, ensure_ascii=False, separators=(",", ":"))
        stream.write("\n")
    print(json.dumps(report, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
