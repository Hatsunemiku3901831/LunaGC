"""Execute native response graph fixtures; no server integration or live acceptance."""
import argparse
import json
import subprocess
from pathlib import Path
try:
    from .extract_client72 import HASHES, read_verified, extract
    from .extract_client72_map_aot import recover as recover_aot
    from .client72_emulator import Offline72
    from .client72_graph import GraphReader72
    from .client72_graph_fixtures import fixtures, invalid_fixtures
    from .observed_proto72 import render, result_text
except ImportError:
    from extract_client72 import HASHES, read_verified, extract
    from extract_client72_map_aot import recover as recover_aot
    from client72_emulator import Offline72
    from client72_graph import GraphReader72
    from client72_graph_fixtures import fixtures, invalid_fixtures
    from observed_proto72 import render, result_text


def closure(schemas):
    wanted=set();pending=[37481,40503]
    while pending:
        index=pending.pop()
        if index in wanted:continue
        wanted.add(index)
        pending.extend(f['nestedTypeIndex'] for f in schemas[index]['fields'] if f.get('nestedTypeIndex') is not None)
    return [schemas[i] for i in sorted(wanted)]


def verify(graph,protoc=None,proto=None):
    good=[];bad=[]
    for case in fixtures():
        result=graph.run_graph(case['typeIndex'],bytes.fromhex(case['wire']))
        if result['result']!=case['expected']:raise ValueError(f"{case['name']}: native result differs: {result['result']} expected {case['expected']}")
        if ('duplicate_entry' in case['name'] or case['name']=='string_map_duplicate_and_order') and not result['mapStores']:
            raise ValueError('Expected native map node overwrite not observed')
        item=dict(case,result=result)
        if protoc:
            name=graph.schemas[case['typeIndex']]['clientType'];text=result_text(case['typeIndex'],case['expected'],graph.schemas)
            process=subprocess.run([str(protoc),'--proto_path='+str(proto.parent),'--encode=cn72.research.'+name,str(proto)],
                                   input=text.encode('ascii'),capture_output=True,check=True)
            canonical=graph.run_graph(case['typeIndex'],process.stdout)
            if canonical['result']!=case['expected']:raise ValueError(case['name']+': protoc wire/native reader mismatch')
            item.update(protocWire=process.stdout.hex(),protocResult=canonical)
        good.append(item)
    for case in invalid_fixtures():
        try:graph.run_graph(case['typeIndex'],bytes.fromhex(case['wire']),case.get('recursionLimit',100))
        except ValueError as error:
            if case['error'] not in str(error):raise ValueError(f"{case['name']}: unexpected failure: {error}") from error
            bad.append(dict(case,observedError=str(error)))
        else:raise ValueError('Malformed graph input accepted: '+case['name'])
    return good,bad


def content_bindings(schemas, cases):
    observed={}
    def visit(index,result,case):
        for f in schemas[index]['fields']:
            name=f['clientField']
            if name not in result:continue
            value=result[name]
            if (f['type']=='message' or f['cardinality'] in ('map','repeated')) and value:
                observed.setdefault((index,name),set()).add(case)
            if f.get('nestedTypeIndex') is not None:
                items=value.values() if f['cardinality']=='map' else value if f['cardinality']=='repeated' else [value]
                for child in items:visit(f['nestedTypeIndex'],child,case)
    for case in cases:visit(case['typeIndex'],case['result']['result'],case['name'])
    return [dict(typeIndex=i,clientType=schemas[i]['clientType'],clientField=name,
                 tag=next(f['tag'] for f in schemas[i]['fields'] if f['clientField']==name),
                 contentStatus='native-graph-observed',cases=sorted(names)) for (i,name),names in sorted(observed.items())]


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--client',required=True,type=Path);p.add_argument('--output',required=True,type=Path)
    p.add_argument('--proto-output',required=True,type=Path);p.add_argument('--summary-output',required=True,type=Path)
    p.add_argument('--directory',type=Path,default=Path(__file__).resolve().parents[1]/'protocol/cn-7.2')
    p.add_argument('--protoc',type=Path,help='Compare independently expected graphs encoded by protoc with native readers')
    p.add_argument('--python-libs',type=Path,default=Path('local/migration72/python'));a=p.parse_args()
    root=a.client.resolve();paths=[x.resolve() for x in (a.output,a.proto_output,a.summary_output)]
    if len(set(paths))!=3 or any(x.is_relative_to(root) for x in paths):p.error('Distinct outputs outside client required')
    b,m,s=[read_verified(root/n,h) for n,h in HASHES.items()];records,_=extract(b,m,startup=s,structures=True)
    aot=recover_aot(b,m,s,records)
    if aot!=json.loads((a.directory/'map-aot-observations.json').read_text('utf8')):raise ValueError('Stored map AOT table differs from target')
    e=Offline72(b,a.python_libs,records,trace_instructions=False);g=GraphReader72(e,a.directory)
    try:
        schemas=closure(g.schemas);proto=render(schemas);reference=(a.directory/'response-graph.proto').resolve()
        if a.protoc and reference.read_text('utf8')!=proto:raise ValueError('Research Proto differs from observations; regenerate first')
        good,bad=verify(g,a.protoc.resolve() if a.protoc else None,reference)
        bindings=content_bindings(g.schemas,good)
    finally:g.close();e.close()
    summary=dict(schemaVersion=1,inputSHA256=HASHES,scope='Offline base path, native nested/map/list/oneof control flow',
        runtimeStatus='not-tested',validFixtures=len(good),rejectedFixtures=len(bad),protocFixtures=sum('protocWire' in x for x in good),schemaMessages=len(schemas),
        fields=sum(len(x['fields']) for x in schemas),fixtureNames=[x['name'] for x in good],
        contentBindings=bindings,
        nativeEntries=sorted({entry['entry'] for x in good for entry in x['result']['entered']}),
        limitations=['Managed allocation/default initialization modeled','Synthetic interface/RGCTX metadata and primitive delegate bindings',
                     'Managed dictionary lookup/insertion and linked node allocation modeled; native map setter handles duplicates',
                     'ASCII strings, fixed array capacity 64; no IFix or live process ABI acceptance','Business semantics and enum symbols unknown'])
    report=dict(summary,fixtures=good,rejections=bad)
    for path,content in zip(paths,(json.dumps(report,indent=2)+'\n',proto,json.dumps(summary,indent=2)+'\n')):
        path.parent.mkdir(parents=True,exist_ok=True);path.write_text(content,'utf8')
    print(json.dumps({k:summary[k] for k in ('validFixtures','rejectedFixtures','schemaMessages','fields','runtimeStatus')}))


if __name__=='__main__':main()
