"""Hash-bound offline cold interfaces, Type[] cache and reflection contract."""
import argparse
import hashlib
import json
import struct
from collections import Counter
from pathlib import Path

try:
    from .client72_avatar_cold_contract import base, ColdRuntime, RETURN
    from .client72_avatar_cold_completion import audit_sample
    from .client72_emulator import STACK
    from .client72_session_runtime import fragment
    from .client72_seed_crypto import import_slots
except ImportError:
    from client72_avatar_cold_contract import base, ColdRuntime, RETURN
    from client72_avatar_cold_completion import audit_sample
    from client72_emulator import STACK
    from client72_session_runtime import fragment
    from client72_seed_crypto import import_slots

FIVE_INPUTS = base.HASHES | {
    'YuanShen_Data/Plugins/AccountPlatNative.dll': 'adecdf0bd5442e321b1b66b0ca9e98bef76d5067326aa4add5b75266055a6c25',
    'YuanShen_Data/Plugins/Astrolabe.dll': '87e0a005ac46515156636af2120d3f824bcd28d69253e7d7235b959b54a0b442',
}
SAMPLER = 'local/migration72/d02-active/plain/source-snapshots/sample_client72_initialization-v3.py'
SAMPLER_SHA = '2dfc86a6bd9f99ae72cd380c1315ba6d5f6d3968ff892a5eb7c089f4c1fc65ab'
NATIVE = (0x1404DD170, 0x1404E9E60, 0x1404E48C0, 0x140517680, 0x140538230,
          0x14053C1C0, 0x1404F0740, 0x1404F0820, 0x159B6E5B0, 0x159B6EC60,
          0x159B6EB50, 0x14051B7B0, 0x14051F280)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ('client', 'output'):
        parser.add_argument('--' + name, type=Path, required=True)
    parser.add_argument('--sample', type=Path, default=Path('local/migration72/d02-active/plain/initialization-provider-elevated4.json'))
    parser.add_argument('--python-libs', type=Path, default=Path('local/migration72/python'))
    args = parser.parse_args()
    if args.output.resolve().is_relative_to(args.client.resolve()):
        parser.error('Output must be outside client')
    verified = [base.read_verified(args.client / n, h) for n, h in FIVE_INPUTS.items()]
    b, m, s = verified[:3]
    base.read_verified(Path(SAMPLER), SAMPLER_SHA)
    sample, data = audit_sample(args.sample)
    records, _ = base.extract(b, m, startup=s, structures=True)
    declared = declarations(b, m, s, records, args.python_libs)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.with_suffix('.static.json').write_text(json.dumps(declared, indent=2) + '\n', 'utf8')
    runtime = InterfaceRuntime(b, m, records, declared, data, args.python_libs)
    try:
        results = run_cases(runtime)
    except Exception:
        e = runtime.e
        args.output.with_suffix('.failure.json').write_text(json.dumps(dict(
            registers={k: hex(e.reg(k)) for k in ('rip', 'rcx', 'rdx', 'r8', 'rax', 'rsp')},
            calls=runtime.order, models=e.calls, allocations=runtime.allocations), indent=2) + '\n', 'utf8')
        raise
    finally:
        runtime.e.close()
    report = dict(task='D02-R1-cold-interfaces', inputSHA256=FIVE_INPUTS, snapshot=sample,
        samplerSourceSHA256=SAMPLER_SHA, declarations=declared, native=results,
        classPreparation=runtime.preparation, completeColdComparer=False,
        completeGetInterfacesUnderDeclaredModels=True, completeHasIEquatableUnderDeclaredModels=True,
        firstColdBlocker=dict(entry='0x1404e9e60', stop='0x1404ea15f',
            scope='First unresolved cold continuation of the declared interface-provider path, not an observed whole natural constructor.',
            reason='Type[] array-class cache is not naturally observed; its miss branch first needs initialized System.Array base class state.',
            followingConditionalBlocker='Generic comparer factory reaches Type314 class initialization lock; generic construction and default write remain unexecuted.'),
        naturalInterfacesObserved=False, completeAvatarHandler=False, targetProtocolReady=False,
        modelCatalog={hex(address): getattr(fn, '__name__', type(fn).__name__) for address, fn in runtime.e.models.items()
                      if hex(address) in {model for row in results for model in row['trace']['models']}},
        sourceSHA256={n: hashlib.sha256(Path(n).read_bytes()).hexdigest() for n in (
            'tools/client72_avatar_cold_interfaces.py', 'tools/client72_avatar_cold_completion.py',
            'tools/client72_avatar_cold_contract.py', 'tools/client72_emulator.py',
            'tools/client72_session_runtime.py', 'tools/extract_client72.py', 'tools/client72_seed_crypto.py')},
        limitations=['Static declarations and actual producer fragments supply prepared runtime class/interface storage, not live cache readiness.',
            'Type[] class cache and interface RuntimeType cache are explicitly populated typed storage inputs.',
            'Only typed allocation/free and independently verified imported lock operations may be modeled.',
            'Natural HashSet getter/constructor RGCTX slots remain zero; actual defaultComparer static field was not read.',
            'Full comparer generic construction/default write and actual natural Avatar handler remain unproved.'])
    args.output.write_text(json.dumps(report, indent=2) + '\n', 'utf8')
    print(json.dumps(dict(cases=len(results), completeColdComparer=False)))


def declarations(binary, metadata, startup, records, libraries):
    base.load_emulator(libraries)
    from capstone import Cs, CS_ARCH_X86, CS_MODE_64
    pe = base.PE(binary)
    header = pe.offset(base.HEADER_VA)
    resolver = base.TypeResolver(pe, startup, records, header)
    cells = base.type_cells(binary, metadata, startup, records)
    array = cells[0x145886658]
    if array['type']['kind'] != 'szarray' or array['type']['elementType']['typeDefinition'] != 314:
        raise ValueError('Exact Type[] class usage changed')
    type_table = base.BODY + ((base.number(binary, header + 0x64) + 0xa7932555) & 0xffffffff)
    interface_table = base.BODY + (base.number(binary, header + 0xc8) ^ 0x1d590619)
    rows, index = [], 20060
    while index is not None:
        owner = records[index]
        entry = type_table + index * 70
        raw = metadata[entry:entry + 70]
        count = raw[0x43] ^ 0x25
        first = (base.number(raw, 0x36, 'H') + 0x6f6c) & 0xffff
        refs = [base.number(metadata, interface_table + (first + i) * 4) for i in range(count)]
        rows.append(dict(type=index, name=owner['name'], parent=owner['parent'], interfaceCount=count,
                         interfaceStart=first, interfaceReferences=refs,
                         interfaces=[resolver.resolve_index(i) for i in refs],
                         entryOffset=entry, entrySHA256=hashlib.sha256(raw).hexdigest()))
        index = owner['parent']['typeDefinition'] if owner['parent'] else None
    if [r['type'] for r in rows] != [20060, 204, 405, 371] or [r['interfaceCount'] for r in rows] != [0, 3, 0, 0]:
        raise ValueError('Enum inheritance/interface declaration changed')
    methods = {m['virtualSlot']: m for m in records[284]['methods'] if m['virtualSlot'] in (21, 82, 106, 107)}
    if methods[82]['registeredAddress'] != '0x159b6ec60':
        raise ValueError('RuntimeType IsGenericType virtual implementation changed')
    runtime_parents, index = [], 284
    while index is not None:
        owner = records[index]
        runtime_parents.append(dict(type=index, parent=owner['parent']))
        index = owner['parent']['typeDefinition'] if owner['parent'] else None
    optional = base.number(binary, 0x3c) + 24
    rva, size = struct.unpack_from('<II', binary, optional + 112 + 3 * 8)
    start = pe.offset(pe.base + rva)
    extents = {pe.base + a: pe.base + z for a, z, _ in struct.iter_unpack('<III', binary[start:start + size])}
    windows = {a: extents[a] for a in NATIVE}
    windows.update({0x15a4d6f4e: 0x15a4d6ff1, 0x15a4d7291: 0x15a4d72e2,
                    0x140542d34: 0x140542d55, 0x140542c0b: 0x140542c2c})
    functions, decoder = {}, Cs(CS_ARCH_X86, CS_MODE_64)
    for address, end in windows.items():
        code = binary[pe.offset(address):pe.offset(address) + end - address]
        functions[hex(address)] = dict(end=hex(end), sha256=hashlib.sha256(code).hexdigest(),
            instructions=[dict(address=hex(i.address), instruction=i.mnemonic + ' ' + i.op_str) for i in decoder.disasm(code, address)])
    namespace = 0x140542d3b + 7 + 0x3563329
    name = 0x140542d42 + 7 + 0x356c213
    strings = [binary[pe.offset(a):pe.offset(a) + 80].split(b'\0', 1)[0].decode('ascii') for a in (namespace, name)]
    if strings != ['System', 'Type']:
        raise ValueError('Type default-class producer changed')
    array_names = [binary[pe.offset(a):pe.offset(a) + 80].split(b'\0', 1)[0].decode('ascii')
                   for a in (0x140542c12 + 7 + 0x3563452, 0x140542c19 + 7 + 0x355094f)]
    if array_names != ['System', 'Array']:
        raise ValueError('Array default-class producer changed')
    locks = import_slots(pe)
    return dict(typeTableOffset=type_table, interfaceTableOffset=interface_table, parentChain=rows,
                typeArrayCell=array, runtimeTypeCell=cells[0x1458869b8], runtimeTypeMethods=methods,
                runtimeTypeParents=runtime_parents,
                defaultTypeProducer=dict(cell='0x145bc97a8', namespace='System', name='Type', type=314,
                                         entry='0x140542d34', store='0x140542d4e'),
                defaultArrayProducer=dict(cell='0x145bc9868', namespace='System', name='Array', type=117,
                                          entry='0x140542c0b', store='0x140542c25'),
                referenceBase=hex(resolver.references),
                locks={n: hex(locks[n]) for n in ('AcquireSRWLockShared', 'ReleaseSRWLockShared')}, functions=functions)


class InterfaceRuntime:
    def __init__(self, binary, metadata, records, declared, natural, libraries):
        e = self.e = base.Offline72(binary, libraries, records, trace_instructions=True)
        self.declared, self.order, self.allocations, self.frees, self.lock_events, self.preparation = declared, [], {}, [], [], []
        self.lock_held = None
        self.pool_allocations = {}
        e.uc.mem_map(RETURN & ~4095, 4096)
        e.uc.mem_write(RETURN, b'\x90')
        e.prepare()
        for address in NATIVE:
            e.allow_unwind(address)
        e.allowed.extend(((0x1405269f0, 0x1405269f5), (0x15a4d6f4e, 0x15a4d6ff1), (0x15a4d7291, 0x15a4d72e2)))
        starts = set(e.unwind_ranges) | {0x1405269f0, 0x141a2e150, 0x141cbfe48}
        def observe(uc, address, size, context):
            if address in starts or address in e.models:
                self.order.append(dict(entry=hex(address), rcx=hex(e.reg('rcx')), rdx=hex(e.reg('rdx')), r8=hex(e.reg('r8'))))
        e.uc.hook_add(4, observe)  # UC_HOOK_CODE; extra observer never changes CPU state.
        self.reflection_lock, self.array_lock = e.alloc(8), e.alloc(8)
        e.write(0x145bc9028, self.reflection_lock)
        e.write(0x145bc7218, self.array_lock)
        e.write(0x145699598, 0, 'I')
        e.write(0x143bac0f8, e.model_stub(self.acquire))
        e.write(0x143bac708, e.model_stub(self.release))
        e.models[0x141a2e150] = self.allocate_node
        e.models[0x141cbfe48] = self.free_node
        e.models[0x140514ee0] = self.allocate_array
        e.models[0x1405091d0] = self.allocate_hierarchy
        e.models[0x141fdf9c0] = self.memset
        e.models[0x141fdf310] = self.memcpy
        ids = (20060, 204, 405, 371, 241, 248, 243, 314, 284, 531, 513)
        self.classes = {i: e.alloc(1024) for i in ids}
        self.compact_base = min(self.classes.values()) - 1024
        registry = e.alloc(8)
        e.write(registry, self.compact_base)
        e.write(0x145bc8ad0, registry)
        self.class_cache = e.alloc(20061 * 8)
        e.write(0x145bc8f18, self.class_cache)
        refs = {20060: 48169, 204: 158332, 405: 430405, 371: 1, 241: 60193, 248: 444914, 243: 444915, 284: 419}
        for row in declared['runtimeTypeParents']:
            if row['parent']:
                refs[row['parent']['typeDefinition']] = row['parent']['typeIndex']
        reference_base = int(declared['referenceBase'], 16)
        for index, klass in self.classes.items():
            e.uc.mem_write(klass, b'\xff' * 0xd0)
            meta = e.alloc(70)
            raw = metadata[declared['typeTableOffset'] + index * 70:declared['typeTableOffset'] + (index + 1) * 70]
            e.uc.mem_write(meta, raw)
            clear = self.invoke(0x15a4d6f4e, {0x15a4d6ff1}, r13=klass)
            cleared = bytes(e.uc.mem_read(klass, 0xd0)) == bytes(0xd0)
            flag = self.invoke(0x15a4d7291, {0x15a4d72e2}, r13=klass, r15=meta, r8=0, r10=0)
            pointer = 0x142e62360 if index == 314 else reference_base + refs[index] * 16
            descriptor = binary[e.pe.offset(pointer):e.pe.offset(pointer) + 16]
            e.uc.mem_write(klass + 0x10, descriptor)
            e.write(klass + 0x58, meta)
            e.write(self.class_cache + index * 8, klass)
            self.preparation.append(dict(type=index, descriptorHex=descriptor.hex(), metadataEntrySHA256=hashlib.sha256(raw).hexdigest(),
                genericContainerEncoded=hex(base.number(raw, 0x3e, 'H')), clearAllD0BytesZero=cleared,
                genericInstPointer=e.read(klass + 0x68), genericDefinitionFlag=bool(e.read(klass + 0xc9, 'B') & 32), clear=clear, flag=flag))
        # Actual provider4 fields replace only their corresponding static fixture fields.
        for index, prefix in ((20060, 'enumClassCache.enum'), (284, 'runtimeTypeCache.objectClass')):
            e.uc.mem_write(self.classes[index] + 0x10, natural[prefix + '.type'])
            e.uc.mem_write(self.classes[index] + 0xc8, natural[prefix + '.initialized'])
        e.uc.mem_write(self.classes[20060] + 0xca, natural['enumClassCache.enum.flagsCA'])
        for row in declared['parentChain']:
            klass = self.classes[row['type']]
            array = e.alloc(max(8, row['interfaceCount'] * 8))
            e.write(klass + 0x88, array)
            for j, interface in enumerate(row['interfaces']):
                e.write(array + j * 8, self.classes[interface['typeDefinition']])
            parent = row['parent']
            e.write(klass + 0xb0, self.classes[parent['typeDefinition']] - self.compact_base if parent else 0, 'I')
        for row in declared['runtimeTypeParents']:
            parent = row['parent']
            e.write(self.classes[row['type']] + 0xb0, self.classes[parent['typeDefinition']] - self.compact_base if parent else 0, 'I')
        e.write(self.classes[284] + 0x360, 0x159b6ec60)
        self.type_array_class = e.alloc(512)
        array_descriptor = reference_base + declared['typeArrayCell']['sourceIndex'] * 16
        e.uc.mem_write(self.type_array_class + 0x10, binary[e.pe.offset(array_descriptor):e.pe.offset(array_descriptor) + 16])
        e.write(self.type_array_class + 0xac, self.classes[314] - self.compact_base, 'I')
        e.write(0x145886658, self.type_array_class)
        e.write(0x145bc97a8, self.classes[314])
        self.array_block, self.array_entries = e.alloc(32), e.alloc(16)
        self.array_hash = ((18 * 0x1cfaa2db) * 0x1cfaa2db + 314) & 0xffffffffffffffff
        e.write(0x145bc7160, 63)
        e.write(0x145bc7180, self.array_block)
        e.write(0x145bc7188, 64)
        e.write(self.array_block, self.array_entries)
        e.write(self.array_block + 8, 1 << (self.array_hash & 63))
        e.write(self.array_entries, self.classes[314])
        e.write(self.array_entries + 8, self.type_array_class)
        self.reflections = {}
        for index in (20060, 241, 248, 243):
            obj = e.alloc(32)
            e.write(obj, self.classes[284])
            e.write(obj + 0x10, self.classes[index] + 0x10)
            self.reflections[index] = obj
        # Reuse only the native sparse-map storage builder; restore our two-lock model.
        helper = ColdRuntime(e)
        self.reflection_cache = helper.cache([(self.classes[i] + 0x10, obj) for i, obj in self.reflections.items()])
        e.write(0x145bc9068, self.reflection_cache)
        e.write(0x145bc9028, self.reflection_lock)
        e.write(0x143bac0f8, e.model_stub(self.acquire))
        e.write(0x143bac708, e.model_stub(self.release))
        self.last_array = None

    def allocate_hierarchy(self):
        e = self.e
        size = e.reg('rdx')
        if e.read(e.reg('rsp')) != 0x14051f2ff or size not in (8, 16, 24, 32, 40):
            raise ValueError('Only bounded native hierarchy allocation allowed')
        pointer = e.alloc(size)
        self.pool_allocations[pointer] = size
        return pointer

    def memset(self):
        e = self.e
        pointer, value, size = e.reg('rcx'), e.reg('edx'), e.reg('r8')
        if self.pool_allocations.get(pointer) != size or value != 0:
            raise ValueError('Unexpected hierarchy clear')
        e.uc.mem_write(pointer, bytes(size))
        return pointer

    def memcpy(self):
        e = self.e
        target, source, size = e.reg('rcx'), e.reg('rdx'), e.reg('r8')
        if target not in self.pool_allocations or self.pool_allocations.get(source) != size or size >= self.pool_allocations[target]:
            raise ValueError('Unexpected parent hierarchy copy')
        e.uc.mem_write(target, bytes(e.uc.mem_read(source, size)))
        return target

    def invoke(self, entry, stops=None, **regs):
        self.order = []
        self.lock_events = []
        self.e.write(STACK + 0x100008, RETURN)
        trace = fragment(self.e, entry, stops or {RETURN}, dict(rsp=STACK + 0x100008, **regs))
        trace['callOrder'] = list(self.order)
        trace['lockOrder'] = list(self.lock_events)
        return trace

    def acquire(self):
        pointer = self.e.reg('rcx')
        if pointer not in (self.reflection_lock, self.array_lock) or self.lock_held is not None:
            raise ValueError('Declared single-thread lock ownership mismatch')
        self.lock_held = pointer
        self.lock_events.append(dict(action='acquire', lock='array' if pointer == self.array_lock else 'reflection'))
        return 0

    def release(self):
        if self.e.reg('rcx') != self.lock_held:
            raise ValueError('Unbalanced declared lock release')
        self.lock_events.append(dict(action='release', lock='array' if self.lock_held == self.array_lock else 'reflection'))
        self.lock_held = None
        return 0

    def allocate_node(self):
        if self.e.reg('rcx') != 40 or self.e.read(self.e.reg('rsp')) not in (0x1404dd1ad, 0x1404dd307):
            raise ValueError('Only observed 40-byte native set nodes allowed')
        pointer = self.e.alloc(40)
        self.allocations[pointer] = 40
        return pointer

    def free_node(self):
        pointer, size = self.e.reg('rcx'), self.e.reg('rdx')
        if self.allocations.get(pointer) != size or pointer in self.frees or self.e.read(self.e.reg('rsp')) not in (0x1404dd648, 0x1404e48e9):
            raise ValueError('Native set free mismatch')
        self.frees.append(pointer)
        return 0

    def allocate_array(self):
        e = self.e
        klass, count = e.reg('rcx'), e.reg('rdx')
        caller = e.read(e.reg('rsp'))
        if klass != self.type_array_class or count > 8 or caller not in (0x1404dd5a9, 0x159b6eb6e):
            raise ValueError('Only exact Type[] caller and bounded count storage allowed')
        array = e.alloc(32 + count * 8 + 8)
        e.write(array, klass)
        e.write(array + 0x18, count)
        e.write(array + 32 + count * 8, 0xfedcba9876543210)
        self.last_array = array
        return array


def run_cases(runtime):
    e = runtime.e
    output = []
    for entry, name in ((0x1404dd170, 'get-interfaces-complete-prepared-caches'), (0x159b6e5b0, 'has-iequatable-complete-prepared-caches')):
        trace = runtime.invoke(entry, rcx=runtime.reflections[20060])
        array = runtime.last_array
        values = [e.read(array + 0x20 + i * 8) for i in range(e.read(array + 0x18))]
        types = [e.read(e.read(value + 0x10)) for value in values]
        output.append(dict(name=name, result=e.reg('rax'), arrayCount=len(values), interfaceTypes=types,
            allFullPointersPreserved=set(values) == {runtime.reflections[i] for i in (241, 248, 243)},
            tailCanary=e.read(array + 0x20 + len(values) * 8), lockBalanced=runtime.lock_held is None, trace=trace))
    # Repeated interface at an ancestor must be deduplicated by the native set.
    value_class = runtime.classes[405]
    metadata = e.read(value_class + 0x58)
    original_count = e.read(metadata + 0x43, 'B')
    array = e.read(value_class + 0x88)
    e.write(metadata + 0x43, 0x24, 'B')
    e.write(array, runtime.classes[241])
    trace = runtime.invoke(0x1404dd170, rcx=runtime.reflections[20060])
    result = e.reg('rax')
    output.append(dict(name='counterfactual-ancestor-duplicate-interface', arrayCount=e.read(result + 0x18),
        setNodeCalls=trace['executedAddresses']['0x141a2e150'], trace=trace))
    e.write(metadata + 0x43, original_count, 'B')
    e.write(array, 0)
    enum_metadata = e.read(runtime.classes[204] + 0x58)
    original_enum_count = e.read(enum_metadata + 0x43, 'B')
    e.write(enum_metadata + 0x43, 0x25, 'B')
    trace = runtime.invoke(0x1404dd170, rcx=runtime.reflections[20060])
    output.append(dict(name='counterfactual-empty-interface-parents', arrayCount=e.read(e.reg('rax') + 0x18),
        setNodeCalls=trace['executedAddresses']['0x141a2e150'], trace=trace))
    e.write(enum_metadata + 0x43, original_enum_count, 'B')
    trace = runtime.invoke(0x159b6e5b0, {0x1404c2be0}, rcx=0)
    output.append(dict(name='null-runtime-type-rejected', trace=trace))
    e.write(runtime.classes[241] + 0xc9, 32, 'B')
    trace = runtime.invoke(0x159b6e5b0, {0x159b6e646}, rcx=runtime.reflections[20060])
    output.append(dict(name='counterfactual-generic-interface-before-definition-provider', trace=trace))
    e.write(runtime.classes[241] + 0xc9, 0, 'B')
    # Actual Type[] class factory miss is observed after it releases the lock.
    occupied = e.read(runtime.array_block + 8)
    e.write(runtime.array_block + 8, 0)
    trace = runtime.invoke(0x1404dd170, {0x1404ea15f}, rcx=runtime.reflections[20060])
    output.append(dict(name='type-array-factory-miss-before-array-base-state', lockBalanced=runtime.lock_held is None,
        elementType=e.read(runtime.classes[314] + 0x10), trace=trace))
    e.write(runtime.array_block + 8, occupied)
    cache_block = e.read(runtime.reflection_cache + 0x28)
    reflection_occupied = e.read(cache_block + 8)
    e.write(cache_block + 8, 0)
    trace = runtime.invoke(0x1404dd170, {0x1405382a9}, rcx=runtime.reflections[20060])
    output.append(dict(name='interface-runtime-type-cache-miss-before-allocation',
        missedType=e.read(e.read(e.reg('rbp') - 0x10)), lockBalanced=runtime.lock_held is None, trace=trace))
    e.write(cache_block + 8, reflection_occupied)
    # Full original hierarchy producer uses static parent declarations.
    trace = runtime.invoke(0x14051f280, rcx=runtime.classes[284])
    depth = e.read(runtime.classes[284] + 0xc4, 'B')
    hierarchy = e.read(runtime.classes[284] + 0x48)
    chain = [e.read(e.read(hierarchy + i * 8) + 0x10) for i in range(depth)]
    output.append(dict(name='runtime-type-hierarchy-native-producer', depth=depth, chain=chain, trace=trace))
    # Exact ordinary-class hierarchy check after generic/array readiness branches.
    frame = e.alloc(64) + 32
    for expected, actual, result in ((314, 284, True), (284, 314, False), (314, 371, False)):
        e.write(frame - 0x20, runtime.classes[expected])
        trace = runtime.invoke(0x14051c02c, {0x14051bdf5}, rbp=frame, r14=runtime.classes[actual])
        output.append(dict(name='hierarchy-assignment-' + str(expected) + '-from-' + str(actual),
            expectedType=expected, actualType=actual, expected=result, actual=bool(e.reg('eax') & 255),
            completeAssignabilityFunction=False, trace=trace))
    # Counterfactual same-bucket key verifies this array cache follows a collision.
    collision = e.alloc(512)
    e.uc.mem_write(collision + 0x10, bytes(e.uc.mem_read(runtime.classes[314] + 0x10, 16)))
    e.write(collision + 0x10, 314 + 64)
    first = runtime.array_hash & 63
    second = (first + 1) & 63
    packed = e.alloc(32)
    positions = {first: (collision, 0xfedcba9876543210), second: (runtime.classes[314], runtime.type_array_class)}
    for index, (_, (key, value)) in enumerate(sorted(positions.items())):
        e.write(packed + index * 16, key)
        e.write(packed + index * 16 + 8, value)
    e.write(runtime.array_block, packed)
    e.write(runtime.array_block + 8, (1 << first) | (1 << second))
    trace = runtime.invoke(0x1404e9e60, rcx=runtime.classes[314], edx=1, r8=0)
    output.append(dict(name='counterfactual-array-cache-collision-hit', returnedCorrectArrayClass=e.reg('rax') == runtime.type_array_class,
        keyCompareCalls=trace['executedAddresses']['0x1404f0820'], lockBalanced=runtime.lock_held is None, trace=trace))
    e.write(runtime.array_block, runtime.array_entries)
    e.write(runtime.array_block + 8, occupied)
    # The comparer factory now gets an exact Type[] and actual RuntimeType input.
    # Definition wrapper is a separate declared generic definition, not a created comparer.
    definition = e.alloc(32)
    e.write(definition, runtime.classes[284])
    reference_base = int(runtime.declared['referenceBase'], 16)
    e.write(definition + 0x10, reference_base + 0x691330)
    trace = runtime.invoke(0x159b6eb50, {0x14051b7f0}, rcx=definition, rdx=runtime.reflections[20060])
    output.append(dict(name='generic-comparer-factory-before-type-class-initialization-lock',
        definitionType=e.read(e.read(definition + 0x10)), argumentType=20060,
        requestedArrayLength=e.read(runtime.last_array + 0x18),
        arrayElementStillNull=e.read(runtime.last_array + 0x20) == 0,
        assignmentExpectedType=e.read(e.reg('rdi') + 0x10),
        assignmentActualType=e.read(e.reg('r14') + 0x10), trace=trace))
    return output


if __name__ == '__main__':
    main()
