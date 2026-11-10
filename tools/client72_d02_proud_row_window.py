"""Bounded static collection of the one approved CN72 row entry."""
import argparse
import hashlib
import json
import pickle
import struct
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PRIVATE = ROOT / 'local/migration72/d02-resume-20261110b/c-proud-row-window'
PREV = ROOT / 'local/migration72/d02-resume-20261110b/c-proud-actual-fields'
ENTRY, CALLER, SITE, CELL, METHOD = 0x14d7adf70, 0x14d7acac0, 0x14d7ad394, 0x1422f9950, 663540
MAX_BYTES, MAX_INSTRUCTIONS = 2048, 768
PINS = {
    'tools/extract_client72.py': '7d1c75162f77c0fe0df154aa1240c142ad3f6af3295c89cc3d7b85372d3cb97d',
    'tools/game_path.ps1': '9d89da73dbb8479507eaf00fbe12f523b3472ca05e4aaa20602e2eb196cefa3b',
    'local/migration72/d02-resume-20261108/r3-equipment/metadata.private.pkl': 'df52eb70fe8e70dfecd1059e898d15e288cfa98bcefc78bcaabf753ae150349f',
    'local/migration72/evidence/client72-signatures.json': '28377e7a8d3c891cd61be97ddb74f8502a22f2f2f039d6a91dba3b092d7822fe',
    'local/migration72/d02-resume-20261110b/c-proud-actual-fields/map-producer1/code.bin': '1b6a043d8571670397330326eee063fe324d241ea71ae36932e36838d140bf30',
    'local/migration72/d02-resume-20261110b/c-proud-actual-fields/collect_map_producer.private.py': '74f31156a5018c882418740b594fbd511dc8c74b3426ea378a4ec95b195a55fa',
    'local/migration72/python/capstone/__init__.py': '94571d1314355e261aff0ddfc94985fe8761232ddff6874181fd21efa9fab47f',
    'local/migration72/python/capstone/lib/capstone.dll': '76958e18380023a68fd1714fa2e01c594cc6db1955a07ad6937b66e66dc5d6c3',
}
FIVE = {
    'YuanShen.exe': (450927000, '8b014f64b5dc12fbc476e0c362b053abfdd7a1378cd282e4ad81057621dbd6b5'),
    'YuanShen_Data/Managed/Metadata/global-metadata.dat': (83644188, '45dc7a44dc9fc187146c52f6c33de57e2d1c1dcfec57f8736431553460f6ef2a'),
    'YuanShen_Data/Managed/Metadata/startup-metadata.dat': (4116148, 'b314810840ca6f495822c9c44483dde2394b9f09ed3c6b13c2782092f04d5105'),
    'YuanShen_Data/Plugins/AccountPlatNative.dll': (9360280, 'adecdf0bd5442e321b1b66b0ca9e98bef76d5067326aa4add5b75266055a6c25'),
    'YuanShen_Data/Plugins/Astrolabe.dll': (2614168, '87e0a005ac46515156636af2120d3f824bcd28d69253e7d7235b959b54a0b442'),
}


def digest(raw):
    return hashlib.sha256(raw).hexdigest()


def checked(raw, expected, label):
    actual = digest(raw)
    if actual != expected:
        raise ValueError('source-drift: ' + label)
    return actual


def extent(table, entry, base, directory_rva):
    if len(table) % 12:
        raise ValueError('unaligned exception directory')
    exact, containing = [], []
    for offset in range(0, len(table), 12):
        begin, end, unwind = struct.unpack_from('<III', table, offset)
        row = dict(begin=hex(base+begin), endExclusive=hex(base+end), unwindRVA=hex(unwind),
                   recordRVA=hex(directory_rva+offset), offset=offset, size=end-begin)
        if begin == entry-base:
            exact.append(row)
        if begin <= entry-base < end:
            containing.append(row)
    return dict(exact=exact, containing=containing)


def select_extent(diagnostic):
    exact, containing = diagnostic['exact'], diagnostic['containing']
    if len(exact) != 1 or exact != containing:
        raise ValueError('exception entry is missing, non-exact or ambiguous')
    row = exact[0]
    return row


def enforce_bound(size):
    if not 0 < size <= MAX_BYTES:
        raise ValueError('extent exceeds approved bound: %d bytes' % size)


def save(path, value):
    path.write_text(json.dumps(value, ensure_ascii=False, sort_keys=True, indent=2)+'\n', encoding='utf8')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', required=True)
    args = parser.parse_args()
    dest = PRIVATE / args.output
    if not args.output or dest.name != args.output or dest.exists():
        parser.error('output must be a new private basename')
    dest.mkdir(parents=True)
    report = dict(task='E-01/R3-PROUD-ROW-WINDOW', stage='source-gate', inputChecks={},
                  sourceSHA256=digest(Path(__file__).read_bytes()), nativeCPU=0, liveReads=0,
                  xdataBytesRead=0, neighborCalleeBytesRead=0, newCodeWindowCount=0)
    try:
        sources = {}
        for path, expected in PINS.items():
            raw = (ROOT / path).read_bytes()
            sources[path] = dict(bytes=len(raw), sha256=checked(raw, expected, path))
        report['sources'] = sources
        meta = pickle.loads((ROOT / 'local/migration72/d02-resume-20261108/r3-equipment/metadata.private.pkl').read_bytes())[0]
        unique = [(r['index'], m) for r in meta for m in r['methods'] if m['registeredAddress'] == hex(ENTRY)]
        if len(unique) != 1 or unique[0][0] != 80063 or unique[0][1]['index'] != METHOD:
            raise ValueError('metadata identity differs')
        signatures = json.loads((ROOT / 'local/migration72/evidence/client72-signatures.json').read_text(encoding='utf8'))
        sig = [m for m in signatures['methods'] if m['index'] == METHOD]
        if len(sig) != 1 or sig[0]['declaringType'] != 80063 or sig[0]['registeredAddress'] != hex(ENTRY):
            raise ValueError('signature identity differs')
        types = [signatures['typeReferences'][str(p['typeIndex'])] for p in sig[0]['parameters']]
        if [(t['kind'], t['byRef']) for t in types] != [('uint32', False), ('bool', False)]:
            raise ValueError('signature parameters differ')
        report['identity'] = dict(owner=unique[0][0], method=unique[0][1], signature=sig[0], parameterTypes=types)
        client = Path(subprocess.check_output(['powershell.exe', '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', str(ROOT/'tools/game_path.ps1')], text=True).strip())
        binary = None
        for name, (size, expected) in FIVE.items():
            data = (client/name).read_bytes()
            actual = digest(data)
            report['inputChecks'][name] = dict(bytes=len(data), sha256=actual, expectedBytes=size, expectedSHA256=expected)
            if len(data) != size or actual != expected:
                raise ValueError('input size or SHA differs: ' + name)
            if name == 'YuanShen.exe':
                binary = data
        sys.path.insert(0, str(ROOT))
        from tools.extract_client72 import PE, number
        pe = PE(binary)
        read = lambda address, n, executable=False: binary[pe.offset(address, n, executable=executable):pe.offset(address, n, executable=executable)+n]
        report['stage'] = 'caller-and-aot'
        original = read(CALLER, 3179, True)
        frozen = (PREV/'map-producer1/code.bin').read_bytes()
        if original != frozen or digest(original) != PINS['local/migration72/d02-resume-20261110b/c-proud-actual-fields/map-producer1/code.bin']:
            raise ValueError('caller body differs')
        if read(0x14d7ad390, 9, True).hex() != '89d931d2e8d70b0000':
            raise ValueError('call setup or call differs')
        call = read(SITE, 5, True)
        if SITE+5+int.from_bytes(call[1:], 'little', signed=True) != ENTRY:
            raise ValueError('call target differs')
        cell = read(CELL, 8)
        pointer = int.from_bytes(cell, 'little')
        slot = pointer + METHOD*8
        slot_raw = read(slot, 8)
        if int.from_bytes(slot_raw, 'little') != ENTRY:
            raise ValueError('AOT slot differs')
        (dest/'cell.bin').write_bytes(cell)
        (dest/'slot.bin').write_bytes(slot_raw)
        report.update(caller=dict(start=hex(CALLER), bytes=len(original), sha256=digest(original), site=hex(SITE), setupAndCallHex='89d931d2e8d70b0000'),
                      aot=dict(cell=hex(CELL), cellHex=cell.hex(), cellSHA256=digest(cell), table=hex(pointer), slot=hex(slot), slotHex=slot_raw.hex(), slotSHA256=digest(slot_raw)))
        report['stage'] = 'pdata-before-body'
        nt = number(binary, 0x3c)
        directory_rva, directory_size = struct.unpack_from('<II', binary, nt+24+0x70+3*8)
        table = read(pe.base+directory_rva, directory_size)
        diagnostic = extent(table, ENTRY, pe.base, directory_rva)
        report['exceptionDirectory'] = dict(rva=hex(directory_rva), bytes=directory_size, diagnostic=diagnostic)
        save(dest/'boundary.private.json', report)
        row = select_extent(diagnostic)
        pdata = table[row['offset']:row['offset']+12]
        (dest/'pdata.bin').write_bytes(pdata)
        report.update(pdataHex=pdata.hex(), pdataSHA256=digest(pdata), entry=hex(ENTRY), endExclusive=row['endExclusive'], codeBytes=row['size'])
        save(dest/'pre-body.private.json', report)
        enforce_bound(row['size'])
        code = read(ENTRY, row['size'], True)
        sys.path.insert(0, str(ROOT/'local/migration72/python'))
        import capstone
        from capstone.x86_const import X86_OP_IMM, X86_OP_MEM, X86_REG_RIP
        decoder = capstone.Cs(capstone.CS_ARCH_X86, capstone.CS_MODE_64)
        decoder.detail = True
        insns = list(decoder.disasm(code, ENTRY))
        cursor, lines, calls, rip = ENTRY, [], [], []
        for ins in insns:
            raw = bytes(ins.bytes)
            if ins.address != cursor or raw != code[cursor-ENTRY:cursor-ENTRY+len(raw)]:
                raise ValueError('non-contiguous decode')
            cursor += len(raw)
            line = dict(address=hex(ins.address), hex=raw.hex(), mnemonic=ins.mnemonic, operands=ins.op_str)
            lines.append(line)
            for op in ins.operands:
                if ins.mnemonic == 'call' and op.type == X86_OP_IMM:
                    calls.append(dict(address=hex(ins.address), hex=raw.hex(), target=hex(op.imm)))
                if op.type == X86_OP_MEM and op.mem.base == X86_REG_RIP:
                    rip.append(dict(address=hex(ins.address), hex=raw.hex(), target=hex(ins.address+ins.size+op.mem.disp), mnemonic=ins.mnemonic, operands=ins.op_str))
        if cursor != ENTRY+len(code) or len(insns) > MAX_INSTRUCTIONS or b''.join(bytes.fromhex(x['hex']) for x in lines) != code:
            raise ValueError('decode incomplete or instruction bound exceeded')
        (dest/'code.bin').write_bytes(code)
        save(dest/'code.asm.private.json', lines)
        report.update(stage='complete', passed=True, newCodeWindowCount=1, codeSHA256=digest(code), instructions=len(lines),
                      assemblySHA256=digest((dest/'code.asm.private.json').read_bytes()),
                      decoder=dict(version=capstone.__version__, source='local/migration72/python/capstone/__init__.py'),
                      directCalls=calls, ripDataReferences=rip, targetSemantics='unknown', resourceIdentity='unknown', d02Complete=False)
        save(dest/'extraction.private.json', report)
        print(json.dumps(dict(entry=hex(ENTRY), endExclusive=row['endExclusive'], codeBytes=len(code), codeSHA256=digest(code), instructions=len(lines))))
    except Exception as error:
        report.update(passed=False, errorType=type(error).__name__, error=str(error))
        save(dest/'failure.private.json', report)
        raise


if __name__ == '__main__':
    main()
