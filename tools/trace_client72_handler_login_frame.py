"""Native response Handler/installed key/Login object graph -> complete frame.

The response reader is an explicit preceding invocation. From Handler entry to
the frame's return, CPU state is continuous. No socket or whole Handler return.
"""
import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path
import subprocess

try:
    from .extract_client72 import HASHES,read_verified,extract
    from .client72_emulator import Offline72,GLOBAL,OBJECT
    from .trace_client72_handler_login import (HandlerLogin72,ContinuousConstructor72,BASE,CASES as OLD_CASES,
                                               FRAME,STOPS,declarations)
    from .trace_client72_handler_tail import HandlerTail72,TailReader72,expected_input
    from .trace_client72_response_crypto import discover_private_key,response_wire
    from .client72_rsa_fixture import private_numbers,encrypt_seed,sign_sha256
    from .trace_client72_token import varint,parse_wire
    from .trace_client72_token_frame import TokenFrame72
    from .trace_client72_frame import expected_frame
    from .trace_client72_send_flow import slot_method
    from .trace_client72_session import java_keys
except ImportError:
    from extract_client72 import HASHES,read_verified,extract
    from client72_emulator import Offline72,GLOBAL,OBJECT
    from trace_client72_handler_login import (HandlerLogin72,ContinuousConstructor72,BASE,CASES as OLD_CASES,
                                              FRAME,STOPS,declarations)
    from trace_client72_handler_tail import HandlerTail72,TailReader72,expected_input
    from trace_client72_response_crypto import discover_private_key,response_wire
    from client72_rsa_fixture import private_numbers,encrypt_seed,sign_sha256
    from trace_client72_token import varint,parse_wire
    from trace_client72_token_frame import TokenFrame72
    from trace_client72_frame import expected_frame
    from trace_client72_send_flow import slot_method
    from trace_client72_session import java_keys

CASES=OLD_CASES[:3]+(('unicode-key-wrap',dict(token='令牌🚀\0'+'L'*4200,
       sequence=0xffffffff,sentMs=0x200000044),'send'),)+OLD_CASES[3:]
HELPERS=(0x147CF1A40,0x151767530,0x151727A20,0x15171A020,0x151719B20,
         0x151765C70,0x147CF1DC0,0x1517663F0,0x14A721B00)


class LoginFrame72(TokenFrame72):
    def __init__(self,e):
        self.native_byte_class=0
        super().__init__(e,initial_key=None,session_key=None)
        # Keep the native byte copy helper throughout the crypto/Handler flow.
        e.models.pop(0x1404D7630)
        self.bound_types=[]

    def array(self,data):
        pointer=super().array(data)
        if self.native_byte_class:self.e.write(pointer,self.native_byte_class)
        return pointer

    def attach_handler(self,runtime):
        e=self.e; self.ctor=ctor=runtime.ctor; self.native_byte_class=runtime.array_class
        self.attach_flow(ctor,runtime.flow,76410)
        for pointer,index in ctor.allocations.items():
            for slot in (13,17):
                method=next(m for m in e.records[index]['methods'] if m['virtualSlot']==slot)
                address=int(method['registeredAddress'],16); slot_method(e.records[index],slot,address)
                e.write(e.read(pointer)+0xd0+slot*8,address); e.allow(address)
            self.bound_types.append(index)
        for address in HELPERS:e.allow(address)
        for address in (0x1517662A0,0x151766960,0x151765E10,0x151727A20):e.models.pop(address,None)
        for pointer in self.arrays|set(ctor.array_values):e.write(pointer,self.native_byte_class)
        if (e.read(self.native_byte_class+0xb8,'H'),e.read(self.native_byte_class+0xc8,'B'))!=(1,2):
            raise ValueError('Native byte-copy class layout changed')
        if 0x1404D7630 in e.models:raise ValueError('Native copy was replaced')


class HandlerLoginFrame72(HandlerLogin72):
    def __init__(self,*args,framer):
        super().__init__(*args)
        self.framer=framer; self.frame_return=None; self.key_pointer=None; self.frame_visits=Counter()

    def observe_response(self,address):
        e=self.e
        if address==FRAME:
            # Continue the original call rather than inheriting the prefix stop.
            HandlerTail72.observe_response(self,address)
            self.connection_visits[address]+=1
            if self.frame_return is not None or e.read(e.reg('rcx')+0x10)!=self.ctor.request:
                raise ValueError('Unexpected Login frame receiver or duplicate call')
            self.frame_return=e.read(e.reg('rsp'))
        else:super().observe_response(address)
        if address==0x151214D20:
            self.key_pointer=e.read(GLOBAL+0x19580)
            if not self.key_pointer:raise ValueError('Login send lacks installed response key')
            self.framer.attach_handler(self)
        if self.frame_return is not None:self.frame_visits[address]+=1
        if self.frame_return is not None and address==self.frame_return:
            self.stop=address; e.uc.emu_stop()


def object_proto_text(e,ctor,schemas,index,pointer):
    """Read constructor outputs for an independent protoc input, not native wire."""
    parts=[]
    for field in schemas[index]['fields']:
        address=pointer+field['objectOffset']; kind=field['type']; name=field['clientField']
        if field['cardinality']!='singular' or field.get('oneof'):
            if e.read(address):raise ValueError('Nonempty container/oneof outside this constructor experiment')
            continue
        if kind=='message':
            child=e.read(address)
            if child:parts.append(name+' { '+object_proto_text(e,ctor,schemas,field['nestedTypeIndex'],child)+' }')
            continue
        if kind in ('string','bytes'):
            value=e.read(address)
            if kind=='string':raw=ctor.text(value).encode('utf8')
            elif value:
                array=e.read(value+24);raw=bytes(e.uc.mem_read(array+32,e.read(array+24)))
            else:raw=b''
            text='"'+''.join('\\%03o'%v for v in raw)+'"'
        elif kind=='bool':text='true' if e.read(address,'B') else 'false'
        elif kind in ('uint32','int32','uint64'):
            text=str(e.read(address,dict(uint32='I',int32='i',uint64='Q')[kind]))
        else:raise ValueError('Unexpected constructor field wire type')
        parts.append(name+': '+text)
    return ' '.join(parts)


def recover(binary,metadata,startup,records,libraries,signing_key,java_home,protoc,root):
    declared=declarations(binary,metadata,startup,records)
    observations=json.loads((root/'protocol/cn-7.2/login-wire-observations.json').read_text('utf8'))
    if observations['inputSHA256']!=HASHES:raise ValueError('Login wire input changed')
    schemas={r['typeIndex']:r for r in observations['messages']}
    proto=root/'protocol/cn-7.2/login-research.proto'
    private,_,_=discover_private_key(binary,metadata,records,libraries);client=private_numbers(private)
    signer=private_numbers(signing_key.read_bytes());plain=bytes.fromhex('fedcba9876543210');seed=0x0123456789abcdef
    keys,java_sources=java_keys(java_home,[int.from_bytes(plain,'big')^seed],root)
    fixtures=[];results=[]
    for ordinal,(name,changes,mode) in enumerate(CASES):
        inputs=BASE|changes; signature=sign_sha256(signer,plain)
        if mode=='bad_signature':signature=bytes([signature[0]^1])+signature[1:]
        token=inputs['token'].encode('utf8');secret=inputs['secret'].encode('utf8');blob=b'robot\0\xff'
        wire=response_wire(encrypt_seed(client,plain),signature)+b'\x48'+varint(inputs['uid'])+b'\x32'+varint(len(token))+token
        wire+=b'\x1a'+varint(len(blob))+blob+b'\x92\x29'+varint(len(secret))+secret+b'\x08'+varint(inputs['rawFlag'])
        e=Offline72(binary,libraries,records,True);reader=None;runtime=None
        try:
            e.prepare();framer=LoginFrame72(e);reader=TailReader72(e);read=reader.run(37481,wire)
            ctor=ContinuousConstructor72(e,binary,metadata,ordinal,'123','456');ctor.close()
            e.models.pop(0x1531D02B0);e.models.pop(0x159A8D050)
            source=e.read(e.read(e.read(GLOBAL+0x9e80)+0x180)+0x50)
            runtime=HandlerLoginFrame72(e,binary,metadata,seed,signer,ctor,source,inputs,mode,framer=framer)
            trace,key=runtime.run_handler();sent=mode=='send';rejected=mode=='bad_signature'
            expected_stop=runtime.frame_return if sent else STOPS[mode]
            if runtime.stop!=expected_stop or key!=(None if rejected else keys[0]):
                raise ValueError('Handler stop or independently computed session key differs')
            if runtime.stages!=([9,6] if sent else [9]):raise ValueError('Handler/Login stages changed')
            frame_result=None;cipher=None;proto_text=None
            if sent:
                if e.reg('al')!=1 or e.read(0x145878FC0,'B')!=0:raise ValueError('Login frame failed or selected another key')
                if e.read(GLOBAL+0x19580)!=runtime.key_pointer:raise ValueError('Frame replaced native installed key')
                if runtime.request_before!=bytes(e.uc.mem_read(ctor.request,0x160)):
                    raise ValueError('Frame mutated original Login request')
                if (e.read(ctor.request+0xd8),e.read(ctor.request+0x60))!=(e.read(OBJECT+0x68),e.read(GLOBAL+0x11b90)):
                    raise ValueError('Login token/hash pointers changed')
                length=e.read(runtime.flow.packet+0x30,'I')
                cipher=bytes(e.uc.mem_read(framer.buffer+32,length));raw=bytes(v^key[i%len(key)] for i,v in enumerate(cipher))
                header_length=int.from_bytes(raw[4:6],'big');body=raw[10+header_length:-2]
                proto_text=object_proto_text(e,ctor,schemas,76410,ctor.request)
                expected=subprocess.run([str(protoc),'--proto_path='+str(proto.parent),
                    '--encode=cn72.research.'+schemas[76410]['clientType'],str(proto)],input=proto_text.encode('ascii'),
                    capture_output=True,check=True).stdout
                if body!=expected:raise ValueError('Native complete payload differs from independent protoc object input')
                if cipher!=expected_frame(23172,expected,inputs['sequence'],inputs['sentMs'],key):
                    raise ValueError('Independent Login complete frame expression differs')
                fields={t:(k,v) for t,k,v in parse_wire(body)}
                version,encoded,hashed=expected_input(inputs['parts'],inputs['secret'])
                for tag,value in ((1,token),(9,version.encode('utf8')),(1996,hashed.encode('ascii'))):
                    if fields.get(tag,(2,b''))!=(2,value):raise ValueError('Response/version/hash source lost in Login frame')
                for address in (FRAME,0x152EF0D50,0x152EEB470,0x15171A020,0x151719B20,
                                0x151765C70,0x151767530,0x147CF1DC0,0x1517663F0,0x1404D7630):
                    if not runtime.frame_visits[address]:raise ValueError('Native graph/byte/copy stage absent')
                if (length>4096)!=(name=='unicode-key-wrap'):raise ValueError('Login key-cycle fixture boundary changed')
                frame_result=dict(length=length,payloadLength=len(body),sha256=hashlib.sha256(cipher).hexdigest(),
                    payloadSHA256=hashlib.sha256(body).hexdigest(),sequence=inputs['sequence'],sentMs=inputs['sentMs'],
                    cmdId=23172,selector=0,keyWrap=length>4096,keyPointerPreserved=True,keyBytesPreserved=True,
                    requestUnchanged=True,tokenHashPointersPreserved=True,protocPayloadMatch=True,independentFrameMatch=True,
                    tokenUTF8Match=True,tokenByteLength=len(token),boundNativeTypes=sorted(set(framer.bound_types)))
            elif ctor.request is not None or runtime.flow is not None or runtime.frame_visits:
                raise ValueError('Rejected/delayed Handler serialized Login')
            row=dict(name=name,mode=mode,stop=hex(runtime.stop),stages=runtime.stages,frameReturned=sent,
                     frame=frame_result,sessionKeySHA256=hashlib.sha256(key).hexdigest() if key else None,
                     javaByteComparison=not rejected,nativeKeyChecks=runtime.key_checks,passed=True)
            results.append(row);fixtures.append(dict(**row,responseReader=read,handlerTrace=trace,
                frameVisits={hex(a):n for a,n in sorted(runtime.frame_visits.items())},
                frameHex=cipher.hex() if cipher else None,independentProtocInput=proto_text))
            print(json.dumps(dict(case=name,frameReturned=sent,length=frame_result['length'] if sent else None)),flush=True)
        finally:
            if runtime:runtime.close()
            if reader:reader.close()
            e.close()
    return dict(schemaVersion=1,inputSHA256=HASHES,fixtureCount=len(fixtures),results=results,fixtures=fixtures,
                runtimeStatus='not-tested',entry='0x1507be0e0',frameEntry=hex(FRAME),cmdId=23172,
                continuousHandlerToFrameReturn=True,wholeHandlerReturned=False,frameKeySupplied=False,
                declarations=declared,javaSourceSHA256=java_sources,
                protocSHA256=hashlib.sha256(protoc.read_bytes()).hexdigest(),researchProtoSHA256=hashlib.sha256(proto.read_bytes()).hexdigest(),
                signingFixtureSHA256=hashlib.sha256(signing_key.read_bytes()).hexdigest(),
                models=['response reader inputs, local signed trust and OS/CRT storage',
                        'WorldType.Login/PackageType.Full and clock/gates from handler-login',
                        'old Shell/SDK and version source fixtures', 'pooled byte arrays/stream and UTF-8 conversion',
                        'declared message vtables and byte[] class layout; native copy executes'],
                outsideScope=['socket/real-client login','whole Handler return','nonempty list/map/oneof constructor inputs',
                              'real SDK/IFix startup and remaining semantic fields'])


def validate_summary(report):
    errors=[]
    expected=dict(inputSHA256=HASHES,fixtureCount=10,runtimeStatus='not-tested',entry='0x1507be0e0',
        frameEntry=hex(FRAME),cmdId=23172,continuousHandlerToFrameReturn=True,wholeHandlerReturned=False,frameKeySupplied=False)
    if any(report.get(k)!=v for k,v in expected.items()):errors.append('Handler/Login frame scope mismatch')
    if not report.get('models') or not report.get('javaSourceSHA256') or not report.get('protocSHA256'):
        errors.append('Handler/Login frame independent evidence missing')
    rows=report.get('results',[])
    if [(r.get('name'),r.get('mode')) for r in rows]!=[(name,mode) for name,_,mode in CASES]:
        errors.append('Handler/Login frame cases mismatch')
    for row,(name,changes,mode) in zip(rows,CASES):
        sent=mode=='send';rejected=mode=='bad_signature';frame=row.get('frame');inputs=BASE|changes
        if (row.get('passed') is not True or row.get('frameReturned')!=sent or row.get('javaByteComparison')!=(not rejected)
            or row.get('stages')!=([9,6] if sent else [9])):errors.append('Handler/Login frame route mismatch')
        if row.get('sessionKeySHA256')!=(None if rejected else '5d517d6c52800a32b573b136c7a469131670b869881157c2fa013f714b796df5'):
            errors.append('Handler/Login frame installed key mismatch')
        if [(v.get('operation'),v.get('oracle'),v.get('native')) for v in row.get('nativeKeyChecks',[])]!=[(101,True,True),(103,True,True)]:
            errors.append('Handler/Login frame native key verification mismatch')
        if sent:
            if not isinstance(frame,dict):errors.append('Login frame missing');continue
            for key in ('keyPointerPreserved','keyBytesPreserved','requestUnchanged','tokenHashPointersPreserved',
                        'protocPayloadMatch','independentFrameMatch','tokenUTF8Match'):
                if frame.get(key) is not True:errors.append('Login frame identity or independent comparison missing')
            if (frame.get('cmdId')!=23172 or frame.get('selector')!=0 or frame.get('keyWrap')!=(name=='unicode-key-wrap')
                or frame.get('sequence')!=inputs['sequence'] or frame.get('sentMs')!=inputs['sentMs']
                or frame.get('tokenByteLength')!=len(inputs['token'].encode('utf8'))
                or frame.get('boundNativeTypes')!=[68918,76410] or row.get('stop')!='0x1472cd364'):
                errors.append('Login frame boundary mismatch')
            header_size=sum(1+len(varint(v)) for v in (inputs['sequence'],inputs['sentMs']) if v)
            payload_length=frame.get('payloadLength')
            if not isinstance(payload_length,int) or payload_length<=0 or frame.get('length')!=payload_length+12+header_size:
                errors.append('Login frame lengths mismatch')
        elif frame is not None or row.get('stop')!=hex(STOPS[mode]):errors.append('Rejected Handler produced a frame')
    return errors


def main():
    p=argparse.ArgumentParser(description=__doc__)
    for name in ('client','java-home','protoc','output','summary-output'):p.add_argument('--'+name,required=True,type=Path)
    p.add_argument('--libraries',type=Path,default=Path('local/migration72/python'))
    p.add_argument('--signing-key',type=Path,default=Path('src/main/resources/keys/SigningKey.der'));a=p.parse_args()
    for path in (a.output,a.summary_output):
        if path.resolve().is_relative_to(a.client.resolve()):p.error('Output must be outside client')
    b,m,s=[read_verified(a.client/name,sha) for name,sha in HASHES.items()];records,_=extract(b,m,startup=s,structures=True)
    report=recover(b,m,s,records,a.libraries,a.signing_key,a.java_home,a.protoc,Path(__file__).resolve().parents[1])
    errors=validate_summary(report)
    if errors:raise ValueError(errors)
    for path,value in ((a.output,report),(a.summary_output,{k:v for k,v in report.items() if k!='fixtures'})):
        path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(value,indent=2)+'\n','utf8')
    print(json.dumps(dict(fixtures=report['fixtureCount'],runtimeStatus=report['runtimeStatus'])))


if __name__=='__main__':main()
