"""Explicit managed storage fixtures for bounded CN 7.2 session code.

The 16x256 permutation table and Random.Next are controlled runtime inputs.
The client's integer encoding, decoding and MT64 methods execute natively.
This module does not model RSA, signature verification, Shell or IFix success.
"""
from collections import Counter
import hashlib
import math
import struct
try:
    from .client72_emulator import GLOBAL, OBJECT, STACK, END
except ImportError:
    from client72_emulator import GLOBAL, OBJECT, STACK, END


def permutation_table(multiplier=1, shift=0):
    if type(multiplier) is not int or not 0 < multiplier < 256 or not multiplier & 1:
        raise ValueError('Byte permutation requires an odd multiplier in 1..255')
    if type(shift) is not int or not 0 <= shift < 256:
        raise ValueError('Byte permutation shift out of range')
    forward = bytes((i * multiplier + shift) & 255 for i in range(256))
    inverse = bytearray(256)
    for i, value in enumerate(forward):
        inverse[value] = i
    # Native codec encodes with rows 0..3 and decodes with rows 8..11.
    return forward * 8 + bytes(inverse) * 8


def fragment(e, entry, stops, registers):
    """Run a declared basic-block boundary with explicit live-in registers."""
    e.uc.context_restore(e.context)
    e.calls, e.failure, e.steps = [], None, 0
    for name, value in registers.items():
        e.uc.reg_write(getattr(e.R, 'UC_X86_REG_' + name.upper()), value)
    counts, digest, reached = Counter(), hashlib.sha256(), []
    # Stops are observed before executing their instruction, including throw helpers.
    for stop in stops:
        e.allowed.append((stop, stop + 1))
    def observe(address):
        counts[address] += 1
        digest.update(struct.pack('<Q', address))
        if address in stops:
            reached.append(address)
            e.uc.emu_stop()
    e.before_instruction = observe
    try:
        e.uc.emu_start(entry, END, timeout=20_000_000, count=20_000_000)
    finally:
        e.before_instruction = None
    if e.failure or len(reached) != 1:
        raise ValueError(e.failure or 'Declared fragment did not reach a stop')
    return dict(entry=hex(entry), stop=hex(reached[0]), completion='bounded-fragment',
                liveIn={k:hex(v) for k,v in registers.items()}, instructions=e.steps,
                nativePathSHA256=digest.hexdigest(),
                executedAddresses={hex(k):v for k,v in sorted(counts.items())},
                models=[c['address'] for c in e.calls])


class SessionRuntime72:
    NATIVE = (0x14CB58BD0, 0x14CB58170, 0x159B54250, 0x14B76FEB0,
              0x14B770030, 0x14B770520, 0x14B770650, 0x14B770850)

    def __init__(self, engine, multiplier=1, shift=0, random_value=0x12345678):
        self.e = e = engine
        if type(random_value) is not int or not 0 <= random_value < 0x80000000:
            raise ValueError('Random.Next fixture outside nonnegative int32')
        for address in self.NATIVE:
            e.allow(address)
        self.random = e.alloc()
        random_class = e.alloc(512)
        e.write(self.random, random_class)
        e.write(random_class + 0xf8, e.model_stub(lambda: random_value))
        self.next_double_value = 0.0
        def next_double():
            if e.reg('rcx') != self.random:
                raise ValueError('Unexpected Random.NextDouble receiver')
            e.uc.reg_write(e.R.UC_X86_REG_XMM0, int.from_bytes(struct.pack('<d', self.next_double_value), 'little'))
            return 0
        e.write(random_class + 0x110, e.model_stub(next_double))
        # 149A75140 obtains the thread-local System.Random. TLS construction is
        # replaced by this declared object; no session operation is stubbed.
        e.models[0x149A75140] = lambda: self.random
        table = self.array(4096)
        bounds = e.alloc()
        e.write(bounds, 16)
        e.write(bounds + 16, 256)
        e.write(table + 16, bounds)
        e.uc.mem_write(table + 32, permutation_table(multiplier, shift))
        e.write(GLOBAL + 0x204d0, table)
        for cell in (0x14588E4F8, 0x14589F4F8, 0x1458C94F0,
                     0x145887760, 0x145886090):
            e.synthetic_type(cell)
        self.object_class = e.read(0x1458C94F0)
        self.array_classes = {e.read(0x145887760):8, e.read(0x145886090):1}
        e.models[0x1405336C0] = self.allocate_object
        e.models[0x140514EE0] = self.allocate_array
        self.call(0x14B770850)  # Native .cctor sets N=312 and M=156.
        if (e.read(0x145880A44,'I'),e.read(0x145880A40,'I')) != (312,156):
            raise ValueError('Native MT64 dimensions changed')

    def generate_client_seed(self, sample):
        if type(sample) not in (int,float) or not math.isfinite(sample) or not 0 <= sample < 1:
            raise ValueError('Random.NextDouble fixture outside [0,1)')
        e=self.e
        methods=[m for m in e.records[280]['methods'] if m.get('virtualSlot')==8]
        if len(methods)!=1 or methods[0]['name']!='NextDouble' or methods[0]['parameterCount']:
            raise ValueError('Random.NextDouble virtual-slot declaration mismatch')
        if e.read(0x1422404F0,'d') != float(1<<64):
            raise ValueError('Native client-seed scale changed')
        self.next_double_value=float(sample)
        e.write(GLOBAL+0x255b0,self.random)
        e.allow(0x152F1B4D0)
        result=e.alloc(16)
        self.call(0x152F1B4D0,result)
        instructions=e.steps
        expected=int(float(sample)*(1<<64))
        if bytes(e.uc.mem_read(result,16))!=bytes(e.uc.mem_read(0x14587D740,16)):
            raise ValueError('Client seed return/global storage differ')
        actual=self.call(0x14CB58170,0x14587D740)
        if actual!=expected or self.call(0x14CB58170,result)!=expected:
            raise ValueError('Native client seed disagrees with independent double scaling')
        return dict(randomDoubleHex=float(sample).hex(),seedHex=f'{actual:016x}',
                    nativeEntry='0x152f1b4d0',savedAt='0x14587d740',instructions=instructions,passed=True)

    def array(self, length, width=1):
        if type(length) is not int or not 0 <= length <= 4096 or width not in (1,8):
            raise ValueError('Array allocation outside reviewed fixture bounds')
        pointer = self.e.alloc(32 + length * width)
        self.e.write(pointer + 24, length)
        return pointer

    def allocate_array(self):
        width = self.array_classes.get(self.e.reg('rcx'))
        if width is None:
            raise ValueError('Unreviewed array element class')
        return self.array(self.e.reg('edx'), width)

    def allocate_object(self):
        if self.e.reg('rcx') != self.object_class:
            raise ValueError('Unreviewed managed object class')
        pointer = self.e.alloc(128)
        self.e.write(pointer, self.object_class)
        return pointer

    def call(self, address, receiver=OBJECT, argument=0):
        e = self.e
        e.uc.context_restore(e.context)
        e.calls, e.failure, e.steps = [], None, 0
        e.run(address, receiver, argument)
        return e.reg('rax')

    def encode_integer(self, pointer, value):
        if type(value) is not int or not 0 <= value < 1 << 64:
            raise ValueError('Integer fixture outside uint64')
        self.e.uc.mem_write(pointer, bytes(16))
        self.call(0x14CB58BD0, pointer, value)
        if self.call(0x14CB58170, pointer) != value:
            raise ValueError('Native integer codec failed its supplied value')

    def seed_suffix(self, decrypted, client_seed):
        e = self.e
        if not isinstance(decrypted, bytes) or len(decrypted) > 4096:
            raise ValueError('Invalid decrypted-input fixture')
        self.encode_integer(0x14587D740, client_seed)
        pointer = self.array(len(decrypted))
        if decrypted:
            e.uc.mem_write(pointer + 32, decrypted)
        e.write(GLOBAL + 0x19580, 0)
        e.write(0x145878FC0, 1, 'B')
        frame = STACK + 0x100000
        e.write(frame + 0x100, OBJECT)
        e.write(frame + 0x108, OBJECT + 0x1000)
        result = fragment(e, 0x15A4E064F, {0x15A4E0D4B, 0x159CA4C40, 0x159CA4EA0},
                          dict(rsp=frame-0x80, rbp=frame, rcx=pointer))
        key = e.read(GLOBAL + 0x19580)
        if len(decrypted) < 8:
            expected = 0x159CA4C40 if not decrypted else 0x159CA4EA0
            if result['stop'] != hex(expected) or key or e.read(0x145878FC0,'B') != 1:
                raise ValueError('Short seed did not reject before key installation')
            return result, None
        if result['stop'] != '0x15a4e0d4b' or not key or e.read(key+24) != 4096:
            raise ValueError('Seed suffix did not generate the complete key')
        if e.read(0x145878FC0,'B') != 0:
            raise ValueError('Native key-switch flag store missing')
        expected_seed = int.from_bytes(decrypted[:8], 'big') ^ client_seed
        if self.call(0x14CB58170, frame + 0x70) != expected_seed:
            raise ValueError('Native byte order/XOR differs from independent expression')
        return result, bytes(e.uc.mem_read(key + 32, 4096))
