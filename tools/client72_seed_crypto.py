"""Offline Windows/CRT fixtures for the pinned client's native seed cryptography.

RSA, SHA-256, PKCS#1 padding and key-signature decisions execute in the image.
Only allocation, single-thread OS state, time and entropy are fixture inputs.
No Windows API is called on the host and no live client state is changed.
"""
import base64
import hashlib
import struct
try:
    from .extract_client72 import number
    from .client72_emulator import STACK
except ImportError:
    from extract_client72 import number
    from client72_emulator import STACK


class NativeCrypto72:
    def __init__(self,e,entropy_variant=0):
        self.e=e;self.allocations={};self.tls={};self.last_error=0
        self.onexit_callbacks=[];self.entropy_calls=[];self.entropy_variant=entropy_variant
        # Exact executable .text extent in the hash-pinned image. This includes
        # the native crypto library; managed/protected code uses Offline72 rules.
        e.pe.offset(0x140001000,0x21c1096,executable=True)
        e.allowed.append((0x140001000,0x1421c2096))
        e.ends[0x140869FE0]=0x140869FE5
        e.uc.mem_map(0x613000000,4096);e.write(0x613000010,STACK+0x1000)
        e.uc.reg_write(e.R.UC_X86_REG_GS_BASE,0x613000000)
        e.context=e.uc.context_save()
        e.models.update({0x141A2E390:self.allocate,0x141A2E260:self.allocate,
                         0x141C41728:self.allocate,0x141C3EE1C:self.free,
                         0x141A2E1F0:self.free,0x141FDF9C0:self.memset,
                         0x141FDF310:self.memcpy,0x141CC0838:self.onexit})
        e.write(0x145EEF928,0x100)  # Declared synthetic CRT heap handle.
        models={'HeapAlloc':self.heap_allocate,'HeapFree':self.heap_free,'HeapReAlloc':self.heap_reallocate,
                'TlsAlloc':self.tls_alloc,'TlsGetValue':self.tls_get,'TlsSetValue':self.tls_set,'TlsFree':self.tls_free,
                'InitializeCriticalSectionAndSpinCount':lambda:1,'InitializeCriticalSection':lambda:0,
                'EnterCriticalSection':lambda:0,'LeaveCriticalSection':lambda:0,'DeleteCriticalSection':lambda:0,
                'GetCurrentThreadId':lambda:1,'GetCurrentProcessId':lambda:42,
                'GetModuleHandleExW':self.module_handle,
                'GetSystemTimeAsFileTime':self.system_time,'QueryPerformanceCounter':self.counter,'BCryptGenRandom':self.entropy,
                'GetLastError':lambda:self.last_error,'SetLastError':self.set_error}
        self.imports=import_slots(e.pe)
        for name,callback in models.items():e.write(self.imports[name],e.model_stub(callback))

    def trace_library_blocks(self):
        """Keep real crypto execution; trace hot native code by basic block.

        Models, managed code and protected VM code retain instruction hooks.
        The instruction/time budgets and global forbidden-instruction hooks
        still cover the whole emulation. Counts below are blocks, not opcodes.
        """
        e=self.e;self.library_blocks=0;self.library_digest=hashlib.sha256()
        e.uc.hook_del(e.hooks.pop(0))
        e.hooks.extend((e.uc.hook_add(e.U.UC_HOOK_CODE,e._code,begin=1,end=0x14057ffff),
                        e.uc.hook_add(e.U.UC_HOOK_CODE,e._code,begin=0x140780000,end=0xffffffffffffffff)))
        def block(uc,address,size,data):
            self.library_blocks+=1;self.library_digest.update(struct.pack('<QI',address,size))
        e.hooks.append(e.uc.hook_add(e.U.UC_HOOK_BLOCK,block,begin=0x140580000,end=0x14077ffff))

    def allocate(self):
        n=self.e.reg('rcx');p=self.e.alloc(n);self.allocations[p]=n;return p

    def free(self):
        p=self.e.reg('rcx')
        if p:self.allocations.pop(p)
        return 0

    def memcpy(self):
        e=self.e;p=e.reg('rcx');e.uc.mem_write(p,bytes(e.uc.mem_read(e.reg('rdx'),e.reg('r8'))));return p

    def memset(self):
        e=self.e;p=e.reg('rcx');e.uc.mem_write(p,bytes([e.reg('dl')])*e.reg('r8'));return p

    def heap_allocate(self):
        e=self.e
        if e.reg('rcx')!=0x100 or e.reg('edx') not in (0,8):raise ValueError('HeapAlloc fixture contract')
        n=e.reg('r8');p=e.alloc(n);self.allocations[p]=n;return p

    def heap_free(self):
        e=self.e
        if e.reg('rcx')!=0x100 or e.reg('edx')!=0:raise ValueError('HeapFree fixture contract')
        self.allocations.pop(e.reg('r8'));return 1

    def heap_reallocate(self):
        e=self.e
        if e.reg('rcx')!=0x100 or e.reg('edx')!=0:raise ValueError('HeapReAlloc fixture contract')
        old=e.reg('r8');size=e.reg('r9');old_size=self.allocations.pop(old)
        p=e.alloc(size);e.uc.mem_write(p,bytes(e.uc.mem_read(old,min(size,old_size))))
        self.allocations[p]=size;return p

    def onexit(self):
        callback=self.e.reg('rcx');self.onexit_callbacks.append(callback);return callback

    def tls_alloc(self):
        index=len(self.tls)+1;self.tls[index]=0;return index

    def tls_get(self):return self.tls[self.e.reg('ecx')]

    def tls_set(self):self.tls[self.e.reg('ecx')]=self.e.reg('rdx');return 1

    def tls_free(self):self.tls.pop(self.e.reg('ecx'));return 1

    def set_error(self):self.last_error=self.e.reg('ecx');return 0

    def module_handle(self):
        e=self.e
        if e.reg('ecx')!=5 or not e.pe.base<=e.reg('rdx')<e.image_end:raise ValueError('Module-handle fixture contract')
        e.write(e.reg('r8'),e.pe.base);return 1

    def system_time(self):self.e.write(self.e.reg('rcx'),0x123456789abc);return 0

    def counter(self):self.e.write(self.e.reg('rcx'),0x123456789abc);return 1

    def entropy(self):
        e=self.e
        if e.reg('rcx')!=0 or e.reg('r9d')!=2:raise ValueError('BCryptGenRandom fixture contract')
        raw=bytes((i+17+len(self.entropy_calls)+self.entropy_variant)%256 for i in range(e.reg('r8d')))
        e.uc.mem_write(e.reg('rdx'),raw);self.entropy_calls.append(raw);return 0


def import_slots(pe):
    b=pe.data;descriptor=pe.offset(pe.base+number(b,number(b,0x3c)+24+112+8));result={}
    while b[descriptor:descriptor+20]!=bytes(20):
        names=number(b,descriptor);first=number(b,descriptor+16);index=0
        while True:
            raw=number(b,pe.offset(pe.base+(names or first)+index*8),'Q')
            if not raw:break
            if not raw>>63:
                off=pe.offset(pe.base+raw)+2;name=b[off:off+128].split(b'\0')[0].decode('ascii')
                result[name]=pe.base+first+index*8
            index+=1
        descriptor+=20
    return result


def rsa_public_numbers(der):
    """Parse the two unsigned INTEGERs in a PKCS#1 public-key DER input."""
    def item(offset,tag):
        if offset+2>len(der) or der[offset]!=tag:raise ValueError('Unexpected RSA DER tag')
        length=der[offset+1];offset+=2
        if length&128:
            width=length&127
            if not 1<=width<=4 or offset+width>len(der):raise ValueError('Invalid RSA DER length')
            length=int.from_bytes(der[offset:offset+width],'big');offset+=width
        end=offset+length
        if end>len(der):raise ValueError('Truncated RSA DER')
        return der[offset:end],offset,end
    _,start,end=item(0,0x30)
    if end!=len(der):raise ValueError('Trailing RSA DER')
    modulus,_,middle=item(start,2);exponent,_,last=item(middle,2)
    if last!=end or not modulus or not exponent or modulus[0]&128 or exponent[0]&128:
        raise ValueError('Invalid RSA public numbers')
    return int.from_bytes(modulus,'big'),int.from_bytes(exponent,'big')


def verify_key_signature(der,signature,root_pem):
    root_der=base64.b64decode(b''.join(root_pem.splitlines()[1:-1]),validate=True)
    modulus,exponent=rsa_public_numbers(root_der);width=(modulus.bit_length()+7)//8
    if len(signature)!=width or int.from_bytes(signature,'big')>=modulus:return False
    digest_info=bytes.fromhex('3031300d060960864801650304020105000420')+hashlib.sha256(der).digest()
    expected=b'\0\1'+b'\xff'*(width-len(digest_info)-3)+b'\0'+digest_info
    actual=pow(int.from_bytes(signature,'big'),exponent,modulus).to_bytes(width,'big')
    return actual==expected


def check_seed_cipher(der,plain,padded,cipher):
    """Independent PKCS#1 v1.5 structure and Python-integer RSA calculation."""
    modulus,exponent=rsa_public_numbers(der);width=(modulus.bit_length()+7)//8
    split=width-len(plain)-1
    if (len(padded)!=width or padded[:2]!=b'\0\2' or split<10 or
            0 in padded[2:split] or padded[split:]!=b'\0'+plain):
        raise ValueError('Native padding does not encode the supplied seed')
    expected=pow(int.from_bytes(padded,'big'),exponent,modulus).to_bytes(width,'big')
    if cipher!=expected:raise ValueError('Native ciphertext differs from independent RSA')
    return dict(plainLength=len(plain),paddingLength=split-2,cipherLength=len(cipher),
                cipherSHA256=hashlib.sha256(cipher).hexdigest(),passed=True)
