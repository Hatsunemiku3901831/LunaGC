"""Execute the complete CN72 seed-string helper with declared runtime inputs.

The SDK initialization flag is a controlled branch input, not a live-client
initialization result. Native key verification is never replaced or forced.
"""
import argparse
import base64
from collections import Counter
import hashlib
import json
import math
from pathlib import Path
import struct
try:
    from .extract_client72 import HASHES, read_verified, extract
    from .extract_client72_literals import Literals72
    from .client72_emulator import Offline72, GLOBAL
    from .client72_session_runtime import SessionRuntime72
    from .client72_seed_crypto import NativeCrypto72, verify_key_signature, check_seed_cipher
except ImportError:
    from extract_client72 import HASHES, read_verified, extract
    from extract_client72_literals import Literals72
    from client72_emulator import Offline72, GLOBAL
    from client72_session_runtime import SessionRuntime72
    from client72_seed_crypto import NativeCrypto72, verify_key_signature, check_seed_cipher

ENTRY=0x15121DB40
CHECKPOINTS=(0x152F1B4D0,0x140571EB0,0x14078BAB0,0x140574A9A,
             0x1406595F0,0x140659AC0,0x15A4E3AB2,0x159BC3080,0x159BC34C0)


class SeedString72:
    def __init__(self,e,binary,metadata,sample,initialized,entropy_variant=0,tamper=None):
        self.e=e;self.session=SessionRuntime72(e);self.native=NativeCrypto72(e,entropy_variant)
        self.session.next_double_value=sample;e.write(GLOBAL+0x255b0,self.session.random)
        self.tamper=tamper;self.checkpoints=Counter();self.digest=hashlib.sha256()
        self.der=self.signature=self.root=self.plain=self.padded=None
        self.signature_result=None;self.ioctl_return=None
        for address in (ENTRY,0x152F1B4D0,0x159BC3080,0x159BC34C0):e.allow(address)
        e.allowed.extend(((0x148A22550,0x148A22555),(0x148A22560,0x148A22565)))
        # Marshal.Copy reads element width at klass+b8; the array allocator
        # receives a byte element class and supplies a separate byte[] class.
        self.array_class=e.alloc(512);e.write(self.array_class+0xb8,1,'H')
        e.models[0x140514EE0]=self.allocate_byte_array
        e.write(0x145B74508,self.session.array(0))
        empty=e.alloc(24);e.write(GLOBAL,empty);e.write(0x145BC9108,empty)
        tls=e.alloc(0x2000);slots=e.alloc(8);self.allocator=e.alloc();self.string_class=e.alloc(512)
        e.write(0x613000058,slots);e.write(slots,tls);e.write(tls+0x19d0,self.allocator)
        e.write(0x145EEF080,0,'I');e.write(0x145BC9798,self.string_class)
        e.models[0x140563CD0]=self.allocate_string
        alphabet='ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/='
        table=e.alloc(32+len(alphabet)*2);e.write(table+24,len(alphabet))
        e.uc.mem_write(table+32,alphabet.encode('utf-16-le'));e.write(GLOBAL+0x4e20,table)
        e.write(0x145DCAEA8,int(initialized),'B')
        self.literals=Literals72(binary,metadata)
        self.literal_rows={int(row['cell'],16):row for row in self.literals.group(self.literals.global_group)}
        self.loaded={};self.hook=e.uc.hook_add(e.U.UC_HOOK_MEM_READ,self.load_literal)

    def allocate_byte_array(self):
        e=self.e
        if e.reg('rcx')!=e.read(0x145886090):raise ValueError('Seed helper requested non-byte array')
        p=self.session.allocate_array();e.write(p,self.array_class);return p

    def allocate_string(self):
        e=self.e
        if e.reg('rcx')!=self.allocator or e.reg('r8')!=self.string_class:
            raise ValueError('Base64 string allocation contract changed')
        p=e.alloc(e.reg('rdx'));e.write(p,self.string_class);return p

    def load_literal(self,uc,access,address,size,value,data):
        if address in self.literal_rows and address not in self.loaded:
            e=self.e;row=self.literal_rows[address];raw=self.literals.decode(row['index']).encode('utf-16-le')
            p=e.alloc(22+len(raw));e.write(p+16,len(raw)//2,'I');e.uc.mem_write(p+20,raw);e.write(address,p)
            self.loaded[address]=row['index']

    def observe(self,address):
        e=self.e;self.digest.update(struct.pack('<Q',address))
        if address in CHECKPOINTS:self.checkpoints[address]+=1
        if address==0x14078BAB0:
            if self.tamper:
                # Deliberately alter one external verifier input for rejection
                # cases. The verifier's instructions and return stay untouched.
                p=e.reg('r8') if self.tamper=='signature' else e.reg('rcx')+32
                e.uc.mem_write(p,bytes([e.read(p,'B')^1]))
            self.der=bytes(e.uc.mem_read(e.reg('rcx'),e.reg('edx')))
            self.signature=bytes(e.uc.mem_read(e.reg('r8'),e.reg('r9d')))
            self.root=bytes(e.uc.mem_read(e.read(e.reg('rsp')+0x28),0x1ac)).split(b'\0')[0]
        elif address==0x140574A9A:self.signature_result=e.reg('al')
        elif address==0x1406595F0:self.plain=bytes(e.uc.mem_read(e.reg('rdx'),e.reg('ecx')))
        elif address==0x140659AC0:self.padded=bytes(e.uc.mem_read(e.reg('r14'),e.reg('ebx')))
        elif address==0x15A4E3AB2:self.ioctl_return=e.reg('eax')

    def run(self,sample,initialized,tamper):
        e=self.e;e.before_instruction=self.observe
        result=self.session.call(ENTRY,e.alloc(1024))
        return self.finish(result,sample,initialized,tamper)

    def finish(self,result,sample,initialized,tamper):
        """Check a returned helper value, also reusable after the outer constructor."""
        e=self.e
        text=bytes(e.uc.mem_read(result+20,e.read(result+16,'I')*2)).decode('utf-16-le')
        trace=dict(instructions=e.steps,nativePathSHA256=self.digest.hexdigest(),
                   checkpoints={hex(k):v for k,v in sorted(self.checkpoints.items())},
                   models=dict(sorted(Counter(c['address'] for c in e.calls).items())))
        e.before_instruction=None
        expected_seed=int(sample*(1<<64))
        if self.session.call(0x14CB58170,0x14587D740)!=expected_seed:
            raise ValueError('Encrypted-seed helper did not preserve its generated seed')
        expected_return=0xfffffffe if not initialized else 0xfffffff9 if tamper else 0
        if self.ioctl_return!=expected_return:raise ValueError('Seed IOCTL result changed')
        oracle=None
        if initialized:
            valid=verify_key_signature(self.der,self.signature,self.root)
            if valid!=bool(self.signature_result) or valid==bool(tamper):
                raise ValueError('Native and independent key-signature decisions differ')
            if not tamper:
                if self.plain!=expected_seed.to_bytes(8,'big'):raise ValueError('Native seed endian/value changed')
                cipher=base64.b64decode(text,validate=True)
                if base64.b64encode(cipher).decode('ascii')!=text:raise ValueError('Native Base64 output differs')
                oracle=check_seed_cipher(self.der,self.plain,self.padded,cipher)
        if (not initialized or tamper) and (text or self.plain is not None or self.native.entropy_calls):
            raise ValueError('Rejected seed input unexpectedly reached encryption')
        return dict(randomDoubleHex=sample.hex(),seedHex=f'{expected_seed:016x}',initializedFixture=initialized,
                    tamperedInput=tamper,entropyVariant=self.native.entropy_variant,
                    ioctlReturn=struct.unpack('<i',struct.pack('<I',self.ioctl_return))[0],
                    signatureAccepted=bool(self.signature_result) if initialized else None,
                    base64Length=len(text),oracle=oracle,trace=trace,
                    entropyCalls=[dict(length=len(v),sha256=hashlib.sha256(v).hexdigest()) for v in self.native.entropy_calls],
                    keyEvidence=None if not initialized else dict(derSHA256=hashlib.sha256(self.der).hexdigest(),
                        signatureSHA256=hashlib.sha256(self.signature).hexdigest(),rootPemSHA256=hashlib.sha256(self.root).hexdigest()),
                    passed=True)

    def close(self):self.e.uc.hook_del(self.hook)


def recover(binary,metadata,records,libraries):
    by_index={r['index']:r for r in records}
    for owner,index,name,address in ((189,1746,'ToBase64String',0x159BC3080),
            (189,1749,'ConvertToBase64Array',0x159BC34C0),(23177,179013,'ODDKHCEJJCI',ENTRY)):
        rows=[m for m in by_index[owner]['methods'] if m['index']==index]
        if len(rows)!=1 or (rows[0]['name'],rows[0]['registeredAddress'])!=(name,hex(address)):
            raise ValueError('Seed helper declaration changed')
    fixtures=[]
    cases=[(0.5,False,0,None),(0.0,True,0,None),(2.0**-53,True,0,None),
           (0.5,True,0,None),(math.nextafter(1.0,0.0),True,0,None),
           (0.5,True,37,None),(0.5,True,0,'signature'),(0.5,True,0,'public-key')]
    for sample,initialized,entropy_variant,tamper in cases:
        e=Offline72(binary,libraries,records,True);runtime=None
        try:
            e.prepare();runtime=SeedString72(e,binary,metadata,sample,initialized,entropy_variant,tamper)
            fixtures.append(runtime.run(sample,initialized,tamper))
        finally:
            if runtime:runtime.close()
            e.close()
    if fixtures[3]['oracle']['cipherSHA256']==fixtures[5]['oracle']['cipherSHA256']:
        raise ValueError('Changing entropy did not change ciphertext')
    return dict(schemaVersion=1,inputSHA256=HASHES,entry=hex(ENTRY),fixtureCount=len(fixtures),fixtures=fixtures,
                completion='native-seed-helper-return-with-explicit-runtime-inputs',runtimeStatus='not-tested',
                seedStorage='0x14587d740',initializationFlagFixture='0x145dcaea8',
                keyVerification=dict(entry='0x14078bab0',result='0x140574a9a',algorithm='RSA PKCS1 v1.5 SHA256',
                                     forcedSuccess=False,independentOracle='Python integer RSA and full DigestInfo/padding equality'),
                encryption=dict(entry='0x1406595f0',paddingResult='0x140659ac0',algorithm='RSA PKCS1 v1.5',
                                plainBytes=8,byteOrder='big',cipherBytes=256,base64Characters=344),
                requestBinding=dict(typeIndex=82491,objectOffset=0x90,tag=623,type='string',
                                    evidence='token-constructor-observations.json',scope='separately executed constructor binding'),
                results=[{k:v for k,v in f.items() if k not in ('trace',)} for f in fixtures],
                models=['CRT/Unity/GC allocations, copy/fill and tracked frees',
                        'single-thread TLS/critical sections/module handle/onexit storage',
                        'fixed process/thread IDs and time; explicit BCrypt entropy bytes',
                        'Random.NextDouble, integer permutation table and byte[]/string storage',
                        'empty runtime array/string, standard Convert Base64 alphabet, target literals',
                        'explicit SDK initialized/uninitialized flag; default zero hotpatch state'],
                outsideScope=['legitimate SDK initialization and live entropy','response decryption/signature verification',
                              'whole Token constructor with this helper in one execution','network/IFix/new Shell',
                              'patch installation and real-client acceptance'])


def main():
    a=parse_args();b,m,s=[read_verified(a.client/name,sha) for name,sha in HASHES.items()]
    records,_=extract(b,m,startup=s,structures=True)
    report=recover(b,m,records,a.libraries)
    for path,value in ((a.output,report),(a.summary_output,{k:v for k,v in report.items() if k!='fixtures'})):
        path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(value,indent=2)+'\n','utf-8')
    print(json.dumps(dict(fixtures=report['fixtureCount'],completion=report['completion'])))


def parse_args():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--client',required=True,type=Path);p.add_argument('--output',required=True,type=Path)
    p.add_argument('--summary-output',required=True,type=Path)
    p.add_argument('--libraries',type=Path,default=Path('local/migration72/python'));a=p.parse_args()
    for path in (a.output,a.summary_output):
        if path.resolve().is_relative_to(a.client.resolve()):p.error('Output must be outside client')
    return a


if __name__=='__main__':main()
