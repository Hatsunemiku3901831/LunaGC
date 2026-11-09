"""Fresh-epoch CN72 original successors; frozen code/bytes only, no stock access."""
import argparse
import hashlib
import json
from pathlib import Path
import struct
import sys

ROOT = Path(__file__).resolve().parents[1]
if not __package__:
    sys.path.insert(0, str(ROOT))
    __package__ = 'tools'
from . import client72_avatar_warm_continuation as prior
from . import collect72_avatar_string_successors as collector

OUT = ROOT / 'local/migration72/d02-parallel-20261109/fresh-successor-replay'
BASE = 'local/migration72/d02-parallel-20261109/'
PLAN = BASE + 'combined-successor-inputs/live-plan.private.json'
ACCEPTANCE = BASE + 'combined-successor-inputs/main-live-acceptance.private.json'
RAW_SHA = '1a2e7509f6149f995cc0cc5c4ddc8f0c3f8acd297afcfc27c1eb9d5caee9c63e'
EXE_SHA = '8b014f64b5dc12fbc476e0c362b053abfdd7a1378cd282e4ad81057621dbd6b5'
GETTER = 'local/migration72/d02-resume-20261109/r3-string-comparer-input/getdefault-and-cache-windows.private.json'
STRING = 'local/migration72/d02-resume-20261109/r3-string-comparer-input/sampling-contract.private.json'
ALLOCATOR = BASE + 'r1-allocator-successor/allocator-full1.private.json'
LOOKUP = BASE + 'r1-allocator-successor/main-frozen-reused-code/manifest.private.json'
AVATAR_CODE = BASE + 'r1-allocator-successor/main-frozen-avatar-entry1/manifest.private.json'
PREDECESSOR = BASE + 'a-avatar-string/main-third.private.json'
PINS = {
    PLAN: 'e4c319402fc56bd33b19b0935c77bbda0304255e04570c7c50c29a2b648f9b4a',
    ACCEPTANCE: 'd2b0a9a3389f7f0b8d0947efd6b72cfb6e54bf33731369e61ba3614f8daccd22',
    GETTER: 'fd1ea590071f564817a28c2a5017858fd8b1b46428c716f4b69dd2ddd43ba64f',
    STRING: '7722ea05b8fabc04527fafec20585b31f6a3d985ab683037c05e90b15e9df20a',
    ALLOCATOR: 'ee0e3d4b24cbd235b177b8ee7e6edfd20bd49feac89e1bda79b8d759d08274e2',
    LOOKUP: 'ba2d6bacf6574cd62f0cffd3e50e85200bbd7c3613b858e10c63c2b4ce67cc1e',
    AVATAR_CODE: '4acdd657015a45c7ae8982271406333b3caf88325610b088a5627a89e0bac19d',
    PREDECESSOR: '5ca9ac157ede168f299bceb65841e24a9accf89d8ef3bdb08c249a2afabdad1c',
    'tools/client72_avatar_warm_continuation.py': '7981c1e22609d4ac6c5927354e508cfc02b5aa242e33e0c213ea16883e8993ef',
}
PREFERRED = prior.PREFERRED
LIMIT = 2_000_000
BOUNDARY_READS, BOUNDARY_BYTES = 16, 128


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


def load_pin(path, digest, pins):
    raw = (ROOT / path).read_bytes()
    if sha(raw) != digest:
        raise ValueError('Frozen input differs: ' + path)
    pins[path] = digest
    return json.loads(raw)


def instruction_bytes(window):
    start, end = int(window['start'], 16), int(window['end'], 16)
    cursor, output = start, bytearray()
    for row in window['instructions']:
        raw = bytes.fromhex(row['bytes'])
        if int(row['address'], 16) != cursor or len(raw) != row['size']:
            raise ValueError('Frozen original instruction bytes are not contiguous')
        output.extend(raw)
        cursor += len(raw)
    if cursor != end or sha(output) != window['sha256']:
        raise ValueError('Frozen original window extent/hash differs')
    return start, end, bytes(output), window['sha256']


def audit_capture(capture, memory):
    actual, rows = capture['collection'], capture['backendRecords']
    if actual['budget'] != dict(reads=69, requestedBytes=2704, firstPassReads=35,
            firstPassBytes=1360, maxReads=76, maxBytes=3264, maxSingleRead=208):
        raise ValueError('Fresh captured budget differs')
    cursor = 0
    def read(address, width):
        nonlocal cursor
        row = rows[cursor]
        cursor += 1
        if (address, width) != (row['address'], row['requested']):
            raise ValueError('Fresh temporal request differs')
        return memory.read(address, width, 'source.temporal-projection')
    ticks = iter([actual['collectionBeginUTC'], *[tick for row in actual['records']
        for tick in (row['beginUTC'], row['endUTC'])], actual['collectionEndUTC']])
    projected = collector.collect(read, actual['moduleBase'], now=lambda: next(ticks))
    if projected != actual or cursor != len(rows) or next(ticks, None) is not None:
        raise ValueError('Fresh full collector/clock projection differs')
    return dict(fullTemporalProjectionEqual=True, sourceSHA256=RAW_SHA,
        rawReads=69, rawBytes=2704, finalExactSpans=34, oldCapturedHeapImported=False)


def load_sources(extra_manifest=None, extra_sha=None):
    pins = {}
    loaded = {p: load_pin(p, h, pins) for p, h in PINS.items() if p.endswith('.json')}
    for path, digest in PINS.items():
        if path.endswith('.py'):
            if sha((ROOT / path).read_bytes()) != digest:
                raise ValueError('Frozen harness source differs: ' + path)
            pins[path] = digest
    # Hash source files only; previous runtime/capture/model contents never enter memory.
    for path, digest in loaded[PREDECESSOR]['fixedSourcePins'].items():
        if path.startswith('tools/'):
            if sha((ROOT / path).read_bytes()) != digest:
                raise ValueError('Inherited harness source differs: ' + path)
            pins[path] = digest
    for path, digest in loaded[PLAN]['sourcePins'].items():
        if sha((ROOT / path).read_bytes()) != digest:
            raise ValueError('Fresh collector source differs: ' + path)
        pins[path] = digest
    capture = load_pin(loaded[PLAN]['outputPath'], RAW_SHA, pins)
    acceptance = loaded[ACCEPTANCE]
    if not acceptance['passed'] or acceptance['actualSampleSHA256'] != RAW_SHA:
        raise ValueError('Fresh acceptance differs')
    window = loaded[GETTER]['newWindow']
    windows = [(*instruction_bytes(window), GETTER)]
    source = loaded[ALLOCATOR]
    raw = bytes.fromhex(source['bytesHex'])
    if source['fixedEXE']['sha256'] != EXE_SHA or sha(raw) != source['nativeSHA256'] or len(raw) != 420:
        raise ValueError('Frozen allocator original bytes differ')
    windows.append((int(source['start'], 16), int(source['end'], 16), raw,
        source['nativeSHA256'], ALLOCATOR))
    avatar = dict(loaded[AVATAR_CODE])
    avatar['path'] = str(Path(AVATAR_CODE).parent / 'avatar-entry.bin')
    manifests = [(LOOKUP, loaded[LOOKUP]), (AVATAR_CODE, dict(avatar, windows=[avatar]))]
    if extra_manifest is not None:
        if not extra_sha:
            raise ValueError('Exact main-approved additional manifest SHA required')
        manifests.append((extra_manifest, load_pin(extra_manifest, extra_sha, pins)))
    for path, manifest in manifests:
        if manifest['fixedEXESHA256'] != EXE_SHA or manifest['newNativeWindows'] != 0:
            raise ValueError('Only main-frozen existing native ranges allowed')
        for row in manifest['windows']:
            raw = (ROOT / row['path']).read_bytes()
            start, end = int(row['start'], 16), int(row['end'], 16)
            if sha(raw) != row['sha256'] or len(raw) != end - start or len(raw) != row['bytes']:
                raise ValueError('Main-frozen original code bytes differ')
            pins[row['path']] = row['sha256']
            windows.append((start, end, raw, row['sha256'], path))
    string = capture['collection']['string']
    module = capture['collection']['moduleBase']
    if string['getterMethodInfo']['functionPointer'] - module + PREFERRED != 0x15746c870:
        raise ValueError('Fresh getter does not bind the frozen native body')
    template = loaded[STRING]['derivedDefinitionTemplates']['1243']
    method = template['methods'][0]
    if method['index'] != 10339 or int(method['registeredAddress'], 16) != string['equalsFunction'] - module + PREFERRED:
        raise ValueError('Fresh Equals metadata/AOT binding differs')
    return capture, windows, pins


class FrozenNative(prior.Native):
    """Reuse exact-byte native guards, loading code at its captured ASLR base."""
    def __init__(self, libraries, memory, windows, module, collection):
        self.U, self.R = prior.load_emulator(libraries)
        self.uc = self.U.Uc(self.U.UC_ARCH_X86, self.U.UC_MODE_64)
        self.memory, self.constants = memory, []
        self.module, self.collection = module, collection
        self.windows = [(module + a - PREFERRED, module + z - PREFERRED, h)
            for a, z, raw, h, source in windows]
        self.path, self.reads, self.writes, self.missing = [], [], [], []
        self.failure, self.stop_reason, self.stops = None, None, set()
        self.boundary_reads, self.boundary_bytes, self.boundaries = [], 0, []
        self.pages = set()
        self.map(prior.STACK, 0x20000)
        self.map(prior.RETURN, 1)
        self.map(prior.OBJECTS, 0x1000)
        for start, end, raw, digest, source in windows:
            address = module + start - PREFERRED
            self.map(address, len(raw))
            self.uc.mem_write(address, raw)
        for address, raw in memory.spans():
            self.map(address, len(raw))
            self.uc.mem_write(address, raw)
        self.hooks = [self.uc.hook_add(self.U.UC_HOOK_CODE, self.code),
            self.uc.hook_add(self.U.UC_HOOK_MEM_READ, self.read),
            self.uc.hook_add(self.U.UC_HOOK_MEM_WRITE, self.write),
            self.uc.hook_add(self.U.UC_HOOK_MEM_INVALID, self.invalid)]

    def code(self, uc, address, width, data):
        preferred = address - self.module + PREFERRED
        if preferred in (0x1405336c0, 0x1405269f0, 0x15746c870, 0x159b6f300):
            if len(self.boundary_reads) >= BOUNDARY_READS or self.boundary_bytes + 8 > BOUNDARY_BYTES:
                self.failure = 'caller-boundary-budget'
                uc.emu_stop()
                return
            sp = self.reg('rsp')
            ret = bytes(uc.mem_read(sp, 8))
            self.boundary_bytes += 8
            self.boundary_reads.append(dict(address=hex(sp), width=8, bytesHex=ret.hex(),
                entry=hex(address), preferredEntry=hex(preferred)))
            self.boundaries.append(dict(entry=hex(address), preferredEntry=hex(preferred),
                returnAddress=hex(int.from_bytes(ret, 'little')),
                registers={k: hex(self.reg(k)) for k in ('rcx', 'rdx', 'r8', 'r9', 'rsp')}))
        super().code(uc, address, width, data)

    def invalid(self, uc, access, address, width, value, data):
        if access in (self.U.UC_MEM_READ_UNMAPPED, self.U.UC_MEM_READ_PROT):
            _, _, origins = self.memory.lookup(address, width)
            self.memory.note('native.invalid-read', address, width, b'', 'unprovided-captured-byte',
                origins, reader=hex(self.reg('rip')))
        return super().invalid(uc, access, address, width, value, data)

    def run_fresh(self, name, entry, registers, owned=()):
        self.uc.mem_write(prior.OBJECTS, bytes(0x1000))
        for address, raw in owned:
            self.uc.mem_write(address, raw)
        sp = prior.STACK + 0x10008
        self.uc.mem_write(sp, struct.pack('<Q', prior.RETURN))
        for key, value in dict(rsp=sp, **registers).items():
            self.uc.reg_write(getattr(self.R, 'UC_X86_REG_' + key.upper()), value)
        error = None
        try:
            self.uc.emu_start(entry, prior.RETURN, count=LIMIT)
        except self.U.UcError as failure:
            error = str(failure)
        if len(self.path) >= LIMIT and self.failure is None:
            self.failure = 'instruction-budget'
        verified = True
        for (address, width), expected in self.memory.exact.items():
            actual = bytes(self.uc.mem_read(address, width))
            _, _, origins = self.memory.lookup(address, width)
            failure = None if actual == expected else 'input-readback-drift'
            self.memory.note('native.final-readback', address, width, actual, failure, origins)
            verified = verified and failure is None
        returned = self.reg('rip') == prior.RETURN and self.failure is None and error is None
        failed_last = self.failure == 'unprovided-captured-byte'
        return dict(name=name, entry=hex(entry), initialRegisters={k: hex(v) for k, v in registers.items()},
            returned=returned, stop=hex(self.reg('rip')),
            preferredStop=hex(self.reg('rip') - self.module + PREFERRED) if self.reg('rip') != prior.RETURN else None,
            failure=self.failure, emulatorError=error, observations=len(self.path),
            completedOriginalInstructions=len(self.path) - int(failed_last),
            lastObservedInstructionCompleted=not failed_last, perCallLimit=LIMIT,
            executedAddresses=[hex(a) for a in self.path],
            executedPreferred=[hex(a - self.module + PREFERRED) for a in self.path],
            pathSHA256=sha(b''.join(struct.pack('<Q', a) for a in self.path)),
            registers={k: hex(self.reg(k)) for k in ('rax', 'rcx', 'rdx', 'r8', 'r9', 'rsi', 'rsp')},
            reads=self.reads, writes=self.writes, missing=self.missing, boundaries=self.boundaries,
            boundaryHostBudget=dict(reads=len(self.boundary_reads), requestedBytes=self.boundary_bytes,
                maxReads=BOUNDARY_READS, maxBytes=BOUNDARY_BYTES, records=self.boundary_reads),
            capturedReadbackEqual=verified, naturalCallerObserved=False, successModelsSupplied=[],
            capturedPointersChanged=False, nativeCodeAtCapturedASLR=True,
            controlledOwnedStorage=[dict(address=hex(a), bytes=len(b), bytesHex=b.hex()) for a, b in owned])


def replay(libraries, extra_manifest=None, extra_sha=None):
    capture, windows, pins = load_sources(extra_manifest, extra_sha)
    collection = capture['collection']
    memory = prior.ExactBytes(collection['records'])
    audit = audit_capture(capture, memory)
    module = collection['moduleBase']
    runtime = lambda address: module + address - PREFERRED
    string = collection['string']
    has_avatar = any(start == 0x15183fe60 for start, *_ in windows)
    cases = [('string-warm-getter', string['getterMethodInfo']['functionPointer'],
        dict(rcx=string['getterMethodInfo']['pointer']), ())]
    # Body bytes are required before a selected Equals instruction can execute.
    equals_available = any(start <= 0x159b6f300 < end for start, end, *_ in windows)
    if equals_available:
        a, b = prior.OBJECTS + 0x600, prior.OBJECTS + 0x680
        text = 'fresh-owned-key'.encode('utf-16-le')
        value = bytes(16) + struct.pack('<I', len(text) // 2) + text + bytes(2)
        cases.append(('string-selected-equals-owned-equal', string['equalsFunction'],
            dict(rcx=string['default'], rdx=a, r8=b, r9=string['equalsMethodInfo']), ((a, value), (b, value))))
    entry = 0x15183fe60 if has_avatar else 0x1405336c0
    registers = dict(rcx=prior.AVATAR if has_avatar else collection['list']['pointer'])
    cases.append(('avatar-allocator-ordinary-comparison' if has_avatar else 'allocator-direct-owned-caller',
        runtime(entry), registers, ()))
    native = []
    for name, entry, registers, owned in cases:
        engine = FrozenNative(libraries, memory, windows, module, collection)
        native.append(engine.run_fresh(name, entry, registers, owned))
    return dict(task='F-03d3f4d2/E-01-R3-ITEM-ENTRY', sourceSHA256=sha(Path(__file__).read_bytes()),
        fixedSourcePins=pins, fixedEXESHA256=EXE_SHA, stockDiskRead=False,
        inputAudit=audit, moduleBase=hex(module), inputEpochSHA256=RAW_SHA,
        nativeWindows=[dict(start=hex(a), end=hex(z), bytes=len(raw), sha256=h, source=p)
            for a, z, raw, h, p in windows], newNativeWindows=0,
        native=native, actualEqualsSelection=dict(function=hex(string['equalsFunction']),
            preferredFunction='0x159b6f300', methodDefinition=10339, derivedDefinition=1243,
            actualMethodInfo=hex(string['equalsMethodInfo']),
            functionEqualsMethodInfoField=string['equals']['functionEqualsSlot'],
            metadataAotBound=True, bodyBytesAvailable=equals_available,
            bodyExecuted=any(0x159b6f300 == int(a, 16) for n in native for a in n['executedPreferred'])),
        hostBudget=dict(reads=len(memory.reads), requestedBytes=memory.requested,
            maxReads=4096, maxBytes=65536, maxSingleRead=208, records=memory.reads),
        cpuBudget=dict(calls=len(native), observations=sum(n['observations'] for n in native),
            completedOriginalInstructions=sum(n['completedOriginalInstructions'] for n in native),
            perContinuousCall=LIMIT, allFailuresPreserved=True),
        rawCapturedSourceUnchanged=all(n['capturedReadbackEqual'] for n in native),
        fullAvatarEntryUsed=has_avatar, oldCapturedHeapImported=False, OSModelsImported=False,
        allocationsReturnedByHost=False, hostDefaultReturned=False, capturedWritesAllowed=False,
        naturalCallerObserved=False, findEntryMethodInfoStillZero=collection['carrier']['findEntryMI'] == 0,
        defaultOriginalWriteExecuted=False, managedAllocationReturned=False,
        completeAvatarConstructor=False, completeAvatarHandler=False, completeStringComparer=False,
        configAbilityApproved=False, g2Verified=False, d02Complete=False, atomicSnapshot=False,
        clientOrDebuggerStarted=False, remoteCallsExecuted=False, successModelsSupplied=[])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--python-libs', type=Path, default=ROOT / 'local/migration72/python')
    parser.add_argument('--existing-code-manifest')
    parser.add_argument('--existing-code-manifest-sha256')
    args = parser.parse_args()
    output = args.output.resolve()
    if not output.is_relative_to(OUT.resolve()) or output.exists():
        parser.error('Fresh output in the assigned private directory required')
    result = replay(args.python_libs, args.existing_code_manifest, args.existing_code_manifest_sha256)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.with_suffix('.source.py').write_bytes(Path(__file__).read_bytes())
    with output.open('x', encoding='utf8') as stream:
        json.dump(result, stream, indent=2)
        stream.write('\n')
    print(json.dumps(dict(output=str(output.relative_to(ROOT)), sha256=sha(output.read_bytes()),
        cases=[dict(name=n['name'], returned=n['returned'], stop=n['preferredStop'],
            failure=n['failure'], observations=n['observations']) for n in result['native']],
        hostReads=result['hostBudget']['reads'], hostBytes=result['hostBudget']['requestedBytes'])))


if __name__ == '__main__':
    main()
