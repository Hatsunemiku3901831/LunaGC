"""B-02d: hash-bound born trio; bounded native execution, never a client launch.

Managed allocation/string encoding and reader primitives are explicit fixtures.
No validation, IFix success, network or UI acceptance is simulated.
"""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
try:
    from .extract_client72 import HASHES, read_verified, extract, PE, HEADER_VA, BODY, number, TypeResolver
    from .extract_client72_signatures import parameter_start, parameter_words
    from .extract_client72_enums import recover as enums
    from .client72_emulator import Offline72, OBJECT, STREAM, GLOBAL, STACK, END
    from .client72_session_runtime import fragment
    from .trace_client72_login import Writer72
    from .trace_client72_responses import Reader72
    from .trace_client72_token import varint, parse_wire
except ImportError:
    from extract_client72 import HASHES, read_verified, extract, PE, HEADER_VA, BODY, number, TypeResolver
    from extract_client72_signatures import parameter_start, parameter_words
    from extract_client72_enums import recover as enums
    from client72_emulator import Offline72, OBJECT, STREAM, GLOBAL, STACK, END
    from client72_session_runtime import fragment
    from trace_client72_login import Writer72
    from trace_client72_responses import Reader72
    from trace_client72_token import varint, parse_wire

ROLES = (("DoSetPlayerBornDataNotify",69319,5008,0x1507D25D0,0x1507C2B20),
         ("SetPlayerBornDataReq",29956,8541,None,None),
         ("SetPlayerBornDataRsp",34385,414,0x1507D2820,0x1507C2B2F))
REQ_CASES = ((0,''),(10000005,'Aether'),(10000007,'Lumine'),(127,'a'),(128,'b'),
             (0xffffffff,'旅人🚀\0'),(0x80000000,'Z'*130))
RSP_CASES = (0,1,116,132,-1,-2147483648,2147483647)


def request_wire(avatar, name):
    if type(avatar) is not int or not 0 <= avatar <= 0xffffffff:
        raise ValueError('avatar outside uint32')
    raw=name.encode('utf8')
    return (b'\x22'+varint(len(raw))+raw if raw else b'')+(b'\x58'+varint(avatar) if avatar else b'')


def response_wire(code):
    if type(code) is not int or not -2147483648 <= code <= 2147483647:
        raise ValueError('retcode outside int32')
    return b'\x78'+varint(code & ((1<<64)-1)) if code else b''


class BornWriter(Writer72):
    def pointer(self,kind,value,class_cell=None):
        if kind!='string':return super().pointer(kind,value,class_cell)
        p=self.e.alloc(len(value)*2+32);self.objects[p]=(kind,value)
        raw=value.decode('utf8').encode('utf-16-le')
        self.e.write(p+16,len(raw)//2,'I');self.e.uc.mem_write(p+20,raw)
        return p

    def string(self):
        kind,data=self.objects[self.e.reg('rdx')]
        if kind!='string':raise ValueError('Expected string fixture')
        data.decode('utf8');self.e.emit(varint(len(data))+data);return 0


def declarations(b,m,s,records):
    pe=PE(b);h=pe.offset(HEADER_VA);resolver=TypeResolver(pe,s,records,h)
    mb=BODY+(number(b,h+0xb0)^0x51324a1f)
    pb=BODY+((number(b,h+0x174)+0xae6bcd68)&0xffffffff)
    byid={r['index']:r for r in records};out=[]
    for name,index,cmd,handler,registration in ROLES:
        r=byid[index];getter=next(x for x in r['methods'] if x.get('virtualSlot')==22)
        if getter.get('literalU16Candidate')!=cmd:raise ValueError('Command getter drift')
        item=dict(semanticName=name,typeIndex=index,clientType=r['name'],cmdId=cmd,getter=getter['registeredAddress'])
        if handler:
            owner=[(t,x) for t in records for x in t['methods'] if x['registeredAddress']==hex(handler)]
            if len(owner)!=1 or owner[0][0]['index']!=56238:raise ValueError('Handler owner drift')
            method=owner[0][1]
            first=parameter_start(method['index'],number(m,mb+method['index']*26+4))
            _,ref=parameter_words(first,number(m,pb+first*8),number(m,pb+first*8+4))
            if method['parameterCount']!=1 or resolver.resolve_index(ref).get('typeDefinition')!=index:
                raise ValueError('Handler parameter drift')
            o=pe.offset(registration,12,executable=True);code=b[o:o+12]
            if code[:8]!=b'\x66\xba'+cmd.to_bytes(2,'little')+bytes.fromhex('4989f0e8') or registration+12+int.from_bytes(code[8:],'little',signed=True)!=0x1532ed560:
                raise ValueError('Registration drift')
            item.update(handler=hex(handler),registration=hex(registration),parameterIndex=first)
        else:
            clone=next(x for x in r['methods'] if x['name']=='GFJDJODGIDB')
            a=int(clone['registeredAddress'],16)+12;o=pe.offset(a,7,executable=True)
            if b[o:o+3]!=bytes.fromhex('488b1d') or a+7+int.from_bytes(b[o+3:o+7],'little',signed=True)!=0x1458c5458:
                raise ValueError('Own clone self-class changed')
            item.update(clone=clone['registeredAddress'],selfClassCell='0x1458c5458',copyScope='inlined own-field copy')
        out.append(item)
    # A fieldless notification is established from its OWN declaration and base,
    # not from the missing legacy Proto. Confirm the effective virtual slots.
    for index in (69319,34385,29956):
        if byid[index]['parent']['typeDefinition']!=67861:raise ValueError('Message base drift')
    if byid[69319]['fields'] or any(x.get('virtualSlot') in (13,17,19) for x in byid[69319]['methods']):
        raise ValueError('Notification no longer inherits empty schema')
    expected={29956:[(24,'string'),(32,'uint32')],34385:[(24,'int32')]}
    for index,fields in expected.items():
        if [(f['offset'],f['type']['kind']) for f in byid[index]['fields']]!=fields:raise ValueError('Field shape drift')
    return out


def protoc(protoc_path, mode, name, data):
    root=Path(__file__).resolve().parents[1]/'protocol/cn-7.2'
    p=subprocess.run([str(protoc_path),f'--{mode}=born72.{name}',f'--proto_path={root}','born-research.proto'],input=data,capture_output=True)
    if p.returncode:raise ValueError(p.stderr.decode('utf8','replace'))
    return p.stdout


def semantic_probes(b, records, libraries):
    e=Offline72(b,libraries,records,True);fixtures=[]
    try:
        # Native default selection values. Stop before the unrelated UI base ctor.
        e.prepare();e.allow(0x14e456280)
        tr=fragment(e,0x14e456280,{0x149a87390},dict(rsp=STACK+0x100008,rcx=OBJECT))
        if (e.read(OBJECT+0x270,'I'),e.read(OBJECT+0x274,'I'))!=(10000007,10000005):raise ValueError('Selection defaults differ')
        fixtures.append(dict(kind='selection-defaults',female=10000007,male=10000005,trace=tr,scope='defaults; config row3 may override'))
        # The named SelectMale/SelectFemale sinks establish business identity;
        # actual callback assigns selected avatar into the actual name-page layout.
        for avatar,male in ((10000005,1),(10000007,0),(0xffffffff,1)):
            e.prepare();page=e.alloc(1024);scene=e.alloc();ui=e.alloc();namepage=e.alloc(1024);desc=e.alloc()
            e.write(page+0x250,scene);e.write(GLOBAL+0x9e60,ui)
            e.write(0x145ba4340,1,'B');e.write(0x145b96281,1,'B');e.synthetic_type(0x1458f4c90)
            called=[]
            for entry,label in ((0x1494c2ab0,'SelectMale'),(0x1494c1880,'SelectFemale')):
                declared=[(r,x) for r in records for x in r['methods'] if x['registeredAddress']==hex(entry)]
                if len(declared)!=1 or declared[0][0]['name']!='MonoCharacterSelectScene' or declared[0][1]['name']!=label:raise ValueError('Named selection method drift')
                def sink(label=label):called.append(label);return 0
                e.models[entry]=sink
            e.models[0x1405336c0]=lambda:namepage
            def base():e.write(e.reg('rcx')+0xd8,desc);return 0
            e.models[0x149a87390]=base;e.allow(0x14e4569d0)
            tr=fragment(e,0x14e4569d0,{0x14a1f20c0},dict(rsp=STACK+0x100008,rcx=page,rdx=avatar,r8=male,r9=0))
            if called!=['SelectMale' if male else 'SelectFemale'] or e.reg('rdx')!=namepage or e.read(namepage+0x248,'I')!=avatar:raise ValueError('Selection/name page propagation differs')
            fixtures.append(dict(kind='selection-name-page',avatar=avatar,male=male,namedSink=called[0],trace=tr))
        # Execute submit prefix up to its REAL validator, never substitute true.
        e.prepare();page=e.alloc(1024);view=e.alloc(256);input_box=e.alloc();input_cls=e.alloc(1024);text=e.alloc()
        e.write(page+0x240,view);e.write(view+0x60,input_box);e.write(input_box,input_cls)
        e.write(input_cls+0x350,e.model_stub(lambda:text));e.allow(0x15245a2f0)
        tr=fragment(e,0x15245a2f0,{0x15245a920},dict(rsp=STACK+0x100008,rcx=page))
        if e.reg('rdx')!=text:raise ValueError('Name input source differs')
        fixtures.append(dict(kind='name-input-to-validator',trace=tr,validatorExecuted=False))
        # Native UI-entry prefix leads to the verified character-selection page.
        e.prepare();ui=e.alloc(1024);page=e.alloc(1024);e.write(ui+0x228,page);e.write(GLOBAL+0x9e60,ui)
        e.write(0x145b810d0,1,'B');e.models[0x14a1eff00]=lambda:0;e.models[0x14a1f0000]=lambda:0;e.allow(0x14a1efa30)
        tr=fragment(e,0x14a1efa30,{0x14e457800},dict(rsp=STACK+0x100008,rcx=ui))
        if e.reg('rcx')!=page:raise ValueError('Notify UI destination differs')
        fixtures.append(dict(kind='notify-selection-entry',trace=tr))
    finally:e.close()
    return fixtures


def recover(b,m,s,records,libraries,protoc_path):
    roles=declarations(b,m,s,records);fixtures=semantic_probes(b,records,libraries)
    enum_rows=enums(b,m,records,indices=(70958,75464))['enums']
    symbols={v['symbol']:v['value'] for row in enum_rows for v in row['values']}
    for symbol,value in [('RetRepeatSetPlayerBornData',116),('RetNicknameWordIllegal',132),('NicknameWordIllegal',850)]:
        if symbols[symbol]!=value:raise ValueError('Named error/event changed')
    byid={r['index']:r for r in records}
    fields={29956:((24,4,'string','nick_name'),(32,11,'uint32','avatar_id')),34385:((24,15,'int32','retcode'),),69319:()}
    messages=[]
    for role in roles:
        r=byid[role['typeIndex']]
        fs=[dict(clientField=next(f['name'] for f in r['fields'] if f['offset']==off),objectOffset=off,
                 tag=tag,type=kind,semanticField=name,cardinality='singular',presence='implicit',packed=False,
                 status='verified',wireStatus='emulated-observation') for off,tag,kind,name in fields[r['index']]]
        messages.append(dict(semanticName=role['semanticName'],clientType=r['name'],typeIndex=r['index'],fields=fs,
                             status='verified',scope='fixed-input bounded-native schema; not live acceptance'))
    e=Offline72(b,libraries,records,True)
    try:
        for role in roles:
            e.prepare();e.run(int(role['getter'],16))
            if e.reg('ax')!=role['cmdId']:raise ValueError('Native getter mismatch')
        for cmd,handler in [(5008,0x1507d25d0),(414,0x1507d2820),(0,None),(65535,None)]:
            e.prepare();captured=[];msg=e.alloc()
            for a in (0x1507d25d0,0x1507d2820):
                def capture(a=a):captured.append((a,e.reg('rdx')));return 0
                e.models[a]=capture
            e.write(STREAM+0x22,cmd,'H');e.write(STREAM+0x10,msg);e.run(0x1507cc240)
            if captured!=([(handler,msg)] if handler else []):raise ValueError('Dispatch mismatch')
            fixtures.append(dict(kind='dispatch',cmdId=cmd,handler=hex(handler) if handler else None,passed=True))
        for a in (0x1507d25d0,0x1507d2820):e.models.pop(a)
        w=BornWriter(e);e.allow(0x14a42bd90)
        for avatar,name in REQ_CASES:
            # Run the request constructor from entry, with declared application
            # arguments; stop at send entry, before queue/SDK tail.
            e.prepare();w.objects={};p=w.pointer('string',name.encode('utf8'));cls=e.synthetic_type(0x1458c5458)
            def allocate():
                if e.reg('rcx')!=cls:raise ValueError('Wrong request allocation')
                e.write(OBJECT+0x14,1,'I');return OBJECT
            e.models[0x14d4480c0]=allocate;e.allow(0x151223b80)
            trace=fragment(e,0x151223b80,{0x15120d730},dict(rsp=STACK+0x100008,rcx=e.alloc(),rdx=avatar,r8=p))
            if e.reg('rdx')!=OBJECT or e.read(OBJECT+24)!=p or e.read(OBJECT+32,'I')!=avatar:raise ValueError('Constructor source mismatch')
            e.failure=None;e.steps=0;wire=e.run(0x14cc44560)
            expected=request_wire(avatar,name)
            text=f'avatar_id: {avatar}\nnick_name: "'+''.join(f'\\{x:03o}' for x in name.encode('utf8'))+'"\n'
            compiled=protoc(protoc_path,'encode','SetPlayerBornDataReq',text.encode())
            if wire!=expected or wire!=compiled:raise ValueError('Request native/protoc wire differs')
            e.models[0x1517204c0]=lambda:len(varint(len(name.encode('utf8'))))+len(name.encode('utf8'))
            e.run(0x14cc44380)
            if e.reg('eax')!=len(wire):raise ValueError('Native request size mismatch')
            fixtures.append(dict(kind='request',avatar=avatar,nameUTF8Hex=name.encode('utf8').hex(),wire=wire.hex(),trace=trace,size=len(wire),protocMatch=True))
        # Null string is rejected by the ORIGINAL constructor, never forced valid.
        e.prepare();e.allow(0x151223b80)
        trace=fragment(e,0x151223b80,{0x1404c2770,0x15120d730},dict(rsp=STACK+0x100008,rcx=e.alloc(),rdx=5,r8=0))
        if trace['stop']!='0x1404c2770':raise ValueError('Null string not rejected')
        fixtures.append(dict(kind='request-null-rejection',trace=trace))
        rd=Reader72(e)
        for code in RSP_CASES:
            wire=response_wire(code)
            compiled=protoc(protoc_path,'encode','SetPlayerBornDataRsp',f'retcode: {code}'.encode())
            if wire!=compiled:raise ValueError('Response protoc differs')
            read=rd.run(34385,wire,0x15374ac20)
            if e.read(OBJECT+24,'i')!=code:raise ValueError('Native response reader differs')
            e.run(0x15374ab50)
            if e.reg('eax')!=len(wire):raise ValueError('Response native size differs')
            errors=[];events=[];success=[];e.write(GLOBAL+0xbe90,e.alloc());e.write(GLOBAL+0x9e50,e.alloc())
            def error():errors.append(e.reg('edx'));return 0
            def event():events.append(e.read(e.reg('rdx'),'I'));return 0
            def ok():success.append(True);return 0
            e.models.update({0x14cd2b920:error,0x1532d6720:event,0x14cd68860:ok})
            e.failure=None;e.run(0x1507d2820,e.alloc(1024),OBJECT)
            if errors!=([code&0xffffffff] if code else []) or events!=([850] if code==132 else []) or success!=([True] if code==0 else []):raise ValueError('Response branches differ')
            fixtures.append(dict(kind='response',retcode=code,wire=wire.hex(),reader=read,errorArguments=errors,events=events,success=bool(success),protocMatch=True))
        # Duplicate scalar last wins; mismatched wire/unknown 64-bit, nested and
        # repeated envelopes are skipped, not claimed as declared trio fields.
        extras=b'\x08'+varint(1<<63)+b'\x12\x02\x08\x01'+b'\x18\x01\x18\x02'+b'\x7a\x01x'
        for index,entry,wire,expected in [(34385,0x15374ac20,b'\x78\x01\x78\x84\x01'+extras,132),(69319,0x1495c9f10,b'',0),(69319,0x1495c9f10,extras,0)]:
            read=rd.run(index,wire,entry)
            if index==34385 and e.read(OBJECT+24,'i')!=expected:raise ValueError('Duplicate/wrong wire mismatch')
            fixtures.append(dict(kind='reader-boundary',typeIndex=index,reader=read))
        for index,entry in [(34385,0x15374ac20),(69319,0x1495c9f10)]:
            for wire in (b'\x78\x80',b'\x12\x03x',b'\x00',b'\x08'+b'\x80'*10):
                try:rd.run(index,wire,entry)
                except ValueError as error:fixtures.append(dict(kind='malformed-reader',typeIndex=index,wire=wire.hex(),error=str(error),scope='explicit-wire-input-model'))
                else:raise ValueError('Malformed wire accepted by fixture')
        rd.close()
        # Inherited empty writer AND size execute, then native notification gates.
        e.prepare();wire=e.run(0x1495c9ef0);e.run(0x1495c9db0)
        if wire or e.reg('eax') or protoc(protoc_path,'encode','DoSetPlayerBornDataNotify',b''):raise ValueError('Empty notification contract mismatch')
        for enabled,state in ((0,0),(1,1),(1,0)):
            e.prepare();gm=e.alloc();ui=e.alloc();receiver=e.alloc(1024)
            e.write(GLOBAL+0xb6d0,gm);e.write(gm+0x1a,enabled,'B');e.write(receiver+0x1d8,state,'I');e.write(GLOBAL+0x9e60,ui)
            e.write(0x145b913d2,1,'B');calls=[]
            e.models[0x14a2104f0]=lambda:0 # UI transition sink
            e.models[0x14116b850]=lambda:0 # explicit platform fixture, no mobile branch claim
            def select():calls.append(e.reg('rcx'));return 0
            e.models[0x14a1efa30]=select;e.run(0x1507d25d0,receiver,OBJECT)
            if calls!=([ui] if enabled and not state else []):raise ValueError('Notify gate mismatch')
            fixtures.append(dict(kind='notify-handler',enabled=enabled,state=state,opensSelection=bool(calls)))
    finally:e.close()
    return dict(schemaVersion=1,inputSHA256=HASHES,roles=roles,messages=messages,fixtureCount=len(fixtures),fixtures=fixtures,
                namedErrors={k:symbols[k] for k in ('RetRepeatSetPlayerBornData','RetNicknameWordIllegal','NicknameWordIllegal')},
                runtimeStatus='not-tested',scope='bounded-native-trio-evidence',
                models=['UI base constructor storage and named rendering sinks; text input virtual getter','initialized class/storage objects; IFix absent fixture','managed message allocation','UTF-8 conversion and string size','reader scalar/tag/skip primitives with explicit rejection rules','UI/error/event/success sinks and platform=0'],
                outsideScope=['live socket/SDK/UI validation and rendering','actual dispatch registration lifecycle','request queue and SDK tail','full success continuation','B-03/C-01/D-02'])


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--client',type=Path,required=True);p.add_argument('--protoc',type=Path,required=True)
    p.add_argument('--output',type=Path,required=True);p.add_argument('--summary-output',type=Path,required=True)
    p.add_argument('--libraries',type=Path,default=Path('local/migration72/python'));a=p.parse_args()
    for out in (a.output,a.summary_output):
        if out.resolve().is_relative_to(a.client.resolve()):p.error('Output must be outside client')
    b,m,s=[read_verified(a.client/n,h) for n,h in HASHES.items()]
    records,_=extract(b,m,startup=s,structures=True)
    report=recover(b,m,s,records,a.libraries,a.protoc)
    report['protocSHA256']=hashlib.sha256(a.protoc.read_bytes()).hexdigest()
    summary={k:v for k,v in report.items() if k!='fixtures'}
    summary['fixtureKinds']={k:sum(x['kind']==k for x in report['fixtures']) for k in sorted({x['kind'] for x in report['fixtures']})}
    for path,value in ((a.output,report),(a.summary_output,summary)):
        path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(value,indent=2)+'\n',encoding='utf8')
    print(json.dumps(dict(fixtures=report['fixtureCount'],roles=report['roles'])))


if __name__=='__main__':main()
