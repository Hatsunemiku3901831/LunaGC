"""Audit exported CN7.2 provider state and execute bounded native read helpers.

Offline only: no process API, client calls, I/O-success stubs, or altered assets.
"""
import argparse
import hashlib
import json
from pathlib import Path
import struct

try:
    from .client72_resource_index_native import NativeIndexProbe, CTABLE_SHA256, PERSISTENT, STREAMING
    from .extract_client72 import PE, read_verified, number
except ImportError:
    from client72_resource_index_native import NativeIndexProbe, CTABLE_SHA256, PERSISTENT, STREAMING
    from extract_client72 import PE, read_verified, number

BASE = 0x140000000
VTABLES = {'default': 0x1421e8a38, 'registered-0': 0x14220ba70, 'special': 0x14220c260}
NATIVE = (0x1415a0da0, 0x141c46fe0, 0x1415b6ee0, 0x141fdf310,
          0x1415beb80, 0x1415bf070, 0x141fdf210)
LEAVES = ((0x1415aa740, 0x1415aa75e), (0x140984540, 0x140984543),
          (0x1415b60c0, 0x1415b612e))
STATIC = (0x1415b8030, 0x1415d36d0, 0x1419eccf0, 0x141a08830,
          0x141a07f60, 0x141a09920, 0x141a09160, 0x1419f08b0,
          0x1419f1260, 0x1419e9df0, 0x1419ea990, 0x1419ea1e0,
          0x1419f1030, 0x1415a1bb0, 0x1415a2680, 0x1415b6ce0,
          0x1415b68f0, 0x1415b5f00, 0x1415b65c0, 0x1415b6040,
          0x140adede0, 0x140af0b20)


def main():
    args = arguments()
    root = Path(__file__).resolve().parents[1]
    profile_bytes = (root/'protocol/cn-7.2/profile.json').read_bytes()
    profile = json.loads(profile_bytes)['files']
    binary = None
    for row in profile.values():
        data = read_verified(args.client/row['path'], row['sha256'])
        if row['path'] == 'YuanShen.exe':
            binary = data
    ctable = read_verified(args.client/PERSISTENT, CTABLE_SHA256)
    if read_verified(args.client/STREAMING, CTABLE_SHA256) != ctable:
        raise ValueError('The two fixed ctable inputs differ')
    snapshots = [Snapshot(path, profile, digest(profile_bytes)) for path in args.snapshot]
    identity = {(s.data['pid'], s.data['creation']['filetime'], s.base) for s in snapshots}
    if len(identity) != 1:
        raise ValueError('Snapshots do not identify the same process creation/module base')
    audited = [audit_snapshot(s, binary) for s in snapshots]
    latest = snapshots[-1]
    prefix = registered_prefix(latest)
    probe = ProviderProbe(binary, root)
    try:
        result = experiments(probe, ctable, prefix, args.client)
        result['code'] = code_evidence(probe.engine, binary)
    finally:
        probe.close()
    source_dir = args.snapshot[-1].parent/'source-snapshots'
    source_proofs = []
    for s in snapshots:
        matches = [p.name for p in source_dir.glob('*.py') if digest(p.read_bytes()) == s.data['sourceSHA256']]
        if len(matches) != 1:
            raise ValueError('A unique saved sampler source matching the executed SHA is required')
        source_proofs.append(dict(snapshotSHA256=s.sha256, executedSourceSHA256=s.data['sourceSHA256'], archive=matches[0]))
    result.update(schemaVersion=1, task='D02-R3-resource-provider', snapshots=audited,
                  samplerSourceProofs=source_proofs, ctableSHA256=CTABLE_SHA256, ctableBytes=len(ctable),
                  profileSHA256=digest(profile_bytes),
                  sourceSHA256={name: digest((root/name).read_bytes()) for name in (
                      'tools/client72_resource_provider.py', 'tools/client72_resource_index_native.py',
                      'tools/client72_resource_index.py', 'tools/client72_emulator.py', 'tools/trace_client72_token.py')},
                  nativeCodeModels=[],
                  fixtures=['Offline native structures/stack/GS storage; no client state is written.',
                            'Immutable fixed ctable slices are placed in an explicit cached-window fixture.',
                            'Prefix bytes come from the exported provider; candidate paths are experiments, not observed calls.',
                            'Hash lookup nodes are synthetic contract fixtures, never presented as natural file entries.'],
                  actualResourceRouteObserved=False, naturalCtableInputObserved=False,
                  completeNativeOpener=False, ctableDecoded=False, entry0000006fRead=False,
                  resourceReady=False, clientExecuted=False)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    encoded = (json.dumps(result, indent=2, ensure_ascii=False)+'\n').encode()
    args.output.write_bytes(encoded)
    print(json.dumps(dict(sha256=digest(encoded), snapshots=len(audited), prefix=prefix.decode(),
                          prefixCases=len(result['prefixCases']), cacheCases=len(result['cacheCases']),
                          lookupCases=len(result['lookupCases']), ctableDecoded=False)))


def arguments():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--client', type=Path, required=True)
    p.add_argument('--snapshot', type=Path, action='append', required=True)
    p.add_argument('--output', type=Path, required=True)
    a = p.parse_args()
    a.client, a.output = a.client.resolve(), a.output.resolve()
    a.snapshot = [s.resolve() for s in a.snapshot]
    local = Path(__file__).resolve().parents[1]/'local'
    if not a.output.is_relative_to(local) or a.output.is_relative_to(a.client):
        p.error('Output must be under checkout local/ and outside the client directory')
    return a


def digest(data):
    return hashlib.sha256(data).hexdigest()


def u64(data, offset=0):
    return struct.unpack_from('<Q', data, offset)[0]


def fnv64(data):
    h = 0xcbf29ce484222325
    for value in data:
        h = ((h ^ value)*0x100000001b3) & 0xffffffffffffffff
    return h


class Snapshot:
    """Validate each external record once; consumers use typed bytes/addresses."""
    def __init__(self, path, profile, profile_sha):
        raw = path.read_bytes()
        self.sha256, self.data = digest(raw), json.loads(raw)
        d = self.data
        if d['status'] != 'completed' or d['creation']['winerror'] is not None:
            raise ValueError('Incomplete or unidentified snapshot')
        identities = {key: {k: row[k] for k in ('path', 'sha256')} for key, row in profile.items()}
        if d['inputChecks']['profileSHA256'] != profile_sha or d['inputChecks']['files'] != identities:
            raise ValueError('Snapshot input profile differs')
        self.base = d['mainModule']['base']
        self.rows = {}
        for row in d['reads']:
            data = bytes.fromhex(row['bytesHex'])
            if row['error'] is not None or len(data) != row['length'] or digest(data) != row['sha256']:
                raise ValueError('Invalid exported read: '+row['label'])
            if row['label'] in self.rows or not 0 < row['length'] <= 256 or row['address'] <= 0:
                raise ValueError('Duplicate or unbounded exported read')
            self.rows[row['label']] = (row['address'], data)

    def at(self, label, address=None, size=None):
        actual, data = self.rows[label]
        if address is not None and address != actual or size is not None and len(data) != size:
            raise ValueError('Pointer/size link differs for '+label)
        return data


def audit_snapshot(s, binary):
    pe, b = PE(binary), s.base
    router = u64(s.at('fileRouter.cell', b+0x5756990, 8))
    header = s.at('fileRouter.header', router, 0x90)
    count = u64(header, 0x30)
    tail_count = min(count, 4)
    tail = s.at('fileRouter.registeredTail', u64(header, 0x20)+(count-tail_count)*8, tail_count*8)
    if count != 1 or s.data['registeredFileProviderCount'] != count:
        raise ValueError('This recorded three-provider audit requires exactly one registered provider')
    objects = {'default': u64(header), 'registered-0': u64(tail), 'special': u64(header, 0x88)}
    providers = []
    for name, pointer in objects.items():
        obj = s.at('fileRouter.'+name+'.object', pointer, 16)
        vt = u64(obj)
        original_vt = VTABLES[name]
        if vt-b+BASE != original_vt:
            raise ValueError('Provider concrete vtable differs')
        live = s.at('fileRouter.'+name+'.vtable', vt, 0x90)
        functions = [value-b+BASE for (value,) in struct.iter_unpack('<Q', live)]
        offset = pe.offset(original_vt, 0x90)
        expected = list(struct.unpack('<18Q', binary[offset:offset+0x90]))
        if functions != expected:
            raise ValueError('Relocated provider vtable differs from the fixed PE')
        windows = []
        for label, slot in (('open', 8), ('read', 0x10), ('acceptPath', 0x60)):
            original = functions[slot//8]
            data = s.at('fileRouter.'+name+'.'+label+'.code', original-BASE+b, 32)
            pos = pe.offset(original, 32, executable=True)
            if data != binary[pos:pos+32]:
                raise ValueError('Observed provider code window differs from the fixed PE')
            windows.append(dict(slot=hex(slot), va=hex(original), bytes=32, sha256=digest(data), matches=True))
        providers.append(dict(name=name, vtableVA=hex(original_vt), all18SlotsMatch=True, code=windows))
    special = s.at('fileRouter.special.object')
    if u64(special, 8) != objects['default']:
        raise ValueError('Special provider no longer wraps the recorded default')
    cache = u64(s.at('fileCache.cell', b+0x5756980, 8))
    field = u64(s.at('fileCache.routeField558', cache+0x558, 8))
    result = dict(snapshotSHA256=s.sha256, readCount=len(s.rows), allReadHashesMatch=True,
                  pid=s.data['pid'], creationFILETIME=s.data['creation']['filetime'],
                  moduleBase=b, registeredCount=count, providers=providers,
                  specialWrapsDefault=True, fileCacheField558=field,
                  resourceSelection=s.at('resourceSelection.byte', b+0x5766598, 1)[0],
                  onceGuard=hex(struct.unpack('<I', s.at('resourceSelection.onceGuard', b+0x576659c, 4))[0]),
                  atomicSnapshot=s.data['atomicSnapshot'], actualResourceRouteObserved=False)
    if 'newResourceManager.object' in s.rows:
        manager = s.at('newResourceManager.object', b+0x55bc4b8, 0x20)
        state = s.at('newResourceManager.state', u64(manager, 8), 0x68)
        if u64(manager, 8)-b != 0x57aa100 or u64(manager, 16)-b != 0x57aa170:
            raise ValueError('Manager initialized state links differ from fixed source')
        result['manager'] = dict(staticObjectRVA='0x55bc4b8', stateRVA='0x57aa100',
                                 otherStateRVA='0x57aa170', fileKeyCount=u64(state, 0x18),
                                 createdLoadStateCount=u64(state, 0x58), bucketMask=u64(state, 0x38),
                                 bucketCount=u64(state, 0x40), cumulativeCounter=struct.unpack_from('<I', manager, 0x1c)[0],
                                 fileNamesObserved=False)
        result['registeredPrefix'] = registered_prefix(s).decode('utf-8')
    return result


def registered_prefix(s):
    pointer = s.rows['fileRouter.registered-0.object'][0]
    obj = s.at('fileRouter.registered-0.object', pointer, 16)
    if u64(obj)-s.base+BASE != VTABLES['registered-0']:
        raise ValueError('Prefix layout applies only to the proven registered concrete type')
    h = s.at('fileRouter.registered-0.prefixHeader', pointer+0x30, 0x28)
    data_pointer, length = u64(h), u64(h, 0x18)
    if length > 96 or not data_pointer and length > 15:
        raise ValueError('Recorded native prefix exceeds the bounded string contract')
    return s.at('fileRouter.registered-0.prefixBytes', data_pointer, length) if data_pointer else h[8:8+length]


def descriptor_slot(header, file_id):
    """Pure reviewed deque addressing; caller supplies the appropriate 0x30 header."""
    if len(header) != 0x30 or not 0 <= file_id < 0xffffffff:
        raise ValueError('Invalid descriptor header or reserved file ID')
    index = file_id & 0x7fffffff
    array, map_size, start, size = (u64(header, x) for x in (0x10, 0x18, 0x20, 0x28))
    if not array or not map_size or map_size & (map_size-1) or index >= size:
        raise ValueError('Descriptor index is absent or deque map is invalid')
    combined = start+index
    return array+(((combined >> 1) & (map_size-1))*8), (combined & 1)*8


class ProviderProbe(NativeIndexProbe):
    def __init__(self, binary, root):
        super().__init__(binary, root)
        e = self.engine
        e.models.clear()
        e.allowed.clear()
        for address in NATIVE:
            e.allow_unwind(address)
        e.allowed.extend(LEAVES)


def experiments(p, ctable, prefix, client):
    e = p.engine
    obj = e.alloc(0x58)
    e.uc.mem_write(obj+0x38, prefix+b'\0')
    e.write(obj+0x48, len(prefix))
    cases = [prefix, prefix+b'0000006f', prefix[:-1], prefix.upper()+b'0000006f',
             b'prefix/'+prefix, b'ctable.dat', str(client/PERSISTENT).encode(), str(client/STREAMING).encode()]
    prefixes = []
    for raw in cases:
        path = e.alloc(len(raw)+16)
        e.uc.mem_write(path, raw)
        returned, run, _ = p.call(0x1415a0da0, dict(rcx=obj, rdx=path))
        expected = raw.startswith(prefix)
        if bool(returned & 255) != expected or run['modeledCalls']:
            raise ValueError('Native prefix predicate differs from independent bytes comparison')
        prefixes.append(dict(path=raw.decode(), expected=expected, returned=bool(returned & 255), run=run))
    returned, stub, _ = p.call(0x140984540, {})
    if returned & 255:
        raise ValueError('Registered open/read no longer return false')
    normalized = []
    tls_array, tls_block = e.alloc(8), e.alloc(0x2000)
    e.write(p.teb+0x58, tls_array)
    e.write(tls_array, tls_block)
    # Explicit offline TLS-index fixture, not an observed target thread value.
    e.write(0x145eef080, 0, 'I')
    for raw in (b'archive:/0000006f', b'C:/Game/YuanShen_Data/Persistent/ctable.dat',
                b'UPPER/lower\\Mixed', b''):
        path = e.alloc(len(raw)+16)
        e.uc.mem_write(path, raw)
        pointer, run, _ = p.call(0x1415b60c0, dict(rcx=path))
        actual = bytes(e.uc.mem_read(pointer, len(raw)+1))
        if pointer != tls_block+0x16c0 or actual != raw.replace(b'/', b'\\')+b'\0' or run['modeledCalls']:
            raise ValueError('Native path normalization differs from slash-only conversion')
        normalized.append(dict(input=raw.decode(), output=actual[:-1].decode(), run=run))
    cached = []
    cache_cases = [('head', 0, 128, 0, 12, True), ('next', 0, 128, 12, 17, True),
                   ('window', 17, 64, 30, 23, True), ('before-window', 17, 64, 10, 16, False),
                   ('beyond-window', 0, 128, 125, 8, False), ('empty-window', 0, 0, 0, 12, False),
                   ('zero-request', 0, 128, 10, 0, False), ('offset64', 1 << 33, 128, (1 << 33)+7, 12, True)]
    for name, start, size, offset, count, expected in cache_cases:
        raw = ctable[:size]
        buffer = e.alloc(max(size, 16))
        if raw:
            e.uc.mem_write(buffer, raw)
        view, reader, output, out_count = e.alloc(0x30), e.alloc(24), e.alloc(count+32), e.alloc(8)
        e.write(view, buffer)
        e.write(view+0x20, start)
        e.write(view+0x28, start+size)
        e.write(reader+8, view)
        e.write(reader+16, offset)
        e.uc.mem_write(output, b'\xa5'*(count+32))
        e.write(out_count, 0xffffffffffffffff)
        returned, run, visited = p.call(0x1415b6ee0, dict(rcx=reader, rdx=count, r8=output+16, r9=out_count))
        actual = bytes(e.uc.mem_read(output+16, count))
        expected_data = raw[offset-start:offset-start+count] if expected else b'\xa5'*count
        amount = count if expected else 0
        if (bool(returned & 255) != expected or e.read(out_count) != amount
                or actual != expected_data or e.read(reader+16) != offset+amount
                or bytes(e.uc.mem_read(output, 16)) != b'\xa5'*16
                or bytes(e.uc.mem_read(output+16+count, 16)) != b'\xa5'*16 or run['modeledCalls']):
            raise ValueError('Native cached-window read differs from independent slicing/bounds')
        row = dict(name=name, windowStart=start, windowBytes=size, offset=offset, requested=count,
                   returned=bool(returned & 255), bytesRead=amount, finalOffset=e.read(reader+16),
                   outputSHA256=digest(actual), nativeMemcpyExecuted=0x141fdf310 in visited, run=run)
        if name == 'head':
            verdict, signature, _ = p.call(0x1415aa740, dict(rcx=output+16))
            row.update(signatureReturn=verdict & 255, signatureRun=signature,
                       rawSignatureHex=actual.hex(), actualClientReadFailure=False)
            if verdict & 255:
                raise ValueError('Fixed raw ctable unexpectedly satisfies the native signature')
        cached.append(row)
    lookups = lookup_experiments(p)
    return dict(prefixCases=prefixes, registeredOpenReadStub=stub, normalizationCases=normalized,
                cacheCases=cached, lookupCases=lookups)


def lookup_experiments(p):
    """Execute native FNV and exact case-sensitive map matching against explicit fixtures."""
    e, result = p.engine, []
    for raw in (b'ctable.dat', b'archive:/0000006f', b'C:\\game\\ctable.dat', b'', b'A'*31):
        text, string = e.alloc(max(32, len(raw)+16)), e.alloc(40)
        e.uc.mem_write(text, raw)
        if len(raw) < 16:
            e.uc.mem_write(string, raw)
            e.write(string+24, 15)
        else:
            e.write(string, text)
            e.write(string+24, len(raw))
        e.write(string+16, len(raw))
        table, sentinel, buckets, out = e.alloc(64), e.alloc(16), e.alloc(128), e.alloc(16)
        e.write(table+8, sentinel)
        e.write(table+24, buckets)
        e.write(table+48, 7)
        for slot in range(16):
            e.write(buckets+slot*8, sentinel)
        _, run, _ = p.call(0x1415beb80, dict(rcx=table, rdx=out, r8=string), stops=(0x1415bf070,))
        actual_hash = e.reg('r9')
        if actual_hash != fnv64(raw) or run['modeledCalls']:
            raise ValueError('Native string hash differs from independent FNV-1a64')
        native_results = []
        for exists in (False, True):
            if exists:
                node = e.alloc(56)
                e.uc.mem_write(node+16, bytes(e.uc.mem_read(string, 32)))
                e.write(node, sentinel)
                e.write(node+8, sentinel)
                e.write(node+48, 37, 'I')
                bucket = buckets+(actual_hash & 7)*16
                e.write(bucket, node)
                e.write(bucket+8, node)
            _, lookup, _ = p.call(0x1415bf070, dict(rcx=table, rdx=out, r8=string, r9=actual_hash))
            found = e.read(out+8)
            if bool(found) != exists or exists and found != node or lookup['modeledCalls']:
                raise ValueError('Native map lookup differs from the explicit fixture')
            native_results.append(dict(expectedPresent=exists, returnedPresent=bool(found), run=lookup))
        if raw:
            collision = bytes([raw[0] ^ 8])+raw[1:]
            if fnv64(collision) & 7 != actual_hash & 7:
                raise ValueError('Collision fixture no longer shares the reviewed bucket')
            other_text, other = e.alloc(len(collision)+16), e.alloc(32)
            e.uc.mem_write(other_text, collision)
            if len(collision) < 16:
                e.uc.mem_write(other, collision)
                e.write(other+24, 15)
            else:
                e.write(other, other_text)
                e.write(other+24, len(collision))
            e.write(other+16, len(collision))
            _, lookup, _ = p.call(0x1415bf070, dict(rcx=table, rdx=out, r8=other, r9=fnv64(collision)))
            if e.read(out+8) or lookup['modeledCalls']:
                raise ValueError('Equal-length hash-bucket collision incorrectly matched')
            native_results.append(dict(expectedPresent=False, returnedPresent=False,
                                       equalLengthBucketCollision=True, run=lookup))
        result.append(dict(key=raw.decode(), nativeHash=hex(actual_hash), hashRun=run, cases=native_results))
    return result


def code_evidence(e, binary):
    result = []
    for start in sorted(set(NATIVE+STATIC)):
        end = e.unwind_ranges[start]
        raw = binary[e.pe.offset(start):e.pe.offset(start)+end-start]
        result.append(dict(start=hex(start), end=hex(end), extent='one .pdata record; may be one chunk only',
                           sha256=digest(raw), executionAllowed=start in NATIVE))
    for start, end in LEAVES:
        raw = binary[e.pe.offset(start):e.pe.offset(start)+end-start]
        result.append(dict(start=hex(start), end=hex(end), extent='reviewed leaf through ret', sha256=digest(raw), executionAllowed=True))
    for start, end in ((0x140adede0, 0x140adf09f), (0x140af0b20, 0x140af0dd3),
                       (0x1415b6ce0, 0x1415b6edd), (0x1415b6880, 0x1415b68e1)):
        raw = binary[e.pe.offset(start):e.pe.offset(start)+end-start]
        result.append(dict(start=hex(start), end=hex(end), extent='reviewed contiguous code span, includes chained chunks',
                           sha256=digest(raw), executionAllowed=False))
    result.extend(import_evidence(binary, e.pe))
    return result


def import_evidence(binary, pe):
    """Resolve the actual PE import slots used by the reviewed synchronous reader."""
    optional = number(binary, 0x3c)+24
    directory, size = struct.unpack_from('<II', binary, optional+112+8)
    wanted = {0x143bac168: 'CreateFileW', 0x143bac7c0: 'SetFilePointerEx', 0x143bac6e0: 'ReadFile'}
    found = []
    for cursor in range(pe.offset(BASE+directory), pe.offset(BASE+directory)+size, 20):
        original, _, _, name, first = struct.unpack_from('<5I', binary, cursor)
        if not any((original, name, first)):
            break
        dll = binary[pe.offset(BASE+name):].split(b'\0', 1)[0].decode()
        index = 0
        while True:
            thunk = u64(binary, pe.offset(BASE+(original or first))+index*8)
            if not thunk:
                break
            slot = BASE+first+index*8
            if slot in wanted:
                target = pe.offset(BASE+thunk)+2
                name_text = binary[target:target+128].split(b'\0', 1)[0].decode()
                if name_text != wanted[slot]:
                    raise ValueError('Reviewed reader IAT identity differs')
                found.append(dict(iat=hex(slot), library=dll, name=name_text))
            index += 1
    if len(found) != len(wanted):
        raise ValueError('A reviewed reader IAT slot was not resolved')
    return found


if __name__ == '__main__':
    main()
