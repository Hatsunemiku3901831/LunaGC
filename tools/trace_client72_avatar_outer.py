"""F-03d3a: Avatar candidate outer lists/scalars, Team/Rename raw lower bound.

AvatarInfo content is explicitly refused. Full dependency audit is static, not
a complete recursive wire schema or a business identity proof.
"""
import argparse
import json
import struct
from pathlib import Path
try:
    from .extract_client72 import HASHES, PE, HEADER_VA, CODE_REGISTRATION, TypeResolver, number, read_verified, extract
    from .extract_client72_commands import classify_commands
    from .client72_emulator import Offline72, GLOBAL, STREAM
    from .client72_graph import GraphReader72
    from .client72_map_runtime import MapRuntime72
    from .trace_client72_player_store import type_cells
    from .trace_client72_token import load_emulator, varint
except ImportError:
    from extract_client72 import HASHES, PE, HEADER_VA, CODE_REGISTRATION, TypeResolver, number, read_verified, extract
    from extract_client72_commands import classify_commands
    from client72_emulator import Offline72, GLOBAL, STREAM
    from client72_graph import GraphReader72
    from client72_map_runtime import MapRuntime72
    from trace_client72_player_store import type_cells
    from trace_client72_token import load_emulator, varint

ROOT, TEAM, RENAME, AVATAR = 30509, 26081, 14786, 48178
ENTRIES = {ROOT: 0x152DE03C0, TEAM: 0x1502CE400, RENAME: 0x149778220, AVATAR: 0x14F676D80}
WIRE_TYPES = {ROOT, TEAM, RENAME}


def avatar_aot(binary, metadata, startup, records, audit):
    pe = PE(binary); h = pe.offset(HEADER_VA); cr = pe.offset(CODE_REGISTRATION)
    specs = 0x210 + (number(binary, h + 0x170) ^ 0x6347382B)
    spec_count = (number(binary, h + 0x24) ^ 0x323BAE70) // 12
    table = 0x210 + (number(binary, h + 0xEC) ^ 0x3CF0720C)
    size = (number(binary, h + 0x110) + 0xA3D9CC8B) & 0xffffffff
    count = (number(binary, cr + 0x64) + 0x9EA91E3B) & 0xffffffff
    pointers = pe.offset(number(binary, cr + 0x30, 'Q'), count * 8)
    methods = {m['index']: (t, m) for t in records for m in t['methods']}
    resolver = TypeResolver(pe, startup, records, h); rows = []
    selected = {66210: {19346, 1541}, 66214: {1541}, 35584: {11, 58}, 25060: {6060, 8554}}
    requirements = set()
    for message in audit['types']:
        for field in message['fields']:
            typ = field['type']
            if typ['kind'] == 'genericinst' and typ['typeDefinition'] in (17961, 66210, 35584, 25060):
                identity = (typ['typeDefinition'], typ['instanceIndex']); requirements.add(identity)
                selected.setdefault(identity[0], set()).add(identity[1])
    for i in range(size // 14):
        pointer, spec = struct.unpack_from('<ii', metadata, table + i * 14)
        if not 0 <= pointer < count or not 0 <= spec < spec_count: raise ValueError('Avatar AOT bounds invalid')
        method, cls, args = struct.unpack_from('<iii', metadata, specs + spec * 12); owner, row = methods[method]
        if cls not in selected.get(owner['index'], set()) or row['name'] not in ('.ctor', 'MOMMACBEMEK', 'FNNADLCNGFO', 'set_Item'): continue
        if args != -1: raise ValueError('Unexpected Avatar AOT method arguments')
        address = number(binary, pointers + pointer * 8, 'Q'); pe.offset(address, executable=True)
        rows.append(dict(owner=owner['index'], classInstance=cls, method=row['name'], methodDefinition=method,
            tableIndex=i, methodSpec=spec, pointerIndex=pointer, address=hex(address),
            arguments=[resolver.resolve_index(x) for x in resolver.arguments[cls]]))
    expected = {(66210, 19346): 0x156D35A40, (35584, 11): 0x158DB8E10,
                (35584, 58): 0x1571DDD60, (25060, 6060): 0x158DC41F0, (25060, 8554): 0x158DC41F0}
    for identity, address in expected.items():
        hits = [r for r in rows if (r['owner'], r['classInstance']) == identity and r['method'] == 'FNNADLCNGFO']
        if len(hits) != 1 or int(hits[0]['address'], 16) != address: raise ValueError('Avatar concrete collection AOT drift')
    found = {(r['owner'], r['classInstance']) for r in rows if r['method'] == 'FNNADLCNGFO'}
    audit['containerAOTRequirements'] = [dict(owner=owner, classInstance=instance,
        status='static-binding-found-not-all-executed' if (owner, instance) in found else 'unresolved')
        for owner, instance in sorted(requirements)]
    return rows


def dependency_audit(binary, records, cells, python_libs):
    load_emulator(python_libs)
    from capstone import Cs, CS_ARCH_X86, CS_MODE_64
    pe = PE(binary); md = Cs(CS_ARCH_X86, CS_MODE_64); md.detail = True
    ends = sorted({int(m['registeredAddress'], 16) for t in records for m in t['methods'] if m['registeredAddress'] != '0x0'})
    seen = {}; pending = [ROOT]; edges = []; object_refs = []; protected = []
    while pending:
        index = pending.pop()
        if index in seen: continue
        row = records[index]; seen[index] = row
        object_owner = any(f['offsetKind'] == 'instance' and f['type']['kind'] == 'object' for f in row['fields'])
        def add(typ, origin, site=None):
            for sub in typ.get('arguments', [typ]):
                child = sub.get('typeDefinition')
                if sub['kind'] == 'class' and child != index and child is not None and any(m['name'] == 'MOMMACBEMEK' for m in records[child]['methods']):
                    edges.append(dict(source=index, target=child, origin=origin, site=site)); pending.append(child)
        for field in row['fields']:
            if field['offsetKind'] == 'instance': add(field['type'], 'field:' + field['name'])
        for method in row['methods']:
            if method['name'] not in ('MOMMACBEMEK', '.cctor'): continue
            start = int(method['registeredAddress'], 16); end = next(x for x in ends if x > start)
            raw = binary[pe.offset(start):pe.offset(start) + end - start]
            if method['name'] == 'MOMMACBEMEK' and raw[:1] == b'\xe9':
                protected.append(dict(typeIndex=index, entry=hex(start), jump=hex(start + 5 + struct.unpack_from('<i', raw, 1)[0])))
            for ins in md.disasm(raw, start):
                for op in ins.operands:
                    if op.type == 3 and op.mem.base == 41:
                        cell = ins.address + ins.size + op.mem.disp
                        if cell not in cells: continue
                        typ = cells[cell]['type']; add(typ, 'native-usage:' + hex(cell), hex(ins.address))
                        if object_owner: object_refs.append(dict(owner=index, site=hex(ins.address), **cells[cell]))
    if len(seen) != 65: raise ValueError('Avatar static dependency-set drift')
    return dict(scope='Static metadata fields plus all bounded usage class/generic references in readers/cctors; not complete recursive wire',
        typeCount=len(seen), instanceStorageFields=sum(sum(f['offsetKind'] == 'instance' for f in t['fields']) for t in seen.values()),
        types=[dict(typeIndex=i, clientType=t['name'], fields=[f for f in t['fields'] if f['offsetKind'] == 'instance'],
            readers=[m for m in t['methods'] if m['name'] == 'MOMMACBEMEK']) for i, t in sorted(seen.items())], edges=edges,
        objectOwners=sorted(i for i, t in seen.items() if any(f['offsetKind'] == 'instance' and f['type']['kind'] == 'object' for f in t['fields'])),
        objectUsageReferences=object_refs, objectEdgesStatus='static-candidates-not-wire-discriminated', protectedReaders=protected)


class TeamMap72(MapRuntime72):
    CONFIG = {5: dict(owner=66210, concrete=19346, shared=1541, adapter=66214, child=TEAM,
        key='uint32', static=0x22800, lookup=0x1578B66F0, insert=0x1578B6A20,
        dictionaryOffset=16, listOffset=24, entriesOffset=24, nodeOffset=40)}

    def __init__(self, graph, directory, bindings):
        self.avatar_bindings = bindings; super().__init__(graph, directory)

    def binding(self, owner, instance, method):
        hits = {int(r['address'], 16) for r in self.avatar_bindings if (r['owner'], r['classInstance'], r['method']) == (owner, instance, method)}
        if len(hits) != 1: raise ValueError('Avatar team map AOT missing/ambiguous')
        return hits.pop()


class AvatarOuter72(GraphReader72):
    def __init__(self, engine, directory, cells, bindings):
        self.codec_classes = set(); super().__init__(engine, directory); e = self.e
        for index, entry in ENTRIES.items():
            klass = e.alloc(1024); table = e.alloc(16); e.write(klass + 0xC7, 1, 'B')
            e.write(klass + 0x80, table); e.write(klass + 0xC0, 1, 'H'); e.write(klass + 0xBE, 32, 'H')
            e.write(table, self.interface); e.write(klass + 0xE8, entry)
            self.classes[index] = klass; self.class_indices[klass] = index; self.entries[index] = entry; e.allow(entry)
        for cell, row in cells.items():
            if row['type']['kind'] == 'class' and row['type'].get('typeDefinition') in ENTRIES:
                e.write(cell, self.classes[row['type']['typeDefinition']])
        for index in WIRE_TYPES:
            row = e.records[index]; fields = []
            for f in row['fields']:
                if f['offsetKind'] != 'instance': continue
                t = f['type']; field = dict(clientField=f['name'], objectOffset=f['offset'], type=t['kind'], cardinality='singular')
                if t['kind'] == 'genericinst':
                    args = t['arguments']; child = args[-1]
                    if len(args) == 2: field.update(type='map', cardinality='map', keyType=args[0]['kind'], valueType='message', nestedTypeIndex=child['typeDefinition'])
                    elif child['kind'] == 'class': field.update(type='message', cardinality='repeated', nestedTypeIndex=child['typeDefinition'])
                    else: field.update(type=child['kind'], cardinality='repeated', packed=True)
                fields.append(field)
            self.schemas[index] = dict(typeIndex=index, clientType=row['name'], fields=fields)
        self.maps = TeamMap72(self, directory, bindings)
        self.codec_classes = {e.synthetic_type(0x14592A0B8), e.synthetic_type(0x1458C5AA8)}
        self.factory_calls = []
        e.models[0x14E5A5E30] = lambda: self.codec_factory(32)
        e.models[0x14E5DFF80] = lambda: self.codec_factory(64)
        e.models[0x14052D0F0] = lambda: 0
        self.initializers = []
        for index, entry in [(ROOT, 0x152DE07F0), (TEAM, 0x1502CE560)]:
            before = len(self.factory_calls); e.allow(entry); e.prepare(); e.run(entry)
            self.initializers.append(dict(typeIndex=index, entry=hex(entry), factoryArguments=self.factory_calls[before:]))
        self.maps.config[5]['codec'] = e.read(GLOBAL + 0x22800)
        if e.read(self.maps.config[5]['codec'] + 0x20, 'I') != 42: raise ValueError('Avatar map initializer changed')
        self.heap_floor = e.heap_cursor

    def allocate(self):
        if self.e.reg('rcx') in self.codec_classes: return self.e.alloc()
        return super().allocate()

    def codec_factory(self, width):
        e = self.e; tag = e.reg('ecx'); codec, delegate = e.alloc(), e.alloc()
        e.write(codec + 0x34, tag, 'I'); e.write(codec + 0x18, delegate)
        e.write(delegate + 0x18, 0x147520110 if width == 32 else 0x1475202D0); e.write(delegate + 0x40, STREAM)
        self.factory_calls.append(dict(width=width, tag=tag)); return codec

    def new_message(self, index, address=None):
        if index == AVATAR:
            p = self.e.alloc(512) if address is None else address
            self.e.write(p, self.classes[index]); self.e.write(p + 0x14, 1, 'I'); self.objects[p] = index; return p
        return super().new_message(index, address)

    def observe_entry(self, address):
        if self.active and address == ENTRIES[AVATAR]:
            stream = self.e.reg('rdx')
            if self.e.read(stream + 0x44, 'I') != self.e.read(stream + 0x38, 'I'): raise ValueError('Nonempty AvatarInfo outside F-03d3a')
        if self.active and address in (0x158DB8E10, 0x1571DDD60):
            container = self.e.reg('rcx')
            for pointer, index in self.objects.items():
                if index not in WIRE_TYPES: continue
                for field in self.e.records[index]['fields']:
                    typ = field['type']
                    if field['offsetKind'] == 'instance' and typ['kind'] == 'genericinst' and len(typ['arguments']) == 1 and self.e.read(pointer + field['offset']) == container:
                        self.entered.append(dict(kind='native-list-field', ownerType=index, clientField=field['name'],
                            objectOffset=field['offset'], key=self.e.read(STREAM + 0x20, 'I'), entry=hex(address)))
        return super().observe_entry(address)

    def snapshot(self, pointer, depth=0):
        if pointer and self.objects[pointer] == AVATAR: return dict(untracedType=AVATAR, content='empty-envelope-only')
        if pointer and self.objects[pointer] in (TEAM, RENAME):
            e = self.e; result = {}; index = self.objects[pointer]
            string_offset = 32 if index == TEAM else 24
            text = self.values[e.read(pointer + string_offset)].get('text', '')
            if text: result['MGIDEJKCAKD' if index == TEAM else 'PBDIJJHDMOF'] = text
            if index == TEAM:
                container = e.read(pointer + 24); count = e.read(container + 24, 'I'); array = e.read(container + 16)
                if count > 64: raise ValueError('Team uint64 list fixture capacity exceeded')
                if count: result['PIDCNPCLBCE'] = [e.read(array + 32 + i * 8) for i in range(count)]
            elif e.read(pointer + 32, 'I'): result['NLNJDCDLHHE'] = e.read(pointer + 32, 'I')
            return result
        return super().snapshot(pointer, depth)

    def blob(self, kind):
        if kind != 'string': return super().blob(kind)
        self.sync(); raw = self.input.blob(); self.position(); text = raw.decode('utf8'); encoded = text.encode('utf-16-le')
        pointer = self.e.alloc(24 + len(encoded)); self.e.write(pointer + 16, len(encoded) // 2, 'I')
        if encoded: self.e.uc.mem_write(pointer + 20, encoded)
        self.values[pointer] = dict(kind='string', hex=raw.hex(), text=text)
        return pointer


def scan_raw(graph):
    samples = []; count = 0
    for index in sorted(WIRE_TYPES):
        observed = {}
        for tag in range(1, 33):
            for wire, payload in ((0, varint(0x12345678)), (1, bytes(8)), (2, b'\0'), (5, bytes(4))):
                row = graph.run_graph(index, varint(tag << 3 | wire) + payload); count += 1
                list_calls = [e for e in row['entered'] if e.get('kind') == 'native-list-field' and e['ownerType'] == index]
                if row['result'] or row['stores'] or list_calls:
                    names = list(row['result'])
                    if not names: names = [f['clientField'] for f in graph.schemas[index]['fields'] if any(e['typeIndex'] == index and e['objectOffset'] == f['objectOffset'] for e in row['stores'])]
                    if not names: names = [e['clientField'] for e in list_calls]
                    if len(set(names)) != 1: raise ValueError('Ambiguous Avatar outer branch')
                    name = names[0]; observed.setdefault(name, []).append((tag, wire))
                    samples.append(dict(typeIndex=index, tag=tag, wireType=wire, result=row['result'], entered=row['entered'], stores=row['stores']))
        for field in graph.schemas[index]['fields']:
            bindings = observed.pop(field['clientField'], [])
            if not bindings or len({t for t, _ in bindings}) != 1: raise ValueError('Avatar raw field missing/ambiguous: ' + field['clientField'])
            wires = sorted(w for _, w in bindings)
            expected = [0, 2] if field.get('packed') else [2] if field['cardinality'] != 'singular' or field['type'] == 'string' else [0]
            if wires != expected: raise ValueError('Avatar metadata/wire mismatch')
            field.update(tag=bindings[0][0], acceptedWireTypes=wires, semanticStatus='unresolved')
        if observed: raise ValueError('Unexpected Avatar outer raw branch')
    return dict(nativeFixtures=count, scope='3 selected types; tags1..32/wire0,1,2,5; native list call argument identifies empty packed branches', fields=samples)


def fixtures():
    cases = [dict(name='root-empty', typeIndex=ROOT, wire='', expected={}),
        dict(name='outer-independent-scalars', typeIndex=ROOT, wire='188180808080808010600b', expected={'LFMIIOCDKNK': 9007199254740993, 'OCIKDBJPOAJ': 11}),
        dict(name='outer-upper-scalars', typeIndex=ROOT, wire='18ffffffffffffffffff0160ffffffff0f', expected={'LFMIIOCDKNK': 18446744073709551615, 'OCIKDBJPOAJ': 4294967295}),
        dict(name='outer-u32-lists', typeIndex=ROOT, wire='22030180016a0302800172030380017a03048001', expected={'BJKPIFCAKKA': [1, 128], 'GDFOCENMMIA': [2, 128], 'LCDMGOFLDKF': [3, 128], 'HENJGDHPLIN': [4, 128]}),
        dict(name='outer-u64-list-high', typeIndex=ROOT, wire='4a088180808080808010', expected={'IAACAIILIFD': [9007199254740993]}),
        dict(name='outer-u64-list-packed-unpacked-upper-zero', typeIndex=ROOT, wire='48004a128180808080808010ffffffffffffffffff01',
             expected={'IAACAIILIFD': [0, 9007199254740993, 18446744073709551615]}),
        dict(name='outer-u32-list-packed-unpacked-upper-zero', typeIndex=ROOT, wire='2000220601ffffffff0f', expected={'BJKPIFCAKKA': [0, 1, 4294967295]}),
        dict(name='outer-duplicate-zero-wrong-wire', typeIndex=ROOT, wire='180118001a0060016000', expected={}),
        dict(name='team-nonempty', typeIndex=TEAM, wire='12015462088180808080808010', expected={'MGIDEJKCAKD': 'T', 'PIDCNPCLBCE': [9007199254740993]}),
        dict(name='team-unicode-nul', typeIndex=TEAM, wire='1208e4b8adf09f8c9900', expected={'MGIDEJKCAKD': '中🌙\0'}),
        dict(name='team-u64-packed-unpacked-upper-zero', typeIndex=TEAM, wire='6000620affffffffffffffffff01', expected={'PIDCNPCLBCE': [0, 18446744073709551615]}),
        dict(name='root-nonempty-team-map', typeIndex=ROOT, wire='2a110807120d12015462088180808080808010', expected={'CNCCIILAJNJ': {'7': {'MGIDEJKCAKD': 'T', 'PIDCNPCLBCE': [9007199254740993]}}}),
        dict(name='root-team-map-duplicate-value-replaces', typeIndex=ROOT, wire='2a110807120d120154620881808080808080102a0708071203120142', expected={'CNCCIILAJNJ': {'7': {'MGIDEJKCAKD': 'B'}}}),
        dict(name='rename-nonempty', typeIndex=RENAME, wire='280b4a014e', expected={'NLNJDCDLHHE': 11, 'PBDIJJHDMOF': 'N'}),
        dict(name='rename-unicode-nul', typeIndex=RENAME, wire='4a08e4b8adf09f8c9900', expected={'PBDIJJHDMOF': '中🌙\0'}),
        dict(name='rename-explicit-zero-empty', typeIndex=RENAME, wire='280b4a014e28004a00', expected={}),
        dict(name='root-nonempty-rename-list', typeIndex=ROOT, wire='5a05280b4a014e', expected={'AGCIPPKKCAC': [{'NLNJDCDLHHE': 11, 'PBDIJJHDMOF': 'N'}]}),
        dict(name='root-empty-avatar-info-envelope', typeIndex=ROOT, wire='3200', expected={'JGECGMPNGBD': [dict(untracedType=AVATAR, content='empty-envelope-only')]})]
    cases.append(dict(name='all-allowed-outer-content', typeIndex=ROOT,
        wire='188180808080808010600b22030180016a0302800172030380017a030480014a0881808080808080102a110807120d120154620881808080808080105a05280b4a014e3200',
        expected={'LFMIIOCDKNK': 9007199254740993, 'OCIKDBJPOAJ': 11,
            'BJKPIFCAKKA': [1, 128], 'GDFOCENMMIA': [2, 128], 'LCDMGOFLDKF': [3, 128], 'HENJGDHPLIN': [4, 128],
            'IAACAIILIFD': [9007199254740993], 'CNCCIILAJNJ': {'7': {'MGIDEJKCAKD': 'T', 'PIDCNPCLBCE': [9007199254740993]}},
            'AGCIPPKKCAC': [{'NLNJDCDLHHE': 11, 'PBDIJJHDMOF': 'N'}],
            'JGECGMPNGBD': [dict(untracedType=AVATAR, content='empty-envelope-only')]}))
    return cases


def verify(graph):
    good = []
    for case in fixtures():
        row = graph.run_graph(case['typeIndex'], bytes.fromhex(case['wire']))
        if row['result'] != case['expected']: raise ValueError(case['name'] + ': Avatar native result differs ' + str(row['result']))
        good.append(dict(case, observation=row))
    rejected = []
    for name, index, wire in [('nonempty-avatar-info', ROOT, '32020801'), ('invalid-tag', ROOT, '00'), ('overflow-guid', ROOT, '18ffffffffffffffffff02'),
        ('truncated-u64-packed', ROOT, '4a0180'), ('invalid-utf8-model', TEAM, '1201ff')]:
        try: graph.run_graph(index, bytes.fromhex(wire))
        except ValueError as error: rejected.append(dict(name=name, wire=wire, error=str(error), scope='explicit-scope-refusal' if name == 'nonempty-avatar-info' else 'explicit-input-model'))
        else: raise ValueError('Invalid/out-of-scope Avatar input accepted')
    return good, rejected


def main():
    p = argparse.ArgumentParser(description=__doc__); p.add_argument('--client', required=True, type=Path)
    p.add_argument('--output', required=True, type=Path); p.add_argument('--observations', required=True, type=Path)
    p.add_argument('--directory', type=Path, default=Path('protocol/cn-7.2')); p.add_argument('--python-libs', type=Path, default=Path('local/migration72/python')); a = p.parse_args()
    paths = [a.output.resolve(), a.observations.resolve()]
    if len(set(paths)) != 2 or any(x.is_relative_to(a.client.resolve()) for x in paths): p.error('Distinct outputs outside client required')
    b, m, s = [read_verified(a.client / name, digest) for name, digest in HASHES.items()]; records, _ = extract(b, m, startup=s, structures=True)
    cells = type_cells(b, m, s, records); audit = dependency_audit(b, records, cells, a.python_libs); bindings = avatar_aot(b, m, s, records, audit)
    commands = [x for x in classify_commands(records)['commands'] if x['cmdId'] == 20143]
    if len(commands) != 1 or commands[0]['typeIndex'] != ROOT: raise ValueError('Avatar Cmd/type binding drift')
    e = Offline72(b, a.python_libs, records, trace_instructions=False); graph = AvatarOuter72(e, a.directory, cells, bindings)
    try: scan = scan_raw(graph); good, rejected = verify(graph)
    finally: graph.close(); e.close()
    context = dict(schemaVersion=1, task='F-03d3a', inputSHA256=HASHES, runtimeStatus='not-tested', targetProtocolReady=False,
        candidateSemanticName='AvatarDataNotify', semanticIdentityStatus='unresolved', fullAvatarRawClosure=False, commands=commands,
        dependencyAudit=audit, aotBindings=bindings, initializers=graph.initializers,
        messages=[graph.schemas[i] for i in sorted(WIRE_TYPES)], scan=scan, nativeFixtures=len(good), rejections=rejected,
        limitations=['Only outer10 fields plus Team/Rename raw content; AvatarInfo nonempty input refused',
            'Root protected trampoline executes; UTF8-to-managed-UTF16 strings, primitive input/delegates/metadata/managed allocation and bounded map dictionary contexts are explicit fixtures',
            'Static65-type dependency audit is not65 recovered wire schemas; new Object owners51050/85344 remain unresolved',
            'No IFix/live ABI/handler/UI/client acceptance or production integration'])
    summary = dict(context, fixtures=[dict(name=x['name'], typeIndex=x['typeIndex'], wire=x['wire'], result=x['observation']['result'], entered=x['observation']['entered'], mapStores=x['observation']['mapStores']) for x in good])
    for path, data in zip(paths, [dict(context, fixtures=good), summary]):
        path.parent.mkdir(parents=True, exist_ok=True); path.write_text(json.dumps(data, indent=2) + '\n', 'utf8')
    print(json.dumps(dict(dependencyTypes=audit['typeCount'], storageFields=audit['instanceStorageFields'], messages=3, fields=14, nativeFixtures=len(good))))


if __name__ == '__main__': main()
