"""Fixed-target raw E8 index; static disk data only, never instruction validation."""
import argparse
import base64
from datetime import datetime, timezone
import bisect
import hashlib
import json
import struct
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
PLAN = ROOT / 'local/migration72/d02-resume-20261115/proud-caller-plan'
OUTPUT = ROOT / 'local/migration72/d02-resume-20261115/proud-float-caller-index'
MANIFEST_SHA = '92bad0a5090c3abfb501917aa96f60946ada9375924538f579dd543e92dc17ac'
PLAN_SHA = '1ef6c5bad410aca8bbfff8cb0988489111e9a0181ddf0d7bbf1d0dd430d3ac39'
EQUIPMENT = 'local/migration72/d02-resume-20261108/r3-equipment/final-first.json'
EQUIPMENT_SHA = '50aaea9406152e1ec9e0c423ec532618b8fe635e3d0cf758d24fccc6403eb5bc'
TARGET = 0x14f905140
FALSES = dict(instructionBoundaryValidated=False, ProudFieldBridgeApproved=False,
              sameEpochInputApproved=False, actualRowApproved=False,
              currentAdoptionApproved=False, resourceReady=False)
ARTIFACTS = ('index.private.json', 'source-audit.private.json', 'excluded-completed.private.json')


def check(path, size, expected):
    h = hashlib.sha256()
    n = 0
    with path.open('rb') as f:
        for chunk in iter(lambda: f.read(1048576), b''):
            h.update(chunk)
            n += len(chunk)
    if n != size or h.hexdigest() != expected:
        raise ValueError('source pin mismatch: ' + path.name)
    return {'bytes': n, 'sha256': h.hexdigest()}


def save(path, obj):
    with path.open('xb') as f:
        f.write((json.dumps(obj, sort_keys=True, ensure_ascii=False, indent=2) + '\n').encode('utf-8'))


def exclusions():
    rows = [{'start': TARGET, 'end': TARGET + 166, 'source': '31B getter'}]
    for name in ('parent-windows', 'sibling-windows', 'old-owner-window'):
        p = ROOT / ('local/migration72/d02-resume-20261109/r3-critical-consumer/' + name + '.private.json')
        data = json.loads(p.read_bytes())
        rows.extend({'start': int(w['start'], 16), 'end': int(w['end'], 16), 'source': name} for w in data['windows'])
    data = json.loads((ROOT / EQUIPMENT).read_bytes())
    rows.extend({'start': int(w['entry'], 16), 'end': int(w['end'], 16), 'source': 'equipment final-first staticConsumers'} for w in data['staticConsumers'])
    return sorted(rows, key=lambda r: (r['start'], r['end'], r['source']))


def subtract(begin, end, excluded):
    result = []
    cursor = begin
    for row in excluded:
        a, b = row['start'], row['end']
        if b <= cursor or a >= end:
            continue
        if cursor < a:
            result.append((cursor, min(a, end)))
        cursor = max(cursor, b)
        if cursor >= end:
            break
    if cursor < end:
        result.append((cursor, end))
    return result


def raw_candidates(data, va, offset):
    pos = data.find(b'\xe8')
    while pos >= 0 and pos + 5 <= len(data):
        if va + pos + 5 + struct.unpack_from('<i', data, pos + 1)[0] == TARGET:
            yield {'site': va + pos, 'fileOffset': offset + pos, 'bytesHex': data[pos:pos + 5].hex()}
        pos = data.find(b'\xe8', pos + 1)



def scan_domains(f, domains, dest, identity, header):
    candidates = []
    reads = []
    for domain in domains:
        f.seek(domain['fileOffset'])
        data = f.read(domain['bytes'])
        reads.append({'domain': domain, 'requestedBytes': domain['bytes'],
                      'returnedBytes': len(data), 'scanComplete': False})
        if len(data) != domain['bytes']:
            raise ValueError('short exec read')
        for row in raw_candidates(data, domain['begin'], domain['fileOffset']):
            if len(candidates) == 4096:
                reads[-1]['scannedThroughSiteInclusive'] = row['site']
                save(dest / 'stopped.private.json', {
                    'reason': 'candidate limit', 'stopSite': row['site'],
                    'observedCandidateCount': 4097, 'retainedCandidateCount': len(candidates),
                    'completeIndex': False, 'binaryHashRead': identity, 'headerRead': header,
                    'execScan': {'declaredDomains': domains, 'reads': reads,
                                 'readCalls': len(reads),
                                 'bytesReturned': sum(r['returnedBytes'] for r in reads)},
                    'rawCandidates': candidates, 'pdataReadBytes': 0, **FALSES})
                raise ValueError('candidate limit at ' + hex(row['site']))
            row['scanDomainBegin'] = domain['begin']
            candidates.append(row)
        reads[-1]['scanComplete'] = True
    return candidates


def pdata_matches(sites, table, base, directory_rva, directory_offset):
    result = {site: {'containingSite': [], 'coveringFiveBytes': []} for site in sites}
    for i in range(0, len(table), 12):
        begin, end, unwind = struct.unpack_from('<III', table, i)
        lo = bisect.bisect_left(sites, base + begin)
        hi = bisect.bisect_left(sites, base + end)
        if lo == hi:
            continue
        row = {'recordRVA': directory_rva + i, 'recordFileOffset': directory_offset + i,
               'bytesHex': table[i:i + 12].hex(), 'begin': base + begin, 'end': base + end, 'unwindRVA': unwind}
        for site in sites[lo:hi]:
            result[site]['containingSite'].append(row)
            if site + 5 <= base + end:
                result[site]['coveringFiveBytes'].append(row)
    return result


def method_aliases(structures, signatures, begins):
    result = {v: [] for v in begins}
    by_index = {m['index']: m for m in signatures['methods']}
    refs = signatures['typeReferences']
    for owner in structures:
        for method in owner['methods']:
            address = int(method['registeredAddress'], 16)
            if address not in result:
                continue
            sig = by_index.get(method['index'])
            params = [] if sig is None else [refs[str(p['typeIndex'])] for p in sig['parameters']]
            hints = [p for p in params if p.get('typeDefinition') in (26072, 41105, 52802)]
            result[address].append({'ownerIndex': owner['index'], 'ownerName': owner['name'],
                                    'ownerNamespace': owner['namespace'], 'method': method,
                                    'signaturePresent': sig is not None, 'directTypedHints': hints})
    for aliases in result.values():
        aliases.sort(key=lambda a: (a['ownerIndex'], a['method']['index']))
    return result


def pe_header(f, size):
    h = f.read(4096)
    nt = struct.unpack_from('<I', h, 0x3c)[0]
    if h[:2] != b'MZ' or h[nt:nt + 4] != b'PE\0\0' or struct.unpack_from('<H', h, nt + 4)[0] != 0x8664 or struct.unpack_from('<H', h, nt + 24)[0] != 0x20b:
        raise ValueError('PE identity')
    base = struct.unpack_from('<Q', h, nt + 48)[0]
    count = struct.unpack_from('<H', h, nt + 6)[0]
    table_offset = nt + 24 + struct.unpack_from('<H', h, nt + 20)[0]
    length = count * 40
    if base != 0x140000000 or not length or 4096 + length > 8192:
        raise ValueError('PE header budget/base')
    f.seek(table_offset)
    table = f.read(length)
    sections = []
    for i in range(count):
        pos = i * 40
        rva, raw_size, raw_offset = struct.unpack_from('<III', table, pos + 12)
        flags = struct.unpack_from('<I', table, pos + 36)[0]
        if raw_offset + raw_size > size:
            raise ValueError('section exceeds file')
        sections.append({'begin': base + rva, 'end': base + rva + raw_size,
                         'fileOffset': raw_offset, 'flags': flags})
    pdata_rva, pdata_size = struct.unpack_from('<II', h, nt + 24 + 0x70 + 3 * 8)
    if pdata_size != 14344080 or pdata_size % 12:
        raise ValueError('exception directory size drift')
    return base, sections, pdata_rva, pdata_size, {'readCalls': 2, 'bytesRead': 4096 + length,
        'intervals': [{'offset': 0, 'bytes': 4096}, {'offset': table_offset, 'bytes': length}]}


def resolve_game_path(dest):
    argv = ['powershell.exe', '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', str(ROOT / 'tools/game_path.ps1')]
    result = subprocess.run(argv, capture_output=True)
    if result.returncode:
        save(dest / 'resolver-failure.private.json', {
            'phase': 'game-path-resolver', 'utc': datetime.now(timezone.utc).isoformat(),
            'argv': argv, 'exitCode': result.returncode,
            'stdoutBase64': base64.b64encode(result.stdout).decode('ascii'),
            'stderrBase64': base64.b64encode(result.stderr).decode('ascii'),
            'PEHashReadBytes': 0, 'PEHeaderReadBytes': 0, 'PEExecReadBytes': 0, 'PEPdataReadBytes': 0})
        raise ValueError('game path resolver failed; original buffers saved')
    return result.stdout.decode('utf-8').strip()


def collect(dest):
    manifest_path = PLAN / 'fixed-inputs.private.json'
    check(manifest_path, 6767, MANIFEST_SHA)
    check(PLAN / '静态caller最小来源计划.zh.md', 14568, PLAN_SHA)
    manifest = json.loads(manifest_path.read_bytes())
    pins = {p: check(ROOT / p, v['bytes'], v['sha256']) for p, v in sorted(manifest['pins'].items())}
    pins[EQUIPMENT] = check(ROOT / EQUIPMENT, 4164206, EQUIPMENT_SHA)
    profile = json.loads((ROOT / 'protocol/cn-7.2/profile.json').read_bytes())
    if profile['target'] != {'version': '7.2.0', 'channel': 1, 'subChannel': 1}:
        raise ValueError('profile identity')
    binary = profile['files']['binary']
    game = resolve_game_path(dest)
    path = Path(game) / binary['path']
    identity = check(path, binary['size'], binary['sha256'])
    excluded = exclusions()
    with path.open('rb') as f:
        base, sections, pdata_rva, pdata_size, header = pe_header(f, binary['size'])
        domains = []
        for section in sections:
            if not section['flags'] & 0x20000000:
                continue
            for begin, end in subtract(section['begin'], section['end'], excluded):
                domains.append({'begin': begin, 'end': end, 'fileOffset': section['fileOffset'] + begin - section['begin'], 'bytes': end - begin})
        if sum(d['bytes'] for d in domains) > binary['size']:
            raise ValueError('exec budget exceeded')
        candidates = scan_domains(f, domains, dest, identity, header)
        psecs = [s for s in sections if s['begin'] <= base + pdata_rva and base + pdata_rva + pdata_size <= s['end']]
        if len(psecs) != 1:
            raise ValueError('exception directory section')
        pdata_offset = psecs[0]['fileOffset'] + base + pdata_rva - psecs[0]['begin']
        f.seek(pdata_offset)
        table = f.read(pdata_size)
        if len(table) != pdata_size:
            raise ValueError('short exception directory')
    candidates.sort(key=lambda c: c['site'])
    sites = [c['site'] for c in candidates]
    pdata = pdata_matches(sites, table, base, pdata_rva, pdata_offset)
    begins = {r['begin'] for p in pdata.values() for r in p['containingSite']}
    structures = json.loads((ROOT / 'local/migration72/analysis/current-structures.json').read_bytes())
    signatures = json.loads((ROOT / 'local/migration72/evidence/client72-signatures.json').read_bytes())
    aliases = method_aliases(structures, signatures, begins)
    for c in candidates:
        matches = pdata[c['site']]
        c.update(matches)
        c['methodAliasesByBegin'] = [{'begin': begin, 'aliases': aliases[begin]} for begin in sorted({r['begin'] for r in matches['containingSite']})]
        c['uniqueFullPdata'] = len(matches['containingSite']) == 1 and len(matches['coveringFiveBytes']) == 1
        c.update(FALSES)
    index = {'task': 'R3-PROUD-FLOAT-DIRECT-E8-INDEX', 'target': TARGET, 'rawCandidates': candidates, **FALSES}
    audit = {'sourcePins': pins, 'binaryHashRead': identity, 'headerRead': header,
             'execScan': {'domains': domains, 'bytesRead': sum(d['bytes'] for d in domains), 'readCalls': len(domains)},
             'pdataRead': {'rva': pdata_rva, 'fileOffset': pdata_offset, 'bytesRead': len(table), 'readCalls': 1, 'sha256': hashlib.sha256(table).hexdigest()},
             'limitations': 'Raw E8 only; no boundary validation, E9 or indirect calls. Negative coverage is limited to declared scan domains.', **FALSES}
    save(dest / ARTIFACTS[0], index)
    save(dest / ARTIFACTS[1], audit)
    save(dest / ARTIFACTS[2], {'ranges': excluded, 'coverage': 'excluded from executable scan and five-byte evidence'})


def audit_artifacts(first, second):
    files = {}
    for name in ARTIFACTS:
        a, b = (first / name).read_bytes(), (second / name).read_bytes()
        if a != b:
            raise ValueError('artifact mismatch: ' + name)
        files[name] = {'bytes': len(a), 'sha256': hashlib.sha256(a).hexdigest()}
    index = json.loads((first / ARTIFACTS[0]).read_bytes())
    source = json.loads((first / ARTIFACTS[1]).read_bytes())
    excluded = json.loads((first / ARTIFACTS[2]).read_bytes())['ranges']
    domains = source['execScan']['domains']
    if index['target'] != TARGET or any(index[k] is not False or source[k] is not False for k in FALSES):
        raise ValueError('target or false flags')
    if sum(d['bytes'] for d in domains) != source['execScan']['bytesRead']:
        raise ValueError('scan ledger')
    for d in domains:
        if d['end'] - d['begin'] != d['bytes'] or any(d['begin'] < e['end'] and e['start'] < d['end'] for e in excluded):
            raise ValueError('domain or excluded range')
    sites = []
    for c in index['rawCandidates']:
        site = c['site']; sites.append(site)
        raw = bytes.fromhex(c['bytesHex'])
        if len(raw) != 5 or raw[0] != 0xe8 or site + 5 + struct.unpack_from('<i', raw, 1)[0] != TARGET or any(c[k] is not False for k in FALSES):
            raise ValueError('candidate calculation')
        matching = [d for d in domains if d['begin'] == c['scanDomainBegin'] and d['begin'] <= site and site + 5 <= d['end'] and c['fileOffset'] == d['fileOffset'] + site - d['begin']]
        if len(matching) != 1:
            raise ValueError('candidate domain')
        contained = c['containingSite']; covered = c['coveringFiveBytes']
        for r in contained:
            begin, end, unwind = struct.unpack('<III', bytes.fromhex(r['bytesHex']))
            if (r['begin'], r['end'], r['unwindRVA']) != (0x140000000 + begin, 0x140000000 + end, unwind) or not r['begin'] <= site < r['end'] or r['recordRVA'] - source['pdataRead']['rva'] != r['recordFileOffset'] - source['pdataRead']['fileOffset']:
                raise ValueError('pdata record')
        if covered != [r for r in contained if site + 5 <= r['end']] or c['uniqueFullPdata'] != (len(contained) == 1 and len(covered) == 1):
            raise ValueError('pdata coverage')
        if [x['begin'] for x in c['methodAliasesByBegin']] != sorted({r['begin'] for r in contained}):
            raise ValueError('alias begins')
        for group in c['methodAliasesByBegin']:
            if any(int(a['method']['registeredAddress'], 16) != group['begin'] for a in group['aliases']):
                raise ValueError('alias address')
    if sites != sorted(set(sites)) or len(sites) > 4096:
        raise ValueError('sites ordering or limit')
    return {'artifacts': files, 'candidateCount': len(sites), 'PEReads': 0, 'passed': True}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--output', required=True)
    parser.add_argument('--audit', nargs=2)
    args = parser.parse_args()
    if args.audit:
        save(OUTPUT / args.output, audit_artifacts(*(OUTPUT / p for p in args.audit)))
    else:
        dest = OUTPUT / args.output
        dest.mkdir(parents=True, exist_ok=False)
        collect(dest)


if __name__ == '__main__':
    main()
