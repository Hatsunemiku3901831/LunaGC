"""JDK parse-only method/call evidence for the legacy server source.

No Java symbol attribution, target-client mapping or runtime execution proof.
"""
from collections import defaultdict
import hashlib
import json
from pathlib import Path
import re
import subprocess


def compile_scanner(java_home,classes,source):
    classes.mkdir(parents=True,exist_ok=True)
    subprocess.run([str(java_home/'bin/javac.exe'),'-encoding','UTF-8','-d',str(classes),str(source)],
                   capture_output=True,check=True)


def scan_sources(repo,java_home,classes,sources):
    manifest=classes/'sources.txt'
    manifest.write_text('\n'.join(sorted(sources))+'\n','utf8')
    result=subprocess.run([str(java_home/'bin/java.exe'),'-Xmx1g','-cp',str(classes),
                           'Client72JavaSurface',str(repo),str(manifest)],capture_output=True,check=True)
    rows=[json.loads(line) for line in result.stdout.decode('utf8').splitlines()]
    for row in rows:
        if row['kind']=='source':
            expected=hashlib.sha256((repo/row['file']).read_text('utf8').encode('utf8')).hexdigest()
            if expected!=row['sourceSHA256']:raise ValueError('JDK/Python normalized source hashes differ')
    return rows


def method_id(row):
    return row['owner']+'#'+row['name']+'('+','.join(p['type'] for p in row['parameters'])+')'


def packet_like(selector):
    name=selector.split('.')[-1]
    return name.startswith('Packet') or name=='BasePacket'


def direct_packet_arguments(method):
    """Syntactic direct arguments, not proof a constructor is a network packet."""
    constructs={(v['startUTF16'],v['endUTF16']):v for v in method['calls'] if v['kind']=='construct'}
    found=[]
    for call in method['calls']:
        if call['kind']!='invoke' or call['selector'].split('.')[-1] not in ('send','sendPacket','broadcastPacket'):
            continue
        for position,span in enumerate(call['argumentSpansUTF16']):
            value=constructs.get(tuple(span))
            if value is None:continue
            name=value['selector'].split('.')[-1]
            if not packet_like(value['selector']):continue
            found.append(dict(callLine=call['line'],constructionLine=value['line'],receiverCall=call['selector'],
                              argument=position,constructedType=value['selector'],lambdaDepth=value['lambdaDepth'],
                              dynamicOpcode=name=='BasePacket'))
    return found


def source_graph(rows,roots,reviewed_edges):
    methods={method_id(v):v for v in rows if v['kind']=='method'}
    if len(methods)!=sum(v['kind']=='method' for v in rows):
        raise ValueError('Method IDs collide; review initializer/local-class scopes')
    by_name=defaultdict(list)
    for key,row in methods.items():by_name[row['owner'],row['name'],len(row['parameters'])].append(key)
    manual=defaultdict(list)
    for edge in reviewed_edges:
        if edge['from'] not in methods or edge['to'] not in methods:raise ValueError('Reviewed edge endpoint absent')
        matches=[v for v in methods[edge['from']]['calls'] if
                 (v['kind'],v['selector'],v['argumentCount'])==(edge['kind'],edge['selector'],edge['argumentCount'])]
        if len(matches)!=edge['occurrences']:raise ValueError('Reviewed call occurrence drift')
        manual[edge['from'],edge['kind'],edge['selector'],edge['argumentCount']].append(edge['to'])
    pending=list(roots);seen=set();edges=[];unresolved=[];selected=[]
    while pending:
        key=pending.pop()
        if key in seen:continue
        if key not in methods:raise ValueError('Root method absent: '+key)
        seen.add(key);method=methods[key]
        selected.append(dict(id=key,file=method['file'],line=method['line'],sourceSHA256=method['sourceSHA256'],
                             callCount=len(method['calls']),directPacketArguments=direct_packet_arguments(method),
                             packetConstructions=[dict(line=v['line'],constructedType=v['selector'],lambdaDepth=v['lambdaDepth'])
                                                  for v in method['calls'] if v['kind']=='construct' and packet_like(v['selector'])]))
        for call in method['calls']:
            targets=manual.get((key,call['kind'],call['selector'],call['argumentCount']),[])
            basis='reviewed-source-edge'
            if not targets and call['kind']=='invoke':
                selector=call['selector'];name=None
                if re.fullmatch(r'(?:this\.)?[A-Za-z_$][\w$]*',selector):
                    name=selector.removeprefix('this.')
                    if name=='this':name='<init>'
                if name is not None:targets=by_name.get((method['owner'],name,call['argumentCount']),[])
                basis='same-owner-unique-name-arity-candidate'
            if len(targets)==1:
                target=targets[0];pending.append(target)
                edges.append(dict(source=key,target=target,line=call['line'],selector=call['selector'],
                                  kind=call['kind'],lambdaDepth=call['lambdaDepth'],basis=basis))
            else:
                unresolved.append(dict(source=key,line=call['line'],selector=call['selector'],kind=call['kind'],
                    argumentCount=call['argumentCount'],lambdaDepth=call['lambdaDepth'],
                    reason='ambiguous' if targets else 'not-linked',candidates=sorted(targets)))
    ordered=lambda rows:sorted(rows,key=lambda v:json.dumps(v,sort_keys=True,ensure_ascii=False))
    return dict(methods=sorted(selected,key=lambda v:v['id']),edges=ordered(edges),unresolved=ordered(unresolved))
