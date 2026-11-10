"""Owned native capability artifact gate. No CPU probing occurs in this module."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import struct
import subprocess
import sys
from datetime import datetime, timezone

ROOT = Path(__file__).resolve().parent.parent
PRIVATE = ROOT / 'local/migration72/d02-resume-20261110b'
SPEC = PRIVATE / 'a-clear48-native-capability/byte-and-source-spec.final.private.json'
SPEC_SHA = '85add258803469ad0ce12be24a437ded217bff7f8b90a406056434bd6287d903'
PROBE = bytes.fromhex('c5fe6f01c5fe7f02c4e37d18c001c4c17e7f00c5f877c3')
XCR = bytes.fromhex('31c90f01d048c1e2204809d0c3')
UD2 = bytes.fromhex('0f0bc3')
SOURCE_PINS = {
    'tools/client72_d02_native_avx/fixture.c': 'c62dd632a9d2d763a13f2ccd447d6bd92254f7464e23e7305467c2432afe52f1',
    'tools/client72_d02_native_avx/fixture.asm': 'fd9be18f978abbf2f896181508dde97917effee96ac964e51b499e46c266b88f',
    'tools/client72_d02_native_avx/build.ps1': '41b7a90c685bf8cf9a44e0efb231dec7a70d1a4e127a5f91c2fd9ca5b3982b12',
    'tools/test_client72_d02_native_avx.py': '62dd482d5cd65b0940c08351e83d5eee1fb97061c81f1cd105294ed04a8a6907',
}

def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()

def verify_pin(path, digest):
    require(sha(path) == digest, 'source drift: ' + str(path))

def require(condition, message):
    if not condition:
        raise ValueError(message)

def source_gate():
    require(sha(SPEC) == SPEC_SHA, 'spec drift')
    spec = json.loads(SPEC.read_text(encoding='utf-8'))
    pins = spec['readOnly112PinsPassed']
    require(len(pins) == 112, '112 pin count')
    for pin in pins:
        path = ROOT / pin['path']
        require(path.stat().st_size == pin['bytes'] and sha(path) == pin['sha256'], 'source drift: ' + pin['path'])
    helper = (PRIVATE / 'a-gc/gc-helper.code.bin').read_bytes()
    require(len(helper) == 904 and hashlib.sha256(helper).hexdigest() == '34287f706e27541c8119091aa9e55c7e2916610935f90c9b74992856b9654b31' and helper[242:248] == PROBE[8:14], 'original instruction drift')
    require(SOURCE_PINS, 'implementation source pins absent')
    for name, digest in SOURCE_PINS.items():
        verify_pin(ROOT / name, digest)

def host_plan(max_leaf, ecx, xcr):
    if max_leaf < 1 or ecx & 0x1c000000 != 0x1c000000:
        return (False, False)
    return (True, xcr is not None and xcr & 6 == 6)

def block_initial():
    result = bytearray()
    for i in range(3):
        result.extend(bytes([0x41+i])*32)
        result.extend(bytes(range(1, 33)) if i == 0 else bytes([0x61+i])*32)
        result.extend(bytes([0x51+i])*32)
    return bytes(result)

def validate_report(report, exit_code, rvas, mode):
    require(report['mode'] == mode and report['exit'] == exit_code, 'exit mismatch')
    for key, expected in [('probe', PROBE), ('xcr', XCR), ('witness', UD2)]:
        require(report[key+'BytesBefore'] == expected.hex() and report[key+'BytesAfter'] == expected.hex(), key+' runtime bytes')
        require(report[key+'Rva'] == rvas[key], key+' runtime RVA')
    initial = block_initial()
    before, after = bytes.fromhex(report['blocksBefore']), bytes.fromhex(report['blocksAfter'])
    require(before == initial and len(after) == 288, 'initial blocks')
    fault = report['fault']
    if mode == 'ud2':
        require(exit_code == 30 and report['probeCount'] == 0 and report['xgetbvCount'] == 0 and report['cpuidCount'] == 0 and report['witnessCount'] == 1, 'witness counts')
        addr = int(report['imageBase'], 16) + rvas['witness']
        require(fault['seen'] and fault['phase'] == 3 and fault['code'] == 0xc000001d and int(fault['address'], 16) == addr and int(fault['rip'], 16) == addr and fault['rva'] == rvas['witness'] and not report['normalReturnObserved'] and after == initial, 'witness fault')
        return
    require(exit_code == 0 and not fault['seen'] and report['witnessCount'] == 0 and report['cpuidCount'] == 2 and report['xgetbvCount'] == 1 and report['probeCount'] == 1, 'capability counts or fault')
    plan = host_plan(report['cpuid0'][0], report['cpuid1'][2], int(report['xcr0'], 16))
    require(plan == (True, True) and report['normalReturnObserved'] and report['postSnapshotReached'], 'host or return')
    expected = bytearray(initial)
    expected[128:160] = bytes(range(1, 33))
    expected[224:256] = bytes(range(1, 17))*2
    require(after == expected, 'pre/post/input/guard mismatch')

def pe_info(path):
    b = path.read_bytes()
    require(b[:2] == b'MZ', 'MZ')
    pe = struct.unpack_from('<I', b, 0x3c)[0]
    require(b[pe:pe+4] == b'PE\0\0', 'PE')
    machine, nsec, _, _, _, optsz, _ = struct.unpack_from('<HHIIIHH', b, pe+4)
    require(machine == 0x8664 and struct.unpack_from('<H', b, pe+24)[0] == 0x20b, 'PE x64')
    image_base = struct.unpack_from('<Q', b, pe+24+24)[0]
    sections = []
    for i in range(nsec):
        p = pe+24+optsz+i*40
        name = b[p:p+8].rstrip(b'\0').decode('ascii')
        vs, va, rawsize, rawptr = struct.unpack_from('<IIII', b, p+8)
        flags = struct.unpack_from('<I', b, p+36)[0]
        sections.append((name, va, vs, rawptr, rawsize, flags))
    return b, image_base, sections

def mapped_bytes(b, sections, rva, size):
    matches = [s for s in sections if s[1] <= rva and rva+size <= s[1]+min(s[2], s[4])]
    require(len(matches) == 1, 'RVA mapping')
    s = matches[0]
    require(s[5] & 0x20000000 and s[5] & 0x40000000 and not s[5] & 0x80000000, 'section permissions')
    offset = s[3]+rva-s[1]
    require(offset+size <= len(b), 'truncated PE')
    return b[offset:offset+size], {'section':s[0], 'fileOffset':offset, 'flags':s[5]}

def parse_map(path):
    labels = ('Probe','ProbeEntry','ProbeSite','ProbeReturn','ProbeEnd','ReadXcr0','Xcr0Entry','Xcr0Site','Xcr0Return','Xcr0End','Witness','WitnessEntry','WitnessSite','WitnessEnd')
    found = {}
    for line in path.read_text(encoding='utf-8', errors='strict').splitlines():
        m = re.match(r'^\s*[0-9A-Fa-f]+:[0-9A-Fa-f]+\s+(\S+)\s+([0-9A-Fa-f]{8,16})\b', line)
        if m and m[1] in labels:
            require(m[1] not in found, 'duplicate map symbol')
            found[m[1]] = int(m[2],16)
    require(set(found) == set(labels), 'missing map symbols')
    return found

def audit_pe(exe, mapfile, dumpbin):
    source_gate()
    require(sha(dumpbin) == 'cfee7ed764b7b0866aae0be12f9c0ad2a7f5aecc4046ba2b7de26335827ca815', 'dumpbin drift')
    b, base, sections = pe_info(exe)
    labels = parse_map(mapfile)
    rvas = {name: value-base for name,value in labels.items()}
    for label, entry, delta in [('ProbeEntry','Probe',0),('ProbeSite','Probe',8),('ProbeReturn','Probe',22),('ProbeEnd','Probe',23),('Xcr0Entry','ReadXcr0',0),('Xcr0Site','ReadXcr0',2),('Xcr0Return','ReadXcr0',12),('Xcr0End','ReadXcr0',13),('WitnessEntry','Witness',0),('WitnessSite','Witness',0),('WitnessEnd','Witness',3)]:
        require(rvas[label] == rvas[entry]+delta, 'label offset '+label)
    sys.path.insert(0, str(ROOT / 'local/migration72/python'))
    import capstone
    require(capstone.__version__ == '5.0.7', 'Capstone binding version')
    dec = capstone.Cs(capstone.CS_ARCH_X86, capstone.CS_MODE_64)
    run = subprocess.run([str(dumpbin), '/disasm', str(exe)], capture_output=True, timeout=30, check=True)
    dump = run.stdout.decode('utf-8', errors='replace')
    evidence = {}
    for name, value, expected_lengths in [('Probe', PROBE, [4,4,6,5,3,1]), ('ReadXcr0', XCR, [2,3,4,3,1]), ('Witness', UD2, [2,1])]:
        data, mapping = mapped_bytes(b, sections, rvas[name], len(value))
        require(data == value, name+' PE bytes')
        insns = list(dec.disasm(data, base+rvas[name]))
        require([i.size for i in insns] == expected_lengths and sum(i.size for i in insns) == len(value), name+' Capstone coverage')
        # dumpbin prints absolute virtual addresses and instruction bytes on each row.
        rows = []
        for ins in insns:
            pattern = r'(?im)^\s*0*' + format(ins.address, 'x') + r':\s+' + r'\s+'.join(f'{x:02x}' for x in ins.bytes) + r'\s+([a-z0-9]+)([^\r\n]*)'
            match = re.search(pattern, dump)
            require(match is not None and match[1].lower() == ins.mnemonic.lower(), name+' dumpbin decode mismatch at '+hex(ins.address))
            rows.append({'rva':ins.address-base,'bytes':bytes(ins.bytes).hex(),'mnemonic':ins.mnemonic,'operands':ins.op_str,'dumpbinMnemonic':match[1],'dumpbinOperands':match[2].strip()})
        evidence[name] = {'mapping':mapping,'rva':rvas[name],'bytes':data.hex(),'sha256':hashlib.sha256(data).hexdigest(),'capstone':rows}
    return {'rvas':{'probe':rvas['Probe'],'xcr':rvas['ReadXcr0'],'witness':rvas['Witness']},'labels':rvas,'sections':evidence,'exeSha256':sha(exe),'mapSha256':sha(mapfile),'dumpbinSha256':sha(dumpbin),'dumpbinText':dump}

def dependency_gate(review_path, exe, mapfile):
    review = json.loads(review_path.read_text(encoding='utf-8-sig'))
    require(review.get('independentlyReviewed') is True, 'dependency review missing')
    require(review.get('exeSha256') == sha(exe) and review.get('mapSha256') == sha(mapfile), 'build identity mismatch')
    require(exe.resolve().parent == mapfile.resolve().parent, 'build paths differ')
    for category in ('source', 'includes', 'libraries', 'compilerDlls', 'buildLogs'):
        entries = review.get(category)
        require(isinstance(entries, list) and len(entries) > 0, 'missing dependency category: '+category)
        for entry in entries:
            verify_pin(entry['path'], entry['sha256'])
    sources = {str(Path(e['path']).resolve()).casefold() for e in review['source']}
    logs = {str(Path(e['path']).resolve()).casefold() for e in review['buildLogs']}
    required_sources = [ROOT / name for name in SOURCE_PINS] + [Path(__file__), exe.parent/'fixture.c', exe.parent/'fixture.asm']
    required_logs = [exe.parent/name for name in ('build.cmd','vcvars.log','environment.private.log','asm.log','compile.log','link.log','build-record.private.json')]
    require(all(str(p.resolve()).casefold() in sources for p in required_sources), 'missing required source identity')
    require(all(str(p.resolve()).casefold() in logs for p in required_logs), 'missing required build logs')
    require(sha(exe.parent/'fixture.c') == SOURCE_PINS['tools/client72_d02_native_avx/fixture.c'] and sha(exe.parent/'fixture.asm') == SOURCE_PINS['tools/client72_d02_native_avx/fixture.asm'], 'compiled source copy mismatch')

def reserve_output(output):
    output.mkdir(parents=False, exist_ok=False)

def previous_gate(previous, current_audit):
    old_audit = json.loads((previous/'audit.json').read_text(encoding='utf-8'))
    parent = json.loads((previous/'parent.json').read_text(encoding='utf-8'))
    child = json.loads((previous/'child.json').read_text(encoding='utf-8'))
    require(old_audit['exeSha256'] == current_audit['exeSha256'] and old_audit['mapSha256'] == current_audit['mapSha256'] and old_audit['rvas'] == current_audit['rvas'], 'previous artifact identity')
    require(not parent['timeout'] and parent['creationFiletime'] is not None and parent['before'] == parent['after'], 'previous parent record')
    require(child['mode'] in ('ud2', 'capability'), 'previous mode')
    validate_report(child, parent['exit'], current_audit['rvas'], child['mode'])

def run_owned(exe, mapfile, dumpbin, output, mode, review_path, previous):
    owned_root = (PRIVATE / 'a-clear48-native-implementation').resolve()
    require(exe.resolve().is_relative_to(owned_root) and mapfile.resolve().is_relative_to(owned_root) and output.resolve().is_relative_to(owned_root) and review_path.resolve().is_relative_to(owned_root), 'non-owned path')
    reserve_output(output)
    try:
        audit = audit_pe(exe, mapfile, dumpbin)
        dependency_gate(review_path, exe, mapfile)
        if mode == 'capability':
            require(previous is not None and previous.resolve().is_relative_to(owned_root), 'previous successful witness or capability required')
            previous_gate(previous, audit)
    except Exception as error:
        with (output/'gate-failure.json').open('x', encoding='utf-8') as f:
            json.dump({'utc':datetime.now(timezone.utc).isoformat(), 'error':str(error), 'childCreated':False, 'exit':10}, f, indent=2)
        raise
    (output/'audit.json').write_text(json.dumps(audit, indent=2), encoding='utf-8')
    before = {str(p):sha(p) for p in [exe,mapfile,dumpbin]}
    child_report = output/'child.json'
    argv = [str(exe.resolve()), mode, str(child_report.resolve())]
    with (output/'stdout.bin').open('xb') as stdout, (output/'stderr.bin').open('xb') as stderr:
        start = datetime.now(timezone.utc).isoformat()
        child = subprocess.Popen(argv, stdout=stdout, stderr=stderr, creationflags=subprocess.CREATE_NO_WINDOW)
        import ctypes
        from ctypes import wintypes
        creation, last_exit, kernel, user = (wintypes.FILETIME() for _ in range(4))
        get_times = ctypes.windll.kernel32.GetProcessTimes
        get_times.argtypes = [wintypes.HANDLE] + [ctypes.POINTER(wintypes.FILETIME)] * 4
        get_times.restype = wintypes.BOOL
        creation_ok = get_times(int(child._handle), ctypes.byref(creation), ctypes.byref(last_exit), ctypes.byref(kernel), ctypes.byref(user))
        creation_filetime = (creation.dwHighDateTime << 32) | creation.dwLowDateTime if creation_ok else None
        try:
            code = child.wait(timeout=10)
            timeout = False
        except subprocess.TimeoutExpired:
            child.kill(); child.wait(); code = 124; timeout = True
    after = {str(p):sha(p) for p in [exe,mapfile,dumpbin]}
    record = {'argv':argv,'previous':str(previous.resolve()) if previous else None,'startUtc':start,'endUtc':datetime.now(timezone.utc).isoformat(),'pid':child.pid,'creationFiletime':creation_filetime,'exit':code,'timeout':timeout,'before':before,'after':after}
    with (output/'parent.json').open('x', encoding='utf-8') as f:
        json.dump(record, f, indent=2)
    source_gate()
    require(before == after and not timeout and creation_filetime is not None, 'artifact drift, timeout or missing child creation time')
    report = json.loads(child_report.read_text(encoding='utf-8'))
    validate_report(report, code, audit['rvas'], mode)
    return record

def main():
    p = argparse.ArgumentParser()
    p.add_argument('mode', choices=['audit','capability','ud2'])
    p.add_argument('--exe', type=Path, required=True); p.add_argument('--map', type=Path, required=True)
    p.add_argument('--dumpbin', type=Path, required=True); p.add_argument('--output', type=Path, required=True)
    p.add_argument('--dependency-review', type=Path)
    p.add_argument('--previous', type=Path)
    a = p.parse_args()
    if a.mode == 'audit':
        audit = audit_pe(a.exe,a.map,a.dumpbin)
        with a.output.open('x', encoding='utf-8') as f: json.dump(audit, f, indent=2)
    else:
        require(a.dependency_review is not None, 'independent dependency review required')
        run_owned(a.exe,a.map,a.dumpbin,a.output,a.mode,a.dependency_review,a.previous)

if __name__ == '__main__':
    main()
