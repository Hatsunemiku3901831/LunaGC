"""F-03d3e4: signed int32-list and three same-shape Avatar subtree owners.

Executes concrete readers and append; managed allocation/primitive input remain
explicit models. Complex type51050 is still refused when nonempty.
"""
import argparse
import hashlib
import json
import struct
from pathlib import Path
try:
    from .extract_client72 import HASHES, PE, HEADER_VA, CODE_REGISTRATION, TypeResolver, number, read_verified, extract
    from .client72_emulator import Offline72, STREAM, STACK
    from .client72_graph import GraphReader72
    from .trace_client72_avatar_core import core_aot
    from .trace_client72_avatar_complex_outer import string_map_aot
    from .trace_client72_avatar_primitive_leaves import AvatarPrimitiveLeaves72
    from .trace_client72_avatar_items import INVENTORY_SHA
    from .trace_client72_player_store import type_cells
    from .trace_client72_token import varint, load_emulator
except ImportError:
    from extract_client72 import HASHES, PE, HEADER_VA, CODE_REGISTRATION, TypeResolver, number, read_verified, extract
    from client72_emulator import Offline72, STREAM, STACK
    from client72_graph import GraphReader72
    from trace_client72_avatar_core import core_aot
    from trace_client72_avatar_complex_outer import string_map_aot
    from trace_client72_avatar_primitive_leaves import AvatarPrimitiveLeaves72
    from trace_client72_avatar_items import INVENTORY_SHA
    from trace_client72_player_store import type_cells
    from trace_client72_token import varint, load_emulator

TYPES = {19829, 21103, 89623}


def selected_aot(binary, metadata, startup, records, selections):
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
        if not 0 <= pointer < count or not 0 <= spec < spec_count: raise ValueError('Selected Avatar AOT bounds')
        method, cls, args = struct.unpack_from('<iii', metadata, specs + spec * 12); owner, row = methods[method]
        if (owner['index'], cls) not in selections or row['name'] not in selections[owner['index'], cls]: continue
        if args != -1: raise ValueError('Unexpected Avatar AOT method arguments')
        address = number(binary, pointers + pointer * 8, 'Q'); pe.offset(address, executable=True)
        rows.append(dict(owner=owner['index'], classInstance=cls, method=row['name'], methodDefinition=method,
            tableIndex=i, methodSpec=spec, pointerIndex=pointer, address=hex(address), arguments=[resolver.resolve_index(x) for x in resolver.arguments[cls]]))
    return rows


def signed_aot(binary, metadata, startup, records):
    rows = selected_aot(binary, metadata, startup, records, {
        (35584, 6): ('.ctor', '.cctor', 'FNNADLCNGFO', 'Add', 'set_Item'),
        (45077, 6): ('.ctor', '.cctor'), (25060, 6112): ('.ctor', 'FNNADLCNGFO')})
    for owner, method, address in [(35584, 'FNNADLCNGFO', '0x1568b8d60'), (45077, '.cctor', '0x155b36190'), (25060, 'FNNADLCNGFO', '0x158dc41f0')]:
        hits = [x for x in rows if x['owner'] == owner and x['method'] == method]
        if len(hits) != 1 or hits[0]['address'] != address: raise ValueError('Signed Avatar AOT drift')
    if len(rows) != 12 or not any(x['address'] == '0x1568b8b10' and x['arguments'][0]['kind'] == 'int32' for x in rows): raise ValueError('Signed scalar Add AOT drift')
    return rows


def dependency_audit(binary, records, cells, types, expected_children, storage, python_libs):
    load_emulator(python_libs)
    from capstone import Cs, CS_ARCH_X86, CS_MODE_64
    md = Cs(CS_ARCH_X86, CS_MODE_64); md.detail = True; pe = PE(binary)
    ends = sorted({int(m['registeredAddress'], 16) for t in records for m in t['methods'] if m['registeredAddress'] != '0x0'})
    references = []; dependencies = set(); rows = []
    for index in sorted(types):
        row = records[index]; fields = [f for f in row['fields'] if f['offsetKind'] == 'instance']
        def add(typ):
            for arg in typ.get('arguments', [typ]):
                child = arg.get('typeDefinition')
                if arg['kind'] == 'class' and child is not None and any(m['name'] == 'MOMMACBEMEK' for m in records[child]['methods']): dependencies.add(child)
        for f in fields: add(f['type'])
        methods = [m for m in row['methods'] if m['name'] in ('MOMMACBEMEK', '.cctor')]
        for method in methods:
            entry = int(method['registeredAddress'], 16); end = next(x for x in ends if x > entry)
            raw = binary[pe.offset(entry):pe.offset(entry) + end - entry]
            if raw[:1] == b'\xe9': raise ValueError('Unexpected protected subtree reader')
            for ins in md.disasm(raw, entry):
                for operand in ins.operands:
                    if operand.type == 3 and operand.mem.base == 41:
                        cell = ins.address + ins.size + operand.mem.disp
                        if cell in cells:
                            references.append(dict(owner=index, method=method['name'], site=hex(ins.address), **cells[cell])); add(cells[cell]['type'])
        rows.append(dict(typeIndex=index, clientType=row['name'], fields=fields, methods=methods))
    if dependencies - types != expected_children or sum(len(x['fields']) for x in rows) != storage: raise ValueError('Avatar subtree dependency/storage drift')
    return dict(typeIndices=sorted(types), metadataStorageFields=storage, nestedMessageDependencies=sorted(dependencies - types), types=rows, nativeUsageReferences=references)


def install_types(graph, cells, types):
    e = graph.e
    for index in sorted(types):
        row = e.records[index]; entry = next(int(m['registeredAddress'], 16) for m in row['methods'] if m['name'] == 'MOMMACBEMEK')
        klass = graph.classes.get(index)
        if klass is None:
            klass = e.alloc(1024); table = e.alloc(16); e.write(klass + 0xC7, 1, 'B')
            e.write(klass + 0x80, table); e.write(klass + 0xC0, 1, 'H'); e.write(klass + 0xBE, 32, 'H')
            e.write(table, graph.interface); e.write(klass + 0xE8, entry)
        graph.classes[index] = klass; graph.class_indices[klass] = index; graph.entries[index] = entry; e.allow(entry)
        fields = []
        for f in row['fields']:
            if f['offsetKind'] != 'instance': continue
            typ = f['type']; field = dict(clientField=f['name'], objectOffset=f['offset'], type=typ['kind'], cardinality='singular')
            if typ['kind'] == 'class': field.update(type='message', nestedTypeIndex=typ['typeDefinition'])
            elif typ['kind'] == 'genericinst':
                if len(typ['arguments']) != 1: raise ValueError('Unexpected subtree map')
                child = typ['arguments'][0]; field.update(type=child['kind'], cardinality='repeated')
                if child['kind'] == 'class': field.update(type='message', nestedTypeIndex=child['typeDefinition'])
                else: field['packed'] = True
            elif typ['kind'] == 'valuetype': field.update(type=next(x['type']['kind'] for x in e.records[typ['typeDefinition']]['fields'] if x['name'] == 'value__'), enumTypeIndex=typ['typeDefinition'])
            elif typ['kind'] not in ('int32', 'uint32', 'int64', 'uint64', 'bool', 'float'): raise ValueError('Unexpected subtree scalar')
            fields.append(field)
        graph.schemas[index] = dict(typeIndex=index, clientType=row['name'], fields=fields)
    for cell, row in cells.items():
        typ = row['type']
        if typ['kind'] == 'class' and typ.get('typeDefinition') in types: e.write(cell, graph.classes[typ['typeDefinition']])


def initialize_types(graph, types):
    e = graph.e; rows = []
    for index in sorted(types):
        for method in e.records[index]['methods']:
            if method['name'] != '.cctor': continue
            before = len(graph.factory_calls); entry = int(method['registeredAddress'], 16); e.allow(entry); e.prepare(); e.run(entry)
            rows.append(dict(typeIndex=index, entry=hex(entry), factoryArguments=graph.factory_calls[before:]))
    graph.heap_floor = e.heap_cursor
    return rows


class AvatarSignedTrees72(AvatarPrimitiveLeaves72):
    def __init__(self, engine, directory, cells, bindings, items, strings):
        self.signed_list_stores = []; super().__init__(engine, directory, cells, bindings, items, strings); e = self.e
        install_types(self, cells, TYPES)
        self.box_classes[e.synthetic_type(0x145886118)] = ('int32', 4)
        helper = e.synthetic_type(0x14592A138); block = e.alloc(); e.write(helper + 0x38, block)
        self.values = {}; e.allow_unwind(0x155B36190); e.prepare(); e.uc.reg_write(e.R.UC_X86_REG_RSP, STACK + 0x100008)
        e.uc.emu_start(0x155B36190, 0x155B361C0, timeout=20_000_000, count=1_000_000)
        if e.failure or e.reg('rip') != 0x155B361C0: raise ValueError(e.failure or 'Signed helper prefix boundary')
        self.signed_helper_prefix = dict(entry='0x155b36190', stopBefore='0x155b361c0', flagStore='0x155b361bd', observedPackedFlag=e.read(block, 'B'), scope='Native prefix; int32 boxing model; reflection remainder not executed')
        if self.signed_helper_prefix['observedPackedFlag'] != 1: raise ValueError('Signed helper native flag drift')
        self.signed_list_class = e.synthetic_type(0x145897788); block = e.alloc(); e.write(self.signed_list_class + 0x38, block)
        for entry in (0x1568B8D60, 0x1568B8B10): e.allow_unwind(entry)
        e.models[0x14E5DEDF0] = self.signed_codec_factory
        self.signed_initializers = initialize_types(self, TYPES)

    def signed_codec_factory(self):
        e = self.e; tag = e.reg('ecx'); codec, delegate = e.alloc(), e.alloc()
        # Independently inspected 45077/6 ctor155b36420: reader +20, tag +2c.
        e.write(codec + 0x2C, tag, 'I'); e.write(codec + 0x20, delegate)
        e.write(delegate + 0x18, 0x147520110); e.write(delegate + 0x40, STREAM)
        self.factory_calls.append(dict(kind='int32', tag=tag, scope='primitive-codec/delegate model', readerOffset=32, tagOffset=44)); return codec

    def new_message(self, index, address=None):
        if index not in TYPES: return super().new_message(index, address)
        return self.new_subtree(index, address)

    def new_subtree(self, index, address=None):
        pointer = GraphReader72.new_message(self, index, address)
        for f in self.schemas[index]['fields']:
            if f['cardinality'] == 'repeated' and f['type'] == 'int32':
                container = self.e.read(pointer + f['objectOffset']); array = self.new_array(4)
                self.e.write(container, self.signed_list_class); self.e.write(container + 16, array)
                self.containers[container].update(width=4, capacity=64, primitiveSigned=True)
        return pointer

    def snapshot(self, pointer, depth=0):
        if not pointer or self.objects[pointer] not in TYPES: return super().snapshot(pointer, depth)
        return self.subtree_snapshot(pointer, depth)

    def subtree_snapshot(self, pointer, depth=0):
        result = GraphReader72.snapshot(self, pointer, depth)
        for f in self.schemas[self.objects[pointer]]['fields']:
            if f['cardinality'] == 'repeated' and f['type'] == 'int32':
                container = self.e.read(pointer + f['objectOffset']); count = self.e.read(container + 24, 'I'); array = self.e.read(container + 16)
                if count: result[f['clientField']] = [self.e.read(array + 32 + i * 4, 'i') for i in range(count)]
            elif f['type'] == 'int64':
                value = self.e.read(pointer + f['objectOffset'], 'q')
                if value: result[f['clientField']] = value
        return result

    def observe_entry(self, address):
        if self.active and address in (0x1568B8D60, 0x158DB8E10, 0x1571DDD60):
            container = self.e.reg('rcx')
            for pointer, index in self.objects.items():
                for f in self.schemas[index]['fields']:
                    if f['cardinality'] == 'repeated' and f['type'] in ('int32', 'uint32', 'uint64') and self.e.read(pointer + f['objectOffset']) == container:
                        self.entered.append(dict(kind='native-signed-tree-list-field', ownerType=index, clientField=f['clientField'], objectOffset=f['objectOffset'], key=self.e.read(STREAM + 0x20, 'I'), entry=hex(address)))
        if self.active and address == 0x1568B8B10: self.entered.append(dict(kind='native-int32-list-add', entry=hex(address)))
        return super().observe_entry(address)

    def graph_write(self, uc, access, address, size, value, data):
        super().graph_write(uc, access, address, size, value, data)
        if not self.active: return
        for container, info in self.containers.items():
            if not info.get('primitiveSigned'): continue
            array = self.e.read(container + 16)
            if array + 32 <= address < array + 32 + info['capacity'] * 4:
                self.signed_list_stores.append(dict(arrayByteOffset=address - array - 32, size=size, valueBits=hex(value & 0xffffffff), source=hex(self.e.reg('rip'))))

    def run_graph(self, index, data, recursion_limit=100):
        self.signed_list_stores = []; row = super().run_graph(index, data, recursion_limit); row['signedListStores'] = self.signed_list_stores; return row


def scan_types(graph, types, storage, max_tag=32):
    selected = []; count = 0
    for index in sorted(types):
        observed = {}
        for tag in range(1, max_tag + 1):
            for wire, payload in ((0, varint(0x12345678)), (1, bytes(8)), (2, b'\0'), (5, bytes.fromhex('0000a03f'))):
                row = graph.run_graph(index, varint(tag << 3 | wire) + payload); count += 1
                calls = [x for x in row['entered'] if x.get('kind') == 'native-signed-tree-list-field' and x['ownerType'] == index]
                if row['result'] or row['stores'] or calls:
                    names = list(row['result']) or [x['clientField'] for x in calls]
                    if not names: names = [f['clientField'] for f in graph.schemas[index]['fields'] if any(s['typeIndex'] == index and s['objectOffset'] == f['objectOffset'] for s in row['stores'])]
                    if len(set(names)) != 1: raise ValueError('Ambiguous subtree raw branch: ' + str((index, tag, wire, names)))
                    observed.setdefault(names[0], []).append((tag, wire)); selected.append(dict(typeIndex=index, tag=tag, wireType=wire, result=row['result'], entered=row['entered'], stores=row['stores']))
        for field in graph.schemas[index]['fields']:
            bindings = observed.pop(field['clientField'], [])
            if not bindings or len({tag for tag, _ in bindings}) != 1: raise ValueError('Missing subtree field: ' + str((index, field)))
            wires = sorted(w for _, w in bindings)
            expected = [2] if field['type'] == 'message' else [0, 2] if field['cardinality'] == 'repeated' else [5] if field['type'] == 'float' else [0]
            if wires != expected: raise ValueError('Subtree metadata/wire mismatch')
            field.update(tag=bindings[0][0], acceptedWireTypes=wires, semanticStatus='unresolved')
        if observed: raise ValueError('Unexpected subtree raw field')
    if sum(len(graph.schemas[i]['fields']) for i in types) != storage: raise ValueError('Subtree raw field count drift')
    return dict(nativeFixtures=count, scope=f'{len(types)} types, tags1..{max_tag}/wire0,1,2,5; independent metadata/wire matching', fields=selected)


def fixtures():
    return [{'name': 'all-fields-19829',
  'typeIndex': 19829,
  'wire': '08ffffffffffffffffff01108180808080808010188080808080808080800120022a0d08f5ffffffffffffffff011016304238ffffffffffffffffff01420d08dfffffffffffffffff01102c4880808080f8ffffffff015208008001ffffffff0f5879600168f1feffffffffffffff01',
  'expected': {'GBHHCKCBGEO': -1,
               'HOPABANFAJI': 9007199254740993,
               'NIHNNHMGENL': 9223372036854775808,
               'EBPLPBLFIMM': True,
               'HHAFHFEPLPD': {'CJMJBINEBAG': -11, 'ALOKJBKPLGA': 22},
               'AANCEPCPHKJ': 66,
               'OOKFBIPCHBO': 18446744073709551615,
               'FANOIJAHMKN': {'CJMJBINEBAG': -33, 'ALOKJBKPLGA': 44},
               'HPLBLGJLIFF': -2147483648,
               'HCMGPBLEPNC': [0, 128, 4294967295],
               'MKGAOEBNHEN': 121,
               'HPCEGOFOCDK': True,
               'KMGIJJLGPMJ': -143}},
 {'name': 'all-fields-21103',
  'typeIndex': 21103,
  'wire': '080b1821202c283730424863506e5879608401688f01709a0178a5018001b001120d08c9ffffffffffffffff0110424080808080f8ffffffff01',
  'expected': {'PJAJDHBBMGB': 11,
               'IAHPPLKIIBK': 33,
               'KOCFLGMCFJF': 44,
               'LDNIJIHJDCI': 55,
               'JFPJMHPNCJC': 66,
               'IAEMKIANGPG': 99,
               'CJEGOKFEHNI': 110,
               'BBMEHEDHDIG': 121,
               'GKMEFGNOBCK': 132,
               'BHPKAFCOABG': 143,
               'GMPOLEMIECO': 154,
               'DAJIKMKBOBD': 165,
               'ODKDJGDHHMI': 176,
               'HHAFHFEPLPD': {'CJMJBINEBAG': -55, 'ALOKJBKPLGA': 66},
               'HPLBLGJLIFF': -2147483648}},
 {'name': 'all-fields-89623',
  'typeIndex': 89623,
  'wire': '080b120d08ffffffffffffffffff01100218dfffffffffffffffff01220d08fdffffffffffffffff01100428373208008001ffffffff0f3a0d08fbffffffffffffffff0110063a0d08f9ffffffffffffffff0110084a1cffffffffffffffffff0180808080f8ffffffff01ffffffff070080015879608401688f0170e6feffffffffffffff01',
  'expected': {'ANKEOLKBECN': 11,
               'HHAFHFEPLPD': {'CJMJBINEBAG': -1, 'ALOKJBKPLGA': 2},
               'PCHKJMKBABB': -33,
               'PODNCONEGKP': {'CJMJBINEBAG': -3, 'ALOKJBKPLGA': 4},
               'ELDBHKCPKMN': 55,
               'FEJJCDNNECH': [0, 128, 4294967295],
               'AOMDFCPMDOP': [{'CJMJBINEBAG': -5, 'ALOKJBKPLGA': 6}, {'CJMJBINEBAG': -7, 'ALOKJBKPLGA': 8}],
               'MLKKEOCOAKN': [-1, -2147483648, 2147483647, 0, 128],
               'AHNAKLJIIIM': 121,
               'GANIFCPLHKE': 132,
               'MKGAOEBNHEN': 143,
               'BEKEIEFAIDD': -154}},
 {'name': 'signed-packed-unpacked-append',
  'typeIndex': 89623,
  'wire': '48ffffffffffffffffff014a0f80808080f8ffffffff01ffffffff0748004a028001',
  'expected': {'MLKKEOCOAKN': [-1, -2147483648, 2147483647, 0, 128]}},
 {'name': 'signed-empty-packed-preserves-prior',
  'typeIndex': 89623,
  'wire': '48efffffffffffffffff014a00',
  'expected': {'MLKKEOCOAKN': [-17]}},
 {'name': 'signed-five-byte-low-bits', 'typeIndex': 89623, 'wire': '48ffffffff0f', 'expected': {'MLKKEOCOAKN': [-1]}},
 {'name': 'signed-wrong-wire-skipped', 'typeIndex': 89623, 'wire': '4d000080ff48efffffffffffffffff01', 'expected': {'MLKKEOCOAKN': [-17]}},
 {'name': 'signed-negative-packed-length-no-append', 'typeIndex': 89623, 'wire': '4affffffff0f', 'expected': {}},
 {'name': 'u32-packed-unpacked-19829', 'typeIndex': 19829, 'wire': '5000520601ffffffff0f', 'expected': {'HCMGPBLEPNC': [0, 1, 4294967295]}},
 {'name': 'u32-packed-unpacked-89623', 'typeIndex': 89623, 'wire': '3000320601ffffffff0f', 'expected': {'FEJJCDNNECH': [0, 1, 4294967295]}},
 {'name': 'same-shape-children-merge-independently',
  'typeIndex': 19829,
  'wire': '2a0d08f5ffffffffffffffff011016420d08dfffffffffffffffff01102c2a0210374200',
  'expected': {'HHAFHFEPLPD': {'CJMJBINEBAG': -11, 'ALOKJBKPLGA': 55}, 'FANOIJAHMKN': {'CJMJBINEBAG': -33, 'ALOKJBKPLGA': 44}}},
 {'name': 'same-shape-list-and-children-remain-distinct',
  'typeIndex': 89623,
  'wire': '120d08ffffffffffffffffff011002220d08fdffffffffffffffff0110043a0d08fbffffffffffffffff0110063a0d08f9ffffffffffffffff0110083a0012020800',
  'expected': {'HHAFHFEPLPD': {'ALOKJBKPLGA': 2},
               'PODNCONEGKP': {'CJMJBINEBAG': -3, 'ALOKJBKPLGA': 4},
               'AOMDFCPMDOP': [{'CJMJBINEBAG': -5, 'ALOKJBKPLGA': 6}, {'CJMJBINEBAG': -7, 'ALOKJBKPLGA': 8}, {}]}},
 {'name': 'uint64-three-distinct-nonzero',
  'typeIndex': 19829,
  'wire': '10ffffffffffffffffff011881808080808080103880808080808080808001',
  'expected': {'HOPABANFAJI': 18446744073709551615, 'NIHNNHMGENL': 9007199254740993, 'OOKFBIPCHBO': 9223372036854775808}},
 {'name': 'empty-19829', 'typeIndex': 19829, 'wire': '', 'expected': {}},
 {'name': 'empty-21103', 'typeIndex': 21103, 'wire': '', 'expected': {}},
 {'name': 'empty-89623', 'typeIndex': 89623, 'wire': '', 'expected': {}},
 {'name': 'scalar-signed-bool-u64-duplicate-zero',
  'typeIndex': 19829,
  'wire': '08ffffffffffffffffff010800100110001801180020012000300130003801380048ffffffffffffffffff014800580158006001600068ffffffffffffffffff016800',
  'expected': {}},
 {'name': 'signed-fixture-capacity-64',
  'typeIndex': 89623,
  'wire': '4ae00200ffffffffffffffffff0102fdffffffffffffffff0104fbffffffffffffffff0106f9ffffffffffffffff0108f7ffffffffffffffff010af5ffffffffffffffff010cf3ffffffffffffffff010ef1ffffffffffffffff0110efffffffffffffffff0112edffffffffffffffff0114ebffffffffffffffff0116e9ffffffffffffffff0118e7ffffffffffffffff011ae5ffffffffffffffff011ce3ffffffffffffffff011ee1ffffffffffffffff0120dfffffffffffffffff0122ddffffffffffffffff0124dbffffffffffffffff0126d9ffffffffffffffff0128d7ffffffffffffffff012ad5ffffffffffffffff012cd3ffffffffffffffff012ed1ffffffffffffffff0130cfffffffffffffffff0132cdffffffffffffffff0134cbffffffffffffffff0136c9ffffffffffffffff0138c7ffffffffffffffff013ac5ffffffffffffffff013cc3ffffffffffffffff013ec1ffffffffffffffff01',
  'expected': {'MLKKEOCOAKN': [0,
                               -1,
                               2,
                               -3,
                               4,
                               -5,
                               6,
                               -7,
                               8,
                               -9,
                               10,
                               -11,
                               12,
                               -13,
                               14,
                               -15,
                               16,
                               -17,
                               18,
                               -19,
                               20,
                               -21,
                               22,
                               -23,
                               24,
                               -25,
                               26,
                               -27,
                               28,
                               -29,
                               30,
                               -31,
                               32,
                               -33,
                               34,
                               -35,
                               36,
                               -37,
                               38,
                               -39,
                               40,
                               -41,
                               42,
                               -43,
                               44,
                               -45,
                               46,
                               -47,
                               48,
                               -49,
                               50,
                               -51,
                               52,
                               -53,
                               54,
                               -55,
                               56,
                               -57,
                               58,
                               -59,
                               60,
                               -61,
                               62,
                               -63]}},
 {'name': 'singular-explicit-empty-both-children',
  'typeIndex': 19829,
  'wire': '2a004200',
  'expected': {'HHAFHFEPLPD': {}, 'FANOIJAHMKN': {}}}]


def verify(graph):
    good = []
    for case in fixtures():
        row = graph.run_graph(case['typeIndex'], bytes.fromhex(case['wire']))
        if row['result'] != case['expected']: raise ValueError(case['name'] + ': signed subtree native result differs ' + str(row['result']))
        good.append(dict(case, observation=row))
    rejected = []
    errors = [('nonempty-child-51050', 34045, '2a020801', 'explicit-scope-refusal', 100),
        ('invalid-tag', 89623, '00', 'explicit-input-model', 100),
        ('signed-varint-overflow', 89623, '48ffffffffffffffffff02', 'explicit-input-model', 100),
        ('truncated-signed-packed', 89623, '4a0180', 'explicit-input-model', 100),
        ('uint64-overflow', 19829, '10ffffffffffffffffff02', 'explicit-input-model', 100),
        ('negative-message-length', 19829, '2affffffff0f', 'native-error-entry', 100),
        ('truncated-message', 19829, '2a030801', 'native-error-entry', 100),
        ('singular-recursion-limit', 19829, '2a00', 'native-error-entry', 0),
        ('list-recursion-limit', 89623, '3a00', 'native-error-entry', 0)]
    payload = bytes(65); errors.append(('signed-fixture-capacity-65-unsupported-growth', 89623, (b'\x4a' + varint(len(payload)) + payload).hex(), 'fixture-model-boundary', 100))
    for name, index, wire, scope, limit in errors:
        try: graph.run_graph(index, bytes.fromhex(wire), limit)
        except ValueError as error:
            text = str(error)
            if scope == 'explicit-scope-refusal' and 'Nonempty child outside F-03d3e2' not in text: raise
            if scope == 'native-error-entry' and 'Native error branch:' not in text: raise
            if scope == 'fixture-model-boundary' and 'Unmodeled native call 0x140514ee0' not in text: raise
            rejected.append(dict(name=name, typeIndex=index, wire=wire, scope=scope, error=text))
        else: raise ValueError('Invalid/out-of-scope signed subtree input accepted: ' + name)
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
    cells = type_cells(b, m, s, records); aot = signed_aot(b, m, s, records); bindings = core_aot(b, m, s, records); strings = string_map_aot(b, m, s, records)
    audit = dependency_audit(b, records, cells, TYPES, {68918}, 40, a.python_libs)
    e = Offline72(b, a.python_libs, records, trace_instructions=False); graph = AvatarSignedTrees72(e, a.directory, cells, bindings, items, strings)
    try: scan = scan_types(graph, TYPES, 40); good, rejected = verify(graph)
    finally: graph.close(); e.close()
    context = dict(schemaVersion=1, task='F-03d3e4', inputSHA256=HASHES, runtimeStatus='not-tested', targetProtocolReady=False,
        semanticIdentityStatus='unresolved', fullAvatarRawClosure=False, fullComplexRawClosure=False, blockedTypeIndices=[51050],
        aotBindings=aot, initializers=graph.signed_initializers, signedHelperPrefix=graph.signed_helper_prefix,
        signedTypeUsage=[cells[x] for x in (0x14592A138, 0x145897788, 0x145886168, 0x145886118)],
        dependencyScope=audit, messages=[graph.schemas[i] for i in sorted(TYPES)], scan=scan, nativeFixtures=len(good), rejections=rejected,
        limitations=['Three raw subtree owners only;68918 reused;51050 nonempty remains refused',
            'Signed concrete reader/Add and helper flag prefix execute; full helper reflection/list cctor not executed',
            'Primitive codecs/delegates/input, boxing, managed class/null-policy/allocation and fixed64 arrays are explicit models',
            'No business semantics, complete complex graph, live/client acceptance or production integration'])
    summary = dict(context, fixtures=[dict(name=x['name'], typeIndex=x['typeIndex'], wire=x['wire'], result=x['observation']['result'], entered=x['observation']['entered'], stores=x['observation']['stores'], signedListStores=x['observation']['signedListStores']) for x in good])
    for path, data in zip(paths, [dict(context, fixtures=good), summary]):
        path.parent.mkdir(parents=True, exist_ok=True); path.write_text(json.dumps(data, indent=2) + '\n', 'utf8')
    print(json.dumps(dict(messages=len(TYPES), storage=40, wireFields=40, scanFixtures=scan['nativeFixtures'], nativeFixtures=len(good), rejections=len(rejected))))


if __name__ == '__main__': main()
