"""Hash-bound raw initialization reader slice: type28836/Cmd22609 candidate.

Executes the target reader to return using the existing explicit WireInput
primitive models. Asta names/tags do not validate target business semantics.
"""
import argparse
import json
from pathlib import Path
try:
    from .extract_client72 import HASHES, read_verified, extract
    from .client72_emulator import Offline72
    from .trace_client72_responses import Reader72
    from .trace_client72_token import varint
    from .extract_client72_commands import classify_commands
except ImportError:
    from extract_client72 import HASHES, read_verified, extract
    from client72_emulator import Offline72
    from trace_client72_responses import Reader72
    from trace_client72_token import varint
    from extract_client72_commands import classify_commands

TYPE, COMMAND, ENTRY = 28836, 22609, 0x14FD45000
# Metadata plus independently exercised native branches. No business aliases.
LAYOUT = {1: ('PHDOBIIMHLB', 28), 2: ('PAKLDABINMP', 44),
          4: ('ADCHFHMCOJG', 24), 5: ('NAEDDJBKCFC', 32),
          6: ('CLFJNKKFONI', 48), 7: ('DECHDEGPBOE', 36),
          9: ('CKHIAGMBNCC', 40), 14: ('GGGJAGOKMCI', 52)}
INDEPENDENT_WIRE = bytes.fromhex('080b1016202c28373001384d4863708d01')
INDEPENDENT_VALUES = {28: 11, 44: 22, 24: 44, 32: 55, 48: 1, 36: 77, 40: 99, 52: 141}


def validate_fixtures(rows):
    """Validate observable native stores against independent hand-written inputs."""
    names = {row['name']: row for row in rows}
    if len(names) != len(rows): raise ValueError('Duplicate fixture name')
    if names['independent-nonzero']['wire'] != INDEPENDENT_WIRE.hex():
        raise ValueError('Independent fixture drift')
    for name, expected in [('independent-nonzero', INDEPENDENT_VALUES),
                           ('uint32-upper-bound', dict.fromkeys(INDEPENDENT_VALUES, 0xFFFFFFFF)),
                           ('explicit-zero', dict.fromkeys(INDEPENDENT_VALUES, 0)),
                           ('duplicate-last-wins', {44: 2}), ('wrong-wire-and-unknown', {})]:
        actual = {e['objectOffset']: e['value'] for e in names[name]['events']}
        if actual != expected: raise ValueError('Native values differ: ' + name)
    for tag, (field, offset) in LAYOUT.items():
        row = names['scan-varint-' + str(tag)]
        if len(row['events']) != 1: raise ValueError('Field branch absent')
        event = row['events'][0]
        if (event['key'], event['objectOffset'], event['size'], event['value']) != (tag << 3, offset, 4, 0x12345678):
            raise ValueError('Field branch drift: ' + field)


def recover(reader):
    e = reader.e
    row = e.records[TYPE]
    if row['name'] != 'KNCCLAJAEEC': raise ValueError('Target type identity drift')
    fields = {f['offset']: f for f in row['fields'] if f['offsetKind'] == 'instance'}
    if set(fields) != {off for _, off in LAYOUT.values()}: raise ValueError('Instance layout drift')
    e.allow(ENTRY)
    rows = []
    def run(name, raw):
        sample = reader.run(TYPE, raw, ENTRY)
        sample['name'] = name; rows.append(sample)
        return sample
    for tag in range(1, 33):
        for wire, suffix in [(0, varint(0x12345678)), (1, bytes(8)), (2, b'\0'), (5, bytes(4))]:
            sample = run(('scan-varint-' if wire == 0 else 'scan-wire' + str(wire) + '-') + str(tag), varint(tag << 3 | wire) + suffix)
            if wire != 0 or tag not in LAYOUT:
                if sample['events']: raise ValueError('Unexpected field branch in bounded scan')
    run('empty', b'')
    run('independent-nonzero', INDEPENDENT_WIRE)
    run('uint32-upper-bound', b''.join(varint(tag << 3) + bytes.fromhex('ffffffff0f') for tag in LAYOUT))
    run('explicit-zero', bytes.fromhex('08001000200028003000380048007000'))
    run('duplicate-last-wins', bytes.fromhex('10011002'))
    run('wrong-wire-and-unknown', bytes.fromhex('1201781801fa010178'))
    rejections = []
    for name, raw in [('truncated-varint', b'\x10\x80'), ('uint64-overflow', b'\x10'+b'\xff'*9+b'\x02'),
                      ('zero-tag', b'\0'), ('truncated-fixed32', b'\x15\x01'),
                      ('truncated-blob', b'\x12\x03x')]:
        try: reader.run(TYPE, raw, ENTRY)
        except ValueError as error:
            rejections.append(dict(name=name, wire=raw.hex(), error=str(error), errorScope='explicit-wire-input-model'))
        else: raise ValueError('Malformed fixture unexpectedly accepted: ' + name)
    validate_fixtures(rows)
    recovered = []
    for tag, (name, offset) in LAYOUT.items():
        field = fields[offset]
        if field['name'] != name: raise ValueError('Client field name drift')
        kind = field['type']['kind']
        if kind not in ('uint32', 'valuetype'): raise ValueError('Field type drift')
        event = next(r for r in rows if r['name'] == 'scan-varint-' + str(tag))['events'][0]
        recovered.append(dict(clientField=name, objectOffset=offset, tag=tag,
                              type='enum' if kind == 'valuetype' else kind,
                              typeDefinition=field['type'].get('typeDefinition'),
                              wireType=0, cardinality='singular', packed=False,
                              evidence=dict(reader=hex(ENTRY), nativeStore=event['source']),
                              wireStatus='emulated-observation', semanticStatus='unresolved'))
    return recovered, rows, rejections


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client', required=True, type=Path)
    parser.add_argument('--output', required=True, type=Path)
    parser.add_argument('--observations', required=True, type=Path)
    parser.add_argument('--python-libs', type=Path, default=Path('local/migration72/python'))
    args = parser.parse_args()
    root = args.client.resolve(); paths = [args.output.resolve(), args.observations.resolve()]
    if len(set(paths)) != 2 or any(p.is_relative_to(root) for p in paths):
        parser.error('Distinct output files outside client required')
    binary, metadata, startup = [read_verified(root/name, digest) for name, digest in HASHES.items()]
    records, _ = extract(binary, metadata, startup=startup, structures=True)
    commands = classify_commands(records)
    legacy = records[46070]
    legacy_audit = dict(historicalClaim=dict(clientType='HPHDHDJHCGO', typeIndex=46070, cmdId=21136,
                                            source='docs/COMBINE_UI_2026-10-18.md; src/main/proto/AvatarSelectionDataNotify.proto'),
                        targetCommandCount=len(commands['commands']),
                        targetCmd21136Matches=[c for c in commands['commands'] if c['cmdId'] == 21136],
                        targetSameIndexCommandBindings=[c for c in commands['commands'] if c['typeIndex'] == 46070],
                        targetTypeAt46070=dict(index=legacy['index'], name=legacy['name'], namespace=legacy['namespace'],
                                               parent=legacy['parent'], fields=legacy['fields'], methods=legacy['methods']),
                        conclusion='Historical type index/name/Cmd cannot be reused as CN72 proof; absence in bounded static command extraction does not establish protocol nonexistence')
    e = Offline72(binary, args.python_libs, records, trace_instructions=False); reader = Reader72(e)
    try: fields, rows, errors = recover(reader)
    finally: reader.close(); e.close()
    context = dict(schemaVersion=1, inputSHA256=HASHES, typeIndex=TYPE, clientType='KNCCLAJAEEC',
                   cmdId=COMMAND, commandEvidence='commands.json target getter 0x14fd44ff0',
                   candidateSemanticName='StoreWeightLimitNotify', semanticIdentityStatus='candidate',
                   runtimeStatus='not-tested', targetProtocolReady=False,
                   limitations=['No independent business identity/field-consumer proof',
                                'Reader native dispatch/store to return; WireInput scalar/tag/skip are explicit fixtures',
                                'Managed objects and reference count are explicit fixtures; IFix alternate branch not executed',
                                'Malformed input rejection is model behavior, not target-client rejection',
                                'No nested/list/uint64 fields exist in this slice; no such coverage claimed',
                                'Tag/wire scan bounded to tags 1..32; complete recovered fields equal metadata instance offsets'])
    selected = [r for r in rows if not r['name'].startswith('scan-') or
                r['name'] in {'scan-varint-' + str(tag) for tag in LAYOUT}]
    observation = dict(context, reader=hex(ENTRY), fields=fields, scanFixtureCount=128,
                       validationFixtureCount=6, modelRejectionCount=len(errors),
                       fixtureEvidence=selected, modelRejections=errors,
                       legacySelectionAudit=legacy_audit)
    trace = dict(context, fixtures=rows, modelRejections=errors)
    for path, value in zip(paths, [trace, observation]):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(value, indent=2, ensure_ascii=False)+'\n', 'utf8')
    print(json.dumps(dict(fields=len(fields), nativeFixtures=len(rows), modelRejections=len(errors))))


if __name__ == '__main__': main()
