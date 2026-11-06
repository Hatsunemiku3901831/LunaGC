"""Offline Login response nested resource consumers; no process attachment.

Uses the existing native graph reader and actual Handler prefix. Only names
with an independently named GlobalVars destination are proposed. The parent
tag61/type and bool/tag2/string/tag6 remain raw until their names are proven.
"""
import argparse
import hashlib
import json
from pathlib import Path

try:
    from .extract_client72 import HASHES, read_verified, extract
    from .client72_emulator import Offline72, OBJECT, GLOBAL
    from .client72_graph import GraphReader72
    from .client72_graph_fixtures import scalar, message
    from .trace_client72_login_semantics import checkpoint, static_field, static_address
except ImportError:
    from extract_client72 import HASHES, read_verified, extract
    from client72_emulator import Offline72, OBJECT, GLOBAL
    from client72_graph import GraphReader72
    from client72_graph_fixtures import scalar, message
    from trace_client72_login_semantics import checkpoint, static_field, static_address


# Child tag, field, instance offset, independently named static destination,
# raw static address, proposed meaning. No upstream schema is an input.
BINDINGS = (
    (1, 'KDMKODCHHIP', 0x40, 'RESOURCE_VERSION', 0x200192c, 'version'),
    (3, 'MFHJIGAMNFK', 0x38, 'RESOURCE_MD5', 0x40061f0, 'md5'),
    (4, 'GFCEGMNDCKH', 0x30, 'RESOURCE_VERSION_SIZE', 0x40060b0, 'resource_version_size'),
    (5, 'DIDEGEOAEJN', 0x20, 'RES_VERSION_SUFFIX', 0x40060f8, 'version_suffix'),
    (7, 'LMMCPBMDBKP', 0x18, 'NEXT_SCRIPT_VERSION', 0x4006320, 'next_script_version'),
)


def recover(binary, records, libraries, directory):
    e = Offline72(binary, libraries, records, trace_instructions=False)
    g = GraphReader72(e, directory)
    fixtures = []
    try:
        index = next(i for i, r in e.records.items() if r['name'] == 'MDKGJAOEHIL')
        schema = g.schemas[index]
        for tag, field, offset, destination, raw, meaning in BINDINGS:
            row = next(f for f in schema['fields'] if f['tag'] == tag)
            if (row['clientField'], row['objectOffset']) != (field, offset):
                raise ValueError('Independent child wire/layout mismatch')
            static_field(e.records, raw, 'GlobalVars', destination)
        # Both false/different-version and true/equal-version avoid the actual
        # resource-download branch. Its behavior is outside this bounded probe.
        for name, version, relogin in [('nonzero', 42, False), ('uint32-max', 0xffffffff, False),
                                       ('zero', 0, False), ('equal-version-flag', 300, True),
                                       ('duplicate-resource-configs', 99, False)]:
            texts = {3: 'md5_' + name, 4: 'size_' + name, 5: 'suffix_' + name, 7: 'next_' + name}
            child = scalar(1, version) + scalar(2, relogin) + b''.join(
                message(tag, texts[tag].encode()) for tag in (3, 4, 5, 7))
            wire = message(61, child) + message(1337, child)
            if name == 'duplicate-resource-configs':
                prior = scalar(1, 7) + message(3, b'prior-md5') + message(4, b'prior-size') + message(5, b'prior-suffix')
                wire = message(61, prior) + message(1337, prior) + wire
            read = g.run_graph(40503, wire)
            e.write(0x145B8E499, 1, 'B')
            e.synthetic_type(0x1458AD418)
            e.write(GLOBAL + 0x26790, e.alloc())
            e.models[0x14C8AE9A0] = lambda: 0
            e.models[0x147603350] = lambda: 0
            e.write(static_address(0x200192c), version if relogin else version ^ 1, 'I')
            trace = checkpoint(e, 0x1507B7BE0, 0x1507B8368, e.alloc(1024), OBJECT)
            current, following = e.read(OBJECT + 0x68), e.read(OBJECT + 0x48)
            observed = []
            for tag, field, offset, destination, raw, meaning in BINDINGS:
                child_pointer = following if tag == 7 else current
                value = e.read(static_address(raw), 'I' if tag == 1 else 'Q')
                expected = version if tag == 1 else e.read(child_pointer + offset)
                if value != expected:
                    raise ValueError('Native child-to-GlobalVars consumer mismatch')
                if tag != 1 and g.values[value]['hex'] != texts[tag].encode().hex():
                    raise ValueError('Independent string source differs from native consumer value')
                observed.append(dict(tag=tag, destination=destination,
                    value=version if tag == 1 else g.values[value]['hex'], pointerPreserved=tag != 1))
            fixtures.append(dict(name=name, wire=wire.hex(), reader=read,
                trace=trace, observed=observed, passed=True))
        # Missing child is observed separately; no missing field is filled in.
        read = g.run_graph(40503, b'')
        e.write(GLOBAL + 0x26790, e.alloc())
        trace = checkpoint(e, 0x1507B7BE0, 0x1507B8368, e.alloc(1024), OBJECT)
        fixtures.append(dict(name='missing-resource-configs', reader=read, trace=trace, passed=True))
    finally:
        g.close()
        e.close()
    return dict(schemaVersion=1, inputSHA256=HASHES, runtimeStatus='not-tested',
        wholeHandlerExecuted=False, fixtureCount=len(fixtures), fixtures=fixtures,
        fieldBindings=[dict(typeIndex=index, clientType='MDKGJAOEHIL', tag=tag,
            clientField=field, objectOffset=offset, source=dict(owner='GlobalVars',
            field=destination, rawOffset=hex(raw)), semanticField=meaning,
            status='verified', scope='native-graph-to-bounded-resource-consumer')
            for tag, field, offset, destination, raw, meaning in BINDINGS],
        unresolved=['PlayerLoginRsp.FHCEHICKAIP/tag61 semantic name',
            'PlayerLoginRsp.MONEPNFKIML/tag1337 semantic name',
            'MDKGJAOEHIL.FBJOBGOBDIE/tag2 relogin name',
            'MDKGJAOEHIL.MCKEMGKBDKG/tag6 branch name'],
        limitations=['Existing graph managed allocations/ASCII storage and runtime metadata models',
            'Stage bookkeeping and cache reset modeled as in prior login-semantic probe',
            'True/different-version resource-download branch is not executed',
            'Nested tag7 consumption proven through outer1337, shared native child type',
            'No resource data source provisioning or live-client acceptance'])


def validate_report(report):
    errors = []
    if report.get('inputSHA256') != HASHES or report.get('runtimeStatus') != 'not-tested' or report.get('wholeHandlerExecuted') is not False:
        errors.append('Input or bounded execution scope changed')
    expected = [(tag, field, offset, destination, hex(raw), meaning)
        for tag, field, offset, destination, raw, meaning in BINDINGS]
    observed = [(f.get('tag'), f.get('clientField'), f.get('objectOffset'),
        f.get('source', {}).get('field'), f.get('source', {}).get('rawOffset'), f.get('semanticField'))
        for f in report.get('fieldBindings', [])]
    if observed != expected:
        errors.append('Independent named destination contract changed')
    fixtures = report.get('fixtures', [])
    if [f.get('name') for f in fixtures] != ['nonzero', 'uint32-max', 'zero', 'equal-version-flag', 'duplicate-resource-configs', 'missing-resource-configs']:
        errors.append('Nonzero, boundary, duplicate or missing fixture lost')
    for fixture in fixtures:
        trace = fixture.get('trace', {})
        if fixture.get('passed') is not True or trace.get('entry') != '0x1507b7be0' or trace.get('checkpoint') != '0x1507b8368':
            errors.append('Native Handler prefix evidence changed')
    return errors


def summarize(report):
    result = {key: value for key, value in report.items() if key != 'fixtures'}
    result['fixtures'] = []
    for fixture in report['fixtures']:
        trace = fixture['trace']
        result['fixtures'].append({key: value for key, value in fixture.items() if key not in ('reader', 'trace')} |
            dict(trace=dict(entry=trace['entry'], checkpoint=trace['checkpoint'],
                completion=trace['completion'], models=trace['models'],
                nativePathSHA256=hashlib.sha256(json.dumps(trace['nativePath'], separators=(',', ':')).encode()).hexdigest())))
    return result


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--client', type=Path, required=True)
    p.add_argument('--output', type=Path, required=True)
    p.add_argument('--summary-output', type=Path, required=True)
    p.add_argument('--libraries', type=Path, default=Path('local/migration72/python'))
    a = p.parse_args()
    outputs = (a.output, a.summary_output)
    if len({path.resolve() for path in outputs}) != len(outputs) or any(path.resolve().is_relative_to(a.client.resolve()) for path in outputs):
        p.error('Distinct outputs must be outside client')
    b, m, s = [read_verified(a.client / n, h) for n, h in HASHES.items()]
    records, _ = extract(b, m, startup=s, structures=True)
    result = recover(b, records, a.libraries, Path('protocol/cn-7.2'))
    inputs = ('tools/client72_response_contract.py', 'tools/client72_graph.py',
        'tools/trace_client72_login_semantics.py', 'protocol/cn-7.2/response-leaf-wire-observations.json',
        'protocol/cn-7.2/response-wire-observations.json')
    result['sourceSHA256'] = {name: hashlib.sha256(Path(name).read_bytes()).hexdigest() for name in inputs}
    errors = validate_report(result)
    if errors:
        raise ValueError(errors)
    summary = summarize(result)
    if validate_report(summary):
        raise ValueError('Summary lost native contract evidence')
    for path, value in ((a.output, result), (a.summary_output, summary)):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(value, indent=2) + '\n', 'utf8')
    print(json.dumps(dict(fixtures=result['fixtureCount'], fields=len(result['fieldBindings']))))


if __name__ == '__main__':
    main()
