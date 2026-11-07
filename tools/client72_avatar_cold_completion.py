"""Offline provider4 natural enum evidence and exact cold-helper frontier."""
import argparse
import hashlib
import json
import struct
from pathlib import Path

try:
    from .client72_avatar_cold_contract import ColdRuntime, base, RETURN
except ImportError:
    from client72_avatar_cold_contract import ColdRuntime, base, RETURN

SAMPLE_SHA256 = '5a233bd0048f38f449e890268510e00d74870c770f58a416a82ceffe9faf0b9b'
ENTRIES = (0x14052DD70, 0x14052DF40, 0x159B6E720, 0x159B87C70, 0x1404E0F30)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client', type=Path, required=True)
    parser.add_argument('--sample', type=Path, default=Path('local/migration72/d02-active/plain/initialization-provider-elevated4.json'))
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--python-libs', type=Path, default=Path('local/migration72/python'))
    args = parser.parse_args()
    if args.output.resolve().is_relative_to(args.client.resolve()):
        parser.error('Output must be outside client')
    b, m, s = [base.read_verified(args.client / n, h) for n, h in base.HASHES.items()]
    records, _ = base.extract(b, m, startup=s, structures=True)
    sample, data = audit_sample(args.sample)
    report = dict(task='D02-R1-cold-completion', inputSHA256=base.HASHES,
                  snapshot=sample, declarations=declarations(b, m, s, records, args.python_libs),
                  native=replay(b, args.python_libs, data),
                  sourceSHA256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
                  dependencySHA256={n: hashlib.sha256(Path(n).read_bytes()).hexdigest() for n in (
                      'tools/client72_avatar_cold_contract.py', 'tools/extract_client72.py',
                      'tools/client72_emulator.py', 'tools/client72_session_runtime.py')},
                  completeColdComparer=False, naturalAvatarConstructorObserved=False,
                  completeAvatarHandler=False, targetProtocolReady=False,
                  model='Only exported class/type fields are copied into relocated storage. No function success models run.',
                  unresolved=['Natural EqualityComparer getter/ctor RGCTX slots remain zero; defaultComparer itself was not read.',
                              'RuntimeType class identity and initialized bit do not supply its hierarchy +48/+c4 or vtable.',
                              'CreateDefaultEqualityComparer first requires expected RuntimeType class cell1458869b8 plus hierarchy cast validation.',
                              'System.Byte/String RuntimeType cache, HasIEquatable/GetInterfaces, parent/underlying RuntimeType cache and generic construction remain unexecuted.',
                              'TypeCode internal implementation is directly invoked; the natural vtable slot and its cctor guard are not observed or executed.'])
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2) + '\n', 'utf8')
    print(json.dumps(dict(nativeCases=len(report['native']), completeColdComparer=False)))


def audit_sample(path):
    raw = path.read_bytes()
    if hashlib.sha256(raw).hexdigest() != SAMPLE_SHA256:
        raise ValueError('provider4 sample changed')
    sample = json.loads(raw)
    rows = {x['label']: x for x in sample['reads']}
    if len(rows) != 57:
        raise ValueError('provider4 read count changed')
    data = {}
    for label, row in rows.items():
        value = bytes.fromhex(row['bytesHex'])
        if row['error'] is not None or len(value) != row['length'] or hashlib.sha256(value).hexdigest() != row['sha256']:
            raise ValueError('Invalid exported read: ' + label)
        data[label] = value
    def number(label, offset=0, width=8):
        return int.from_bytes(data[label][offset:offset + width], 'little')
    module = sample['mainModule']['base']
    assert rows['enumClassCache.cell']['address'] == module + 0x5bc8f18
    assert rows['enumClassCache.entry20060']['address'] == number('enumClassCache.cell') + 20060 * 8
    enum = number('enumClassCache.entry20060')
    for label, offset in (('type', 0x10), ('initialized', 0xc8), ('flagsCA', 0xca), ('underlyingOffset', 0xac)):
        assert rows['enumClassCache.enum.' + label]['address'] == enum + offset
    assert data['enumClassCache.enum.type'] == data['registeredEnumDescriptor'] == data['runtimeTypeCache.firstKey']
    assert number('enumClassCache.enum.type') == 20060 and data['enumClassCache.enum.type'][10] == 17
    assert rows['underlyingRegistry.cell']['address'] == module + 0x5bc8ad0
    assert rows['underlyingRegistry.base']['address'] == number('underlyingRegistry.cell')
    underlying = number('underlyingRegistry.base') + number('enumClassCache.enum.underlyingOffset', width=4)
    assert rows['enumClassCache.underlying.type']['address'] == underlying + 0x10
    assert rows['enumClassCache.underlying.initialized']['address'] == underlying + 0xc8
    assert number('enumClassCache.underlying.type') == 251 and data['enumClassCache.underlying.type'][10] == 8
    rt_class = number('runtimeTypeCache.firstRuntimeType')
    assert rows['runtimeTypeCache.objectClass.type']['address'] == rt_class + 0x10
    assert rows['runtimeTypeCache.objectClass.initialized']['address'] == rt_class + 0xc8
    assert number('runtimeTypeCache.objectClass.type') == 284 and data['runtimeTypeCache.objectClass.type'][10] == 18
    assert number('runtimeTypeCache.firstRuntimeType', 0x10) == enum + 0x10
    assert number('enumContext.getterSlot') == number('enumContext.ctorSlot') == 0
    assert not sample['defaultComparerRead']
    assert number('options.cell') == module + 0x22f9988 and data['options.record'] == bytes.fromhex('0101000007000000')
    assert number('canonicalEnum.class.type') == 351
    return dict(sampleSHA256=SAMPLE_SHA256, sourceSamplerSHA256=sample['sourceSHA256'],
                sourceObservationSHA256=sample['sourceObservationSHA256'], readCount=57,
                allReadHashesValid=True, enumClassType=20060, underlyingClassType=251, underlyingKind=8,
                runtimeTypeObjectClass=284, runtimeTypeObjectClassKind=18,
                actualEnumFlagsCA=data['enumClassCache.enum.flagsCA'][0],
                actualUnderlyingOffset=number('enumClassCache.enum.underlyingOffset', width=4),
                initializedBits={name: data[name][0] for name in data if name.endswith('.initialized')},
                defaultComparerRead=False, getterSlot=0, constructorSlot=0,
                atomicSnapshot=False), data


def declarations(binary, metadata, startup, records, libraries):
    base.load_emulator(libraries)
    from capstone import Cs, CS_ARCH_X86, CS_MODE_64
    cells = base.type_cells(binary, metadata, startup, records)
    expected = cells[0x1458869B8]
    assert expected['type']['typeDefinition'] == 284
    method = next(m for t in records if t['index'] == 284 for m in t['methods'] if m['index'] == 2790)
    assert method['virtualSlot'] == 21 and method['registeredAddress'] == '0x159b87c70'
    pe = base.PE(binary)
    resolver = base.TypeResolver(pe, startup, records, pe.offset(base.HEADER_VA))
    selected = resolver.resolve_index(0x691330 // 16)
    assert selected['typeDefinition'] == 1239
    optional = base.number(binary, 0x3c) + 24
    rva, size = struct.unpack_from('<II', binary, optional + 112 + 3 * 8)
    start = pe.offset(pe.base + rva)
    extents = {pe.base + a: pe.base + z for a, z, _ in struct.iter_unpack('<III', binary[start:start + size])}
    functions = {}
    decoder = Cs(CS_ARCH_X86, CS_MODE_64)
    for address in ENTRIES:
        end = extents[address]
        code = binary[pe.offset(address):pe.offset(address) + end - address]
        functions[hex(address)] = dict(end=hex(end), sha256=hashlib.sha256(code).hexdigest(),
            instructions=[dict(address=hex(i.address), instruction=i.mnemonic + ' ' + i.op_str) for i in decoder.disasm(code, address)])
    return dict(expectedRuntimeTypeCell=expected, typeCodeVirtualMethod=method,
                typeCodeVtableOffset=hex(0xd0 + 21 * 8), selectedComparerDefinition=selected,
                registeredTypeBase=hex(resolver.references), functions=functions)


def replay(binary, libraries, data):
    e = base.Offline72(binary, libraries, [], trace_instructions=True)
    e.uc.mem_map(RETURN & ~4095, 4096)
    e.uc.mem_write(RETURN, b'\x90')
    e.prepare()
    for entry in ENTRIES:
        e.allow_unwind(entry)
    e.allowed.append((0x1405269F0, 0x1405269F5))
    rt = ColdRuntime(e)
    output = []
    try:
        enum, underlying, canonical, object_class = [e.alloc(512) for _ in range(4)]
        for target, prefix in ((enum, 'enumClassCache.enum'), (underlying, 'enumClassCache.underlying'),
                               (canonical, 'canonicalEnum.class'), (object_class, 'runtimeTypeCache.objectClass')):
            e.uc.mem_write(target + 0x10, data[prefix + '.type'])
            e.uc.mem_write(target + 0xc8, data[prefix + '.initialized'])
        e.uc.mem_write(enum + 0xca, data['enumClassCache.enum.flagsCA'])
        offset = int.from_bytes(data['enumClassCache.enum.underlyingOffset'], 'little')
        e.write(enum + 0xac, offset, 'I')
        registry = e.alloc(8)
        e.write(registry, underlying - offset)
        e.write(0x145BC8AD0, registry)
        cache = e.alloc((20060 + 1) * 8)
        e.write(cache + 20060 * 8, enum)
        e.write(0x145BC8F18, cache)
        e.write(0x145BC9638, canonical)
        options = e.alloc(8)
        e.uc.mem_write(options, data['options.record'])
        e.write(0x145BC8EF0, options)
        for name, entry, argument, expected in (
            ('natural-enum-underlying', 0x14052DF40, enum + 0x10, underlying + 0x10),
            ('natural-enum-canonical', 0x14052DD70, enum + 0x10, canonical + 0x10),
            ('natural-int32-unchanged', 0x14052DD70, underlying + 0x10, underlying + 0x10),
        ):
            trace = rt.invoke(entry, rcx=argument)
            result = e.reg('rax')
            output.append(dict(name=name, expectedPointerReturned=result == expected,
                returnedType=e.read(result), returnedKind=e.read(result + 10, 'B'), trace=trace))
        # Explicit counterfactual only: by-ref cannot use the enum underlying shortcut.
        byref = e.alloc(16)
        e.uc.mem_write(byref, data['enumClassCache.enum.type'])
        e.write(byref + 8, e.read(byref + 8, 'I') | 0x40000000, 'I')
        trace = rt.invoke(0x14052DF40, rcx=byref)
        output.append(dict(name='counterfactual-byref-retains-descriptor', originalPointerRetained=e.reg('rax') == byref, trace=trace))
        reflection = e.alloc(32)
        e.uc.mem_write(reflection, data['runtimeTypeCache.firstRuntimeType'])
        e.write(reflection, object_class)
        e.write(reflection + 0x10, enum + 0x10)
        for name, wrapped in (('natural-enum-typecode-internal', enum + 0x10),
                              ('natural-int32-typecode-internal', underlying + 0x10),
                              ('counterfactual-byref-typecode-internal', byref)):
            e.write(reflection + 0x10, wrapped)
            trace = rt.invoke(0x1404E0F30, rcx=reflection)
            code = e.reg('eax')
            output.append(dict(name=name, typeCode=code, naturalVirtualDispatch=False, trace=trace))
            if name == 'natural-enum-typecode-internal':
                natural_typecode = code
        e.write(0x145BC8EE0, 0x1428A53C8)
        trace = rt.invoke(0x159B6EA12, {0x159B6E9A2, 0x159B6EA6A}, eax=natural_typecode)
        output.append(dict(name='natural-typecode-fed-to-selector', typeCode=natural_typecode,
            selectedDescriptor=hex(e.reg('rcx')), selectedType=e.read(e.reg('rcx')),
            continuousHelperExecution=False, trace=trace))
        e.write(reflection + 0x10, enum + 0x10)
        # The next instruction would read a missing live expected-class cell.
        trace = rt.invoke(0x159B6E720, {0x159B6E72E}, rcx=reflection)
        output.append(dict(name='natural-runtime-type-helper-before-unobserved-class-cell',
            actualRuntimeTypeClass=e.read(object_class + 0x10), actualWrappedType=e.read(enum + 0x10),
            firstUnobservedCell='0x1458869b8', firstUnobservedHierarchyOffsets=['0xc4', '0x48'], trace=trace))
        assert all(not row['trace']['models'] for row in output)
        return output
    finally:
        e.close()


if __name__ == '__main__':
    main()
