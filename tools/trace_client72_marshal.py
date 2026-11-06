"""Native CN72 PtrToStringAnsi with explicit tracked CRT/managed storage models.

This executes conversion/return instructions, not the full native allocator or
a live Marshal call. It never starts a client or loads a patch.
"""
import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path
import struct

try:
    from .extract_client72 import HASHES, read_verified, extract
    from .client72_emulator import Offline72
    from .trace_client72_seed_crypto import SeedString72
except ImportError:
    from extract_client72 import HASHES, read_verified, extract
    from client72_emulator import Offline72
    from trace_client72_seed_crypto import SeedString72

ENTRY, FACTORY = 0x159C21C90, 0x14052FBF0
ALLOCATE, SIZE, FREE = 0x141CAD0CC, 0x141CA72AC, 0x141CBFE48
CASES = [('null', None), ('empty', b''), ('one', b'A'),
         ('url', b'https://example.invalid/path'),
         ('unicode', 'http://example.invalid/中文😀'.encode('utf8')), ('embedded-nul', b'abc\0tail')]
CASES += [(f'ascii-{length}', b'x' * length) for length in (7, 8, 15, 16, 31, 32, 63, 64, 255, 256, 1024)]
CHECKPOINTS = (ENTRY, FACTORY, 0x140510E40, 0x14050C950, 0x140E44360,
               0x141CA7930, ALLOCATE, SIZE, FREE, 0x140563CD0)


class TrackedCrt72:
    """Single-thread fixture allocator, usable-size accounting and ownership."""
    def __init__(self, emulator):
        self.e = emulator
        self.allocations, self.events = {}, []
        self.context = emulator.alloc(2752)
        tls = emulator.read(emulator.read(0x613000058))
        emulator.write(tls + 0x1A50, self.context)
        emulator.models.update({ALLOCATE:self.allocate, SIZE:self.size, FREE:self.free})

    def allocate(self):
        e = self.e
        length, alignment, offset = e.reg('rdx'), e.reg('r8'), e.reg('r9')
        if (e.reg('rcx'), alignment, offset, e.read(e.reg('rsp') + 0x28, 'B')) != (self.context, 16, 0, 0):
            raise ValueError('Unexpected aligned CRT allocation inputs')
        pointer = e.alloc(length)
        self.allocations[pointer] = length
        self.events.append(dict(operation='allocate', bytes=length, alignment=alignment,
                                returnAddress=hex(e.read(e.reg('rsp')))))
        return pointer

    def size(self):
        e = self.e
        length = self.allocations[e.reg('rcx')]
        self.events.append(dict(operation='allocation-size-query', bytes=length,
                                returnAddress=hex(e.read(e.reg('rsp')))))
        return length

    def free(self):
        e = self.e
        pointer = e.reg('rcx')
        if pointer:
            length = self.allocations.pop(pointer)
            self.events.append(dict(operation='free', bytes=length, returnAddress=hex(e.read(e.reg('rsp')))))
        return 0


def recover(binary, metadata, records, libraries):
    declaration = [(row['index'], row['name'], method['name']) for row in records for method in row['methods']
                   if method['registeredAddress'] == hex(ENTRY)]
    if len(declaration) != 1 or declaration[0][1:] != ('Marshal', 'PtrToStringAnsi'):
        raise ValueError('Marshal entry declaration changed')
    fixtures = []
    for name, raw in CASES:
        e = Offline72(binary, libraries, records, True)
        runtime = None
        try:
            e.prepare()
            runtime = SeedString72(e, binary, metadata, 0, True)
            e.allow(ENTRY)
            storage = TrackedCrt72(e)
            pointer = 0
            if raw is not None:
                pointer = e.alloc(len(raw) + 1)
                e.uc.mem_write(pointer, raw + b'\0')
            visits, digest = Counter(), hashlib.sha256()
            def observe(address):
                digest.update(struct.pack('<Q', address))
                if address in CHECKPOINTS:
                    visits[address] += 1
            e.before_instruction = observe
            result = runtime.session.call(ENTRY, pointer)
            length = e.read(result + 16, 'I') if result else None
            actual = bytes(e.uc.mem_read(result + 20, length * 2)) if result else None
            expected = raw.split(b'\0')[0].decode('utf8').encode('utf-16-le') if raw is not None else None
            if actual != expected or storage.allocations:
                raise ValueError('Native string/CRT ownership differs from independent fixture')
            fixtures.append(dict(name=name, inputHex=raw.hex() if raw is not None else None,
                                 resultLength=length, resultUtf16Hex=actual.hex() if actual is not None else None,
                                 allocationEvents=storage.events, liveCrtAllocations=0, passed=True,
                                 trace=dict(instructions=e.steps, instructionPathSHA256=digest.hexdigest(),
                                            checkpoints={hex(k):v for k,v in sorted(visits.items())},
                                            models=dict(sorted(Counter(c['address'] for c in e.calls).items())))))
        finally:
            if runtime:
                runtime.close()
            e.close()
    return dict(schemaVersion=1, task='D-01b3', inputSHA256=HASHES, fixtureCount=len(fixtures),
                entry=hex(ENTRY), factory=hex(FACTORY), declaration=dict(typeIndex=declaration[0][0],
                    type=declaration[0][1], method=declaration[0][2]),
                runtimeStatus='not-tested', liveMarshalObserved=False, nativeMarshalExecuted=True,
                nativeCrtAllocatorExecuted=False, nativeManagedAllocatorExecuted=False,
                allocatorInputs=dict(tlsIndexCell='0x145eef080', gsTlsArrayOffset=0x58,
                                     managedAllocatorTlsOffset=0x19D0, crtAllocatorTlsOffset=0x1A50,
                                     nativeCrtPageTableCell='0x145ee5b80'),
                modeledBoundaries=dict(alignedAllocation=hex(ALLOCATE), allocationSize=hex(SIZE), free=hex(FREE),
                                       managedStringAllocation='0x140563cd0'),
                allocationSizeMeaning='fixture requested bytes used for native accounting; real heap rounding not recovered',
                fixtures=fixtures, results=[{k:v for k,v in row.items() if k != 'trace'} for row in fixtures],
                models=['SeedString72 declared OS/TLS/managed allocation and initialized storage',
                        'separate synthetic CRT context at TLS+1A50; 16-byte aligned tracked allocations',
                        'usable-size query returns this fixture allocation request; tracked free rejects unknown ownership'],
                outsideScope=['native CRT heap initialization/allocation/page-table algorithm',
                              'native managed GC allocation and lifetime', 'natural live nonempty Marshal call',
                              'DLL callback installation/SDK routing', 'startup exit cause', 'D-02 acceptance'])


def validate_summary(report):
    errors = []
    expected = dict(inputSHA256=HASHES, fixtureCount=len(CASES), entry=hex(ENTRY), factory=hex(FACTORY),
                    runtimeStatus='not-tested', liveMarshalObserved=False, nativeMarshalExecuted=True,
                    nativeCrtAllocatorExecuted=False, nativeManagedAllocatorExecuted=False)
    if any(report.get(key) != value for key,value in expected.items()):
        errors.append('Marshal input or execution scope mismatch')
    rows = report.get('results', [])
    if [row.get('name') for row in rows] != [name for name,_ in CASES]:
        errors.append('Marshal case list mismatch')
    for row, (_, raw) in zip(rows, CASES):
        expected = raw.split(b'\0')[0].decode('utf8').encode('utf-16-le') if raw is not None else None
        if (row.get('inputHex'), row.get('resultUtf16Hex'), row.get('resultLength')) != (
                raw.hex() if raw is not None else None, expected.hex() if expected is not None else None,
                len(expected)//2 if expected is not None else None):
            errors.append('Marshal UTF-8/UTF-16/null/NUL semantics mismatch')
        if row.get('passed') is not True or row.get('liveCrtAllocations') != 0:
            errors.append('Marshal result/CRT ownership mismatch')
        events = row.get('allocationEvents', [])
        if len(expected or b'') <= 14:
            if events:
                errors.append('Short string unexpectedly required a CRT allocation')
        elif (len(events) != 3 or [e.get('operation') for e in events] != ['allocate', 'allocation-size-query', 'free']
              or len({e.get('bytes') for e in events}) != 1 or events[0].get('alignment') != 16
              or [e.get('returnAddress') for e in events] != ['0x141ca7965', '0x141a30da7', '0x14052fd44']):
            errors.append('Marshal declared CRT storage boundary mismatch')
    if report.get('allocatorInputs', {}).get('crtAllocatorTlsOffset') != 0x1A50:
        errors.append('Marshal CRT TLS source mismatch')
    if report.get('allocatorInputs', {}).get('managedAllocatorTlsOffset') != 0x19D0:
        errors.append('Marshal managed TLS source mismatch')
    return errors


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--summary-output', type=Path, required=True)
    parser.add_argument('--libraries', type=Path, default=Path('local/migration72/python'))
    args = parser.parse_args()
    for path in (args.output, args.summary_output):
        if path.resolve().is_relative_to(args.client.resolve()):
            parser.error('Output must remain outside client')
    binary, metadata, startup = [read_verified(args.client / name, digest) for name,digest in HASHES.items()]
    records, _ = extract(binary, metadata, startup=startup, structures=True)
    report = recover(binary, metadata, records, args.libraries)
    errors = validate_summary(report)
    if errors:
        raise ValueError(errors)
    for path, value in ((args.output, report), (args.summary_output, {k:v for k,v in report.items() if k != 'fixtures'})):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(value, indent=2) + '\n', 'utf8')
    print(json.dumps(dict(fixtures=report['fixtureCount'],nativeMarshalExecuted=True,nativeAllocatorsExecuted=False)))


if __name__ == '__main__':
    main()
