"""Bounded native comparison of the Avatar root GUID and local-avatar consumers.

The selected hero source and active scene entity are distinct inputs in these
controlled storage fixtures. This does not prove a complete scene handler.
"""
import argparse
import hashlib
import json
import struct
from pathlib import Path

try:
    from .extract_client72 import HASHES, read_verified, extract
    from .trace_client72_avatar_core import core_aot
    from .trace_client72_avatar_outer import avatar_aot
    from .trace_client72_player_store import type_cells
    from .client72_avatar_contract import AvatarContractGraph, AvatarRuntime, avatar_wire, declarations
    from .client72_emulator import Offline72, GLOBAL
    from .client72_graph_fixtures import scalar, message
except ImportError:
    from extract_client72 import HASHES, read_verified, extract
    from trace_client72_avatar_core import core_aot
    from trace_client72_avatar_outer import avatar_aot
    from trace_client72_player_store import type_cells
    from client72_avatar_contract import AvatarContractGraph, AvatarRuntime, avatar_wire, declarations
    from client72_emulator import Offline72, GLOBAL
    from client72_graph_fixtures import scalar, message

F1_SHA = 'a54f62de9a803e2070c515ecc854488827ea6c62db4468c633190d6733f2b2f3'
NATIVE = {0x1507CC240, 0x1507CEE50, 0x1517B8730, 0x14E9B2C60,
          0x156F6F510, 0x1517ABD80, 0x153E46950, 0x153E4D900}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ('client', 'output', 'observations'):
        parser.add_argument('--' + name, type=Path, required=True)
    parser.add_argument('--directory', type=Path, default=Path('protocol/cn-7.2'))
    parser.add_argument('--python-libs', type=Path, default=Path('local/migration72/python'))
    args = parser.parse_args()
    outputs = [args.output.resolve(), args.observations.resolve()]
    if len(set(outputs)) != 2 or any(p.is_relative_to(args.client.resolve()) for p in outputs):
        parser.error('Distinct outputs outside the client are required')
    f1 = (args.directory / 'avatar-contract-observations.json').read_bytes()
    if hashlib.sha256(f1).hexdigest() != F1_SHA:
        raise ValueError('Reviewed Avatar contract changed')
    binary, metadata, startup = [read_verified(args.client / name, digest) for name, digest in HASHES.items()]
    records, _ = extract(binary, metadata, startup=startup, structures=True)
    declarations(binary, metadata, startup, records, args.directory)
    cells = type_cells(binary, metadata, startup, records)
    bindings = core_aot(binary, metadata, startup, records)
    outer_bindings = avatar_aot(binary, metadata, startup, records, dict(types=[]))
    outer = json.loads((args.directory / 'avatar-outer-observations.json').read_text('utf8'))
    declared = control_declarations(records, cells)
    rows = [run_case(binary, records, cells, bindings, outer_bindings, outer, args, case) for case in cases()]
    report = dict(schemaVersion=1, task='F-03d3f2a', inputSHA256=HASHES,
                  avatarContractSHA256=F1_SHA, declarations=declared, fixtures=rows,
                  runtimeStatus='not-tested', targetProtocolReady=False,
                  currentControlGuidBindingApproved=False, completeSceneHandler=False,
                  conclusion='Root tag3 feeds the named Hero entity consumer; the local-avatar consumer separately reads scene entity ID +0x284',
                  models=['Managed class/allocation and Avatar batch application boundaries inherit fixed F-03d3f1',
                          'Scene entity rows, model-to-entity IDs and active entity ID are explicit controlled storage',
                          'UInt32 entity dictionary index lookup is a declared fixture; native UInt64 GUID lookup executes',
                          'Hero movement ends at the real effect entry; scene generation, switching and movement effects are not executed'])
    root = Path(__file__).resolve().parents[1]
    report['sourceSHA256'] = {name: hashlib.sha256((root / name).read_bytes()).hexdigest() for name in (
        'tools/client72_avatar_control.py', 'tools/client72_avatar_contract.py', 'tools/client72_emulator.py',
        'tools/client72_session_runtime.py')}
    summary = dict(report, fixtures=[compact(row) for row in rows])
    for path, data in zip(outputs, (report, summary)):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(data, indent=2) + '\n', encoding='utf8')
    print(json.dumps(dict(fixtures=len(rows), currentControlGuidBindingApproved=False)))


def control_declarations(records, cells):
    methods = []
    for owner, address, name in [(49228, 0x1517B8730, None), (49228, 0x1517ABD80, None),
                                 (58088, 0x153E46950, 'GetLocalAvatarConfigId'),
                                 (58088, 0x153E4D900, 'SetHeroMoveRatio')]:
        method = next(m for m in records[owner]['methods'] if m['registeredAddress'] == hex(address))
        if name and method['name'] != name:
            raise ValueError('Named Avatar consumer changed')
        methods.append(dict(owner=owner, **method))
    field = next(f for f in records[58013]['fields'] if f['offset'] == 0x128)
    active = next(f for f in records[49228]['fields'] if f['offset'] == 0x284)
    if field['type']['kind'] != 'uint64' or active['type']['kind'] != 'uint32':
        raise ValueError('Avatar GUID and active entity storage changed')
    return dict(methods=methods, playerGuidField=field, activeEntityField=active,
                classUsages=[cells[cell] for cell in (0x14589BC70, 0x145898D98, 0x145897948)])


def cases():
    return [dict(name=name, guids=guids, hero=hero, active=active, enabled=enabled) for name, guids, hero, active, enabled in (
        ('hero-a-local-b', [9007199254740993, 9223372036854775809], 9007199254740993, 1, True),
        ('hero-b-local-a', [9007199254740993, 9223372036854775809], 9223372036854775809, 0, True),
        ('hero-and-local-match', [0x100000001, 0x200000002], 0x100000001, 0, True),
        ('max-u64-hero', [0xffffffffffffffff, 0x200000002], 0xffffffffffffffff, 1, True),
        ('missing-hero-keeps-local', [0x100000001, 0x200000002], 999, 1, True),
        ('zero-hero-keeps-local', [0x100000001, 0x200000002], 0, 1, True),
        ('disabled-hero-keeps-local', [0x100000001, 0x200000002], 0x100000001, 1, False))]


def run_case(binary, records, cells, bindings, outer_bindings, outer, args, case):
    engine = Offline72(binary, args.python_libs, records, trace_instructions=False)
    graph = AvatarContractGraph(engine, args.directory, cells, bindings, outer_bindings, outer)
    try:
        wire = scalar(3, case['hero']) + b''.join(message(6, avatar_wire(1001 + i, guid, [], [])) for i, guid in enumerate(case['guids']))
        reader = graph.run_graph(30509, wire)
        runtime = AvatarRuntime(engine, graph)
        dispatch = runtime.root()
        identities = [runtime.identity(model, source) for model, source in runtime.delivery]
        scene, entities = scene_storage(engine, runtime, case)
        for entry in (0x1517B8730, 0x1517ABD80, 0x153E46950, 0x153E4D900):
            engine.allow(entry)
        selected = runtime.invoke(0x1517B8730, rcx=scene)
        hero_entity = engine.reg('rax')
        local = runtime.invoke(0x153E46950)
        local_config = engine.reg('eax')
        effect = runtime.invoke(0x153E4D900, {0x153E6D660, 0x620000100},
                                xmm0=int.from_bytes(struct.pack('<f', 1.25), 'little'))
        delivered = engine.reg('rcx') if effect['stop'] == '0x153e6d660' else 0
        expected = case['guids'].index(case['hero']) if case['hero'] in case['guids'] and case['enabled'] else None
        if hero_entity != (entities[expected] if expected is not None else 0) or delivered != hero_entity:
            raise ValueError('Native Hero GUID lookup or movement destination differs')
        if local_config != 1001 + case['active'] or engine.read(runtime.model + 0x128) != case['hero']:
            raise ValueError('Native local-avatar result or root GUID storage differs')
        for trace, required in [(selected, {0x1517B8730, 0x14E9B2C60, 0x156F6F510}),
                                (local, {0x153E46950, 0x1517ABD80}), (effect, {0x153E4D900, 0x1517B8730})]:
            if not {hex(x) for x in required} <= set(trace['executedAddresses']) or {hex(x) for x in required} & set(trace['models']):
                raise ValueError('A native control consumer was replaced by a model')
        return dict(case, wire=wire.hex(), reader=reader, dispatch=dispatch, identities=identities,
                    storedGuid=engine.read(runtime.model + 0x128), heroOrdinal=expected,
                    activeOrdinal=case['active'], localConfig=local_config,
                    hero=selected, local=local, heroEffect=effect, heroEffectDelivered=bool(delivered))
    finally:
        graph.close()
        engine.close()


def scene_storage(engine, runtime, case):
    scene = engine.alloc(1024)
    engine.write(GLOBAL + 0xA3A0, scene)
    engine.write(GLOBAL + 0x9E80, runtime.network)
    engine.write(0x14589BC70, runtime.klass)
    entity_class = engine.synthetic_type(0x145898D98)
    engine.synthetic_type(0x145897948)
    entries, dictionary = runtime.array(24), engine.alloc()
    engine.write(dictionary + 0x18, entries)
    engine.write(scene + 0x68, dictionary)
    positions, entities = {}, []
    for i, (model, _) in enumerate(runtime.delivery):
        entity, entity_id = engine.alloc(0x600), 0x100001 + i
        engine.write(entity, entity_class)
        engine.write(entity + 0x80, 1)
        engine.write(entity + 0x3F4, 1001 + i, 'I')
        engine.write(entity + 0x4FA, int(case['enabled']), 'B')
        engine.write(entries + 0x30 + i * 24, entity)
        positions[entity_id] = i
        entities.append(entity)
        engine.write(model + 0x218, entity_id, 'I')
    listing = engine.alloc()
    engine.write(listing + 16, runtime.array(8))
    engine.write(scene + 0x198, listing)
    engine.write(scene + 0x284, 0x100001 + case['active'], 'I')
    previous = engine.models[0x1578B66F0]
    engine.models[0x1578B66F0] = lambda: positions.get(engine.reg('edx'), 0xffffffff) if engine.reg('rcx') == dictionary else previous()
    return scene, entities


def compact(row):
    result = {k: v for k, v in row.items() if k not in ('reader', 'dispatch', 'identities', 'hero', 'local', 'heroEffect')}
    for key in ('dispatch', 'hero', 'local', 'heroEffect'):
        trace = row[key]
        result[key] = {k: trace[k] for k in ('entry', 'stop', 'nativePathSHA256', 'models')}
        result[key]['nativeEntries'] = sorted(hex(x) for x in NATIVE if hex(x) in trace['executedAddresses'])
    result['readerResult'] = row['reader']['result']
    return result


if __name__ == '__main__':
    main()
