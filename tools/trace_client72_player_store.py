"""F-03d2a1: fixed CN72 store/Item outer wire and two scalar oneof leaves.

The 15-type dependency audit is complete for field and reader class-cell edges.
Eleven remaining child schemas are NOT recovered by this slice. Nonempty input
to those readers is explicitly refused; no empty envelope becomes a full graph.
"""
import argparse
import json
from pathlib import Path
try:
    from .extract_client72 import HASHES, PE, HEADER_VA, TypeResolver, number, read_verified, extract
    from .extract_client72_literals import Literals72, USAGE_REGISTRATION
    from .extract_client72_commands import classify_commands
    from .client72_emulator import Offline72, GLOBAL
    from .client72_graph import GraphReader72
    from .trace_client72_token import varint, load_emulator
except ImportError:
    from extract_client72 import HASHES, PE, HEADER_VA, TypeResolver, number, read_verified, extract
    from extract_client72_literals import Literals72, USAGE_REGISTRATION
    from extract_client72_commands import classify_commands
    from client72_emulator import Offline72, GLOBAL
    from client72_graph import GraphReader72
    from trace_client72_token import varint, load_emulator

ROOT, ITEM = 68537, 65333
EXPECTED_CLOSURE = {68537, 65333, 40274, 63442, 28471, 28474, 28475, 28473,
                    51247, 14095, 44169, 64947, 74063, 63899, 61085}
BRANCHES = {5: 63442, 6: 44169, 7: 51247, 8: 63899, 9: 40274, 10: 14095}
SCHEMAS = {
    ROOT: dict(typeIndex=ROOT, clientType='CAIHCNHAMHB', fields=[
        dict(clientField='MBEEKCPJBEL', objectOffset=24, tag=2, type='message', cardinality='repeated', nestedTypeIndex=ITEM),
        dict(clientField='PAKLDABINMP', objectOffset=32, tag=8, type='uint32', cardinality='singular'),
        dict(clientField='CLFJNKKFONI', objectOffset=36, tag=14, type='uint32', cardinality='singular')]),
    ITEM: dict(typeIndex=ITEM, clientType='NDGHFPMJFIM', fields=[
        dict(clientField='PJAGIOHBKLD', objectOffset=40, tag=1, type='uint32', cardinality='singular'),
        dict(clientField='FBLKPHOCBIK', objectOffset=32, tag=2, type='uint64', cardinality='singular'),
        *[dict(clientField='FLPGMLOFAKP_' + str(tag), objectOffset=24, tag=tag,
               type='message', cardinality='singular', nestedTypeIndex=child,
               oneof='raw_detail', discriminatorOffset=44, discriminatorValue=tag)
          for tag, child in BRANCHES.items()]]),
    51247: dict(typeIndex=51247, clientType='JOPADICJMPJ', fields=[
        dict(clientField='GNHFNECPAHD', objectOffset=24, tag=1, type='uint32', cardinality='singular')]),
    40274: dict(typeIndex=40274, clientType='MIEKLLOFKOJ', fields=[
        dict(clientField='GNHFNECPAHD', objectOffset=24, tag=1, type='uint32', cardinality='singular')])}


def type_cells(binary, metadata, startup, records):
    """Decode source and destination independently across all bounded usage groups."""
    pe = PE(binary); lit = Literals72(binary, metadata)
    resolver = TypeResolver(pe, startup, records, pe.offset(HEADER_VA))
    base = number(binary, pe.offset(USAGE_REGISTRATION) + 0x48, 'Q')
    cache = {}
    def first(i):
        return ((number(metadata, lit.groups_base + i * 4) + 0x8bff3e05) & 0xffffffff) ^ ((i * 0x5843eaa0 - 0x443c8630) & 0xffffffff)
    # Some nested TPS branch class cells are initialized before the global group.
    for row in range(first(lit.global_group + 1)):
        key = (row * 0x57c15286 + 0x26ddebda) & 0xffffffff
        source = ((number(metadata, lit.usages_base + row * 8) + 0xabbdcabf) & 0xffffffff) ^ key ^ 0x2a357d90
        slot = number(metadata, lit.usages_base + row * 8 + 4) ^ key ^ 0x1855aa65
        if source >> 29 != 1: continue
        typ = resolver.resolve_index(source & 0x1fffffff)
        cache[base + slot * 8] = dict(usageRow=row, sourceIndex=source & 0x1fffffff, cell=hex(base + slot * 8), type=typ)
    return cache


def dependency_audit(binary, records, cells, python_libs):
    load_emulator(python_libs)
    from capstone import Cs, CS_ARCH_X86, CS_MODE_64
    pe = PE(binary); md = Cs(CS_ARCH_X86, CS_MODE_64); md.detail = True
    ends = sorted({int(m['registeredAddress'], 16) for t in records for m in t['methods'] if m['registeredAddress'] != '0x0'})
    pending = [ROOT]; seen = {}; edges = []; bindings = {}
    while pending:
        index = pending.pop()
        if index in seen: continue
        row = records[index]; seen[index] = row
        def add(typ, origin, site=None):
            target = typ.get('typeDefinition')
            if typ['kind'] == 'class' and target != index and target is not None and any(m['name'] == 'MOMMACBEMEK' for m in records[target]['methods']):
                edges.append(dict(source=index, target=target, origin=origin, site=site)); pending.append(target)
        for field in row['fields']:
            if field['offsetKind'] == 'instance':
                for typ in field['type'].get('arguments', [field['type']]): add(typ, 'field:' + field['name'])
        for method in row['methods']:
            if method['name'] not in ('MOMMACBEMEK', '.cctor'): continue
            start = int(method['registeredAddress'], 16); end = next(x for x in ends if x > start)
            raw = binary[pe.offset(start):pe.offset(start) + end - start]
            for ins in md.disasm(raw, start):
                for op in ins.operands:
                    if op.type == 3 and op.mem.base == 41:
                        cell = ins.address + ins.size + op.mem.disp
                        if cell in cells:
                            typ = cells[cell]['type']; target = typ.get('typeDefinition')
                            if typ['kind'] == 'class' and target in EXPECTED_CLOSURE: bindings[cell] = cells[cell]
                            add(typ, 'native-class-cell:' + hex(cell), hex(ins.address))
    if set(seen) != EXPECTED_CLOSURE: raise ValueError('Store dependency closure drift')
    return dict(typeCount=len(seen), instanceStorageFields=sum(sum(f['offsetKind'] == 'instance' for f in t['fields']) for t in seen.values()),
                scope='field/class-cell dependency audit; not a recovered recursive wire schema',
                types=[dict(typeIndex=i, clientType=t['name'], fields=[f for f in t['fields'] if f['offsetKind'] == 'instance'],
                            readers=[m for m in t['methods'] if m['name'] == 'MOMMACBEMEK']) for i, t in sorted(seen.items())],
                edges=edges, typeCells=list(bindings.values()))


class PlayerStoreGraph72(GraphReader72):
    def __init__(self, engine, directory, audit):
        super().__init__(engine, directory); e = self.e
        self.schemas.update(SCHEMAS); self.outer_entries = {}
        for row in audit['types']:
            index = row['typeIndex']; entry = int(row['readers'][0]['registeredAddress'], 16)
            klass = e.alloc(1024); table = e.alloc(16)
            e.write(klass + 0xC7, 1, 'B'); e.write(klass + 0x80, table)
            e.write(klass + 0xC0, 1, 'H'); e.write(klass + 0xBE, 32, 'H')
            e.write(table, self.interface); e.write(klass + 0xE8, entry)
            self.classes[index] = klass; self.class_indices[klass] = index; self.entries[index] = entry
            self.outer_entries[entry] = index; e.allow(entry)
        for row in audit['typeCells']: e.write(int(row['cell'], 16), self.classes[row['type']['typeDefinition']])
        codec = e.alloc(); e.write(codec + 0x10, 18, 'I'); e.write(codec + 0x18, self.classes[ITEM]); e.write(GLOBAL + 0x11270, codec)
        e.models[0x14052D0F0] = lambda: 0  # Explicit metadata-usage initialization fixture.
        self.heap_floor = e.heap_cursor

    def new_message(self, index, address=None):
        if index not in SCHEMAS:
            p = self.e.alloc(512) if address is None else address
            self.e.write(p, self.classes[index]); self.e.write(p + 0x14, 1, 'I'); self.objects[p] = index
            return p
        return super().new_message(index, address)

    def observe_entry(self, address):
        index = self.outer_entries.get(address)
        if self.active and index is not None and index not in SCHEMAS:
            stream = self.e.reg('rdx')
            if self.e.read(stream + 0x44, 'I') != self.e.read(stream + 0x38, 'I'):
                raise ValueError('Nonempty child outside F-03d2a1: ' + str(index))
        return super().observe_entry(address)

    def snapshot(self, pointer, depth=0):
        if pointer and self.objects[pointer] not in SCHEMAS:
            return dict(untracedType=self.objects[pointer], content='empty-envelope-only')
        return super().snapshot(pointer, depth)


def fixtures():
    cases = [
        dict(name='root-empty', typeIndex=ROOT, wire='', expected={}),
        dict(name='root-max', typeIndex=ROOT, wire='40ffffffff0f70ffffffff0f', expected={'PAKLDABINMP': 4294967295, 'CLFJNKKFONI': 4294967295}),
        dict(name='item-high64', typeIndex=ITEM, wire='082a108180808080808010', expected={'PJAGIOHBKLD': 42, 'FBLKPHOCBIK': 9007199254740993}),
        dict(name='item-max', typeIndex=ITEM, wire='08ffffffff0f10ffffffffffffffffff01', expected={'PJAGIOHBKLD': 4294967295, 'FBLKPHOCBIK': 18446744073709551615}),
        dict(name='item-duplicate-wrong-wire', typeIndex=ITEM, wire='080108020a001081808080808080101003', expected={'PJAGIOHBKLD': 2, 'FBLKPHOCBIK': 3}),
        dict(name='item-explicit-zero', typeIndex=ITEM, wire='08001000', expected={}),
        dict(name='scalar-leaf-seven', typeIndex=ITEM, wire='3a02082a', expected={'FLPGMLOFAKP_7': {'GNHFNECPAHD': 42}}),
        dict(name='scalar-leaf-nine', typeIndex=ITEM, wire='4a0608ffffffff0f', expected={'FLPGMLOFAKP_9': {'GNHFNECPAHD': 4294967295}}),
        dict(name='same-scalar-leaf-omission-retains', typeIndex=ITEM, wire='3a02082a3a00', expected={'FLPGMLOFAKP_7': {'GNHFNECPAHD': 42}}),
        dict(name='same-scalar-leaf-explicit-zero', typeIndex=ITEM, wire='3a02082a3a020800', expected={'FLPGMLOFAKP_7': {}}),
        dict(name='switch-and-return-clears-old-value', typeIndex=ITEM, wire='3a02082a4a0208013a00', expected={'FLPGMLOFAKP_7': {}}),
        dict(name='root-two-item-list', typeIndex=ROOT, wire='120408011002120608034a020804',
             expected={'MBEEKCPJBEL': [{'PJAGIOHBKLD': 1, 'FBLKPHOCBIK': 2}, {'PJAGIOHBKLD': 3, 'FLPGMLOFAKP_9': {'GNHFNECPAHD': 4}}]}),
        dict(name='root-empty-item-list', typeIndex=ROOT, wire='1200', expected={'MBEEKCPJBEL': [{}]})]
    for tag, child in BRANCHES.items():
        expected = {} if child in SCHEMAS else dict(untracedType=child, content='empty-envelope-only')
        cases.append(dict(name='item-branch-envelope-' + str(tag), typeIndex=ITEM,
                          wire=(varint(tag << 3 | 2) + b'\0').hex(), expected={'FLPGMLOFAKP_' + str(tag): expected}))
    for index in (51247, 40274):
        cases.append(dict(name='scalar-leaf-max-' + str(index), typeIndex=index, wire='08ffffffff0f', expected={'GNHFNECPAHD': 4294967295}))
    return cases


def verify(graph):
    results = []
    for case in fixtures():
        row = graph.run_graph(case['typeIndex'], bytes.fromhex(case['wire']))
        if row['result'] != case['expected']: raise ValueError(case['name'] + ': native graph differs ' + str(row['result']))
        results.append(dict(case, observation=row))
    rejected = []
    for name, index, wire, scope in [('zero-tag', ROOT, '00', 'explicit-input-model'), ('truncated-varint', ITEM, '1080', 'explicit-input-model'),
        ('overflow-u64', ITEM, '10ffffffffffffffffff02', 'explicit-input-model'),
        ('truncated-item-list', ROOT, '120208', 'explicit-input-model'),
        ('untraced-nonempty-nested', ITEM, '2a020801', 'explicit-scope-refusal')]:
        try: graph.run_graph(index, bytes.fromhex(wire))
        except ValueError as error: rejected.append(dict(name=name, wire=wire, scope=scope, error=str(error)))
        else: raise ValueError('Invalid/out-of-scope fixture accepted: ' + name)
    return results, rejected


def scan_raw(graph):
    expected = {ROOT: {2: 2, 8: 0, 14: 0}, ITEM: {1: 0, 2: 0, **dict.fromkeys(BRANCHES, 2)},
                51247: {1: 0}, 40274: {1: 0}}
    selected = []; count = 0
    for index, tags in expected.items():
        observed = set()
        for tag in range(1, 33):
            for wire, payload in ((0, varint(0x12345678)), (1, bytes(8)), (2, b'\0'), (5, bytes(4))):
                row = graph.run_graph(index, varint(tag << 3 | wire) + payload); count += 1
                match = tags.get(tag) == wire
                if bool(row['result'] or row['stores']) != match:
                    raise ValueError(f'Store outer raw branch mismatch: {index}/{tag}/{wire}')
                if match:
                    observed.add(tag)
                    selected.append(dict(typeIndex=index, tag=tag, wireType=wire,
                        result=row['result'], entered=row['entered'], stores=row['stores']))
        if observed != set(tags): raise ValueError('Store outer raw field missing')
    return dict(nativeFixtures=count, scope='four selected types, tags1..32/wire0,1,2,5; child envelopes explicitly incomplete', fields=selected)


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--client', required=True, type=Path); p.add_argument('--output', required=True, type=Path)
    p.add_argument('--observations', required=True, type=Path); p.add_argument('--python-libs', type=Path, default=Path('local/migration72/python'))
    p.add_argument('--directory', type=Path, default=Path('protocol/cn-7.2')); a = p.parse_args()
    paths = [a.output.resolve(), a.observations.resolve()]
    if len(set(paths)) != 2 or any(x.is_relative_to(a.client.resolve()) for x in paths): p.error('Distinct outputs outside client required')
    b, m, s = [read_verified(a.client / name, digest) for name, digest in HASHES.items()]
    records, _ = extract(b, m, startup=s, structures=True)
    cells = type_cells(b, m, s, records); audit = dependency_audit(b, records, cells, a.python_libs)
    commands = [x for x in classify_commands(records)['commands'] if x['cmdId'] == 20892]
    if len(commands) != 1 or commands[0]['typeIndex'] != ROOT: raise ValueError('Store Cmd/type binding drift')
    e = Offline72(b, a.python_libs, records, trace_instructions=False); graph = PlayerStoreGraph72(e, a.directory, audit)
    try:
        scan = scan_raw(graph)
        good, bad = verify(graph)
    finally: graph.close(); e.close()
    context = dict(schemaVersion=1, task='F-03d2a1', inputSHA256=HASHES, runtimeStatus='not-tested', targetProtocolReady=False,
        candidateSemanticName='PlayerStoreNotify', semanticIdentityStatus='unresolved', commands=commands,
        dependencyAudit=audit, messages=list(SCHEMAS.values()), scan=scan, nativeFixtures=len(good), rejectedFixtures=len(bad),
        fullStoreRawClosure=False, limitations=['Only root, Item outer/oneof bindings and two uint32 leaves recovered',
            'Other children execute EMPTY envelopes only; nonempty child input explicitly refused',
            'Managed allocation/metadata usage initialization/primitive input and list runtime contexts are fixtures',
            'Native list append, ReadMessage/limits, concrete readers and scalar oneof merge/switch execute',
            'Remaining primitive maps/lists/nested oneof contents belong to F-03d2a2/a3; no production or live acceptance'])
    summary = dict(context, fixtures=[dict(name=x['name'], typeIndex=x['typeIndex'], wire=x['wire'], result=x['observation']['result'],
                    entered=x['observation']['entered'], stores=x['observation']['stores']) for x in good], rejections=bad)
    for path, value in zip(paths, [dict(context, fixtures=good, rejections=bad), summary]):
        path.parent.mkdir(parents=True, exist_ok=True); path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', 'utf8')
    print(json.dumps(dict(dependencyTypes=audit['typeCount'], nativeFixtures=len(good), rejections=len(bad), fullStoreRawClosure=False)))


if __name__ == '__main__': main()
