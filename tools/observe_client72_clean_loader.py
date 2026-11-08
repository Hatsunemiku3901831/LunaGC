"""Bounded zero-hook proxy startup and read-only module binding observation."""
import argparse
import ctypes as C
from ctypes import wintypes as W
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import struct
import subprocess

try:
    from .observe_client72_uninstrumented import ROOT, WindowsBackend, MODULEENTRY32W, EXPECTED, observe, sha256, file_state
    from .audit_sdk72_clean_loader import PE, require_exports, source_names, ENTRY_RVA, ENTRY_LENGTH
except ImportError:
    from observe_client72_uninstrumented import ROOT, WindowsBackend, MODULEENTRY32W, EXPECTED, observe, sha256, file_state
    from audit_sdk72_clean_loader import PE, require_exports, source_names, ENTRY_RVA, ENTRY_LENGTH

RAW = ROOT / 'local/migration72/d02-resume-20261108/clean-loader'
CALLER_WINDOWS = ((0x34ee6b, 76), (0x34ef3c, 76))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--artifact-audit', required=True, type=Path)
    parser.add_argument('--output', required=True, type=Path)
    parser.add_argument('--user-ready', action='store_true')
    args = parser.parse_args()
    if not args.user_ready:
        parser.error('A new user-ready response is required for this visible startup')
    output = args.output.resolve()
    if output.exists() or not output.is_relative_to(RAW.resolve()):
        parser.error('Use a new output inside the clean-loader private directory')
    evidence = dict(scope='Zero-hook transparent proxy startup; no debugger, injection, target call or authentication',
                    startUtc=datetime.now(timezone.utc).isoformat(), sourceSHA256=sha256(Path(__file__)), status='preflight')
    try:
        game = Path(subprocess.check_output(['powershell.exe', '-NoProfile', '-ExecutionPolicy', 'Bypass',
            '-File', str(ROOT/'tools/game_path.ps1')], text=True, encoding='utf-8').strip()).resolve()
        inputs, proxy, stock, sdk = verify_installed(game, args.artifact_audit)
        evidence['inputs'] = inputs
        backend = ModuleBackend()
        observe(backend, game, evidence, 90)
        evidence['binding'] = inspect_process(backend, game, evidence, proxy, stock, sdk)
        evidence['status'] = 'completed' if evidence['binding']['status'] == 'bound' else 'partial'
    except Exception as error:
        evidence.update(status='error', errorType=type(error).__name__, error=str(error),
                        winerror=getattr(error, 'winerror', None))
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(evidence, indent=2)+'\n', encoding='utf-8')
    print(json.dumps({'status': evidence['status'], 'binding': evidence.get('binding', {}).get('status'),
                      'd02Passed': False}))
    if evidence['status'] != 'completed':
        raise SystemExit(1)


def verify_installed(game, audit_path):
    audit_path = audit_path.resolve()
    if not audit_path.is_relative_to(RAW.resolve()):
        raise ValueError('Artifact audit must be inside this task private directory')
    audit = json.loads(audit_path.read_bytes())
    for name, expected in audit['source_hashes'].items():
        if sha256(ROOT/name) != expected:
            raise ValueError('Audited proxy source changed: '+name)
    profile = json.loads((ROOT/'protocol/cn-7.2/profile.json').read_bytes())
    checked = {}
    for name in ('binary', 'metadata', 'startup', 'sdk'):
        item = profile['files'][name]
        actual = sha256(game/item['path'])
        if item['sha256'] != EXPECTED[name] or actual != EXPECTED[name]:
            raise ValueError('Fixed stock input differs: '+name)
        checked[name] = {'path': item['path'], 'sha256': actual}
    plugins = game/'YuanShen_Data/Plugins'
    proxy, stock, sdk = [(plugins/name).read_bytes() for name in
                        ('Astrolabe.dll', 'Astrolabe_orig.dll', 'AccountPlatNative.dll')]
    production = audit['artifacts']['production']
    if digest(proxy) != production['sha256'] or len(proxy) != production['bytes']:
        raise ValueError('Installed proxy differs from the audited build')
    if digest(stock) != EXPECTED['astrolabe'] or profile['files']['astrolabe']['sha256'] != EXPECTED['astrolabe']:
        raise ValueError('Renamed original differs from fixed stock')
    require_exports(PE(proxy).exports(), source_names(), [('Sdk72CleanInspectLoadedSdk', 48)])
    require_exports(PE(stock).exports(), source_names())
    checked['proxy'] = {'path': 'YuanShen_Data/Plugins/Astrolabe.dll', 'sha256': digest(proxy)}
    checked['original'] = {'path': 'YuanShen_Data/Plugins/Astrolabe_orig.dll', 'sha256': digest(stock)}
    return {'files': checked, 'profileSHA256': sha256(ROOT/'protocol/cn-7.2/profile.json'),
            'artifactAuditSHA256': sha256(audit_path), 'profileChanged': False,
            'config': {name: file_state(ROOT/name) for name in ('.env', '.env.local', 'config.json')}}, proxy, stock, sdk


def digest(data):
    return hashlib.sha256(data).hexdigest()


def pointer_table(proxy):
    pe = PE(proxy)
    rows = [x for x in pe.exports() if x['name'].startswith('Astrolabe_')]
    locations = []
    for index, row in enumerate(rows):
        instruction = pe.bytes_at(row['rva'], 6)
        if instruction[:2] != b'\xff\x25':
            raise ValueError('Proxy export is not the reviewed RIP-relative indirect JMP')
        locations.append(row['rva']+6+struct.unpack_from('<i', instruction, 2)[0])
    if len(locations) != 47 or locations != [locations[0]+8*i for i in range(47)]:
        raise ValueError('Proxy export targets are not the exact contiguous 47-slot table')
    return locations[0]


class ModuleBackend(WindowsBackend):
    def self_probe(self):
        return {'value': None, 'scope': 'Module-only observer; no thread query'}

    def sample(self, pid, allow_query):
        return {'modules': self.snapshot(8|16, pid, MODULEENTRY32W, 'Module32FirstW', 'Module32NextW')}


def inspect_process(backend, game, evidence, proxy, stock, sdk):
    handle = backend.k.OpenProcess(0x410, False, evidence['pid'])
    if not handle:
        return {'status': 'process-unavailable', 'winerror': C.get_last_error()}
    try:
        if backend.creation_time(handle) != evidence['processCreationTime'] or Path(backend.image(handle)).resolve() != (game/'YuanShen.exe').resolve():
            raise ValueError('Created process identity changed before read-only inspection')
        if not backend.status(handle)['alive']:
            return {'status': 'target-exited'}
        modules = backend.snapshot(8|16, evidence['pid'], MODULEENTRY32W, 'Module32FirstW', 'Module32NextW')
        if modules['error'] is not None:
            return {'status': 'module-enumeration-error', 'winerror': modules['error']}
        wanted = {}
        for name, data in [('Astrolabe.dll', proxy), ('Astrolabe_orig.dll', stock), ('AccountPlatNative.dll', sdk)]:
            rows = [x for x in modules['items'] if x['name'].lower() == name.lower()]
            if len(rows) != 1:
                return {'status': 'module-not-uniquely-loaded', 'module': name, 'count': len(rows)}
            row = rows[0]
            if Path(row['path']).resolve() != (game/'YuanShen_Data/Plugins'/name).resolve() or row['size'] != PE(data).image_size or sha256(Path(row['path'])) != digest(data):
                raise ValueError('Loaded module path/size/disk identity differs: '+name)
            wanted[name] = row
        backend.k.ReadProcessMemory.argtypes = [W.HANDLE, C.c_void_p, C.c_void_p, C.c_size_t, C.POINTER(C.c_size_t)]
        backend.k.ReadProcessMemory.restype = W.BOOL
        def read(address, width):
            buffer = C.create_string_buffer(width); length = C.c_size_t()
            ok = backend.k.ReadProcessMemory(handle, address, buffer, width, C.byref(length))
            if not ok or length.value != width:
                raise OSError(C.get_last_error(), 'Bounded module window read failed')
            return buffer.raw
        binding = inspect_windows(read, wanted, proxy, stock, sdk)
        binding['modules'] = wanted
        binding['statusAtEnd'] = backend.status(handle)
        return binding
    finally:
        backend.close(handle)


def inspect_windows(read, modules, proxy, stock, sdk):
    table = pointer_table(proxy)
    target = modules['Astrolabe.dll']['base']+table
    observed = struct.unpack('<47Q', read(target, 376))
    original_base = modules['Astrolabe_orig.dll']['base']
    expected = tuple(original_base+x['rva'] for x in PE(stock).exports())
    rows = [{'name': 'proxy.forward-targets', 'bytes': 376, 'equal': observed == expected}]
    pe = PE(sdk); base = modules['AccountPlatNative.dll']['base']
    for name, rva, width in [('sdk.encrypt', ENTRY_RVA, ENTRY_LENGTH),
                             ('sdk.account-caller', *CALLER_WINDOWS[0]), ('sdk.password-caller', *CALLER_WINDOWS[1])]:
        observed_code = read(base+rva, width)
        original_code = pe.bytes_at(rva, width)
        rows.append({'name': name, 'bytes': width, 'equal': observed_code == original_code,
                     'diskCodeSHA256': digest(original_code)})
    return {'status': 'bound' if all(x['equal'] for x in rows) else 'window-or-target-mismatch',
            'windows': rows, 'readCalls': 4, 'readBytes': 781, 'targetFunctionsCalled': False,
            'hookInstalled': False, 'naturalCallerObserved': False, 'd02Passed': False}


if __name__ == '__main__':
    main()
