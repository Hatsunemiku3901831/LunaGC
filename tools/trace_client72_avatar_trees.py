"""F-03d3c: two small Avatar child trees, with nonempty AvatarInfo integration.

Six metadata types/eighteen storage slots are audited before native wire tests.
The other two complex child graphs remain explicitly refused.
"""
import argparse
import copy
import json
import struct
from pathlib import Path
try:
    from .extract_client72 import HASHES, PE, HEADER_VA, CODE_REGISTRATION, TypeResolver, number, read_verified, extract
    from .client72_emulator import Offline72, STREAM
    from .client72_graph import GraphReader72
    from .trace_client72_avatar_core import AvatarCore72, core_aot, ROOT, fixtures as core_fixtures
    from .trace_client72_player_store import type_cells
    from .trace_client72_token import varint, load_emulator
except ImportError:
    from extract_client72 import HASHES, PE, HEADER_VA, CODE_REGISTRATION, TypeResolver, number, read_verified, extract
    from client72_emulator import Offline72, STREAM
    from client72_graph import GraphReader72
    from trace_client72_avatar_core import AvatarCore72, core_aot, ROOT, fixtures as core_fixtures
    from trace_client72_player_store import type_cells
    from trace_client72_token import varint, load_emulator

TYPES = {27196, 76885, 71429, 18373, 66308, 77263}
BLOCKED = {16352, 53824}


def tree_audit(binary, records, cells, python_libs):
    load_emulator(python_libs)
    from capstone import Cs, CS_ARCH_X86, CS_MODE_64
    pe = PE(binary); md = Cs(CS_ARCH_X86, CS_MODE_64); md.detail = True
    ends = sorted({int(m['registeredAddress'], 16) for t in records for m in t['methods'] if m['registeredAddress'] != '0x0'})
    pending = [27196, 66308]; seen = set(); edges = []; rows = []; references = []
    while pending:
        index = pending.pop()
        if index in seen: continue
        seen.add(index); row = records[index]
        fields = [f for f in row['fields'] if f['offsetKind'] == 'instance']
        def add(typ, source):
            for arg in typ.get('arguments', [typ]):
                child = arg.get('typeDefinition')
                if arg['kind'] == 'class' and child is not None and any(m['name'] == 'MOMMACBEMEK' for m in records[child]['methods']):
                    edges.append(dict(sourceType=index, targetType=child, source=source)); pending.append(child)
        for f in fields:
            if f['type']['kind'] == 'object': raise ValueError('Unexpected Object expands tree scope')
            add(f['type'], 'field:' + f['name'])
        methods = [m for m in row['methods'] if m['name'] in ('MOMMACBEMEK', '.cctor')]
        for method in methods:
            entry = int(method['registeredAddress'], 16); end = next(x for x in ends if x > entry)
            raw = binary[pe.offset(entry):pe.offset(entry) + end - entry]
            if raw[:1] == b'\xe9': raise ValueError('Unexpected protected tree reader requires audit')
            for ins in md.disasm(raw, entry):
                for operand in ins.operands:
                    if operand.type == 3 and operand.mem.base == 41:
                        cell = ins.address + ins.size + operand.mem.disp
                        if cell not in cells: continue
                        references.append(dict(owner=index, method=method['name'], site=hex(ins.address), **cells[cell]))
                        add(cells[cell]['type'], 'native:' + hex(ins.address))
        rows.append(dict(typeIndex=index, clientType=row['name'], fields=fields, methods=methods))
    if seen != TYPES or sum(len(x['fields']) for x in rows) != 18: raise ValueError('Two-tree dependency/storage scope drift')
    return dict(typeCount=len(rows), instanceStorageFields=18, scope='Metadata fields plus bounded reader/cctor usage class references; wire count established separately',
                types=sorted(rows, key=lambda x: x['typeIndex']), edges=edges, nativeUsageReferences=references)


def tree_aot(binary, metadata, startup, records):
    pe = PE(binary); h = pe.offset(HEADER_VA); cr = pe.offset(CODE_REGISTRATION)
    specs = 0x210 + (number(binary, h + 0x170) ^ 0x6347382B)
    spec_count = (number(binary, h + 0x24) ^ 0x323BAE70) // 12
    table = 0x210 + (number(binary, h + 0xEC) ^ 0x3CF0720C)
    size = (number(binary, h + 0x110) + 0xA3D9CC8B) & 0xffffffff
    count = (number(binary, cr + 0x64) + 0x9EA91E3B) & 0xffffffff
    pointers = pe.offset(number(binary, cr + 0x30, 'Q'), count * 8)
    methods = {m['index']: (t, m) for t in records for m in t['methods']}
    resolver = TypeResolver(pe, startup, records, h); rows = []
    selected = {25060: {36273, 12616, 23297}, 35584: {11}}
    for i in range(size // 14):
        pointer, spec = struct.unpack_from('<ii', metadata, table + i * 14)
        if not 0 <= pointer < count or not 0 <= spec < spec_count: raise ValueError('Tree AOT reference bounds')
        method, cls, args = struct.unpack_from('<iii', metadata, specs + spec * 12); owner, row = methods[method]
        if cls not in selected.get(owner['index'], set()) or row['name'] not in ('.ctor', 'FNNADLCNGFO'): continue
        if args != -1: raise ValueError('Unexpected tree AOT method arguments')
        address = number(binary, pointers + pointer * 8, 'Q'); pe.offset(address, executable=True)
        rows.append(dict(owner=owner['index'], classInstance=cls, method=row['name'], methodDefinition=method,
            tableIndex=i, methodSpec=spec, pointerIndex=pointer, address=hex(address),
            arguments=[resolver.resolve_index(x) for x in resolver.arguments[cls]]))
    for owner, instance, address in [(25060, 36273, 0x158DC41F0), (25060, 12616, 0x158DC41F0),
                                     (25060, 23297, 0x158DC41F0), (35584, 11, 0x158DB8E10)]:
        hits = [x for x in rows if (x['owner'], x['classInstance'], x['method']) == (owner, instance, 'FNNADLCNGFO')]
        if len(hits) != 1 or int(hits[0]['address'], 16) != address: raise ValueError('Tree concrete list AOT drift')
    return rows


class AvatarTrees72(AvatarCore72):
    def __init__(self, engine, directory, cells, bindings):
        super().__init__(engine, directory, cells, bindings); e = self.e
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
                if typ['kind'] == 'class': field.update(type='message', nestedTypeIndex=typ['typeDefinition'])
                elif typ['kind'] == 'genericinst':
                    arg = typ['arguments'][0]
                    if arg['kind'] == 'class': field.update(type='message', cardinality='repeated', nestedTypeIndex=arg['typeDefinition'])
                    else: field.update(type=arg['kind'], cardinality='repeated', packed=True)
                fields.append(field)
            self.schemas[index] = dict(typeIndex=index, clientType=row['name'], fields=fields)
        for cell, row in cells.items():
            typ = row['type']
            if typ['kind'] == 'class' and typ.get('typeDefinition') in TYPES: e.write(cell, self.classes[typ['typeDefinition']])
        self.tree_initializers = []
        for index in sorted(TYPES):
            for method in e.records[index]['methods']:
                if method['name'] != '.cctor': continue
                before = len(self.factory_calls); entry = int(method['registeredAddress'], 16)
                e.allow(entry); e.prepare(); e.run(entry)
                self.tree_initializers.append(dict(typeIndex=index, entry=hex(entry), factoryArguments=self.factory_calls[before:]))
        self.heap_floor = e.heap_cursor

    def new_message(self, index, address=None):
        if index in TYPES: return GraphReader72.new_message(self, index, address)
        return super().new_message(index, address)

    def snapshot(self, pointer, depth=0):
        if pointer and self.objects[pointer] in TYPES: return GraphReader72.snapshot(self, pointer, depth)
        return super().snapshot(pointer, depth)

    def observe_entry(self, address):
        if self.active:
            if address in (self.entries[i] for i in BLOCKED):
                stream = self.e.reg('rdx')
                if self.e.read(stream + 0x44, 'I') != self.e.read(stream + 0x38, 'I'): raise ValueError('Nonempty child outside F-03d3c: ' + str(self.objects[self.e.reg('rcx')]))
            if address == 0x158DB8E10:
                container = self.e.reg('rcx')
                for pointer, index in self.objects.items():
                    if index not in TYPES: continue
                    for field in self.schemas[index]['fields']:
                        if field['cardinality'] == 'repeated' and field['type'] == 'uint32' and self.e.read(pointer + field['objectOffset']) == container:
                            self.entered.append(dict(kind='native-list-field', ownerType=index, clientField=field['clientField'], objectOffset=field['objectOffset'], key=self.e.read(STREAM + 0x20, 'I'), entry=hex(address)))
        return GraphReader72.observe_entry(self, address)


def scan_raw(graph):
    selected = []; count = 0
    for index in sorted(TYPES):
        observed = {}
        for tag in range(1, 33):
            for wire, payload in ((0, varint(0x12345678)), (1, bytes(8)), (2, b'\0'), (5, bytes(4))):
                row = graph.run_graph(index, varint(tag << 3 | wire) + payload); count += 1
                calls = [x for x in row['entered'] if x.get('kind') == 'native-list-field' and x['ownerType'] == index]
                if row['result'] or row['stores'] or calls:
                    names = list(row['result']) or [x['clientField'] for x in calls]
                    if not names: names = [f['clientField'] for f in graph.schemas[index]['fields'] if any(s['typeIndex'] == index and s['objectOffset'] == f['objectOffset'] for s in row['stores'])]
                    if len(set(names)) != 1: raise ValueError('Ambiguous tree raw branch')
                    observed.setdefault(names[0], []).append((tag, wire))
                    selected.append(dict(typeIndex=index, tag=tag, wireType=wire, result=row['result'], entered=row['entered'], stores=row['stores']))
        for field in graph.schemas[index]['fields']:
            bindings = observed.pop(field['clientField'], [])
            if not bindings or len({tag for tag, _ in bindings}) != 1: raise ValueError('Missing/ambiguous tree field: ' + field['clientField'])
            wires = sorted(w for _, w in bindings)
            expected = [0, 2] if field.get('packed') else [2] if field['type'] == 'message' else [0]
            if wires != expected: raise ValueError('Tree metadata/wire mismatch')
            field.update(tag=bindings[0][0], acceptedWireTypes=wires, semanticStatus='unresolved')
        if observed: raise ValueError('Unexpected tree raw fields')
    return dict(nativeFixtures=count, scope='6 types, tags1..32/wire0,1,2,5; matched against metadata storage fields and native usage closure', fields=selected)


def fixtures():
    first_wire = '080b120c1204080b10160a040821102c1a0c1204083710420a04084d1058'
    first = {'EPCMGLPMKLC': 11,
        'GONJACBHGEP': {'CNGEAFGELMF': [{'KIIGMHLMLMA': 11, 'DODCEPDNAIM': 22}], 'AIEEBDFPNPJ': [{'MHAIENEPEIP': 33, 'OACOOBKGAKO': 44}]},
        'CBIONIIBDIN': {'CNGEAFGELMF': [{'KIIGMHLMLMA': 55, 'DODCEPDNAIM': 66}], 'AIEEBDFPNPJ': [{'MHAIENEPEIP': 77, 'OACOOBKGAKO': 88}]}}
    second_wire = '084710481a070300ffffffff0f22070400ffffffff0f2a070500ffffffff0f320e080b10161a08008001ffffffff0f'
    second = {'KLNPMEAELPD': 71, 'PHCBOKNHIHH': 72, 'MLGIMHKCCHG': [3, 0, 4294967295],
        'DCOFEJBIBGN': [4, 0, 4294967295], 'AAAMKIEOGNJ': [5, 0, 4294967295],
        'JEONOFFKPNB': [{'BMMLCJLAODF': 11, 'GIFDBBABDOD': 22, 'NCKEHOOOKGN': [0, 128, 4294967295]}]}
    combined_wire = '8a021e' + first_wire + '622f' + second_wire
    cases = [dict(name='27196-two-same-shape-fields-distinct-nonzero', typeIndex=27196, wire=first_wire, expected=first),
        dict(name='66308-all-three-lists-and-nonempty-child', typeIndex=66308, wire=second_wire, expected=second),
        dict(name='76885-two-lists-two-independent-leaves', typeIndex=76885,
            wire='1204080110021204080310040a04080510060a0408071008',
            expected={'CNGEAFGELMF': [{'KIIGMHLMLMA': 1, 'DODCEPDNAIM': 2}, {'KIIGMHLMLMA': 3, 'DODCEPDNAIM': 4}],
                'AIEEBDFPNPJ': [{'MHAIENEPEIP': 5, 'OACOOBKGAKO': 6}, {'MHAIENEPEIP': 7, 'OACOOBKGAKO': 8}]}),
        dict(name='27196-repeated-submessage-merges-and-appends', typeIndex=27196,
            wire='120612040801100212060a040803100412061204080510061200',
            expected={'GONJACBHGEP': {'CNGEAFGELMF': [{'KIIGMHLMLMA': 1, 'DODCEPDNAIM': 2}, {'KIIGMHLMLMA': 5, 'DODCEPDNAIM': 6}], 'AIEEBDFPNPJ': [{'MHAIENEPEIP': 3, 'OACOOBKGAKO': 4}]}}),
        dict(name='18373-upper-and-duplicate-zero', typeIndex=18373, wire='08ffffffff0f10011000', expected={'MHAIENEPEIP': 4294967295}),
        dict(name='71429-two-upper-values', typeIndex=71429, wire='08ffffffff0f10ffffffff0f', expected={'KIIGMHLMLMA': 4294967295, 'DODCEPDNAIM': 4294967295}),
        dict(name='77263-packed-unpacked-upper-zero', typeIndex=77263, wire='18001a0601ffffffff0f080b1016', expected={'NCKEHOOOKGN': [0, 1, 4294967295], 'BMMLCJLAODF': 11, 'GIFDBBABDOD': 22}),
        dict(name='66308-three-packed-unpacked-lists', typeIndex=66308, wire='18001a030180012000220302800128002a03038001',
            expected={'MLGIMHKCCHG': [0, 1, 128], 'DCOFEJBIBGN': [0, 2, 128], 'AAAMKIEOGNJ': [0, 3, 128]}),
        dict(name='27196-scalar-wrong-wire-duplicate-zero', typeIndex=27196, wire='080108000a00', expected={}),
        dict(name='66308-scalars-wrong-wire-duplicate-zero', typeIndex=66308, wire='08011002080010000a001200', expected={}),
        dict(name='66308-message-list-fresh-empty-and-nonempty-items', typeIndex=66308, wire='32003204080b10163200', expected={'JEONOFFKPNB': [{}, {'BMMLCJLAODF': 11, 'GIFDBBABDOD': 22}, {}]}),
        dict(name='27196-two-explicit-empty-objects', typeIndex=27196, wire='12001a00', expected={'GONJACBHGEP': {}, 'CBIONIIBDIN': {}}),
        dict(name='AvatarInfo-two-new-nonempty-trees-high64-float', typeIndex=ROOT,
            wire=combined_wire + '1081808080808080103a070807150000a0bf',
            expected={'CKDDHADKCBK': first, 'PKPEFNIHPLA': second, 'FBLKPHOCBIK': 9007199254740993, 'ADJINCFKMML': {'7': -1.25}})]
    baseline = next(x for x in core_fixtures() if x['name'] == 'all-37-outer-fields-with-allowed-nonempty-children')
    expected = copy.deepcopy(baseline['expected']); expected.update(CKDDHADKCBK=first, PKPEFNIHPLA=second)
    cases.append(dict(name='AvatarInfo-all-37-fields-with-both-new-nonempty-trees', typeIndex=ROOT, wire=baseline['wire'] + combined_wire, expected=expected))
    for index in sorted(TYPES): cases.append(dict(name='empty-' + str(index), typeIndex=index, wire='', expected={}))
    return cases


def verify(graph):
    good = []
    for case in fixtures():
        row = graph.run_graph(case['typeIndex'], bytes.fromhex(case['wire']))
        if row['result'] != case['expected']: raise ValueError(case['name'] + ': tree native result differs ' + str(row['result']))
        good.append(dict(case, observation=row))
    rejected = []
    errors = [('nonempty-child-16352', ROOT, '4a020801', 'explicit-scope-refusal', 100),
        ('nonempty-child-53824', ROOT, 'aa02020801', 'explicit-scope-refusal', 100),
        ('invalid-tag', 27196, '00', 'explicit-input-model', 100),
        ('varint-overflow', 71429, '08ffffffffffffffffff02', 'explicit-input-model', 100),
        ('truncated-packed-u32', 66308, '1a0180', 'explicit-input-model', 100),
        ('truncated-leaf-varint', 66308, '320108', 'explicit-input-model', 100),
        ('negative-nested-length', 27196, '12ffffffff0f', 'native-error-entry', 100),
        ('truncated-nested-envelope', 27196, '12030801', 'native-error-entry', 100),
        ('nested-recursion-limit', 27196, '1200', 'native-error-entry', 0),
        ('second-level-recursion-limit', 27196, '1206120408011002', 'native-error-entry', 1)]
    for name, index, wire, scope, limit in errors:
        try: graph.run_graph(index, bytes.fromhex(wire), limit)
        except ValueError as error:
            message = str(error)
            if scope == 'explicit-scope-refusal' and 'Nonempty child outside F-03d3c' not in message: raise
            if scope == 'native-error-entry' and 'Native error branch:' not in message: raise
            rejected.append(dict(name=name, typeIndex=index, wire=wire, scope=scope, error=message))
        else: raise ValueError('Invalid/out-of-scope tree input accepted: ' + name)
    return good, rejected


def main():
    p = argparse.ArgumentParser(description=__doc__); p.add_argument('--client', required=True, type=Path)
    p.add_argument('--output', required=True, type=Path); p.add_argument('--observations', required=True, type=Path)
    p.add_argument('--directory', type=Path, default=Path('protocol/cn-7.2')); p.add_argument('--python-libs', type=Path, default=Path('local/migration72/python')); a = p.parse_args()
    paths = [a.output.resolve(), a.observations.resolve()]
    if len(set(paths)) != 2 or any(x.is_relative_to(a.client.resolve()) for x in paths): p.error('Distinct outputs outside client required')
    b, m, s = [read_verified(a.client / name, digest) for name, digest in HASHES.items()]; records, _ = extract(b, m, startup=s, structures=True)
    cells = type_cells(b, m, s, records); audit = tree_audit(b, records, cells, a.python_libs)
    bindings = tree_aot(b, m, s, records); inherited_bindings = core_aot(b, m, s, records)
    e = Offline72(b, a.python_libs, records, trace_instructions=False); graph = AvatarTrees72(e, a.directory, cells, inherited_bindings)
    try: scan = scan_raw(graph); good, rejected = verify(graph)
    finally: graph.close(); e.close()
    context = dict(schemaVersion=1, task='F-03d3c', inputSHA256=HASHES, runtimeStatus='not-tested', targetProtocolReady=False,
        semanticIdentityStatus='unresolved', fullAvatarRawClosure=False, fullSelectedTreeRawClosure=True, blockedTypeIndices=sorted(BLOCKED),
        dependencyAudit=audit, aotBindings=bindings, inheritedCoreAOTBindings=inherited_bindings,
        initializers=graph.tree_initializers, messages=[graph.schemas[i] for i in sorted(TYPES)], scan=scan,
        nativeFixtures=len(good), rejections=rejected,
        limitations=['Only27196/76885/71429/18373 and66308/77263 selected trees are fully traced;16352/53824 nonempty refused',
            'Metadata18 storage lower bound independently becomes18 wire fields only after full bounded scan and nonempty graphs',
            'Managed allocation/class/generic context, primitive input/delegates and fixed-capacity64 backing arrays/maps are explicit fixtures',
            'Concrete readers/ReadMessage limits/list append and inherited map adapters/setters execute; native errors stop before throw',
            'No full Avatar graph, named identity, business semantics, handler/live ABI/client acceptance or production integration'])
    summary = dict(context, fixtures=[dict(name=x['name'], typeIndex=x['typeIndex'], wire=x['wire'], result=x['observation']['result'], entered=x['observation']['entered'], nestedCalls=x['observation']['nestedCalls'], stores=x['observation']['stores']) for x in good])
    for path, data in zip(paths, [dict(context, fixtures=good), summary]):
        path.parent.mkdir(parents=True, exist_ok=True); path.write_text(json.dumps(data, indent=2) + '\n', 'utf8')
    print(json.dumps(dict(messages=6, storage=18, wireFields=sum(len(graph.schemas[i]['fields']) for i in TYPES), scanFixtures=scan['nativeFixtures'], nativeFixtures=len(good), rejections=len(rejected))))


if __name__ == '__main__': main()
