"""One bounded, read-only Proud hot-input sample from a newly ready stock process."""
import argparse
import ctypes as C
from ctypes import wintypes as W
from datetime import datetime, timezone
import json
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools'))
from observe_client72_uninstrumented import WindowsBackend, MODULEENTRY32W, verified_inputs, sha256
from extract_client72 import PE

PRIOR = ROOT / 'local/migration72/d02-resume-20261111b/proud'
OUTPUT = ROOT / 'local/migration72/d02-resume-20261111b/proud-equality-sampler'
MONITOR_SHA = 'e0b4134e6bb7be8c758b3a7bad3884a8246b25c6d65cd8ca3c91d0941ed33c6e'
EXTRACT_SHA = '7d1c75162f77c0fe0df154aa1240c142ad3f6af3295c89cc3d7b85372d3cb97d'
REPORT_SHA = '50bfa653a29a05a1f1723dcb4469b352ba5903c856db1e867dbca6fbf708742b'
CODE_SHA = 'b2262dd40a51785b5af6bfa16914976b61ead0bed05d1d9d68c171262f2a8527'
CACHE_SHA = 'df52eb70fe8e70dfecd1059e898d15e288cfa98bcefc78bcaabf753ae150349f'
MAX_U64 = 1 << 64


def utc():
    return datetime.now(timezone.utc).isoformat()


def collect(read, base, image_size):
    """The callback returns (actual bytes, error); every attempted read is recorded."""
    records, values = [], {}

    def get(name, address, width, pointer=True):
        if len(records) == 7 or sum(r['requested'] for r in records) + width > 50:
            raise ValueError('read budget exceeded')
        if address <= 0 or address + width > MAX_U64:
            data, error = b'', 'address-outside-uint64'
        else:
            try:
                data, error = read(address, width)
            except OSError as exc:
                data, error = b'', {'winerror': getattr(exc, 'winerror', exc.errno)}
        complete = error is None and len(data) == width
        records.append(dict(name=name, address=address, requested=width, bytesRead=len(data),
                            bytesHex=data.hex(), error=error if error is not None else
                            (None if complete else 'partial-read'), timeUtc=utc()))
        if not complete:
            return None
        value = int.from_bytes(data, 'little')
        values[name] = value
        return value if not pointer or value else None

    q = get('Q', base + 0x594e6f0, 8)
    s = get('S', q + 0x38, 8) if q is not None else None
    o = get('O', s, 8) if s is not None else None
    k = get('K', o, 8) if o is not None else None
    n = get('N', k + 0xbe, 2, pointer=False) if k is not None else None
    f = get('F', k + 0x110, 8) if n is not None else None
    m = get('M', k + 0x110 + n * 8, 8) if f is not None else None
    complete = m is not None
    return dict(status='completed' if complete else 'partial', values=values, records=records,
                reads=len(records), requestedBytes=sum(r['requested'] for r in records),
                functionLocation=('fixed-exe' if base <= f < base + image_size else 'unknown')
                if f is not None else 'unknown', atomicSnapshot=False,
                naturalComparisonObserved=False)


def verify_sources(binary):
    pins = {
        ROOT/'tools/observe_client72_uninstrumented.py': MONITOR_SHA,
        ROOT/'tools/extract_client72.py': EXTRACT_SHA,
        PRIOR/'lookup-index-contract1.private.json': REPORT_SHA,
        PRIOR/'index2/code.bin': CODE_SHA,
        ROOT/'local/migration72/d02-resume-20261108/r3-equipment/metadata.private.pkl': CACHE_SHA,
    }
    for path, expected in pins.items():
        if sha256(path) != expected:
            raise ValueError('fixed source mismatch: ' + str(path.relative_to(ROOT)))
    report = json.loads((PRIOR/'lookup-index-contract1.private.json').read_bytes())
    cell = report['defaultComparerCell']
    if (cell['cell'], cell['usageRow'], cell['sourceIndex'], cell['type']['typeDefinition'],
        cell['type']['instanceIndex'], cell['type']['arguments'][0]['typeDefinition']) != (
            '0x14594e6f0', 732126, 403933, 1234, 50464, 80067):
        raise ValueError('metadata usage mismatch')
    data = binary.read_bytes()
    pe = PE(data)
    if pe.base != 0x140000000:
        raise ValueError('unexpected preferred image base')
    offset = pe.offset(0x1593e7870, 802, executable=True)
    if data[offset:offset+802] != (PRIOR/'index2/code.bin').read_bytes():
        raise ValueError('original FindEntry window mismatch')
    optional = int.from_bytes(data[0x3c:0x40], 'little') + 24
    image_size = int.from_bytes(data[optional+56:optional+60], 'little')
    return dict(sourcePins={str(p.relative_to(ROOT)).replace('\\', '/'): h for p, h in pins.items()},
                usage=cell, imageSize=image_size)


def main_module(snapshot, target):
    if snapshot['error'] is not None:
        raise ValueError('module enumeration failed')
    rows = [r for r in snapshot['items'] if r['name'].casefold() == 'yuanshen.exe']
    if len(rows) != 1 or not Path(rows[0]['path']).samefile(target):
        raise ValueError('main module identity mismatch')
    return rows[0]


def identify(pid, result):
    game = Path(subprocess.check_output(['powershell.exe', '-NoProfile', '-ExecutionPolicy', 'Bypass',
        '-File', str(ROOT/'tools/game_path.ps1')], text=True, encoding='utf-8').strip()).resolve()
    target = game/'YuanShen.exe'
    inputs = verified_inputs(ROOT, game)
    if not (game/inputs['files']['binary']['path']).samefile(target):
        raise ValueError('profile binary is not the target executable')
    checks = verify_sources(target)
    result.update(pid=pid, inputChecks=inputs, sourceChecks=checks)
    backend = WindowsBackend()
    handle = backend.k.OpenProcess(0x101000, False, pid)
    if not handle:
        raise C.WinError(C.get_last_error())
    try:
        creation = backend.creation_time(handle)
        image = backend.image(handle)
        if creation['filetime'] is None or not Path(image).samefile(target) or not backend.status(handle)['alive']:
            raise ValueError('identity process image or lifetime differs')
        module = main_module(backend.snapshot(8|16, pid, MODULEENTRY32W,
                             'Module32FirstW', 'Module32NextW'), target)
        if module['size'] != checks['imageSize']:
            raise ValueError('identity module size differs from fixed PE')
        result.update(creation=creation, image=image, mainModule=module, finalStatus=backend.status(handle))
        if not result['finalStatus']['alive']:
            raise ValueError('process exited during identity capture')
        result.update(status='completed', identityEndUtc=utc())
    finally:
        backend.close(handle)


def execute(observation, result):
    source = json.loads(observation.read_text(encoding='utf-8-sig'))
    game = Path(subprocess.check_output(['powershell.exe', '-NoProfile', '-ExecutionPolicy', 'Bypass',
        '-File', str(ROOT/'tools/game_path.ps1')], text=True, encoding='utf-8').strip()).resolve()
    target = game/'YuanShen.exe'
    if (source['task'] != 'R3-PROUD-HOT-EQUALITY-IDENTITY' or source['status'] != 'completed' or
        not source['finalStatus']['alive'] or source['sourceSHA256'] != sha256(Path(__file__)) or
        not Path(source['image']).samefile(target)):
        raise ValueError('ready identity mismatch')
    inputs = verified_inputs(ROOT, game)
    if not (game/inputs['files']['binary']['path']).samefile(target):
        raise ValueError('profile binary is not the target executable')
    if source['inputChecks'] != inputs:
        raise ValueError('ready identity inputs or configuration changed')
    checks = verify_sources(target)
    observed = main_module(dict(error=None, items=[source['mainModule']]), target)
    if observed['size'] != checks['imageSize']:
        raise ValueError('observed module size differs from fixed PE')
    result.update(sourceIdentitySHA256=sha256(observation), inputChecks=inputs,
                  sourceChecks=checks, observedModule=observed, pid=source['pid'])
    backend = WindowsBackend()
    handle = backend.k.OpenProcess(0x101010, False, source['pid'])
    if not handle:
        raise C.WinError(C.get_last_error())
    try:
        creation = backend.creation_time(handle)
        if (creation['filetime'] is None or creation != source['creation'] or
            not Path(backend.image(handle)).samefile(target) or not backend.status(handle)['alive']):
            raise ValueError('live process identity or lifetime differs')
        current = main_module(backend.snapshot(8|16, source['pid'], MODULEENTRY32W,
                              'Module32FirstW', 'Module32NextW'), target)
        if current['base'] != observed['base'] or current['size'] != observed['size']:
            raise ValueError('live module epoch differs')
        result.update(creation=creation, mainModule=current, sampleStartUtc=utc())
        backend.k.ReadProcessMemory.argtypes = [W.HANDLE, C.c_void_p, C.c_void_p, C.c_size_t, C.POINTER(C.c_size_t)]
        backend.k.ReadProcessMemory.restype = W.BOOL
        def read(address, width):
            buffer, count = C.create_string_buffer(width), C.c_size_t()
            ok = backend.k.ReadProcessMemory(handle, address, buffer, width, C.byref(count))
            return buffer.raw[:count.value], None if ok else {'winerror': C.get_last_error()}
        result.update(collect(read, current['base'], current['size']))
        result['sampleEndUtc'] = utc()
        result['finalStatus'] = backend.status(handle)
        if not result['finalStatus']['alive']:
            result['status'] = 'partial'
    finally:
        backend.close(handle)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest='mode', required=True)
    identity = sub.add_parser('identity')
    identity.add_argument('--pid', type=int, required=True)
    sample = sub.add_parser('sample')
    sample.add_argument('--identity', type=Path, required=True)
    for command in (identity, sample):
        command.add_argument('--output', required=True, type=Path)
        command.add_argument('--user-ready', action='store_true')
    args = parser.parse_args()
    output = args.output.resolve()
    if not args.user_ready or not output.is_relative_to(OUTPUT.resolve()) or output == OUTPUT.resolve():
        parser.error('new user-ready and output under private sampler directory required')
    if args.mode == 'identity' and args.pid <= 0:
        parser.error('a positive explicit PID is required')
    if args.mode == 'sample' and not args.identity.resolve().is_relative_to(OUTPUT.resolve()):
        parser.error('identity must be under the private sampler directory')
    output.parent.mkdir(parents=True, exist_ok=True)
    task = 'R3-PROUD-HOT-EQUALITY-IDENTITY' if args.mode == 'identity' else 'R3-PROUD-HOT-EQUALITY-INPUT'
    result = dict(task=task, status='error', timeUtc=utc(),
                  sourceSHA256=sha256(Path(__file__)))
    with output.open('x', encoding='utf-8') as stream:
        try:
            if args.mode == 'identity':
                identify(args.pid, result)
            else:
                execute(args.identity, result)
        except (OSError, ValueError, KeyError, IndexError) as exc:
            result.update(status='error', errorType=type(exc).__name__, error=str(exc),
                          winerror=getattr(exc, 'winerror', None))
        json.dump(result, stream, indent=2, ensure_ascii=False)
        stream.write('\n')
    print(json.dumps(dict(status=result['status'], reads=result.get('reads', 0))))
    if result['status'] != 'completed':
        raise SystemExit(1)


if __name__ == '__main__':
    main()
