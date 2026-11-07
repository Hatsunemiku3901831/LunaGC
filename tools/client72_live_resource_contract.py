"""Fixed-input resource static roots and bounded passive-reader native controls.

This program never opens a process and never loads or modifies resource files.
"""
import argparse
import hashlib
import json
import struct
from pathlib import Path

try:
    from . import client72_avatar_deep as base
    from .extract_client72_literals import Literals72
    from .client72_session_runtime import fragment
    from .client72_avatar_contract import RETURN
    from .client72_emulator import STACK, GLOBAL
except ImportError:
    import client72_avatar_deep as base
    from extract_client72_literals import Literals72
    from client72_session_runtime import fragment
    from client72_avatar_contract import RETURN
    from client72_emulator import STACK, GLOBAL

TABLES = (
    ('Avatar', 65789, 0x30870, 58272, 0x14F3382E0, (10000005, 10000007)),
    ('AvatarSkillDepot', 63488, 0x212B8, 22284, 0x14D6D70F0, (501, 701)),
    ('AvatarSkill', 11866, 0x16C70, 88602, 0x14EB02D30, ()),
    ('AvatarOrnamentGroup', 28604, 0x13B70, 61286, 0x15437ACB0, ()),
    ('AvatarOrnamentGroupByAvatar', 28604, 0x13B78, 61286, 0x15437ADA0, (10000005, 10000007)),
    ('AvatarOrnament', 39027, 0x1CD98, 44799, 0x1473673B0, (101, 102)),
    ('Weapon', 71245, 0x34900, 87097, 0x154177600, (11101,)),
)
FIND = 0x1578B66F0
DECODE = 0x14AEF9DB0
SAMPLE_SHA256 = 'c5e6b05778f6326891065f120b966f4a8135b8601f097c30f6477724066a765d'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ('client', 'output'):
        parser.add_argument('--' + name, type=Path, required=True)
    parser.add_argument('--python-libs', type=Path, default=Path('local/migration72/python'))
    parser.add_argument('--sample', type=Path, default=Path('local/migration72/d02-active/plain/initialization-resources-elevated5.json'))
    args = parser.parse_args()
    if args.output.resolve().is_relative_to(args.client.resolve()):
        parser.error('Output must be outside client')
    binary, metadata, startup = [base.read_verified(args.client / n, h) for n, h in base.HASHES.items()]
    records, _ = base.extract(binary, metadata, startup=startup, structures=True)
    sample = audit_sample(args.sample)
    report = dict(task='D02-R1-live-resource-contract', inputSHA256=base.HASHES,
                  static=declarations(binary, metadata, records, args.python_libs),
                  native=native_controls(binary, records, args.python_libs, sample),
                  snapshot={k: v for k, v in sample.items() if k != 'inverseBytes'},
                  actualCacheRead=True, actualResourceRevisionVerified=False,
                  completeAvatarHandler=False, targetProtocolReady=False,
                  sourceSHA256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
                  dependencySHA256={n: hashlib.sha256(Path(n).read_bytes()).hexdigest() for n in (
                      'tools/client72_avatar_deep.py', 'tools/extract_client72.py', 'tools/extract_client72_literals.py',
                      'tools/client72_emulator.py', 'tools/client72_session_runtime.py')},
                  limitations=['Native dictionaries and byte table are explicitly authored offline storage controls.',
                               'Only null-comparer FindEntry is interpreted; custom comparer needs its actual interface methods.',
                               'Ornament101/102 and weapon11101 are candidate lookup keys, not verified selected revision.',
                               'Null cache, absent row and query budget exhaustion are distinct; no loader is called.'])
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2) + '\n', 'utf8')
    print(json.dumps(dict(tables=len(report['static']['tables']), nativeCases=len(report['native']))))


def declarations(binary, metadata, records, libraries):
    base.load_emulator(libraries)
    from capstone import Cs, CS_ARCH_X86, CS_MODE_64
    pe = base.PE(binary)
    decoder = Cs(CS_ARCH_X86, CS_MODE_64)
    decoder.detail = True
    literals = Literals72(binary, metadata)
    usages = {int(x['cell'], 16): x for x in literals.group(literals.global_group)}
    optional = base.number(binary, 0x3c) + 24
    rva, size = struct.unpack_from('<II', binary, optional + 112 + 3 * 8)
    start = pe.offset(pe.base + rva)
    extents = {pe.base + a: pe.base + z for a, z, _ in struct.iter_unpack('<III', binary[start:start + size])}
    owners = {x['index']: x for x in records}
    tables, addresses = [], {FIND, DECODE, 0x151843490}
    for name, owner_id, offset, row_type, query, keys in TABLES:
        owner = owners[owner_id]
        field = next(f for f in owner['fields'] if int(f['rawOffset'], 16) == 0x4000000 + offset)
        getter = next(m for m in owner['methods'] if m['name'] == 'LJNPMLJAIAO')
        address = int(getter['registeredAddress'], 16)
        ins = list(decoder.disasm(binary[pe.offset(address):pe.offset(address) + 8], address))
        if len(ins) != 2 or ins[0].mnemonic != 'mov' or ins[1].mnemonic != 'ret':
            raise ValueError('Resource path leaf changed')
        cell = address + ins[0].size + ins[0].operands[1].mem.disp
        literal = usages[cell]
        value = literals.decode(literal['index'])
        expected_name = 'AvatarOrnamentGroup' if name.endswith('ByAvatar') else name
        if value != 'Data/_ExcelBinOutput/' + expected_name + 'ExcelConfigData':
            raise ValueError('Resource path identity changed: ' + value)
        tables.append(dict(name=name, owner=owner_id, rowType=row_type, field=field,
                           staticOffset=hex(offset), query=hex(query), pathGetter=getter,
                           pathLiteral=dict(literal, value=value), queryKeys=list(keys)))
        addresses.add(query)
    functions = {}
    for address in sorted(addresses):
        end = extents[address]
        code = binary[pe.offset(address):pe.offset(address) + end - address]
        functions[hex(address)] = dict(end=hex(end), sha256=hashlib.sha256(code).hexdigest(),
            instructions=[dict(address=hex(i.address), instruction=i.mnemonic + ' ' + i.op_str) for i in decoder.disasm(code, address)])
    return dict(rootCellRVA='0x586b600', tables=tables, functions=functions,
                boundedRead=dict(dictionaryHeaderBytes=0x48, queryKeysPerTable=2, collisionNodesPerKey=8,
                    nonNullComparer='record-only-stop-before-query', arraysHeaderBytes=0x20,
                    entriesStride=24, entriesHashOffset=0, entriesNextOffset=4, entriesKeyOffset=8, entriesValueOffset=16),
                decodeRead=dict(tablePointerStaticOffset='0xe970', arrayLengthOffset='0x18',
                    minimumArrayLength=2048, seedDirectValueRVA='0x5872d30', seedBytes=4,
                    decodeWindowObjectOffset='0x420', decodeWindowBytes=1024))


def audit_sample(path):
    raw = path.read_bytes()
    if hashlib.sha256(raw).hexdigest() != SAMPLE_SHA256:
        raise ValueError('Natural resource sample changed')
    sample = json.loads(raw)
    reads = {row['label']: row for row in sample['reads']}
    if len(reads) != 93:
        raise ValueError('Unexpected sample read count')
    for row in reads.values():
        value = bytes.fromhex(row['bytesHex'])
        if row['error'] is not None or len(value) != row['length'] or hashlib.sha256(value).hexdigest() != row['sha256']:
            raise ValueError('Invalid raw read: ' + row['label'])
    def number(label):
        return int.from_bytes(bytes.fromhex(reads[label]['bytesHex']), 'little')
    module = sample['mainModule']['base']
    root = number('resourceStorage.cell')
    assert reads['resourceStorage.cell']['address'] == module + 0x586b600
    labels = ('avatar', 'depot', 'skill', 'ornamentGroupPrimary', 'ornamentGroupByAvatar', 'ornament', 'weapon')
    caches = {}
    for label, table in zip(labels, TABLES):
        row = reads['resourceStorage.' + label + '.cell']
        assert row['address'] == root + table[2] and row['length'] == 8
        caches[label] = 'not-created-at-this-observation' if number(row['label']) == 0 else 'non-null-not-reviewed'
    assert reads['resourceDecode.permutation']['address'] == root + 0xe970
    array = number('resourceDecode.permutation')
    assert reads['resourceDecode.length']['address'] == array + 0x18
    length = number('resourceDecode.length')
    assert length >= 2048
    quarters = []
    for index in range(4):
        row = reads['resourceDecode.quarter' + str(index)]
        assert row['address'] == array + 0x420 + index * 256 and row['length'] == 256
        quarters.append(bytes.fromhex(row['bytesHex']))
    assert reads['resourceDecode.seed']['address'] == module + 0x5872d30
    inverse = b''.join(quarters)
    return dict(sampleSHA256=SAMPLE_SHA256, sourceSamplerSHA256=sample['sourceSHA256'],
                sourceObservationSHA256=sample['sourceObservationSHA256'], allReadHashesValid=True,
                readCount=93, rootNonNull=bool(root), caches=caches, decodeArrayLength=length,
                inverseWindowSHA256=hashlib.sha256(inverse).hexdigest(),
                allFourQuartersArePermutations=all(len(set(q)) == 256 for q in quarters),
                inverseBytes=inverse, seed=number('resourceDecode.seed'),
                atomicSnapshot=False, actualResourceRowCaptured=False)


def native_controls(binary, records, libraries, sample):
    e = base.Offline72(binary, libraries, records, trace_instructions=True)
    e.uc.mem_map(RETURN & ~4095, 4096)
    e.uc.mem_write(RETURN, b'\x90')
    e.prepare()
    for entry in (FIND, DECODE):
        e.allow_unwind(entry)
    output = []
    try:
        # Separate masks collide deliberately, while keys retain all 32 bits.
        for name, key, nodes, first, custom in (
            ('nonzero-hit', 11101, [(11101, -1, 11101)], 1, False),
            ('high-bit-key-hit', 0x80000005, [(5, -1, 0x80000005)], 1, False),
            ('same-hash-wrong-key-miss', 5, [(5, -1, 0x80000005)], 1, False),
            ('collision-second-hit', 11101, [(11104, 1, 11104), (11101, -1, 11101)], 1, False),
            ('empty-bucket-miss', 11101, [], 0, False),
            ('cycle-rejected', 11101, [(11104, 0, 11104)], 1, False),
            ('custom-comparer-unresolved', 11101, [(11101, -1, 11101)], 1, True),
        ):
            dictionary, buckets, entries = e.alloc(0x48), e.alloc(0x2c), e.alloc(0x20 + max(1, len(nodes)) * 24)
            e.write(dictionary + 0x18, entries)
            e.write(dictionary + 0x20, buckets)
            e.write(dictionary + 0x28, e.alloc(8) if custom else 0)
            e.write(buckets + 0x18, 3, 'I')
            e.write(buckets + 0x20 + ((key & 0x7fffffff) % 3) * 4, first, 'I')
            e.write(entries + 0x18, len(nodes), 'I')
            for index, (hashed, next_index, node_key) in enumerate(nodes):
                row = entries + 0x20 + index * 24
                e.write(row, hashed, 'I')
                e.write(row + 4, next_index & 0xffffffff, 'I')
                e.write(row + 8, node_key, 'I')
                e.write(row + 16, 0xfedcba9876543210)
            e.write(STACK + 0x100008, RETURN)
            trace = fragment(e, FIND, {RETURN, 0x1578B671A, 0x159CA53C0}, dict(rsp=STACK + 0x100008, rcx=dictionary, edx=key))
            index = e.reg('eax')
            output.append(dict(name=name, inputKey=key, returnedIndex=index,
                               value=e.read(entries + 0x20 + index * 24 + 16) if trace['stop'] == hex(RETURN) and index < len(nodes) else None,
                               trace=trace))
        table = e.alloc(2080)
        e.write(table + 0x18, 2048, 'I')
        # Independent quadrant formulas; not the emulator's permutation helper.
        inverse = bytes((i * 7 + quadrant * 37 + 11) & 255 for quadrant in range(4) for i in range(256))
        e.uc.mem_write(table + 0x420, inverse)
        e.write(GLOBAL + 0xe970, table)
        seed = 0xa1b2c3d4
        e.write(0x145872D30, seed, 'I')
        field = e.alloc(4)
        for value in (0, 0x12345678, 0x80000000, 0xffffffff):
            e.write(field, value, 'I')
            e.write(STACK + 0x100008, RETURN)
            trace = fragment(e, DECODE, {RETURN}, dict(rsp=STACK + 0x100008, rcx=field))
            x = value ^ seed
            expected = sum((((((x >> shift) & 255) * 7 + quadrant * 37 + 11) & 255) << shift)
                           for quadrant, shift in enumerate((24, 16, 8, 0)))
            output.append(dict(name='decode-' + hex(value), raw=value, seed=seed,
                               expected=expected, actual=e.reg('eax'), trace=trace))
        e.uc.mem_write(table + 0x420, sample['inverseBytes'])
        e.write(0x145872D30, sample['seed'], 'I')
        for value in (0, 0x12345678, 0x80000000, 0xffffffff):
            e.write(field, value, 'I')
            e.write(STACK + 0x100008, RETURN)
            trace = fragment(e, DECODE, {RETURN}, dict(rsp=STACK + 0x100008, rcx=field))
            x = value ^ sample['seed']
            expected = int.from_bytes(bytes(sample['inverseBytes'][q * 256 + ((x >> (24 - q * 8)) & 255)] for q in range(4)), 'big')
            output.append(dict(name='natural-decoder-controlled-field-' + hex(value), raw=value,
                seed=sample['seed'], expected=expected, actual=e.reg('eax'), actualResourceField=False, trace=trace))
        for name, array_pointer, length in (('counterfactual-truncated-decoder', table, 2047), ('counterfactual-null-decoder', 0, 2048)):
            e.write(GLOBAL + 0xe970, array_pointer)
            e.write(table + 0x18, length, 'I')
            e.write(field, sample['seed'] ^ 255, 'I')
            e.write(STACK + 0x100008, RETURN)
            trace = fragment(e, DECODE, {0x1404C2650, 0x1404C2BE0}, dict(rsp=STACK + 0x100008, rcx=field))
            output.append(dict(name=name, actualNaturalFailure=False, trace=trace))
        return output
    finally:
        e.close()


if __name__ == '__main__':
    main()
