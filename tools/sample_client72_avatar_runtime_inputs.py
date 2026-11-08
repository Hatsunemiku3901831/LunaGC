"""Bounded CN72 runtime input and two-scalar sampling; fresh stock readiness required."""
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
    from . import collect72_avatar_runtime_inputs as runtime
except ImportError:
    import sample_client72_interface_context as environment
    import probe72_avatar_method_cache as probe
    import collect72_avatar_runtime_inputs as runtime

RAW = ROOT / 'local/migration72/d02-resume-20261109/r1-runtime-inputs/live'
SOURCE_PINS = {
    'tools/observe_client72_uninstrumented.py': 'e0b4134e6bb7be8c758b3a7bad3884a8246b25c6d65cd8ca3c91d0941ed33c6e',
    'tools/sample_client72_interface_context.py': '02ffaeab9c5e2f96bd135f7007b1f964c2863fb16471611c03867b3aefdda251',
    'tools/probe72_avatar_method_cache.py': '6053f724185e2b2c7e75503021beb2f5cbde18319c7aac6edb1dfec7ed7d8544',
    'tools/collect72_avatar_runtime_inputs.py': '2c9ba14c652823a71771a2fee385de44a75161ad9bba947d263b4164aa39fffa',
}


def digest(raw):
    return hashlib.sha256(raw).hexdigest()


GATE_PROOF = 'local/migration72/d02-resume-20261109/r2-config-trigger/main-third.private.json'
GATE_PROOF_SHA = 'f99de6aa2b80367507cc4e17d7bf6b1fdfec44851c436a3c52f96d4e31ea7eb9'
GATES = {'registered': 0x586dbb0, 'once': 0x586ba00}


def verify_gate_sources(binary):
    raw = (ROOT / GATE_PROOF).read_bytes()
    if digest(raw) != GATE_PROOF_SHA:
        raise ValueError('Frozen scalar source proof differs')
    proof = json.loads(raw)
    if int(proof['GRegisteredRva'], 16) != GATES['registered'] or int(proof['GOnceRva'], 16) != GATES['once']:
        raise ValueError('Scalar root contract differs')
    pe = environment.PE(binary)
    for window in proof['newWindows']:
        at = pe.offset(pe.base + int(window['startRva'], 16), window['bytes'], executable=True)
        if digest(binary[at:at + window['bytes']]) != window['sha256']:
            raise ValueError('Scalar original source window differs')
    return dict(reportSHA256=GATE_PROOF_SHA, originalWindows=proof['newWindows'], roots=GATES)


def read_gates(read, base, phase):
    records = []
    for name, rva in GATES.items():
        begin = datetime.now(timezone.utc).isoformat()
        try:
            raw, error = read(base + rva, 1)
        except OSError as failure:
            winerror = getattr(failure, 'winerror', None)
            raw, error = b'', {'winerror': winerror if winerror is not None else failure.errno}
        if error is None and len(raw) != 1:
            error = 'partial-read'
        complete = error is None
        state = ('false' if raw == b'\x00' else 'true' if raw == b'\x01' else 'non-bool') if complete else 'unreadable'
        records.append(dict(name=name, phase=phase, address=base + rva, requested=1,
            bytesRead=len(raw), bytesHex=raw[:1].hex(), error=error, classification=state,
            beginUtc=begin, endUtc=datetime.now(timezone.utc).isoformat()))
    return records


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
    metadata = (game / profile['files']['metadata']['path']).read_bytes()
    pins, windows, context = probe.verify_sources(binary, metadata)
    runtime_sources = runtime.verify_sources(binary, metadata, verified_sources=pins)
    gate_sources = verify_gate_sources(binary)
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
        gates_before = read_gates(read, current['base'], 'before-runtime-collection')
        result = runtime.collect(read, current['base'])
        gates_after = read_gates(read, current['base'], 'after-runtime-final-repeat')
        result['configurationGates'] = dict(records=gates_before + gates_after,
            requestedReads=4, requestedBytes=4, source=gate_sources,
            sameValues=all(a['error'] is None and b['error'] is None and a['bytesHex'] == b['bytesHex']
                for a, b in zip(gates_before, gates_after)),
            valuesDescribeTheirReadTimesOnly=True, atomicSnapshot=False,
            pastConfigSyncProved=False, naturalBranchProved=False, configBodyRead=False)
        result['combinedBudget'] = dict(reads=result['budget']['reads'] + 4,
            requestedBytes=result['budget']['requestedBytes'] + 4, maxReads=5192, maxBytes=89964, maxSingleRead=208)
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
            result.update(status='partial', failure=result.get('failure') or 'end-process-identity-or-lifetime')
        result.update(timeUtc=started, finishedUtc=datetime.now(timezone.utc).isoformat(),
            pid=source['pid'], creation=creation, mainModule=current, finalStatus=final,
            endIdentityVerified=end_valid, inputChecks=inputs, sourceObservationSHA256=digest(raw),
            sourceSHA256=digest(Path(__file__).read_bytes()), verifiedSources=SOURCE_PINS,
            probeSourcePins=pins, codeWindows=windows, staticContextSource=context,
            runtimeSourceChecks=runtime_sources,
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
        parser.error('Choose a new output inside the runtime-input live directory')
    output.parent.mkdir(parents=True, exist_ok=True)
    output.with_suffix('.source.py').write_bytes(Path(__file__).read_bytes())
    try:
        result = execute(args.observation.resolve())
    except (OSError, ValueError, KeyError, IndexError) as error:
        result = dict(task='R1-RUNTIME-INPUTS-live-adapter', status='error', error=str(error),
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
