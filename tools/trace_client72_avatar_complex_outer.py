"""F-03d3e1: complex Avatar outer53824 and leaves64132/86812.

String/uint32 map bindings are independently selected from concrete/shared AOT.
Complex child34045 nonempty remains refused; no production identity promotion.
"""
import argparse
import copy
import json
import hashlib
import struct
from pathlib import Path
try:
    from .extract_client72 import HASHES, PE, HEADER_VA, CODE_REGISTRATION, TypeResolver, number, read_verified, extract
    from .client72_emulator import Offline72, GLOBAL, STREAM
    from .client72_graph import GraphReader72
    from .client72_map_runtime import MapRuntime72
    from .trace_client72_avatar_core import ROOT, core_aot
    from .trace_client72_avatar_items import AvatarItems72, INVENTORY_SHA, fixtures as item_fixtures
    from .trace_client72_avatar_outer import AvatarOuter72
    from .trace_client72_player_store import type_cells
    from .trace_client72_token import varint
except ImportError:
    from extract_client72 import HASHES, PE, HEADER_VA, CODE_REGISTRATION, TypeResolver, number, read_verified, extract
    from client72_emulator import Offline72, GLOBAL, STREAM
    from client72_graph import GraphReader72
    from client72_map_runtime import MapRuntime72
    from trace_client72_avatar_core import ROOT, core_aot
    from trace_client72_avatar_items import AvatarItems72, INVENTORY_SHA, fixtures as item_fixtures
    from trace_client72_avatar_outer import AvatarOuter72
    from trace_client72_player_store import type_cells
    from trace_client72_token import varint

TYPES = {53824, 64132, 86812}


def string_map_aot(binary, metadata, startup, records):
    pe = PE(binary); h = pe.offset(HEADER_VA); cr = pe.offset(CODE_REGISTRATION)
    specs = 0x210 + (number(binary, h + 0x170) ^ 0x6347382B)
    spec_count = (number(binary, h + 0x24) ^ 0x323BAE70) // 12
    table = 0x210 + (number(binary, h + 0xEC) ^ 0x3CF0720C)
    size = (number(binary, h + 0x110) + 0xA3D9CC8B) & 0xffffffff
    count = (number(binary, cr + 0x64) + 0x9EA91E3B) & 0xffffffff
    pointers = pe.offset(number(binary, cr + 0x30, 'Q'), count * 8)
    methods = {m['index']: (t, m) for t in records for m in t['methods']}
    resolver = TypeResolver(pe, startup, records, h); rows = []
    for i in range(size // 14):
        pointer, spec = struct.unpack_from('<ii', metadata, table + i * 14)
        if not 0 <= pointer < count or not 0 <= spec < spec_count: raise ValueError('String map AOT bounds')
        method, cls, args = struct.unpack_from('<iii', metadata, specs + spec * 12); owner, row = methods[method]
        if owner['index'] not in (17961, 17964) or cls < 0 or row['name'] not in ('.ctor', 'MOMMACBEMEK', 'FNNADLCNGFO', 'set_Item'): continue
        arguments = [resolver.resolve_index(x) for x in resolver.arguments[cls]]
        if len(arguments) != 2 or arguments[0]['kind'] not in ('string', 'object') or arguments[1]['kind'] != 'uint32': continue
        if args != -1: raise ValueError('Unexpected string map method arguments')
        address = number(binary, pointers + pointer * 8, 'Q'); pe.offset(address, executable=True)
        rows.append(dict(owner=owner['index'], classInstance=cls, method=row['name'], methodDefinition=method,
            tableIndex=i, methodSpec=spec, pointerIndex=pointer, address=hex(address), arguments=arguments))
    return rows


class StringUInt32Map72:
    """Measured object/u32 shared body with string concrete codec and node+16."""
    klass = MapRuntime72.klass
    context = MapRuntime72.context
    delegate = MapRuntime72.delegate

    def __init__(self, graph, bindings):
        self.g, self.e = graph, graph.e; e = self.e
        def binding(owner, instance, method):
            hits = {int(x['address'], 16) for x in bindings if (x['owner'], x['classInstance'], x['method']) == (owner, instance, method)}
            if len(hits) != 1: raise ValueError('String/u32 map AOT missing/ambiguous')
            return hits.pop()
        self.reader = binding(17961, 3472, 'FNNADLCNGFO')
        if self.reader != binding(17961, 3473, 'FNNADLCNGFO'): raise ValueError('String/u32 concrete/shared reader mismatch')
        self.setter = binding(17961, 3473, 'set_Item'); self.merge = binding(17964, 3473, 'MOMMACBEMEK'); self.ctor = binding(17964, 3473, '.ctor')
        if (self.reader, self.setter, self.merge, self.ctor) != (0x154D585C0, 0x154D5A490, 0x155F989E0, 0x155F98970): raise ValueError('String/u32 measured body drift')
        self.empty_string = e.alloc(24); e.write(self.empty_string + 16, 0, 'I')
        self.static_values = {self.empty_string: dict(kind='string', hex='', text='')}
        initialized = self.klass(); e.write(initialized + 0x38, e.alloc())
        entry_info, entry_ctx = self.context()
        e.write(entry_ctx + 0x10, e.alloc()); e.write(entry_ctx + 0x20, initialized)
        self.adapter_class = self.klass(1024); table = e.alloc(16)
        e.write(table, graph.interface); e.write(self.adapter_class + 0x80, table); e.write(self.adapter_class + 0xC0, 1, 'H'); e.write(self.adapter_class + 0xBE, 32, 'H')
        e.write(self.adapter_class + 0xE8, self.merge); e.write(self.adapter_class + 0xE8 + 32 * 8, entry_info)
        self.info, read_ctx = self.context(); ctor_info = e.alloc(); e.write(ctor_info + 8, self.ctor)
        e.write(read_ctx + 0x10, self.adapter_class); e.write(read_ctx + 0x18, ctor_info); e.write(read_ctx + 0x148, e.alloc())
        set_info, set_ctx = self.context(); e.write(read_ctx + 0x140, set_info)
        policy = self.klass(); e.write(policy + 0x38, e.alloc()); e.write(set_ctx + 0x40, policy)
        for offset, sub_offset in [(0xF8, 0x78), (0x1E0, 0x88)]:
            meta, ctx = self.context(); e.write(ctx + sub_offset, e.alloc()); e.write(set_ctx + offset, meta)
        self.codec_class = e.synthetic_type(0x1458DC0A0); self.codec = None
        self.append_stub = e.model_stub(self.append_node)
        e.models[0x15787CDC0] = self.lookup; e.models[0x15787D2A0] = self.insert
        e.models[0x14E5DF080] = self.codec_factory; self.factory_calls = []
        for address in (self.reader, self.setter, self.merge, self.ctor): e.allow_unwind(address)

    def codec_factory(self):
        e = self.e; tag = e.reg('ecx'); codec = e.alloc()
        e.write(codec + 0x30, tag, 'I'); e.write(codec + 0x28, self.delegate(0x147500E70)); e.write(codec + 0x18, self.empty_string)
        self.factory_calls.append(tag); return codec

    def reset(self): self.dictionaries = {}; self.linked_lists = {}

    def create(self, pointer, field_type):
        if [x['kind'] for x in field_type['arguments']] != ['string', 'uint32']: raise ValueError('Only measured string/u32 map supported')
        e = self.e; dictionary, linked, entries = e.alloc(), e.alloc(), e.alloc(32 + 64 * 24)
        e.write(entries + 24, 64, 'I'); e.write(dictionary + 48, entries); e.write(pointer + 24, dictionary); e.write(pointer + 16, linked)
        klass = self.klass(512); e.write(klass + 0x138, self.append_stub); e.write(linked, klass)
        self.dictionaries[dictionary] = dict(entries=entries, nodes=[], indices={}); self.linked_lists[linked] = dictionary
        self.g.containers[pointer]['mapDictionary'] = dictionary

    def allocate(self, klass):
        if klass == self.codec_class: return self.e.alloc()
        if klass != self.adapter_class: return None
        p = self.e.alloc(512); self.e.write(p, klass); self.g.objects[p] = 17964; return p

    def before(self, address):
        if address not in (self.reader, self.merge, self.setter, self.ctor): return
        if address == self.reader:
            if self.e.reg('r8') != self.codec: raise ValueError('Wrong string/u32 codec')
            self.e.uc.reg_write(self.e.R.UC_X86_REG_R9, self.info)
        self.g.entered.append(dict(kind='native-string-u32-map', entry=hex(address)))

    def lookup(self):
        key = self.g.values[self.e.reg('rdx')]['hex']
        return self.dictionaries[self.e.reg('rcx')]['indices'].get(key, -1)

    def append_node(self):
        p = self.e.alloc(80); self.e.uc.mem_write(p + 16, bytes(self.e.uc.mem_read(self.e.reg('rdx'), 16))); return p

    def insert(self):
        e = self.e; d = self.dictionaries[e.reg('rcx')]; key = self.g.values[e.reg('rdx')]['hex']; node = e.reg('r8')
        if key in d['indices'] or len(d['nodes']) >= 64: raise ValueError('String-map fixture insertion/capacity')
        index = len(d['nodes']); d['indices'][key] = index; d['nodes'].append(node); e.write(d['entries'] + 32 + index * 24 + 16, node); return 1

    def snapshot(self, dictionary, depth):
        e = self.e
        return {bytes.fromhex(self.g.values[e.read(node + 16)]['hex']).decode('utf8'): e.read(node + 24, 'I') for node in self.dictionaries[dictionary]['nodes']}

    def observe_store(self, address, size):
        for d in self.dictionaries.values():
            for node in d['nodes']:
                if node + 16 <= address < node + 32: return dict(kind='string-u32-map-node', nodeOffset=address - node, size=size, source=hex(self.e.reg('rip')))
        return None


class ComplexMaps72:
    def __init__(self, core, strings):
        self.core, self.strings = core, strings; core.parts.append(strings); core.static_values.update(strings.static_values)

    def __getattr__(self, name): return getattr(self.core, name)

    def create(self, pointer, typ):
        if typ['arguments'][0]['kind'] == 'string': return self.strings.create(pointer, typ)
        return self.core.create(pointer, typ)


class AvatarComplexOuter72(AvatarItems72):
    def blob(self, kind):
        if kind == 'string': return AvatarOuter72.blob(self, kind)
        return GraphReader72.blob(self, kind)

    def __init__(self, engine, directory, cells, bindings, item_schemas, string_bindings):
        super().__init__(engine, directory, cells, bindings, item_schemas); e = self.e
        for index in TYPES | {34045}:
            row = e.records[index]; entry = next(int(m['registeredAddress'], 16) for m in row['methods'] if m['name'] == 'MOMMACBEMEK')
            #53824 already has a class captured by AvatarInfo's native codec.
            if index in self.classes: klass = self.classes[index]
            else:
                klass = e.alloc(1024); table = e.alloc(16); e.write(klass + 0xC7, 1, 'B')
                e.write(klass + 0x80, table); e.write(klass + 0xC0, 1, 'H'); e.write(klass + 0xBE, 32, 'H')
                e.write(table, self.interface); e.write(klass + 0xE8, entry)
            self.classes[index] = klass; self.class_indices[klass] = index; self.entries[index] = entry; e.allow(entry)
            if index not in TYPES: continue
            fields = []
            for f in row['fields']:
                if f['offsetKind'] != 'instance': continue
                typ = f['type']; field = dict(clientField=f['name'], objectOffset=f['offset'], type=typ['kind'], cardinality='singular')
                if typ['kind'] == 'class': field.update(type='message', nestedTypeIndex=typ['typeDefinition'])
                elif typ['kind'] == 'genericinst':
                    args = typ['arguments']
                    if len(args) == 2: field.update(type='map', cardinality='map', keyType=args[0]['kind'], valueType=args[1]['kind'])
                    else: field.update(type='message', cardinality='repeated', nestedTypeIndex=args[0]['typeDefinition'])
                fields.append(field)
            self.schemas[index] = dict(typeIndex=index, clientType=row['name'], fields=fields)
        for cell, row in cells.items():
            typ = row['type']
            if typ['kind'] == 'class' and typ.get('typeDefinition') in TYPES | {34045}: e.write(cell, self.classes[typ['typeDefinition']])
        strings = StringUInt32Map72(self, string_bindings); self.maps = ComplexMaps72(self.maps, strings); self.complex_initializers = []
        for index, entry in [(64132, 0x14BEBC440), (53824, 0x148490870)]:
            before = len(self.factory_calls); e.allow(entry); e.prepare(); e.run(entry)
            self.complex_initializers.append(dict(typeIndex=index, entry=hex(entry), factoryArguments=self.factory_calls[before:]))
        strings.codec = e.read(GLOBAL + 0x1F290)
        if e.read(strings.codec + 0x20, 'I') != 10: raise ValueError('String-map actual initializer tag drift')
        self.maps.integer.codecs[e.read(GLOBAL + 0x2A8F8)] = (53824, 8)
        if e.read(e.read(GLOBAL + 0x2A8F8) + 0x20, 'I') != 66: raise ValueError('Complex outer map initializer tag drift')
        self.heap_floor = e.heap_cursor

    def new_message(self, index, address=None):
        if index in TYPES: return GraphReader72.new_message(self, index, address)
        if index == 34045:
            p = self.e.alloc(512) if address is None else address
            self.e.write(p, self.classes[index]); self.e.write(p + 0x14, 1, 'I'); self.objects[p] = index; return p
        return super().new_message(index, address)

    def snapshot(self, pointer, depth=0):
        if pointer and self.objects[pointer] in TYPES: return GraphReader72.snapshot(self, pointer, depth)
        if pointer and self.objects[pointer] == 34045: return dict(untracedType=34045, content='empty-envelope-only')
        return super().snapshot(pointer, depth)

    def observe_entry(self, address):
        if self.active:
            if address == self.entries[34045]:
                stream = self.e.reg('rdx')
                if self.e.read(stream + 0x44, 'I') != self.e.read(stream + 0x38, 'I'): raise ValueError('Nonempty child outside F-03d3e1: 34045')
            if self.item_copy: self.item_copy.before(address)
        return GraphReader72.observe_entry(self, address)


def scan_raw(graph):
    selected = []; count = 0
    for index in sorted(TYPES):
        observed = {}
        for tag in range(1, 33):
            for wire, payload in ((0, varint(0x12345678)), (1, bytes(8)), (2, b'\0'), (5, bytes(4))):
                row = graph.run_graph(index, varint(tag << 3 | wire) + payload); count += 1
                if row['result'] or row['stores']:
                    names = list(row['result'])
                    if not names: names = [f['clientField'] for f in graph.schemas[index]['fields'] if any(s['typeIndex'] == index and s['objectOffset'] == f['objectOffset'] for s in row['stores'])]
                    if len(set(names)) != 1: raise ValueError('Ambiguous complex outer raw branch')
                    observed.setdefault(names[0], []).append((tag, wire))
                    selected.append(dict(typeIndex=index, tag=tag, wireType=wire, result=row['result'], entered=row['entered'], stores=row['stores']))
        for field in graph.schemas[index]['fields']:
            bindings = observed.pop(field['clientField'], [])
            if not bindings or len({tag for tag, _ in bindings}) != 1: raise ValueError('Missing complex outer field: ' + field['clientField'])
            wires = sorted(w for _, w in bindings); expected = [2] if field['type'] in ('message', 'map') else [0]
            if wires != expected: raise ValueError('Complex outer metadata/wire mismatch')
            field.update(tag=bindings[0][0], acceptedWireTypes=wires, semanticStatus='unresolved')
            if field.get('nestedTypeIndex') == 34045: field['contentStatus'] = 'empty-envelope-only; nonempty-refused'
        if observed: raise ValueError('Unexpected complex outer raw field')
    if sum(len(graph.schemas[i]['fields']) for i in TYPES) != 18: raise ValueError('Complex outer metadata field drift')
    return dict(nativeFixtures=count, scope='3 types, tags1..32/wire0,1,2,5; blocked34045 nonempty refused', fields=selected)


def fixtures():
    complex_wire = '080b10161821208180808080808010283730425001586f42040807100b4a0c0a060a014110ac02101618016206080b101618216206082c103718423a00'
    expected_complex = {'LLHEBIHGDAE': 11, 'FAAMIEFKMPB': 22, 'PJAGIOHBKLD': 33, 'FBLKPHOCBIK': 9007199254740993,
        'NJAGFJINHMA': 55, 'KFDCBKBPPGL': 66, 'MNCFEDPDEAG': True, 'MKFOJONFDMA': 111, 'JMGKMMFNABP': {'7': 11},
        'GDLHHFGKOGO': {'JMPEPGDDKPD': {'A': 300}, 'BGNAFEDFIHF': 22, 'HBBLAFDHAOL': True},
        'LOLPFGGFGLH': [{'BAHECKLMKGJ': 11, 'HDKEBNDMFLF': 22, 'ELPEMEBJKNO': 33}, {'BAHECKLMKGJ': 44, 'HDKEBNDMFLF': 55, 'ELPEMEBJKNO': 66}],
        'KDKAEHEEMMJ': {'untracedType': 34045, 'content': 'empty-envelope-only'}}
    cases = [dict(name='53824-all-12-fields-nonempty-leaves', typeIndex=53824, wire=complex_wire, expected=expected_complex),
        dict(name='64132-three-nonzero-fields', typeIndex=64132, wire='0a060a014110ac0210161801', expected={'JMPEPGDDKPD': {'A': 300}, 'BGNAFEDFIHF': 22, 'HBBLAFDHAOL': True}),
        dict(name='string-map-value-upper-is-unsigned', typeIndex=64132, wire='0a090a014110ffffffff0f', expected={'JMPEPGDDKPD': {'A': 4294967295}}),
        dict(name='string-map-same-key-duplicate-zero', typeIndex=64132, wire='0a060a014110ac020a050a01411000', expected={'JMPEPGDDKPD': {'A': 0}}),
        dict(name='string-map-unicode-emoji-nul-key', typeIndex=64132, wire='0a0c0a08e4b8adf09f8c9900100b', expected={'JMPEPGDDKPD': {'中🌙\0': 11}}),
        dict(name='string-map-empty-key-upper-value', typeIndex=64132, wire='0a080a0010ffffffff0f', expected={'JMPEPGDDKPD': {'': 4294967295}}),
        dict(name='string-map-key-only', typeIndex=64132, wire='0a030a0141', expected={'JMPEPGDDKPD': {'A': 0}}),
        dict(name='string-map-value-only', typeIndex=64132, wire='0a02100b', expected={'JMPEPGDDKPD': {'': 11}}),
        dict(name='string-map-empty-entry', typeIndex=64132, wire='0a00', expected={'JMPEPGDDKPD': {'': 0}}),
        dict(name='string-map-entry-duplicates-and-unknown', typeIndex=64132, wire='0a0c0a01410a0142100b1801100c', expected={'JMPEPGDDKPD': {'B': 12}}),
        dict(name='64132-upper-scalar-bool-normalization-and-zero', typeIndex=64132, wire='10ffffffff0f18021800', expected={'BGNAFEDFIHF': 4294967295}),
        dict(name='86812-distinct-nonzero', typeIndex=86812, wire='080b10161821', expected={'BAHECKLMKGJ': 11, 'HDKEBNDMFLF': 22, 'ELPEMEBJKNO': 33}),
        dict(name='86812-upper-and-duplicate-zero', typeIndex=86812, wire='08ffffffff0f10ffffffff0f18011800', expected={'BAHECKLMKGJ': 4294967295, 'HDKEBNDMFLF': 4294967295}),
        dict(name='53824-upper-u64-and-u32', typeIndex=53824, wire='08ffffffff0f20ffffffffffffffffff01', expected={'LLHEBIHGDAE': 4294967295, 'FBLKPHOCBIK': 18446744073709551615}),
        dict(name='53824-duplicate-zero-and-wrong-wire', typeIndex=53824, wire='080108000a0020012000220050015000', expected={}),
        dict(name='53824-nested-string-map-merge-and-zero', typeIndex=53824,
            wire='4a0c0a060a014110ac02101618014a0b0a050a0142100b100018004a070a050a01411000',
            expected={'GDLHHFGKOGO': {'JMPEPGDDKPD': {'A': 0, 'B': 11}}}),
        dict(name='53824-u32-map-duplicate-zero', typeIndex=53824, wire='42040807100b420408071000', expected={'JMGKMMFNABP': {'7': 0}})]
    payload = bytes.fromhex(complex_wire); envelope = varint(37 << 3 | 2) + varint(len(payload)) + payload
    cases.append(dict(name='AvatarInfo-nonempty-complex-outer', typeIndex=ROOT, wire=envelope.hex(), expected={'AMLLHJJIMNI': [expected_complex]}))
    baseline = next(x for x in item_fixtures() if x['name'] == 'AvatarInfo-all-37-fields-three-nonempty-child-trees')
    expected = copy.deepcopy(baseline['expected']); expected['AMLLHJJIMNI'] = [{}, expected_complex]
    cases.append(dict(name='AvatarInfo-all-37-fields-with-complex-outer', typeIndex=ROOT, wire=baseline['wire'] + envelope.hex(), expected=expected))
    for index in sorted(TYPES): cases.append(dict(name='empty-' + str(index), typeIndex=index, wire='', expected={}))
    return cases


def verify(graph):
    good = []
    for case in fixtures():
        row = graph.run_graph(case['typeIndex'], bytes.fromhex(case['wire']))
        if row['result'] != case['expected']: raise ValueError(case['name'] + ': complex outer native result differs ' + str(row['result']))
        good.append(dict(case, observation=row))
    rejected = []
    errors = [('nonempty-child-34045', 53824, '3a020801', 'explicit-scope-refusal', 100),
        ('invalid-tag', 53824, '00', 'explicit-input-model', 100),
        ('guid-overflow', 53824, '20ffffffffffffffffff02', 'explicit-input-model', 100),
        ('invalid-utf8-key', 64132, '0a030a01ff', 'explicit-input-model', 100),
        ('truncated-string-key', 64132, '0a030a0241', 'explicit-input-model', 100),
        ('negative-map-length', 64132, '0affffffff0f', 'native-error-entry', 100),
        ('truncated-child-envelope', 53824, '62030801', 'native-error-entry', 100),
        ('map-recursion-limit', 64132, '0a00', 'native-error-entry', 0),
        ('child-recursion-limit', 53824, '4a00', 'native-error-entry', 0)]
    for name, index, wire, scope, limit in errors:
        try: graph.run_graph(index, bytes.fromhex(wire), limit)
        except ValueError as error:
            text = str(error)
            if scope == 'explicit-scope-refusal' and 'Nonempty child outside F-03d3e1' not in text: raise
            if scope == 'native-error-entry' and 'Native error branch:' not in text: raise
            rejected.append(dict(name=name, typeIndex=index, wire=wire, scope=scope, error=text))
        else: raise ValueError('Invalid/out-of-scope complex outer input accepted: ' + name)
    return good, rejected


def main():
    p = argparse.ArgumentParser(description=__doc__); p.add_argument('--client', required=True, type=Path)
    p.add_argument('--output', required=True, type=Path); p.add_argument('--observations', required=True, type=Path)
    p.add_argument('--directory', type=Path, default=Path('protocol/cn-7.2')); p.add_argument('--python-libs', type=Path, default=Path('local/migration72/python')); a = p.parse_args()
    paths = [a.output.resolve(), a.observations.resolve()]
    if len(set(paths)) != 2 or any(x.is_relative_to(a.client.resolve()) for x in paths): p.error('Distinct outputs outside client required')
    source = (a.directory / 'inventory-graph-observations.json').read_bytes()
    if hashlib.sha256(source).hexdigest() != INVENTORY_SHA: raise ValueError('Reused Item evidence drift')
    items = {x['typeIndex']: x for x in json.loads(source)['messages'] if x['typeIndex'] != 68537}
    b, m, s = [read_verified(a.client / name, digest) for name, digest in HASHES.items()]; records, _ = extract(b, m, startup=s, structures=True)
    cells = type_cells(b, m, s, records); bindings = string_map_aot(b, m, s, records); inherited = core_aot(b, m, s, records)
    e = Offline72(b, a.python_libs, records, trace_instructions=False); graph = AvatarComplexOuter72(e, a.directory, cells, inherited, items, bindings)
    try: scan = scan_raw(graph); good, rejected = verify(graph)
    finally: graph.close(); e.close()
    context = dict(schemaVersion=1, task='F-03d3e1', inputSHA256=HASHES, runtimeStatus='not-tested', targetProtocolReady=False,
        semanticIdentityStatus='unresolved', fullAvatarRawClosure=False, fullComplexRawClosure=False, blockedTypeIndices=[34045],
        aotBindings=bindings, initializers=graph.complex_initializers, stringCodecFactoryTags=graph.maps.strings.factory_calls,
        measuredStringMapLayout=dict(sharedClassInstance=3473, concreteClassInstance=3472, dictionaryOffset=24, listOffset=16, entriesOffset=48,
            keyNodeOffset=16, uint32ValueNodeOffset=24, keyCodecTagOffset=48, keyCodecDelegateOffset=40, valueCodecTagOffset=52, valueCodecDelegateOffset=24),
        dependencyScope=dict(typeIndices=sorted(TYPES), metadataStorageFields=18, blockedNonemptyType=34045,
            types=[dict(typeIndex=i, clientType=records[i]['name'], fields=[f for f in records[i]['fields'] if f['offsetKind'] == 'instance'],
                methods=[x for x in records[i]['methods'] if x['name'] in ('.cctor', 'MOMMACBEMEK')]) for i in sorted(TYPES)]),
        messages=[graph.schemas[i] for i in sorted(TYPES)], scan=scan, nativeFixtures=len(good), rejections=rejected,
        limitations=['Only53824 outer12 plus64132/86812 leaves6;34045 nonempty refused; complete complex/Avatar graph remains open',
            'String/u32 concrete/shared AOT and actual native adapter/setter independently checked; no old signed-map body/layout reused',
            'UTF8-to-managed-UTF16 and dictionary string equality, allocation/class/generic contexts, primitives/delegates and bounded64 storage are explicit models',
            'Native readers/ReadMessage limits/map adapter/setter/list append and inherited clones/copy helpers execute; native errors stop before throw',
            'No named identity, business semantics, handler/live ABI/client acceptance or production integration'])
    summary = dict(context, fixtures=[dict(name=x['name'], typeIndex=x['typeIndex'], wire=x['wire'], result=x['observation']['result'], entered=x['observation']['entered'], nestedCalls=x['observation']['nestedCalls'], mapStores=x['observation']['mapStores']) for x in good])
    for path, data in zip(paths, [dict(context, fixtures=good), summary]):
        path.parent.mkdir(parents=True, exist_ok=True); path.write_text(json.dumps(data, indent=2) + '\n', 'utf8')
    print(json.dumps(dict(messages=3, storage=18, wireFields=18, scanFixtures=scan['nativeFixtures'], nativeFixtures=len(good), rejections=len(rejected))))


if __name__ == '__main__': main()
