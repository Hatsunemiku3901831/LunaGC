"""Single approved static index-owner .cctor window; no producer/loader semantics."""
import argparse
import hashlib
import json
import pickle
import struct
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PRIVATE = ROOT/'local/migration72/d02-resume-20261110b/c-proud-index-ctor-window'
META = 'local/migration72/d02-resume-20261108/r3-equipment/metadata.private.pkl'
SIGNATURES = 'local/migration72/evidence/client72-signatures.json'
PRIOR = 'local/migration72/d02-resume-20261110b/c-proud-row-reader/main-third.private.json'
FROZEN_SHA = '5d1117a090088715ce4b8299914ab9932e87d3f2f3a54ae5cb0c98e30964ddc8'
ENTRY, OWNER, METHOD, FIELD = 0x14D7ADBC0, 80063, 663526, 400190
CELL, SLOT = 0x1422F9950, 0x142DB5400
MAX_BYTES, MAX_INSTRUCTIONS = 2048, 512
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
    raw = read(PRIVATE/'frozen-inputs.private.json')
    require(digest(raw) == FROZEN_SHA, 'frozen source manifest drift')
    manifest = json.loads(raw)
    fixed = {}
    for path, expected in manifest['sources'].items():
        raw = read(ROOT/path)
        require(len(raw) == expected['bytes'] and digest(raw) == expected['sha256'], 'source drift: '+path)
        fixed[path] = raw
    return manifest, fixed


def bind_identity(records, signatures, prior):
    owners = [record for record in records if record['index'] == OWNER]
    require(len(owners) == 1, 'unique declaring type differs')
    owner = owners[0]
    fields = [field for field in owner['fields'] if field['index'] == FIELD]
    require(len(fields) == 1 and fields[0] == prior['rowIndexField'], 'field declaration differs')
    field = fields[0]
    typed = field['type']
    require(field['rawOffset'] == '0x4021f58' and typed['attributes'] == '0x31' and
            typed['kind'] == 'genericinst' and typed['typeDefinition'] == 18192 and typed['instanceIndex'] == 5071 and
            [argument.get('typeDefinition', argument['kind']) for argument in typed['arguments']] ==
            ['uint32', 75077, 47647, 25030], 'field closed tuple differs')
    constructors = [method for method in owner['methods'] if method['name'] == '.cctor']
    require(len(constructors) == 1 and constructors[0] == prior['indexOwnerStaticConstructorCandidate'] and
            (constructors[0]['index'], constructors[0]['registeredAddress'], constructors[0]['parameterCount']) ==
            (METHOD, hex(ENTRY), 0), 'unique static constructor differs')
    hits = [(record['index'], method['index']) for record in records for method in record['methods']
            if method['registeredAddress'] == hex(ENTRY)]
    require(hits == [(OWNER, METHOD)], 'unique AOT method declaration differs')
    require(not [method for method in signatures['methods'] if method['index'] == METHOD],
            'zero parameter signature omission differs')
    return dict(ownerIndex=OWNER, ownerName=owner['name'], field=field, method=constructors[0],
                zeroParametersOmittedFromSignatureIndex=True, initializationCandidateOnly=True,
                callerEvidence='none; static field owner .cctor candidate, no E8 caller asserted')


def static_identity(fixed):
    records, _ = pickle.loads(fixed[META])
    signatures = json.loads(fixed[SIGNATURES])
    prior = json.loads(fixed[PRIOR])['selectedDeclarations']
    return bind_identity(records, signatures, prior)


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
    require(0 < size <= MAX_BYTES, 'extent exceeds approved2048B bound')


def validate_decode(code, lines, end):
    require(0 < len(lines) <= MAX_INSTRUCTIONS, 'approved512 instruction bound differs')
    cursor = ENTRY
    for line in lines:
        raw = bytes.fromhex(line['hex'])
        require(raw and int(line['address'], 16) == cursor and
                raw == code[cursor-ENTRY:cursor-ENTRY+len(raw)], 'complete contiguous decode differs')
        cursor += len(raw)
    require(cursor == end and end-ENTRY == len(code), 'decode incomplete')
    require(b''.join(bytes.fromhex(line['hex']) for line in lines) == code, 'decode bytes differ')


def verify_aot(cell, slot, report=None):
    require(cell == bytes.fromhex('d0548a4201000000') and len(slot) == 8 and
            int.from_bytes(cell, 'little')+METHOD*8 == SLOT and int.from_bytes(slot, 'little') == ENTRY,
            'ordinary AOT binding differs')
    if report is not None:
        require(report['aot']['slot'] == hex(SLOT) and report['aot']['cellHex'] == cell.hex() and
                report['aot']['slotHex'] == slot.hex(), 'AOT report differs')


def collect(dest):
    report = dict(task='E-01/R3-PROUD-INDEX-CTOR-WINDOW', stage='source-gate', inputChecks={},
                  sourceSHA256=digest(Path(__file__).read_bytes()), frozenManifestSHA256=FROZEN_SHA,
                  nativeCPU=0, liveReads=0, xdataBytesRead=0, neighborCalleeBytesRead=0, newCodeWindowCount=0,
                  newREA=0, network=0, targetProcessOperations=0, builds=0, closedAotTuplePlanExecuted=False)
    try:
        manifest, fixed = verify_sources()
        report['sources'] = manifest['sources']
        report['identity'] = static_identity(fixed)
        client = Path(subprocess.check_output(['powershell.exe', '-NoProfile', '-ExecutionPolicy', 'Bypass',
                      '-File', str(ROOT/'tools/game_path.ps1')], text=True).strip())
        binary = None
        for name, (size, expected) in FIVE.items():
            raw = (client/name).read_bytes()
            actual = digest(raw)
            report['inputChecks'][name] = dict(bytes=len(raw), sha256=actual)
            require(len(raw) == size and actual == expected, 'five-input drift: '+name)
            if name == 'YuanShen.exe':
                binary = raw
        sys.path.insert(0, str(ROOT))
        from tools.extract_client72 import PE, number
        pe = PE(binary)
        def read(address, size, executable=False):
            offset = pe.offset(address, size, executable=executable)
            return binary[offset:offset+size]
        report['stage'] = 'ordinary-aot'
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
        lines, calls, rip, writes = [], [], [], []
        for ins in decoder.disasm(code, ENTRY):
            raw = bytes(ins.bytes)
            line = dict(address=hex(ins.address), hex=raw.hex(), mnemonic=ins.mnemonic, operands=ins.op_str)
            lines.append(line)
            for index, operand in enumerate(ins.operands):
                if ins.mnemonic == 'call' and operand.type == X86_OP_IMM:
                    calls.append(dict(address=hex(ins.address), hex=raw.hex(), target=hex(operand.imm)))
                if operand.type == X86_OP_MEM:
                    if operand.mem.base == X86_REG_RIP:
                        rip.append(dict(address=hex(ins.address), hex=raw.hex(), target=hex(ins.address+ins.size+operand.mem.disp),
                                        mnemonic=ins.mnemonic, operands=ins.op_str))
                    if operand.access & capstone.CS_AC_WRITE:
                        writes.append(dict(address=hex(ins.address), hex=raw.hex(), mnemonic=ins.mnemonic,
                                           operands=ins.op_str, writtenOperandIndex=index))
        validate_decode(code, lines, ENTRY+len(code))
        (dest/'code.bin').write_bytes(code)
        save(dest/'code.asm.private.json', lines)
        report.update(stage='complete', passed=True, newCodeWindowCount=1, codeSHA256=digest(code), instructions=len(lines),
            assemblySHA256=digest((dest/'code.asm.private.json').read_bytes()), decoderVersion=capstone.__version__,
            directCalls=calls, ripDataReferences=rip, rawMemoryWrites=writes,
            targetSemantics='unknown', indexProducerClosed=False, field400190StoreProven=False, indexLoaderClosed=False,
            cctorExecuted=False, naturalIndexValuesObserved=False, actualRowSpanApproved=False,
            parserExecuted=False, resourceReady=False, completeAvatar=False, d02Complete=False)
        save(dest/'extraction.private.json', report)
        print(json.dumps(dict(entry=hex(ENTRY), endExclusive=row['endExclusive'], codeBytes=len(code),
                             codeSHA256=digest(code), instructions=len(lines), directCalls=len(calls),
                             ripReferences=len(rip), rawMemoryWrites=len(writes))))
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
    require(report['identity']['initializationCandidateOnly'] is True and report['identity']['callerEvidence'] ==
            'none; static field owner .cctor candidate, no E8 caller asserted' and 'caller' not in report,
            'candidate promoted to asserted caller')
    require(report['passed'] and report['stage'] == 'complete' and report['newCodeWindowCount'] == 1 and
            report['targetSemantics'] == 'unknown', 'mechanical scope differs')
    for key in ('nativeCPU', 'liveReads', 'xdataBytesRead', 'neighborCalleeBytesRead', 'newREA', 'network', 'targetProcessOperations', 'builds',
                'closedAotTuplePlanExecuted', 'indexProducerClosed', 'field400190StoreProven', 'indexLoaderClosed',
                'cctorExecuted', 'naturalIndexValuesObserved', 'actualRowSpanApproved', 'parserExecuted', 'resourceReady', 'completeAvatar', 'd02Complete'):
        require(not report[key], 'unproved semantic promotion: '+key)
    return report


def audit(first, second, output):
    manifest, fixed = verify_sources()
    identity = static_identity(fixed)
    copies = [{name:(directory/name).read_bytes() for name in FILES} for directory in (first, second)]
    require(copies[0] == copies[1], 'two fresh eight original files differ')
    report = validate_window(copies[0])
    require(report['identity'] == identity and report['sourceSHA256'] == digest(Path(__file__).read_bytes()) and
            report['sources'] == manifest['sources'] and report['frozenManifestSHA256'] == FROZEN_SHA,
            'collector identity/source provenance differs')
    require(report['inputChecks'] == {name:dict(bytes=size, sha256=sha) for name,(size,sha) in FIVE.items()}, 'five complete input hashes differ')
    result = dict(task='E-01/R3-PROUD-INDEX-CTOR-WINDOW', passed=True, extraction=report, twoFreshEightFilesEqual=True,
                  artifacts={name:dict(bytes=len(raw), sha256=digest(raw)) for name,raw in copies[0].items()},
                  nativeCPU=0, liveReads=0, newREA=0, currentPEReads=0, targetSemantics='unknown',
                  indexProducerClosed=False, field400190StoreProven=False, closedAotTuplePlanExecuted=False,
                  cctorExecuted=False, naturalIndexValuesObserved=False, actualRowSpanApproved=False,
                  parserExecuted=False, resourceReady=False, d02Complete=False)
    save(output, result)
    raw = output.read_bytes()
    print(json.dumps(dict(passed=True, bytes=len(raw), sha256=digest(raw), codeBytes=report['codeBytes'], instructions=report['instructions'])))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', required=True)
    parser.add_argument('--audit', nargs=2, metavar=('FIRST', 'SECOND'))
    args = parser.parse_args()
    dest = PRIVATE/args.output
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
