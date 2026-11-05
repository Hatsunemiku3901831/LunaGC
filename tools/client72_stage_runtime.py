"""Explicit clock/array inputs for the native login-stage timing recorder."""
import struct
try:
    from .client72_emulator import GLOBAL
except ImportError:
    from client72_emulator import GLOBAL

STAGE = 0x14C8AE9A0


class StageRuntime72:
    def __init__(self, engine, enabled=True, length=16, clock=123.125, present=True):
        self.e = e = engine
        e.synthetic_type(0x1458AD418)
        self.array = e.alloc(32 + length * 4); self.length = length
        e.write(self.array + 24, length, 'I')
        self.sentinel = 0xC61A5200
        for i in range(length): e.write(self.array + 32 + i * 4, self.sentinel, 'I')
        e.write(GLOBAL + 0x191F8, self.array if present else 0)
        e.write(0x145878E41, int(enabled), 'B')
        self.clock, self.context, self.clock_calls = clock, e.alloc(), []
        self.writes = []
        e.models[0x141705750] = self.clock_context
        e.models[0x1417063F0] = self.clock_value
        e.allow(STAGE); e.allow_unwind(0x141447A90)
        self.hook = e.uc.hook_add(e.U.UC_HOOK_MEM_WRITE, self.observe_write,
                                 begin=self.array, end=self.array + 31 + length * 4)

    def clock_context(self):
        self.clock_calls.append('context')
        return self.context

    def clock_value(self):
        if self.e.reg('rcx') != self.context: raise ValueError('Native clock context argument mismatch')
        self.clock_calls.append('double-value')
        self.e.uc.reg_write(self.e.R.UC_X86_REG_XMM0, int.from_bytes(struct.pack('<d', self.clock), 'little'))
        return 0

    def observe_write(self, uc, access, address, size, value, _):
        self.writes.append(dict(index=(address - self.array - 32) // 4, size=size, bits=value & 0xFFFFFFFF,
                                source=hex(self.e.reg('rip'))))

    def snapshot(self):
        return [self.e.read(self.array + 32 + i * 4, 'I') for i in range(self.length)]

    def close(self):
        self.e.uc.hook_del(self.hook)
