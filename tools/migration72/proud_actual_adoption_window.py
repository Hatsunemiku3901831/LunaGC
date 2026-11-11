"""Extract the single approved closed TryInsert body, offline and hash-bound."""
import argparse
import hashlib
import json
import pickle
import struct
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'local/migration72/d02-resume-20261111b/proud-insert-window'
REQUEST = 'local/migration72/d02-resume-20261111b/proud/tryinsert-window-request.json'
REQUEST_SHA = '820a132304496e5f6af3e8f215c3f9b5958394b3c122c0a476d568415ba6238c'
SOURCE_SCRIPT = 'tools/migration72/proud_actual_adoption_sources.py'
AOT = 'local/migration72/d02-resume-20261110b/c-proud-actual-fields/closed-aot-final1.private.json'
PICKLE = 'local/migration72/d02-resume-20261108/r3-equipment/metadata.private.pkl'
STORE = 'local/migration72/d02-resume-20261111b/proud/store2/code.bin'
ENTRY, CALLER, CALL = 0x1593e7bf0, 0x154d40010, 0x154d400ab
SLOT = 0x1425fc4a0
CALL_BYTES = bytes.fromhex('e8407b6a04')
EXTRA_PINS = {
    'tools/game_path.ps1': '9d89da73dbb8479507eaf00fbe12f523b3472ca05e4aaa20602e2eb196cefa3b',
    'local/migration72/python/capstone/__init__.py': '94571d1314355e261aff0ddfc94985fe8761232ddff6874181fd21efa9fab47f',
    'local/migration72/python/capstone/lib/capstone.dll': '76958e18380023a68fd1714fa2e01c594cc6db1955a07ad6937b66e66dc5d6c3',
}
FILES = ('boundary.private.json', 'slot.bin', 'pdata.bin', 'pre-body.private.json',
         'code.bin', 'code.asm.private.json', 'extraction.private.json')
sys.path.insert(0, str(ROOT))


def sha(data):
    return hashlib.sha256(data).hexdigest()


def require(condition, message):
    if not condition:
        raise ValueError(message)


def checked(path, digest, size=None):
    raw = path.read_bytes()
    require(sha(raw) == digest and (size is None or len(raw) == size), 'pin mismatch: ' + str(path))
    return raw


def save(path, obj):
    with path.open('xb') as f:
        f.write((json.dumps(obj, sort_keys=True, ensure_ascii=False, indent=2) + '\n').encode('utf-8'))


def source_gate():
    request = json.loads(checked(ROOT / REQUEST, REQUEST_SHA))
    fixed = {}
    for name, info in request['sources'].items():
        fixed[name] = checked(ROOT / name, info['sha256'], info['bytes'])
    for name, digest in request['inheritedPins'].items():
        fixed[name] = checked(ROOT / name, digest)
    for name, digest in EXTRA_PINS.items():
        fixed[name] = checked(ROOT / name, digest)
    reports = [json.loads(fixed[p]) for p in request['sources'] if p.endswith(('sources1.private.json', 'sources2.private.json'))]
    require(len(reports) == 2 and reports[0] == reports[1], 'two source reports differ')
    target = request['closedAOT']
    source = reports[0]
    require(source['insertTarget'] == target and source['dictionaryUsage'] == request['typedDictionaryUsage'], 'typed source differs')
    require(source['inputSHA256'] == request['fixedClientInputs'] and source['pins'] == request['inheritedPins'], 'inherited pins or stock differ')
    require(target == {'address': hex(ENTRY), 'classInstance': 50461, 'method': 10196,
        'methodInstance': -1, 'methodSpec': 294419, 'name': 'TryInsert', 'owner': 1222,
        'pointerIndex': 202494, 'pointerQWORD': 'f07b3e5901000000', 'pointerSlot': hex(SLOT),
        'specRecord12': 'd42700001dc50000ffffffff', 'tableIndex': 46522,
        'tableRecord14': 'fe160300137e0400ffffb5f50000'}, 'closed AOT differs')
    records, cells = pickle.loads(fixed[PICKLE])
    typ = cells[0x145935d18]['type']
    method = [m for m in records[1222]['methods'] if m['index'] == 10196]
    require(cells[0x145935d18] == request['typedDictionaryUsage'], 'typed usage cache differs')
    require((typ['typeDefinition'], typ['instanceIndex']) == (1222, 50461) and
        [(a['kind'], a.get('typeDefinition')) for a in typ['arguments']] == [('valuetype', 80067), ('uint32', None)], 'generic dictionary differs')
    require(len(method) == 1 and method[0]['name'] == 'TryInsert', 'method definition differs')
    caller = fixed[STORE]
    require(len(caller) == 178, 'Store extent differs')
    check_call(caller[CALL-CALLER:CALL-CALLER+5])
    require(request['directSource']['bytes'] == CALL_BYTES.hex(), 'request E8 differs')
    require(request['limits'] == {'calleeBodies': 0, 'live': 0, 'maxCodeBytes': 4096,
        'maxInstructions': 1024, 'maxPdataBytes': 12, 'newBodies': 1,
        'targetCPU': 0, 'targetEntry': hex(ENTRY), 'xdataBytes': 0}, 'request bounds differ')
    return request, fixed


def check_call(raw):
    require(raw == CALL_BYTES and raw[0] == 0xe8 and CALL + 5 + struct.unpack('<i', raw[1:])[0] == ENTRY, 'Store E8 differs')


def verify_aot(metadata, aot, target):
    # Table offsets are absolute offsets in global-metadata.dat, not PE VAs.
    from tools.migration72.proud_actual_adoption_sources import selected_insert
    row = selected_insert(metadata, aot)
    expected = dict(target)
    expected.pop('pointerQWORD')
    require(row == expected, 'unique AOT selected row differs')
    return row


def extent(table, entry, base, rva):
    require(len(table) % 12 == 0, 'unaligned exception directory')
    exact, containing = [], []
    for offset in range(0, len(table), 12):
        begin, end, unwind = struct.unpack_from('<III', table, offset)
        if begin == entry - base or begin <= entry - base < end:
            row = {'begin': hex(base + begin), 'endExclusive': hex(base + end),
                   'unwindRVA': hex(unwind), 'recordRVA': hex(rva + offset),
                   'offset': offset, 'size': end - begin}
            if begin == entry - base:
                exact.append(row)
            if begin <= entry - base < end:
                containing.append(row)
    return {'exact': exact, 'containing': containing}


def choose(diag):
    require(len(diag['exact']) == 1 and diag['exact'] == diag['containing'], 'ambiguous pdata')
    row = diag['exact'][0]
    require(row['begin'] == hex(ENTRY) and 0 < row['size'] <= 4096, 'body extent outside bound')
    return row


def decode(code):
    sys.path.insert(0, str(ROOT / 'local/migration72/python'))
    import capstone
    from capstone.x86_const import X86_OP_IMM, X86_OP_MEM, X86_REG_RIP
    cs = capstone.Cs(capstone.CS_ARCH_X86, capstone.CS_MODE_64)
    cs.detail = True
    lines, calls, branches, rip, memory, stack = [], [], [], [], [], []
    for i in cs.disasm(code, ENTRY):
        line = {'address': hex(i.address), 'hex': bytes(i.bytes).hex(), 'mnemonic': i.mnemonic, 'operands': i.op_str}
        lines.append(line)
        require(len(lines) <= 1024, 'instruction bound exceeded')
        if i.mnemonic in ('push', 'pop', 'call', 'ret'):
            stack.append(line)
        if i.group(capstone.CS_GRP_JUMP):
            branches.append(dict(line, target=hex(i.operands[0].imm) if i.operands and i.operands[0].type == X86_OP_IMM else None))
        if i.group(capstone.CS_GRP_CALL):
            calls.append(dict(line, target=hex(i.operands[0].imm) if i.operands and i.operands[0].type == X86_OP_IMM else None))
        for n, operand in enumerate(i.operands):
            if operand.type == X86_OP_MEM:
                memory.append(dict(line, operandIndex=n, access=operand.access))
                if operand.mem.base == X86_REG_RIP:
                    rip.append(dict(line, operandIndex=n, target=hex(i.address + i.size + operand.mem.disp)))
    require(lines, 'empty decode')
    cursor = ENTRY
    for line in lines:
        raw = bytes.fromhex(line['hex'])
        require(int(line['address'], 16) == cursor and code[cursor-ENTRY:cursor-ENTRY+len(raw)] == raw, 'decode gap')
        cursor += len(raw)
    require(cursor == ENTRY + len(code), 'incomplete decode')
    return {'lines': lines, 'calls': calls, 'branches': branches, 'ripReferences': rip,
            'memoryOperands': memory, 'implicitStackInstructions': stack, 'capstoneVersion': capstone.__version__}


def collect(dest):
    report = {'task': 'R3-PROUD-ACTUAL-ADOPTION/HOT-TRYINSERT-WINDOW', 'stage': 'source-gate',
              'toolSHA256': sha(Path(__file__).read_bytes()), 'requestSHA256': REQUEST_SHA,
              'nativeCPU': 0, 'live': 0, 'calleeBodies': 0, 'xdataBytes': 0, 'newBodies': 0,
              'actualMapObserved': False, 'targetSemanticsProven': False, 'resourceReady': False}
    try:
        request, fixed = source_gate()
        report['stage'] = 'five-stock'
        client = Path(subprocess.check_output(['powershell.exe', '-NoProfile', '-ExecutionPolicy', 'Bypass',
            '-File', str(ROOT / 'tools/game_path.ps1')], text=True).strip())
        inputs = {name: checked(client / name, digest) for name, digest in request['fixedClientInputs'].items()}
        report['inputChecks'] = {name: {'bytes': len(raw), 'sha256': sha(raw)} for name, raw in inputs.items()}
        binary = inputs['YuanShen.exe']
        metadata = inputs['YuanShen_Data/Managed/Metadata/global-metadata.dat']
        from tools.extract_client72 import PE, number
        pe = PE(binary)
        def read(va, length, executable=False):
            off = pe.offset(va, length, executable=executable)
            return binary[off:off+length]
        report['stage'] = 'original-e8'
        original_call = read(CALL, 5, True)
        check_call(original_call)
        report['originalE8'] = original_call.hex()
        report['stage'] = 'closed-aot'
        target = verify_aot(metadata, json.loads(fixed[AOT]), request['closedAOT'])
        require(int(json.loads(fixed[AOT])['tables']['pointerBase'], 16) + target['pointerIndex'] * 8 == SLOT, 'slot arithmetic differs')
        slot = read(SLOT, 8)
        require(slot.hex() == request['closedAOT']['pointerQWORD'] and int.from_bytes(slot, 'little') == ENTRY, 'AOT slot differs')
        (dest / 'slot.bin').write_bytes(slot)
        report['aot'] = target
        report['stage'] = 'pdata-before-body'
        nt = number(binary, 0x3c)
        directory_rva, directory_size = struct.unpack_from('<II', binary, nt + 24 + 0x70 + 3 * 8)
        table = read(pe.base + directory_rva, directory_size)
        diagnostic = extent(table, ENTRY, pe.base, directory_rva)
        report['exceptionDirectory'] = {'rva': hex(directory_rva), 'bytesScanned': directory_size, 'diagnostic': diagnostic}
        save(dest / 'boundary.private.json', report)
        row = choose(diagnostic)
        pdata = table[row['offset']:row['offset']+12]
        require(len(pdata) == 12, 'short pdata record')
        (dest / 'pdata.bin').write_bytes(pdata)
        report.update(entry=hex(ENTRY), endExclusive=row['endExclusive'], codeBytes=row['size'],
                      pdataHex=pdata.hex(), pdataSHA256=sha(pdata), pdataRecordBytes=12)
        save(dest / 'pre-body.private.json', report)
        report['stage'] = 'single-body'
        code = read(ENTRY, row['size'], True)
        report['newBodies'] = 1
        decoded = decode(code)
        (dest / 'code.bin').write_bytes(code)
        save(dest / 'code.asm.private.json', decoded['lines'])
        report.update(stage='complete', passed=True, newBodies=1, codeSHA256=sha(code),
                      assemblySHA256=sha((dest / 'code.asm.private.json').read_bytes()),
                      instructions=len(decoded['lines']), **{k: v for k, v in decoded.items() if k != 'lines'})
        save(dest / 'extraction.private.json', report)
        print(json.dumps({'bytes': len(code), 'sha256': sha(code), 'instructions': len(decoded['lines']),
                          'calls': len(decoded['calls']), 'branches': len(decoded['branches'])}))
    except Exception as e:
        report.update(passed=False, errorType=type(e).__name__, error=str(e))
        save(dest / 'failure.private.json', report)
        raise


def audit(first, second, output):
    request, _ = source_gate()
    copies = [{n: (p / n).read_bytes() for n in FILES} for p in (first, second)]
    require(copies[0] == copies[1], 'independent artifacts differ')
    files = copies[0]
    boundary, pre, report = [json.loads(files[n]) for n in ('boundary.private.json', 'pre-body.private.json', 'extraction.private.json')]
    row = choose(report['exceptionDirectory']['diagnostic'])
    require(boundary['stage'] == pre['stage'] == 'pdata-before-body' and
            boundary['exceptionDirectory'] == pre['exceptionDirectory'] == report['exceptionDirectory'] and
            boundary['aot'] == pre['aot'] == report['aot'], 'provenance differs')
    begin, end, _ = struct.unpack('<III', files['pdata.bin'])
    require(begin + 0x140000000 == ENTRY and end - begin == row['size'] and hex(end + 0x140000000) == row['endExclusive'], 'pdata mismatch')
    require(files['slot.bin'].hex() == request['closedAOT']['pointerQWORD'] and int.from_bytes(files['slot.bin'], 'little') == ENTRY, 'slot mismatch')
    code = files['code.bin']
    decoded = decode(code)
    require(len(code) == row['size'] and sha(code) == report['codeSHA256'] and
            sha(files['pdata.bin']) == report['pdataSHA256'] and files['pdata.bin'].hex() == report['pdataHex'] and
            sha(files['code.asm.private.json']) == report['assemblySHA256'], 'artifact hash differs')
    require(decoded['lines'] == json.loads(files['code.asm.private.json']) and
            all(decoded[k] == report[k] for k in decoded if k != 'lines'), 'decode differs')
    require(report['toolSHA256'] == sha(Path(__file__).read_bytes()) and report['requestSHA256'] == REQUEST_SHA and
            report['originalE8'] == CALL_BYTES.hex() and report['passed'] and report['newBodies'] == 1 and
            not any(report[k] for k in ('nativeCPU', 'live', 'calleeBodies', 'xdataBytes', 'actualMapObserved', 'targetSemanticsProven', 'resourceReady')), 'scope differs')
    require({name: x['sha256'] for name, x in report['inputChecks'].items()} == request['fixedClientInputs'], 'stock proof differs')
    result = {'passed': True, 'twoFreshSevenFilesEqual': True, 'entry': hex(ENTRY), 'endExclusive': row['endExclusive'],
              'codeBytes': len(code), 'instructions': len(decoded['lines']), 'artifacts': {n: {'bytes': len(b), 'sha256': sha(b)} for n, b in files.items()},
              'calls': decoded['calls'], 'branches': decoded['branches'], 'ripReferences': decoded['ripReferences'],
              'memoryOperands': decoded['memoryOperands'], 'nativeCPU': 0, 'live': 0, 'calleeBodies': 0,
              'xdataBytes': 0, 'currentPEReads': 0, 'targetSemanticsProven': False, 'resourceReady': False}
    save(output, result)
    print(json.dumps({'auditSHA256': sha(output.read_bytes()), 'bytes': output.stat().st_size}))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', required=True)
    parser.add_argument('--audit', nargs=2)
    args = parser.parse_args()
    require(Path(args.output).name == args.output and not (OUT / args.output).exists(), 'new task-local basename required')
    OUT.mkdir(parents=True, exist_ok=True)
    if args.audit:
        require(args.output.endswith('.json') and all(Path(x).name == x for x in args.audit), 'audit requires task-local inputs and JSON output')
        audit(*(OUT / x for x in args.audit), OUT / args.output)
    else:
        dest = OUT / args.output
        dest.mkdir()
        collect(dest)

if __name__ == '__main__':
    main()
