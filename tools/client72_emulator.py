"""Shared bounded, offline CPU harness. It never starts a Windows client process."""
import struct
try:
    from .extract_client72 import PE, number
    from .trace_client72_token import load_emulator, varint
except ImportError:
    from extract_client72 import PE, number
    from trace_client72_token import load_emulator, varint

OBJECT, STREAM, BUFFER, STACK, END = 0x610000000, 0x610001000, 0x610004000, 0x600000000, 0x620000000
GLOBAL, HEAP, CAPACITY = 0x611000000, 0x612000000, 32768


class Offline72:
    def __init__(self, binary, library_directory, records, trace_instructions=True):
        self.U, self.R = load_emulator(library_directory)
        U = self.U; pe = PE(binary); self.pe = pe
        image_size = number(binary, number(binary, 0x3c) + 24 + 56)
        if not 0 < image_size < 1 << 30:
            raise ValueError('Unexpected image size')
        self.image_end = pe.base + image_size
        self.uc = U.Uc(U.UC_ARCH_X86, U.UC_MODE_64)
        self.uc.mem_map(pe.base, (image_size + 4095) & ~4095)
        self.uc.mem_write(pe.base, binary[:number(binary, number(binary, 0x3c) + 24 + 60)])
        for va, size, raw, _ in pe.sections:
            if size:
                self.uc.mem_write(va, binary[raw:raw + size])
        for address, size in ((STACK, 0x200000), (OBJECT, 0x100000), (GLOBAL, 0x600000), (HEAP, 0x200000)):
            self.uc.mem_map(address, size)
        self.write(0x14586B600, GLOBAL, 'Q')
        self.records = {r['index']: r for r in records}
        addresses = sorted({int(m['registeredAddress'], 16) for r in records for m in r['methods'] if m['registeredAddress'] != '0x0'})
        self.ends = dict(zip(addresses, addresses[1:]))
        self.unwind_ranges = None
        self.context = self.uc.context_save()
        self.allowed, self.models, self.calls = [], {}, []
        self.heap_cursor = HEAP
        self.before_instruction = None
        self.trace_instructions = trace_instructions
        self.stub_cursor = END + 0x1000
        # Protected VM instructions need no Python callback unless collecting counts.
        # Unicorn's count/time budgets still cover every executed instruction.
        code_range = {} if trace_instructions else dict(begin=pe.base, end=0x15A2FFFFF)
        self.hooks = [self.uc.hook_add(U.UC_HOOK_CODE, self._code, **code_range),
                      self.uc.hook_add(U.UC_HOOK_MEM_INVALID, self._invalid),
                      self.uc.hook_add(U.UC_HOOK_INTR, self._forbidden),
                      self.uc.hook_add(U.UC_HOOK_INSN, self._forbidden, None, 1, 0, self.R.UC_X86_INS_SYSCALL),
                      self.uc.hook_add(U.UC_HOOK_INSN, self._forbidden, None, 1, 0, self.R.UC_X86_INS_SYSENTER)]

    def model_stub(self, callback):
        """Synthetic managed-library callback, never confused with a client VA."""
        if self.stub_cursor == END+0x1000:
            self.uc.mem_map(self.stub_cursor, 4096)
            if not self.trace_instructions:
                self.hooks.append(self.uc.hook_add(self.U.UC_HOOK_CODE,self._code,begin=self.stub_cursor,end=self.stub_cursor+4095))
        if self.stub_cursor >= END+0x2000: raise ValueError('Model stub capacity exceeded')
        address=self.stub_cursor;self.stub_cursor+=16
        self.uc.mem_write(address,b'\xc3');self.models[address]=callback
        return address

    def close(self):
        for h in self.hooks:
            self.uc.hook_del(h)
        self.hooks = []

    def read(self, address, fmt='Q'):
        return struct.unpack('<' + fmt, self.uc.mem_read(address, struct.calcsize('<' + fmt)))[0]

    def write(self, address, value, fmt='Q'):
        self.uc.mem_write(address, struct.pack('<' + fmt, value))

    def reg(self, name):
        return self.uc.reg_read(getattr(self.R, 'UC_X86_REG_' + name.upper()))

    def alloc(self, size=256):
        address = self.heap_cursor; self.heap_cursor += (size + 15) & ~15
        if self.heap_cursor > HEAP + 0x200000:
            raise ValueError('Synthetic heap exhausted')
        self.uc.mem_write(address, bytes(size))
        return address

    def allow(self, entry):
        if entry not in self.ends:
            raise ValueError('Missing native method extent')
        self.allowed.append((entry, self.ends[entry]))

    def allow_unwind(self, entry):
        """Allow one reviewed function, bounded by target PE exception records."""
        if self.unwind_ranges is None:
            b = self.pe.data; optional = number(b, 0x3c) + 24
            rva, size = struct.unpack_from('<II', b, optional + 112 + 3 * 8)
            if not size or size % 12: raise ValueError('Invalid PE exception directory')
            offset = self.pe.offset(self.pe.base + rva, size)
            self.unwind_ranges = {self.pe.base + start: self.pe.base + end
                for start, end, _ in struct.iter_unpack('<III', b[offset:offset+size])}
        end = self.unwind_ranges.get(entry)
        if end is None or not entry < end: raise ValueError('Native entry lacks exact unwind extent')
        self.pe.offset(entry, end-entry, executable=True)
        if (entry, end) not in self.allowed: self.allowed.append((entry, end))

    def synthetic_type(self, cell):
        pointer = self.alloc(512); self.write(pointer + 0xc7, 1, 'B'); self.write(cell, pointer)
        return pointer

    def prepare(self):
        self.uc.context_restore(self.context)
        self.uc.mem_write(OBJECT, bytes(0x100000))
        self.uc.mem_write(STACK + 0xf0000, bytes(0x20000))
        self.write(STACK + 0x100008, END)
        self.write(STREAM + 0x18, BUFFER)
        self.write(STREAM + 0x28, 0, 'I'); self.write(STREAM + 0x2c, CAPACITY, 'I')
        self.write(BUFFER + 0x18, CAPACITY, 'I')
        self.calls, self.failure, self.steps = [], None, 0

    def emit(self, data, stream=None):
        if (self.reg('rcx') if stream is None else stream) != STREAM:
            raise ValueError('Unexpected writer stream')
        pos = self.read(STREAM + 0x28, 'I')
        if pos + len(data) > CAPACITY:
            raise ValueError('Output overflow')
        self.uc.mem_write(BUFFER + 0x20 + pos, data); self.write(STREAM + 0x28, pos + len(data), 'I')

    def return_value(self, value=0):
        sp = self.reg('rsp'); ret = self.read(sp)
        self.uc.reg_write(self.R.UC_X86_REG_RSP, sp + 8)
        self.uc.reg_write(self.R.UC_X86_REG_RAX, value & ((1 << 64) - 1))
        self.uc.reg_write(self.R.UC_X86_REG_RIP, ret)

    def _code(self, uc, address, size, _):
        self.steps += 1
        if self.before_instruction:
            try: self.before_instruction(address)
            except (ValueError, KeyError) as error:
                self.failure=f'{address:#x}: {error}'; uc.emu_stop(); return
        if address in self.models:
            self.calls.append(dict(address=hex(address), rcx=self.reg('rcx'), rdx=self.reg('rdx'), r8=self.reg('r8')))
            try:
                self.return_value(self.models[address]())
            except (ValueError, KeyError) as error:
                self.failure = f'{address:#x}: {error}'; uc.emu_stop()
        elif not (0x15A300000 <= address < self.image_end or any(a <= address < z for a, z in self.allowed)):
            self.failure = f'Unmodeled native call {address:#x} (rcx={self.reg("rcx"):#x}, rdx={self.reg("rdx"):#x})'
            uc.emu_stop()

    def _invalid(self, uc, access, address, size, value, _):
        self.failure = f'Unmapped access {address:#x} at {self.reg("rip"):#x}'
        return False

    def _forbidden(self, uc, *args):
        self.failure = 'System instruction or interrupt'; uc.emu_stop()

    def run(self, entry, object_address=OBJECT, stream_address=STREAM):
        self.uc.reg_write(self.R.UC_X86_REG_RSP, STACK + 0x100008)
        self.uc.reg_write(self.R.UC_X86_REG_RCX, object_address)
        self.uc.reg_write(self.R.UC_X86_REG_RDX, stream_address)
        self.allow(entry)
        self.uc.emu_start(entry, END, timeout=20_000_000, count=20_000_000)
        if self.failure or self.reg('rip') != END:
            raise ValueError(self.failure or 'Execution budget exhausted')
        pos = self.read(STREAM + 0x28, 'I')
        if pos > CAPACITY:
            raise ValueError('Output position exceeds capacity')
        return bytes(self.uc.mem_read(BUFFER + 0x20, pos))

    def basic_writers(self):
        def write_varint(width):
            self.emit(varint(self.reg('rdx') & ((1 << width) - 1))); return 0
        def raw_byte():
            self.emit(bytes([self.reg('rdx') & 255])); return 0
        def enum_int32():
            value = self.reg('rdx') & 0xffffffff
            if value & 0x80000000:
                value |= 0xffffffff00000000
            self.emit(varint(value)); return 0
        self.models.update({0x1517662A0: lambda: write_varint(32), 0x151766960: lambda: write_varint(64),
                            0x151765E10: raw_byte, 0x151727A20: enum_int32})
