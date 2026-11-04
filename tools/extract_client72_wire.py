"""Recover reviewed, unprotected CN 7.2 readers/writers into research-only Proto.

This is a bounded verifier for manually traced sites, not a general disassembler.
It joins tag stores, field loads, known writer calls and recovered object fields.
Unknown encodings, incomplete fields or changed helpers fail; business names and
runtime IFix behavior are deliberately outside this tool's claims.
"""
import argparse
import json
from pathlib import Path
import re
import struct

try:
    from .extract_client72 import HASHES, PE, read_verified, extract
except ImportError:
    from extract_client72 import HASHES, PE, read_verified, extract

# Reviewed normal paths; alternate IFix branches remain unverified.
# type index, client type, (tag-store VA, direct writer call/jump VA)
SITES = [
    (27182, 'LOPNMMJPFAJ', [
        (0x14CD80DC6, 0x14CD80DE2), (0x14CD80E42, 0x14CD80E5E),
        (0x14CD80EBD, 0x14CD80ED9), (0x14CD80F38, 0x14CD80F54),
        (0x14CD80FB3, 0x14CD80FCF), (0x14CD8102E, 0x14CD81050)]),
    (64405, 'LMINJCCKNDD', [
        (0x15182C047, 0x15182C063), (0x15182C0C2, 0x15182C0DE),
        (0x15182C14D, 0x15182C159), (0x15182C1C3, 0x15182C1E2),
        (0x15182C251, 0x15182C25D), (0x15182C2D1, 0x15182C2DE),
        (0x15182C33D, 0x15182C359), (0x15182C3B8, 0x15182C3DA)]),
]
# Helper bodies are separately reviewed in CLIENT_72_NETWORK.md.
# address: Proto scalar, wire type, field-load prefix excluding the displacement
WRITERS = {
    0x1517662A0: ('uint32', 0, bytes.fromhex('448b47')),
    0x151766960: ('uint64', 0, bytes.fromhex('4c8b47')),
    0x151767530: ('bytes', 2, bytes.fromhex('488b57')),
    0x151765EF0: ('double', 1, bytes.fromhex('f30f7e57')),
    0x151748C40: ('float', 5, bytes.fromhex('f30f104f')),
}
READ_SITES = [(68111, 'MJGOAHFNCGC', [
    (0x15251F360, 0x15251F371, 0x15251F376),
    (0x15251F33F, 0x15251F38C, 0x15251F391),
    (0x15251F344, 0x15251F355, 0x15251F35A)])]


def recover_read_field(pe, fields, compare_va, call_va, store_va):
    b = pe.data
    compare = pe.offset(compare_va, 3, executable=True)
    if b[compare:compare + 2] != b'\x83\xf8':
        raise ValueError('Expected reviewed read-tag comparison')
    key = b[compare + 2]
    if key >= 128 or key & 7 or key < 8:
        raise ValueError('Expected single-byte varint key')
    call = pe.offset(call_va, 5, executable=True)
    if b[call] != 0xE8 or call_va + 5 + struct.unpack_from('<i', b, call + 1)[0] != 0x147520110:
        raise ValueError('Unreviewed varint32 reader')
    store = pe.offset(store_va, 3, executable=True)
    if store_va != call_va + 5 or b[store:store + 2] != b'\x89\x47':
        raise ValueError('Expected reader result store')
    offset = b[store + 2]
    matches = [f for f in fields if f['offsetKind'] == 'instance' and f['offset'] == offset]
    if len(matches) != 1 or matches[0]['type']['kind'] not in ('int32', 'uint32'):
        raise ValueError('Read field layout/type mismatch')
    field = matches[0]
    return dict(clientField=field['name'], objectOffset=offset, tag=key >> 3, type=field['type']['kind'],
                wireType=0, cardinality='singular', presence='implicit', packed=False,
                wireStatus='static-confirmed', semanticStatus='unresolved',
                evidence=dict(tagCompare=hex(compare_va), readerCall=hex(call_va), fieldStore=hex(store_va), reader='0x147520110'))


def recover_field(pe, fields, tag_va, call_va):
    binary = pe.data
    tag_offset = pe.offset(tag_va, 5, executable=True)
    instruction = binary[tag_offset:tag_offset + 5]
    if instruction[:4] != bytes.fromhex('c6440120'):
        raise ValueError('Expected reviewed single-byte tag store')
    tag, wire = instruction[4] >> 3, instruction[4] & 7
    if not tag or instruction[4] >= 128:
        raise ValueError('Not a complete single-byte protobuf key')
    call_offset = pe.offset(call_va, 5, executable=True)
    if binary[call_offset] not in (0xE8, 0xE9) or not 5 < call_va - tag_va <= 48:
        raise ValueError('Expected nearby direct writer call/jump')
    target = call_va + 5 + struct.unpack_from('<i', binary, call_offset + 1)[0]
    if target not in WRITERS:
        raise ValueError('Unreviewed writer helper')
    scalar, expected_wire, prefix = WRITERS[target]
    if wire != expected_wire:
        raise ValueError('Tag wire type disagrees with writer')
    load = pe.offset(tag_va + 5, len(prefix) + 1, executable=True)
    if binary[load:load + len(prefix)] != prefix:
        raise ValueError('Unexpected object-field load')
    offset = binary[load + len(prefix)]
    if offset >= 128:
        raise ValueError('Negative object displacement')
    candidates = [f for f in fields if f['offsetKind'] == 'instance' and f['offset'] == offset]
    if len(candidates) != 1:
        raise ValueError('Field offset absent or ambiguous in metadata')
    field = candidates[0]
    reference = field['type']
    if scalar == 'bytes':
        if reference.get('name') != 'PPGGBLPHADP' or reference['kind'] != 'class':
            raise ValueError('Byte writer does not match reviewed byte container')
    elif reference['kind'] != scalar:
        raise ValueError('Writer scalar disagrees with metadata')
    return dict(clientField=field['name'], objectOffset=offset, tag=tag, type=scalar,
                wireType=wire, cardinality='singular', presence='implicit', packed=False,
                wireStatus='static-confirmed', semanticStatus='unresolved',
                evidence=dict(tagStore=hex(tag_va), fieldLoad=hex(tag_va + 5),
                              writerCall=hex(call_va), writer=hex(target)))


def recover_wire(pe, records):
    by_index = {r['index']: r for r in records}
    messages = []
    for index, name, sites in SITES + READ_SITES:
        row = by_index[index]
        if row['name'] != name:
            raise ValueError('Selected target type changed')
        fn = recover_read_field if index in {r[0] for r in READ_SITES} else recover_field
        fields = [fn(pe, row['fields'], *site) for site in sites]
        if len({f['tag'] for f in fields}) != len(fields):
            raise ValueError('Duplicate wire tag')
        actual = {f['name'] for f in row['fields'] if not int(f['type']['attributes'], 16) & 0x10}
        if {f['clientField'] for f in fields} != actual or len(fields) != len(actual):
            raise ValueError('Selected serializer field coverage incomplete')
        messages.append(dict(typeIndex=index, clientType=name,
                             status='static-wire-confirmed', runtimeStatus='not-tested',
                             fields=sorted(fields, key=lambda f: f['tag'])))
    return dict(schemaVersion=1, inputSHA256=HASHES,
                scope='Research-only raw names; not server-ready business schemas',
                evidence='docs/CLIENT_72_NETWORK.md#包头和心跳-wire', messages=messages)


def proto_text(report):
    lines = ['// Generated by extract_client72_wire.py. Research only; no runtime acceptance.',
             '// Raw client names intentionally preserve unresolved business semantics.',
             'syntax = "proto3";', 'package cn72.research;', '']
    for message in report['messages']:
        name = message['clientType']
        if not re.fullmatch(r'[A-Za-z_][A-Za-z_0-9]*', name):
            raise ValueError('Invalid raw message identifier')
        lines.append(f'message {name} {{')
        for field in message['fields']:
            if not re.fullmatch(r'[A-Za-z_][A-Za-z_0-9]*', field['clientField']):
                raise ValueError('Invalid raw field identifier')
            lines.append(f"  {field['type']} {field['clientField']} = {field['tag']}; // object +0x{field['objectOffset']:x}")
        lines.extend(['}', ''])
    return '\n'.join(lines)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--proto', type=Path, required=True)
    args = parser.parse_args()
    root = args.client.resolve()
    outputs = [args.output.resolve(), args.proto.resolve()]
    if outputs[0] == outputs[1] or any(p.is_relative_to(root) for p in outputs):
        parser.error('Use distinct outputs outside the read-only client')
    files = [read_verified(root / name, sha) for name, sha in HASHES.items()]
    records, _ = extract(files[0], files[1], startup=files[2], structures=True)
    report = recover_wire(PE(files[0]), records)
    for path in outputs:
        path.parent.mkdir(parents=True, exist_ok=True)
    outputs[0].write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    outputs[1].write_text(proto_text(report), encoding='utf-8')
    print(json.dumps(dict(messages=len(report['messages']), fields=sum(len(m['fields']) for m in report['messages']),
                         status='static-wire-confirmed', runtimeStatus='not-tested')))


if __name__ == '__main__':
    main()
