"""Replay native v5 blob headers using exported context and real fixed assets.

Never opens a process or alters a client file. Blob recovery is not ctable recovery.
"""
import argparse
import hashlib
import json
from pathlib import Path
import struct

try:
    from .client72_resource_provider import Snapshot, audit_snapshot, descriptor_slot, digest, u64
    from .client72_resource_index_native import NativeIndexProbe
    from .extract_client72 import read_verified
except ImportError:
    from client72_resource_provider import Snapshot, audit_snapshot, descriptor_slot, digest, u64
    from client72_resource_index_native import NativeIndexProbe
    from extract_client72 import read_verified

FILES = {
    'YuanShen_Data/StreamingAssets/20527480.blk': 'c73e06a002ceb74ef20fe2d334e7b7ed55c3d1cc4e6e13469bb874c62789addb',
    'YuanShen_Data/StreamingAssets/AssetBundles/blocks/00/31049740.blk': 'a5fdc46d5f7cb79812160d4a73b10048e593adb239e7b3555c2e5b491138021b',
    'YuanShen_Data/StreamingAssets/AssetBundles/blocks/00/31049741.blk': 'd993c81196145c66ac968108390952733a00d4cf8fce3b7b1275c9231a8f59ea',
    'YuanShen_Data/StreamingAssets/AssetBundles/blocks/00/09302948.blk': 'f307fff7f1b3bb3005ae9b15ca7c869726c2c1f75ffd73ddbff9bf4780f8dd6f',
}
MANIFESTS = {
    'pkg_version': '0b8d5de9333f241af06ac3989f8bf3d090f086f71bedcdece844d7cbf194d219',
    'YuanShen_Data/StreamingAssets/res_versions_streaming': '512a84b0d1bd3e7b4e91d276e40815409f36ce103e307b20b5d2ed6a05fde21e',
    'YuanShen_Data/StreamingAssets/data_versions_streaming': 'e6510ecb754a6bd1afd8aec71568aef6fcf957475d22f962ff3bb0afb2265841',
}
NATIVE = (0x1415b6ee0, 0x141fdf310, 0x141fdf170, 0x1415aa820, 0x1415aaa00,
          0x1408bc470, 0x1408bb9d0, 0x1408bbbc0, 0x1408bbd80, 0x1408baf20,
          0x1408bb240, 0x1408bc200, 0x1408bc060, 0x141cc06c0)
LEAVES = ((0x1415aa740, 0x1415aa75e), (0x1408bbec0, 0x1408bbf1f),
          (0x1408bbf20, 0x1408bc01c), (0x1408bc020, 0x1408bc057),
          (0x1408bc190, 0x1408bc1fb), (0x1415aa890, 0x1415aa897),
          (0x1415aa8a0, 0x1415aa8b5), (0x1415aa920, 0x1415aa929),
          (0x1415aa930, 0x1415aa940), (0x1415aa940, 0x1415aa948),
          (0x1415aa950, 0x1415aa9a0), (0x1415aa9a0, 0x1415aa9e9),
          (0x1415aa9f0, 0x1415aa9f8))
PREPARE_SPAN = (0x1415aa760, 0x1415aa81b)
CONTEXT_RVA = 0x5eed350


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
    resources = Snapshot(args.resources, profile, digest(profile_bytes))
    context = Snapshot(args.context, profile, digest(profile_bytes))
    identities = {(s.data['pid'], s.data['creation']['filetime'], s.base) for s in (resources, context)}
    if len(identities) != 1:
        raise ValueError('Natural resource and context samples differ in process identity')
    source_proofs = []
    for path, snapshot in ((args.resources, resources), (args.context, context)):
        candidates = [p.name for p in (path.parent/'source-snapshots').glob('*.py')
                      if digest(p.read_bytes()) == snapshot.data['sourceSHA256']]
        if len(candidates) != 1:
            raise ValueError('Executed sampler source archive is missing or ambiguous')
        source_proofs.append(dict(snapshotSHA256=snapshot.sha256,
                                 sourceSHA256=snapshot.data['sourceSHA256'], archive=candidates[0]))
    context_pointer = u64(context.at('archiveDecodeTable.cell', context.base+CONTEXT_RVA, 8))
    if not context_pointer:
        raise ValueError('Native archive context was not initialized; fallback is not modeled')
    native_context = context.at('archiveDecodeTable.bytes', context_pointer, 256)
    files, file_audit = natural_files(resources, args.client)
    probe = BlobProbe(binary, root, native_context)
    try:
        blobs = [probe.decode(path, data) for path, data in files.items()]
        errors = probe.invalid_parameters()
        code = probe.evidence(binary)
    finally:
        probe.close()
    resource_audit = audit_snapshot(resources, binary)
    resource_audit['spanCount'] = resource_audit.pop('readCount')
    resource_audit['spanCountIsSystemCallCount'] = False
    result = dict(schemaVersion=1, task='D02-R3-resource-blob',
                  profileSHA256=digest(profile_bytes),
                  inputSHA256={row['path']: row['sha256'] for row in profile.values()},
                  resourceSnapshot=resource_audit,
                  contextSnapshotSHA256=context.sha256, contextSpanCount=len(context.rows),
                  archiveContextSHA256=digest(native_context), samplerSources=source_proofs,
                  naturalFiles=file_audit, blobs=blobs, invalidParameters=errors, code=code,
                  sourceSHA256={name: digest((root/name).read_bytes()) for name in (
                      'tools/client72_resource_blob.py', 'tools/client72_resource_provider.py',
                      'tools/client72_resource_index_native.py', 'tools/client72_resource_index.py',
                      'tools/client72_emulator.py', 'tools/trace_client72_token.py')},
                  nativeFunctionModels=[],
                  fixtures=['Captured native 256-byte context is relocated into offline memory; it is not the managed permutation.',
                            'Cached-window structures and preallocated header capacity are explicit offline fixtures.',
                            'Native cache read, signature, header preparation, transform, cookie checks and getters execute original PE bytes.',
                            'Decoded structures are checked independently against actual file bounds; this is not live archive execution.'],
                  allFourHeadersDecoded=True, payloadDecompressed=False, completeNativeArchive=False,
                  actualResourceRouteObserved=False, ctableDecoded=False, entry0000006fRead=False,
                  resourceReady=False, clientExecuted=False)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    encoded = (json.dumps(result, ensure_ascii=False, indent=2)+'\n').encode()
    args.output.write_bytes(encoded)
    print(json.dumps(dict(bytes=len(encoded), sha256=digest(encoded), headers=len(blobs),
                          invalidParameterCases=len(errors), ctableDecoded=False)))


def arguments():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--client', type=Path, required=True)
    p.add_argument('--resources', type=Path, required=True)
    p.add_argument('--context', type=Path, required=True)
    p.add_argument('--output', type=Path, required=True)
    a = p.parse_args()
    for name in ('client', 'resources', 'context', 'output'):
        setattr(a, name, getattr(a, name).resolve())
    if not a.output.is_relative_to(Path(__file__).resolve().parents[1]/'local') or a.output.is_relative_to(a.client):
        p.error('Output must be under checkout local/ and outside the client directory')
    return a


def natural_files(snapshot, client):
    s = snapshot
    state = s.at('newResourceManager.state')
    sentinel = u64(state, 0x10)
    node = u64(s.at('newResourceManager.sentinel', sentinel, 16))
    if u64(state, 0x18) < 4:
        raise ValueError('Recorded manager has fewer than four file nodes')
    manifests = {name: [json.loads(line) for line in read_verified(client/name, sha).decode().splitlines() if line]
                 for name, sha in MANIFESTS.items()}
    files, rows, seen = {}, [], set()
    for index in range(4):
        if not node or node == sentinel or node in seen:
            raise ValueError('Natural manager list ended or repeated within four nodes')
        seen.add(node)
        label = 'newResourceManager.node'+str(index)
        raw = s.at(label, node, 48)
        file_id = struct.unpack_from('<I', raw, 16)[0]
        d = s.at(label+'.descriptor', s.base+0x5756ec0+(0xc0 if file_id & 0x80000000 else 0x48), 48)
        slot, within = descriptor_slot(d, file_id)
        block = u64(s.at(label+'.nameBlock', slot, 8))
        pointer = u64(s.at(label+'.namePointer', block+within, 8))
        raw_name = s.at(label+'.filename', pointer)
        original = next(row for row in s.data['reads'] if row['label'] == label+'.filename')
        if raw_name[-1:] != b'\0' or b'\0' in raw_name[:-1] or original.get('readUnitBytes') != 1 or not original.get('terminated'):
            raise ValueError('Natural filename is not a bounded NUL-terminated bytewise record')
        path = Path(raw_name[:-1].decode('utf-8')).relative_to(client).as_posix()
        if path not in FILES:
            raise ValueError('Natural file differs from the four reviewed fixed inputs')
        data = read_verified(client/path, FILES[path])
        md5 = hashlib.md5(data).hexdigest()
        matches = []
        for name, entries in manifests.items():
            for row in entries:
                if row['remoteName'] in (path, path.removeprefix('YuanShen_Data/StreamingAssets/AssetBundles/')):
                    if row['fileSize'] != len(data) or row['md5'] != md5:
                        raise ValueError('Natural file differs from package manifest MD5/size')
                    matches.append(dict(path=name, manifestSHA256=MANIFESTS[name], entry=row))
        if not any(row['path'] == 'pkg_version' for row in matches):
            raise ValueError('Natural file lacks an exact package manifest row')
        files[path] = data
        rows.append(dict(nodeIndex=index, fileID=hex(file_id), innerStateCount=u64(raw, 40),
                         path=path, bytes=len(data), sha256=FILES[path], md5=md5,
                         manifestMatches=matches, filenameReadUnitBytes=1, atomicFilenameRead=False))
        node = u64(raw)
    if set(files) != set(FILES):
        raise ValueError('Natural list does not contain the four distinct fixed inputs')
    return files, rows


def parse_header(header, file_bytes, header_size):
    """Independent structure/bounds verifier; no cipher implementation or guessed keys."""
    if len(header) != header_size or header_size < 0x48:
        raise ValueError('Invalid decoded-header extent')
    total, tail = struct.unpack_from('<II', header)
    compression, exponent = header[0x14:0x16]
    block_count, entry_count = struct.unpack_from('<II', header, 0x18)
    if not 0 < block_count <= 100000 or not 0 < entry_count <= 100000 or exponent > 31:
        raise ValueError('Invalid decoded block/entry count or block exponent')
    block_size, data_offset = 1 << exponent, header_size+28
    if not 0 < tail <= block_size or not data_offset < total <= file_bytes:
        raise ValueError('Blob or tail length is outside the actual file')
    table, entries, bits = [offset+struct.unpack_from('<q', header, offset)[0] for offset in (0x20, 0x28, 0x30)]
    for start, size in ((table, block_count*4), (entries, entry_count*16), (bits, ((entry_count+31)//32)*4)):
        if start < 0 or start+size > header_size:
            raise ValueError('Relative header table lies outside the decoded header')
    ends = list(struct.unpack_from('<'+str(block_count)+'I', header, table))
    if any(a >= b for a, b in zip([0]+ends, ends)) or data_offset+ends[-1] != total:
        raise ValueError('Compressed block intervals do not partition the blob payload')
    uncompressed_size = (block_count-1)*block_size+tail
    rows = []
    for index in range(entry_count):
        cursor = entries+index*16
        offset, size = struct.unpack_from('<II', header, cursor)
        name_offset = cursor+8+struct.unpack_from('<q', header, cursor+8)[0]
        if offset+size > uncompressed_size or not 0 <= name_offset < header_size:
            raise ValueError('Content extent/name lies outside decoded bounds')
        end = header.find(b'\0', name_offset)
        if end < 0:
            raise ValueError('Content name lacks a bounded terminator')
        name = header[name_offset:end].decode('utf-8')
        flag = bool(struct.unpack_from('<I', header, bits+(index//32)*4)[0] & (1 << (index & 31)))
        rows.append(dict(index=index, offset=offset, size=size, name=name, nameOffset=name_offset, flag=flag))
    return dict(logicalBlobBytes=total, dataOffset=data_offset, headerBytes=header_size,
                compressionTag=compression, blockBytes=block_size, blockCount=block_count,
                lastBlockBytes=tail, uncompressedBytes=uncompressed_size,
                compressedEnds=ends, content=rows, fileTailBytes=file_bytes-total)


class BlobProbe(NativeIndexProbe):
    def __init__(self, binary, root, context):
        super().__init__(binary, root)
        e = self.engine
        e.allowed.clear()
        e.models.clear()
        for address in NATIVE:
            e.allow_unwind(address)
        e.allowed.extend(LEAVES)
        e.allowed.append(PREPARE_SPAN)
        pointer = e.alloc(256)
        e.uc.mem_write(pointer, context)
        e.write(0x140000000+CONTEXT_RVA, pointer)

    def decode(self, path, data):
        e = self.engine
        header_size = struct.unpack_from('<I', data, 4)[0]
        if data[:4] != b'Blb\3' or data[8] != 5 or not 72 <= header_size <= 65536 or header_size+28 > len(data):
            raise ValueError('Unexpected fixed blob signature/header bound')
        window = data[:header_size+28]
        source, view, reader = e.alloc(len(window)), e.alloc(48), e.alloc(24)
        e.uc.mem_write(source, window)
        e.write(view, source)
        e.write(view+40, len(window))
        e.write(reader+8, view)
        signature, count = e.alloc(16), e.alloc(8)
        returned, read1, _ = self.call(0x1415b6ee0, dict(rcx=reader, rdx=12, r8=signature, r9=count))
        if returned & 255 != 1 or e.read(count) != 12:
            raise ValueError('Native signature read failed')
        returned, check, _ = self.call(0x1415aa740, dict(rcx=signature))
        if returned & 255 != 1:
            raise ValueError('Native signature check rejected the unchanged blob')
        header, storage = e.alloc(64), e.alloc(header_size+16+32)
        buffer = storage+16
        e.uc.mem_write(storage, b'\xa5'*(header_size+48))
        e.write(header+16, buffer)
        e.write(header+40, header_size+16)
        out, n = e.alloc(8), e.alloc(8)
        returned, prepare, _ = self.call(0x1415aa760, dict(rcx=header, rdx=signature, r8=out, r9=n))
        if returned & 255 != 1 or e.read(out) != buffer or e.read(n) != header_size+16:
            raise ValueError('Native header preparation differs from the v5 capacity contract')
        returned, read2, _ = self.call(0x1415b6ee0, dict(rcx=reader, rdx=e.read(n), r8=buffer, r9=count))
        if returned & 255 != 1 or e.read(count) != header_size+16:
            raise ValueError('Native encrypted-header read failed')
        encrypted = bytes(e.uc.mem_read(buffer, header_size+16))
        returned, transform, visited = self.call(0x1415aa820, dict(rcx=header))
        decoded = bytes(e.uc.mem_read(buffer+16, header_size))
        if (returned & 255 != 1 or e.read(header+56) != buffer+16 or e.read(header+48) != buffer
                or bytes(e.uc.mem_read(storage, 16)) != b'\xa5'*16
                or bytes(e.uc.mem_read(buffer+header_size+16, 16)) != b'\xa5'*16
                or bytes(e.uc.mem_read(buffer, 16)) != encrypted[:16]
                or decoded[min(header_size, 128):] != encrypted[16+min(header_size, 128):]
                or 0x141cc06c0 not in visited):
            raise ValueError('Native transform broke bounds, IV, tail or cookie-check contract')
        parsed = parse_header(decoded, len(data), header_size)
        getters = self.check_getters(header, parsed)
        runs = [read1, check, prepare, read2, transform]+[row['run'] for row in getters]
        if any(run['modeledCalls'] or run['memoryModels'] for run in runs):
            raise ValueError('Native header execution used an unapproved model')
        return dict(path=path, inputSHA256=FILES[path], fileBytes=len(data),
                    encryptedHeaderSHA256=digest(encrypted), decodedHeaderSHA256=digest(decoded),
                    decodedHeaderHex=decoded.hex(), parsed=parsed, nativeGetters=getters,
                    decryptBytes=min(header_size, 128), boundaryCanariesIntact=True,
                    ivAndPlaintextTailUnchanged=True, nativeCookieCheckExecuted=True,
                    runs=[read1, check, prepare, read2, transform])

    def check_getters(self, header, parsed):
        e, rows = self.engine, []
        for address, field in ((0x1415aa890, 'logicalBlobBytes'), (0x1415aa8a0, 'dataOffset'),
                               (0x1415aa920, 'compressionTag'), (0x1415aa930, 'blockBytes'),
                               (0x1415aa940, 'blockCount')):
            value, run, _ = self.call(address, dict(rcx=header))
            if value & 0xffffffff != parsed[field]:
                raise ValueError('Native scalar getter differs from independent header parser')
            rows.append(dict(kind=field, value=value & 0xffffffff, run=run))
        value, run, _ = self.call(0x1415aa9f0, dict(rcx=header))
        if value & 0xffffffff != len(parsed['content']):
            raise ValueError('Native content count differs')
        rows.append(dict(kind='contentCount', value=value & 0xffffffff, run=run))
        for index in sorted({0, parsed['blockCount']-1, parsed['blockCount']}):
            for address, compressed in ((0x1415aa950, False), (0x1415aa9a0, True)):
                out = e.alloc(8)
                e.uc.mem_write(out, b'\xa5'*8)
                _, run, _ = self.call(address, dict(rcx=header, rdx=index, r8=out, r9=out+4))
                if index == parsed['blockCount']:
                    expected = (0, 0)
                elif compressed:
                    start = parsed['compressedEnds'][index-1] if index else 0
                    expected = (start, parsed['compressedEnds'][index]-start)
                else:
                    expected = (index*parsed['blockBytes'], parsed['lastBlockBytes'] if index+1 == parsed['blockCount'] else parsed['blockBytes'])
                actual = struct.unpack('<II', e.uc.mem_read(out, 8))
                if actual != expected:
                    raise ValueError('Native block interval differs from independent cumulative arithmetic')
                rows.append(dict(kind='compressedBlock' if compressed else 'uncompressedBlock',
                                 index=index, offset=actual[0], size=actual[1], run=run))
        for index in range(len(parsed['content'])+1):
            out = e.alloc(32)
            e.uc.mem_write(out, b'\xa5'*32)
            returned, run, _ = self.call(0x1415aaa00, dict(rcx=header, rdx=index, r8=out, r9=out+4),
                                         stack=(out+8, out+16, out+24))
            if index == len(parsed['content']):
                if returned & 255 or e.read(out+16) or e.read(out+24, 'I') or e.read(out, 'I') != 0xa5a5a5a5:
                    raise ValueError('Native out-of-range content result differs from source contract')
                rows.append(dict(kind='content', index=index, returned=False, run=run))
                continue
            expected = parsed['content'][index]
            pointer = e.read(out+16)
            name = bytes(e.uc.mem_read(pointer, len(expected['name'].encode())+1))
            if (returned & 255 != 1 or e.read(out, 'I') != expected['offset']
                    or e.read(out+4, 'I') != expected['size'] or bool(e.read(out+8, 'B')) != expected['flag']
                    or name != expected['name'].encode()+b'\0'
                    or pointer != e.read(header+56)+expected['nameOffset']
                    or e.read(out+24, 'I') != e.read(header+16)+e.read(header+32)-pointer):
                raise ValueError('Native content entry differs from independent bounded parser')
            rows.append(dict(kind='content', index=index, returned=True, offset=expected['offset'],
                             size=expected['size'], nameSHA256=digest(name), run=run))
        return rows

    def invalid_parameters(self):
        e, rows = self.engine, []
        data, iv = e.alloc(64), e.alloc(32)
        e.uc.mem_write(data, b'\xa5'*64)
        cases = [('null-iv', data, 16, 0, 16, 0, 1005), ('zero-iv-length', data, 16, iv, 0, 0, 1005),
                 ('unaligned-iv-length', data, 16, iv, 15, 0, 1006), ('null-data', 0, 16, iv, 16, 0, 1001),
                 ('zero-data', data, 0, iv, 16, 0, 1001), ('short-data', data, 15, iv, 16, 0, 1002),
                 ('invalid-mode', data, 16, iv, 16, 3, 1004)]
        for name, pointer, size, iv_pointer, iv_size, mode, expected in cases:
            value, run, visited = self.call(0x1408bc470, dict(rcx=pointer, rdx=size, r8=iv_pointer, r9=iv_size), stack=(mode,))
            if value != expected or 0x1408bb9d0 in visited or bytes(e.uc.mem_read(data, 64)) != b'\xa5'*64 or run['modeledCalls']:
                raise ValueError('Native invalid-parameter check changed or modified data')
            rows.append(dict(name=name, returned=value, transformEntered=False, dataUnchanged=True, run=run))
        return rows

    def evidence(self, binary):
        e, rows = self.engine, []
        ranges = [(start, e.unwind_ranges[start], 'exact .pdata') for start in NATIVE]
        ranges += [(start, end, 'reviewed leaf through ret') for start, end in LEAVES]
        ranges += [(PREPARE_SPAN[0], PREPARE_SPAN[1], 'reviewed contiguous header-prepare chained chunks')]
        for start, end, extent in ranges:
            pos = e.pe.offset(start, end-start, executable=True)
            rows.append(dict(start=hex(start), end=hex(end), extent=extent, sha256=digest(binary[pos:pos+end-start])))
        return rows


if __name__ == '__main__':
    main()
