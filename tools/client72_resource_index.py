"""Fixed CN72 resource-index declarations and bounded static/native research."""
import argparse
import hashlib
import json
from pathlib import Path
import struct
import sys
try:
    from .audit_d02_resource_sources72 import PROFILE, file_hash
    from .extract_client72 import read_verified
except ImportError:
    from audit_d02_resource_sources72 import PROFILE, file_hash
    from extract_client72 import read_verified

CTABLE_SHA256 = '03b2478ba340c6fcda27a4988c6501c32665b1323022e927aaeeb1f338e4710b'

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client', required=True, type=Path)
    parser.add_argument('--output', required=True, type=Path)
    parser.add_argument('--native-path', action='store_true', help='Execute bounded native path conversion; no file I/O models')
    args = parser.parse_args()
    client, output = args.client.resolve(), args.output.resolve()
    repo = Path(__file__).resolve().parents[1]
    if output.is_relative_to(client) or output.is_relative_to(repo / 'resources'):
        parser.error('Output must be outside client/runtime resources')
    verified = [read_verified(client / name, digest) for name, digest in PROFILE.items()]
    ctable = []
    for relative in ('YuanShen_Data/Persistent/ctable.dat', 'YuanShen_Data/StreamingAssets/ctable_streaming.dat'):
        data = (client / relative).read_bytes()
        digest = hashlib.sha256(data).hexdigest()
        if digest != CTABLE_SHA256:
            raise ValueError('Fixed ctable changed')
        ctable.append(dict(path=relative, size=len(data), sha256=digest))
    report = dict(task='D02-R3-resource-index', ctable=ctable, inputSHA256=PROFILE,
                  static=audit_static_io(verified[0], verified[1], repo), nativeExecuted=False,
                  ctableDecoded=False, resourceReady=False, sourceSHA256=file_hash(Path(__file__)))
    if args.native_path:
        report['nativePath'] = probe_native_path(verified[0], client, repo)
        report['nativeExecuted'] = True
    data = (json.dumps(report, indent=2) + '\n').encode()
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_bytes(data)
    print(json.dumps(dict(functions=len(report['static']['functions']), nativeExecuted=report['nativeExecuted'],
                          ctableDecoded=False, sha256=hashlib.sha256(data).hexdigest())))


def audit_static_io(binary, metadata, repo):
    """Recover selected declarations anew, then inspect exact .pdata ranges."""
    sys.path.insert(0, str(repo / 'local/migration72/python'))
    from capstone import Cs, CS_ARCH_X86, CS_MODE_64, __version__ as capstone_version
    try:
        from .extract_client72 import PE, Names, HEADER_VA, BODY, STRIDE, CODE_REGISTRATION, number, read_verified
        from .extract_client72_literals import Literals72
    except ImportError:
        from extract_client72 import PE, Names, HEADER_VA, BODY, STRIDE, CODE_REGISTRATION, number, read_verified
        from extract_client72_literals import Literals72
    pe = PE(binary)
    header = pe.offset(HEADER_VA, BODY)
    type_base = BODY + ((number(binary, header + 0x64) + 0xa7932555) & 0xffffffff)
    names = Names(metadata, BODY + (number(binary, header + 0x70) ^ 0x18202ccc))
    method_base = BODY + (number(binary, header + 0xb0) ^ 0x51324a1f)
    method_bytes = (number(binary, header + 0x1c) + 0x85490f40) & 0xffffffff
    pointers = pe.offset(number(binary, pe.offset(CODE_REGISTRATION) + 0x70, 'Q'), method_bytes // 26 * 8)
    specs = [(11307, 80101, 'LoadBinDataZipFile', 0x146eb90f0),
             (11089, 78058, 'LoadBinDataZipFile', 0x147065360),
             (11132, 78585, 'LoadBinDataZipFile', 0x14713f3c0),
             (11132, 78581, 'LoadResourceIndex', 0x14713ea90),
             (10895, 76986, 'DeserializeBinary', 0x146f00000),
             (10894, 76967, '.ctor', 0x146d8bd20),
             (11099, 78158, 'GetResourceIdentifier', 0x147237b80),
             (11099, 78139, 'CheckIsLegitAssetPath', 0x147234540),
             (89475, 738725, 'NKBIBEFPMPD', 0x14b65aee0)]
    declarations = recover_declarations(binary, metadata, names, type_base, method_base, pointers, specs)
    optional = number(binary, 0x3c) + 24
    rva, size = struct.unpack_from('<II', binary, optional + 112 + 3 * 8)
    raw_offset = pe.offset(pe.base + rva)
    ranges = {pe.base + begin: pe.base + end for begin, end, _ in
              struct.iter_unpack('<III', binary[raw_offset:raw_offset + size])}
    literals = Literals72(binary, metadata).selected((24034, 24035, 45482, 55633, 55646))
    literal_cells = {int(row['cell'], 16) for row in literals['literals']}
    entries = sorted({row[3] for row in specs} | {0x14d6d53a0, 0x14d6e1570, 0x14146c630, 0x141122120, 0x141122e00, 0x141a12b90})
    decoder = Cs(CS_ARCH_X86, CS_MODE_64)
    decoder.detail = True
    functions = []
    for entry in entries:
        if entry not in ranges:
            raise ValueError('Selected entry lacks an exact .pdata range: ' + hex(entry))
        raw = binary[pe.offset(entry):pe.offset(entry) + ranges[entry] - entry]
        instructions, calls, references = [], [], []
        for ins in decoder.disasm(raw, entry):
            instructions.append(dict(address=hex(ins.address), bytes=bytes(ins.bytes).hex(),
                                     mnemonic=ins.mnemonic, operands=ins.op_str))
            if ins.mnemonic in ('call', 'jmp'):
                calls.append(dict(address=hex(ins.address), instruction=ins.mnemonic, target=ins.op_str))
            for operand in ins.operands:
                if operand.type == 3 and operand.mem.base == 41:
                    target = ins.address + ins.size + operand.mem.disp
                    if target in literal_cells:
                        references.append(dict(address=hex(ins.address), cell=hex(target)))
        functions.append(dict(entry=hex(entry), end=hex(ranges[entry]), sha256=hashlib.sha256(raw).hexdigest(),
                              instructions=instructions, calls=calls, selectedLiteralReferences=references))
    return dict(declarations=declarations, literals=literals, functions=functions, capstoneVersion=capstone_version,
                sourceSHA256={name: file_hash(repo / name) for name in (
                    'tools/extract_client72.py', 'tools/extract_client72_literals.py',
                    'tools/audit_d02_resource_sources72.py')},
                scope='Selected fixed-input metadata declarations and complete .pdata code ranges; static only.',
                nativeExecuted=False, ctableDecoded=False, completeResourceIO=False,
                openBoundaries=['Native file/archive helpers at 14146c630/141122120/141122e00 are observed code, not approved decoders.',
                                'IFix flags, runtime resource index/receiver and active file selection remain unobserved.'])


def recover_declarations(binary, metadata, names, type_base, method_base, pointers, specs):
    try:
        from .extract_client72 import STRIDE, number
    except ImportError:
        from extract_client72 import STRIDE, number
    declarations = []
    for owner, index, expected_name, expected_address in specs:
        type_offset = type_base + owner * STRIDE
        first = number(metadata, type_offset + 0xc) ^ 0x5abb91bd
        count = (number(metadata, type_offset + 0x3a, 'H') + 0xbf1a) & 0xffff
        if not first <= index < first + count:
            raise ValueError('Selected method is outside declaring type')
        key = (((index * 0x1639) ^ 0x5e01c03b) * 0x106b250c17f34ccb + 0x069ffdcbe218faf4) & 0xffffffffffffffff
        entry = method_base + index * 26
        name = names.decode((number(metadata, entry + 8) - key + 0xde01c441) & 0xffffffff)
        address = number(binary, pointers + index * 8, 'Q')
        if name != expected_name or address != expected_address:
            raise ValueError('Selected method declaration changed')
        declarations.append(dict(owner=owner,
            ownerName=names.decode((number(metadata, type_offset + 0x18) + 0xa285a06d) & 0xffffffff),
            namespace=names.decode(number(metadata, type_offset + 0x14) ^ 0x149e96f1),
            index=index, name=name, address=hex(address),
            parameterCount=((metadata[entry + 0x18] ^ 0xf9) - key) & 0xff))
    return declarations


def probe_native_path(binary, client, repo):
    try:
        from .client72_emulator import Offline72, STACK, END
    except ImportError:
        from client72_emulator import Offline72, STACK, END
    engine = Offline72(binary, repo/'local/migration72/python', [], trace_instructions=True)
    rows, keys = [], []
    try:
        for entry in (0x14146c630, 0x1414d30b0, 0x1414d9f90, 0x141a456a0, 0x141a456d1, 0x141a457cb, 0x141a12b90):
            engine.allow_unwind(entry)
        for entry, expected in ((0x140003750, bytes.fromhex('8b4110c3')),
                                (0x1404bbeb0, bytes.fromhex('488d4114c3'))):
            offset = engine.pe.offset(entry)
            if binary[offset:offset+len(expected)] != expected:
                raise ValueError('Selected managed-string leaf changed')
            engine.allowed.append((entry, entry+len(expected)))
        # These instructions implement native stack probing, never an I/O callback.
        engine.allowed.append((0x141cc01a0, 0x141cc01ee))
        # Stop before the next native string constructor; this byte is not executed.
        engine.allowed.append((0x140970e90, 0x140970e91))
        samples = [('fixed-file-path', str(client/'YuanShen_Data/Persistent/ctable.dat')),
                   ('unicode-fixture', '资源/🙂/ctable.dat'), ('empty', ''), ('null', None)]
        path_digest = hashlib.sha256()
        def observe(address):
            path_digest.update(struct.pack('<Q', address))
            if address == 0x140970e90:
                engine.uc.emu_stop()
        for name, text in samples:
            engine.prepare()
            teb = engine.alloc(128)
            engine.write(teb+0x10, STACK)
            engine.uc.reg_write(engine.R.UC_X86_REG_GS_BASE, teb)
            pointer = 0
            if text is not None:
                chars = text.encode('utf-16le')
                pointer = engine.alloc(22+len(chars))
                engine.write(pointer+16, len(chars)//2, 'I')
                engine.uc.mem_write(pointer+20, chars)
            for register, value in (('rsp', STACK+0x100008), ('rcx', pointer), ('rdx', 0), ('r8', 0), ('r9', 2)):
                engine.uc.reg_write(getattr(engine.R, 'UC_X86_REG_'+register.upper()), value)
            engine.write(STACK+0x100008+0x28, 0)
            path_digest = hashlib.sha256()
            engine.before_instruction = observe
            engine.uc.emu_start(0x14146c630, END, timeout=1_000_000, count=100_000)
            engine.before_instruction = None
            if engine.failure:
                raise ValueError(engine.failure)
            stop = engine.reg('rip')
            if text is None:
                if stop != END or engine.reg('rax') != 0:
                    raise ValueError('Native null-path result changed')
                output = None
            else:
                if stop != 0x140970e90:
                    raise ValueError('Native path did not reach declared string boundary')
                length = engine.reg('r8')
                output = bytes(engine.uc.mem_read(engine.reg('rdx'), length)) if length else b''
                if output != text.encode('utf-8'):
                    raise ValueError('Independent UTF-8 comparison failed')
            rows.append(dict(name=name, stop=hex(stop), instructions=engine.steps,
                             nativePathSHA256=path_digest.hexdigest(), models=engine.calls,
                             inputSHA256=hashlib.sha256(text.encode()).hexdigest() if text is not None else None,
                             utf8SHA256=hashlib.sha256(output).hexdigest() if output is not None else None,
                             utf8Bytes=len(output) if output is not None else None,
                             nativeNullReturnZero=text is None))
        for value in (0, 0x6f, 0x80000001, 0xffffffff):
            engine.prepare()
            destination = engine.alloc(40)
            engine.write(destination+0x20, 0x46, 'I')
            for register, number in (('rsp', STACK+0x100008), ('rcx', value), ('rdx', destination)):
                engine.uc.reg_write(getattr(engine.R, 'UC_X86_REG_'+register.upper()), number)
            path_digest = hashlib.sha256()
            engine.before_instruction = observe
            engine.uc.emu_start(0x141a12b90, END, timeout=1_000_000, count=100_000)
            engine.before_instruction = None
            if engine.failure or engine.reg('rip') != 0x140970e90:
                raise ValueError(engine.failure or 'Entry key did not reach declared string boundary')
            actual = bytes(engine.uc.mem_read(engine.reg('rdx'), engine.reg('r8')))
            if actual != f'{value:08x}'.encode():
                raise ValueError('Independent uint32 entry-name comparison failed')
            keys.append(dict(input=value, output=actual.decode('ascii'), stop=hex(engine.reg('rip')),
                             instructions=engine.steps, nativePathSHA256=path_digest.hexdigest(), models=engine.calls))
    finally:
        engine.close()
    return dict(samples=rows, entryKeySamples=keys, nativeExecuted=True, fileIOExecuted=False, ctableDecoded=False,
        unicornVersion=engine.U.__version__,
        allowedCodeRanges=[dict(start=hex(start), end=hex(end),
            sha256=hashlib.sha256(binary[engine.pe.offset(start):engine.pe.offset(start)+end-start]).hexdigest(),
            boundaryOnly=start == 0x140970e90) for start, end in engine.allowed],
        models=['Managed UTF-16 string storage is an explicit fixture.',
                'GS+0x10 StackLimit points to the mapped offline stack; native stack-probe instructions execute.'],
        sourceSHA256={name: file_hash(repo/name) for name in ('tools/client72_emulator.py', 'tools/trace_client72_token.py')},
        scope='Native path/null and uint32 entry-name formatting only. Positive paths/keys stop before 140970e90; no file handle or bytes are invented.')


if __name__ == '__main__':
    main()
