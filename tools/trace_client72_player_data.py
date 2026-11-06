"""Hash-bound PlayerData candidate raw graph; business identity stays unresolved.

Only types 79263/78350 are added to the existing native graph harness. No live
client, server, production Proto, command constants or mappings are changed.
"""
import argparse
import json
import struct
from pathlib import Path
try:
    from .extract_client72 import HASHES, PE, HEADER_VA, CODE_REGISTRATION, TypeResolver, number, read_verified, extract
    from .extract_client72_commands import classify_commands
    from .client72_emulator import Offline72, GLOBAL
    from .client72_graph import GraphReader72
    from .client72_map_runtime import MapRuntime72
except ImportError:
    from extract_client72 import HASHES, PE, HEADER_VA, CODE_REGISTRATION, TypeResolver, number, read_verified, extract
    from extract_client72_commands import classify_commands
    from client72_emulator import Offline72, GLOBAL
    from client72_graph import GraphReader72
    from client72_map_runtime import MapRuntime72

ROOT, VALUE = 79263, 78350
ENTRIES = {ROOT: 0x14E420460, VALUE: 0x147F16990}
SCHEMAS = {
    ROOT: dict(typeIndex=ROOT, clientType='BIMKHOFCKHB', fields=[
        dict(clientField='PLMNNCGEMIN', objectOffset=44, tag=5, type='uint32', cardinality='singular'),
        dict(clientField='IKIFIBAEDGD', objectOffset=24, tag=6, type='map', keyType='uint32', valueType='message', cardinality='map', nestedTypeIndex=VALUE),
        dict(clientField='OMFPDOKCLNL', objectOffset=40, tag=7, type='bool', cardinality='singular'),
        dict(clientField='FPOLBCIBIOB', objectOffset=32, tag=8, type='string', cardinality='singular'),
        dict(clientField='KFICMDLCEHE', objectOffset=48, tag=13, type='uint64', cardinality='singular')]),
    VALUE: dict(typeIndex=VALUE, clientType='CODAEKFNEEE', fields=[
        dict(clientField='NOEEBKJAFAG', objectOffset=36, tag=1, type='uint32', cardinality='singular'),
        dict(clientField='BNJKCHONGGE_int64', objectOffset=24, tag=2, type='int64', cardinality='singular', discriminatorOffset=32, discriminatorValue=2),
        dict(clientField='BNJKCHONGGE_float', objectOffset=24, tag=3, type='float', cardinality='singular', discriminatorOffset=32, discriminatorValue=3),
        dict(clientField='AEJAFPNBBHO', objectOffset=40, tag=4, type='int64', cardinality='singular')])}


def map_bindings(binary, metadata, startup, records):
    """Independently read the concrete map registration, including shared body."""
    pe = PE(binary); h = pe.offset(HEADER_VA); cr = pe.offset(CODE_REGISTRATION)
    specs = 0x210 + (number(binary, h + 0x170) ^ 0x6347382B)
    spec_count = (number(binary, h + 0x24) ^ 0x323BAE70) // 12
    table = 0x210 + (number(binary, h + 0xEC) ^ 0x3CF0720C)
    size = (number(binary, h + 0x110) + 0xA3D9CC8B) & 0xffffffff
    count = (number(binary, cr + 0x64) + 0x9EA91E3B) & 0xffffffff
    pointers = pe.offset(number(binary, cr + 0x30, 'Q'), count * 8)
    methods = {m['index']: (r, m) for r in records for m in r['methods']}
    resolver = TypeResolver(pe, startup, records, h); result = []
    for i in range(size // 14):
        pointer, spec = struct.unpack_from('<ii', metadata, table + i * 14)
        if not 0 <= pointer < count or not 0 <= spec < spec_count: raise ValueError('Invalid AOT reference')
        method, cls, args = struct.unpack_from('<iii', metadata, specs + spec * 12)
        owner, m = methods[method]
        if owner['index'] not in (66210, 66214) or cls not in (9505, 1541): continue
        if m['name'] not in ('.ctor', 'MOMMACBEMEK', 'FNNADLCNGFO', 'set_Item'): continue
        if args != -1: raise ValueError('Unexpected method arguments')
        address = number(binary, pointers + pointer * 8, 'Q'); pe.offset(address, executable=True)
        result.append(dict(owner=owner['index'], classInstance=cls, method=m['name'],
                           methodDefinition=method, methodSpec=spec, tableIndex=i,
                           pointerIndex=pointer, address=hex(address),
                           arguments=[resolver.resolve_index(x) for x in resolver.arguments[cls]]))
    concrete = [x for x in result if x['owner'] == 66210 and x['classInstance'] == 9505 and x['method'] == 'FNNADLCNGFO']
    if len(concrete) != 1 or concrete[0]['arguments'][1].get('typeDefinition') != VALUE:
        raise ValueError('Concrete uint32/value AOT registration changed')
    return result


class PlayerDataMap72(MapRuntime72):
    CONFIG = {6: dict(owner=66210, concrete=9505, shared=1541, adapter=66214,
        child=VALUE, key='uint32', static=0x11280, lookup=0x1578B66F0,
        insert=0x1578B6A20, dictionaryOffset=16, listOffset=24, entriesOffset=24, nodeOffset=40)}

    def __init__(self, graph, directory, bindings):
        self.raw_bindings = bindings
        super().__init__(graph, directory)

    def binding(self, owner, instance, method):
        hits = {int(x['address'], 16) for x in self.raw_bindings
                if (x['owner'], x['classInstance'], x['method']) == (owner, instance, method)}
        if len(hits) != 1: raise ValueError('Missing/ambiguous PlayerData map binding')
        return hits.pop()


class PlayerDataGraph72(GraphReader72):
    def __init__(self, engine, directory, bindings):
        super().__init__(engine, directory); e = self.e
        self.schemas.update(SCHEMAS)
        for index, entry in ENTRIES.items():
            klass = e.alloc(1024); interface = e.alloc(16)
            e.write(klass + 0xC7, 1, 'B'); e.write(klass + 0x80, interface)
            e.write(klass + 0xC0, 1, 'H'); e.write(klass + 0xBE, 32, 'H')
            e.write(interface, self.interface); e.write(klass + 0xE8, entry)
            self.classes[index] = klass; self.class_indices[klass] = index; self.entries[index] = entry
            e.allow(entry)
        e.synthetic_type(0x1458AD9F0)
        self.box_classes = {e.synthetic_type(0x145886320): ('int64', 8),
                            e.synthetic_type(0x1458864C0): ('float', 4)}
        e.models[0x140525C40] = self.box
        e.models[0x1474E97E0] = self.float32
        self.maps = PlayerDataMap72(self, directory, bindings)
        self.heap_floor = e.heap_cursor

    def box(self):
        e = self.e; kind, width = self.box_classes[e.reg('rcx')]
        raw = bytes(e.uc.mem_read(e.reg('rdx'), width)); p = e.alloc()
        e.write(p, e.reg('rcx')); e.uc.mem_write(p + 0x10, raw)
        self.values[p] = dict(kind=kind, hex=raw.hex())
        return p

    def float32(self):
        self.sync(); raw = self.input.take(4); self.position()
        self.e.uc.reg_write(self.e.R.UC_X86_REG_XMM0, int.from_bytes(raw, 'little'))
        return 0

    def snapshot(self, pointer, depth=0):
        if pointer and self.objects[pointer] == VALUE:
            e = self.e; result = {}; discriminator = e.read(pointer + 32, 'I')
            if e.read(pointer + 36, 'I'): result['NOEEBKJAFAG'] = e.read(pointer + 36, 'I')
            if discriminator:
                boxed = self.values[e.read(pointer + 24)]
                if discriminator not in (2, 3): raise ValueError('Unexpected oneof discriminator')
                kind = 'int64' if discriminator == 2 else 'float'
                if boxed['kind'] != kind: raise ValueError('Wrong boxed oneof type')
                raw = bytes.fromhex(boxed['hex'])
                result['BNJKCHONGGE_' + kind] = struct.unpack('<q' if kind == 'int64' else '<f', raw)[0]
            if e.read(pointer + 40, 'q'): result['AEJAFPNBBHO'] = e.read(pointer + 40, 'q')
            return result
        return super().snapshot(pointer, depth)


def fixtures():
    # Hex keys, lengths and independently expected values are authored explicitly.
    return [
        dict(name='empty', typeIndex=ROOT, wire='', expected={}),
        dict(name='outer-nonzero-high64', typeIndex=ROOT, wire='280138014203416263688180808080808010',
             expected={'PLMNNCGEMIN': 1, 'OMFPDOKCLNL': True, 'FPOLBCIBIOB': 'Abc', 'KFICMDLCEHE': 9007199254740993}),
        dict(name='outer-max-and-bool-normalization', typeIndex=ROOT, wire='28ffffffff0f380268ffffffffffffffffff01',
             expected={'PLMNNCGEMIN': 4294967295, 'OMFPDOKCLNL': True, 'KFICMDLCEHE': 18446744073709551615}),
        dict(name='outer-explicit-zero', typeIndex=ROOT, wire='2800380042006800', expected={}),
        dict(name='outer-duplicates-and-wrong-wire', typeIndex=ROOT, wire='280128022a0038013800', expected={'PLMNNCGEMIN': 2}),
        dict(name='value-int64-and-independent-int64', typeIndex=VALUE, wire='082a10ffffffffffffffffff01208180808080808010',
             expected={'NOEEBKJAFAG': 42, 'BNJKCHONGGE_int64': -1, 'AEJAFPNBBHO': 9007199254740993}),
        dict(name='value-float', typeIndex=VALUE, wire='1d0000a0bf', expected={'BNJKCHONGGE_float': -1.25}),
        dict(name='value-oneof-switch-and-return', typeIndex=VALUE, wire='10011d000020401002', expected={'BNJKCHONGGE_int64': 2}),
        dict(name='value-oneof-explicit-zero', typeIndex=VALUE, wire='1000', expected={'BNJKCHONGGE_int64': 0}),
        dict(name='value-float-explicit-zero', typeIndex=VALUE, wire='1d00000000', expected={'BNJKCHONGGE_float': 0.0}),
        dict(name='value-signed-boundaries', typeIndex=VALUE, wire='10ffffffffffffffff7f2080808080808080808001',
             expected={'BNJKCHONGGE_int64': 9223372036854775807, 'AEJAFPNBBHO': -9223372036854775808}),
        dict(name='nonempty-map-int64', typeIndex=ROOT, wire='32110807120d082a10ffffffffffffffffff01',
             expected={'IKIFIBAEDGD': {'7': {'NOEEBKJAFAG': 42, 'BNJKCHONGGE_int64': -1}}}),
        dict(name='map-duplicate-replaces-value', typeIndex=ROOT, wire='32060807120210013209080712051d00002040',
             expected={'IKIFIBAEDGD': {'7': {'BNJKCHONGGE_float': 2.5}}}),
        dict(name='map-two-keys-and-empty-default', typeIndex=ROOT, wire='3206080112021001320408021200',
             expected={'IKIFIBAEDGD': {'1': {'BNJKCHONGGE_int64': 1}, '2': {}}})]


def scan_raw(graph):
    """Bounded tag/wire scan; completeness is checked against instance layout."""
    try:
        from .trace_client72_token import varint
    except ImportError:
        from trace_client72_token import varint
    expected = {ROOT: {5: 0, 6: 2, 7: 0, 8: 2, 13: 0}, VALUE: {1: 0, 2: 0, 3: 5, 4: 0}}
    selected = []; count = 0
    for index in (ROOT, VALUE):
        observed = set()
        for tag in range(1, 33):
            for wire, payload in ((0, varint(0x12345678)), (1, bytes(8)), (2, b'\0'), (5, bytes.fromhex('0000a03f'))):
                row = graph.run_graph(index, varint(tag << 3 | wire) + payload); count += 1
                match = expected[index].get(tag) == wire
                if bool(row['result'] or row['stores']) != match:
                    raise ValueError(f'Raw scan unexpected/missing native branch {index}/{tag}/{wire}')
                if match:
                    observed.add(tag)
                    selected.append(dict(typeIndex=index, tag=tag, wireType=wire,
                                         result=row['result'], stores=row['stores'], entered=row['entered']))
        if observed != set(expected[index]): raise ValueError('Raw field coverage incomplete')
    expected_layout = {ROOT: {24: 'genericinst', 32: 'string', 40: 'bool', 44: 'uint32', 48: 'uint64'},
                       VALUE: {24: 'object', 32: 'valuetype', 36: 'uint32', 40: 'int64'}}
    for index, layout in expected_layout.items():
        actual = {f['offset']: f['type']['kind'] for f in graph.e.records[index]['fields'] if f['offsetKind'] == 'instance'}
        if actual != layout: raise ValueError('Raw metadata layout drift')
    return dict(nativeFixtures=count, scope='tags1..32/wire0,1,2,5; all metadata instance storage covered', fields=selected)


def verify(graph):
    results = []
    for case in fixtures():
        row = graph.run_graph(case['typeIndex'], bytes.fromhex(case['wire']))
        if row['result'] != case['expected']: raise ValueError(case['name'] + ': native graph differs ' + str(row['result']))
        if case['name'] == 'map-duplicate-replaces-value' and not row['mapStores']:
            raise ValueError('Missing native map overwrite stores')
        results.append(dict(case, observation=row))
    rejected = []
    for name, raw in [('zero-tag', '00'), ('truncated-varint', '6880'), ('truncated-string', '420341'),
                      ('overflow-varint', '68ffffffffffffffffff02')]:
        try: graph.run_graph(ROOT, bytes.fromhex(raw))
        except ValueError as error: rejected.append(dict(name=name, wire=raw, error=str(error), scope='explicit-input-model'))
        else: raise ValueError(name + ': malformed model input accepted')
    return results, rejected


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--client', required=True, type=Path); p.add_argument('--output', required=True, type=Path)
    p.add_argument('--observations', required=True, type=Path)
    p.add_argument('--python-libs', type=Path, default=Path('local/migration72/python'))
    p.add_argument('--directory', type=Path, default=Path('protocol/cn-7.2')); a = p.parse_args()
    paths = [a.output.resolve(), a.observations.resolve()]
    if len(set(paths)) != 2 or any(x.is_relative_to(a.client.resolve()) for x in paths): p.error('Distinct outputs outside client required')
    b, m, s = [read_verified(a.client / name, digest) for name, digest in HASHES.items()]
    records, _ = extract(b, m, startup=s, structures=True)
    bindings = map_bindings(b, m, s, records)
    commands = [c for c in classify_commands(records)['commands'] if c['cmdId'] == 7193]
    if len(commands) != 1 or commands[0]['typeIndex'] != ROOT: raise ValueError('Cmd/type binding changed')
    e = Offline72(b, a.python_libs, records, trace_instructions=False)
    g = PlayerDataGraph72(e, a.directory, bindings)
    try:
        scan = scan_raw(g)
        good, bad = verify(g)
    finally: g.close(); e.close()
    context = dict(schemaVersion=1, inputSHA256=HASHES, runtimeStatus='not-tested', targetProtocolReady=False,
        candidateSemanticName='PlayerDataNotify', semanticIdentityStatus='unresolved', commands=commands,
        messages=[dict(v, reader=hex(ENTRIES[k]), semanticStatus='unresolved') for k, v in SCHEMAS.items()],
        aotBindings=bindings, scan=scan, nativeFixtures=len(good), modelRejections=len(bad),
        limitations=['No independent business identity or field-name proof',
                    'Native concrete message readers, ReadMessage/limits, shared map adapter/setter execute',
                    'Managed allocation, primitive input, ASCII strings, boxing and generic runtime contexts are explicit fixtures',
                    'Managed map lookup/insertion and linked node allocation modeled; duplicate value overwrite is native',
                    'Fixed map capacity64; IFix/live ABI/dispatcher/UI/client acceptance not executed',
                    'This graph has no repeated list; Avatar/Item closures remain outside scope'])
    summary = dict(context, fixtures=[dict(name=x['name'], typeIndex=x['typeIndex'], wire=x['wire'], result=x['observation']['result'],
                   entered=x['observation']['entered'], mapStores=x['observation']['mapStores']) for x in good], rejections=bad)
    for path, value in zip(paths, [dict(context, fixtures=good, rejections=bad), summary]):
        path.parent.mkdir(parents=True, exist_ok=True); path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', 'utf8')
    print(json.dumps(dict(nativeFixtures=len(good), modelRejections=len(bad), semanticIdentityStatus='unresolved')))


if __name__ == '__main__': main()
