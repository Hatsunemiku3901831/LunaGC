"""Source-bound CN72 cache sampling; fresh monitor and explicit live readiness required."""
import argparse
import ctypes as C
from ctypes import wintypes as W
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'local/migration72/python'))
try:
    from . import sample_client72_interface_context as environment
    from . import probe72_avatar_method_cache as probe
except ImportError:
    import sample_client72_interface_context as environment
    import probe72_avatar_method_cache as probe

RAW = ROOT / 'local/migration72/d02-resume-20261109/r1-method-cache/live'
SOURCE_PINS = {
    'tools/observe_client72_uninstrumented.py': 'e0b4134e6bb7be8c758b3a7bad3884a8246b25c6d65cd8ca3c91d0941ed33c6e',
    'tools/sample_client72_interface_context.py': '02ffaeab9c5e2f96bd135f7007b1f964c2863fb16471611c03867b3aefdda251',
    'tools/probe72_avatar_method_cache.py': '6053f724185e2b2c7e75503021beb2f5cbde18319c7aac6edb1dfec7ed7d8544',
}


def digest(raw):
    return hashlib.sha256(raw).hexdigest()


def execute(observation):
    """No launch or target mutation. Keep the handle bound to one observed creation."""
    for name, expected in SOURCE_PINS.items():
        if digest((ROOT / name).read_bytes()) != expected:
            raise ValueError('Frozen sampler source differs: ' + name)
    raw = observation.read_bytes()
    source = json.loads(raw.decode('utf-8-sig'))
    if (source.get('status') != 'completed' or not source['finalStatus']['alive']
            or source['elapsedSeconds'] < 90 or source['sourceSHA256'] != environment.MONITOR_SHA):
        raise ValueError('A completed live 90-second stock monitor is required')
    game = Path(source['launch']['cwd']).resolve()
    target = game / 'YuanShen.exe'
    if not environment.same_file(source['image'], target) or not environment.same_file(source['launch']['executable'], target):
        raise ValueError('Observed image identity differs')
    inputs = environment.verified_inputs(ROOT, game)
    original = source['inputs']
    if inputs['profileSHA256'] != original['profileSHA256'] or inputs['files'] != original['files']:
        raise ValueError('Monitor fixed inputs differ')
    for name in ('.env', '.env.local', 'config.json'):
        if any(original['config'][name].get(key) != inputs['config'][name].get(key)
                for key in ('exists', 'sha256', 'error')):
            raise ValueError('Monitor configuration differs: ' + name)
    profile = json.loads((ROOT / 'protocol/cn-7.2/profile.json').read_bytes())
    binary = target.read_bytes()
    pins, windows, context = probe.verify_sources(binary,
        (game / profile['files']['metadata']['path']).read_bytes())
    pe = environment.PE(binary)
    # SizeOfImage is a loader identity check, not an arbitrary address budget.
    import struct
    optional = struct.unpack_from('<I', pe.data, 0x3c)[0] + 24
    image_size = struct.unpack_from('<I', pe.data, optional + 56)[0]
    observed = environment.main_module(source['samples'][-1]['modules'], target)
    if observed['size'] != image_size:
        raise ValueError('Observed image size differs')
    backend = environment.WindowsBackend()
    handle = backend.k.OpenProcess(environment.PROCESS_READ_QUERY_WAIT, False, source['pid'])
    if not handle:
        raise C.WinError(C.get_last_error())
    try:
        creation = backend.creation_time(handle)
        if creation['filetime'] is None or creation != source['processCreationTime']:
            raise ValueError('Live process creation differs')
        if not environment.same_file(backend.image(handle), target) or not backend.status(handle)['alive']:
            raise ValueError('Live image or lifetime differs')
        modules = backend.snapshot(8 | 16, source['pid'], environment.MODULEENTRY32W,
            'Module32FirstW', 'Module32NextW')
        current = environment.main_module(modules, target)
        if current['base'] != observed['base'] or current['size'] != observed['size']:
            raise ValueError('Live module binding differs')
        backend.k.ReadProcessMemory.argtypes = [W.HANDLE, C.c_void_p, C.c_void_p,
            C.c_size_t, C.POINTER(C.c_size_t)]
        backend.k.ReadProcessMemory.restype = W.BOOL

        def read(address, width):
            buffer, count = C.create_string_buffer(width), C.c_size_t()
            ok = backend.k.ReadProcessMemory(handle, address, buffer, width, C.byref(count))
            error = None if ok and count.value == width else {
                'winerror': C.get_last_error(), 'bytesRead': count.value}
            return buffer.raw[:count.value], error

        started = datetime.now(timezone.utc).isoformat()
        result = probe.collect(read, current['base'])
        final_modules = backend.snapshot(8 | 16, source['pid'], environment.MODULEENTRY32W,
            'Module32FirstW', 'Module32NextW')
        final = backend.status(handle)
        try:
            end_module = environment.main_module(final_modules, target)
            end_valid = (final['alive'] and backend.creation_time(handle) == creation
                and environment.same_file(backend.image(handle), target)
                and end_module['base'] == current['base'] and end_module['size'] == current['size'])
        except (ValueError, OSError):
            end_valid = False
        if not end_valid:
            result.update(status='partial', failure=result['failure'] or 'end-process-identity-or-lifetime')
        result.update(timeUtc=started, finishedUtc=datetime.now(timezone.utc).isoformat(),
            pid=source['pid'], creation=creation, mainModule=current, finalStatus=final,
            endIdentityVerified=end_valid, inputChecks=inputs, sourceObservationSHA256=digest(raw),
            sourceSHA256=digest(Path(__file__).read_bytes()), verifiedSources=SOURCE_PINS,
            probeSourcePins=pins, codeWindows=windows, staticContextSource=context,
            liveAdapterImplemented=True, naturalSamplingStarted=True)
        return result
    finally:
        backend.close(handle)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--observation', required=True, type=Path)
    parser.add_argument('--output', required=True, type=Path)
    parser.add_argument('--user-ready', action='store_true')
    args = parser.parse_args()
    if not args.user_ready:
        parser.error('A new explicit user-ready for this prepared experiment is required')
    output = args.output.resolve()
    if not output.is_relative_to(RAW.resolve()) or output.exists() or output.with_suffix('.source.py').exists():
        parser.error('Choose a new output inside the method-cache live directory')
    output.parent.mkdir(parents=True, exist_ok=True)
    output.with_suffix('.source.py').write_bytes(Path(__file__).read_bytes())
    try:
        result = execute(args.observation.resolve())
    except (OSError, ValueError, KeyError, IndexError) as error:
        result = dict(task='R1-METHOD-CACHE-live-adapter', status='error', error=str(error),
            sourceSHA256=digest(Path(__file__).read_bytes()), timeUtc=datetime.now(timezone.utc).isoformat(),
            naturalLookupObserved=False, completeAvatarHandler=False, d02Complete=False)
    with output.open('x', encoding='utf-8') as stream:
        json.dump(result, stream, indent=2)
        stream.write('\n')
    print(json.dumps(dict(status=result['status'], reads=result.get('budget', {}).get('reads'),
        requestedBytes=result.get('budget', {}).get('requestedBytes'), naturalLookupObserved=False,
        completeAvatarHandler=False, d02Complete=False)))
    if result['status'] != 'completed':
        raise SystemExit(1)


if __name__ == '__main__':
    main()
