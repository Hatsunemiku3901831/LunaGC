"""Fixed CN72 request raw-zero omission and inverse-token contract.

Runs the actual application constructors and full native writers. Primitive
output sinks and the existing managed runtime boundaries remain modeled.
Request omission is determined by native wire output, not response constructors.
"""
import argparse
import hashlib
import json
from pathlib import Path

try:
    from .extract_client72 import HASHES, read_verified, extract
    from .client72_emulator import Offline72, GLOBAL, STACK
    from .client72_scene_handshake import ROLES, CTORS, WRITERS, declarations
    from .trace_client72_login import Writer72
    from .client72_session_runtime import fragment
except ImportError:
    from extract_client72 import HASHES, read_verified, extract
    from client72_emulator import Offline72, GLOBAL, STACK
    from client72_scene_handshake import ROLES, CTORS, WRITERS, declarations
    from trace_client72_login import Writer72
    from client72_session_runtime import fragment


# Independent fixed literals: logical token, raw token, full native wire.
# Each pair is the raw-zero logical input followed by its logical neighbor.
VECTORS = {
    74391: ((4294925116, 0, ''), (4294925117, 0xffffffff, '18ffffffff0f')),
    20380: ((4294922560, 0, ''), (4294922561, 0xffffffff, '58ffffffff0f')),
    63999: ((42557, 0, ''), (42558, 1, '6001')),
    74076: ((64180, 0, '090100000000004043'), (64181, 1, '0901000000000040433001')),
}
DEFAULTS = {74391: 79044, 20380: 85824, 63999: 4294941227, 74076: 4294939980}
FORMULAS = {
    74391: 'u32(raw - 0x4c01) XOR 0xe8c3',
    20380: 'u32(raw - 0xf8cd) XOR 0x5673',
    63999: 'u32(raw + 0x8609) XOR 0x2034',
    74076: 'u32(raw + 0xb3b6) XOR 0x4902',
}


def decode_request_token(index, raw):
    if index == 74391:
        return ((raw - 0x4c01) & 0xffffffff) ^ 0xe8c3
    if index == 20380:
        return ((raw - 0xf8cd) & 0xffffffff) ^ 0x5673
    if index == 63999:
        return ((raw + 0x8609) & 0xffffffff) ^ 0x2034
    if index == 74076:
        return ((raw + 0xb3b6) & 0xffffffff) ^ 0x4902
    raise ValueError('Not a verified scene request')


def recover(binary, metadata, startup, records, libraries):
    identity = declarations(binary, metadata, startup, records)
    e = Offline72(binary, libraries, records, True)
    rows = []
    try:
        writer = Writer72(e)
        # The primitive double sink is identical to the frozen handshake probe;
        # field presence/selection remains in the actual target writer body.
        e.models[0x151765EF0] = lambda: e.emit(e.uc.reg_read(e.R.UC_X86_REG_XMM2).to_bytes(16, 'little')[:8]) or 0
        e.models.update({0x1531FDB80: lambda: 0, 0x159AB12F0: lambda: 0,
            0x140525C40: lambda: 0, 0x14D581600: lambda: 0})
        e.allow(0x14A42BD90)
        e.synthetic_type(0x1458971C0)
        for name, index, command, entry, registration, cell, *_ in ROLES:
            if registration:
                continue
            e.allow(entry)
            for logical, raw, expected_wire in VECTORS[index]:
                e.prepare()
                klass = e.synthetic_type(cell)
                request, owner, state = e.alloc(256), e.alloc(1024), e.alloc(1024)
                e.write(GLOBAL + 0x9e80, state)
                e.write(state + 0x1ec, 1, 'B')
                e.write(request, klass)
                e.write(request + 0x14, 1, 'I')
                e.run(CTORS[index], request)
                offset = 32 if index == 74076 else 24
                if e.read(request + offset, 'I') != DEFAULTS[index]:
                    raise ValueError('Application request constructor default changed')

                def allocate():
                    if e.reg('rcx') != klass:
                        raise ValueError('Unreviewed request allocation')
                    return request

                e.models[0x14D4480C0] = allocate
                double_bits = 0x4340000000000001 if index == 74076 else 0
                e.write(0x145873360, double_bits)
                trace = fragment(e, entry, {0x15120D730},
                    dict(rsp=STACK + 0x100008, rcx=owner, rdx=logical, r8=0))
                if e.reg('rdx') != request or e.read(request + offset, 'I') != raw:
                    raise ValueError('Native application did not create the expected raw token')
                wire = e.run(WRITERS[index], request)
                if wire.hex() != expected_wire:
                    raise ValueError('Native writer differs from independent raw-zero/neighbor vector')
                if decode_request_token(index, raw) != logical:
                    raise ValueError('Fixed inverse arithmetic disagrees with native input')
                rows.append(dict(name=name, typeIndex=index, cmdId=command,
                    logicalToken=logical, rawToken=raw, constructorRawDefault=DEFAULTS[index],
                    tokenFieldEmitted=raw != 0, wire=wire.hex(), writer=hex(WRITERS[index]),
                    applicationEntry=hex(entry), doubleBits=f'{double_bits:016x}' if index == 74076 else None,
                    producerTrace=trace))
    finally:
        e.close()
    sources = ('tools/client72_scene_request_contract.py', 'tools/client72_scene_handshake.py',
        'tools/client72_emulator.py', 'tools/trace_client72_login.py',
        'tools/client72_session_runtime.py', 'protocol/cn-7.2/scene-handshake-observations.json')
    return dict(schemaVersion=1, inputSHA256=HASHES,
        sourceSHA256={name: hashlib.sha256(Path(name).read_bytes()).hexdigest() for name in sources},
        runtimeStatus='not-tested', targetProtocolReady=False, fixtureCount=len(rows),
        roles=[role for role in identity['roles'] if role['typeIndex'] in VECTORS], fixtures=rows,
        inverseBindings=[dict(typeIndex=index, wireToLogical=formula,
            omittedRawValue=0, omittedLogicalToken=VECTORS[index][0][0]) for index, formula in FORMULAS.items()],
        scope='Full native request application construction and writer; raw-zero plus adjacent logical input',
        absentFieldContract='Request writer omits raw zero. Decode missing request token as raw zero; never substitute response native-constructor defaults.',
        models=['Frozen Writer72 integer/byte sinks and Post-double sink; field selection/full writer bodies execute natively',
            'Existing managed allocations, initialized classes and logging boundaries from scene-handshake request probes'],
        outsideScope=['Native request reader/server error-code or retry policy',
            'Request-token comparison policy, scene side effects and full initialization lifecycle',
            'Socket, actual scheduler, production runtime and real-client acceptance'])


def summarize(report):
    result = {key: value for key, value in report.items() if key != 'fixtures'}
    result['fixtures'] = []
    for row in report['fixtures']:
        trace = row['producerTrace']
        result['fixtures'].append({key: value for key, value in row.items() if key != 'producerTrace'} |
            dict(trace=dict(entry=trace['entry'], stop=trace['stop'],
                nativePathSHA256=trace['nativePathSHA256'])))
    return result


def validate_summary(report):
    errors = []
    if report.get('inputSHA256') != HASHES or report.get('runtimeStatus') != 'not-tested' or report.get('targetProtocolReady') is not False:
        errors.append('Fixed input or runtime scope changed')
    expected = [(index, logical, raw, wire) for index, cases in VECTORS.items() for logical, raw, wire in cases]
    actual = [(row.get('typeIndex'), row.get('logicalToken'), row.get('rawToken'), row.get('wire'))
        for row in report.get('fixtures', [])]
    if expected != actual or report.get('fixtureCount') != 8:
        errors.append('Native raw-zero/adjacent fixed vectors changed')
    for row in report.get('fixtures', []):
        index = row.get('typeIndex')
        if index not in VECTORS:
            continue
        if row.get('constructorRawDefault') != DEFAULTS[index] or row.get('tokenFieldEmitted') != (row.get('rawToken') != 0):
            errors.append('Constructor and native wire omission confused')
        if row.get('writer') != hex(WRITERS[index]) or row.get('trace', {}).get('stop') != '0x15120d730':
            errors.append('Native writer or application boundary changed')
    bindings = [(row.get('typeIndex'), row.get('wireToLogical'), row.get('omittedRawValue'), row.get('omittedLogicalToken'))
        for row in report.get('inverseBindings', [])]
    if bindings != [(index, formula, 0, VECTORS[index][0][0]) for index, formula in FORMULAS.items()]:
        errors.append('Request absence inverse contract changed')
    return errors


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--summary-output', type=Path, required=True)
    parser.add_argument('--libraries', type=Path, default=Path('local/migration72/python'))
    args = parser.parse_args()
    paths = (args.output, args.summary_output)
    if len({p.resolve() for p in paths}) != 2 or any(p.resolve().is_relative_to(args.client.resolve()) for p in paths):
        parser.error('Distinct outputs outside client required')
    binary, metadata, startup = [read_verified(args.client / name, digest) for name, digest in HASHES.items()]
    records, _ = extract(binary, metadata, startup=startup, structures=True)
    report = recover(binary, metadata, startup, records, args.libraries)
    summary = summarize(report)
    errors = validate_summary(summary)
    if errors:
        raise ValueError(errors)
    for path, value in ((args.output, report), (args.summary_output, summary)):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(value, indent=2) + '\n', 'utf8')
    print(json.dumps(dict(fixtures=8, omittedTokenFields=4, runtimeStatus='not-tested')))


if __name__ == '__main__':
    main()
