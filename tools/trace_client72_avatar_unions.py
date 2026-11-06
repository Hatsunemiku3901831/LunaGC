"""F-03d3e2: boxed string/u32, scalar floats and upper message lists.

Seven metadata types/36 storage slots only; child51050 nonempty is refused.
"""
import argparse
import copy
import hashlib
import json
import struct
from pathlib import Path
try:
    from .extract_client72 import HASHES, PE, HEADER_VA, CODE_REGISTRATION, TypeResolver, number, read_verified, extract
    from .client72_emulator import Offline72
    from .client72_graph import GraphReader72
    from .trace_client72_avatar_core import ROOT, core_aot
    from .trace_client72_avatar_complex_outer import AvatarComplexOuter72, string_map_aot, fixtures as outer_fixtures
    from .trace_client72_avatar_items import INVENTORY_SHA
    from .trace_client72_player_store import type_cells
    from .trace_client72_token import varint
except ImportError:
    from extract_client72 import HASHES, PE, HEADER_VA, CODE_REGISTRATION, TypeResolver, number, read_verified, extract
    from client72_emulator import Offline72
    from client72_graph import GraphReader72
    from trace_client72_avatar_core import ROOT, core_aot
    from trace_client72_avatar_complex_outer import AvatarComplexOuter72, string_map_aot, fixtures as outer_fixtures
    from trace_client72_avatar_items import INVENTORY_SHA
    from trace_client72_player_store import type_cells
    from trace_client72_token import varint

TYPES = {85344, 80101, 62912, 77771, 70808, 51162, 34045}


def union_lists_aot(binary, metadata, startup, records):
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
        if not 0 <= pointer < count or not 0 <= spec < spec_count: raise ValueError('Union-list AOT bounds')
        method, cls, args = struct.unpack_from('<iii', metadata, specs + spec * 12); owner, row = methods[method]
        if owner['index'] != 25060 or cls not in (5883, 27561, 27562, 27563) or row['name'] not in ('.ctor', 'FNNADLCNGFO'): continue
        if args != -1: raise ValueError('Unexpected union-list AOT method arguments')
        address = number(binary, pointers + pointer * 8, 'Q'); pe.offset(address, executable=True)
        rows.append(dict(owner=owner['index'], classInstance=cls, method=row['name'], methodDefinition=method,
            tableIndex=i, methodSpec=spec, pointerIndex=pointer, address=hex(address), arguments=[resolver.resolve_index(x) for x in resolver.arguments[cls]]))
    for instance, child in [(5883, 77771), (27561, 70808), (27562, 51162), (27563, 51050)]:
        hits = [x for x in rows if (x['classInstance'], x['method']) == (instance, 'FNNADLCNGFO')]
        if len(hits) != 1 or hits[0]['address'] != '0x158dc41f0' or hits[0]['arguments'][0].get('typeDefinition') != child: raise ValueError('Union-list concrete AOT drift')
    return rows


class AvatarUnions72(AvatarComplexOuter72):
    def __init__(self, engine, directory, cells, bindings, items, strings):
        super().__init__(engine, directory, cells, bindings, items, strings); e = self.e
        for index in TYPES | {51050}:
            row = e.records[index]; entry = next(int(m['registeredAddress'], 16) for m in row['methods'] if m['name'] == 'MOMMACBEMEK')
            if index in self.classes: klass = self.classes[index]
            else:
                klass = e.alloc(1024); table = e.alloc(16); e.write(klass + 0xC7, 1, 'B')
                e.write(klass + 0x80, table); e.write(klass + 0xC0, 1, 'H'); e.write(klass + 0xBE, 32, 'H')
                e.write(table, self.interface); e.write(klass + 0xE8, entry)
            self.classes[index] = klass; self.class_indices[klass] = index; self.entries[index] = entry; e.allow(entry)
            if index not in TYPES or index == 85344: continue
            fields = []
            for f in row['fields']:
                if f['offsetKind'] != 'instance': continue
                typ = f['type']; field = dict(clientField=f['name'], objectOffset=f['offset'], type=typ['kind'], cardinality='singular')
                if typ['kind'] == 'class': field.update(type='message', nestedTypeIndex=typ['typeDefinition'])
                elif typ['kind'] == 'genericinst': field.update(type='message', cardinality='repeated', nestedTypeIndex=typ['arguments'][0]['typeDefinition'])
                elif typ['kind'] == 'valuetype': field.update(type=next(x['type']['kind'] for x in e.records[typ['typeDefinition']]['fields'] if x['name'] == 'value__'), enumTypeIndex=typ['typeDefinition'])
                fields.append(field)
            self.schemas[index] = dict(typeIndex=index, clientType=row['name'], fields=fields)
        self.schemas[85344] = dict(typeIndex=85344, clientType=e.records[85344]['name'], fields=[
            dict(clientField='NOEEBKJAFAG_string', objectOffset=24, type='string', cardinality='singular', discriminatorOffset=32, discriminatorValue=1),
            dict(clientField='NOEEBKJAFAG_uint32', objectOffset=24, type='uint32', cardinality='singular', discriminatorOffset=32, discriminatorValue=2)])
        for cell, row in cells.items():
            typ = row['type']
            if typ['kind'] == 'class' and typ.get('typeDefinition') in TYPES | {51050}: e.write(cell, self.classes[typ['typeDefinition']])
        self.box_classes[e.synthetic_type(0x145886170)] = ('uint32', 4)
        self.union_initializers = []
        for index in sorted(TYPES):
            for method in e.records[index]['methods']:
                if method['name'] != '.cctor': continue
                entry = int(method['registeredAddress'], 16); e.allow(entry); e.prepare(); e.run(entry)
                self.union_initializers.append(dict(typeIndex=index, entry=hex(entry)))
        self.heap_floor = e.heap_cursor

    def new_message(self, index, address=None):
        if index in TYPES: return GraphReader72.new_message(self, index, address)
        if index == 51050:
            p = self.e.alloc(512) if address is None else address
            self.e.write(p, self.classes[index]); self.e.write(p + 0x14, 1, 'I'); self.objects[p] = index; return p
        return super().new_message(index, address)

    def snapshot(self, pointer, depth=0):
        if pointer and self.objects[pointer] == 51050: return dict(untracedType=51050, content='empty-envelope-only')
        if pointer and self.objects[pointer] == 85344:
            discriminator = self.e.read(pointer + 32, 'I')
            if not discriminator: return {}
            boxed = self.values[self.e.read(pointer + 24)]
            if discriminator == 1: return {'NOEEBKJAFAG_string': bytes.fromhex(boxed['hex']).decode('utf8')}
            if discriminator == 2 and boxed['kind'] == 'uint32': return {'NOEEBKJAFAG_uint32': struct.unpack('<I', bytes.fromhex(boxed['hex']))[0]}
            raise ValueError('Union discriminator/boxed type mismatch')
        if pointer and self.objects[pointer] in TYPES:
            result = GraphReader72.snapshot(self, pointer, depth)
            for field in self.schemas[self.objects[pointer]]['fields']:
                if field['type'] == 'float':
                    if self.e.read(pointer + field['objectOffset'], 'I'): result[field['clientField']] = self.e.read(pointer + field['objectOffset'], 'f')
                    else: result.pop(field['clientField'], None)
            return result
        return super().snapshot(pointer, depth)

    def observe_entry(self, address):
        if self.active:
            if address == self.entries[51050]:
                stream = self.e.reg('rdx')
                if self.e.read(stream + 0x44, 'I') != self.e.read(stream + 0x38, 'I'): raise ValueError('Nonempty child outside F-03d3e2: 51050')
            if self.item_copy: self.item_copy.before(address)
        return GraphReader72.observe_entry(self, address)

    def run_graph(self, index, data, recursion_limit=100):
        row = super().run_graph(index, data, recursion_limit)
        row['floatScalarBits'] = [dict(typeIndex=i, clientField=f['clientField'], valueBits=hex(self.e.read(pointer + f['objectOffset'], 'I')))
            for pointer, i in self.objects.items() if i in TYPES for f in self.schemas[i]['fields'] if f['type'] == 'float']
        return row


def scan_raw(graph):
    selected = []; count = 0
    for index in sorted(TYPES):
        observed = {}
        for tag in range(1, 33):
            for wire, payload in ((0, varint(0x12345678)), (1, bytes(8)), (2, b'\0'), (5, bytes.fromhex('0000a03f'))):
                row = graph.run_graph(index, varint(tag << 3 | wire) + payload); count += 1
                if row['result'] or row['stores']:
                    names = list(row['result'])
                    if not names: names = [f['clientField'] for f in graph.schemas[index]['fields'] if any(s['typeIndex'] == index and s['objectOffset'] == f['objectOffset'] for s in row['stores'])]
                    if len(set(names)) != 1: raise ValueError('Ambiguous upper union raw branch: ' + str((index, tag, wire, names)))
                    observed.setdefault(names[0], []).append((tag, wire))
                    selected.append(dict(typeIndex=index, tag=tag, wireType=wire, result=row['result'], entered=row['entered'], stores=row['stores']))
        for field in graph.schemas[index]['fields']:
            bindings = observed.pop(field['clientField'], [])
            if not bindings or len({tag for tag, _ in bindings}) != 1: raise ValueError('Missing union field: ' + field['clientField'])
            wires = sorted(w for _, w in bindings); expected = [2] if field['type'] in ('message', 'string') else [5] if field['type'] == 'float' else [0]
            if wires != expected: raise ValueError('Union metadata/wire mismatch')
            field.update(tag=bindings[0][0], acceptedWireTypes=wires, semanticStatus='unresolved')
            if field.get('nestedTypeIndex') == 51050: field['contentStatus'] = 'empty-envelope-only; nonempty-refused'
        if observed: raise ValueError('Unexpected upper union raw field')
    if sum(len(graph.schemas[i]['fields']) for i in TYPES) != 36: raise ValueError('Upper union raw field count drift')
    return dict(nativeFixtures=count, scope='7 types, tags1..32/wire0,1,2,5; boxed union branches and scalar float32', fields=selected)


def message(tag, payload): return varint(tag << 3 | 2) + varint(len(payload)) + payload


def fixtures():
    geo = {'MDDPGNIHKAF': 1.25, 'EBDPHJGGAFO': -2.5}
    scalar = {'KNLALOGEGAI': True, 'HBNNOCOEFEE': 4294967295, 'IJKEMAINHOD': 33, 'DCLPPKIHMBC': -2147483648, 'OKDKGHLCKDL': -1}
    wrap_a = {'DBCJNBDFEOL': {'NOEEBKJAFAG_string': 'A'}, 'GCOLEBCPBNP': -1.25}
    wrap_b = {'DBCJNBDFEOL': {'NOEEBKJAFAG_uint32': 7}, 'GCOLEBCPBNP': 3.5}
    mid_wire = '0a0a0a08e4b8adf09f8c9900120610ffffffff0f1a0a0a030a01411d0000a0bf1a090a0210071d00006040202c'
    mid = {'COECLMLNMMA': {'NOEEBKJAFAG_string': '中🌙\0'}, 'PKNHHAFBGFB': {'NOEEBKJAFAG_uint32': 4294967295}, 'KNHMDLBJFMJ': [wrap_a, wrap_b], 'ONFKOHNNLDI': 44}
    large_wire = '1a030a0141220210074220080210ffffffff0f18212880808080f8ffffffff0130ffffffffffffffffff015a0a150000a03f0d000020c008efffffffffffffffff011016283730423d000060c048635001607970017897018001ffffffffffffffffff01'
    large = {'FDCIMEECAIM': {'NOEEBKJAFAG_string': 'A'}, 'DNCDDOAIGAO': {'NOEEBKJAFAG_uint32': 7}, 'DIFHAHEMPCN': scalar, 'DHJBELHAMOE': geo,
        'IHHOCELBHNN': -17, 'JAAKMAJMAFP': 22, 'ONFKOHNNLDI': 55, 'IJKEMAINHOD': 66, 'NPJOJHAIMEE': -3.5, 'MMOEOHFIHCN': 99,
        'PEMOKNBLGBN': True, 'PGNIPDHPCFD': 121, 'CMLFPFPAFJH': True, 'CLOIMKOPMMP': 151, 'OKDKGHLCKDL': -1}
    upper_wire = '0801320a0a030a01411d0000a0bf12090a0210071d000060401a2d0a0a0a08e4b8adf09f8c9900120610ffffffff0f1a0a0a030a01411d0000a0bf1a090a0210071d00006040202c22641a030a0141220210074220080210ffffffff0f18212880808080f8ffffffff0130ffffffffffffffffff015a0a150000a03f0d000020c008efffffffffffffffff011016283730423d000060c048635001607970017897018001ffffffffffffffffff012a00'
    upper = {'FAGKAADHKDI': True, 'COADAPGCGKK': [wrap_a], 'AOGEMELMKBO': [wrap_b], 'HCPGHHDMGKM': [mid], 'PCHCMFOCHDO': [large], 'PPDFGNJDAAE': [{'untracedType': 51050, 'content': 'empty-envelope-only'}]}
    cases = [dict(name='boxed-u32-upper', typeIndex=85344, wire='10ffffffff0f', expected={'NOEEBKJAFAG_uint32': 4294967295}),
        dict(name='boxed-string-unicode-nul', typeIndex=85344, wire='0a08e4b8adf09f8c9900', expected={'NOEEBKJAFAG_string': '中🌙\0'}),
        dict(name='boxed-string-explicit-empty', typeIndex=85344, wire='0a00', expected={'NOEEBKJAFAG_string': ''}),
        dict(name='boxed-u32-explicit-zero', typeIndex=85344, wire='1000', expected={'NOEEBKJAFAG_uint32': 0}),
        dict(name='union-switch-string-u32-string-empty', typeIndex=85344, wire='0a014110070a00', expected={'NOEEBKJAFAG_string': ''}),
        dict(name='union-same-string-last-wins', typeIndex=85344, wire='0a01410a0142', expected={'NOEEBKJAFAG_string': 'B'}),
        dict(name='80101-two-distinct-floats', typeIndex=80101, wire='150000a03f0d000020c0', expected=geo),
        dict(name='80101-negative-float-negative-zero', typeIndex=80101, wire='150000a0bf0d00000080', expected={'MDDPGNIHKAF': -1.25, 'EBDPHJGGAFO': -0.0}),
        dict(name='80101-max-finite-min-subnormal', typeIndex=80101, wire='15ffff7f7f0d01000000', expected={'MDDPGNIHKAF': 3.4028234663852886e38, 'EBDPHJGGAFO': 1.401298464324817e-45}),
        dict(name='80101-duplicate-explicit-zero', typeIndex=80101, wire='150000a0bf15000000000d00000000', expected={}),
        dict(name='62912-signed-enum-u32-boundaries', typeIndex=62912, wire='080210ffffffff0f18212880808080f8ffffffff0130ffffffffffffffffff01', expected=scalar),
        dict(name='77771-nonempty-union-and-negative-float', typeIndex=77771, wire='0a030a01411d0000a0bf', expected=wrap_a),
        dict(name='70808-all-fields-distinct-unions-and-two-list-items', typeIndex=70808, wire=mid_wire, expected=mid),
        dict(name='51162-all-15-fields-nonempty', typeIndex=51162, wire=large_wire, expected=large),
        dict(name='34045-all-six-fields-nonempty-upper-tree', typeIndex=34045, wire=upper_wire, expected=upper),
        dict(name='70808-repeated-union-field-switch', typeIndex=70808, wire='0a030a01410a021007', expected={'COECLMLNMMA': {'NOEEBKJAFAG_uint32': 7}}),
        dict(name='34045-two-same-shape-lists-append-independently', typeIndex=34045,
            wire='320a0a030a01411d0000a0bf12090a0210071d0000604032090a0210071d00006040', expected={'COADAPGCGKK': [wrap_a, wrap_b], 'AOGEMELMKBO': [wrap_b]}),
        dict(name='51162-float-bool-u32-and-int32-duplicate-zero', typeIndex=51162,
            wire='080108005001500070017000480148003d0000a0bf3d00000000', expected={})]
    old = next(x for x in outer_fixtures() if x['name'] == '53824-all-12-fields-nonempty-leaves')
    complex_wire = old['wire'] + message(7, bytes.fromhex(upper_wire)).hex()
    complex_result = copy.deepcopy(old['expected']); complex_result['KDKAEHEEMMJ'] = upper
    cases.append(dict(name='53824-complete-upper-tree-nonempty', typeIndex=53824, wire=complex_wire, expected=complex_result))
    baseline = next(x for x in outer_fixtures() if x['name'] == 'AvatarInfo-all-37-fields-with-complex-outer')
    expected = copy.deepcopy(baseline['expected']); expected['AMLLHJJIMNI'][1]['KDKAEHEEMMJ'] = {}
    expected['AMLLHJJIMNI'].append(complex_result)
    cases.append(dict(name='AvatarInfo-all-37-fields-with-upper-union-tree', typeIndex=ROOT,
        wire=baseline['wire'] + message(37, bytes.fromhex(complex_wire)).hex(), expected=expected))
    for index in sorted(TYPES): cases.append(dict(name='empty-' + str(index), typeIndex=index, wire='', expected={}))
    return cases


def verify(graph):
    good = []
    for case in fixtures():
        row = graph.run_graph(case['typeIndex'], bytes.fromhex(case['wire']))
        if row['result'] != case['expected']: raise ValueError(case['name'] + ': union native result differs ' + str(row['result']))
        good.append(dict(case, observation=row))
    rejected = []
    errors = [('nonempty-child-51050', 34045, '2a020801', 'explicit-scope-refusal', 100),
        ('invalid-tag', 85344, '00', 'explicit-input-model', 100),
        ('boxed-u32-overflow', 85344, '10ffffffffffffffffff02', 'explicit-input-model', 100),
        ('invalid-union-string-utf8', 85344, '0a01ff', 'explicit-input-model', 100),
        ('truncated-scalar-float', 80101, '150000', 'explicit-input-model', 100),
        ('negative-list-length', 34045, '32ffffffff0f', 'native-error-entry', 100),
        ('truncated-list-envelope', 34045, '32030801', 'native-error-entry', 100),
        ('upper-list-recursion-limit', 34045, '3200', 'native-error-entry', 0),
        ('nested-union-recursion-limit', 77771, '0a00', 'native-error-entry', 0)]
    for name, index, wire, scope, limit in errors:
        try: graph.run_graph(index, bytes.fromhex(wire), limit)
        except ValueError as error:
            text = str(error)
            if scope == 'explicit-scope-refusal' and 'Nonempty child outside F-03d3e2' not in text: raise
            if scope == 'native-error-entry' and 'Native error branch:' not in text: raise
            rejected.append(dict(name=name, typeIndex=index, wire=wire, scope=scope, error=text))
        else: raise ValueError('Invalid/out-of-scope union input accepted: ' + name)
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
    cells = type_cells(b, m, s, records); aot = union_lists_aot(b, m, s, records); bindings = core_aot(b, m, s, records); strings = string_map_aot(b, m, s, records)
    e = Offline72(b, a.python_libs, records, trace_instructions=False); graph = AvatarUnions72(e, a.directory, cells, bindings, items, strings)
    try: scan = scan_raw(graph); good, rejected = verify(graph)
    finally: graph.close(); e.close()
    context = dict(schemaVersion=1, task='F-03d3e2', inputSHA256=HASHES, runtimeStatus='not-tested', targetProtocolReady=False,
        semanticIdentityStatus='unresolved', fullAvatarRawClosure=False, fullComplexRawClosure=False, blockedTypeIndices=[51050],
        aotBindings=aot, initializers=graph.union_initializers, boxedPrimitiveUsage=cells[0x145886170],
        dependencyScope=dict(typeIndices=sorted(TYPES), metadataStorageFields=36, blockedNonemptyType=51050,
            types=[dict(typeIndex=i, clientType=records[i]['name'], fields=[f for f in records[i]['fields'] if f['offsetKind'] == 'instance'],
                methods=[x for x in records[i]['methods'] if x['name'] in ('.cctor', 'MOMMACBEMEK')]) for i in sorted(TYPES)]),
        messages=[graph.schemas[i] for i in sorted(TYPES)], scan=scan, nativeFixtures=len(good), rejections=rejected,
        limitations=['Only7 upper types;51050 nonempty refused and complete complex/Avatar graph stays open',
            '85344 Object+discriminator independently executed as string/tag1 and boxed-u32/tag2, retaining active empty/zero',
            'Float32 primitive/XMM0, UTF8-to-managed-UTF16, boxing/allocation/class/generic contexts and bounded64 storage are explicit models',
            'Concrete readers/ReadMessage limits/list append and inherited map/clone/copy helpers execute; native error entries stop before throw',
            'No named identity, business semantics, handler/live ABI/client acceptance or production integration'])
    summary = dict(context, fixtures=[dict(name=x['name'], typeIndex=x['typeIndex'], wire=x['wire'], result=x['observation']['result'], entered=x['observation']['entered'], nestedCalls=x['observation']['nestedCalls'], stores=x['observation']['stores'], floatScalarBits=x['observation']['floatScalarBits']) for x in good])
    for path, data in zip(paths, [dict(context, fixtures=good), summary]):
        path.parent.mkdir(parents=True, exist_ok=True); path.write_text(json.dumps(data, indent=2) + '\n', 'utf8')
    print(json.dumps(dict(messages=7, storage=36, wireFields=36, scanFixtures=scan['nativeFixtures'], nativeFixtures=len(good), rejections=len(rejected))))


if __name__ == '__main__': main()
