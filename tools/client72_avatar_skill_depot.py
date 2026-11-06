"""Bounded CN72 Avatar skill-depot ID and current-Team resource consumers."""
import argparse
import hashlib
import json
import struct
from pathlib import Path
try:
    from .extract_client72 import HASHES, PE, HEADER_VA, BODY, Names, TypeResolver, number, read_verified, extract
    from .extract_client72_signatures import parameter_start, parameter_words
    from .extract_client72_enums import recover as recover_enums
    from .extract_client72_literals import Literals72
    from .trace_client72_token import load_emulator
    from .trace_client72_avatar_core import core_aot
    from .trace_client72_avatar_outer import avatar_aot
    from .trace_client72_player_store import type_cells
    from .client72_avatar_contract import avatar_wire, declarations, RETURN
    from .client72_avatar_team import AvatarTeamGraph, TeamRuntime, team_wire
    from .client72_avatar_fight_props import EVIDENCE_SHA as FIGHT_INPUTS
    from .audit_d02_resources72 import load_tables, COMMIT as RESOURCE_COMMIT, ROOT as RESOURCE_ROOT
    from .client72_graph_fixtures import scalar, message
    from .client72_emulator import Offline72, GLOBAL
except ImportError:
    from extract_client72 import HASHES, PE, HEADER_VA, BODY, Names, TypeResolver, number, read_verified, extract
    from extract_client72_signatures import parameter_start, parameter_words
    from extract_client72_enums import recover as recover_enums
    from extract_client72_literals import Literals72
    from trace_client72_token import load_emulator
    from trace_client72_avatar_core import core_aot
    from trace_client72_avatar_outer import avatar_aot
    from trace_client72_player_store import type_cells
    from client72_avatar_contract import avatar_wire, declarations, RETURN
    from client72_avatar_team import AvatarTeamGraph, TeamRuntime, team_wire
    from client72_avatar_fight_props import EVIDENCE_SHA as FIGHT_INPUTS
    from audit_d02_resources72 import load_tables, COMMIT as RESOURCE_COMMIT, ROOT as RESOURCE_ROOT
    from client72_graph_fixtures import scalar, message
    from client72_emulator import Offline72, GLOBAL

RESOURCE_ENTRIES = {0x14D6D5490, 0x14D6D53A0, 0x14D6D70F0, 0x14EB02D90, 0x14EB02C40, 0x14EB02D30,
                    0x14AEFA090, 0x14AEF9DB0}
NATIVE = RESOURCE_ENTRIES | {0x15184E100, 0x14B1046F0, 0x14B1049F0, 0x151A9EC40,
    0x151A9BD20, 0x151A9E080, 0x14E9B2C60, 0x156F6F510, 0x153217960}
EVIDENCE_SHA = FIGHT_INPUTS | {'avatar-fight-props-observations.json': '66113992295fb759d80c2caa392c7ac8c2e6f78dc9961e6860427f23564d940e'}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ('client', 'output', 'observations'): parser.add_argument('--' + name, type=Path, required=True)
    parser.add_argument('--directory', type=Path, default=Path('protocol/cn-7.2'))
    parser.add_argument('--python-libs', type=Path, default=Path('local/migration72/python'))
    parser.add_argument('--resource-source', type=Path, default=Path('local/migration72/sources/AnimeGameData') / RESOURCE_COMMIT)
    parser.add_argument('--resource-evidence', type=Path, default=Path('local/migration72/evidence'))
    args = parser.parse_args(); outputs = [args.output.resolve(), args.observations.resolve()]
    if len(set(outputs)) != 2 or any(p.is_relative_to(args.client.resolve()) for p in outputs):
        parser.error('Distinct outputs outside the client are required')
    for name, digest in EVIDENCE_SHA.items():
        if hashlib.sha256((args.directory / name).read_bytes()).hexdigest() != digest: raise ValueError('Reviewed/replayed input changed: ' + name)
    binary, metadata, startup = [read_verified(args.client / name, digest) for name, digest in HASHES.items()]
    records, extraction = extract(binary, metadata, startup=startup, structures=True)
    declarations(binary, metadata, startup, records, args.directory)
    cells = type_cells(binary, metadata, startup, records)
    declared = depot_declarations(binary, metadata, startup, records, extraction, cells, args.python_libs)
    bindings = core_aot(binary, metadata, startup, records)
    outer_bindings = avatar_aot(binary, metadata, startup, records, dict(types=[]))
    outer = json.loads((args.directory / 'avatar-outer-observations.json').read_text('utf8'))
    candidate, candidate_case = candidate_projection(args)
    rows = [run_case(binary, records, cells, bindings, outer_bindings, outer, args, case) for case in [*cases(), candidate_case]]
    bad = rejections(binary, records, cells, bindings, outer_bindings, outer, args)
    report = dict(schemaVersion=1, task='F-03d3f4c', inputSHA256=HASHES, inputContracts=EVIDENCE_SHA,
        declarations=declared, candidateSource=candidate, fixtures=rows, rejections=bad,
        semanticEvidence=dict(typeIndex=48178, tag=11, clientField='BLBHOJMCOJN', objectOffset=196, rawType='uint32',
            meaning='AvatarSkillDepotExcelConfigData resource ID', status='proposed-for-independent-review',
            sources=['Native reader/prologue encodes tag11 into model+0x2b8 and native decode preserves all uint32 bits',
                     'Actual current-Team GUID lookup selects model, then resolves the value in the typed depot resource cache',
                     'Target literal/path getter/loader and parser allocation/cache stores bind depot and skill resource classes',
                     'Named Miscs.GetAvatarElementType independently follows default Avatar row to the same depot, skill and enum fields',
                     'Actual depot+0x6c skill-reference decode, skill+0x54 enum read and manager+0xb8 list writes execute']),
        siblingField=dict(owner=22284, name='LHFOOPMJHBN', offset=16, arrayElementType=40302,
            readByTestedTeamConsumer=False, businessSemantic='unknown', specialProudSkillOpensAliasApproved=False),
        producer=dict(businessRequiredForEntry='not-established',
            omittedField='Native identity prologue overwrites controlled prior depot with zero; tested Team consumer skips zero',
            resourceDataOrigin='Twenty-one independently authored synthetic caches plus one pinned501/701 partial scalar projection; no target resource parser or JSON alias is executed',
            notEstablished=['Choice between initial501/701 and candidate504/704 for an actual character',
                            'Original business role of depot HEGEDDGIFLL/+0x6c beyond this skill reference',
                            'Default constructor state, complete resource parser, full Team/Avatar initialization and entry minimum']),
        completeAvatarHandler=False, completeTeamHandler=False, completeDerivedTeamConsumer=True, resourceFileIOExecuted=False,
        runtimeStatus='not-tested', targetProtocolReady=False,
        models=['Fixed f1 Avatar allocation/batch delivery/identity and f3 Team map/current-Team storage boundaries are reused',
                'Native identity prologue, int codecs, current-Team selection, GUID lookup, resource query bodies and Team derived-list function execute',
                'Managed u32 resource dictionary index lookup is a declared model; native code traverses returned rows and reads actual fields',
                'Controlled cache row IDs and depot skill references are initialized through actual58209 encoder; skill enum is controlled resource data',
                'Pinned501/701 rows omit non-ID scalars; logical-zero projection is explicit and does not prove target parser/constructor defaults',
                'Path getter and loader execute only to14d6e1570 before resource I/O; parser provenance is static, not executed deserialization',
                'Team fallback/override lists are empty; derived list has existing64-slot backing and cases use at most two members',
                'IFix guard stops before unknown patch object access; no dynamic wrapper is modeled successful'])
    root = Path(__file__).resolve().parents[1]
    report['sourceSHA256'] = {name: hashlib.sha256((root / name).read_bytes()).hexdigest() for name in (
        'tools/client72_avatar_skill_depot.py', 'tools/client72_avatar_team.py', 'tools/client72_avatar_contract.py',
        'tools/client72_emulator.py', 'tools/client72_session_runtime.py', 'tools/audit_d02_resources72.py', 'tools/audit_resource72.py')}
    for path, data in zip(outputs, (report, compact(report))):
        path.parent.mkdir(parents=True, exist_ok=True); path.write_text(json.dumps(data, indent=2) + '\n', 'utf8')
    print(json.dumps(dict(fixtures=len(rows), rejections=len(bad), semanticFields=1,
                         specialProudSkillOpensAliasApproved=False, targetProtocolReady=False)))


class DepotRuntime(TeamRuntime):
    def __init__(self, engine, graph):
        self.cache_dicts = {}; self.cache_rows = {}; self.resource_queries = []; self.row_reads = []; self.element_writes = []
        self.resource_stage = 'initial'
        super().__init__(engine, graph, initial_team=0x10000)
        for entry in RESOURCE_ENTRIES | {0x153217960}:
            engine.allow(entry); engine.models.pop(entry, None)
        self.resource_read_hook = engine.uc.hook_add(engine.U.UC_HOOK_MEM_READ, self.observe_resource_read)
        self.element_write_hook = engine.uc.hook_add(engine.U.UC_HOOK_MEM_WRITE, self.observe_element_write)

    def team_lookup(self):
        e = self.e; pointer = e.reg('rcx')
        if pointer not in self.cache_dicts: return super().team_lookup()
        cache = self.cache_dicts[pointer]; key = e.reg('edx')
        position = cache['positions'].get(key, 0xffffffff)
        self.resource_queries.append(dict(stage=self.resource_stage, resource=cache['kind'], id=key, found=position != 0xffffffff))
        return position

    def build_cache(self, kind, rows):
        e = self.e; pointer = e.alloc(); entries = self.g.new_array(24)
        e.write(pointer + 24, entries); positions = {}; pointers = {}; encoders = []
        for key, value in rows:
            position = len(positions); positions[key] = position; row = e.alloc(512) if value is not None else 0
            e.write(entries + 48 + position * 24, row); pointers[key] = row
            if not row: continue
            self.cache_rows[row] = dict(resource=kind, id=key)
            id_offset = {'avatar': 0x178, 'depot': 0x54, 'skill': 0x68}[kind]
            encoders.append(self.invoke(0x14AEFA090, rcx=row + id_offset, rdx=key))
            if kind in ('depot', 'avatar'):
                encoders.append(self.invoke(0x14AEFA090, rcx=row + (0x6C if kind == 'depot' else 0x168), rdx=value))
            else: e.write(row + 0x54, value, 'I')
        self.cache_dicts[pointer] = dict(kind=kind, positions=positions, rows=pointers)
        e.write(GLOBAL + {'avatar': 0x30870, 'depot': 0x212B8, 'skill': 0x16C70}[kind], pointer)
        return dict(pointer=pointer, rows=pointers, encoders=encoders)

    def observe_resource_read(self, uc, access, address, size, value, data):
        if self.resource_stage not in ('team-consumer', 'named-element'): return
        for row, info in self.cache_rows.items():
            if row <= address < row + 512:
                self.row_reads.append(dict(**info, offset=address - row, width=size, source=hex(self.e.reg('rip'))))

    def observe_element_write(self, uc, access, address, size, value, data):
        if self.e.reg('rip') == 0x151A9ECF5:
            self.element_writes.append(dict(source=hex(self.e.reg('rip')), width=size, value=value))

    def resource_paths(self):
        e = self.e; rows = []
        for kind, cell, getter, loader in (('depot', 0x145B22B70, 0x14D6D5490, 0x14D6D53A0),
                                           ('skill', 0x145B0A6E8, 0x14EB02D90, 0x14EB02C40)):
            pointer = e.alloc(); e.write(cell, pointer)
            read = self.invoke(getter)
            if e.reg('rax') != pointer: raise ValueError('Resource literal getter differs')
            load = self.invoke(loader, {0x14D6E1570})
            if e.reg('rcx') != pointer: raise ValueError('Resource loader input differs')
            rows.append(dict(resource=kind, getter=read, loader=load, fileIOExecuted=False))
        return rows

    def elements(self):
        listing = self.e.read(self.team_manager + 0xB8); array = self.e.read(listing + 16)
        return [self.e.read(array + 32 + i * 4, 'I') for i in range(self.e.read(listing + 24, 'I'))]

    def consume(self):
        self.resource_stage = 'team-consumer'
        trace = self.invoke(0x151A9EC40, {RETURN, 0x1404C2BE0, 0x1404C2650, 0x151A9EF29}, rcx=self.team_manager)
        self.resource_stage = 'after-consumer'
        return trace

    def close(self):
        self.e.uc.hook_del(self.resource_read_hook); self.e.uc.hook_del(self.element_write_hook); super().close()


def depot_wire(avatars, teams, current, avatar_ids=None, wrong_depot_wire=False):
    wire = team_wire(teams, current, avatars[0][0] if avatars else 0, [])
    for i, (guid, depots) in enumerate(avatars):
        wire += message(6, avatar_wire(avatar_ids[i] if avatar_ids else 1001 + i, guid, [], []) + b''.join(scalar(11, depot) for depot in depots)
                        + (bytes.fromhex('5d2a000000') if wrong_depot_wire else b''))
    return wire


def cases():
    a, b = 9007199254740993, 9223372036854775809
    base = dict(avatars=[(a, [501]), (b, [701])], teams=[(1, 'A', [a, b]), (2, 'B', [b])],
                current=1, switches=[1, 2, 9, 1], depots=[(501, 101), (701, 201)], skills=[(101, 1), (201, 2)],
                absentDepotCache=False, absentSkillCache=False)
    specs = [('two-depots-two-teams', {}),
        ('candidate-id-numbers-only', dict(avatars=[(a, [504]), (b, [704])], depots=[(504, 101), (704, 201)])),
        ('high-u32-depot-and-skill-keys', dict(avatars=[(a, [0x80000001]), (b, [0xffffffff])],
             depots=[(0x80000001, 0x80000101), (0xffffffff, 0xfffffffe)], skills=[(0x80000101, 1), (0xfffffffe, 2)])),
        ('omitted-overwrites-prior', dict(avatars=[(a, []), (b, [701])], depots=[(0, 101), (701, 201)])),
        ('duplicate-field-last-wins', dict(avatars=[(a, [501, 701]), (b, [701])])),
        ('explicit-zero-last', dict(avatars=[(a, [501, 0]), (b, [701])], depots=[(0, 101), (701, 201)])),
        ('wrong-depot-wire-skipped', dict(avatars=[(a, []), (b, [701])], wrongDepotWire=True)),
        ('same-depot-distinct-guids', dict(avatars=[(a, [501]), (b, [501])])),
        ('same-guid-latest-model-field', dict(avatars=[(a, [501]), (a, [701])], teams=[(1, 'A', [a]), (2, 'B', [a])])),
        ('missing-depot-row', dict(depots=[(701, 201)])),
        ('null-depot-row', dict(depots=[(501, None), (701, 201)])),
        ('absent-depot-cache', dict(absentDepotCache=True)),
        ('zero-skill-reference', dict(depots=[(501, 0), (701, 201)], skills=[(0, 1), (201, 2)])),
        ('missing-skill-row', dict(skills=[(201, 2)])),
        ('null-skill-row', dict(skills=[(101, None), (201, 2)])),
        ('absent-skill-cache', dict(absentSkillCache=True)),
        ('zero-skill-enum', dict(skills=[(101, 0), (201, 2)])),
        ('unknown-enum-remains-raw', dict(skills=[(101, 777), (201, 2)])),
        ('missing-and-zero-member-guids', dict(avatars=[(0, [501]), (b, [701])], teams=[(1, 'A', [0, 999, b]), (2, 'B', [b])])),
        ('no-owned-avatar', dict(avatars=[])),
        ('only-selected-team-resources', dict(current=2, switches=[2, 9, 2], depots=[(701, 201)]))]
    return [dict(base, name=name, **change) for name, change in specs]


def expected_consumer(case, team_id):
    models = {guid: values[-1] if values else 0 for guid, values in case['avatars'] if guid}
    teams = {key: list(dict.fromkeys(guids)) for key, _, guids in case['teams']}
    depots, skills = dict(case['depots']), dict(case['skills']); queries = []; reads = []; elements = []
    members = teams.get(team_id, [])
    for guid in members:
        depot = models.get(guid, 0)
        if not depot or case['absentDepotCache']: continue
        queries.append(dict(stage='team-consumer', resource='depot', id=depot, found=depot in depots))
        reference = depots.get(depot)
        if reference is None: continue
        sites = (0x151A9EDE5,) if reference == 0 else (0x151A9EDE5, 0x151A9EDFE)
        reads.extend(dict(resource='depot', id=depot, offset=108, width=4, source=hex(site)) for site in sites)
        if reference == 0 or case['absentSkillCache']: continue
        queries.append(dict(stage='team-consumer', resource='skill', id=reference, found=reference in skills))
        element = skills.get(reference)
        if element is None: continue
        reads.append(dict(resource='skill', id=reference, offset=84, width=4, source='0x151a9ee62'))
        if element: elements.append(element)
    return members, queries, reads, elements


def candidate_projection(args):
    evidence = args.resource_evidence
    commit, root_tree, excel_tree = [json.loads((evidence / name).read_text('utf-8-sig')) for name in (
        'resource-source-commit.json', 'resource-source-tree.json', 'resource-source-ExcelBinOutput-tree.json')]
    tables, hashes = load_tables(args.resource_source, root_tree, excel_tree, commit)
    avatar_ids = [10000005, 10000007]
    avatar_rows = [tables['AvatarExcelConfigData.json'][i] for i in avatar_ids]
    depot_rows = [tables['AvatarSkillDepotExcelConfigData.json'][r['skillDepotId']] for r in avatar_rows]
    if [r['id'] for r in depot_rows] != [501, 701]: raise ValueError('Controlled candidate initial depot IDs changed')
    # This projection makes its zero-default assumption explicit, without assigning an English alias to +0x6c.
    if any({k for k, v in row.items() if type(v) in (int, float, bool)} != {'id'} for row in depot_rows):
        raise ValueError('Candidate depot has non-default scalars requiring a separately proven field binding')
    source = dict(commit=RESOURCE_COMMIT, rootTree=RESOURCE_ROOT, tableSHA256=hashes,
        avatarRows=[dict(id=r['id'], skillDepotId=r['skillDepotId']) for r in avatar_rows], depotRows=depot_rows,
        depotRowsCanonicalSHA256=hashlib.sha256(json.dumps(depot_rows, sort_keys=True, separators=(',', ':')).encode()).hexdigest(),
        projection='Row IDs are pinned candidate values; every non-ID scalar is omitted in both selected rows and is materialized as logical zero for this controlled partial cache',
        targetResourceDeserializationVerified=False, targetConstructorDefaultsVerified=False, englishEnergySkillAliasVerified=False,
        excluded='Arrays/strings and their loading, revision matching and complete row/skill initialization are not replayed')
    case = dict(cases()[0], name='pinned-candidate-initial-501-701', avatars=[(9007199254740993, [501]), (9223372036854775809, [701])],
                avatarIds=avatar_ids, avatarDefaults=[501, 701], depots=[(501, 0), (701, 0)], skills=[],
                resourceOrigin='pinned candidate partial zero-default projection; not target deserialization')
    return source, case


def run_case(binary, records, cells, bindings, outer_bindings, outer, args, case):
    e = Offline72(binary, args.python_libs, records, trace_instructions=False)
    g = AvatarTeamGraph(e, args.directory, cells, bindings, outer_bindings, outer); rt = None
    try:
        avatar_ids = case.get('avatarIds', [1001 + i for i in range(len(case['avatars']))])
        defaults = case.get('avatarDefaults', [values[0] if values else 0 for _, values in case['avatars']])
        wire = depot_wire(case['avatars'], case['teams'], case['current'], avatar_ids, case.get('wrongDepotWire', False)); reader = g.run_graph(30509, wire)
        rt = DepotRuntime(e, g); paths = rt.resource_paths(); dispatch = rt.notification(); identities = []
        avatar_cache = rt.build_cache('avatar', list(zip(avatar_ids, defaults)))
        for (model, source), (guid, values) in zip(rt.delivery, case['avatars']):
            prior = rt.invoke(0x14B1046F0, rcx=model + 0x2B8, rdx=777)
            identity = rt.identity(model, source)
            decode = rt.invoke(0x14B1049F0, rcx=model + 0x2B8); value = e.reg('eax')
            expected = values[-1] if values else 0
            if value != expected or identity['guid'] != guid or e.read(source + 0xC4, 'I') != expected:
                raise ValueError('Native depot field encode/decode differs')
            identities.append(dict(guid=guid, priorDepot=777, depot=value, prior=prior, identity=identity, decode=decode))
        caches = {kind: rt.build_cache(kind, case[kind + 's' if kind == 'depot' else 'skills']) for kind in ('depot', 'skill')}
        if case['absentDepotCache']: e.write(GLOBAL + 0x212B8, 0)
        if case['absentSkillCache']: e.write(GLOBAL + 0x16C70, 0)
        resource_checks = []
        for kind, entry, absent in (('depot', 0x14D6D70F0, case['absentDepotCache']), ('skill', 0x14EB02D30, case['absentSkillCache'])):
            for key, pointer in caches[kind]['rows'].items():
                rt.resource_stage = 'direct-query'; trace = rt.invoke(entry, rcx=key)
                if e.reg('rax') != (0 if absent else pointer): raise ValueError('Native resource query differs')
                resource_checks.append(dict(resource=kind, id=key, found=bool(e.reg('rax')), trace=trace))
        named = []
        for aid, default in zip(avatar_ids, defaults):
            rt.resource_stage = 'named-element'; start = len(rt.resource_queries); read_start = len(rt.row_reads)
            trace = rt.invoke(0x153217960, rcx=aid); actual = e.reg('eax')
            reference = dict(case['depots']).get(default)
            expected = 0 if case['absentDepotCache'] or case['absentSkillCache'] or reference is None else dict(case['skills']).get(reference) or 0
            queries = [dict(stage='named-element', resource='avatar', id=aid, found=True)]
            reads = [dict(resource='avatar', id=aid, offset=360, width=4, source='0x1532179c8')]
            if not case['absentDepotCache']:
                queries.append(dict(stage='named-element', resource='depot', id=default, found=default in dict(case['depots'])))
                if reference is not None:
                    reads.append(dict(resource='depot', id=default, offset=108, width=4, source='0x153217a27'))
                    if not case['absentSkillCache']:
                        queries.append(dict(stage='named-element', resource='skill', id=reference, found=reference in dict(case['skills'])))
                        if dict(case['skills']).get(reference) is not None:
                            reads.append(dict(resource='skill', id=reference, offset=84, width=4, source='0x153217a77'))
            if (actual != expected or trace['stop'] != hex(RETURN) or {hex(x) for x in NATIVE} & set(trace['models']) or
                rt.resource_queries[start:] != queries or rt.row_reads[read_start:] != reads):
                raise ValueError('Named GetAvatarElementType differs')
            named.append(dict(avatarId=aid, defaultDepot=default, element=actual, queries=rt.resource_queries[start:],
                              resourceReads=rt.row_reads[read_start:], trace=trace))
        selection = rt.selection_field(); steps = []
        for team in [case['current'], *case['switches']]:
            change = rt.invoke(0x151A9E080, rcx=rt.team_manager, rdx=team)
            starts = [len(x) for x in (rt.team_lookups, rt.resource_queries, rt.row_reads, rt.element_writes)]
            trace = rt.consume(); members, queries, reads, elements = expected_consumer(case, team)
            actual_queries, actual_reads, writes = rt.resource_queries[starts[1]:], rt.row_reads[starts[2]:], rt.element_writes[starts[3]:]
            if (trace['stop'] != hex(RETURN) or rt.team_lookups[starts[0]:] != members or actual_queries != queries or
                actual_reads != reads or rt.elements() != elements or [x['value'] for x in writes] != elements):
                raise ValueError('Native Team depot resource consumer differs: ' + case['name'])
            required = {0x151A9EC40, 0x151A9BD20}
            if members: required |= {0x14E9B2C60, 0x156F6F510}
            if any(x['resource'] == 'skill' for x in queries): required.add(0x14AEF9DB0)
            if not {hex(x) for x in required} <= set(trace['executedAddresses']) or {hex(x) for x in NATIVE} & set(trace['models']):
                raise ValueError('Critical depot/Team native function missing or modeled')
            steps.append(dict(teamId=team, members=members, resourceQueries=actual_queries, resourceReads=actual_reads,
                              elements=elements, elementWrites=writes, change=change, trace=trace))
        return dict(case, wire=wire.hex(), reader=reader, paths=paths, dispatch=dispatch, identities=identities,
                    resourceEncoders={kind: cache['encoders'] for kind, cache in (caches | {'avatar': avatar_cache}).items()}, resourceChecks=resource_checks,
                    namedAvatarElements=named, selection=selection, steps=steps)
    except Exception:
        args.output.with_suffix('.failure.json').write_text(json.dumps(dict(case=case, calls=e.calls,
            registers={key: hex(e.reg(key)) for key in ('rip', 'rcx', 'rdx', 'r8', 'r9')}), indent=2) + '\n', 'utf8')
        raise
    finally:
        if rt: rt.close()
        g.close(); e.close()


def rejections(binary, records, cells, bindings, outer_bindings, outer, args):
    e = Offline72(binary, args.python_libs, records, trace_instructions=False)
    g = AvatarTeamGraph(e, args.directory, cells, bindings, outer_bindings, outer); rt = None; rows = []
    try:
        for name, index, wire in [('truncated-depot-varint', 48178, '5880'), ('depot-varint-overflow', 48178, '58ffffffffffffffffff02'),
                                  ('negative-avatar-length', 30509, '32ffffffff0f')]:
            try: g.run_graph(index, bytes.fromhex(wire))
            except ValueError as error:
                text = str(error); rows.append(dict(name=name, wire=wire, error=text,
                    scope='native-reader-error-helper' if 'Native error branch:' in text else 'explicit-input-model'))
            else: raise ValueError('Invalid depot input accepted')
        a = 9007199254740993
        g.run_graph(30509, depot_wire([(a, [501])], [(1, 'A', [a])], 1)); rt = DepotRuntime(e, g)
        rt.notification(); model, source = rt.delivery[0]; rt.identity(model, source)
        cache = rt.build_cache('depot', [(501, 101)]); rt.build_cache('skill', [(101, 1)]); rt.selection_field()
        pointer = cache['pointer']; entries = e.read(pointer + 24); e.write(pointer + 24, 0)
        trace = rt.consume()
        if trace['stop'] != '0x1404c2be0': raise ValueError('Malformed resource entries did not reach null boundary')
        rows.append(dict(name='null-depot-entry-array', scope='native-null-error-helper-boundary; controlled malformed resource storage', trace=trace))
        e.write(pointer + 24, entries); rt.cache_dicts[pointer]['positions'][501] = 64
        trace = rt.consume()
        if trace['stop'] != '0x1404c2650': raise ValueError('Malformed resource position did not reach bounds boundary')
        rows.append(dict(name='out-of-bounds-resource-position', scope='native-bounds-error-helper-boundary; fixture corruption, not resource-ID limit', trace=trace))
        rt.cache_dicts[pointer]['positions'][501] = 0
        e.write(0x145806B9F, 1, 'B')
        trace = rt.consume()
        if trace['stop'] != '0x151a9ef29': raise ValueError('IFix branch did not stop before patch state')
        rows.append(dict(name='ifix-consumer-boundary', scope='native-guard-prefix; unknown wrapper not executed', trace=trace))
    finally:
        if rt: rt.close()
        g.close(); e.close()
    return rows


def depot_declarations(binary, metadata, startup, records, extraction, cells, libraries):
    pe = PE(binary); h = pe.offset(HEADER_VA); resolver = TypeResolver(pe, startup, records, h)
    names = Names(metadata, int(extraction['stringBase'], 16)); methods = []
    mb = BODY + (number(binary, h + 0xB0) ^ 0x51324A1F); pb = BODY + ((number(binary, h + 0x174) + 0xAE6BCD68) & 0xffffffff)
    for owner in (18163, 39488, 63488, 11866, 58209, 12832):
        for method in records[owner]['methods']:
            entry = int(method['registeredAddress'], 16)
            if entry not in NATIVE | {0x14D6D54A0, 0x14EB02DA0}: continue
            first = parameter_start(method['index'], number(metadata, mb + method['index'] * 26 + 4)); parameters = []
            for i in range(first, first + method['parameterCount']):
                name, typ = parameter_words(i, number(metadata, pb + i * 8), number(metadata, pb + i * 8 + 4))
                parameters.append(dict(name=names.decode(name), type=resolver.resolve_index(typ)))
            methods.append(dict(owner=owner, method=method, parameters=parameters))
    resources = []
    for owner, offset, row_index, cell, literal_index, literal_value in (
        (63488, '0x40212b8', 22284, 0x145B22B70, 45482, 'Data/_ExcelBinOutput/AvatarSkillDepotExcelConfigData'),
        (11866, '0x4016c70', 88602, 0x145B0A6E8, 33049, 'Data/_ExcelBinOutput/AvatarSkillExcelConfigData')):
        field = next(f for f in records[owner]['fields'] if f['rawOffset'] == offset)
        if field['type']['arguments'][0]['kind'] != 'uint32' or field['type']['arguments'][1].get('typeDefinition') != row_index:
            raise ValueError('Typed resource cache changed')
        lit = Literals72(binary, metadata); literal = next(x for x in lit.group(lit.global_group) if x['index'] == literal_index)
        if literal['cell'] != hex(cell) or lit.decode(literal_index) != literal_value: raise ValueError('Named resource literal changed')
        resources.append(dict(owner=owner, cache=field, rowType=row_index, literal=dict(literal, value=literal_value)))
    raw = next(f for f in records[48178]['fields'] if f['offset'] == 196)
    reference = next(f for f in records[22284]['fields'] if f['offset'] == 108)
    element = next(f for f in records[88602]['fields'] if f['offset'] == 84)
    sibling = next(f for f in records[22284]['fields'] if f['name'] == 'LHFOOPMJHBN')
    listing = next(f for f in records[39488]['fields'] if f['offset'] == 0xB8)
    if (raw['name'] != 'BLBHOJMCOJN' or raw['type']['kind'] != 'uint32' or reference['type'].get('typeDefinition') != 58209 or
        element['type'].get('typeDefinition') != 74813 or listing['type']['arguments'][0].get('typeDefinition') != 74813 or
        sibling['offset'] != 16 or sibling['type']['elementType'].get('typeDefinition') != 40302):
        raise ValueError('Depot/skill resource member declarations changed')
    parser_cells = [cells[x] for x in (0x1458E1E68, 0x1458E1E70, 0x1458C09E0, 0x1458C09E8)]
    if [x['type'].get('typeDefinition') for x in parser_cells] != [1222, 22284, 1222, 88602]:
        raise ValueError('Resource parser concrete allocation classes changed')
    enum = recover_enums(binary, metadata, records, indices=(74813,))['enums'][0]
    named = next(x for x in methods if x['method']['registeredAddress'] == '0x153217960')
    if named['method']['name'] != 'GetAvatarElementType': raise ValueError('Named element consumer changed')
    load_emulator(libraries)
    from capstone import Cs, CS_ARCH_X86, CS_MODE_64
    decoder = Cs(CS_ARCH_X86, CS_MODE_64); sites = {}
    for address, expected in {
        0x15184E18E: ('mov', 'edx, dword ptr [rbx + 0xc4]'),
        0x15184E1AD: ('movups', 'xmmword ptr [rdi + 0x2b8], xmm0'),
        0x14D6D5535: ('mov', 'qword ptr [rax + 0x212b8], rbx'),
        0x14EB02E35: ('mov', 'qword ptr [rax + 0x16c70], rbx'),
        0x151A9EDE5: ('mov', 'eax, dword ptr [rbp + 0x6c]'),
        0x151A9EE62: ('mov', 'r9d, dword ptr [rax + 0x54]'),
        0x1532179C8: ('mov', 'eax, dword ptr [rax + 0x168]'),
        0x153217A27: ('mov', 'eax, dword ptr [rax + 0x6c]'),
        0x153217A77: ('mov', 'esi, dword ptr [rax + 0x54]'),
        0x151A9ECF5: ('mov', 'dword ptr [rax + rdx*4 + 0x20], r9d')}.items():
        ins = next(decoder.disasm(binary[pe.offset(address):pe.offset(address) + 15], address))
        if (ins.mnemonic, ins.op_str) != expected: raise ValueError('Depot native site changed: ' + hex(address))
        sites[hex(address)] = dict(instruction=ins.mnemonic + ' ' + ins.op_str, instructionSHA256=hashlib.sha256(ins.bytes).hexdigest())
    return dict(methods=methods, resources=resources, rawField=raw, depotSkillReference=reference, skillEnumField=element,
        teamDerivedList=listing, siblingField=sibling, resourceParserCells=parser_cells, elementEnum=enum, nativeSites=sites,
        avatarResourceFields=[f for f in records[58272]['fields'] if f['offset'] in (0x168, 0x178)])


def compact(value):
    if isinstance(value, list): return [compact(x) for x in value]
    if isinstance(value, dict):
        if 'executedAddresses' in value:
            return {k: value[k] for k in ('entry', 'stop', 'instructions', 'nativePathSHA256', 'models')} | dict(
                nativeEntries=sorted(({hex(x) for x in NATIVE} | {value['entry'], value['stop']}) & set(value['executedAddresses'])))
        if 'reader' in value: value = dict(value, readerResult=value['reader']['result'])
        return {k: compact(v) for k, v in value.items() if k != 'reader'}
    return value


if __name__ == '__main__': main()
