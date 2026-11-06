"""Exact Avatar SortedDictionary comparer/tree constructor chain, offline only."""
import argparse
import hashlib
import json
import struct
from pathlib import Path

try:
    from . import client72_avatar_deep as base
    from .extract_client72_signatures import annotate_generic_positions, bind_class_parameters
except ImportError:
    import client72_avatar_deep as base
    from extract_client72_signatures import annotate_generic_positions, bind_class_parameters

INPUTS = {'avatar-deep-observations.json': 'fc08a41533290f671693ac1a0a0ec3608ac56c1cd93cc039f2c681f8102a44c7',
          'avatar-deep-rgctx-observations.json': 'a8b29f1916799b754669bb1f609fd89f9a8427466217db1460a9503d7df642e0'}
ENTRIES = (0x1570F65B0, 0x158878110, 0x1560F5700, 0x157601A80, 0x156381BF0)


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
            raise ValueError('Reviewed Avatar input changed')
    b, m, s = [base.read_verified(args.client / name, digest) for name, digest in base.HASHES.items()]
    records, _ = base.extract(b, m, startup=s, structures=True)
    cells = base.type_cells(b, m, s, records)
    declared = declarations(b, m, s, records, cells, args.python_libs)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.with_suffix('.static.json').write_text(json.dumps(declared, indent=2) + '\n', 'utf8')
    rows = probe(b, m, s, records, cells, declared, args)
    root = Path(__file__).resolve().parents[1]
    report = dict(task='F-03d3f4d2-sorted', inputSHA256=base.HASHES, inputContracts=INPUTS, declarations=declared, native=rows,
        completeSelectedSortedConstructor=True, completeConstructor=False, completeAvatarHandler=False,
        runtimeStatus='not-tested', targetProtocolReady=False,
        sourceSHA256={name: hashlib.sha256((root / name).read_bytes()).hexdigest() for name in (
            'tools/client72_avatar_deep_sorted.py', 'tools/client72_avatar_deep.py', 'tools/client72_avatar_initializer.py',
            'tools/extract_client72.py', 'tools/extract_client72_signatures.py', 'tools/client72_emulator.py', 'tools/client72_session_runtime.py')},
        models=['Typed managed allocation and initialized class/MethodInfo/RGCTX storage are explicit fixtures based on recovered definitions.',
            'Non-null and warm cached IComparer<32758> pointers are opaque; their creation and comparison operations are not executed.',
            'Native SortedDictionary, KeyValuePairComparer, Comparer base ctor, TreeSet and SortedSet constructors execute; cold cache stops before unknown metadata/reflection.',
            'Reference-value generic sharing reuses the reviewed canonicalizer; actual context types retain51041, never replace original model type arguments with Object.'],
        unresolved=['Avatar enum HashSet target/comparer initialization remains open; no enum-sharing runtime option is selected.',
            'Complete Avatar handler, nonempty tree operations, cold comparer creation and real client initialization are not established.'])
    args.output.write_text(json.dumps(report, indent=2) + '\n', 'utf8')
    args.observations.parent.mkdir(parents=True, exist_ok=True)
    args.observations.write_text(json.dumps(compact(report), indent=2) + '\n', 'utf8')
    print(json.dumps(dict(nativeCases=len(rows), completeSelectedSortedConstructor=True, completeConstructor=False)))


def declarations(binary, metadata, startup, records, cells, libraries):
    base.load_emulator(libraries)
    from capstone import Cs, CS_ARCH_X86, CS_MODE_64
    decoder = Cs(CS_ARCH_X86, CS_MODE_64)
    pe = base.PE(binary)
    h = pe.offset(base.HEADER_VA)
    resolver = base.TypeResolver(pe, startup, records, h)
    range_base = base.BODY + ((base.number(binary, h + 0x50) + 0xe113ba5c) & 0xffffffff)
    range_size = (base.number(binary, h + 0x104) + 0x938d8470) & 0xffffffff
    ranges = dict(struct.iter_unpack('<II', metadata[range_base:range_base + range_size]))
    definitions = base.BODY + (base.number(binary, h + 0x9c) ^ 0x4b5cdca3)
    specs = base.BODY + (base.number(binary, h + 0x170) ^ 0x6347382b)
    generic_base = base.BODY + ((base.number(binary, h + 0x94) + 0xd97e8a5c) & 0xffffffff)
    arguments = [resolver.resolve_index(index) for index in resolver.arguments[12373]]
    contexts = []
    for owner, slots, actual in ((2034, (2, 3, 4, 5), arguments), (2046, (0,), arguments)):
        for slot in slots:
            packed = ranges[owner]
            word = base.number(metadata, definitions + ((packed & 0xffffff) + slot) * 4)
            kind, index = (word >> 26) & 31, word & 0x3ffffff
            if kind == 2:
                opened = annotate_generic_positions(resolver.resolve_index(index), metadata, generic_base)
                contexts.append(dict(owner=owner, slot=slot, word=hex(word), kind=kind, type=bind_class_parameters(opened, actual)))
            elif kind == 3:
                method, cls, instance = struct.unpack_from('<iii', metadata, specs + index * 12)
                inflated = [bind_class_parameters(annotate_generic_positions(resolver.resolve_index(ref), metadata, generic_base), actual)
                            for ref in resolver.arguments[cls]]
                contexts.append(dict(owner=owner, slot=slot, word=hex(word), kind=kind, methodSpec=index,
                    methodDefinition=method, arguments=inflated, methodInstance=instance))
            else:
                raise ValueError('Unexpected sorted constructor context kind')
    tree_arguments = next(row['type']['arguments'] for row in contexts if row['owner'] == 2034 and row['slot'] == 4)
    packed = ranges[2049]
    word = base.number(metadata, definitions + ((packed & 0xffffff) + 1) * 4)
    method, cls, instance = struct.unpack_from('<iii', metadata, specs + (word & 0x3ffffff) * 12)
    contexts.append(dict(owner=2049, slot=1, word=hex(word), kind=(word >> 26) & 31, methodSpec=word & 0x3ffffff,
        methodDefinition=method, arguments=[bind_class_parameters(annotate_generic_positions(resolver.resolve_index(ref), metadata, generic_base), tree_arguments)
                                          for ref in resolver.arguments[cls]], methodInstance=instance))
    aot = base.selected_aot(binary, metadata, startup, records, {(2034, 12374):('.ctor',), (2046,12374):('.ctor',),
        (2049,12375):('.ctor',), (2056,12375):('.ctor',), (1230,12375):('.ctor',)})
    expected = {14780:'0x1570f65b0', 14879:'0x158878110', 10271:'0x1560f5700', 14886:'0x157601a80', 14994:'0x156381bf0'}
    aot = [row for row in aot if row['methodDefinition'] in expected]
    if {row['methodDefinition']:row['address'] for row in aot} != expected:
        raise ValueError('Exact sorted constructor AOT changed')
    optional = base.number(binary, 0x3c) + 24
    rva, size = struct.unpack_from('<II', binary, optional + 112 + 3 * 8)
    extents = {pe.base + begin:pe.base + end for begin,end,_ in struct.iter_unpack('<III', binary[pe.offset(pe.base + rva):pe.offset(pe.base + rva) + size])}
    functions = {}
    for entry in ENTRIES:
        end = extents[entry]
        code = binary[pe.offset(entry):pe.offset(entry) + end - entry]
        functions[hex(entry)] = dict(end=hex(end), nativeBytesSHA256=hashlib.sha256(code).hexdigest(),
            instructions=[dict(address=hex(ins.address), instruction=ins.mnemonic+' '+ins.op_str) for ins in decoder.disasm(code, entry)])
    comparer_cell = 0x15887813e + 7 - 0x12f35045
    comparer_type = cells[comparer_cell]['type']
    if comparer_type['typeDefinition'] != 1230 or comparer_type['arguments'][0].get('typeDefinition') != 32758:
        raise ValueError('Warm comparer type differs')
    return dict(actualArguments=arguments, keyTypeParent=records[32758]['parent'], treeFields=records[2056]['fields'], contexts=contexts, canonicalAOT=aot,
                comparer=dict(cell=hex(comparer_cell), type=comparer_type), functions=functions)


def probe(binary, metadata, startup, records, cells, declared, args):
    directory = Path('protocol/cn-7.2')
    initial = base.initializer_declarations(binary, metadata, startup, records, cells, args.python_libs)
    e = base.Offline72(binary, args.python_libs, records, trace_instructions=False)
    g = base.AvatarContractGraph(e, directory, cells, base.core_aot(binary, metadata, startup, records),
        base.avatar_aot(binary, metadata, startup, records, dict(types=[])), json.loads((directory / 'avatar-outer-observations.json').read_text('utf8')))
    rt, result = None, []
    try:
        g.run_graph(30509, base.message(6, base.avatar_wire(1001, 0x8000000000000001, [], [])))
        rt = base.DeepRuntime(e, g, initial, json.loads((directory / 'avatar-deep-observations.json').read_text('utf8')))
        e.models[0x15184E100] = rt.deliver
        rt.root()
        e.models.pop(0x15184E100, None)
        model, _ = rt.delivery[0]
        for entry in ENTRIES:
            e.allow(entry) if entry in e.ends else e.allow_unwind(entry)
            e.models.pop(entry, None)
        # Actual closed types retain51041, while code addresses come from canonical AOT.
        pair_class, tree_class = e.alloc(512), e.alloc(512)
        pair_context, tree_context = e.alloc(128), e.alloc(128)
        for klass, context, slot in ((pair_class, pair_context, 2), (tree_class, tree_context, 4)):
            typ = next(row['type'] for row in declared['contexts'] if row['owner'] == 2034 and row['slot'] == slot)
            rt.classes[klass] = typ
            e.write(klass + 0x30, context)
            e.write(klass + 0xc8, 1, 'B')
        methods = {}
        for method, address, klass in ((14780, 0x1570F65B0, 0), (14879, 0x158878110, pair_class),
                (10271, 0x1560F5700, 0), (14886, 0x157601A80, tree_class), (14994, 0x156381BF0, 0)):
            info = e.alloc(64)
            e.write(info, klass)
            e.write(info + 8, address)
            methods[method] = info
        root_class, root_context = e.alloc(512), e.alloc(128)
        e.write(root_class + 0x30, root_context)
        e.write(methods[14780], root_class)
        e.write(root_context + 0x20, pair_class)
        e.write(root_context + 0x28, methods[14879])
        e.write(root_context + 0x30, tree_class)
        e.write(root_context + 0x38, methods[14886])
        e.write(pair_context + 0x10, methods[10271])
        e.write(tree_context + 0x18, methods[14994])
        # The base Comparer ctor is a real ret, and SortedSet receives a non-null pair comparer.
        comparer_class = e.synthetic_type(int(declared['comparer']['cell'], 16))
        cache, cached_comparer, supplied_comparer = e.alloc(32), e.alloc(64), e.alloc(64)
        e.write(comparer_class + 0x38, cache)
        e.write(cache, cached_comparer)
        for mode, incoming in (('supplied', supplied_comparer), ('warm-default', 0)):
            target = e.alloc(96)
            e.write(target + 0x10, 0x123456789)
            trace = rt.invoke(0x1570F65B0, rcx=target, rdx=incoming, r8=methods[14780])
            tree = e.read(target + 0x10)
            pair = e.read(tree + 0x10)
            result.append(dict(name='sorted-ctor-' + mode, treeType=rt.allocated_types[tree]['typeDefinition'],
                pairType=rt.allocated_types[pair]['typeDefinition'], comparerPreserved=e.read(pair + 0x10) == (incoming or cached_comparer),
                trace=trace))
        context = e.read(e.read(e.read(int(initial['sortedContextCell'], 16))) + 0x30)
        e.write(context + 0x10, methods[14780])
        for old in (False, True):
            e.write(model + 0x1c0, 0x123456789 if old else 0)
            trace = rt.invoke(0x15183FF1A, {0x15183FF7A}, r14=model)
            target = e.read(model + 0x1c0)
            tree, pair = e.read(target + 0x10), e.read(e.read(target + 0x10) + 0x10)
            result.append(dict(name='avatar-sorted-fragment-' + ('old' if old else 'fresh'),
                rootType=rt.allocated_types[target]['typeDefinition'], treeType=rt.allocated_types[tree]['typeDefinition'],
                pairType=rt.allocated_types[pair]['typeDefinition'], comparerPreserved=e.read(pair + 0x10) == cached_comparer,
                actualRootArguments=rt.allocated_types[target]['arguments'], trace=trace))
        e.write(cache, 0)
        target = e.alloc(96)
        trace = rt.invoke(0x1570F65B0, {0x158878154}, rcx=target, rdx=0, r8=methods[14780])
        result.append(dict(name='cold-comparer-before-reflection', rootUnwritten=e.read(target + 0x10) == 0, trace=trace))
        e.write(cache, cached_comparer)
        trace = rt.invoke(0x1570F65B0, {0x1570F6675}, rcx=0, rdx=supplied_comparer, r8=methods[14780])
        result.append(dict(name='null-root-before-native-error-helper', trace=trace))
        e.write(tree_context + 0x18, 0)
        trace = rt.invoke(0x1570F65B0, {0x157601AC3}, rcx=target, rdx=supplied_comparer, r8=methods[14780])
        result.append(dict(name='missing-tree-base-context-before-resolver', rootUnwritten=e.read(target + 0x10) == 0, trace=trace))
        return result
    except Exception:
        args.output.with_suffix('.failure.json').write_text(json.dumps(dict(completed=result, calls=e.calls,
            registers={name:hex(e.reg(name)) for name in ('rip','rcx','rdx','r8','rsp')}), indent=2)+'\n','utf8')
        raise
    finally:
        if rt:
            rt.close()
        g.close()
        e.close()


def compact(value):
    if isinstance(value,list):
        return [compact(item) for item in value]
    if isinstance(value,dict):
        if 'executedAddresses' in value:
            return {key:value[key] for key in ('entry','stop','completion','liveIn','instructions','nativePathSHA256','models')} | {
                'nativeEntries':sorted((set(map(hex,ENTRIES)) | {value['entry']}) & set(value['executedAddresses']))}
        return {key:compact(item) for key,item in value.items() if key!='instructions' or not isinstance(item,list)}
    return value


if __name__ == '__main__':
    main()
