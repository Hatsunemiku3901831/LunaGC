"""Independent offline suffix experiments after the frozen Avatar deep batch."""
import argparse
import hashlib
import json
import struct
from pathlib import Path

try:
    from . import client72_avatar_deep as base
    from .extract_client72_literals import Literals72, USAGE_REGISTRATION
    from .extract_client72_enums import recover as recover_enums
except ImportError:
    import client72_avatar_deep as base
    from extract_client72_literals import Literals72, USAGE_REGISTRATION
    from extract_client72_enums import recover as recover_enums

ENTRIES = {0x151840EB0, 0x15184DCA0, 0x15437ADA0, 0x15437AF90, 0x15184D840,
           0x1473673B0, 0x151845920, 0x15183E450, 0x15183FBF0, 0x14AD07E80,
           0x159113B70, 0x157EE1820, 0x140524010, 0x1404EE6F0}
EVIDENCE_SHA = base.EVIDENCE_SHA | {'avatar-deep-observations.json':
    'fc08a41533290f671693ac1a0a0ec3608ac56c1cd93cc039f2c681f8102a44c7'}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--python-libs', type=Path, default=Path('local/migration72/python'))
    parser.add_argument('--native', action='store_true')
    parser.add_argument('--observations', type=Path)
    args = parser.parse_args()
    outputs = [args.output] + ([args.observations] if args.observations else [])
    if len({path.resolve() for path in outputs}) != len(outputs) or any(path.resolve().is_relative_to(args.client.resolve()) for path in outputs):
        parser.error('Distinct outputs outside client required')
    for name, digest in EVIDENCE_SHA.items():
        if hashlib.sha256((Path('protocol/cn-7.2') / name).read_bytes()).hexdigest() != digest:
            raise ValueError('Reviewed Avatar input changed: ' + name)
    binary, metadata, startup = [base.read_verified(args.client / name, digest) for name, digest in base.HASHES.items()]
    records, extraction = base.extract(binary, metadata, startup=startup, structures=True)
    cells = base.type_cells(binary, metadata, startup, records)
    report = inspect(binary, metadata, startup, records, extraction, cells, args.python_libs)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.with_suffix('.static.json').write_text(json.dumps(report, indent=2) + '\n', 'utf8')
    if args.native:
        report['native'] = native_probe(binary, metadata, startup, records, cells, report, args)
    report['inputContracts'] = EVIDENCE_SHA
    root = Path(__file__).resolve().parents[1]
    report['sourceSHA256'] = {name: hashlib.sha256((root / name).read_bytes()).hexdigest() for name in (
        'tools/client72_avatar_deep_suffix.py', 'tools/client72_avatar_deep.py', 'tools/client72_avatar_initializer.py',
        'tools/client72_avatar_contract.py', 'tools/client72_emulator.py', 'tools/client72_session_runtime.py',
        'tools/extract_client72_literals.py', 'tools/extract_client72_enums.py')}
    report['models'] = [
        'Inherited typed managed reader/storage/allocator and reversible-codec fixtures are retained.',
        'Tag29 static list fragment has explicit rsi List/rax array/rdx index live-ins and capacity6. Full cctor allocation/growth/comparer dictionary construction is not executed.',
        'Tag29 bit fragments stop before actual dictionary insertion; effects, HashSet Contains and difference application are not executed.',
        'Tag38 resource caches, row groups/defaults and initialized metadata are synthetic. Key-to-index dictionary lookup and actual-reader Count/Item interfaces are managed storage models.',
        'Tag38 consumer, cache query branches, first-match selection, codecs, clearing and list stores execute original native code. Cache construction, resource parsing and real lifecycle are not executed.']
    report['unresolved'] = [
        'Exact enum HashSet/SortedDictionary/List<63831> shared constructor targets remain unknown: resolver140524010 needs runtime class+30 context+8 definition words and class+68 generic instantiation context; resolver1404ee6f0 dispatches tagged definitions and inflates MethodSpecs.',
        'Tag29 six-bit source mapping is bounded; insert15582ba40 and HashSet difference effects remain unexecuted.',
        'Tag38 typed cache/parser names and production source remain unapproved, despite complete consumer return in synthetic initialized caches.',
        'Equipment factory identities are static exact usage bindings; complete native factory+assignment+refresh and full Avatar constructor/handler remain incomplete.']
    args.output.write_text(json.dumps(report, indent=2) + '\n', 'utf8')
    lines = []
    for entry, function in report['functions'].items():
        lines.append('ENTRY ' + entry)
        lines.extend(row['address'] + ': ' + row['instruction'] for row in function['instructions'])
    args.output.with_suffix('.asm.txt').write_text('\n'.join(lines) + '\n', 'utf8')
    if args.observations:
        args.observations.parent.mkdir(parents=True, exist_ok=True)
        args.observations.write_text(json.dumps(compact(report), indent=2) + '\n', 'utf8')
    print(json.dumps(dict(functions=len(report['functions']), nativeExecuted=args.native)))


def inspect(binary, metadata, startup, records, extraction, cells, libraries):
    base.load_emulator(libraries)
    from capstone import Cs, CS_ARCH_X86, CS_MODE_64
    decoder = Cs(CS_ARCH_X86, CS_MODE_64)
    decoder.detail = True
    pe = base.PE(binary)
    optional = base.number(binary, 0x3C) + 24
    rva, size = struct.unpack_from('<II', binary, optional + 112 + 3 * 8)
    ranges = {pe.base + begin: pe.base + end for begin, end, _ in struct.iter_unpack('<III', binary[pe.offset(pe.base + rva):pe.offset(pe.base + rva) + size])}
    starts = sorted({int(method['registeredAddress'], 16) for record in records for method in record['methods'] if method['registeredAddress'] != '0x0'})
    functions = {}
    for entry in sorted(ENTRIES):
        end = ranges.get(entry) or next(address for address in starts if address > entry)
        raw = binary[pe.offset(entry):pe.offset(entry) + end - entry]
        instructions, references, calls = [], [], []
        for ins in decoder.disasm(raw, entry):
            instructions.append(dict(address=hex(ins.address), instruction=ins.mnemonic + ' ' + ins.op_str))
            if ins.mnemonic in ('call', 'jmp'):
                calls.append(dict(site=hex(ins.address), target=hex(ins.operands[0].imm) if ins.operands[0].type == 2 else ins.op_str,
                                  direct=ins.operands[0].type == 2))
            for operand in ins.operands:
                if operand.type == 3 and operand.mem.base == 41:
                    cell = ins.address + ins.size + operand.mem.disp
                    if cell in cells:
                        references.append(dict(site=hex(ins.address), cell=hex(cell), type=cells[cell]['type']))
        functions[hex(entry)] = dict(end=hex(end), extent='PE-exception-table' if entry in ranges else 'registered-method-boundary',
            nativeBytesSHA256=hashlib.sha256(raw).hexdigest(), instructions=instructions, calls=calls, types=references)
    h = pe.offset(base.HEADER_VA)
    resolver = base.TypeResolver(pe, startup, records, h)
    lit = Literals72(binary, metadata)
    cache = base.number(binary, pe.offset(USAGE_REGISTRATION) + 0x10, 'Q')
    n = ((base.number(metadata, lit.groups_base + (lit.global_group + 1) * 4) + 0x8bff3e05) & 0xffffffff) ^ (((lit.global_group + 1) * 0x5843eaa0 - 0x443c8630) & 0xffffffff)
    factories = []
    for index in range(n):
        key = (index * 0x57c15286 + 0x26ddebda) & 0xffffffff
        source = ((base.number(metadata, lit.usages_base + index * 8) + 0xabbdcabf) & 0xffffffff) ^ key ^ 0x2a357d90
        slot = base.number(metadata, lit.usages_base + index * 8 + 4) ^ key ^ 0x1855aa65
        if source >> 29 != 6 or cache + slot * 8 not in (0x145994388, 0x145A30858):
            continue
        spec = source & 0x1fffffff
        method, cls, instance = struct.unpack_from('<iii', metadata, base.BODY + (base.number(binary, h + 0x170) ^ 0x6347382b) + spec * 12)
        count, pointer = struct.unpack_from('<QQ', binary, pe.offset(resolver.instances + instance * 16))
        arguments = [resolver.resolve_index(resolver.pointer_index(base.number(binary, pe.offset(pointer + j * 8), 'Q'))) for j in range(count)]
        factories.append(dict(usageRow=index, methodSpec=spec, methodDefinition=method, classInstance=cls, arguments=arguments, cell=hex(cache + slot * 8)))
    prior = json.loads(Path('protocol/cn-7.2/avatar-deep-observations.json').read_text('utf8'))
    assignment = next(row for row in prior['methods'] if row['method']['registeredAddress'] == '0x15184a5f0')
    callsites = []
    for role, index, load, call, cell in (('weapon-input-branch', 1, '0x15184a679', '0x15184a683', '0x145994388'),
            ('reliquary-input-branch', 2, '0x15184a6f8', '0x15184a702', '0x145a30858')):
        callsites.append(dict(role=role, sourceParameter=assignment['parameters'][index],
            sourceContract='avatar-contract-observations.json and equip-contract-observations.json',
            sourceCall='0x15184a5f0', methodInfoLoad=load, factoryCall=call,
            binding=next(row for row in factories if row['cell'] == cell),
            scope='Exact typed branch and target-model association; not complete model construction or all uses of the type'))
    return dict(task='F-03d3f4d2-suffix', inputSHA256=base.HASHES, functions=functions, factories=factories, factoryCallsites=callsites,
        concreteAOT=base.selected_aot(binary, metadata, startup, records, {(1222,12365):('TryInsert',), (2147,5917):('Contains',)}),
        enum20060=recover_enums(binary, metadata, records, indices=(20060,))['enums'][0],
        owners=[records[index] for index in (18163, 28604)],
        completeConstructor=False, completeAvatarHandler=False, runtimeStatus='not-tested', targetProtocolReady=False)


def native_probe(binary, metadata, startup, records, cells, static, args):
    directory = Path('protocol/cn-7.2')
    prior_static = json.loads(Path('protocol/cn-7.2/avatar-deep-observations.json').read_text('utf8'))
    declared = base.initializer_declarations(binary, metadata, startup, records, cells, args.python_libs)
    bindings = base.core_aot(binary, metadata, startup, records)
    outer_bindings = base.avatar_aot(binary, metadata, startup, records, dict(types=[]))
    outer = json.loads((directory / 'avatar-outer-observations.json').read_text('utf8'))
    e = base.Offline72(binary, args.python_libs, records, trace_instructions=False)
    g = base.AvatarContractGraph(e, directory, cells, bindings, outer_bindings, outer)
    rt = None
    rows = []
    try:
        list_cases = [('omitted', []), ('two-matches', [7001, 7002]), ('first-match', [7003, 7002, 7001]),
                      ('unmatched', [999]), ('zero', [0]), ('high-id', [0xffffffff])]
        wire = b''.join(base.message(6, base.avatar_wire(1001, 101 + index, [], []) + b''.join(base.scalar(38, value) for value in values))
                        for index, (_, values) in enumerate(list_cases))
        g.run_graph(30509, wire)
        rt = SuffixRuntime(e, g, declared, prior_static, static)
        e.models[0x15184E100] = rt.deliver
        rt.root()
        e.models.pop(0x15184E100, None)
        model, source = rt.delivery[0]
        rt.constructor_fragments(model)
        rt.identity(model, source)
        # The exact six stores run with explicit List capacity and live-ins.
        listing, array = e.alloc(64), rt.array(4, 6)
        e.write(listing + 0x10, array)
        trace = rt.invoke(0x15184DCF9, {0x15184DEDC}, rsi=listing, rax=array, rdx=0)
        values = [e.read(array + 0x20 + index * 4, 'I') for index in range(e.read(listing + 0x18, 'I'))]
        rows.append(dict(name='tag29-cctor-six-store-fragment', values=values,
            staticPointerStored=e.read(base.GLOBAL + 0x2a5c0) == listing, completeStaticConstructor=False, trace=trace))
        dictionary = e.alloc(96)
        e.write(dictionary + 0x38, 3, 'I')
        e.write(dictionary + 0x20, rt.old_array(4, 4))
        e.write(dictionary + 0x18, rt.old_array(16, 4))
        e.write(base.GLOBAL + 0x2a5b8, dictionary)
        # Use exact instruction-derived context, not a guessed same-shape generic.
        function = static['functions']['0x151840eb0']['instructions']
        ins = next(row for row in function if row['address'] == '0x151840fc2')
        if ins['instruction'] != 'mov rax, qword ptr [rip - 0xbe105b9]':
            raise ValueError('Tag29 context reference changed')
        context_cell = 0x151840FC2 + 7 - 0xBE105B9
        context = rt.make_context(context_cell)
        e.write(context + 0x88, e.alloc(64))
        clear_start = len(rt.clears)
        trace = rt.invoke(0x151840EB0, {0x151840FF9}, rcx=0)
        rows.append(dict(name='tag29-helper-clears-before-first-insert', count=e.read(dictionary + 0x38, 'I'),
                         clears=rt.clears[clear_start:], trace=trace))
        for mask in (0, 1, 2, 0x20, 0x3f, 0x40, 0x80000000, 0xffffffff):
            pairs = []
            for index in range(6):
                trace = rt.invoke(0x151840FBE, {0x151840FF9}, rcx=array, rdi=index, r14=mask, rbx=dictionary)
                pairs.append(dict(key=e.reg('edx'), value=bool(e.reg('r8') & 0xff), trace=trace))
            rows.append(dict(name='tag29-mask-' + str(mask), mask=mask, pairs=pairs,
                             insertionExecuted=False, effectsExecuted=False))
        # Two independently authored resource rows grouped under the actual Avatar ID.
        rows_by_group = [(101, 501), (202, 502)]
        resource_list, resource_array = e.alloc(64), rt.array(8, 2)
        e.write(resource_list + 0x10, resource_array)
        e.write(resource_list + 0x18, 2, 'I')
        for index, (group, default) in enumerate(rows_by_group):
            row = e.alloc(64)
            rt.invoke(0x14AEFA090, rcx=row + 0x10, rdx=group)
            rt.invoke(0x14AEFA090, rcx=row + 0x1c, rdx=default)
            e.write(resource_array + 0x20 + index * 8, row)
        primary = e.alloc(96)
        e.write(primary + 0x38, 1, 'I')
        e.write(base.GLOBAL + 0x13b70, primary)
        rt.cache(0x13b78, {1001: resource_list})
        item_resources = {}
        for item, group in ((7001, 202), (7002, 101), (7003, 101), (0xffffffff, 101)):
            row = e.alloc(64)
            rt.invoke(0x14AEFA090, rcx=row + 0x24, rdx=group)
            item_resources[item] = row
        rt.cache(0x1cd98, item_resources)
        # Already initialized metadata/cache fixture; native query body is never a success stub.
        e.write(0x1473673B8 + 7 - 0x17DCBE3, 1, 'B')
        target = e.read(model + 0x70)
        e.write(target + 0x10, rt.array(4, 8))
        for (name, inputs), (_, source) in zip(list_cases, rt.delivery):
            raw_list = e.read(source + 0x60)
            rt.u32_interfaces(raw_list)
            e.write(target + 0x18, 3, 'I')
            begin = len(rt.cache_queries)
            trace = rt.invoke(0x15184FE30, rcx=model, rdx=raw_list)
            output = [e.read(e.read(target + 0x10) + 0x20 + index * 4, 'I') for index in range(e.read(target + 0x18, 'I'))]
            rows.append(dict(name='tag38-' + name, inputs=inputs, output=output, queries=rt.cache_queries[begin:], trace=trace))
        e.write(primary + 0x38, 0, 'I')
        e.write(target + 0x18, 3, 'I')
        trace = rt.invoke(0x15184FE30, rcx=model, rdx=e.read(source + 0x60))
        rows.append(dict(name='tag38-primary-empty-clears-old', count=e.read(target + 0x18, 'I'), trace=trace))
        return rows
    except Exception:
        args.output.with_suffix('.failure.json').write_text(json.dumps(dict(completed=rows, calls=e.calls,
            registers={name: hex(e.reg(name)) for name in ('rip', 'rcx', 'rdx', 'r8', 'r9', 'rsp')}), indent=2) + '\n', 'utf8')
        raise
    finally:
        if rt:
            rt.close()
        g.close()
        e.close()


class SuffixRuntime(base.DeepRuntime):
    def __init__(self, engine, graph, declared, prior_static, static):
        combined = dict(prior_static, functions=prior_static['functions'] | static['functions'])
        super().__init__(engine, graph, declared, combined)
        self.cache_positions = {}
        self.cache_queries = []
        for entry in ENTRIES:
            engine.allow(entry) if entry in engine.ends else engine.allow_unwind(entry)
            engine.models.pop(entry, None)
        for cell in self.deep_classes:
            engine.write(self.deep_classes[cell] + 0xc7, 1, 'B')
        previous = engine.models[0x1578b66f0]
        def lookup():
            pointer, key = engine.reg('rcx'), engine.reg('edx')
            if pointer not in self.cache_positions:
                return previous()
            info = self.cache_positions[pointer]
            self.cache_queries.append(dict(cache=hex(info['offset']), key=key))
            return info['keys'].get(key, 0xffffffff)
        engine.models[0x1578b66f0] = lookup

    def cache(self, offset, values):
        e = self.e
        pointer, array = e.alloc(96), self.array(24, len(values))
        e.write(pointer + 0x18, array)
        e.write(pointer + 0x38, len(values), 'I')
        self.cache_positions[pointer] = dict(offset=offset, keys={key: index for index, key in enumerate(values)})
        for index, value in enumerate(values.values()):
            e.write(array + 0x30 + index * 24, value)
        e.write(base.GLOBAL + offset, pointer)
        return pointer

    def u32_interfaces(self, pointer):
        e = self.e
        klass, table = e.alloc(1024), e.alloc(32)
        e.write(pointer, klass)
        e.write(klass + 0xc0, 2, 'H')
        e.write(klass + 0x80, table)
        # Exact ICollection<uint32>.Count and IList<uint32>.Item interface cells.
        for index, cell in enumerate((0x15184D882 + 7 - 0xBFAB9C9, 0x15184D924 + 7 - 0xBFABA73)):
            interface = e.read(cell)
            e.write(table + index * 16, interface)
            e.write(table + index * 16 + 8, index, 'I')
        e.write(klass + 0xd0, e.model_stub(lambda: e.read(e.reg('rcx') + 0x18, 'I')))
        e.write(klass + 0xd8, e.model_stub(lambda: e.read(e.read(e.reg('rcx') + 0x10) + 0x20 + e.reg('edx') * 4, 'I')))


def compact(value):
    if isinstance(value, list):
        return [compact(item) for item in value]
    if isinstance(value, dict):
        if 'executedAddresses' in value:
            return {key: value[key] for key in ('entry', 'stop', 'completion', 'liveIn', 'instructions', 'nativePathSHA256', 'models')} | {
                'nativeEntries': sorted((set(map(hex, ENTRIES | {0x15184FE30})) | {value['entry']}) & set(value['executedAddresses']))}
        if 'owners' in value:
            value = dict(value, owners=[dict(index=row['index'], name=row['name'],
                fields=[field for field in row['fields'] if field['rawOffset'] in ('0x402a5b8', '0x402a5c0', '0x4013b70', '0x4013b78')]) for row in value['owners']])
        return {key: compact(item) for key, item in value.items() if key != 'instructions' or not isinstance(item, list)}
    return value


if __name__ == '__main__':
    main()
