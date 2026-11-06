"""Decode actual generated-Java Avatar bytes with the fixed target native readers.

Expected objects come only from hash-bound independent native fixtures, never
from Java output. Does not start the game, server, debugger or database.
"""
import argparse
import hashlib
import json
from pathlib import Path
import struct
import xml.etree.ElementTree as ET
try:
    from .generate_avatar72_wire import load_evidence, render_sources, native_cases, canonical_sha256, RAW_CANONICAL_SHA256, SOURCE_CANONICAL_SHA256
    from .trace_client72_avatar_complete_raw import AvatarCompleteRaw72, inputs, avatar_aot, Offline72, HASHES
except ImportError:
    from generate_avatar72_wire import load_evidence, render_sources, native_cases, canonical_sha256, RAW_CANONICAL_SHA256, SOURCE_CANONICAL_SHA256
    from trace_client72_avatar_complete_raw import AvatarCompleteRaw72, inputs, avatar_aot, Offline72, HASHES


def java_rows(path, raw, sources):
    suite = ET.parse(path).getroot()
    if suite.get('name') != 'io.grasscutter.Protocol72AvatarWireTest' or any(suite.get(key) != '0' for key in ('failures', 'errors', 'skipped')):
        raise ValueError('Avatar generated-Java suite did not pass completely')
    rows = [json.loads(line[len('CN72_AVATAR_WIRE '):]) for line in suite.findtext('system-out', '').splitlines() if line.startswith('CN72_AVATAR_WIRE ')]
    cases = {(row['source'], row['name']): row for row in native_cases(raw, sources)}
    keys = [(row['source'], row['name']) for row in rows]
    if len(keys) != len(set(keys)) or set(keys) != set(cases): raise ValueError('Actual Java output missing/duplicate/unknown native case')
    names = {row['typeIndex']: row['clientType'] for row in raw['closureMessages']}
    for row in rows:
        expected = cases[row['source'], row['name']]
        if row['typeIndex'] != expected['typeIndex'] or row['generatedClass'] != 'emu.grasscutter.net.proto.Client72AvatarWireOuterClass$' + names[expected['typeIndex']]:
            raise ValueError('Actual Java output type/class differs from independent fixture')
        row['bytes'] = bytes.fromhex(row['wire'])
    return rows, cases, dict(tests=int(suite.get('tests')), failures=0, errors=0, skipped=0)


def float_bits(value, path=()):
    if isinstance(value, float): return {json.dumps(path): struct.pack('<f', value).hex()}
    result = {}
    if isinstance(value, dict):
        for key, item in value.items(): result.update(float_bits(item, path + (key,)))
    elif isinstance(value, list):
        for index, item in enumerate(value): result.update(float_bits(item, path + (index,)))
    return result


def verify_java(graph, rows, cases):
    observations = []
    for row in rows:
        case = cases[row['source'], row['name']]; observed = graph.run_graph(row['typeIndex'], row['bytes'])
        if observed['result'] != case['expected'] or float_bits(observed['result']) != float_bits(case['expected']):
            raise ValueError('Actual Java wire/native object mismatch: ' + row['source'] + '/' + row['name'])
        observations.append(dict(source=row['source'], name=row['name'], typeIndex=row['typeIndex'], generatedClass=row['generatedClass'],
            originalWire=case['wire'], javaWire=row['wire'], canonicalEncodingChanged=case['wire'] != row['wire'],
            expected=case['expected'], observation=observed, floatValueBits=float_bits(observed['result'])))
    return observations


def main():
    parser = argparse.ArgumentParser(description=__doc__); parser.add_argument('--client', type=Path, required=True)
    parser.add_argument('--java-results', type=Path, required=True); parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--observations', type=Path, required=True); parser.add_argument('--directory', type=Path, default=Path('protocol/cn-7.2'))
    parser.add_argument('--python-libs', type=Path, default=Path('local/migration72/python')); args = parser.parse_args()
    paths = [args.output.resolve(), args.observations.resolve()]
    if len(set(paths)) != 2 or any(path.is_relative_to(args.client.resolve()) or path == args.java_results.resolve() for path in paths): parser.error('Distinct outputs outside client and input required')
    raw, sources = load_evidence(args.directory); render_sources(raw, sources)
    rows, cases, suite = java_rows(args.java_results, raw, sources)
    b, m, s, records, cells, bindings, strings, items = inputs(args); outer = avatar_aot(b, m, s, records, dict(types=[]))
    engine = Offline72(b, args.python_libs, records, trace_instructions=False)
    graph = AvatarCompleteRaw72(engine, args.directory, cells, bindings, items, strings, outer)
    try: observations = verify_java(graph, rows, cases)
    finally: graph.close(); engine.close()
    context = dict(task='F-03d3g1', inputSHA256=HASHES, rawCanonicalSHA256=RAW_CANONICAL_SHA256,
        sourceCanonicalSHA256=SOURCE_CANONICAL_SHA256, actualJavaJUnitSHA256=hashlib.sha256(args.java_results.read_bytes()).hexdigest(), javaSuite=suite,
        javaExports=len(rows), nativeDecoded=len(observations), canonicalEncodingChanged=sum(row['canonicalEncodingChanged'] for row in observations),
        schemaTypes=65, schemaWireFields=372, targetProtocolReady=False, runtimeStatus='not-tested', productionSenderIntegrated=False,
        mergeDifferences=['Optional ordinary scalars preserve explicit-zero native merge; original Proto presence syntax is not recovered',
            'Same-branch message copy/merge, lists and map replacement are compared against independent native objects; serialization canonicalizes wire order and packing',
            'Native signed negative packed length takes jle/no-append, while protobuf rejects; excluded from positive equivalence only by this fixed case identity',
            'Native skips unknown/wrong-wire fields; protobuf retains and re-emits them; native decoded known-field result still agrees'],
        limitations=['Target offline native readers with existing explicit managed metadata/allocation/input/codec/boxing/collection models',
            'No handler, live ABI, client acceptance, producer/business meaning or D02 readiness claim'])
    for path, data in zip(paths, [dict(context, fixtures=observations), dict(context, fixtures=[{key: value for key, value in row.items() if key != 'observation'} for row in observations])]):
        path.parent.mkdir(parents=True, exist_ok=True); path.write_text(json.dumps(data, indent=2) + '\n', 'utf8')
    print(json.dumps({key: context[key] for key in ('javaExports', 'nativeDecoded', 'canonicalEncodingChanged', 'schemaTypes', 'schemaWireFields')}))


if __name__ == '__main__': main()
