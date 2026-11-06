"""F-03d3b: bounded AvatarInfo raw core; four complex children stay refused.

No business names, production schema or shared runtime changes. Native concrete
readers/adapters/setters run with explicit bounded managed allocation models.
"""
import argparse
import json
import struct
from pathlib import Path
try:
    from .extract_client72 import HASHES, PE, HEADER_VA, CODE_REGISTRATION, TypeResolver, number, read_verified, extract
    from .client72_emulator import Offline72, GLOBAL, STREAM
    from .client72_graph import GraphReader72
    from .client72_map_runtime import MapRuntime72
    from .trace_client72_player_store import type_cells
    from .trace_client72_player_data import PlayerDataGraph72, SCHEMAS
    from .trace_client72_inventory_containers import UInt32MapRuntime72
    from .trace_client72_token import varint
except ImportError:
    from extract_client72 import HASHES, PE, HEADER_VA, CODE_REGISTRATION, TypeResolver, number, read_verified, extract
    from client72_emulator import Offline72, GLOBAL, STREAM
    from client72_graph import GraphReader72
    from client72_map_runtime import MapRuntime72
    from trace_client72_player_store import type_cells
    from trace_client72_player_data import PlayerDataGraph72, SCHEMAS
    from trace_client72_inventory_containers import UInt32MapRuntime72
    from trace_client72_token import varint

ROOT, VALUE = 48178, 78350
TYPES = {ROOT, VALUE, 66100, 27246, 13438, 40807}
BLOCKED = {53824, 16352, 27196, 66308}
STATICS = {3: 0x36718, 5: 0x366F0, 6: 0x36720, 7: 0x36730, 10: 0x36748,
           14: 0x36700, 15: 0x36740, 17: 0x36708, 20: 0x36760, 22: 0x36758,
           24: 0x36728, 35: 0x36710, 36: 0x36750, 37: 0x366F8, 38: 0x36738}


def core_aot(binary, metadata, startup, records):
    pe = PE(binary); h = pe.offset(HEADER_VA); cr = pe.offset(CODE_REGISTRATION)
    specs = 0x210 + (number(binary, h + 0x170) ^ 0x6347382B)
    spec_count = (number(binary, h + 0x24) ^ 0x323BAE70) // 12
    table = 0x210 + (number(binary, h + 0xEC) ^ 0x3CF0720C)
    size = (number(binary, h + 0x110) + 0xA3D9CC8B) & 0xffffffff
    count = (number(binary, cr + 0x64) + 0x9EA91E3B) & 0xffffffff
    pointers = pe.offset(number(binary, cr + 0x30, 'Q'), count * 8)
    methods = {m['index']: (t, m) for t in records for m in t['methods']}
    resolver = TypeResolver(pe, startup, records, h); rows = []
    selected = {66210: {9505, 12396, 1541}, 66214: {1541}, 17961: {3723, 5971},
                17964: {3723, 5971}, 35584: {11, 58}, 25060: {6712, 6486}}
    for i in range(size // 14):
        pointer, spec = struct.unpack_from('<ii', metadata, table + i * 14)
        if not 0 <= pointer < count or not 0 <= spec < spec_count: raise ValueError('Core AOT reference bounds')
        method, cls, args = struct.unpack_from('<iii', metadata, specs + spec * 12); owner, row = methods[method]
        if cls not in selected.get(owner['index'], set()) or row['name'] not in ('.ctor', 'MOMMACBEMEK', 'FNNADLCNGFO', 'set_Item'): continue
        if args != -1: raise ValueError('Unexpected core AOT method arguments')
        address = number(binary, pointers + pointer * 8, 'Q'); pe.offset(address, executable=True)
        rows.append(dict(owner=owner['index'], classInstance=cls, method=row['name'], methodDefinition=method,
            tableIndex=i, methodSpec=spec, pointerIndex=pointer, address=hex(address),
            arguments=[resolver.resolve_index(x) for x in resolver.arguments[cls]]))
    return rows


class CoreMessageMaps72(MapRuntime72):
    CONFIG = {tag: dict(owner=66210, concrete=instance, shared=1541, adapter=66214, child=child,
        key='uint32', static=STATICS[tag], lookup=0x1578B66F0, insert=0x1578B6A20,
        dictionaryOffset=16, listOffset=24, entriesOffset=24, nodeOffset=40)
        for tag, instance, child in [(3, 9505, VALUE), (10, 12396, 66100)]}

    def __init__(self, graph, directory, bindings):
        self.bindings_core = bindings; self.broken_routing = False
        super().__init__(graph, directory)

    def binding(self, owner, instance, method):
        hits = {int(x['address'], 16) for x in self.bindings_core
                if (x['owner'], x['classInstance'], x['method']) == (owner, instance, method)}
        if len(hits) != 1: raise ValueError('Core map AOT missing/ambiguous')
        return hits.pop()

    def before(self, address):
        if self.broken_routing: return super().before(address)
        if address == 0x156D35A40:
            c = next((c for c in self.config.values() if c['codec'] == self.e.reg('r8')), None)
            if c is None: raise ValueError('Unknown dedicated message-map codec')
            self.e.uc.reg_write(self.e.R.UC_X86_REG_R9, c['info'])
            self.g.entered.append(dict(kind='codec-selected-context', entry=hex(address), mapTag=c['tag'], child=c['child']))
        elif address == 0x157B22E00:
            c = self.entry_classes[self.e.read(self.e.reg('rcx'))]
            self.g.entered.append(dict(typeIndex=c['adapter'], entry=hex(address), mapTag=c['tag'], child=c['child']))
        elif address == 0x156D350E0:
            self.g.entered.append(dict(kind='native-shared-map-setter', entry=hex(address)))


class FloatMap72(UInt32MapRuntime72):
    """Exact concrete u32/float map layout, separate from public map runtime."""
    def __init__(self, graph, bindings):
        self.g, self.e = graph, graph.e; e = self.e
        self.static_values = {}; self.codecs = {}; self.factory_calls = []
        def binding(owner, method):
            hits = {int(x['address'], 16) for x in bindings if (x['owner'], x['classInstance'], x['method']) == (owner, 5971, method)}
            if len(hits) != 1: raise ValueError('Float map AOT missing/ambiguous')
            return hits.pop()
        self.reader = binding(17961, 'FNNADLCNGFO'); self.setter = binding(17961, 'set_Item')
        self.merge = binding(17964, 'MOMMACBEMEK'); self.ctor = binding(17964, '.ctor')
        if (self.reader, self.setter) != (0x1551DB160, 0x1551DC230): raise ValueError('Float map body drift')
        self.codec_class = e.synthetic_type(0x1458E01A0)
        self.adapter_class = e.synthetic_type(0x1459368D8); table = e.alloc(16)
        e.write(self.adapter_class + 0x80, table); e.write(self.adapter_class + 0xC0, 1, 'H')
        e.write(self.adapter_class + 0xBE, 32, 'H'); e.write(table, graph.interface); e.write(self.adapter_class + 0xE8, self.merge)
        policy = e.synthetic_type(0x1458E0198); e.write(policy + 0x38, e.alloc())
        dictionary = e.alloc(512); info = e.alloc(); e.write(info, dictionary); e.write(0x145A926E0, info)
        context = e.alloc(); e.write(dictionary + 0x30, context); e.write(context + 0x88, e.alloc())
        self.append_stub = e.model_stub(self.append_node)
        for entry in (self.reader, self.setter, self.merge, self.ctor): e.allow_unwind(entry)

    def create(self, pointer, field_type):
        e = self.e; dictionary, linked, entries = e.alloc(), e.alloc(), e.alloc(32 + 64 * 24)
        e.write(entries + 24, 64, 'I'); e.write(dictionary + 24, entries)
        e.write(pointer + 24, dictionary); e.write(pointer + 16, linked)
        klass = e.alloc(512); e.write(klass + 0x138, self.append_stub); e.write(linked, klass)
        self.dictionaries[dictionary] = dict(entries=entries, nodes=[], indices={})
        self.linked_lists[linked] = dictionary; self.g.containers[pointer]['mapDictionary'] = dictionary

    def append_node(self):
        p = self.e.alloc(80); self.e.write(p + 44, self.e.reg('rdx')); return p

    def before(self, address):
        if address not in (self.reader, self.setter, self.merge): return
        if address == self.reader and self.e.reg('r8') not in self.codecs: raise ValueError('Unbound float-map codec')
        self.g.entered.append(dict(kind='native-float-map', entry=hex(address)))

    def snapshot(self, dictionary, depth):
        return {str(self.e.read(node + 44, 'I')): self.e.read(node + 48, 'f') for node in self.dictionaries[dictionary]['nodes']}

    def observe_store(self, address, size):
        for d in self.dictionaries.values():
            for node in d['nodes']:
                if node + 44 <= address < node + 52:
                    return dict(kind='float-map-node', nodeOffset=address - node, size=size, source=hex(self.e.reg('rip')))
        return None


class CoreMaps72:
    def __init__(self, graph, directory, bindings):
        self.message = CoreMessageMaps72(graph, directory, bindings)
        self.integer = UInt32MapRuntime72(graph); self.floating = FloatMap72(graph, bindings)
        self.parts = [self.message, self.integer, self.floating]; self.static_values = self.message.static_values
        self.e = graph.e
        self.e.models[0x1578B66F0] = lambda: self.dictionary_owner().lookup()
        self.e.models[0x1578B6A20] = lambda: self.dictionary_owner().insert()

    def dictionary_owner(self):
        return next(p for p in self.parts if self.e.reg('rcx') in p.dictionaries)

    def reset(self):
        for p in self.parts: p.reset()

    def create(self, pointer, typ):
        child = typ['arguments'][1]['kind']
        part = self.message if child == 'class' else self.floating if child == 'float' else self.integer
        part.create(pointer, typ)

    def allocate(self, klass):
        for p in self.parts:
            pointer = p.allocate(klass)
            if pointer is not None: return pointer
        return None

    def before(self, address):
        for p in self.parts: p.before(address)

    def snapshot(self, dictionary, depth):
        return next(p for p in self.parts if dictionary in p.dictionaries).snapshot(dictionary, depth)

    def observe_store(self, address, size):
        for p in self.parts:
            event = p.observe_store(address, size)
            if event: return event
        return None


class AvatarCore72(GraphReader72):
    box = PlayerDataGraph72.box
    float32 = PlayerDataGraph72.float32

    def __init__(self, engine, directory, cells, bindings):
        self.codec_classes = set(); super().__init__(engine, directory); e = self.e
        for index in TYPES | BLOCKED:
            row = e.records[index]; entry = next(int(m['registeredAddress'], 16) for m in row['methods'] if m['name'] == 'MOMMACBEMEK')
            klass = e.alloc(1024); table = e.alloc(16); e.write(klass + 0xC7, 1, 'B')
            e.write(klass + 0x80, table); e.write(klass + 0xC0, 1, 'H'); e.write(klass + 0xBE, 32, 'H')
            e.write(table, self.interface); e.write(klass + 0xE8, entry)
            self.classes[index] = klass; self.class_indices[klass] = index; self.entries[index] = entry; e.allow(entry)
        for cell, row in cells.items():
            typ = row['type']
            if typ['kind'] == 'class' and typ.get('typeDefinition') in TYPES | BLOCKED: e.write(cell, self.classes[typ['typeDefinition']])
        for index in TYPES - {VALUE}:
            fields = []
            for f in e.records[index]['fields']:
                if f['offsetKind'] != 'instance': continue
                t = f['type']; field = dict(clientField=f['name'], objectOffset=f['offset'], type=t['kind'], cardinality='singular')
                if t['kind'] == 'genericinst':
                    args = t['arguments']; child = args[-1]
                    if len(args) == 2:
                        field.update(type='map', cardinality='map', keyType=args[0]['kind'], valueType='message' if child['kind'] == 'class' else child['kind'])
                        if child['kind'] == 'class': field['nestedTypeIndex'] = child['typeDefinition']
                    elif child['kind'] == 'class': field.update(type='message', cardinality='repeated', nestedTypeIndex=child['typeDefinition'])
                    else: field.update(type=child['kind'], cardinality='repeated', packed=True)
                elif t['kind'] == 'class': field.update(type='message', nestedTypeIndex=t['typeDefinition'])
                elif t['kind'] == 'valuetype':
                    underlying = next(x['type']['kind'] for x in e.records[t['typeDefinition']]['fields'] if x['name'] == 'value__')
                    field.update(type=underlying, enumTypeIndex=t['typeDefinition'], metadataKind='valuetype')
                fields.append(field)
            self.schemas[index] = dict(typeIndex=index, clientType=e.records[index]['name'], fields=fields)
        self.schemas[VALUE] = json.loads(json.dumps(SCHEMAS[VALUE]))
        self.maps = CoreMaps72(self, directory, bindings)
        self.codec_classes = {e.synthetic_type(cell) for cell in (0x14592A0B8, 0x1458ADAB0, 0x1458BE038)}
        self.box_classes = {e.synthetic_type(0x145886320): ('int64', 8), e.synthetic_type(0x1458864C0): ('float', 4)}
        e.synthetic_type(0x1458AD9F0)
        e.models[0x140525C40] = self.box; e.models[0x1474E97E0] = self.float32
        e.models[0x14E5A5E30] = lambda: self.codec_factory('uint32')
        e.models[0x14E5DFF80] = lambda: self.codec_factory('uint64')
        e.models[0x14E5DF7C0] = lambda: self.codec_factory('float')
        e.models[0x14052D0F0] = lambda: 0
        self.factory_calls = []; self.initializers = []
        for index, entry in [(ROOT, 0x14F672C20), (66100, 0x151248710)]:
            before = len(self.factory_calls); e.allow(entry); e.prepare(); e.run(entry)
            self.initializers.append(dict(typeIndex=index, entry=hex(entry), factoryArguments=self.factory_calls[before:]))
        for tag in (3, 10): self.maps.message.config[tag]['codec'] = e.read(GLOBAL + STATICS[tag])
        for tag in (15, 17): self.maps.integer.codecs[e.read(GLOBAL + STATICS[tag])] = (ROOT, tag)
        self.maps.floating.codecs[e.read(GLOBAL + STATICS[7])] = (ROOT, 7)
        for tag, offset in STATICS.items():
            codec = e.read(GLOBAL + offset); tag_offset = 0x20 if tag in (3, 7, 10, 15, 17) else 0x10 if tag in (22, 37) else 0x34
            if e.read(codec + tag_offset, 'I') != tag << 3 | 2: raise ValueError('Avatar core native initializer tag drift')
        self.heap_floor = e.heap_cursor

    def codec_factory(self, kind):
        e = self.e; tag = e.reg('ecx'); codec, delegate = e.alloc(), e.alloc()
        e.write(codec + (0x2C if kind == 'float' else 0x34), tag, 'I')
        e.write(codec + (0x20 if kind == 'float' else 0x18), delegate)
        reader = {'uint32': 0x147520110, 'uint64': 0x1475202D0, 'float': 0x1474E97E0}[kind]
        e.write(delegate + 0x18, reader); e.write(delegate + 0x40, STREAM)
        self.factory_calls.append(dict(kind=kind, tag=tag)); return codec

    def allocate(self):
        if self.e.reg('rcx') in self.codec_classes: return self.e.alloc()
        return super().allocate()

    def new_message(self, index, address=None):
        if index in BLOCKED:
            p = self.e.alloc(512) if address is None else address
            self.e.write(p, self.classes[index]); self.e.write(p + 0x14, 1, 'I'); self.objects[p] = index; return p
        return super().new_message(index, address)

    def observe_entry(self, address):
        if self.active:
            blocked = next((i for i in BLOCKED if self.entries[i] == address), None)
            if blocked is not None:
                stream = self.e.reg('rdx')
                if self.e.read(stream + 0x44, 'I') != self.e.read(stream + 0x38, 'I'): raise ValueError('Nonempty child outside F-03d3b: ' + str(blocked))
            if address in (0x158DB8E10, 0x1571DDD60):
                container = self.e.reg('rcx')
                for pointer, index in self.objects.items():
                    if index not in TYPES: continue
                    for field in self.schemas[index]['fields']:
                        if field['cardinality'] == 'repeated' and field['type'] in ('uint32', 'uint64') and self.e.read(pointer + field['objectOffset']) == container:
                            self.entered.append(dict(kind='native-list-field', ownerType=index, clientField=field['clientField'], objectOffset=field['objectOffset'], key=self.e.read(STREAM + 0x20, 'I'), entry=hex(address)))
        return super().observe_entry(address)

    def snapshot(self, pointer, depth=0):
        if pointer and self.objects[pointer] in BLOCKED: return dict(untracedType=self.objects[pointer], content='empty-envelope-only')
        if pointer and self.objects[pointer] == VALUE: return PlayerDataGraph72.snapshot(self, pointer, depth)
        return super().snapshot(pointer, depth)

    def run_graph(self, index, data, recursion_limit=100):
        row = super().run_graph(index, data, recursion_limit)
        row['floatMapNodes'] = [dict(key=self.e.read(n + 44, 'I'), valueBits=hex(self.e.read(n + 48, 'I')))
            for d in self.maps.floating.dictionaries.values() for n in d['nodes']]
        return row


def reproduce_codec_conflict(graph):
    wire = bytes.fromhex('1a06080712020801')
    graph.maps.message.broken_routing = True
    try:
        try: graph.run_graph(ROOT, wire)
        except ValueError as error:
            if not str(error).endswith(': Wrong map codec'): raise
            red = dict(wire=wire.hex(), error=str(error), reader='0x156d35a40',
                selectedByEntryTag=graph.maps.message.by_entry[0x156D35A40]['tag'], requestedTag=3,
                scope='Actual root reader call into unchanged public MapRuntime72.before with two AOT-confirmed shared bodies')
        else: raise ValueError('Expected public shared-map codec conflict was not reproduced')
    finally: graph.maps.message.broken_routing = False
    green = graph.run_graph(ROOT, wire)
    if green['result'] != {'IKIFIBAEDGD': {'7': {'NOEEBKJAFAG': 1}}}: raise ValueError('Dedicated codec-context fix failed')
    return dict(red=red, green=green)


def scan_raw(graph):
    selected = []; count = 0
    for index in sorted(TYPES):
        observed = {}
        for tag in range(1, 65):
            for wire, payload in ((0, varint(0x12345678)), (1, bytes(8)), (2, b'\0'), (5, bytes.fromhex('0000a03f'))):
                row = graph.run_graph(index, varint(tag << 3 | wire) + payload); count += 1
                calls = [x for x in row['entered'] if x.get('kind') == 'native-list-field' and x['ownerType'] == index]
                if row['result'] or row['stores'] or calls:
                    names = list(row['result'])
                    if not names: names = [x['clientField'] for x in calls]
                    if not names: names = [f['clientField'] for f in graph.schemas[index]['fields'] if any(s['typeIndex'] == index and s['objectOffset'] == f['objectOffset'] for s in row['stores'])]
                    if len(set(names)) != 1: raise ValueError('Ambiguous core raw branch: ' + str((index, tag, wire, names)))
                    observed.setdefault(names[0], []).append((tag, wire))
                    selected.append(dict(typeIndex=index, tag=tag, wireType=wire, result=row['result'], entered=row['entered'], stores=row['stores']))
        for field in graph.schemas[index]['fields']:
            bindings = observed.pop(field['clientField'], [])
            if not bindings or len({tag for tag, _ in bindings}) != 1: raise ValueError('Missing/ambiguous core field: ' + field['clientField'])
            wires = sorted(w for _, w in bindings)
            expected = [0, 2] if field.get('packed') else [2] if field['type'] in ('map', 'message') else [5] if field['type'] == 'float' else [0]
            if wires != expected: raise ValueError('Core metadata/wire mismatch: ' + str((field, wires)))
            field.update(tag=bindings[0][0], acceptedWireTypes=wires, semanticStatus='unresolved')
            if field.get('nestedTypeIndex') in BLOCKED: field['contentStatus'] = 'empty-envelope-only; nonempty-refused'
        if observed: raise ValueError('Unexpected core raw fields')
    if sum(len(graph.schemas[i]['fields']) for i in TYPES) != 53: raise ValueError('Core raw field count drift')
    return dict(nativeFixtures=count, scope='6 types, tags1..64/wire0,1,2,5; native list call identifies empty packed branch', fields=selected)


def fixtures():
    cases = [dict(name='all-37-outer-fields-with-allowed-nonempty-children', typeIndex=ROOT,
        wire='080b108180808080808010202c586f6883018001039001019801bf01a801d301b801e701c801fb01d8018f02e801a302f001ad02f801b7028002c1029002d5022a138180808080808010ffffffffffffffffff010032070600ffffffff0f72070e00ffffffff0fa201071400ffffffff0fc201071800ffffffff0f9a02072300ffffffff0fa202072400ffffffff0fb202072600ffffffff0f1a1a08071216082a10ffffffffffffffffff012081808080808080103a070807150000a0bf520d08071209080b120301800118217a040807100f8a010408071011b20104080b1016d201230811108080808080808080800118ffffffffffffffffff01208180808080808010281de2011108ffffffff0f10ffffffffffffffffff014a0062008a0200aa0200',
        expected={'NLNJDCDLHHE': 11, 'FBLKPHOCBIK': 9007199254740993, 'DFENDOGMEAD': 44,
            'BLBHOJMCOJN': 111, 'ACNMNNPAHPA': 131, 'DADPEGADCAC': 3, 'JPDLHLGNJIH': True,
            'MMLPICELLIF': 191, 'HCHEIDINIMO': 211, 'APNACEAKHPN': 231, 'MHAIENEPEIP': 251,
            'GBBBBHEGCDL': 271, 'EACODLJLDIL': 291, 'IBPJHIBEFNP': 301, 'BPGODHNDNNJ': 311,
            'MKFOJONFDMA': 321, 'JIDLIPEAKLI': 341,
            'AODNDDBPMFB': [9007199254740993, 18446744073709551615, 0],
            'KLODODOMJPF': [6, 0, 4294967295], 'HEHFONFNHEH': [14, 0, 4294967295],
            'CBEKIIICDOM': [20, 0, 4294967295], 'IEDIGDHMEEI': [24, 0, 4294967295],
            'EIJBACPBACO': [35, 0, 4294967295], 'LKJJFCJNENG': [36, 0, 4294967295], 'EIBCGPEOJLB': [38, 0, 4294967295],
            'IKIFIBAEDGD': {'7': {'NOEEBKJAFAG': 42, 'BNJKCHONGGE_int64': -1, 'AEJAFPNBBHO': 9007199254740993}},
            'ADJINCFKMML': {'7': -1.25}, 'HLAIJJEMIHE': {'7': {'GMKNIJBNFLK': 11, 'ABAAMEKEIFJ': [1, 128], 'AICLAOPOLPO': 33}},
            'PJGIPBJMBOP': {'7': 15}, 'JOJLFAOOLMH': {'7': 17}, 'DLDNCCJCHOB': [{'JFKPLJIAFJL': 11, 'PKDBMPGJLIJ': 22}],
            'BNHMCGLAMLE': {'OHPFCLOBJEH': 17, 'MKNHMFHLHLK': 9223372036854775808, 'PONFILNCCAA': 18446744073709551615, 'LJMOEFOGCMP': 9007199254740993, 'JJAMEEKFHEK': 29},
            'CMGAIFKIOJP': {'LPNCBONKHNN': 4294967295, 'BHKAKIPABLM': 18446744073709551615},
            'JCKNHCDJFCE': {'untracedType': 16352, 'content': 'empty-envelope-only'},
            'PKPEFNIHPLA': {'untracedType': 66308, 'content': 'empty-envelope-only'},
            'CKDDHADKCBK': {'untracedType': 27196, 'content': 'empty-envelope-only'},
            'AMLLHJJIMNI': [{'untracedType': 53824, 'content': 'empty-envelope-only'}]}),
        dict(name='u64-packed-unpacked-high-upper-zero', typeIndex=ROOT, wire='28002a128180808080808010ffffffffffffffffff01', expected={'AODNDDBPMFB': [0, 9007199254740993, 18446744073709551615]}),
        dict(name='u32-packed-unpacked-upper-zero', typeIndex=66100, wire='1000120601ffffffff0f', expected={'ABAAMEKEIFJ': [0, 1, 4294967295]}),
        dict(name='float-negative-two-keys', typeIndex=ROOT, wire='3a070807150000a0bf3a0b08ffffffff0f1500006040', expected={'ADJINCFKMML': {'7': -1.25, '4294967295': 3.5}}),
        dict(name='float-duplicate-negative-zero', typeIndex=ROOT, wire='3a070807150000a0bf3a0708071500000080', expected={'ADJINCFKMML': {'7': -0.0}}, expectedFloatBits=[{'key': 7, 'valueBits': '0x80000000'}]),
        dict(name='float-key-only-value-only-and-empty-default', typeIndex=ROOT, wire='3a0208073a0515000020c03a00', expected={'ADJINCFKMML': {'7': 0.0, '0': 0.0}}),
        dict(name='float-entry-duplicate-keys-values-unknown', typeIndex=ROOT, wire='3a1008070808150000a0bf15000020c01801', expected={'ADJINCFKMML': {'8': -2.5}}),
        dict(name='message-maps-interleave-same-key-replace', typeIndex=ROOT,
            wire='1a0608071202080152090807120512030180011a09080712051d0000a0bf5206080712021803',
            expected={'IKIFIBAEDGD': {'7': {'BNJKCHONGGE_float': -1.25}}, 'HLAIJJEMIHE': {'7': {'AICLAOPOLPO': 3}}}),
        dict(name='two-u32-maps-duplicate-zero-and-upper', typeIndex=ROOT,
            wire='7a040807100b8a010c08ffffffff0f10ffffffff0f7a0408071000', expected={'PJGIPBJMBOP': {'7': 0}, 'JOJLFAOOLMH': {'4294967295': 4294967295}}),
        dict(name='message-list-two-nonempty-values', typeIndex=ROOT, wire='b20104080b1016b201040821102c', expected={'DLDNCCJCHOB': [{'JFKPLJIAFJL': 11, 'PKDBMPGJLIJ': 22}, {'JFKPLJIAFJL': 33, 'PKDBMPGJLIJ': 44}]}),
        dict(name='five-u64-leaf-distinct-boundaries', typeIndex=27246, wire='0811108080808080808080800118ffffffffffffffffff01208180808080808010281d',
            expected={'OHPFCLOBJEH': 17, 'MKNHMFHLHLK': 9223372036854775808, 'PONFILNCCAA': 18446744073709551615, 'LJMOEFOGCMP': 9007199254740993, 'JJAMEEKFHEK': 29}),
        dict(name='two-u32-leaf-upper-and-zero', typeIndex=13438, wire='08ffffffff0f10011000', expected={'JFKPLJIAFJL': 4294967295}),
        dict(name='u32-u64-leaf-upper', typeIndex=40807, wire='08ffffffff0f10ffffffffffffffffff01', expected={'LPNCBONKHNN': 4294967295, 'BHKAKIPABLM': 18446744073709551615}),
        dict(name='root-scalar-wrong-wire-duplicate-zero', typeIndex=ROOT, wire='080108000a0010011000900102900100', expected={}),
        dict(name='enum-signed-underlying-raw-boundary', typeIndex=ROOT, wire='8001ffffffffffffffffff01', expected={'DADPEGADCAC': -1}),
        dict(name='prop-value-oneof-zero', typeIndex=VALUE, wire='1000', expected={'BNJKCHONGGE_int64': 0}),
        dict(name='prop-value-float-switch', typeIndex=VALUE, wire='10011d0000a0bf', expected={'BNJKCHONGGE_float': -1.25}),
        dict(name='prop-value-independent-int64-signed-min', typeIndex=VALUE, wire='2080808080808080808001', expected={'AEJAFPNBBHO': -9223372036854775808})]
    for index in sorted(TYPES): cases.append(dict(name='empty-' + str(index), typeIndex=index, wire='', expected={}))
    return cases


def verify(graph):
    good = []
    for case in fixtures():
        row = graph.run_graph(case['typeIndex'], bytes.fromhex(case['wire']))
        if row['result'] != case['expected']: raise ValueError(case['name'] + ': core native result differs ' + str(row['result']))
        if 'expectedFloatBits' in case and row['floatMapNodes'] != case['expectedFloatBits']: raise ValueError('Float storage bits differ')
        good.append(dict(case, observation=row))
    rejected = []
    errors = [('nonempty-child-16352', '4a020801', 'explicit-scope-refusal', 100),
        ('nonempty-child-66308', '62020801', 'explicit-scope-refusal', 100),
        ('nonempty-child-27196', '8a02020801', 'explicit-scope-refusal', 100),
        ('nonempty-child-53824', 'aa02020801', 'explicit-scope-refusal', 100),
        ('invalid-tag', '00', 'explicit-input-model', 100),
        ('u64-overflow', '10ffffffffffffffffff02', 'explicit-input-model', 100),
        ('truncated-packed-u64', '2a0180', 'explicit-input-model', 100),
        ('truncated-map-float', '3a0408071500', 'explicit-input-model', 100),
        ('negative-nested-length', 'd201ffffffff0f', 'native-error-entry', 100),
        ('truncated-nested-envelope', 'd201030801', 'native-error-entry', 100),
        ('nested-recursion-limit', 'd20100', 'native-error-entry', 0)]
    for name, wire, scope, limit in errors:
        try: graph.run_graph(ROOT, bytes.fromhex(wire), limit)
        except ValueError as error:
            message = str(error)
            if scope == 'explicit-scope-refusal' and 'Nonempty child outside F-03d3b' not in message: raise
            if scope == 'native-error-entry' and 'Native error branch:' not in message: raise
            rejected.append(dict(name=name, wire=wire, scope=scope, error=message))
        else: raise ValueError('Invalid/out-of-scope core input accepted: ' + name)
    return good, rejected


def main():
    p = argparse.ArgumentParser(description=__doc__); p.add_argument('--client', required=True, type=Path)
    p.add_argument('--output', required=True, type=Path); p.add_argument('--observations', required=True, type=Path)
    p.add_argument('--directory', type=Path, default=Path('protocol/cn-7.2')); p.add_argument('--python-libs', type=Path, default=Path('local/migration72/python')); a = p.parse_args()
    paths = [a.output.resolve(), a.observations.resolve()]
    if len(set(paths)) != 2 or any(x.is_relative_to(a.client.resolve()) for x in paths): p.error('Distinct outputs outside client required')
    b, m, s = [read_verified(a.client / name, digest) for name, digest in HASHES.items()]; records, _ = extract(b, m, startup=s, structures=True)
    cells = type_cells(b, m, s, records); bindings = core_aot(b, m, s, records)
    e = Offline72(b, a.python_libs, records, trace_instructions=False); graph = AvatarCore72(e, a.directory, cells, bindings)
    try: reproduction = reproduce_codec_conflict(graph); scan = scan_raw(graph); good, rejected = verify(graph)
    finally: graph.close(); e.close()
    context = dict(schemaVersion=1, task='F-03d3b', inputSHA256=HASHES, runtimeStatus='not-tested', targetProtocolReady=False,
        semanticIdentityStatus='unresolved', fullAvatarRawClosure=False, blockedTypeIndices=sorted(BLOCKED),
        aotBindings=bindings, initializers=graph.initializers, codecConflict=reproduction,
        messages=[graph.schemas[i] for i in sorted(TYPES)], scan=scan, nativeFixtures=len(good), rejections=rejected,
        limitations=['Only48178 outer37 + four leaves12 + reused78350 four; four complex children nonempty refused',
            'Managed allocation/class/generic contexts, primitive input/delegates and fixed-capacity64 map dictionary/linked storage are explicit fixtures',
            'Native reader/ReadMessage limits/map adapter/setter/list append execute; native errors stop at error entry before throw',
            'No complete Avatar graph, named identity, field semantics, handler/live ABI/client acceptance or production integration'])
    summary = dict(context, fixtures=[dict(name=x['name'], typeIndex=x['typeIndex'], wire=x['wire'], result=x['observation']['result'], entered=x['observation']['entered'], mapStores=x['observation']['mapStores'], floatMapNodes=x['observation']['floatMapNodes']) for x in good])
    for path, data in zip(paths, [dict(context, fixtures=good), summary]):
        path.parent.mkdir(parents=True, exist_ok=True); path.write_text(json.dumps(data, indent=2) + '\n', 'utf8')
    print(json.dumps(dict(messages=6, fields=53, scanFixtures=scan['nativeFixtures'], nativeFixtures=len(good), rejections=len(rejected))))


if __name__ == '__main__': main()
