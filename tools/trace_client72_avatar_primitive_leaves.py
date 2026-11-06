"""F-03d3e3: eleven independent scalar/primitive-list Avatar leaves.

Float-list concrete reader and scalar Add execute natively with four-byte arrays.
Signed lists and complex child51050 remain outside this slice.
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
    from .trace_client72_avatar_unions import AvatarUnions72
    from .trace_client72_avatar_items import INVENTORY_SHA
    from .trace_client72_player_store import type_cells
    from .trace_client72_token import varint
except ImportError:
    from extract_client72 import HASHES, PE, HEADER_VA, CODE_REGISTRATION, TypeResolver, number, read_verified, extract
    from client72_emulator import Offline72, STREAM, STACK
    from client72_graph import GraphReader72
    from trace_client72_avatar_core import core_aot
    from trace_client72_avatar_complex_outer import string_map_aot
    from trace_client72_avatar_unions import AvatarUnions72
    from trace_client72_avatar_items import INVENTORY_SHA
    from trace_client72_player_store import type_cells
    from trace_client72_token import varint

TYPES = {21518, 38218, 48088, 51614, 67498, 68918, 12876, 18849, 31113, 65248, 66229}


def leaf_lists_aot(binary, metadata, startup, records):
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
        if not 0 <= pointer < count or not 0 <= spec < spec_count: raise ValueError('Primitive leaf AOT bounds')
        method, cls, args = struct.unpack_from('<iii', metadata, specs + spec * 12); owner, row = methods[method]
        if not (owner['index'] == 35584 and cls in (11, 78) or owner['index'] == 45077 and cls == 78 and row['name'] == '.cctor'): continue
        address = number(binary, pointers + pointer * 8, 'Q')
        if row['name'] not in ('.ctor', '.cctor', 'FNNADLCNGFO', 'set_Item', 'Add') and address != 0x1571DC010: continue
        if args != -1: raise ValueError('Unexpected primitive leaf AOT method arguments')
        pe.offset(address, executable=True)
        rows.append(dict(owner=owner['index'], classInstance=cls, method=row['name'], methodDefinition=method,
            tableIndex=i, methodSpec=spec, pointerIndex=pointer, address=hex(address), arguments=[resolver.resolve_index(x) for x in resolver.arguments[cls]]))
    for instance, address in [(11, 0x158DB8E10), (78, 0x1571DC270)]:
        hits = [x for x in rows if (x['classInstance'], x['method']) == (instance, 'FNNADLCNGFO')]
        if len(hits) != 1 or int(hits[0]['address'], 16) != address: raise ValueError('Primitive leaf reader AOT drift')
    add = [x for x in rows if x['address'] == '0x1571dc010']
    if len(add) != 1 or add[0]['arguments'][0]['kind'] != 'float': raise ValueError('Float scalar Add AOT drift')
    return rows


class AvatarPrimitiveLeaves72(AvatarUnions72):
    def __init__(self, engine, directory, cells, bindings, items, strings):
        self.float_list_stores = []; super().__init__(engine, directory, cells, bindings, items, strings); e = self.e
        for index in TYPES:
            row = e.records[index]; entry = next(int(m['registeredAddress'], 16) for m in row['methods'] if m['name'] == 'MOMMACBEMEK')
            klass = e.alloc(1024); table = e.alloc(16); e.write(klass + 0xC7, 1, 'B')
            e.write(klass + 0x80, table); e.write(klass + 0xC0, 1, 'H'); e.write(klass + 0xBE, 32, 'H')
            e.write(table, self.interface); e.write(klass + 0xE8, entry)
            self.classes[index] = klass; self.class_indices[klass] = index; self.entries[index] = entry; e.allow(entry)
            fields = []
            for f in row['fields']:
                if f['offsetKind'] != 'instance': continue
                typ = f['type']; field = dict(clientField=f['name'], objectOffset=f['offset'], type=typ['kind'], cardinality='singular')
                if typ['kind'] == 'genericinst':
                    arg = typ['arguments'][0]
                    if len(typ['arguments']) != 1 or arg['kind'] not in ('uint32', 'float'): raise ValueError('Unexpected primitive-leaf dependency')
                    field.update(type=arg['kind'], cardinality='repeated', packed=True)
                elif typ['kind'] not in ('int32', 'uint32', 'bool'): raise ValueError('Unexpected scalar-leaf dependency')
                fields.append(field)
            self.schemas[index] = dict(typeIndex=index, clientType=row['name'], fields=fields)
        for cell, row in cells.items():
            typ = row['type']
            if typ['kind'] == 'class' and typ.get('typeDefinition') in TYPES: e.write(cell, self.classes[typ['typeDefinition']])
        helper = e.synthetic_type(0x14592A188); block = e.alloc(); e.write(helper + 0x38, block)
        # Execute the actual flag-producing prefix; stop before unrelated runtime
        # reflection/default-value initialization, rather than inventing its ABI.
        self.values = {}; e.allow_unwind(0x159229A20); e.prepare()
        e.uc.reg_write(e.R.UC_X86_REG_RSP, STACK + 0x100008)
        e.uc.emu_start(0x159229A20, 0x159229A51, timeout=20_000_000, count=1_000_000)
        if e.failure or e.reg('rip') != 0x159229A51: raise ValueError(e.failure or 'Float helper prefix did not reach boundary')
        self.float_helper_prefix = dict(entry='0x159229a20', stopBefore='0x159229a51', flagStore='0x159229a4d', observedPackedFlag=e.read(block + 4, 'B'), scope='Native prefix only; float boxing explicit model; reflection remainder not executed')
        if self.float_helper_prefix['observedPackedFlag'] != 1: raise ValueError('Float helper native packed flag drift')
        self.float_list_class = e.synthetic_type(0x14589DA10); block = e.alloc(); e.write(self.float_list_class + 0x38, block)
        for entry in (0x1571DC270, 0x1571DC010): e.allow_unwind(entry)
        self.leaf_initializers = []
        for index in sorted(TYPES):
            for method in e.records[index]['methods']:
                if method['name'] != '.cctor': continue
                before = len(self.factory_calls); entry = int(method['registeredAddress'], 16); e.allow(entry); e.prepare(); e.run(entry)
                self.leaf_initializers.append(dict(typeIndex=index, entry=hex(entry), factoryArguments=self.factory_calls[before:]))
        self.heap_floor = e.heap_cursor

    def new_message(self, index, address=None):
        if index not in TYPES: return super().new_message(index, address)
        pointer = GraphReader72.new_message(self, index, address)
        for f in self.schemas[index]['fields']:
            if f['cardinality'] == 'repeated' and f['type'] == 'float':
                container = self.e.read(pointer + f['objectOffset']); array = self.new_array(4)
                self.e.write(container, self.float_list_class); self.e.write(container + 16, array)
                self.containers[container].update(width=4, capacity=64, primitiveFloat=True)
        return pointer

    def snapshot(self, pointer, depth=0):
        if not pointer or self.objects[pointer] not in TYPES: return super().snapshot(pointer, depth)
        result = GraphReader72.snapshot(self, pointer, depth)
        for field in self.schemas[self.objects[pointer]]['fields']:
            if field['cardinality'] == 'repeated' and field['type'] == 'float':
                container = self.e.read(pointer + field['objectOffset']); count = self.e.read(container + 24, 'I'); array = self.e.read(container + 16)
                if count: result[field['clientField']] = [self.e.read(array + 32 + i * 4, 'f') for i in range(count)]
                else: result.pop(field['clientField'], None)
        return result

    def observe_entry(self, address):
        if self.active and address in (0x158DB8E10, 0x1571DC270):
            container = self.e.reg('rcx')
            for pointer, index in self.objects.items():
                if index not in TYPES: continue
                for f in self.schemas[index]['fields']:
                    if f['cardinality'] == 'repeated' and self.e.read(pointer + f['objectOffset']) == container:
                        self.entered.append(dict(kind='native-list-field', ownerType=index, clientField=f['clientField'], objectOffset=f['objectOffset'], key=self.e.read(STREAM + 0x20, 'I'), entry=hex(address)))
        if self.active and address == 0x1571DC010: self.entered.append(dict(kind='native-float-list-add', entry=hex(address)))
        return super().observe_entry(address)

    def graph_write(self, uc, access, address, size, value, data):
        super().graph_write(uc, access, address, size, value, data)
        if not self.active: return
        for container, info in self.containers.items():
            if not info.get('primitiveFloat'): continue
            array = self.e.read(container + 16)
            if array + 32 <= address < array + 32 + info['capacity'] * 4:
                self.float_list_stores.append(dict(arrayByteOffset=address - array - 32, size=size, valueBits=hex(value & 0xffffffff), source=hex(self.e.reg('rip'))))

    def run_graph(self, index, data, recursion_limit=100):
        self.float_list_stores = []; row = super().run_graph(index, data, recursion_limit)
        row['floatListStores'] = self.float_list_stores
        row['floatListBits'] = [dict(typeIndex=i, clientField=f['clientField'], bits=[hex(self.e.read(self.e.read(self.e.read(pointer + f['objectOffset']) + 16) + 32 + n * 4, 'I')) for n in range(self.e.read(self.e.read(pointer + f['objectOffset']) + 24, 'I'))])
            for pointer, i in self.objects.items() if i in TYPES for f in self.schemas[i]['fields'] if f['cardinality'] == 'repeated' and f['type'] == 'float']
        return row


def scan_raw(graph):
    selected = []; count = 0
    for index in sorted(TYPES):
        observed = {}
        for tag in range(1, 33):
            for wire, payload in ((0, varint(0x12345678)), (1, bytes(8)), (2, b'\0'), (5, bytes.fromhex('0000a03f'))):
                row = graph.run_graph(index, varint(tag << 3 | wire) + payload); count += 1
                calls = [x for x in row['entered'] if x.get('kind') == 'native-list-field' and x['ownerType'] == index]
                if row['result'] or row['stores'] or calls:
                    names = list(row['result']) or [x['clientField'] for x in calls]
                    if not names: names = [f['clientField'] for f in graph.schemas[index]['fields'] if any(s['typeIndex'] == index and s['objectOffset'] == f['objectOffset'] for s in row['stores'])]
                    if len(set(names)) != 1: raise ValueError('Ambiguous primitive-leaf raw branch')
                    observed.setdefault(names[0], []).append((tag, wire))
                    selected.append(dict(typeIndex=index, tag=tag, wireType=wire, result=row['result'], entered=row['entered'], stores=row['stores']))
        for field in graph.schemas[index]['fields']:
            bindings = observed.pop(field['clientField'], [])
            if not bindings or len({tag for tag, _ in bindings}) != 1: raise ValueError('Missing primitive-leaf field: ' + str((index, field['clientField'])))
            wires = sorted(w for _, w in bindings)
            expected = [2, 5] if field['cardinality'] == 'repeated' and field['type'] == 'float' else [0, 2] if field['cardinality'] == 'repeated' else [0]
            if wires != expected: raise ValueError('Primitive-leaf metadata/wire mismatch')
            field.update(tag=bindings[0][0], acceptedWireTypes=wires, semanticStatus='unresolved')
        if observed: raise ValueError('Unexpected primitive-leaf raw field')
    if sum(len(graph.schemas[i]['fields']) for i in TYPES) != 43: raise ValueError('Primitive-leaf raw count drift')
    return dict(nativeFixtures=count, scope='11 types, tags1..32/wire0,1,2,5; no nested/Object dependencies', fields=selected)


def fixtures():
    cases = [dict(name='all-fields-12876', typeIndex=12876,
        wire='08ffffffffffffffffff011208008001ffffffff0f1a100000a03f000020c00000000000000080202c',
        expected={'NOEEBKJAFAG': -1, 'MDAMHOLGONG': [0, 128, 4294967295], 'HMODGFNMPML': [1.25, -2.5, 0.0, -0.0], 'LPJMAOFKBPI': 44}),
        dict(name='all-fields-18849', typeIndex=18849, wire='0a0901800100ffffffff0f120302ff01', expected={'ABBODGKNDEP': [1, 128, 0, 4294967295], 'KGMKPEBKEFF': [2, 255]}),
        dict(name='all-fields-21518', typeIndex=21518, wire='080b10161821', expected={'OGJOJBLIGCB': 11, 'JDMBGGLHOMJ': 22, 'HDDPFKNMJJG': 33}),
        dict(name='all-fields-31113', typeIndex=31113, wire='080b12030280011a0303ff01', expected={'PJAJDHBBMGB': 11, 'PFBKEGPBKAN': [2, 128], 'CCPOLEPGAEB': [3, 255]}),
        dict(name='all-fields-38218', typeIndex=38218, wire='08ffffffffffffffffff0110feffffffffffffffff0118ffffffff0f2080808080f8ffffffff01',
            expected={'HGGPCEMHOBI': -1, 'ILJLGHBLKMP': -2, 'PBIBHHJEOPE': 4294967295, 'GOKKMOKPCEE': -2147483648}),
        dict(name='all-fields-48088', typeIndex=48088, wire='08efffffffffffffffff011012', expected={'CJMJBINEBAG': -17, 'ALOKJBKPLGA': 18}),
        dict(name='all-fields-51614', typeIndex=51614,
            wire='080b101618dfffffffffffffffff0120d4ffffffffffffffff0128c9ffffffffffffffff0130beffffffffffffffff0138b3ffffffffffffffff0140a8ffffffffffffffff014802509bffffffffffffffff015891ffffffffffffffff016079688301708d017897018001a101',
            expected={'NOEEBKJAFAG': 11, 'ANKEOLKBECN': 22, 'JCGPKLKCINE': -33, 'OOKGFNCMOMD': -44, 'AEPFCKJJOMN': -55, 'CMACDPNAIAF': -66, 'GEODCFEFNLP': -77, 'NEBNDBIGKIA': -88,
                'HJPIIFMALJK': True, 'ECEHDMGMPBB': -101, 'ILJLGHBLKMP': -111, 'PLGFFEKCPHF': 121, 'AANCEPCPHKJ': 131, 'FDBBEEIKJDK': 141, 'NJAGFJINHMA': 151, 'OCNIJJOEGJM': 161}),
        dict(name='all-fields-65248', typeIndex=65248, wire='0a0900018001ffffffff0f', expected={'HGFBPFCEACB': [0, 1, 128, 4294967295]}),
        dict(name='all-fields-66229', typeIndex=66229, wire='0880808080f8ffffffff0110ffffffff0718ffffffff0f2203048001',
            expected={'KMBPJIKEDGD': -2147483648, 'KBDNIGMHCBM': 2147483647, 'PPJFLLGKNAH': 4294967295, 'FEKDEEENNBB': [4, 128]}),
        dict(name='all-fields-67498', typeIndex=67498, wire='080b10ffffffff0f', expected={'AFKJIKCEOKA': 11, 'GNHFNECPAHD': 4294967295}),
        dict(name='all-fields-68918', typeIndex=68918, wire='0880808080f8ffffffff0110ffffffff07', expected={'CJMJBINEBAG': -2147483648, 'ALOKJBKPLGA': 2147483647}),
        dict(name='float-packed-extremes-zero-and-negative-zero', typeIndex=12876, wire='1a180000a03f000020c00000000000000080ffff7f7f01000000',
            expected={'HMODGFNMPML': [1.25, -2.5, 0.0, -0.0, 3.4028234663852886e38, 1.401298464324817e-45]}),
        dict(name='float-mixed-packed-unpacked-append', typeIndex=12876, wire='1d0000a0bf1a0800002040000000801d00000000', expected={'HMODGFNMPML': [-1.25, 2.5, -0.0, 0.0]}),
        dict(name='float-empty-packed-preserves-prior', typeIndex=12876, wire='1a040000a0bf1a00', expected={'HMODGFNMPML': [-1.25]}),
        dict(name='float-wrong-varint-wire-is-skipped', typeIndex=12876, wire='18011a040000a0bf1d00000000', expected={'HMODGFNMPML': [-1.25, 0.0]}),
        dict(name='u32-mixed-12876', typeIndex=12876, wire='1000120601ffffffff0f', expected={'MDAMHOLGONG': [0, 1, 4294967295]}),
        dict(name='u32-mixed-18849-two-lists', typeIndex=18849, wire='08000a030180011000120302ff01', expected={'ABBODGKNDEP': [0, 1, 128], 'KGMKPEBKEFF': [0, 2, 255]}),
        dict(name='u32-mixed-31113-two-lists', typeIndex=31113, wire='1000120302800118001a0303ff01', expected={'PFBKEGPBKAN': [0, 2, 128], 'CCPOLEPGAEB': [0, 3, 255]}),
        dict(name='u32-mixed-65248', typeIndex=65248, wire='08000a0601ffffffff0f', expected={'HGFBPFCEACB': [0, 1, 4294967295]}),
        dict(name='u32-mixed-66229', typeIndex=66229, wire='2000220601ffffffff0f', expected={'FEKDEEENNBB': [0, 1, 4294967295]}),
        dict(name='signed-bool-u32-duplicate-zero', typeIndex=51614, wire='0801080018ffffffffffffffffff01180048024800', expected={})]
    values = [float(-i if i & 1 else i) for i in range(64)]; payload = b''.join(struct.pack('<f', x) for x in values)
    cases.append(dict(name='float-fixture-capacity-64', typeIndex=12876, wire=(b'\x1a' + varint(len(payload)) + payload).hex(), expected={'HMODGFNMPML': values}))
    for index in sorted(TYPES): cases.append(dict(name='empty-' + str(index), typeIndex=index, wire='', expected={}))
    return cases


def verify(graph):
    good = []
    for case in fixtures():
        row = graph.run_graph(case['typeIndex'], bytes.fromhex(case['wire']))
        if row['result'] != case['expected']: raise ValueError(case['name'] + ': primitive leaf native result differs ' + str(row['result']))
        good.append(dict(case, observation=row))
    rejected = []
    errors = [('nonempty-child-51050', 34045, bytes.fromhex('2a020801'), 'explicit-scope-refusal'),
        ('invalid-tag', 51614, b'\0', 'explicit-input-model'),
        ('signed-varint-overflow', 38218, bytes.fromhex('08ffffffffffffffffff02'), 'explicit-input-model'),
        ('truncated-u32-packed', 65248, bytes.fromhex('0a0180'), 'explicit-input-model'),
        ('truncated-float-packed', 12876, bytes.fromhex('1a020000'), 'explicit-input-model'),
        ('truncated-float-unpacked', 12876, bytes.fromhex('1d0000'), 'explicit-input-model')]
    payload = bytes(65 * 4); errors.append(('float-fixture-capacity-65-unsupported-growth', 12876, b'\x1a' + varint(len(payload)) + payload, 'fixture-model-boundary'))
    for name, index, wire, scope in errors:
        try: graph.run_graph(index, wire)
        except ValueError as error:
            text = str(error)
            if scope == 'explicit-scope-refusal' and 'Nonempty child outside F-03d3e2' not in text: raise
            if scope == 'fixture-model-boundary' and 'Unmodeled native call 0x140514ee0' not in text: raise
            rejected.append(dict(name=name, typeIndex=index, wire=wire.hex(), scope=scope, error=text))
        else: raise ValueError('Invalid/out-of-scope primitive leaf input accepted: ' + name)
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
    cells = type_cells(b, m, s, records); aot = leaf_lists_aot(b, m, s, records); bindings = core_aot(b, m, s, records); strings = string_map_aot(b, m, s, records)
    e = Offline72(b, a.python_libs, records, trace_instructions=False); graph = AvatarPrimitiveLeaves72(e, a.directory, cells, bindings, items, strings)
    try: scan = scan_raw(graph); good, rejected = verify(graph)
    finally: graph.close(); e.close()
    context = dict(schemaVersion=1, task='F-03d3e3', inputSHA256=HASHES, runtimeStatus='not-tested', targetProtocolReady=False,
        semanticIdentityStatus='unresolved', fullAvatarRawClosure=False, fullComplexRawClosure=False, blockedTypeIndices=[51050],
        aotBindings=aot, initializers=graph.leaf_initializers, floatHelperPrefix=graph.float_helper_prefix, floatTypeUsage=[cells[x] for x in (0x14592A188, 0x14589DA10, 0x145886440)],
        dependencyScope=dict(typeIndices=sorted(TYPES), metadataStorageFields=43, nestedMessageDependencies=[],
            types=[dict(typeIndex=i, clientType=records[i]['name'], fields=[f for f in records[i]['fields'] if f['offsetKind'] == 'instance'],
                methods=[x for x in records[i]['methods'] if x['name'] in ('.cctor', 'MOMMACBEMEK')]) for i in sorted(TYPES)]),
        messages=[graph.schemas[i] for i in sorted(TYPES)], scan=scan, nativeFixtures=len(good), rejections=rejected,
        limitations=['11 independent leaves only;43 metadata slots become43 independently scanned wire fields;51050 nonempty remains refused',
            'Native float list reader/Add and helper packed-flag prefix execute; helper reflection remainder and list reflection cctor are not executed',
            'Primitive float/XMM0, class/generic/null-policy metadata, boxing/managed allocations and fixed-capacity64 arrays remain explicit models',
            '65-element float growth reaches unsupported native allocator; this is a fixture boundary, not a client list-size rejection',
            'No int32-list recovery, complete complex/Avatar graph, named identity, business semantics, live/client acceptance or production integration'])
    summary = dict(context, fixtures=[dict(name=x['name'], typeIndex=x['typeIndex'], wire=x['wire'], result=x['observation']['result'], entered=x['observation']['entered'], stores=x['observation']['stores'], floatListBits=x['observation']['floatListBits'], floatListStores=x['observation']['floatListStores']) for x in good])
    for path, data in zip(paths, [dict(context, fixtures=good), summary]):
        path.parent.mkdir(parents=True, exist_ok=True); path.write_text(json.dumps(data, indent=2) + '\n', 'utf8')
    print(json.dumps(dict(messages=11, storage=43, wireFields=43, scanFixtures=scan['nativeFixtures'], nativeFixtures=len(good), rejections=len(rejected))))


if __name__ == '__main__': main()
