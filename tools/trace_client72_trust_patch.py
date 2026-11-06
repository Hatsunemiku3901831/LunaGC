"""Run production Rust trust callbacks against the fixed offline native client.

The test-only bridge has no game hooks or DllMain. Target private DER travels
only through host memory; only hashes and decisions are emitted.
"""
import argparse
import ctypes as C
import hashlib
import json
from pathlib import Path

try:
    from . import trace_client72_trust_contract as contract
    from .extract_client72 import HASHES, read_verified, extract
except ImportError:
    import trace_client72_trust_contract as contract
    from extract_client72 import HASHES, read_verified, extract

REGISTERS = ('r15', 'r14', 'r13', 'r12', 'r11', 'r10', 'r9', 'r8', 'rbp',
             'rdi', 'rsi', 'rdx', 'rcx', 'rbx', 'rsp', 'rflags', '_no_use', 'rax')


class Registers(C.Structure):
    _fields_ = [('xmm', C.c_uint64 * 8)] + [(name, C.c_uint64) for name in REGISTERS]


class ActualSupply(contract.TrustSupply72):
    bridge = None
    material = None
    observations = []

    def __init__(self, emulator, expected, mode):
        super().__init__(emulator, expected, mode)
        material = dict(self.material)
        if mode in ('private-signature', 'public-signature'):
            name = mode.split('-')[0] + 'KeySignatureHex'
            signature = bytearray.fromhex(material[name])
            signature[0] ^= 1
            material[name] = signature.hex()
        encoded = json.dumps(material).encode('ascii')
        self.handle = self.bridge.trust72_load(encoded, len(encoded))
        if not self.handle:
            raise ValueError('Rust bundle loader rejected the declared fixture')
        layout = (C.c_uint64 * 5)()
        self.bridge.trust72_layout(self.handle, layout)
        if list(layout[:4]) != [C.sizeof(Registers), Registers.rsp.offset, Registers.rflags.offset, Registers.rax.offset]:
            raise ValueError('Actual ilhook Registers ABI differs from ctypes layout')
        self.host_raw = layout[4]
        self.actual_calls = []
        self.observations.append(self)

    def observe(self, address):
        if address not in (contract.COPY, contract.CHECK):
            return super().observe(address)
        e = self.e
        registers = Registers()
        for name in REGISTERS:
            if name != '_no_use':
                setattr(registers, name, e.reg('eflags' if name == 'rflags' else name))
        operation = C.c_uint32(e.read(e.reg('r12'), 'I'))
        registers.r12 = C.addressof(operation)
        if address == contract.COPY:
            original = e.reg('r13')
            raw = C.create_string_buffer(bytes(e.uc.mem_read(original, e.reg('r8') - 3)))
            registers.r13 = C.addressof(raw)
            before = bytes(registers)
            self.bridge.trust72_copy(self.handle, C.byref(registers))
            supplied = registers.r13 == self.host_raw
            actual = registers.r13
            registers.r13 = C.addressof(raw)
            if bytes(registers) != before or (not supplied and actual != C.addressof(raw)):
                raise ValueError('Production COPY callback changed undeclared registers')
            super().observe(address)
            if supplied != bool(self.current['copyChangedRegisters']):
                raise ValueError('Rust COPY decision differs from independently traced contract')
            e.uc.reg_write(e.R.UC_X86_REG_R13, self.raw if supplied else original)
        else:
            original_signature = e.reg('r8')
            original_root = e.read(e.reg('rsp') + 0x20)
            der = C.create_string_buffer(bytes(e.uc.mem_read(e.reg('rcx'), e.reg('edx'))))
            signature = C.create_string_buffer(bytes(e.uc.mem_read(original_signature, 256)))
            root = C.create_string_buffer(bytes(e.uc.mem_read(original_root, 428)))
            stack = (C.c_uint64 * 6)(*[e.read(e.reg('rsp') + i * 8) for i in range(6)])
            stack[4] = C.addressof(root)
            registers.rcx = registers.rdi = C.addressof(der)
            registers.r8 = C.addressof(signature)
            registers.r13 = self.host_raw if e.reg('r13') == self.raw else e.reg('r13')
            registers.rsp = C.addressof(stack)
            before, before_stack = bytes(registers), list(stack)
            self.bridge.trust72_check(self.handle, C.byref(registers))
            supplied = registers.r8 != C.addressof(signature)
            actual_signature = C.string_at(registers.r8, 256)
            actual_root = C.string_at(stack[4])
            registers.r8 = C.addressof(signature)
            stack[4] = C.addressof(root)
            if bytes(registers) != before or list(stack) != before_stack:
                raise ValueError('Production CHECK callback changed undeclared registers/stack')
            super().observe(address)
            row = self.current
            if (supplied != row['supplied'] or hashlib.sha256(actual_signature).hexdigest() != row['signatureSHA256']
                    or hashlib.sha256(actual_root).hexdigest() != row['rootSHA256']):
                raise ValueError('Rust CHECK outputs differ from independently traced trust inputs')
            if supplied:
                e.uc.mem_write(self.signatures[row['kind']], actual_signature)
                e.uc.mem_write(self.root, actual_root + b'\0')
                e.uc.reg_write(e.R.UC_X86_REG_R8, self.signatures[row['kind']])
                e.write(e.reg('rsp') + 0x20, self.root)
        self.actual_calls.append(dict(address=hex(address), operation=operation.value, supplied=supplied,
                                      registerWhitelist=True, stackWhitelist=True))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client', type=Path, required=True)
    parser.add_argument('--java-home', type=Path, required=True)
    parser.add_argument('--bridge', type=Path, required=True)
    parser.add_argument('--bundle', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--libraries', type=Path, default=Path('local/migration72/python'))
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    if not args.output.resolve().is_relative_to((root / 'local').resolve()):
        parser.error('Output must stay under ignored local/')
    bridge = C.CDLL(str(args.bridge.resolve()))
    bridge.trust72_load.argtypes = [C.c_char_p, C.c_size_t]
    bridge.trust72_load.restype = C.c_void_p
    for name in ('trust72_copy', 'trust72_check'):
        getattr(bridge, name).argtypes = [C.c_void_p, C.POINTER(Registers)]
    bridge.trust72_layout.argtypes = [C.c_void_p, C.POINTER(C.c_uint64)]
    bridge.trust72_free.argtypes = [C.c_void_p]
    ActualSupply.bridge = bridge
    ActualSupply.material = json.loads(args.bundle.read_text('ascii'))
    # Same native executions/oracles as F-04b; replace only the supplier with
    # the compiled production callbacks. Invalid roots/wrong server bundles
    # are loader failures tested separately, not accepted runtime configuration.
    contract.TrustSupply72 = ActualSupply
    contract.CASES = [row for row in contract.CASES if row[2] not in ('root', 'wrong-server-key')]
    binary, metadata, startup = [read_verified(args.client / name, digest) for name, digest in HASHES.items()]
    records, _ = extract(binary, metadata, startup=startup, structures=True)
    try:
        report = contract.recover(binary, metadata, records, args.libraries, root, args.java_home)
        errors = contract.validate_summary(report)
        if errors:
            raise ValueError(errors)
        report.update(task='F-04c', productionCallbacksExecuted=True,
                      callbackTrace=[row.actual_calls for row in ActualSupply.observations],
                      bridgeSHA256=hashlib.sha256(args.bridge.read_bytes()).hexdigest(),
                      bundleSHA256=hashlib.sha256(args.bundle.read_bytes()).hexdigest())
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(report, indent=2) + '\n', 'utf8')
    finally:
        for supplier in ActualSupply.observations:
            bridge.trust72_free(supplier.handle)


if __name__ == '__main__':
    main()
