"""F-03d3d: Avatar child16352/71717 plus the reused14-type raw Item graph.

No named identity promotion; complex child53824 remains explicitly refused.
"""
import argparse
import copy
import hashlib
import json
import struct
from pathlib import Path
try:
    from .extract_client72 import HASHES, PE, HEADER_VA, CODE_REGISTRATION, TypeResolver, number, read_verified, extract
    from .client72_emulator import Offline72, STREAM, GLOBAL
    from .client72_graph import GraphReader72
    from .trace_client72_avatar_core import ROOT, core_aot
    from .trace_client72_avatar_trees import AvatarTrees72, fixtures as tree_fixtures
    from .trace_client72_player_store import type_cells, ITEM
    from .trace_client72_inventory_containers import STATICS, primitive_aot
    from .trace_client72_inventory_graph import InventoryCopyRuntime72, fixtures as inventory_fixtures
    from .trace_client72_token import varint, load_emulator
except ImportError:
    from extract_client72 import HASHES, PE, HEADER_VA, CODE_REGISTRATION, TypeResolver, number, read_verified, extract
    from client72_emulator import Offline72, STREAM, GLOBAL
    from client72_graph import GraphReader72
    from trace_client72_avatar_core import ROOT, core_aot
    from trace_client72_avatar_trees import AvatarTrees72, fixtures as tree_fixtures
    from trace_client72_player_store import type_cells, ITEM
    from trace_client72_inventory_containers import STATICS, primitive_aot
    from trace_client72_inventory_graph import InventoryCopyRuntime72, fixtures as inventory_fixtures
    from trace_client72_token import varint, load_emulator

NEW = {16352, 71717}
INVENTORY_SHA = 'ca45b0d9b038cd46fc35943924ca77ae46c8fbae82c06e131245e03f43d95b18'


def item_audit(binary, records, cells, expected, python_libs):
    load_emulator(python_libs)
    from capstone import Cs, CS_ARCH_X86, CS_MODE_64
    pe = PE(binary); md = Cs(CS_ARCH_X86, CS_MODE_64); md.detail = True
    ends = sorted({int(m['registeredAddress'], 16) for t in records for m in t['methods'] if m['registeredAddress'] != '0x0'})
    pending = [16352]; seen = set(); edges = []; rows = []; references = []
    while pending:
        index = pending.pop()
        if index in seen: continue
        seen.add(index); row = records[index]; fields = [f for f in row['fields'] if f['offsetKind'] == 'instance']
        def add(typ, source):
            for arg in typ.get('arguments', [typ]):
                child = arg.get('typeDefinition')
                if arg['kind'] == 'class' and child is not None and any(m['name'] == 'MOMMACBEMEK' for m in records[child]['methods']):
                    edges.append(dict(sourceType=index, targetType=child, source=source)); pending.append(child)
        for f in fields: add(f['type'], 'field:' + f['name'])
        methods = [m for m in row['methods'] if m['name'] in ('MOMMACBEMEK', '.cctor')]
        for method in methods:
            entry = int(method['registeredAddress'], 16); end = next(x for x in ends if x > entry)
            raw = binary[pe.offset(entry):pe.offset(entry) + end - entry]
            if raw[:1] == b'\xe9': raise ValueError('Unexpected protected Item dependency reader')
            for ins in md.disasm(raw, entry):
                for operand in ins.operands:
                    if operand.type == 3 and operand.mem.base == 41:
                        cell = ins.address + ins.size + operand.mem.disp
                        if cell not in cells: continue
                        references.append(dict(owner=index, method=method['name'], site=hex(ins.address), **cells[cell]))
                        add(cells[cell]['type'], 'native:' + hex(ins.address))
        rows.append(dict(typeIndex=index, clientType=row['name'], fields=fields, methods=methods))
    if seen != expected or sum(len(x['fields']) for x in rows) != 45: raise ValueError('Avatar Item dependency/storage scope drift')
    return dict(typeCount=len(rows), instanceStorageFields=45, newTypeIndices=sorted(NEW), reusedTypeCount=14,
        scope='Metadata fields and bounded reader/cctor usage references; Object wire branches validated separately',
        types=sorted(rows, key=lambda x: x['typeIndex']), edges=edges, nativeUsageReferences=references,
        objectOwners=sorted(x['typeIndex'] for x in rows if any(f['type']['kind'] == 'object' for f in x['fields'])))


def item_list_aot(binary, metadata, startup, records):
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
        if not 0 <= pointer < count or not 0 <= spec < spec_count: raise ValueError('Item list AOT reference bounds')
        method, cls, args = struct.unpack_from('<iii', metadata, specs + spec * 12); owner, row = methods[method]
        if (owner['index'], cls) != (25060, 10366) or row['name'] not in ('.ctor', 'FNNADLCNGFO'): continue
        if args != -1: raise ValueError('Unexpected Item list AOT method arguments')
        address = number(binary, pointers + pointer * 8, 'Q'); pe.offset(address, executable=True)
        rows.append(dict(owner=owner['index'], classInstance=cls, method=row['name'], methodDefinition=method,
            tableIndex=i, methodSpec=spec, pointerIndex=pointer, address=hex(address),
            arguments=[resolver.resolve_index(x) for x in resolver.arguments[cls]]))
    hits = [x for x in rows if x['method'] == 'FNNADLCNGFO']
    if len(hits) != 1 or hits[0]['address'] != '0x158dc41f0' or hits[0]['arguments'][0].get('typeDefinition') != ITEM: raise ValueError('Item list AOT drift')
    return rows


class AvatarItemCopy72(InventoryCopyRuntime72):
    def __init__(self, graph):
        super().__init__(graph); self.maps = graph.maps.integer

    def insert(self):
        dictionary = self.e.reg('rcx'); result = self.insert_original()
        if dictionary in self.maps.dictionaries:
            self.e.write(dictionary + 0x38, len(self.maps.dictionaries[dictionary]['nodes']), 'I')
        return result


class AvatarItems72(AvatarTrees72):
    def __init__(self, engine, directory, cells, bindings, item_schemas):
        self.item_copy = None; self.item_types = set(item_schemas)
        super().__init__(engine, directory, cells, bindings); e = self.e
        self.schemas.update(copy.deepcopy(item_schemas))
        for index in NEW | self.item_types:
            row = e.records[index]; entry = next(int(m['registeredAddress'], 16) for m in row['methods'] if m['name'] == 'MOMMACBEMEK')
            klass = e.alloc(1024); table = e.alloc(16); e.write(klass + 0xC7, 1, 'B')
            e.write(klass + 0x80, table); e.write(klass + 0xC0, 1, 'H'); e.write(klass + 0xBE, 32, 'H')
            e.write(table, self.interface); e.write(klass + 0xE8, entry)
            self.classes[index] = klass; self.class_indices[klass] = index; self.entries[index] = entry; e.allow(entry)
            if index in NEW:
                fields = []
                for f in row['fields']:
                    if f['offsetKind'] != 'instance': continue
                    typ = f['type']; field = dict(clientField=f['name'], objectOffset=f['offset'], type=typ['kind'], cardinality='singular')
                    if typ['kind'] == 'class': field.update(type='message', nestedTypeIndex=typ['typeDefinition'])
                    elif typ['kind'] == 'genericinst': field.update(type='message', cardinality='repeated', nestedTypeIndex=typ['arguments'][0]['typeDefinition'])
                    fields.append(field)
                self.schemas[index] = dict(typeIndex=index, clientType=row['name'], fields=fields)
            for method in row['methods']:
                if method['name'] in ('CopyFrom', 'GFJDJODGIDB', 'JMOJGIHEHFK', 'DGOODBNLMIN'): e.allow(int(method['registeredAddress'], 16))
        for cell, row in cells.items():
            typ = row['type']
            if typ['kind'] == 'class' and typ.get('typeDefinition') in NEW | self.item_types: e.write(cell, self.classes[typ['typeDefinition']])
        self.item_initializers = []
        for index in sorted(self.item_types | NEW):
            for method in e.records[index]['methods']:
                if method['name'] != '.cctor': continue
                before = len(self.factory_calls); entry = int(method['registeredAddress'], 16)
                e.allow(entry); e.prepare(); e.run(entry)
                for offset, tag in STATICS.get(index, (None, []))[1]:
                    # Only concrete primitive maps need codec registration.
                    codec = e.read(GLOBAL + offset)
                    if offset in (0x228D0, 0x342F0, 0x25200, 0x1B3B8, 0x2C270): self.maps.integer.codecs[codec] = (index, tag)
                self.item_initializers.append(dict(typeIndex=index, entry=hex(entry), factoryArguments=self.factory_calls[before:]))
        self.item_copy = AvatarItemCopy72(self); self.heap_floor = e.heap_cursor

    def new_message(self, index, address=None):
        if index in NEW | self.item_types:
            pointer = GraphReader72.new_message(self, index, address)
            if self.item_copy and index in self.item_types: self.item_copy.decorate(pointer, index)
            return pointer
        return super().new_message(index, address)

    def snapshot(self, pointer, depth=0):
        if pointer and self.objects[pointer] in NEW | self.item_types: return GraphReader72.snapshot(self, pointer, depth)
        return super().snapshot(pointer, depth)

    def observe_entry(self, address):
        if self.active:
            if address == self.entries[53824]:
                stream = self.e.reg('rdx')
                if self.e.read(stream + 0x44, 'I') != self.e.read(stream + 0x38, 'I'): raise ValueError('Nonempty child outside F-03d3d: 53824')
            if self.item_copy: self.item_copy.before(address)
            if address == 0x158DB8E10:
                container = self.e.reg('rcx')
                for pointer, index in self.objects.items():
                    if index not in self.item_types: continue
                    for field in self.schemas[index]['fields']:
                        if field['cardinality'] == 'repeated' and field['type'] == 'uint32' and self.e.read(pointer + field['objectOffset']) == container:
                            self.entered.append(dict(kind='native-list-field', ownerType=index, clientField=field['clientField'], objectOffset=field['objectOffset'], key=self.e.read(STREAM + 0x20, 'I'), entry=hex(address)))
        return GraphReader72.observe_entry(self, address)


def scan_raw(graph):
    selected = []; count = 0
    for index in sorted(NEW | graph.item_types):
        observed = {}
        for tag in range(1, 33):
            for wire, payload in ((0, varint(0x12345678)), (1, bytes(8)), (2, b'\0'), (5, bytes(4))):
                row = graph.run_graph(index, varint(tag << 3 | wire) + payload); count += 1
                calls = [x for x in row['entered'] if x.get('kind') == 'native-list-field' and x['ownerType'] == index]
                if row['result'] or row['stores'] or calls:
                    names = list(row['result']) or [x['clientField'] for x in calls]
                    if not names: names = [f['clientField'] for f in graph.schemas[index]['fields'] if any(s['typeIndex'] == index and s['objectOffset'] == f['objectOffset'] for s in row['stores'])]
                    if len(set(names)) != 1: raise ValueError('Ambiguous Avatar Item raw branch')
                    observed.setdefault(names[0], []).append((tag, wire))
                    selected.append(dict(typeIndex=index, tag=tag, wireType=wire, result=row['result'], entered=row['entered'], stores=row['stores']))
        for field in graph.schemas[index]['fields']:
            bindings = observed.pop(field['clientField'], [])
            if not bindings or len({tag for tag, _ in bindings}) != 1: raise ValueError('Missing Avatar Item field: ' + str((index, field['clientField'])))
            wires = sorted(w for _, w in bindings)
            expected = [0, 2] if field['cardinality'] == 'repeated' and field['type'] == 'uint32' else [2] if field['type'] in ('message', 'map') else [0]
            if wires != expected or field.get('tag', bindings[0][0]) != bindings[0][0]: raise ValueError('Avatar Item metadata/wire drift')
            field.update(tag=bindings[0][0], acceptedWireTypes=wires, semanticStatus='unresolved')
        if observed: raise ValueError('Unexpected Avatar Item raw fields')
    return dict(nativeFixtures=count, scope='2 new types plus14 reused Item dependencies, tags1..32/wire0,1,2,5', fields=selected)


def message(tag, payload): return varint(tag << 3 | 2) + varint(len(payload)) + payload


def fixtures():
    authored = inventory_fixtures(); left_wire = []; right_wire = []; left = []; right = []
    for tag in (5, 6, 7, 8, 9, 10):
        case = next(x for x in authored if x['name'] == 'item-nonempty-' + str(tag))
        for wires, results, item_id, guid in [(left_wire, left, 100 + tag, 9007199254740993 + tag),
                                             (right_wire, right, 200 + tag, 18446744073709551615 - tag)]:
            wire = bytes.fromhex(case['wire']) + b'\x08' + varint(item_id) + b'\x10' + varint(guid)
            expected = copy.deepcopy(case['expected']); expected.update(PJAGIOHBKLD=item_id, FBLKPHOCBIK=guid)
            wires.append(wire); results.append(expected)
    full_wire = bytes.fromhex('080b1a04080b1016') + b''.join(message(4, w) for w in left_wire) + b''.join(message(2, w) for w in right_wire)
    full = {'MNIBICBHICK': 11, 'BBMFJFKAECB': {'JEDCIENABEB': 11, 'JCPAOHDANBO': 22}, 'CLFDBPJOFLI': left, 'BAKDLDCFCIF': right}
    cases = [dict(name='16352-all-fields-two-distinct-lists-six-branches-each', typeIndex=16352, wire=full_wire.hex(), expected=full),
        dict(name='16352-first-list-six-branches', typeIndex=16352, wire=b''.join(message(4, w) for w in left_wire).hex(), expected={'CLFDBPJOFLI': left}),
        dict(name='16352-second-list-six-branches', typeIndex=16352, wire=b''.join(message(2, w) for w in right_wire).hex(), expected={'BAKDLDCFCIF': right}),
        dict(name='71717-nonzero-both-fields', typeIndex=71717, wire='080b1016', expected={'JEDCIENABEB': 11, 'JCPAOHDANBO': 22}),
        dict(name='71717-upper-both-fields', typeIndex=71717, wire='08ffffffff0f10ffffffff0f', expected={'JEDCIENABEB': 4294967295, 'JCPAOHDANBO': 4294967295}),
        dict(name='71717-duplicate-zero-and-wrong-wire', typeIndex=71717, wire='08011002080010000a001200', expected={}),
        dict(name='16352-explicit-empty-items-in-each-list', typeIndex=16352, wire='220012002200', expected={'CLFDBPJOFLI': [{}, {}], 'BAKDLDCFCIF': [{}]}),
        dict(name='16352-singular-leaf-merge-and-zero', typeIndex=16352, wire='1a0210011a0208021a0210001a00', expected={'BBMFJFKAECB': {'JEDCIENABEB': 2}}),
        dict(name='16352-scalar-wrong-wire-duplicate-zero', typeIndex=16352, wire='080108000a00', expected={}),
        dict(name='16352-interleaved-lists-append', typeIndex=16352, wire='220408011002120408031004220408051006',
            expected={'CLFDBPJOFLI': [{'PJAGIOHBKLD': 1, 'FBLKPHOCBIK': 2}, {'PJAGIOHBKLD': 5, 'FBLKPHOCBIK': 6}], 'BAKDLDCFCIF': [{'PJAGIOHBKLD': 3, 'FBLKPHOCBIK': 4}]}),
        dict(name='AvatarInfo-nonempty-Item-child-high64-negative-float', typeIndex=ROOT,
            wire=(message(9, full_wire) + bytes.fromhex('1081808080808080103a070807150000a0bf')).hex(),
            expected={'JCKNHCDJFCE': full, 'FBLKPHOCBIK': 9007199254740993, 'ADJINCFKMML': {'7': -1.25}})]
    baseline = next(x for x in tree_fixtures() if x['name'] == 'AvatarInfo-all-37-fields-with-both-new-nonempty-trees')
    # The full Avatar composition also reaches each alternate nested Item oneof.
    extended_wire = full_wire; extended = copy.deepcopy(full)
    variants = [('tps-branch-three', 5), ('tps-branch-four', 5), ('equip-branch-one', 6)]
    for list_tag, name, start in [(4, 'CLFDBPJOFLI', 300), (2, 'BAKDLDCFCIF', 400)]:
        for order, (source_name, item_tag) in enumerate(variants, 1):
            source = next(x for x in authored if x['name'] == source_name)
            payload = bytes.fromhex(source['wire']); detail = copy.deepcopy(source['expected'])
            if item_tag == 5: payload = message(2, payload); detail = {'PNGOHOKGKHP': detail}
            item_id, guid = start + order, 9007199254740993 + start + order
            wire = message(item_tag, payload) + b'\x08' + varint(item_id) + b'\x10' + varint(guid)
            extended_wire += message(list_tag, wire)
            extended[name].append({'FLPGMLOFAKP_' + str(item_tag): detail, 'PJAGIOHBKLD': item_id, 'FBLKPHOCBIK': guid})
    expected = copy.deepcopy(baseline['expected']); expected['JCKNHCDJFCE'] = extended
    cases.append(dict(name='AvatarInfo-all-37-fields-three-nonempty-child-trees', typeIndex=ROOT, wire=baseline['wire'] + message(9, extended_wire).hex(), expected=expected))
    for case in authored:
        if case['typeIndex'] != 68537:
            row = copy.deepcopy(case); row['name'] = 'reused-' + row['name']; cases.append(row)
    for index in sorted(NEW): cases.append(dict(name='empty-' + str(index), typeIndex=index, wire='', expected={}))
    return cases


def verify(graph):
    good = []
    for case in fixtures():
        row = graph.run_graph(case['typeIndex'], bytes.fromhex(case['wire']))
        if row['result'] != case['expected']: raise ValueError(case['name'] + ': Avatar Item native result differs ' + str(row['result']))
        good.append(dict(case, observation=row))
    rejected = []
    errors = [('nonempty-child-53824', ROOT, 'aa02020801', 'explicit-scope-refusal', 100),
        ('invalid-tag', 16352, '00', 'explicit-input-model', 100),
        ('Item-guid-overflow', 16352, '220b10ffffffffffffffffff02', 'explicit-input-model', 100),
        ('truncated-packed-u32', 64947, '320180', 'explicit-input-model', 100),
        ('truncated-new-leaf-varint', 16352, '1a0108', 'explicit-input-model', 100),
        ('negative-first-list-length', 16352, '22ffffffff0f', 'native-error-entry', 100),
        ('truncated-second-list-envelope', 16352, '12030801', 'native-error-entry', 100),
        ('first-list-recursion-limit', 16352, '2200', 'native-error-entry', 0),
        ('new-leaf-recursion-limit', 16352, '1a00', 'native-error-entry', 0),
        ('nested-Item-branch-recursion-limit', 16352, '22043a020801', 'native-error-entry', 1)]
    for name, index, wire, scope, limit in errors:
        try: graph.run_graph(index, bytes.fromhex(wire), limit)
        except ValueError as error:
            text = str(error)
            if scope == 'explicit-scope-refusal' and 'Nonempty child outside F-03d3d' not in text: raise
            if scope == 'native-error-entry' and 'Native error branch:' not in text: raise
            rejected.append(dict(name=name, typeIndex=index, wire=wire, scope=scope, error=text))
        else: raise ValueError('Invalid/out-of-scope Avatar Item input accepted: ' + name)
    return good, rejected


def main():
    p = argparse.ArgumentParser(description=__doc__); p.add_argument('--client', required=True, type=Path)
    p.add_argument('--output', required=True, type=Path); p.add_argument('--observations', required=True, type=Path)
    p.add_argument('--directory', type=Path, default=Path('protocol/cn-7.2')); p.add_argument('--python-libs', type=Path, default=Path('local/migration72/python')); a = p.parse_args()
    paths = [a.output.resolve(), a.observations.resolve()]
    if len(set(paths)) != 2 or any(x.is_relative_to(a.client.resolve()) for x in paths): p.error('Distinct outputs outside client required')
    source = (a.directory / 'inventory-graph-observations.json').read_bytes()
    if hashlib.sha256(source).hexdigest() != INVENTORY_SHA: raise ValueError('Reused inventory graph evidence drift')
    item_schemas = {x['typeIndex']: x for x in json.loads(source)['messages'] if x['typeIndex'] != 68537}
    if len(item_schemas) != 14: raise ValueError('Item-only source scope drift')
    b, m, s = [read_verified(a.client / name, digest) for name, digest in HASHES.items()]; records, _ = extract(b, m, startup=s, structures=True)
    cells = type_cells(b, m, s, records); audit = item_audit(b, records, cells, NEW | set(item_schemas), a.python_libs)
    bindings = item_list_aot(b, m, s, records); primitive_bindings = primitive_aot(b, m, s, records); inherited = core_aot(b, m, s, records)
    e = Offline72(b, a.python_libs, records, trace_instructions=False); graph = AvatarItems72(e, a.directory, cells, inherited, item_schemas)
    try: scan = scan_raw(graph); good, rejected = verify(graph)
    finally: graph.close(); e.close()
    context = dict(schemaVersion=1, task='F-03d3d', inputSHA256=HASHES, runtimeStatus='not-tested', targetProtocolReady=False,
        semanticIdentityStatus='unresolved', fullAvatarRawClosure=False, fullSelectedItemRawClosure=True, blockedTypeIndices=[53824],
        inventoryEvidenceSHA256=INVENTORY_SHA, dependencyAudit=audit, aotBindings=bindings, primitiveMapAOTBindings=primitive_bindings,
        inheritedCoreAOTBindings=inherited, initializers=graph.item_initializers, messages=[graph.schemas[i] for i in sorted(NEW | graph.item_types)], scan=scan,
        nativeFixtures=len(good), rejections=rejected,
        limitations=['Only16352/71717 plus14 Item types form the selected closed raw graph;53824 nonempty refused',
            '16 types45 metadata storage independently become50 wire fields, including expanded Object oneofs; StoreNotify root68537 excluded',
            'Managed allocation/class/generic contexts, primitives/delegates and fixed-capacity64 arrays/maps/enumeration/clear callbacks modeled',
            'Concrete reader/ReadMessage/list append/message clone/map clear-copy/list copy execute; native errors stop before throw',
            'No complete Avatar graph, named identity, business semantics, handler/live ABI/client acceptance or production integration'])
    summary = dict(context, fixtures=[dict(name=x['name'], typeIndex=x['typeIndex'], wire=x['wire'], result=x['observation']['result'], entered=x['observation']['entered'], nestedCalls=x['observation']['nestedCalls'], mapStores=x['observation']['mapStores']) for x in good])
    for path, data in zip(paths, [dict(context, fixtures=good), summary]):
        path.parent.mkdir(parents=True, exist_ok=True); path.write_text(json.dumps(data, indent=2) + '\n', 'utf8')
    print(json.dumps(dict(messages=16, storage=45, wireFields=sum(len(graph.schemas[i]['fields']) for i in NEW | graph.item_types), scanFixtures=scan['nativeFixtures'], nativeFixtures=len(good), rejections=len(rejected))))


if __name__ == '__main__': main()
