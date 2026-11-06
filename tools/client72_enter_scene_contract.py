"""Bounded CN72 PlayerEnterSceneNotify application contract; offline only.

Reuses hash-pinned raw reader evidence, then verifies named consumers on the
original reader object. Unknown business names are not promoted by old setters.
"""
import argparse
import copy
import hashlib
import json
from pathlib import Path
import struct
try:
    from .extract_client72 import HASHES, read_verified, extract, PE, HEADER_VA, BODY, number, TypeResolver
    from .extract_client72_literals import Literals72
    from .client72_emulator import Offline72, OBJECT, STACK, GLOBAL
    from .client72_scene_reader import SceneReader72
    from .client72_session_runtime import fragment
    from .trace_client72_scene_wait import declarations as scene_declarations, scalar_wire, blob_wire
    from .client72_scene_handshake import REUSED_EVIDENCE
    from .extract_client72_enums import recover as recover_enums
    from .extract_client72_signatures import parameter_start, parameter_words
    from .extract_client72 import Names
except ImportError:
    from extract_client72 import HASHES, read_verified, extract, PE, HEADER_VA, BODY, number, TypeResolver
    from extract_client72_literals import Literals72
    from client72_emulator import Offline72, OBJECT, STACK, GLOBAL
    from client72_scene_reader import SceneReader72
    from client72_session_runtime import fragment
    from trace_client72_scene_wait import declarations as scene_declarations, scalar_wire, blob_wire
    from client72_scene_handshake import REUSED_EVIDENCE
    from extract_client72_enums import recover as recover_enums
    from extract_client72_signatures import parameter_start, parameter_words
    from extract_client72 import Names

MASK32, MASK64 = 0xFFFFFFFF, 0xFFFFFFFFFFFFFFFF
SOURCE_HASHES = dict(REUSED_EVIDENCE)
SOURCE_HASHES['scene-handshake-observations.json'] = 'b2ff1ac423fc3f1685715a261452359265a7dd0bfdb2821b6af3c55866ad8847'
SCENE_ID_VALUES = (0, 1, 3, 0x80000000, 0xFFFFFFFF)
FLOAT_VECTORS = ((0x3FA00000, 0xC0200000, 0x40700000),
                 (0x80000000, 0x00000001, 0x7F7FFFFF), (0, 0, 0))


def source_evidence(root):
    reports = {}
    for name, digest in SOURCE_HASHES.items():
        data = (root / name).read_bytes()
        if hashlib.sha256(data).hexdigest() != digest: raise ValueError('Enter-scene input evidence changed: ' + name)
        if name.endswith('.json'):
            report = json.loads(data)
            if report['inputSHA256'] != HASHES: raise ValueError('Enter-scene source has different target hashes')
            reports[name] = report
    return reports


def declarations(binary, metadata, startup, records, reports):
    declared = scene_declarations(binary, metadata, startup, records)
    byid = {r['index']: r for r in records}; pe = PE(binary)
    vector = byid[2329]
    if (vector['namespace'], vector['name']) != ('UnityEngine', 'Vector3'):
        raise ValueError('Named Vector3 declaration drift')
    axes = {f['name']: f['offset'] for f in vector['fields'] if f['offsetKind'] == 'instance'}
    if axes != {'x': 16, 'y': 20, 'z': 24}: raise ValueError('Named Vector3 axis layout drift')
    task = next(f for f in byid[75453]['fields'] if f['offset'] == 0x44)
    if task['type'].get('typeDefinition') != 2329: raise ValueError('Loading task position no longer Vector3')
    labels = Literals72(binary, metadata); selected = []
    for index, site in ((45681, 0x153A3D3CD), (52972, 0x14CD67725)):
        item = next(r for r in labels.group(labels.global_group) if r['index'] == index)
        code = binary[pe.offset(site, 7):][:7]
        if code[:2] != b'\x48\x8b' or site + 7 + int.from_bytes(code[3:], 'little', signed=True) != int(item['cell'], 16):
            raise ValueError('Named scene/enter-type literal reference drift')
        selected.append(dict(item, value=labels.decode(index), site=hex(site)))
    methods = {int(m['registeredAddress'], 16): (r, m) for r in records for m in r['methods'] if m['registeredAddress'] != '0x0'}
    curr_owner, curr_method = methods[0x14FEE86B0]
    if curr_owner['name'] != 'ActorManager' or curr_method['name'] != 'GetCurrSceneId': raise ValueError('Named current-scene getter drift')
    # Assemble every raw field from independently fixed target observations;
    # no legacy/Asta field spelling or schema participates in this join.
    wait = reports['scene-wait-observations.json']; nested = reports['scene-nested-observations.json']
    raw = [dict(typeIndex=46311, clientType=byid[46311]['name'], fields=copy.deepcopy(wait['fields']))]
    raw.append(dict(typeIndex=84343, clientType=byid[84343]['name'], fields=copy.deepcopy(wait['positionFields'])))
    raw.extend(copy.deepcopy(nested['messages']))
    for message in raw:
        fields = [f for f in byid[message['typeIndex']]['fields'] if f['offsetKind'] == 'instance']
        for field in message['fields']:
            declaration = next(f for f in fields if f['offset'] == field['objectOffset'])
            field.setdefault('clientField', declaration['name'])
            field.setdefault('semanticStatus', 'unresolved')
            field.setdefault('wireStatus', 'emulated-observation')
            if 'wireType' not in field:
                field['wireType'] = 2 if field['type'] in ('message', 'string', 'bytes') or field['cardinality'] == 'repeated' else 5 if field['type'] == 'float' else 0
        if {(f['offset'], f['name']) for f in fields} != {(f['objectOffset'], f['clientField']) for f in message['fields']}:
            raise ValueError('Raw field source/declaration coverage differs')
    enums = recover_enums(binary, metadata, records, indices=(19400, 74697))['enums']
    modes = next(e for e in enums if e['typeIndex'] == 19400)
    event = next(v for e in enums if e['typeIndex'] == 74697 for v in e['values'] if v['value'] == 96)
    if event['symbol'] != 'EvtTransmitAvatar' or {v['value']: v['symbol'] for v in modes['values']}[3] != 'DirectlySetPos':
        raise ValueError('Named native transmit declaration drift')
    carrier = next(f for f in byid[19399]['fields'] if f['offset'] == 0x50)
    if carrier['type'].get('typeDefinition') != 2329: raise ValueError('Transmit carrier vector drift')
    quick = byid[9740]
    if quick['name'] != 'QuickTransmitData' or {f['name']: f['offset'] for f in quick['fields']} != {
        'centerBefore': 16, 'centerAfter': 40, 'yawDiff': 64, 'pitchDiff': 72, 'quaternion': 80}:
        raise ValueError('Named quick-transmit layout drift')
    h = pe.offset(HEADER_VA); names = Names(metadata, BODY + (number(binary, h + 0x70) ^ 0x18202CCC))
    method_base = BODY + (number(binary, h + 0xB0) ^ 0x51324A1F)
    parameter_base = BODY + ((number(binary, h + 0x174) + 0xAE6BCD68) & MASK32)
    method = methods[0x146E71F10][1]
    first = parameter_start(method['index'], number(metadata, method_base + method['index'] * 26 + 4))
    params = [names.decode(parameter_words(i, number(metadata, parameter_base + i * 8),
              number(metadata, parameter_base + i * 8 + 4))[0]) for i in range(first, first + method['parameterCount'])]
    if params != ['pos1', 'pos2', 'yaw']: raise ValueError('Named position constructor parameters drift')
    # The actual receiver checks the same class with CMP, not a RIP-relative MOV.
    site = 0x14B030582; code = binary[pe.offset(site, 7):][:7]
    if code[:3] != b'\x48\x3b\x05' or site + 7 + int.from_bytes(code[3:], 'little', signed=True) != 0x1458C5D10:
        raise ValueError('Concrete transmit receiver class comparison drift')
    return dict(identity=declared, rawSchemas=raw, vectorAxes=axes, vectorTaskField=task,
                namedLiterals=selected, namedSceneGetter=dict(owner=curr_owner['index'], method=curr_method),
                positionNaming=dict(carrierType=19399, carrierField=carrier, receiverType=62249,
                    receiver='0x14b030540', classComparison=hex(site), classCell='0x1458c5d10',
                    event=event, modes=modes, namedType=9740, namedTypeName=quick['name'],
                    namedConstructor='0x146e71f10', parameters=params, fields=quick['fields']))


def scene_id_probes(e, reader):
    rows = []
    for entry in (0x1507AD6F0, 0x153A3CD60, 0x14FEE86B0): e.allow(entry)
    for expected in SCENE_ID_VALUES:
        raw = ((expected - 0xC0E3) & MASK32) ^ 0x2D03
        result = reader.run_scene(scalar_wire(7, raw))
        player = e.alloc(1024); e.write(GLOBAL + 0x9E80, player)
        store = fragment(e, 0x1507AD82C, {0x1507AD841}, dict(rsp=STACK + 0x100008, r14=OBJECT, rsi=player))
        if e.read(player + 0x1D0, 'I') != expected: raise ValueError('Notify scene-id native store differs')
        # The diagnostic calls this exact PlayerModule field tryEnterSceneId.
        # Supply the explicitly different current-scene live-in, not a success predicate.
        current = expected ^ 0x40000000; frame = STACK + 0xFF000
        e.write(player + 0x1E4, current, 'I'); e.write(frame + 0x10, current, 'I')
        boxed = []
        def box():
            value = e.read(e.reg('rdx'), 'I'); pointer = e.alloc(); boxed.append((pointer, value)); return pointer
        e.models[0x140525C40] = box
        diagnostic = fragment(e, 0x153A3D378, {0x159AB12F0}, dict(rsp=STACK + 0xFEF00, rbp=frame, rax=player, r14=current))
        arguments = [e.read(e.reg('r8') + i * 8) for i in (0, 1)]
        if [value for pointer, value in boxed] != [current, expected] or arguments != [pointer for pointer, value in boxed] or e.reg('rdx') != e.read(0x145B231A8):
            raise ValueError('Scene id failed independent named diagnostic binding')
        e.run(0x14FEE86B0, e.alloc())
        if e.reg('eax') != current: raise ValueError('Named current-scene getter conflated with entering scene')
        rows.append(dict(name='scene-id-' + str(expected), raw=raw, sceneId=expected, currentSceneId=current,
                         namedDiagnosticValues=[current, expected], source=result, store=store, diagnostic=diagnostic))
    return rows


def position_probes(e, reader):
    rows = []; e.allow(0x14CD67260)
    for bits in FLOAT_VECTORS:
        # Independent raw bytes: target nested tags are x=1,y=2,z=3.
        position = b''.join(bytes((tag << 3 | 5,)) + struct.pack('<I', value) for tag, value in zip((1, 2, 3), bits))
        result = reader.run_scene(blob_wire(14, position))
        task = e.alloc(256)
        trace = fragment(e, 0x14CD673B8, {0x14CD673F0}, dict(rsp=STACK + 0x100008, rsi=OBJECT, rbx=task))
        actual = [e.read(task + off, 'I') for off in (0x44, 0x48, 0x4C)]
        if actual != list(bits): raise ValueError('Native nested-to-Vector3 axis mapping changed')
        rows.append(dict(name='vector-' + str(len(rows)), vectorXYZBits=list(bits), taskVectorXYZBits=actual,
                         source=result, trace=trace, scope='Notify+18 -> actual typed LoadingTask Vector3; later spawn is not executed'))
    result = reader.run_scene(b'')
    trace = fragment(e, 0x14CD673B8, {0x1404C2BE0}, dict(rsp=STACK + 0x100008, rsi=OBJECT, rbx=e.alloc()))
    if '0x14cd676d4' not in trace['executedAddresses']: raise ValueError('Missing position reached different null branch')
    rows.append(dict(name='missing-position-original-null', source=result, trace=trace, scope='actual required pointer null branch; not a successful scene entry'))
    return rows


def enter_type_probes(e, reader):
    rows = []
    branches = {1: 0x14CD637B0, 2: 0x14CD67990, 3: 0x14CD637B0, 4: 0x14CD637B0,
                5: 0x14CD637B0, 6: 0x14CD637B0, 7: 0x14CD67990, 8: 0x14CD67990,
                9: 0x14CD637B0, 10: 0x14CD637B0, 11: 0x14CD637B0, 12: 0x14CD67990,
                13: 0x14CD637B0, 14: 0x14CD637B0}
    e.allow(0x14CD67260)
    for value in (*range(15), -1, 15, 0x7FFFFFFF):
        result = reader.run_scene(scalar_wire(8, value & MASK64))
        trace = fragment(e, 0x14CD6753D, {0x14CD637B0, 0x14CD67990, 0x147161B60},
                         dict(rsp=STACK + 0x100008, rsi=OBJECT, rdi=e.alloc(1024)))
        expected = branches.get(value, 0x147161B60)
        if trace['stop'] != hex(expected): raise ValueError('Target Enter enum dispatch differs')
        if expected != 0x147161B60 and e.reg('rdx') != OBJECT: raise ValueError('Enter branch replaced notification object')
        rows.append(dict(enterType=value, destination=trace['stop'], source=result, trace=trace,
                         enumValueKnown=0 <= value <= 14, diagnostic=value not in branches))
    return rows


def position_consumption_probes(e, reader):
    rows = []
    for entry in (0x14CD67260, 0x14CD67990, 0x14CD64860, 0x14CD64E90, 0x14CD62630,
                  0x148B16AE0, 0x14E4DED50, 0x14B030540, 0x14B031540, 0x14B030B70, 0x146E71F10):
        e.allow(entry)
    carrier_class = e.synthetic_type(0x1458C5D10)
    reader.heap_floor = e.heap_cursor
    for bits in FLOAT_VECTORS:
        for flag in (0, 1):
            position = b''.join(bytes((tag << 3 | 5,)) + struct.pack('<I', value) for tag, value in zip((1, 2, 3), bits))
            source = reader.run_scene(blob_wire(14, position) + scalar_wire(8, 2) + scalar_wire(1345, flag))
            task, loader, game, level = e.alloc(), e.alloc(1024), e.alloc(512), e.alloc(1024)
            e.write(GLOBAL + 0xB330, game); e.write(game + 0x60, level); e.write(level + 0x18, 2, 'I')
            e.write(GLOBAL + 0xA370, 0)
            queue, items, array, carrier, receiver = e.alloc(), e.alloc(), e.alloc(64), e.alloc(), e.alloc()
            e.write(carrier, carrier_class); e.write(GLOBAL + 0xA840, queue); e.write(queue + 0x40, items)
            e.write(items + 0x10, array); e.write(array + 0x18, 4, 'I')
            borrowed = []
            def borrow():
                borrowed.append(e.reg('rcx')); return carrier
            e.models[0x1547A9320] = borrow
            transferred = fragment(e, 0x14CD673B8, {0x14CD673F0}, dict(rsp=STACK + 0xFF008, rsi=OBJECT, rbx=task))
            queued = fragment(e, 0x14CD67C1C, {0x14CD67C6D},
                dict(rsp=STACK + 0xFF008, rbx=task, rsi=OBJECT, r14=loader, rbp=2))
            mode = e.read(carrier + 0x60, 'I')
            if (mode != flag * 3 or e.read(carrier + 0x14, 'I') != 96 or len(borrowed) != 1 or
                    e.read(items + 0x18, 'I') != 1 or e.read(array + 0x20) != carrier):
                raise ValueError('Original transmit construction/queue differs')
            # Explicit handoff of the actual queue element to the concrete receiver.
            # Event manager registration/scheduling is outside this native boundary.
            routed = fragment(e, 0x14B030540, {0x14B031540, 0x1404C2BE0},
                dict(rsp=STACK + 0xFE008, rcx=receiver, rdx=e.read(array + 0x20)))
            argument = e.reg('rdx'); vector = [e.read(argument + i * 4, 'I') for i in range(3)]
            if routed['stop'] != '0x14b031540' or vector != list(bits) or e.reg('r9') != mode:
                raise ValueError('Concrete receiver changed the original position argument')
            stored = fragment(e, 0x14B0316B7 if mode == 3 else 0x14B031716,
                {0x14B0316C5 if mode == 3 else 0x14B031724},
                dict(rsp=STACK + 0xFE008, r15=argument, rsi=receiver))
            if [e.read(receiver + 0x60 + i * 4, 'I') for i in range(3)] != list(bits):
                raise ValueError('Default/DirectlySetPos native vector store differs')
            rows.append(dict(kind='notify-mode', vectorXYZBits=list(bits), rawFlag1345=flag, mode=mode,
                modeName='DirectlySetPos' if mode else 'Default', receiverVectorXYZBits=vector,
                eventValue=96, originalCarrierQueued=True, queueCount=1, poolBorrowCount=1,
                source=source, traces=[transferred, queued, routed, stored]))
        # Independently exercise the same concrete field in QuickV3. Only the
        # explicit control-mode fixture changes; this is NOT the Notify mode0/3 path.
        quick_carrier = e.alloc(); e.uc.mem_write(quick_carrier, bytes(e.uc.mem_read(carrier, 256)))
        e.write(quick_carrier + 0x60, 7, 'I')
        fast = fragment(e, 0x14B030540, {0x14B030B70, 0x1404C2BE0},
            dict(rsp=STACK + 0xFE008, rcx=receiver, rdx=quick_carrier))
        if fast['stop'] != '0x14b030b70': raise ValueError('QuickV3 concrete branch changed')
        argument = e.reg('rdx'); before = (0x41200000, 0x41A00000, 0x41F00000)
        frame = STACK + 0xFD008
        e.write(frame + 0x60, (before[1] << 32) | before[0]); e.write(frame + 0x68, before[2], 'I')
        # The current-position getter and rotation getter are not modeled. This
        # is their declared before-position live-in, deliberately different from pos2.
        selected = fragment(e, 0x14B030CBD, {0x14B030D04}, dict(rsp=frame, rdi=argument, rbp=0))
        registers = {name: e.reg(name) for name in ('xmm7', 'xmm8', 'xmm9', 'xmm10')}
        converted = fragment(e, 0x14B030D98, {0x146E71F10}, dict(rsp=frame, **registers, xmm11=0))
        expected = [struct.unpack('<Q', struct.pack('<d', struct.unpack('<f', struct.pack('<I', value))[0]))[0] for value in bits]
        actual = [e.read(e.reg('r8') + i * 8) for i in range(3)]
        original = [e.read(e.reg('rdx') + i * 8) for i in range(3)]
        if actual != expected or actual == original: raise ValueError('Named pos2 argument is not the original vector')
        destination = e.reg('rcx')
        constructor = fragment(e, 0x146E71F10, {0x146E71F5D},
            dict(rsp=STACK + 0xFC008, rcx=destination, rdx=e.reg('rdx'), r8=e.reg('r8'), xmm3=e.reg('xmm3')))
        after = [e.read(destination + 0x18 + i * 8) for i in range(3)]
        if after != actual: raise ValueError('Named centerAfter native store differs')
        rows.append(dict(kind='independent-quickv3-naming', vectorXYZBits=list(bits), mode=7, modeName='QuickV3',
            notifyPath=False, namedPos1Bits=original, namedPos2Bits=actual, centerAfterBits=after,
            traces=[fast, selected, converted, constructor]))
    e.models.pop(0x1547A9320)
    return rows


def unbound_consumption_probes(e, reader):
    rows = []
    for entry in (0x14CD637B0, 0x14CD67260): e.allow(entry)
    for value32, value64 in ((0, 0), (1, 1), (0x80000000, 0xFEDCBA9876543210), (MASK32, MASK64)):
        raw_world = ((value32 ^ 0x7B5B) + 0x4DA1) & MASK32
        raw_uid = ((value32 - 0x7633) & MASK32) ^ 0x1031
        raw_time = ((value64 ^ 0x6CC3) + 0x416D) & MASK64
        raw_dungeon = ((value32 ^ 0x6B98) - 0x45A5) & MASK32
        wire = scalar_wire(2, raw_world) + scalar_wire(11, raw_uid) + scalar_wire(6, raw_time) + scalar_wire(1, raw_dungeon)
        result = reader.run_scene(wire); owner, player, task = e.alloc(1024), e.alloc(1024), e.alloc(256)
        world = fragment(e, 0x14CD6381F, {0x14CD63847}, dict(rsp=STACK + 0x100008, rsi=OBJECT, rdx=owner))
        clock = fragment(e, 0x14CD63858, {0x14CD6387E}, dict(rsp=STACK + 0x100008, rsi=OBJECT))
        uid = fragment(e, 0x14CD63926, {0x14CD6393D}, dict(rsp=STACK + 0x100008, rsi=OBJECT, rax=player))
        dungeon = fragment(e, 0x14CD6739D, {0x14CD673B0}, dict(rsp=STACK + 0x100008, rsi=OBJECT, rbx=task))
        observed = dict(manager100=e.read(owner + 0x100, 'I'), player1d8=e.read(player + 0x1D8, 'I'),
                        static145873388=e.read(0x145873388), task28=e.read(task + 0x28, 'I'))
        if observed != dict(manager100=value32, player1d8=value32, static145873388=value64, task28=value32):
            raise ValueError('Unbound native scalar arithmetic differs')
        rows.append(dict(value32=value32, value64=value64, observed=observed, source=result,
                         traces=[world, clock, uid, dungeon], semanticStatus='unresolved', productionAllowed=False))
    return rows


def recover(binary, metadata, startup, records, libraries):
    root = Path(__file__).resolve().parents[1] / 'protocol/cn-7.2'
    reports = source_evidence(root)
    report = dict(schemaVersion=1, inputSHA256=HASHES, evidenceSHA256=SOURCE_HASHES,
                  runtimeStatus='not-tested', targetProtocolReady=False, completeSenderContract=False,
                  minimumSenderFieldContract=['scene_id', 'type', 'enter_scene_token', 'pos'],
                  scope='Four named Notify sender fields; native mode0/3 receiver and independent named QuickV3 position; other scalar roles remain blocked',
                  **declarations(binary, metadata, startup, records, reports))
    e = Offline72(binary, libraries, records, True); reader = SceneReader72(e)
    try:
        report['sceneIdFixtures'] = scene_id_probes(e, reader)
        report['positionFixtures'] = position_probes(e, reader)
        report['enterTypeFixtures'] = enter_type_probes(e, reader)
        report['unboundConsumerFixtures'] = unbound_consumption_probes(e, reader)
        report['positionConsumptionFixtures'] = position_consumption_probes(e, reader)
        report['constructorDefaults'] = reader.run_scene(b'')['constructorDefaults']
    finally: reader.close(); e.close()
    token = next(b for b in reports['scene-handshake-observations.json']['semanticBindings'] if b['typeIndex'] == 46311)
    report['semanticBindings'] = [
        dict(typeIndex=46311, objectOffset=124, tag=7, name='scene_id', status='verified-offline',
             wireToApplication='u32((raw XOR 0x2d03) + 0xc0e3)',
             evidence='Actual reader -> PlayerModule+1D0 native store -> independent tryEnterSceneId named diagnostic'),
        dict(typeIndex=46311, objectOffset=152, tag=8, name='type', status='verified-offline',
             wireToApplication='identity int32; target Enter enum86551,15 exact symbols',
             evidence='Target enum defaults and actual native Enter dispatch including unknown-value diagnostics'),
        copy.deepcopy(token),
        dict(typeIndex=46311, objectOffset=24, tag=14, name='pos', nestedTypeIndex=84343,
             status='verified-offline', wireToApplication='nested float32 x/y/z bits unchanged into UnityEngine.Vector3',
             evidence='Actual reader -> native task/carrier/queue -> concrete Default/DirectlySetPos receiver; independent same-field QuickV3 -> named pos2/centerAfter',
             requiredNonNull=True, runtimePositionApplied=False),
        *[dict(typeIndex=84343, objectOffset=offset, tag=tag, name=name, status='verified-offline',
               wireToApplication='identity float32 bits', evidence='Actual nested reader -> typed UnityEngine.Vector3 member declaration and native transfer')
          for name, tag, offset in (('x', 1, 24), ('y', 2, 32), ('z', 3, 28))],
    ]
    report['unresolvedSenderFields'] = [
        dict(candidateName='world_level', typeIndex=46311, objectOffset=140, tag=2,
             wireToObservedStorage='u32(raw - 0x4da1) XOR 0x7b5b -> manager+100', productionAllowed=False),
        dict(candidateName='scene_begin_time', typeIndex=46311, objectOffset=104, tag=6,
             wireToObservedStorage='u64(raw - 0x416d) XOR 0x6cc3 -> static145873388', productionAllowed=False),
        dict(candidateName='target_uid', typeIndex=46311, objectOffset=128, tag=11,
             wireToObservedStorage='u32((raw XOR 0x1031) + 0x7633) -> PlayerModule+1D8', productionAllowed=False),
        dict(candidateName='dungeon_id', typeIndex=46311, objectOffset=160, tag=1,
             wireToObservedStorage='u32(raw + 0x45a5) XOR 0x6b98 -> LoadingTask+28', productionAllowed=False),
        dict(candidateName='scene_transaction', typeIndex=46311, objectOffset=56, tag=293,
             evidence='Raw string copied to LoadingTask+10; business name unbound', productionAllowed=False),
        dict(candidateName='limited_region_info', evidence='Existing runtime WorldRegions setter has no independent target semantic binding', productionAllowed=False),
    ]
    report['outsideScope'] = [
        'No full scene entry, sender business closure, scheduler, loaded client, transport, service deployment or D-02.',
        'Native code fragments have explicit live-in context; parent/caller prefixes outside each fragment are not claimed.',
        'Boxing and string formatting are capture boundaries; named current-scene getter executes target instructions.',
        'Existing raw reader/all nested evidence is hash-pinned; repeated uint64/list content comes from those separately declared prior samples.',
        'Unknown scalar roles remain unknown even when arithmetic matches old server/Asta code. Do not send those candidate values.',
        'Whole raw schema presence cannot erase encoded constructor defaults; missing and explicit zero remain distinct.',
        'Position queue-to-concrete-receiver handoff is explicit; actual listener registration and scheduler are not executed.',
        'QuickV3 is an independent same-field naming fixture with mode7; Notify mode0/3 do not execute that branch.',
        'Position pool borrow supplies zeroed storage with the declared class; unknown game helpers are never modeled as successful.',
    ]
    return report


def summarize(report):
    excluded = ('sceneIdFixtures', 'positionFixtures', 'enterTypeFixtures', 'unboundConsumerFixtures', 'positionConsumptionFixtures')
    result = {k: v for k, v in report.items() if k not in excluded}
    result['sceneIdObservations'] = [dict(raw=r['raw'], sceneId=r['sceneId'], currentSceneId=r['currentSceneId'],
        namedDiagnosticValues=r['namedDiagnosticValues'], storePathSHA256=r['store']['nativePathSHA256'],
        diagnosticPathSHA256=r['diagnostic']['nativePathSHA256']) for r in report['sceneIdFixtures']]
    result['positionObservations'] = [{k: v for k, v in r.items() if k not in ('source', 'trace')} |
        dict(stop=r['trace']['stop'], nativePathSHA256=r['trace']['nativePathSHA256']) for r in report['positionFixtures']]
    result['enterTypeObservations'] = [{k: v for k, v in r.items() if k not in ('source', 'trace')} |
        dict(nativePathSHA256=r['trace']['nativePathSHA256']) for r in report['enterTypeFixtures']]
    result['unboundConsumerObservations'] = [{k: v for k, v in r.items() if k not in ('source', 'traces')} |
        dict(nativePathSHA256=[t['nativePathSHA256'] for t in r['traces']]) for r in report['unboundConsumerFixtures']]
    result['positionConsumptionObservations'] = [{k: v for k, v in r.items() if k not in ('source', 'traces')} |
        dict(stops=[t['stop'] for t in r['traces']], nativePathSHA256=[t['nativePathSHA256'] for t in r['traces']])
        for r in report['positionConsumptionFixtures']]
    return result


def validate_summary(summary):
    if summary['inputSHA256'] != HASHES or summary['evidenceSHA256'] != SOURCE_HASHES:
        raise ValueError('Enter-scene evidence inputs changed')
    if summary['runtimeStatus'] != 'not-tested' or summary['targetProtocolReady'] or summary['completeSenderContract']:
        raise ValueError('Enter-scene runtime/sender scope overstated')
    if len(summary['rawSchemas']) != 8 or sum(len(m['fields']) for m in summary['rawSchemas']) != 44:
        raise ValueError('Complete raw scene schema family missing')
    pinned = source_evidence(Path(__file__).resolve().parents[1] / 'protocol/cn-7.2')
    outer = pinned['scene-wait-observations.json']
    position = next(m for m in pinned['scene-handshake-observations.json']['messages'] if m['typeIndex'] == 84343)
    expected = {46311: outer['fields'], 84343: position['fields']}
    expected.update({m['typeIndex']: m['fields'] for m in pinned['scene-nested-observations.json']['messages']})
    if {m['typeIndex'] for m in summary['rawSchemas']} != set(expected): raise ValueError('Raw scene schema types changed')
    for message in summary['rawSchemas']:
        def signature(fields):
            return {(f['objectOffset'], f['tag'], f['clientField'], f['type'], f['cardinality'], f.get('nestedTypeIndex')) for f in fields}
        if signature(message['fields']) != signature(expected[message['typeIndex']]): raise ValueError('Pinned raw scene wire field changed')
    if {(b['typeIndex'], b['tag'], b['name']) for b in summary['semanticBindings']} != {
        (46311, 7, 'scene_id'), (46311, 8, 'type'), (46311, 10, 'enter_scene_token'),
        (46311, 14, 'pos'),
        (84343, 1, 'x'), (84343, 2, 'y'), (84343, 3, 'z')}:
        raise ValueError('Unreviewed scene business semantics promoted')
    if [r['sceneId'] for r in summary['sceneIdObservations']] != list(SCENE_ID_VALUES): raise ValueError('Scene-id boundary samples incomplete')
    for row in summary['sceneIdObservations']:
        if ((row['raw'] ^ 0x2D03) + 0xC0E3) & MASK32 != row['sceneId'] or row['namedDiagnosticValues'] != [row['currentSceneId'], row['sceneId']]:
            raise ValueError('Named scene-id data flow changed')
    position = summary['positionObservations']
    if len(position) != 4 or [r['vectorXYZBits'] for r in position[:3]] != [list(v) for v in FLOAT_VECTORS]: raise ValueError('Vector boundaries incomplete')
    if any(r['vectorXYZBits'] != r['taskVectorXYZBits'] for r in position[:3]) or position[-1]['stop'] != '0x1404c2be0': raise ValueError('Vector/null behavior changed')
    if summary['minimumSenderFieldContract'] != ['scene_id', 'type', 'enter_scene_token', 'pos']:
        raise ValueError('Minimum sender field scope changed')
    consumption = summary['positionConsumptionObservations']
    native = [r for r in consumption if r['kind'] == 'notify-mode']
    named = [r for r in consumption if r['kind'] == 'independent-quickv3-naming']
    if len(native) != 6 or len(named) != 3: raise ValueError('Position naming samples incomplete')
    for row, (bits, flag) in zip(native, ((list(v), f) for v in FLOAT_VECTORS for f in (0, 1))):
        if (row['vectorXYZBits'] != bits or row['receiverVectorXYZBits'] != bits or row['rawFlag1345'] != flag or
                row['mode'] != flag * 3 or row['eventValue'] != 96 or not row['originalCarrierQueued'] or
                row['queueCount'] != 1 or row['stops'][2] != '0x14b031540'):
            raise ValueError('Notify position receiver proof changed')
    for row, bits in zip(named, FLOAT_VECTORS):
        expected = [struct.unpack('<Q', struct.pack('<d', struct.unpack('<f', struct.pack('<I', value))[0]))[0] for value in bits]
        if (row['mode'] != 7 or row['notifyPath'] or row['namedPos2Bits'] != expected or row['centerAfterBits'] != expected or
                row['namedPos1Bits'] == expected or row['stops'][-2:] != ['0x146e71f10', '0x146e71f5d']):
            raise ValueError('Independent named position branch changed')
    if len(summary['enterTypeObservations']) != 18: raise ValueError('Enter enum boundary samples incomplete')
    if len(summary['unboundConsumerObservations']) != 4 or any(r['productionAllowed'] or r['semanticStatus'] != 'unresolved' for r in summary['unboundConsumerObservations']):
        raise ValueError('Unknown scalar consumers promoted')
    if any(r['productionAllowed'] for r in summary['unresolvedSenderFields']): raise ValueError('Unknown sender values enabled')
    if summary['unboundConsumerObservations'][-1]['observed']['static145873388'] != MASK64: raise ValueError('Uint64 boundary truncated')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ('client', 'output', 'summary-output'): parser.add_argument('--' + name, type=Path, required=True)
    parser.add_argument('--libraries', type=Path, default=Path('local/migration72/python'))
    args = parser.parse_args()
    if any(p.resolve().is_relative_to(args.client.resolve()) for p in (args.output, args.summary_output)): parser.error('Outputs must be outside client')
    binary, metadata, startup = [read_verified(args.client / name, digest) for name, digest in HASHES.items()]
    records, _ = extract(binary, metadata, startup=startup, structures=True)
    report = recover(binary, metadata, startup, records, args.libraries); summary = summarize(report); validate_summary(summary)
    for path, value in ((args.output, report), (args.summary_output, summary)):
        path.parent.mkdir(parents=True, exist_ok=True); path.write_text(json.dumps(value, indent=2) + '\n', 'utf8')
    print(json.dumps(dict(rawSchemas=8, rawFields=44, sceneId=5, vector=4, enterType=18,
        unresolvedConsumers=4, nativePosition=6, namedPosition=3, minimumSenderFields=4, fullSenderReady=False)))


if __name__ == '__main__': main()
