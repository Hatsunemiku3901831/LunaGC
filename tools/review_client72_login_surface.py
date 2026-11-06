"""Method-scoped legacy login/scene source index, never a CN72 protocol map."""
import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path
import tempfile

try:
    from .client72_java_surface import compile_scanner,scan_sources,source_graph
except ImportError:
    from client72_java_surface import compile_scanner,scan_sources,source_graph


def check_review(repo,review):
    if (review.get('schemaVersion')!=1 or review.get('closureComplete') is not False
        or review.get('targetProtocolReady') is not False or not review.get('openBoundaries')):
        raise ValueError('Source review cannot claim runtime closure or protocol readiness')
    for name,expected in review['sourceSHA256'].items():
        actual=hashlib.sha256((repo/name).read_text('utf8').encode('utf8')).hexdigest()
        if actual!=expected:raise ValueError('Source changed; review call edges again: '+name)


def recover(repo,java_home,review_path):
    review=json.loads(review_path.read_text('utf8'));check_review(repo,review)
    cache=repo/'local/migration72/analysis';cache.mkdir(parents=True,exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='login-source-',dir=cache) as directory:
        classes=Path(directory)
        compile_scanner(java_home,classes,repo/'tools/Client72JavaSurface.java')
        rows=scan_sources(repo,java_home,classes,list(review['sourceSHA256']))
    graph=source_graph(rows,review['roots'],review['reviewedEdges'])
    packets=sorted({p['constructedType'] for m in graph['methods'] for p in m['directPacketArguments']})
    constructions=sorted({p['constructedType'] for m in graph['methods'] for p in m['packetConstructions']})
    edges=Counter(v['basis'] for v in graph['edges'])
    report=dict(schemaVersion=1,scope='method-scoped legacy source; no CN72 mapping',runtimeStatus='not-tested',
                closureComplete=False,targetProtocolReady=False,javaAttributionExecuted=False,
                sourceSHA256=review['sourceSHA256'],roots=review['roots'],rootCount=len(review['roots']),
                methodCount=len(graph['methods']),edgeCount=len(graph['edges']),edgeKinds=dict(sorted(edges.items())),
                unlinkedCallSiteCount=len(graph['unresolved']),unlinkedKinds=dict(sorted(Counter(v['kind'] for v in graph['unresolved']).items())),
                directArgumentCount=sum(len(v['directPacketArguments']) for v in graph['methods']),
                directConstructedTypeCount=len(packets),directConstructedTypes=packets,
                allConstructedTypeCount=len(constructions),allConstructedTypes=constructions,
                methods=graph['methods'],sourceEdges=graph['edges'],unlinkedCallSites=graph['unresolved'],
                openBoundaries=review['openBoundaries'],
                reviewSHA256=hashlib.sha256(review_path.read_bytes()).hexdigest(),
                toolSourceSHA256={name:hashlib.sha256((repo/name).read_text('utf8').encode('utf8')).hexdigest()
                                 for name in ('tools/Client72JavaSurface.java','tools/client72_java_surface.py',
                                              'tools/review_client72_login_surface.py')},
                models=['JDK17 parse only; no dependency resolution or annotation processing',
                        'same-owner unique name/arity edges remain candidates',
                        '16 explicitly reviewed links: original 12 retained plus four static SceneHandshakeToken associations; no runtime claim',
                        'lambda depth is syntax, not evidence of immediate or asynchronous execution',
                        'direct constructor arguments to send-named calls are source candidates, not a complete required set'])
    return report


def validate_summary(report):
    errors=[]
    expected=dict(schemaVersion=1,scope='method-scoped legacy source; no CN72 mapping',runtimeStatus='not-tested',
                  closureComplete=False,targetProtocolReady=False,javaAttributionExecuted=False,rootCount=9,
                  methodCount=81,edgeCount=98,unlinkedCallSiteCount=998,directArgumentCount=80,
                  directConstructedTypeCount=61,allConstructedTypeCount=62,
                  edgeKinds={'reviewed-source-edge':16,'same-owner-unique-name-arity-candidate':82})
    if any(report.get(k)!=v for k,v in expected.items()):errors.append('Login source scope/count mismatch')
    if not report.get('openBoundaries') or not report.get('models') or len(report.get('sourceSHA256',{}))!=13:
        errors.append('Login source input or unresolved boundary missing')
    methods=report.get('methods',[]);edges=report.get('sourceEdges',[])
    if len(methods)!=81 or len(edges)!=98 or len(report.get('roots',[]))!=9:errors.append('Login source graph incomplete')
    packets=sorted({v['constructedType'] for row in methods for v in row.get('directPacketArguments',[])})
    if packets!=report.get('directConstructedTypes') or len(packets)!=61:errors.append('Login source constructor inventory differs')
    constructions=sorted({v['constructedType'] for row in methods for v in row.get('packetConstructions',[])})
    if constructions!=report.get('allConstructedTypes') or set(constructions)-set(packets)!={'PacketWindSeedUID'}:
        errors.append('Indirect packet constructor evidence missing')
    if sum(len(v.get('directPacketArguments',[])) for v in methods)!=report.get('directArgumentCount'):
        errors.append('Login source direct argument count differs')
    rows=[v for v in methods if v['id']=='emu.grasscutter.game.player.Player#onLogin()']
    if len(rows)!=1 or len(rows[0]['directPacketArguments'])!=19:errors.append('Player.onLogin method scope differs')
    associations=[edge for edge in edges if edge.get('selector')=='SceneHandshakeToken.requireCurrent']
    callers={f'emu.grasscutter.server.packet.recv.{name}#handle(GameSession,byte[],byte[])' for name in
             ('HandlerEnterSceneReadyReq','HandlerSceneInitFinishReq','HandlerEnterSceneDoneReq','HandlerPostEnterSceneReq')}
    if (len(associations)!=4 or {edge.get('source') for edge in associations}!=callers or
        any(edge.get('target')!='emu.grasscutter.server.game.SceneHandshakeToken#requireCurrent(Player,int)' or
            edge.get('kind')!='invoke' or edge.get('basis')!='reviewed-source-edge' for edge in associations)):
        errors.append('Scene token static source associations missing or promoted')
    if any('onLogout(' in v['id'] for v in methods):errors.append('Unreviewed logout expansion entered login source index')
    if Counter(v.get('basis') for v in edges)!=Counter(expected['edgeKinds']):errors.append('Source candidate edges promoted or lost')
    return errors


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--repo',type=Path,default=Path(__file__).resolve().parents[1])
    p.add_argument('--java-home',type=Path,required=True)
    p.add_argument('--review',type=Path,default=Path('protocol/cn-7.2/login-source-review.json'))
    p.add_argument('--output',type=Path,required=True);p.add_argument('--summary-output',type=Path,required=True)
    a=p.parse_args();report=recover(a.repo.resolve(),a.java_home.resolve(),a.review)
    errors=validate_summary(report)
    if errors:raise ValueError(errors)
    for path,value in ((a.output,report),(a.summary_output,{k:v for k,v in report.items() if k!='unlinkedCallSites'})):
        path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(value,indent=2)+'\n','utf8')
    print(json.dumps({k:report[k] for k in ('methodCount','edgeCount','unlinkedCallSiteCount','directConstructedTypeCount','targetProtocolReady')}))


if __name__=='__main__':main()
