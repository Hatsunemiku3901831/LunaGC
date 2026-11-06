"""F-03d2a2: dedicated native uint32/uint32 maps and inventory scalar lists.

Does not change the shared graph/map runtime or recover business identities.
"""
import argparse
import hashlib
import json
import struct
from pathlib import Path
try:
    from .extract_client72 import HASHES, PE, HEADER_VA, CODE_REGISTRATION, TypeResolver, number, read_verified, extract
    from .client72_emulator import Offline72, GLOBAL, STREAM
    from .client72_graph import GraphReader72
    from .trace_client72_player_store import PlayerStoreGraph72
    from .trace_client72_token import varint
except ImportError:
    from extract_client72 import HASHES, PE, HEADER_VA, CODE_REGISTRATION, TypeResolver, number, read_verified, extract
    from client72_emulator import Offline72, GLOBAL, STREAM
    from client72_graph import GraphReader72
    from trace_client72_player_store import PlayerStoreGraph72
    from trace_client72_token import varint

TYPES = {14095, 28473, 28475, 64947, 74063, 61085}
SOURCE_SHA = '2c14b416e5e18d868bde7d645939f6ea9c2a936761967e555e92538c2d11e20f'
STATICS = {14095: (0x149C5C530, [(0x228D0, 26), (0x228D8, 18)]),
           28473: (0x14E458080, [(0x342F0, 10)]), 28475: (0x152274E80, [(0x25200, 10)]),
           64947: (0x1504D7A10, [(0x1B3B8, 34), (0x1B3B0, 50)]),
           74063: (0x147744FC0, [(0x29968, 42), (0x29970, 58), (0x29960, 66)]),
           61085: (0x14B6FAA40, [(0x2C270, 10)])}


def primitive_aot(binary, metadata, startup, records):
    pe = PE(binary); h = pe.offset(HEADER_VA); cr = pe.offset(CODE_REGISTRATION)
    specs = 0x210 + (number(binary, h + 0x170) ^ 0x6347382B)
    spec_count = (number(binary, h + 0x24) ^ 0x323BAE70) // 12
    table = 0x210 + (number(binary, h + 0xEC) ^ 0x3CF0720C)
    size = (number(binary, h + 0x110) + 0xA3D9CC8B) & 0xffffffff
    count = (number(binary, cr + 0x64) + 0x9EA91E3B) & 0xffffffff
    pointers = pe.offset(number(binary, cr + 0x30, 'Q'), count * 8)
    methods = {m['index']: (t, m) for t in records for m in t['methods']}
    resolver = TypeResolver(pe, startup, records, h); result = []
    for i in range(size // 14):
        pointer, spec = struct.unpack_from('<ii', metadata, table + i * 14)
        if not 0 <= pointer < count or not 0 <= spec < spec_count: raise ValueError('Primitive AOT reference invalid')
        method, cls, args = struct.unpack_from('<iii', metadata, specs + spec * 12)
        owner, row = methods[method]
        if owner['index'] not in (17961, 17964) or cls != 3723 or row['name'] not in ('.ctor', 'MOMMACBEMEK', 'FNNADLCNGFO', 'set_Item'): continue
        if args != -1: raise ValueError('Unexpected primitive map method arguments')
        address = number(binary, pointers + pointer * 8, 'Q'); pe.offset(address, executable=True)
        result.append(dict(owner=owner['index'], classInstance=cls, method=row['name'], methodDefinition=method,
            tableIndex=i, methodSpec=spec, pointerIndex=pointer, address=hex(address),
            arguments=[resolver.resolve_index(x) for x in resolver.arguments[cls]]))
    expected = {(17961, 'FNNADLCNGFO'): 0x15893D460, (17961, 'set_Item'): 0x15893E4D0,
                (17964, 'MOMMACBEMEK'): 0x158935D30, (17964, '.ctor'): 0x158935CD0}
    for identity, address in expected.items():
        rows = [x for x in result if (x['owner'], x['method']) == identity]
        if len(rows) != 1 or int(rows[0]['address'], 16) != address or [a['kind'] for a in rows[0]['arguments']] != ['uint32', 'uint32']:
            raise ValueError('Primitive map AOT binding drift')
    return result


class UInt32MapRuntime72:
    """Specific nonshared map reader/adapter/setter, bounded managed storage."""
    def __init__(self, graph):
        self.g, self.e = graph, graph.e; e = self.e
        self.static_values = {}; self.codecs = {}; self.factory_calls = []
        self.codec_class = e.synthetic_type(0x145898640)
        self.adapter_class = e.synthetic_type(0x145937A18); table = e.alloc(16)
        e.write(self.adapter_class + 0x80, table); e.write(self.adapter_class + 0xC0, 1, 'H')
        e.write(self.adapter_class + 0xBE, 32, 'H'); e.write(table, graph.interface)
        e.write(self.adapter_class + 0xE8, 0x158935D30)
        policy = e.synthetic_type(0x145898618); e.write(policy + 0x38, e.alloc())
        dictionary = e.alloc(512); info = e.alloc(); e.write(info, dictionary); e.write(0x145A93BC8, info)
        context = e.alloc(); e.write(dictionary + 0x30, context)
        e.write(context + 0x88, e.alloc())
        self.append_stub = e.model_stub(self.append_node)
        e.models[0x1578B66F0] = self.lookup; e.models[0x1578B6A20] = self.insert
        e.models[0x14E5A5E30] = self.codec_factory
        for entry in (0x15893D460, 0x158935D30, 0x15893E4D0, 0x158935CD0): e.allow_unwind(entry)

    def codec_factory(self):
        e = self.e; tag = e.reg('ecx'); p = e.alloc(); delegate = e.alloc()
        e.write(p + 0x34, tag, 'I'); e.write(p + 0x18, delegate)
        e.write(delegate + 0x18, 0x147520110); e.write(delegate + 0x40, STREAM)
        self.factory_calls.append(tag)
        return p

    def reset(self): self.dictionaries = {}; self.linked_lists = {}

    def create(self, pointer, field_type):
        if [a['kind'] for a in field_type['arguments']] != ['uint32', 'uint32']: raise ValueError('Only uint32/uint32 maps supported')
        e = self.e; dictionary, linked, entries = e.alloc(), e.alloc(), e.alloc(32 + 64 * 24)
        e.write(entries + 24, 64, 'I'); e.write(dictionary + 24, entries)
        e.write(pointer + 16, dictionary); e.write(pointer + 24, linked)
        klass = e.alloc(512); e.write(klass + 0x138, self.append_stub); e.write(linked, klass)
        self.dictionaries[dictionary] = dict(entries=entries, nodes=[], indices={})
        self.linked_lists[linked] = dictionary; self.g.containers[pointer]['mapDictionary'] = dictionary

    def allocate(self, klass):
        if klass == self.codec_class: return self.e.alloc()
        if klass != self.adapter_class: return None
        p = self.e.alloc(512); self.e.write(p, klass); self.g.objects[p] = 17964
        return p

    def before(self, address):
        if address not in (0x15893D460, 0x158935D30, 0x15893E4D0): return
        if address == 0x15893D460 and self.e.reg('r8') not in self.codecs: raise ValueError('Unbound primitive map codec')
        self.g.entered.append(dict(kind='native-primitive-map', entry=hex(address)))

    def lookup(self):
        return self.dictionaries[self.e.reg('rcx')]['indices'].get(self.e.reg('edx'), -1)

    def append_node(self):
        p = self.e.alloc(80); self.e.write(p + 40, self.e.reg('rdx')); return p

    def insert(self):
        e = self.e; d = self.dictionaries[e.reg('rcx')]; key, node = e.reg('edx'), e.reg('r8')
        if key in d['indices'] or len(d['nodes']) >= 64: raise ValueError('Primitive map fixture capacity/duplicate insert')
        index = len(d['nodes']); d['indices'][key] = index; d['nodes'].append(node)
        e.write(d['entries'] + 32 + index * 24 + 16, node); return 1

    def snapshot(self, dictionary, depth):
        return {str(self.e.read(node + 40, 'I')): self.e.read(node + 44, 'I') for node in self.dictionaries[dictionary]['nodes']}

    def observe_store(self, address, size):
        for d in self.dictionaries.values():
            for node in d['nodes']:
                if node + 40 <= address < node + 48:
                    return dict(nodeOffset=address - node, size=size, source=hex(self.e.reg('rip')))
        return None


class InventoryContainers72(PlayerStoreGraph72):
    def __init__(self, engine, directory, audit):
        super().__init__(engine, directory, audit); e = self.e
        for index in TYPES:
            row = e.records[index]; fields = []
            for f in row['fields']:
                if f['offsetKind'] != 'instance': continue
                t = f['type']; kind = t['kind']; field = dict(clientField=f['name'], objectOffset=f['offset'], type=kind, cardinality='singular')
                if kind == 'genericinst':
                    if len(t['arguments']) == 2: field.update(type='map', cardinality='map', keyType='uint32', valueType='uint32')
                    else: field.update(type='uint32', cardinality='repeated', packed=True)
                fields.append(field)
            self.schemas[index] = dict(typeIndex=index, clientType=row['name'], fields=fields)
        self.maps = UInt32MapRuntime72(self); self.initializers = []
        for index, (entry, statics) in STATICS.items():
            before = len(self.maps.factory_calls); e.allow(entry); e.prepare(); e.run(entry)
            stores = []
            for offset, tag in statics:
                codec = e.read(GLOBAL + offset)
                # Map and repeated codecs have different concrete field layouts.
                if offset in (0x228D0, 0x342F0, 0x25200, 0x1B3B8, 0x2C270):
                    observed_tag = e.read(codec + 0x20, 'I'); self.maps.codecs[codec] = (index, tag)
                else: observed_tag = e.read(codec + 0x34, 'I')
                if observed_tag != tag: raise ValueError('Native primitive codec initializer changed')
                stores.append(dict(staticOffset=offset, tag=tag))
            self.initializers.append(dict(typeIndex=index, entry=hex(entry), factoryTags=self.maps.factory_calls[before:], stores=stores))
        self.heap_floor = e.heap_cursor

    def new_message(self, index, address=None):
        if index in TYPES: return GraphReader72.new_message(self, index, address)
        return super().new_message(index, address)

    def observe_entry(self, address): return GraphReader72.observe_entry(self, address)

    def snapshot(self, pointer, depth=0):
        if pointer and self.objects[pointer] in TYPES: return GraphReader72.snapshot(self, pointer, depth)
        return super().snapshot(pointer, depth)


def scan_raw(graph):
    samples = []; count = 0
    for index in sorted(TYPES):
        observed = {}
        for tag in range(1, 33):
            for wire, payload in ((0, varint(0x12345678)), (1, bytes(8)), (2, bytes.fromhex('020801')), (5, bytes(4))):
                row = graph.run_graph(index, varint(tag << 3 | wire) + payload); count += 1
                if row['result']:
                    if len(row['result']) != 1: raise ValueError('Ambiguous inventory container field branch')
                    name = next(iter(row['result'])); observed.setdefault(name, []).append((tag, wire))
                    samples.append(dict(typeIndex=index, tag=tag, wireType=wire, result=row['result'], entered=row['entered'], stores=row['stores']))
        for field in graph.schemas[index]['fields']:
            bindings = observed.pop(field['clientField'], [])
            if not bindings or len({tag for tag, _ in bindings}) != 1: raise ValueError('Inventory field missing/ambiguous')
            wires = sorted(wire for _, wire in bindings)
            expected = [0, 2] if field['cardinality'] == 'repeated' else [2] if field['cardinality'] == 'map' else [0]
            if wires != expected: raise ValueError('Inventory field wire mismatch')
            field.update(tag=bindings[0][0], acceptedWireTypes=wires, semanticStatus='unresolved')
        if observed: raise ValueError('Unexpected inventory native field')
    return dict(nativeFixtures=count, scope='six primitive container types, tags1..32/wire0,1,2,5', fields=samples)


def fixtures():
    cases = []
    for index, tag, name in [(14095, '1a', 'JMGKMMFNABP'), (28473, '0a', 'ALNBHHEJLKP'), (28475, '0a', 'ALNBHHEJLKP'),
                             (64947, '22', 'JMGKMMFNABP'), (61085, '0a', 'ALNBHHEJLKP')]:
        cases.extend([
            dict(name='map-nonzero-' + str(index), typeIndex=index, wire=tag + '040807100b', expected={name: {'7': 11}}),
            dict(name='map-duplicate-zero-' + str(index), typeIndex=index, wire=tag + '040807100b' + tag + '0408071000', expected={name: {'7': 0}}),
            dict(name='map-upper-and-default-' + str(index), typeIndex=index, wire=tag + '0c08ffffffff0f10ffffffff0f' + tag + '00', expected={name: {'4294967295': 4294967295, '0': 0}})])
    cases.extend([
        dict(name='list-packed-unpacked-append', typeIndex=14095, wire='100112048001ff011000', expected={'EIBDIEHEAKF': [1, 128, 255, 0]}),
        dict(name='list-zero-and-upper', typeIndex=14095, wire='120600ffffffff0f', expected={'EIBDIEHEAKF': [0, 4294967295]}),
        dict(name='three-lists', typeIndex=74063, wire='2a030180013a030280014203038001', expected={'FOFCJMPCEBB': [1, 128], 'IMEGOOADEBF': [2, 128], 'IHKPPDPBEML': [3, 128]}),
        dict(name='all-six-64947', typeIndex=64947, wire='080b1016182122040807100b28023203018001',
             expected={'NJAGFJINHMA': 11, 'NILMBAGDEAL': 22, 'KFDCBKBPPGL': 33, 'JMGKMMFNABP': {'7': 11}, 'MNCFEDPDEAG': True, 'FLGGIMBHFOO': [1, 128]}),
        dict(name='scalar-duplicate-wrong-wire-and-bool-zero', typeIndex=64947, wire='080108020a0028012800', expected={'NJAGFJINHMA': 2}),
        dict(name='map-key-only', typeIndex=61085, wire='0a020807', expected={'ALNBHHEJLKP': {'7': 0}}),
        dict(name='map-value-only', typeIndex=61085, wire='0a02100b', expected={'ALNBHHEJLKP': {'0': 11}}),
        dict(name='map-entry-duplicates-and-unknown', typeIndex=61085, wire='0a0a08070808100b1801100c', expected={'ALNBHHEJLKP': {'8': 12}})])
    for index in sorted(TYPES): cases.append(dict(name='empty-' + str(index), typeIndex=index, wire='', expected={}))
    return cases


def verify(graph):
    good = []
    for case in fixtures():
        row = graph.run_graph(case['typeIndex'], bytes.fromhex(case['wire']))
        if row['result'] != case['expected']: raise ValueError(case['name'] + ': primitive native result differs ' + str(row['result']))
        if case['name'].startswith('map-duplicate-zero-') and not row['mapStores']: raise ValueError('No native map overwrite observed')
        good.append(dict(case, observation=row))
    rejected = []
    for name, index, wire in [('truncated-map', 61085, '0a03080110'), ('truncated-packed', 14095, '120180'), ('invalid-tag', 64947, '00')]:
        try: graph.run_graph(index, bytes.fromhex(wire))
        except ValueError as error: rejected.append(dict(name=name, wire=wire, error=str(error), scope='explicit-input-model'))
        else: raise ValueError('Malformed primitive fixture accepted')
    return good, rejected


def main():
    p = argparse.ArgumentParser(description=__doc__); p.add_argument('--client', required=True, type=Path)
    p.add_argument('--output', required=True, type=Path); p.add_argument('--observations', required=True, type=Path)
    p.add_argument('--directory', type=Path, default=Path('protocol/cn-7.2')); p.add_argument('--python-libs', type=Path, default=Path('local/migration72/python')); a = p.parse_args()
    paths = [a.output.resolve(), a.observations.resolve()]
    if len(set(paths)) != 2 or any(x.is_relative_to(a.client.resolve()) for x in paths): p.error('Distinct outputs outside client required')
    source = (a.directory / 'player-store-outer-observations.json').read_bytes()
    if hashlib.sha256(source).hexdigest() != SOURCE_SHA: raise ValueError('Outer inventory evidence source drift')
    audit = json.loads(source)['dependencyAudit']
    b, m, s = [read_verified(a.client / name, digest) for name, digest in HASHES.items()]; records, _ = extract(b, m, startup=s, structures=True)
    for row in audit['types']:
        if row['fields'] != [f for f in records[row['typeIndex']]['fields'] if f['offsetKind'] == 'instance']: raise ValueError('Inventory metadata layout drift')
    bindings = primitive_aot(b, m, s, records); e = Offline72(b, a.python_libs, records, trace_instructions=False)
    graph = InventoryContainers72(e, a.directory, audit)
    try: scan = scan_raw(graph); good, bad = verify(graph)
    finally: graph.close(); e.close()
    context = dict(schemaVersion=1, task='F-03d2a2', inputSHA256=HASHES, outerEvidenceSHA256=SOURCE_SHA,
        runtimeStatus='not-tested', semanticIdentityStatus='unresolved', fullStoreRawClosure=False,
        messages=[graph.schemas[i] for i in sorted(TYPES)], primitiveMapAOT=bindings,
        initializers=graph.initializers, scan=scan, nativeFixtures=len(good), rejections=bad,
        limitations=['Native concrete primitive map reader/adapter/setter, list append and codec initializers execute',
            'Managed dictionary lookup/insertion and packed-pair node allocation are explicit fixed-capacity64 fixtures',
            'Managed allocation, metadata usage, primitive input/delegates and initialized class context modeled',
            'No resize/IFix/live ABI/handler/UI/client acceptance; nested oneof combination remains F-03d2a3'])
    summary = dict(context, fixtures=[dict(name=x['name'], typeIndex=x['typeIndex'], wire=x['wire'], result=x['observation']['result'], entered=x['observation']['entered'], mapStores=x['observation']['mapStores']) for x in good])
    for path, data in zip(paths, [dict(context, fixtures=good), summary]):
        path.parent.mkdir(parents=True, exist_ok=True); path.write_text(json.dumps(data, indent=2) + '\n', 'utf8')
    print(json.dumps(dict(messages=len(TYPES), fields=sum(len(x['fields']) for x in context['messages']), scanFixtures=scan['nativeFixtures'], nativeFixtures=len(good), rejections=len(bad))))


if __name__ == '__main__': main()
