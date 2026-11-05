"""Signed dispatch -> native Ec2b/key storage -> native Token frame.

Dispatch and Token are two explicit invocations in the same emulator. The
dispatch-to-Token scheduler, HTTP and SDK startup are not claimed to execute.
No frame key is supplied: the dispatch's actual output pointer stays installed.
"""
from collections import Counter
import base64
import hashlib
import json
from pathlib import Path
import struct

try:
    from .extract_client72 import HASHES, read_verified, extract
    from .client72_emulator import Offline72, GLOBAL, END
    from .client72_ec2b import Ec2b72, KEY_HASHES
    from .client72_rsa_fixture import private_numbers, sign_sha256, encrypt_seed, tlv
    from .client72_seed_crypto import rsa_public_numbers
    from .trace_client72_dispatch_session import DispatchSession72, STOP, declarations
    from .trace_client72_dispatch_config import RETURNS, verify_environment
    from .trace_client72_dispatch_post import check_declarations
    from .trace_client72_token_complete import construct_native_token
    from .trace_client72_token_frame import TokenFrame72, check_payload
    from .trace_client72_frame import expected_frame
    from .trace_client72_token import varint
    from .trace_client72_auth_sources import parse_args
except ImportError:
    from extract_client72 import HASHES, read_verified, extract
    from client72_emulator import Offline72, GLOBAL, END
    from client72_ec2b import Ec2b72, KEY_HASHES
    from client72_rsa_fixture import private_numbers, sign_sha256, encrypt_seed, tlv
    from client72_seed_crypto import rsa_public_numbers
    from trace_client72_dispatch_session import DispatchSession72, STOP, declarations
    from trace_client72_dispatch_config import RETURNS, verify_environment
    from trace_client72_dispatch_post import check_declarations
    from trace_client72_token_complete import construct_native_token
    from trace_client72_token_frame import TokenFrame72, check_payload
    from trace_client72_frame import expected_frame
    from trace_client72_token import varint
    from trace_client72_auth_sources import parse_args

CASES = ('repository-pair', 'unicode-key-wrap', 'invalid-magic-plaintext', 'empty-key-plaintext')
CONFIG_TEXTS = (b'{"downloadThreadNum":7}', b'{"showexception":true}')


def xor_bytes(data, key):
    return bytes(v ^ key[i % len(key)] for i,v in enumerate(data)) if key else data


def dispatch_wire(seed, key):
    # Literal wire keys, independent of extracted field tables.
    parts = ((b'\x72',b'source'), (b'\x1a',b''),
             (b'\x62',xor_bytes(CONFIG_TEXTS[0],key)),
             (b'\x6a',xor_bytes(CONFIG_TEXTS[1],key)), (b'\x5a',seed))
    return b''.join(tag+varint(len(value))+value for tag,value in parts)


class DispatchKey72(DispatchSession72):
    def __init__(self,*args):
        super().__init__(*args)
        self.key_calls=[]; self.xor_inputs=[]; self.xor_outputs=[]; self.key_clears=[]

    def string(self,text):
        # Full Ec2b requires multi-block RSA and a JSON string over 2048 bytes.
        raw=text.encode('utf-16-le'); pointer=self.e.alloc(22+len(raw))
        self.e.write(pointer+16,len(raw)//2,'I')
        self.e.uc.mem_write(pointer+20,raw); self.strings[pointer]=text
        return pointer

    def run_prefix(self,payload):
        return super().run_prefix(payload,timeout_us=360_000_000,instruction_limit=600_000_000)

    def array_bytes(self,pointer):
        return bytes(self.e.uc.mem_read(pointer+32,self.e.read(pointer+24))) if pointer else b''

    def observe(self,address):
        e=self.e
        if address==0x14146ADA0:
            self.key_calls.append(dict(inputSHA256=hashlib.sha256(self.array_bytes(e.reg('rcx'))).hexdigest(),
                                       returnAddress=e.read(e.reg('rsp')),outputPointer=None))
        if self.key_calls and address==self.key_calls[-1]['returnAddress']:
            row=self.key_calls[-1]; row['outputPointer']=e.reg('rax')
            output=self.array_bytes(e.reg('rax'))
            row.update(outputLength=len(output),outputSHA256=hashlib.sha256(output).hexdigest())
        if address==0x151204C37:
            pointer=e.reg('rbx'); key=self.array_bytes(pointer)
            self.xor_inputs.append(dict(keyPointer=pointer,keyLength=len(key),
                                       keySHA256=hashlib.sha256(key).hexdigest(),
                                       inputSHA256=hashlib.sha256(self.array_bytes(e.reg('r12'))).hexdigest()))
        if address==0x151204C8D:self.xor_outputs.append(self.array_bytes(e.reg('r12')))
        if address in RETURNS:self.key_clears.append(e.read(GLOBAL+0x19588)==0)
        super().observe(address)


def token_frame(e,runtime,frame,binary,metadata,wire,key,sequence,sent_ms):
    key_pointer=e.read(GLOBAL+0x19588)
    counts=Counter(); digest=hashlib.sha256(); returns=[]; request_before=[]
    def observe(address):
        counts[address]+=1; digest.update(struct.pack('<Q',address))
        if address==0x14A447E00:
            if e.read(e.reg('rcx')+0x10)!=runtime.request:raise ValueError('Frame lost original Token request')
            returns.append(e.read(e.reg('rsp')))
        if returns and address==returns[0]:e.uc.emu_stop()
    def continue_send():
        if e.read(GLOBAL+0x19588)!=key_pointer:raise ValueError('Token construction replaced dispatch key')
        request_before.append(bytes(e.uc.mem_read(runtime.request,0xc8)))
        frame.ctor=runtime; frame.attach(runtime,sequence,sent_ms)
        e.models[0x1404D7630]=frame.copy_array
        e.before_instruction=observe
        # Run before the seed oracle invokes its separate native value getter.
        # Continue at the constructor's original send call without a CPU reset.
        e.uc.emu_start(e.reg('rip'),END,timeout=20_000_000,count=20_000_000)
        e.before_instruction=None
        if e.failure or len(returns)!=1 or e.reg('rip')!=returns[0] or e.reg('al')!=1:
            raise ValueError(e.failure or 'Token frame did not return')
    crypto,seed_pointer=construct_native_token(e,runtime,binary,metadata,0.5,True,
                                              seed=runtime.crypto,continue_send=continue_send)
    if request_before!=[bytes(e.uc.mem_read(runtime.request,0xc8))] or e.read(runtime.request+0x90)!=seed_pointer:
        raise ValueError('Token request or native seed return changed')
    if e.read(GLOBAL+0x19588)!=key_pointer or runtime.array_bytes(key_pointer)!=key:
        raise ValueError('Native dispatch key pointer/bytes changed during frame construction')
    if e.read(0x145878FC0,'B')!=1:raise ValueError('Token failed to select initial key slot')
    length=e.read(frame.flow.packet+0x30,'I')
    encrypted=bytes(e.uc.mem_read(frame.buffer+32,length)); plain=xor_bytes(encrypted,key)
    header_length=int.from_bytes(plain[4:6],'big'); body=plain[10+header_length:-2]
    fields=check_payload(e,runtime,body,wire)
    if encrypted!=expected_frame(24604,body,sequence,sent_ms,key):raise ValueError('Independent frame bytes differ')
    if fields.get(965)!=(2,b'source'):raise ValueError('Dispatch source did not reach Token tag965')
    for address in (0x151214D20,0x1472CD100,0x14A447E00,0x1500C16E0,0x1500C2660):
        if not counts[address]:raise ValueError('Native Token/frame stage missing')
    return dict(frameLength=length,bodyLength=len(body),frameSHA256=hashlib.sha256(encrypted).hexdigest(),
                bodySHA256=hashlib.sha256(body).hexdigest(),sequence=sequence,sentMs=sent_ms,
                cmdId=24604,keyPointerPreserved=True,keyBytesPreserved=True,requestPointerPreserved=True,
                seedPointerPreserved=True,allTokenFieldsMatch=True,source965Match=True,
                seedIndependentOracle=crypto['passed'] and crypto['oracle'] is not None,
                plainFrame=not bool(key),keyWrap=bool(key) and length>len(key),
                trace=dict(pathSHA256=digest.hexdigest(),addresses={hex(a):v for a,v in sorted(counts.items())}))


def recover(binary,metadata,startup,records,libraries,root):
    check_declarations(binary,records); verify_environment(records)
    declarations(binary,metadata,startup,records)
    seed,key=[read_verified(root/'src/main/resources/keys'/name,sha) for name,sha in KEY_HASHES.items()]
    wire=json.loads((root/'protocol/cn-7.2/token-wire-observations.json').read_text('utf8'))
    if wire['inputSHA256']!=HASHES or wire['typeIndex']!=82491:raise ValueError('Token wire input changed')
    enums=json.loads((root/'protocol/cn-7.2/enum-observations.json').read_text('utf8'))
    if enums['inputSHA256']!=HASHES:raise ValueError('Language enum input changed')
    languages={v['value']:v['symbol'] for r in enums['enums'] if r['typeIndex']==77368 for v in r['values']}
    signer=private_numbers((root/'src/main/resources/keys/SigningKey.der').read_bytes())
    _,body,_=tlv((root/'src/main/resources/keys/game_keys/4_Pub.der').read_bytes())
    _,_,at=tlv(body); _,value,_=tlv(body,at); n,exponent=rsa_public_numbers(value[1:])
    fixtures=[]; results=[]
    for name in CASES:
        valid=name in CASES[:2]; input_seed=seed if valid else (b'Nope' if name==CASES[2] else b'')
        expected_key=key if valid else b''; plain=dispatch_wire(input_seed,expected_key)
        blocks=[encrypt_seed([0,n,exponent],plain[i:i+245]) for i in range(0,len(plain),245)]
        payload=dict(content=base64.b64encode(b''.join(blocks)).decode(),sign=base64.b64encode(sign_sha256(signer,plain)).decode())
        e=Offline72(binary,libraries,records,True); runtime=None
        try:
            e.prepare(); frame=TokenFrame72(e,initial_key=None,session_key=None)
            runtime=DispatchKey72(e,binary,metadata,plain,signer,'CN',2,languages,'normal')
            ec2b=Ec2b72(e,runtime.crypto.native)
            # Dispatch's byte/uint arrays have real class layouts and use the
            # native copy helper. The frame's pooled-storage model starts later.
            e.models.pop(0x1404D7630)
            if e.read(GLOBAL+0x19588):raise ValueError('Unexpected supplied initial frame key')
            flow=runtime.run_config(payload)
            key_pointer=e.read(GLOBAL+0x19588)
            if flow['stop']!=hex(STOP) or runtime.array_bytes(key_pointer)!=expected_key:
                raise ValueError('Dispatch failed to install expected Ec2b output')
            expected_calls=3 if input_seed else 0
            if len(runtime.key_calls)!=expected_calls or len(ec2b.output_allocations)!=(3 if valid else 0):
                raise ValueError('Unexpected managed Ec2b calls/allocations')
            if any(v['inputSHA256']!=hashlib.sha256(input_seed).hexdigest() or
                   v['outputSHA256']!=hashlib.sha256(expected_key).hexdigest() for v in runtime.key_calls):
                raise ValueError('Dispatch source or Ec2b output bytes differ between consumers')
            if runtime.key_calls and key_pointer!=runtime.key_calls[-1]['outputPointer']:
                raise ValueError('Final dispatch store replaced decoder return pointer')
            if runtime.xor_outputs!=list(CONFIG_TEXTS) or runtime.key_clears!=[True,True]:
                raise ValueError('Native config XOR/clear differs')
            if valid and any(v['keyPointer']!=runtime.key_calls[i]['outputPointer'] for i,v in enumerate(runtime.xor_inputs)):
                raise ValueError('Config XOR did not use native decoder return pointer')
            if any(v['keySHA256']!=hashlib.sha256(expected_key).hexdigest() for v in runtime.xor_inputs):
                raise ValueError('Config XOR key bytes differ')
            if runtime.snapshot()['downloadThreadNum']!=7 or runtime.snapshot()['showexception']!=1:
                raise ValueError('Decrypted config consumers changed')
            if name=='unicode-key-wrap':
                text='账号\U0001f680\x00'+'L'*1600
                for offset in (0x78,0xf8):e.write(runtime.account+offset,runtime.string(text))
                e.write(GLOBAL+0x18190,runtime.string(text))
            sequence,sent_ms=(0,0) if name=='repository-pair' else (0xffffffff,0x200000044)
            sent=token_frame(e,runtime,frame,binary,metadata,wire,expected_key,sequence,sent_ms)
            if sent['keyWrap']!=(name=='unicode-key-wrap'):raise ValueError('Expected frame key-cycle boundary changed')
            result=dict(name=name,rsaBlocks=len(blocks),dispatchStop=flow['stop'],nativePlaintextMatch=runtime.decrypted==plain,
                        keyCalls=expected_calls,keyAllocations=len(ec2b.output_allocations),keyLength=len(expected_key),
                        keySHA256=hashlib.sha256(expected_key).hexdigest(),sourceSHA256=hashlib.sha256(input_seed).hexdigest(),
                        configPlaintexts=[v.decode('ascii') for v in runtime.xor_outputs],configKeyCleared=runtime.key_clears,
                        configValues=runtime.snapshot(),frame={k:v for k,v in sent.items() if k!='trace'},passed=True)
            results.append(result)
            fixtures.append(dict(**result,dispatchTrace=flow['trace'],keyCallsTrace=runtime.key_calls,
                                 configXorTrace=runtime.xor_inputs,frameTrace=sent['trace']))
            print(json.dumps(dict(fixture=name,frameLength=sent['frameLength'],keyLength=len(expected_key))),flush=True)
        finally:
            if runtime:runtime.close()
            e.close()
    return dict(schemaVersion=1,inputSHA256=HASHES,keyFileSHA256=KEY_HASHES,fixtureCount=len(fixtures),
                results=results,fixtures=fixtures,sourceTypeIndex=30677,sourceTag=11,sourceOffset=64,
                semanticStatus='unknown',runtimeStatus='not-tested',dispatchEntry='0x151210540',dispatchStop=hex(STOP),
                keyDecoder='0x14146ada0',keySlot='GLOBAL+0x19588',keyStore='0x151212942',
                tokenEntry='0x15121ff70',frameEntry='0x14a447e00',schedulerExecuted=False,frameKeySupplied=False,
                models=['JSON/SDK/storage from dispatch-session', 'native crypto OS/CRT and local signed trust inputs',
                        'Ec2b engine selector5 and byte[] allocation', 'cached UTF-8 reads actual mutated array bytes',
                        'pooled stream/transport selection from token-frame'],
                outsideScope=['HTTP and dispatch-to-Token scheduler', 'SDK initialization and patch loading',
                              'second dispatch coroutine with nonempty Ec2b', 'source business name', 'socket and real-client acceptance'])


def validate_summary(report):
    errors=[]
    expected=dict(inputSHA256=HASHES,keyFileSHA256=KEY_HASHES,fixtureCount=4,sourceTypeIndex=30677,
                  sourceTag=11,sourceOffset=64,semanticStatus='unknown',runtimeStatus='not-tested',
                  dispatchEntry='0x151210540',dispatchStop=hex(STOP),keyDecoder='0x14146ada0',
                  keySlot='GLOBAL+0x19588',keyStore='0x151212942',tokenEntry='0x15121ff70',
                  frameEntry='0x14a447e00',schedulerExecuted=False,frameKeySupplied=False)
    if any(report.get(k)!=v for k,v in expected.items()):errors.append('Dispatch-key scope or input mismatch')
    if not report.get('models') or not report.get('outsideScope'):errors.append('Dispatch-key model boundaries missing')
    rows=report.get('results',[])
    if [r.get('name') for r in rows]!=list(CASES):errors.append('Dispatch-key cases mismatch')
    for row in rows:
        valid=row.get('name') in CASES[:2]; frame=row.get('frame',{})
        source_hash=KEY_HASHES['dispatchSeed.bin'] if valid else hashlib.sha256(b'Nope' if row.get('name')==CASES[2] else b'').hexdigest()
        if (row.get('passed') is not True or row.get('nativePlaintextMatch') is not True
            or row.get('dispatchStop')!=hex(STOP) or row.get('keyLength')!=(4096 if valid else 0)
            or row.get('rsaBlocks')!=(9 if valid else 1) or row.get('sourceSHA256')!=source_hash
            or row.get('keyCalls')!=(0 if row.get('name')==CASES[3] else 3)
            or row.get('keyAllocations')!=(3 if valid else 0)
            or row.get('keySHA256')!=(KEY_HASHES['dispatchKey.bin'] if valid else hashlib.sha256(b'').hexdigest())):
            errors.append('Dispatch-key native result mismatch')
        if (row.get('configPlaintexts')!=[v.decode() for v in CONFIG_TEXTS]
            or row.get('configKeyCleared')!=[True,True]
            or row.get('configValues')!=dict(downloadThreadNum=7,showexception=1,
                safeOptionalResourceCategories=[],secureOptionalResourcePolicyPlugins=[])):
            errors.append('Config decryption/clear mismatch')
        for name in ('keyPointerPreserved','keyBytesPreserved','requestPointerPreserved','seedPointerPreserved',
                     'allTokenFieldsMatch','source965Match','seedIndependentOracle'):
            if frame.get(name) is not True:errors.append('Token/frame continuity or independent comparison missing')
        if (frame.get('cmdId')!=24604 or frame.get('plainFrame')!=(not valid)
            or frame.get('keyWrap')!=(row.get('name')=='unicode-key-wrap')):
            errors.append('Token frame key selection mismatch')
        sequence,sent_ms=(0,0) if row.get('name')=='repository-pair' else (0xffffffff,0x200000044)
        if frame.get('sequence')!=sequence or frame.get('sentMs')!=sent_ms:errors.append('Frame header boundary mismatch')
    return errors


def main():
    args=parse_args(); binary,metadata,startup=[read_verified(args.client/name,sha) for name,sha in HASHES.items()]
    records,_=extract(binary,metadata,startup=startup,structures=True)
    report=recover(binary,metadata,startup,records,args.libraries,Path(__file__).resolve().parents[1])
    errors=validate_summary(report)
    if errors:raise ValueError(errors)
    for path,value in ((args.output,report),(args.summary_output,{k:v for k,v in report.items() if k!='fixtures'})):
        path.parent.mkdir(parents=True,exist_ok=True); path.write_text(json.dumps(value,indent=2)+'\n','utf8')
    print(json.dumps(dict(fixtures=report['fixtureCount'],runtimeStatus=report['runtimeStatus'])))


if __name__=='__main__':main()
