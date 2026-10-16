#!/usr/bin/env python3
"""Resolve concrete TypeInfo cells from the exact supported 7.1 client usage records.

Follows 0x140524250 and the cache registration at 0x1402f3730. Only named class/value
type references are supported; method, field, string and generic-type cells are not
silently treated as classes. The search envelope ends at the metadata file boundary,
not a claimed decoded usage-pair count. Conflicting matches fail explicitly.
"""

import argparse
import json
import struct
from pathlib import Path

import extract_client_type_names as profile


MASK32 = (1 << 32) - 1
USAGE_CACHE_REGISTRATION_VA = 0x142871C08


def _integer(value, maximum=MASK32):
    if not isinstance(value, int) or isinstance(value, bool) or not 0 <= value <= maximum:
        raise ValueError("Expected a bounded nonnegative integer")
    return value


def decode_pair(index, encoded_destination, encoded_source):
    _integer(index)
    _integer(encoded_destination)
    _integer(encoded_source)
    key = ((index * 0x3C5F6DC3F5D0 & profile.MASK64) >> 20) ^ 0x615EB706
    key = (key + 0x63D46A21) & MASK32
    destination = ((encoded_destination + 0x8AB89515) & MASK32) ^ key
    source = encoded_source ^ key ^ 0x5CD30101
    return index, source >> 29, destination, source & 0x1FFFFFFF


def collect_type_uses(records, destinations):
    wanted = {_integer(value) for value in destinations}
    if not wanted:
        raise ValueError("Missing class destinations")
    selected = {}
    for pair_index, kind, destination, source in records:
        if kind != 1 or destination not in wanted:
            continue
        if destination not in selected:
            selected[destination] = (source, [])
        previous, indices = selected[destination]
        if previous != source:
            raise ValueError(f"Ambiguous class destination {destination}")
        indices.append(pair_index)
    if selected.keys() != wanted:
        raise ValueError(f"Missing class usages for {sorted(wanted - selected.keys())}")
    return selected


def resolve_reference(binary, reference_base, reference_count, types, index):
    _integer(index)
    _integer(reference_count)
    _integer(reference_base, len(binary))
    if index >= reference_count or reference_base + reference_count * 16 > len(binary):
        raise ValueError("Type reference exceeds the registered table or backing bytes")
    definition, bits = struct.unpack_from("<QI", binary, reference_base + index * 16)
    kind = (bits >> 16) & 255
    if kind not in (17, 18):
        raise ValueError(f"Unsupported concrete class reference kind {kind}")
    if definition >= len(types) or types[definition].get("index") != definition:
        raise ValueError("Type definition exceeds the decoded type table")
    row = types[definition]
    return dict(kind=profile.TYPE_KINDS[kind], typeDefinition=definition,
                name=row["name"], namespace=row["namespace"])


def literal_packet_id(code):
    if not isinstance(code, (bytes, bytearray)):
        raise ValueError("Expected native getter bytes")
    if len(code) == 5 and code[:2] == b"\x66\xb8" and code[4] == 0xC3:
        return struct.unpack_from("<H", code, 2)[0]
    return None


def method_name_index(index, encoded):
    _integer(index)
    _integer(encoded)
    key = ((((index * 0x4E94) ^ 0x62D8B7B8) * 0x7572748A + 0x1F4D9EEA)
           ^ 0x6E8931D6) * 0x39036375 & profile.MASK64
    return ((encoded ^ 0x0613ACE3) - key) & MASK32


def _packet_id(binary, metadata, types, report, definition):
    """Read only this definition's methods, using the existing literal-getter profile."""
    type_base = int(report["typeOffset"], 16)
    entry = type_base + definition * profile.TYPE_STRIDE
    start = profile.u32(metadata, entry + 8) ^ 0x0478AF59
    count = struct.unpack_from("<H", metadata, entry + 0x3C)[0] ^ 0x8811
    total = sum(struct.unpack_from("<H", metadata, type_base + i * profile.TYPE_STRIDE + 0x3C)[0]
                ^ 0x8811 for i in range(len(types)))
    if count and start + count > total:
        raise ValueError("Selected method span exceeds method registration")
    header = profile.pe_file_offset(binary, profile.HEADER_VA, profile.BODY_OFFSET)
    method_base = profile.BODY_OFFSET + (profile.u32(binary, header + 0x1E0) ^ 0x2EE480FB)
    registration = profile.pe_file_offset(binary, profile.CODE_REGISTRATION_VA, 0x30)
    pointer_va = struct.unpack_from("<Q", binary, registration + 0x28)[0]
    pointer_base = profile.pe_file_offset(binary, pointer_va, total * 8)
    names = profile.MetadataNames(metadata, int(report["stringDataBase"], 16))
    found = []
    for index in range(start, start + count):
        offset = method_base + index * 26
        if offset < 0 or offset + 26 > len(metadata):
            raise ValueError("Selected method exceeds metadata bytes")
        key = ((((index * 0x4E94) ^ 0x62D8B7B8) * 0x7572748A + 0x1F4D9EEA)
               ^ 0x6E8931D6) * 0x39036375 & profile.MASK64
        name_index = method_name_index(index, profile.u32(metadata, offset + 4))
        parameters = (metadata[offset + 0x18] - key + 0x8C) & 255
        if names.decode(name_index) != "AEGNNPENLNM" or parameters:
            continue
        address = struct.unpack_from("<Q", binary, pointer_base + index * 8)[0]
        if address:
            found.append((index, address))
    if len(found) > 1:
        raise ValueError("Ambiguous native packet ID getter")
    if not found:
        return {}
    index, address = found[0]
    offset = profile.pe_file_offset(binary, address, 5)
    packet_id = literal_packet_id(binary[offset:offset + 5])
    return ({} if packet_id is None else
            dict(packetId=packet_id, packetIdGetter=hex(address), packetIdMethodIndex=index))


def recover_usage_types(binary, metadata, cells):
    """Inputs must have passed the exact profile's read_verified checks."""
    addresses = sorted({_integer(cell, profile.MASK64) for cell in cells})
    cache = profile.pe_file_offset(binary, USAGE_CACHE_REGISTRATION_VA, 0x58)
    class_base = struct.unpack_from("<Q", binary, cache + 0x50)[0]
    next_cache = struct.unpack_from("<Q", binary, cache + 8)[0]
    if not 0 < class_base < next_cache or (next_cache - class_base) % 8:
        raise ValueError("Invalid concrete TypeInfo cache range")
    if not addresses or any(cell < class_base or cell >= next_cache or (cell - class_base) % 8
                            for cell in addresses):
        raise ValueError("Cell must be aligned inside the concrete TypeInfo cache")
    header = profile.pe_file_offset(binary, profile.HEADER_VA, profile.BODY_OFFSET)
    pair_base = profile.BODY_OFFSET + (profile.u32(binary, header + 0x14C) ^ 0x2F249C2F)
    if not 0 <= pair_base <= len(metadata) - 8:
        raise ValueError("Usage pair start exceeds metadata bytes")
    count = (len(metadata) - pair_base) // 8
    destinations = {(cell - class_base) // 8 for cell in addresses}
    records = (decode_pair(index, *struct.unpack_from("<II", metadata, pair_base + index * 8))
               for index in range(count))
    selected = collect_type_uses(records, destinations)
    types, report = profile.recover_types(binary, metadata)
    registration = profile.pe_file_offset(binary, profile.METADATA_REGISTRATION_VA, 0x58)
    reference_count = profile.u32(binary, registration + 0x30) ^ 0x28A0FA2D
    reference_va = struct.unpack_from("<Q", binary, registration + 0x50)[0]
    reference_base = profile.pe_file_offset(binary, reference_va, reference_count * 16)
    results = []
    for cell in addresses:
        destination = (cell - class_base) // 8
        reference, pairs = selected[destination]
        resolved = resolve_reference(binary, reference_base, reference_count, types, reference)
        results.append(dict(cell=hex(cell), destinationIndex=destination, sourceReference=reference,
                            usagePairIndices=pairs, **resolved,
                            **_packet_id(binary, metadata, types, report, resolved["typeDefinition"])))
    return dict(profile=dict(binarySha256=profile.BINARY_SHA256, metadataSha256=profile.METADATA_SHA256),
                cacheRegistration=hex(USAGE_CACHE_REGISTRATION_VA), classCacheBase=hex(class_base),
                classCacheEnd=hex(next_cache), usagePairStart=hex(pair_base),
                scanEnvelope="metadata-file-end", scannedRecords=count,
                completeUsagePairCountRecovered=False, rows=results)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--binary", type=Path, required=True)
    parser.add_argument("--metadata", type=Path, required=True)
    parser.add_argument("--cell", type=lambda value: int(value, 0), action="append", required=True)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args(argv)
    try:
        if args.output:
            for path in (args.binary, args.metadata, args.metadata.with_name("startup-metadata.dat")):
                if args.output.resolve() == path.resolve() or (args.output.exists() and path.exists() and args.output.samefile(path)):
                    raise ValueError("Output must differ from input, including aliases")
            if args.output.resolve().is_relative_to(args.binary.parent.resolve()):
                raise ValueError("Write analysis results outside the client directory")
        binary = profile.read_verified(args.binary, profile.BINARY_SHA256)
        metadata = profile.read_verified(args.metadata, profile.METADATA_SHA256)
        result = recover_usage_types(binary, metadata, args.cell)
        text = json.dumps(result, ensure_ascii=False, indent=2) + "\n"
        if args.output:
            args.output.write_text(text, encoding="utf-8")
            print(json.dumps(dict(output=str(args.output), rows=len(result["rows"]))))
        else:
            print(text, end="")
    except (OSError, ValueError, struct.error) as error:
        parser.error(str(error))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
