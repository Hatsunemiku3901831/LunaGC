"""Bounded PlayerEnterSceneInfoNotify field-to-local-avatar identity contract.

The five outer wire fields are observed with empty nested envelopes only. The
real manager prefix and local-avatar getter execute; scene construction, the
network handler and the setter's downstream effects are not completed here.
"""
import argparse
import hashlib
import json
from pathlib import Path
import struct

try:
    from .extract_client72 import HASHES, read_verified, extract, PE, HEADER_VA, BODY, number, TypeResolver
    from .extract_client72_signatures import parameter_start, parameter_words
    from .extract_client72_literals import Literals72
    from .trace_client72_responses import Reader72, bind_fields
    from .trace_client72_player_store import type_cells
    from .client72_emulator import Offline72, GLOBAL, OBJECT, STACK, END
    from .client72_graph_fixtures import scalar, message
    from .trace_client72_token import varint
    from .client72_session_runtime import fragment
except ImportError:
    from extract_client72 import HASHES, read_verified, extract, PE, HEADER_VA, BODY, number, TypeResolver
    from extract_client72_signatures import parameter_start, parameter_words
    from extract_client72_literals import Literals72
    from trace_client72_responses import Reader72, bind_fields
    from trace_client72_player_store import type_cells
    from client72_emulator import Offline72, GLOBAL, OBJECT, STACK, END
    from client72_graph_fixtures import scalar, message
    from trace_client72_token import varint
    from client72_session_runtime import fragment

INFO, READER, MANAGER, SETTER, LOOKUP, LOCAL = 34208, 0x1541B67D0, 0x14CD68530, 0x1517B18F0, 0x1517ABD80, 0x153E46950
BOUNDARY, NULL, CLEANUP, IFIX = 0x153A20F30, 0x1404C2BE0, 0x14ADF3180, 0x14CD6872E
RETURN = END + 0x100
METHODS = ((34208, READER, 1), (56238, 0x1507D1620, 1), (18753, MANAGER, 1), (49228, SETTER, 1), (58088, LOCAL, 0))
LITERALS = {0x145B2A718: (49439, 'OnPlayerEnterSceneInfoNotify notify is null'),
            0x145B2A720: (49440, 'notify.TeamEnterInfo.AbilityControlBlock is null OnPlayerEnterSceneNotify'),
            0x145B2A728: (49441, 'notify.TeamEnterInfo is null OnPlayerEnterSceneNotify'),
            0x145B2A730: (49442, 'avatarEnterInfo is null OnPlayerEnterSceneNotify')}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ('client', 'output', 'observations'): parser.add_argument('--' + name, type=Path, required=True)
    parser.add_argument('--python-libs', type=Path, default=Path('local/migration72/python'))
    args = parser.parse_args()
    binary, metadata, startup = [read_verified(args.client / name, digest) for name, digest in HASHES.items()]
    records, _ = extract(binary, metadata, startup=startup, structures=True)
    declared = declarations(binary, metadata, startup, records)
    engine = Offline72(binary, args.python_libs, records, trace_instructions=False)
    reader = Reader72(engine)
    for row in declared['envelopeClassUsages']: engine.synthetic_type(int(row['cell'], 16))
    codec = engine.alloc(); engine.write(GLOBAL + 0x35000, codec)
    reader.codecs[codec] = (INFO, 2); reader.heap_floor = engine.heap_cursor
    try:
        scan, fields = scan_outer(reader)
        engine.uc.mem_map(END, 4096); engine.uc.mem_write(RETURN, b'\xc3')
        engine.hooks.append(engine.uc.hook_add(engine.U.UC_HOOK_CODE, engine._code, begin=END, end=END + 4095))
        rows = [run_case(engine, reader, case) for case in cases()]
        rejected = reject_wire(reader)
    finally:
        reader.close(); engine.close()
    context = dict(schemaVersion=1, task='F-03d3f2b', inputSHA256=HASHES, declarations=declared,
        messages=[dict(typeIndex=INFO, clientType=records[INFO]['name'], fields=fields)],
        scanFixtures=128, runtimeStatus='not-tested', targetProtocolReady=False,
        completeSceneInfoRawClosure=False, completeNetworkHandler=False, completeSetterEffects=False,
        currentControlGuidBindingApproved=False,
        verifiedBinding=dict(typeIndex=INFO, clientField='OLCJCLBMMCF', tag=12, objectOffset=48,
                             type='uint32', purpose='local avatar entity ID', managerFieldOffset=644,
                             namedConsumer='MoleMole.ActorUtils.GetLocalAvatarConfigId'),
        limitations=['Outer nested envelopes are empty-only models, not their schemas or full raw closure',
                     'UInt32 entity dictionary index and scene/entity objects are explicit controlled fixtures',
                     'Manager prefix stops at real downstream effect entry; full handler and scene loading are not executed',
                     'Avatar root tag3 remains a separate Hero GUID contract; no current GUID rename or production readiness'])
    context['sourceSHA256'] = {name: hashlib.sha256(Path(name).read_bytes()).hexdigest() for name in (
        'tools/client72_scene_entry_identity.py', 'tools/trace_client72_responses.py',
        'tools/client72_emulator.py', 'tools/client72_session_runtime.py')}
    report = dict(context, scan=scan, fixtures=rows, rejections=rejected)
    summary = dict(context, scan=scan, fixtures=[compact(row) for row in rows], rejections=rejected)
    for path, value in ((args.output, report), (args.observations, summary)):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(value, indent=2) + '\n', encoding='utf8')
    print(json.dumps(dict(fields=len(fields), scan=128, cases=len(rows), rejections=len(rejected))))


def declarations(binary, metadata, startup, records):
    pe = PE(binary); header = pe.offset(HEADER_VA)
    method_base = BODY + (number(binary, header + 0xB0) ^ 0x51324A1F)
    parameter_base = BODY + ((number(binary, header + 0x174) + 0xAE6BCD68) & 0xFFFFFFFF)
    resolver = TypeResolver(pe, startup, records, header); methods = []
    for owner, address, count in METHODS:
        method = next(m for m in records[owner]['methods'] if m['registeredAddress'] == hex(address))
        if method['parameterCount'] != count: raise ValueError('Scene identity method parameter count changed')
        item = dict(ownerType=owner, **method)
        if address in (0x1507D1620, MANAGER, SETTER):
            first = parameter_start(method['index'], number(metadata, method_base + method['index'] * 26 + 4))
            _, ref = parameter_words(first, number(metadata, parameter_base + first * 8), number(metadata, parameter_base + first * 8 + 4))
            typ = resolver.resolve_index(ref)
            if typ.get('byRef') or (typ.get('kind') != 'uint32' if address == SETTER else typ.get('typeDefinition') != INFO):
                raise ValueError('Scene identity typed parameter changed')
            item.update(parameterIndex=first, parameterTypeIndex=ref, parameterType=typ)
        if address == LOCAL and method['name'] != 'GetLocalAvatarConfigId': raise ValueError('Named local-avatar consumer changed')
        methods.append(item)
    getter = next(m for m in records[INFO]['methods'] if m.get('virtualSlot') == 22)
    if getter.get('literalU16Candidate') != 21761: raise ValueError('Scene info command getter changed')
    registration = 0x1507C2A12; code = binary[pe.offset(registration, 12, executable=True):][:12]
    if code[:8] != bytes.fromhex('66ba01554989f0e8') or registration + 12 + struct.unpack_from('<i', code, 8)[0] != 0x1532ED560:
        raise ValueError('Scene info registration changed')
    calls = []
    for site, target in ((0x1507D17FD, MANAGER), (0x14CD685C6, SETTER), (0x153E46985, LOOKUP)):
        code = binary[pe.offset(site, 5, executable=True):][:5]
        if code[0] != 0xE8 or site + 5 + struct.unpack_from('<i', code, 1)[0] != target:
            raise ValueError('Scene identity call chain changed')
        calls.append(dict(site=hex(site), target=hex(target)))
    literal = Literals72(binary, metadata); selected = []
    for item in literal.group(literal.global_group):
        cell = int(item['cell'], 16)
        if cell in LITERALS:
            index, value = LITERALS[cell]
            if item['index'] != index or literal.decode(index) != value: raise ValueError('Scene info identity literal changed')
            selected.append(dict(item, value=value))
    if len(selected) != 4: raise ValueError('Scene info identity literal missing')
    fields = [f for f in records[INFO]['fields'] if f['offsetKind'] == 'instance']
    if {(f['offset'], f['type']['kind']) for f in fields} != {(24, 'genericinst'), (32, 'class'), (40, 'class'), (48, 'uint32'), (52, 'uint32')}:
        raise ValueError('Scene info outer layout changed')
    active = next(f for f in records[49228]['fields'] if f['offset'] == 0x284)
    if active['type']['kind'] != 'uint32': raise ValueError('Active scene entity declaration changed')
    cells = type_cells(binary, metadata, startup, records)
    if cells[0x145898D98]['type']['typeDefinition'] != 54061: raise ValueError('Scene entity lookup class usage changed')
    envelope = [row for row in cells.values() if row['type'].get('typeDefinition') in (INFO, 18295, 49723, 44512)
                or row['type'].get('instanceIndex') == 12523]
    if len(envelope) != 5: raise ValueError('Scene empty-envelope class usage set changed')
    return dict(typeIndex=INFO, clientType=records[INFO]['name'], cmdId=21761, getter=getter,
                registration=hex(registration), semanticName='PlayerEnterSceneInfoNotify', methods=methods,
                calls=calls, literals=selected, activeEntityField=active, entityClassUsage=cells[0x145898D98],
                envelopeClassUsages=envelope)


def scan_outer(reader):
    samples, selected = [], []
    for tag in range(1, 33):
        for wire, payload in ((0, bytes.fromhex('e78a8d8908')), (1, bytes(8)), (2, b'\0'), (5, bytes(4))):
            data = varint(tag << 3 | wire) + payload
            try: row = reader.run(INFO, data, READER)
            except Exception as error:
                raise ValueError(f'Scene-info scan tag={tag} wire={wire}: {reader.e.failure}') from error
            samples.append(row)
            if row['events']: selected.append(row)
    fields = bind_fields(reader.e.records[INFO], samples)
    if len(fields) != 5 or {f['objectOffset']: f['tag'] for f in fields if f['objectOffset'] in (48, 52)} != {48: 12, 52: 7}:
        raise ValueError('Scene info outer wire binding changed')
    return selected, fields


def cases():
    values = [('low-id', 0x100001, 77), ('high-id', 0x80000001, 7), ('max-id', 0xFFFFFFFF, 0x80000001),
              ('two-fields-swapped', 7, 0x80000001), ('duplicate-zero', 0, 0x100001),
              ('other-scalar-only', 0, 0x80000001), ('wrong-wire-id', 0, 7)]
    output = []
    for name, entity, other in values:
        wire = scalar(7, other)
        if name == 'duplicate-zero': wire += scalar(12, 0x80000001) + scalar(12, 0)
        elif name == 'wrong-wire-id': wire += message(12, b'')
        elif name != 'other-scalar-only': wire += scalar(12, entity)
        output.append(dict(name=name, wire=wire.hex(), entityId=entity, otherField=other))
    for name in ('existing-entity-cleanup-boundary', 'null-typed-input', 'ifix-manager-boundary'):
        output.append(dict(name=name, wire=scalar(12, 0x80000001).hex(), entityId=0x80000001, otherField=0))
    return output


def run_case(e, reader, case):
    decoded = reader.run(INFO, bytes.fromhex(case['wire']), READER)
    scene, manager = e.alloc(1024), e.alloc()
    e.write(GLOBAL + 0xA3A0, scene); e.write(GLOBAL + 0xA400, e.alloc(1024))
    dictionary, entries = e.alloc(), e.alloc(32 + 4 * 24)
    e.write(entries + 24, 4, 'I'); e.write(dictionary + 24, entries); e.write(scene + 0x68, dictionary)
    entity = e.alloc(0x600); klass = e.synthetic_type(0x145898D98)
    e.write(entity, klass); e.write(entity + 0x3F4, 10000005, 'I'); e.write(entries + 0x30, entity)
    e.synthetic_type(0x145897948)
    e.models[0x1578B66F0] = lambda: 0 if e.reg('rcx') == dictionary and e.reg('edx') in {case['entityId'] or -1, 0x100001} else 0xFFFFFFFF
    old_id = 0x100001 if case['name'] == 'existing-entity-cleanup-boundary' else 0
    e.write(scene + 0x284, old_id, 'I')
    e.write(0x1457D952B, int(case['name'] == 'ifix-manager-boundary'), 'B')
    for entry in (MANAGER, SETTER, LOOKUP, LOCAL): e.allow(entry)
    writes = []
    hook = e.uc.hook_add(e.U.UC_HOOK_MEM_WRITE, lambda uc, access, address, size, value, data:
                        writes.append(dict(source=hex(e.reg('rip')), size=size, value=value)), begin=scene + 0x284, end=scene + 0x287)
    def invoke(entry, stops, **registers):
        e.write(STACK + 0x100008, RETURN)
        return fragment(e, entry, stops, dict(rsp=STACK + 0x100008, **registers))
    try:
        trace = invoke(MANAGER, {BOUNDARY, NULL, CLEANUP, IFIX}, rcx=manager,
                       rdx=0 if case['name'] == 'null-typed-input' else OBJECT)
    finally:
        e.uc.hook_del(hook); e.write(0x1457D952B, 0, 'B')
    wanted = {'existing-entity-cleanup-boundary': CLEANUP, 'null-typed-input': NULL, 'ifix-manager-boundary': IFIX}.get(case['name'], BOUNDARY)
    if trace['stop'] != hex(wanted): raise ValueError('Scene manager boundary changed: ' + case['name'])
    expected = old_id if wanted != BOUNDARY else case['entityId']
    if e.read(scene + 0x284, 'I') != expected: raise ValueError('Scene active entity store differs')
    local = None; config = None
    if wanted == BOUNDARY:
        if writes != [dict(source='0x1517b1904', size=4, value=expected)]: raise ValueError('Active entity native store source differs')
        local = invoke(LOCAL, {RETURN, NULL})
        config = e.reg('eax')
        if local['stop'] != hex(RETURN) or config != (10000005 if case['entityId'] else 0): raise ValueError('Named local-avatar getter differs')
        required = {hex(SETTER), hex(LOOKUP)}
        if not required <= set(trace['executedAddresses']) or required & set(trace['models']): raise ValueError('Scene native manager or lookup was modeled')
    elif writes: raise ValueError('Stopped boundary unexpectedly changed active entity')
    return dict(case, reader=decoded, manager=trace, storedEntityId=expected, stores=writes,
                local=local, localAvatarConfigId=config, networkZeroGuardNotExecuted=case['entityId'] == 0)


def reject_wire(reader):
    rejected = []
    for name, wire in [('invalid-tag', '00'), ('truncated-varint', '6080'), ('nonempty-avatar-list-outside-scope', '12020801')]:
        try: reader.run(INFO, bytes.fromhex(wire), READER)
        except ValueError as error: rejected.append(dict(name=name, wire=wire, scope='explicit-input-or-empty-envelope-model', error=str(error)))
        else: raise ValueError('Invalid or out-of-scope scene identity wire accepted')
    return rejected


def compact(row):
    result = {k: v for k, v in row.items() if k not in ('manager', 'local')}
    for key in ('manager', 'local'):
        trace = row[key]
        result[key] = None if trace is None else {k: trace[k] for k in ('entry', 'stop', 'models', 'nativePathSHA256')}
    return result


if __name__ == '__main__': main()
