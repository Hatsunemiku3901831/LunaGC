"""Collect one hash-bound Proud query caller window; never execute the target."""
import argparse
import hashlib
import json
import pickle
import struct
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'local/migration72/d02-resume-20261111b/proud-gate'
REQUEST = OUT / 'query-window-request.private.json'
REQUEST_SHA = '6a1eb6a7e50d0ae042eb3f00c02c7caee958e4184e1cfe84d09dfaf81b2a1f74'
SPEC_SHA = '76443724a51c57cbce14f05c466193beb13863eae4b5187ed08581e152281d49'
CAP_PINS = {'local/migration72/python/capstone/__init__.py': '94571d1314355e261aff0ddfc94985fe8761232ddff6874181fd21efa9fab47f', 'local/migration72/python/capstone/lib/capstone.dll': '76958e18380023a68fd1714fa2e01c594cc6db1955a07ad6937b66e66dc5d6c3'}
BASE, ENTRY, END, SITE = 0x140000000, 0x14a621790, 0x14a621aab, 0x14a6218fa
QUERY = 0x14d6d70f0
FILES = ('boundary.private.json', 'aot-pointer-cell.bin', 'aot-slot.bin', 'candidate-site.bin', 'pdata.bin', 'pre-body.private.json', 'code.bin', 'code.asm.private.json', 'extraction.private.json')


def require(condition, message):
    if not condition:
        raise ValueError(message)


def sha(data):
    return hashlib.sha256(data).hexdigest()


def checked(path, digest, size=None):
    h = hashlib.sha256()
    count = 0
    with path.open('rb') as f:
        while chunk := f.read(1024 * 1024):
            count += len(chunk)
            h.update(chunk)
    require(h.hexdigest() == digest and (size is None or count == size), 'pin mismatch: ' + str(path))
    return {'bytes': count, 'sha256': h.hexdigest()}


def save(path, data):
    if not isinstance(data, bytes):
        data = (json.dumps(data, indent=2, sort_keys=True, ensure_ascii=False) + '\n').encode('utf8')
    with path.open('xb') as f:
        f.write(data)


def verify_declaration(index, records, signatures, request):
    require(len(index['candidates']) == index['totalCandidates'] == 73, 'candidate count differs')
    require(index['candidates'][17] == request['boundedSelection']['selectedCandidate'], 'selected index differs')
    declarations = (request['declaration'], request['queryDeclaration'])
    for declaration in declarations:
        owner_id, method_id = declaration['owner'], declaration['method']
        address = declaration['registeredAddress']
        hits = [(r['index'], m['index']) for r in records for m in r['methods'] if m['registeredAddress'] == address]
        require(hits == [(owner_id, method_id)], 'registered address not unique')
        owner = records[owner_id]
        methods = [m for m in owner['methods'] if m['index'] == method_id]
        require(owner['index'] == owner_id and owner['name'] == declaration['ownerName'] and len(methods) == 1, 'owner/method differs')
        method = methods[0]
        require((method['name'], method['registeredAddress'], method['parameterCount'], method['flags'], method['virtualSlot']) == (declaration['methodName'], address, declaration['parameterCount'], declaration['flags'], declaration['virtualSlot']), 'method declaration differs')
        matches = [m for m in signatures['methods'] if m['index'] == method_id or m['registeredAddress'] == address]
        require(len(matches) == 1, 'signature or address not unique')
        signature = matches[0]
        require((signature['index'], signature['declaringType'], signature['name'], signature['registeredAddress']) == (method_id, owner_id, declaration['methodName'], address), 'signature declaration differs')
        expected = declaration.get('parameters', [declaration.get('parameter')])
        actual = signature['parameters']
        require(len(actual) == len(expected) == declaration['parameterCount'], 'parameter count differs')
        for param, wanted in zip(actual, expected):
            require((param['index'], param['typeIndex']) == (wanted['index'], wanted['typeIndex']), 'parameter index differs')
            typ = signatures['typeReferences'][str(param['typeIndex'])]
            require(typ['kind'] == wanted['kind'] and typ['byRef'] == wanted['byRef'] and typ.get('typeDefinition') == wanted.get('typeDefinition'), 'parameter type differs')


def source_gate():
    checked(REQUEST, REQUEST_SHA, 13531)
    checked(OUT / 'query-window-tool-spec.zh.md', SPEC_SHA, 3806)
    request = json.loads(REQUEST.read_bytes())
    for name, pin in request['sources'].items():
        checked(ROOT / name, pin['sha256'], pin['bytes'])
    for name, digest in CAP_PINS.items():
        checked(ROOT / name, digest)
    names = [ROOT / ('local/migration72/d02-resume-20261111b/proud/' + n) for n in ('gate-index1.private.json', 'gate-index2.private.json', 'gate-index-main-third.private.json')]
    indices = [p.read_bytes() for p in names]
    require(indices[0] == indices[1] == indices[2], 'index bytes differ')
    records, _ = pickle.loads((ROOT / 'local/migration72/d02-resume-20261108/r3-equipment/metadata.private.pkl').read_bytes())
    signatures = json.loads((ROOT / 'local/migration72/evidence/client72-signatures.json').read_bytes())
    verify_declaration(json.loads(indices[0]), records, signatures, request)
    return request


def read_at(f, offset, length):
    f.seek(offset)
    data = f.read(length)
    require(len(data) == length, 'short read')
    return data


def pe_info(f):
    header = read_at(f, 0, 64)
    require(header[:2] == b'MZ', 'missing MZ')
    pe = struct.unpack_from('<I', header, 0x3c)[0]
    coff = read_at(f, pe, 24)
    require(coff[:4] == b'PE\0\0' and struct.unpack_from('<H', coff, 4)[0] == 0x8664, 'not AMD64 PE')
    count, opt_size = struct.unpack_from('<H', coff, 6)[0], struct.unpack_from('<H', coff, 20)[0]
    opt = read_at(f, pe + 24, opt_size)
    require(len(opt) >= 0x90 and struct.unpack_from('<H', opt)[0] == 0x20b and struct.unpack_from('<Q', opt, 24)[0] == BASE, 'image base or PE32+ differs')
    directory = struct.unpack_from('<II', opt, 0x70 + 3 * 8)
    sections = []
    for i in range(count):
        section = read_at(f, pe + 24 + opt_size + 40 * i, 40)
        rva, size, offset = struct.unpack_from('<III', section, 12)
        flags = struct.unpack_from('<I', section, 36)[0]
        sections.append((BASE + rva, size, offset, flags))
    return directory, sections


def offset(sections, address, length, executable=False):
    hits = [raw + address - start for start, size, raw, flags in sections if start <= address and address + length <= start + size and (not executable or flags & 0x20000000)]
    require(len(hits) == 1, 'extent not uniquely file-backed executable' if executable else 'extent not uniquely file-backed')
    return hits[0]


def select_pdata(table, directory_rva, entry=ENTRY, site=SITE, max_code=1024):
    require(len(table) % 12 == 0, 'unaligned pdata')
    exact, containing, site_containing = [], [], []
    for pos in range(0, len(table), 12):
        begin, end, unwind = struct.unpack_from('<III', table, pos)
        row = {'recordRVA': hex(directory_rva + pos), 'recordHex': table[pos:pos+12].hex(), 'begin': hex(BASE + begin), 'endExclusive': hex(BASE + end), 'unwindRVA': hex(unwind), 'size': end-begin}
        if begin == entry - BASE: exact.append(row)
        if begin <= entry - BASE < end: containing.append(row)
        if begin <= site - BASE < end: site_containing.append(row)
    diag = {'exact': exact, 'containing': containing, 'siteContaining': site_containing}
    if len(exact) != 1 or exact != containing or exact != site_containing:
        error = ValueError('pdata exact/containing/site ambiguous')
        error.diagnostic = diag
        raise error
    if not 0 < exact[0]['size'] <= max_code:
        error = ValueError('body byte budget exceeded')
        error.diagnostic = diag
        raise error
    return diag


def check_aot(cell, slots):
    require(len(cell) == 8 and cell.hex() == 'd0548a4201000000', 'actual pointer cell differs')
    table = int.from_bytes(cell, 'little')
    require((table + 540714 * 8, table + 526531 * 8) == (0x142cc5620, 0x142ca9ae8), 'actual slot arithmetic differs')
    require(len(slots) == 16 and slots.hex() == '9017624a01000000f0706d4d01000000', 'actual pointer slots differ')
    require(struct.unpack('<QQ', slots) == (ENTRY, QUERY), 'slot identities differ')


def rel32_target(raw, site):
    require(len(raw) == 5 and raw[0] == 0xe8, 'site is not E8 rel32')
    return site + 5 + struct.unpack('<i', raw[1:])[0]


def check_site(raw, site=SITE, target=None):
    target = QUERY if target is None else target
    require(rel32_target(raw, site) == target, 'E8 target differs')
    require(raw.hex() == 'e8f1570b03', 'candidate bytes differ')


def decode(code, entry=ENTRY, site=SITE, site_bytes=bytes.fromhex('e8f1570b03'), max_instructions=512):
    sys.path.insert(0, str(ROOT / 'local/migration72/python'))
    import capstone
    from capstone.x86_const import X86_OP_IMM, X86_OP_MEM, X86_REG_RIP
    cs = capstone.Cs(capstone.CS_ARCH_X86, capstone.CS_MODE_64)
    cs.detail = True
    lines, refs = [], []
    cursor = entry
    for ins in cs.disasm(code, entry):
        raw = bytes(ins.bytes)
        require(ins.address == cursor and raw == code[cursor-entry:cursor-entry+len(raw)], 'decode gap')
        line = {'address': hex(ins.address), 'hex': raw.hex(), 'mnemonic': ins.mnemonic, 'operands': ins.op_str}
        lines.append(line)
        require(len(lines) <= max_instructions, 'instruction budget exceeded')
        for op in ins.operands:
            if op.type == X86_OP_IMM and (ins.group(capstone.CS_GRP_CALL) or ins.group(capstone.CS_GRP_JUMP)):
                refs.append({'address': hex(ins.address), 'kind': 'call' if ins.group(capstone.CS_GRP_CALL) else 'branch', 'target': hex(op.imm)})
            if op.type == X86_OP_MEM and op.mem.base == X86_REG_RIP:
                refs.append({'address': hex(ins.address), 'kind': 'rip', 'target': hex(ins.address + ins.size + op.mem.disp)})
        cursor += len(raw)
    require(cursor == entry + len(code), 'incomplete decode')
    require(sum(1 for line in lines if int(line['address'], 16) == site and bytes.fromhex(line['hex']) == site_bytes) == 1, 'candidate is not one whole instruction')
    if entry == ENTRY and site == SITE:
        require(any(r == {'address': hex(SITE), 'kind': 'call', 'target': hex(QUERY)} for r in refs), 'decoded E8 target differs')
    return {'lines': lines, 'references': refs, 'capstoneVersion': capstone.__version__}


def collect(dest):
    report = {'task': 'E-01d1/R3-PROUD-ACTUAL-ADOPTION/PROUD-GATE-COMPARISON-QUERY-WINDOW', 'stage': 'source-gate', 'requestSHA256': REQUEST_SHA, 'toolSHA256': sha(Path(__file__).read_bytes()), 'newBodies': 0, 'targetCPU': 0, 'nativeExecution': 0, 'liveReads': 0, 'xdataBytes': 0, 'calleeBodies': 0, 'ripTargetReads': 0}
    try:
        req = source_gate()
        report['stage'] = 'five-stock'
        client = Path(subprocess.check_output(['powershell.exe', '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', str(ROOT / 'tools/game_path.ps1')], text=True).strip())
        report['inputs'] = {name: checked(client / name, pin['sha256'], pin['bytes']) for name, pin in req['fixedClientInputs'].items()}
        with (client / 'YuanShen.exe').open('rb') as f:
            directory, sections = pe_info(f)
            report['stage'] = 'actual-aot'
            cell = read_at(f, offset(sections, 0x1422f9950, 8), 8)
            save(dest / 'aot-pointer-cell.bin', cell)
            require(cell.hex() == 'd0548a4201000000', 'actual pointer cell differs')
            table_va = int.from_bytes(cell, 'little')
            slot_vas = (table_va + 540714 * 8, table_va + 526531 * 8)
            require(slot_vas == (0x142cc5620, 0x142ca9ae8), 'actual slot arithmetic differs')
            # One CreateNew artifact; if the second read fails, the first QWORD remains.
            with (dest / 'aot-slot.bin').open('xb') as slot_file:
                caller_slot = read_at(f, offset(sections, slot_vas[0], 8), 8)
                slot_file.write(caller_slot)
                query_slot = read_at(f, offset(sections, slot_vas[1], 8), 8)
                slot_file.write(query_slot)
            slots = caller_slot + query_slot
            check_aot(cell, slots)
            report['stage'] = 'pdata-before-body'
            require(directory == (0x1a4e6a40, 14344080), 'exception directory extent differs')
            table = read_at(f, offset(sections, BASE + directory[0], directory[1]), directory[1])
            require(sha(table) == '5895dbeef8fe13458720889e318ba64f3fc9c5d3ca03bf01b0adc70eee16c08d', 'exception directory hash differs')
            diag = select_pdata(table, directory[0])
            row = diag['exact'][0]
            report['exceptionDirectory'] = {'rva': hex(directory[0]), 'bytes': len(table), 'sha256': sha(table), 'diagnostic': diag}
            save(dest / 'boundary.private.json', report)
            require(row == {'recordRVA': '0x1a893c9c', 'recordHex': '9017620aab1a620a70510d04', 'begin': hex(ENTRY), 'endExclusive': hex(END), 'unwindRVA': '0x40d5170', 'size': 795}, 'selected pdata differs')
            pdata = bytes.fromhex(row['recordHex'])
            save(dest / 'pdata.bin', pdata)
            body_offset = offset(sections, ENTRY, 795, True)
            site_offset = offset(sections, SITE, 5, True)
            require(site_offset == 0x9dca0fa and site_offset == body_offset + SITE - ENTRY, 'candidate file offset differs')
            candidate = read_at(f, site_offset, 5)
            save(dest / 'candidate-site.bin', candidate)
            check_site(candidate, target=int.from_bytes(query_slot, 'little'))
            report.update(bodyBytes=795, bodyFileOffset=hex(body_offset), pointerTable=hex(table_va), slotVAs=[hex(x) for x in slot_vas], candidateHex=candidate.hex(), queryEntry=hex(int.from_bytes(query_slot, 'little')))
            save(dest / 'pre-body.private.json', report)
            report['stage'] = 'single-body'
            code = read_at(f, body_offset, 795)
            report['newBodies'] = 1
            save(dest / 'code.bin', code)
            decoded = decode(code)
            save(dest / 'code.asm.private.json', decoded['lines'])
            report.update(stage='complete', passed=True, codeSHA256=sha(code), instructionCount=len(decoded['lines']), references=decoded['references'], capstoneVersion=decoded['capstoneVersion'], instructionBoundaryProven=True)
            save(dest / 'extraction.private.json', report)
            print(json.dumps({'passed': True, 'codeSHA256': sha(code), 'instructions': len(decoded['lines']), 'references': len(decoded['references'])}))
    except Exception as e:
        if hasattr(e, 'diagnostic'):
            report['pdataDiagnostic'] = e.diagnostic
        report.update(passed=False, errorType=type(e).__name__, error=str(e))
        save(dest / 'failure.private.json', report)
        raise


def audit(first, second, output):
    source_gate()
    copies = [{n: (p / n).read_bytes() for n in FILES} for p in (first, second)]
    require(copies[0] == copies[1], 'fresh artifacts differ')
    files = copies[0]
    boundary, pre, report = (json.loads(files[n]) for n in ('boundary.private.json', 'pre-body.private.json', 'extraction.private.json'))
    check_aot(files['aot-pointer-cell.bin'], files['aot-slot.bin'])
    require(files['pdata.bin'].hex() == '9017620aab1a620a70510d04' and files['candidate-site.bin'].hex() == 'e8f1570b03', 'raw evidence differs')
    check_site(files['candidate-site.bin'], target=struct.unpack('<QQ', files['aot-slot.bin'])[1])
    diag = report['exceptionDirectory']['diagnostic']
    require(boundary['exceptionDirectory'] == pre['exceptionDirectory'] == report['exceptionDirectory'] and len(diag['exact']) == 1 and diag['exact'] == diag['containing'] == diag['siteContaining'] and diag['exact'][0]['recordHex'] == files['pdata.bin'].hex(), 'boundary audit failed')
    code = files['code.bin']
    decoded = decode(code)
    require(len(code) == 795 and sha(code) == report['codeSHA256'] and decoded['lines'] == json.loads(files['code.asm.private.json']) and decoded['references'] == report['references'] and len(decoded['lines']) == report['instructionCount'], 'decode audit failed')
    require(code[SITE-ENTRY:SITE-ENTRY+5] == files['candidate-site.bin'] and report['passed'] and report['newBodies'] == 1 and report['toolSHA256'] == sha(Path(__file__).read_bytes()), 'report audit failed')
    require(not any(report[k] for k in ('targetCPU', 'nativeExecution', 'liveReads', 'xdataBytes', 'calleeBodies', 'ripTargetReads')), 'scope audit failed')
    result = {'passed': True, 'twoFreshNineFilesByteIdentical': True, 'currentPEReads': 0, 'artifacts': {n: {'bytes': len(b), 'sha256': sha(b)} for n, b in files.items()}, 'instructionCount': len(decoded['lines']), 'references': len(decoded['references'])}
    save(output, result)
    print(json.dumps(result))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', required=True)
    parser.add_argument('--audit', nargs=2)
    args = parser.parse_args()
    require(Path(args.output).name == args.output and not (OUT / args.output).exists(), 'new task-local basename required')
    if args.audit:
        require(args.output.endswith('.json') and all(Path(x).name == x for x in args.audit), 'audit requires task-local inputs')
        audit(*(OUT / x for x in args.audit), OUT / args.output)
    else:
        dest = OUT / args.output
        dest.mkdir()
        collect(dest)


if __name__ == '__main__':
    main()
