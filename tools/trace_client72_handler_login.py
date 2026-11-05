"""Continuous native Token Handler -> Login constructor -> frame-builder entry.

Login/Full state, cached configuration, clock, SDK, old Shell and transport are
explicit offline inputs. Native callbacks/conditions execute. No frame body,
socket, actual SDK initialization, whole Handler return or live login is claimed.
"""
import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path
import struct
try:
    from .extract_client72 import HASHES,read_verified,extract,PE,HEADER_VA,number,TypeResolver
    from .extract_client72_literals import Literals72,USAGE_REGISTRATION
    from .extract_client72_enums import recover as recover_enums
    from .client72_emulator import Offline72,GLOBAL,OBJECT
    from .client72_response_crypto import REJECT
    from .trace_client72_handler_tail import HandlerTail72,TailReader72,HASH_RETURN,expected_input,declarations as tail_declarations
    from .trace_client72_handler_core import HandlerCore72
    from .trace_client72_response_crypto import discover_private_key,response_wire
    from .client72_rsa_fixture import private_numbers,encrypt_seed,sign_sha256
    from .trace_client72_login_constructor import Constructor72
    from .trace_client72_login import Writer72
    from .trace_client72_send_flow import SendFlow72,declarations as send_declarations
    from .trace_client72_request_identity import self_cell
    from .trace_client72_session import java_keys
    from .trace_client72_token import parse_wire,varint
except ImportError:
    from extract_client72 import HASHES,read_verified,extract,PE,HEADER_VA,number,TypeResolver
    from extract_client72_literals import Literals72,USAGE_REGISTRATION
    from extract_client72_enums import recover as recover_enums
    from client72_emulator import Offline72,GLOBAL,OBJECT
    from client72_response_crypto import REJECT
    from trace_client72_handler_tail import HandlerTail72,TailReader72,HASH_RETURN,expected_input,declarations as tail_declarations
    from trace_client72_handler_core import HandlerCore72
    from trace_client72_response_crypto import discover_private_key,response_wire
    from client72_rsa_fixture import private_numbers,encrypt_seed,sign_sha256
    from trace_client72_login_constructor import Constructor72
    from trace_client72_login import Writer72
    from trace_client72_send_flow import SendFlow72,declarations as send_declarations
    from trace_client72_request_identity import self_cell
    from trace_client72_session import java_keys
    from trace_client72_token import parse_wire,varint

FRAME=0x14A447E00
STOPS=dict(send=FRAME,delay=0x1507B9A50,missing_manager=0x15A4E1B99,missing_gate=0x1507C0CBD,
           medium_package=0x14BB767E3,bad_signature=REJECT)
TYPES=((698995,0x145887448,142),(702787,0x14589A988,82313),(708398,0x1458BA860,63102))
BASE=dict(token='login_token_A',uid=1,marker=1,sequence=1,sentMs=1234,clock=100.0,threshold=0.0,
          parts=('CNRELWin7.2.0',None,None,'123','456','789'),secret='secret-A',rawFlag=0)
CASES=(('login-full',{},'send'),
       ('high-fields',dict(token='9007199254740993',uid=0xffffffff,marker=0xffffffff,sequence=0xffffffff,
                           sentMs=0x200000044,threshold=99.0,secret='\u5bc6\u94a5\U0001f511\0',rawFlag=1),'send'),
       ('empty-token-version',dict(token='',uid=0,marker=0x80000000,sequence=0,sentMs=0,parts=(None,)*6,secret=''),'send'),
       ('clock-equal',dict(threshold=100.0),'delay'),('clock-below',dict(clock=99.0,threshold=100.0),'delay'),
       ('missing-game-manager',{},'missing_manager'),('missing-gate-state',{},'missing_gate'),
       ('medium-package',{},'medium_package'),('invalid-response-signature',{},'bad_signature'))
VISITS=(0x15A4E16C9,0x14BB76740,0x14BB76A31,0x1507C0BB0,0x1507C21B0,
        0x141447780,0x151213730,0x151214D20,FRAME)


class ContinuousConstructor72(Constructor72):
    def text(self,pointer):
        # Consume strings actually allocated by the Handler and native Concat.
        e=self.e
        return bytes(e.uc.mem_read(pointer+20,e.read(pointer+16,'I')*2)).decode('utf-16-le') if pointer else ''


class HandlerLogin72(HandlerTail72):
    def __init__(self,e,binary,metadata,client_seed,signer,ctor,source,inputs,mode):
        super().__init__(e,binary,metadata,client_seed,signer,inputs['parts'])
        self.ctor=ctor;self.inputs=inputs;self.mode=mode;self.flow=None;self.request_before=None
        self.connection_visits=Counter();self.delegate_calls=[];self.clock_values=[]
        e.write(self.account+0x50,source);ctor.provider=self.provider
        world=e.alloc();manager=e.alloc();e.write(world+0x18,0,'I');e.write(manager+0x20,world)
        e.write(GLOBAL+0xb6d0,0 if mode=='missing_manager' else manager)
        e.write(0x14586cf14,2 if mode=='medium_package' else 5,'I')
        e.write(GLOBAL+0x111b0,0 if mode=='missing_gate' else e.alloc())
        auxiliary=e.read(GLOBAL+0xa070);e.write(auxiliary+0x30,e.alloc(256))
        e.write(GLOBAL+0xa0d0,ctor.sender);clock=e.alloc(256);e.write(clock+0xa0,inputs['clock'],'d')
        e.write(0x1457a98b0+7*8,clock);e.write(self.manager+0x208,inputs['threshold'],'f')
        e.write(self.manager+0x1d8,inputs['marker'],'I')
        flags=e.read(GLOBAL+0x6100);array=e.alloc(40);e.write(array+24,2)
        e.write(array+32,5714,'I');e.write(array+36,3628,'I');e.write(flags+16,array);e.write(flags+24,2,'I')
        for address in (0x1507C0BB0,0x1507C21B0,0x14BB76740,0x14A9CD540):e.allow(address)
        self.gate_classes={e.synthetic_type(cell):index for _,cell,index in TYPES}
        self.old_allocate=e.models[0x1405336C0];e.models[0x1405336C0]=self.allocate_gate
        self.method=e.alloc(64);e.write(self.method+8,0x1507C0BB0);e.write(0x145A00A40,self.method)
        for stop in STOPS.values():e.allowed.append((stop,stop+1))

    def allocate_gate(self):
        e=self.e;cls=e.reg('rcx')
        if cls not in self.gate_classes:return self.old_allocate()
        pointer=e.alloc(256);e.write(pointer,cls);return pointer

    def observe_response(self,address):
        e=self.e
        if address==HASH_RETURN:
            HandlerCore72.observe_response(self,address);self.version.observe(address)
        else:super().observe_response(address)
        if address in VISITS:self.connection_visits[address]+=1
        if address==0x14BB76A31:
            if (e.reg('r8'),e.reg('rcx'),e.reg('rdx'))!=(0x1507C0BB0,self.manager,self.method):
                raise ValueError('Native Action did not preserve registered target/receiver/MethodInfo')
            self.delegate_calls.append(dict(target='0x1507c0bb0',receiverPreserved=True,methodInfoPreserved=True))
        elif address==0x1507C21E2:
            self.clock_values.append(e.reg('xmm0')&0xffffffff)
        elif address==0x151213730:
            if (e.reg('rcx'),e.reg('rdx'),e.reg('r8d'))!=(self.ctor.sender,e.read(OBJECT+0x68),self.inputs['marker']):
                raise ValueError('Native Login arguments differ from saved response/session')
        elif address==0x151214D20:
            if self.flow is not None:raise ValueError('Duplicate Login send')
            self.request_before=bytes(e.uc.mem_read(self.ctor.request,0x160))
            self.flow=SendFlow72(e,self.ctor,76410,23172,self.inputs['sequence'],self.inputs['sentMs'],'connected')
        if address in STOPS.values():self.stop=address;e.uc.emu_stop()


def declarations(binary,metadata,startup,records):
    tail=tail_declarations(binary,metadata,startup,records);transport=send_declarations(binary,metadata,startup,records)
    by_index={r['index']:r for r in records};pe=PE(binary);literals=Literals72(binary,metadata)
    reg=pe.offset(USAGE_REGISTRATION);type_cache=number(binary,reg+0x48,'Q');method_cache=number(binary,reg+0x10,'Q')
    resolver=TypeResolver(pe,startup,records,pe.offset(HEADER_VA));types=[];callback=None
    for i,cell,index in (*TYPES,(749199,0x145A00A40,467563)):
        key=(i*0x57c15286+0x26ddebda)&0xffffffff
        source=((number(metadata,literals.usages_base+i*8)+0xabbdcabf)&0xffffffff)^key^0x2a357d90
        slot=number(metadata,literals.usages_base+i*8+4)^key^0x1855aa65
        if i==749199:
            method=next(m for m in by_index[56238]['methods'] if m['index']==index)
            if source>>29!=3 or source&0x1fffffff!=index or method_cache+slot*8!=cell or method['registeredAddress']!='0x1507c0bb0':
                raise ValueError('Action MethodInfo usage changed')
            callback=dict(usageRow=i,cell=hex(cell),kind=3,methodDefinition=index,ownerType=56238,target=method['registeredAddress'])
        else:
            resolved=resolver.resolve_index(source&0x1fffffff)
            if source>>29!=1 or type_cache+slot*8!=cell or resolved.get('typeDefinition')!=index:
                raise ValueError('Gate allocation type binding changed')
            types.append(dict(usageRow=i,cell=hex(cell),typeIndex=index,name=resolved['name']))
    enums=recover_enums(binary,metadata,records,indices=(11326,11334))['enums']
    enum_values={r['clientType']:{v['symbol']:v['value'] for v in r['values']} for r in enums}
    if (enum_values['WorldType']['Login'],enum_values['WorldType']['Level'],enum_values['PackageType']['Full'],
            enum_values['PackageType']['Medium'],enum_values['PackageType']['PlayGo'])!=(0,2,5,2,3):
        raise ValueError('World/package input symbols changed')
    def field(owner,offset):return next(f for f in by_index[owner]['fields'] if f.get('offsetKind')=='instance' and f['offset']==offset)
    if (field(50103,0x20)['type'].get('typeDefinition'),field(59570,0x18)['type'].get('typeDefinition'))!=(59570,11326):
        raise ValueError('GameManager world field type chain changed')
    singleton=next(f for f in by_index[50103]['fields'] if f['name']=='Instance')
    package=next(f for r in records if r['name']=='GlobalVars' for f in r['fields'] if f['name']=='packageType')
    if singleton['rawOffset']!='0x400b6d0' or package['rawOffset']!='0x2001904' or package['type'].get('typeDefinition')!=11334:
        raise ValueError('World/package static storage changed')
    return dict(tail=tail,transport=transport,types=types,callback=callback,environmentEnums=enums,
                worldChain=[50103,0x20,59570,0x18,11326],packageAddress='0x14586cf14',
                login=self_cell(binary,by_index[76410],0x1458C3DF0,23172))


def recover(binary,metadata,startup,records,libraries,signing_key,java_home,root):
    declared=declarations(binary,metadata,startup,records)
    private,_,_=discover_private_key(binary,metadata,records,libraries);client=private_numbers(private)
    signer=private_numbers(signing_key.read_bytes());plain=bytes.fromhex('fedcba9876543210');seed=0x0123456789abcdef
    keys,java_sources=java_keys(java_home,[int.from_bytes(plain,'big')^seed],root);fixtures=[]
    for ordinal,(name,changes,mode) in enumerate(CASES):
        inputs=BASE|changes;signature=sign_sha256(signer,plain)
        if mode=='bad_signature':signature=bytes([signature[0]^1])+signature[1:]
        token=inputs['token'].encode('ascii');secret=inputs['secret'].encode('utf8');blob=b'robot\0\xff'
        wire=response_wire(encrypt_seed(client,plain),signature)+b'\x48'+varint(inputs['uid'])+b'\x32'+varint(len(token))+token
        wire+=b'\x1a'+varint(len(blob))+blob+b'\x92\x29'+varint(len(secret))+secret+b'\x08'+varint(inputs['rawFlag'])
        e=Offline72(binary,libraries,records,True);reader=None;runtime=None
        try:
            reader=TailReader72(e);read=reader.run(37481,wire)
            ctor=ContinuousConstructor72(e,binary,metadata,ordinal,'123','456');ctor.close()
            e.models.pop(0x1531D02B0);e.models.pop(0x159A8D050)
            source=e.read(e.read(e.read(GLOBAL+0x9e80)+0x180)+0x50)
            runtime=HandlerLogin72(e,binary,metadata,seed,signer,ctor,source,inputs,mode)
            trace,key=runtime.run_handler();sent=mode=='send';rejected=mode=='bad_signature'
            if runtime.stop!=STOPS[mode]:raise ValueError(f'{name}: wrong branch boundary {runtime.stop:#x}')
            if key!=(None if rejected else keys[0]):raise ValueError('Native session key differs from independent Java')
            if runtime.stages!=([9,6] if sent else [9]):raise ValueError('Unexpected native login-stage sequence')
            version,encoded,hashed=expected_input(inputs['parts'],inputs['secret'])
            if rejected:
                if runtime.tail_entries or runtime.version.hash_inputs or runtime.connection_visits:
                    raise ValueError('Rejected response reached later Handler/Login work')
            else:
                if runtime.version.hash_inputs!=[encoded] or runtime.version.hash_outputs!=[hashlib.sha1(encoded).digest()]:
                    raise ValueError('Connected Handler version hash differs')
                if e.read(runtime.account+0x168,'I')!=inputs['uid'] or e.read(runtime.account+0x98)!=e.read(OBJECT+0x68):
                    raise ValueError('Connected Handler lost UID or session token')
            request_wire=header_wire=None;checks=None
            if sent:
                if (e.read(ctor.request+0xd8),e.read(ctor.request+0x60))!=(e.read(OBJECT+0x68),e.read(GLOBAL+0x11b90)):
                    raise ValueError('Login token/hash pointers were not preserved')
                flow=runtime.flow
                if runtime.request_before!=bytes(e.uc.mem_read(ctor.request,0x160)):
                    raise ValueError('Send routing mutated Login request')
                if (e.reg('rcx'),e.read(flow.packet+0x10),e.read(flow.packet+0x22,'H'),e.read(flow.header+0x30,'I'),e.read(flow.header+0x18))!=(
                        flow.packet,ctor.request,23172,inputs['sequence'],inputs['sentMs']):
                    raise ValueError('Frame-builder pointer/command/header differs')
                writer=Writer72(e);request_wire=writer.run(76410,ctor.values(76410,ctor.request,writer))['wire']
                header_wire=writer.run(27182,ctor.values(27182,flow.header,writer))['wire']
                decoded={t:(k,v) for t,k,v in parse_wire(bytes.fromhex(request_wire))}
                checks={1:token,9:version.encode('ascii'),1996:hashed.encode('ascii')}
                for tag,value in checks.items():
                    if decoded.get(tag,(2,b''))!=(2,value):raise ValueError('Native Login wire differs from response/version/hash source')
                expected_header=(b'\x18'+varint(inputs['sequence']) if inputs['sequence'] else b'')+(b'\x30'+varint(inputs['sentMs']) if inputs['sentMs'] else b'')
                if bytes.fromhex(header_wire)!=expected_header:raise ValueError('Independent 32/64-bit header wire differs')
                checks={str(k):v.hex() for k,v in checks.items()}
            elif ctor.request is not None or runtime.flow is not None:
                raise ValueError('Stopped branch constructed or sent a Login request')
            fixtures.append(dict(name=name,mode=mode,inputs=inputs,stop=hex(runtime.stop),stages=runtime.stages,
                                 visits={hex(k):v for k,v in sorted(runtime.connection_visits.items())},
                                 delegates=runtime.delegate_calls,clockFloatBits=runtime.clock_values,
                                 versionSourceCalls=runtime.version.version_calls,requestSent=sent,
                                 tokenHashPointersPreserved=sent,requestUnchanged=sent,cmdId=23172 if sent else None,
                                 wireFields=checks,headerWire=header_wire,requestWire=request_wire,
                                 nativeOperations=runtime.operations,keyChecks=runtime.key_checks,
                                 sessionKeySHA256=hashlib.sha256(key).hexdigest() if key else None,javaByteComparison=not rejected,
                                 reader=read,trace=trace,passed=True))
            print(json.dumps(dict(case=name,stop=hex(runtime.stop),loginSent=sent)),flush=True)
        finally:
            if runtime:runtime.close()
            if reader:reader.close()
            e.close()
    return dict(schemaVersion=1,inputSHA256=HASHES,fixtureCount=len(fixtures),fixtures=fixtures,
                results=[{k:v for k,v in f.items() if k not in ('reader','trace','requestWire')} for f in fixtures],
                declarations=declared,entry='0x1507be0e0',sendStop=hex(FRAME),cmdId=23172,
                runtimeStatus='not-tested',continuousLoginExecuted=True,wholeHandlerReturned=False,frameBuilderExecuted=False,
                javaSourceSHA256=java_sources,unknownResponseTags=[1,3,658],unknownRequestTags=[965],
                modelsFrom=['handler-tail-observations.json','login-constructor-observations.json','send-flow-observations.json'],
                addedInputs=['WorldType.Login=0 and PackageType.Full=5; Medium=2 negative branch',
                             'cached auxiliary config +30, raw gate presence at GLOBAL+111B0 and +3B=false',
                             'engine selector7 clock storage; native float conversion/condition execute',
                             'independently registered Action/MethodInfo storage',
                             'preserved prior account+50 source and one shared NumberFormatInfo',
                             'native-compatible UTF16 string reads and one literal storage hook'],
                trustInputReplacements=['native private-key signature','server public key and signature','root PEM input'],
                outsideScope=['real SDK/version/config/clock initialization','raw gate state business name',
                              'new Shell/IFix','delayed retry and package-download UI','remaining Handler after nested send returns',
                              'frame body/encryption/socket','real-client acceptance'])


def validate_summary(report):
    errors=[]
    expected=dict(schemaVersion=1,inputSHA256=HASHES,fixtureCount=len(CASES),entry='0x1507be0e0',sendStop=hex(FRAME),
                  cmdId=23172,runtimeStatus='not-tested',continuousLoginExecuted=True,wholeHandlerReturned=False,
                  frameBuilderExecuted=False,unknownResponseTags=[1,3,658],unknownRequestTags=[965])
    if any(report.get(k)!=v for k,v in expected.items()):errors.append('Handler Login: scope/input mismatch')
    declared=report.get('declarations',{})
    expected_callback=dict(usageRow=749199,cell='0x145a00a40',kind=3,methodDefinition=467563,ownerType=56238,target='0x1507c0bb0')
    if declared.get('callback')!=expected_callback:errors.append('Handler Login: callback provenance mismatch')
    if [(t.get('usageRow'),t.get('cell'),t.get('typeIndex')) for t in declared.get('types',[])]!=[(i,hex(c),t) for i,c,t in TYPES]:
        errors.append('Handler Login: class usage mismatch')
    values={r.get('clientType'):{v.get('symbol'):v.get('value') for v in r.get('values',[])} for r in declared.get('environmentEnums',[])}
    if (values.get('WorldType',{}).get('Login'),values.get('WorldType',{}).get('Level'),
            values.get('PackageType',{}).get('Full'),values.get('PackageType',{}).get('Medium'),values.get('PackageType',{}).get('PlayGo'))!=(0,2,5,2,3):
        errors.append('Handler Login: login/package symbols mismatch')
    if declared.get('worldChain')!=[50103,0x20,59570,0x18,11326] or declared.get('packageAddress')!='0x14586cf14':
        errors.append('Handler Login: state field source mismatch')
    if report.get('trustInputReplacements')!=['native private-key signature','server public key and signature','root PEM input']:
        errors.append('Handler Login: omitted local trust boundary')
    rows=report.get('results',[])
    if len(rows)!=len(CASES):errors.append('Handler Login: missing cases')
    for row,(name,changes,mode) in zip(rows,CASES):
        inputs=BASE|changes;sent=mode=='send';rejected=mode=='bad_signature'
        declared_inputs=dict(row.get('inputs',{}));declared_inputs['parts']=list(declared_inputs.get('parts',[]))
        if declared_inputs!=inputs|{'parts':list(inputs['parts'])}:errors.append(f'Handler Login: {name} fixture inputs differ')
        version,_,hashed=expected_input(inputs['parts'],inputs['secret'])
        count={'send':9,'delay':6,'missing_manager':0,'missing_gate':4,'medium_package':2,'bad_signature':0}[mode]
        visits={hex(a):1 for a in VISITS[:count]}
        delegated=mode in ('send','delay','missing_gate');clocked=mode in ('send','delay')
        fields={'1':inputs['token'].encode('ascii').hex(),'9':version.encode('ascii').hex(),'1996':hashed.encode('ascii').hex()} if sent else None
        header=((b'\x18'+varint(inputs['sequence']) if inputs['sequence'] else b'')+
                (b'\x30'+varint(inputs['sentMs']) if inputs['sentMs'] else b'')).hex() if sent else None
        target=dict(name=name,mode=mode,stop=hex(STOPS[mode]),stages=[9,6] if sent else [9],visits=visits,
                    delegates=[dict(target='0x1507c0bb0',receiverPreserved=True,methodInfoPreserved=True)] if delegated else [],
                    clockFloatBits=[struct.unpack('<I',struct.pack('<f',inputs['clock']))[0]] if clocked else [],
                    versionSourceCalls=2 if sent else 0 if rejected else 1,requestSent=sent,
                    tokenHashPointersPreserved=sent,requestUnchanged=sent,cmdId=23172 if sent else None,
                    wireFields=fields,headerWire=header,javaByteComparison=not rejected,passed=True)
        if any(row.get(k)!=v for k,v in target.items()):errors.append(f'Handler Login: {name} native flow/wire differs')
        if {str(k):v for k,v in row.get('nativeOperations',{}).items()}!={'101':0,'103':-7 if rejected else 0}:
            errors.append(f'Handler Login: {name} crypto outcome differs')
        if bool(row.get('sessionKeySHA256'))==rejected or [(k.get('operation'),k.get('oracle'),k.get('native')) for k in row.get('keyChecks',[])]!=[(101,True,True),(103,True,True)]:
            errors.append(f'Handler Login: {name} key verification/install differs')
    return errors


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--client',required=True,type=Path);p.add_argument('--java-home',required=True,type=Path)
    p.add_argument('--output',required=True,type=Path);p.add_argument('--summary-output',required=True,type=Path)
    p.add_argument('--libraries',type=Path,default=Path('local/migration72/python'))
    p.add_argument('--signing-key',type=Path,default=Path('src/main/resources/keys/SigningKey.der'));a=p.parse_args()
    for path in (a.output,a.summary_output):
        if path.resolve().is_relative_to(a.client.resolve()):p.error('Output must be outside client')
    b,m,s=[read_verified(a.client/name,sha) for name,sha in HASHES.items()];records,_=extract(b,m,startup=s,structures=True)
    report=recover(b,m,s,records,a.libraries,a.signing_key,a.java_home,Path(__file__).resolve().parents[1])
    errors=validate_summary(report)
    if errors:raise ValueError(errors)
    for path,value in ((a.output,report),(a.summary_output,{k:v for k,v in report.items() if k!='fixtures'})):
        path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(value,indent=2)+'\n','utf8')
    print(json.dumps(dict(fixtures=report['fixtureCount'],sendStop=report['sendStop'],runtimeStatus=report['runtimeStatus'])))


if __name__=='__main__':main()
