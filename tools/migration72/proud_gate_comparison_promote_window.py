"""Collect one hash-bound Proud Promote-context caller window; never execute the target."""
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
REQUEST = OUT / 'promote-window-request.private.json'
REQUEST_SHA = '35cc87447d738bddecd9f2c314b5038cd77034f90f85c5e718dc5f1bde1fb838'
SPEC_SHA = '30e1ac40bd6f5e0ef34d5628b0dd46c23346d913fd34139d49ccfd1acb811ef0'
CAP_PINS = {'local/migration72/python/capstone/__init__.py': '94571d1314355e261aff0ddfc94985fe8761232ddff6874181fd21efa9fab47f', 'local/migration72/python/capstone/lib/capstone.dll': '76958e18380023a68fd1714fa2e01c594cc6db1955a07ad6937b66e66dc5d6c3'}
BASE, ENTRY, END, SITE = 0x140000000, 0x14cbdd560, 0x14cbddd52, 0x14cbdda27
CALLS = ((0x14cbdd5c3, 0x15184adf0, 'e828d8c604'), (0x14cbdd5d4, 0x14af8f5c0, 'e8e71f3bfe'), (0x14cbdd5ed, 0x14af8f5c0, 'e8ce1f3bfe'))
FILES = ('boundary.private.json', 'aot-pointer-cell.bin', 'aot-slot.bin', 'candidate-site.bin', 'provenance-calls.bin', 'pdata.bin', 'pre-body.private.json', 'code.bin', 'code.asm.private.json', 'extraction.private.json')


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


def verify_sources(index, contexts, prior_join, records, signatures, request):
    require(len(index['candidates']) == index['totalCandidates'] == 73 and len(contexts) == 41, 'source counts differ')
    require(index['candidates'][33] == request['selection']['selectedCandidate'], 'selected candidate differs')
    matches = []
    for candidate in index['candidates']:
        for row in contexts:
            if any(int(record['begin'], 16) == BASE + row['function'][0] for record in candidate['containingRecords']):
                matches.append({'candidate': candidate['address'], 'oldContext': row['before'], 'oldDeclaredOwnerMethod': row['method'], 'oldFunctionRVAs': row['function'], 'oldSiteRVA': row['siteRva'], 'originalPdata': candidate['containingRecords'], 'registeredOwners': candidate['registeredOwners']})
    starts = sorted({hex(BASE + m['oldFunctionRVAs'][0]) for m in matches})
    require(len(matches) == 4 and starts == ['0x14cbdd560', '0x151da35a0'], 'context join differs')
    require(matches == prior_join['matches'] and starts == prior_join['uniqueFunctionStarts'] and prior_join['newPEReads'] == 0, 'prior join differs')
    selected = [r for r in contexts if BASE + r['function'][0] == ENTRY]
    require(selected == request['selection']['selectedOriginalContexts'], 'selected original contexts differ')
    for decl in request['declarations']:
        owner_id, method_id, address = decl['owner'], decl['index'], decl['registeredAddress']
        hits = [(r['index'], m['index']) for r in records for m in r['methods'] if m['registeredAddress'] == address]
        require(hits == [(owner_id, method_id)], 'registered address not unique')
        owner = records[owner_id]
        methods = [m for m in owner['methods'] if m['index'] == method_id]
        require(owner['index'] == owner_id and owner['name'] == decl['ownerName'] and len(methods) == 1, 'owner/method differs')
        require(all(methods[0][k] == decl[k] for k in ('index','name','nameIndex','registeredAddress','parameterCount','flags','virtualSlot')), 'method declaration differs')
        sig = [m for m in signatures['methods'] if m['index'] == method_id or m['registeredAddress'] == address]
        if decl['parameterCount'] == 0:
            require(not sig, 'zero parameter method unexpectedly present')
        else:
            require(len(sig) == 1 and sig[0]['index'] == method_id and sig[0]['declaringType'] == owner_id and sig[0]['name'] == decl['name'] and len(sig[0]['parameters']) == decl['parameterCount'], 'query signature differs')
            require([p['typeIndex'] for p in sig[0]['parameters']] == [124,124] and signatures['typeReferences']['124']['kind'] == 'uint32' and not signatures['typeReferences']['124']['byRef'], 'query parameter types differ')
    for field_spec in (request['selectedThisField'], request['phaseCandidateField']):
        fields = [f for f in records[field_spec['owner']]['fields'] if f['index'] == field_spec['field']]
        require(len(fields) == 1, 'field not unique')
        f = fields[0]
        require(f['rawOffset'] == field_spec['offset'] and f['offsetKind'] == 'instance' and f['type']['kind'] == field_spec['kind'] and f['type'].get('typeDefinition') == field_spec.get('typeDefinition') and not f['type']['byRef'], 'field type/offset differs')


def source_gate():
    checked(REQUEST, REQUEST_SHA, 15657)
    checked(OUT / 'promote-window-tool-spec.zh.md', SPEC_SHA, 3702)
    request = json.loads(REQUEST.read_bytes())
    for name, pin in request['sources'].items():
        checked(ROOT / name, pin['sha256'], pin['bytes'])
    for name, digest in CAP_PINS.items():
        checked(ROOT / name, digest)
    index = json.loads((ROOT / 'local/migration72/d02-resume-20261111b/proud/gate-index-main-third.private.json').read_bytes())
    contexts = json.loads((ROOT / 'local/migration72/d02-resume-20261108/r3-promote/avatar-promote-getter-callers.private.json').read_bytes())
    prior = json.loads((OUT / 'root-promote-context-intersection.private.json').read_bytes())
    records, _ = pickle.loads((ROOT / 'local/migration72/d02-resume-20261108/r3-equipment/metadata.private.pkl').read_bytes())
    signatures = json.loads((ROOT / 'local/migration72/evidence/client72-signatures.json').read_bytes())
    verify_sources(index, contexts, prior, records, signatures, request)
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


def select_pdata(table, directory_rva, entry=ENTRY, sites=None, max_code=4096):
    require(len(table) % 12 == 0, 'unaligned pdata')
    sites = [SITE] + [c[0] for c in CALLS] if sites is None else sites
    exact, containing, site_rows = [], [], [[] for _ in sites]
    for pos in range(0, len(table), 12):
        begin, end, unwind = struct.unpack_from('<III', table, pos)
        row = {'recordRVA': hex(directory_rva + pos), 'recordHex': table[pos:pos+12].hex(), 'begin': hex(BASE + begin), 'endExclusive': hex(BASE + end), 'unwindRVA': hex(unwind), 'size': end-begin}
        if begin == entry - BASE: exact.append(row)
        if begin <= entry - BASE < end: containing.append(row)
        for site, rows in zip(sites, site_rows):
            if begin <= site - BASE < end: rows.append(row)
    diag = {'exact': exact, 'containing': containing, 'sites': {hex(site): rows for site, rows in zip(sites, site_rows)}}
    if len(exact) != 1 or exact != containing or any(rows != exact for rows in site_rows):
        error = ValueError('pdata exact/containing/sites ambiguous'); error.diagnostic = diag; raise error
    if not 0 < exact[0]['size'] <= max_code:
        error = ValueError('body byte budget exceeded'); error.diagnostic = diag; raise error
    return diag


def check_aot(cell, slots):
    require(len(cell) == 8 and cell.hex() == 'd0548a4201000000', 'actual pointer cell differs')
    table = int.from_bytes(cell, 'little')
    require(tuple(table+i*8 for i in (198316,138176,176922)) == (0x142a28a30,0x1429b32d0,0x1429feda0), 'actual slot arithmetic differs')
    require(len(slots) == 24 and slots.hex() == '60d5bd4c01000000f0ad845101000000c0f5f84a01000000', 'actual pointer slots differ')
    require(struct.unpack('<QQQ',slots) == (ENTRY,0x15184adf0,0x14af8f5c0), 'slot identities differ')


def rel32_target(raw, site):
    require(len(raw) == 5 and raw[0] == 0xe8, 'site is not E8 rel32')
    return site + 5 + struct.unpack('<i', raw[1:])[0]


def check_calls(raw, targets):
    require(len(raw) == 15, 'provenance call bytes differ')
    for i, (site, target, expected) in enumerate(CALLS):
        data = raw[i*5:i*5+5]
        require(target == targets[0 if i == 0 else 1] and rel32_target(data, site) == target, 'E8 target differs')
        require(data.hex() == expected, 'E8 bytes/order differ')


def decode(code, entry=ENTRY, site=SITE, site_bytes=bytes.fromhex('488bb9b8120200'), max_instructions=1024):
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
        for call_site, target, expected in CALLS:
            require(sum(1 for line in lines if line['address'] == hex(call_site) and line['hex'] == expected) == 1, 'provenance is not one whole instruction')
            require({'address': hex(call_site), 'kind': 'call', 'target': hex(target)} in refs, 'decoded E8 target differs')
    return {'lines': lines, 'references': refs, 'capstoneVersion': capstone.__version__}


def collect(dest):
    report = {'task': 'E-01d1/R3-PROUD-GATE-COMPARISON/PROMOTE-CONTEXT-WINDOW', 'stage': 'source-gate', 'requestSHA256': REQUEST_SHA, 'toolSHA256': sha(Path(__file__).read_bytes()), 'newBodies': 0, 'targetCPU': 0, 'nativeExecution': 0, 'liveReads': 0, 'xdataBytes': 0, 'calleeBodies': 0, 'ripTargetReads': 0}
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
            slot_vas = tuple(table_va + index * 8 for index in (198316,138176,176922))
            require(slot_vas == (0x142a28a30,0x1429b32d0,0x1429feda0), 'actual slot arithmetic differs')
            slot_parts = []
            with (dest / 'aot-slot.bin').open('xb') as slot_file:
                for slot_va in slot_vas:
                    part = read_at(f, offset(sections, slot_va, 8), 8)
                    slot_file.write(part)
                    slot_parts.append(part)
            slots = b''.join(slot_parts)
            check_aot(cell, slots)
            report['stage'] = 'pdata-before-body'
            require(directory == (0x1a4e6a40, 14344080), 'exception directory extent differs')
            table = read_at(f, offset(sections, BASE + directory[0], directory[1]), directory[1])
            require(sha(table) == '5895dbeef8fe13458720889e318ba64f3fc9c5d3ca03bf01b0adc70eee16c08d', 'exception directory hash differs')
            diag = select_pdata(table, directory[0])
            row = diag['exact'][0]
            report['exceptionDirectory'] = {'rva': hex(directory[0]), 'bytes': len(table), 'sha256': sha(table), 'diagnostic': diag}
            save(dest / 'boundary.private.json', report)
            require(row == {'recordRVA': '0x1a9c25b8', 'recordHex': '60d5bd0c52ddbd0ccc263704', 'begin': hex(ENTRY), 'endExclusive': hex(END), 'unwindRVA': '0x43726cc', 'size': 2034}, 'selected pdata differs')
            pdata = bytes.fromhex(row['recordHex'])
            save(dest / 'pdata.bin', pdata)
            body_offset = offset(sections, ENTRY, 2034, True)
            site_offset = offset(sections, SITE, 7, True)
            require(site_offset == 0xc386227 and site_offset == body_offset + SITE - ENTRY, 'candidate file offset differs')
            candidate = read_at(f, site_offset, 7)
            save(dest / 'candidate-site.bin', candidate)
            require(candidate.hex() == '488bb9b8120200', 'candidate bytes differ')
            call_parts = []
            with (dest / 'provenance-calls.bin').open('xb') as call_file:
                for call_site, _, _ in CALLS:
                    call_offset = offset(sections, call_site, 5, True)
                    require(call_offset == body_offset + call_site - ENTRY, 'provenance file offset differs')
                    part = read_at(f, call_offset, 5)
                    call_file.write(part)
                    call_parts.append(part)
            calls = b''.join(call_parts)
            check_calls(calls, struct.unpack('<QQQ',slots)[1:])
            report.update(bodyBytes=2034, bodyFileOffset=hex(body_offset), pointerTable=hex(table_va), slotVAs=[hex(x) for x in slot_vas], candidateHex=candidate.hex(), provenanceSites=[hex(x[0]) for x in CALLS])
            save(dest / 'pre-body.private.json', report)
            report['stage'] = 'single-body'
            code = read_at(f, body_offset, 2034)
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
    check_calls(files['provenance-calls.bin'], struct.unpack('<QQQ',files['aot-slot.bin'])[1:])
    require(files['pdata.bin'].hex() == '60d5bd0c52ddbd0ccc263704' and files['candidate-site.bin'].hex() == '488bb9b8120200', 'raw evidence differs')
    diag = report['exceptionDirectory']['diagnostic']
    require(boundary['exceptionDirectory'] == pre['exceptionDirectory'] == report['exceptionDirectory'] and len(diag['exact']) == 1 and diag['exact'] == diag['containing'] and all(x == diag['exact'] for x in diag['sites'].values()) and diag['exact'][0]['recordHex'] == files['pdata.bin'].hex(), 'boundary audit failed')
    code = files['code.bin']
    decoded = decode(code)
    require(len(code) == 2034 and sha(code) == report['codeSHA256'] and decoded['lines'] == json.loads(files['code.asm.private.json']) and decoded['references'] == report['references'] and len(decoded['lines']) == report['instructionCount'], 'decode audit failed')
    require(code[SITE-ENTRY:SITE-ENTRY+7] == files['candidate-site.bin'] and b''.join(code[site-ENTRY:site-ENTRY+5] for site,_,_ in CALLS) == files['provenance-calls.bin'] and report['passed'] and report['newBodies'] == 1 and report['toolSHA256'] == sha(Path(__file__).read_bytes()), 'report audit failed')
    require(not any(report[k] for k in ('targetCPU', 'nativeExecution', 'liveReads', 'xdataBytes', 'calleeBodies', 'ripTargetReads')), 'scope audit failed')
    result = {'passed': True, 'twoFreshTenFilesByteIdentical': True, 'currentPEReads': 0, 'artifacts': {n: {'bytes': len(b), 'sha256': sha(b)} for n, b in files.items()}, 'instructionCount': len(decoded['lines']), 'references': len(decoded['references'])}
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
