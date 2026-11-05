"""Second dispatch coroutine, from explicit resumed HTTP-result fixtures.

States 2/3 execute the real MoveNext, crypto, parser and post-processing. Initial
request/wait scheduling and the tail after B0 are outside this experiment.
"""
import base64
import hashlib
import json
import struct
from pathlib import Path
try:
    from .trace_client72_dispatch_session import DispatchSession72, declarations
    from .trace_client72_dispatch_prefix import REJECT
    from .trace_client72_dispatch_post import check_declarations
    from .trace_client72_dispatch_config import verify_environment
    from .client72_sdk_update import SDKUpdate72
    from .client72_dispatch_notice import DispatchNotice72
    from .client72_emulator import Offline72, STACK, END
    from .client72_rsa_fixture import private_numbers, sign_sha256, encrypt_seed, tlv
    from .client72_seed_crypto import rsa_public_numbers
    from .extract_client72 import HASHES, read_verified, extract, PE
    from .trace_client72_auth_sources import parse_args
    from .trace_client72_token_complete import construct_native_token
    from .trace_client72_login import Writer72
    from .trace_client72_token import varint, parse_wire
except ImportError:
    from trace_client72_dispatch_session import DispatchSession72, declarations
    from trace_client72_dispatch_prefix import REJECT
    from trace_client72_dispatch_post import check_declarations
    from trace_client72_dispatch_config import verify_environment
    from client72_sdk_update import SDKUpdate72
    from client72_dispatch_notice import DispatchNotice72
    from client72_emulator import Offline72, STACK, END
    from client72_rsa_fixture import private_numbers, sign_sha256, encrypt_seed, tlv
    from client72_seed_crypto import rsa_public_numbers
    from extract_client72 import HASHES, read_verified, extract, PE
    from trace_client72_auth_sources import parse_args
    from trace_client72_token_complete import construct_native_token
    from trace_client72_login import Writer72
    from trace_client72_token import varint, parse_wire

ENTRY, POST, STOP = 0x151226CD0, 0x1512287E8, 0x151229567
TRANSPORT_BRANCH, INVALID_STATE = 0x151226F28, 0x151229E5B
SIGNATURE_ERROR = 0x14713C680
SDK_BOUNDARY=0x1512290B0
RETURNS=(0x1512289DC,0x151228A33)
CASES=(('state2',2,'source-A','normal'),('state3-empty',3,'','normal'),
       ('state3-high',3,'9007199254740993','normal'),('bad-sign',2,'unused','bad-sign'),
       ('missing-content',2,'unused','missing-content'),('transport-error',3,'unused','transport-error'),
       ('status520',2,'unused','status520'),('invalid-state',5,'unused','invalid-state'))


class Coroutine72(DispatchSession72):
    def __init__(self,e,binary,metadata,plain,signer,languages):
        super().__init__(e,binary,metadata,plain,signer,'CN',2,languages,'normal')
        for address in (ENTRY,0x1471685A0,0x159BC3AC0,0x159BD48D0,0x14F8F0AE0,
                        0x152754190,0x15276B740,0x153D33980): e.allow(address)
        for address in (STOP,TRANSPORT_BRANCH,INVALID_STATE,SIGNATURE_ERROR): e.allowed.append((address,address+1))
        self.coroutine=e.alloc(512); self.closure=e.alloc()
        e.write(self.coroutine+32,self.sender); e.write(self.coroutine+24,self.closure)

    def observe(self,address):
        e=self.e
        if address==POST:
            self.post_pointer=e.read(e.read(e.reg('rbp')+0x98))==self.parsed
            if not self.post_pointer or self.reader_calls!=1: raise ValueError('Coroutine parsed pointer/reader mismatch')
        if address==SDK_BOUNDARY:
            self.sdk_update=SDKUpdate72(self,'sdk_app_fixture','7.2.0-fixture','CN',2,self.languages)
            self.notice=DispatchNotice72(self)
        super().observe(address)
        if address in RETURNS:
            self.config_returns.append(dict(address=hex(address),returnAL=e.reg('al')))
        if address==0x151229560: self.stored=True
        if address==SIGNATURE_ERROR: self.error_marker=self.text(e.reg('rcx'))
        if address in (STOP,TRANSPORT_BRANCH,INVALID_STATE,SIGNATURE_ERROR): self.stop=address; e.uc.emu_stop()

    def run_coroutine(self,state,payload,mode):
        e=self.e
        e.write(self.coroutine+80,state,'I')
        e.write(self.closure+24,self.string(json.dumps(payload)))
        e.write(self.closure+32,self.string('transport-fixture' if mode=='transport-error' else ''))
        e.write(self.closure+48,520 if mode=='status520' else 200,'I')
        e.uc.context_restore(e.context); e.calls=[]; e.failure=None; e.steps=0
        for k,v in dict(rsp=STACK+0x100008,rcx=self.coroutine).items(): e.uc.reg_write(getattr(e.R,'UC_X86_REG_'+k.upper()),v)
        e.before_instruction=self.observe
        e.uc.emu_start(ENTRY,END,timeout=90_000_000,count=100_000_000)
        e.before_instruction=None
        if e.failure or self.stop is None: raise ValueError(e.failure or 'Coroutine budget exhausted')
        source=e.read(self.parsed+32) if self.parsed else None
        return dict(stop=hex(self.stop),stateAfter=e.read(self.coroutine+80,'i'),readerCalls=self.reader_calls,
                    postParsePointerPreserved=self.post_pointer,storeExecuted=self.stored,
                    sessionPointerPreserved=source is not None and e.read(self.account+176)==source,
                    sessionUnchanged=self.text(e.read(self.account+176))==self.session_values[176],
                    errorMarker=self.error_marker,keyChecks=self.key_checks,configReturns=self.config_returns,
                    sdkEntries=self.sdk_update.entries if self.sdk_update else 0,
                    nativePlaintextSHA256=hashlib.sha256(self.decrypted).hexdigest() if self.decrypted is not None else None,
                    trace=dict(instructions=e.steps,instructionPathSHA256=self.digest.hexdigest(),
                               nativeLibraryBlocks=self.crypto.native.library_blocks,
                               nativeLibraryBlockSHA256=self.crypto.native.library_digest.hexdigest()))


def coroutine_declarations(binary,records):
    by_index={r['index']:r for r in records}; row=by_index[23192]
    if not any((m['name'],m['registeredAddress'],m['virtualSlot'])==('MoveNext',hex(ENTRY),6) for m in row['methods']):
        raise ValueError('Coroutine MoveNext declaration changed')
    for owner,offset,kind,index in ((23192,80,'int32',None),(23192,24,'class',23181),(23192,32,'class',23177),
                                   (23181,24,'string',None),(23181,32,'string',None),(23181,48,'valuetype',1787)):
        fields=[f for f in by_index[owner]['fields'] if f.get('offsetKind')=='instance' and f['offset']==offset]
        if len(fields)!=1 or fields[0]['type']['kind']!=kind or fields[0]['type'].get('typeDefinition')!=index:
            raise ValueError('Coroutine closure layout changed')
    if (by_index[1787]['namespace'],by_index[1787]['name'])!=('System.Net','HttpStatusCode'): raise ValueError('Closure transport enum identity changed')
    table=0x15122A1F8; pe=PE(binary); targets=[table+struct.unpack_from('<i',binary,pe.offset(table)+4*i)[0] for i in range(5)]
    if targets!=[0x151226DEA,0x151226E77,0x151226D20,0x151226D20,0x151226DCD]: raise ValueError('Coroutine state dispatch changed')
    return dict(typeIndex=23192,closureTypeIndex=23181,stateOffset=80,httpStatusTypeIndex=1787,
                jumpTable=hex(table),targets=[hex(v) for v in targets])


def recover(binary,metadata,startup,records,libraries,root):
    check_declarations(binary,records); verify_environment(records); declarations(binary,metadata,startup,records)
    binding=coroutine_declarations(binary,records)
    enums=json.loads((root/'protocol/cn-7.2/enum-observations.json').read_text('utf8'))
    if enums['inputSHA256']!=HASHES: raise ValueError('Enum inputs changed')
    languages={v['value']:v['symbol'] for v in next(r for r in enums['enums'] if r['typeIndex']==77368)['values']}
    signer=private_numbers((root/'src/main/resources/keys/SigningKey.der').read_bytes())
    _,body,_=tlv((root/'src/main/resources/keys/game_keys/4_Pub.der').read_bytes()); _,_,at=tlv(body)
    _,value,_=tlv(body,at); n,exponent=rsa_public_numbers(value[1:]); fixtures=[]
    for name,state,source,mode in CASES:
        raw=source.encode('ascii'); plain=b'\x72'+varint(len(raw))+raw+b'\x1a\x00\x62\x02{}\x6a\x02{}\x5a\x00'
        signature=sign_sha256(signer,plain)
        if mode=='bad-sign': signature=bytes([signature[0]^1])+signature[1:]
        payload=dict(content=base64.b64encode(encrypt_seed([0,n,exponent],plain)).decode(),sign=base64.b64encode(signature).decode())
        if mode=='missing-content': payload.pop('content')
        e=Offline72(binary,libraries,records,True); runtime=None
        try:
            e.prepare(); runtime=Coroutine72(e,binary,metadata,plain,signer,languages)
            result=runtime.run_coroutine(state,payload,mode)
            row=dict(name=name,state=state,mode=mode,plainWire=plain.hex(),**result,tokenFieldWire=None,cmdId=None)
            token=None
            if mode=='normal':
                crypto,_=construct_native_token(e,runtime,binary,metadata,0.5,True,seed=runtime.crypto)
                p=e.read(runtime.request+112); text=bytes(e.uc.mem_read(p+20,e.read(p+16,'I')*2)).decode('utf-16-le')
                if text!=source or raw and p!=e.read(runtime.parsed+32): raise ValueError('Coroutine source changed in Token')
                runtime.strings[p]=text; writer=Writer72(e)
                wire=writer.run(82491,runtime.values(82491,runtime.request,writer))['wire']
                if [(t,k,v) for t,k,v in parse_wire(bytes.fromhex(wire)) if t==965]!=( [(965,2,raw)] if raw else []): raise ValueError('Coroutine independent wire mismatch')
                e.run(0x1500C4A50,runtime.request,0)
                if e.reg('eax')!=24604: raise ValueError('Coroutine Token command mismatch')
                row.update(tokenFieldWire=(b'\xaa\x3c'+varint(len(raw))+raw).hex() if raw else '',cmdId=24604)
                token=dict(crypto=crypto,wire=wire)
            fixtures.append(dict(**row,token=token))
            print(json.dumps(dict(fixture=name,stop=result['stop'],store=runtime.stored)),flush=True)
        finally:
            if runtime: runtime.close()
            e.close()
    report=dict(schemaVersion=1,inputSHA256=HASHES,fixtureCount=len(fixtures),fixtures=fixtures,
                results=[{k:v for k,v in f.items() if k not in ('trace','token')} for f in fixtures],
                entry=hex(ENTRY),postParse=hex(POST),stop=hex(STOP),binding=binding,
                semanticStatus='unknown',runtimeStatus='not-tested',wholeCoroutineExecuted=False,
                sourceAssociation='native-resumed-coroutine-to-session',
                models=['inherited dispatch/config/SDK/notice/OS fixtures, explicit local signing trust',
                        'coroutine state2/3 and completed response closure supplied as inputs; no HTTP performed'],
                outsideScope=['state0/1 request creation and HTTP waiting','retry/error recovery after transport branch',
                              'tail after B0 including callbacks','actual SDK/UI/IFix','tag14/tag965 business name','actual client'])
    errors=validate_summary(report)
    if errors: raise ValueError(errors)
    return report


def validate_summary(report):
    errors=[]
    expected=dict(inputSHA256=HASHES,fixtureCount=8,entry=hex(ENTRY),postParse=hex(POST),stop=hex(STOP),
                  semanticStatus='unknown',runtimeStatus='not-tested',wholeCoroutineExecuted=False,
                  sourceAssociation='native-resumed-coroutine-to-session')
    if any(report.get(k)!=v for k,v in expected.items()): errors.append('Coroutine scope mismatch')
    binding=dict(typeIndex=23192,closureTypeIndex=23181,stateOffset=80,httpStatusTypeIndex=1787,
                 jumpTable='0x15122a1f8',targets=['0x151226dea','0x151226e77','0x151226d20','0x151226d20','0x151226dcd'])
    if report.get('binding')!=binding: errors.append('Coroutine declaration/state table mismatch')
    rows=report.get('results',[])
    if [r.get('name') for r in rows]!=[c[0] for c in CASES]: errors.append('Coroutine cases mismatch')
    for row,(_,state,source,mode) in zip(rows,CASES):
        accepted=mode=='normal'
        stop=STOP if accepted else TRANSPORT_BRANCH if mode in ('transport-error','status520') else INVALID_STATE if mode=='invalid-state' else SIGNATURE_ERROR if mode=='bad-sign' else REJECT
        if (row.get('state')!=state or row.get('mode')!=mode or row.get('stop')!=hex(stop)
                or row.get('stateAfter')!=(5 if mode=='invalid-state' else -1)): errors.append('Coroutine state/branch mismatch')
        if any(row.get(k)!=accepted for k in ('storeExecuted','postParsePointerPreserved','sessionPointerPreserved')) or row.get('sessionUnchanged')!= (not accepted):
            errors.append('Coroutine parsed/store boundary mismatch')
        if row.get('readerCalls')!=int(accepted) or row.get('sdkEntries')!=int(accepted): errors.append('Rejected coroutine reached parser/SDK')
        raw=source.encode(); plain=b'\x72'+varint(len(raw))+raw+b'\x1a\x00\x62\x02{}\x6a\x02{}\x5a\x00'
        if row.get('plainWire')!=plain.hex(): errors.append('Coroutine input fixture changed')
        if accepted:
            if row.get('configReturns')!=[dict(address=hex(a),returnAL=1) for a in RETURNS]: errors.append('Coroutine config returns missing')
            if row.get('nativePlaintextSHA256')!=hashlib.sha256(plain).hexdigest(): errors.append('Coroutine native plaintext differs')
            checks=row.get('keyChecks',[])
            if [c.get('operation') for c in checks]!=[101,103] or any(c.get('native') is not True or c.get('oracle') is not True for c in checks): errors.append('Coroutine crypto checks missing')
        elif row.get('configReturns'): errors.append('Rejected coroutine reached config')
        if mode in ('bad-sign','missing-content'):
            marker='[RegionDispatch] DecryptRegionDispatch Decrypt '+('VerifyHash Fail' if mode=='bad-sign' else 'inputJson Content is Null')
            if row.get('errorMarker')!=marker: errors.append('Coroutine rejection marker mismatch')
        field=(b'\xaa\x3c'+varint(len(raw))+raw).hex() if raw else ''
        if row.get('tokenFieldWire')!=(field if accepted else None) or row.get('cmdId')!=(24604 if accepted else None): errors.append('Coroutine Token wire mismatch')
    return errors


def main():
    a=parse_args(); b,m,s=[read_verified(a.client/name,sha) for name,sha in HASHES.items()]
    records,_=extract(b,m,startup=s,structures=True)
    report=recover(b,m,s,records,a.libraries,Path(__file__).resolve().parents[1])
    for path,value in ((a.output,report),(a.summary_output,{k:v for k,v in report.items() if k!='fixtures'})):
        path.parent.mkdir(parents=True,exist_ok=True); path.write_text(json.dumps(value,indent=2)+'\n','utf8')
    print(json.dumps(dict(fixtures=report['fixtureCount'],wholeCoroutineExecuted=False)))


if __name__=='__main__': main()
