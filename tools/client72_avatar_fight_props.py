"""Bounded CN72 AvatarInfo raw float-map initialization and named HP consumers."""
import argparse
import hashlib
import json
import math
import struct
from pathlib import Path
try:
    from .extract_client72 import HASHES, PE, HEADER_VA, BODY, Names, TypeResolver, number, read_verified, extract
    from .extract_client72_signatures import parameter_start, parameter_words
    from .extract_client72_enums import recover as recover_enums
    from .trace_client72_avatar_signed_trees import selected_aot
    from .trace_client72_token import load_emulator
    from .trace_client72_avatar_core import core_aot
    from .trace_client72_avatar_outer import avatar_aot
    from .trace_client72_player_store import type_cells
    from .client72_avatar_contract import AvatarContractGraph, AvatarRuntime, avatar_wire, declarations, RETURN
    from .client72_avatar_state import EVIDENCE_SHA as STATE_INPUTS
    from .client72_graph_fixtures import scalar, message
    from .client72_emulator import Offline72, GLOBAL
except ImportError:
    from extract_client72 import HASHES, PE, HEADER_VA, BODY, Names, TypeResolver, number, read_verified, extract
    from extract_client72_signatures import parameter_start, parameter_words
    from extract_client72_enums import recover as recover_enums
    from trace_client72_avatar_signed_trees import selected_aot
    from trace_client72_token import load_emulator
    from trace_client72_avatar_core import core_aot
    from trace_client72_avatar_outer import avatar_aot
    from trace_client72_player_store import type_cells
    from client72_avatar_contract import AvatarContractGraph, AvatarRuntime, avatar_wire, declarations, RETURN
    from client72_avatar_state import EVIDENCE_SHA as STATE_INPUTS
    from client72_graph_fixtures import scalar, message
    from client72_emulator import Offline72, GLOBAL

NATIVE = {0x1518517C0, 0x152F7F190, 0x151852CF0, 0x152F7E830, 0x15184CCA0,
          0x151845780, 0x15183D220, 0x152E90FE0, 0x152E90A60, 0x152F80950,
          0x151852660, 0x151852490, 0x1517AB3A0, 0x15720A600, 0x152585120, 0x14E9B1040}
EVIDENCE_SHA = STATE_INPUTS | {'avatar-state-observations.json': '6cbc46b1d447883a0d4c3ede00b43af56ccf3950a37de85db50840396f1b4cc9'}
IDS = {1: 'FIGHT_PROP_BASE_HP', 4: 'FIGHT_PROP_BASE_ATTACK', 7: 'FIGHT_PROP_BASE_DEFENSE',
       1010: 'FIGHT_PROP_CUR_HP', 2000: 'FIGHT_PROP_MAX_HP', 2001: 'FIGHT_PROP_CUR_ATTACK', 2002: 'FIGHT_PROP_CUR_DEFENSE'}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ('client', 'output', 'observations'): parser.add_argument('--' + name, type=Path, required=True)
    parser.add_argument('--directory', type=Path, default=Path('protocol/cn-7.2'))
    parser.add_argument('--python-libs', type=Path, default=Path('local/migration72/python'))
    args = parser.parse_args(); outputs = [args.output.resolve(), args.observations.resolve()]
    if len(set(outputs)) != 2 or any(p.is_relative_to(args.client.resolve()) for p in outputs):
        parser.error('Distinct outputs outside the client are required')
    for name, digest in EVIDENCE_SHA.items():
        if hashlib.sha256((args.directory / name).read_bytes()).hexdigest() != digest: raise ValueError('Reviewed input changed: ' + name)
    binary, metadata, startup = [read_verified(args.client / name, digest) for name, digest in HASHES.items()]
    records, extraction = extract(binary, metadata, startup=startup, structures=True)
    declarations(binary, metadata, startup, records, args.directory)
    declared = fight_declarations(binary, metadata, startup, records, extraction, args.python_libs)
    cells = type_cells(binary, metadata, startup, records)
    bindings = core_aot(binary, metadata, startup, records)
    outer_bindings = avatar_aot(binary, metadata, startup, records, dict(types=[]))
    outer = json.loads((args.directory / 'avatar-outer-observations.json').read_text('utf8'))
    rows = [run_case(binary, records, cells, bindings, outer_bindings, outer, args, case) for case in cases()]
    bad = rejections(binary, records, cells, bindings, outer_bindings, outer, args)
    report = dict(schemaVersion=1, task='F-03d3f4b', inputSHA256=HASHES, inputContracts=EVIDENCE_SHA,
        declarations=declared, fixtures=rows, rejections=bad,
        semanticEvidence=dict(typeIndex=48178, tag=7, clientField='ADJINCFKMML', objectOffset=40,
            meaning='FightProp enum-keyed float map', rawKeyType='uint32', rawValueType='float', status='proposed-for-independent-review',
            testedNamedIds=IDS, unknownRawIds=[0, 4242, 0xffffffff],
            sources=['Native raw map reaches actual typed virtual map application and codec30710 storage',
                     'Getter152f80950 and callbacks15184cca0/151845780 take target enum32419',
                     'Complete named LuaAvatarPropWatcherHandler.CheckAvatarProp reads each tested key',
                     'Native current/max HP division reaches named MonoAvatarIcon.set_hpRatio entry',
                     'Real HP/MaxHP callbacks construct named AvatarHPChanged/HPChangedEnd/HPDebtRefresh events']),
        producer=dict(businessRequiredForEntry='not-established', omittedMap='Native application clears the controlled prior FightProp map',
            currentHpWithoutMaxHp='Missing max HP reads zero and the tested HP ratio is zero',
            remaining=['Server source/formulas and minimum set required for actual entry', 'Combat entity effects, derived-property initialization and full Avatar handler',
                       'Untested enum IDs and acceptance of negative/nonfinite values as gameplay inputs']),
        completeAvatarHandler=False, completeEntityEffects=False, allFightPropIdsApproved=False,
        runtimeStatus='not-tested', targetProtocolReady=False,
        models=['Fixed f1 Avatar construction/delivery and encoded identity fixtures are reused',
                'Codec30710 executes with the explicit existing reversible permutation table and deterministic Random fixture',
                'Managed encoded-key dictionary index/insert, Array.Clear byte operation, rental and return are declared storage models',
                'Raw map linked topology is explicit managed storage over actual decoded node key/value bytes; MoveNext executes natively',
                'Native clear count/reset/null assignment, float setter/getter/codecs, callbacks and named watcher execute',
                'Empty scene index is controlled; no combat entity or component effect is modeled successful',
                'Secondary Avatar-ID index points to the actual produced model as controlled storage; registration of that index is not claimed',
                'HP UI call block14cb6e3fe has explicit r14=model/rbx=UI live-ins and stops before named setter14f44f530',
                'Actual application stops at15184e3f3 before following152f7ec20; full handler tail remains untested',
                'Nonfinite snapshot floats are represented by float32 bits; actual reader-node/getter bits are independently asserted'])
    root = Path(__file__).resolve().parents[1]
    report['sourceSHA256'] = {name: hashlib.sha256((root / name).read_bytes()).hexdigest() for name in (
        'tools/client72_avatar_fight_props.py', 'tools/client72_avatar_contract.py', 'tools/client72_avatar_state.py',
        'tools/client72_emulator.py', 'tools/client72_session_runtime.py', 'tools/trace_client72_avatar_core.py')}
    for path, data in zip(outputs, (report, compact(report))):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(json_safe(data), indent=2, allow_nan=False) + '\n', 'utf8')
    print(json.dumps(dict(fixtures=len(rows), rejections=len(bad), namedIds=len(IDS), targetProtocolReady=False)))


class FightRuntime(AvatarRuntime):
    def __init__(self, engine, graph):
        self.fight_events = []; self.float_inputs = []; self.float_stores = []; self.clears = []
        self.rented_arrays = {}; self.returns = []
        super().__init__(engine, graph); e = self.e
        for entry in NATIVE:
            if entry == 0x15720A600: e.allow_unwind(entry)
            else: e.allow(entry)
            e.models.pop(entry, None)
        for offset, entry in ((0xF0, 0x151852CF0), (0x120, 0x1518517C0), (0x110, 0x15184CCA0)):
            e.write(self.klass + offset, entry)
        e.synthetic_type(0x1458A7128)
        e.write(GLOBAL + 0x25A50, e.read(GLOBAL + 0x204D0))
        e.models[0x158F3F170] = self.lookup; e.models[0x158F3F4C0] = self.insert
        e.models[0x159C68C70] = self.array_clear
        e.write(e.read(self.pool) + 0xF8, e.model_stub(self.return_pool))
        # Actual linked iteration over existing decoded nodes; link topology is managed collection storage.
        for pointer, container in graph.containers.items():
            dictionary = container.get('mapDictionary')
            if dictionary not in graph.maps.floating.dictionaries: continue
            nodes = graph.maps.floating.dictionaries[dictionary]['nodes']; linked = e.read(pointer + 16)
            sentinel = e.alloc(80); e.write(linked + 16, sentinel); e.write(linked + 24, len(nodes), 'I')
            chain = [sentinel, *nodes, sentinel]
            for left, right in zip(chain, chain[1:]): e.write(left + 32, right)
            for node in nodes: e.write(node + 16, linked); e.write(node + 40, 1, 'B')

    def rent(self):
        e = self.e; pointer = e.alloc(); entries = self.array(40, 64); buckets = self.array(4, 64)
        e.write(pointer + 0x18, entries); e.write(pointer + 0x20, buckets)
        self.dictionaries[pointer] = dict(entries=entries, buckets=buckets, keys=[])
        self.rented_arrays[entries] = (40, 64); self.rented_arrays[buckets] = (4, 64)
        return pointer

    def insert(self):
        result = super().insert(); e = self.e; pointer = e.reg('rcx')
        e.write(pointer + 0x38, len(self.dictionaries[pointer]['keys']), 'I')
        self.float_stores.append(dict(encodedKey=bytes(e.uc.mem_read(e.reg('rdx'), 16)).hex(),
                                     encodedValue=bytes(e.uc.mem_read(e.reg('r8'), 16)).hex()))
        return result

    def array_clear(self):
        e = self.e; pointer = e.reg('rcx'); index = e.reg('edx'); count = e.reg('r8d')
        width, capacity = self.rented_arrays[pointer]
        if index + count > capacity: raise ValueError('Fight dictionary Array.Clear fixture bounds')
        e.uc.mem_write(pointer + 32 + index * width, bytes(count * width))
        self.clears.append(dict(elementWidth=width, index=index, count=count)); return 0

    def return_pool(self):
        e = self.e; pointer = e.reg('rdx'); info = self.dictionaries[pointer]
        self.returns.append(dict(nativeCount=e.read(pointer + 0x38, 'I'),
            entriesZero=not any(e.uc.mem_read(info['entries'] + 32, len(info['keys']) * 40))))
        return 0

    def event(self):
        e = self.e; event = e.reg('rdx'); boxed = e.read(event + 8)
        self.fight_events.append(dict(id=e.read(event, 'I'), guid=e.read(boxed + 16))); return 0

    def observe(self, uc, address, size, data):
        super().observe(uc, address, size, data)
        if address == 0x152F7E830:
            self.float_inputs.append(dict(id=self.e.reg('edx'), bits=f'{self.e.reg("xmm2") & 0xffffffff:08x}'))

    def empty_scene(self):
        e = self.e; scene = e.alloc(1024); e.write(GLOBAL + 0xA3A0, scene)
        dictionary = e.alloc(); e.write(dictionary + 24, self.array(24)); e.write(scene + 0x150, dictionary)
        listing = e.alloc(); e.write(listing + 16, self.array(8)); e.write(scene + 0xC0, listing)
        previous = e.models[0x1578B66F0]
        e.models[0x1578B66F0] = lambda: 0xffffffff if e.reg('rcx') == dictionary else previous()

    def apply(self, model, source):
        return self.invoke(0x15184E3BF, {0x15184E3F3, 0x1404C2BE0}, rdi=model, rbx=source)

    def watcher_storage(self, model):
        e = self.e; dictionary = e.alloc(); entries = self.array(24)
        e.write(dictionary + 24, entries); e.write(entries + 48, model); e.write(self.manager + 0x20, dictionary)
        previous = e.models[0x1578B66F0]
        e.models[0x1578B66F0] = lambda: (0 if e.reg('edx') == 1001 else 0xffffffff) if e.reg('rcx') == dictionary else previous()
        self.watcher = e.alloc(128)

    def watch(self, key, value_bits, denominator=0, comparison=0, avatar_id=1001):
        e = self.e
        for offset, value in ((0x48, comparison), (0x50, denominator), (0x58, value_bits), (0x5C, key)):
            e.write(self.watcher + offset, value & 0xffffffff, 'I')
        trace = self.invoke(0x152585120, rcx=self.watcher, rdx=avatar_id)
        return dict(trace=trace, result=bool(e.reg('al')))

    def named_hp(self, model):
        self.e.allow(0x14CB6E140); ui = self.e.alloc(512)
        trace = self.invoke(0x14CB6E3FE, {0x14F44F530}, r14=model, rbx=ui)
        return dict(trace=trace, ratioBits=f'{self.e.reg("xmm1") & 0xffffffff:08x}', receiverMatches=self.e.reg('rcx') == ui)

    def close(self): self.e.uc.hook_del(self.observer)


def fight_wire(pairs, guid=9007199254740993):
    child = avatar_wire(1001, guid, [], [])
    for key, bits in pairs: child += message(7, scalar(1, key) + b'\x15' + struct.pack('<I', bits))
    return message(6, child)


def cases():
    standard = [(1010, 0x42F70000), (2000, 0x43FA0000), (1, 0x43C80000), (4, 0x42740000),
                (7, 0x41A40000), (2001, 0x42B60000), (2002, 0x42220000)]
    rows = []
    def add(name, pairs, **kw):
        rows.append(dict(name=name, pairs=pairs, lifeState=1, repeat=1, guid=9007199254740993, extraWire='') | kw)
    add('seven-nonzero-properties', standard)
    add('same-map-reapplied', standard, repeat=2, guid=9223372036854775809)
    add('omitted-map-clears', [])
    add('current-hp-only', [(1010, 0x42F70000)])
    add('max-hp-only', [(2000, 0x43FA0000)])
    add('duplicate-current-hp-last-wins', [(1010, 0x43C80000), (2000, 0x43FA0000), (1010, 0x42F70000)])
    add('explicit-zero-overwrite', [(1010, 0x42F70000), (1010, 0), (2000, 0x43FA0000)])
    add('dead-state-ratio-suppressed', standard, lifeState=2)
    add('ratio-clamps-above-one', [(1010, 0x447A0000), (2000, 0x43FA0000)])
    add('ratio-clamps-below-zero', [(1010, 0xC2F70000), (2000, 0x43FA0000)])
    add('zero-guid-events', standard[:2], guid=0)
    add('max-guid-events', standard[:2], guid=0xffffffffffffffff)
    add('unknown-keys-remain-raw', [(0, 0x3F800000), (4242, 0x40200000), (0xffffffff, 0xC0600000)])
    add('wrong-outer-wire-skipped', [], extraWire=scalar(7, 99).hex())
    add('wrong-float-wire-defaults-zero', [], extraWire=message(7, scalar(1, 1010) + scalar(2, 99)).hex(), expectedOverride=[(1010, 0)])
    for name, bits in [('positive-zero', 0), ('negative-zero', 0x80000000), ('min-subnormal', 1), ('max-finite', 0x7F7FFFFF),
                       ('negative-fraction', 0xC1240000), ('positive-infinity', 0x7F800000), ('negative-infinity', 0xFF800000), ('quiet-nan-payload', 0x7FC12345)]:
        add('float-' + name, standard[:2] + [(7, bits)])
    return rows


def float32(bits): return struct.unpack('<f', struct.pack('<I', bits))[0]
def float_bits(value): return struct.unpack('<I', struct.pack('<f', value))[0]


def expected_ratio(values):
    current = float32(values.get(1010, 0)); maximum = float32(values.get(2000, 0))
    return 0 if maximum == 0 else float_bits(max(0.0, min(1.0, float32(float_bits(current / maximum)))))


def run_case(binary, records, cells, bindings, outer_bindings, outer, args, case):
    e = Offline72(binary, args.python_libs, records, trace_instructions=False)
    g = AvatarContractGraph(e, args.directory, cells, bindings, outer_bindings, outer); rt = None
    try:
        child = avatar_wire(1001, case['guid'], [], [])
        child += b''.join(message(7, scalar(1, key) + b'\x15' + struct.pack('<I', bits)) for key, bits in case['pairs'])
        wire = message(6, child + bytes.fromhex(case['extraWire'])); reader = g.run_graph(30509, wire)
        rt = FightRuntime(e, g); dispatch = rt.root(); model, source = rt.delivery[0]; identity = rt.identity(model, source)
        rt.empty_scene(); rt.watcher_storage(model); e.write(model + 0x2CC, case['lifeState'], 'I')
        expected = dict(case.get('expectedOverride', case['pairs']))
        map_pointer = e.read(source + 0x28); info = g.maps.floating.dictionaries[e.read(map_pointer + 24)]
        raw = {e.read(node + 44, 'I'): e.read(node + 48, 'I') for node in info['nodes']}
        if raw != expected: raise ValueError('Decoded float-map bits differ: ' + case['name'])
        prior = [(1010, 0x41200000), (2000, 0x42C80000), (4242, 0x40E00000)]
        seed = [rt.invoke(0x152F7E830, rcx=model, rdx=key, xmm2=bits, r9=0) for key, bits in prior]
        steps = []; previous_count = len(prior)
        for _ in range(case['repeat']):
            before_inputs, before_events, before_clears, before_returns = len(rt.float_inputs), len(rt.fight_events), len(rt.clears), len(rt.returns)
            trace = rt.apply(model, source)
            inputs = rt.float_inputs[before_inputs:]; events = rt.fight_events[before_events:]
            expected_inputs = [dict(id=key, bits=f'{bits:08x}') for key, bits in expected.items()]
            event_ids = [374 for key in expected if key in (1010, 2000)]
            if 1010 in expected: event_ids.append(375)
            if 2000 in expected: event_ids.append(376)
            expected_events = [dict(id=key, guid=case['guid']) for key in event_ids]
            if trace['stop'] != '0x15184e3f3' or inputs != expected_inputs or events != expected_events:
                raise ValueError('Native FightProp application differs: ' + case['name'])
            clears, returns = rt.clears[before_clears:], rt.returns[before_returns:]
            if clears != [dict(elementWidth=4, index=0, count=64), dict(elementWidth=40, index=0, count=previous_count)] or returns != [dict(nativeCount=0, entriesZero=True)]:
                raise ValueError('Native preexisting FightProp map clear differs')
            reads = []; watches = []
            for key in sorted(set(IDS) | set(expected) | {4242}):
                getter = rt.invoke(0x152F80950, rcx=model, rdx=key); bits = e.reg('xmm0') & 0xffffffff
                if bits != expected.get(key, 0): raise ValueError('Native float getter bits differ')
                reads.append(dict(id=key, bits=f'{bits:08x}', trace=getter))
                same = rt.watch(key, expected.get(key, 0)); different_bits = 0x40000000 if bits == 0x3F800000 else 0x3F800000
                different = rt.watch(key, different_bits)
                if same['result'] != (not math.isnan(float32(bits))) or different['result']:
                    raise ValueError('Named FightProp watcher comparison differs')
                watches.append(dict(id=key, expectedBits=f'{bits:08x}', equal=same, different=different))
            ratio = expected_ratio(expected); named = rt.named_hp(model)
            expected_ui = 0 if case['lifeState'] == 2 else ratio
            if named['ratioBits'] != f'{expected_ui:08x}' or not named['receiverMatches']:
                raise ValueError('Native named HP UI receiver/value differs')
            ratio_watch = rt.watch(1010, ratio, denominator=2000)
            missing_watch = rt.watch(1010, 0, avatar_id=99999)
            if not ratio_watch['result'] or not missing_watch['result']:
                raise ValueError('Named ratio/missing Avatar watcher differs')
            required = {0x1518517C0, 0x152F7F190, 0x151852CF0, 0x15720A600, 0x15183D220}
            if expected: required |= {0x152F7E830, 0x152E90FE0, 0x152E90A60, 0x15184CCA0}
            if not {hex(x) for x in required} <= set(trace['executedAddresses']): raise ValueError('Required native FightProp body not executed')
            all_traces = [trace, named['trace'], ratio_watch['trace'], missing_watch['trace'], *[x['trace'] for x in reads],
                          *[x[k]['trace'] for x in watches for k in ('equal', 'different')]]
            if any({hex(x) for x in NATIVE} & set(t['models']) for t in all_traces): raise ValueError('Native FightProp consumer modeled successful')
            steps.append(dict(apply=trace, inputs=inputs, events=events, clears=clears, returns=returns, reads=reads,
                              watcher=watches, rawRatioBits=f'{ratio:08x}', namedHp=named, ratioWatcher=ratio_watch,
                              missingAvatarWatcher=missing_watch, outputDictionaryIsNull=e.read(model + 16) == 0))
            previous_count = len(expected)
        return dict(case, wire=wire.hex(), reader=reader, rawNodeBits={str(k): f'{v:08x}' for k, v in raw.items()},
                    dispatch=dispatch, identity=identity, seed=seed, steps=steps)
    except Exception:
        args.output.with_suffix('.failure.json').write_text(json.dumps(dict(case=case, calls=e.calls,
            registers={key: hex(e.reg(key)) for key in ('rip', 'rcx', 'rdx', 'r8', 'r9')}), indent=2) + '\n', 'utf8')
        raise
    finally:
        if rt: rt.close()
        g.close(); e.close()


def rejections(binary, records, cells, bindings, outer_bindings, outer, args):
    e = Offline72(binary, args.python_libs, records, trace_instructions=False)
    g = AvatarContractGraph(e, args.directory, cells, bindings, outer_bindings, outer); rt = None; rows = []
    try:
        for name, wire in [('negative-map-length', '3affffffff0f'), ('truncated-map-float', '3a0508011500'), ('invalid-tag', '00')]:
            try: g.run_graph(48178, bytes.fromhex(wire))
            except ValueError as error:
                text = str(error); rows.append(dict(name=name, wire=wire, error=text,
                    scope='native-reader-error-helper' if 'Native error branch:' in text else 'explicit-input-model'))
            else: raise ValueError('Invalid FightProp input accepted')
        g.run_graph(30509, fight_wire([(1010, 0x42F70000)])); rt = FightRuntime(e, g)
        rt.root(); model, source = rt.delivery[0]; rt.identity(model, source); rt.empty_scene()
        rt.invoke(0x152F7E830, rcx=model, rdx=1010, xmm2=0x42F70000, r9=0)
        pointer = e.read(model + 16); value_pointer = rt.dictionaries[pointer]['entries'] + 56
        e.write(value_pointer, e.read(value_pointer, 'B') ^ 1, 'B')
        tampered = rt.invoke(0x152F80950, {0x152E90D75, RETURN}, rcx=model, rdx=1010)
        if tampered['stop'] != '0x152e90d75': raise ValueError('Native float consistency check did not reject corruption')
        rows.append(dict(name='corrupted-float-storage', scope='native-consistency-failure-branch-boundary', trace=tampered))
        e.write(source + 0x28, 0); before = len(rt.float_inputs)
        null = rt.apply(model, source)
        if null['stop'] != '0x1404c2be0' or e.read(model + 16) != 0 or len(rt.float_inputs) != before:
            raise ValueError('Null map did not stop after actual clear and before setter')
        rows.append(dict(name='null-map-after-clear', scope='native-null-error-helper-boundary; controlled malformed storage', trace=null, dictionaryCleared=True))
    finally:
        if rt: rt.close()
        g.close(); e.close()
    return rows


def fight_declarations(binary, metadata, startup, records, extraction, libraries):
    pe = PE(binary); h = pe.offset(HEADER_VA); resolver = TypeResolver(pe, startup, records, h)
    names = Names(metadata, int(extraction['stringBase'], 16)); methods = []
    method_base = BODY + (number(binary, h + 0xB0) ^ 0x51324A1F)
    parameter_base = BODY + ((number(binary, h + 0x174) + 0xAE6BCD68) & 0xffffffff)
    wanted = NATIVE | {0x14F44F530, 0x14CB6E140}
    for owner in (18163, 89508, 30710, 35156, 49228, 72302, 70990, 28833):
        for method in records[owner]['methods']:
            if int(method['registeredAddress'], 16) not in wanted: continue
            first = parameter_start(method['index'], number(metadata, method_base + method['index'] * 26 + 4)); parameters = []
            for i in range(first, first + method['parameterCount']):
                name, typ = parameter_words(i, number(metadata, parameter_base + i * 8), number(metadata, parameter_base + i * 8 + 4))
                parameters.append(dict(name=names.decode(name), type=resolver.resolve_index(typ)))
            methods.append(dict(owner=owner, ownerName=records[owner]['name'], method=method, parameters=parameters))
    by_entry = {x['method']['registeredAddress']: x for x in methods}
    for entry in ('0x152f80950', '0x15184cca0', '0x151845780'):
        if by_entry[entry]['parameters'][0]['type'].get('typeDefinition') != 32419: raise ValueError('FightProp enum-typed consumer changed')
    if by_entry['0x152585120']['method']['name'] != 'CheckAvatarProp' or by_entry['0x14f44f530']['method']['name'] != 'set_hpRatio':
        raise ValueError('Named FightProp consumer changed')
    mapper = by_entry['0x151852cf0']['parameters'][0]['type']
    if mapper.get('typeDefinition') != 17961 or [x['kind'] for x in mapper['arguments']] != ['uint32', 'float']:
        raise ValueError('Typed raw float map changed')
    virtuals = [dict(slot=slot, entry=entry, method=next(m for m in records[18163]['methods'] if m['virtualSlot'] == slot))
                for slot, entry in ((4, '0x151852cf0'), (8, '0x15184cca0'), (10, '0x1518517c0'))]
    if any(x['method']['registeredAddress'] != x['entry'] for x in virtuals): raise ValueError('FightProp virtual dispatch changed')
    dictionary = next(f for f in records[89508]['fields'] if f['offset'] == 16)
    if [x['typeDefinition'] for x in dictionary['type']['arguments']] != [32758, 30710]: raise ValueError('Encoded float dictionary changed')
    wanted_symbols = set(IDS.values()) | {'AvatarHPChanged', 'AvatarHPChangedEnd', 'AvatarHPDebtRefresh'}
    enums = [dict(x, values=[v for v in x['values'] if v['symbol'] in wanted_symbols])
             for x in recover_enums(binary, metadata, records, indices=(32419, 75464))['enums']]
    if {v['symbol']: v['value'] for x in enums for v in x['values']} != {**{v: k for k, v in IDS.items()},
        'AvatarHPChanged': 374, 'AvatarHPChangedEnd': 375, 'AvatarHPDebtRefresh': 376}: raise ValueError('Target FightProp/event enum changed')
    aot = selected_aot(binary, metadata, startup, records, {(11026, 5972): ('MoveNext',), (1222, 53617): ('FindEntry', 'TryInsert')})
    if {x['address'] for x in aot} != {'0x15720a600', '0x158f3f170', '0x158f3f4c0'}:
        raise ValueError('Concrete FightProp map iteration/dictionary AOT changed')
    fields = [f for f in records[72302]['fields'] if f['offset'] in (0x48, 0x50, 0x58, 0x5C)]
    if {f['offset']: f['name'] for f in fields} != {0x48: 'CompareState', 0x50: 'FightPropType2', 0x58: 'PropValue', 0x5C: 'FightPropType1'}:
        raise ValueError('Named watcher fields changed')
    load_emulator(libraries)
    from capstone import Cs, CS_ARCH_X86, CS_MODE_64
    decoder = Cs(CS_ARCH_X86, CS_MODE_64); sites = {}
    for address, expected in {
        0x15184E3BF: ('mov', 'rsi, qword ptr [rbx + 0x28]'), 0x15184E3D8: ('call', 'qword ptr [rax + 0x120]'),
        0x15184E3ED: ('call', 'qword ptr [rax + 0xf0]'), 0x152F7F218: ('mov', 'qword ptr [rsi + 0x10], 0'),
        0x151852D8B: ('mov', 'ebx, dword ptr [rax + 0x2c]'), 0x151852D8E: ('movss', 'xmm2, dword ptr [rax + 0x30]'),
        0x151852685: ('mov', 'edx, 0x3f2'), 0x151852695: ('mov', 'edx, 0x7d0'),
        0x14CB6E401: ('call', '0x151852660'), 0x14CB6E40C: ('call', '0x14f44f530'),
        0x152585208: ('mov', 'ebx, dword ptr [rsi + 0x5c]'), 0x152585230: ('call', '0x152f80950')}.items():
        ins = next(decoder.disasm(binary[pe.offset(address):pe.offset(address) + 15], address))
        if (ins.mnemonic, ins.op_str) != expected: raise ValueError('FightProp native site changed: ' + hex(address))
        sites[hex(address)] = dict(instruction=ins.mnemonic + ' ' + ins.op_str, instructionSHA256=hashlib.sha256(ins.bytes).hexdigest())
    return dict(methods=methods, virtualDispatch=virtuals, encodedDictionary=dictionary, codecFields=records[30710]['fields'],
                enums=enums, watcherFields=fields, concreteAOT=aot, nativeSites=sites,
                avatarIdIndex=next(f for f in records[35156]['fields'] if f['offset'] == 32))


def compact(value):
    if isinstance(value, list): return [compact(x) for x in value]
    if isinstance(value, dict):
        if 'executedAddresses' in value:
            return {k: value[k] for k in ('entry', 'stop', 'instructions', 'nativePathSHA256', 'models')} | dict(
                nativeEntries=sorted(({hex(x) for x in NATIVE} | {value['entry'], value['stop']}) & set(value['executedAddresses'])))
        if 'reader' in value: value = dict(value, readerResult=value['reader']['result'])
        return {k: compact(v) for k, v in value.items() if k != 'reader'}
    return value


def json_safe(value):
    if isinstance(value, float) and not math.isfinite(value): return dict(snapshotFloat32Bits=f'{float_bits(value):08x}')
    if isinstance(value, list): return [json_safe(x) for x in value]
    if isinstance(value, dict): return {k: json_safe(v) for k, v in value.items()}
    return value


if __name__ == '__main__': main()
