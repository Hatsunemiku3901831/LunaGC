"""CN72 AvatarInfo tag4: bounded native life-state application and effects.

Target enums and named events establish meaning; effect delivery, entity virtual
calls and IFix wrappers retain explicit, non-successful boundaries.
"""
import argparse
import hashlib
import json
import struct
from pathlib import Path

try:
    from .extract_client72 import HASHES, PE, HEADER_VA, BODY, CODE_REGISTRATION, Names, TypeResolver, number, read_verified, extract
    from .extract_client72_signatures import parameter_start, parameter_words
    from .extract_client72_enums import recover as recover_enums
    from .extract_client72_literals import Literals72, USAGE_REGISTRATION
    from .trace_client72_avatar_core import core_aot
    from .trace_client72_avatar_outer import avatar_aot
    from .trace_client72_player_store import type_cells
    from .client72_avatar_contract import AvatarContractGraph, AvatarRuntime, avatar_wire, declarations, RETURN
    from .client72_graph_fixtures import scalar, message
    from .client72_emulator import Offline72, GLOBAL
    from .trace_client72_token import load_emulator
except ImportError:
    from extract_client72 import HASHES, PE, HEADER_VA, BODY, CODE_REGISTRATION, Names, TypeResolver, number, read_verified, extract
    from extract_client72_signatures import parameter_start, parameter_words
    from extract_client72_enums import recover as recover_enums
    from extract_client72_literals import Literals72, USAGE_REGISTRATION
    from trace_client72_avatar_core import core_aot
    from trace_client72_avatar_outer import avatar_aot
    from trace_client72_player_store import type_cells
    from client72_avatar_contract import AvatarContractGraph, AvatarRuntime, avatar_wire, declarations, RETURN
    from client72_graph_fixtures import scalar, message
    from client72_emulator import Offline72, GLOBAL
    from trace_client72_token import load_emulator

NATIVE = {0x15184E100, 0x151849670, 0x151840630, 0x151852150, 0x1517AB3A0, 0x148D74B00}
STOPS = {0x15184E202, 0x148D74B38, 0x14E4E1000,
         0x151849ABC, 0x151840710, 0x148D74B5E, 0x1404C2BE0}
EVIDENCE_SHA = {
    'avatar-complete-raw-observations.json': '37c90e4dea9fccc4ac20ce68616612250b5a57169e564081c33dc69a25def837',
    'avatar-contract-observations.json': 'a54f62de9a803e2070c515ecc854488827ea6c62db4468c633190d6733f2b2f3',
    'avatar-control-observations.json': '8e2433b3d13ec17414cc26f21461f1cc62f64b16c413b327610ce0b7e7480ec0',
    'scene-entry-identity-observations.json': '550fcdd642132598256aca41bc79eec89bdfb7009ad2d22ab7a82409484644d1',
    'avatar-team-observations.json': '3a08503225ecbb198a7cc6340b203cc6ce76dd7c14b5619da845b35e74993073'}
IFIX = {'state': (0x1457D8356, 0x151849ABC), 'setter': (0x1457D8350, 0x151840710),
        'entity': (0x1457EBEE3, 0x148D74B5E)}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ('client', 'output', 'observations'): parser.add_argument('--' + name, type=Path, required=True)
    parser.add_argument('--directory', type=Path, default=Path('protocol/cn-7.2'))
    parser.add_argument('--python-libs', type=Path, default=Path('local/migration72/python'))
    args = parser.parse_args(); outputs = [args.output.resolve(), args.observations.resolve()]
    if len(set(outputs)) != 2 or any(p.is_relative_to(args.client.resolve()) for p in outputs):
        parser.error('Distinct outputs outside the client are required')
    for name, digest in EVIDENCE_SHA.items():
        if hashlib.sha256((args.directory / name).read_bytes()).hexdigest() != digest:
            raise ValueError('Reviewed Avatar source changed: ' + name)
    binary, metadata, startup = [read_verified(args.client / name, digest) for name, digest in HASHES.items()]
    records, extraction = extract(binary, metadata, startup=startup, structures=True)
    declarations(binary, metadata, startup, records, args.directory)
    declared = state_declarations(binary, metadata, startup, records, extraction, args.python_libs)
    cells = type_cells(binary, metadata, startup, records)
    bindings = core_aot(binary, metadata, startup, records)
    outer_bindings = avatar_aot(binary, metadata, startup, records, dict(types=[]))
    outer = json.loads((args.directory / 'avatar-outer-observations.json').read_text('utf8'))
    rows = [run_case(binary, records, cells, bindings, outer_bindings, outer, args, case) for case in cases()]
    bad = rejections(binary, records, cells, bindings, outer_bindings, outer, args)
    report = dict(schemaVersion=1, task='F-03d3f4a', inputSHA256=HASHES, inputContracts=EVIDENCE_SHA,
        declarations=declared, fixtures=rows, rejections=bad,
        semanticEvidence=dict(typeIndex=48178, tag=4, clientField='DFENDOGMEAD', objectOffset=0xC8,
            rawType='uint32', meaning='Avatar life-state application input', status='proposed-for-independent-review',
            sources=['Actual 15184e1e4/1fd typed application call into enum57376 parameter',
                     'Model+0x2cc has same enum type; actual setter constructs AvatarLifeStateChanged371',
                     'Actual entity branches construct EvtKill75 / EvtRevive82 before delivery boundary']),
        producer=dict(wireRequired=False, businessRequiredForEntry='not-established',
            observedOmission='Absent/default0 preserves controlled prior state and constructs RefreshAvatarData16',
            candidateUse='After independent review, explicit LIFE_ALIVE1/LIFE_DEAD2 can represent known stored state in an isolated producer',
            notApproved=['Full entry minimum, default model state or sending LIFE_REVIVE3 as persistent state',
                         'Unknown values as safe fallback, FightProp consistency or completed death/revive effects']),
        completeAvatarHandler=False, completeEntityEffects=False, ifixExecutionVerified=False,
        runtimeStatus='not-tested', targetProtocolReady=False,
        models=['Inherited fixed f1 allocation, Avatar batch delivery, encoded identity and resource fixtures',
                'Scene dictionary index lookup and entity/list storage are explicit controlled inputs; actual scene lookup and fallback scan execute',
                'Concrete entity28174 virtual slot98 points to native148d74b00; deeper virtual effect stops before148d74b38',
                'Generic pool is an explicit fresh managed-object allocation model bound to actual method-spec event types15410/21751',
                'Actual EvtKill/EvtRevive objects stop before14e4e1000 delivery; no subscriber or effect is reported complete',
                'Actual AvatarLifeStateChanged/RefreshAvatarData construction is captured at the existing event bus boundary',
                '15184e1e4..15184e202 is an exact application call block with rdi=model/rbx=decoded AvatarInfo live-ins',
                'Model dictionary+0x80 is null in these fixtures; its nonempty downstream meaning and initialization are not inferred',
                'IFix enabled branches stop before dereferencing unknown patch state; no wrapper is modeled successful'])
    root = Path(__file__).resolve().parents[1]
    report['sourceSHA256'] = {name: hashlib.sha256((root / name).read_bytes()).hexdigest() for name in (
        'tools/client72_avatar_state.py', 'tools/client72_avatar_contract.py', 'tools/client72_emulator.py',
        'tools/client72_session_runtime.py')}
    for path, data in zip(outputs, (report, compact(report))):
        path.parent.mkdir(parents=True, exist_ok=True); path.write_text(json.dumps(data, indent=2) + '\n', 'utf8')
    print(json.dumps(dict(fixtures=len(rows), rejections=len(bad), semanticFields=1,
                         completeEntityEffects=False, targetProtocolReady=False)))


class StateRuntime(AvatarRuntime):
    def __init__(self, engine, graph):
        self.state_events = []; self.state_inputs = []; self.scene_queries = []; self.state_writes = []
        super().__init__(engine, graph)
        for entry in NATIVE:
            engine.allow(entry)
        for entry in NATIVE - {0x15184E100}: engine.models.pop(entry, None)
        self.state_hook = engine.uc.hook_add(engine.U.UC_HOOK_MEM_WRITE, self.observe_state_write)
        self.state_model = None
        self.pool_types = {}; self.pooled = {}
        for cell, index in ((0x145A31670, 15410), (0x1459AE430, 21751)):
            token = engine.alloc(); engine.write(cell, token); self.pool_types[token] = index
        engine.models[0x1547A9320] = self.pool_allocate
        self.effect_bus = engine.alloc(); engine.write(GLOBAL + 0xA840, self.effect_bus)

    def pool_allocate(self):
        index = self.pool_types[self.e.reg('rcx')]
        pointer = self.e.alloc(128); self.e.write(pointer, self.e.alloc(1024)); self.pooled[pointer] = index
        return pointer

    def event(self):
        e = self.e; event = e.reg('rdx'); kind = e.read(event, 'I'); boxed = e.read(event + 8)
        if kind == 371:
            self.state_events.append(dict(id=kind, guid=e.read(boxed + 16), state=e.read(boxed + 24, 'I')))
        elif kind == 16:
            self.state_events.append(dict(id=kind, value=e.read(boxed + 16, 'I')))
        else: raise ValueError('Unreviewed state event ' + str(kind))
        return 0

    def observe(self, uc, address, size, data):
        super().observe(uc, address, size, data)
        if address == 0x151849670: self.state_inputs.append(self.e.reg('edx'))
        if address == 0x1517AB3A0: self.scene_queries.append(self.e.reg('edx'))

    def observe_state_write(self, uc, access, address, size, value, data):
        if self.state_model and address == self.state_model + 0x2CC:
            self.state_writes.append(dict(source=hex(self.e.reg('rip')), width=size, value=value))

    def setup_scene(self, presence, entity_id=0x100001, active=False, can_die=False, usable=True):
        e = self.e
        if presence == 'none': return None
        scene = e.alloc(1024); e.write(GLOBAL + 0xA3A0, scene)
        dictionary = e.alloc(); entries = self.array(24); e.write(dictionary + 24, entries); e.write(scene + 0x150, dictionary)
        listing = e.alloc(); e.write(listing + 16, self.array(8)); e.write(scene + 0xC0, listing)
        previous = e.models[0x1578B66F0]
        e.models[0x1578B66F0] = lambda: (0 if presence == 'entity' and e.reg('edx') == entity_id else 0xffffffff) if e.reg('rcx') == dictionary else previous()
        if presence not in ('entity', 'fallback'): return None
        entity = e.alloc(0x600); entity_class = e.alloc(0x500); e.write(entity, entity_class)
        # Metadata class28174 slot98; no inherited/fabricated deeper effect is supplied.
        e.write(entity_class + 0x3E0, 0x148D74B00)
        e.write(entity + 0x80, e.alloc() if usable else 0); e.write(entity + 0x348, entity_id, 'I')
        e.write(entity + 0x4FA, active, 'B'); e.write(entity + 0x426, can_die, 'B')
        e.write(entries + 48, entity)
        if presence == 'fallback':
            e.write(e.read(listing + 16) + 32, entity); e.write(listing + 24, 1, 'I')
        return entity

    def apply(self, model, source):
        self.state_model = model
        return self.invoke(0x15184E1E4, STOPS, rdi=model, rbx=source)

    def close(self):
        self.e.uc.hook_del(self.state_hook); self.e.uc.hook_del(self.observer)


def cases():
    rows = []
    def add(name, values, presence='none', **kw):
        rows.append(dict(name=name, values=values, presence=presence, initial=2, active=False,
                         canDie=False, usable=True, repeat=1, ifix=None, guid=9007199254740993) | kw)
    for name, values in [('none-zero', [0]), ('alive-one', [1]), ('dead-two', [2]), ('revive-three', [3]),
                         ('unknown-seven', [7]), ('unknown-max-u32', [0xffffffff]), ('omitted', []),
                         ('duplicate-last-revive', [2, 1, 3]), ('explicit-zero-last', [1, 0])]: add(name, values)
    add('same-alive-repeated', [1], initial=1, repeat=2, guid=9223372036854775809)
    add('same-dead-repeated', [2], repeat=2, guid=0xffffffffffffffff)
    for value in (1, 2, 3): add('missing-entity-' + str(value), [value], 'missing')
    add('alive-entity-flag-set', [1], 'entity', active=True)
    add('alive-entity-flag-clear', [1], 'entity')
    add('revive-entity-flag-set', [3], 'entity', active=True)
    add('revive-entity-flag-clear', [3], 'entity')
    add('dead-entity-guard-clear', [2], 'entity')
    add('dead-entity-guard-set', [2], 'entity', canDie=True)
    add('entity-unusable', [3], 'entity', usable=False)
    add('fallback-entity', [3], 'fallback', active=True)
    add('zero-guid-state-event', [1], guid=0)
    for flag in IFIX: add('ifix-' + flag, [3], 'entity', ifix=flag)
    return rows


def effect_snapshot(rt):
    e = rt.e; pointer = e.reg('rdx'); index = rt.pooled[pointer]
    result = dict(typeIndex=index, eventId=e.read(pointer + 0x14, 'I'), entityId=e.read(pointer + 0x2C, 'I'),
                  flag1d=e.read(pointer + 0x1D, 'B'), flag1e=e.read(pointer + 0x1E, 'B'),
                  flag1f=e.read(pointer + 0x1F, 'B'), flag29=e.read(pointer + 0x29, 'B'))
    if index == 15410: result.update(parameter30=e.read(pointer + 0x30, 'I'), flag2a=e.read(pointer + 0x2A, 'B'))
    else: result.update(reason40=e.read(pointer + 0x40, 'I'), parameter44=e.read(pointer + 0x44, 'I'), value48=e.read(pointer + 0x48, 'I'))
    return result


def run_case(binary, records, cells, bindings, outer_bindings, outer, args, case):
    e = Offline72(binary, args.python_libs, records, trace_instructions=False)
    g = AvatarContractGraph(e, args.directory, cells, bindings, outer_bindings, outer); rt = None
    try:
        wire = message(6, avatar_wire(1001, case['guid'], [], []) + b''.join(scalar(4, v) for v in case['values']))
        reader = g.run_graph(30509, wire); rt = StateRuntime(e, g); dispatch = rt.root()
        model, source = rt.delivery[0]; identity = rt.identity(model, source)
        e.write(model + 0x218, 0x100001, 'I'); e.write(model + 0x2CC, case['initial'], 'I')
        entity = rt.setup_scene(case['presence'], active=case['active'], can_die=case['canDie'], usable=case['usable'])
        if case['ifix']: e.write(IFIX[case['ifix']][0], 1, 'B')
        value = case['values'][-1] if case['values'] else 0
        if e.read(source + 0xC8, 'I') != value: raise ValueError('Native raw state storage differs')
        steps = []; current = case['initial']
        for _ in range(case['repeat']):
            before_events, before_writes, before_queries = len(rt.state_events), len(rt.state_writes), len(rt.scene_queries)
            trace = rt.apply(model, source)
            stored = e.read(model + 0x2CC, 'I'); events = rt.state_events[before_events:]
            writes = rt.state_writes[before_writes:]; queries = rt.scene_queries[before_queries:]
            live_entity = entity is not None and case['usable']
            expected_state = 1 if value in (1, 3) else 2 if value == 2 else current
            expected_events = [dict(id=371, guid=case['guid'], state=expected_state)] if value in (1, 2, 3) else []
            stop = 0x15184E202; effect = None
            if live_entity and value in (1, 3) and (value == 3 or not case['active']):
                if not case['active']: stop = 0x148D74B38
                else:
                    stop = 0x14E4E1000; expected_events *= 2
                    effect = dict(typeIndex=15410, eventId=82, entityId=0x100001, flag1d=1, flag1e=1,
                                  flag1f=1, flag29=1, parameter30=0, flag2a=1)
            elif live_entity and value == 2 and case['canDie']:
                stop = 0x14E4E1000
                effect = dict(typeIndex=21751, eventId=75, entityId=0x100001, flag1d=1, flag1e=1,
                              flag1f=1, flag29=1, reason40=0, parameter44=0, value48=0)
            if case['ifix']:
                stop = IFIX[case['ifix']][1]; effect = None
                if case['ifix'] in ('state', 'setter'): expected_state = current; expected_events = []
            if stop == 0x15184E202: expected_events.append(dict(id=16, value=65535))
            expected_writes = [x['state'] for x in expected_events if x['id'] == 371]
            actual_effect = effect_snapshot(rt) if trace['stop'] == '0x14e4e1000' else None
            expected_queries = [] if case['presence'] == 'none' or case['ifix'] == 'state' else [0x100001]
            if (stored, events, trace['stop'], [x['value'] for x in writes], actual_effect, queries) != (
                expected_state, expected_events, hex(stop), expected_writes, effect, expected_queries):
                failure = dict(case=case, state=stored, expectedState=expected_state, events=events, expectedEvents=expected_events,
                               writes=writes, actualEffect=actual_effect, expectedEffect=effect, queries=queries, trace=trace)
                args.output.with_suffix('.failure.json').write_text(json.dumps(failure, indent=2) + '\n', 'utf8')
                raise ValueError('Native Avatar state mismatch: ' + case['name'])
            required = {0x151849670}
            if value in (1, 2, 3) and case['ifix'] != 'state': required.add(0x151840630)
            if expected_queries: required.add(0x1517AB3A0)
            if stop in (0x148D74B38, 0x148D74B5E) or effect and effect['eventId'] == 82: required.add(0x148D74B00)
            if not {hex(x) for x in required} <= set(trace['executedAddresses']) or {hex(x) for x in NATIVE} & set(trace['models']):
                raise ValueError('State application native function was missing or modeled')
            steps.append(dict(priorState=current, state=stored, events=events, writes=writes, sceneQueries=queries,
                              effectAtBoundary=actual_effect, entityFlag4fa=e.read(entity + 0x4FA, 'B') if entity else None, trace=trace))
            current = stored
        return dict(case, wire=wire.hex(), reader=reader, rawState=value, dispatch=dispatch, identity=identity, steps=steps)
    finally:
        if rt: rt.close()
        g.close(); e.close()


def rejections(binary, records, cells, bindings, outer_bindings, outer, args):
    e = Offline72(binary, args.python_libs, records, trace_instructions=False)
    g = AvatarContractGraph(e, args.directory, cells, bindings, outer_bindings, outer); rt = None; rows = []
    try:
        for name, index, wire in [('truncated-state-varint', 48178, '2080'), ('state-varint-overflow', 48178, '20ffffffffffffffffff02'),
                                   ('invalid-state-tag', 48178, '00'), ('negative-avatar-length', 30509, '32ffffffff0f')]:
            try: g.run_graph(index, bytes.fromhex(wire))
            except ValueError as error:
                text = str(error); scope = 'native-reader-error-helper' if 'Native error branch:' in text else 'explicit-input-model'
                rows.append(dict(name=name, typeIndex=index, wire=wire, error=text, scope=scope))
            else: raise ValueError('Bad state wire accepted: ' + name)
        g.run_graph(30509, message(6, avatar_wire(1001, 73, [], []) + scalar(4, 1)))
        rt = StateRuntime(e, g); rt.root(); model, source = rt.delivery[0]; rt.identity(model, source)
        rt.setup_scene('missing'); e.write(e.read(GLOBAL + 0xA3A0) + 0x150, 0)
        trace = rt.apply(model, source)
        if trace['stop'] != '0x1404c2be0' or rt.state_writes or rt.state_events:
            raise ValueError('Native malformed scene dictionary did not stop before state change')
        rows.append(dict(name='null-scene-dictionary', scope='native-null-error-helper-boundary', trace=trace))
    finally:
        if rt: rt.close()
        g.close(); e.close()
    return rows


def state_declarations(binary, metadata, startup, records, extraction, libraries):
    pe = PE(binary); h = pe.offset(HEADER_VA); resolver = TypeResolver(pe, startup, records, h)
    names = Names(metadata, int(extraction['stringBase'], 16))
    method_base = BODY + (number(binary, h + 0xB0) ^ 0x51324A1F)
    parameter_base = BODY + ((number(binary, h + 0x174) + 0xAE6BCD68) & 0xffffffff)
    methods = []
    for owner in (18163, 49228, 28174):
        for method in records[owner]['methods']:
            if int(method['registeredAddress'], 16) not in NATIVE: continue
            first = parameter_start(method['index'], number(metadata, method_base + method['index'] * 26 + 4)); parameters = []
            for i in range(first, first + method['parameterCount']):
                name, typ = parameter_words(i, number(metadata, parameter_base + i * 8), number(metadata, parameter_base + i * 8 + 4))
                parameters.append(dict(name=names.decode(name), type=resolver.resolve_index(typ)))
            methods.append(dict(owner=owner, method=method, parameters=parameters))
    for entry in ('0x151849670', '0x151840630'):
        if next(x for x in methods if x['method']['registeredAddress'] == entry)['parameters'][0]['type'].get('typeDefinition') != 57376:
            raise ValueError('Typed life-state parameter changed')
    state_field = next(f for f in records[18163]['fields'] if f['offset'] == 0x2CC)
    if state_field['type'].get('typeDefinition') != 57376: raise ValueError('Life-state model field changed')
    virtual = next(m for m in records[28174]['methods'] if m['virtualSlot'] == 98)
    if virtual['registeredAddress'] != '0x148d74b00': raise ValueError('Entity virtual slot98 changed')
    enum_rows = recover_enums(binary, metadata, records, indices=(57376, 57391, 75464, 74697))['enums']
    enum_rows = [dict(x, values=[v for v in x['values'] if x['typeIndex'] in (57376, 57391) or
        x['typeIndex'] == 75464 and v['value'] in (16, 371) or x['typeIndex'] == 74697 and v['value'] in (75, 82)]) for x in enum_rows]
    symbols = {v['symbol']: v['value'] for x in enum_rows for v in x['values']}
    for key, value in dict(LIFE_NONE=0, LIFE_ALIVE=1, LIFE_DEAD=2, LIFE_REVIVE=3, RefreshAvatarData=16,
                           AvatarLifeStateChanged=371, EvtKill=75, EvtRevive=82).items():
        if symbols.get(key) != value: raise ValueError('Named life-state/event declaration changed')
    lit = Literals72(binary, metadata); cache = number(binary, pe.offset(USAGE_REGISTRATION) + 0x10, 'Q')
    spec_base = BODY + (number(binary, h + 0x170) ^ 0x6347382B); pools = []
    aot_base = BODY + (number(binary, h + 0xEC) ^ 0x3CF0720C)
    aot_size = (number(binary, h + 0x110) + 0xA3D9CC8B) & 0xffffffff
    cr = pe.offset(CODE_REGISTRATION); pointers = number(binary, cr + 0x30, 'Q'); pool_aot = {}
    for i in range(aot_size // 14):
        pointer_index, spec = struct.unpack_from('<ii', metadata, aot_base + i * 14)
        if spec in (32634, 78152):
            address = number(binary, pe.offset(pointers + pointer_index * 8), 'Q')
            if address != 0x1547A9320 or spec in pool_aot: raise ValueError('State pool concrete AOT changed')
            pool_aot[spec] = dict(tableIndex=i, pointerIndex=pointer_index, address=hex(address))
    for usage, cell, expected_spec, expected_type in ((740636, 0x1459AE430, 32634, 21751), (754018, 0x145A31670, 78152, 15410)):
        key = (usage * 0x57C15286 + 0x26DDEBDA) & 0xffffffff
        source = ((number(metadata, lit.usages_base + usage * 8) + 0xABBDCABF) & 0xffffffff) ^ key ^ 0x2A357D90
        slot = number(metadata, lit.usages_base + usage * 8 + 4) ^ key ^ 0x1855AA65
        if source != (6 << 29 | expected_spec) or cache + slot * 8 != cell: raise ValueError('State pool method-usage changed')
        method, cls, args = struct.unpack_from('<iii', metadata, spec_base + expected_spec * 12)
        n, pointer = struct.unpack_from('<QQ', binary, pe.offset(resolver.instances + args * 16))
        types = [resolver.resolve_index(resolver.pointer_index(number(binary, pe.offset(pointer + i * 8), 'Q'))) for i in range(n)]
        if method != 645855 or cls != -1 or len(types) != 1 or types[0].get('typeDefinition') != expected_type:
            raise ValueError('Concrete state event pool type changed')
        pools.append(dict(usageRow=usage, cell=hex(cell), methodSpec=expected_spec, methodDefinition=method, owner=77908,
                          classInstance=cls, methodArguments=args, arguments=types, aot=pool_aot[expected_spec]))
    load_emulator(libraries)
    from capstone import Cs, CS_ARCH_X86, CS_MODE_64
    decoder = Cs(CS_ARCH_X86, CS_MODE_64); sites = {}
    for address, expected in {
        0x15184E1E4: ('mov', 'edx, dword ptr [rbx + 0xc8]'), 0x15184E1FD: ('call', '0x151849670'),
        0x15184064B: ('mov', 'dword ptr [rdx + 0x2cc], r8d'),
        0x1518406A5: ('mov', 'dword ptr [rsp + 0x20], 0x173'),
        0x151849A49: ('mov', 'dword ptr [rsp + 0x50], 0x10'),
        0x151849777: ('mov', 'dword ptr [rsi + 0x14], 0x52'),
        0x151849985: ('mov', 'dword ptr [rsi + 0x14], 0x4b'),
        0x15184973C: ('call', '0x1547a9320'), 0x15184992D: ('call', '0x1547a9320'),
        0x148D74B20: ('mov', 'byte ptr [rsi + 0x4fa], 1'),
        0x148D74B38: ('call', 'qword ptr [rax + 0x4b8]')}.items():
        ins = next(decoder.disasm(binary[pe.offset(address):pe.offset(address) + 15], address))
        if (ins.mnemonic, ins.op_str) != expected: raise ValueError('State native site changed: ' + hex(address))
        sites[hex(address)] = dict(instruction=ins.mnemonic + ' ' + ins.op_str, instructionSHA256=hashlib.sha256(ins.bytes).hexdigest())
    return dict(methods=methods, stateField=state_field, entityVirtual=virtual, enums=enum_rows, pools=pools,
        eventFields={str(i): records[i]['fields'] for i in (15410, 21751, 58582)},
        sceneFields=[f for f in records[49228]['fields'] if f['offset'] in (0x150, 0xC0)], nativeSites=sites)


def compact(value):
    if isinstance(value, list): return [compact(x) for x in value]
    if isinstance(value, dict):
        if 'executedAddresses' in value:
            return {k: value[k] for k in ('entry', 'stop', 'instructions', 'nativePathSHA256', 'models')} | dict(
                nativeEntries=sorted(({hex(x) for x in NATIVE} | {value['entry'], value['stop']}) & set(value['executedAddresses'])))
        if 'reader' in value: value = dict(value, readerResult=value['reader']['result'])
        return {k: compact(v) for k, v in value.items() if k != 'reader'}
    return value


if __name__ == '__main__':
    main()
