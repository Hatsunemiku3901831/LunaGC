"""Exact CN7.2 interface/TLS read-only sampler; never launches a process."""
import argparse
import ctypes as C
from ctypes import wintypes as W
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import struct

try:
    from .observe_client72_uninstrumented import ROOT, WindowsBackend, MODULEENTRY32W, verified_inputs, sha256
    from .extract_client72 import PE
except ImportError:
    from observe_client72_uninstrumented import ROOT, WindowsBackend, MODULEENTRY32W, verified_inputs, sha256
    from extract_client72 import PE

OUTPUT_ROOT = ROOT / 'local/migration72/d02-resume-20261108/r1-interface-sampling'
CONTRACT = 'local/migration72/d02-resume-20261108/r1-fresh-ancestor/main-sampling-contract.lf.private.json'
CONTRACT_SHA = 'bac006269d333ebe5ca88d25f6ac84c3b25f1bbadea72e95d571655366ffea33'
ANCESTOR_REPORT = 'local/migration72/d02-resume-20261108/r1-fresh-ancestor/main-third.json'
ANCESTOR_SHA = 'dc738c4f3f3d80cdb72bb852d2250de685657fa489818f436ce8e8cc9933256b'
MONITOR_SHA = 'e0b4134e6bb7be8c758b3a7bad3884a8246b25c6d65cd8ca3c91d0941ed33c6e'
SOURCE_PINS = {
    'tools/observe_client72_uninstrumented.py': MONITOR_SHA,
    'tools/sample_client72_entry_context.py': 'ecab38c9e39daf5ea48bb017cc7060c61c8b142c4f3844d37b54c3e7374f9c7c',
    'tools/extract_client72.py': '7d1c75162f77c0fe0df154aa1240c142ad3f6af3295c89cc3d7b85372d3cb97d',
    'tools/client72_avatar_fresh_ancestor.py': '3715861c8c6570a37d69a5de7967633a563671eba2b185e3e77759d4142c548c',
    'tools/client72_scene_pool_continuation.py': '8540a3cc1b853cad69319e31ce8da0ed45369ef302263ecf191abddb6d349629',
}
# Current fixed PE windows, not runtime reads; no old absolute process address.
WINDOWS = (
    (0x1a4d6b60, 193, '5245ce48b3f06b60e060ab85ba8d28e0dc98a43bf112b515c74a14ea1cb610a0'),
    (0x517980, 776, 'ddc12c9ec67a6803dce0cef1f7bd991cf594ea0dd40442bda42292f67be53f4e'),
    (0x51f370, 484, '9eaa3b22e3e226c9ef985250c29d3514d605a09e58e0b6e21bc8af4acfd0f363'),
    (0x50a890, 1216, '7b3f2a829a31d0f3aaeaa16dc46628a996c41d8431779f9d06bddc108234c73b'),
)
CLASSES = (
    (1169, 0x2488, 0xa21026, 'a120af6afc25d7d683dbdcc306919a244fe9877f05698d0839836122f7d2a4ac'),
    (1249, 0x2708, 0xa22606, 'ba00239c8db9e1caa6ae10e9a6a7921f7ec31242f8aeb07a1c89ca286e4846e4'),
)
PROCESS_READ_QUERY_WAIT = 0x101010
MAX_READS, MAX_BYTES, MAX_SINGLE = 14, 664, 208


def utc():
    return datetime.now(timezone.utc).isoformat()


def digest(raw):
    return hashlib.sha256(raw).hexdigest()


def verify_contracts(binary, metadata):
    for path, expected in SOURCE_PINS.items():
        if sha256(ROOT/path) != expected:
            raise ValueError('Source hash mismatch: '+path)
    raw, report_raw = (ROOT/CONTRACT).read_bytes(), (ROOT/ANCESTOR_REPORT).read_bytes()
    if digest(raw) != CONTRACT_SHA or digest(report_raw) != ANCESTOR_SHA:
        raise ValueError('Interface contract or ancestor report hash mismatch')
    contract, report = json.loads(raw), json.loads(report_raw)
    if contract != report['nextSamplingContract']:
        raise ValueError('Interface contract differs from frozen ancestor projection')
    pe = PE(binary)
    if pe.base != 0x140000000:
        raise ValueError('Unexpected fixed PE preferred image base')
    checked = []
    for rva, width, expected in WINDOWS:
        offset = pe.offset(pe.base+rva, width, executable=True)
        if digest(binary[offset:offset+width]) != expected:
            raise ValueError('Fixed PE code window mismatch: '+hex(rva))
        checked.append(dict(rva=hex(rva), bytes=width, sha256=expected))
    rows = contract['classes']
    if len(rows) != 2:
        raise ValueError('Unexpected interface class count')
    expected_metadata = {}
    for row, (index, slot, file, expected) in zip(rows, CLASSES):
        if (row['definition'] != index or int(row['cacheOffset'], 16) != slot or
                row['headerIdentity'] != dict(data=index, kind=18) or row['parent'] is not None or
                row['metadata']['interfaceCount'] != 0 or int(row['metadata']['fileOffset'], 16) != file):
            raise ValueError('Unexpected interface class contract')
        fixed = row['fixedDescriptor']
        offset = pe.offset(int(fixed['start'], 16), 16)
        descriptor = binary[offset:offset+16]
        if descriptor != bytes.fromhex(fixed['bytesHex']) or digest(descriptor) != fixed['sha256']:
            raise ValueError('Fixed interface descriptor mismatch')
        data = metadata[file:file+70]
        if len(data) != 70 or data != bytes.fromhex(row['metadata']['bytesHex']) or digest(data) != expected:
            raise ValueError('Fixed interface metadata mismatch: '+str(index))
        expected_metadata[index] = data
    optional = struct.unpack_from('<I', binary, 0x3c)[0]+24
    tls_rva, tls_size = struct.unpack_from('<II', binary, optional+112+9*8)
    if (tls_rva, tls_size) != (0x3a8dab0, 40):
        raise ValueError('Fixed TLS directory differs')
    tls_file = pe.offset(pe.base+tls_rva, tls_size)
    if struct.unpack_from('<Q', binary, tls_file+16)[0] != pe.base+0x5eef080:
        raise ValueError('Fixed TLS AddressOfIndex differs')
    image_size = struct.unpack_from('<I', binary, optional+56)[0]
    return expected_metadata, dict(sourcePins=SOURCE_PINS, contractSHA256=CONTRACT_SHA,
        ancestorReportSHA256=ANCESTOR_SHA, codeWindows=checked, imageSize=image_size,
        tlsDirectory=dict(rva=hex(tls_rva), bytes=tls_size, addressOfIndexRVA='0x5eef080'))


class ReadBudget:
    """One shared ledger; rejected/failed requests never cause broader fallback reads."""
    def __init__(self, read):
        self.read, self.records, self.requested = read, [], 0

    def get(self, name, address, width):
        if len(self.records) >= MAX_READS or self.requested+width > MAX_BYTES or not 0 < width <= MAX_SINGLE:
            raise ValueError('Interface read budget exceeded')
        self.requested += width
        if not 0 < address < (1 << 64) or address+width > (1 << 64):
            data, error = b'', 'address-outside-uint64'
        else:
            try:
                data, error = self.read(address, width)
            except OSError as failure:
                data, error = b'', dict(winerror=getattr(failure, 'winerror', failure.errno))
        complete = error is None and len(data) == width
        self.records.append(dict(name=name, address=address, requested=width, bytesRead=len(data),
            bytesHex=data[:width].hex(), error=error if error is not None else
            (None if complete else 'partial-read'), timeUtc=utc()))
        return data if complete else None

    def pointer(self, name, address):
        raw = self.get(name, address, 8)
        return int.from_bytes(raw, 'little') if raw is not None else None


def collect(read, base, expected_metadata):
    budget = ReadBudget(read)
    cache = budget.pointer('interface.classCache.cell', base+0x5bc8f18)
    registry, registry_attempted = None, False
    classes = []
    for index, slot, _, _ in CLASSES:
        name = 'interface.class'+str(index)
        row = dict(definition=index, staticParent=None, staticInterfaceCount=0,
                   status='cache-null' if cache == 0 else 'cache-unreadable')
        classes.append(row)
        if not cache:
            continue
        header_address = budget.pointer(name+'.entry', cache+slot)
        row.update(headerAddress=header_address, status='entry-null' if header_address == 0 else 'entry-unreadable')
        if not header_address:
            continue
        header = budget.get(name+'.header', header_address, 208)
        row['status'] = 'header-unreadable'
        if header is None:
            continue
        identity = dict(data=struct.unpack_from('<Q', header, 0x10)[0],
                        kind=(struct.unpack_from('<I', header, 0x18)[0] >> 16) & 255)
        row.update(identity=identity, status='identity-mismatch')
        if identity != dict(data=index, kind=18):
            continue
        offsets = dict(parent=struct.unpack_from('<I', header, 0xb0)[0],
                       declaring=struct.unpack_from('<I', header, 0xa8)[0])
        metadata_address = struct.unpack_from('<Q', header, 0x58)[0]
        row.update(compactOffsets=offsets, metadataAddress=metadata_address, status='metadata-null')
        if not metadata_address:
            continue
        metadata = budget.get(name+'.metadata', metadata_address, 70)
        row['status'] = 'metadata-unreadable' if metadata is None else 'metadata-mismatch'
        if metadata != expected_metadata[index]:
            continue
        row.update(metadataMatchesFixed=True, descriptors={}, status='read')
        for field, compact in offsets.items():
            descriptor = dict(compact=compact, status='zero-skipped', semanticIdentity='unknown')
            row['descriptors'][field] = descriptor
            if not compact:
                continue
            if not registry_attempted:
                registry_attempted = True
                cell = budget.pointer('interface.registry.cell', base+0x5bc8ad0)
                registry = budget.pointer('interface.registry.base', cell) if cell else cell
            descriptor['status'] = 'registry-null' if registry == 0 else 'registry-unreadable'
            if registry:
                address = registry+compact+0x10
                data = budget.get(name+'.'+field+'Descriptor', address, 16)
                descriptor.update(address=address, status='read-uninterpreted' if data is not None else 'descriptor-unreadable')
            if descriptor['status'] != 'read-uninterpreted':
                row['status'] = 'descriptor-unavailable'
    tls = budget.get('pool.loaderTlsIndex', base+0x5eef080, 4)
    complete = all(row['status'] == 'read' for row in classes) and tls is not None
    return dict(task='R1-SAMPLE-INTERFACES', status='completed' if complete else 'partial',
        timeUtc=utc(), cacheRoot=cache, registryRoot=registry, registryAttempted=registry_attempted,
        classes=classes, tlsIndex=dict(status='read' if tls is not None else 'unreadable',
            value=int.from_bytes(tls, 'little') if tls is not None else None,
            allocationThreadProved=False, threadOrTlsArrayRead=False), records=budget.records,
        budget=dict(reads=len(budget.records), requestedBytes=budget.requested,
                    maxReads=MAX_READS, maxBytes=MAX_BYTES, maxSingleRead=MAX_SINGLE),
        atomicSnapshot=False, completeColdComparer=False, completeClassInitialization=False,
        completeAvatarHandler=False, targetProtocolReady=False, d02Complete=False)


def same_file(left, right):
    # Windows file identity, including canonical \\?\ and ordinary path spellings.
    return Path(left).samefile(right)


def main_module(modules, target):
    if modules['error'] is not None:
        raise ValueError('Main module enumeration failed')
    rows = [row for row in modules['items'] if row['name'].casefold() == 'yuanshen.exe']
    if len(rows) != 1 or not same_file(rows[0]['path'], target):
        raise ValueError('Main module file identity differs')
    return rows[0]


def execute(observation):
    raw = observation.read_bytes()
    source = json.loads(raw.decode('utf-8-sig'))
    if (source.get('status') != 'completed' or not source['finalStatus']['alive'] or
            source['elapsedSeconds'] < 90):
        raise ValueError('A completed live 90-second uninstrumented observation is required')
    if source['sourceSHA256'] != MONITOR_SHA:
        raise ValueError('Monitor source mismatch')
    game = Path(source['launch']['cwd']).resolve()
    target = game/'YuanShen.exe'
    if not same_file(source['image'], target) or not same_file(source['launch']['executable'], target):
        raise ValueError('Monitor image file identity differs')
    inputs = verified_inputs(ROOT, game)
    original = source['inputs']
    if original['profileSHA256'] != inputs['profileSHA256'] or original['files'] != inputs['files']:
        raise ValueError('Monitor profile or client inputs differ')
    for name in ('.env', '.env.local', 'config.json'):
        if any(original['config'][name].get(key) != inputs['config'][name].get(key) for key in ('exists', 'sha256', 'error')):
            raise ValueError('Monitor configuration differs: '+name)
    profile = json.loads((ROOT/'protocol/cn-7.2/profile.json').read_bytes())
    expected_metadata, checks = verify_contracts(target.read_bytes(), (game/profile['files']['metadata']['path']).read_bytes())
    observed = main_module(source['samples'][-1]['modules'], target)
    if observed['size'] != checks['imageSize']:
        raise ValueError('Monitor main module PE image size differs')
    backend = WindowsBackend()
    handle = backend.k.OpenProcess(PROCESS_READ_QUERY_WAIT, False, source['pid'])
    if not handle:
        raise C.WinError(C.get_last_error())
    try:
        creation = backend.creation_time(handle)
        if creation['filetime'] is None or creation != source['processCreationTime']:
            raise ValueError('Live PID creation identity differs')
        if not same_file(backend.image(handle), target) or not backend.status(handle)['alive']:
            raise ValueError('Live image or lifetime differs')
        current = main_module(backend.snapshot(8|16, source['pid'], MODULEENTRY32W,
                              'Module32FirstW', 'Module32NextW'), target)
        if current['base'] != observed['base'] or current['size'] != observed['size']:
            raise ValueError('Live main module base or size differs')
        backend.k.ReadProcessMemory.argtypes = [W.HANDLE, C.c_void_p, C.c_void_p, C.c_size_t, C.POINTER(C.c_size_t)]
        backend.k.ReadProcessMemory.restype = W.BOOL
        def read(address, width):
            buffer, count = C.create_string_buffer(width), C.c_size_t()
            ok = backend.k.ReadProcessMemory(handle, address, buffer, width, C.byref(count))
            return buffer.raw[:count.value], None if ok and count.value == width else dict(winerror=C.get_last_error(), bytesRead=count.value)
        result = collect(read, current['base'], expected_metadata)
        final = backend.status(handle)
        if not final['alive']:
            result['status'] = 'partial'
        result.update(pid=source['pid'], creation=creation, mainModule=current, inputChecks=inputs,
            sourceObservationSHA256=digest(raw), sourceSHA256=sha256(Path(__file__)),
            verifiedSources=checks, finalStatus=final)
        return result
    finally:
        backend.close(handle)


def output_path(path, game):
    path, allowed = path.resolve(), OUTPUT_ROOT.resolve()
    if (not path.is_relative_to(allowed) or path == allowed or path.exists() or
            path.is_relative_to(game.resolve())):
        raise ValueError('Choose a new private r1-interface-sampling output')
    return path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--observation', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    try:
        source = json.loads(args.observation.read_text(encoding='utf-8-sig'))
        output = output_path(args.output, Path(source['launch']['cwd']))
        sidecar = output.with_suffix('.source.py')
        if sidecar.exists():
            raise ValueError('Source sidecar already exists')
        output.parent.mkdir(parents=True, exist_ok=True)
        with sidecar.open('xb') as stream:
            stream.write(Path(__file__).read_bytes())
        with output.open('x', encoding='utf-8') as stream:
            try:
                result = execute(args.observation)
            except (OSError, ValueError, KeyError, IndexError) as error:
                result = dict(task='R1-SAMPLE-INTERFACES', status='error', error=str(error),
                              sourceSHA256=sha256(Path(__file__)), timeUtc=utc())
            json.dump(result, stream, indent=2)
            stream.write('\n')
    except (OSError, ValueError, KeyError) as error:
        parser.error(str(error))
    print(json.dumps(dict(status=result['status'], targetProtocolReady=False)))
    if result['status'] != 'completed':
        raise SystemExit(1)


if __name__ == '__main__':
    main()
