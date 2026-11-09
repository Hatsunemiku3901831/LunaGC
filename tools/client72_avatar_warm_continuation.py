"""Replay frozen CN72 warm inputs without a client, allocator or success model."""
import argparse
import hashlib
import json
from pathlib import Path
import pickle
import struct
import sys

ROOT = Path(__file__).resolve().parents[1]
if not __package__:
    sys.path.insert(0, str(ROOT))
    __package__ = 'tools'
from . import collect72_avatar_warm_usages as warm
from . import extract_client72 as static
from .extract_client72_literals import Literals72, USAGE_REGISTRATION
from .trace_client72_token import load_emulator

OUT = ROOT / 'local/migration72/d02-parallel-20261109/a-avatar-string'
INVENTORY = ROOT / 'local/migration72/d02-parallel-20261109/initial-inventory.private.json'
CAPTURE = 'local/migration72/d02-resume-20261109/r1-warm-usages/live/warm-live2.private.json'
MONITOR = 'local/migration72/d02-resume-20261109/r2-early-bridge/live2.private.json'
REVIEW = 'local/migration72/d02-resume-20261109/r2-early-bridge/live2-main-review.private.json'
EXIT = 'local/migration72/d02-resume-20261109/r2-early-bridge/live2-post-exit.private.json'
DEEP = 'protocol/cn-7.2/avatar-deep-observations.json'
RESOLVER = 'local/migration72/astra-avatar/rgctx-final-first-native.json'
GADGET = 'local/migration72/d02-resume-20261109/r3-gadget-config-key/first.json'
METADATA_CACHE = 'local/migration72/d02-resume-20261108/r3-equipment/metadata.private.pkl'
PINS = {
    CAPTURE: 'cfea3e67d6795e791c541e2c140db8b3f19cc70d6c571f47e7ce84c3eb5a4204',
    MONITOR: 'd7f1f483ae293069dbd4611837377c4b076674d7fe04cf42c9a1cc1f79488771',
    REVIEW: '12dd94c7b1457a2a15c4041de45c91931afde773eb24e680e313e326ac0ffb1f',
    EXIT: '9115095a3ffefad62b9ef9cbb1f5512610a9e361b8ed0af66b445f943af38a90',
    DEEP: 'fc08a41533290f671693ac1a0a0ec3608ac56c1cd93cc039f2c681f8102a44c7',
    RESOLVER: '9c3adfd698677c4cb83464c2bbc81819862361eff2a84d3dd76f33aa451f133e',
    GADGET: '10085ff137a6d918a43adaa2cf27754e4b5c638c9de12c3119705852e65e5abd',
    METADATA_CACHE: 'df52eb70fe8e70dfecd1059e898d15e288cfa98bcefc78bcaabf753ae150349f',
    'tools/collect72_avatar_warm_usages.py': '667fa5948f1d645feb923f5b166efa7bb1667cb12ad6b8135d2911bffd4e26b7',
    'tools/sample_client72_avatar_warm_usages.py': '3f00e2185bfce87b2726c21dac38ea4d669f5f5ad7c413cea71f17fbc37efaca',
}
PREFERRED = 0x140000000
STACK, RETURN, OBJECTS = 0x33000000000, 0x33000030000, 0x33000040000
AVATAR, LIST, HASHSET = OBJECTS, OBJECTS + 0x400, OBJECTS + 0x500
LIMIT = 2_000_000
MAX_READS, MAX_BYTES = 4096, 65536


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


class ExactBytes:
    """Captured bytes and explicit aliases; mapped page padding is never input."""
    def __init__(self, records):
        self.values, self.origins, self.exact, self.aliases, self.reads = {}, {}, {}, [], []
        self.requested = 0
        for row in records:
            address, width = row['address'], row['requested']
            raw = bytes.fromhex(row['bytesHex'])
            if (type(address) is not int or not 0 < address <= warm.runtime.U64 or
                    not 0 < width <= 208 or address + width > 1 << 64 or
                    row['error'] is not None or row['bytesRead'] != width or len(raw) != width):
                raise ValueError('Failed, short or invalid capture span')
            self.note('source.audit', address, width, raw, None, [row['ordinal']])
            self.add(address, raw, [row['ordinal']])
            self.exact.setdefault((address, width), raw)

    def add(self, address, raw, origins):
        for offset, value in enumerate(raw):
            cell = address + offset
            if cell in self.values and self.values[cell] != value:
                raise ValueError('Contradictory captured overlap')
            self.values[cell] = value
            self.origins.setdefault(cell, set()).update(origins)

    def lookup(self, address, width):
        cells = range(address, address + width)
        if not all(cell in self.values for cell in cells):
            return b'', 'unprovided-captured-byte', []
        origins = set().union(*(self.origins[cell] for cell in cells))
        return bytes(self.values[cell] for cell in cells), None, sorted(origins)

    def note(self, purpose, address, width, raw, error, origins, **extra):
        if not 0 < width <= 208 or len(self.reads) >= MAX_READS or self.requested + width > MAX_BYTES:
            raise ValueError('Cumulative captured-byte read budget')
        self.requested += width
        row = dict(ordinal=len(self.reads) + 1, purpose=purpose, address=hex(address), width=width,
            bytesHex=raw.hex(), error=error, captureOrdinals=origins, **extra)
        self.reads.append(row)
        return row

    def read(self, address, width, purpose='projection'):
        raw, error, origins = self.lookup(address, width)
        self.note(purpose, address, width, raw, error, origins)
        return raw, error

    def root_aliases(self, module):
        for name, rva in warm.ROOTS.items():
            raw, error = self.read(module + rva, 8, 'source.root-alias')
            if error:
                raise ValueError('Unprovided usage root')
            _, _, origins = self.lookup(module + rva, 8)
            self.add(PREFERRED + rva, raw, origins)
            self.aliases.append(dict(name=name, source=hex(module + rva), alias=hex(PREFERRED + rva),
                width=8, bytesHex=raw.hex(), pointerUnchanged=True))

    def spans(self):
        result, raw, start, previous = [], bytearray(), None, None
        for address, value in sorted(self.values.items()):
            if previous is not None and address != previous + 1:
                result.append((start, bytes(raw)))
                start, raw = None, bytearray()
            if start is None:
                start = address
            raw.append(value)
            previous = address
        if start is not None:
            result.append((start, bytes(raw)))
        return result


def audit_capture(capture, memory):
    collection = capture['collection']
    if (capture['status'], capture['endIdentityVerified'], collection['budget']['reads'],
            collection['budget']['requestedBytes'], collection['inputConsistency']['status'],
            collection['inputConsistency']['uniqueSpans']) != ('partial', True, 256, 8104, 'repeated-unchanged', 84):
        raise ValueError('Frozen warm capture inventory differs')
    if (collection['enumCaller']['getterMethodInfo'], collection['enumCaller']['constructorMethodInfo'],
            collection['optionalString']['failure']) != (0, 0, 'stringGetter:usage-null'):
        raise ValueError('Frozen real partial branches differ')
    if sum(collection['candidateInputs'].values()) != 7 or capture['sourceSHA256'] != PINS['tools/sample_client72_avatar_warm_usages.py']:
        raise ValueError('Capture source/candidates differ')
    rows, cursor = capture['backendRecords'], 0
    def read(address, width):
        nonlocal cursor
        row = rows[cursor]
        cursor += 1
        if (address, width) != (row['address'], row['requested']):
            raise ValueError('Frozen temporal read order differs')
        return memory.read(address, width)
    ticks = [collection['collectionBeginUTC']]
    # Collector timestamps enclose the backend's own timestamps. One recorded
    # outer end is 0.5ms later; preserve each clock instead of conflating them.
    for row in collection['records']:
        ticks.extend((row['beginUTC'], row['endUTC']))
    ticks.append(collection['collectionEndUTC'])
    clock = iter(ticks)
    projected = warm.collect(read, collection['moduleBase'], now=lambda: next(clock))
    if projected != collection or cursor != len(rows) or next(clock, None) is not None:
        raise ValueError('Frozen full collector/time projection differs')
    return dict(fullTemporalProjectionEqual=True, rawRequests=cursor, rawBytes=8104,
        finalExactSpans=84, sourceCaptureSHA256=PINS[CAPTURE], naturalCallerObserved=False)


class Native:
    def __init__(self, binary, libraries, memory, windows, constants):
        self.U, self.R = load_emulator(libraries)
        self.uc = self.U.Uc(self.U.UC_ARCH_X86, self.U.UC_MODE_64)
        self.memory, self.windows, self.constants = memory, windows, constants
        self.path, self.reads, self.writes, self.missing = [], [], [], []
        self.failure, self.stop_reason, self.stops = None, None, set()
        self.pages = set()
        self.map(STACK, 0x20000)
        self.map(RETURN, 1)
        self.map(OBJECTS, 0x1000)
        pe = static.PE(binary)
        for address, end, _ in windows:
            self.map(address, end - address)
            self.uc.mem_write(address, binary[pe.offset(address):pe.offset(address) + end - address])
        for address, raw in memory.spans() + constants:
            self.map(address, len(raw))
            self.uc.mem_write(address, raw)
        self.hooks = [self.uc.hook_add(self.U.UC_HOOK_CODE, self.code),
            self.uc.hook_add(self.U.UC_HOOK_MEM_READ, self.read),
            self.uc.hook_add(self.U.UC_HOOK_MEM_WRITE, self.write),
            self.uc.hook_add(self.U.UC_HOOK_MEM_INVALID, self.invalid)]

    def map(self, address, width):
        for page in range(address & ~4095, (address + width + 4095) & ~4095, 4096):
            if page not in self.pages:
                self.uc.mem_map(page, 4096)
                self.pages.add(page)

    def reg(self, name):
        return self.uc.reg_read(getattr(self.R, 'UC_X86_REG_' + name.upper()))

    def code(self, uc, address, width, data):
        if address in self.stops:
            self.stop_reason = 'declared-fragment-boundary'
            uc.emu_stop()
            return
        if not any(start <= address and address + width <= end for start, end, _ in self.windows):
            self.failure = 'unreviewed-code-edge'
            uc.emu_stop()
            return
        self.path.append(address)

    def read(self, uc, access, address, width, value, data):
        if STACK <= address and address + width <= STACK + 0x20000:
            return
        if OBJECTS <= address and address + width <= OBJECTS + 0x1000:
            self.reads.append(dict(kind='controlled-storage', address=hex(address), width=width, reader=hex(self.reg('rip'))))
            return
        for start, raw in self.constants:
            if start <= address and address + width <= start + len(raw):
                actual = bytes(uc.mem_read(address, width))
                expected = raw[address-start:address-start+width]
                if actual != expected:
                    self.failure = 'file-backed-constant-drift'
                    uc.emu_stop()
                self.reads.append(dict(kind='fixed-PE-constant', address=hex(address), width=width,
                    reader=hex(self.reg('rip')), bytesHex=actual.hex()))
                return
        expected, error, origins = self.memory.lookup(address, width)
        actual = bytes(uc.mem_read(address, width)) if error is None else b''
        if error is None and actual != expected:
            error = 'emulator-source-drift'
        row = self.memory.note('native.read', address, width, actual, error, origins, reader=hex(self.reg('rip')))
        self.reads.append(dict(kind='captured', readOrdinal=row['ordinal']))
        if error:
            self.failure = error
            self.missing.append(dict(address=hex(address), width=width, reader=hex(self.reg('rip'))))
            uc.emu_stop()

    def write(self, uc, access, address, width, value, data):
        if STACK <= address and address + width <= STACK + 0x20000:
            return
        row = dict(address=hex(address), width=width, writer=hex(self.reg('rip')),
            value=hex(value & ((1 << (width * 8)) - 1)))
        self.writes.append(row)
        if not OBJECTS <= address or address + width > OBJECTS + 0x1000:
            self.failure = 'write-outside-controlled-storage'
            uc.emu_stop()

    def invalid(self, uc, access, address, width, value, data):
        fetch = access in (self.U.UC_MEM_FETCH_UNMAPPED, self.U.UC_MEM_FETCH_PROT)
        self.failure = 'unreviewed-code-edge' if fetch else 'unprovided-captured-byte'
        self.missing.append(dict(address=hex(address), width=width, reader=hex(self.reg('rip')), fetch=fetch))
        return False

    def run(self, name, entry, registers, *, stops=(), fill=0):
        self.uc.mem_write(OBJECTS, bytes([fill]) * 0x1000)
        self.uc.mem_write(LIST, struct.pack('<Q', self.collection['listU32']['pointer']))
        klass = self.collection['hashSetEnum' if 'enum' in name else 'hashSetU32']['pointer']
        self.uc.mem_write(HASHSET, struct.pack('<Q', klass))
        self.stops = set(stops)
        for address in stops:
            self.map(address, 1)
        sp = STACK + 0x10008
        self.uc.mem_write(sp, struct.pack('<Q', RETURN))
        for key, value in dict(rsp=sp, **registers).items():
            self.uc.reg_write(getattr(self.R, 'UC_X86_REG_' + key.upper()), value)
        error = None
        try:
            self.uc.emu_start(entry, RETURN, count=LIMIT)
        except self.U.UcError as failure:
            error = str(failure)
        if len(self.path) >= LIMIT and self.failure is None:
            self.failure = 'instruction-budget'
        returned = self.reg('rip') == RETURN and self.failure is None and error is None
        verified = True
        protected = list(self.memory.exact.items()) + [((int(row['alias'],16),8),bytes.fromhex(row['bytesHex']))
            for row in self.memory.aliases]
        for (address, width), expected in protected:
            raw = bytes(self.uc.mem_read(address, width))
            _, _, origins = self.memory.lookup(address, width)
            failure = None if raw == expected else 'input-readback-drift'
            self.memory.note('native.final-readback', address, width, raw, failure, origins)
            verified = verified and failure is None
        result = dict(name=name, entry=hex(entry), initialRegisters={k:hex(v) for k,v in registers.items()},
            returned=returned, stop=hex(self.reg('rip')), stopReason=self.stop_reason, failure=self.failure,
            emulatorError=error, steps=len(self.path), perCallLimit=LIMIT,
            registers={k:hex(self.reg(k)) for k in ('rax','rcx','rdx','r8','rdi','rsi','r14','rsp')},
            executedAddresses=[hex(a) for a in self.path],
            pathSHA256=sha(b''.join(struct.pack('<Q', a) for a in self.path)),
            reads=self.reads, writes=self.writes, missing=self.missing, capturedReadbackEqual=verified,
            storageReadout=dict(avatar=bytes(self.uc.mem_read(AVATAR, 0x300)).hex(),
                list=bytes(self.uc.mem_read(LIST, 32)).hex(), hashSet=bytes(self.uc.mem_read(HASHSET, 56)).hex()),
            controlledStorageOnly=True, allocationExecuted=False, successModelsSupplied=[],
            capturedPointersChanged=False, naturalCallerObserved=False)
        for hook in self.hooks:
            self.uc.hook_del(hook)
        return result


def known_windows(binary, loaded):
    initial = json.loads((ROOT / warm.INITIALIZER).read_bytes())['declarations']['functions']
    deep, resolver = loaded[DEEP]['functions'], loaded[RESOLVER]['functions']
    selected = [(int(key,16), value) for key,value in initial.items()
        if key in ('0x15183fe60','0x157d97690','0x157d7fe90')]
    selected += [(0x156fbc020, deep['0x156fbc020'])]
    selected += [(int(key,16), resolver[key]) for key in ('0x140524010','0x1404ee6f0')]
    pe = static.PE(binary)
    windows = []
    for start, value in selected:
        end, digest = int(value['end'],16), value['nativeBytesSHA256']
        if sha(binary[pe.offset(start,end-start,executable=True):pe.offset(start)+end-start]) != digest:
            raise ValueError('Frozen native window differs')
        windows.append((start,end,digest))
    return windows


def selected_usage_and_definitions(binary, metadata, loaded):
    pe, lit = static.PE(binary), Literals72(binary, metadata)
    cache = static.number(binary, pe.offset(USAGE_REGISTRATION)+0x10, 'Q')
    targets = {PREFERRED+0x596c848, PREFERRED+0x59ae108}
    group = lit.global_group + 1
    count = ((static.number(metadata, lit.groups_base+group*4)+0x8bff3e05)&0xffffffff) ^ ((group*0x5843eaa0-0x443c8630)&0xffffffff)
    h = pe.offset(static.HEADER_VA)
    spec_base = static.BODY + (static.number(binary,h+0x170)^0x6347382b)
    rows = []
    for i in range(count):
        key = (i*0x57c15286+0x26ddebda)&0xffffffff
        source = ((static.number(metadata,lit.usages_base+i*8)+0xabbdcabf)&0xffffffff)^key^0x2a357d90
        slot = static.number(metadata,lit.usages_base+i*8+4)^key^0x1855aa65
        address = cache+slot*8
        if source>>29 == 6 and address in targets:
            spec = source&0x1fffffff
            raw = metadata[spec_base+spec*12:spec_base+spec*12+12]
            method, context, method_context = struct.unpack('<iii',raw)
            rows.append(dict(usageRow=i,slot=slot,moduleRVA=hex(address-PREFERRED),methodSpec=spec,
                methodDefinition=method,classInstance=context,methodInstance=method_context,
                methodSpecFile=hex(spec_base+spec*12),methodSpecHex=raw.hex(),methodSpecSHA256=sha(raw)))
    if {int(row['moduleRVA'],16) for row in rows} != {0x596c848,0x59ae108}:
        raise ValueError('Selected concrete method usages differ')
    ranges = loaded[RESOLVER]['tables']
    owner = next(row for row in loaded[RESOLVER]['selected'] if row['owner']==2147)
    definitions = []
    for slot in (0,1):
        offset = int(ranges['definitionBase'],16)+(owner['definitionIndex']+slot)*4
        raw = metadata[offset:offset+4]
        word = int.from_bytes(raw,'little')
        spec = word&0x3ffffff
        specraw = metadata[spec_base+spec*12:spec_base+spec*12+12]
        definitions.append(dict(slot=slot,metadataOffset=hex(offset),bytesHex=raw.hex(),word=hex(word),
            kind=(word>>26)&31,methodSpec=spec,methodSpecHex=specraw.hex(),
            methodDefinition=struct.unpack_from('<i',specraw)[0]))
    records, _ = pickle.loads((ROOT/METADATA_CACHE).read_bytes())
    dictionary = next(record for record in records if record['index']==1222)
    methods = [method for method in dictionary['methods'] if method['index'] in (10194,10200)]
    if [(m['index'],m['name']) for m in methods] != [(10194,'FindEntry'),(10200,'TryGetValue')]:
        raise ValueError('Fixed Dictionary methods differ')
    range_base = int(ranges['classRangeBase'],16)
    packed = dict(struct.iter_unpack('<II',metadata[range_base:range_base+ranges['classRangeBytes']]))[1222]
    dictionary_slots = []
    for slot in (2,13):
        offset = int(ranges['definitionBase'],16)+((packed&0xffffff)+slot)*4
        word = static.number(metadata,offset)
        spec = word&0x3ffffff
        raw = metadata[spec_base+spec*12:spec_base+spec*12+12]
        method,context,method_context = struct.unpack('<iii',raw)
        dictionary_slots.append(dict(slot=slot,metadataOffset=hex(offset),word=hex(word),kind=(word>>26)&31,
            methodSpec=spec,methodDefinition=method,classInstance=context,methodInstance=method_context,
            methodSpecHex=raw.hex()))
    if [(x['slot'],x['methodDefinition']) for x in dictionary_slots] != [(2,10288),(13,10194)]:
        raise ValueError('Fixed Dictionary lazy definitions differ')
    gadget = loaded[GADGET]['staticContract']
    find = next(w for w in gadget['newWindows'] if w['start']=='0x158b48be0')
    caller = next(w for w in gadget['reusedCalleesNotNew'] if w['start']=='0x14aa9b8a0')
    static_windows = []
    for window in (caller,find):
        start,end = int(window['start'],16),int(window['end'],16)
        raw = binary[pe.offset(start,end-start,executable=True):pe.offset(start)+end-start]
        if sha(raw) != window['sha256']:
            raise ValueError('Frozen String carrier source window differs')
        static_windows.append(dict(start=hex(start),end=hex(end),sha256=sha(raw),originalCPUExecuted=False))
    producer = loaded[RESOLVER]['functions']['0x14051c9d0']
    start,end = 0x14051c9d0,int(producer['end'],16)
    raw = binary[pe.offset(start,end-start,executable=True):pe.offset(start)+end-start]
    if sha(raw) != producer['nativeBytesSHA256']:
        raise ValueError('Frozen class RGCTX producer differs')
    static_windows.append(dict(start=hex(start),end=hex(end),sha256=sha(raw),originalCPUExecuted=False))
    chain = [i for i in caller['instructions'] if 0x14aa9b8e1<=int(i['address'],16)<=0x14aa9b902]
    chain += [i for i in find['instructions'] if 0x158b48cf6<=int(i['address'],16)<=0x158b48d12 or
        0x158b48faa<=int(i['address'],16)<=0x158b48fc3]
    return dict(concreteUsageRows=rows,hashSetDefinition2147Slots=definitions,
        dictionaryDefinition=1222,dictionaryMethods=methods,dictionaryDefinitionSlots=dictionary_slots,
        originalStringCarrierChain=chain,reusedStaticWindows=static_windows,originalCarrierCPUExecuted=False,
        runtimeDefinitionPointerNotCaptured=True,ordinary10294NotSubstituted=True)


def string_contract(declared):
    nodes = [
        ('tryGetUsage','B+59ae108',8),('tryGetMI','actual usage pointer',56),
        ('dictionaryOwner','MI+0',208),('dictionaryGenericClass','owner+68',32),
        ('dictionaryInst','GenericClass+8',16),('dictionaryArguments','Inst+8',16),
        ('stringArgument','argument[0]',16),('u64Argument','argument[1]',16),
        ('findEntrySlot','[owner+30]+78',8),('findEntryMI','actual slot13 pointer',56),
        ('stringGetterSlot','[owner+30]+20',8),
    ]
    return dict(task='R3-STRING-CONSUMER-INPUT-v2-proposed',status='awaiting-mainline-freeze',
        previousContractSHA256=warm.PINS[warm.STRING],previousContractChanged=False,
        exactRoot=dict(moduleRVA='0x59ae108',width=8,usageRow=740611,slot=33825,
            methodSpec=32569,methodDefinition=10200,classInstance=3744,ownerDefinition=1222),
        originalConsumer='14aa9b8a0 -> owner1222.rgctx slot13/FindEntry10194 -> 158b48be0 owner.rgctx slot2/get_Default10288 -> 15746c870',
        nodes=[dict(name=n,addressSource=a,width=w) for n,a,w in nodes],
        ownerGuard='Dictionary1222; count2; String14/byref0 then UInt64 kind11/byref0; actual GC methodInst0/backref. MI indices10200 and10194 share actual owner. Serialized instance3744 is not a runtime pointer.',
        getterSuccessor='Only actual nonzero slot2 MI10288 enters the unchanged previous String graph at its node2; validate actual String1234 context and finite Default/Equals owners exactly as before.',
        conditionalLazyDiagnostic=dict(nodes=[dict(name='dictionaryRGCTXDataPrefix',addressSource='owner+30 pointer',width=16),
            dict(name='slot2Definition',addressSource='[prefix+8]+2*4',width=4),
            dict(name='slot13Definition',addressSource='[prefix+8]+13*4',width=4)],
            expectedDefinitions=declared['dictionaryDefinitionSlots'],
            condition='If slot13 or slot2 is zero, capture this prefix and the two exact words, then stop; do not call resolver, set flags or publish slots.',
            reads=3,bytes=24),
        budgets=dict(carrierFirstPassReads=11,carrierFirstPassBytes=440,lazyDiagnosticBranchReads=14,
            lazyDiagnosticBranchBytes=464,fullWarmSuccessorFirstPassReads=27,fullWarmSuccessorFirstPassBytes=1352,
            fullExactRepeatMaxReads=54,fullExactRepeatMaxBytes=2704,maxSingleRead=208,
            oldR1MainUpperReads=42,oldR1MainUpperBytes=2296,
            combinedWithOldR1MainReads=69,combinedWithOldR1MainBytes=3648,
            fitsOld64NewReadCap=False,requiresSeparateApprovedCollectorBudget=True),
        timing='Read after natural TryGetValue/FindEntry metadata initialization, or at a new login-page epoch to diagnose null/zero without assuming initialization. If still null, record partial; elapsed time alone is not a release condition.',
        actualProducer='Class RGCTX allocation/zeroing at14051d8ff..14051d925; slot lazy resolution via140524010 ->1404ee6f0. Dictionary slot13 is requested by14aa9b9d8; slot2 by158b48faa.',
        primaryClarification='B+596c848 is a separate concrete get_Default<String> usage. Its zero value is not the FindEntry carrier value and does not prove absent String canonical/default/cache.',
        firstMissingInFrozenCapture=dict(moduleRVA='0x59ae108',width=8),
        unchangedEqualsABI='RCX=actual Default, RDX=entry key, R8=query key, R9=actual companion MI; function and MI+8 need not match.',
        fallbackSparseContractUnchanged=True,sparseProbeAutoFallback=False,nativeCalls=0,remoteCalls=0,
        sourcePinsUnchanged=True,completeStringComparer=False,gadgetConfigurationApproved=False,abilityApproved=False,d02Complete=False)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--python-libs', type=Path, default=ROOT/'local/migration72/python')
    args = parser.parse_args()
    output = args.output.resolve()
    if not output.is_relative_to(OUT.resolve()) or output.exists() or output.with_suffix('.source.py').exists():
        parser.error('New output within the assigned directory required')
    loaded = {}
    for path, expected in PINS.items():
        raw = (ROOT/path).read_bytes()
        if sha(raw) != expected:
            raise ValueError('Frozen source differs: '+path)
        if path.endswith('.json'):
            loaded[path] = json.loads(raw)
    client = Path(json.loads(INVENTORY.read_bytes())['gamePath'])
    profile = json.loads((ROOT/'protocol/cn-7.2/profile.json').read_bytes())
    if {spec['path']:spec['sha256'] for spec in profile['files'].values()} != warm.cache.prior.frozen.FIVE_INPUTS:
        raise ValueError('Fixed five-stock profile differs')
    inputs = {}
    for spec in profile['files'].values():
        raw = static.read_verified(client/spec['path'],spec['sha256'])
        if len(raw) != spec['size']:
            raise ValueError('Fixed stock size differs')
        inputs[spec['path']] = raw
    binary, metadata, startup = (inputs[name] for name in static.HASHES)
    sources = warm.verify_sources(binary,metadata,startup)
    capture = loaded[CAPTURE]
    memory = ExactBytes(capture['collection']['records'])
    audit = audit_capture(capture,memory)
    memory.root_aliases(capture['collection']['moduleBase'])
    windows = known_windows(binary,loaded)
    pe = static.PE(binary)
    constant_address = 0x1421e9d70
    constants = [(constant_address,binary[pe.offset(constant_address,16):pe.offset(constant_address)+16])]
    collection = capture['collection']
    calls = [
        ('avatar-entry-before-managed-allocator',0x15183fe60,dict(rcx=AVATAR),(0x1405336c0,),0),
        ('list-inline-real-empty-pointer',0x15183fe92,dict(rax=LIST,r14=AVATAR),(0x15183fea1,),0xa5),
        ('u32-hashset-fresh',0x157d97690,dict(rcx=HASHSET,rdx=0),(),0),
        ('u32-hashset-overwrite-nonzero',0x157d97690,dict(rcx=HASHSET,rdx=0),(),0xa5),
        ('u32-hashset-null-this',0x157d97690,dict(rcx=0,rdx=0),(0x1404c2be0,),0),
        ('enum-warm-getter-current-epoch',0x157d7fe90,dict(rcx=collection['enumWarm']['dependency']['methodInfo']),(),0),
        ('enum-zero-slots-original-wrapper',0x156fbc020,dict(rcx=HASHSET,rdx=collection['enumCaller']['methodInfo']),(),0),
        ('avatar-enum-lazy-original-fragment',0x15183fed0,dict(rdi=HASHSET,r14=AVATAR),(),0),
        ('enum-lazy-inner-slot0',0x1404ee6f0,dict(rcx=collection['enumCaller']['owner']['rgctx'],
            rdx=collection['enumCaller']['owner']['genericClass']+8,r8=0),(),0),
        ('enum-lazy-inner-slot1',0x1404ee6f0,dict(rcx=collection['enumCaller']['owner']['rgctx'],
            rdx=collection['enumCaller']['owner']['genericClass']+8,r8=1),(),0),
    ]
    native = []
    for name,entry,registers,stops,fill in calls:
        engine = Native(binary,args.python_libs,memory,windows,constants)
        engine.collection = collection
        native.append(engine.run(name,entry,registers,stops=stops,fill=fill))
    declared = selected_usage_and_definitions(binary,metadata,loaded)
    string = string_contract(declared)
    generic_roots = collection['cacheCollector']['roots']['genericInst']
    string_hash = 15*warm.cache.C
    block = generic_roots[1]+((string_hash&generic_roots[0])>>6)*32
    sparse, sparse_error = memory.read(block,24,'string.sparse-first-block-availability-only')
    result = dict(task='F-03d3f4d2/R1-WARM-CONTINUATION',sourceSHA256=sha(Path(__file__).read_bytes()),
        fixedSourcePins={**sources['sourcePins'],**PINS},fiveInputSHA256={x['path']:x['sha256'] for x in profile['files'].values()},
        inputAudit=audit,knownWindows=[dict(start=hex(a),end=hex(z),sha256=h) for a,z,h in windows],
        newNativeWindows=0,staticConstants=[dict(address=hex(a),width=len(b),bytesHex=b.hex(),sha256=sha(b)) for a,b in constants],
        native=native,selectedStaticSources=declared,stringConsumerInputContract=string,rootAliases=memory.aliases,
        stringSparseAvailability=dict(queryHash=hex(string_hash),probe=0,slot=string_hash&generic_roots[0],
            block=hex(block),width=24,bytesHex=sparse.hex(),failure=sparse_error,
            actualLookupExecuted=False,cacheMissProved=False,liveProbeStarted=False),
        hostBudget=dict(reads=len(memory.reads),requestedBytes=memory.requested,maxReads=MAX_READS,maxBytes=MAX_BYTES,
            records=memory.reads,scope='Capture audit, original collector projection, aliases, native exact reads/final readbacks and one captured-availability check'),
        cpuBudget=dict(calls=len(native),maxCalls=10,instructions=sum(x['steps'] for x in native),
            perCall=LIMIT,maxInstructions=10*LIMIT),
        rawCapturedSourceUnchanged=all(x['capturedReadbackEqual'] for x in native),
        managedAllocatorExecuted=False,defaultOriginalWriteExecuted=False,completeAvatarConstructor=False,
        completeColdClassInitialization=False,completeAvatarHandler=False,g2Verified=False,d02Complete=False,
        stockDiskIdentityRequired=True,clientOrDebuggerStarted=False,remoteCallsExecuted=False,
        successModelsSupplied=[],priorReportsChanged=False)
    output.parent.mkdir(parents=True,exist_ok=True)
    output.with_suffix('.source.py').write_bytes(Path(__file__).read_bytes())
    output.write_text(json.dumps(result,indent=2)+'\n',encoding='utf8')
    print(json.dumps(dict(calls=len(native),instructions=result['cpuBudget']['instructions'],
        returns=[x['name'] for x in native if x['returned']],
        stops=[dict(name=x['name'],stop=x['stop'],failure=x['failure']) for x in native if not x['returned']],
        hostReads=len(memory.reads),hostBytes=memory.requested,sha256=sha(output.read_bytes()))))


if __name__ == '__main__':
    main()
