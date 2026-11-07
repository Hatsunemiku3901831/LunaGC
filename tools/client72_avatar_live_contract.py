"""Fixed-input Avatar enum bootstrap and continuous warm constructor contract.

Only offline CPU execution. This tool neither opens nor attaches to a client.
"""
import argparse
import hashlib
import json
import struct
from pathlib import Path

try:
    from . import client72_avatar_deep as base
    from . import client72_avatar_deep_sorted as sorted_contract
    from .client72_avatar_contract import fragment, RETURN
    from .client72_emulator import STACK
except ImportError:
    import client72_avatar_deep as base
    import client72_avatar_deep_sorted as sorted_contract
    from client72_avatar_contract import fragment, RETURN
    from client72_emulator import STACK

INPUTS = sorted_contract.INPUTS | {
    'avatar-deep-sorted-observations.json': '549566564c42c653c05b11a26a73ef249d2e2e8cba0dca75efaa0e4648ed1401'}
BOOTSTRAP = 0x1402F8450
FLAG_CELL = 0x145BC8EF0
FLAG_DATA = 0x1422F9988
ENUM_DEFAULT = 0x145BC9638
ENUM_CTOR = 0x156FBC0B0
NATIVE = (0x14052DD70, 0x14052DF40, 0x156FBC0B0, 0x159111AF0)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ('client', 'output', 'observations'):
        parser.add_argument('--' + name, type=Path, required=True)
    parser.add_argument('--python-libs', type=Path, default=Path('local/migration72/python'))
    args = parser.parse_args()
    if args.output.resolve() == args.observations.resolve() or any(
            path.resolve().is_relative_to(args.client.resolve()) for path in (args.output, args.observations)):
        parser.error('Distinct outputs outside client required')
    for name, digest in INPUTS.items():
        if hashlib.sha256((Path('protocol/cn-7.2') / name).read_bytes()).hexdigest() != digest:
            raise ValueError('Reviewed input changed: ' + name)
    b, m, s = [base.read_verified(args.client / name, digest) for name, digest in base.HASHES.items()]
    records, _ = base.extract(b, m, startup=s, structures=True)
    cells = base.type_cells(b, m, s, records)
    static = declarations(b, m, s, records, cells, args.python_libs)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.with_suffix('.static.json').write_text(json.dumps(static, indent=2) + '\n', 'utf8')
    native = probe(b, m, s, records, cells, static, args)
    root = Path(__file__).resolve().parents[1]
    report = dict(task='F-03d3f4d2-live-contract', evidenceKind='static-and-bounded-offline-native',
        inputSHA256=base.HASHES, inputContracts=INPUTS, declarations=static, native=native,
        completeWarmManagedConstructor=True, completeConstructor=False, completeAvatarHandler=False,
        runtimeStatus='not-tested', targetProtocolReady=False,
        sourceSHA256={name: hashlib.sha256((root / name).read_bytes()).hexdigest() for name in (
            'tools/client72_avatar_live_contract.py', 'tools/client72_avatar_deep.py',
            'tools/client72_avatar_initializer.py', 'tools/client72_avatar_deep_sorted.py',
            'tools/extract_client72.py', 'tools/client72_emulator.py', 'tools/client72_avatar_contract.py')},
        models=['Typed allocation, initialized arrays, original closed MethodInfo/RGCTX and warm opaque comparer caches retain prior reviewed fixture semantics.',
            'Type20060 enum class, System.Int32 underlying class, System.Int32Enum canonical class and populated class-cache array are explicit metadata storage fixtures.',
            'Class lookup trampoline and its actual cached native path execute; no class-lookup or enum-normalization success model is installed.',
            'Bootstrap leaf executes all eleven original instructions. Runtime registration order and cold reflection/comparer construction remain unobserved.'],
        unresolved=['Cold comparer/reflection initialization still required for natural first construction.',
            'Full equipment refresh, skill/depot resource state, pool lifecycle and complete Avatar handler are not established.',
            'No production Java sender, formal g2, deployment or live client acceptance follows from a warm managed constructor.'])
    args.output.write_text(json.dumps(report, indent=2) + '\n', 'utf8')
    args.observations.parent.mkdir(parents=True, exist_ok=True)
    args.observations.write_text(json.dumps(compact(report), indent=2) + '\n', 'utf8')
    print(json.dumps(dict(cases=len(native), warmManagedConstructor=True, completeAvatarHandler=False)))


def declarations(binary, metadata, startup, records, cells, libraries):
    base.load_emulator(libraries)
    from capstone import Cs, CS_ARCH_X86, CS_MODE_64
    pe = base.PE(binary)
    decoder = Cs(CS_ARCH_X86, CS_MODE_64)
    optional = base.number(binary, 0x3c) + 24
    rva, size = struct.unpack_from('<II', binary, optional + 112 + 3 * 8)
    ranges = {pe.base + begin: pe.base + end for begin, end, _ in struct.iter_unpack(
        '<III', binary[pe.offset(pe.base + rva):pe.offset(pe.base + rva) + size])}
    windows = {BOOTSTRAP: 0x1402F8497, 0x1402F84A0: 0x1402F84B9,
        0x140543452: 0x140543473, 0x1405269F0: 0x1405269F5}
    windows.update({entry: ranges[entry] for entry in NATIVE})
    functions = {}
    for start, end in windows.items():
        code = binary[pe.offset(start):pe.offset(start) + end - start]
        instructions = [dict(address=hex(ins.address), instruction=ins.mnemonic + ' ' + ins.op_str,
                             bytes=ins.bytes.hex()) for ins in decoder.disasm(code, start)]
        functions[hex(start)] = dict(end=hex(end), nativeBytesSHA256=hashlib.sha256(code).hexdigest(), instructions=instructions)
    if any(start <= BOOTSTRAP < end for start, end in ranges.items()):
        raise ValueError('Bootstrap leaf unwind classification changed')
    leaf = functions[hex(BOOTSTRAP)]['instructions']
    if len(leaf) != 11 or leaf[-1]['instruction'] != 'ret ':
        raise ValueError('Bootstrap leaf boundary changed')
    flag_data = binary[pe.offset(FLAG_DATA):pe.offset(FLAG_DATA) + 8]
    if flag_data != bytes.fromhex('0101000007000000'):
        raise ValueError('Enum-sharing option record changed')
    literal_addresses = (0x143AA606B, 0x143AA59CE)
    literals = [binary[pe.offset(address):pe.offset(address) + 32].split(b'\0')[0].decode('ascii') for address in literal_addresses]
    if literals != ['System', 'Int32Enum'] or records[351]['name'] != 'Int32Enum' or records[351]['namespace'] != 'System':
        raise ValueError('Default enum class source changed')
    value_field = next(field for field in records[20060]['fields'] if field['name'] == 'value__')
    if value_field['type']['kind'] != 'int32' or records[20060]['parent']['typeDefinition'] != 204:
        raise ValueError('Target enum underlying type changed')
    selected = base.selected_aot(binary, metadata, startup, records, {(2147, 65): ('.ctor',)})
    target = next(row for row in selected if row['methodDefinition'] == 15591)
    if target['address'] != hex(ENUM_CTOR) or target['arguments'][0]['typeDefinition'] != 351:
        raise ValueError('Canonical enum constructor AOT changed')
    flag_section = next(dict(start=hex(va), rawSize=n, flags=hex(flags), writable=bool(flags & 0x80000000))
                        for va, n, raw, flags in pe.sections if va <= FLAG_DATA < va + n)
    return dict(bootstrap=dict(entry=hex(BOOTSTRAP), end='0x1402f8497', hasUnwindRecord=False,
        previousUnwindEnd=hex(ranges[0x1402F8400]), registrationReference='0x1402f84a7',
        flagCell=hex(FLAG_CELL), optionRecord=hex(FLAG_DATA), optionBytes=flag_data.hex(), optionSection=flag_section),
        enum=dict(type=20060, underlyingField=value_field, defaultClass=351, defaultClassCell=hex(ENUM_DEFAULT),
                  classNameLiterals=literals, literalAddresses=list(map(hex, literal_addresses)), canonicalAOT=target),
        functions=functions,
        sorted=sorted_contract.declarations(binary, metadata, startup, records, cells, libraries))


def bind_warm_constructors(e, rt, initial, sorted_declared):
    """Install exact recovered MethodInfo links; original closed types stay intact."""
    enum_method = e.read(int(initial['enumContextCell'], 16))
    enum_class = e.read(enum_method)
    enum_context = e.read(enum_class + 0x30)
    method = e.alloc(64)
    e.write(method, enum_class)
    e.write(method + 8, ENUM_CTOR)
    e.write(enum_context + 0x18, method)
    list_context = e.read(e.read(e.read(int(initial['customContextCell'], 16))) + 0x30)
    list_method, list_class, list_slots = e.alloc(64), e.alloc(512), e.alloc(64)
    e.write(list_method, list_class)
    e.write(list_method + 8, 0x159111AF0)
    e.write(list_class + 0x30, list_slots)
    e.write(list_slots + 0x10, rt.empty_array())
    e.write(list_context + 0x18, list_method)
    pair_class, tree_class = e.alloc(512), e.alloc(512)
    pair_slots, tree_slots = e.alloc(128), e.alloc(128)
    for klass, slots, slot in ((pair_class, pair_slots, 2), (tree_class, tree_slots, 4)):
        rt.classes[klass] = next(row['type'] for row in sorted_declared['contexts'] if row['owner'] == 2034 and row['slot'] == slot)
        e.write(klass + 0x30, slots)
        e.write(klass + 0xc8, 1, 'B')
    methods = {}
    for identity, address, klass in ((14780, 0x1570F65B0, 0), (14879, 0x158878110, pair_class),
            (10271, 0x1560F5700, 0), (14886, 0x157601A80, tree_class), (14994, 0x156381BF0, 0)):
        info = e.alloc(64)
        e.write(info, klass)
        e.write(info + 8, address)
        methods[identity] = info
    root_class, root_slots = e.alloc(512), e.alloc(128)
    e.write(root_class + 0x30, root_slots)
    e.write(methods[14780], root_class)
    for offset, value in ((0x20, pair_class), (0x28, methods[14879]), (0x30, tree_class), (0x38, methods[14886])):
        e.write(root_slots + offset, value)
    e.write(pair_slots + 0x10, methods[10271])
    e.write(tree_slots + 0x18, methods[14994])
    comparer_class = e.synthetic_type(int(sorted_declared['comparer']['cell'], 16))
    cache = e.alloc(32)
    e.write(comparer_class + 0x38, cache)
    e.write(cache, e.alloc(64))
    context = e.read(e.read(e.read(int(initial['sortedContextCell'], 16))) + 0x30)
    e.write(context + 0x10, methods[14780])
    return method


def probe(binary, metadata, startup, records, cells, static, args):
    directory = Path('protocol/cn-7.2')
    initial = base.initializer_declarations(binary, metadata, startup, records, cells, args.python_libs)
    e = base.Offline72(binary, args.python_libs, records, trace_instructions=True)
    g = base.AvatarContractGraph(e, directory, cells, base.core_aot(binary, metadata, startup, records),
        base.avatar_aot(binary, metadata, startup, records, dict(types=[])),
        json.loads((directory / 'avatar-outer-observations.json').read_text('utf8')))
    rt, result = None, []
    try:
        g.run_graph(30509, base.message(6, base.avatar_wire(1001, 0x8000000000000001, [], [])))
        rt = base.DeepRuntime(e, g, initial, json.loads((directory / 'avatar-deep-observations.json').read_text('utf8')))
        for entry in (*NATIVE, *sorted_contract.ENTRIES):
            e.allow(entry) if entry in e.ends else e.allow_unwind(entry)
            e.models.pop(entry, None)
        e.allowed.extend(((BOOTSTRAP, 0x1402F8497), (0x1405269F0, 0x1405269F5)))
        for old in (0, 0xFEDCBA9876543210):
            e.write(FLAG_CELL, old)
            trace = rt.invoke(BOOTSTRAP)
            result.append(dict(name='bootstrap-' + ('fresh' if old == 0 else 'old'), flagPointer=hex(e.read(FLAG_CELL)),
                flagByte=e.read(e.read(FLAG_CELL), 'B'), trace=trace))
        enum_class, integer_class, shared_class = e.alloc(512), e.alloc(512), e.alloc(512)
        registry = e.alloc((20060 + 1) * 8)
        e.write(registry + 20060 * 8, enum_class)
        e.write(0x145BC8F18, registry)
        e.write(enum_class + 0x10, 20060)
        e.write(enum_class + 0x18, 0x110000, 'I')
        e.write(enum_class + 0xca, 4, 'B')
        e.write(integer_class + 0x10, 251)
        e.write(integer_class + 0x18, 0x080000, 'I')
        e.write(shared_class + 0x10, 351)
        e.write(shared_class + 0x18, 0x110000, 'I')
        compact_base = e.alloc(32)
        e.write(compact_base, enum_class)
        e.write(0x145BC8AD0, compact_base)
        e.write(enum_class + 0xac, integer_class - enum_class, 'I')
        e.write(ENUM_DEFAULT, shared_class)
        trace = rt.invoke(0x14052DD70, rcx=enum_class + 0x10)
        result.append(dict(name='enum20060-canonicalized-by-native', returnedType=e.read(e.reg('rax')),
            returnedCanonicalPointer=e.reg('rax') == shared_class + 0x10, trace=trace))
        trace = rt.invoke(0x14052DD70, rcx=integer_class + 0x10)
        result.append(dict(name='ordinary-int32-is-not-enum', originalPointerRetained=e.reg('rax') == integer_class + 0x10, trace=trace))
        e.write(registry + 20060 * 8, 0)
        trace = rt.invoke(0x14052DD70, {0x15A4D6C03}, rcx=enum_class + 0x10)
        result.append(dict(name='cold-class-cache-before-lock-and-class-build', trace=trace))
        e.write(registry + 20060 * 8, enum_class)
        enum_method = bind_warm_constructors(e, rt, initial, static['sorted'])
        comparer = e.read(rt.enum_comparer_cache)
        for old in (False, True):
            target = e.alloc(96)
            if old:
                e.uc.mem_write(target + 0x10, b'\x7d' * 48)
            trace = rt.invoke(ENUM_CTOR, rcx=target, rdx=comparer, r8=enum_method)
            result.append(dict(name='enum-constructor-' + ('old' if old else 'fresh'), comparerPreserved=e.read(target + 0x10) == comparer,
                headerWords=[e.read(target + offset, 'I') for offset in (0x28, 0x2c, 0x30, 0x34)],
                untouchedPayload=bytes(e.uc.mem_read(target + 0x18, 16)).hex(), trace=trace))
        for old in (False, True):
            model = e.alloc(1024)
            e.write(model, rt.klass)
            if old:
                for offset in (0x1e8, 0x1c0, 0x50, 0x1c8, 0x40):
                    e.write(model + offset, 0xFEDCBA9876543210)
                e.write(model + 0x284, 777, 'I')
                e.write(model + 0x2b0, 0, 'B')
            trace = rt.invoke(0x15183FE60, rcx=model)
            snapshot = rt.snapshot(model)
            middle = snapshot.pop('unresolvedUnwrittenOffsets')
            snapshot['middleContainers'] = {key: dict(type=rt.allocated_types[value], nonNull=bool(value)) for key, value in middle.items()}
            result.append(dict(name='continuous-avatar-constructor-' + ('old' if old else 'fresh'), state=snapshot, trace=trace))
        trace = rt.invoke(ENUM_CTOR, {0x156FBC0F2}, rcx=0, rdx=comparer, r8=enum_method)
        result.append(dict(name='null-enum-object-before-native-error', trace=trace))
        e.write(rt.enum_comparer_cache, 0)
        trace = rt.invoke(0x15183FE60, {0x157D7FEEB}, rcx=e.alloc(1024))
        result.append(dict(name='cold-enum-comparer-before-type-resolution', trace=trace))
        return result
    except Exception:
        args.output.with_suffix('.failure.json').write_text(json.dumps(dict(completed=result, calls=e.calls,
            registers={name: hex(e.reg(name)) for name in ('rip', 'rcx', 'rdx', 'r8', 'r14', 'rsp')}), indent=2) + '\n', 'utf8')
        raise
    finally:
        if rt:
            rt.close()
        g.close()
        e.close()


def compact(value):
    if isinstance(value, list):
        return [compact(item) for item in value]
    if isinstance(value, dict):
        if 'executedAddresses' in value:
            return {key: value[key] for key in ('entry', 'stop', 'completion', 'liveIn', 'instructions', 'nativePathSHA256', 'models')} | {
                'nativeEntries': sorted((set(map(hex, (*NATIVE, *sorted_contract.ENTRIES, BOOTSTRAP, 0x1405269F0, 0x15A4D6B60))) | {value['entry']}) & set(value['executedAddresses']))}
        return {key: compact(item) for key, item in value.items() if key != 'instructions' or not isinstance(item, list)}
    return value


if __name__ == '__main__':
    main()
