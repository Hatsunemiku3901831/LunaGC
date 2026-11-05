"""Bounded native login dispatch, error callbacks and named field-source probes.

These are explicitly stopped prefixes, not complete Handler/constructor runs.
Environment sources return controlled values; native stores and protected wire
readers/writer execute. Unknown calls stop instead of silently succeeding.
"""
import argparse
import json
from pathlib import Path
try:
    from .extract_client72 import HASHES, read_verified, extract, PE, HEADER_VA, BODY, number, TypeResolver
    from .extract_client72_signatures import parameter_start, parameter_words
    from .extract_client72_literals import Literals72
    from .client72_emulator import Offline72, OBJECT, STREAM, GLOBAL, STACK, END
    from .trace_client72_responses import Reader72
    from .trace_client72_login import Writer72
    from .trace_client72_token import parse_wire, varint
except ImportError:
    from extract_client72 import HASHES, read_verified, extract, PE, HEADER_VA, BODY, number, TypeResolver
    from extract_client72_signatures import parameter_start, parameter_words
    from extract_client72_literals import Literals72
    from client72_emulator import Offline72, OBJECT, STREAM, GLOBAL, STACK, END
    from trace_client72_responses import Reader72
    from trace_client72_login import Writer72
    from trace_client72_token import parse_wire, varint

# offset, independent observed tag, source function, native source name, stable meaning.
SOURCES = (
    (0x50,213,0x1531EC530,'GetFPID','fp_id'),
    (0xc8,9,0x1531D02B0,'GetUniversalGameVersion','client_version'),
    (0x154,10,0x1531E0F20,'GetServerPlatformType','platform_type'),
    (0xc0,7,0x1531DC9D0,'GetDeviceName','device_name'),
    (0x48,2,0x1531C1BD0,'GetSystemVersion','system_version'),
    (0xa0,15,0x15320E900,'GetDeviceInfo','device_info'),
    (0x124,1931,0x1531D91A0,'GetNetworkIP','network_ip'),
    (0x28,1785,0x1531ED5D0,'GetNetworkIPString','client_ip_str'),
    (0xe0,552,0x1531CAF00,'GetRobotCheckOutput','robot_check_output'),
    (0x20,345,0x1531E7F90,'GetCPS','cps'),
    (0x40,891,0x1531DCB70,'GetGameVersion','game_version'),
)
GLOBALS = ((0x60,1996,0x4011b90,'Miscs','<versionHash>k__BackingField','client_version_hash'),
           (0x11c,4,0x20055b0,'PBNJKAMEJAI','GKJCGMHJLHM','language_type'),
           (0x118,274,0x20019a4,'GlobalVars','DATA_VERSION','client_data_version'))
RESPONSE_GLOBALS = ((0xb0,0x40060f0,'SILENCE_DATA_MD5','client_silence_md5'),
                    (0x18,0x4006148,'DATA_MD5','client_md5'),
                    (0x78,0x4006110,'DATA_VERSION_SUFFIX','client_version_suffix'),
                    (0xc0,0x40061a0,'SILENCE_DATA_VERSION_SUFFIX','client_silence_version_suffix'))


def field_at(records, index, offset):
    rows=[f for f in records[index]['fields'] if f.get('offsetKind')=='instance' and f['offset']==offset]
    if len(rows)!=1:raise ValueError('Ambiguous/missing instance field')
    return rows[0]


def static_field(records, raw, owner, name):
    rows=[f for r in records.values() if r['name']==owner for f in r['fields']
          if f['name']==name and f.get('rawOffset')==hex(raw)]
    if len(rows)!=1:raise ValueError('Named static field/layout mismatch')
    return rows[0]


def static_address(raw):
    kind=raw>>24;offset=raw&0xffffff
    if kind==4:return GLOBAL+offset
    if kind==2:return 0x14586B610+offset
    raise ValueError('Unsupported reviewed static storage kind')


def checkpoint(e,entry,stop,receiver,argument,r8=0):
    e.uc.context_restore(e.context);e.calls=[];e.failure=None;e.steps=0
    e.write(STACK+0x100008,END)
    for name,value in [('RSP',STACK+0x100008),('RCX',receiver),('RDX',argument),('R8',r8)]:
        e.uc.reg_write(getattr(e.R,'UC_X86_REG_'+name),value)
    e.allow(entry);e.allowed.append((stop,stop+1));captured=[];path=[]
    def observe(address):
        path.append(hex(address))
        if address==stop:
            captured.append({name:e.reg(name) for name in ('rcx','rdx','r8','r9')});e.uc.emu_stop()
    e.before_instruction=observe
    try:e.uc.emu_start(entry,END,timeout=20_000_000,count=20_000_000)
    finally:e.before_instruction=None
    if e.failure or len(captured)!=1:raise ValueError(e.failure or f'Prefix did not reach {stop:#x}')
    return dict(entry=hex(entry),checkpoint=hex(stop),registers=captured[0],nativePath=path,
                completion='checkpoint-only',models=[c['address'] for c in e.calls])


def verify_role(binary,metadata,startup,records,role):
    pe=PE(binary);h=pe.offset(HEADER_VA)
    owner=[(r,m) for r in records for m in r['methods'] if m['registeredAddress']==role['handler']]
    if len(owner)!=1 or owner[0][0]['name']!='HBIJFOJIFAH' or owner[0][1]['parameterCount']!=1:
        raise ValueError('Response Handler declaration mismatch')
    method=owner[0][1];method_base=BODY+(number(binary,h+0xb0)^0x51324a1f)
    first=parameter_start(method['index'],number(metadata,method_base+method['index']*26+4))
    base=BODY+((number(binary,h+0x174)+0xae6bcd68)&0xffffffff)
    _,ref=parameter_words(first,number(metadata,base+first*8),number(metadata,base+first*8+4))
    resolved=TypeResolver(pe,startup,records,h).resolve_index(ref)
    if resolved.get('typeDefinition')!=role['typeIndex'] or resolved.get('byRef'):
        raise ValueError('Response parameter type mismatch')
    getters=[m for r in records if r['index']==role['typeIndex'] for m in r['methods']
             if m['name']=='AMPEGOHOBJM' and m.get('virtualSlot')==22]
    if len(getters)!=1 or getters[0].get('literalU16Candidate')!=role['cmdId']:
        raise ValueError('Response command getter mismatch')
    address=int(role['registration'],16);off=pe.offset(address,12,executable=True);code=binary[off:off+12]
    if code[:4]!=b'\x66\xba'+role['cmdId'].to_bytes(2,'little') or code[4:8]!=bytes.fromhex('4989f0e8'):
        raise ValueError('Response registration opcode/argument mismatch')
    target=address+12+int.from_bytes(code[8:12],'little',signed=True)
    if target!=0x1532ED560:raise ValueError('Response registration function mismatch')
    return dict(handlerMethod=method['index'],parameterIndex=first,parameterType=ref,
                getter=getters[0]['registeredAddress'],registrationFunction=hex(target))


def recover(e,binary,metadata,startup,wire_tables):
    if any(table.get('inputSHA256')!=HASHES for table in wire_tables):
        raise ValueError('Wire observation input hash mismatch')
    pe=PE(binary)
    if number(binary,pe.offset(0x1428A53C8)+0x30,'Q')!=0x14586B610:
        raise ValueError('Reviewed primitive static storage base mismatch')
    by_type={m['typeIndex']:m for table in wire_tables for m in table['messages']}
    def wire_field(index,off):
        rows=[f for f in by_type[index]['fields'] if f['objectOffset']==off]
        f=field_at(e.records,index,off)
        if len(rows)!=1 or rows[0]['clientField']!=f['name']:raise ValueError('Existing wire/layout binding mismatch')
        return rows[0]
    def binding(index,off,name,source):
        f=wire_field(index,off)
        return dict(typeIndex=index,clientType=e.records[index]['name'],clientField=f['clientField'],
                    objectOffset=off,tag=f['tag'],type=f['type'],semanticField=name,
                    source=source,status='verified',scope='bounded-native-data-flow')
    reader=Reader72(e);fixtures=[];bindings=[]
    # Dispatch executes to return; the two selected handlers are capture models.
    for cmd,target in ((27808,0x1507BE0E0),(20835,0x1507B7BE0),(0,None),(65535,None)):
        captured=[];e.prepare();message=e.alloc()
        def capture(address):
            captured.append((address,e.reg('rcx'),e.reg('rdx')));return 0
        for a in (0x1507BE0E0,0x1507B7BE0):e.models[a]=lambda a=a:capture(a)
        e.write(STREAM+0x22,cmd,'H');e.write(STREAM+0x10,message)
        e.run(0x1507CC240)
        expected=[] if target is None else [(target,OBJECT,message)]
        if captured!=expected:raise ValueError('Native CmdID dispatch mismatch')
        fixtures.append(dict(kind='dispatch',cmdId=cmd,handler=hex(target) if target else None,passed=True))
    for a in (0x1507BE0E0,0x1507B7BE0):e.models.pop(a)
    labels=Literals72(binary,metadata).selected()['literals']
    label_map={r['index']:r for r in labels}
    # Error code comes from independent wire, passes through the actual protected
    # reader and actual handler branches to the target's own diagnostic label.
    for index,entry,off,label_index in ((37481,0x1507BE0E0,0x104,46129),(40503,0x1507B7BE0,0xe0,46063)):
        f=wire_field(index,off);label=label_map[label_index]
        if f['tag']!={37481:14,40503:8}[index] or f['type']!='int32':raise ValueError('Independent error wire mismatch')
        for value in (1,-1,-2147483648):
            data=varint(f['tag']<<3)+varint(value&((1<<64)-1));read=reader.run(index,data)
            e.write(0x145B8E499,1,'B')
            label_ptr=e.alloc();e.write(int(label['cell'],16),label_ptr)
            e.models[0x159AD6130]=lambda:0x1234  # Formatting provider, not business logic.
            formatted=[]
            def format_int():formatted.append(e.reg('edx'));return 0x5678
            e.models[0x159C1AEB0]=format_int
            result=checkpoint(e,entry,0x1531DC5E0,e.alloc(1024),OBJECT)
            if result['registers']['rcx']!=label_ptr or formatted!=[value&0xffffffff]:
                raise ValueError('Error field did not reach its own named diagnostic')
            fixtures.append(dict(kind='response-error-prefix',typeIndex=index,value=value,wire=data.hex(),labelIndex=label_index,
                                 reader=read,trace=result,passed=True))
        bindings.append(binding(index,off,'retcode',dict(handler=hex(entry),literalIndex=label_index)))
    # Real wrappers forward two response fields and a bool into a common helper.
    for index,entry,a,b in ((37481,0x151221D70,0x48,0x70),(40503,0x1512160D0,0x90,0x58)):
        for flag in (0,1):
            e.prepare();e.write(OBJECT+a,0x11112222);e.write(OBJECT+b,0x33334444)
            result=checkpoint(e,entry,0x151216140,0x22223333,OBJECT,flag)
            if result['registers']!={'rcx':0x22223333,'rdx':0x11112222,'r8':0x33334444,'r9':flag}:
                raise ValueError('Shared wrapper argument mismatch')
            fixtures.append(dict(kind='shared-configuration-wrapper',typeIndex=index,flag=flag,trace=result,passed=True))
    # Four independent wire strings -> native reader -> actual GlobalVars stores.
    parts=[];expected=[]
    for ordinal,(off,raw,source,name) in enumerate(RESPONSE_GLOBALS):
        f=wire_field(40503,off);text=f'global_probe_{ordinal}'.encode()
        if f['tag']!={0xb0:1255,0x18:437,0x78:1792,0xc0:744}[off]:raise ValueError('Independent consumer wire mismatch')
        parts.append(varint(f['tag']<<3|2)+varint(len(text))+text)
        static_field(e.records,raw,'GlobalVars',source);expected.append((off,raw,text))
        bindings.append(binding(40503,off,name,dict(owner='GlobalVars',field=source,rawOffset=hex(raw))))
    read=reader.run(40503,b''.join(parts));e.write(0x145B8E499,1,'B')
    e.synthetic_type(0x1458AD418);e.write(GLOBAL+0x26790,e.alloc())
    e.models[0x14C8AE9A0]=lambda:0;e.models[0x147603350]=lambda:0
    result=checkpoint(e,0x1507B7BE0,0x1507B7D41,e.alloc(1024),OBJECT)
    for off,raw,text in expected:
        pointer=e.read(static_address(raw))
        if pointer!=e.read(OBJECT+off) or reader.values[pointer]['hex']!=text.hex():raise ValueError('Global consumer mismatch')
    fixtures.append(dict(kind='response-global-consumers',reader=read,trace=result,passed=True))
    reader.close()
    # Bounded request constructor prefix; ALL later protected code is outside this
    # probe. Captured field values are then serialized by its complete native writer.
    writer=Writer72(e);e.models.pop(0x14C8AE9A0,None)
    for variant in (0,1):
        e.prepare();writer.objects={};values={};models={}
        def controlled(kind,ordinal):
            if kind in ('string','bytes'):
                text=f'source_{variant}_{ordinal}'.encode();return writer.pointer(kind,text),text
            value=(0xffffffff-ordinal) if variant else 0x76540000+ordinal
            return value,value
        for ordinal,(off,tag,entry,source,name) in enumerate(SOURCES):
            row=[(r,m) for r in e.records.values() for m in r['methods'] if m['registeredAddress']==hex(entry)]
            if len(row)!=1 or row[0][0]['name']!='Miscs' or row[0][1]['name']!=source:raise ValueError('Named source registration mismatch')
            f=wire_field(76410,off)
            if tag and f['tag']!=tag:raise ValueError('Independent source wire tag mismatch')
            pointer,value=controlled(f['type'],ordinal)
            models[entry]=lambda pointer=pointer:pointer;values[off]=(f['type'],value) if f['type']=='bytes' else value
            if variant==0:bindings.append(binding(76410,off,name,dict(owner='Miscs',method=source,entry=hex(entry))))
        for ordinal,(off,tag,raw,owner,field,name) in enumerate(GLOBALS,20):
            source_field=static_field(e.records,raw,owner,field);f=wire_field(76410,off)
            if f['tag']!=tag:raise ValueError('Independent global wire tag mismatch')
            pointer,value=controlled(f['type'],ordinal);e.write(static_address(raw),pointer,'Q' if f['type']=='string' else 'I');values[off]=value
            if variant==0:
                source=dict(owner=owner,field=field,rawOffset=hex(raw))
                if source_field['type']['kind']=='valuetype':source['enumClientType']=source_field['type']['name']
                bindings.append(binding(76410,off,name,source))
        for cell in (0x1458AD418,0x1458C3DF0,0x145899180):e.synthetic_type(cell)
        session=e.alloc(512);account=e.alloc(512);e.write(GLOBAL+0x9e80,session);e.write(session+0x180,account)
        e.write(account+0x50,writer.pointer('string',b'unnamed_source'))
        e.write(OBJECT+0x14,1,'I');e.allow(0x14A42BD90)
        e.models.update(models);e.models[0x14D4480C0]=lambda:OBJECT;e.models[0x14C8AE9A0]=lambda:0
        e.write(GLOBAL+0x81b8,0)
        result=checkpoint(e,0x151213730,0x151213AC6,e.alloc(1024),writer.pointer('string',b'argument_pending'))
        captured={}
        for off,value in values.items():
            kind=wire_field(76410,off)['type'];actual=e.read(OBJECT+off,'Q' if kind in ('string','bytes') else 'I')
            decoded=writer.objects[actual][1] if kind in ('string','bytes') else actual
            expected=value[1] if isinstance(value,tuple) else value
            if decoded!=expected:raise ValueError('Native constructor source/store mismatch')
            captured[off]=value
        # Generated protobuf objects use nonnull empty string/ByteString defaults.
        # A partial raw struct with null strings is not a valid writer fixture.
        defaults={f['offset']:b'' for f in e.records[76410]['fields']
                  if f.get('offsetKind')=='instance' and f['type']['kind']=='string'}
        defaults.update({f['offset']:('bytes',b'') for f in e.records[76410]['fields']
                         if f.get('offsetKind')=='instance' and f['type'].get('name')=='PPGGBLPHADP'})
        sample=writer.run(76410,defaults|captured);decoded={tag:(wire,value) for tag,wire,value in parse_wire(bytes.fromhex(sample['wire']))}
        for off,value in captured.items():
            f=wire_field(76410,off);expected=value[1] if isinstance(value,tuple) else value
            if decoded.get(f['tag'])!=(2 if isinstance(expected,bytes) else 0,expected):raise ValueError('Native source->writer mismatch')
        fixtures.append(dict(kind='request-source-to-wire',variant=variant,wire=sample['wire'],fields=len(captured),trace=result,passed=True))
    roles=[dict(semanticName='GetPlayerTokenRsp',clientType='CLDBKDNJHFG',typeIndex=37481,cmdId=27808,
                handler='0x1507be0e0',literalIndex=46129,registration='0x1507c2a4e',dispatch='0x1507cd80b'),
           dict(semanticName='PlayerLoginRsp',clientType='NDENMDAIAMC',typeIndex=40503,cmdId=20835,
                handler='0x1507b7be0',literalIndex=46063,registration='0x1507c29e5',dispatch='0x1507ce3da')]
    for role in roles:role['declarationCheck']=verify_role(binary,metadata,startup,list(e.records.values()),role)
    return dict(inputSHA256=HASHES,roles=roles,fieldBindings=bindings,fixtures=fixtures,
                runtimeStatus='not-tested',models=['ASCII/bytes wire helpers','mutable synthetic objects',
                'formatting provider and integer formatter','environment source returns','stage bookkeeping and reset helper',
                'request allocation; constructor prefix stops before protected continuation'])


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--client',required=True,type=Path);p.add_argument('--output',required=True,type=Path)
    p.add_argument('--summary-output',required=True,type=Path);p.add_argument('--libraries',type=Path,default=Path('local/migration72/python'));a=p.parse_args()
    for path in (a.output,a.summary_output):
        if path.resolve().is_relative_to(a.client.resolve()):p.error('Output must be outside client')
    b,m,s=[read_verified(a.client/n,h) for n,h in HASHES.items()];records,_=extract(b,m,startup=s,structures=True)
    directory=Path(__file__).resolve().parents[1]/'protocol/cn-7.2'
    tables=[json.loads((directory/n).read_text('utf8')) for n in ('login-wire-observations.json','response-wire-observations.json')]
    e=Offline72(b,a.libraries,records,True)
    try:report=recover(e,b,m,s,tables)
    finally:e.close()
    summary={k:v for k,v in report.items() if k!='fixtures'};summary['fixtureCount']=len(report['fixtures']);summary['schemaVersion']=1
    for path,value in ((a.output,report),(a.summary_output,summary)):
        path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(value,indent=2)+'\n','utf8')
    print(json.dumps(dict(fixtures=summary['fixtureCount'],fields=len(summary['fieldBindings']),roles=len(summary['roles']))))


if __name__=='__main__':main()
