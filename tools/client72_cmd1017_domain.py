"""Separate the target's two Cmd1017 users by actual outbound transport path.

This does not assign an old gameplay name to type22065, classify it as game S2C,
or merge/remove either global static command binding. No client/socket starts.
"""
import argparse
import hashlib
import json
from pathlib import Path
try:
    from .extract_client72 import HASHES, read_verified, extract, PE, TypeResolver, HEADER_VA, BODY, number
    from .extract_client72_commands import classify_commands
    from .extract_client72_literals import Literals72, USAGE_REGISTRATION
    from .extract_client72_signatures import parameter_start, parameter_words
    from .client72_emulator import Offline72, GLOBAL, STACK
    from .client72_session_runtime import fragment
except ImportError:
    from extract_client72 import HASHES, read_verified, extract, PE, TypeResolver, HEADER_VA, BODY, number
    from extract_client72_commands import classify_commands
    from extract_client72_literals import Literals72, USAGE_REGISTRATION
    from extract_client72_signatures import parameter_start, parameter_words
    from client72_emulator import Offline72, GLOBAL, STACK
    from client72_session_runtime import fragment

GAME_SHA256 = 'b2ff1ac423fc3f1685715a261452359265a7dd0bfdb2821b6af3c55866ad8847'
COLLECTOR, SENDER, CONNECTION, QUEUE = 0x1514609B0, 0x1533D9F30, 0x15011B180, 0x14BBE8100
MASK = 0xFFFFFFFF


def rip_target(binary, pe, site):
    code = binary[pe.offset(site, 7):][:7]
    if code[:2] not in (b'\x48\x8b', b'\x4c\x8b') or code[2] & 0xC7 != 5:
        raise ValueError('Reviewed RIP-relative load changed')
    return site + 7 + int.from_bytes(code[3:], 'little', signed=True)


def declarations(binary, metadata, startup, records, directory):
    pe = PE(binary); byid = {r['index']: r for r in records}
    commands = [r for r in classify_commands(records)['commands'] if r['cmdId'] == 1017]
    if [(r['typeIndex'], r['clientType']) for r in commands] != [(22065, 'NCJGOJMOPHB'), (63999, 'HDPGCNCMACO')]:
        raise ValueError('Reviewed static1017 bindings changed')
    literal = Literals72(binary, metadata)
    resolver = TypeResolver(pe, startup, records, pe.offset(HEADER_VA))
    class_base = number(binary, pe.offset(USAGE_REGISTRATION) + 0x48, 'Q')
    usages = []
    for row, expected_type, expected_cell in ((180792, 22065, 0x1458D86F0), (180795, 34182, 0x1458CCF88), (379632, 18771, 0x1458D7FD0)):
        key = (row * 0x57C15286 + 0x26DDEBDA) & MASK
        source = ((number(metadata, literal.usages_base + row * 8) + 0xABBDCABF) & MASK) ^ key ^ 0x2A357D90
        slot = number(metadata, literal.usages_base + row * 8 + 4) ^ key ^ 0x1855AA65
        if source >> 29 != 1: raise ValueError('Type usage source kind changed')
        reference = source & 0x1FFFFFFF; resolved = resolver.resolve_index(reference)
        if resolved.get('typeDefinition') != expected_type or class_base + slot * 8 != expected_cell:
            raise ValueError('Type usage source/target binding changed')
        usages.append(dict(usageRow=row, sourceIndex=reference, slot=slot, typeIndex=expected_type, cell=hex(expected_cell)))
    for site, expected in ((0x1514609ED, 0x1458D86F0), (0x15145FE8F, 0x1458D86F0), (0x151460ED4, 0x1458CCF88), (0x1533D9F51, 0x1458D7FD0)):
        if rip_target(binary, pe, site) != expected: raise ValueError('Actual allocation class reference changed')
    methods = {int(m['registeredAddress'], 16): (r, m) for r in records for m in r['methods'] if m['registeredAddress'] != '0x0'}
    for entry, expected_owner in ((COLLECTOR, 59241), (SENDER, 51381), (CONNECTION, 63857), (QUEUE, 77722)):
        if methods[entry][0]['index'] != expected_owner: raise ValueError('Transport method owner changed')
    method = methods[SENDER][1]; header = pe.offset(HEADER_VA)
    method_base = BODY + (number(binary, header + 0xB0) ^ 0x51324A1F)
    parameter_base = BODY + ((number(binary, header + 0x174) + 0xAE6BCD68) & MASK)
    first = parameter_start(method['index'], number(metadata, method_base + method['index'] * 26 + 4))
    parameters = []
    for i in range(2):
        _, ref = parameter_words(first + i, number(metadata, parameter_base + (first + i) * 8), number(metadata, parameter_base + (first + i) * 8 + 4))
        parameters.append(resolver.resolve_index(ref))
    if parameters[0]['kind'] != 'uint16' or parameters[1].get('typeDefinition') != 67861:
        raise ValueError('External sender command/protobuf parameters changed')
    field_bindings = []
    for owner, offset, expected_name, expected_definition in ((51381, 0x28, 'OBNHEBNBNJE', 57733),
                (34428, 0x20, 'ODJFHMOPELO', 63857), (63857, 0x10, 'NetworkStream', 1945),
                (63857, 0x38, 'DOIMGNMIECK', 77722), (77722, 0x10, 'Stream', None)):
        field = next(f for f in byid[owner]['fields'] if f['offset'] == offset)
        # Stream's exact metadata index is recorded from the target, while its
        # named System.IO type is the independent semantic transport anchor.
        if field['type'].get('name') != expected_name: raise ValueError('Transport field type changed')
        if owner != 77722 and field['type'].get('typeDefinition') != expected_definition:
            raise ValueError('Transport field declaration changed')
        field_bindings.append(dict(ownerTypeIndex=owner, owner=byid[owner]['name'], field=field))
    strings = []
    for group, index, expected, site in ((0x193F5, 51829, 'beyond socket error ', 0x14830094E),
                                       (0xC418, 33292, 'wpf send thread error ', 0x14BBE8AA0)):
        row = next(r for r in literal.group(group) if r['index'] == index)
        row = dict(row, value=literal.decode(index), group=hex(group), nativeReference=hex(site))
        if row['value'] != expected or rip_target(binary, pe, site) != int(row['cell'], 16):
            raise ValueError('Independent socket/WPF literal source/reference changed')
        strings.append(row)
    path = directory / 'scene-handshake-observations.json'
    if hashlib.sha256(path.read_bytes()).hexdigest() != GAME_SHA256: raise ValueError('Reviewed Game-domain handshake evidence changed')
    game = json.loads(path.read_text('utf8'))
    role = next(r for r in game['roles'] if r['typeIndex'] == 63999)
    requests = [r for r in game['requestObservations'] if r['typeIndex'] == 63999]
    if game['inputSHA256'] != HASHES or role['entry'] != '0x15121e380' or len(requests) != 4:
        raise ValueError('Game Done request provenance changed')
    if any(r['stop'] != '0x15120d730' or not r['requestPointerPreserved'] or not r['protocMatch'] for r in requests):
        raise ValueError('Game-domain sender proof changed')
    return dict(staticCommands=commands, typeUsages=usages, externalSenderParameters=parameters,
                transportFields=field_bindings, transportLiterals=strings,
                gameEvidence=dict(file='scene-handshake-observations.json', sha256=GAME_SHA256, role=role, requestObservations=requests),
                nativeEntry32SHA256={hex(a): hashlib.sha256(binary[pe.offset(a, 32):][:32]).hexdigest()
                    for a in (COLLECTOR, SENDER, CONNECTION, QUEUE, 0x158A9FE20)})


def native_probes(binary, records, libraries):
    e = Offline72(binary, libraries, records, trace_instructions=False)
    try:
        for entry in (COLLECTOR, SENDER, CONNECTION, 0x14A42BD90): e.allow(entry)
        e.allow_unwind(0x158A9FE20)
        classes = {e.synthetic_type(cell): index for index, cell in ((22065, 0x1458D86F0), (34182, 0x1458CCF88), (18771, 0x1458D7FD0))}
        e.synthetic_type(0x1458862B0)
        for flag in (0x145B8B377, 0x145BA137C, 0x145BB01EB): e.write(flag, 1, 'B')
        allocation_floor = e.heap_cursor; allocated = []
        def allocate():
            klass = e.reg('rcx')
            if klass not in classes: raise ValueError('Unbound managed allocation class')
            p = e.alloc(512); e.write(p, klass); e.write(p + 0x14, 1, 'I')
            allocated.append((classes[klass], p)); return p
        e.models[0x14D4480C0] = allocate; e.models[0x1405336C0] = allocate
        e.models[0x159AE9180] = lambda: e.write(e.reg('rcx') + 8, 621355968000000000) or 0
        fixtures = []
        for first, second in ((0, 0), (1, 0), (1, 1)):
            e.prepare(); e.heap_cursor = allocation_floor; allocated.clear()
            owner, dictionary, manager, channel, connection, writer = [e.alloc() for _ in range(6)]
            e.write(owner + 0x10, dictionary); e.write(owner + 0x20, first, 'B'); e.write(owner + 0x21, second, 'B')
            e.write(GLOBAL + 0xCDD0, manager); e.write(manager + 0x28, channel)
            e.write(channel + 0x20, connection); e.write(connection + 0x38, writer); e.write(writer + 0x48, 1, 'B')
            trace = fragment(e, COLLECTOR, {QUEUE}, dict(rsp=STACK + 0x100008, rcx=owner))
            envelope = e.reg('rdx'); message = e.read(envelope + 0x10); child = e.read(message + 0x20)
            if [i for i, _ in allocated] != [22065, 34182, 18771] or message != allocated[0][1] or envelope != allocated[2][1]:
                raise ValueError('Actual message allocation/envelope pointer changed')
            flags = [e.read(child + offset, 'B') for offset in (0x18, 0x19, 0x1A)]
            if e.reg('rcx') != writer or e.read(envelope + 0x1C, 'H') != 1017 or flags != [first, second, 0]:
                raise ValueError('Actual outgoing1017 stream handoff changed')
            fixtures.append(dict(sourceFlags=[first, second], childFlags=flags, envelopeCommand=1017,
                                 messageTypeIndex=22065, envelopeTypeIndex=18771, messagePointerPreserved=True,
                                 writerReceiverPreserved=True, sourceSecondFlagAfter=e.read(owner + 0x21, 'B'),
                                 trace=trace, completion='external-stream-queue-entry-only'))
        return fixtures
    finally: e.close()


def validate_fixtures(rows):
    if [r['sourceFlags'] for r in rows] != [[0, 0], [1, 0], [1, 1]]: raise ValueError('Independent flag sources changed')
    for row in rows:
        if row['childFlags'] != row['sourceFlags'] + [0] or row['envelopeCommand'] != 1017:
            raise ValueError('Native outgoing contents changed')
        if row['messageTypeIndex'] != 22065 or not row['messagePointerPreserved'] or not row['writerReceiverPreserved']:
            raise ValueError('Type/pointer/writer source changed')
        if row['trace']['stop'] != hex(QUEUE) or row['sourceSecondFlagAfter'] != 0:
            raise ValueError('Actual queue boundary/state mutation changed')
        addresses = row['trace']['executedAddresses']
        if not all(hex(a) in addresses for a in (COLLECTOR, 0x158A9FE20, SENDER, CONNECTION)) or '0x15120d730' in addresses:
            raise ValueError('External/game domains were conflated')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client', type=Path, required=True); parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--python-libs', type=Path, default=Path('local/migration72/python'))
    parser.add_argument('--evidence-directory', type=Path, default=Path('protocol/cn-7.2'))
    args = parser.parse_args()
    if args.output.resolve().is_relative_to(args.client.resolve()): parser.error('Write outside the read-only client')
    binary, metadata, startup = [read_verified(args.client/name, sha) for name, sha in HASHES.items()]
    records, _ = extract(binary, metadata, startup=startup, structures=True)
    sources = declarations(binary, metadata, startup, records, args.evidence_directory)
    rows = native_probes(binary, records, args.python_libs); validate_fixtures(rows)
    report = dict(schemaVersion=1, inputSHA256=HASHES, command=1017, declarations=sources, fixtures=rows,
                  domains=[dict(typeIndex=63999, clientType='HDPGCNCMACO', semanticName='EnterSceneDoneReq',
                                direction='C2S', transportDomain='game-session', source='15121e380 -> 15120d730; named NetworkManager RequestEnterSceneDone literal'),
                           dict(typeIndex=22065, clientType='NCJGOJMOPHB', semanticName=None,
                                direction='process-outbound-to-external-stream', transportDomain='beyond-wpf-stream',
                                source='1514609b0 -> 1533d9f30 -> 15011b180 -> 14bbe8100; NetworkStream/Stream fields and independent beyond/WPF literals')],
                  gameC2SAmbiguity='resolved-for-observed-normal-native-transport-paths',
                  globalStaticDuplicateRetained=True, runtimeStatus='not-tested', targetProtocolReady=False,
                  limitations=['The external peer role, endpoint and message gameplay name remain unknown; do not label22065 as Game S2C',
                               'Only normal native paths; IFix/alternative reflective use and full global protocol closure remain unverified',
                               'Explicit managed allocation, initialized metadata flags, zero/empty dirty dictionary and DateTime source; actual dictionary MoveNext executes',
                               'Stops at the real external stream queue entry before enqueue/serialization/thread/socket; no external TCP connection is opened',
                               'No22065 wire schema, payload encoding or remote consumption is asserted',
                               'Global command binding1017 remains on both types; domain resolution must not erase or alias the raw rows'])
    args.output.parent.mkdir(parents=True, exist_ok=True); args.output.write_text(json.dumps(report, indent=2)+'\n', 'utf8')
    print(json.dumps(dict(domains=2, externalNativeFixtures=len(rows), globalStaticDuplicateRetained=True)))


if __name__ == '__main__': main()
