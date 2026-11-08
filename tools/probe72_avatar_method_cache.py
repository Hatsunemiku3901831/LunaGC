"""Fixed-CN7.2 method-cache contract and offline native controls; no live backend."""
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
from . import client72_avatar_fresh_interfaces as prior

RAW = ROOT / 'local/migration72/d02-resume-20261109/r1-method-cache'
PRIOR_REPORT = 'local/migration72/d02-resume-20261108/r1-fresh-interfaces/main-third.json'
PRIOR_SHA = 'd89df39ba1eb67f58add0bd276ad4ab057c21f5fcfbc2b8739004d31d3e9e970'
PINS = {
    'tools/client72_avatar_fresh_interfaces.py': '79272fc90238e0c5e5de237b4cec4e817fa3bdef14330375edd31bbcc7c6dd89',
    'tools/client72_avatar_fresh_ancestor.py': '3715861c8c6570a37d69a5de7967633a563671eba2b185e3e77759d4142c548c',
    PRIOR_REPORT: PRIOR_SHA,
    'local/migration72/d02-resume-20261108/r1-fresh-interfaces/next-runtime-contract.private.json':
        'c7b6b89421149b7ab533f97e3b8d4fa53297ad360229c5e3e7fb4203541e83f3',
}
# The first and final three functions are reused frozen windows. Only the
# equality and two miss-side functions are newly reviewed in this card.
WINDOWS = {
    0x1404eea50: (0x1404efc2e, 'b92006506acefc36c21a50d0ae69e021ee7e249e88ded04f5267d0e18e3c70c2'),
    0x1404f0640: (0x1404f0732, 'a732127238b3ae56a752335f201297dd9cde672c3d79a06fdf6e86047fb9f74f'),
    0x14052d590: (0x14052dc89, 'c8067fb7db51dbbf8797f88f94ee7381b75810d9abd55be2ce0d899403f566b5'),
    0x140526fb0: (0x1405270a2, '62df1ca1bb7c777bfa056102f32302fdeb197ccec09bee3570b74348519b64e5'),
    0x14051fb70: (0x14052017f, 'e938268a84c1cde8f26336d7c6293069c1d27f3974c24fc9bf79fed5041a332b'),
    0x1404f0740: (0x1404f081d, '1284709e440ff94b4541a1f3f8261edbae44f843aa5e5f53afaa0d14d877ce97'),
    0x1404f0820: (0x1404f09e0, 'cf72db3eb508e80f0ef5dd462b7cfead910bf0013b5a83376eba0651a0363ee4'),
}
NEW_WINDOWS = (0x1404f0640, 0x14052d590, 0x140526fb0)
U64 = (1 << 64) - 1
C = 0x1cfaa2db
INDICES = tuple(range(10287, 10295))
TARGET_TYPE = struct.pack('<QII', 20060, 17 << 16, 0)
ROOTS = {'genericInst': (0x5bc8c18, 0x5bc8c38, 0x5bc8c40),
         'method': (0x5bc7290, 0x5bc72b0, 0x5bc72b8)}
MAX_PROBES = 32
# A read count is a requested backend call, including errors and partial reads.
# 2 tables * (initial/final 3 roots) + 32*5 + 8*32*6 + 8 values.
FIRST_PASS_READS = 12 + MAX_PROBES * 5 + len(INDICES) * MAX_PROBES * 6 + len(INDICES)
FIRST_PASS_BYTES = 96 + MAX_PROBES * 72 + len(INDICES) * MAX_PROBES * 104 + len(INDICES) * 56
MAX_READS = FIRST_PASS_READS * 2
MAX_BYTES = FIRST_PASS_BYTES * 2
MAX_SINGLE = 56
LIMIT = 2_000_000


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


def rol(value, shift):
    return ((value << shift) | (value >> (64 - shift))) & U64


def pointer_hash(value):
    value = rol(value * 0xc2b2ae3d27d4eb4f & U64, 31) * 0x9e3779b185ebca87 & U64
    value = rol(value ^ 0x27d4eb2f165667cd, 27) * 0x9e3779b185ebca87 & U64
    value = (value + 0x85ebca77c2b2ae63) & U64
    value = (value ^ (value >> 33)) * 0xc2b2ae3d27d4eb4f & U64
    value = (value ^ (value >> 29)) * 0x165667b19e3779f9 & U64
    return value ^ (value >> 32)


def method_hash(index, class_inst, method_inst):
    signed = index if index < 0x80000000 else index - (1 << 32)
    return ((pointer_hash(class_inst) + signed) * C + pointer_hash(method_inst)) & U64


def enum_instance_hash():
    # Original14051fbb8/1404f0740: count1 seed, kind17/byref0/data20060.
    return (C + (17 * C) * C + 20060) & U64


def same_enum_type(raw):
    data, bits = struct.unpack_from('<QI', raw)
    return (bits >> 16) & 255 == 17 and not bits & 0x40000000 and data & 0xffffffff == 20060


class ReadStop(Exception):
    pass


class Ledger:
    """Pure, exact-span collector. The caller owns any real process handle."""
    def __init__(self, read):
        self.read = read
        self.records = []
        self.requested = 0

    def get(self, label, address, width):
        if len(self.records) >= MAX_READS or self.requested + width > MAX_BYTES or not 0 < width <= MAX_SINGLE:
            raise ReadStop('collector-budget')
        self.requested += width
        if not 0 < address <= U64 or address + width > 1 << 64:
            raw, error = b'', 'address-outside-uint64'
        else:
            try:
                raw, error = self.read(address, width)
            except OSError as failure:
                raw, error = b'', {'winerror': getattr(failure, 'winerror', failure.errno)}
        complete = error is None and len(raw) == width
        self.records.append(dict(name=label, address=address, requested=width,
            bytesRead=len(raw), bytesHex=raw[:width].hex(),
            error=error if error is not None else (None if complete else 'partial-read')))
        if not complete:
            raise ReadStop(label + ':unreadable')
        return raw

    def qword(self, label, address):
        return int.from_bytes(self.get(label, address, 8), 'little')


def roots(ledger, module, table, phase):
    values = tuple(ledger.qword(table + '.' + phase + '.' + field, module + rva)
        for field, rva in zip(('mask', 'blocks', 'probeLimit'), ROOTS[table]))
    # Zero mask is a valid one-slot native table. Null blocks are not a miss.
    if values[0] & (values[0] + 1):
        raise ReadStop(table + ':mask-not-capacity-minus-one')
    if not values[1]:
        raise ReadStop(table + ':blocks-null')
    return values


def enum_instance(ledger, pointer, label):
    raw = ledger.get(label + '.header', pointer, 16)
    count, array = struct.unpack('<I4xQ', raw)
    if count != 1:
        return False
    if not array:
        raise ReadStop(label + ':argument-array-null')
    descriptor = ledger.qword(label + '.argument0', array)
    if not descriptor:
        raise ReadStop(label + ':type-descriptor-null')
    return same_enum_type(ledger.get(label + '.type0', descriptor, 16))


def lookup(ledger, table, state, query_hash, index=None):
    mask, blocks, probe_limit = state
    slot = query_hash & mask
    path = []
    for probe in range(MAX_PROBES):
        label = table + ('' if index is None else str(index)) + '.p' + str(probe)
        block_address = blocks + (slot >> 6) * 32
        packed, occupied, tombstone = struct.unpack('<QQQ', ledger.get(label + '.block', block_address, 24))
        bit = 1 << (slot & 63)
        event = dict(probe=probe, slot=slot, block=block_address,
                     occupied=bool(occupied & bit), tombstone=bool(tombstone & bit))
        path.append(event)
        if occupied & bit:
            if not packed:
                raise ReadStop(label + ':occupied-packed-null')
            rank = (occupied & (bit - 1)).bit_count()
            entry_address = packed + rank * (8 if table == 'genericInst' else 16)
            event.update(rank=rank, entry=entry_address)
            if table == 'genericInst':
                pointer = ledger.qword(label + '.entry', entry_address)
                if not pointer:
                    raise ReadStop(label + ':occupied-instance-null')
                if enum_instance(ledger, pointer, label + '.candidate'):
                    return dict(status='hit', pointer=pointer, path=path)
            else:
                descriptor, value = struct.unpack('<QQ', ledger.get(label + '.entry', entry_address, 16))
                if not descriptor:
                    raise ReadStop(label + ':occupied-key-null')
                raw = ledger.get(label + '.key', descriptor, 24)
                candidate_index, class_inst, method_inst = struct.unpack('<I4xQQ', raw)
                event.update(descriptor=descriptor, methodInfo=value, indexMatches=candidate_index == index)
                # Native1404f0653..0695 rejects null mismatch before pointees.
                if candidate_index == index and class_inst and not method_inst:
                    if enum_instance(ledger, class_inst, label + '.context'):
                        return dict(status='hit' if value else 'hit-null', pointer=value, path=path)
        elif not tombstone & bit or probe >= probe_limit:
            return dict(status='miss', pointer=None, path=path,
                        nativeStop='0x1404eec91' if table == 'method' else '0x14051fd12')
        # Occupied collisions always advance, even when probe >= probeLimit.
        slot = (slot + probe + 1) & mask
    return dict(status='probe-budget', pointer=None, path=path)


def collect(read, module):
    """Collect one semantic enum context and eight selected method lookups.

    No module discovery, OpenProcess, retry, writes, function calls or launch.
    This is a sampled graph, not an atomic execution or a writer-safe snapshot.
    """
    ledger = Ledger(read)
    generic = method = canonical = None
    methods = []
    failure = None
    initial = {}
    consistency = dict(status='not-checked', uniqueSpans=0, firstPassReads=0)
    try:
        generic = roots(ledger, module, 'genericInst', 'initial')
        initial['genericInst'] = generic
        canonical = lookup(ledger, 'genericInst', generic, enum_instance_hash())
        if canonical['status'] != 'hit':
            raise ReadStop('canonical-context:' + canonical['status'])
        method = roots(ledger, module, 'method', 'initial')
        initial['method'] = method
        for index in INDICES:
            result = lookup(ledger, 'method', method, method_hash(index, canonical['pointer'], 0), index)
            result.update(index=index, classInstance=canonical['pointer'], methodInstance=0)
            methods.append(result)
            if result['status'] == 'hit':
                raw = ledger.get('method' + str(index) + '.value', result['pointer'], 56)
                result.update(methodInfoHex=raw.hex(), returnedIndex=struct.unpack_from('<I', raw, 24)[0])
                if result['returnedIndex'] != index:
                    raise ReadStop('method' + str(index) + ':returned-index-mismatch')
            elif result['status'] not in ('miss',):
                raise ReadStop('method' + str(index) + ':' + result['status'])
    except ReadStop as stop:
        failure = str(stop)
    finally:
        # Root equality detects these root changes only; it proves no atomicity.
        for table, before in initial.items():
            try:
                after = roots(ledger, module, table, 'final')
                if after != before:
                    failure = failure or table + ':roots-changed'
            except ReadStop as stop:
                failure = failure or str(stop)
        consistency['firstPassReads'] = len(ledger.records)
        # A mutable cache can change in place without changing any root. Keep
        # each raw read, reject contradictory overlap, then repeat each exact
        # successful span once. Even all-equal reads do not prove no writer.
        spans, observed = {}, {}
        try:
            for row in list(ledger.records):
                if row['error'] is not None:
                    continue
                address, width, raw = row['address'], row['requested'], bytes.fromhex(row['bytesHex'])
                for offset, value in enumerate(raw):
                    if address+offset in observed and observed[address+offset] != value:
                        raise ReadStop('sample-spans-disagree')
                    observed[address+offset] = value
                spans[(address,width)] = raw
            consistency['uniqueSpans'] = len(spans)
            for ordinal, ((address,width), before) in enumerate(spans.items()):
                after = ledger.get('consistency.'+str(ordinal),address,width)
                if after != before:
                    raise ReadStop('sample-span-changed')
            consistency['status'] = 'repeated-unchanged'
        except ReadStop as stop:
            consistency['status'] = 'failed'
            failure = failure or str(stop)
    return dict(task='R1-METHOD-CACHE-selected-read-only-collector',
        status='completed' if failure is None else 'partial', failure=failure,
        canonicalContext=canonical, methods=methods, roots={k:list(v) for k,v in initial.items()},
        records=ledger.records, inputConsistency=consistency,
        budget=dict(reads=len(ledger.records), requestedBytes=ledger.requested,
            maxReads=MAX_READS, maxBytes=MAX_BYTES, maxSingleRead=MAX_SINGLE, maxProbesPerQuery=MAX_PROBES),
        completedMeans='Repeated sampled candidate graph only, not an observed natural lookup; misses stop before native materialization.',
        candidateGraphOnly=True,naturalLookupObserved=False,
        atomicSnapshot=False, naturalCallerObserved=False, naturalWriterExcluded=False,
        completeColdComparer=False, completeClassInitialization=False, completeAvatarHandler=False,
        defaultComparerWriteExecuted=False, targetProtocolReady=False, d02Complete=False)


def verify_sources(binary, metadata):
    checked = prior.ancestor.prior.verify_sources()
    for path, expected in PINS.items():
        prior.ancestor.prior.read_pinned(path, expected)
        checked[path] = expected
    frozen = json.loads((ROOT / PRIOR_REPORT).read_bytes())
    if json.loads((ROOT / list(PINS)[-1]).read_bytes()) != frozen['firstMissing']:
        raise ValueError('Frozen three-root contract differs from its source report')
    pe = prior.frozen.base.PE(binary)
    optional = struct.unpack_from('<I', binary, 0x3c)[0] + 24
    rva, width = struct.unpack_from('<II', binary, optional + 112 + 3*8)
    offset = pe.offset(pe.base+rva, width)
    extents = {pe.base+a:pe.base+z for a,z,_ in struct.iter_unpack('<III', binary[offset:offset+width])}
    if pe.base != 0x140000000:
        raise ValueError('Fixed preferred image base differs')
    from capstone import Cs, CS_ARCH_X86, CS_MODE_64, CS_OP_MEM, CS_OP_IMM
    md = Cs(CS_ARCH_X86, CS_MODE_64)
    md.detail = True
    windows = []
    for start, (end, expected) in WINDOWS.items():
        offset = pe.offset(start, end-start, executable=True)
        raw = binary[offset:offset+end-start]
        if extents[start] != end or sha(raw) != expected:
            raise ValueError('Precise PE function window differs: ' + hex(start))
        instructions = []
        for ins in md.disasm(raw, start):
            row = dict(address=hex(ins.address), instruction=ins.mnemonic+' '+ins.op_str)
            memories = []
            for op in ins.operands:
                if op.type == CS_OP_MEM:
                    mem = dict(width=op.size, read=bool(op.access & 1), write=bool(op.access & 2))
                    if ins.reg_name(op.mem.base) == 'rip':
                        mem['moduleRVA'] = hex(ins.address+ins.size+op.mem.disp-pe.base)
                    memories.append(mem)
            if memories:
                row['memoryOperands'] = memories
            if ins.mnemonic == 'call' and ins.operands[0].type == CS_OP_IMM:
                row['directCallee'] = hex(ins.operands[0].imm)
            instructions.append(row)
        windows.append(dict(start=hex(start), end=hex(end), width=len(raw), sha256=expected,
            new=start in NEW_WINDOWS, instructions=instructions))
    registration = prior.frozen.METADATA_REGISTRATION
    instance_base = struct.unpack_from('<Q', binary, pe.offset(registration)+0x70)[0]
    instance = instance_base + 5917*16
    inst = binary[pe.offset(instance):pe.offset(instance)+16]
    count, array = struct.unpack('<QQ', inst)
    argument = struct.unpack_from('<Q', binary, pe.offset(array))[0]
    typ = binary[pe.offset(argument):pe.offset(argument)+16]
    if instance != 0x142377940 or count != 1 or typ != TARGET_TYPE:
        raise ValueError('Registered instance5917 target is not exact enum20060')
    original = prior.frozen.audit_snapshot(binary, metadata)
    if original['data']['cold.class20060.header'][16:32] != typ:
        raise ValueError('Independent original captured enum descriptor differs')
    source = dict(registrationRVA=hex(registration-pe.base), instanceIndex=5917,
        registeredInstanceRVA=hex(instance-pe.base), argumentArrayRVA=hex(array-pe.base),
        descriptorRVA=hex(argument-pe.base), descriptorHex=typ.hex(),
        registeredInstanceSHA256=sha(inst), descriptorSHA256=sha(typ),
        originalCaptureSHA256=prior.frozen.RAW_SHA,
        canonicalPointerObserved=False,
        warning='Registered instance5917 under controlled empty intern cache is not the natural canonical pointer. The collector resolves it by content in the fresh natural intern table.')
    return checked, windows, source


class SparseMemory:
    """Owned synthetic bytes; absent bytes stay absent even on mapped pages."""
    def __init__(self):
        self.spans = []

    def add(self, label, address, raw):
        if any(a < address+len(raw) and address < a+len(b) for _,a,b in self.spans):
            raise ValueError('Overlapping owned span: ' + label)
        self.spans.append((label, address, bytes(raw)))

    def read(self, address, width):
        for _, start, raw in self.spans:
            if start <= address and address+width <= start+len(raw):
                return raw[address-start:address-start+width], None
        return b'', 'unprovided-owned-span'


class Native:
    """Executes fixed native fragments; no OS/allocator/success-return models."""
    STACK, RETURN = 0x30000000000, 0x30000030000

    def __init__(self, binary, libraries, memory):
        self.U, self.R = prior.frozen.base.load_emulator(libraries)
        self.uc = self.U.Uc(self.U.UC_ARCH_X86, self.U.UC_MODE_64)
        self.pe = prior.frozen.base.PE(binary)
        self.memory = memory
        pages = set()
        self.code = []
        for start, (end, _) in WINDOWS.items():
            self.code.append((start,end))
            for page in range(start & ~4095, (end+4095) & ~4095, 4096):
                if page not in pages:
                    self.uc.mem_map(page,4096)
                    pages.add(page)
            self.uc.mem_write(start,binary[self.pe.offset(start):self.pe.offset(start)+end-start])
        self.uc.mem_map(self.STACK,0x20000)
        for _, start, raw in memory.spans:
            for page in range(start & ~4095, (start+len(raw)+4095) & ~4095, 4096):
                if page not in pages:
                    self.uc.mem_map(page,4096)
                    pages.add(page)
            self.uc.mem_write(start,raw)
        self.hooks = [self.uc.hook_add(self.U.UC_HOOK_CODE,self.on_code),
                      self.uc.hook_add(self.U.UC_HOOK_MEM_READ,self.on_read),
                      self.uc.hook_add(self.U.UC_HOOK_MEM_WRITE,self.on_write),
                      self.uc.hook_add(self.U.UC_HOOK_MEM_INVALID,self.on_invalid)]

    def reg(self, name):
        return self.uc.reg_read(getattr(self.R,'UC_X86_REG_'+name.upper()))

    def on_code(self, uc, address, size, _):
        self.steps += 1
        self.path.update(struct.pack('<Q',address))
        if address in self.stops:
            self.stop = address
            uc.emu_stop()
        elif not any(a <= address < z for a,z in self.code):
            self.failure = 'unreviewed-native-edge'
            uc.emu_stop()

    def on_read(self, uc, access, address, width, value, _):
        if self.STACK <= address and address+width <= self.STACK+0x20000:
            return
        if any(a <= address and address+width <= z for a,z in self.code):
            return
        raw, error = self.memory.read(address,width)
        self.reads.append(dict(address=hex(address), width=width, reader=hex(self.reg('rip')),
                               bytesHex=raw.hex(), error=error))
        if error:
            self.failure = 'unprovided-owned-span'
            uc.emu_stop()

    def on_write(self, uc, access, address, width, value, _):
        if self.STACK <= address and address+width <= self.STACK+0x20000:
            return
        self.writes.append(dict(address=hex(address),width=width,writer=hex(self.reg('rip'))))
        self.failure = 'unexpected-external-write'
        uc.emu_stop()

    def on_invalid(self, uc, access, address, width, value, _):
        self.failure = 'unprovided-owned-span'
        self.missing.append(dict(address=hex(address),width=width,site=hex(self.reg('rip'))))
        return False

    def run(self, entry, regs, stops):
        self.steps = 0
        self.stop = self.failure = None
        self.reads, self.writes, self.missing = [], [], []
        self.path = hashlib.sha256()
        self.stops = set(stops)
        sp = self.STACK+0x10008
        self.uc.mem_write(sp,struct.pack('<Q',self.RETURN))
        for name, value in dict(rsp=sp,rbp=self.STACK+0x11000,**regs).items():
            self.uc.reg_write(getattr(self.R,'UC_X86_REG_'+name.upper()),value)
        error = None
        try:
            self.uc.emu_start(entry,self.RETURN,count=LIMIT)
        except self.U.UcError as failure:
            error = str(failure)
        if not self.failure and self.steps >= LIMIT:
            self.failure = 'instruction-budget'
        result = dict(entry=hex(entry),stop=hex(self.stop) if self.stop else None,
            failure=self.failure,error=error,steps=self.steps,instructionLimit=LIMIT,
            pathSHA256=self.path.hexdigest(),externalReads=self.reads,externalWrites=self.writes,
            missing=self.missing,regs={n:hex(self.reg(n)) for n in ('rip','rax','rdi','rsi','r14')},
            naturalMemory=False,scope='Owned controls, stopped before lock/allocator/intern/materialization; no continuation of the frozen natural-input gap.')
        for hook in self.hooks:
            self.uc.hook_del(hook)
        return result


def owned_context(memory, base, type_raw=TARGET_TYPE, count=1):
    memory.add('context',base,struct.pack('<I4xQ',count,base+0x100))
    if count == 1:
        memory.add('argument',base+0x100,struct.pack('<Q',base+0x200))
        memory.add('type',base+0x200,type_raw)
    return base


def owned_lookup(variant):
    memory = SparseMemory()
    key, query_context = 0x31000000000, 0x31000010000
    if variant in ('rank63','occupied-cross-block'):
        query_context = next(query_context+i*4096 for i in range(2048)
            if method_hash(INDICES[0],query_context+i*4096,0)&127 == 63)
    candidate_context = query_context
    owned_context(memory,query_context)
    if variant in ('deep-alias','element-mismatch'):
        candidate_context = owned_context(memory,0x31000020000,
            TARGET_TYPE if variant == 'deep-alias' else struct.pack('<QII',20061,17 << 16,0))
    if variant == 'count-mismatch':
        candidate_context = owned_context(memory,0x31000020000,count=0)
    memory.add('query',key,struct.pack('<I4xQQ',INDICES[0],query_context,0))
    mask, blocks, packed = 127, 0x31000100000, 0x31000200000
    for rva, value in zip(ROOTS['method'],(mask,blocks,0 if variant in ('occupied-beyond-limit','tombstone-limit') else 1)):
        if variant != 'missing-root' or rva != ROOTS['method'][0]:
            memory.add('method-root',0x140000000+rva,struct.pack('<Q',value))
    first = method_hash(INDICES[0],query_context,0) & mask
    slots = {}
    if variant in ('empty','tombstone-limit','tombstone-continue'):
        slots[first] = (None,variant != 'empty')
    else:
        slots[first] = ((INDICES[0]+1 if variant in ('occupied-beyond-limit','occupied-cross-block') else INDICES[0],candidate_context),False)
    if variant in ('occupied-beyond-limit','occupied-cross-block','tombstone-continue'):
        slots[(first+1)&mask] = ((INDICES[0],query_context),False)
    if variant in ('element-mismatch','count-mismatch'):
        slots[(first+1)&mask] = (None,False)
    grouped = {}
    for slot, row in slots.items():
        grouped.setdefault(slot >> 6,[]).append((slot,row))
    for block, entries in grouped.items():
        occupied = sum(1 << (slot & 63) for slot,(item,_) in entries if item is not None)
        if variant == 'rank63':
            occupied = U64
        tombstone = sum(1 << (slot & 63) for slot,(_,tomb) in entries if tomb)
        pointer = packed+block*4096
        memory.add('block',blocks+block*32,struct.pack('<QQQ',pointer,occupied,tombstone))
        for rank,(slot,(item,_)) in enumerate(sorted((x for x in entries if x[1][0] is not None))):
            if variant == 'rank63':
                rank = 63
            index, context = item
            descriptor = 0x31000300000+slot*4096
            value = 0 if variant == 'hit-null' else 0x31000400000
            memory.add('entry',pointer+rank*16,struct.pack('<QQ',descriptor,value))
            memory.add('candidate',descriptor,struct.pack('<I4xQQ',index,context,0))
    if variant == 'unprovided-context':
        memory.spans = [row for row in memory.spans if row[0] != 'context']
    return memory,key,query_context


def native_controls(binary,libraries):
    cases = []
    for name in ('deep-alias','occupied-beyond-limit','element-mismatch','count-mismatch',
                 'empty','tombstone-limit','tombstone-continue','hit-null','missing-root','unprovided-context',
                 'rank63','occupied-cross-block','empty-no-intern'):
        memory,key,context = owned_lookup('empty' if name == 'empty-no-intern' else name)
        machine = Native(binary,libraries,memory)
        result = machine.run(0x1404eea9c,dict(rsi=key,rbx=0 if name == 'empty-no-intern' else 1),
                             {0x1404efb1c,0x14052d590,0x140526fb0})
        result['name'] = name
        ledger = Ledger(memory.read)
        try:
            oracle = lookup(ledger,'method',roots(ledger,0x140000000,'method','control'),method_hash(INDICES[0],context,0),INDICES[0])
        except ReadStop as failure:
            oracle = dict(status='partial',failure=str(failure))
        result['collector'] = oracle
        cases.append(result)
    for index,klass,method in ((10287,0x80000001,0), (0xffffffff,0x180000001,0x200000001),
                               (0x80000000,0x100000001,0xfffffffffffffffe), (0,0,0)):
        memory = SparseMemory()
        key = 0x31000000000
        memory.add('query',key,struct.pack('<I4xQQ',index,klass,method))
        machine = Native(binary,libraries,memory)
        result = machine.run(0x1404eea9c,dict(rsi=key,rbx=1),{0x1404eeb80})
        result.update(name='hash-'+str(index)+'-'+str(klass),expectedHash=hex(method_hash(index,klass,method)))
        cases.append(result)
    memory = SparseMemory()
    machine = Native(binary,libraries,memory)
    result = machine.run(0x1404eea93,dict(rsi=0,rbx=1),{0x1404eed7d})
    result['name'] = 'null-descriptor-allocating-branch'
    cases.append(result)
    # Full original context-equality callee, including its separate method list.
    for name, class_counts, method_types, expected in (
            ('both-null',(None,None),(None,None),1),
            ('class-null-mismatch',(None,0),(None,None),0),
            ('zero-count-class',(0,0),(None,None),1),
            ('class-count-mismatch',(1,0),(None,None),0),
            ('method-null-mismatch',(None,None),(None,[TARGET_TYPE]),0),
            ('two-method-elements',(None,None),([TARGET_TYPE,TARGET_TYPE],[TARGET_TYPE,TARGET_TYPE]),1),
            ('second-method-element-mismatch',(None,None),([TARGET_TYPE,TARGET_TYPE],
                [TARGET_TYPE,struct.pack('<QII',20061,17 << 16,0)]),0),
            ('method-byref-mismatch',(None,None),([TARGET_TYPE],
                [struct.pack('<QII',20060,(17 << 16)|0x40000000,0)]),0)):
        memory = SparseMemory()
        arguments = []
        for side in range(2):
            base = 0x32000000000+side*0x10000
            klass = 0 if class_counts[side] is None else owned_context(memory,base,count=class_counts[side])
            types = method_types[side]
            method = 0
            if types is not None:
                method = base+0x1000
                memory.add('method-context',method,struct.pack('<I4xQ',len(types),method+0x100))
                memory.add('method-arguments',method+0x100,
                    b''.join(struct.pack('<Q',method+0x200+i*32) for i in range(len(types))))
                for i,raw in enumerate(types):
                    memory.add('method-type',method+0x200+i*32,raw)
            argument = base+0x2000
            memory.add('context-pair',argument,struct.pack('<QQ',klass,method))
            arguments.append(argument)
        machine = Native(binary,libraries,memory)
        result = machine.run(0x1404f0640,dict(rcx=arguments[0],rdx=arguments[1]),set())
        result.update(name='context-'+name,expectedEqual=expected)
        cases.append(result)
    memory = SparseMemory()
    memory.add('enum',0x31000000000,TARGET_TYPE)
    machine = Native(binary,libraries,memory)
    result = machine.run(0x1404f0740,dict(rcx=0x31000000000),set())
    result.update(name='registered-enum-original-type-hash',
                  expectedTypeHash=hex((enum_instance_hash()-C)&U64))
    cases.append(result)
    return cases


def contract(source):
    return dict(task='R1-METHOD-CACHE',sourceReportSHA256=PRIOR_SHA,
        staticContextSource=source,selectedMethodIndices=list(INDICES),
        canonicalContext=dict(target='one argument, enum20060 kind17/byref0',
            source='original14051fb70 lookup plus1404f0740 hash/1404f0820 comparison',
            roots=[dict(moduleRVA=hex(x),width=8) for x in ROOTS['genericInst']],
            initialHash=hex(enum_instance_hash()),staticRegisteredPointerIsNaturalCanonical=False,
            missStop='Record unresolved canonical miss; do not allocate or guess instance5917.'),
        selectedMethodLookup=dict(roots=[dict(moduleRVA=hex(x),width=8) for x in ROOTS['method']],
            hash='((pointer_hash(actual canonical classInst)+sign_extend32(index))*0x1cfaa2db+pointer_hash(0)) mod2^64',
            slot='hash & mask; then (slot+probe+1)&mask, probe starts0',
            block='blocks+(slot>>6)*32; read24B fields packed/occupied/tombstone',
            rank='popcount(occupied & ((1<<(slot&63))-1))',
            occupied='Read entry16B at packed+rank*16, descriptor24B. Check index then class/method nulls, class count1 and one type descriptor. Do not stop occupied collisions at probeLimit.',
            miss='Only unoccupied and (tombstone clear OR probe>=probeLimit). Stop before1404eec91 materialization branch.',
            hit='Return entry+8 unconditionally; null is a hit-null state. Nonzero value: read exactly56B and check+18 index; no pointees.',
            context='Only target count1/type17/data20060/byref0/methodInstnull; mismatching count/kind/byref/data ends comparison without following arbitrary types.'),
        budgets=dict(maxProbesPerQuery=MAX_PROBES,queries=9,maxReads=MAX_READS,
            maxBytes=MAX_BYTES,maxSingleRead=MAX_SINGLE,
            formula='First pass:12 roots/96B +32*(5reads/72B) +8*32*(6reads/104B) +8*(1read/56B) =1716reads/29472B. Repeat each unique exact successful span once, at most the same again =3432reads/58944B.',
            rootRechecks='Three exact8B cells per table at end, included; no retry or expanded fallback.',
            spanRechecks='Reject contradictory overlapping first-pass bytes; repeat each unique(address,width) successful span once. Drift or short/error => partial. This establishes repeat consistency only.',
            limitSemantics='Probe cap is an acquisition limit. Exceeding it is partial/probe-budget, never native miss.'),
        lifecycle=dict(owner='Main line only, fresh explicit user-ready and current profile/config/process/port inventory.',
            discovery='Frozen90s uninstrumented monitor selects one fresh PID/creation/image/module; never reuse prior PID/base/ready.',
            addressing='Module roots are B+RVA, B from this fresh monitor and revalidated current module. All descendants come only from successful same-collection reads.',
            identity='Before first RPM verify five stock SHA, source pins, precise PE windows, PID+creation, image samefile, module path/base/size and process alive. Use existing read/query/wait backend with0x101010 and close in finally.',
            execution='Only pure collect(read,B). No launch/attach/target function/lock/allocation/write/credential/network action. Main must supply its audited lifecycle wrapper.',
            failure='Null blocks, occupied-null pointer, error/short read, drift, changed roots, returned index mismatch, unsupported/overbudget graph => partial and stop dependent lookup. End-root reads remain bounded.',
            end='Revalidate same creation/image/module/alive, retain partial on exit, close handle. Main handles normal user exit and stock identity audit.',
            concurrency='Root and exact span rechecks detect only observed changes; they do not exclude writers/ABA or establish an atomic graph or actual caller consumption.'),
        afterCaptureReplay=dict(
            prerequisite='Completed collector plus repeated-unchanged spans, current source/five input gates, same collection module identity; preserve raw and verification sidecar.',
            addressBinding='Map successful raw spans at captured addresses; map only module root-cell addresses from capture B+RVA to preferredPE+RVA. Preserve raw pointer values and fresh canonical pointer for the hash. Reject contradictory overlaps and any unprovided byte.',
            entry='1404eea9c with owned24B query(index,actual fresh canonical,0) and copy byte1; original hash, lookup and1404f0640/0820 run with2M cap.',
            stops='Hit before1404efb1c unlock, or miss at14052d590 before intern; no allocator/import/success models. Match read spans, index, path and returned pointer with sampled candidate graph.',
            claim='Source-bound controlled replay of a repeated sampled graph, not observed natural hit/miss, caller execution, class initialization or default write.',
            fullPrefix='Do not combine natural cache roots with old modeled instance5917/hash. A new independent continuous-prefix case must let original14051fb70 consume the fresh intern cache to obtain its fresh canonical pointer, keep the frozen six-prefix case unchanged, and stop on any new context/read/write. This integration is not supplied or claimed by this card.'),
        excludedMissInputs=dict(
            descriptorIntern='14052d590 has its own sparse cache+locks,24B allocation, insertion/resize. Not an empty-cache substitute.',
            ordinaryMethod='140526fb0 reads B+5bc8f20 then[index*8]; null goes through metadata/class/method initialization and writes that slot.',
            materialization='1404eea50 allocates/clears56B, may rehash/copy/free method cache, inserts key/value, then fills MethodInfo and invokes pointer producers. Writer closure is not provided by this lookup sampler.',
            next='Any observed miss needs a separate exact writer-path plan using actual roots/capacity/entries; do not run it with missing input or invented zero cache.'),
        inputSufficientForCompleteAvatar=False,readOnlyCollectorImplemented=True,liveAdapterImplemented=False,
        samplingSpecificationComplete=True,samplingReadyForMainReview=True,samplingReady=False,
        fullMissMaterializationBudgetClaimed=False,naturalSamplingStarted=False,
        completeColdComparer=False,completeClassInitialization=False,defaultComparerWriteExecuted=False,
        completeAvatarHandler=False,targetProtocolReady=False,d02Complete=False)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client',type=Path,required=True)
    parser.add_argument('--output',type=Path,required=True)
    parser.add_argument('--python-libs',type=Path,default=ROOT/'local/migration72/python')
    args = parser.parse_args()
    siblings = [args.output,args.output.with_suffix('.source.py'),args.output.with_suffix('.contract.private.json')]
    if not args.output.resolve().is_relative_to(RAW.resolve()):
        parser.error('Output must stay in r1-method-cache')
    if any(path.exists() for path in siblings):
        parser.error('Prior evidence is immutable; choose a new output')
    args.output.parent.mkdir(parents=True,exist_ok=True)
    siblings[1].write_bytes(Path(__file__).read_bytes())
    binary,metadata,*_ = [prior.frozen.base.read_verified(args.client/name,digest)
        for name,digest in prior.frozen.FIVE_INPUTS.items()]
    prior.frozen.base.load_emulator(args.python_libs)
    pins,windows,source = verify_sources(binary,metadata)
    sampling = contract(source)
    report = dict(task='R1-METHOD-CACHE',sourceSHA256=sha(Path(__file__).read_bytes()),
        inputSHA256=prior.frozen.FIVE_INPUTS,sourcePins=pins,functions=windows,
        staticContextSource=source,nativeControls=native_controls(binary,args.python_libs),
        nextSamplingContract=sampling,priorContinuousStopUnchanged=True,
        suppliedNaturalMethodCacheBytes=0,completeColdComparer=False,completeClassInitialization=False,
        defaultComparerWriteExecuted=False,completeAvatarHandler=False,targetProtocolReady=False,d02Complete=False)
    siblings[0].write_bytes((json.dumps(report,indent=2)+'\n').encode())
    siblings[2].write_bytes((json.dumps(sampling,indent=2)+'\n').encode())
    print(json.dumps(dict(task=report['task'],nativeControls=len(report['nativeControls']),
        maxReads=MAX_READS,maxBytes=MAX_BYTES,samplingSpecificationComplete=True,
        liveAdapterImplemented=False,completeAvatarHandler=False)))


if __name__ == '__main__':
    main()
