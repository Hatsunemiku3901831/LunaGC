"""Run compiled owned DLL fixtures only, retaining fixed public results and identity proof."""
from __future__ import annotations
import argparse
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys

try:
    from . import audit_sdk72_clean_loader as audit
except ImportError:
    import audit_sdk72_clean_loader as audit

RAW = audit.RAW
EXPECTED_ABI = {
    'scope': 'self-owned DLLs only; no client DLL loaded', 'baseline_calls': 94,
    'forwarded_comparisons': 188, 'cold_calls': 1, 'hot_calls': 187, 'exports': 47,
    'original_calls_once': True, 'full_gpr': True, 'xmm0_15': True, 'flags': True,
    'stack_128_bytes': True, 'high64': True, 'caller_last_error': True,
    'returned_last_error': True, 'original_rax_xmm_return': True, 'normal_detach_reload': True,
    'original_held_for_process': True, 'loaded_binding_positive': True, 'binding_rejections': 6,
    'production_sdk_absent_status': 10, 'production_diagnostic_last_error': True,
    'sdk_load_attempted': False, 'hooks_installed': 0,
    'uncovered': ['target DLL execution', 'natural loader timing', 'YMM/ZMM upper halves',
                  'x87/MXCSR', 'exception unwinding', 'concurrent unload'],
}
EXCEPTION = 0xe0720001
REJECTIONS = {'missing': (2, 2), 'hash': (3, 0), 'deny-attach': (4, 1114), 'missing-export': (7, 127)}


def confined(path):
    path = Path(path).resolve()
    if not path.is_relative_to(RAW.resolve()):
        raise ValueError('Owned fixture paths must remain in this task directory')
    return path


def identity(path):
    path = Path(path)
    data = path.read_bytes()
    return {'path': path.resolve().relative_to(audit.ROOT).as_posix(),
            'sha256': audit.digest(data), 'bytes': len(data)}


def checked_abi(completed):
    result = {'raw_returncode': completed.returncode, 'stdout_bytes': len(completed.stdout),
              'stderr_bytes': len(completed.stderr), 'passed': False}
    try:
        payload = json.loads(completed.stdout)
    except (ValueError, UnicodeDecodeError):
        return result
    # Persist no unrecognized stdout/stderr. Only this exact fixed boolean/count schema is public.
    if json.dumps(payload, sort_keys=True) != json.dumps(EXPECTED_ABI, sort_keys=True) or completed.stderr:
        return result
    result['public_payload'] = payload
    result['passed'] = completed.returncode == 0
    return result


def checked_rejection(completed, stage, win32):
    unsigned = completed.returncode & 0xffffffff
    match = re.fullmatch(rb'sdk72-clean-bind-failed stage=([0-9]+) win32=([0-9]+)\r?\n', completed.stderr)
    result = {'raw_returncode': completed.returncode, 'unsigned_returncode': unsigned,
              'exit_hex': f'0x{unsigned:08x}', 'expected_exception': '0xe0720001',
              'stdout_bytes': len(completed.stdout), 'stderr_bytes': len(completed.stderr),
              'stderr_fixed_format': bool(match), 'passed': False}
    if match:
        actual_stage, actual_win32 = map(int, match.groups())
        result['stage'], result['win32_error'] = actual_stage, actual_win32
        result['public_stderr'] = completed.stderr.decode('ascii')
        result['passed'] = unsigned == EXCEPTION and not completed.stdout and (actual_stage, actual_win32) == (stage, win32)
    return result


def execute(command, directory, environment):
    try:
        return subprocess.run(command, cwd=directory, env=environment, stdout=subprocess.PIPE,
                              stderr=subprocess.PIPE, timeout=45, check=False)
    except subprocess.TimeoutExpired as error:
        # Unknown partial payloads are intentionally not copied into logs or reports.
        return subprocess.CompletedProcess(command, -1, b'', b'')


def write_json(path, result):
    Path(path).write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')


def stage(directory, original, owned, production=None):
    directory.mkdir()
    copied = {}
    for name, source in [('OwnedAstrolabe.dll', original), ('owned-proxy.dll', owned),
                         ('production-proxy.dll', production)]:
        if source is not None:
            destination = directory / name
            shutil.copyfile(source, destination)
            if audit.digest(destination.read_bytes()) != audit.digest(Path(source).read_bytes()):
                raise ValueError('Owned artifact copy identity differs')
            copied[name] = destination
    return copied


def verify(output, fixture, production, owned, normal, deny, omit, artifact_audit):
    output = confined(output)
    if output.exists():
        raise FileExistsError('Preserve previous run; choose a new output directory')
    artifact_audit = confined(artifact_audit)
    sources = json.loads(artifact_audit.read_bytes())
    for relative, expected in sources['source_hashes'].items():
        if audit.digest((audit.ROOT / relative).read_bytes()) != expected:
            raise ValueError('Rust/asm source changed after the main build audit: ' + relative)
    names = audit.source_names()
    artifacts = {'fixture': fixture, 'production': production, 'owned': owned,
                 'normal_mock': normal, 'deny_mock': deny, 'omit_mock': omit}
    artifacts = {key: confined(path) for key, path in artifacts.items()}
    for kind in ['production', 'owned']:
        if audit.digest(artifacts[kind].read_bytes()) != sources['artifacts'][kind]['sha256']:
            raise ValueError('Compiled proxy differs from the main artifact audit')
    for kind, extra, selected in [('production', [('Sdk72CleanInspectLoadedSdk', 48)], names),
                                   ('owned', [], names), ('normal_mock', [('OwnedMockAttachCount', 48)], names),
                                   ('deny_mock', [('OwnedMockAttachCount', 48)], names),
                                   ('omit_mock', [('OwnedMockAttachCount', 48)], names[:46])]:
        audit.require_exports(audit.PE(artifacts[kind].read_bytes()).exports(), selected, extra)
    output.mkdir(parents=True)
    proof = {'scope': 'fresh self-owned processes only; production forwarding exports never called',
             'source_hashes_at_main_build_audit': sources['source_hashes'],
             'main_artifact_audit': identity(artifact_audit),
             'artifacts': {kind: identity(path) for kind, path in artifacts.items()},
             'tools': {path: identity(audit.ROOT / path) for path in [
                 'tools/audit_sdk72_clean_loader.py', 'tools/test_audit_sdk72_clean_loader.py',
                 'tools/verify_sdk72_clean_loader.py', 'tools/test_verify_sdk72_clean_loader.py']},
             'build_inputs': {}, 'build_logs': {}, 'abi': [], 'rejections': {},
             'production_calls': 'DllMain and diagnostic inspector only; no Astrolabe export',
             'real_stock_or_sdk_loaded': False}
    for path in [audit.ROOT / 'patch/Cargo.toml', audit.ROOT / 'patch/Cargo.lock']:
        if path.exists():
            proof['build_inputs'][path.relative_to(audit.ROOT).as_posix()] = identity(path)
    for path in sorted((RAW / 'build-inputs').glob('*.def')):
        proof['build_inputs'][path.relative_to(audit.ROOT).as_posix()] = identity(path)
    for path in sorted(RAW.glob('build-*.log')):
        proof['build_logs'][path.name] = identity(path)
    environment = dict(os.environ)
    environment['SDK72_CLEAN_OWNED_ORIGINAL_SHA'] = audit.digest(artifacts['normal_mock'].read_bytes())
    public_runs = []
    for name in ['first', 'repeat']:
        directory = output / ('abi-' + name)
        files = stage(directory, artifacts['normal_mock'], artifacts['owned'], artifacts['production'])
        command = [str(artifacts['fixture']), 'abi', str(files['OwnedAstrolabe.dll']),
                   str(files['owned-proxy.dll']), str(files['production-proxy.dll'])]
        completed = execute(command, directory, environment)
        result = checked_abi(completed)
        proof['abi'].append(result)
        write_json(output / ('abi-' + name + '-result.json'), result)
        if result['passed']:
            (output / ('abi-' + name + '-public.json')).write_bytes(completed.stdout)
            public_runs.append(completed.stdout)
    proof['abi_byte_identical'] = len(public_runs) == 2 and public_runs[0] == public_runs[1]
    for name, (expected_stage, expected_win32) in REJECTIONS.items():
        original = {'missing': None, 'hash': artifacts['normal_mock'],
                    'deny-attach': artifacts['deny_mock'], 'missing-export': artifacts['omit_mock']}[name]
        directory = output / ('reject-' + name)
        files = stage(directory, original, artifacts['owned'])
        child_environment = dict(environment)
        child_environment['SDK72_CLEAN_OWNED_ORIGINAL_SHA'] = (
            '0' * 64 if name == 'hash' else audit.digest(original.read_bytes()) if original else environment['SDK72_CLEAN_OWNED_ORIGINAL_SHA'])
        completed = execute([str(artifacts['fixture']), 'reject-child', str(files['owned-proxy.dll'])], directory, child_environment)
        result = checked_rejection(completed, expected_stage, expected_win32)
        proof['rejections'][name] = result
        write_json(output / ('reject-' + name + '-result.json'), result)
        if 'public_stderr' in result:
            (output / ('reject-' + name + '-public.stderr.txt')).write_text(result['public_stderr'], encoding='ascii', newline='')
    proof['all_passed'] = proof['abi_byte_identical'] and all(row['passed'] for row in proof['abi']) and all(row['passed'] for row in proof['rejections'].values())
    proof['remaining_real_load_contract'] = [
        'fresh user ready and fixed five-input/process inventory',
        'reviewed reversible transparent proxy install and original ownership',
        'real loader/export invocation timing and native identity/signature decision',
        'natural loaded SDK Windows path/base/size/disk and exact entry-window binding',
        'actual client lifecycle and original return/error behavior',
        'no key/trust/route hooks enabled by this work',
    ]
    write_json(output / 'source-build-artifact-proof.json', proof)
    return proof


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output-directory', type=Path, required=True)
    parser.add_argument('--fixture', type=Path, default=RAW / 'target/release/examples/sdk72_clean_proxy_fixture.exe')
    parser.add_argument('--production', type=Path, default=RAW / 'target/release/examples/sdk72_clean_proxy.dll')
    parser.add_argument('--owned', type=Path, default=RAW / 'target/release/examples/sdk72_clean_proxy_owned.dll')
    parser.add_argument('--normal', type=Path, default=RAW / 'mock-normal/sdk72_clean_mock.dll')
    parser.add_argument('--deny', type=Path, default=RAW / 'mock-deny/sdk72_clean_mock.dll')
    parser.add_argument('--omit', type=Path, default=RAW / 'mock-omit/sdk72_clean_mock.dll')
    parser.add_argument('--artifact-audit', type=Path, default=RAW / 'offline-artifacts-main1.json')
    args = parser.parse_args()
    proof = verify(args.output_directory, args.fixture, args.production, args.owned,
                   args.normal, args.deny, args.omit, args.artifact_audit)
    print(json.dumps({'all_passed': proof['all_passed'], 'fresh_abi_processes': 2,
                      'rejection_processes': 4, 'abi_byte_identical': proof['abi_byte_identical'],
                      'client_dlls_loaded': 0, 'production_forwarding_calls': 0}))
    return 0 if proof['all_passed'] else 1


if __name__ == '__main__':
    sys.exit(main())
