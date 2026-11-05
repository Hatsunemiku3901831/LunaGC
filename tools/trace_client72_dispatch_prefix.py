"""CN72 RegionDispatch JSON -> native crypto -> declared response reader prefix.

Local success uses an explicit signed public-key/root input, never a forced
verification result. HTTP transport and post-parse dispatch are outside scope.
"""
import base64
from collections import Counter
import hashlib
import json
from pathlib import Path
import struct
try:
    from .extract_client72 import HASHES, read_verified, extract, PE
    from .client72_emulator import Offline72, STACK, END
    from .trace_client72_auth_sources import AuthSources72, parse_args
    from .client72_response_crypto import ResponseCrypto72
    from .client72_rsa_fixture import private_numbers, public_der, public_pem, sign_sha256, encrypt_seed, tlv
    from .client72_seed_crypto import rsa_public_numbers, verify_key_signature
    from .client72_reader import WireInput
except ImportError:
    from extract_client72 import HASHES, read_verified, extract, PE
    from client72_emulator import Offline72, STACK, END
    from trace_client72_auth_sources import AuthSources72, parse_args
    from client72_response_crypto import ResponseCrypto72
    from client72_rsa_fixture import private_numbers, public_der, public_pem, sign_sha256, encrypt_seed, tlv
    from client72_seed_crypto import rsa_public_numbers, verify_key_signature
    from client72_reader import WireInput

ENTRY, SUCCESS, REJECT = 0x151210540, 0x151211BA1, 0x147161B60
READER, MERGE, CLASS_CELL = 0x153A598A0, 0x147EA0940, 0x1458C3CC0


class DispatchPrefix72(AuthSources72):
    def __init__(self,e,binary,metadata,plain,signer,local_trust):
        super().__init__(e,binary,metadata,0,'123')
        self.plain=plain;self.signer=signer;self.local_trust=local_trust
        for cell in (0x1458C3CB8,CLASS_CELL,0x1458AD6D0):e.synthetic_type(cell)
        self.classes[e.read(CLASS_CELL)]=30677
        self.crypto=ResponseCrypto72(e,binary,metadata,0);self.crypto.close()
        e.models[0x1405336C0]=self.allocate_dispatch_environment
        e.write(e.read(CLASS_CELL)+0x168,READER)
        for address in (ENTRY,MERGE,READER):e.allow(address)
        e.allowed.append((REJECT,REJECT+1))
        self.inputs={};self.parsed=None;self.decrypted=None;self.operation=None;self.operations=[];self.key_checks=[]
        self.reader_calls=0;self.stop=None;self.error_marker=None;self.visits=Counter();self.digest=hashlib.sha256()
        self.local_der=public_der(signer);self.local_root=public_pem(self.local_der)
        self.local_raw=e.alloc(512);e.uc.mem_write(self.local_raw,self.local_der[2:])
        self.local_signature=e.alloc(256);e.uc.mem_write(self.local_signature,sign_sha256(signer,self.local_der))
        e.models.update({0x1474D8100:lambda:self.read_wire('tag'),0x147500E70:lambda:self.read_wire('string'),
                         0x1474D7BB0:lambda:self.read_wire('skip')})

    def allocate_dispatch_environment(self):
        if self.e.reg('rcx') in (self.e.read(0x1458C3CB8),self.e.read(0x1458AD6D0)):return self.e.alloc(512)
        return self.crypto.allocate_hash()

    def read_wire(self,kind):
        e=self.e;stream=e.reg('rcx')
        if stream not in self.inputs:
            array=e.read(stream+24);self.inputs[stream]=WireInput(bytes(e.uc.mem_read(array+32,e.read(array+24))))
        inp=self.inputs[stream];inp.position=e.read(stream+0x44,'I')
        if kind=='tag':result=inp.tag();e.write(stream+0x20,result,'I')
        elif kind=='string':result=self.string(inp.blob().decode('ascii'))
        else:inp.skip();result=0
        e.write(stream+0x44,inp.position,'I');return result

    def observe(self,address):
        e=self.e;self.digest.update(struct.pack('<Q',address))
        if address in (ENTRY,SUCCESS,REJECT,MERGE,READER,0x140571EB0,0x14078BAB0):self.visits[address]+=1
        if address==0x140571EB0:
            self.operation=e.reg('ecx')
            if self.operation not in (101,103):raise ValueError('Unexpected dispatch crypto operation')
            if self.operation==103:
                digest=bytes(e.uc.mem_read(e.reg('rdx'),e.reg('r8d')))
                if digest!=hashlib.sha256(self.plain).digest():raise ValueError('Native dispatch SHA256 differs from independent plaintext hash')
                if self.local_trust:
                    # Native API inputs: raw DER, key signature and signature length.
                    e.write(e.reg('rsp')+0x30,self.local_raw);e.write(e.reg('rsp')+0x38,self.local_signature)
                    e.write(e.reg('rsp')+0x40,256,'I')
        elif address==0x14078BAB0:
            root=e.read(e.reg('rsp')+0x28)
            if self.local_trust and self.operation==103:e.uc.mem_write(root,self.local_root+b'\0')
            der=bytes(e.uc.mem_read(e.reg('rcx'),e.reg('edx')))
            sig=bytes(e.uc.mem_read(e.reg('r8'),e.reg('r9d')))
            pem=bytes(e.uc.mem_read(root,0x1ac)).split(b'\0')[0]
            self.key_checks.append(dict(operation=self.operation,oracle=verify_key_signature(der,sig,pem),
                                        derSHA256=hashlib.sha256(der).hexdigest(),rootSHA256=hashlib.sha256(pem).hexdigest()))
        elif address==0x140574A9A:
            actual=bool(e.reg('al'));self.key_checks[-1]['native']=actual
            if actual!=self.key_checks[-1]['oracle']:raise ValueError('Native key signature differs from RSA oracle')
        elif address==0x15A4D7F00:self.operations.append(dict(operation=self.operation,nativeReturnRAX=hex(e.reg('rax'))))
        elif address==MERGE:
            self.parsed=e.reg('rcx');array=e.reg('rdx')
            if self.allocations.get(self.parsed)!=30677:raise ValueError('Dispatch parse receiver is not declared response30677')
            self.decrypted=bytes(e.uc.mem_read(array+32,e.read(array+24)))
            if self.decrypted!=self.plain:raise ValueError('Native dispatch decryption differs from independent plaintext')
        elif address==READER:
            if e.reg('rcx')!=self.parsed:raise ValueError('Native virtual reader receiver changed')
            self.reader_calls+=1
        if address in (SUCCESS,REJECT):
            self.stop=address
            if address==REJECT:self.error_marker=self.text(e.reg('rdx'))
            e.uc.emu_stop()

    def run_prefix(self,payload):
        e=self.e;e.uc.context_restore(e.context);e.calls=[];e.failure=None;e.steps=0
        for k,v in dict(rsp=STACK+0x100008,rcx=self.sender,rdx=self.string(json.dumps(payload)),r8=0).items():
            e.uc.reg_write(getattr(e.R,'UC_X86_REG_'+k.upper()),v)
        e.before_instruction=self.observe
        e.uc.emu_start(ENTRY,END,timeout=90_000_000,count=100_000_000)
        e.before_instruction=None
        if e.failure or self.stop is None:raise ValueError(e.failure or 'Dispatch prefix budget exhausted')
        value=None;pointer_match=False
        if self.stop==SUCCESS:
            source=e.read(e.read(e.reg('rbp')+0xa8))
            pointer_match=source==self.parsed
            if not pointer_match or self.reader_calls!=1:raise ValueError('Parsed response not preserved for post-parse caller')
            value=self.text(e.read(source+32))
            if any(v.position!=len(v.data) for v in self.inputs.values()):raise ValueError('Reader did not consume independent wire')
        return dict(stop=hex(self.stop),errorMarker=self.error_marker,readerCalls=self.reader_calls,
                    postParsePointerPreserved=pointer_match,value=value,operations=self.operations,keyChecks=self.key_checks,
                    nativePlaintextSHA256=hashlib.sha256(self.decrypted).hexdigest() if self.decrypted is not None else None,
                    trace=dict(observedInstructionsOutsideLibrary=e.steps,instructionPathSHA256=self.digest.hexdigest(),
                               nativeLibraryBlocks=self.crypto.native.library_blocks,nativeLibraryBlockSHA256=self.crypto.native.library_digest.hexdigest(),
                               visits={hex(k):v for k,v in sorted(self.visits.items())}))


def recover(binary,metadata,records,libraries,root):
    row=next(r for r in records if r['index']==30677)
    if not any(m['registeredAddress']==hex(READER) and m['virtualSlot']==19 for m in row['methods']):raise ValueError('Response reader slot changed')
    pe=PE(binary);a=0x153A597FC;o=pe.offset(a,7)
    if binary[o:o+3]!=bytes.fromhex('488b1d') or a+7+int.from_bytes(binary[o+3:o+7],'little',signed=True)!=CLASS_CELL:
        raise ValueError('Response Clone self-class binding changed')
    a=0x153A59822;o=pe.offset(a,5)
    if binary[o]!=0xe8 or a+5+int.from_bytes(binary[o+1:o+5],'little',signed=True)!=0x153A594A0:
        raise ValueError('Response Clone CopyFrom identity changed')
    key_bytes=(root/'src/main/resources/keys/SigningKey.der').read_bytes();signer=private_numbers(key_bytes)
    public_bytes=(root/'src/main/resources/keys/game_keys/4_Pub.der').read_bytes()
    _,body,_=tlv(public_bytes);_,_,at=tlv(body);tag,v,_=tlv(body,at)
    if tag!=3 or v[0]!=0:raise ValueError('Unexpected client public-key wrapper')
    n,exponent=rsa_public_numbers(v[1:]);fixtures=[]
    for name,value,mode in (('local-nonzero','fixture-A','local'),('local-empty','','local'),
                            ('local-high-text','9007199254740993','local'),('bad-sign','fixture-A','bad-sign'),
                            ('stock-trust-reject','fixture-A','stock'),('missing-content','fixture-A','missing')):
        raw=value.encode('ascii');plain=b'\x72'+bytes([len(raw)])+raw
        signature=sign_sha256(signer,plain)
        if mode=='bad-sign':signature=bytes([signature[0]^1])+signature[1:]
        payload=dict(content=base64.b64encode(encrypt_seed([0,n,exponent],plain)).decode(),sign=base64.b64encode(signature).decode())
        if mode=='missing':payload.pop('content')
        e=Offline72(binary,libraries,records,True);runtime=None
        try:
            e.prepare();runtime=DispatchPrefix72(e,binary,metadata,plain,signer,mode!='stock')
            result=runtime.run_prefix(payload);accepted=mode=='local'
            if result['stop']!=hex(SUCCESS if accepted else REJECT):raise ValueError('Dispatch prefix acceptance changed')
            if accepted and result['value']!=value:raise ValueError('Independent tag14 value changed after decrypt/parse')
            if not accepted and (result['readerCalls'] or result['postParsePointerPreserved']):raise ValueError('Rejected input reached response parser')
            fixtures.append(dict(name=name,accepted=accepted,plainWire=plain.hex(),localTrust=mode!='stock',**result,passed=True))
        finally:
            if runtime:runtime.close()
            e.close()
    return dict(schemaVersion=1,inputSHA256=HASHES,fixtureCount=len(fixtures),fixtures=fixtures,
                results=[{k:v for k,v in f.items() if k!='trace'} for f in fixtures],entry=hex(ENTRY),successStop=hex(SUCCESS),rejectStop=hex(REJECT),
                sourceTypeIndex=30677,sourceField='DGHGFAELIDO',sourceOffset=32,sourceTag=14,selfClassCell=hex(CLASS_CELL),
                nativeReader=hex(READER),nativeByteArrayMerge=hex(MERGE),semanticStatus='unknown',runtimeStatus='not-tested',wholeDispatchExecuted=False,
                signingKeySHA256=hashlib.sha256(key_bytes).hexdigest(),clientPublicKeySHA256=hashlib.sha256(public_bytes).hexdigest(),
                models=['JSON parsing/lookup/storage from AuthSources72','native crypto runtime from ResponseCrypto72',
                        'declared closure/stream/response allocation and reader slot19 metadata','bounded ASCII wire helper and UTF-16 storage',
                        'local success: explicit signed server public-key/root input for operation103 only'],
                outsideScope=['HTTP transport','stock-trust successful server response','remaining RegionInfo fields','post-parse dispatch/second coroutine',
                              'business name for tag14/tag965','actual SDK/client'])


def validate_summary(report):
    errors=[]
    expected=dict(inputSHA256=HASHES,fixtureCount=6,entry=hex(ENTRY),successStop=hex(SUCCESS),rejectStop=hex(REJECT),
                  sourceTypeIndex=30677,sourceField='DGHGFAELIDO',sourceOffset=32,sourceTag=14,selfClassCell=hex(CLASS_CELL),
                  nativeReader=hex(READER),nativeByteArrayMerge=hex(MERGE),semanticStatus='unknown',
                  runtimeStatus='not-tested',wholeDispatchExecuted=False)
    if any(report.get(k)!=v for k,v in expected.items()):errors.append('Dispatch prefix scope/type binding mismatch')
    rows=report.get('results',[])
    cases=[('local-nonzero',True),('local-empty',True),('local-high-text',True),('bad-sign',False),('stock-trust-reject',False),('missing-content',False)]
    if [(r.get('name'),r.get('accepted')) for r in rows]!=cases:errors.append('Dispatch prefix cases mismatch')
    for row in rows:
        accepted=row.get('accepted')
        if (row.get('passed') is not True or row.get('stop')!=hex(SUCCESS if accepted else REJECT)
                or row.get('readerCalls')!=int(accepted) or row.get('postParsePointerPreserved')!=accepted):
            errors.append('Dispatch prefix acceptance/result mismatch')
        if row.get('localTrust')!=(row.get('name')!='stock-trust-reject'):errors.append('Dispatch prefix trust input claim mismatch')
        checks=row.get('keyChecks',[])
        if any(c.get('oracle') is not True or c.get('native') is not True for c in checks):errors.append('Dispatch prefix key verification mismatch')
        if row.get('name')!='missing-content' and [c.get('operation') for c in checks]!=[101,103]:errors.append('Dispatch prefix key checks incomplete')
        if accepted:
            value=row.get('value')
            if not isinstance(value,str):
                errors.append('Dispatch prefix accepted value is not a string');continue
            raw=value.encode('ascii');plain=b'\x72'+bytes([len(raw)])+raw
            if row.get('plainWire')!=plain.hex() or row.get('nativePlaintextSHA256')!=hashlib.sha256(plain).hexdigest():
                errors.append('Dispatch prefix independent plaintext/wire mismatch')
        elif row.get('value') is not None or not row.get('errorMarker'):
            errors.append('Dispatch prefix rejection did not preserve failure boundary')
    return errors


def main():
    a=parse_args();b,m,s=[read_verified(a.client/name,sha) for name,sha in HASHES.items()]
    records,_=extract(b,m,startup=s,structures=True);root=Path(__file__).resolve().parents[1]
    report=recover(b,m,records,a.libraries,root)
    errors=validate_summary(report)
    if errors:raise ValueError(errors)
    for path,value in ((a.output,report),(a.summary_output,{k:v for k,v in report.items() if k!='fixtures'})):
        path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(value,indent=2)+'\n','utf8')
    print(json.dumps(dict(fixtures=report['fixtureCount'],semanticStatus='unknown')))


if __name__=='__main__':main()
