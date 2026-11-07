"""Fixed-client native string/reader execution with an explicit raw-file view.

Never launches the client. The file-manager read-at model supplies unchanged
ctable bytes; it does not implement or assume the live provider/cache/transform.
"""
import argparse
import hashlib
import json
from pathlib import Path
import struct
import sys

try:
    from .client72_emulator import Offline72, STACK, END
    from .client72_resource_index import CTABLE_SHA256
    from .extract_client72 import read_verified
except ImportError:
    from client72_emulator import Offline72, STACK, END
    from client72_resource_index import CTABLE_SHA256
    from extract_client72 import read_verified

PERSISTENT = 'YuanShen_Data/Persistent/ctable.dat'
STREAMING = 'YuanShen_Data/StreamingAssets/ctable_streaming.dat'
NATIVE = (0x14146c630, 0x1414d30b0, 0x1414d9f90, 0x141a456a0, 0x141a456d1,
          0x141a457cb, 0x140970e90, 0x140970f80, 0x141fdf310, 0x141a2e420,
          0x141123490, 0x1415b9f40, 0x141c47070, 0x141123530, 0x141123790,
          0x141123680, 0x1419eccf0, 0x1415ab1b0, 0x1415ab360, 0x1415ab9ea)
LEAVES = {0x140003750: '8b4110c3', 0x1404bbeb0: '488d4114c3',
          0x140973fd0: '488bc1c3'}
LEAF_RANGES = ((0x141cc01a0, 0x141cc01ee), (0x1415ba6a0, 0x1415ba6d6),
               (0x141123810, 0x141123833))
STATIC = (0x141a2e450, 0x141a2ee50, 0x141a2e5a0, 0x1415d36d0,
          0x141a07890, 0x141a08830, 0x141a07f60, 0x1419fcc10,
          0x1419fccb0, 0x1415aabb0, 0x1415ba4a0, 0x1415bc210,
          0x1415bd380, 0x1415ce800, 0x1415b8030,
          0x141136fe0, 0x141137010, 0x141137eb0, 0x141a13060,
          0x1415b7a90, 0x1415b7580, 0x1415b7705, 0x140b0a7b0)


def main():
    args = arguments()
    root = Path(__file__).resolve().parents[1]
    profile = json.loads((root/'protocol/cn-7.2/profile.json').read_text())['files']
    inputs = {row['path']: row['sha256'] for row in profile.values()}
    binary = None
    for name, expected_sha in inputs.items():
        data = read_verified(args.client/name, expected_sha)
        if name == 'YuanShen.exe':
            binary = data
    ctable = read_verified(args.client/PERSISTENT, CTABLE_SHA256)
    if read_verified(args.client/STREAMING, CTABLE_SHA256) != ctable:
        raise ValueError('ctable copies differ')
    missing = args.client/'__resource_index_native_missing__.dat'
    if missing.exists():
        raise ValueError('Missing-file fixture unexpectedly exists')
    probe = NativeIndexProbe(binary, root)
    try:
        result = run_experiments(probe, ctable, str(args.client/PERSISTENT), str(missing))
        result['code'] = probe.code_evidence(binary)
    finally:
        probe.close()
    result.update(schemaVersion=1, task='D02-R3-resource-index-native', inputSHA256=inputs,
                  ctableSHA256=CTABLE_SHA256, ctableBytes=len(ctable),
                  sourceSHA256={name: digest((root/name).read_bytes()) for name in (
                      'tools/client72_resource_index_native.py', 'tools/client72_emulator.py',
                      'tools/client72_resource_index.py', 'tools/trace_client72_token.py')},
                  models=['Bounded aligned allocation/release at known native memory-service ABI; allocator internals not executed.',
                          'Managed UTF-16 storage and GS StackLimit are offline fixtures.',
                          '1415ba4a0 read-at returns actual bytes/count/EOF from an immutable fixed-file view; live provider/cache/transform not modeled.',
                          'Header check is the exact 1415ab350 continuation with an explicitly staged frame and an actually constructed reader; not the full archive opener.'],
                  completeNativeOpener=False, liveFileProviderObserved=False, ctableDecoded=False,
                  entry0000006fRead=False, resourceReady=False, clientExecuted=False)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    encoded = (json.dumps(result, ensure_ascii=False, indent=2)+'\n').encode()
    args.output.write_bytes(encoded)
    print(json.dumps(dict(strings=len(result['strings']), reads=len(result['readerCases']),
                          headerCases=len(result['headerCases']), ctableDecoded=False, sha256=digest(encoded))))


def arguments():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client', required=True, type=Path)
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    args.client, args.output = args.client.resolve(), args.output.resolve()
    local = Path(__file__).resolve().parents[1]/'local'
    if not args.output.is_relative_to(local) or args.output.is_relative_to(args.client):
        parser.error('Output must be under the checkout local directory and outside the client.')
    return args


def digest(data):
    return hashlib.sha256(data).hexdigest()


def fnv32(data):
    value = 0x811c9dc5
    for byte in data:
        value = ((value ^ byte)*0x1000193) & 0xffffffff
    return value


class RawFileView:
    """One immutable, hash-verified byte view; None represents a missing file."""
    def __init__(self, path, data):
        self.path, self.data = path.encode('utf-8'), data
        self.requests = []

    def read_at(self, path, name_id, offset, size):
        if path != self.path or name_id != fnv32(path):
            raise ValueError('Read-at path/FNV identity differs from the opened view')
        if not 0 <= offset < 1 << 64 or not 0 <= size <= 65536:
            raise ValueError('Read-at request outside this bounded model')
        data = b'' if self.data is None else self.data[offset:offset+size]
        self.requests.append(dict(offset=offset, requested=size, returned=len(data), sha256=digest(data),
                                  missing=self.data is None, eof=self.data is not None and offset >= len(self.data)))
        return data


class NativeIndexProbe:
    def __init__(self, binary, root):
        self.engine = e = Offline72(binary, root/'local/migration72/python', [], trace_instructions=True)
        for address in NATIVE:
            e.allow_unwind(address)
        for address, expected in LEAVES.items():
            raw = bytes.fromhex(expected)
            if binary[e.pe.offset(address):e.pe.offset(address)+len(raw)] != raw:
                raise ValueError('Known leaf bytes changed')
            e.allowed.append((address, address+len(raw)))
        e.allowed.extend(LEAF_RANGES)
        e.allowed.append((0x1415d36d0, 0x1415d36d1))
        self.allocations, self.memory_calls, self.runs = {}, [], []
        self.view = None
        self.teb = e.alloc(128)
        e.write(self.teb+0x10, STACK)
        e.models.update({0x141a2e450: self.allocate, 0x141a2e5a0: self.release,
                         0x1415ba4a0: self.read_at})

    def close(self):
        self.engine.close()

    def allocate(self):
        e = self.engine
        size, alignment, label, flags = (e.reg(name) for name in ('rcx', 'rdx', 'r8', 'r9'))
        if not 0 < size <= 65536 or alignment not in (1, 16) or flags != 0:
            raise ValueError('Allocation outside the reviewed memory-service model')
        pointer = e.alloc(size)
        self.allocations[pointer] = (size, label)
        self.memory_calls.append(dict(action='allocate', size=size, alignment=alignment, label=label))
        return pointer

    def release(self):
        e = self.engine
        pointer, label = e.reg('rcx'), e.reg('rdx')
        if pointer:
            allocation = self.allocations.get(pointer)
            if allocation is None or allocation[1] != label:
                raise ValueError('Release of an unowned or differently labelled allocation')
            del self.allocations[pointer]
        self.memory_calls.append(dict(action='release', null=pointer == 0, label=label))
        return 0

    def read_at(self):
        e = self.engine
        state, name_id, destination, offset = (e.reg(name) for name in ('rcx', 'rdx', 'r8', 'r9'))
        count = e.read(e.reg('rsp')+0x28)
        pointer = e.read(state) or state+8
        path = bytes(e.uc.mem_read(pointer, e.read(state+0x18)))
        if self.view is None:
            raise ValueError('No fixed file view is installed')
        data = self.view.read_at(path, name_id, offset, count)
        if data:
            e.uc.mem_write(destination, data)
        return len(data)

    def call(self, address, registers, stack=(), stops=()):
        e = self.engine
        e.prepare()
        e.uc.reg_write(e.R.UC_X86_REG_GS_BASE, self.teb)
        e.uc.reg_write(e.R.UC_X86_REG_RSP, STACK+0x100008)
        for name, value in registers.items():
            e.uc.reg_write(getattr(e.R, 'UC_X86_REG_'+name.upper()), value)
        for index, value in enumerate(stack):
            e.write(STACK+0x100030+index*8, value)
        path_hash, visited = hashlib.sha256(), set()
        def observe(pc):
            path_hash.update(struct.pack('<Q', pc))
            visited.add(pc)
            if pc in stops:
                e.uc.emu_stop()
        e.before_instruction = observe
        start = len(self.memory_calls)
        error = None
        try:
            e.uc.emu_start(address, END, timeout=3_000_000, count=500_000)
        except e.U.UcError as exc:
            error = str(exc)
        e.before_instruction = None
        row = dict(entry=hex(address), stop=hex(e.reg('rip')), steps=e.steps, pathSHA256=path_hash.hexdigest(),
                   memoryModels=self.memory_calls[start:], modeledCalls=e.calls, failure=e.failure, error=error)
        self.runs.append(row)
        if error or e.failure or e.reg('rip') not in (END, *stops):
            raise ValueError(str(row))
        return e.reg('rax'), row, visited

    def string(self, text):
        e = self.engine
        chars = text.encode('utf-16le')
        managed = e.alloc(22+len(chars))
        e.write(managed+16, len(chars)//2, 'I')
        e.uc.mem_write(managed+20, chars)
        wrapper, destination = e.alloc(8), e.alloc(40)
        e.write(wrapper, managed)
        returned, row, _ = self.call(0x1414d30b0, dict(rcx=wrapper, rdx=destination))
        length, pointer = e.read(destination+0x18), e.read(destination) or destination+8
        data = bytes(e.uc.mem_read(pointer, length))
        if returned != destination or data != text.encode() or e.read(pointer+length, 'B') != 0:
            raise ValueError('Native string differs from independent UTF-8/terminator expectation')
        row.update(inputSHA256=digest(text.encode()), utf8Bytes=length, utf8SHA256=digest(data),
                   storage='heap' if e.read(destination) else 'inline', capacity=e.read(destination+8) if e.read(destination) else 15)
        return managed, destination, row

    def reader(self, path, view):
        e = self.engine
        reader = e.alloc(0x258)
        self.call(0x141123490, dict(rcx=reader))
        raw = path.encode()
        pointer = e.alloc(len(raw)+1)
        e.uc.mem_write(pointer, raw)
        self.view = view
        returned, row, _ = self.call(0x141123530, dict(rcx=reader, rdx=pointer, r8=0, r9=0))
        state = e.read(reader+0x250)
        if e.read(state+0x30, 'I') != fnv32(raw) or e.read(reader) != 0x1421fe258 or returned & 0xff != 1:
            raise ValueError('Native reader construction/path-hash changed')
        row.update(openReturn=returned & 0xff, pathSHA256=digest(raw), nativeFNV32=e.read(state+0x30, 'I'),
                   operatingSystemFileHandleCreated=False)
        return reader, row

    def code_evidence(self, binary):
        e = self.engine
        execution_ranges = set(e.allowed)
        ranges = set(execution_ranges)
        for address in STATIC:
            e.allow_unwind(address)
            ranges.add((address, e.unwind_ranges[address]))
        return [dict(start=hex(a), end=hex(z), sha256=digest(binary[e.pe.offset(a):e.pe.offset(a)+z-a]),
                     exactPdata=e.unwind_ranges.get(a) == z, executionAllowed=(a, z) in execution_ranges,
                     boundaryOnly=(a, z) == (0x1415d36d0, 0x1415d36d1)) for a, z in sorted(ranges)]


def run_experiments(probe, ctable, path, missing_path):
    e = probe.engine
    strings = []
    for name, text in [('empty', ''), ('inline-15', 'a'*15), ('heap-16', 'b'*16),
                       ('unicode', '资源/🙂/ctable.dat'), ('temporary-heap-501', 'c'*501),
                       ('embedded-nul', 'a\0b'), ('fixed-path', path)]:
        _, _, row = probe.string(text)
        row['name'] = name
        strings.append(row)
    managed, _, _ = probe.string(path)
    _, frontier, _ = probe.call(0x14146c630, dict(rcx=managed, rdx=0, r8=0, r9=2), stack=(0,), stops=(0x1415d36d0,))
    frontier['unknown'] = 'Native resource-manager selection/initialization at 1415d36d0; no return modeled.'
    view = RawFileView(path, ctable)
    reader, opened = probe.reader(path, view)
    cases = []
    for name, offset, origin, count, null_buffer in (
            ('head', 0, 1, 12, False), ('next', 0, 2, 17, False),
            ('tail-short', len(ctable)-2, 1, 8, False), ('eof', len(ctable), 1, 8, False),
            ('large-offset', (1 << 33)+7, 1, 8, False), ('invalid-origin', 0, 0, 0, False),
            ('zero-count', 0, 1, 0, False), ('null-buffer', 0, 1, 12, True)):
        previous = e.read(reader+0x248)
        seek, seek_row, _ = probe.call(0x141123810, dict(rcx=reader, rdx=offset, r8=origin))
        position = offset if origin == 1 else previous+offset if origin == 2 else previous
        if e.read(reader+0x248) != position or seek & 0xff != int(origin in (1, 2)):
            raise ValueError('Native seek disagrees with independent absolute/relative/unsupported contract')
        output, amount = e.alloc(max(1, count)), e.alloc(8)
        e.write(amount, 0xA5A5A5A5A5A5A5A5)
        before = len(view.requests)
        returned, row, _ = probe.call(0x141123790, dict(rcx=reader, rdx=count, r8=0 if null_buffer else output, r9=amount))
        actual_count = e.read(amount)
        expected = b'' if null_buffer else ctable[position:position+count]
        if actual_count != len(expected) or returned & 0xff != int(not null_buffer and len(expected) == count):
            raise ValueError('Native read count/success differs from raw file-view expectation')
        if e.read(reader+0x248) != position+len(expected) or bytes(e.uc.mem_read(output, actual_count)) != expected:
            raise ValueError('Native read output or 64-bit position differs')
        cases.append(dict(name=name, seek=seek_row, read=row, offsetBeforeRead=position, count=actual_count,
                          success=bool(returned & 0xff), requests=view.requests[before:], sha256=digest(expected)))
    missing_view = RawFileView(missing_path, None)
    missing_reader, missing_open = probe.reader(missing_path, missing_view)
    output, amount = e.alloc(16), e.alloc(8)
    returned, missing_read, _ = probe.call(0x141123790, dict(rcx=missing_reader, rdx=12, r8=output, r9=amount))
    if returned & 0xff or e.read(amount) != 0:
        raise ValueError('Missing view incorrectly succeeded')
    missing_read.update(success=False, count=0, requests=missing_view.requests)
    header_cases = []
    for name, data in [('fixed-ctable', ctable), ('short-view', ctable[:4]), ('missing-view', None)]:
        probe.view = RawFileView(path, data)
        archive = e.alloc(0x4e0)
        e.write(archive+0x398, reader)
        _, row, visited = probe.call(0x1415ab350, dict(r13=archive, r12=0, r15=0, rbp=STACK+0x100800),
                                     stops=(0x1415aba2b, 0x1415ab3c5))
        if e.reg('rip') != 0x1415aba2b:
            raise ValueError('Unchanged raw ctable view unexpectedly passed blob-signature checks')
        row.update(name=name, rawFileView=True, prefixCheckReached=0x1415ab3a0 in visited,
                   nativeSignatureRejected=True, requests=probe.view.requests,
                   headerBytesSHA256=digest(data[:12]) if data is not None else None)
        header_cases.append(row)
    return dict(strings=strings, openerFrontier=frontier, readerOpen=opened, readerCases=cases,
                missingOpen=missing_open, missingRead=missing_read, headerCases=header_cases,
                allNativeCalls=probe.runs, unicornVersion=e.U.__version__)


if __name__ == '__main__':
    main()
