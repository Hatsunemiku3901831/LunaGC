"""Native Token-response crypto and key installation with explicit fixtures."""
from collections import Counter
import hashlib
import struct
try:
    from .client72_emulator import OBJECT, GLOBAL, STACK, END
    from .trace_client72_responses import Reader72
    from .trace_client72_seed_crypto import SeedString72
    from .client72_seed_crypto import verify_key_signature
    from .client72_rsa_fixture import public_der,public_pem,sign_sha256
except ImportError:
    from client72_emulator import OBJECT, GLOBAL, STACK, END
    from trace_client72_responses import Reader72
    from trace_client72_seed_crypto import SeedString72
    from client72_seed_crypto import verify_key_signature
    from client72_rsa_fixture import public_der,public_pem,sign_sha256

ENTRY=0x15A4DE707
DECRYPT=0x15A4DFB35
VERIFY=0x15A4E054F
SUCCESS=0x15A4E0D4B
REJECT=0x15A4E0645
SHORT=0x159CA4EA0


class CryptoResponseReader72(Reader72):
    def blob(self,kind):
        self.sync();data=self.input.blob();self.position()
        if kind!='string':raise ValueError('Crypto fixture contains a non-string payload')
        text=data.decode('ascii');raw=text.encode('utf-16-le');e=self.e
        pointer=e.alloc(22+len(raw));e.write(pointer+16,len(text),'I');e.uc.mem_write(pointer+20,raw)
        self.values[pointer]=dict(kind=kind,hex=data.hex());return pointer


class ResponseCrypto72(SeedString72):
    def __init__(self,e,binary,metadata,client_seed,signer=None,tamper=None,discover=False):
        super().__init__(e,binary,metadata,0.0,True)
        self.native.trace_library_blocks();self.client_seed=client_seed
        self.signer=signer;self.case_tamper=tamper;self.discover=discover
        self.local_public=public_der(signer) if signer else None
        self.local_root=public_pem(self.local_public) if signer else None
        self.sha_class=e.synthetic_type(0x145887560);self.uint_class=e.synthetic_type(0x1458869F0)
        e.synthetic_type(0x1458860E8)
        self.uint_array_class=e.alloc(512);e.write(self.uint_array_class+0xb8,4,'H')
        e.write(self.uint_array_class+0xc8,2,'B');e.write(self.array_class+0xc8,2,'B')
        for address in (0x159BC3B10,0x159BC3E40,0x159BB9650,0x159BD49F0,0x159AF4510,0x159C68C70):e.allow(address)
        methods={m['name']:m for m in e.records[1031]['methods']}
        if e.records[1031]['name']!='SHA256Managed':raise ValueError('Native SHA class identity changed')
        for method in methods.values():e.allow(int(method['registeredAddress'],16))
        for slot,name,address in ((16,'Initialize',0x159AA9DC0),(17,'HashCore',0x159AA9E10),(18,'HashFinal',0x159AA9FA0)):
            if methods[name]['virtualSlot']!=slot or methods[name]['registeredAddress']!=hex(address):
                raise ValueError('SHA256Managed virtual declaration changed')
            e.write(self.sha_class+0xd0+slot*8,address)
        dispose=next(m for m in e.records[996]['methods'] if m['virtualSlot']==15)
        if dispose['name']!='Dispose' or dispose['registeredAddress']!='0x159bd49f0':raise ValueError('Hash disposal slot changed')
        e.write(self.sha_class+0x148,0x159BD49F0)
        e.models.update({0x1405336C0:self.allocate_hash,0x140514EE0:self.allocate_response_array,
                         0x140533590:self.clone_bytes,0x14051B7B0:self.byte_array_cast,
                         0x14055ECF0:self.suppress_finalize})
        # Selected 64-word target metadata initializer, supplied as runtime
        # storage. The complete metadata/field-handle initializer is not run.
        prefix=bytes.fromhex('982f8a4291443771cffbc0b5a5dbb5e9')
        offset=metadata.find(prefix)
        if offset<0 or metadata.find(prefix,offset+1)!=-1:raise ValueError('Ambiguous SHA constant initializer')
        constants=metadata[offset:offset+256]
        if len(constants)!=256:raise ValueError('Truncated SHA initializer')
        self.constant_hash=hashlib.sha256(constants).hexdigest()
        table=e.alloc(288);e.write(table,self.uint_array_class);e.write(table+24,64)
        e.uc.mem_write(table+32,constants);e.write(GLOBAL+0x4280,table)
        self.frame=STACK+0x100000;self.manager=e.alloc(1024)
        self.session.encode_integer(0x14587D740,client_seed)
        e.write(0x145878FC0,1,'B');e.write(GLOBAL+0x19580,0)
        self.operations={};self.key_checks=[];self.original_keys={};self.decrypted=None;self.hash_result=None
        self.path_digest=hashlib.sha256();self.visits=Counter();self.stop=None;self.operation=None

    def allocate_hash(self):
        if self.e.reg('rcx')!=self.sha_class:return self.session.allocate_object()
        p=self.e.alloc(128);self.e.write(p,self.sha_class);return p

    def allocate_response_array(self):
        e=self.e
        if e.reg('rcx')==e.read(0x145887760):return self.session.allocate_array()
        if e.reg('rcx')!=self.uint_class:return self.allocate_byte_array()
        n=e.reg('edx');p=e.alloc(32+4*n);e.write(p,self.uint_array_class);e.write(p+24,n);return p

    def clone_bytes(self):
        e=self.e;p=e.reg('rcx')
        if e.read(p)!=self.array_class:raise ValueError('Hash clone is not byte[]')
        n=e.read(p+24);q=e.alloc(32+n);e.uc.mem_write(q,bytes(e.uc.mem_read(p,32+n)));return q

    def byte_array_cast(self):
        e=self.e
        if e.reg('rcx')!=e.read(0x145886090) or e.reg('rdx')!=self.array_class:
            raise ValueError('Hash cast is not the declared byte[] class')
        return 1

    def suppress_finalize(self):
        e=self.e
        if e.read(e.reg('rcx'))!=self.sha_class or e.reg('rdx')!=0:raise ValueError('Unexpected finalizer operation')
        return 0

    def observe_response(self,address):
        e=self.e;self.path_digest.update(struct.pack('<Q',address))
        if address in (ENTRY,DECRYPT,VERIFY,SUCCESS,REJECT,SHORT,0x14078BAB0,0x140574A9A):self.visits[address]+=1
        if address in (DECRYPT,VERIFY):
            self.operation=101 if address==DECRYPT else 103;args=e.reg('rdx')
            if (e.reg('ecx'),e.reg('r8d'))!=(self.operation,64):raise ValueError('Response IOCTL ABI changed')
            raw=bytes(e.uc.mem_read(e.read(args+32),e.read(args+40,'I')))
            signature=bytes(e.uc.mem_read(e.read(args+48),e.read(args+56,'I')))
            der=b'\x30\x82'+raw[:int.from_bytes(raw[:2],'big')+2]
            self.original_keys[self.operation]=(der,signature)
            if self.discover and address==DECRYPT:self.stop=address;e.uc.emu_stop();return
            if self.signer:
                # Coherent declared trust-input fixture. No verification code,
                # return register, or result flag is replaced.
                if address==VERIFY:
                    der=self.local_public;e.uc.mem_write(e.read(args+32),der[2:]+bytes(4))
                signature=sign_sha256(self.signer,der)
                if self.case_tamper=='key-signature' and address==DECRYPT:signature=bytes([signature[0]^1])+signature[1:]
                e.uc.mem_write(e.read(args+48),signature)
        elif address==0x14078BAB0:
            root=e.read(e.reg('rsp')+0x28)
            if self.signer:e.uc.mem_write(root,self.local_root+b'\0')
            der=bytes(e.uc.mem_read(e.reg('rcx'),e.reg('edx')))
            signature=bytes(e.uc.mem_read(e.reg('r8'),e.reg('r9d')))
            pem=bytes(e.uc.mem_read(root,0x1ac)).split(b'\0')[0]
            self.key_checks.append(dict(operation=self.operation,oracle=verify_key_signature(der,signature,pem),
                                        derSHA256=hashlib.sha256(der).hexdigest(),rootSHA256=hashlib.sha256(pem).hexdigest()))
        elif address==0x140574A9A:
            self.key_checks[-1]['native']=bool(e.reg('al'))
            if self.key_checks[-1]['native']!=self.key_checks[-1]['oracle']:raise ValueError('Native key signature differs from independent RSA')
        elif address==0x15A4DFB3A:
            self.operations[101]=signed32(e.reg('eax'));p=e.read(self.frame+0xc0);n=e.read(self.frame+0xc8,'I')
            self.decrypted=bytes(e.uc.mem_read(p,n)) if p else b''
        elif address==0x15A4E039B:
            p=e.reg('rax');self.hash_result=bytes(e.uc.mem_read(p+32,e.read(p+24)))
            if self.hash_result!=hashlib.sha256(self.decrypted).digest():raise ValueError('Native SHA256 differs from hashlib')
        elif address==0x15A4E0554:
            self.operations[103]=signed32(e.reg('eax'));self.signature_result=e.read(self.frame+0x30)
        if address in (SUCCESS,REJECT,SHORT,0x159CA4C40):self.stop=address;e.uc.emu_stop()

    def run_response(self):
        return self.run_native(ENTRY,dict(rsp=self.frame-0x80,rbp=self.frame,r13=self.manager))

    def run_native(self,entry,registers):
        e=self.e;e.uc.context_restore(e.context);e.calls=[];e.failure=None;e.steps=0
        e.write(self.frame+0x100,OBJECT)
        for name,value in registers.items():
            e.uc.reg_write(getattr(e.R,'UC_X86_REG_'+name.upper()),value)
        for stop in (SHORT,0x159CA4C40):e.allowed.append((stop,stop+1))
        e.before_instruction=self.observe_response
        e.uc.emu_start(entry,END,timeout=90_000_000,count=100_000_000)
        e.before_instruction=None
        if e.failure or self.stop is None:raise ValueError(e.failure or 'Response fragment budget exhausted')
        p=e.read(GLOBAL+0x19580);key=bytes(e.uc.mem_read(p+32,e.read(p+24))) if p else None
        trace=dict(entry=hex(entry),stop=hex(self.stop),observedInstructionsOutsideLibrary=e.steps,
                   instructionPathSHA256=self.path_digest.hexdigest(),nativeLibraryBlocks=self.native.library_blocks,
                   nativeLibraryBlockSHA256=self.native.library_digest.hexdigest(),
                   blockTraceRange=['0x140580000','0x140780000'],visits={hex(k):v for k,v in sorted(self.visits.items())},
                   models=dict(sorted(Counter(c['address'] for c in e.calls).items())))
        return trace,key


def signed32(value):return struct.unpack('<i',struct.pack('<I',value))[0]
