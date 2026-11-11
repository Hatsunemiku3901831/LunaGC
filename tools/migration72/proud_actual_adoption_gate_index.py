"""Index fixed Depot query/load opcode candidates without function bodies."""
import argparse
from bisect import bisect_left
from collections import Counter
import hashlib
import json
import pickle
import struct
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT))
from tools.extract_client72 import PE
from tools.migration72.proud_actual_adoption_sources import FIVE

OUT = ROOT / 'local/migration72/d02-resume-20261111b/proud'
SPEC = OUT / 'gate-index-spec.zh.md'
METADATA = ROOT / 'local/migration72/d02-resume-20261108/r3-equipment/metadata.private.pkl'
QUERY = 0x14d6d70f0
LIMIT = 128
PINS = {
    'tools/extract_client72.py': '7d1c75162f77c0fe0df154aa1240c142ad3f6af3295c89cc3d7b85372d3cb97d',
    'tools/migration72/proud_actual_adoption_sources.py': '5f5ddfbbecbef73ff3c45dfe916a5acd2d25c2a2d2357f1940844834e31f1627',
    'local/migration72/d02-resume-20261111b/proud/gate-index-spec.zh.md': '604844edc96f372b130689e49901d0825aff349ae3469ac816f8f66ea0af7904',
    'local/migration72/d02-resume-20261108/r3-equipment/metadata.private.pkl': 'df52eb70fe8e70dfecd1059e898d15e288cfa98bcefc78bcaabf753ae150349f',
}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', required=True)
    args = parser.parse_args()
    if Path(args.output).name != args.output or not args.output.endswith('.json'):
        parser.error('use a new task-local JSON basename')
    output = OUT / args.output
    if output.exists():
        parser.error('output already exists')
    for p, sha in PINS.items():
        checked(ROOT / p, sha)
    client = Path(subprocess.check_output(['powershell.exe', '-NoProfile', '-ExecutionPolicy', 'Bypass',
                 '-File', str(ROOT / 'tools/game_path.ps1')], text=True).strip())
    inputs = {p: checked(client / p, sha) for p, sha in FIVE.items()}
    binary = inputs['YuanShen.exe']
    pe = PE(binary)
    candidates, sections = [], []
    for va, size, offset, flags in pe.sections:
        if not flags & 0x20000000:
            continue
        data = binary[offset:offset + size]
        found = scan_section(data, va)
        for item in found:
            item['fileOffset'] = hex(offset + int(item['address'], 16) - va)
        candidates.extend(found)
        sections.append(dict(start=hex(va), bytes=size, fileOffset=hex(offset), flags=hex(flags),
                             sha256=hashlib.sha256(data).hexdigest(), candidateCount=len(found)))
    candidates.sort(key=lambda x: (int(x['address'], 16), x['kind']))
    report = inventory(candidates, LIMIT)
    if report['withinCandidateLimit']:
        records, _ = pickle.loads(METADATA.read_bytes())
        registered = {}
        for owner in records:
            for method in owner['methods']:
                address = int(method['registeredAddress'], 16)
                if address:
                    registered.setdefault(address, []).append(dict(owner=owner['index'], ownerName=owner['name'],
                        method=method['index'], methodName=method['name']))
        optional = struct.unpack_from('<I', binary, 0x3c)[0] + 24
        rva, size = struct.unpack_from('<II', binary, optional + 112 + 3 * 8)
        start = pe.offset(pe.base + rva, size)
        pdata = binary[start:start + size]
        bind_containing(candidates, pdata, pe.base, rva, registered)
        report.update(candidates=candidates, exceptionDirectory=dict(rva=hex(rva), bytes=size,
                      sha256=hashlib.sha256(pdata).hexdigest()))
    report.update(task='R3-PROUD-ACTUAL-ADOPTION/GATE-ENTRY-INDEX', pins=PINS, inputSHA256=FIVE,
                  specSHA256=hashlib.sha256(SPEC.read_bytes()).hexdigest(),
                  sourceSHA256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
                  executableSections=sections, query=hex(QUERY), displacement='0x212b8',
                  instructionBoundaryProven=False, typedDepotDataflowProven=False,
                  thresholdReadOrCompareProven=False, adoptedOrSkippedProven=False,
                  newFunctionBodies=0, xdataBytes=0, targetCPU=0, live=0,
                  actualMapHit=False, resourceReady=False,
                  scope='Opcode-shaped candidates only. Exact 5/7 bytes and containing exception records do not prove instruction alignment or a GLOBAL base. SIB and other load forms are outside this index.')
    raw = (json.dumps(report, ensure_ascii=False, sort_keys=True, indent=2) + '\n').encode()
    with output.open('xb') as stream:
        stream.write(raw)
    print(json.dumps(dict(bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest(),
                         totalCandidates=report['totalCandidates'], counts=report['countsByKind'],
                         withinCandidateLimit=report['withinCandidateLimit'])))
    return 0 if report['withinCandidateLimit'] else 2


def checked(path, expected):
    raw = path.read_bytes()
    if hashlib.sha256(raw).hexdigest() != expected:
        raise ValueError('frozen source SHA differs: ' + path.name)
    return raw


def scan_section(data, va):
    """Find exact local encodings; caller reachability and alignment stay unknown."""
    found = []
    for opcode, kind in [(0xe8, 'direct-call-to-depot-query'), (0xe9, 'direct-jump-to-depot-query')]:
        cursor = 0
        while (site := data.find(bytes([opcode]), cursor)) != -1:
            cursor = site + 1
            if site + 5 <= len(data) and va + site + 5 + struct.unpack_from('<i', data, site + 1)[0] == QUERY:
                found.append(dict(address=hex(va + site), kind=kind, hex=data[site:site + 5].hex(), bytes=5,
                                  target=hex(QUERY)))
    cursor = 0
    while (displacement := data.find(bytes.fromhex('b8120200'), cursor)) != -1:
        cursor = displacement + 1
        site = displacement - 3
        if site < 0:
            continue
        rex, opcode, modrm = data[site:displacement]
        if rex & 0xf8 == 0x48 and opcode == 0x8b and modrm & 0xc0 == 0x80 and modrm & 7 != 4:
            found.append(dict(address=hex(va + site), kind='load-displacement-212b8',
                              hex=data[site:site + 7].hex(), bytes=7, baseIsGlobalProven=False))
    return found


def inventory(candidates, limit):
    """Over-limit scans preserve totals and never select a prefix of candidates."""
    within = len(candidates) <= limit
    return dict(totalCandidates=len(candidates), candidateLimit=limit,
                countsByKind=dict(sorted(Counter(x['kind'] for x in candidates).items())),
                withinCandidateLimit=within, candidates=[] if not within else candidates,
                stopReason=None if within else 'candidate-limit-exceeded; no body or candidate selection')


def bind_containing(candidates, pdata, base, directory_rva, registered):
    """Attach every containing original exception record, preserving ambiguity."""
    sites = [int(x['address'], 16) for x in candidates]
    containers = [[] for _ in candidates]
    for index, (begin, end, unwind) in enumerate(struct.iter_unpack('<III', pdata)):
        left, right = bisect_left(sites, base + begin), bisect_left(sites, base + end)
        for match in range(left, right):
            containers[match].append(dict(begin=hex(base + begin), endExclusive=hex(base + end),
                recordRVA=hex(directory_rva + index * 12), recordHex=pdata[index * 12:(index + 1) * 12].hex(),
                unwindRVA=hex(unwind)))
    for candidate, rows in zip(candidates, containers):
        candidate['containingRecords'] = rows
        candidate['uniqueContainingRecord'] = len(rows) == 1
        candidate['registeredOwners'] = registered.get(int(rows[0]['begin'], 16), []) if len(rows) == 1 else []


if __name__ == '__main__':
    raise SystemExit(main())
