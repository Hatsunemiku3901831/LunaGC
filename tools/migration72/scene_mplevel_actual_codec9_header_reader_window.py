"""Extract one pre-approved native reader window without executing target code."""
import argparse
import hashlib
import json
import struct
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'local/migration72/d02-resume-20261111b/scene-codec9-header-reader-window'
REQUEST = 'local/migration72/d02-resume-20261111b/scene-header-conflict/codec9-header-reader-source-request.private.json'
REQUEST_SHA = 'df478a7797a0632e10ebecdd1a9cf88286a5249e0eb93db7396c041afe08b2fc'
CALL, ENTRY = 0x141c80707, 0x141d265c0
CALL_BYTES = bytes.fromhex('e8b45e0a00')
EXTRA = {'tools/game_path.ps1': '9d89da73dbb8479507eaf00fbe12f523b3472ca05e4aaa20602e2eb196cefa3b',
         'local/migration72/python/capstone/__init__.py': '94571d1314355e261aff0ddfc94985fe8761232ddff6874181fd21efa9fab47f',
         'local/migration72/python/capstone/lib/capstone.dll': '76958e18380023a68fd1714fa2e01c594cc6db1955a07ad6937b66e66dc5d6c3'}
FILES = ('boundary.private.json', 'pre-body.private.json', 'body.bin', 'body.asm.private.json', 'extraction.private.json')


def require(ok, message):
    if not ok:
        raise ValueError(message)


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


def check(path, size, digest):
    h = hashlib.sha256()
    n = 0
    with path.open('rb') as f:
        for block in iter(lambda: f.read(1024 * 1024), b''):
            h.update(block)
            n += len(block)
    require((size is None or n == size) and h.hexdigest() == digest, 'pin mismatch: ' + path.name)
    return {'bytes': n, 'sha256': h.hexdigest()}


def save(path, obj):
    with path.open('xb') as f:
        f.write((json.dumps(obj, sort_keys=True, ensure_ascii=False, indent=2) + '\n').encode())


def source_gate():
    check(ROOT / REQUEST, None, REQUEST_SHA)
    req = json.loads((ROOT / REQUEST).read_bytes())
    fixed = req['frozenSource']
    pins = {req['profile']['path']: req['profile'], fixed['file']: fixed, **req['sourceTools']}
    checks = {p: check(ROOT / p, v['bytes'], v['sha256']) for p, v in pins.items()}
    checks.update({p: check(ROOT / p, None, digest) for p, digest in EXTRA.items()})
    profile = json.loads((ROOT / req['profile']['path']).read_bytes())
    require({x['path']: {'bytes': x['size'], 'sha256': x['sha256']} for x in profile['files'].values()} == req['stock'], 'profile stock mismatch')
    records = json.loads((ROOT / fixed['file']).read_bytes())
    selected = [r for r in records if r['entry'] == fixed['recordEntry']]
    require(len(selected) == 1 and selected[0]['sha256'] == fixed['recordCodeSHA256'], 'frozen source mismatch')
    require([line for line in selected[0]['lines'] if line.startswith('141c80707:')] == ['141c80707: call 0x141d265c0'], 'saved instruction boundary mismatch')
    require(req['binding']['expectedRawE8Hex'] == CALL_BYTES.hex() and req['binding']['exactTarget'] == hex(ENTRY), 'request binding mismatch')
    return req, checks


class FilePE:
    def __init__(self, f):
        self.f = f
        header = self.read(0, 4096)
        nt = struct.unpack_from('<I', header, 0x3c)[0]
        require(header[:2] == b'MZ' and header[nt:nt+4] == b'PE\0\0' and struct.unpack_from('<H', header, nt+4)[0] == 0x8664 and struct.unpack_from('<H', header, nt+24)[0] == 0x20b, 'PE mismatch')
        self.base = struct.unpack_from('<Q', header, nt+48)[0]
        require(self.base == 0x140000000, 'image base mismatch')
        count, opt = struct.unpack_from('<H', header, nt+6)[0], struct.unpack_from('<H', header, nt+20)[0]
        table = self.read(nt+24+opt, count*40)
        self.sections = []
        for i in range(count):
            off = i*40
            rva, size, raw = struct.unpack_from('<III', table, off+12)
            flags = struct.unpack_from('<I', table, off+36)[0]
            self.sections.append({'begin': self.base+rva, 'end': self.base+rva+size, 'raw': raw, 'flags': flags})
        self.pdata_rva, self.pdata_size = struct.unpack_from('<II', header, nt+24+0x70+3*8)

    def read(self, offset, size):
        self.f.seek(offset)
        raw = self.f.read(size)
        require(len(raw) == size, 'short read')
        return raw

    def section(self, va, size, executable=False):
        matches = [s for s in self.sections if s['begin'] <= va and va+size <= s['end'] and (not executable or s['flags'] & 0x20000000)]
        require(len(matches) == 1, 'section missing or ambiguous')
        return matches[0]

    def at(self, va, size, executable=False):
        s = self.section(va, size, executable)
        return self.read(s['raw']+va-s['begin'], size)


def call_gate(raw):
    require(raw == CALL_BYTES and CALL+5+struct.unpack('<i', raw[1:])[0] == ENTRY, 'caller E8 mismatch')


def extent(table, base, rva):
    require(len(table) % 12 == 0, 'unaligned exception directory')
    exact, containing = [], []
    for offset, (begin, end, unwind) in enumerate(struct.iter_unpack('<III', table)):
        if begin == ENTRY-base or begin <= ENTRY-base < end:
            row = {'begin': hex(base+begin), 'endExclusive': hex(base+end), 'unwindRVA': hex(unwind),
                   'recordRVA': hex(rva+offset*12), 'recordHex': table[offset*12:offset*12+12].hex(), 'size': end-begin}
            if begin == ENTRY-base:
                exact.append(row)
            if begin <= ENTRY-base < end:
                containing.append(row)
    return {'exact': exact, 'containing': containing}


def choose(diag):
    require(len(diag['exact']) == 1 and diag['exact'] == diag['containing'], 'pdata missing or ambiguous')
    row = diag['exact'][0]
    require(0 < row['size'] <= 4096, 'body budget exceeded')
    return row


def decode(code):
    sys.path.insert(0, str(ROOT / 'local/migration72/python'))
    import capstone
    from capstone.x86_const import X86_OP_IMM, X86_OP_MEM, X86_REG_RIP
    cs = capstone.Cs(capstone.CS_ARCH_X86, capstone.CS_MODE_64)
    cs.detail = True
    rows, cursor = [], ENTRY
    for ins in cs.disasm(code, ENTRY):
        raw = bytes(ins.bytes)
        require(ins.address == cursor and raw == code[cursor-ENTRY:cursor-ENTRY+len(raw)], 'decode gap or overlap')
        branch = ins.group(capstone.CS_GRP_JUMP)
        call = ins.group(capstone.CS_GRP_CALL)
        direct = hex(ins.operands[0].imm) if (branch or call) and ins.operands and ins.operands[0].type == X86_OP_IMM else None
        rip = [hex(ins.address+ins.size+op.mem.disp) for op in ins.operands if op.type == X86_OP_MEM and op.mem.base == X86_REG_RIP]
        rows.append({'address': hex(ins.address), 'size': ins.size, 'hex': raw.hex(), 'mnemonic': ins.mnemonic,
                     'operands': ins.op_str, 'branch': branch, 'call': call, 'directTarget': direct, 'ripTargets': rip})
        cursor += ins.size
        require(len(rows) <= 2048, 'instruction budget exceeded')
    require(cursor == ENTRY+len(code), 'incomplete decode')
    return rows


def collect(dest):
    report = {'task': 'F-03d3s2/CODEC9-INTERNAL-HEADER-READER-WINDOW', 'stage': 'source-gate', 'nativeCPU': 0, 'live': 0,
              'xdataBytes': 0, 'streamBytes': 0, 'newDataTables': 0, 'newHelpers': 0, 'newBodies': 0,
              'sceneReady': False, 'D02Ready': False, 'toolSHA256': sha(Path(__file__).read_bytes())}
    try:
        req, report['pins'] = source_gate()
        report['stage'] = 'five-stock'
        game = Path(subprocess.check_output(['powershell.exe', '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', str(ROOT/'tools/game_path.ps1')], text=True).strip())
        report['stock'] = {p: check(game/p, v['bytes'], v['sha256']) for p, v in req['stock'].items()}
        with (game/'YuanShen.exe').open('rb') as f:
            pe = FilePE(f)
            report['stage'] = 'caller-gate'
            raw = pe.at(CALL, 5, True)
            call_gate(raw)
            report['caller'] = {'address': hex(CALL), 'hex': raw.hex(), 'target': hex(ENTRY), 'savedInstructionBoundary': True}
            report['stage'] = 'pdata-before-body'
            table = pe.at(pe.base+pe.pdata_rva, pe.pdata_size)
            report['exceptionDirectory'] = {'rva': hex(pe.pdata_rva), 'bytes': pe.pdata_size, 'diagnostic': extent(table, pe.base, pe.pdata_rva)}
            save(dest/'boundary.private.json', report)
            row = choose(report['exceptionDirectory']['diagnostic'])
            sec = pe.section(ENTRY, row['size'], True)
            report['selected'] = row
            report['executableSection'] = {'begin': hex(sec['begin']), 'endExclusive': hex(sec['end']), 'flags': hex(sec['flags'])}
            report['budget'] = {'maxBodyBytes': 4096, 'bodyBytes': row['size'], 'maxInstructions': 2048, 'xdataBytes': 0}
            save(dest/'pre-body.private.json', report)
            code = pe.at(ENTRY, row['size'], True)
            (dest/'body.bin').write_bytes(code)
            report['newBodies'] = 1
            rows = decode(code)
            save(dest/'body.asm.private.json', rows)
            outside = [r for r in rows if r['branch'] and (r['directTarget'] is None or not ENTRY <= int(r['directTarget'], 16) < ENTRY+len(code))]
            report.update(stage='complete', passed=True, body={'begin': hex(ENTRY), 'endExclusive': row['endExclusive'], 'bytes': len(code), 'sha256': sha(code), 'instructions': len(rows)},
                          assemblySHA256=sha((dest/'body.asm.private.json').read_bytes()), calls=[r for r in rows if r['call']], branches=[r for r in rows if r['branch']],
                          ripReferences=[r for r in rows if r['ripTargets']], externalBranches=outside, sourceCoverage='incomplete-external-branches' if outside else 'complete-linear-window',
                          semanticClaims='not analyzed; root review required')
            save(dest/'extraction.private.json', report)
            print(json.dumps({'bytes': len(code), 'sha256': sha(code), 'instructions': len(rows), 'externalBranches': len(outside)}))
    except Exception as exc:
        report.update(passed=False, errorType=type(exc).__name__, error=str(exc))
        save(dest/'failure.private.json', report)
        raise


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--output', required=True)
    a = p.parse_args()
    require(Path(a.output).name == a.output and not (OUT/a.output).exists(), 'new task-local basename required')
    OUT.mkdir(parents=True, exist_ok=True)
    dest = OUT/a.output
    dest.mkdir()
    collect(dest)


if __name__ == '__main__':
    main()

