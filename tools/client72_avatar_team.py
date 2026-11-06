"""Bounded CN72 Avatar Team-map/current-Team semantic consumers.

Native notification prefix, Team writers/getters and named-event construction
execute with declared managed collection and event-delivery fixtures.
"""
import argparse
import hashlib
import json
import struct
from pathlib import Path
try:
    from .extract_client72 import HASHES, PE, read_verified, extract
    from .extract_client72_enums import recover as recover_enums
    from .trace_client72_avatar_core import core_aot
    from .trace_client72_avatar_outer import avatar_aot, AvatarOuter72
    from .trace_client72_player_store import type_cells
    from .trace_client72_avatar_signed_trees import selected_aot
    from .client72_avatar_contract import AvatarContractGraph, AvatarRuntime, avatar_wire, declarations, RETURN
    from .client72_graph import GraphReader72
    from .client72_graph_fixtures import scalar, message
    from .trace_client72_token import varint, load_emulator
    from .client72_emulator import Offline72, GLOBAL, OBJECT
except ImportError:
    from extract_client72 import HASHES, PE, read_verified, extract
    from extract_client72_enums import recover as recover_enums
    from trace_client72_avatar_core import core_aot
    from trace_client72_avatar_outer import avatar_aot, AvatarOuter72
    from trace_client72_player_store import type_cells
    from trace_client72_avatar_signed_trees import selected_aot
    from client72_avatar_contract import AvatarContractGraph, AvatarRuntime, avatar_wire, declarations, RETURN
    from client72_graph import GraphReader72
    from client72_graph_fixtures import scalar, message
    from trace_client72_token import varint, load_emulator
    from client72_emulator import Offline72, GLOBAL, OBJECT

EVIDENCE_SHA = {'avatar-complete-raw-observations.json': '37c90e4dea9fccc4ac20ce68616612250b5a57169e564081c33dc69a25def837',
    'avatar-contract-observations.json': 'a54f62de9a803e2070c515ecc854488827ea6c62db4468c633190d6733f2b2f3',
    'avatar-control-observations.json': '8e2433b3d13ec17414cc26f21461f1cc62f64b16c413b327610ce0b7e7480ec0'}
NATIVE = {0x1507CC240, 0x1507CEE50, 0x151A9D430, 0x151A9AE90, 0x151A9EA20, 0x151A9EC40,
    0x151A9BD20, 0x151A9B850, 0x151A9D910, 0x151A9E080, 0x151A9C360, 0x157EE1FE0,
    0x14E9B2A80, 0x14E9B2C60, 0x156F6F510}


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
    for name, digest in EVIDENCE_SHA.items():
        if hashlib.sha256((args.directory / name).read_bytes()).hexdigest() != digest:
            raise ValueError('Reviewed Avatar evidence changed: ' + name)
    binary, metadata, startup = [read_verified(args.client / name, digest) for name, digest in HASHES.items()]
    records, _ = extract(binary, metadata, startup=startup, structures=True)
    declarations(binary, metadata, startup, records, args.directory)
    cells = type_cells(binary, metadata, startup, records)
    bindings = core_aot(binary, metadata, startup, records)
    outer_bindings = avatar_aot(binary, metadata, startup, records, dict(types=[]))
    outer = json.loads((args.directory / 'avatar-outer-observations.json').read_text('utf8'))
    declared = team_declarations(binary, metadata, startup, records, cells, args.python_libs)
    rows = [run_case(binary, records, cells, bindings, outer_bindings, outer, args, case) for case in cases()]
    bad = rejections(binary, records, cells, bindings, outer_bindings, outer, args)
    report = dict(schemaVersion=1, task='F-03d3f3', inputSHA256=HASHES, inputContracts=EVIDENCE_SHA,
        declarations=declared, fixtures=rows, rejections=bad,
        semanticEvidence=semantic_evidence(), completeAvatarHandler=False,
        root3CurrentControlGuidApproved=False, persistenceVerified=False,
        runtimeStatus='not-tested', targetProtocolReady=False,
        models=['Inherited fixed F-03d3f1 managed class, Avatar construction/delivery and resource fixtures',
                'Managed Team u32 dictionary index/insert and IEnumerator storage are explicit models over native-decoded input',
                'List<uint64> object/array allocation and Array.Copy are modeled; capacity decision, dedup and qword append execute natively',
                'Empty fallback/override/derived lists and an initially absent selected Team are controlled entry state',
                'Events are constructed natively and captured at event bus delivery; subscribers/effects are not executed',
                'Name string decoding is the existing explicit UTF-8/managed-string model; native code routes its pointer',
                'Root prefix stops before Rename processing; tag12 assignment is a separate exact block with r15=root live-in',
                'Derived member consumer stops before encoded Avatar model+0x2b8; no resource feature or scene effect is stubbed successful'])
    root = Path(__file__).resolve().parents[1]
    report['sourceSHA256'] = {name: hashlib.sha256((root / name).read_bytes()).hexdigest() for name in (
        'tools/client72_avatar_team.py', 'tools/client72_avatar_contract.py', 'tools/client72_emulator.py',
        'tools/client72_session_runtime.py', 'tools/trace_client72_avatar_outer.py')}
    for path, data in zip(outputs, (report, compact(report))):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(data, indent=2) + '\n', encoding='utf8')
    print(json.dumps(dict(fixtures=len(rows), rejections=len(bad), proposedSemanticFields=4,
                         completeAvatarHandler=False, targetProtocolReady=False)))


class AvatarTeamGraph(AvatarContractGraph):
    def blob(self, kind):
        return AvatarOuter72.blob(self, kind) if kind == 'string' else GraphReader72.blob(self, kind)


class TeamRuntime(AvatarRuntime):
    def __init__(self, engine, graph, initial_team=0):
        self.team_events = []; self.team_lookups = []; self.member_stores = []; self.index_checks = []; self.capture_team = False
        super().__init__(engine, graph); e = self.e
        self.team_manager = e.alloc(512); e.write(GLOBAL + 0xA420, self.team_manager)
        self.team_dictionaries = {}; self.team_arrays = set(); self.team_lists = set(); self.array_allocations = []
        self.lookup_fallback = e.models[0x1578B66F0]; self.insert_fallback = e.models[0x1578B6A20]
        e.models[0x1578B66F0] = self.team_lookup; e.models[0x1578B6A20] = self.team_insert
        for offset in (0x50, 0x78): e.write(self.team_manager + offset, self.new_dictionary())
        for offset in (0x80, 0x88, 0xB8, 0xE0): e.write(self.team_manager + offset, self.new_list(4 if offset == 0xB8 else 8))
        e.write(self.team_manager + 0x124, initial_team, 'I')
        self.list_class = e.synthetic_type(0x14588F388); self.array_class = e.synthetic_type(0x145887760)
        e.write(0x145B74648, graph.new_array(8, 0))
        self.allocation_fallback = e.models[0x1405336C0]; e.models[0x1405336C0] = self.team_allocate
        e.models[0x140514EE0] = self.team_array_allocate
        for cell in (0x1459DB270, 0x145990590):
            info, klass, context = e.alloc(), e.alloc(), e.alloc()
            e.write(info, klass); e.write(klass + 0x30, context); e.write(context + 0x88, e.alloc()); e.write(cell, info)
        # Same native map node tuples; only the concrete IEnumerator<KVP<u32,26081>> interface changes.
        enum_class = e.read(self.enumerator); table = e.read(enum_class + 0x80)
        e.write(table + 16, e.synthetic_type(0x1458EBED0))
        for pointer, index in graph.objects.items():
            if index == 26081:
                listing = e.read(pointer + 24); klass = e.alloc(1024); e.write(listing, klass)
                e.write(klass + 0x230, e.model_stub(self.enumerate_members))
        for entry in NATIVE:
            if entry in (0x157EE1FE0, 0x156F6F510): e.allow_unwind(entry)
            else: e.allow(entry)
            e.models.pop(entry, None)
        # The existing Avatar construction delivery remains a declared boundary.
        e.models[0x15184E100] = self.deliver
        e.write(0x14589BC70, self.klass)
        self.capture_team = True
        self.store_hook = e.uc.hook_add(e.U.UC_HOOK_MEM_WRITE, self.observe_store)

    def new_dictionary(self):
        pointer = self.e.alloc(); entries = self.g.new_array(24)
        self.e.write(pointer + 24, entries); self.team_dictionaries[pointer] = dict(entries=entries, positions={})
        return pointer

    def new_list(self, width=8):
        pointer = self.e.alloc(); array = self.g.new_array(width)
        self.e.write(pointer + 16, array); self.team_arrays.add(array); self.team_lists.add(pointer)
        return pointer

    def team_lookup(self):
        pointer = self.e.reg('rcx')
        if pointer in self.team_dictionaries: return self.team_dictionaries[pointer]['positions'].get(self.e.reg('edx'), 0xffffffff)
        return self.lookup_fallback()

    def team_insert(self):
        e = self.e; pointer = e.reg('rcx')
        if pointer not in self.team_dictionaries: return self.insert_fallback()
        dictionary = self.team_dictionaries[pointer]; key = e.reg('edx')
        position = dictionary['positions'].setdefault(key, len(dictionary['positions']))
        if position >= 64: raise ValueError('Team dictionary fixture capacity exceeded')
        e.write(dictionary['entries'] + 40 + position * 24, key, 'I')
        e.write(dictionary['entries'] + 48 + position * 24, e.reg('r8'))
        e.write(pointer + 0x38, len(dictionary['positions']), 'I'); return 1

    def team_allocate(self):
        if self.e.reg('rcx') != self.list_class: return self.allocation_fallback()
        pointer = self.e.alloc(); self.e.write(pointer, self.list_class); self.team_lists.add(pointer); return pointer

    def team_array_allocate(self):
        e = self.e; capacity = e.reg('edx')
        if e.reg('rcx') != self.array_class or not 0 < capacity <= 64: raise ValueError('Unmodeled Team managed array allocation')
        array = self.g.new_array(8, capacity); self.team_arrays.add(array); self.array_allocations.append(capacity); return array

    def enumerate_members(self):
        e = self.e; target, listing = e.reg('rcx'), e.reg('rdx')
        e.write(target, 1, 'B'); e.write(target + 8, e.read(listing + 16)); e.write(target + 16, e.read(listing + 24, 'I'), 'I'); e.write(target + 20, 0xffffffff, 'I')
        return target

    def event(self):
        e = self.e; event = e.reg('rdx'); payload = e.read(event + 8)
        self.team_events.append(dict(id=e.read(event, 'I'), boxedBoolean=bool(e.read(payload + 16, 'B')) if payload else None)); return 0

    def observe(self, uc, address, size, data):
        super().observe(uc, address, size, data)
        if self.capture_team and address == 0x14E9B2C60: self.team_lookups.append(self.e.reg('rdx'))
        if self.capture_team and address == 0x14E9B2AB0: self.index_checks.append(self.e.reg('rax'))

    def observe_store(self, uc, access, address, size, value, data):
        if self.e.reg('rip') not in (0x151A9EB63, 0x157EE208A): return
        self.member_stores.append(dict(source=hex(self.e.reg('rip')), width=size, guid=value & ((1 << 64) - 1)))

    def read_list(self, pointer):
        return [self.e.read(self.e.read(pointer + 16) + 32 + i * 8) for i in range(self.e.read(pointer + 24, 'I'))]

    def snapshot_teams(self):
        e = self.e; members = self.team_dictionaries[e.read(self.team_manager + 0x50)]; names = self.team_dictionaries[e.read(self.team_manager + 0x78)]
        result = {}
        for key, position in members['positions'].items():
            listing = e.read(members['entries'] + 48 + position * 24); row = dict(guids=self.read_list(listing))
            if key in names['positions']:
                string = e.read(names['entries'] + 48 + names['positions'][key] * 24); row['name'] = self.g.values[string].get('text', '')
            result[str(key)] = row
        return result

    def notification(self):
        packet = self.e.alloc(); self.e.write(packet + 16, OBJECT); self.e.write(packet + 0x22, 20143, 'H')
        return self.invoke(0x1507CC240, {0x1507CF4A1, 0x1404C2BE0}, rcx=self.network, rdx=packet)

    def selection_field(self):
        return self.invoke(0x1507CF606, {0x1507CF627, 0x1404C2BE0}, r15=OBJECT)

    def close(self):
        self.e.uc.hook_del(self.store_hook); self.e.uc.hook_del(self.observer)


def team_wire(teams, current, hero, avatars, mixed=False):
    wire = scalar(3, hero) + scalar(12, current)
    for team_id, name, guids in teams:
        packed = guids[:1] if mixed else guids
        child = message(2, name.encode('utf8')) + message(12, b''.join(varint(guid) for guid in packed))
        if mixed: child += b''.join(scalar(12, guid) for guid in guids[1:])
        wire += message(5, scalar(1, team_id) + message(2, child))
    return wire + b''.join(message(6, avatar_wire(1001 + i, guid, [], [])) for i, guid in enumerate(avatars))


def cases():
    a, b = 9007199254740993, 9223372036854775809
    rows = [
        ('two-teams-high-guids', [(1, 'Alpha', [a, b, a]), (2, 'Beta', [b])], 2, a, [a, b], [2, 1, 1, 7, 2]),
        ('same-low32-distinct-guids', [(3, 'One', [0x100000001]), (4, 'Two', [0x200000001])], 4, 0x100000001, [0x100000001, 0x200000001], [3, 4]),
        ('max-team-and-member', [(0xffffffff, 'Max', [0xffffffffffffffff]), (2, 'B', [b])], 0xffffffff, b, [0xffffffffffffffff, b], [2, 0xffffffff]),
        ('zero-team-and-member', [(0, 'Zero', [0, 0]), (1, 'Nonzero', [a])], 0, a, [0, a], [1, 0]),
        ('missing-current-team', [(1, 'A', [a]), (2, 'B', [b])], 7, b, [a, b], [1, 7]),
        ('missing-members-before-valid', [(1, 'Missing then valid', [999, 0, b]), (2, 'A', [a])], 1, a, [a, b], [2, 1]),
        ('no-owned-avatar-members', [(1, 'Unresolved', [a, b]), (2, 'Other', [7])], 1, a, [], [2, 1]),
        ('empty-team-and-unicode-name', [(1, '', []), (2, '\u961f\u4f0d\0\U0001f680', [a, b])], 1, b, [a, b], [2, 1]),
        ('duplicate-team-key-replaces', [(1, 'Old', [a]), (2, 'B', [b]), (1, 'New', [b, b])], 1, a, [a, b], [2, 1]),
        ('packed-unpacked-members', [(1, 'Mixed', [a, 0, b, a]), (2, 'B', [b])], 1, b, [a, b], [2, 1]),
        ('explicit-zero-overwrites', [(0, 'Zero', [a]), (2, 'B', [b])], 0, 0, [a, b], [2, 0]),
        ('managed-list-growth-nine', [(1, 'Nine', [0x100000000 + i for i in range(9)]), (2, 'B', [b])], 1, a, [], [2, 1]),
        ('managed-list-capacity-64', [(1, 'Sixty four', [0x100000000 + i for i in range(64)]), (2, 'B', [b])], 1, a, [], [2, 1])]
    return [dict(name=name, teams=teams, current=current, hero=hero, avatars=avatars, switches=switches,
                 mixed=name == 'packed-unpacked-members') for name, teams, current, hero, avatars, switches in rows]


def run_case(binary, records, cells, bindings, outer_bindings, outer, args, case):
    e = Offline72(binary, args.python_libs, records, trace_instructions=False)
    g = AvatarTeamGraph(e, args.directory, cells, bindings, outer_bindings, outer); rt = None
    try:
        wire = team_wire(case['teams'], case['current'], case['hero'], case['avatars'], case['mixed'])
        if case['name'] == 'explicit-zero-overwrites': wire = scalar(3, 999) + scalar(12, 9) + wire
        reader = g.run_graph(30509, wire)
        expected = {str(key): dict(name=name, guids=list(dict.fromkeys(guids))) for key, name, guids in case['teams']}
        # Start with a distinct selected key so the native append tail follows its actual empty-current-list branch.
        initial = next(key for key in range(0x10000, 0x10040) if str(key) not in expected)
        rt = TeamRuntime(e, g, initial)
        dispatch = rt.notification(); actual = rt.snapshot_teams()
        if dispatch['stop'] != '0x1507cf4a1' or actual != expected:
            raise ValueError('Native notification Team map differs from independently authored values')
        identities = [rt.identity(model, source) for model, source in rt.delivery]
        prefix_events = list(rt.team_events)
        if prefix_events != [dict(id=363, boxedBoolean=False)] * len(expected):
            raise ValueError('Native Team name events differ')
        if [x['guid'] for x in rt.member_stores] != [v for row in expected.values() for v in row['guids']]:
            raise ValueError('Native append stores differ from expected per-Team deduplication')
        if e.read(rt.model + 0x128) != case['hero']:
            raise ValueError('Independent root Hero storage changed')
        selection = rt.selection_field()
        if selection['stop'] != '0x1507cf627' or e.read(rt.team_manager + 0x124, 'I') != case['current'] or rt.team_events != prefix_events:
            raise ValueError('Root current-Team assignment or event-free boundary changed')
        queries = []
        for team_id in list(dict.fromkeys([case['current'], *case['switches']])):
            exists = rt.invoke(0x151A9D910, rcx=rt.team_manager, rdx=team_id); present = bool(e.reg('al'))
            name_trace = rt.invoke(0x151A9B850, rcx=rt.team_manager, rdx=team_id); pointer = e.reg('rax')
            name = g.values[pointer]['text'] if pointer else None
            if present != (str(team_id) in expected) or name != expected.get(str(team_id), {}).get('name'):
                raise ValueError('Native Team existence/name lookup differs')
            queries.append(dict(teamId=team_id, present=present, name=name, exists=exists, nameGetter=name_trace))
        switches = []; current = case['current']
        for team_id in [current, *case['switches']]:
            event_count = len(rt.team_events)
            change = rt.invoke(0x151A9E080, rcx=rt.team_manager, rdx=team_id)
            emitted = rt.team_events[event_count:]
            if emitted != ([] if team_id == current else [dict(id=364, boxedBoolean=None)]):
                raise ValueError('Native CurrentTeamChanged event or idempotence differs')
            getter = rt.invoke(0x151A9BD20, rcx=rt.team_manager); actual_members = rt.read_list(e.reg('rax'))
            expected_members = expected.get(str(team_id), {}).get('guids', [])
            if actual_members != expected_members or e.read(rt.team_manager + 0x124, 'I') != team_id:
                raise ValueError('Current Team list differs')
            before = len(rt.team_lookups)
            consumer = rt.invoke(0x151A9EC40, {0x151A9ED62, RETURN}, rcx=rt.team_manager)
            expected_lookups = []; found = None
            for guid in expected_members:
                expected_lookups.append(guid)
                # Native 14e9b2aad/ab0 skips zero during Avatar dictionary registration.
                if guid and guid in case['avatars']: found = guid; break
            if rt.team_lookups[before:] != expected_lookups or consumer['stop'] != hex(0x151A9ED62 if found is not None else RETURN):
                failure = dict(case=case['name'], team=team_id, expectedLookups=expected_lookups,
                               actualLookups=rt.team_lookups[before:], expectedMatch=found, trace=consumer)
                args.output.with_suffix('.failure.json').write_text(json.dumps(failure, indent=2) + '\n', 'utf8')
                raise ValueError('Native current-Team member GUID consumer differs: ' + case['name'] + ' Team ' + str(team_id))
            switches.append(dict(fromTeam=current, toTeam=team_id, members=actual_members, events=emitted,
                                 lookedUpGuids=expected_lookups, matchedGuid=found, change=change, getter=getter, consumer=consumer))
            current = team_id
        traces = [dispatch, selection, *[x[k] for x in queries for k in ('exists', 'nameGetter')],
                  *[x[k] for x in switches for k in ('change', 'getter', 'consumer')]]
        for trace in traces:
            if {hex(x) for x in NATIVE} & set(trace['models']): raise ValueError('Team consumer replaced by model')
        required = {0x1507CC240, 0x1507CEE50, 0x151A9D430, 0x151A9AE90}
        if any(x['guids'] for x in expected.values()): required |= {0x151A9EA20, 0x157EE1FE0}
        if not {hex(x) for x in required} <= set(dispatch['executedAddresses']): raise ValueError('Required Team bodies not executed')
        return dict(case, wire=wire.hex(), reader=reader, expectedTeams=expected, actualTeams=actual,
                    initialTeam=initial, rootHeroGuid=case['hero'], dispatch=dispatch, identities=identities,
                    rootSelection=selection, prefixEvents=prefix_events, queries=queries, switches=switches,
                    memberStores=rt.member_stores, arrayAllocations=rt.array_allocations, avatarIndexChecks=rt.index_checks)
    finally:
        if rt: rt.close()
        g.close(); e.close()


def rejections(binary, records, cells, bindings, outer_bindings, outer, args):
    e = Offline72(binary, args.python_libs, records, trace_instructions=False)
    g = AvatarTeamGraph(e, args.directory, cells, bindings, outer_bindings, outer); rt = None; rows = []
    try:
        specs = [('negative-team-map-length', 30509, '2affffffff0f', 100),
                 ('truncated-team-map', 30509, '2a030801', 100),
                 ('invalid-tag', 26081, '00', 100),
                 ('member-guid-overflow', 26081, '60ffffffffffffffffff02', 100),
                 ('truncated-packed-member', 26081, '620280', 100),
                 ('negative-name-length', 26081, '12ffffffff0f', 100),
                 ('team-recursion-limit', 30509, '2a0412021200', 0),
                 ('member-capacity-65', 26081, message(12, b''.join(varint(i) for i in range(65))).hex(), 100)]
        for name, index, wire, limit in specs:
            try: g.run_graph(index, bytes.fromhex(wire), limit)
            except ValueError as error:
                text = str(error)
                scope = 'native-reader-error-helper' if 'Native error branch:' in text else 'explicit-input-model-or-capacity'
                rows.append(dict(name=name, typeIndex=index, wire=wire, recursionLimit=limit, error=text, scope=scope))
            else: raise ValueError('Invalid/out-of-fixture Team input accepted: ' + name)
        g.run_graph(30509, b''); rt = TeamRuntime(e, g)
        null = rt.invoke(0x1507CEE50, {0x1404C2BE0}, rcx=rt.network, rdx=0)
        rows.append(dict(name='null-typed-input', scope='native-null-error-helper-boundary', trace=null))
    finally:
        if rt: rt.close()
        g.close(); e.close()
    return rows


def team_declarations(binary, metadata, startup, records, cells, libraries):
    methods = [dict(owner=39488, **m) for m in records[39488]['methods'] if int(m['registeredAddress'], 16) in NATIVE]
    fields = [f for f in records[39488]['fields'] if f['offset'] in (0x50, 0x78, 0x80, 0x88, 0xB8, 0xE0, 0x124)]
    by_offset = {f['offset']: f['type'] for f in fields}
    if (by_offset[0x50]['arguments'][0]['kind'] != 'uint32' or
        by_offset[0x50]['arguments'][1]['arguments'][0]['kind'] != 'uint64' or
        [x['kind'] for x in by_offset[0x78]['arguments']] != ['uint32', 'string'] or
        by_offset[0x124]['kind'] != 'uint32'):
        raise ValueError('Team manager concrete field types changed')
    enums = recover_enums(binary, metadata, records, indices=(75464,))['enums']
    enum = dict(enums[0], values=[v for v in enums[0]['values'] if v['symbol'] in ('TeamNameRefresh', 'CurrentTeamChanged', 'AvatarRenameInfoReady')])
    if {v['symbol']: v['value'] for v in enum['values']} != dict(TeamNameRefresh=363, CurrentTeamChanged=364, AvatarRenameInfoReady=29):
        raise ValueError('Named Team events changed')
    aot = selected_aot(binary, metadata, startup, records, {(1255, 58): ('.ctor', '.cctor', 'Add', 'AddWithResize', 'GetEnumerator')})
    if not any(x['method'] == 'AddWithResize' and x['address'] == '0x157ee1fe0' and x['arguments'][0]['kind'] == 'uint64' for x in aot):
        raise ValueError('Concrete uint64 List growth AOT changed')
    load_emulator(libraries)
    from capstone import Cs, CS_ARCH_X86, CS_MODE_64
    pe = PE(binary); decoder = Cs(CS_ARCH_X86, CS_MODE_64)
    sites = {}
    for address, expected in {
        0x1507CF085: ('mov', 'rcx, qword ptr [rax + 0x28]'),
        0x1507CF203: ('mov', 'r8, qword ptr [rsi + 0x20]'),
        0x1507CF20A: ('call', '0x151a9d430'),
        0x1507CF20F: ('mov', 'rdx, qword ptr [rsi + 0x18]'),
        0x1507CF305: ('mov', 'r8, qword ptr [rax]'),
        0x1507CF30B: ('call', '0x151a9ea20'),
        0x1507CF61D: ('mov', 'edx, dword ptr [r15 + 0x60]'),
        0x1507CF621: ('mov', 'dword ptr [rcx + 0x124], edx'),
        0x151A9D51C: ('mov', 'dword ptr [rsp + 0x30], 0x16b'),
        0x151A9E0B0: ('mov', 'dword ptr [rdx + 0x124], r8d'),
        0x151A9E0F0: ('mov', 'dword ptr [rsp + 0x20], 0x16c'),
        0x151A9EB63: ('mov', 'qword ptr [rax + rdx*8 + 0x20], rdi'),
        0x157EE208A: ('mov', 'qword ptr [rbx + r15*8 + 0x20], r14'),
        0x151A9ED49: ('call', '0x14e9b2c60'),
        0x14E9B2AAD: ('test', 'rax, rax'),
        0x14E9B2AB0: ('je', '0x14e9b2afc'),
        0x151A9ED62: ('movups', 'xmm0, xmmword ptr [rbp + 0x2b8]')}.items():
        ins = next(decoder.disasm(binary[pe.offset(address):pe.offset(address) + 15], address))
        if (ins.mnemonic, ins.op_str) != expected: raise ValueError('Team native site changed: ' + hex(address))
        sites[hex(address)] = dict(instruction=ins.mnemonic + ' ' + ins.op_str, instructionSHA256=hashlib.sha256(ins.bytes).hexdigest())
    return dict(managerType=39488, fields=fields, methods=methods, namedEvent=enum, uint64ListAOT=aot,
                classUsages=[cells[x] for x in (0x14588F388, 0x145887760, 0x1458EBED0, 0x14589BC70)], nativeSites=sites)


def semantic_evidence():
    return dict(status='proposed-for-independent-review; no mapping changes',
        fields=[dict(typeIndex=30509, tag=5, meaning='Team ID to Team description map',
                     evidence='native root map key routes both TeamNameRefresh writer and member GUID writer'),
                dict(typeIndex=26081, tag=2, meaning='Team name',
                     evidence='native writer stores under the same key and constructs named TeamNameRefresh=363'),
                dict(typeIndex=26081, tag=12, meaning='ordered Team member Avatar GUID list',
                     evidence='native u64 append/dedup; current-Team consumer looks up values in actual Avatar GUID dictionary'),
                dict(typeIndex=30509, tag=12, meaning='current Team ID',
                     evidence='root assignment reaches same +0x124 used by selected-list getter and named CurrentTeamChanged=364 setter')],
        root3='independent Hero GUID source remains F-03d3f2a; no equality with current Team members or active scene entity proved',
        remainingRootTags=[4, 9, 11, 13, 14, 15],
        unresolved=['Saved preset/persistence backend and meaning of root other lists',
                    'Whether Hero must belong to current Team, server selection rules, and active entity relationship',
                    'Override +0x88 and fallback +0x80 nonempty business origins',
                    'Encoded Avatar +0x2b8 derived feature/resource path and event subscribers',
                    'Repeated notification existing-Team merge semantics beyond an initially empty manager'])


def compact(value):
    if isinstance(value, list): return [compact(x) for x in value]
    if isinstance(value, dict):
        if 'executedAddresses' in value:
            return {k: value[k] for k in ('entry', 'stop', 'instructions', 'nativePathSHA256', 'models')} | dict(
                nativeEntries=sorted(({hex(x) for x in NATIVE} | {value['entry'], value['stop']}) & set(value['executedAddresses'])))
        if 'reader' in value:
            value = dict(value, readerResult=value['reader']['result'])
        return {k: compact(v) for k, v in value.items() if k != 'reader'}
    return value


if __name__ == '__main__':
    main()
