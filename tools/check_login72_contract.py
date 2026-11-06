"""Compare production protoc with independent native login/response vectors."""
import argparse
import copy
import json
from pathlib import Path
import re
import subprocess

try:
    from .extract_client72 import HASHES, read_verified, extract
    from .generate_login72_contract import ROOTS, SEMANTICS
    from .observed_proto72 import fixture_text, result_text
    from .client72_emulator import Offline72
    from .client72_graph import GraphReader72
    from .client72_graph_fixtures import invalid_fixtures
except ImportError:
    from extract_client72 import HASHES, read_verified, extract
    from generate_login72_contract import ROOTS, SEMANTICS
    from observed_proto72 import fixture_text, result_text
    from client72_emulator import Offline72
    from client72_graph import GraphReader72
    from client72_graph_fixtures import invalid_fixtures


def rename_root_fields(text, names):
    """Change only top-level text-format names, leaving child fields/strings intact."""
    depth = 0
    def token(match):
        nonlocal depth
        value = match[0]
        if value == '{':
            depth += 1
        elif value == '}':
            depth -= 1
        elif depth == 0:
            return names.get(value, value)
        return value
    return re.sub(r'"(?:\\.|[^"\\])*"|[{}]|[A-Za-z_][A-Za-z_0-9]*', token, text)


def check(protoc, directory, login_trace, graph_trace, graph):
    if any(t['inputSHA256'] != HASHES for t in (login_trace, graph_trace)):
        raise ValueError('Native vectors differ from fixed target')
    repository = directory.parents[1]
    proto = repository / 'src/main/proto'
    load = lambda n: json.loads((directory/n).read_text('utf8'))
    schemas = {r['typeIndex']: r for name in ('login-wire-observations.json',
        'response-wire-observations.json', 'response-leaf-wire-observations.json')
        for r in load(name)['messages']}
    canonical_types = {raw: name for name, raw in ROOTS.items()}
    def encode(index, text):
        row = schemas[index]
        name = canonical_types.get(row['clientType'], row['clientType'])
        aliases = {f['clientField']: SEMANTICS[name][f['tag']] for f in row['fields']
                   if name in SEMANTICS and f['tag'] in SEMANTICS[name]}
        text = rename_root_fields(text, aliases)
        file = name+'.proto' if name in ROOTS else 'Client72LoginNested.proto'
        return subprocess.run([str(protoc), '--proto_path='+str(proto), '--encode='+name,
            str(proto/file)], input=text.encode('ascii'), check=True, capture_output=True).stdout
    writer_samples = copy.deepcopy(login_trace['fixtures'])
    for sample in writer_samples:
        fields = {str(f['objectOffset']):f for f in schemas[sample['typeIndex']]['fields']}
        sample['values'] = {k:v for k,v in sample['values'].items() if fields[k]['type']=='message' or
            fields[k]['cardinality']!='singular' or not primitive_default(fields[k]['type'],v)}
    samples = {(s['typeIndex'], s['wire']): s for s in writer_samples}
    for sample in writer_samples:
        actual = encode(sample['typeIndex'], fixture_text(sample, schemas, samples))
        if actual.hex() != sample['wire']:
            raise ValueError('Production schema differs from independent native writer: '+str(sample['typeIndex']))
    for sample in graph_trace['fixtures']:
        canonical_result = nondefault_graph(sample['typeIndex'],sample['expected'],schemas)
        actual = encode(sample['typeIndex'], result_text(sample['typeIndex'], canonical_result, schemas))
        if actual.hex() != sample['protocWire']:
            raise ValueError('Production schema differs from independently expected response graph: '+sample['name'])
        native = graph.run_graph(sample['typeIndex'], actual)
        if native['result'] != sample['expected']:
            raise ValueError('Native reader rejects production serialization: '+sample['name'])
    for sample in invalid_fixtures():
        try:
            graph.run_graph(sample['typeIndex'], bytes.fromhex(sample['wire']), sample.get('recursionLimit', 100))
        except ValueError as error:
            if sample['error'] not in str(error):
                raise ValueError('Unexpected native/model error: '+sample['name']) from error
        else:
            raise ValueError('Malformed response graph accepted: '+sample['name'])
    return dict(inputSHA256=HASHES, loginWriterComparisons=len(login_trace['fixtures']),
        productionToNativeGraphs=len(graph_trace['fixtures']), rejectedGraphs=len(invalid_fixtures()),
        presenceAdapter='Set nondefault values for canonical writer comparison; native explicit-zero/empty merge has separate Java regression vectors',
        runtimeStatus='not-tested', scope='Production schema encoding vs independent native writer and bounded readers')


def primitive_default(kind,value):
    if kind in ('string','bytes'):
        if isinstance(value,str):return value==''
        if isinstance(value,(tuple,list)):value=value[1]
        return value.get('hex')==''
    return value==0 or value is False


def nondefault_graph(index,result,schemas):
    fields={f['clientField']:f for f in schemas[index]['fields']};output={}
    for name,value in result.items():
        field=fields[name]
        if field['type']=='map':
            output[name]={k:nondefault_graph(field['nestedTypeIndex'],v,schemas) for k,v in value.items()} if field['valueType']=='message' else value
        elif field['type']=='message':
            output[name]=[nondefault_graph(field['nestedTypeIndex'],v,schemas) for v in value] if field['cardinality']=='repeated' else nondefault_graph(field['nestedTypeIndex'],value,schemas)
        elif field['cardinality']=='repeated' or not primitive_default(field['type'],value):
            output[name]=value
    return output


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client', type=Path, required=True)
    parser.add_argument('--protoc', type=Path, required=True)
    parser.add_argument('--login-trace', type=Path, required=True)
    parser.add_argument('--graph-trace', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args=parser.parse_args()
    directory=Path(__file__).resolve().parents[1]/'protocol/cn-7.2'
    binary, metadata, startup=[read_verified(args.client/n, h) for n,h in HASHES.items()]
    records,_=extract(binary, metadata, startup=startup, structures=True)
    engine=Offline72(binary, directory.parents[1]/'local/migration72/python', records, trace_instructions=False)
    graph=GraphReader72(engine,directory)
    try:
        report=check(args.protoc.resolve(),directory,json.loads(args.login_trace.read_text('utf8')),
            json.loads(args.graph_trace.read_text('utf8')),graph)
    finally:
        graph.close();engine.close()
    args.output.write_text(json.dumps(report,indent=2)+'\n','utf8')
    print(json.dumps(report))


if __name__=='__main__':main()
