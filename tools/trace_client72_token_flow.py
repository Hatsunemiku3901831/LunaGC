"""Native wire -> Token success store -> Login argument -> request wire fragments.

Execution boundaries are explicit: seed/UID handling and the caller's timer and
reset prefix are outside this experiment. Unknown helper calls fail closed.
"""
import argparse
import json
from pathlib import Path
try:
    from .extract_client72 import HASHES, read_verified, extract
    from .client72_emulator import Offline72, OBJECT, GLOBAL, STACK
    from .client72_session_runtime import fragment
    from .trace_client72_responses import Reader72
    from .trace_client72_login import Writer72
    from .trace_client72_token import varint, parse_wire
    from .trace_client72_login_semantics import checkpoint, field_at
except ImportError:
    from extract_client72 import HASHES, read_verified, extract
    from client72_emulator import Offline72, OBJECT, GLOBAL, STACK
    from client72_session_runtime import fragment
    from trace_client72_responses import Reader72
    from trace_client72_login import Writer72
    from trace_client72_token import varint, parse_wire
    from trace_client72_login_semantics import checkpoint, field_at


def verify_fields(records, tables):
    result=[]
    for index,offset,tag in ((37481,0x68,6),(76410,0xd8,1)):
        field=field_at(records,index,offset)
        matches=[f for t in tables for m in t['messages'] if m['typeIndex']==index
                 for f in m['fields'] if f['objectOffset']==offset]
        if len(matches)!=1 or (matches[0]['tag'],matches[0]['type'],matches[0]['clientField']) != (tag,'string',field['name']):
            raise ValueError('Independent token field/wire binding mismatch')
        result.append(dict(typeIndex=index,clientType=records[index]['name'],clientField=field['name'],
                           objectOffset=offset,tag=tag,type='string',semanticField='token',
                           status='verified',scope='bounded-session-credential-transfer'))
        getters=[m for m in records[index]['methods'] if m.get('virtualSlot')==22]
        if len(getters)!=1 or getters[0].get('literalU16Candidate')!={37481:27808,76410:23172}[index]:
            raise ValueError('Credential source/destination command getter mismatch')
    if field_at(records,56238,0x180)['type'].get('typeDefinition')!=58013:
        raise ValueError('Login manager session owner type mismatch')
    if field_at(records,58013,0x98)['type']['kind']!='string':
        raise ValueError('Session credential member no longer a string')
    return result


def recover(e, tables):
    if any(t.get('inputSHA256')!=HASHES for t in tables):raise ValueError('Wire input hash mismatch')
    bindings=verify_fields(e.records,tables)
    fixtures=[]
    reader=Reader72(e)
    e.allow(0x1507C21B0)
    try:
        for values,marker in (([b'session_probe_A'],1),([b'session_probe_B_\x7f'],0xffffffff),
                              ([b'obsolete',b'replacement'],0x80000000),([b''],0)):
            wire=b''.join(b'\x32'+varint(len(value))+value for value in values)
            read=reader.run(37481,wire)
            response_token=e.read(OBJECT+0x68)
            if reader.values[response_token] != dict(kind='string',hex=values[-1].hex()):
                raise ValueError('Independent TokenRsp string wire did not reach +68')
            manager=e.alloc(1024);session=e.alloc(512);sender=e.alloc(1024)
            e.write(manager+0x180,session);e.write(manager+0x1d8,marker,'I')
            e.write(GLOBAL+0xa0d0,sender)
            frame=STACK+0x100000
            store=fragment(e,0x15A4E0D74,{0x15A4E0D9A},dict(rsp=frame-0x80,r15=manager,r12=OBJECT))
            if e.read(session+0x98)!=response_token:raise ValueError('Token success fragment did not save credential')
            caller=fragment(e,0x1507C21EB,{0x151213730},dict(rsp=frame-0x80,rsi=manager))
            arguments={name:e.reg(name) for name in ('rcx','rdx','r8')}
            if arguments != dict(rcx=sender,rdx=response_token,r8=marker):
                raise ValueError('Login send arguments did not preserve session token and uint32 member')
            request=e.alloc(512);e.write(request+0x14,1,'I')
            for cell in (0x1458AD418,0x1458C3DF0):e.synthetic_type(cell)
            e.write(GLOBAL+0x81b8,0)
            e.models[0x14D4480C0]=lambda:request
            # Existing stage-bookkeeping boundary; exact args are captured below.
            stages=[]
            def stage():stages.append(e.reg('ecx'));return 0
            e.models[0x14C8AE9A0]=stage
            constructor=checkpoint(e,0x151213730,0x1512137F2,sender,response_token,marker)
            if e.read(request+0xd8)!=response_token or stages!=[6]:
                raise ValueError('Login constructor did not copy its token argument')
            writer=Writer72(e)
            defaults={f['offset']:b'' for f in e.records[76410]['fields']
                      if f.get('offsetKind')=='instance' and f['type']['kind']=='string'}
            defaults.update({f['offset']:('bytes',b'') for f in e.records[76410]['fields']
                             if f.get('offsetKind')=='instance' and f['type'].get('name')=='PPGGBLPHADP'})
            written=writer.run(76410,defaults|{0xd8:values[-1]})
            expected=[] if not values[-1] else [(1,2,values[-1])]
            if parse_wire(bytes.fromhex(written['wire']))!=expected:
                raise ValueError('Token did not serialize to independent LoginReq tag1')
            fixtures.append(dict(kind='response-session-login-token-wire',responseWire=wire.hex(),
                                 requestWire=written['wire'],marker=marker,reader=read,
                                 store=store,caller=caller,constructor=constructor,passed=True))
        # A missing session reaches the real null-error edge before assignment.
        e.prepare();manager=e.alloc(1024)
        result=fragment(e,0x15A4E0D74,{0x1404C2BE0},dict(rsp=STACK+0x100000,r15=manager,r12=OBJECT))
        fixtures.append(dict(kind='missing-session-native-rejection',trace=result,passed=True))
    finally:
        reader.close()
    return dict(schemaVersion=1,inputSHA256=HASHES,fieldBindings=bindings,fixtureCount=len(fixtures),fixtures=fixtures,
                chain=dict(responseType='CLDBKDNJHFG',responseCmdId=27808,responseOffset=0x68,responseTag=6,
                           store='0x15a4e0d93',sessionType='HOMIEDIPBBJ',sessionOffset=0x98,
                           caller='0x1507c2211',call='0x1507c2218',constructor='0x151213730',
                           requestType='KOHBOMDDFCB',requestCmdId=23172,requestOffset=0xd8,requestTag=1),
                unknown=['meaning of manager +1D8 argument','full request business pairing','remaining request fields'],
                runtimeStatus='not-tested',scope='separately executed native fragments linked by actual stored pointers',
                models=['ASCII reader/writer library boundaries','managed request allocation','stage bookkeeping capture'],
                outsideScope=['pre-store seed/UID logic','caller reset/timer condition','constructor after first token store','real network send'])


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--client',required=True,type=Path);p.add_argument('--output',required=True,type=Path)
    p.add_argument('--summary-output',required=True,type=Path)
    p.add_argument('--libraries',type=Path,default=Path('local/migration72/python'));a=p.parse_args()
    for path in (a.output,a.summary_output):
        if path.resolve().is_relative_to(a.client.resolve()):p.error('Output must be outside client')
    binary,metadata,startup=[read_verified(a.client/name,sha) for name,sha in HASHES.items()]
    records,_=extract(binary,metadata,startup=startup,structures=True)
    root=Path(__file__).resolve().parents[1]/'protocol/cn-7.2'
    tables=[json.loads((root/name).read_text('utf8')) for name in ('login-wire-observations.json','response-wire-observations.json')]
    e=Offline72(binary,a.libraries,records,True)
    try:report=recover(e,tables)
    finally:e.close()
    summary={k:v for k,v in report.items() if k!='fixtures'}
    for path,value in ((a.output,report),(a.summary_output,summary)):
        path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(value,indent=2)+'\n','utf8')
    print(json.dumps(dict(fixtures=report['fixtureCount'],fields=len(report['fieldBindings']))))


if __name__=='__main__':main()
