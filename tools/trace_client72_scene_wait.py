"""B-02e2: target scene notification reader and born-cache waiting boundaries.

Actual native instructions, explicit managed storage/config inputs and observed
stops. This does not execute complete scene entry, media scheduling or sockets.
"""
import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path
import struct
import subprocess
import tempfile
try:
    from .extract_client72 import HASHES, read_verified, extract, PE, HEADER_VA, BODY, number, TypeResolver
    from .extract_client72_signatures import parameter_start, parameter_words
    from .extract_client72_literals import Literals72
    from .extract_client72_enums import recover as enum_defaults
    from .client72_emulator import Offline72, OBJECT, STREAM, GLOBAL, STACK
    from .client72_scene_reader import SceneReader72, SCENE, POSITION, READER, CONSTRUCTOR, CLONE, COPY, POSITION_READER
    from .client72_session_runtime import fragment
    from .trace_client72_responses import Reader72, bind_fields
    from .trace_client72_token import varint
except ImportError:
    from extract_client72 import HASHES, read_verified, extract, PE, HEADER_VA, BODY, number, TypeResolver
    from extract_client72_signatures import parameter_start, parameter_words
    from extract_client72_literals import Literals72
    from extract_client72_enums import recover as enum_defaults
    from client72_emulator import Offline72, OBJECT, STREAM, GLOBAL, STACK
    from client72_scene_reader import SceneReader72, SCENE, POSITION, READER, CONSTRUCTOR, CLONE, COPY, POSITION_READER
    from client72_session_runtime import fragment
    from trace_client72_responses import Reader72, bind_fields
    from trace_client72_token import varint

HANDLER, GATE, CACHE, DISPATCH = 0x1507AD6F0, 0x1507B32D0, 0x14CD67260, 0x1507CC240
WRAPPER = 0x1507AD120
SCALARS = ((0xA0, 1, 'uint32'), (0x8C, 2, 'uint32'), (0xA4, 5, 'uint32'), (0x68, 6, 'uint64'),
           (0x7C, 7, 'uint32'), (0x98, 8, 'int32'), (0x94, 10, 'uint32'), (0x80, 11, 'uint32'),
           (0x84, 12, 'bool'), (0x74, 46, 'uint32'), (0x9C, 157, 'uint32'), (0x90, 941, 'uint32'),
           (0x88, 957, 'uint32'), (0x85, 1345, 'bool'), (0x78, 1583, 'uint32'), (0x70, 2008, 'uint32'))
CHILDREN = ((0x18, 14, POSITION), (0x50, 15, POSITION), (0x60, 395, 66040), (0x58, 739, 67761),
            (0x30, 763, POSITION), (0x40, 1357, 34133), (0x48, 1723, 69612), (0x28, 1964, 25866))
POSITION_TAGS = {24: 1, 32: 2, 28: 3}
LITERALS = {0x145B2BB70: (50090, 'Genshin Loading Log: PlayerModule OnPlayerEnterSceneNotify'),
            0x145B23B68: (45993, 'Genshin Loading Log: OnReceivePlayerEnterSceneNotify {0}'),
            0x145B316F8: (53019, 'Genshin Loading Log: LoadingManager OnPlayerEnterSceneNotify'),
            0x145B23C58: (46023, 'Beyond Transport Interrupt EnterDungeon, Invoke Client Reconnect')}


def declarations(binary, metadata, startup, records):
    pe = PE(binary); header = pe.offset(HEADER_VA); byid = {r['index']: r for r in records}
    method_base = BODY + (number(binary, header + 0xB0) ^ 0x51324A1F)
    parameter_base = BODY + ((number(binary, header + 0x174) + 0xAE6BCD68) & 0xFFFFFFFF)
    resolver = TypeResolver(pe, startup, records, header)
    expected = {(off, 'valuetype' if off == 0x98 else kind, 86551 if off == 0x98 else None) for off, _, kind in SCALARS}
    expected |= {(off, 'class', child) for off, _, child in CHILDREN} | {(0x20, 'genericinst', 35584), (0x38, 'string', None)}
    row = byid[SCENE]
    actual = {(f['offset'], f['type']['kind'], f['type'].get('typeDefinition')) for f in row['fields'] if f['offsetKind'] == 'instance'}
    if actual != expected or len(actual) != 26: raise ValueError('Scene instance declaration drift')
    getter = next(m for m in row['methods'] if m.get('virtualSlot') == 22)
    if getter['registeredAddress'] != '0x1523856a0' or getter.get('literalU16Candidate') != 27748:
        raise ValueError('Scene command binding drift')
    targets = []
    for address, owner in ((WRAPPER, 56238), (HANDLER, 56238), (GATE, 56238), (CACHE, 18753), (READER, SCENE),
                           (CONSTRUCTOR, SCENE), (CLONE, SCENE), (COPY, SCENE), (POSITION_READER, POSITION)):
        methods = [m for m in byid[owner]['methods'] if m['registeredAddress'] == hex(address)]
        if len(methods) != 1: raise ValueError('Scene native owner/method drift')
        m = methods[0]; item = dict(entry=hex(address), ownerType=owner, methodIndex=m['index'])
        if address in (WRAPPER, HANDLER, GATE, CACHE):
            first = parameter_start(m['index'], number(metadata, method_base + m['index'] * 26 + 4))
            _, ref = parameter_words(first, number(metadata, parameter_base + first * 8), number(metadata, parameter_base + first * 8 + 4))
            if m['parameterCount'] != 1 or resolver.resolve_index(ref).get('typeDefinition') != SCENE:
                raise ValueError('Scene handler/cache parameter drift')
            item['parameterIndex'] = first
        item['entry32SHA256'] = hashlib.sha256(binary[pe.offset(address, 32, executable=True):][:32]).hexdigest()
        targets.append(item)
    at = pe.offset(0x1507C2A03, 12, executable=True); code = binary[at:at + 12]
    if code[:8] != bytes.fromhex('66ba646c4989f0e8') or 0x1507C2A0F + int.from_bytes(code[8:], 'little', signed=True) != 0x1532ED560:
        raise ValueError('Scene network registration drift')
    literals = Literals72(binary, metadata)
    selected = []
    for item in literals.group(literals.global_group):
        cell = int(item['cell'], 16)
        if cell not in LITERALS: continue
        index, text = LITERALS[cell]
        if item['index'] != index or literals.decode(index) != text: raise ValueError('Scene identity literal drift')
        selected.append(dict(item, value=text))
    if len(selected) != len(LITERALS): raise ValueError('Missing scene identity literal')
    enum = enum_defaults(binary, metadata, records, indices=(86551,))['enums'][0]
    if enum['values'][2]['symbol'] != 'EnterGoto' or enum['values'][2]['value'] != 2 or len(enum['values']) != 15:
        raise ValueError('Scene Enter enum drift')
    return dict(typeIndex=SCENE, clientType=row['name'], cmdId=27748, getter=getter['registeredAddress'],
                semanticName='PlayerEnterSceneNotify', semanticEvidence='typed network handler plus native entry log and literal usage',
                runtimeStatus='not-tested', registration='0x1507c2a03', methods=targets, literals=selected, enum=enum,
                fields=[dict(clientField=f['name'], objectOffset=f['offset'], declaration=f['type']) for f in row['fields'] if f['offsetKind'] == 'instance'])


def recover_outer(binary, records, libraries):
    e = Offline72(binary, libraries, records, False); reader = Reader72(e)
    try:
        e.synthetic_type(0x1458A8C10); codec = e.alloc(); e.write(GLOBAL + 0xFC90, codec)
        reader.codecs[codec] = (SCENE, 13); reader.heap_floor = e.heap_cursor
        samples = []
        for wire in (0, 2, 1, 5):
            for start in range(1, 2048, 64):
                data = b''.join(varint(t << 3 | wire) + (varint(300 + t) if wire == 0 else b'\0' if wire == 2 else
                    bytes.fromhex('000000000000f43f') if wire == 1 else bytes.fromhex('0000a03f'))
                    for t in range(start, min(start + 64, 2048)))
                samples.append(reader.run(SCENE, data, READER))
        fields = bind_fields(e.records[SCENE], samples)
        expected = {off: tag for off, tag, _ in SCALARS + CHILDREN} | {32: 13, 56: 293}
        if {f['objectOffset']: f['tag'] for f in fields} != expected: raise ValueError('Scene native outer binding drift')
        for field in fields:
            if field['objectOffset'] == 0x98: field.update(type='int32', enumTypeIndex=86551)
        def float32():
            reader.sync(); bits = int.from_bytes(reader.input.take(4), 'little'); reader.position()
            e.uc.reg_write(e.R.UC_X86_REG_XMM0, bits); return 0
        e.models[0x1474E97E0] = float32
        position_wire = b''.join(varint(tag << 3 | 5) + struct.pack('<f', tag + 0.25) for tag in (1, 2, 3))
        position = reader.run(POSITION, position_wire, POSITION_READER)
        found = {v['objectOffset']: v['key'] >> 3 for v in position['events']}
        if found != POSITION_TAGS or any(v['key'] & 7 != 5 for v in position['events']): raise ValueError('Position float binding drift')
        return fields, samples, position
    finally:
        reader.close(); e.close()


def scalar_wire(tag, value):
    return varint(tag << 3) + varint(value & 0xFFFFFFFFFFFFFFFF)


def blob_wire(tag, raw):
    return varint(tag << 3 | 2) + varint(len(raw)) + raw


def encode_fixture(values):
    encoded = {}
    for off, tag, kind in SCALARS:
        if off in values and values[off]: encoded[tag] = scalar_wire(tag, values[off])
    if values.get(0x20): encoded[13] = blob_wire(13, b''.join(varint(v) for v in values[0x20]))
    if values.get(0x38): encoded[293] = blob_wire(293, values[0x38].encode('utf8'))
    for off, tag, child in CHILDREN:
        if off not in values: continue
        if child != POSITION: raise ValueError('Opaque child content is not a fixture schema')
        data = b''.join(varint(t << 3 | 5) + struct.pack('<f', values[off][p])
                        for p, t in sorted(POSITION_TAGS.items(), key=lambda x: x[1]) if values[off].get(p))
        encoded[tag] = blob_wire(tag, data)
    return b''.join(encoded[tag] for tag in sorted(encoded))


def fixture_proto():
    lines = ['// Wire fixture carrier, not a complete target schema. Opaque nested messages use bytes.',
             'syntax="proto3"; package cn72.scene_fixture;', 'message Position { float f_18=1; float f_20=2; float f_1c=3; }', 'message Outer {']
    for off, tag, kind in SCALARS: lines.append(f'  {kind} f_{off:x}={tag};')
    lines.extend(['  repeated uint32 f_20=13 [packed=true];', '  string f_38=293;'])
    for off, tag, child in CHILDREN: lines.append(f'  {"Position" if child == POSITION else "bytes"} f_{off:x}={tag};')
    return '\n'.join(lines + ['}', ''])


def protoc_fixture(protoc, proto, values):
    lines = []
    for off, _, kind in SCALARS:
        if off in values: lines.append(f'f_{off:x}: ' + (str(bool(values[off])).lower() if kind == 'bool' else str(values[off])))
    lines.extend(f'f_20: {value}' for value in values.get(0x20, []))
    if 0x38 in values: lines.append('f_38: "' + ''.join('\\%03o' % v for v in values[0x38].encode('utf8')) + '"')
    for off, _, child in CHILDREN:
        if off in values:
            lines.append(f'f_{off:x} {{ ' + ' '.join(f'f_{p:x}: {v}' for p, v in values[off].items()) + ' }')
    command = [str(protoc), '--proto_path=' + str(proto.parent), '--encode=cn72.scene_fixture.Outer', str(proto)]
    return subprocess.run(command, input='\n'.join(lines).encode(), capture_output=True, check=True).stdout


def verify_stored(result, values):
    expected = json.loads(json.dumps(result['constructorDefaults']))
    for off, _, kind in SCALARS:
        if off in values: expected['rawStoredScalars'][hex(off)] = int(bool(values[off])) if kind == 'bool' else values[off] & ((1 << (64 if kind == 'uint64' else 32)) - 1)
    expected['repeatedUint32'] = values.get(0x20, [])
    expected['stringUTF8Hex'] = values.get(0x38, '').encode('utf8').hex()
    for off, _, child in CHILDREN:
        if off in values:
            expected['positionFloat32Bits'][hex(off)] = [int.from_bytes(struct.pack('<f', values[off].get(p, 0)), 'little') for p in (24, 28, 32)]
    if result['storedObject'] != expected: raise ValueError('Native reader differs from independently supplied wire values')


def reader_fixtures(reader, protoc, proto):
    normal = {off: (True if kind == 'bool' else 2 if off == 0x98 else (1 << 63) + 17 if kind == 'uint64' else 0x12340000 + off)
              for off, _, kind in SCALARS}
    boundary = {off: (True if kind == 'bool' else -1 if kind == 'int32' else (1 << 64) - 1 if kind == 'uint64' else 0xFFFFFFFF)
                for off, _, kind in SCALARS}
    cases = [('empty', {}), ('all-scalars', normal), ('scalar-boundaries', boundary),
             ('unicode-nul', {0x38: '旅人🚀\0'}), ('packed-list', {0x20: [0, 127, 128, 0xFFFFFFFF]}),
             ('nonempty-positions', {0x18: {24: 1.25, 28: 3.75, 32: -2.5}, 0x30: {24: -128.5}, 0x50: {28: 65536.25}})]
    rows = []
    for name, values in cases:
        wire = encode_fixture(values)
        if wire != protoc_fixture(protoc, proto, values): raise ValueError('Independent fixture/protoc wire mismatch')
        result = reader.run_scene(wire); verify_stored(result, values)
        e = reader.e; e.run(CLONE, OBJECT); cloned = e.reg('rax')
        if cloned == OBJECT or reader.snapshot(cloned) != result['storedObject']: raise ValueError('Native scene clone changed fields')
        source_list, clone_list = e.read(OBJECT + 32), e.read(cloned + 32)
        position_distinct = all(e.read(OBJECT + off) != e.read(cloned + off) for off, _, child in CHILDREN if off in values)
        list_distinct = source_list != clone_list and (not values.get(32) or e.read(source_list + 16) != e.read(clone_list + 16))
        if not list_distinct or not position_distinct: raise ValueError('Mutable scene clone storage aliases original')
        mutated = []
        if values.get(32):
            e.write(e.read(source_list + 16) + 32, 0xA5A5A5A5, 'I'); mutated.append('uint32-list')
        for off, _, child in CHILDREN:
            if off in values:
                e.write(e.read(OBJECT + off) + 24, 0x42F60000, 'I'); mutated.append('position-' + hex(off))
        if reader.snapshot(cloned) != result['storedObject']: raise ValueError('Source mutation changed the cloned scene graph')
        rows.append(dict(kind='reader-clone', name=name, wire=wire.hex(), values={hex(k): v for k, v in values.items()},
                         result=result, cloneDistinct=True, mutableChildrenDistinct=True, mutatedSourceCollections=mutated,
                         cloneUnaffectedBySourceMutation=True, protocMatch=True))
    mixed = scalar_wire(13, 1) + blob_wire(13, varint(2) + varint(0xFFFFFFFF)) + scalar_wire(13, 0)
    first = blob_wire(14, b'\x0d' + struct.pack('<f', 1.25))
    second = blob_wire(14, b'\x15' + struct.pack('<f', -2.5))
    extras = scalar_wire(10, 1) + scalar_wire(10, 0xFFFFFFFF) + mixed + first + second
    result = reader.run_scene(extras)
    verify_stored(result, {0x94: 0xFFFFFFFF, 0x20: [1, 2, 0xFFFFFFFF, 0], 0x18: {24: 1.25, 32: -2.5}})
    rows.append(dict(kind='reader-merge', name='duplicate-scalar-mixed-list-nested-merge', wire=extras.hex(), result=result))
    unknown = blob_wire(10, b'x') + scalar_wire((1 << 29) - 1, 1 << 63)
    result = reader.run_scene(unknown); verify_stored(result, {})
    rows.append(dict(kind='reader-merge', name='wrong-wire-and-maximum-unknown-tag', wire=unknown.hex(), result=result))
    zero = b''.join(scalar_wire(tag, 0) for _, tag, _ in SCALARS)
    result = reader.run_scene(zero); verify_stored(result, {off: 0 for off, _, _ in SCALARS})
    rows.append(dict(kind='reader-merge', name='explicit-wire-zero-replaces-encoded-constructor-defaults', wire=zero.hex(), result=result))
    for name, wire, depth in [('varint-truncated', b'\x50\x80', 100), ('zero-tag', b'\0', 100),
                             ('varint-overflow', b'\x50' + b'\x80' * 10, 100), ('truncated-string', blob_wire(293, b'x')[:-1], 100),
                             ('truncated-nested', b'\x72\x05\x0d\0', 100), ('nested-recursion-limit', first, 0),
                             ('truncated-packed', b'\x6a\x02\x80', 100)]:
        try: reader.run_scene(wire, depth)
        except ValueError as error: rows.append(dict(kind='reader-rejection', name=name, wire=wire.hex(), error=str(error),
                                                    errorScope='native-branch' if 'Native error branch:' in str(error) else 'explicit-wire-or-storage-model'))
        else: raise ValueError('Malformed scene fixture returned successfully')
    return rows


def network_fixture(reader, name, values, born_flag=1, media_flag=0, born_present=True, gate_state=10,
                    gate_ready=False, gate_id=0, config_kind=None, feature=False, sender_enabled=True, null=False, wrapped=False):
    result = reader.run_scene(encode_fixture(values)); e = reader.e
    owner, born, ui, network, sender, state = (e.alloc(1024) for _ in range(6))
    e.write(GLOBAL + 0xBE90, owner); e.write(GLOBAL + 0x9E60, ui); e.write(GLOBAL + 0xA0D0, sender)
    e.write(owner + 0x48, born if born_present else 0); e.write(owner + 0x30, state)
    e.write(born + 0x19, born_flag, 'B'); e.write(born + 0x1A, media_flag, 'B')
    e.write(state + 0x38, gate_state, 'I'); e.write(state + 0x40, gate_ready, 'B'); e.write(state + 0x28, gate_id, 'I')
    e.write(sender + 0x120, sender_enabled, 'B')
    for cell in (0x1458971C0, 0x145897C48, 0x1458AD418, 0x14589A450): e.synthetic_type(cell)
    e.write(0x145B98E4D, 1, 'B')  # Declared initialized metadata for the config getter, not its return.
    logs, queries = [], []
    literal_pointers = {}
    for cell, (index, text) in LITERALS.items():
        raw = text.encode('utf-16-le'); p = e.alloc(24 + len(raw)); e.write(p + 16, len(raw) // 2, 'I'); e.uc.mem_write(p + 20, raw)
        e.write(cell, p); literal_pointers[p] = index
    def log():
        logs.append(literal_pointers[e.reg('rcx')]); return 0
    def feature_value():
        if e.reg('ecx') != 0x1B1B: raise ValueError('Unexpected feature lookup')
        queries.append(dict(kind='feature', key=e.reg('ecx'), value=feature)); return int(feature)
    def lookup():
        queries.append(dict(kind='config', key=e.reg('ecx'), value=config_kind))
        if config_kind is None: return 0
        p, data = e.alloc(), e.alloc(); e.write(p + 0x38, data); e.write(data + 0x70, config_kind, 'I'); return p
    boxed = {}
    def box():
        p = e.alloc(); boxed[p] = e.read(e.reg('rdx'), 'I'); return p
    def format_log():
        if literal_pointers[e.reg('rdx')] != 45993: raise ValueError('Unexpected wrapper log format')
        value = boxed[e.read(e.reg('r8'))]
        queries.append(dict(kind='wrapper-log-argument', value=value))
        text = LITERALS[0x145B23B68][1].format(value); raw = text.encode('utf-16-le')
        p = e.alloc(24 + len(raw)); e.write(p + 16, len(raw) // 2, 'I'); e.uc.mem_write(p + 20, raw)
        literal_pointers[p] = 45993
        return p
    e.models.update({0x1531FDB80: log, 0x14D581600: feature_value, 0x14A0E5E40: lookup,
                     0x140525C40: box, 0x159AB12F0: format_log})
    e.write(GLOBAL + 0x25260, 0)  # Explicit absent wrapper context; real getter/predicate return paths execute.
    for entry in (WRAPPER, 0x1507FD7D0, 0x1517113F0, HANDLER, GATE, CACHE, 0x14CD5EDC0, 0x153D850D0): e.allow(entry)
    stops = {0x1507AD859, 0x1507ADA79, 0x14C8AE9A0, 0x1507B3490, 0x1404C2BE0}
    arguments = []
    def observe_argument(uc, address, size, _):
        arguments.append(dict(entry=hex(address), originalPointer=e.reg('rdx' if address != CLONE else 'rcx') == OBJECT))
    hooks = [e.uc.hook_add(e.U.UC_HOOK_CODE, observe_argument, begin=address, end=address) for address in (HANDLER, CACHE, CLONE)]
    try:
        trace = fragment(e, WRAPPER if wrapped else HANDLER, stops, dict(rsp=STACK + 0x100008, rcx=network, rdx=0 if null else OBJECT))
    finally:
        for hook in hooks: e.uc.hook_del(hook)
    cached = e.read(born + 0x10)
    cloned = reader.snapshot(cached) if cached else None
    row = dict(kind='network-cache', name=name, wire=result['wire'], trace=trace, logs=logs, queries=queries, argumentObservations=arguments,
               executedNativeEntries=[hex(a) for a in (WRAPPER, 0x1507FD7D0, 0x1517113F0, HANDLER, GATE, CACHE, CONSTRUCTOR, CLONE, COPY, 0x158DB7CD0)
                                      if hex(a) in trace['executedAddresses']],
               wrapped=wrapped, nullMessage=null, senderEnabled=sender_enabled, bornPresent=born_present, initialBornFlags=[born_flag, media_flag],
               finalBornFlags=[e.read(born + 0x19, 'B'), e.read(born + 0x1A, 'B')], uiState=e.read(ui + 0x33C, 'I'),
               cachedCloneDistinct=bool(cached and cached != OBJECT), cachedEqualsReader=bool(cached and cloned == result['storedObject']),
               senderD0=e.read(sender + 0xD0, 'I'), network1d0=e.read(network + 0x1D0, 'I'),
               raw94=e.read(OBJECT + 0x94, 'I'), raw7c=e.read(OBJECT + 0x7C, 'I'), stageValue=e.reg('ecx') if trace['stop'] == '0x14c8ae9a0' else None)
    if cached and (not row['cachedCloneDistinct'] or not row['cachedEqualsReader']): raise ValueError('Network cache did not preserve reader object through actual clone')
    return row


def recover(binary, metadata, startup, records, libraries, protoc):
    declared = declarations(binary, metadata, startup, records)
    fields, scans, position = recover_outer(binary, records, libraries)
    e = Offline72(binary, libraries, records, True); reader = SceneReader72(e)
    try:
        e.run(0x1523856A0)
        if e.reg('ax') != 27748: raise ValueError('Native scene getter mismatch')
        rows = []
        for cmd in (27748, 0, 65535):
            e.prepare(); msg = e.alloc(); captured = []
            def capture(): captured.append(e.reg('rdx')); return 0
            e.models[WRAPPER] = capture
            e.write(STREAM + 0x22, cmd, 'H'); e.write(STREAM + 0x10, msg); e.run(DISPATCH)
            if captured != ([msg] if cmd == 27748 else []): raise ValueError('Scene dispatcher binding mismatch')
            rows.append(dict(kind='dispatch', cmdId=cmd, originalPointerPreserved=bool(captured), executedHandler=False))
        e.models.pop(WRAPPER)
        with tempfile.TemporaryDirectory(prefix='scene-wire-') as folder:
            proto = Path(folder) / 'fixture.proto'; proto.write_text(fixture_proto(), 'utf8')
            rows.extend(reader_fixtures(reader, protoc, proto))
        base = {0x94: 0xFFFFFFFF, 0x7C: 0x80000000, 0x98: 2, 0x18: {24: 1.25}, 0x20: [0, 128, 0xFFFFFFFF]}
        specs = [('waiting-cache', {}), ('media-ready', dict(media_flag=1)), ('born-inactive', dict(born_flag=0)),
                 ('born-absent', dict(born_present=False)), ('feature-on', dict(feature=True)),
                 ('gate-not-ready', dict(gate_state=9)), ('gate-id-zero', dict(gate_state=9, gate_ready=True)),
                 ('gate-config-missing', dict(gate_state=9, gate_ready=True, gate_id=7)),
                 ('gate-config-other', dict(gate_state=9, gate_ready=True, gate_id=7, config_kind=104)),
                 ('gate-config-105-boundary', dict(gate_state=9, gate_ready=True, gate_id=7, config_kind=105)),
                 ('sender-disabled', dict(sender_enabled=False)), ('null-message', dict(null=True)),
                 ('wrapper-absent-context', dict(wrapped=True))]
        rows.extend(network_fixture(reader, name, base, **options) for name, options in specs)
    finally:
        reader.close(); e.close()
    validate_fixtures(rows)
    for field in fields:
        if field.get('nestedTypeIndex') == POSITION: field['contentStatus'] = 'native-position-content-observed'
    return dict(schemaVersion=1, inputSHA256=HASHES, declarations=declared, fields=fields, outerScanSamples=len(scans),
                outerScanScope='bounded tags 1..2047 and wire types 0/2/1/5; complete instance-field coverage checked independently',
                positionFields=[dict(objectOffset=off, tag=tag, type='float', cardinality='singular', presence='implicit', packed=False) for off, tag in sorted(POSITION_TAGS.items())],
                fixtures=rows, outerScans=scans, positionScan=position, fixtureCount=len(rows), runtimeStatus='not-tested',
                closureComplete=False, targetProtocolReady=False, scope='outer wire, position-only nested content and bounded network/born cache',
                fixtureProto=fixture_proto(), protocSHA256=hashlib.sha256(protoc.read_bytes()).hexdigest(),
                models=['managed class/interface/constructor metadata and reference count=1', 'managed allocations, uint32 arrays/copy and codec delegate storage',
                        'tag/skip/raw-byte/float32/UTF-8 primitives with explicit WireInput rejection rules; uint32/uint64 varint bodies execute',
                        'named log/boxing/format sinks and explicit feature/config lookup inputs; wrapper context absent'],
                outsideScope=['remaining nested message content', 'formal business meaning of scalar/string/position fields and their consumer arithmetic',
                              'wrapper with a present context, normal scene entry after stage timestamp recorder, config105 recovery body, network handler suffix',
                              'IFix runtime overrides, SDK, media/timer scheduling, live packet order, production integration and D-02'])


def validate_fixtures(rows):
    counts = Counter(row['kind'] for row in rows)
    if counts != dict(dispatch=3, **{'reader-clone': 6, 'reader-merge': 3, 'reader-rejection': 7, 'network-cache': 13}):
        raise ValueError('Incomplete scene-wait coverage')
    for row in rows:
        if row['kind'] == 'reader-clone':
            if not all(row[k] for k in ('cloneDistinct', 'mutableChildrenDistinct', 'cloneUnaffectedBySourceMutation', 'protocMatch')):
                raise ValueError('Reader/clone evidence changed')
        if row['kind'] != 'network-cache': continue
        name, stop = row['name'], row['trace']['stop']
        expected = ('0x1507ada79' if name == 'null-message' else '0x1507b3490' if name == 'gate-config-105-boundary'
                    else '0x14c8ae9a0' if name in ('media-ready', 'born-inactive', 'born-absent') else '0x1507ad859')
        if stop != expected: raise ValueError('Network/cache branch boundary mismatch')
        cached = name not in ('null-message', 'gate-config-105-boundary', 'born-inactive', 'born-absent')
        if row['cachedCloneDistinct'] != cached or row['cachedEqualsReader'] != cached: raise ValueError('Cache clone state mismatch')
        if row['logs'] != ([45993, 50090] if row['wrapped'] else [50090]): raise ValueError('Network semantic entry log mismatch')
        expected_arguments = [dict(entry=hex(HANDLER), originalPointer=name != 'null-message')]
        if name not in ('null-message', 'gate-config-105-boundary'): expected_arguments.append(dict(entry=hex(CACHE), originalPointer=True))
        if cached: expected_arguments.append(dict(entry=hex(CLONE), originalPointer=True))
        if row['argumentObservations'] != expected_arguments: raise ValueError('Original reader pointer was not preserved at native boundaries')
        expected_flags = [0, 0] if name == 'media-ready' else row['initialBornFlags']
        if row['finalBornFlags'] != expected_flags or row['uiState'] != (3 if name == 'media-ready' else 0):
            raise ValueError('Born waiting/UI state mismatch')
        if name in ('null-message', 'gate-config-105-boundary'):
            if row['senderD0'] or row['network1d0']: raise ValueError('Early branch reached scalar consumers')
            continue
        first = (((row['raw94'] + 0x5889) & 0xFFFFFFFF) ^ 0xE011) if row['senderEnabled'] else 0
        second = ((row['raw7c'] ^ 0x2D03) + 0xC0E3) & 0xFFFFFFFF
        if (row['senderD0'], row['network1d0']) != (first, second): raise ValueError('Native scalar consumer arithmetic mismatch')
        if row['wrapped'] and row['queries'][0] != dict(kind='wrapper-log-argument', value=first):
            raise ValueError('Wrapper source/decoded scalar mismatch')
        if name == 'media-ready' and (row['finalBornFlags'] != [0, 0] or row['uiState'] != 3 or row['stageValue'] != 10):
            raise ValueError('Ready-cache stage boundary mismatch')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ('client', 'protoc', 'output', 'summary-output'): parser.add_argument('--' + name, type=Path, required=True)
    parser.add_argument('--libraries', type=Path, default=Path('local/migration72/python'))
    args = parser.parse_args()
    if args.output.resolve() == args.summary_output.resolve() or any(p.resolve().is_relative_to(args.client.resolve()) for p in (args.output, args.summary_output)):
        parser.error('Distinct report paths outside client required')
    binary, metadata, startup = [read_verified(args.client / name, digest) for name, digest in HASHES.items()]
    records, _ = extract(binary, metadata, startup=startup, structures=True)
    report = recover(binary, metadata, startup, records, args.libraries, args.protoc)
    report['toolSourceSHA256'] = {name: hashlib.sha256((Path(__file__).resolve().parents[1] / name).read_bytes().replace(b'\r\n', b'\n')).hexdigest()
                                 for name in ('tools/trace_client72_scene_wait.py', 'tools/client72_scene_reader.py')}
    summary = {k: v for k, v in report.items() if k not in ('fixtures', 'outerScans', 'positionScan')}
    summary['fixtureKinds'] = dict(Counter(row['kind'] for row in report['fixtures']))
    summary['observations'] = [{k: (dict(v, executedAddresses=None) if k == 'trace' else v) for k, v in row.items()} for row in report['fixtures']]
    for path, value in ((args.output, report), (args.summary_output, summary)):
        path.parent.mkdir(parents=True, exist_ok=True); path.write_text(json.dumps(value, indent=2) + '\n', 'utf8')
    print(json.dumps(dict(fixtures=len(report['fixtures']), fields=len(report['fields']), runtimeStatus='not-tested')))


if __name__ == '__main__': main()
