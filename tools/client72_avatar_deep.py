"""Hash-bound offline Avatar deep investigation. Never operates a live target."""
import argparse
import hashlib
import json
import struct
from pathlib import Path

try:
    from .extract_client72 import HASHES, PE, HEADER_VA, BODY, Names, TypeResolver, number, read_verified, extract
    from .extract_client72_signatures import parameter_start, parameter_words
    from .trace_client72_player_store import type_cells
    from .trace_client72_avatar_signed_trees import selected_aot
    from .trace_client72_token import load_emulator
    from .client72_avatar_initializer import EVIDENCE_SHA as INITIALIZER_INPUTS, initializer_declarations, InitializerRuntime
    from .client72_avatar_contract import AvatarContractGraph, avatar_wire, RETURN
    from .trace_client72_avatar_core import core_aot
    from .trace_client72_avatar_outer import avatar_aot
    from .client72_graph_fixtures import message, scalar
    from .client72_emulator import Offline72, GLOBAL, STACK
except ImportError:
    from extract_client72 import HASHES, PE, HEADER_VA, BODY, Names, TypeResolver, number, read_verified, extract
    from extract_client72_signatures import parameter_start, parameter_words
    from trace_client72_player_store import type_cells
    from trace_client72_avatar_signed_trees import selected_aot
    from trace_client72_token import load_emulator
    from client72_avatar_initializer import EVIDENCE_SHA as INITIALIZER_INPUTS, initializer_declarations, InitializerRuntime
    from client72_avatar_contract import AvatarContractGraph, avatar_wire, RETURN
    from trace_client72_avatar_core import core_aot
    from trace_client72_avatar_outer import avatar_aot
    from client72_graph_fixtures import message, scalar
    from client72_emulator import Offline72, GLOBAL, STACK

ENTRIES = {0x15183F290, 0x15184A5F0, 0x151847F70, 0x15184E100,
           0x15184FC80, 0x15184FE30, 0x14D4480C0, 0x14A42BD90,
           0x15183FE60, 0x156FBC020, 0x1570F5F20, 0x155B229F0,
           0x14C0D6BF0, 0x157D7FE90, 0x157D97690, 0x151843490,
           0x151843970, 0x151843C80, 0x14AD07E80, 0x151845920,
           0x15184EEE0, 0x1535BE1A0, 0x1535BE1D0, 0x1535BE1E0,
           0x1535BE420, 0x1535BE120, 0x1535BDC20}
EVIDENCE_SHA = INITIALIZER_INPUTS | {
    'avatar-initializer-observations.json': '1a0889afe94d0bd815a2ff99604fb4c6d48bbade5f49e0d5a8995071ad92acaf'}


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
            raise ValueError('Reviewed Avatar source changed: ' + name)
    binary, metadata, startup = [read_verified(args.client / name, digest) for name, digest in HASHES.items()]
    records, extraction = extract(binary, metadata, startup=startup, structures=True)
    cells = type_cells(binary, metadata, startup, records)
    report = inspect(binary, metadata, startup, records, extraction, cells, args.python_libs)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.with_suffix('.static.json').write_text(json.dumps(report, indent=2) + '\n', 'utf8')
    if args.native:
        report['native'] = native_probe(binary, metadata, startup, records, cells, report, args)
        report['evidenceKind'] = 'static-and-bounded-offline-native'
    report['inputContracts'] = EVIDENCE_SHA
    root = Path(__file__).resolve().parents[1]
    report['sourceSHA256'] = {name: hashlib.sha256((root / name).read_bytes()).hexdigest() for name in (
        'tools/client72_avatar_deep.py', 'tools/client72_avatar_initializer.py', 'tools/client72_avatar_contract.py',
        'tools/client72_avatar_state.py', 'tools/client72_emulator.py', 'tools/client72_session_runtime.py',
        'tools/trace_client72_avatar_core.py', 'tools/trace_client72_avatar_outer.py',
        'tools/trace_client72_avatar_signed_trees.py', 'tools/extract_client72.py', 'tools/extract_client72_signatures.py')}
    report['models'] = [
        'Inherited f1/f4d1 managed reader, allocation, containers and reversible codec tables remain explicit fixtures.',
        'Pool rent allocates a type-checked synthetic Dictionary<uint,uint>, List<uint>, or type61980; dirty61980 payload is supplied explicitly. Pool internals are not executed.',
        'Array.Clear is inherited controlled byte storage; native code chooses ranges/counts.',
        'Relic assignment starts after factory with explicit live-in equipment/inventory objects and synthetic encoded resource/state values; factory and final slot lookup are not executed.',
        'Depot cache key-to-index lookup is a controlled dictionary model. Native row selection and integer decoding/encoding execute; TryInsert and later skill effects do not.',
        'Initializer gaps are never patched with success; native probes do not establish complete constructor/handler or live runtime behavior.']
    args.output.write_text(json.dumps(report, indent=2) + '\n', 'utf8')
    lines = []
    for entry, function in report['functions'].items():
        lines.append('ENTRY ' + entry)
        lines.extend(row['address'] + ': ' + row['instruction'] for row in function['instructions'])
    args.output.with_suffix('.asm.txt').write_text('\n'.join(lines) + '\n', 'utf8')
    if args.observations:
        args.observations.parent.mkdir(parents=True, exist_ok=True)
        args.observations.write_text(json.dumps(compact(report), indent=2) + '\n', 'utf8')
    print(json.dumps(dict(functions=len(report['functions']), aot=len(report['concreteAOT']), nativeExecuted=args.native)))


def inspect(binary, metadata, startup, records, extraction, cells, libraries):
    load_emulator(libraries)
    from capstone import Cs, CS_ARCH_X86, CS_MODE_64
    decoder = Cs(CS_ARCH_X86, CS_MODE_64)
    decoder.detail = True
    pe = PE(binary)
    h = pe.offset(HEADER_VA)
    resolver = TypeResolver(pe, startup, records, h)
    names = Names(metadata, int(extraction['stringBase'], 16))
    method_base = BODY + (number(binary, h + 0xB0) ^ 0x51324A1F)
    parameter_base = BODY + ((number(binary, h + 0x174) + 0xAE6BCD68) & 0xffffffff)
    owners = {18163, 34054, 69137, 1255, 2147, 2034, 61980, 67861, 88602, 22284}
    methods = []
    entries = set(ENTRIES)
    selections = {(2147, 5917): ('.ctor',), (2034, 12373): ('.ctor',), (69137, 5278): ('.ctor',), (1255, 5278): ('.ctor',)}
    for owner in sorted(owners):
        if owner == 61980:
            entries.update(int(method['registeredAddress'], 16) for method in records[owner]['methods'] if method['name'] in ('.ctor', '.cctor'))
        if owner == 67861:
            entries.update(int(method['registeredAddress'], 16) for method in records[owner]['methods'] if method['virtualSlot'] in (9, 11))
        for method in records[owner]['methods']:
            if int(method['registeredAddress'], 16) not in entries and method['name'] != '.ctor':
                continue
            first = parameter_start(method['index'], number(metadata, method_base + method['index'] * 26 + 4))
            parameters = []
            for i in range(first, first + method['parameterCount']):
                name, typ = parameter_words(i, number(metadata, parameter_base + i * 8), number(metadata, parameter_base + i * 8 + 4))
                parameters.append(dict(name=names.decode(name), type=resolver.resolve_index(typ)))
            methods.append(dict(owner=owner, method=method, parameters=parameters))
    aot = selected_aot(binary, metadata, startup, records, selections)
    entries.update(int(row['address'], 16) for row in aot)
    optional = number(binary, 0x3C) + 24
    rva, size = struct.unpack_from('<II', binary, optional + 112 + 3 * 8)
    ranges = {pe.base + begin: pe.base + end for begin, end, _ in struct.iter_unpack('<III', binary[pe.offset(pe.base + rva):pe.offset(pe.base + rva) + size])}
    starts = sorted({int(method['registeredAddress'], 16) for record in records for method in record['methods'] if method['registeredAddress'] != '0x0'})
    functions = {}
    for entry in sorted(entries):
        if not entry:
            continue
        end = ranges.get(entry) or next(address for address in starts if address > entry)
        raw = binary[pe.offset(entry):pe.offset(entry) + end - entry]
        instructions = []
        references = []
        calls = []
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
    return dict(schemaVersion=1, task='F-03d3f4d2', inputSHA256=HASHES, evidenceKind='static',
                functions=functions, concreteAOT=aot, methods=methods, owners=[records[i] for i in sorted(owners)],
                completeConstructor=False, completeAvatarHandler=False, runtimeStatus='not-tested', targetProtocolReady=False)


class DeepRuntime(InitializerRuntime):
    """Own controlled storage and stop boundaries; no edits to shared runtime."""
    def __init__(self, engine, graph, declared, static):
        super().__init__(engine, graph, declared)
        self.static = static
        self.pool_requests = []
        self.pool_dirty = None
        self.deep_classes = {}
        for function in static['functions'].values():
            for row in function['types']:
                cell = int(row['cell'], 16)
                pointer = engine.read(cell)
                if pointer not in self.classes:
                    pointer = engine.synthetic_type(cell)
                    self.classes[pointer] = row['type']
                self.deep_classes[cell] = pointer
        for entry in ENTRIES | {0x1535BDC10}:
            engine.allow(entry) if entry in engine.ends else engine.allow_unwind(entry)
            engine.models.pop(entry, None)
        pool_type = self.deep_classes[0x1458C8600]
        for slot, address in ((9, 0x1495C9D00), (11, 0x1495C9D60)):
            engine.write(pool_type + 0xd0 + slot * 8, address)
            engine.allow(address)
        engine.write(pool_type + 0xbe, 0, 'H')
        pool_class = engine.read(self.pool)
        engine.write(pool_class + 0xf0, engine.model_stub(self.rent_deep))

    def rent_deep(self):
        e = self.e
        klass = e.reg('rdx')
        typ = self.classes.get(klass)
        if typ is None:
            raise ValueError('Unbound deep pool type')
        pointer = e.alloc(512)
        e.write(pointer, klass)
        if typ['typeDefinition'] == 1255:
            e.write(pointer + 0x10, self.empty_array())
        elif typ['typeDefinition'] not in (1222, 61980):
            raise ValueError('Unimplemented deep pool type')
        if typ['typeDefinition'] == 61980 and self.pool_dirty:
            e.uc.mem_write(pointer + 0x18, self.pool_dirty)
        self.pool_requests.append(dict(type=typ, pointer=pointer))
        return pointer


def native_probe(binary, metadata, startup, records, cells, static, args):
    directory = Path('protocol/cn-7.2')
    declared = initializer_declarations(binary, metadata, startup, records, cells, args.python_libs)
    bindings = core_aot(binary, metadata, startup, records)
    outer_bindings = avatar_aot(binary, metadata, startup, records, dict(types=[]))
    outer = json.loads((directory / 'avatar-outer-observations.json').read_text('utf8'))
    e = Offline72(binary, args.python_libs, records, trace_instructions=False)
    g = AvatarContractGraph(e, directory, cells, bindings, outer_bindings, outer)
    rt = None
    rows = []
    try:
        tag13_inputs = [('omitted', b''), ('explicit-zero', scalar(13, 0)), ('nonzero', scalar(13, 42)),
                        ('max', scalar(13, 0xffffffff)), ('last-zero', scalar(13, 42) + scalar(13, 0))]
        wire = message(6, avatar_wire(1001, 0x8000000000000001, [], []))
        wire += b''.join(message(6, avatar_wire(1001, 100 + index, [], []) + encoded) for index, (_, encoded) in enumerate(tag13_inputs))
        g.run_graph(30509, wire)
        rt = DeepRuntime(e, g, declared, static)
        e.models[0x15184E100] = rt.deliver
        rt.root()
        e.models.pop(0x15184E100, None)
        model, source = rt.delivery[0]
        tag13_read = {name: e.read(pointer + 0xc0, 'I') for (name, _), (_, pointer) in zip(tag13_inputs, rt.delivery[1:])}
        rt.constructor_fragments(model)
        rt.identity(model, source)
        # Constructor is a genuine no-op, independently separated from zero allocation.
        payload = bytes.fromhex('0100c03f7856341201000080fffffffffeffffff')
        for dirty in (False, True):
            target = e.alloc(64)
            e.uc.mem_write(target + 0x18, payload if dirty else bytes(20))
            trace = rt.invoke(0x1535BDC10, rcx=target)
            rows.append(dict(name='61980-ctor-' + ('dirty' if dirty else 'fresh'), before=(payload if dirty else bytes(20)).hex(),
                             after=bytes(e.uc.mem_read(target + 0x18, 20)).hex(), trace=trace))
        for header in (0, 1, 2):
            target = e.alloc(64)
            e.write(target + 0x14, header, 'I')
            e.uc.mem_write(target + 0x18, payload)
            stops = {RETURN, 0x14A42BDBE}
            trace = rt.invoke(0x1535BE1A0, stops, rcx=target)
            rows.append(dict(name='61980-reset-header-' + str(header), before=payload.hex(),
                             after=bytes(e.uc.mem_read(target + 0x18, 20)).hex(), trace=trace))
        for name, incoming in (('nonzero', payload), ('zero', bytes(20))):
            target, source61980 = e.alloc(64), e.alloc(64)
            e.uc.mem_write(target + 0x18, bytes([0x7d]) * 20)
            e.uc.mem_write(source61980 + 0x18, incoming)
            trace = rt.invoke(0x1535BE120, rcx=target, rdx=source61980)
            rows.append(dict(name='61980-copy-' + name, incoming=incoming.hex(), after=bytes(e.uc.mem_read(target + 0x18, 20)).hex(), trace=trace))
        for dirty in (False, True):
            rt.pool_dirty = payload if dirty else None
            begin = len(rt.pool_requests)
            trace = rt.invoke(0x15184E3FB, {0x15184E42D}, rdi=model, rbx=source)
            target = e.read(model + 0xa8)
            rows.append(dict(name='61980-handler-pool-' + ('dirty' if dirty else 'fresh'),
                             incoming=(payload if dirty else bytes(20)).hex(), after=bytes(e.uc.mem_read(target + 0x18, 20)).hex(),
                             header11=e.read(target + 0x11, 'B'), header14=e.read(target + 0x14, 'I'),
                             poolRequests=rt.pool_requests[begin:], trace=trace))
        # Full empty-equipment refresh prefix, including its true pool requests and clearing.
        for old in (False, True):
            listing = e.read(model + 0x1f0)
            if old:
                e.write(listing + 0x10, rt.old_array(8, 4))
                e.write(listing + 0x18, 3, 'I')
            begin = len(rt.pool_requests)
            trace = rt.invoke(0x15183F290, {0x151843490}, rcx=model)
            rows.append(dict(name='empty-equipment-refresh-' + ('old' if old else 'fresh'), clearedCount=e.read(listing + 0x18, 'I'),
                             poolRequests=rt.pool_requests[begin:], trace=trace))
        # The real relic-assignment body after a declared successful model factory live-in.
        for level, encoded_value in ((42, 73), (0, 0), (0xffffffff, 0x80000001)):
            item = e.alloc(128)
            e.write(item + 0x10, 20001, 'I')
            e.write(item + 0x18, 0xffffffffffffffff)
            state, wrapper, resource = e.alloc(128), e.alloc(64), e.alloc(128)
            e.write(state + 0x40, level, 'I')
            e.write(item + 0x48, state)
            e.write(item + 0x30, wrapper)
            e.write(wrapper + 0x10, resource)
            rt.invoke(0x14AEFA090, rcx=resource + 0x2c, rdx=encoded_value)
            equipment, output = e.alloc(256), e.alloc(16)
            trace = rt.invoke(0x15184A710, {0x151845920}, rax=equipment, rsi=item, rbx=1,
                              r14=model, r15=output)
            level_trace = rt.invoke(0x14B1049F0, rcx=equipment + 0x50)
            decoded_level = e.reg('eax')
            value_trace = rt.invoke(0x14B1049F0, rcx=equipment + 0x78)
            rows.append(dict(name='relic-assignment-' + str(level), declaredLiveIn='15184a710 after factory; not a complete function',
                             itemId=e.read(equipment + 0x70, 'I'), slot=e.read(equipment + 0x48, 'I'),
                             inventoryPointerStored=e.read(equipment + 0x40) == item, ownerGuid=e.read(equipment + 0x88),
                             outputGuid=e.read(output), decodedLevel=decoded_level, decodedValue=e.reg('eax'),
                             inputLevel=level, inputValue=encoded_value, trace=trace, decodes=[level_trace, value_trace]))
        # Initial skill dictionaries are cleared before any later list/skill effects.
        for old in (False, True):
            for offset in (0x1f8, 0x200):
                dictionary = e.read(model + offset)
                e.write(dictionary + 0x38, 3 if old else 0, 'I')
                if old:
                    e.write(dictionary + 0x10, rt.old_array(40, 4))
                    e.write(dictionary + 0x18, rt.old_array(4, 4))
            begin = len(rt.clears)
            trace = rt.invoke(0x151847F70, {0x151848096}, rcx=model, rdx=e.read(source + 0x90),
                              r8=e.read(source + 0x68), r9=e.read(source + 0x80))
            rows.append(dict(name='skill-dictionaries-clear-' + ('old' if old else 'fresh'),
                             counts=[e.read(e.read(model + offset) + 0x38, 'I') for offset in (0x1f8, 0x200)],
                             clears=rt.clears[begin:], trace=trace))
        # Exact tail: target cache lookup and real encoding, stopping before unexecuted TryInsert.
        cache, entries, depot = e.alloc(96), rt.array(24, 1), e.alloc(160)
        e.write(cache + 0x18, entries)
        e.write(entries + 0x30, depot)
        e.write(GLOBAL + 0x212b8, cache)
        previous_lookup = e.models[0x1578b66f0]
        cache_queries = []
        def lookup_depot():
            if e.reg('rcx') != cache:
                return previous_lookup()
            key = e.reg('edx')
            cache_queries.append(key)
            return 0 if key == 0x80000001 else 0xffffffff
        e.models[0x1578b66f0] = lookup_depot
        frame = STACK + 0x180000
        for name, key68, field_name, requested in (
                ('nonzero', 701, 'nonzero', 0x80000001), ('omitted', 701, 'omitted', 0x80000001),
                ('explicit-zero', 701, 'explicit-zero', 0x80000001), ('last-zero', 701, 'last-zero', 0x80000001),
                ('zero-key', 0, 'nonzero', 0x80000001), ('max-values', 0xffffffff, 'max', 0x80000001),
                ('missing-depot', 701, 'nonzero', 501)):
            value = tag13_read[field_name]
            rt.invoke(0x14AEFA090, rcx=depot + 0x68, rdx=key68)
            rt.invoke(0x14AEFA090, rcx=depot + 0x6c, rdx=123456789)
            e.write(frame + 0x1b0, requested, 'I')
            e.write(frame + 0x1b8, value, 'I')
            begin = len(cache_queries)
            trace = rt.invoke(0x151849380, {0x15184946B, 0x15184949C}, rbx=model, rbp=frame)
            encoded = None
            if trace['stop'] == '0x15184946b':
                key_pointer, value_pointer = e.reg('rdx'), e.reg('r8')
                key_decode = rt.invoke(0x14B1049F0, rcx=key_pointer)
                decoded_key = e.reg('eax')
                value_decode = rt.invoke(0x14B1049F0, rcx=value_pointer)
                encoded = dict(key=decoded_key, value=e.reg('eax'), decodes=[key_decode, value_decode])
            rows.append(dict(name='depot68-tag13-tail-' + name, inputKey68=key68, ignoredKey6c=123456789,
                             inputTag13=value, query=requested, queries=cache_queries[begin:], encodedArguments=encoded,
                             dictionaryInsertionExecuted=False, trace=trace))
        return rows
    except Exception:
        args.output.with_suffix('.failure.json').write_text(json.dumps(dict(completed=rows, calls=e.calls,
            registers={name:hex(e.reg(name)) for name in ('rip', 'rcx', 'rdx', 'r8', 'r9', 'rsp')}), indent=2) + '\n', 'utf8')
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
                'nativeEntries': sorted((set(map(hex, ENTRIES | {0x1535BDC10, 0x1495C9D00, 0x1495C9D60})) | {value['entry']}) & set(value['executedAddresses']))}
        if 'owners' in value:
            value = dict(value, owners=[dict(index=row['index'], name=row['name'], parent=row.get('parent'),
                fields=row['fields']) for row in value['owners'] if row['index'] in (18163, 34054, 69137, 61980)])
        return {key: compact(item) for key, item in value.items() if key != 'instructions' or not isinstance(item, list)}
    return value


if __name__ == '__main__':
    main()
