"""Execute Token Handler entry through warmup, crypto, UID and token stores.

Stops before subsequent robot-check/version/UI actions. PlayerPrefs storage,
logging and default UInt32 formatting are explicit offline boundaries.
"""
import argparse
import hashlib
import json
from pathlib import Path
try:
    from .extract_client72 import HASHES,read_verified,extract,PE,HEADER_VA,BODY,number,TypeResolver
    from .extract_client72_signatures import parameter_start,parameter_words
    from .client72_emulator import Offline72,OBJECT,GLOBAL,STACK
    from .client72_response_crypto import CryptoResponseReader72,ResponseCrypto72,SUCCESS
    from .client72_rsa_fixture import private_numbers,encrypt_seed,sign_sha256
    from .trace_client72_response_crypto import response_wire,discover_private_key
    from .trace_client72_session import java_keys
    from .trace_client72_token import varint
except ImportError:
    from extract_client72 import HASHES,read_verified,extract,PE,HEADER_VA,BODY,number,TypeResolver
    from extract_client72_signatures import parameter_start,parameter_words
    from client72_emulator import Offline72,OBJECT,GLOBAL,STACK
    from client72_response_crypto import CryptoResponseReader72,ResponseCrypto72,SUCCESS
    from client72_rsa_fixture import private_numbers,encrypt_seed,sign_sha256
    from trace_client72_response_crypto import response_wire,discover_private_key
    from trace_client72_session import java_keys
    from trace_client72_token import varint

STOP=0x15A4E0D9A


class HandlerReader72(CryptoResponseReader72):
    def blob(self,kind):
        if kind=='string':return super().blob(kind)
        self.sync();data=self.input.blob();self.position();e=self.e
        array=e.alloc(32+len(data));e.write(array+24,len(data))
        if data:e.uc.mem_write(array+32,data)
        p=e.alloc(64);e.write(p,e.read(0x145898960));e.write(p+0x14,1,'I');e.write(p+0x18,array)
        self.values[p]=dict(kind=kind,hex=data.hex());return p


class HandlerCore72(ResponseCrypto72):
    def __init__(self,e,binary,metadata,client_seed,signer,old):
        super().__init__(e,binary,metadata,client_seed,signer)
        self.account=e.alloc(1024);e.write(self.manager+0x180,self.account);e.write(GLOBAL+0x9e80,self.manager)
        for cell in (0x1458AD418,0x1458971C0,0x1458AE590,0x145890290):e.synthetic_type(cell)
        for a in (0x1507BE0E0,0x147CF3070,0x14D581600,0x15217AA90,0x14A9CF370,0x153D333E0,
                  0x153D34300,0x150A7FB80,0x150AA36A0,0x1470ECEE0,0x1470ED300,0x1470EC820):e.allow(a)
        for cell in (0x145B8E2B6,0x145B97845):e.write(cell,1,'B')  # supplied initialized metadata caches
        e.write(0x14586EA48,1,'B');e.write(0x14586EA49,0,'B')  # reviewed cached non-cloud input
        auxiliary=e.alloc(256);self.cache=e.alloc(128);e.write(auxiliary+0x20,self.cache);e.write(GLOBAL+0xa070,auxiliary)
        flags=e.alloc();array=e.alloc(40);e.write(flags+16,array);e.write(flags+24,int(old),'I')
        e.write(array+24,int(old));e.write(array+32,5714,'I');e.write(GLOBAL+0x6100,flags)
        self.provider=e.alloc();self.stages=[];self.logs=[];self.prefs=[];self.saves=0;self.warmup=[]
        e.models.update({0x14C8AE9A0:self.stage,0x147161B60:self.log,0x159AD6130:lambda:self.provider,
                         0x159C1ABD0:self.uint_text,0x141454050:self.prefs_set,0x140973BC0:self.prefs_save})
        self.session.call(0x153D333E0)  # target literal -> static __LastUid__ key

    def text(self,p):return bytes(self.e.uc.mem_read(p+20,self.e.read(p+16,'I')*2)).decode('utf-16-le')

    def stage(self):self.stages.append(self.e.reg('ecx'));return 0

    def log(self):self.logs.append(self.text(self.e.reg('rdx')));return 0

    def uint_text(self):
        e=self.e
        if e.reg('rcx')!=0 or e.reg('r8')!=self.provider:raise ValueError('Unreviewed numeric formatting mode')
        text=str(e.reg('edx'));p=e.alloc(22+len(text)*2);e.write(p+16,len(text),'I')
        e.uc.mem_write(p+20,text.encode('utf-16-le'));return p

    def prefs_set(self):self.prefs.append([self.text(self.e.reg('rcx')),self.text(self.e.reg('rdx'))]);return 1

    def prefs_save(self):self.saves+=1;return 0

    def observe_response(self,address):
        e=self.e
        if address==0x15A4DE0FF:self.frame=e.reg('rbp')
        if address==0x15A4DE702:
            p=e.reg('rdx');self.warmup.append(dict(operation=e.reg('ecx'),size=e.reg('r8d'),length=e.read(p+8,'I')))
        if address==0x15A4DE707 and self.warmup:self.warmup[-1]['return']=e.reg('eax')-(1<<32) if e.reg('eax')>>31 else e.reg('eax')
        if address==SUCCESS:
            # Continue beyond the narrower crypto experiment's stop.
            self.path_digest.update(address.to_bytes(8,'little'));self.visits[address]+=1
        else:super().observe_response(address)
        if address==STOP:self.stop=address;e.uc.emu_stop()

    def run_handler(self):
        return self.run_native(0x1507BE0E0,dict(rsp=STACK+0x100008,rcx=self.manager,rdx=OBJECT))


def declarations(binary,metadata,startup,records):
    by_index={r['index']:r for r in records};pe=PE(binary);h=pe.offset(HEADER_VA)
    for owner,name,address in ((11419,'LogToServer',0x147161B60),(692,'get_CurrentInfo',0x159AD6130),
                               (2346,'TrySetSetString',0x15A23D8E0),(2346,'Save',0x15A23DAE0)):
        rows=[m for m in by_index[owner]['methods'] if m['registeredAddress']==hex(address)]
        if len(rows)!=1 or rows[0]['name']!=name:raise ValueError('Declared Handler boundary changed')
    for wrapper,target in ((0x15A23D8E0,0x141454050),(0x15A23DAE0,0x140973BC0)):
        raw=binary[pe.offset(wrapper):pe.offset(wrapper)+5]
        if raw[0]!=0xe9 or wrapper+5+int.from_bytes(raw[1:],'little',signed=True)!=target:
            raise ValueError('PlayerPrefs native boundary differs from named wrapper')
    method=next(m for m in by_index[369]['methods'] if m['registeredAddress']=='0x159c1abd0')
    method_base=BODY+(number(binary,h+0xb0)^0x51324a1f)
    param_base=BODY+((number(binary,h+0x174)+0xae6bcd68)&0xffffffff)
    first=parameter_start(method['index'],number(metadata,method_base+method['index']*26+4))
    resolver=TypeResolver(pe,startup,records,h)
    params=[resolver.resolve_index(parameter_words(i,number(metadata,param_base+i*8),number(metadata,param_base+i*8+4))[1]) for i in range(first,first+3)]
    if method['name']!='NumberToString' or [p['kind'] for p in params]!=['string','uint32','class'] or params[2].get('typeDefinition')!=247:
        raise ValueError('UID formatter is not UInt32/IFormatProvider overload')
    return dict(formatterMethodIndex=method['index'],formatKind='uint32',prefsKeyLiteral=57480,prefsKey='__LastUid__')


def recover(binary,metadata,startup,records,libraries,signer,java_home,root):
    declared=declarations(binary,metadata,startup,records)
    private_der,_,_=discover_private_key(binary,metadata,records,libraries);client=private_numbers(private_der)
    plain=bytes.fromhex('fedcba9876543210');client_seed=0x0123456789abcdef
    expected,java_sources=java_keys(java_home,[int.from_bytes(plain,'big')^client_seed],root)
    cipher=encrypt_seed(client,plain);signature=sign_sha256(signer,plain);fixtures=[]
    for old,uid,token,blob in ((True,1,b'handler_token_A',b''),(False,0xffffffff,b'handler_token_B',b''),
                               (False,0x80000000,b'',b'\x01\x02\x03\x04\x05\x06\x07\x08'),(True,0,b'',b'\0'*8)):
        wire=response_wire(cipher,signature)+b'\x48'+varint(uid)+b'\x32'+varint(len(token))+token+b'\xb2\x34'+varint(len(blob))+blob
        e=Offline72(binary,libraries,records,True);reader=None;runtime=None
        try:
            reader=HandlerReader72(e);read=reader.run(37481,wire)
            runtime=HandlerCore72(e,binary,metadata,client_seed,signer,old)
            trace,key=runtime.run_handler()
            if runtime.stop!=STOP or key!=expected[0] or runtime.stages!=[9]:raise ValueError('Handler core did not reach token store with correct key/stage')
            if e.read(runtime.account+0x168,'I')!=uid or runtime.prefs!=[['__LastUid__',str(uid)]] or runtime.saves!=1:
                raise ValueError('Native UID store/PlayerPrefs path disagrees with wire')
            if e.read(runtime.account+0x98)!=e.read(OBJECT+0x68) or runtime.text(e.read(runtime.account+0x98)).encode('ascii')!=token:
                raise ValueError('Handler lost the exact response token pointer/value')
            warmup=[] if old else [dict(operation=202,size=16,length=len(blob),return_=-1 if blob else -2)]
            actual=[dict(operation=w['operation'],size=w['size'],length=w['length'],return_=w['return']) for w in runtime.warmup]
            if actual!=warmup:raise ValueError(f'Observed warmup branch/result changed: old={old}, uid={uid}, actual={actual}')
            if runtime.logs!=['[Shell] Old WarmupSwitch' if old else '[Shell] New WarmupSwitch'] or e.read(runtime.cache+0x40,'I')!=1:
                raise ValueError('Handler warmup log/native empty-cache reset differs')
            fixtures.append(dict(oldWarmup=old,uid=uid,tokenHex=token.hex(),warmup=runtime.warmup,reader=read,trace=trace,
                                 keySHA256=hashlib.sha256(key).hexdigest(),uidSaved=True,tokenPointerPreserved=True,prefs=runtime.prefs,passed=True))
        finally:
            if runtime:runtime.close()
            if reader:reader.close()
            e.close()
    return dict(schemaVersion=1,inputSHA256=HASHES,fixtureCount=len(fixtures),fixtures=fixtures,declarations=declared,
                entry='0x1507be0e0',stop=hex(STOP),runtimeStatus='not-tested',wholeHandlerExecuted=False,
                fieldBinding=dict(typeIndex=37481,clientField='PJAJDHBBMGB',objectOffset=0xd4,tag=9,type='uint32',
                                  semanticField='uid',status='verified',sessionOffset=0x168,prefsKey='__LastUid__'),
                results=[{k:v for k,v in f.items() if k not in ('reader','trace')} for f in fixtures],javaSourceSHA256=java_sources,
                modelsFrom=['response-crypto-observations.json'],
                addedModels=['LogToServer/stage capture','default UInt32 formatting with declared provider',
                             'in-memory PlayerPrefs set/save','initialized metadata caches, empty cache container, non-cloud environment',
                             'explicit runtime flag-list selects old/new warmup branch'],
                outsideScope=['actual PlayerPrefs persistence','successful new warmup/real SDK initialization',
                              'post-token robot-check/version/UI actions','whole Handler/live client'])


def main():
    a=parse_args();root=Path(__file__).resolve().parents[1]
    b,m,s=[read_verified(a.client/name,sha) for name,sha in HASHES.items()]
    records,_=extract(b,m,startup=s,structures=True);key=a.signing_key.read_bytes()
    report=recover(b,m,s,records,a.libraries,private_numbers(key),a.java_home,root)
    report['signingFixtureFileSHA256']=hashlib.sha256(key).hexdigest()
    for path,value in ((a.output,report),(a.summary_output,{k:v for k,v in report.items() if k!='fixtures'})):
        path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(value,indent=2)+'\n','utf8')
    print(json.dumps(dict(fixtures=report['fixtureCount'],stop=report['stop'])))


def parse_args():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--client',required=True,type=Path);p.add_argument('--java-home',required=True,type=Path)
    p.add_argument('--output',required=True,type=Path);p.add_argument('--summary-output',required=True,type=Path)
    p.add_argument('--libraries',type=Path,default=Path('local/migration72/python'))
    p.add_argument('--signing-key',type=Path,default=Path('src/main/resources/keys/SigningKey.der'));a=p.parse_args()
    for path in (a.output,a.summary_output):
        if path.resolve().is_relative_to(a.client.resolve()):p.error('Output must be outside client')
    return a


if __name__=='__main__':main()
