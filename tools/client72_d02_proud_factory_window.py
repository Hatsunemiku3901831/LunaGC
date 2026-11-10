"""Collect/audit only the approved static CN72 factory entry; no execution or semantic inference."""
import argparse
import hashlib
import json
import pickle
import struct
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PRIVATE = ROOT / 'local/migration72/d02-resume-20261110b/c-proud-factory-window'
OLD = 'local/migration72/d02-resume-20261110b/c-proud-mode-factory/slot9-1/'
META = 'local/migration72/d02-resume-20261108/r3-equipment/metadata.private.pkl'
SIGNATURES = 'local/migration72/evidence/client72-signatures.json'
CALLER_SOURCE = 'local/migration72/d02-resume-20261110b/c-proud-parser-contract/base-constructor1/code.bin'
FROZEN_SHA = 'b9d75bcc76b6f498e6a3e9533648193921ecd6e0c74c04acbc920a31df5fbfb9'
ENTRY, OWNER, METHOD = 0x149F0BB40, 63831, 529228
CALLER, SITE, CELL, SLOT = 0x14D5E24E0, 0x14D5E253A, 0x1422F9950, 0x142CAEF30
MAX_BYTES, MAX_INSTRUCTIONS = 1024, 256
CALLER_SETUP = bytes.fromhex('488b064889f131d2ff901801000048894620')
FILES = ('boundary.private.json', 'cell.bin', 'code.asm.private.json', 'code.bin',
         'extraction.private.json', 'pdata.bin', 'pre-body.private.json', 'slot.bin')
FIVE = {
    'YuanShen.exe': (450927000, '8b014f64b5dc12fbc476e0c362b053abfdd7a1378cd282e4ad81057621dbd6b5'),
    'YuanShen_Data/Managed/Metadata/global-metadata.dat': (83644188, '45dc7a44dc9fc187146c52f6c33de57e2d1c1dcfec57f8736431553460f6ef2a'),
    'YuanShen_Data/Managed/Metadata/startup-metadata.dat': (4116148, 'b314810840ca6f495822c9c44483dde2394b9f09ed3c6b13c2782092f04d5105'),
    'YuanShen_Data/Plugins/AccountPlatNative.dll': (9360280, 'adecdf0bd5442e321b1b66b0ca9e98bef76d5067326aa4add5b75266055a6c25'),
    'YuanShen_Data/Plugins/Astrolabe.dll': (2614168, '87e0a005ac46515156636af2120d3f824bcd28d69253e7d7235b959b54a0b442'),
}


def digest(raw):
    return hashlib.sha256(raw).hexdigest()


def require(value, message):
    if not value:
        raise ValueError(message)


def save(path, value):
    with path.open('xb') as stream:
        stream.write((json.dumps(value, ensure_ascii=False, sort_keys=True, indent=2)+'\n').encode('utf8'))


def verify_sources(read=None):
    read = read or (lambda path: path.read_bytes())
    raw = read(PRIVATE / 'frozen-inputs.private.json')
    require(digest(raw) == FROZEN_SHA, 'frozen source manifest drift')
    manifest = json.loads(raw)
    fixed = {}
    for path, expected in manifest['sources'].items():
        raw = read(ROOT / path)
        require(len(raw) == expected['bytes'] and digest(raw) == expected['sha256'], 'source drift: '+path)
        fixed[path] = raw
    return manifest, fixed


def extent(table, entry, base, directory_rva):
    require(len(table) % 12 == 0, 'unaligned exception directory')
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
    require(len(diagnostic['exact']) == 1 and diagnostic['exact'] == diagnostic['containing'],
            'unique exact/containing pdata required')
    row = diagnostic['exact'][0]
    require(int(row['begin'], 16) == ENTRY, 'pdata entry differs')
    return row


def enforce_bound(size):
    require(0 < size <= MAX_BYTES, 'extent exceeds approved1024B bound')


def validate_decode(code, lines, end):
    require(0 < len(lines) <= MAX_INSTRUCTIONS, 'approved256 instruction bound differs')
    cursor = ENTRY
    for line in lines:
        raw = bytes.fromhex(line['hex'])
        require(raw and int(line['address'], 16) == cursor and
                raw == code[cursor-ENTRY:cursor-ENTRY+len(raw)], 'complete contiguous decode differs')
        cursor += len(raw)
    require(cursor == end and end-ENTRY == len(code), 'decode incomplete')
    require(b''.join(bytes.fromhex(line['hex']) for line in lines) == code, 'decode bytes differ')


def bind_static(fixed):
    report = json.loads(fixed[OLD+'binding.private.json'])
    raw_type, word, method = (fixed[OLD+n] for n in ('type63831.bin', 'slot9-definition.bin', 'method529228.bin'))
    require(len(raw_type) == 70 and raw_type.hex() == report['typeRecord']['hex'] and
            (raw_type[0x44] ^ 0xF4) == 11, 'type/count differs')
    start = (int.from_bytes(raw_type[0x28:0x2C], 'little')+0x9AF313C0) & 0xFFFFFFFF
    require(start == 773617 and start+9 == 773626 and word.hex() == '4c130860' and
            report['vtableDefinition']['index'] == 773626, 'vtable word/index differs')
    require(len(method) == 26 and method.hex() == report['method']['raw']['hex'] and
            (report['method']['owner'], report['method']['declaration']['index'], report['method']['virtualSlot'],
             report['method']['parameterCount']) == (OWNER, METHOD, 9, 0), 'method definition differs')
    records, _ = pickle.loads(fixed[META])
    hits = [(record['index'], method) for record in records for method in record['methods']
            if method['registeredAddress'] == hex(ENTRY)]
    require(len(hits) == 1 and hits[0][0] == OWNER and hits[0][1] == report['method']['declaration'],
            'unique metadata declaration differs')
    signatures = json.loads(fixed[SIGNATURES])
    require(not [method for method in signatures['methods'] if method['index'] == METHOD],
            'zero parameter signature omission differs')
    require(fixed[CALLER_SOURCE][0x52:0x64] == CALLER_SETUP, 'original indirect caller binding differs')
    require(not report['naturalVtableObserved'] and not report['naturalMethodInfoObserved'] and not report['bodyTaken'],
            'static definition boundary differs')
    return report


def verify_aot(cell, slot, report=None):
    require(cell == bytes.fromhex('d0548a4201000000') and len(slot) == 8 and
            int.from_bytes(cell, 'little')+METHOD*8 == SLOT and int.from_bytes(slot, 'little') == ENTRY,
            'ordinary AOT binding differs')
    if report is not None:
        require(report['aot']['slot'] == hex(SLOT) and report['aot']['cellHex'] == cell.hex() and
                report['aot']['slotHex'] == slot.hex(), 'AOT report differs')


def collect(dest):
    report = dict(task='E-01/R3-PROUD-FACTORY-WINDOW', stage='source-gate', inputChecks={},
                  sourceSHA256=digest(Path(__file__).read_bytes()), frozenManifestSHA256=FROZEN_SHA,
                  nativeCPU=0, liveReads=0, xdataBytesRead=0, neighborCalleeBytesRead=0, newCodeWindowCount=0,
                  newREA=0, network=0, targetProcessOperations=0, builds=0)
    try:
        manifest, fixed = verify_sources()
        report['sources'] = manifest['sources']
        binding = bind_static(fixed)
        report['identity'] = dict(typeIndex=OWNER, method=binding['method'], vtableDefinition=binding['vtableDefinition'],
                                  zeroParametersOmittedFromSignatureIndex=True)
        client = Path(subprocess.check_output(['powershell.exe', '-NoProfile', '-ExecutionPolicy', 'Bypass',
                      '-File', str(ROOT/'tools/game_path.ps1')], text=True).strip())
        inputs = {}
        for name, (size, expected) in FIVE.items():
            raw = (client/name).read_bytes()
            report['inputChecks'][name] = dict(bytes=len(raw), sha256=digest(raw))
            require(len(raw) == size and digest(raw) == expected, 'five-input drift: '+name)
            if name in ('YuanShen.exe', 'YuanShen_Data/Managed/Metadata/global-metadata.dat'):
                inputs[name] = raw
        binary = inputs['YuanShen.exe']
        metadata = inputs['YuanShen_Data/Managed/Metadata/global-metadata.dat']
        sys.path.insert(0, str(ROOT))
        from tools.extract_client72 import PE, number
        pe = PE(binary)
        def read(address, size, executable=False):
            offset = pe.offset(address, size, executable=executable)
            return binary[offset:offset+size]
        report['stage'] = 'definition-caller-aot'
        for name, offset, size in [('type63831.bin', 0xE4FE4A, 70), ('slot9-definition.bin', 0x4E08714, 4),
                                   ('method529228.bin', 0x22517A8, 26)]:
            require(metadata[offset:offset+size] == fixed[OLD+name], 'original metadata bytes differ: '+name)
        require(read(CALLER, 320, True) == fixed[CALLER_SOURCE], 'complete original caller differs')
        require(read(0x14D5E2532, 18, True) == CALLER_SETUP, 'original caller setup/call/store differs')
        report['caller'] = dict(entry=hex(CALLER), bytes=320, sha256=digest(fixed[CALLER_SOURCE]),
            setupPC='0x14d5e2532', setupHex=CALLER_SETUP.hex(), site=hex(SITE), callHex='ff9018010000',
            kind='indirect actualklass+118; no direct E8', naturalTargetObserved=False)
        cell = read(CELL, 8)
        slot = read(SLOT, 8)
        verify_aot(cell, slot)
        (dest/'cell.bin').write_bytes(cell)
        (dest/'slot.bin').write_bytes(slot)
        report['aot'] = dict(cell=hex(CELL), cellHex=cell.hex(), cellSHA256=digest(cell), table='0x1428a54d0',
                             slot=hex(SLOT), slotHex=slot.hex(), slotSHA256=digest(slot))
        report['stage'] = 'pdata-before-body'
        nt = number(binary, 0x3C)
        directory_rva, directory_size = struct.unpack_from('<II', binary, nt+24+0x70+3*8)
        table = read(pe.base+directory_rva, directory_size)
        diagnostic = extent(table, ENTRY, pe.base, directory_rva)
        report['exceptionDirectory'] = dict(rva=hex(directory_rva), bytes=directory_size, diagnostic=diagnostic)
        save(dest/'boundary.private.json', report)
        row = select_extent(diagnostic)
        pdata = table[row['offset']:row['offset']+12]
        (dest/'pdata.bin').write_bytes(pdata)
        report.update(pdataHex=pdata.hex(), pdataSHA256=digest(pdata), entry=hex(ENTRY),
                      endExclusive=row['endExclusive'], codeBytes=row['size'])
        save(dest/'pre-body.private.json', report)
        enforce_bound(row['size'])
        code = read(ENTRY, row['size'], True)
        sys.path.insert(0, str(ROOT/'local/migration72/python'))
        import capstone
        from capstone.x86_const import X86_OP_IMM, X86_OP_MEM, X86_REG_RIP
        decoder = capstone.Cs(capstone.CS_ARCH_X86, capstone.CS_MODE_64)
        decoder.detail = True
        lines, calls, rip, returns = [], [], [], []
        for ins in decoder.disasm(code, ENTRY):
            raw = bytes(ins.bytes)
            line = dict(address=hex(ins.address), hex=raw.hex(), mnemonic=ins.mnemonic, operands=ins.op_str)
            lines.append(line)
            if ins.mnemonic == 'ret':
                returns.append(line)
            for operand in ins.operands:
                if ins.mnemonic == 'call' and operand.type == X86_OP_IMM:
                    calls.append(dict(address=hex(ins.address), hex=raw.hex(), target=hex(operand.imm)))
                if operand.type == X86_OP_MEM and operand.mem.base == X86_REG_RIP:
                    rip.append(dict(address=hex(ins.address), hex=raw.hex(), target=hex(ins.address+ins.size+operand.mem.disp),
                                    mnemonic=ins.mnemonic, operands=ins.op_str))
        validate_decode(code, lines, ENTRY+len(code))
        (dest/'code.bin').write_bytes(code)
        save(dest/'code.asm.private.json', lines)
        report.update(stage='complete', passed=True, newCodeWindowCount=1, codeSHA256=digest(code), instructions=len(lines),
            assemblySHA256=digest((dest/'code.asm.private.json').read_bytes()), decoderVersion=capstone.__version__,
            directCalls=calls, ripDataReferences=rip, returns=returns,
            factorySemantics='unknown', factoryReturnTyped41105Proven=False, factoryExecuted=False,
            naturalVtableObserved=False, naturalMethodInfoObserved=False, naturalModeObserved=False,
            parserExecuted=False, resourceReady=False, completeAvatar=False, d02Complete=False)
        save(dest/'extraction.private.json', report)
        print(json.dumps(dict(entry=hex(ENTRY), endExclusive=row['endExclusive'], codeBytes=len(code),
                             codeSHA256=digest(code), instructions=len(lines), directCalls=len(calls), ripReferences=len(rip), returns=len(returns))))
    except Exception as error:
        report.update(passed=False, errorType=type(error).__name__, error=str(error))
        save(dest/'failure.private.json', report)
        raise


def validate_window(files):
    report = json.loads(files['extraction.private.json'])
    row = select_extent(report['exceptionDirectory']['diagnostic'])
    enforce_bound(row['size'])
    require(len(files['pdata.bin']) == 12, 'pdata width differs')
    begin, end, _ = struct.unpack('<III', files['pdata.bin'])
    require(begin+0x140000000 == ENTRY and hex(end+0x140000000) == row['endExclusive'] and
            end-begin == row['size'], 'pdata/raw extent differs')
    code = files['code.bin']
    lines = json.loads(files['code.asm.private.json'])
    require(report['entry'] == hex(ENTRY) and report['endExclusive'] == row['endExclusive'] and
            report['codeBytes'] == row['size'] == len(code) and digest(code) == report['codeSHA256'], 'full original body differs')
    validate_decode(code, lines, int(row['endExclusive'], 16))
    require(report['instructions'] == len(lines), 'decode count differs')
    verify_aot(files['cell.bin'], files['slot.bin'], report)
    require(report['caller']['setupHex'] == CALLER_SETUP.hex() and report['caller']['callHex'] == 'ff9018010000' and
            report['caller']['site'] == hex(SITE) and report['caller']['naturalTargetObserved'] is False, 'original indirect caller differs')
    require(report['passed'] and report['stage'] == 'complete' and report['newCodeWindowCount'] == 1 and
            report['factorySemantics'] == 'unknown', 'mechanical scope differs')
    for key in ('nativeCPU', 'liveReads', 'xdataBytesRead', 'neighborCalleeBytesRead', 'newREA', 'network', 'targetProcessOperations', 'builds',
                'factoryReturnTyped41105Proven', 'factoryExecuted', 'naturalVtableObserved', 'naturalMethodInfoObserved',
                'naturalModeObserved', 'parserExecuted', 'resourceReady', 'completeAvatar', 'd02Complete'):
        require(not report[key], 'unproved semantic promotion: '+key)
    return report


def audit(first, second, output):
    manifest, fixed = verify_sources()
    bind_static(fixed)
    copies = [{name:(directory/name).read_bytes() for name in FILES} for directory in (first, second)]
    require(copies[0] == copies[1], 'two fresh eight original files differ')
    report = validate_window(copies[0])
    require(report['sourceSHA256'] == digest(Path(__file__).read_bytes()) and report['sources'] == manifest['sources'] and
            report['frozenManifestSHA256'] == FROZEN_SHA, 'collector source provenance differs')
    require(report['inputChecks'] == {name:dict(bytes=size, sha256=sha) for name,(size,sha) in FIVE.items()}, 'five complete input hashes differ')
    result = dict(task='E-01/R3-PROUD-FACTORY-WINDOW', passed=True, extraction=report, twoFreshEightFilesEqual=True,
                  artifacts={name:dict(bytes=len(raw), sha256=digest(raw)) for name,raw in copies[0].items()},
                  nativeCPU=0, liveReads=0, newREA=0, currentPEReads=0, factorySemantics='unknown', factoryExecuted=False,
                  factoryReturnTyped41105Proven=False, naturalVtableObserved=False, naturalModeObserved=False,
                  parserExecuted=False, resourceReady=False, d02Complete=False)
    save(output, result)
    raw = output.read_bytes()
    print(json.dumps(dict(passed=True, bytes=len(raw), sha256=digest(raw), codeBytes=report['codeBytes'], instructions=report['instructions'])))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', required=True)
    parser.add_argument('--audit', nargs=2, metavar=('FIRST', 'SECOND'))
    args = parser.parse_args()
    dest = PRIVATE / args.output
    require(args.output and dest.name == args.output and not dest.exists(), 'new private output basename required')
    if args.audit:
        require(dest.suffix == '.json', 'audit requires JSON basename')
        directories = [PRIVATE/name for name in args.audit]
        require(all(directory.name == name for directory,name in zip(directories,args.audit)), 'task-local directory basename required')
        audit(*directories, dest)
    else:
        dest.mkdir(parents=True)
        collect(dest)


if __name__ == '__main__':
    main()
