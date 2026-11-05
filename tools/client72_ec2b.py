"""Pinned native Ec2b decoder, with native table/RNG initialization.

CRT storage is inherited from NativeCrypto72; managed byte[] allocation and
engine-selector5 storage are explicit fixtures. No cipher result is modeled.
"""
import hashlib
import struct
try:
    from .client72_emulator import STACK, END
    from .client72_seed_crypto import NativeCrypto72
    from .client72_session_runtime import fragment
except ImportError:
    from client72_emulator import STACK, END
    from client72_seed_crypto import NativeCrypto72
    from client72_session_runtime import fragment

RNG_STATE = 0x145EE9E58
LIVE_RNG_SHA256 = '65357cbadcb1fa79ad0f588baf140f0378176037228b8e5f2d75cb4eb07b823f'
KEY_HASHES = {
    'dispatchSeed.bin':'2258b87182df6e8e679c15a86184ddaf8d4b5eda24cd81bf12e85bb8a9e00637',
    'dispatchKey.bin':'20f0d259406a01c02c55bfdc57f69c90c1257222d721a141ccbe55ccfaa77eb0',
}


def initial_rng_bytes():
    values = [103]
    for i in range(1,624):
        previous = values[-1]
        values.append((1812433253*(previous^(previous>>30))+i)&0xffffffff)
    return struct.pack('<1250I',624,*values,*([0]*624),0xffffffff)


class Ec2b72:
    def __init__(self,e,native=None):
        self.e = e
        self.native = native if native is not None else NativeCrypto72(e)
        self.gate = e.alloc(16)
        e.uc.mem_write(self.gate,b'\xc3')
        self.registration = self.call(0x1411267B0,{})
        if e.read(0x145744EB8)!=0x1408BAEE0:
            raise ValueError('Native Ec2b function registration changed')
        self.rng_initialization = self.call(0x1408AF2A0,{})
        initial = bytes(e.uc.mem_read(RNG_STATE,5000))
        if initial!=initial_rng_bytes() or hashlib.sha256(initial).hexdigest()!=LIVE_RNG_SHA256:
            raise ValueError('Native RNG initialization differs from independent and live evidence')
        self.byte_class = e.alloc(512)
        self.engine = e.alloc(512)
        e.write(self.engine+0x130,self.byte_class)
        e.write(0x1457A98B0+5*8,self.engine)
        e.models[0x1404BB0A0] = self.allocate_output
        self.output_allocations = []

    def call(self,entry,registers):
        e = self.e
        e.write(STACK+0x100008,self.gate)
        trace = fragment(e,entry,{self.gate},dict(rsp=STACK+0x100008,**registers))
        e.write(STACK+0x100008,END)
        return trace

    def array(self,data):
        e = self.e
        pointer = e.alloc(32+len(data))
        e.write(pointer,self.byte_class)
        e.write(pointer+24,len(data))
        if data:e.uc.mem_write(pointer+32,data)
        return pointer

    def allocate_output(self):
        e = self.e
        if e.reg('rcx')!=self.byte_class or e.reg('edx')!=4096:
            raise ValueError('Unexpected managed Ec2b output allocation')
        result = self.array(bytes(4096))
        self.output_allocations.append(result)
        return result

    def decode_raw(self,data,length=None,null=False):
        e = self.e
        source = e.alloc(max(1,len(data)))
        if data:e.uc.mem_write(source,data)
        output = e.alloc(4096)
        e.uc.mem_write(output,b'\xa5'*4096)
        trace = self.call(0x1411268D0,dict(rcx=0 if null else source,
                          rdx=(len(data) if length is None else length)&0xffffffff,r8=output))
        return trace,e.reg('al'),bytes(e.uc.mem_read(output,4096))

    def decode_managed(self,data):
        e = self.e
        source = 0 if data is None else self.array(data)
        trace = self.call(0x14146ADA0,dict(rcx=source))
        pointer = e.reg('rax')
        result = bytes(e.uc.mem_read(pointer+32,e.read(pointer+24))) if pointer else None
        return trace,pointer,result
