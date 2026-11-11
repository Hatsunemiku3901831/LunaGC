"""Collect the approved single ConfigMeta holder window, offline and fail closed."""
import argparse
import hashlib
import io
import json
import pickle
import struct
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'local/migration72/d02-resume-20261111b/scene-holder-window'
REQUEST = 'local/migration72/d02-resume-20261111b/scene/configmeta-m177845-one-window-request.private.json'
REQUEST_SHA = '913283b2c80f7e9cc9bbbce79aafb467c949320a57f61d0ac4aa69c002755e1a'
BEGIN, END, SITE, TARGET = 0x14d1dad60, 0x14d1daded, 0x14d1dad92, 0x145a5ee48
RAW_SITE = bytes.fromhex('4c8b05af4088f8')
PDATA = bytes.fromhex('60ad1d0dedad1d0d6c2b3d04')


def require(ok, message):
    if not ok:
        raise ValueError(message)


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


def check_file(path, size, expected):
    h = hashlib.sha256()
    count = 0
    with path.open('rb') as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b''):
            h.update(chunk)
            count += len(chunk)
    require(count == size and h.hexdigest() == expected, 'identity drift: ' + path.name)
    return {'bytes': count, 'sha256': h.hexdigest()}


def save(path, obj):
    with path.open('xb') as f:
        f.write((json.dumps(obj, sort_keys=True, indent=2, ensure_ascii=False) + '\n').encode())


class PureData(pickle.Unpickler):
    def find_class(self, module, name):
        raise ValueError('metadata contains global class')


def source_gate(req, raw):
    records, _ = PureData(io.BytesIO(raw['metadata'])).load()
    owner = records[23071]
    field = [x for x in owner['fields'] if x['index'] == 108683]
    method = [x for x in owner['methods'] if x['index'] == 177845]
    require(len(field) == len(method) == 1, 'metadata membership mismatch')
    require(field[0]['type']['typeDefinition'] == 77121 and field[0]['rawOffset'] == '0x400c8e0', 'field mismatch')
    require((records[77121]['namespace'], records[77121]['name']) == ('MoleMole.Config', 'ConfigMetaConfig'), 'field type mismatch')
    require((method[0]['flags'], method[0]['virtualSlot'], method[0]['parameterCount'], method[0]['registeredAddress']) == ('0x96', 65535, 0, hex(BEGIN)), 'method mismatch')
    pre, candidates, join = (json.loads(raw[x]) for x in ('pre', 'candidates', 'join'))
    selected = req['selectedCandidate']
    require(pre['budgetStop'] == 'raw-candidate-limit' and pre['windows'] == 0 and len(pre['candidates']) == 8, 'prior stop mismatch')
    require(pre['located'][2] == selected and pre['candidates'] == candidates['candidates'], 'frozen index mismatch')
    rows = [x for x in join['rows'] if x['candidate'] == selected['candidate']]
    require(len(rows) == 1 and rows[0]['extent'] == selected['containing'][0], 'join extent mismatch')
    bindings = rows[0]['bindings']
    require(len(bindings) == 1 and bindings[0]['owner'] == 23071 and bindings[0]['method'] == method[0] and field[0] in bindings[0]['fields'], 'join metadata mismatch')
    profile = json.loads(raw['profile'])
    require({x['path']: {'bytes': x['size'], 'sha256': x['sha256']} for x in profile['files'].values()} == req['stock'], 'stock profile mismatch')
    return {'owner': 23071, 'method': method[0], 'field': field[0], 'frozenSelection': selected,
            'priorBudgetStop': pre['budgetStop'], 'frozenDirectory': pre['exceptionDirectory']}


class FilePE:
    def __init__(self, f):
        self.f = f
        header = self.read(0, 4096)
        nt = struct.unpack_from('<I', header, 0x3c)[0]
        require(header[:2] == b'MZ' and header[nt:nt+4] == b'PE\0\0' and struct.unpack_from('<H', header, nt+4)[0] == 0x8664, 'PE mismatch')
        opt = struct.unpack_from('<H', header, nt+20)[0]
        require(struct.unpack_from('<H', header, nt+24)[0] == 0x20b, 'PE32+ mismatch')
        self.base = struct.unpack_from('<Q', header, nt+48)[0]
        count = struct.unpack_from('<H', header, nt+6)[0]
        start = nt+24+opt
        table = self.read(start, count*40)
        self.sections = []
        for i in range(count):
            off = i*40
            rva, size, raw = struct.unpack_from('<III', table, off+12)
            flags = struct.unpack_from('<I', table, off+36)[0]
            self.sections.append((self.base+rva, size, raw, flags))
        self.pdata_rva, self.pdata_size = struct.unpack_from('<II', header, nt+24+0x70+3*8)

    def read(self, offset, size):
        self.f.seek(offset)
        raw = self.f.read(size)
        require(len(raw) == size, 'short read')
        return raw

    def at(self, va, size, executable=False):
        for start, length, raw, flags in self.sections:
            if start <= va and va+size <= start+length and (not executable or flags & 0x20000000):
                return self.read(raw+va-start, size)
        raise ValueError('address is not file-backed')


def decode(code):
    sys.path.insert(0, str(ROOT / 'local/migration72/python'))
    import capstone
    from capstone.x86_const import X86_OP_IMM, X86_OP_MEM, X86_REG_RIP
    cs = capstone.Cs(capstone.CS_ARCH_X86, capstone.CS_MODE_64)
    cs.detail = True
    rows, cursor = [], BEGIN
    for ins in cs.disasm(code, BEGIN):
        raw = bytes(ins.bytes)
        require(ins.address == cursor and raw == code[cursor-BEGIN:cursor-BEGIN+len(raw)], 'decode gap')
        rip = [hex(ins.address+ins.size+op.mem.disp) for op in ins.operands if op.type == X86_OP_MEM and op.mem.base == X86_REG_RIP]
        direct = hex(ins.operands[0].imm) if ins.mnemonic == 'call' and ins.operands and ins.operands[0].type == X86_OP_IMM else None
        rows.append({'address': hex(ins.address), 'bytes': raw.hex(), 'mnemonic': ins.mnemonic, 'operands': ins.op_str, 'ripTargets': rip, 'directCall': direct})
        cursor += len(raw)
        require(len(rows) <= 141, 'instruction budget exceeded')
    require(cursor == END and bytes.fromhex(''.join(x['bytes'] for x in rows)) == code, 'incomplete decode')
    site = [x for x in rows if x['address'] == hex(SITE)]
    require(len(site) == 1 and site[0]['bytes'] == RAW_SITE.hex() and site[0]['ripTargets'] == [hex(TARGET)], 'site is not exact instruction and target')
    return rows


def collect(dest):
    result = {'task': 'F-03d3s2/CONFIGMETA-M177845-ONE-WINDOW', 'stage': 'source-gate', 'nativeCPU': 0, 'live': 0, 'calleeBodies': 0, 'xdataBytes': 0, 'windows': 0}
    try:
        check_file(ROOT/REQUEST, 6043, REQUEST_SHA)
        req = json.loads((ROOT/REQUEST).read_bytes())
        pins = {x['path']: x for x in req['sources']}
        result['sources'] = {p: check_file(ROOT/p, x['bytes'], x['sha256']) for p, x in pins.items()}
        cap = {'local/migration72/python/capstone/__init__.py': '94571d1314355e261aff0ddfc94985fe8761232ddff6874181fd21efa9fab47f', 'local/migration72/python/capstone/lib/capstone.dll': '76958e18380023a68fd1714fa2e01c594cc6db1955a07ad6937b66e66dc5d6c3'}
        for p, expected in cap.items():
            check_file(ROOT/p, (ROOT/p).stat().st_size, expected)
        paths = list(pins)
        raw = {'profile': (ROOT/paths[0]).read_bytes(), 'metadata': (ROOT/paths[1]).read_bytes(), 'pre': (ROOT/paths[3]).read_bytes(), 'candidates': (ROOT/paths[4]).read_bytes(), 'join': (ROOT/paths[5]).read_bytes()}
        result['metadataGate'] = source_gate(req, raw)
        game = Path(subprocess.check_output(['powershell.exe', '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', str(ROOT/'tools/game_path.ps1')], text=True).strip())
        result['stage'] = 'five-stock'
        result['stock'] = {p: check_file(game/p, x['bytes'], x['sha256']) for p, x in req['stock'].items()}
        with (game/'YuanShen.exe').open('rb') as f:
            pe = FilePE(f)
            result['stage'] = 'pre-body'
            cell = pe.at(0x1422f9950, 8)
            pointer = int.from_bytes(cell, 'little')
            slot_va = pointer + 177845*8
            slot = pe.at(slot_va, 8)
            require(int.from_bytes(slot, 'little') == BEGIN, 'AOT slot mismatch')
            require(pe.pdata_rva == int(result['metadataGate']['frozenDirectory']['rva'],16) and pe.pdata_size == result['metadataGate']['frozenDirectory']['bytesScanned'], 'pdata directory mismatch')
            pdata = pe.at(pe.base+pe.pdata_rva+5296104, 12)
            require(pdata == PDATA and struct.unpack('<III', pdata)[:2] == (BEGIN-pe.base, END-pe.base), 'pdata record mismatch')
            site = pe.at(SITE, 7, executable=True)
            require(site == RAW_SITE, 'raw site mismatch')
            for name, value in [('pointer-cell.bin', cell), ('pointer-slot.bin', slot), ('record.pdata.bin', pdata), ('raw-site.bin', site)]:
                (dest/name).write_bytes(value)
            result.update(aot={'cellVA': '0x1422f9950', 'cellHex': cell.hex(), 'pointer': hex(pointer), 'slotVA': hex(slot_va), 'slotHex': slot.hex()}, pdata={'recordRVA': '0x1a9f3a28', 'recordHex': pdata.hex(), 'frozenUniqueMultiplicity': [1]}, rawSite={'address': hex(SITE), 'bytes': site.hex(), 'status': 'raw-only'}, bodyBudget=141)
            save(dest/'pre-body.private.json', result)
            code = pe.at(BEGIN, END-BEGIN, executable=True)
            (dest/'body.bin').write_bytes(code)
            result['windows'] = 1
            rows = decode(code)
            save(dest/'body.asm.private.json', rows)
            result.update(stage='complete', passed=True, body={'begin': hex(BEGIN), 'endExclusive': hex(END), 'bytes': len(code), 'sha256': sha(code), 'instructionCount': len(rows)}, rawSite={'address': hex(SITE), 'bytes': site.hex(), 'status': 'instruction-boundary-and-rip-confirmed', 'target': hex(TARGET)}, directCalls=[{'address': x['address'], 'target': x['directCall']} for x in rows if x['directCall']], returns=[x['address'] for x in rows if x['mnemonic'].startswith('ret')])
            save(dest/'extraction.private.json', result)
            print(json.dumps({'stage': result['stage'], 'bodySHA256': sha(code), 'instructions': len(rows)}))
    except Exception as e:
        result.update(passed=False, errorType=type(e).__name__, error=str(e))
        save(dest/'failure.private.json', result)
        raise


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', required=True)
    args = parser.parse_args()
    require(Path(args.output).name == args.output and not (OUT/args.output).exists(), 'new task-local basename required')
    OUT.mkdir(parents=True, exist_ok=True)
    dest = OUT/args.output
    dest.mkdir()
    collect(dest)


if __name__ == '__main__':
    main()
