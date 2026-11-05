"""Execute the actual stage timestamp recorder; no identity request is sent."""
import argparse
import hashlib
import json
from pathlib import Path
import struct
try:
    from .extract_client72 import HASHES, read_verified, extract, PE
    from .extract_client72_enums import recover as enum_defaults
    from .trace_client72_request_identity import stage_signature
    from .client72_emulator import Offline72, GLOBAL, STACK
    from .client72_session_runtime import fragment
    from .client72_stage_runtime import StageRuntime72, STAGE
except ImportError:
    from extract_client72 import HASHES, read_verified, extract, PE
    from extract_client72_enums import recover as enum_defaults
    from trace_client72_request_identity import stage_signature
    from client72_emulator import Offline72, GLOBAL, STACK
    from client72_session_runtime import fragment
    from client72_stage_runtime import StageRuntime72, STAGE


def recover(binary, metadata, startup, records, libraries):
    signature = stage_signature(binary, metadata, startup, records)
    enum = enum_defaults(binary, metadata, records, indices=(85949,))['enums'][0]
    value = next(v['value'] for v in enum['values'] if v['symbol'] == 'OnPlayerEnterSceneNotify')
    if value != 10: raise ValueError('Scene stage symbol drift')
    e = Offline72(binary, libraries, records, True); rows = []
    specs = [('enabled-scene-stage', 10, True, 16, 123.125, True), ('zero-stage-zero-clock', 0, True, 16, 0.0, True),
             ('last-stage-float-rounding', 15, True, 16, 16777217.0, True), ('disabled', 10, False, 16, 123.125, True),
             ('disabled-null-array', 10, False, 16, 123.125, False), ('negative-null-array', -1, True, 16, 123.125, False),
             ('minimum-int32', -2147483648, True, 16, 123.125, True), ('past-array', 16, True, 16, 123.125, True),
             ('empty-array', 0, True, 0, 123.125, True), ('maximum-int32', 2147483647, True, 16, 123.125, True),
             ('enabled-null-array', 10, True, 16, 123.125, False), ('index-equals-length', 10, True, 10, 123.125, True)]
    try:
        for name, stage, enabled, length, clock, present in specs:
            e.prepare(); runtime = StageRuntime72(e, enabled, length, clock, present)
            error = None
            def null_array(): raise ValueError('Native stage null-array branch')
            e.models[0x1404C2BE0] = null_array
            try:
                e.run(STAGE, stage & 0xFFFFFFFF)
            except ValueError as caught:
                if 'Native stage null-array branch' not in str(caught): raise
                error = 'native-null-array'
            row = dict(name=name, stageValue=stage, enabled=enabled, arrayLength=length, arrayPresent=present,
                       clockDouble=clock, clockCalls=runtime.clock_calls, sentinelBits=runtime.sentinel,
                       arrayBits=runtime.snapshot(), writes=runtime.writes, error=error, nativeInstructions=e.steps,
                       models=[call['address'] for call in e.calls])
            runtime.close(); rows.append(row)
        e.prepare(); e.write(0x145864210, 1, 'B')
        table, callback = 0x613000000, e.alloc()
        e.uc.mem_map(table, 0x56E000)
        e.write(GLOBAL + 0x232A0, table); e.write(table + 0x56D620, callback)
        trace = fragment(e, STAGE, {0x14DD8D150}, dict(rsp=STACK + 0x100008, rcx=10))
        rows.append(dict(name='ifix-boundary', stageValue=e.reg('edx'), stop=trace['stop'], executed=False, trace=trace))
    finally:
        e.close()
    validate_fixtures(rows)
    pe = PE(binary)
    return dict(schemaVersion=1, inputSHA256=HASHES, stageDeclaration=signature, stageEnum=enum,
                timingEntry=hex(STAGE), clockWrapper='0x141447a90', enableFlag='0x145878e41', arrayGlobalOffset='0x191f8',
                codeSHA256={hex(address): hashlib.sha256(binary[pe.offset(address, size, executable=True):][:size]).hexdigest()
                            for address, size in ((STAGE, 0x10F), (0x141447A90, 26))},
                semanticCorrection='14C8AE9A0(10) records the OnPlayerEnterSceneNotify stage timestamp; it is not an identity/network request',
                fixtures=rows, fixtureCount=len(rows), runtimeStatus='not-tested', targetProtocolReady=False,
                scope='actual stage gate/array store and native double-to-float wrapper under explicit clock storage',
                models=['initialized metadata/class', 'fixed managed float array and enabled flag', 'native clock context and double value',
                        'original null-array helper observation; IFix target stopped before execution'],
                outsideScope=['live clock initialization/OS time source', 'actual stage enable lifecycle', 'IFix implementation',
                              'normal scene data consumers after this recorder, SDK/socket/client acceptance'])


def validate_fixtures(rows):
    names = {'enabled-scene-stage', 'zero-stage-zero-clock', 'last-stage-float-rounding', 'disabled', 'disabled-null-array',
             'negative-null-array', 'minimum-int32', 'past-array', 'empty-array', 'maximum-int32', 'enabled-null-array',
             'index-equals-length', 'ifix-boundary'}
    if len(rows) != len(names) or {row['name'] for row in rows} != names: raise ValueError('Incomplete native stage coverage')
    for row in rows:
        if row['name'] == 'ifix-boundary':
            if row['stageValue'] != 10 or row['stop'] != '0x14dd8d150' or row['executed']: raise ValueError('IFix stage boundary mismatch')
            continue
        active = row['enabled'] and row['stageValue'] >= 0
        error = active and not row['arrayPresent']
        write = active and row['arrayPresent'] and row['stageValue'] < row['arrayLength']
        expected = [row['sentinelBits']] * row['arrayLength']
        bits = int.from_bytes(struct.pack('<f', row['clockDouble']), 'little')
        if write: expected[row['stageValue']] = bits
        stores = [dict(index=row['stageValue'], size=4, bits=bits, source='0x14c8aea1e')] if write else []
        if row['arrayBits'] != expected or row['writes'] != stores: raise ValueError('Native stage store/float conversion mismatch')
        if row['clockCalls'] != (['context', 'double-value'] if write else []): raise ValueError('Clock called on a disabled/out-of-range stage')
        if row['error'] != ('native-null-array' if error else None): raise ValueError('Native stage error boundary mismatch')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ('client', 'output', 'summary-output'): parser.add_argument('--' + name, type=Path, required=True)
    parser.add_argument('--libraries', type=Path, default=Path('local/migration72/python')); args = parser.parse_args()
    if args.output.resolve() == args.summary_output.resolve() or any(p.resolve().is_relative_to(args.client.resolve()) for p in (args.output, args.summary_output)):
        parser.error('Distinct report paths outside client required')
    binary, metadata, startup = [read_verified(args.client / n, h) for n, h in HASHES.items()]
    records, _ = extract(binary, metadata, startup=startup, structures=True)
    report = recover(binary, metadata, startup, records, args.libraries)
    report['toolSourceSHA256'] = {name: hashlib.sha256((Path(__file__).resolve().parents[1] / name).read_bytes().replace(b'\r\n', b'\n')).hexdigest()
                                 for name in ('tools/client72_stage_runtime.py', 'tools/trace_client72_stage_timing.py')}
    summary = {k: v for k, v in report.items() if k != 'fixtures'}
    summary['observations'] = [{k: v for k, v in row.items() if k != 'trace'} for row in report['fixtures']]
    for path, value in ((args.output, report), (args.summary_output, summary)):
        path.parent.mkdir(parents=True, exist_ok=True); path.write_text(json.dumps(value, indent=2) + '\n', 'utf8')
    print(json.dumps(dict(fixtures=report['fixtureCount'], runtimeStatus='not-tested')))


if __name__ == '__main__': main()
