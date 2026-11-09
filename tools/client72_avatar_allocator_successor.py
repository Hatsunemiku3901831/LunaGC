"""Execute only the approved allocator prefix against frozen CN72 warm bytes."""
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

OUT = ROOT / 'local/migration72/d02-parallel-20261109/r1-allocator-successor'
PREDECESSOR = 'local/migration72/d02-parallel-20261109/a-avatar-string/main-third.private.json'
REVIEW = 'local/migration72/d02-parallel-20261109/a-avatar-string/main-review.private.json'
APPROVED_STRING = 'local/migration72/d02-parallel-20261109/a-avatar-string/approved-string-consumer-contract.private.json'
PREFIX_SOURCE = 'local/migration72/d02-parallel-20261109/r1-allocator-successor/allocator-prefix2.private.json'
FULL_SOURCE = 'local/migration72/d02-parallel-20261109/r1-allocator-successor/allocator-full1.private.json'
PREFIX_FIRST = 'local/migration72/d02-parallel-20261109/r1-allocator-successor/first.private.json'
PREFIX_REPEAT = 'local/migration72/d02-parallel-20261109/r1-allocator-successor/repeat.private.json'
FULL_FIRST = 'local/migration72/d02-parallel-20261109/r1-allocator-successor/full-first.private.json'
FULL_REPEAT = 'local/migration72/d02-parallel-20261109/r1-allocator-successor/full-repeat.private.json'
LOOKUP_APPROVAL = 'local/migration72/d02-parallel-20261109/r1-allocator-successor/main-reused-lookup-approval.private.json'
LOOKUP_SOURCE = 'local/migration72/d02-active/avatar/handler-closure/first.json'
PINS = {
    PREDECESSOR: '5ca9ac157ede168f299bceb65841e24a9accf89d8ef3bdb08c249a2afabdad1c',
    REVIEW: '6903e4b6eccdddba1f5ad188bdf901a643a30e1dfa9a1c168575e16925581ce7',
    APPROVED_STRING: 'f7fe7bc791a929f1ad504213d30e3e59008671f9f251ad5d53fd0c051651b7e9',
    'tools/client72_avatar_warm_continuation.py': '7981c1e22609d4ac6c5927354e508cfc02b5aa242e33e0c213ea16883e8993ef',
    'tools/client72_avatar_initializer.py': '9df1101b6204d371e2e60be593b843d1c1e91a690e9df2444498bde4df49b3b9',
    'tools/client72_emulator.py': '8007d2594ef8058a4f2a9f1c24094fb5d2551a202b4e52154876cdfe77a17c3c',
    PREFIX_SOURCE: '0da01c5edebbdb477ef39d9166985cc7c10b2bc48dcdcbd94058d7eae66f2fcb',
    FULL_SOURCE: 'ee0e3d4b24cbd235b177b8ee7e6edfd20bd49feac89e1bda79b8d759d08274e2',
    PREFIX_FIRST: 'da93c41ebd8f074045b002a7b4df17d490ae127e136c82d82ee129ab2c694d2e',
    PREFIX_REPEAT: 'da93c41ebd8f074045b002a7b4df17d490ae127e136c82d82ee129ab2c694d2e',
    FULL_FIRST: 'dc7dd494b8c49cf761b5fabe6cd9c4819b99eec0a5ae7cea9f888f73dc6240d1',
    FULL_REPEAT: 'dc7dd494b8c49cf761b5fabe6cd9c4819b99eec0a5ae7cea9f888f73dc6240d1',
    LOOKUP_APPROVAL: '03f2942396db30ec17e776288c8f3c2ed29d3c0e32c7387d84c74f24aeb19080',
    LOOKUP_SOURCE: '58c2c4486873fc74d65d825f74aaa715bf56fce8bd77c8e03208500f090d4f67',
}
ALLOCATOR, CALLER, CALLER_RETURN = 0x1405336c0, 0x15183fe84, 0x15183fe89
MAX_PREFIX = 64
MAX_BOUNDARY_READS, MAX_BOUNDARY_BYTES = 16, 128


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


def load_frozen():
    loaded = {}
    for path, digest in PINS.items():
        raw = (ROOT / path).read_bytes()
        if sha(raw) != digest:
            raise ValueError('Frozen predecessor source differs: ' + path)
        if path.endswith('.json'):
            loaded[path] = json.loads(raw)
    accepted = loaded[PREDECESSOR]
    for path, digest in accepted['fixedSourcePins'].items():
        if sha((ROOT / path).read_bytes()) != digest:
            raise ValueError('Inherited frozen source differs: ' + path)
    review = loaded[REVIEW]
    if not review['passed'] or review['mainReportSHA256'] != PINS[PREDECESSOR]:
        raise ValueError('Predecessor main acceptance differs')
    first = accepted['native'][0]
    if (first['name'], first['stop'], first['steps'], first['executedAddresses'][-1],
            first['failure'], first['allocationExecuted']) != (
            'avatar-entry-before-managed-allocator', hex(ALLOCATOR), 10, hex(CALLER), None, False):
        raise ValueError('Original allocator frontier differs')
    return accepted, loaded


def check_prefix(raw, pdata, *, entry=ALLOCATOR):
    """Validate the single approved entry and its original exception record."""
    if len(pdata) != 12:
        raise ValueError('One exact 12B pdata record required')
    begin_rva, end_rva, unwind_rva = struct.unpack('<III', pdata)
    begin, end = prior.PREFERRED + begin_rva, prior.PREFERRED + end_rva
    if entry != ALLOCATOR or begin != entry or end <= begin or unwind_rva == 0:
        raise ValueError('Allocator pdata extent differs')
    if not raw or len(raw) != min(MAX_PREFIX, end - begin):
        raise ValueError('Approved allocator prefix extent differs')
    return dict(start=hex(begin), end=hex(begin + len(raw)), functionEnd=hex(end),
        unwindRVA=hex(unwind_rva), bytes=len(raw), bytesHex=raw.hex(), sha256=sha(raw),
        pdataBytesHex=pdata.hex(), pdataSHA256=sha(pdata), completeFunction=len(raw) == end - begin)


def bind_prefix(source, binary):
    """Bind mainline's two exact files to the unchanged fixed PE."""
    for path, digest in source['sourcePins'].items():
        if sha((ROOT / path).read_bytes()) != digest:
            raise ValueError('Mainline prefix extraction source differs: ' + path)
    prefix_path, pdata_path = source['prefix']['path'], source['pdata']['path']
    if (prefix_path, pdata_path) != ('allocator-prefix2.private.bin', 'allocator-pdata2.private.bin'):
        raise ValueError('Mainline exact prefix files differ')
    raw, pdata = (OUT / prefix_path).read_bytes(), (OUT / pdata_path).read_bytes()
    result = check_prefix(raw, pdata)
    if (sha(raw), sha(pdata), raw.hex(), pdata.hex(), len(binary), sha(binary)) != (
            source['prefix']['sha256'], source['pdata']['sha256'], source['bytesHex'],
            source['pdata']['bytesHex'], source['fixedEXE']['bytes'], source['fixedEXE']['sha256']):
        raise ValueError('Mainline frozen prefix bytes or EXE differ')
    if (result['start'], result['end'], result['functionEnd'], result['unwindRVA']) != (
            source['start'], source['end'], source['functionEnd'], source['pdata']['unwindRVA']):
        raise ValueError('Mainline prefix/pdata declaration differs')
    pe = prior.static.PE(binary)
    code_offset = pe.offset(ALLOCATOR, len(raw), executable=True)
    pdata_offset = pe.offset(int(source['pdata']['recordVA'], 16), 12)
    optional = struct.unpack_from('<I', binary, 0x3c)[0] + 24
    table_rva, table_bytes = struct.unpack_from('<II', binary, optional + 112 + 3 * 8)
    record_rva = int(source['pdata']['recordRVA'], 16)
    if (code_offset != source['prefix']['fileOffset'] or pdata_offset != source['pdata']['fileOffset'] or
            not table_rva <= record_rva <= table_rva + table_bytes - 12 or
            (record_rva - table_rva) % 12 or pe.base + record_rva != int(source['pdata']['recordVA'], 16) or
            binary[code_offset:code_offset + len(raw)] != raw or binary[pdata_offset:pdata_offset + 12] != pdata):
        raise ValueError('Exact approved PE spans differ')
    result.update(sourceFile=PREFIX_SOURCE, sourceSHA256=PINS[PREFIX_SOURCE],
        prefixFile=prefix_path, pdataFile=pdata_path, codeFileOffset=code_offset, pdataFileOffset=pdata_offset,
        fullExceptionExtentApproval=False, mainlineSourcePins=source['sourcePins'])
    return result, raw


def bind_full(source, prefix, binary):
    for path, digest in source['sourcePins'].items():
        if sha((ROOT / path).read_bytes()) != digest:
            raise ValueError('Full allocator extraction source differs: ' + path)
    raw = (OUT / 'allocator-full1.private.bin').read_bytes()
    if (source['priorPrefixProofSHA256'] != PINS[PREFIX_SOURCE] or
            (source['start'], source['end'], source['bytes']) != (prefix['start'], prefix['functionEnd'], 420) or
            len(raw) != 420 or raw.hex() != source['bytesHex'] or sha(raw) != source['nativeSHA256'] or
            raw[:MAX_PREFIX].hex() != prefix['bytesHex'] or source['pdata']['bytesHex'] != prefix['pdataBytesHex'] or
            source['fixedEXE']['sha256'] != sha(binary)):
        raise ValueError('Approved same-function full allocator input differs')
    pe = prior.static.PE(binary)
    offset = pe.offset(ALLOCATOR, len(raw), executable=True)
    if offset != source['fileOffset'] or binary[offset:offset + len(raw)] != raw:
        raise ValueError('Approved complete allocator PE bytes differ')
    return dict(start=source['start'], end=source['end'], bytes=len(raw), bytesHex=raw.hex(),
        sha256=sha(raw), sourceFile=FULL_SOURCE, sourceSHA256=PINS[FULL_SOURCE],
        mainlineSourcePins=source['sourcePins'], fullExceptionExtentApproval=True,
        priorPrefixBytes=64, incrementalNativeBytes=356, newFunctions=0, calleeFetchAllowed=False), raw


def bind_lookup(approval, source, binary):
    expected = [(0x1405269f0, 0x1405269f5,
        'bb3bd836fbc8d866dc73ea8958b74331d207192b579c09e99d87e8637454f8ad'),
        (0x15a4d6b60, 0x15a4d7671,
        '2ae927cf60f8d3e5a4a1ba9595f41e61bc9a6d325d84bfc1bace8c6c27d2fff1')]
    declared = [(int(row['start'], 16), int(row['end'], 16), row['sha256'])
        for row in approval['approvedReusedNative']]
    if (declared != expected or approval['sourcePath'] != LOOKUP_SOURCE or
            approval['sourceSHA256'] != PINS[LOOKUP_SOURCE] or approval['fixedEXESHA256'] != sha(binary) or
            approval['newNativeWindows'] != 0 or approval['newNaturalInput'] is not False or
            approval['oldHostClassCacheModelsAllowed'] is not False or approval['OSLockSuccessModelsAllowed'] is not False):
        raise ValueError('Code-only native lookup approval differs')
    pe = prior.static.PE(binary)
    for start, end, digest in expected:
        offset = pe.offset(start, end - start, executable=True)
        if sha(binary[offset:offset + end - start]) != digest:
            raise ValueError('Existing pure native lookup window differs')
    old = source['cold']
    if old['functions']['0x15a4d6b60']['sha256'] != expected[1][2]:
        raise ValueError('Old full VM source declaration differs')
    executed = []
    for row in old['native']:
        addresses = row['trace']['executedAddresses']
        counts = {hex(start): addresses.get(hex(start), 0) for start, _, _ in expected}
        if any(counts.values()):
            executed.append(dict(name=row['name'], counts=counts, failure=row['failure'],
                pathSHA256=row['trace']['pathSHA256']))
    return expected, dict(approvalFile=LOOKUP_APPROVAL, approvalSHA256=PINS[LOOKUP_APPROVAL],
        sourceFile=LOOKUP_SOURCE, sourceSHA256=PINS[LOOKUP_SOURCE], previousActualCalls=executed,
        sourceContext='Old calls used declared class-cache/storage/OS models; only their fixed code is reused.',
        oldModelsImported=False, newNaturalInputs=False)


class AllocatorNative(prior.Native):
    """Original Avatar caller plus approved prefix; no allocator model exists."""
    def __init__(self, binary, libraries, memory, windows, constants, collection, blocked_calls=None):
        self.collection = collection
        self.boundary_reads, self.boundary_bytes = [], 0
        self.entry_state = None
        self.lookup_state = None
        self.blocked_calls, self.blocked_edge = blocked_calls or {}, None
        super().__init__(binary, libraries, memory, windows, constants)

    def boundary_read(self, address, width, purpose):
        if (len(self.boundary_reads) >= MAX_BOUNDARY_READS or
                self.boundary_bytes + width > MAX_BOUNDARY_BYTES):
            raise ValueError('Allocator boundary read budget')
        raw = bytes(self.uc.mem_read(address, width))
        self.boundary_bytes += width
        self.boundary_reads.append(dict(ordinal=len(self.boundary_reads) + 1,
            address=hex(address), width=width, purpose=purpose, bytesHex=raw.hex()))
        return raw

    def code(self, uc, address, width, data):
        if address in self.blocked_calls:
            self.blocked_edge = dict(self.blocked_calls[address], registers={name: hex(self.reg(name))
                for name in ('rcx', 'rdx', 'r8', 'rax', 'rsi')})
            self.stop_reason = 'unapproved-direct-callee-before-call'
            uc.emu_stop()
            return
        if address == ALLOCATOR:
            stack = self.reg('rsp')
            ret = int.from_bytes(self.boundary_read(stack, 8, 'allocator.actual-return'), 'little')
            pointer = self.reg('rcx')
            if pointer != self.collection['listU32']['pointer'] or ret != CALLER_RETURN:
                self.failure = 'allocator-caller-ABI-differs'
                uc.emu_stop()
                return
            self.entry_state = dict(rcx=hex(pointer), rsp=hex(stack), actualReturn=hex(ret),
                caller=hex(CALLER), classDefinition=1255, capturedPointerChanged=False)
        if address == 0x1405269f0:
            stack = self.reg('rsp')
            ret = int.from_bytes(self.boundary_read(stack, 8, 'lookup.actual-return'), 'little')
            if self.reg('rcx') != 1255 or ret != 0x140533721:
                self.failure = 'ordinary-lookup-caller-ABI-differs'
                uc.emu_stop()
                return
            self.lookup_state = dict(rcx=hex(self.reg('rcx')), rsp=hex(stack), actualReturn=hex(ret),
                caller='0x14053371c', nativeReturnSupplied=False)
        super().code(uc, address, width, data)

    def invalid(self, uc, access, address, width, value, data):
        if access in (self.U.UC_MEM_READ_UNMAPPED, self.U.UC_MEM_READ_PROT):
            _, _, origins = self.memory.lookup(address, width)
            self.memory.note('native.invalid-read', address, width, b'', 'unprovided-captured-byte', origins,
                reader=hex(self.reg('rip')))
        return super().invalid(uc, access, address, width, value, data)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--python-libs', type=Path, default=ROOT / 'local/migration72/python')
    parser.add_argument('--phase', choices=('prefix', 'full', 'lookup'), default='lookup')
    args = parser.parse_args()
    output = args.output.resolve()
    if (not output.is_relative_to(OUT.resolve()) or output.exists() or
            output.with_suffix('.source.py').exists()):
        parser.error('Choose a new output inside the assigned private directory')
    accepted, loaded = load_frozen()
    profile = json.loads((ROOT / 'protocol/cn-7.2/profile.json').read_bytes())
    five = {row['path']: row['sha256'] for row in profile['files'].values()}
    if five != prior.warm.cache.prior.frozen.FIVE_INPUTS or five != accepted['fiveInputSHA256']:
        raise ValueError('Original fixed five-stock profile differs')
    client = Path(json.loads(prior.INVENTORY.read_bytes())['gamePath'])
    inputs = {}
    for row in profile['files'].values():
        raw = prior.static.read_verified(client / row['path'], row['sha256'])
        if len(raw) != row['size']:
            raise ValueError('Fixed stock size differs: ' + row['path'])
        inputs[row['path']] = raw
    binary = inputs[next(iter(prior.static.HASHES))]
    prefix, prefix_raw = bind_prefix(loaded[PREFIX_SOURCE], binary)
    prior.load_emulator(args.python_libs)
    from capstone import Cs, CS_ARCH_X86, CS_MODE_64
    decoded = [dict(address=hex(ins.address), width=ins.size, bytesHex=ins.bytes.hex(),
        text=ins.mnemonic + ' ' + ins.op_str) for ins in Cs(CS_ARCH_X86, CS_MODE_64).disasm(prefix_raw, ALLOCATOR)]
    complete_end = ALLOCATOR + sum(row['width'] for row in decoded)
    prefix['completeInstructions'] = decoded
    prefix['completeInstructionEnd'] = hex(complete_end)
    prefix['partialTrailingBytes'] = dict(address=hex(complete_end), bytesHex=prefix_raw[complete_end - ALLOCATOR:].hex(),
        decoded=False, executed=False, targetKnown=False)
    allocator, allocator_raw = (bind_full(loaded[FULL_SOURCE], prefix, binary) if args.phase != 'prefix'
        else (prefix, prefix_raw))
    allocator_instructions = [dict(address=hex(ins.address), width=ins.size, bytesHex=ins.bytes.hex(),
        text=ins.mnemonic + ' ' + ins.op_str) for ins in Cs(CS_ARCH_X86, CS_MODE_64).disasm(allocator_raw, ALLOCATOR)]
    old_loaded = {path: json.loads((ROOT / path).read_bytes()) for path in (prior.DEEP, prior.RESOLVER)}
    windows = prior.known_windows(binary, old_loaded)
    windows.append((ALLOCATOR, int(allocator['end'], 16), allocator['sha256']))
    reused_windows, reused_source = (bind_lookup(loaded[LOOKUP_APPROVAL], loaded[LOOKUP_SOURCE], binary)
        if args.phase == 'lookup' else ([], None))
    windows.extend(reused_windows)
    direct_instructions = list(allocator_instructions)
    pe = prior.static.PE(binary)
    for start, end, _ in reused_windows:
        offset = pe.offset(start, end - start)
        direct_instructions.extend(dict(address=hex(ins.address), width=ins.size, bytesHex=ins.bytes.hex(),
            text=ins.mnemonic + ' ' + ins.op_str) for ins in Cs(CS_ARCH_X86, CS_MODE_64).disasm(
                binary[offset:offset + end - start], start))
    blocked_calls = {int(row['address'], 16): dict(caller=row['address'],
        target=row['text'].split()[1], callerReturn=hex(int(row['address'], 16) + row['width']),
        instructionBytesHex=row['bytesHex'], executed=False) for row in direct_instructions
        if row['text'].startswith('call 0x') and not any(
            start <= int(row['text'].split()[1], 16) < end for start, end, _ in windows)}
    capture = json.loads((ROOT / prior.CAPTURE).read_bytes())
    memory = prior.ExactBytes(capture['collection']['records'])
    audit = prior.audit_capture(capture, memory)
    memory.root_aliases(capture['collection']['moduleBase'])
    pe = prior.static.PE(binary)
    constants = []
    for row in accepted['staticConstants']:
        address, raw = int(row['address'], 16), bytes.fromhex(row['bytesHex'])
        if binary[pe.offset(address, len(raw)):pe.offset(address) + len(raw)] != raw:
            raise ValueError('Inherited exact PE constant differs')
        constants.append((address, raw))
    engine = AllocatorNative(binary, args.python_libs, memory, windows, constants, capture['collection'], blocked_calls)
    native = engine.run('avatar-allocator-approved-prefix', 0x15183fe60, dict(rcx=prior.AVATAR))
    if native['executedAddresses'][:10] != accepted['native'][0]['executedAddresses']:
        raise ValueError('Original complete Avatar prefix changed')
    allocator_path = [address for address in native['executedAddresses']
        if ALLOCATOR <= int(address, 16) < int(prefix['end'], 16)]
    full_path = [address for address in native['executedAddresses']
        if ALLOCATOR <= int(address, 16) < int(allocator['end'], 16)]
    flag_address = capture['collection']['listU32']['pointer'] + 0xca
    flag_source, flag_error, flag_origins = memory.lookup(flag_address, 1)
    if flag_error:
        raise ValueError('Original List flag byte is not captured')
    flag_reads = [row for row in memory.reads if row['purpose'] == 'native.read' and
        row['address'] == hex(flag_address) and row['reader'] == '0x1405336d7' and row['width'] == 1]
    root_missing = native['missing'] == [dict(address='0x145bc8f18', width=8,
        reader='0x15a4d6bd4', fetch=False)]
    minimal = dict(task='F-03d3f4d2', status='proposed-only-no-live-authorization',
        object='Ordinary class-definition cache root cell; not the MethodInfo table or closed GenericClass.cachedClass',
        actualReader='0x15a4d6bd4', callerDefinition=1255, moduleRVA='0x5bc8f18', width=8,
        addressSource='Fresh verified module base B + RVA; never reuse the exited capture address',
        producer=dict(exactTargetFunctionKnown=False,
            knownSource='Original VM consumes this cache root. Previous harness explicitly bound a controlled class-cache table.',
            exclusion='No natural producer or root value is inferred from that controlled binding.'),
        timing='New verified runtime epoch with its full warm MI/owner/GenericClass graph. Existing nonzero closed cachedClass does not guarantee an observed ordinary root or slot.',
        firstPass=dict(reads=1, bytes=8), withExactFinalRepeat=dict(reads=2, bytes=16),
        maxSingleRead=8, nullOrError='Keep original null/error and stop; do not substitute a class or expand through the pointer.',
        conditional1255SlotNotAuthorized=True, allocatorCompareRootNotAuthorized=True,
        newCollectorOrBudgetApprovalRequired=True, oldCollectorChanged=False, captureUnchanged=True,
        atomicSnapshot=False, currentInputWasReadOnly=True, liveExecuted=False)
    failed_instruction = bool(native['failure'] == 'unprovided-captured-byte' and native['missing'] and
        native['missing'][-1]['reader'] == native['stop'])
    result = dict(task='F-03d3f4d2/R1-ALLOCATOR-SUCCESSOR', sourceSHA256=sha(Path(__file__).read_bytes()),
        fixedSourcePins={**accepted['fixedSourcePins'], **PINS, **prefix['mainlineSourcePins']},
        fiveInputSHA256=five, fullFiveStockGateRun=True, stockDiskIdentityRequired=True,
        predecessorReportSHA256=PINS[PREDECESSOR], inputAudit=audit, approvedPrefix=prefix,
        phase=args.phase, approvedAllocator=allocator, allocatorInstructions=allocator_instructions,
        reusedNativeSource=reused_source,
        reusedNativeWindows=[dict(start=hex(a), end=hex(z), sha256=h) for a, z, h in reused_windows],
        previousPrefixReports={path: PINS[path] for path in (PREFIX_FIRST, PREFIX_REPEAT)},
        knownWindows=[dict(start=hex(a), end=hex(z), sha256=h) for a, z, h in windows],
        newNativeWindows=1, newNativeBytes=len(allocator_raw), newPdataBytes=12,
        incrementalNativeBytes=max(0, len(allocator_raw) - MAX_PREFIX),
        native=native, allocatorEntry=engine.entry_state, allocatorPrefixObservedAddresses=allocator_path,
        allocatorObservedAddresses=full_path, blockedEdge=engine.blocked_edge,
        ordinaryLookupEntry=engine.lookup_state,
        originalAvatarPrefixEqual=True,
        actualFlag=dict(address=hex(flag_address), offset='0xca', valueHex=flag_source.hex(),
            mask='0x20', maskSet=bool(flag_source[0] & 0x20), captureOrdinals=flag_origins,
            nativeReads=flag_reads, semanticNameRecovered=False),
        nextInput=dict(kind=('unprovided-natural-memory' if native['missing'] else
                'unapproved-direct-callee-before-call' if engine.blocked_edge else
                'unapproved-original-code-at-actual-taken-branch'), address=native['stop'],
            branch='0x1405336de jne 0x140533711', blockedEdge=engine.blocked_edge,
            exactMissingObject=minimal['object'] if root_missing else None,
            approvedPrefixEnd=prefix['end'], exceptionFunctionEnd=prefix['functionEnd'],
            wholeFunctionExtentIsNotApproval=True, missing=native['missing'],
            newNaturalInputNeedEstablished=bool(native['missing']),
            liveSamplingAuthorized=False, minimalProposedInput=minimal if root_missing else None,
            autoContinuation=False),
        modelBoundaries=dict(controlledAvatarBytes=768, allocationReturnModel=False,
            gcHeapThreadOSModelsAdded=[], inheritedTypedHeapIsOnlyDeclaredStorage=True,
            capturedClassAndPointersUnchanged=True, oldMetadataStorageModelsNotUsedAsManagedAllocation=True),
        hostBudget=dict(reads=len(memory.reads), requestedBytes=memory.requested,
            maxReads=prior.MAX_READS, maxBytes=prior.MAX_BYTES, maxSingleRead=208, records=memory.reads,
            scope='Exact capture audit, original collector temporal projection, root aliases, native captured reads and all final readbacks'),
        boundaryHostBudget=dict(reads=len(engine.boundary_reads), requestedBytes=engine.boundary_bytes,
            maxReads=MAX_BOUNDARY_READS, maxBytes=MAX_BOUNDARY_BYTES, records=engine.boundary_reads,
            scope='Additional actual allocator caller stack-return read; fixed file gates are separate'),
        cpuBudget=dict(calls=1, maxCalls=1, instructions=native['steps'], perCall=prior.LIMIT,
            maxInstructions=prior.LIMIT, counterIsPreInstructionObservation=True,
            completedOriginalInstructions=native['steps'] - int(failed_instruction),
            lastObservedInstructionCompleted=not failed_instruction),
        successModelsSupplied=[], rawCapturedSourceUnchanged=native['capturedReadbackEqual'],
        managedAllocatorPrefixExecuted=bool(allocator_path), managedAllocationReturned=False,
        defaultOriginalWriteExecuted=False, completeAvatarConstructor=False, completeColdClassInitialization=False,
        completeAvatarHandler=False, g2Verified=False, d02Complete=False, atomicSnapshot=False,
        naturalCallerObserved=False, naturalLookupObserved=False, clientOrDebuggerStarted=False,
        remoteCallsExecuted=False, priorReportsChanged=False)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.with_suffix('.source.py').write_bytes(Path(__file__).read_bytes())
    output.write_text(json.dumps(result, indent=2) + '\n', encoding='utf8')
    print(json.dumps(dict(instructions=native['steps'], allocatorPrefixInstructions=len(allocator_path),
        stop=native['stop'], failure=native['failure'], actualFlag=flag_source.hex(),
        hostReads=len(memory.reads), hostBytes=memory.requested, sha256=sha(output.read_bytes()))))


if __name__ == '__main__':
    main()
