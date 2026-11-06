"""F-03d2a3: complete raw inventory graph, without business-name promotion."""
import argparse
import hashlib
import json
from pathlib import Path
try:
    from .extract_client72 import HASHES, read_verified, extract
    from .client72_emulator import Offline72
    from .client72_graph import GraphReader72
    from .trace_client72_player_store import ROOT, ITEM, SCHEMAS as OUTER_SCHEMAS
    from .trace_client72_inventory_containers import InventoryContainers72, primitive_aot, SOURCE_SHA
    from .trace_client72_token import varint
except ImportError:
    from extract_client72 import HASHES, read_verified, extract
    from client72_emulator import Offline72
    from client72_graph import GraphReader72
    from trace_client72_player_store import ROOT, ITEM, SCHEMAS as OUTER_SCHEMAS
    from trace_client72_inventory_containers import InventoryContainers72, primitive_aot, SOURCE_SHA
    from trace_client72_token import varint

CONTAINERS_SHA = '352f094fec870074abb70207af317870525b025c9b68805500c6410347feb72d'


def field(name, offset, tag, kind='uint32', child=None, discriminator=None):
    row = dict(clientField=name, objectOffset=offset, tag=tag, type=kind, cardinality='singular')
    if child is not None: row.update(nestedTypeIndex=child)
    if discriminator is not None: row.update(oneof='raw_detail', discriminatorOffset=discriminator, discriminatorValue=tag)
    return row


REMAINING = {
    63442: dict(typeIndex=63442, clientType='KKOPHGDBCEO', fields=[field('GNHFNECPAHD', 32, 1), field('PNGOHOKGKHP', 24, 2, 'message', 28471)]),
    63899: dict(typeIndex=63899, clientType='MKFCDFEEAAL', fields=[field('GNHFNECPAHD', 32, 1), field('PNGOHOKGKHP', 24, 2, 'message', 61085)]),
    28471: dict(typeIndex=28471, clientType='JDDDHIGLMGJ', fields=[field('IJPBJCNCDEC', 36, 1, 'bool'),
        field('PNGOHOKGKHP_2', 24, 2, 'message', 28473, 32), field('PNGOHOKGKHP_3', 24, 3, 'message', 28474, 32),
        field('PNGOHOKGKHP_4', 24, 4, 'message', 28475, 32)]),
    44169: dict(typeIndex=44169, clientType='BNDIKNLDLOJ', fields=[field('FAPABAJBMAK', 32, 3, 'bool'),
        field('FLPGMLOFAKP_1', 24, 1, 'message', 74063, 36), field('FLPGMLOFAKP_2', 24, 2, 'message', 64947, 36)]),
    28474: dict(typeIndex=28474, clientType='KNNPHAOJMAH', fields=[field('BHOHKJOCNOL', 24, 1)])}


class InventoryGraph72(InventoryContainers72):
    def __init__(self, engine, directory, audit):
        self.copy_runtime = None
        super().__init__(engine, directory, audit); self.schemas.update(REMAINING)
        self.full_types = set(OUTER_SCHEMAS) | set(REMAINING) | {14095, 28473, 28475, 61085, 64947, 74063}
        for index in sorted(self.full_types):
            for method in engine.records[index]['methods']:
                if method['name'] in ('CopyFrom', 'GFJDJODGIDB', 'JMOJGIHEHFK', 'DGOODBNLMIN'):
                    engine.allow(int(method['registeredAddress'], 16))
        self.copy_runtime = InventoryCopyRuntime72(self)
        self.heap_floor = engine.heap_cursor

    def new_message(self, index, address=None):
        # This is called by the base initializer before the complete set exists.
        if index in OUTER_SCHEMAS or index in REMAINING or index in (14095, 28473, 28475, 61085, 64947, 74063):
            pointer = GraphReader72.new_message(self, index, address)
            if self.copy_runtime: self.copy_runtime.decorate(pointer, index)
            return pointer
        return super().new_message(index, address)

    def snapshot(self, pointer, depth=0): return GraphReader72.snapshot(self, pointer, depth)

    def observe_entry(self, address):
        if self.active and self.copy_runtime: self.copy_runtime.before(address)
        return super().observe_entry(address)


class InventoryCopyRuntime72:
    """Managed enumerable/array fixtures below native map clear/copy and list copy."""
    def __init__(self, graph):
        self.g, self.e, self.maps = graph, graph.e, graph.maps; e = self.e
        self.enumerators = {}; self.array_widths = {}; self.clearing = None
        enumerable, move, current, dispose = [e.alloc() for _ in range(4)]
        for cell, iface in ((0x1458A7398, enumerable), (0x1458861A8, move), (0x1458A73A0, current), (0x1458861B0, dispose)):
            e.write(cell, iface)
        self.map_class = self.interface_class([(enumerable, self.get_enumerator)])
        self.enumerator_class = self.interface_class([(move, self.move_next), (current, self.current), (dispose, lambda: 0)])
        self.clear_stub = e.model_stub(lambda: 0)
        e.models[0x159C68C70] = self.array_clear
        self.insert_original = e.models[0x1578B6A20]; e.models[0x1578B6A20] = self.insert
        for entry in (0x15893C8B0, 0x15893C340, 0x15893D3A0, 0x158DB7CD0): e.allow_unwind(entry)

    def interface_class(self, methods):
        e = self.e; klass = e.alloc(1024); table = e.alloc(len(methods) * 16)
        e.write(klass + 0x80, table); e.write(klass + 0xC0, len(methods), 'H'); e.write(klass + 0xBE, 32, 'H')
        for slot, (iface, callback) in enumerate(methods):
            e.write(table + slot * 16, iface); e.write(table + slot * 16 + 8, slot, 'I')
            e.write(klass + 0xD0 + slot * 8, e.model_stub(callback))
            e.write(klass + 0xD0 + (32 + slot) * 8, e.alloc())
        return klass

    def decorate(self, pointer, index):
        e = self.e
        for field in e.records[index]['fields']:
            typ = field['type']
            if field['offsetKind'] != 'instance' or typ['kind'] != 'genericinst' or len(typ['arguments']) != 2: continue
            container = e.read(pointer + field['offset']); e.write(container, self.map_class)
            dictionary = e.read(container + 16); d = self.maps.dictionaries[dictionary]
            bucket_array = e.alloc(32 + 64 * 4); e.write(bucket_array + 24, 64, 'I'); e.write(dictionary + 32, bucket_array)
            self.array_widths[bucket_array] = 4; self.array_widths[d['entries']] = 24
            linked_class = e.read(e.read(container + 24)); e.write(linked_class + 0x158, self.clear_stub)

    def insert(self):
        dictionary = self.e.reg('rcx'); result = self.insert_original()
        self.e.write(dictionary + 0x38, len(self.maps.dictionaries[dictionary]['nodes']), 'I'); return result

    def before(self, address):
        e = self.e
        if address in (0x15893C8B0, 0x15893C340, 0x15893D3A0, 0x158DB7CD0):
            self.g.entered.append(dict(kind='native-copy-helper', entry=hex(address)))
        if address == 0x15893C8B0: self.clearing = e.read(e.reg('rcx') + 16)
        if address == 0x15893C921:
            d = self.maps.dictionaries[self.clearing]; d['nodes'].clear(); d['indices'].clear(); self.clearing = None

    def array_clear(self):
        e = self.e; array, start, count = e.reg('rcx'), e.reg('edx'), e.reg('r8d')
        width = self.array_widths[array]
        if start + count > e.read(array + 24, 'I'): raise ValueError('Bounded managed clear capacity')
        if count: e.uc.mem_write(array + 32 + start * width, bytes(count * width))
        return 0

    def get_enumerator(self):
        e = self.e; dictionary = e.read(e.reg('rcx') + 16); p = e.alloc(); e.write(p, self.enumerator_class)
        self.enumerators[p] = dict(dictionary=dictionary, position=-1); return p

    def move_next(self):
        state = self.enumerators[self.e.reg('rcx')]; state['position'] += 1
        return int(state['position'] < len(self.maps.dictionaries[state['dictionary']]['nodes']))

    def current(self):
        state = self.enumerators[self.e.reg('rcx')]; nodes = self.maps.dictionaries[state['dictionary']]['nodes']
        return self.e.read(nodes[state['position']] + 40)


def message(tag, payload): return varint(tag << 3 | 2) + varint(len(payload)) + payload


def fixtures():
    raw_map = bytes.fromhex('0a040807100b100c')
    tps = bytes.fromhex('0801') + message(2, raw_map)
    special = bytes.fromhex('080b') + message(2, tps)
    ordinary = bytes.fromhex('080b') + message(2, bytes.fromhex('0a040807100b'))
    six = bytes.fromhex('080b1016182122040807100b28023203018001')
    equip = bytes.fromhex('1801') + message(2, six)
    furniture = bytes.fromhex('12030180011a040807100b')
    expected_tps = {'IJPBJCNCDEC': True, 'PNGOHOKGKHP_2': {'ALNBHHEJLKP': {'7': 11}, 'MNBPLFPCJLG': 12}}
    expected_special = {'GNHFNECPAHD': 11, 'PNGOHOKGKHP': expected_tps}
    expected_six = {'NJAGFJINHMA': 11, 'NILMBAGDEAL': 22, 'KFDCBKBPPGL': 33, 'JMGKMMFNABP': {'7': 11}, 'MNCFEDPDEAG': True, 'FLGGIMBHFOO': [1, 128]}
    expected_equip = {'FAPABAJBMAK': True, 'FLPGMLOFAKP_2': expected_six}
    expected_ordinary = {'GNHFNECPAHD': 11, 'PNGOHOKGKHP': {'ALNBHHEJLKP': {'7': 11}}}
    expected_furniture = {'EIBDIEHEAKF': [1, 128], 'JMGKMMFNABP': {'7': 11}}
    branches = {5: (special, expected_special), 6: (equip, expected_equip), 7: (bytes.fromhex('082a'), {'GNHFNECPAHD': 42}),
                8: (ordinary, expected_ordinary), 9: (bytes.fromhex('084d'), {'GNHFNECPAHD': 77}), 10: (furniture, expected_furniture)}
    cases = [dict(name='leaf-u32-upper', typeIndex=28474, wire='08ffffffff0f', expected={'BHOHKJOCNOL': 4294967295}),
        dict(name='nested-special', typeIndex=63442, wire=special.hex(), expected=expected_special),
        dict(name='nested-ordinary', typeIndex=63899, wire=ordinary.hex(), expected=expected_ordinary),
        dict(name='equip-branch-two', typeIndex=44169, wire=equip.hex(), expected=expected_equip),
        dict(name='tps-branch-two', typeIndex=28471, wire=tps.hex(), expected=expected_tps),
        dict(name='tps-branch-three', typeIndex=28471, wire='1a02082a', expected={'PNGOHOKGKHP_3': {'BHOHKJOCNOL': 42}}),
        dict(name='tps-branch-four', typeIndex=28471, wire='220a0a040807100b100c180d', expected={'PNGOHOKGKHP_4': {'ALNBHHEJLKP': {'7': 11}, 'PGDAIKELICG': 12, 'MNBPLFPCJLG': 13}}),
        dict(name='equip-branch-one', typeIndex=44169, wire='0a0f2a030180013a030280014203038001', expected={'FLPGMLOFAKP_1': {'FOFCJMPCEBB': [1, 128], 'IMEGOOADEBF': [2, 128], 'IHKPPDPBEML': [3, 128]}})]
    items = []; expected_items = []
    for tag, (payload, result) in branches.items():
        wire = bytes.fromhex('082a108180808080808010') + message(tag, payload)
        expected = {'PJAGIOHBKLD': 42, 'FBLKPHOCBIK': 9007199254740993, 'FLPGMLOFAKP_' + str(tag): result}
        cases.append(dict(name='item-nonempty-' + str(tag), typeIndex=ITEM, wire=wire.hex(), expected=expected))
        items.append(message(2, wire)); expected_items.append(expected)
    cases.append(dict(name='root-six-nonempty-items', typeIndex=ROOT, wire=(b''.join(items) + bytes.fromhex('400b7001')).hex(),
        expected={'MBEEKCPJBEL': expected_items, 'PAKLDABINMP': 11, 'CLFJNKKFONI': 1}))
    cases.extend([
        dict(name='same-item-ten-map-overwrite-list-append', typeIndex=ITEM,
             wire=(message(10, furniture) + message(10, bytes.fromhex('120280011a0408071000'))).hex(),
             expected={'FLPGMLOFAKP_10': {'EIBDIEHEAKF': [1, 128, 128], 'JMGKMMFNABP': {'7': 0}}}),
        dict(name='same-item-eight-keep-map-explicit-zero-count', typeIndex=ITEM,
             wire=(message(8, ordinary) + message(8, bytes.fromhex('0800'))).hex(),
             expected={'FLPGMLOFAKP_8': {'PNGOHOKGKHP': {'ALNBHHEJLKP': {'7': 11}}}}),
        dict(name='same-item-five-preserve-nested-zero-count', typeIndex=ITEM,
             wire=(message(5, special) + message(5, bytes.fromhex('0800'))).hex(),
             expected={'FLPGMLOFAKP_5': {'PNGOHOKGKHP': expected_tps}}),
        dict(name='tps-switch-and-return-empty', typeIndex=28471, wire=(message(2, raw_map) + message(3, bytes.fromhex('082a')) + message(2, b'')).hex(),
             expected={'PNGOHOKGKHP_2': {}}),
        dict(name='equip-switch-and-return-empty', typeIndex=44169, wire=(message(2, six) + message(1, bytes.fromhex('2a0101')) + message(2, b'')).hex(),
             expected={'FLPGMLOFAKP_2': {}})])
    merged_six = dict(expected_six, JMGKMMFNABP={'7': 0}, FLGGIMBHFOO=[1, 128, 0]); merged_six.pop('MNCFEDPDEAG')
    merge_payload = bytes.fromhex('2204080710003201002800')
    three_lists = bytes.fromhex('2a030180013a030280014203038001')
    cases.extend([
        dict(name='same-equip-one-list-append', typeIndex=44169, wire=(message(1, three_lists) + message(1, bytes.fromhex('2a0104'))).hex(),
             expected={'FLPGMLOFAKP_1': {'FOFCJMPCEBB': [1, 128, 4], 'IMEGOOADEBF': [2, 128], 'IHKPPDPBEML': [3, 128]}}),
        dict(name='same-equip-two-map-zero-list-append', typeIndex=44169, wire=(message(2, six) + message(2, merge_payload)).hex(), expected={'FLPGMLOFAKP_2': merged_six}),
        dict(name='same-item-six-nested-merge-and-bool-zero', typeIndex=ITEM,
             wire=(message(6, equip) + message(6, bytes.fromhex('1800') + message(2, merge_payload))).hex(),
             expected={'FLPGMLOFAKP_6': {'FLPGMLOFAKP_2': merged_six}}),
        dict(name='same-tps-two-map-zero-counter-zero', typeIndex=28471,
             wire=(tps + bytes.fromhex('0800') + message(2, bytes.fromhex('0a04080710001000'))).hex(),
             expected={'PNGOHOKGKHP_2': {'ALNBHHEJLKP': {'7': 0}}}),
        dict(name='same-tps-four-counter-zero', typeIndex=28471,
             wire='220a0a040807100b100c180d22021000', expected={'PNGOHOKGKHP_4': {'ALNBHHEJLKP': {'7': 11}, 'MNBPLFPCJLG': 13}}),
        dict(name='same-tps-three-zero', typeIndex=28471, wire='1a02082a1a020800', expected={'PNGOHOKGKHP_3': {}}),
        dict(name='same-item-five-tps-type-switch', typeIndex=ITEM,
             wire=(message(5, special) + message(5, message(2, message(3, bytes.fromhex('0863'))))).hex(),
             expected={'FLPGMLOFAKP_5': {'GNHFNECPAHD': 11, 'PNGOHOKGKHP': {'IJPBJCNCDEC': True, 'PNGOHOKGKHP_3': {'BHOHKJOCNOL': 99}}}}),
        dict(name='same-item-eight-map-zero', typeIndex=ITEM,
             wire=(message(8, ordinary) + message(8, message(2, bytes.fromhex('0a0408071000')))).hex(),
             expected={'FLPGMLOFAKP_8': {'GNHFNECPAHD': 11, 'PNGOHOKGKHP': {'ALNBHHEJLKP': {'7': 0}}}}),
        dict(name='same-item-seven-zero', typeIndex=ITEM, wire='3a02082a3a020800', expected={'FLPGMLOFAKP_7': {}}),
        dict(name='same-item-nine-zero', typeIndex=ITEM, wire='4a02082a4a020800', expected={'FLPGMLOFAKP_9': {}}),
        dict(name='item-switch-all-and-return-empty', typeIndex=ITEM,
             wire=(b''.join(message(tag, data) for tag, (data, _) in branches.items()) + message(5, b'')).hex(), expected={'FLPGMLOFAKP_5': {}}),
        dict(name='root-outer-upper-and-high-guid', typeIndex=ROOT,
             wire=(message(2, bytes.fromhex('08ffffffff0f10ffffffffffffffffff01')) + bytes.fromhex('40ffffffff0f70ffffffff0f')).hex(),
             expected={'MBEEKCPJBEL': [{'PJAGIOHBKLD': 4294967295, 'FBLKPHOCBIK': 18446744073709551615}], 'PAKLDABINMP': 4294967295, 'CLFJNKKFONI': 4294967295})])
    return cases


def scan_raw(graph):
    selected = []; count = 0
    for index in sorted(graph.full_types):
        observed = {}
        for tag in range(1, 33):
            for wire, payload in ((0, varint(0x12345678)), (1, bytes(8)), (2, bytes.fromhex('020801')), (5, bytes(4))):
                row = graph.run_graph(index, varint(tag << 3 | wire) + payload); count += 1
                if row['result']:
                    if len(row['result']) != 1: raise ValueError('Ambiguous complete inventory field branch')
                    name = next(iter(row['result'])); observed.setdefault(name, []).append((tag, wire))
                    selected.append(dict(typeIndex=index, tag=tag, wireType=wire, result=row['result'], stores=row['stores'], entered=row['entered']))
        for field in graph.schemas[index]['fields']:
            bindings = observed.pop(field['clientField'], [])
            if not bindings or len({t for t, _ in bindings}) != 1: raise ValueError('Complete inventory field missing ' + str(index) + '/' + field['clientField'])
            tag = bindings[0][0]; wires = sorted(w for _, w in bindings)
            expected = [0, 2] if field['cardinality'] == 'repeated' and field['type'] == 'uint32' else [2] if field['cardinality'] != 'singular' or field['type'] == 'message' else [0]
            if field.get('tag', tag) != tag or wires != expected: raise ValueError('Complete inventory tag/wire drift')
            field.update(tag=tag, acceptedWireTypes=wires, semanticStatus='unresolved')
        if observed: raise ValueError('Unexpected complete inventory raw field')
    return dict(nativeFixtures=count, scope='15-type complete metadata/object/oneof raw closure; tags1..32/wire0,1,2,5', fields=selected)


def verify(graph):
    good = []
    for case in fixtures():
        row = graph.run_graph(case['typeIndex'], bytes.fromhex(case['wire']))
        if row['result'] != case['expected']: raise ValueError(case['name'] + ': full native graph differs ' + str(row['result']))
        good.append(dict(case, observation=row))
    rejected = []
    for name, index, wire, limit in [('recursion-limit', ROOT, '1200', 0),
        ('negative-length', ROOT, '12ffffffff0f', 100), ('truncated-nested', ROOT, '12050a00', 100),
        ('invalid-tag', ROOT, '00', 100), ('packed-truncated', 64947, '320180', 100),
        ('guid-overflow', ITEM, '10ffffffffffffffffff02', 100)]:
        try: graph.run_graph(index, bytes.fromhex(wire), limit)
        except ValueError as error:
            text = str(error); scope = 'native-error-branch' if 'Native error branch:' in text else 'explicit-input-model'
            rejected.append(dict(name=name, typeIndex=index, wire=wire, recursionLimit=limit, error=text, scope=scope))
        else: raise ValueError('Invalid complete inventory fixture accepted: ' + name)
    return good, rejected


def main():
    p = argparse.ArgumentParser(description=__doc__); p.add_argument('--client', required=True, type=Path)
    p.add_argument('--output', required=True, type=Path); p.add_argument('--observations', required=True, type=Path)
    p.add_argument('--directory', type=Path, default=Path('protocol/cn-7.2')); p.add_argument('--python-libs', type=Path, default=Path('local/migration72/python')); a = p.parse_args()
    paths = [a.output.resolve(), a.observations.resolve()]
    if len(set(paths)) != 2 or any(x.is_relative_to(a.client.resolve()) for x in paths): p.error('Distinct outputs outside client required')
    outer = (a.directory / 'player-store-outer-observations.json').read_bytes(); containers = (a.directory / 'inventory-container-observations.json').read_bytes()
    if hashlib.sha256(outer).hexdigest() != SOURCE_SHA or hashlib.sha256(containers).hexdigest() != CONTAINERS_SHA: raise ValueError('Inventory graph evidence source drift')
    audit = json.loads(outer)['dependencyAudit']; b, m, s = [read_verified(a.client / name, digest) for name, digest in HASHES.items()]
    records, _ = extract(b, m, startup=s, structures=True); bindings = primitive_aot(b, m, s, records)
    e = Offline72(b, a.python_libs, records, trace_instructions=False); graph = InventoryGraph72(e, a.directory, audit)
    try: scan = scan_raw(graph); good, rejected = verify(graph)
    finally: graph.close(); e.close()
    context = dict(schemaVersion=1, task='F-03d2a3', inputSHA256=HASHES, outerEvidenceSHA256=SOURCE_SHA,
        containerEvidenceSHA256=CONTAINERS_SHA, runtimeStatus='not-tested', semanticIdentityStatus='unresolved',
        fullStoreRawClosure=True, targetProtocolReady=False, messages=[graph.schemas[i] for i in sorted(graph.full_types)], scan=scan,
        primitiveMapAOT=bindings, nativeFixtures=len(good), rejections=rejected,
        limitations=['Raw graph only; all business identities/field names remain unresolved',
                    'Managed allocation/default containers, metadata contexts, primitives/delegates, array copying/clearing and bounded dictionary lookup/insertion modeled',
                    'Managed map enumeration/linked-clear callbacks modeled; concrete message clones, map clear/copy and list copy execute natively',
                    'No IFix/live ABI/handler/UI/client acceptance or protocol production integration'])
    summary = dict(context, fixtures=[dict(name=x['name'], typeIndex=x['typeIndex'], wire=x['wire'], result=x['observation']['result'],
        entered=x['observation']['entered'], mapStores=x['observation']['mapStores']) for x in good])
    for path, value in zip(paths, [dict(context, fixtures=good), summary]):
        path.parent.mkdir(parents=True, exist_ok=True); path.write_text(json.dumps(value, indent=2) + '\n', 'utf8')
    print(json.dumps(dict(messages=len(graph.full_types), fields=sum(len(x['fields']) for x in context['messages']), scanFixtures=scan['nativeFixtures'], nativeFixtures=len(good))))


if __name__ == '__main__': main()
