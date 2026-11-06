"""Recover exact Avatar generic-context definitions and bounded List constructors."""
import argparse
import bisect
import hashlib
import json
import struct
from pathlib import Path

try:
    from . import client72_avatar_deep as base
    from .extract_client72 import CODE_REGISTRATION, METADATA_REGISTRATION
    from .extract_client72_signatures import annotate_generic_positions, bind_class_parameters
except ImportError:
    import client72_avatar_deep as base
    from extract_client72 import CODE_REGISTRATION, METADATA_REGISTRATION
    from extract_client72_signatures import annotate_generic_positions, bind_class_parameters

INPUTS = {'avatar-deep-observations.json': 'fc08a41533290f671693ac1a0a0ec3608ac56c1cd93cc039f2c681f8102a44c7'}
ENTRIES = (0x14051C9D0, 0x1404EEA50, 0x1404EE6F0, 0x140524010, 0x14052E690,
           0x14052DFF0, 0x14052DD70, 0x14052E0B0, 0x159111AF0)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ('client', 'output', 'observations'):
        parser.add_argument('--' + name, type=Path, required=True)
    parser.add_argument('--python-libs', type=Path, default=Path('local/migration72/python'))
    args = parser.parse_args()
    if args.output.resolve() == args.observations.resolve() or any(path.resolve().is_relative_to(args.client.resolve()) for path in (args.output, args.observations)):
        parser.error('Distinct outputs outside client required')
    for name, digest in INPUTS.items():
        if hashlib.sha256((Path('protocol/cn-7.2') / name).read_bytes()).hexdigest() != digest:
            raise ValueError('Reviewed Avatar source changed')
    b, m, s = [base.read_verified(args.client / name, digest) for name, digest in base.HASHES.items()]
    records, _ = base.extract(b, m, startup=s, structures=True)
    cells = base.type_cells(b, m, s, records)
    report = declarations(b, m, s, records, args.python_libs)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.with_suffix('.static.json').write_text(json.dumps(report, indent=2) + '\n', 'utf8')
    report['native'] = probe(b, m, s, records, cells, report, args)
    root = Path(__file__).resolve().parents[1]
    report.update(task='F-03d3f4d2-rgctx', inputSHA256=base.HASHES, inputContracts=INPUTS,
        completeConstructor=False, completeAvatarHandler=False, runtimeStatus='not-tested', targetProtocolReady=False,
        sourceSHA256={name: hashlib.sha256((root / name).read_bytes()).hexdigest() for name in (
            'tools/client72_avatar_deep_rgctx.py', 'tools/client72_avatar_deep.py', 'tools/client72_avatar_initializer.py',
            'tools/extract_client72.py', 'tools/extract_client72_signatures.py', 'tools/client72_emulator.py', 'tools/client72_session_runtime.py')},
        models=['Generic MethodInfo/class/rgctx and initialized System.Object default-class storage are explicit managed fixtures.',
            'Selected class definition words and original/open MethodSpecs are recovered from fixed metadata tables, not guessed by code shape.',
            'Actual reference-type canonicalizer, List<object> registered constructor and three Avatar middle constructor stores execute natively.',
            'Enum sharing option and class metadata normalization remain unknown; no convenient flag value or enum constructor target is selected.'],
        unresolved=['HashSet comparer overload identity15591 is now exact, but its closed enum20060 executable target still depends on unresolved enum-sharing default-class/option state.',
            'SortedDictionary overload14780 is exact; reference-value normalization has a registered32758/Object candidate, but full key canonicalization and deeper comparer/tree constructors are not executed.',
            'Avatar middle+50/+1c8/+40 are initialized under declared metadata fixture; +1e8/+1c0, full constructor and full handler remain incomplete.'])
    args.output.write_text(json.dumps(report, indent=2) + '\n', 'utf8')
    args.observations.parent.mkdir(parents=True, exist_ok=True)
    args.observations.write_text(json.dumps(compact(report), indent=2) + '\n', 'utf8')
    print(json.dumps(dict(classRanges=report['tables']['classRangeCount'], nativeCases=len(report['native']), completeConstructor=False)))


def declarations(binary, metadata, startup, records, libraries):
    base.load_emulator(libraries)
    from capstone import Cs, CS_ARCH_X86, CS_MODE_64
    decoder = Cs(CS_ARCH_X86, CS_MODE_64)
    pe = base.PE(binary)
    h = pe.offset(base.HEADER_VA)
    resolver = base.TypeResolver(pe, startup, records, h)
    class_base = base.BODY + ((base.number(binary, h + 0x50) + 0xe113ba5c) & 0xffffffff)
    class_size = (base.number(binary, h + 0x104) + 0x938d8470) & 0xffffffff
    definition_base = base.BODY + (base.number(binary, h + 0x9c) ^ 0x4b5cdca3)
    rows = list(struct.iter_unpack('<II', metadata[class_base:class_base + class_size]))
    if not rows or any(a[0] >= b[0] for a, b in zip(rows, rows[1:])):
        raise ValueError('Class RGCTX ranges are not strictly sorted')
    spec_base = base.BODY + (base.number(binary, h + 0x170) ^ 0x6347382b)
    generic_base = base.BODY + ((base.number(binary, h + 0x94) + 0xd97e8a5c) & 0xffffffff)
    methods = {method['index']: (owner['index'], method) for owner in records for method in owner['methods']}
    selected = []
    for owner, slot, actual_instance, expected_method in ((2147, 1, 5917, 15591), (2034, 0, 12373, 14780), (69137, 1, 5278, 10374)):
        packed = dict(rows)[owner]
        count, index = packed >> 24, packed & 0xffffff
        word = base.number(metadata, definition_base + (index + slot) * 4)
        kind = (word >> 26) & 31
        spec = word & 0x3ffffff
        method, cls, instance = struct.unpack_from('<iii', metadata, spec_base + spec * 12)
        if kind != 3 or method != expected_method or instance != -1:
            raise ValueError('Target constructor definition word changed')
        actual_arguments = [resolver.resolve_index(index) for index in resolver.arguments[actual_instance]]
        open_arguments = [annotate_generic_positions(resolver.resolve_index(index), metadata, generic_base) for index in resolver.arguments[cls]]
        inflated = [bind_class_parameters(argument, actual_arguments) for argument in open_arguments]
        selected.append(dict(owner=owner, slot=slot, count=count, definitionIndex=index, word=hex(word), kind=kind,
            methodSpec=spec, methodDefinition=method, declaringOwner=methods[method][0], methodName=methods[method][1]['name'],
            classInstance=cls, actualInstance=actual_instance, openArguments=open_arguments, actualArguments=actual_arguments,
            inflatedArguments=inflated))
    aot = base.selected_aot(binary, metadata, startup, records, {(1255, 0): ('.ctor',), (2034, 12374): ('.ctor',)})
    list_ctor = next(row for row in aot if row['methodDefinition'] == 10374)
    if list_ctor['address'] != '0x159111af0' or list_ctor['arguments'][0]['kind'] != 'object':
        raise ValueError('Registered canonical List constructor changed')
    optional = base.number(binary, 0x3c) + 24
    rva, size = struct.unpack_from('<II', binary, optional + 112 + 3 * 8)
    ranges = {pe.base + begin: pe.base + end for begin, end, _ in struct.iter_unpack('<III', binary[pe.offset(pe.base + rva):pe.offset(pe.base + rva) + size])}
    functions = {}
    for entry in ENTRIES:
        end = ranges[entry]
        raw = binary[pe.offset(entry):pe.offset(entry) + end - entry]
        functions[hex(entry)] = dict(end=hex(end), nativeBytesSHA256=hashlib.sha256(raw).hexdigest(),
            instructions=[dict(address=hex(ins.address), instruction=ins.mnemonic + ' ' + ins.op_str) for ins in decoder.disasm(raw, entry)])
    flag_cell = 0x145BC8EF0
    candidates = []
    for address, size, raw, flags in pe.sections:
        if not flags & 0x20000000:
            continue
        data = binary[raw:raw + size]
        for prefix in (b'\x48\x89', b'\x4c\x89', b'\x48\x8d', b'\x4c\x8d'):
            position = -1
            while True:
                position = data.find(prefix, position + 1)
                if position < 0 or position + 7 > len(data):
                    break
                if data[position + 2] & 0xc7 != 5:
                    continue
                if address + position + 7 + struct.unpack_from('<i', data, position + 3)[0] == flag_cell:
                    candidates.append(address + position)
    starts = sorted(ranges)
    references, candidate_checks = [], []
    for address in candidates:
        position = bisect.bisect_right(starts, address) - 1
        start = starts[position]
        ins = next((item for item in decoder.disasm(binary[pe.offset(start):pe.offset(start) + ranges[start] - start], start)
                    if item.address == address), None) if position >= 0 and address < ranges[start] else None
        candidate_checks.append(dict(address=hex(address), function=hex(start), isInstructionBoundary=ins is not None))
        if ins is not None:
            references.append(hex(address))
    try:
        pe.offset(flag_cell, 8)
        file_backed = True
    except ValueError:
        file_backed = False
    return dict(tables=dict(classRangeBase=hex(class_base), classRangeBytes=class_size, classRangeCount=len(rows),
        definitionBase=hex(definition_base), classRangeSHA256=hashlib.sha256(metadata[class_base:class_base + class_size]).hexdigest()),
        selected=selected, canonicalAOT=aot, functions=functions,
        enumSharing=dict(flagPointerCell=hex(flag_cell), observedRuntimeValue=None, pointerCellFileBacked=file_backed,
            directMovLeaCandidates=references,
            rawByteCandidateChecks=candidate_checks,
            searchScope='REX.W MOV/LEA RIP disp32 candidates in PE executable raw sections only; excludes vector/indirect/immediate stores; empty result is not proof of no initialization',
            unapprovedExampleType=dict(index=57848, name=records[57848]['name'], namespace=records[57848]['namespace'],
                                       relation='Another enum appears at a shared-body candidate; no alias or target equivalence approved')))


def probe(binary, metadata, startup, records, cells, static, args):
    directory = Path('protocol/cn-7.2')
    prior = json.loads((directory / 'avatar-deep-observations.json').read_text('utf8'))
    declared = base.initializer_declarations(binary, metadata, startup, records, cells, args.python_libs)
    e = base.Offline72(binary, args.python_libs, records, trace_instructions=False)
    g = base.AvatarContractGraph(e, directory, cells, base.core_aot(binary, metadata, startup, records),
        base.avatar_aot(binary, metadata, startup, records, dict(types=[])), json.loads((directory / 'avatar-outer-observations.json').read_text('utf8')))
    rt = None
    result = []
    try:
        g.run_graph(30509, base.message(6, base.avatar_wire(1001, 0x8000000000000001, [], [])))
        rt = base.DeepRuntime(e, g, declared, prior)
        e.models[0x15184E100] = rt.deliver
        rt.root()
        e.models.pop(0x15184E100, None)
        model, _ = rt.delivery[0]
        for entry in (0x14052DD70, 0x159111AF0):
            e.allow(entry) if entry in e.ends else e.allow_unwind(entry)
            e.models.pop(entry, None)
        object_class = e.alloc(512)
        e.write(object_class + 0x10, 371)
        e.write(object_class + 0x18, 0x1c0000, 'I')
        e.write(0x14052DD98 + 7 + 0x569B841, object_class)
        for type_index in (63831, 51041):
            typ = e.alloc(16)
            e.write(typ, type_index)
            e.write(typ + 8, 0x120000, 'I')
            trace = rt.invoke(0x14052DD70, rcx=typ)
            result.append(dict(name='reference-canonicalization-' + str(type_index), sourceType=type_index,
                returnedObjectTypePointer=e.reg('rax') == object_class + 0x10,
                resultKind=(e.read(e.reg('rax') + 8, 'I') >> 16) & 255, trace=trace))
        # Exact parent RGCTX slot1 is List<T> .ctor, not the parent wrapper.
        context = e.read(e.read(e.read(int(declared['customContextCell'], 16))) + 0x30)
        method, klass, list_context = e.alloc(64), e.alloc(512), e.alloc(64)
        empty = rt.empty_array()
        e.write(method, klass)
        e.write(method + 8, 0x159111AF0)
        e.write(klass + 0x30, list_context)
        e.write(list_context + 0x10, empty)
        e.write(context + 0x18, method)
        for old in (False, True):
            for offset in (0x50, 0x1c8, 0x40):
                e.write(model + offset, 0x123456789 if old else 0)
            trace = rt.invoke(0x15183FF7A, {0x15184003D}, r14=model)
            containers = []
            for offset in (0x50, 0x1c8, 0x40):
                pointer = e.read(model + offset)
                containers.append(dict(offset=hex(offset), type=rt.allocated_types[pointer]['typeDefinition'],
                    backingArrayIsInitializedEmpty=e.read(pointer + 0x10) == empty,
                    count=e.read(pointer + 0x18, 'I'), arrayLength=e.read(empty + 0x18, 'I')))
            result.append(dict(name='three-inherited-list-constructors-' + ('old' if old else 'fresh'),
                               containers=containers, trace=trace))
        target = e.alloc(64)
        e.write(target + 0x10, 0x123456789)
        trace = rt.invoke(0x159111AF0, rcx=target, rdx=method)
        result.append(dict(name='list-constructor-overwrites-old-backing-array', preservedPointer=e.read(target + 0x10) == empty, trace=trace))
        e.write(list_context + 0x10, 0)
        trace = rt.invoke(0x159111AF0, {0x159111B1E}, rcx=target, rdx=method)
        result.append(dict(name='missing-list-array-context-before-resolver', trace=trace))
        return result
    except Exception:
        args.output.with_suffix('.failure.json').write_text(json.dumps(dict(completed=result, calls=e.calls,
            registers={name: hex(e.reg(name)) for name in ('rip', 'rcx', 'rdx', 'r14', 'rsp')}), indent=2) + '\n', 'utf8')
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
                'nativeEntries': sorted((set(map(hex, ENTRIES)) | {value['entry']}) & set(value['executedAddresses']))}
        return {key: compact(item) for key, item in value.items() if key != 'instructions' or not isinstance(item, list)}
    return value


if __name__ == '__main__':
    main()
