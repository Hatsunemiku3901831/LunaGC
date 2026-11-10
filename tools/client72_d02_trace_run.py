"""Root-owned single-case trace supervisor. Does not validate native results."""
import argparse
import ctypes
from ctypes import wintypes
import datetime
import hashlib
import json
from pathlib import Path
import re
import struct
import subprocess
import sys
import uuid

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
from tools import client72_d02_trace_artifacts as a
from tools import client72_d02_trace_validate as v

OWNED = a.PRIVATE / 'a-clear48-trace-witness'
SOURCE = ROOT / 'tools/client72_d02_native_trace'
GROUPS = ('source', 'includes', 'libraries', 'compilerDlls', 'buildLogs')
ARTIFACT_FILES = {'parentExe': 'parent.exe', 'parentMap': 'parent.map',
                  'childExe': 'child.exe', 'childMap': 'child.map'}
CONTRACT_FILES = dict(zip(('interface', 'cases', 'eventSchema', 'frozenInputs'), a.CONTRACT_PINS))


def need(ok, message):
    if not ok:
        raise ValueError(message)


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def utc():
    return datetime.datetime.now(datetime.timezone.utc).isoformat().replace('+00:00', 'Z')


def creation_time(handle):
    class FILETIME(ctypes.Structure):
        _fields_ = [('dwLowDateTime', wintypes.DWORD), ('dwHighDateTime', wintypes.DWORD)]
    kernel = ctypes.WinDLL('kernel32', use_last_error=True)
    api = kernel.GetProcessTimes
    api.argtypes = [wintypes.HANDLE, ctypes.POINTER(FILETIME), ctypes.POINTER(FILETIME),
                    ctypes.POINTER(FILETIME), ctypes.POINTER(FILETIME)]
    api.restype = wintypes.BOOL
    created, exited, kernel_time, user = FILETIME(), FILETIME(), FILETIME(), FILETIME()
    if not api(handle, ctypes.byref(created), ctypes.byref(exited), ctypes.byref(kernel_time), ctypes.byref(user)):
        raise OSError(ctypes.get_last_error(), 'GetProcessTimes failed on owned parent handle')
    value = (created.dwHighDateTime << 32) | created.dwLowDateTime
    need(value > 0, 'zero parent creation time')
    return f'{value:016x}'


def read_json(path):
    return v.read_json(path)


def snapshot(paths):
    return {str(path): digest(path) for path in paths}


def preflight(args):
    build = args.build.resolve()
    owned = OWNED.resolve()
    need(build.is_dir() and build.is_relative_to(owned), 'build must be an owned directory')
    artifacts = {name: (build / filename).resolve() for name, filename in ARTIFACT_FILES.items()}
    artifacts.update(header=(SOURCE / 'shared.h').resolve(), bootstrap=args.bootstrap.resolve(),
                     bootstrapMirror=args.bootstrap_mirror.resolve(), dependencyReview=args.dependency_review.resolve())
    artifacts.update({name: (a.CONTRACT / filename).resolve() for name, filename in CONTRACT_FILES.items()})
    need(len(set(artifacts.values())) == 12, 'duplicate artifact paths')
    for name in ARTIFACT_FILES:
        need(artifacts[name].is_relative_to(owned), 'EXE/map outside owned directory')
    need(all(p.is_file() for p in artifacts.values()), 'missing artifact')
    first_hashes = snapshot(artifacts.values())
    for filename, expected in a.CONTRACT_PINS.items():
        path = a.CONTRACT / filename
        need(digest(path) == expected, 'contract drift: ' + filename)
        read_json(path)
    need(digest(artifacts['header']) == a.HEADER, 'header drift')
    record = read_json(build / 'build-record.private.json')
    need(type(record['exit']) is int and record['exit'] == 0 and record['sharedHeaderSHA256'] == a.HEADER
         and record['fixtureExecuted'] is False, 'build record rejected')
    record_path = (build / 'build-record.private.json').resolve()
    first_hashes[str(record_path)] = digest(record_path)
    records = record['source']
    need(len(records) == len(a.BUILD_SOURCES) and {r['name'] for r in records} == set(a.BUILD_SOURCES), 'build source set')
    for row in records:
        path = build / row['name']
        raw = path.read_bytes()
        first_hashes[str(path.resolve())] = a.sha(raw)
        first_hashes[str((SOURCE / row['name']).resolve())] = a.sha(raw)
        need(len(raw) == row['bytes'] and a.sha(raw) == row['sha256'] and raw == (SOURCE / row['name']).read_bytes(),
             'source drift: ' + row['name'])
    review = read_json(artifacts['dependencyReview'])
    need(review['schema'] == 'c48-trace-dependency-review-v1' and review['independentlyReviewed'] is True
         and type(review['actualBuildExit']) is int and review['actualBuildExit'] == 0
         and review['peAndCallerUnwindReviewed'] is True, 'independent review rejected')
    need(set(review['artifacts']) == set(ARTIFACT_FILES), 'review artifact set')
    entries = list(review['artifacts'].values())
    for name, entry in review['artifacts'].items():
        need(entry['path'] == str(artifacts[name]), 'review artifact identity: ' + name)
    for group in GROUPS:
        need(isinstance(review[group], list) and review[group], 'empty dependency group: ' + group)
        entries.extend(review[group])
    source_paths = {e['path'] for e in review['source']}
    need({str((build / name).resolve()) for name in (*a.BUILD_SOURCES, 'build-epoch.h')} <= source_paths,
         'review must include actual build sources and epoch')
    paths = set(artifacts.values())
    paths.update((SOURCE / name).resolve() for name in a.BUILD_SOURCES)
    paths.add((build / 'build-record.private.json').resolve())
    declared = {}
    for entry in entries:
        need(set(entry) == {'path', 'bytes', 'sha256'} and isinstance(entry['path'], str), 'dependency entry shape')
        path = Path(entry['path'])
        need(path.is_absolute() and str(path.resolve()) == entry['path'] and type(entry['bytes']) is int
             and entry['bytes'] >= 0 and isinstance(entry['sha256'], str)
             and re.fullmatch('[0-9a-f]{64}', entry['sha256']) is not None, 'dependency entry values')
        raw = path.read_bytes()
        need(len(raw) == entry['bytes'] and a.sha(raw) == entry['sha256'], 'dependency drift: ' + str(path))
        need(path not in declared or declared[path] == (entry['bytes'], entry['sha256']), 'contradictory dependency')
        declared[path] = (entry['bytes'], entry['sha256'])
        paths.add(path)
    bootstrap = artifacts['bootstrap'].read_bytes()
    mirror = read_json(artifacts['bootstrapMirror'])
    # Reuse the frozen bootstrap structural checks; the dummy receipt is not written or accepted.
    dummy = dict(schema='c48-root-run-v1', timedOut=False, parentPid=1, parentExit=0,
                 parentCreationFileTime='0000000000000001', startedUtc='2026-01-01T00:00:00Z',
                 endedUtc='2026-01-01T00:00:00Z', before={'dummy': '0'*64}, after={'dummy': '0'*64})
    contracts = {name: (a.CONTRACT / name).read_bytes() for name in a.CONTRACT_PINS}
    # Validator pins are the same frozen constants; enforce that relation explicitly.
    need(v.HEADER == a.HEADER and v.PINS == a.CONTRACT_PINS, 'validator pin mismatch')
    epoch = v.check_inputs(bootstrap, mirror, artifacts['header'].read_bytes(), contracts, dummy, [])
    raw, base, _ = a.pe_info(artifacts['childExe'])
    pe_offset = struct.unpack_from('<I', raw, 0x3c)[0]
    need(a.sha(raw) == epoch['childExeSHA256'] and struct.unpack_from('<I', raw, pe_offset + 24 + 56)[0] == mirror['imageBytes'], 'bootstrap EXE/image mismatch')
    labels = a.map_symbols(artifacts['childMap'], v.LABELS)
    need(all(labels[k] - base == mirror['labelsRva'][k] for k in v.LABELS), 'bootstrap map label mismatch')
    before = snapshot(sorted(paths, key=str))
    need(all(before.get(path) == value for path, value in first_hashes.items()), 'preflight input drift')
    need(all(before[str(path)] == values[1] for path, values in declared.items()), 'preflight dependency drift')
    return {k: str(p) for k, p in artifacts.items()}, sorted(paths, key=str), before


def write_receipt(path, receipt):
    with path.open('x', encoding='utf-8', newline='\n') as f:
        json.dump(receipt, f, ensure_ascii=False, sort_keys=True, indent=2)
        f.write('\n')


def run(args):
    output = args.output.resolve()
    need(output != OWNED.resolve() and output.is_relative_to(OWNED.resolve()), 'output outside owned witness directory')
    output.mkdir()  # exclusive: do not replace or reuse an existing run
    receipt = dict(schema='c48-root-run-v1', caseId=args.case, runId=args.run_id,
                   parentPid=None, parentCreationFileTime=None, parentExit=None, timedOut=False,
                   startedUtc=None, endedUtc=None, before={}, after={}, argv=[], artifacts={},
                   childCreated=False, parentCreated=False, cleanupConfirmed=None, error=None)
    try:
        need(args.case in (1, 2, 3) and re.fullmatch('[0-9a-f]{32}', args.run_id) is not None, 'invalid case/run id')
        artifacts, paths, before = preflight(args)
        receipt['artifacts'] = artifacts
        receipt['before'] = before
        native = output / 'native'
        receipt['argv'] = [artifacts['parentExe'], '--child-exe', artifacts['childExe'], '--bootstrap',
                           artifacts['bootstrap'], '--case', str(args.case), '--run-id', args.run_id,
                           '--output', str(native)]
        with (output / 'stdout.bin').open('xb') as stdout, (output / 'stderr.bin').open('xb') as stderr:
            receipt['startedUtc'] = utc()
            process = subprocess.Popen(receipt['argv'], stdout=stdout, stderr=stderr,
                                       creationflags=subprocess.CREATE_NO_WINDOW)
            receipt['parentCreated'] = True
            receipt['childCreated'] = None  # Only the parent can report native child creation.
            receipt['parentPid'] = process.pid
            try:
                need(type(process.pid) is int and process.pid > 0, 'invalid parent PID')
                receipt['parentCreationFileTime'] = creation_time(process._handle)
            except (OSError, ValueError) as exc:
                receipt['error'] = 'parent creation time: ' + str(exc)
                receipt['cleanupConfirmed'] = False
                process.kill()
            try:
                receipt['parentExit'] = process.wait(timeout=12)
            except subprocess.TimeoutExpired:
                receipt['timedOut'] = True
                receipt['cleanupConfirmed'] = False
                process.kill()
                try:
                    receipt['parentExit'] = process.wait(timeout=5)
                except subprocess.TimeoutExpired:
                    receipt['error'] = 'owned parent exit unconfirmed after kill'
            receipt['endedUtc'] = utc()
        try:
            receipt['after'] = snapshot(paths)
        except OSError as exc:
            receipt['after'] = {str(path): digest(path) if path.is_file() else None for path in paths}
            receipt['error'] = 'after snapshot failed: ' + str(exc)
        if receipt['before'] != receipt['after']:
            receipt['error'] = 'protected input drift'
        expected = 30 if args.case == 3 else 0
        success = not receipt['error'] and not receipt['timedOut'] and receipt['parentExit'] == expected
        if not success and not receipt['error']:
            receipt['error'] = 'unexpected parent exit or timeout'
    except (OSError, ValueError, KeyError, TypeError, struct.error) as exc:
        receipt['error'] = str(exc)
        if receipt['parentCreated']:
            receipt['endedUtc'] = receipt['endedUtc'] or utc()
            receipt['parentExit'] = process.poll()
            receipt['cleanupConfirmed'] = False
        if receipt['before'] and not receipt['after']:
            receipt['after'] = {str(path): digest(path) if path.is_file() else None for path in paths}
        success = False
    write_receipt(output / 'receipt.json', receipt)
    return 0 if success else 1


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--case', type=int, required=True)
    p.add_argument('--run-id', default=None)
    for name in ('build', 'bootstrap', 'bootstrap-mirror', 'dependency-review', 'output'):
        p.add_argument('--' + name, type=Path, required=True)
    args = p.parse_args()
    args.run_id = args.run_id or uuid.uuid4().hex
    try:
        return run(args)
    except (OSError, ValueError) as exc:
        print(str(exc), file=sys.stderr)
        return 1


if __name__ == '__main__':
    sys.exit(main())
