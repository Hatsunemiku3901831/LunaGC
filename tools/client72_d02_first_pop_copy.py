"""Execute the approved first allocator/pop/helper on separate frozen bytes only."""
import argparse
import hashlib
import json
from pathlib import Path
import struct
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
if not __package__:
    sys.path.insert(0, str(ROOT))
    __package__ = 'tools'
from .trace_client72_token import load_emulator

OUT = ROOT / 'local/migration72/d02-resume-20261110b/a-first-pop-copy'
GC = 'local/migration72/d02-resume-20261110b/a-gc/'
AB = 'local/migration72/d02-resume-20261110/ab-stock-v2/'
RUN = AB + 'ab-stock-v2-live1-20261110T052505Z-2d3fe291-capture.private.json'
PIN_SHA = 'a0464baced66b58c38c96a526b9e349af9ca9a9adee8bbacc4c30558d0dc01a7'
EXE_SHA = '8b014f64b5dc12fbc476e0c362b053abfdd7a1378cd282e4ad81057621dbd6b5'
LOOKUP_APPROVAL = 'local/migration72/d02-parallel-20261109/r1-allocator-successor/main-reused-lookup-approval.private.json'
LOOKUP_SOURCE = 'local/migration72/d02-active/avatar/handler-closure/first.json'
EXTRA_PINS = {
    LOOKUP_APPROVAL: '03f2942396db30ec17e776288c8f3c2ed29d3c0e32c7387d84c74f24aeb19080',
    LOOKUP_SOURCE: '58c2c4486873fc74d65d825f74aaa715bf56fce8bd77c8e03208500f090d4f67',
}
PREFERRED = 0x140000000
ENTRY, POP, HELPER, POST = 0x1405336c0, 0x140563cd0, 0x141fdf9c0, 0x140525e00
STACK, STACK_SIZE, RETURN = 0x20000000, 0x10000, 0x21000000
LIMIT = 50000
LIBS = ROOT / 'local/migration72/python'
HELPER_PATH = [0x141fdf9c0, 0x141fdf9c3, 0x141fdf9c6, 0x141fdf9cd, 0x141fdf9d0,
    0x141fdf9da, 0x141fdf9de, 0x141fdf9e3, 0x141fdf9e7, 0x141fdfa70,
    0x141fdfa74, 0x141fdfa78, 0x141fdfa7a, 0x141fdfa7e, 0x141fdfa85]
CALLS = {0x14053371c: 0x1405269f0, 0x1405337d8: POP,
    0x140563d85: HELPER, 0x1405337fb: POST}


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


def load_frozen():
    raw = (ROOT / (GC + 'input-pins.private.json')).read_bytes()
    if sha(raw) != PIN_SHA:
        raise ValueError('A 18-source pin manifest differs')
    pins = json.loads(raw)
    loaded = {}
    for path, pin in pins.items():
        content = (ROOT / path).read_bytes()
        if {'bytes': len(content), 'sha256': sha(content)} != pin:
            raise ValueError('Frozen input differs: ' + path)
        loaded[path] = content
    for path, digest in EXTRA_PINS.items():
        content = (ROOT / path).read_bytes()
        if sha(content) != digest:
            raise ValueError('Approved lookup source differs: ' + path)
        loaded[path] = content
    capture = json.loads(loaded[RUN])
    collection = capture['sample']['collection']
    rows = collection['records']
    if len(rows) != 62 or collection['inputConsistency']['status'] != 'repeated-unchanged':
        raise ValueError('Expected original 31+31 stable capture')
    for first, final in zip(rows[:31], rows[31:]):
        keys = ('address', 'requested', 'bytesRead', 'bytesHex', 'error')
        if tuple(first[k] for k in keys) != tuple(final[k] for k in keys):
            raise ValueError('Original final differs')
        if first['error'] is not None or first['bytesRead'] != first['requested']:
            raise ValueError('Incomplete original span')
    a = collection['allocationInputs']
    if (a['observedLengthU64'], a['requestU16'], a['oldFlags'], a['classCA'], a['classC7'],
            a['notifyByte'], a['headBytesHex'][32:]) != (
            32, 32, 8, 98, 1, 0, '01000000000000000000000000000000'):
        raise ValueError('Approved exact sample path differs')
    # Hash the immutable historical source copies, never repin changed current documents.
    manifest_path = AB + 'live1-main-review/source-snapshot-manifest.private.json'
    historical = json.loads(loaded[manifest_path])['sourcePins']
    snapshot = ROOT / (AB + 'live1-main-review/source-snapshot')
    for path, digest in historical.items():
        if sha((snapshot / path).read_bytes()) != digest:
            raise ValueError('Historical source snapshot differs: ' + path)
    return dict(pins=pins, raw=loaded, collection=collection, historicalSources=len(historical))


def extract_approved_lookup():
    resolver = ROOT / 'tools/game_path.ps1'
    command = ['powershell.exe', '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', str(resolver)]
    result = subprocess.run(command, capture_output=True, check=False)
    if result.returncode:
        raise ValueError('Current game-path resolver failed')
    client = Path(result.stdout.decode('utf-8-sig').strip())
    path = client / 'YuanShen.exe'
    binary = path.read_bytes()
    if len(binary) != 450927000 or sha(binary) != EXE_SHA:
        raise ValueError('Current fixed EXE size/SHA differs')
    pe = struct.unpack_from('<I', binary, 0x3c)[0]
    sections = struct.unpack_from('<H', binary, pe + 6)[0]
    optional_size = struct.unpack_from('<H', binary, pe + 20)[0]
    optional = pe + 24
    if struct.unpack_from('<Q', binary, optional + 24)[0] != PREFERRED:
        raise ValueError('Fixed EXE preferred base differs')
    approval = json.loads((ROOT / LOOKUP_APPROVAL).read_bytes())
    windows = []
    for row in approval['approvedReusedNative']:
        start, end = int(row['start'], 16), int(row['end'], 16)
        offset = None
        for i in range(sections):
            s = optional + optional_size + i * 40
            _, rva, size, raw = struct.unpack_from('<IIII', binary, s + 8)
            flags = struct.unpack_from('<I', binary, s + 36)[0]
            if rva <= start - PREFERRED and end - PREFERRED <= rva + size and flags & 0x20000000:
                offset = raw + start - PREFERRED - rva
                break
        if offset is None:
            raise ValueError('Exact approved executable lookup span absent')
        content = binary[offset:offset + end - start]
        if sha(content) != row['sha256'] or len(content) != row['bytes']:
            raise ValueError('Exact approved lookup bytes differ')
        windows.append((start, content, 'approved-old-lookup-code-only'))
    return windows, dict(currentPathResolved=True, resolverSHA256=sha(resolver.read_bytes()),
        resolverArgv=command, resolverExit=0, exePath=str(path), bytes=len(binary), sha256=EXE_SHA,
        twoPreviouslyApprovedWindowsOnly=True)


def code_windows(frozen, lookup):
    raw = frozen['raw']
    return [
        (ENTRY, raw['local/migration72/d02-parallel-20261109/r1-allocator-successor/allocator-full1.private.bin'], 'typed-allocator'),
        (POP, raw['local/migration72/d02-resume-20261110/a-tls/code.bin'], 'first-pop'),
        (HELPER, raw[GC + 'gc-helper.code.bin'], 'clear16'),
        (POST, raw['local/migration72/d02-resume-20261110/a-avatar-postpop-window/code.bin'], 'post-pop'),
    ] + lookup


class FirstPopCopy:
    """Preflight each instruction; hooks audit CPU reads and logical writes separately."""
    def __init__(self, frozen, windows, *, write_enabled=True):
        self.U, self.R = load_emulator(LIBS)
        from capstone import Cs, CS_ARCH_X86, CS_MODE_64, CS_AC_READ, CS_AC_WRITE
        from capstone.x86_const import X86_OP_MEM, X86_OP_IMM
        self.AC_READ, self.AC_WRITE = CS_AC_READ, CS_AC_WRITE
        self.OP_MEM, self.OP_IMM = X86_OP_MEM, X86_OP_IMM
        decoder = Cs(CS_ARCH_X86, CS_MODE_64)
        decoder.detail = True
        self.uc = self.U.Uc(self.U.UC_ARCH_X86, self.U.UC_MODE_64)
        self.collection = frozen['collection']
        self.base = self.collection['moduleBase']
        self.delta = self.base - PREFERRED
        self.uc.reg_write(self.R.UC_X86_REG_GS_BASE, self.collection['teb'])
        self.instructions, self.pages, self.code_ranges, self.source = {}, set(), [], {}
        self.write_enabled = write_enabled
        self.failure, self.rejected, self.pending = None, None, []
        self.path, self.reads, self.writes, self.raw_writes, self.calls, self.returns = [], [], [], [], [], []
        self.frames = []
        self._map(STACK, bytes(STACK_SIZE))
        self._map(RETURN, b'\xcc')
        self.entry_sp = STACK + 0x8008
        self.uc.mem_write(self.entry_sp, struct.pack('<Q', RETURN))
        for start, content, name in windows:
            actual = start + self.delta
            self._map(actual, content)
            self.code_ranges.append((actual, actual + len(content), name))
            rows = list(decoder.disasm(content, actual))
            if b''.join(bytes(x.bytes) for x in rows) != content:
                raise ValueError('Approved code is not continuously decoded: ' + name)
            self.instructions.update({x.address: x for x in rows})
        # First23 spans contain the exact R1/TLS/first-block graph. Pool and ctor2 are excluded.
        self.source_rows = self.collection['records'][:23]
        for row in self.source_rows:
            content = bytes.fromhex(row['bytesHex'])
            for i, byte in enumerate(content):
                at = row['address'] + i
                if at in self.source and self.source[at] != byte:
                    raise ValueError('Captured overlap differs')
                self.source[at] = byte
            self._map(row['address'], content)
        a, tls = self.collection['allocationInputs'], self.collection['tls']
        h, r, c, m = a['headPointer'], a['recordPointer'], tls['context'], tls['moduleTLS']
        self.write_specs = {
            0x140563d2d: (m + 0x19cc, 1), 0x140563da4: (m + 0x19cc, 1),
            0x140563d46: (r + 0x10, 2), 0x140563d4e: (r + 0x20, 8),
            0x140563d59: (h, 8), 0x140563d6b: (h + 8, 1),
            0x140563d73: (c + 8, 8), 0x141fdfa7a: (h + 16, 16),
            0x141fdfa7e: (h + 16, 16),
        }
        self.permitted_entries = {start + self.delta for start, _, _ in windows}
        self.hooks = [self.uc.hook_add(self.U.UC_HOOK_CODE, self._code),
            self.uc.hook_add(self.U.UC_HOOK_MEM_READ, self._read),
            self.uc.hook_add(self.U.UC_HOOK_MEM_WRITE, self._write),
            self.uc.hook_add(self.U.UC_HOOK_MEM_INVALID, self._invalid)]

    def _map(self, address, content):
        for page in range(address & ~4095, (address + len(content) + 4095) & ~4095, 4096):
            if page not in self.pages:
                self.uc.mem_map(page, 4096)
                self.pages.add(page)
        self.uc.mem_write(address, content)

    def reg(self, name):
        return self.uc.reg_read(getattr(self.R, 'UC_X86_REG_' + name.upper()))

    def _stop(self, reason, **details):
        self.failure = reason
        self.rejected = dict(reason=reason, pc=hex(self.reg('rip') - self.delta), executed=False, **details)
        self.uc.emu_stop()

    def _flush(self):
        for row in self.pending:
            row['newBytesHex'] = bytes(self.uc.mem_read(int(row['address'], 16), row['width'])).hex()
            self.writes.append(row)
        self.pending = []

    def _read_allowed(self, address, width):
        return (STACK <= address and address + width <= STACK + STACK_SIZE or
            all(address + i in self.source for i in range(width)) or
            any(start <= address and address + width <= end for start, end, _ in self.code_ranges))

    def _preflight(self, ins):
        pc = ins.address - self.delta
        if HELPER <= pc < HELPER + 904 and pc not in HELPER_PATH:
            return 'unapproved-helper-path', dict(instruction=ins.mnemonic)
        accesses = []
        for op in ins.operands:
            if op.type != self.OP_MEM or ins.mnemonic == 'lea':
                continue
            mem = op.mem
            address = mem.disp
            if mem.base:
                name = ins.reg_name(mem.base)
                address += ins.address + ins.size if name == 'rip' else self.reg(name)
            if mem.index:
                address += self.reg(ins.reg_name(mem.index)) * mem.scale
            if mem.segment:
                segment = ins.reg_name(mem.segment)
                if segment != 'gs':
                    return 'unapproved-segment', dict(segment=segment)
                address += self.collection['teb']
            address &= (1 << 64) - 1
            if not op.access:
                return 'unknown-memory-access-kind', dict(instruction=ins.mnemonic)
            accesses.append((address, op.size, op.access))
        mnemonic = ins.mnemonic
        if mnemonic == 'call':
            if (ins.operands[0].type != self.OP_IMM or
                    CALLS.get(pc) != ins.operands[0].imm - self.delta or
                    ins.operands[0].imm not in self.permitted_entries):
                return 'unapproved-callee', dict(target=ins.op_str)
            accesses.append((self.reg('rsp') - 8, 8, self.AC_WRITE))
        elif mnemonic == 'jmp':
            op = ins.operands[0]
            if op.type == self.OP_IMM:
                destination = op.imm
            elif op.type == self.OP_MEM:
                address, width, _ = accesses[0]
                if not self._read_allowed(address, width):
                    return 'unprovided-jump-target', dict(address=hex(address), width=width)
                destination = int.from_bytes(self.uc.mem_read(address, width), 'little')
            else:
                destination = self.reg(ins.reg_name(op.reg))
            if destination not in self.instructions:
                return 'unapproved-tail-edge', dict(target=hex(destination))
        elif mnemonic == 'push':
            accesses.append((self.reg('rsp') - 8, 8, self.AC_WRITE))
        elif mnemonic in ('ret', 'pop'):
            accesses.append((self.reg('rsp'), 8, self.AC_READ))
        for address, width, access in accesses:
            if access & self.AC_READ and not self._read_allowed(address, width):
                return 'unprovided-read-before-instruction', dict(address=hex(address), width=width)
            if access & self.AC_WRITE:
                if STACK <= address and address + width <= STACK + STACK_SIZE:
                    continue
                if not self.write_enabled or self.write_specs.get(pc) != (address, width):
                    return 'unapproved-write-before-instruction', dict(address=hex(address), width=width)
                self.pending.append(dict(pc=hex(pc), actualPC=hex(ins.address), address=hex(address),
                    width=width, oldBytesHex=bytes(self.uc.mem_read(address, width)).hex(),
                    nativeInstructionBytesHex=bytes(ins.bytes).hex()))
        return None, None

    def _code(self, uc, address, width, _):
        self._flush()
        ins = self.instructions.get(address)
        if ins is None or ins.size != width:
            self._stop('unapproved-code-boundary')
            return
        error, details = self._preflight(ins)
        if error:
            self.pending = []
            self._stop(error, **details)
            return
        pc = address - self.delta
        if ins.mnemonic == 'call':
            frame = dict(callPC=hex(pc), target=hex(ins.operands[0].imm - self.delta),
                originalBytesHex=bytes(ins.bytes).hex(), rspBefore=hex(self.reg('rsp')),
                returnAddress=hex(address + width), returnPreferred=hex(pc + width),
                registers={k: hex(self.reg(k)) for k in ('rcx', 'rdx', 'r8', 'rax', 'rsi')})
            self.calls.append(frame)
            self.frames.append(frame)
        elif ins.mnemonic == 'ret':
            destination = struct.unpack('<Q', uc.mem_read(self.reg('rsp'), 8))[0]
            frame = self.frames.pop() if self.frames else None
            expected = RETURN if frame is None else int(frame['returnAddress'], 16)
            if destination != expected:
                self._stop('original-return-stack-differs', actual=hex(destination), expected=hex(expected))
                return
            self.returns.append(dict(retPC=hex(pc), rspBefore=hex(self.reg('rsp')),
                actualStackReturn=hex(destination), caller=None if frame is None else frame['callPC'],
                rax=hex(self.reg('rax')), originalRetExecuted=True))
        self.path.append(pc)

    def _read(self, uc, access, address, width, value, _):
        if not self._read_allowed(address, width):
            self._stop('unexpected-unpreflighted-read', address=hex(address), width=width)
            return
        if not STACK <= address < STACK + STACK_SIZE:
            self.reads.append(dict(pc=hex(self.reg('rip') - self.delta), address=hex(address), width=width,
                bytesHex=bytes(uc.mem_read(address, width)).hex(),
                kind='captured-copy' if address in self.source else 'approved-code-data'))

    def _write(self, uc, access, address, width, value, _):
        if STACK <= address and address + width <= STACK + STACK_SIZE:
            return
        # Unicorn may split one SSE store into two8B callbacks. Logical16B event is flushed once.
        if not any(int(row['address'], 16) <= address and address + width <= int(row['address'], 16) + row['width']
                   for row in self.pending):
            self._stop('unexpected-unpreflighted-write', address=hex(address), width=width)
            return
        self.raw_writes.append(dict(pc=hex(self.reg('rip') - self.delta), address=hex(address), width=width,
            lowValueHex=hex(value & ((1 << min(width * 8, 64)) - 1))))

    def _invalid(self, uc, access, address, width, value, _):
        self._stop('unmapped-or-protected-access', address=hex(address), width=width, access=access)
        return False

    def run(self):
        self.uc.reg_write(self.R.UC_X86_REG_RSP, self.entry_sp)
        self.uc.reg_write(self.R.UC_X86_REG_RCX, self.collection['allocationInputs']['classPointer'])
        emulator_error = None
        try:
            self.uc.emu_start(ENTRY + self.delta, RETURN, count=LIMIT)
        except self.U.UcError as error:
            emulator_error = str(error)
        self._flush()
        returned = self.reg('rip') == RETURN and self.failure is None and emulator_error is None
        changed = []
        for address, original in self.source.items():
            actual = bytes(self.uc.mem_read(address, 1))[0]
            if original != actual:
                changed.append(address)
        approved = {address + i for address, width in self.write_specs.values() for i in range(width)}
        if not set(changed) <= approved:
            raise ValueError('Readback changed bytes outside exact approved spans')
        final = [dict(name=r['name'], address=hex(r['address']), width=r['requested'],
            originalBytesHex=r['bytesHex'], finalBytesHex=bytes(self.uc.mem_read(r['address'], r['requested'])).hex())
            for r in self.source_rows]
        return dict(returned=returned, failure=self.failure, rejected=self.rejected, emulatorError=emulator_error,
            entry=hex(ENTRY), entryKind='controlled-Win64-function-entry', entryRSP=hex(self.entry_sp),
            ownedSentinel=hex(RETURN), stop=hex(self.reg('rip')), finalRSP=hex(self.reg('rsp')),
            rax=hex(self.reg('rax')), instructions=len(self.path), instructionLimit=LIMIT,
            path=[hex(pc) for pc in self.path], pathSHA256=sha(b''.join(struct.pack('<Q', pc) for pc in self.path)),
            calls=self.calls, returns=self.returns, reads=self.reads, capturedWrites=self.writes,
            backendWriteCallbacks=self.raw_writes, originalAndFinalSpans=final,
            changedByteAddresses=[hex(a) for a in sorted(changed)], uniquePermissionBytes=len(approved),
            permissionSpans=[dict(address=hex(a), width=w) for a, w in sorted(set(self.write_specs.values()))])


def verify_execution(result, collection):
    a, tls = collection['allocationInputs'], collection['tls']
    h, r, c, m = a['headPointer'], a['recordPointer'], tls['context'], tls['moduleTLS']
    initial = bytes.fromhex(a['headBytesHex'])
    specifications = [
        (0x140563d2d, m + 0x19cc, 1, b'\x01', b'\x05'),
        (0x140563d46, r + 0x10, 2, struct.pack('<H', 218), struct.pack('<H', 219)),
        (0x140563d4e, r + 0x20, 8, struct.pack('<Q', h), initial[:8]),
        (0x140563d59, h, 8, initial[:8], struct.pack('<Q', a['classPointer'])),
        (0x140563d6b, h + 8, 1, b'\x08', b'\x81'),
        (0x140563d73, c + 8, 8, struct.pack('<Q', 7616), struct.pack('<Q', 7648)),
        (0x141fdfa7a, h + 16, 16, initial[16:], bytes(16)),
        (0x141fdfa7e, h + 16, 16, bytes(16), bytes(16)),
        (0x140563da4, m + 0x19cc, 1, b'\x05', b'\x01'),
    ]
    actual = [(int(w['pc'], 16), int(w['address'], 16), w['width'],
               bytes.fromhex(w['oldBytesHex']), bytes.fromhex(w['newBytesHex'])) for w in result['capturedWrites']]
    if actual != specifications:
        raise ValueError('Actual nine native write events differ from exact contract')
    if not result['returned'] or result['rax'] != hex(h) or int(result['finalRSP'], 16) != int(result['entryRSP'], 16) + 8:
        raise ValueError('Original outer return differs')
    returns = {r['retPC']: r for r in result['returns']}
    if returns['0x141fdfa85']['rax'] != hex(h + 16) or returns['0x140563dbe']['rax'] != hex(h):
        raise ValueError('Original helper/pop return values differ')
    if returns['0x140563dbe']['caller'] != '0x1405337d8' or returns['0x140525e26']['caller'] != '0x1405337fb':
        raise ValueError('Original internal caller stack differs')
    helper_path = [int(pc, 16) for pc in result['path'] if HELPER <= int(pc, 16) < HELPER + 904]
    if helper_path != HELPER_PATH:
        raise ValueError('Exact original helper15-instruction path differs')
    return dict(nineOriginalCapturedWriteEvents=True, sevenUniqueSpans=True, uniquePermissionBytes=44,
        twoSameAddressSSEStoresPreserved=True, actualDirtySuffixCleared=True,
        actualInternalCallRetPreserved=True, originalHelperRAXEqualsHPlus16=True,
        originalPopAndOuterRAXEqualsH=True, sourceBytesOutsideApprovedSpansUnchanged=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    output = args.output.resolve()
    if not output.is_relative_to(OUT.resolve()) or output.exists():
        parser.error('Choose a new output inside the assigned private directory')
    frozen = load_frozen()
    lookup, exe = extract_approved_lookup()
    executable = Path(exe['exePath'])
    dependency_pins = {path: sha((ROOT / path).read_bytes()) for path in (
        'tools/client72_d02_first_pop_copy.py', 'tools/test_client72_d02_first_pop_copy.py',
        'tools/trace_client72_token.py', 'tools/game_path.ps1')}
    windows = code_windows(frozen, lookup)
    native = FirstPopCopy(frozen, windows).run()
    checks = verify_execution(native, frozen['collection']) if native['returned'] else None
    after = load_frozen()
    if frozen['raw'] != after['raw']:
        raise ValueError('Original source changed after execution')
    if (executable.stat().st_size != exe['bytes'] or sha(executable.read_bytes()) != EXE_SHA or
            any(sha((ROOT / path).read_bytes()) != digest for path, digest in dependency_pins.items())):
        raise ValueError('Static EXE or execution dependency changed after execution')
    result = dict(task='F-03d3f4d2/FIRST-POP-COPY', passed=checks is not None,
        originalInputPins=frozen['pins'], pinManifestSHA256=PIN_SHA, extraPins=EXTRA_PINS,
        historicalSourceSnapshotsVerified=frozen['historicalSources'], originalSourcesBeforeAfterSHA256Equal=True,
        staticEXEBeforeAfterSHA256Equal=True, executionDependencyPins=dependency_pins,
        currentStaticEXE=exe, codeWindows=[dict(start=hex(s), bytes=len(b), sha256=sha(b), name=n,
            **({'bytesHex': b.hex()} if n == 'approved-old-lookup-code-only' else {})) for s,b,n in windows],
        native=native, checks=checks, implementationSHA256=sha(Path(__file__).read_bytes()),
        loaderSHA256=sha((ROOT / 'tools/trace_client72_token.py').read_bytes()),
        permissions=dict(nativeCPUExecuted=True, capturedCopyWriteExecuted=bool(native['capturedWrites']),
            exactCapturedCopyWriteApproved=True, liveAPIInvoked=False, targetProcessWrite=False,
            GCObjectOwnershipProven=False, naturalAllocatorThreadProven=False, naturalCtorFrameKnown=False,
            completeAvatar=False, completeG2=False, completeScene=False, secondAllocation=False, d02Passed=False))
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open('x', encoding='utf-8', newline='\n') as stream:
        json.dump(result, stream, ensure_ascii=False, sort_keys=True, indent=2)
        stream.write('\n')
    print(json.dumps(dict(passed=result['passed'], output=str(output.relative_to(ROOT)),
        sha256=sha(output.read_bytes()), instructions=native['instructions'], writes=len(native['capturedWrites']),
        failure=native['failure'])))
    return 0 if result['passed'] else 1


if __name__ == '__main__':
    raise SystemExit(main())
