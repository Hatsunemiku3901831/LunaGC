"""Fixed CN72 SDK crypto segments; synthetic inputs remain only in memory."""
from __future__ import annotations

import argparse
import base64
from collections import Counter
import hashlib
import json
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
sys.path[:0] = [str(ROOT/'tools'), str(ROOT/'local/migration72/python')]
from client72_sdk_transport import Image, digest, MODULE_HASHES, PROFILE_SHA
from client72_sdk_login_chain import Fixture, rtti_tables
from client72_seed_crypto import import_slots, rsa_public_numbers
from client72_rsa_fixture import tlv, private_numbers, public_der, der_item
from extract_client72 import PE
from unicorn import x86_const as ux

INPUT_MANIFEST_SHA='7c574fcd6638b70434a249a587708ad69b035662b357359f09bc617dc3d5a958'
RAW=ROOT/'local/migration72/d02-active/sdk-crypto-contract-astra'


class CryptoFixture(Fixture):
    """Storage and named single-thread Windows state, never crypto success models."""
    IMPORT_BASE = 0x800000000

    def __init__(self, pe):
        super().__init__(pe)
        self.uc.mem_map(self.IMPORT_BASE, 0x10000)
        self.imports = {}
        for index,(name,slot) in enumerate(import_slots(PE(pe.data)).items()):
            target=self.IMPORT_BASE+index*16
            self.put(slot,target)
            self.imports[target]=name
        self.tls = {}
        self.next_tls = 1
        self.last_error = 0
        self.entropy_counter = 0
        self.performance_counter = 0x123456789
        self.observed = []
        self.pending_native = []
        self.padding_blocks = []
        self.rsa_results = []
        self.exit_callbacks = []
        self.cut[0x48274c] = self.register_exit_callback

    def register_exit_callback(self,args):
        self.exit_callbacks.append(args[0])
        self.events.append({'model':'CRT-process-exit-registration','rva':'0x48274c',
                            'callbackRva':hex(args[0]-self.pe.base)})
        return 0

    def call(self,rva,*args):
        self.trace=[]
        self.uc.mem_write(self.STACK-0x10000,bytes(0x11000))
        self.put(self.STACK,self.STOP)
        self.uc.reg_write(ux.UC_X86_REG_RSP,self.STACK)
        for reg,value in zip([ux.UC_X86_REG_RCX,ux.UC_X86_REG_RDX,ux.UC_X86_REG_R8,ux.UC_X86_REG_R9],args):
            self.uc.reg_write(reg,value)
        for index,value in enumerate(args[4:]):
            self.put(self.STACK+0x28+index*8,value)
        self.uc.emu_start(self.pe.base+rva,self.STOP,count=5000000)
        if self.uc.reg_read(ux.UC_X86_REG_RIP)!=self.STOP:
            raise ValueError('Native instruction budget exhausted')
        if self.uc.reg_read(ux.UC_X86_REG_RSP)!=self.STACK+8:
            raise ValueError('Native stack imbalance')
        return self.uc.reg_read(ux.UC_X86_REG_RAX)

    def on_code(self,uc,address,size,context):
        rva=address-self.pe.base
        stack=uc.reg_read(ux.UC_X86_REG_RSP)
        if self.pending_native and self.pending_native[-1]['return']==address and self.pending_native[-1]['stack']+8==stack:
            call=self.pending_native.pop()
            result=uc.reg_read(ux.UC_X86_REG_RAX)&0xffffffff
            row={'rva':hex(call['rva']),'returnZero':uc.reg_read(ux.UC_X86_REG_RAX)==0}
            if call['rva'] in [0x4ed800,0x4c9dd0]:
                row['returnSigned32']=result if result<0x80000000 else result-0x100000000
            else:
                row['nonzero64Pointer']=uc.reg_read(ux.UC_X86_REG_RAX)>0xffffffff
            self.observed.append(row)
            if call['rva']==0x4ed800 and result==1:
                self.padding_blocks.append(bytes(uc.mem_read(call['buffer'],call['size'])))
            if call['rva']==0x4c9dd0 and 0<result<4096:
                self.rsa_results.append(bytes(uc.mem_read(call['buffer'],result)))
        if rva in [0x4ba050,0x4ba010,0x4c9dd0,0x4ed800]:
            rcx=uc.reg_read(ux.UC_X86_REG_RCX);rdx=uc.reg_read(ux.UC_X86_REG_RDX)
            row={'rva':rva,'return':self.get(stack),'stack':stack}
            if rva==0x4ed800:
                row.update(buffer=rcx,size=rdx&0xffffffff)
            elif rva==0x4c9dd0:
                row.update(buffer=uc.reg_read(ux.UC_X86_REG_R8))
            self.pending_native.append(row)
        name=self.imports.get(address) if hasattr(self,'imports') else None
        if name is None:
            return super().on_code(uc,address,size,context)
        args=[uc.reg_read(r) for r in [ux.UC_X86_REG_RCX,ux.UC_X86_REG_RDX,ux.UC_X86_REG_R8,ux.UC_X86_REG_R9]]
        if name=='TlsAlloc':
            result=self.next_tls;self.next_tls+=1
        elif name=='TlsGetValue':
            result=self.tls.get(args[0],0)
        elif name=='TlsSetValue':
            self.tls[args[0]]=args[1];result=1
        elif name=='TlsFree':
            self.tls.pop(args[0],None);result=1
        elif name in ['InitializeCriticalSection','DeleteCriticalSection','EnterCriticalSection','LeaveCriticalSection']:
            result=0
        elif name in ['InitializeCriticalSectionAndSpinCount','InitializeCriticalSectionEx','TryEnterCriticalSection']:
            result=1
        elif name=='GetCurrentThreadId':
            result=0x1234
        elif name=='GetCurrentProcessId':
            result=0x5678
        elif name=='GetSystemTimeAsFileTime':
            self.put(args[0],0x1da000001234567);result=0
        elif name=='QueryPerformanceCounter':
            self.put(args[0],self.performance_counter);self.performance_counter+=1000;result=1
        elif name=='GetLastError':
            result=self.last_error
        elif name=='SetLastError':
            self.last_error=args[0]&0xffffffff;result=0
        elif name=='HeapSize':
            result=self.allocations[args[2]]
        elif name=='HeapAlloc':
            result=self.allocate(args[2])
        elif name=='HeapFree':
            del self.allocations[args[2]];result=1
        elif name=='HeapReAlloc':
            old,size_new=args[2:]
            size_old=self.allocations[old]
            result=self.allocate(size_new)
            uc.mem_write(result,bytes(uc.mem_read(old,min(size_old,size_new))))
            del self.allocations[old]
        elif name=='GetProcessHeap':
            result=0x900000000
        elif name=='GetModuleHandleExW':
            self.put(args[2],self.pe.base);result=1
        elif name=='BCryptGenRandom':
            count=args[2]&0xffffffff
            block=bytes((self.entropy_counter+i)%251+1 for i in range(count))
            self.entropy_counter+=count
            uc.mem_write(args[1],block);result=0
        else:
            raise ValueError('Unmodeled native Windows boundary: '+name)
        kind='storage-Windows-heap' if name.startswith('Heap') or name=='GetProcessHeap' else 'single-thread-Windows-state'
        if name=='BCryptGenRandom':
            kind='synthetic-OS-entropy'
        self.events.append({'model':kind,'api':name})
        stack=uc.reg_read(ux.UC_X86_REG_RSP)
        uc.reg_write(ux.UC_X86_REG_RAX,result)
        uc.reg_write(ux.UC_X86_REG_RIP,self.get(stack))
        uc.reg_write(ux.UC_X86_REG_RSP,stack+8)


def public_numbers(pem):
    lines=pem.splitlines()
    if len(lines)<3 or lines[0]!=b'-----BEGIN PUBLIC KEY-----' or lines[-1]!=b'-----END PUBLIC KEY-----':
        raise ValueError('Expected native SPKI public PEM')
    der=base64.b64decode(b''.join(lines[1:-1]),validate=True)
    tag,body,end=tlv(der)
    if tag!=0x30 or end!=len(der):
        raise ValueError('Expected complete SPKI sequence')
    tag,algorithm,pos=tlv(body)
    if tag!=0x30 or algorithm!=bytes.fromhex('06092a864886f70d0101010500'):
        raise ValueError('Unexpected SPKI algorithm')
    tag,bits,end=tlv(body,pos)
    if tag!=3 or bits[0]!=0 or end!=len(body):
        raise ValueError('Unexpected SPKI bit string')
    return rsa_public_numbers(bits[1:])


def model_summary(events):
    counts=Counter((r['model'],r.get('rva',r.get('api'))) for r in events)
    return [{'model':model,'boundary':boundary,'calls':count}
            for (model,boundary),count in sorted(counts.items())]


def spki_pem(numbers):
    algorithm=bytes.fromhex('300d06092a864886f70d0101010500')
    der=der_item(0x30,algorithm+der_item(3,b'\0'+public_der(numbers)))
    return b'-----BEGIN PUBLIC KEY-----\n'+base64.b64encode(der)+b'\n-----END PUBLIC KEY-----'


def key_fixture(pe,local_keys):
    env=CryptoFixture(pe);obj=env.OBJECT;out=obj+0x1000
    env.put(obj,pe.base+0x6d1cb8)
    xml=ET.parse(ROOT/'patch/sdk_public_key.xml').getroot()
    patch_key=tuple(int.from_bytes(base64.b64decode(xml.findtext(tag)),'big') for tag in ['Modulus','Exponent'])
    rows=[];keys={}
    for mode in [0,1,2,0x100000001]:
        result=env.call(0x3951c0,obj,out,mode)
        pem=env.string(out);numbers=public_numbers(pem);keys[mode]=pem
        row={'mode64':mode,'returnedOutputPointer64':result==out and result>0xffffffff,
             'pemBytes':len(pem),'format':'SPKI-PEM','modulusBits':numbers[0].bit_length(),
             'publicExponent':numbers[1],'nativeSteps':len(env.trace),
             'localPublicEqual':{name:tuple(values[1:3])==numbers for name,values in local_keys.items()},
             'patchXMLPublicEqual':numbers==patch_key}
        env.call(0x1380e0,out)
        row['ownedGetterCopyReleased']=not env.allocations
        rows.append(row)
    return rows,keys


def encrypt_fixture(pe,keys,local_keys):
    env=CryptoFixture(pe);obj=env.OBJECT;out=obj+0x2000;data=obj+0x3000;length=obj+0x4000;keyptr=obj+0x5000
    env.put(obj,pe.base+0x6d1cb8)
    local_numbers=local_keys['passport_1024.der']
    local_pem=spki_pem(local_numbers)
    samples=[('one',bytes([0x41]),1,keys[0],True),
             ('boundary117',bytes(range(1,118)),117,keys[0],True),
             ('oversize118',bytes(range(1,119)),118,keys[0],False),
             ('empty',b'',0,keys[0],True),
             ('embeddedNul',bytes([1,0,2]),3,keys[0],True),
             ('utf8',chr(0x4e2d).encode()+chr(0x1f600).encode(),7,keys[0],True),
             ('lengthHigh32',bytes([0x41]),0x100000001,keys[0],True),
             ('malformedKey',bytes([0x41]),1,b'not-a-key',False),
             ('truncatedKey',bytes([0x41]),1,keys[0][:-12],False),
             ('nullKey',bytes([0x41]),1,None,False),
             ('secondary245',bytes((i%251)+1 for i in range(245)),245,keys[1],True),
             ('secondaryOversize246',bytes((i%251)+1 for i in range(246)),246,keys[1],False),
             ('localKeyOne',bytes([0x41]),1,local_pem,True),
             ('localKey117',bytes(range(1,118)),117,local_pem,True)]
    rows=[]
    for name,plain,wide_length,pem,success in samples:
        env.uc.mem_write(data,plain+b'\0');env.put(length,wide_length)
        if pem is not None:env.uc.mem_write(keyptr,pem+b'\0')
        env.observed=[];env.padding_blocks=[];env.rsa_results=[];env.pending_native=[]
        event_at=len(env.events)
        result=env.call(0x3950c0,obj,out,data,length,0 if pem is None else keyptr)
        code_trace=env.trace;encoded=env.string(out)
        if bool(encoded)!=success or result!=out:
            raise ValueError('Native encrypt outcome mismatch: '+name)
        row={'name':name,'inputBytes':len(plain),'lengthStorage64':wide_length,
             'returnedOutputPointer64':result==out and result>0xffffffff,
             'encodedBytes':len(encoded),'nativeSteps':len(code_trace),
             'nativeCalls':list(env.observed),'executedOnlySDKCode':all(0<=r<0xa00000 for r in code_trace),
             'modelCalls':model_summary(env.events[event_at:])}
        if success:
            n,e=public_numbers(pem);width=(n.bit_length()+7)//8
            block=env.padding_blocks[-1];cipher=base64.b64decode(encoded,validate=True)
            sep=block.index(0,2)
            padding_valid=(len(block)==width and block[:2]==b'\0\2' and sep>=10
                           and all(block[2:sep]) and block[sep+1:]==plain)
            integer_equal=pow(int.from_bytes(block,'big'),e,n).to_bytes(width,'big')==cipher
            native_equal=env.rsa_results[-1]==cipher
            if not (padding_valid and integer_equal and native_equal):
                raise ValueError('Independent padding/RSA/Base64 oracle failed: '+name)
            row.update(pkcs1Type2Valid=padding_valid,integerRSAEqual=integer_equal,
                       nativeRSAEqualsBase64Decoded=native_equal,cipherBytes=width,
                       paddingNonzeroBytes=sep-2)
            if name.startswith('localKey'):
                decoded=pow(int.from_bytes(cipher,'big'),local_numbers[3],local_numbers[1]).to_bytes(width,'big')
                row['localPrivateDecryptEqual']=decoded==block and decoded[decoded.index(0,2)+1:]==plain
                if not row['localPrivateDecryptEqual']:raise ValueError('Local key interoperability failed')
        output_owner=env.get(out) if env.get(out+0x18)>=16 else None
        env.call(0x1380e0,out)
        row['outputOwnedCopyReleased']=output_owner is None or output_owner not in env.allocations
        rows.append(row)
    return rows,{'exitCallbacksRegistered':len(env.exit_callbacks),
                 'processGlobalAllocationsRetained':len(env.allocations),
                 'limit':'OpenSSL process globals/TLS kept until fixture disposal; not native process teardown'}


ROUTE_HOSTS=['passport-api.mihoyo.com','pre-passport-api.mihoyo.com','ue-account.mihoyo.com',
             'devapi-takumi.mihoyo.com','sandbox-sdk.mihoyo.com','ptsapi-takumi.mihoyo.com']
LOGIN_PATH='/account/ma-cn-passport/app/loginByPassword'


def route_fixture(pe):
    env=CryptoFixture(pe);env.call(0x107fe0)
    init_steps=len(env.trace)
    head=env.get(pe.base+0x7dbf70);pending=[env.get(head+8)];rows=[]
    while pending:
        node=pending.pop()
        if node==head:continue
        code=env.get(node+0x20,4);inner=env.get(node+0x28);todo=[env.get(inner+8)];count=0
        while todo:
            item=todo.pop()
            if item==inner:continue
            count+=1
            if env.string(item+0x20)==b'loginUrl':
                expected=('https://'+ROUTE_HOSTS[code]+LOGIN_PATH).encode()
                if env.string(item+0x40)!=expected:raise ValueError('Native fixed route mismatch')
                rows.append({'tableCode':code,'host':ROUTE_HOSTS[code],'path':LOGIN_PATH})
            todo.extend([env.get(item),env.get(item+0x10)])
        pending.extend([env.get(node),env.get(node+0x10)])
    if len(rows)!=6:raise ValueError('Native route table cardinality mismatch')
    config=env.OBJECT;url_object=config+0x2000;out=config+0x3000;name=config+0x4000;override=config+0x5000
    env.put(config,pe.base+0x6c6cd8);env.put(config+0x870,url_object)
    sentinel=env.allocate(0x60)
    for offset in [0,8,0x10]:env.put(sentinel+offset,sentinel)
    env.put(sentinel+0x18,0x101,2);env.put(url_object,sentinel)
    env.uc.mem_write(name,b'loginUrl\0')
    observations=[]
    for code in range(6):
        env.call(0x37e270,url_object,code)
        result=env.call(0x3773c0,config,out,name,0)
        equal=env.string(out)==('https://'+ROUTE_HOSTS[code]+LOGIN_PATH).encode()
        if not equal or result!=out:raise ValueError('Native selected route reader mismatch')
        observations.append({'code':code,'returnedOutputPointer64':result==out and result>0xffffffff,'fixedRouteEqual':equal})
        env.call(0x1380e0,out)
    env.call(0x37e270,url_object,99)
    env.call(0x3773c0,config,out,name,0)
    invalid_preserves=env.string(out)==('https://'+ROUTE_HOSTS[5]+LOGIN_PATH).encode()
    env.call(0x1380e0,out)
    env.uc.mem_write(override,b'0\0');env.call(0x3773c0,config,out,name,override)
    override_zero=env.string(out)==('https://'+ROUTE_HOSTS[0]+LOGIN_PATH).encode();env.call(0x1380e0,out)
    env.uc.mem_write(name,b'unknown_fixture_key\0');env.call(0x3773c0,config,out,name,0)
    fallback=env.string(out)==b'https://127.0.0.1';env.call(0x1380e0,out)
    empty_json=config+0x6000
    env.call(0x3646a0,config,empty_json)
    env.uc.mem_write(name,b'loginUrl\0');env.call(0x3773c0,config,out,name,0)
    default_zero=env.string(out)==('https://'+ROUTE_HOSTS[0]+LOGIN_PATH).encode();env.call(0x1380e0,out)
    if not all([invalid_preserves,override_zero,fallback,default_zero]):raise ValueError('Native config edge mismatch')
    return {'initializerRva':0x107fe0,'globalRva':0x7dbf70,'initNativeSteps':init_steps,
            'tables':sorted(rows,key=lambda r:r['tableCode']),'selectedReaders':observations,
            'invalidCodeKeepsPrevious':invalid_preserves,'explicitOverrideZero':override_zero,
            'unknownKeyUsesLoopbackFallback':fallback,'emptyConfigDefaultsCodeZero':default_zero,
            'modelCalls':model_summary(env.events),'naturalSelection':'unobserved'}


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client',type=Path,required=True)
    parser.add_argument('--output',type=Path,required=True)
    args=parser.parse_args()
    if args.output.exists() or not args.output.resolve().is_relative_to((ROOT/'local').resolve()):
        raise ValueError('Output must be new inside checkout local/')
    manifest_path=RAW/'input-manifest-v2.json'
    if digest(manifest_path.read_bytes())!=INPUT_MANIFEST_SHA:
        raise ValueError('Fixed input manifest changed')
    fixed=json.loads(manifest_path.read_bytes())
    for path,row in fixed.items():
        if digest((ROOT/path).read_bytes())!=row['sha256']:
            raise ValueError('Fixed source/evidence changed: '+path)
    profile=json.loads((ROOT/'protocol/cn-7.2/profile.json').read_bytes())
    modules={}
    for row in profile['files'].values():
        sha=digest((args.client/row['path']).read_bytes())
        if sha!=row['sha256']:raise ValueError('Profile input changed')
        modules[row['path']]=sha
    parent=json.loads((ROOT/'local/migration72/d02-active/sdk-transport-astra/final-first.json').read_bytes())
    for row in parent['modules']:
        sha=digest((args.client/row['path']).read_bytes())
        if sha!=MODULE_HASHES[Path(row['path']).name]:raise ValueError('Natural module disk input changed')
        modules[row['path']]=sha
    pe=Image((args.client/'YuanShen_Data/Plugins/AccountPlatNative.dll').read_bytes())
    local_keys={name:private_numbers((ROOT/'src/main/resources/keys'/name).read_bytes())
                for name in ['private_key.der','auth_private-key.der','SigningKey.der','passport_1024.der']}
    keys,pems=key_fixture(pe,local_keys)
    encryption,retained=encrypt_fixture(pe,pems,local_keys)
    routes=route_fixture(pe)
    security=(ROOT/'patch/src/modules/security.rs').read_text(encoding='utf-8')
    signature=re.search(r'const SDK_UTIL_RSA_ENCRYPT: &str = "([^"]+)"',security).group(1)
    pattern=b''.join(b'.' if x=='??' else re.escape(bytes([int(x,16)])) for x in signature.split())
    exe=PE((args.client/'YuanShen.exe').read_bytes())
    matches=list(re.finditer(pattern,exe.data,re.DOTALL))
    declared=json.loads((ROOT/'protocol/cn-7.2/patch-abi-observations.json').read_bytes())
    rsa_rows=[r for r in declared['declarations'] if r['name']=='RSAEncrypt']
    if len(rsa_rows)!=1 or rsa_rows[0]['entry']!='0x15997c290' or rsa_rows[0]['owner']!='SDKUtil':
        raise ValueError('Parent managed RSA declaration identity mismatch')
    target_offset=exe.offset(0x15997c290)
    if [m.start() for m in matches]!=[target_offset]:
        raise ValueError('Managed SDK RSA signature identity mismatch')
    managed={'module':'YuanShen.exe','entryRva':0x1997c290,'entryVA':0x15997c290,
             'patternUnique':True,'signatureFromCurrentPatch':True,
             'parameters':'RCX managed XML string; RDX managed content string',
             'scope':'parent metadata/ABI evidence plus fixed EXE byte scan; not full managed RSA execution'}
    windows={}
    for start,end in [(0x15390e,0x15395a),(0x34ee39,0x34efdb),(0x3950c0,0x3951bd),
                      (0x3951c0,0x39521b),(0x395d00,0x395ea1),(0x4b9d10,0x4b9d29),
                      (0x4ed800,0x4ed8e0),(0x3773c0,0x377491),(0x37e270,0x37e3d0),
                      (0x37e3e0,0x37e5bc),(0x3648d5,0x3648e8)]:
        data=pe.data[pe.offset(pe.base+start):pe.offset(pe.base+end-1)+1]
        windows[hex(start)]={'endRva':hex(end),'bytes':len(data),'sha256':digest(data),
             'pdataAtStart':list(pe.enclosing(start)),
             'instructions':[[hex(a-pe.base),m,o] for a,size,m,o in pe.instructions(start,end)]}
    native=json.loads((ROOT/'local/migration72/d02-active/main/cold-arrays-tool/live-elevated1.json').read_bytes())
    if native['sourceSHA256']!='2eb415af7e410b53df35340426310a52e72f7e9a69692c2158c4dadb92362aae':
        raise ValueError('Natural source epoch mismatch')
    rea=json.loads((RAW/'rea-final.json').read_bytes())
    evidence=[{'id':r['evidence_id'],'operation':r.get('operation'),'parameters':r.get('parameters'),
               'limitations':r.get('limitations',[])} for r in rea['records']
              if r.get('operation')=='procedure_pseudo_code']
    result={'scope':'Original SDK crypto/config instructions; only synthetic inputs, no live/network',
            'sourceSHA256':digest(Path(__file__).read_bytes()),'inputManifestSHA256':INPUT_MANIFEST_SHA,
            'fixedInputs':fixed,'moduleSHA256':modules,'nativeKeys':keys,'nativeEncrypt':encryption,
            'retainedState':retained,'nativeRoutes':routes,
            'staticRTTI':rtti_tables(pe,'MiHoYoSDKCryptoManager'),
            'managedHook':managed,'codeWindows':windows,'reaEvidence':evidence,
            'naturalInitializationOnly':{'sourceSHA256':native['sourceSHA256'],'sdkResult':native['sdkResult'],
                 'moduleBinding':native['sdkModuleBinding'],'roots':native['sdkRoots'],
                 'naturalCryptoCallObserved':False,'naturalRouteSelected':'unknown'},
            'remainingUnknowns':['Natural AccountPlat selection, crypto call and login URL are unobserved.',
                 'No hook/DLL loading or live key/route substitution is implemented or approved.',
                 'Managed RSA and dispatch crypto are different paths; this card executes only SDK crypto.',
                 'OpenSSL process teardown and real OS RNG/threads are not executed.']}
    args.output.write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8')
    print(json.dumps({'keys':len(keys),'encryptSamples':len(encryption),'routeTables':len(routes['tables']),
                      'sha256':digest(args.output.read_bytes())}))


if __name__=='__main__':
    main()
