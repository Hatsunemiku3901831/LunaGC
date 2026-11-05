"""Bind both request identities using target login-stage symbols and native prefixes.

Does not claim complete constructors, crypto, actual sends or real client login.
Stage method parameter type, enum constants, self-class cells, command getters
and paired response prefixes are all checked against the fixed input.
"""
import argparse
import json
from pathlib import Path
try:
    from .extract_client72 import HASHES, read_verified, extract, PE, HEADER_VA, BODY, number, TypeResolver
    from .extract_client72_signatures import parameter_start, parameter_words
    from .extract_client72_enums import recover as recover_enums
    from .client72_emulator import Offline72, OBJECT, GLOBAL, STACK
    from .client72_session_runtime import fragment
    from .trace_client72_login_semantics import checkpoint
except ImportError:
    from extract_client72 import HASHES, read_verified, extract, PE, HEADER_VA, BODY, number, TypeResolver
    from extract_client72_signatures import parameter_start, parameter_words
    from extract_client72_enums import recover as recover_enums
    from client72_emulator import Offline72, OBJECT, GLOBAL, STACK
    from client72_session_runtime import fragment
    from trace_client72_login_semantics import checkpoint

ROLES=(('GetPlayerTokenReq',82491,24604,8,'OnPlayerTokenReq',0x1458C5500,0x15121FFF5,
        'GetPlayerTokenRsp',37481,27808,9,'OnPlayerTokenRsp',0x1507BE0E0),
       ('PlayerLoginReq',76410,23172,6,'OnLoginReq',0x1458C3DF0,0x151213730,
        'PlayerLoginRsp',40503,20835,7,'OnLoginRsp',0x1507B7BE0))


def stage_signature(binary,metadata,startup,records):
    matches=[(r,m) for r in records for m in r['methods'] if m['registeredAddress']=='0x14c8ae9a0']
    if len(matches)!=1 or matches[0][0]['index']!=85948 or matches[0][1]['parameterCount']!=1:
        raise ValueError('Login-stage method declaration changed')
    method=matches[0][1];pe=PE(binary);h=pe.offset(HEADER_VA)
    methods=BODY+(number(binary,h+0xb0)^0x51324a1f)
    first=parameter_start(method['index'],number(metadata,methods+method['index']*26+4))
    parameters=BODY+((number(binary,h+0x174)+0xae6bcd68)&0xffffffff)
    _,ref=parameter_words(first,number(metadata,parameters+first*8),number(metadata,parameters+first*8+4))
    resolved=TypeResolver(pe,startup,records,h).resolve_index(ref)
    if resolved.get('typeDefinition')!=85949 or resolved.get('kind')!='valuetype' or resolved.get('byRef'):
        raise ValueError('Login-stage parameter is not the recovered enum')
    return dict(methodIndex=method['index'],entry='0x14c8ae9a0',parameterIndex=first,
                parameterTypeIndex=ref,enumTypeIndex=85949,enumClientType='NEKEJMEIJBF')


def self_cell(binary,row,cell,command):
    # Native clone method on the declared message loads its own class, allocates
    # and invokes the same declared type's CopyFrom. Verify both edges.
    clone=next(m for m in row['methods'] if m['name']=='GFJDJODGIDB')
    copy=next(m for m in row['methods'] if m['name']=='CopyFrom')
    address=int(clone['registeredAddress'],16);pe=PE(binary);off=pe.offset(address,0x37,executable=True)
    if binary[off+12:off+15]!=bytes.fromhex('488b1d'):
        raise ValueError('Reviewed self-class load changed')
    actual=address+19+int.from_bytes(binary[off+15:off+19],'little',signed=True)
    if actual!=cell or binary[off+0x32]!=0xe8:
        raise ValueError('Clone self-class cell/CopyFrom call changed')
    target=address+0x37+int.from_bytes(binary[off+0x33:off+0x37],'little',signed=True)
    if target!=int(copy['registeredAddress'],16):raise ValueError('Clone no longer copies same message type')
    getters=[m for m in row['methods'] if m.get('virtualSlot')==22]
    if len(getters)!=1 or getters[0].get('literalU16Candidate')!=command:
        raise ValueError('Request command getter mismatch')
    return dict(clone=hex(address),selfClassCell=hex(cell),copyFrom=hex(target),getter=getters[0]['registeredAddress'])


def recover(binary,metadata,startup,records,libraries):
    declaration=stage_signature(binary,metadata,startup,records)
    enums=recover_enums(binary,metadata,records)
    stage=next(e for e in enums['enums'] if e['typeIndex']==85949)
    symbols={v['symbol']:v['value'] for v in stage['values']}
    fixtures=[];roles=[];e=Offline72(binary,libraries,records,True)
    try:
        for name,index,cmd,value,symbol,cell,entry,rsp,ri,rc,rv,rs,handler in ROLES:
            if symbols.get(symbol)!=value or symbols.get(rs)!=rv:raise ValueError('Request/response stage symbols disagree')
            association=self_cell(binary,e.records[index],cell,cmd)
            e.prepare();e.synthetic_type(0x1458AD418);cls=e.synthetic_type(cell)
            stages=[]
            def record_stage():stages.append(e.reg('ecx'));return 0
            e.models[0x14C8AE9A0]=record_stage
            e.models.pop(0x14D4480C0,None)
            if index==76410:
                e.write(GLOBAL+0x81b8,0)
                trace=checkpoint(e,entry,0x14D4480C0,e.alloc(1024),e.alloc())
            else:
                e.allow(0x15121FF70)
                trace=fragment(e,entry,{0x14D4480C0},dict(rsp=STACK+0x100000,r14=e.alloc(1024)))
            if stages!=[value] or e.reg('rcx')!=cls:raise ValueError('Native request stage/allocation identity mismatch')
            fixtures.append(dict(kind='request-stage-allocation',semanticName=name,stageSymbol=symbol,stageValue=value,trace=trace,passed=True))
            e.models.pop(0x14C8AE9A0)
            e.prepare();e.write(0x145B8E499,1,'B');e.write(GLOBAL+0xe010,0)
            trace=checkpoint(e,handler,0x14C8AE9A0,e.alloc(1024),OBJECT)
            if e.reg('ecx')!=rv:raise ValueError('Paired response stage mismatch')
            fixtures.append(dict(kind='response-stage-prefix',semanticName=rsp,stageSymbol=rs,stageValue=rv,trace=trace,passed=True))
            # Execute the exact getter body too, independently of decoded literal.
            e.prepare();e.run(int(association['getter'],16))
            if e.reg('ax')!=cmd:raise ValueError('Native request command getter disagrees')
            fixtures.append(dict(kind='request-command-getter',semanticName=name,cmdId=cmd,passed=True))
            roles.append(dict(semanticName=name,clientType=e.records[index]['name'],typeIndex=index,cmdId=cmd,
                              stageSymbol=symbol,stageValue=value,constructorPrefix=hex(entry),
                              responseName=rsp,responseClientType=e.records[ri]['name'],responseCmdId=rc,
                              responseStageSymbol=rs,responseStageValue=rv,responseHandler=hex(handler),
                              status='verified',scope='native-stage-and-type-identity',**association))
    finally:e.close()
    return dict(schemaVersion=1,inputSHA256=HASHES,stageDeclaration=declaration,stageEnum=stage,roles=roles,
                fixtureCount=len(fixtures),fixtures=fixtures,runtimeStatus='not-tested',
                models=['synthetic initialized class objects','stage operation capture'],
                outsideScope=['Token constructor before stage block','complete constructors','actual network sends','RSA/IFix','client acceptance'])


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--client',required=True,type=Path);p.add_argument('--output',required=True,type=Path)
    p.add_argument('--summary-output',required=True,type=Path)
    p.add_argument('--libraries',type=Path,default=Path('local/migration72/python'));a=p.parse_args()
    for path in (a.output,a.summary_output):
        if path.resolve().is_relative_to(a.client.resolve()):p.error('Output must be outside client')
    b,m,s=[read_verified(a.client/name,sha) for name,sha in HASHES.items()]
    records,_=extract(b,m,startup=s,structures=True)
    report=recover(b,m,s,records,a.libraries);summary={k:v for k,v in report.items() if k!='fixtures'}
    for path,value in ((a.output,report),(a.summary_output,summary)):
        path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(value,indent=2)+'\n','utf8')
    print(json.dumps(dict(fixtures=report['fixtureCount'],roles=len(report['roles']))))


if __name__=='__main__':main()
