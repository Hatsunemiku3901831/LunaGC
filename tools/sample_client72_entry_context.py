"""Bounded read-only CN 7.2 entry context; run only against a fresh monitor."""
import argparse
import ctypes as C
from ctypes import wintypes as W
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path

try:
    from .observe_client72_uninstrumented import ROOT, WindowsBackend, MODULEENTRY32W, verified_inputs, sha256
    from .extract_client72 import PE
except ImportError:
    from observe_client72_uninstrumented import ROOT, WindowsBackend, MODULEENTRY32W, verified_inputs, sha256
    from extract_client72 import PE

MONITOR_SHA = 'e0b4134e6bb7be8c758b3a7bad3884a8246b25c6d65cd8ca3c91d0941ed33c6e'
CONTRACTS = (
    ('local/migration72/d02-active/avatar/handler-closure/native-config-sampling-contract.json', '6503b65872f093d503c2db8668607542c0a5d3a336002e829e5aa82b924734a4'),
    ('local/migration72/d02-active/scene-runtime-context-astra/minimal-root-contract.json', '149c5ebcacf726009dbd028becf2d75f6caa1b2178ccf38e5566c3de26b4a45d'),
)
PARENT_WINDOWS = (
    (0x517980, 0x77, 'b49101213851b66dcf7162f91b28b6fee7224c0c2c9c551d07109de5068dae72'),
    (0x5269f0, 5, 'bb3bd836fbc8d866dc73ea8958b74331d207192b579c09e99d87e8637454f8ad'),
    (0x1a4d6b60, 0xc1, '5245ce48b3f06b60e060ab85ba8d28e0dc98a43bf112b515c74a14ea1cb610a0'),
    (0x517aa4, 0x28, 'fe29c00adc8ebc21f1374ab96d7dd93cfc71985eb2782603e0589f4b5ba41a98'),
    (0x4ed9c3, 0x12, '0d967f0101c7f9cbef0d4b846e8e0dcd24ee03c04760159fc134fc6e2c15067a'),
)
POOL_WINDOWS = (
    (0x70687c0, 0x20, '98a93ee052b474915ef1212a25986b49326f99a9f91f23530bb52b0196432861'),
    (0x1154bfb9, 0x22, '97df417780bfa1b4acf52b347cd4087bc7d12a127830746fa2ea0399e98ec25b'),
)
OUTPUT_ROOT = ROOT / 'local/migration72/d02-active/main/entry-context-tool'


def utc():
    return datetime.now(timezone.utc).isoformat()


def verify_contracts(binary):
    contracts = []
    for path, expected in CONTRACTS:
        raw = (ROOT / path).read_bytes()
        if hashlib.sha256(raw).hexdigest() != expected:
            raise ValueError('Contract hash mismatch: ' + path)
        contracts.append(json.loads(raw))
    pe = PE(binary)
    windows = [(int(w['rva'], 16), w['bytes'], w['sha256'], None)
               for w in contracts[0]['codeWindows'].values()]
    windows += [(int(w['startRVA'], 16), int(w['endRVA'], 16) - int(w['startRVA'], 16),
                 w['sha256'], bytes.fromhex(''.join(i['bytes'] for i in w['instructions'])))
                for w in contracts[1]['windows']]
    if len(windows) != 16:
        raise ValueError('Unexpected contract window count')
    windows += [(rva, size, digest, None) for rva, size, digest in PARENT_WINDOWS + POOL_WINDOWS]
    for rva, size, expected, instructions in windows:
        offset = pe.offset(pe.base + rva, size, executable=True)
        actual = binary[offset:offset + size]
        if hashlib.sha256(actual).hexdigest() != expected or (instructions is not None and actual != instructions):
            raise ValueError('Code window mismatch at RVA ' + hex(rva))
    return len(windows)


def collect(read, base):
    records = []
    def get(name, address, size):
        data, error = read(address, size)
        complete = error is None and len(data) == size
        records.append(dict(name=name, address=address, requested=size, bytesHex=data.hex(),
                            sha256=hashlib.sha256(data).hexdigest(), error=error if error is not None else
                            (None if complete else 'partial-read'), timeUtc=utc()))
        return data if complete else None
    def pointer(name, address):
        raw = get(name, address, 8)
        return int.from_bytes(raw, 'little') if raw is not None else None

    mode = get('nativeConfig.mode', base + 0x586ceb2, 1)
    root = pointer('staticRoot', base + 0x586b600)
    pool_slot_read = False
    pool_binding_verified = False
    if root:
        for name, offset, size in (('hashSet', 0x60e8, 40), ('list', 0x6100, 32)):
            obj = pointer(name + '.slot', root + offset)
            if obj:
                head = get(name + '.header', obj, size)
                klass = int.from_bytes(head[:8], 'little') if head is not None else None
                if klass:
                    get(name + '.classHeader', klass, 0xd0)
        for name, offset, class_header in (('pool', 0x5d60, True), ('registryStringToUint', 0xc608, True),
                                          ('registryUintToString', 0xc6d0, True), ('defaultString', 0xc698, False)):
            obj = pointer(name + '.slot', root + offset)
            if obj:
                klass = pointer(name + '.klass', obj)
                if klass and class_header:
                    head = get(name + '.classHeader', klass, 0xd0)
                    if name == 'pool' and head is not None and int.from_bytes(head[0x10:0x18], 'little') == 11008 and (int.from_bytes(head[0x18:0x1c], 'little') >> 16) & 255 == 18:
                        target = pointer('pool.allocateSlot4', klass + 0xf0)
                        pool_slot_read = target is not None
                        if target == base + 0x70687c0:
                            code = get('pool.allocateCode', target, 32)
                            pool_binding_verified = code is not None and hashlib.sha256(code).hexdigest() == POOL_WINDOWS[0][2]
    pool_mode = get('pool.mode', base + 0x586d70b, 1)
    entity_class = pointer('pool.entityClass46314.cell', base + 0x5897b48)
    if entity_class:
        get('pool.entityClass46314.header', entity_class, 0xd0)
    table = pointer('parent.classCache.cell', base + 0x5bc8f18)
    entry = pointer('parent.classCache.entry1239', table + 1239 * 8) if table else None
    header = get('parent.class1239.header', entry, 0xd0) if entry else None
    slot = pointer('parent.registry.cell', base + 0x5bc8ad0)
    registry = pointer('parent.registry.base', slot) if slot else None
    parent_status = ('class-cache-null' if table == 0 else 'class-entry-null' if entry == 0 else
                     'registry-slot-null' if slot == 0 else 'registry-base-null' if registry == 0 else
                     'root-or-class-unavailable')
    if header is not None:
        type_id = int.from_bytes(header[0x10:0x18], 'little')
        kind = (int.from_bytes(header[0x18:0x1c], 'little') >> 16) & 0xff
        offset = int.from_bytes(header[0xb0:0xb4], 'little')
        if type_id != 1239 or kind != 18:
            parent_status = 'class-identity-mismatch'
        elif not offset:
            parent_status = 'parent-offset-zero'
        elif registry:
            descriptor = get('parent.descriptor', registry + offset + 0x10, 16)
            parent_status = 'read' if descriptor is not None else 'descriptor-unreadable'
    if any(r['error'] is not None for r in records if r['name'].startswith('parent.')):
        parent_status = 'read-failed' if parent_status != 'descriptor-unreadable' else parent_status
    return dict(reads=records, modeValue=mode[0] if mode is not None else None,
                modeInterpretation=('false' if mode == b'\x00' else 'true' if mode == b'\x01' else 'unknown'),
                parentDescriptorStatus=parent_status,
                poolModeValue=pool_mode[0] if pool_mode is not None else None,
                poolAllocateBindingVerified=pool_binding_verified,
                status='partial' if parent_status != 'read' or any(r['error'] is not None for r in records) else 'completed',
                atomicSnapshot=False, completeCtorVerified=False, naturalRegistryVerified=False,
                worldInitializationVerified=False, sdkRequestVerified=False,
                targetProtocolReady=False, poolAllocateSlotObserved=pool_slot_read)


def execute(observation):
    raw = observation.read_bytes()
    source = json.loads(raw.decode('utf-8-sig'))
    if source.get('status') != 'completed' or not source['finalStatus']['alive']:
        raise ValueError('A completed live uninstrumented observation is required')
    if source['sourceSHA256'] != MONITOR_SHA or sha256(ROOT / 'tools/observe_client72_uninstrumented.py') != MONITOR_SHA:
        raise ValueError('Monitor source mismatch')
    game = Path(source['launch']['cwd']).resolve()
    target = (game / 'YuanShen.exe').resolve()
    if Path(source['image']).resolve() != target or Path(source['launch']['executable']).resolve() != target:
        raise ValueError('Monitor image mismatch')
    inputs = verified_inputs(ROOT, game)
    original = source['inputs']
    if original['profileSHA256'] != inputs['profileSHA256'] or original['files'] != inputs['files']:
        raise ValueError('Monitor profile or client inputs differ')
    for name in ('.env', '.env.local', 'config.json'):
        if any(original['config'][name].get(k) != inputs['config'][name].get(k) for k in ('exists', 'sha256', 'error')):
            raise ValueError('Monitor configuration differs: ' + name)
    windows = verify_contracts(target.read_bytes())
    observed_modules = source['samples'][-1]['modules']
    observed_main = [m for m in observed_modules['items'] if Path(m['path']).resolve() == target]
    if observed_modules['error'] is not None or len(observed_main) != 1:
        raise ValueError('Monitor main module unavailable')
    b = WindowsBackend()
    handle = b.k.OpenProcess(0x101010, False, source['pid'])
    if not handle:
        raise C.WinError(C.get_last_error())
    try:
        creation = b.creation_time(handle)
        if creation['filetime'] is None or creation != source['processCreationTime']:
            raise ValueError('Live process creation identity differs')
        if Path(b.image(handle)).resolve() != target or not b.status(handle)['alive']:
            raise ValueError('Live process image or lifetime differs')
        modules = b.snapshot(8 | 16, source['pid'], MODULEENTRY32W, 'Module32FirstW', 'Module32NextW')
        main = [m for m in modules['items'] if Path(m['path']).resolve() == target]
        if modules['error'] is not None or len(main) != 1 or main[0] != observed_main[0]:
            raise ValueError('Live main module differs from monitor')
        b.k.ReadProcessMemory.argtypes = [W.HANDLE, C.c_void_p, C.c_void_p, C.c_size_t, C.POINTER(C.c_size_t)]
        b.k.ReadProcessMemory.restype = W.BOOL
        def read(address, size):
            if not 0 < address < (1 << 64) or address + size > (1 << 64) or not 0 < size <= 256:
                return b'', 'address-or-size-outside-contract'
            buf = C.create_string_buffer(size); count = C.c_size_t()
            ok = b.k.ReadProcessMemory(handle, address, buf, size, C.byref(count))
            return buf.raw[:count.value], None if ok and count.value == size else dict(winerror=C.get_last_error(), bytesRead=count.value)
        result = collect(read, main[0]['base'])
        try:
            final = b.status(handle)
        except OSError as error:
            final = dict(alive=None, error=str(error))
        if not final['alive']:
            result['status'] = 'partial'
        result.update(pid=source['pid'], creation=creation, mainModule=main[0], inputChecks=inputs,
                      sourceObservationSHA256=hashlib.sha256(raw).hexdigest(), sourceSHA256=sha256(Path(__file__)),
                      verifiedWindows=windows, finalStatus=final)
        return result
    finally:
        b.close(handle)


def output_path(path, game=None):
    path = path.resolve()
    allowed = OUTPUT_ROOT.resolve()
    if (not allowed.is_relative_to((ROOT / 'local').resolve()) or
            not path.is_relative_to(allowed) or path == allowed or path.exists() or
            (game is not None and path.is_relative_to(game.resolve()))):
        raise ValueError('Choose a new output inside the entry-context-tool directory')
    return path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--observation', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    try:
        source = json.loads(args.observation.read_text(encoding='utf-8-sig'))
        output = output_path(args.output, Path(source['launch']['cwd']))
        output.parent.mkdir(parents=True, exist_ok=True)
        with output.open('x', encoding='utf-8') as f:
            try:
                result = execute(args.observation)
            except (OSError, ValueError, KeyError, IndexError) as error:
                result = dict(status='error', error=str(error), timeUtc=utc())
            json.dump(result, f, indent=2)
            f.write('\n')
    except (OSError, ValueError, KeyError) as error:
        parser.error(str(error))
    print(json.dumps({'status': result['status']}))
    if result['status'] != 'completed':
        raise SystemExit(1)


if __name__ == '__main__':
    main()
