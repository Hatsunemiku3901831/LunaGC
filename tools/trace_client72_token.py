"""Bounded offline observation of the protected 7.2 NJAALDCMHEP writer.

Uses Unicorn 2.1.4, never loads the client into Windows or changes client files.
Only four reviewed writer helpers are modeled (ASCII strings, uint32/64 varint,
raw byte). Unknown calls/memory and incomplete execution fail. Zeroed globals
select base, non-IFix code; this is not a loaded-client or ABI acceptance test.
"""
import argparse
import gc
import json
from pathlib import Path
import struct
import sys

try:
    from .extract_client72 import HASHES, PE, HEADER_VA, number, read_verified, extract, recover_layouts72
except ImportError:
    from extract_client72 import HASHES, PE, HEADER_VA, number, read_verified, extract, recover_layouts72

ENTRY, TYPE_INDEX, TYPE_NAME = 0x1500C2660, 82491, 'NJAALDCMHEP'
OBJECT, STREAM, BUFFER, STACK, END = 0x610000000, 0x610001000, 0x61000C000, 0x600000000, 0x620000000
HELPERS = {0x15171FE80: 'ascii-string', 0x1517662A0: 'uint32-varint',
           0x151766960: 'uint64-varint', 0x151765E10: 'raw-byte'}


def varint(value):
    if type(value) is not int or not 0 <= value < 1 << 64:
        raise ValueError('Expected uint64')
    data = bytearray()
    while value >= 128:
        data.append((value & 127) | 128); value >>= 7
    data.append(value)
    return bytes(data)


def read_varint(data, offset):
    value = 0
    for i in range(10):
        if offset >= len(data):
            raise ValueError('Truncated varint')
        byte = data[offset]; offset += 1
        if i == 9 and byte > 1:
            raise ValueError('Overflowing varint')
        value |= (byte & 127) << (i * 7)
        if byte < 128:
            return value, offset
    raise ValueError('Unterminated varint')


def parse_wire(data):
    rows, offset, seen = [], 0, set()
    while offset < len(data):
        key, offset = read_varint(data, offset)
        tag, wire = key >> 3, key & 7
        if not 1 <= tag < (1 << 29) or 19000 <= tag <= 19999 or tag in seen:
            raise ValueError('Invalid/duplicate tag')
        seen.add(tag)
        if wire == 0:
            value, offset = read_varint(data, offset)
        elif wire == 2:
            length, offset = read_varint(data, offset)
            if offset + length > len(data):
                raise ValueError('Truncated length-delimited field')
            value = data[offset:offset + length]; offset += length
        else:
            raise ValueError('Unreviewed wire kind in selected message')
        rows.append((tag, wire, value))
    return rows


def fixture_values(fields, case):
    if case not in ('nonzero', 'boundary', 'zero'):
        raise ValueError('Unknown fixture')
    values = {}
    for field in fields:
        offset, kind = field['offset'], field['type']['kind']
        if field['offsetKind'] != 'instance' or kind not in ('string', 'uint32', 'bool'):
            raise ValueError('Unreviewed target field layout/type')
        if kind == 'string':
            value = b'' if case == 'zero' else f'f_{offset}'.encode('ascii')
        elif kind == 'bool':
            value = 0 if case == 'zero' else 1
        else:
            value = 0 if case == 'zero' else 0x12340000 + offset if case == 'nonzero' else 0xFFFFFFFF - offset
        values[offset] = value
    return values


def bind_wire(fields, values, data):
    identities = {}
    for field in fields:
        value = values[field['offset']]
        key = (2 if isinstance(value, bytes) else 0, value)
        if key in identities:
            raise ValueError('Ambiguous fixture identity')
        identities[key] = field
    rows = []
    for tag, wire, value in parse_wire(data):
        if (wire, value) not in identities:
            raise ValueError('Observed value not in the controlled input')
        field = identities.pop((wire, value))
        rows.append(dict(clientField=field['name'], objectOffset=field['offset'], tag=tag,
                         type=field['type']['kind'], wireType=wire, cardinality='singular', presence='implicit', packed=False,
                         wireStatus='emulated-observation', semanticStatus='unresolved'))
    if identities:
        raise ValueError('Incomplete field coverage')
    return sorted(rows, key=lambda row: row['tag'])


def emulate(binary, fields, case, emulator):
    U, R = emulator
    pe = PE(binary); image_size = number(binary, number(binary, 0x3C) + 24 + 56)
    if not 0 < image_size < (1 << 30):
        raise ValueError('Unexpected image size')
    uc = U.Uc(U.UC_ARCH_X86, U.UC_MODE_64)
    uc.mem_map(pe.base, (image_size + 4095) & ~4095)
    header_size = number(binary, number(binary, 0x3C) + 24 + 60)
    uc.mem_write(pe.base, binary[:header_size])
    for va, size, raw, _ in pe.sections:
        if size:
            uc.mem_write(va, binary[raw:raw + size])
    uc.mem_map(STACK, 0x200000); uc.mem_map(OBJECT, 0x10000)
    sp = STACK + 0x100008
    uc.mem_write(sp, struct.pack('<Q', END))
    uc.reg_write(R.UC_X86_REG_RSP, sp); uc.reg_write(R.UC_X86_REG_RCX, OBJECT); uc.reg_write(R.UC_X86_REG_RDX, STREAM)
    values, strings = fixture_values(fields, case), {}
    for field in fields:
        offset, value = field['offset'], values[field['offset']]
        if isinstance(value, bytes):
            pointer = OBJECT + 0x2000 + offset * 64
            strings[pointer] = value
            uc.mem_write(pointer + 0x10, struct.pack('<I', len(value)) + value.decode('ascii').encode('utf-16-le'))
            uc.mem_write(OBJECT + offset, struct.pack('<Q', pointer))
        else:
            uc.mem_write(OBJECT + offset, struct.pack('<B' if field['type']['kind'] == 'bool' else '<I', value))
    uc.mem_write(STREAM + 0x18, struct.pack('<Q', BUFFER))
    uc.mem_write(STREAM + 0x28, struct.pack('<II', 0, 4096)); uc.mem_write(BUFFER + 0x18, struct.pack('<I', 4096))
    calls, failure = [], []
    count = 0

    def emit(data):
        position = struct.unpack('<I', uc.mem_read(STREAM + 0x28, 4))[0]
        if position + len(data) > 4096:
            raise ValueError('Writer capacity exceeded')
        uc.mem_write(BUFFER + 0x20 + position, data)
        uc.mem_write(STREAM + 0x28, struct.pack('<I', position + len(data)))

    def code_hook(engine, address, size, _):
        nonlocal count
        count += 1
        if address == ENTRY or 0x15A300000 <= address < pe.base + image_size:
            return
        if address not in HELPERS or engine.reg_read(R.UC_X86_REG_RCX) != STREAM:
            failure.append(f'Unmodeled native call/state at {address:#x}'); engine.emu_stop(); return
        arg = engine.reg_read(R.UC_X86_REG_RDX)
        if address == 0x15171FE80:
            if arg not in strings:
                failure.append('Unexpected string pointer'); engine.emu_stop(); return
            value = strings[arg]; emit(varint(len(value)) + value)
        elif address == 0x1517662A0:
            emit(varint(arg & 0xFFFFFFFF))
        elif address == 0x151766960:
            emit(varint(arg))
        else:
            emit(bytes([arg & 255]))
        calls.append(HELPERS[address])
        stack = engine.reg_read(R.UC_X86_REG_RSP)
        ret = struct.unpack('<Q', engine.mem_read(stack, 8))[0]
        engine.reg_write(R.UC_X86_REG_RSP, stack + 8)
        engine.reg_write(R.UC_X86_REG_RAX, 0); engine.reg_write(R.UC_X86_REG_RIP, ret)

    def invalid(engine, access, address, size, value, _):
        failure.append(f'Unmapped memory {address:#x}, instruction {engine.reg_read(R.UC_X86_REG_RIP):#x}')
        return False

    def forbidden(engine, *args):
        failure.append('System instruction or interrupt'); engine.emu_stop()

    hooks = [uc.hook_add(U.UC_HOOK_CODE, code_hook), uc.hook_add(U.UC_HOOK_MEM_INVALID, invalid),
             uc.hook_add(U.UC_HOOK_INTR, forbidden),
             uc.hook_add(U.UC_HOOK_INSN, forbidden, None, 1, 0, R.UC_X86_INS_SYSCALL),
             uc.hook_add(U.UC_HOOK_INSN, forbidden, None, 1, 0, R.UC_X86_INS_SYSENTER)]
    try:
        uc.emu_start(ENTRY, END, timeout=20_000_000, count=20_000_000)
        if failure or uc.reg_read(R.UC_X86_REG_RIP) != END:
            raise ValueError('; '.join(failure) or 'Instruction/time budget exhausted before return')
        position = struct.unpack('<I', uc.mem_read(STREAM + 0x28, 4))[0]
        if position > 4096:
            raise ValueError('Output position exceeds capacity')
        wire = bytes(uc.mem_read(BUFFER + 0x20, position))
        if case == 'zero' and wire:
            raise ValueError('Default fixture unexpectedly produced wire')
        mapped = [] if case == 'zero' else bind_wire(fields, values, wire)
        return dict(case=case, instructions=count, modeledCalls=calls, wire=wire.hex(), fields=mapped)
    finally:
        for hook in hooks:
            uc.hook_del(hook)


def load_emulator(directory):
    sys.path.insert(0, str(directory.resolve()))
    import unicorn
    from unicorn import x86_const
    if unicorn.__version__ != '2.1.4':
        raise ValueError('This observation harness is tested with Unicorn 2.1.4')
    return unicorn, x86_const


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client', type=Path, required=True)
    parser.add_argument('--python-libs', type=Path, default=Path('local/migration72/python'))
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--mapping-output', type=Path, required=True)
    parser.add_argument('--proto-output', type=Path, required=True)
    args = parser.parse_args()
    root = args.client.resolve(); outputs = [args.output.resolve(), args.mapping_output.resolve(), args.proto_output.resolve()]
    if len(set(outputs)) != 3 or any(p.is_relative_to(root) for p in outputs):
        parser.error('Use distinct outputs outside the read-only client')
    binary, metadata, startup = [read_verified(root / name, sha) for name, sha in HASHES.items()]
    records, _ = extract(binary, metadata, members=True)
    row = records[TYPE_INDEX]
    if row['name'] != TYPE_NAME or not any(m['name'] == 'CAIINKNGKDM' and int(m['registeredAddress'], 16) == ENTRY for m in row['methods']):
        raise ValueError('Selected protected writer changed')
    pe = PE(binary); recover_layouts72(pe, metadata, pe.offset(HEADER_VA), [row], {})
    fields = row['fields']; del records, metadata, startup; gc.collect()
    emulator = load_emulator(args.python_libs)
    results = [emulate(binary, fields, case, emulator) for case in ('nonzero', 'boundary', 'zero')]
    if results[0]['fields'] != results[1]['fields']:
        raise ValueError('Independent inputs disagree on field mapping')
    context = dict(inputSHA256=HASHES, clientType=TYPE_NAME, typeIndex=TYPE_INDEX, protectedEntry=hex(ENTRY),
                   evidence='docs/CLIENT_72_PROTECTED.md', emulator='unicorn-2.1.4', runtimeStatus='not-tested',
                   assumptions=['Synthetic mutable object and fixed-capacity stream', 'Zero globals: base/non-IFix path',
                                'Four reviewed helper models; ASCII strings only', 'No Windows/client process executed'])
    mapping = dict(context, schemaVersion=1, fields=results[0]['fields'])
    for path, value in zip(outputs, (dict(context, fixtures=results), mapping)):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf8')
    try:
        from .extract_client72_wire import proto_text
    except ImportError:
        from extract_client72_wire import proto_text
    outputs[2].parent.mkdir(parents=True, exist_ok=True)
    outputs[2].write_text(proto_text({'messages': [mapping]}, 'trace_client72_token.py (offline helper models)'), encoding='utf8')
    print(json.dumps(dict(fields=len(mapping['fields']), fixtures=len(results), instructions=[r['instructions'] for r in results],
                         status='offline-observation', runtimeStatus='not-tested')))


if __name__ == '__main__':
    main()
