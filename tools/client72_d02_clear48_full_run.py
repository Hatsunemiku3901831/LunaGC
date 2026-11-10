"""Root-only runner for the two fixed full-helper cases; never attaches to a PID."""
import argparse
import ctypes
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
PRIVATE = ROOT / 'local/migration72/d02-resume-20261110b'
OWNED = PRIVATE / 'a-clear48-full-helper-owned'
CONTRACT = PRIVATE / 'a-clear48-full-helper-contract'
HEADER = ROOT / 'tools/client72_d02_clear48_full_helper/shared.h'
HEADER_SHA = '26fafcb733635adfc62b1a8afcc6a9139ab69142196e7a02bd43fdff2b77ff48'
SOURCE_SHA = '34287f706e27541c8119091aa9e55c7e2916610935f90c9b74992856b9654b31'
PINS = {
    'catalog.v1.private.json': '02d1803ec7afd6162c23584a76315adaedbeeab81fd83214a76a32a50ecc491e',
    'cases.v1.private.json': 'ae3fe4ec1634dd7b93e177b739ab9868e0dbaf4c7308e5ab563af8d6add8651a',
    'interface.v1.private.json': '5c513a0bf18bd84750b38bda40fd019140209673d2f74264305880fd9c761597',
    'events-and-results.v1.private.json': '795974998cb25d114cefaff53fd7615f0d6c99fe5daf8226662136f8beaf5a26',
    'packages.v1.private.json': '0fb6dec8888a15e12e143ea0c2d3192087f8f19fdde70ee71075860add5c1d25',
    'shared.h.proposal': '7cbe71b1ac1af277b8b727804e0d14d22e9e7e4074f453d03329b19c5891d59d',
}
SUPPLEMENTS = {
    OWNED / 'root-bindings.v1.private.json': 'cf25a1a0f16a3b11f08ac719b6197e71aa6fba6a903a709abd86c76197494e6a',
    OWNED / 'child-api-records.v1.private.json': 'a478fce47774611d39407d17a65d04a0ba7fb41504c43643543881af9cff69c1',
    OWNED / 'build-review-schema.v1.private.json': '49a8887844d1be080e7a372eeb531512e0d079efc28ccdb0003177884b8d02a1',
    OWNED / 'transaction-owned-mapping.v1.private.json': '39391d1a1131b417b0ac910fca1e4f4b603622b425758be5b512c4bc9376d06d',
    OWNED / 'root-receipt-semantics.v1.private.json': 'a5ed259f2ad430ff63ecf8b3efc92bb5f76d13a6f2b162ae697ed02ae5299675',
    CONTRACT / 'contract-artifacts.private.json': '66bf1cc7d2506e82741576f043412daf21a2299634021b660de06d9de6c81cdd',
    PRIVATE / 'a-clear48-backend/input-pins.private.json': 'f02965fbe282ddfac9373df863a456f2ca26af4792dcfaf1db99c8ff1f2e9310',
    PRIVATE / 'a-clear48-trace-exit-lifecycle/exit-lifecycle-supplement.v2.private.json': 'cf52cfafa00eab47316a756a5e04a1edc59ca2b5606d4fa89d6cc2cb552105d8',
    PRIVATE / 'a-clear48-full-helper-boot-contract/boot-lifecycle-supplement.v1.private.json': '2b78eed73664f85605bc693d85ab4562a68158e53d28ae2d6973479ef52ab338',
}
BUILD_SOURCES = ('shared.h', 'child.c', 'full_wrapper.asm', 'parent.c', 'build.ps1')
REVIEW_LISTS = ('actualCompiledSources', 'reviewedCurrentSources', 'source', 'buildLogs',
                'buildTools', 'compilerDlls', 'includes', 'libraries', 'searchedLibraries')


def need(value, reason):
    if not value:
        raise ValueError(reason)


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def unique_object(pairs):
    result = {}
    for name, value in pairs:
        need(name not in result, 'duplicate JSON key: ' + name)
        result[name] = value
    return result


def read_json(path):
    return json.loads(path.read_text(encoding='utf-8-sig'), object_pairs_hook=unique_object)


def utc():
    return datetime.datetime.now(datetime.timezone.utc).isoformat()


def creation_time(handle):
    from ctypes import wintypes
    kernel = ctypes.WinDLL('kernel32', use_last_error=True)
    fn = kernel.GetProcessTimes
    fn.argtypes = [wintypes.HANDLE] + [ctypes.POINTER(wintypes.FILETIME)] * 4
    fn.restype = wintypes.BOOL
    values = [wintypes.FILETIME() for _ in range(4)]
    if not fn(wintypes.HANDLE(int(handle)), *(ctypes.byref(value) for value in values)):
        raise OSError(ctypes.get_last_error(), 'GetProcessTimes for own parent')
    return f'{(values[0].dwHighDateTime << 32) | values[0].dwLowDateTime:016x}'


def preflight(args):
    need(type(args.case) is int and args.case in (257, 258), 'fixed full-helper case required')
    need(re.fullmatch('[0-9a-f]{32}', args.run_id) is not None, 'new run id format')
    build, bootstrap_dir = args.build.resolve(), args.bootstrap.resolve()
    need(build.is_dir() and build.is_relative_to(OWNED.resolve()) and
         bootstrap_dir.is_dir() and bootstrap_dir.is_relative_to(OWNED.resolve()), 'owned build/bootstrap required')
    need(args.contract_dir.resolve() == CONTRACT.resolve(), 'frozen contract directory required')
    artifact_paths = dict(parentExe=build/'parent.exe', childExe=build/'child.exe', header=HEADER,
                          bootstrap=bootstrap_dir/'bootstrap.bin', bootstrapMirror=bootstrap_dir/'bootstrap.private.json',
                          helper=args.helper.resolve(), dependencyReview=args.dependency_review.resolve(), contractDirectory=CONTRACT)
    required = {path.resolve() for name, path in artifact_paths.items() if name != 'contractDirectory'}
    pins = {CONTRACT/name: sha for name, sha in PINS.items()}
    pins.update(SUPPLEMENTS)
    pins[HEADER] = HEADER_SHA
    for path, expected in pins.items():
        need(digest(path) == expected, 'fixed source drift: ' + str(path))
        required.add(path.resolve())
    need(artifact_paths['helper'].stat().st_size == 904 and digest(artifact_paths['helper']) == SOURCE_SHA, 'fixed904 input')
    old_inputs = read_json(PRIVATE/'a-clear48-backend/input-pins.private.json')
    need(len(old_inputs) == 112, 'old112 input count')
    for relative, record in old_inputs.items():
        path = (ROOT/relative).resolve()
        need(path.stat().st_size == record['bytes'] and digest(path) == record['sha256'], 'old112 drift: ' + relative)
        required.add(path)
    record = read_json(build/'build-record.private.json')
    need(record['schema'] == 'c48-full-helper-build-v1' and type(record['exit']) is int and record['exit'] == 0 and
         record['sharedHeaderSHA256'] == HEADER_SHA and record['fixtureExecuted'] is False and
         record['original904Executed'] is False, 'build gate')
    sources = record['source']
    need(len(sources) == 5 and {s['name'] for s in sources} == set(BUILD_SOURCES), 'five build sources')
    for entry in sources:
        path = build/entry['name']; current = HEADER.parent/entry['name']
        need(path.stat().st_size == entry['bytes'] and digest(path) == entry['sha256'] and
             path.read_bytes() == current.read_bytes(), 'current and built source differ')
        required.update((path.resolve(), current.resolve()))
    bootstrap = artifact_paths['bootstrap'].read_bytes()
    need(len(bootstrap) == 256, 'bootstrap256')
    values = struct.unpack('<8sIIIIQ160s8IQ4I8s', bootstrap)
    magic, version, size, descriptor, mask, image_size, hashes, *rest = values
    arm, call, after, end, begin, desc_bytes, helper_rva, helper_bytes, arena, exit_v, flags_v, count, wrapper_bytes, reserved = rest
    need((magic, version, size, mask, desc_bytes, helper_rva, helper_bytes, arena, exit_v, flags_v, count, wrapper_bytes, reserved) ==
         (b'C48FBO01', 1, 256, 3, 384, 0x1fdf9c0, 904, 0x6d20000, 2, 2, 2, 27, bytes(8)), 'bootstrap ABI')
    need(descriptor+384 <= image_size and 0 < begin < end <= image_size and
         (arm, call, after, end) == (begin+18, begin+19, begin+22, begin+27), 'bootstrap labels/extents')
    expected_hashes = [digest(artifact_paths['childExe']), HEADER_SHA, SOURCE_SHA,
                       PINS['catalog.v1.private.json'], PINS['cases.v1.private.json']]
    need([hashes[n:n+32].hex() for n in range(0, 160, 32)] == expected_hashes, 'bootstrap sources')
    mirror = read_json(artifact_paths['bootstrapMirror'])
    need(mirror['schema'] == 'c48-full-helper-bootstrap-root-v1' and mirror['executed'] is False and
         mirror['bootstrapSHA256'] == hashlib.sha256(bootstrap).hexdigest() and
         mirror['imageBytes'] == image_size, 'bootstrap mirror')
    for name, expected in zip(('childExeSHA256','sharedHeaderSHA256','source904SHA256','catalogSHA256','casesSHA256'), expected_hashes):
        need(mirror[name] == expected, 'mirror source')
    need(mirror['labelsRva']['C48FullWrapperBegin'] == begin and mirror['labelsRva']['C48FullArm'] == arm and
         mirror['labelsRva']['C48FullCall'] == call and mirror['labelsRva']['C48FullAfterCall'] == after and
         mirror['labelsRva']['C48FullWrapperEnd'] == end and mirror['labelsRva']['g_c48FullDescriptor'] == descriptor, 'mirror labels')
    review = read_json(artifact_paths['dependencyReview'])
    need(review['schema'] == 'c48-full-helper-dependency-review-v1' and review['actualBuildExit'] == 0 and
         all(review[k] is True for k in ('independentlyReviewed','peAndCallerUnwindReviewed','dependencyProofComplete')), 'independent review gate')
    declared = {}
    for key in REVIEW_LISTS:
        entries = review[key]
        need(isinstance(entries, list) and (entries or key == 'searchedLibraries'), 'empty review array: ' + key)
        for entry in entries:
            path = Path(entry['path']).resolve(); value = (entry['bytes'], entry['sha256'])
            need(path.is_file() and path.stat().st_size == value[0] and digest(path) == value[1], 'dependency drift: ' + str(path))
            need(path not in declared or declared[path] == value, 'contradictory dependency')
            declared[path] = value
    need(isinstance(review['artifacts'], dict) and review['artifacts'], 'review artifacts missing')
    for entry in review['artifacts'].values():
        path = Path(entry['path']).resolve(); value = (entry['bytes'], entry['sha256'])
        need(path.is_file() and path.stat().st_size == value[0] and digest(path) == value[1], 'artifact drift')
        need(path not in declared or declared[path] == value, 'contradictory artifact')
        declared[path] = value
    required.update((Path(__file__).resolve(), (ROOT/'tools/client72_d02_clear48_full_generate.py').resolve(),
                     (ROOT/'tools/client72_d02_clear48_full_artifacts.py').resolve(),
                     (ROOT/'tools/client72_d02_clear48_full_validate.py').resolve(),
                     (build/'build-record.private.json').resolve(), (CONTRACT/'frozen-inputs.private.json').resolve()))
    # The review file cannot include its own hash; the runner snapshots it directly.
    need(required - {artifact_paths['dependencyReview']} <= set(declared), 'review omits a required source/artifact')
    paths = sorted(set(declared) | required)
    return {k: str(p.resolve()) for k, p in artifact_paths.items()}, paths


def snapshot(paths):
    return {str(path): digest(path) for path in paths}


def run(args):
    output = args.output.resolve()
    need(output.is_relative_to(OWNED.resolve()) and not output.exists(), 'new owned run directory required')
    output.mkdir(parents=True)
    receipt = dict(schema='c48-full-helper-root-run-v1', runId=args.run_id, caseId=args.case,
                   argv=[], artifacts={}, before={}, after={}, parentCreated=False, childCreated=None,
                   parentPid=None, parentCreationFileTime=None, parentExit=None, timedOut=False,
                   cleanupConfirmed=False, startedUtc=None, endedUtc=None, error=None)
    paths = []; process = None; success = False
    try:
        artifacts, paths = preflight(args)
        receipt['artifacts'] = artifacts; receipt['before'] = snapshot(paths)
        receipt['argv'] = [artifacts['parentExe'], '--child-exe', artifacts['childExe'], '--bootstrap', artifacts['bootstrap'],
                           '--helper', artifacts['helper'], '--case', str(args.case), '--run-id', args.run_id,
                           '--output', str(output/'native')]
        with (output/'parent.stdout').open('xb') as stdout, (output/'parent.stderr').open('xb') as stderr:
            receipt['startedUtc'] = utc()
            process = subprocess.Popen(receipt['argv'], stdout=stdout, stderr=stderr, creationflags=subprocess.CREATE_NO_WINDOW)
            receipt['parentCreated'] = True; receipt['parentPid'] = process.pid
            try:
                receipt['parentCreationFileTime'] = creation_time(process._handle)
            except OSError as exc:
                receipt['error'] = str(exc); process.kill()
            try:
                receipt['parentExit'] = process.wait(timeout=12)
            except subprocess.TimeoutExpired:
                receipt['timedOut'] = True; process.kill()
                try:
                    receipt['parentExit'] = process.wait(timeout=5)
                except subprocess.TimeoutExpired:
                    receipt['error'] = 'owned parent exit unconfirmed after kill'
            receipt['endedUtc'] = utc()
        receipt['after'] = snapshot(paths)
        elapsed = (datetime.datetime.fromisoformat(receipt['endedUtc']) - datetime.datetime.fromisoformat(receipt['startedUtc'])).total_seconds()
        need(receipt['before'] == receipt['after'], 'protected input drift')
        need(not receipt['timedOut'] and receipt['parentExit'] == 0 and receipt['error'] is None and 0 <= elapsed <= 10, 'parent exit/time gate')
        receipt['cleanupConfirmed'] = True; success = True
    except (OSError, ValueError, KeyError, TypeError, struct.error) as exc:
        receipt['error'] = receipt['error'] or str(exc)
        if process is not None:
            receipt['parentExit'] = process.poll()
            receipt['endedUtc'] = receipt['endedUtc'] or utc()
        if receipt['before'] and not receipt['after']:
            receipt['after'] = {str(p): digest(p) if p.is_file() else None for p in paths}
    with (output/'receipt.json').open('x', encoding='utf-8', newline='\n') as handle:
        json.dump(receipt, handle, sort_keys=True, indent=2); handle.write('\n')
    return 0 if success else 1


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--case', type=int, required=True)
    parser.add_argument('--run-id', default=None)
    for name in ('build','bootstrap','dependency-review','contract-dir','helper','output'):
        parser.add_argument('--'+name, type=Path, required=True)
    args = parser.parse_args(); args.run_id = args.run_id or uuid.uuid4().hex
    try:
        return run(args)
    except (OSError, ValueError) as exc:
        print(str(exc), file=sys.stderr); return 1


if __name__ == '__main__':
    sys.exit(main())
