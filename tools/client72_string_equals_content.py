"""Bounded original10339 -> EqualsHelper2908 UTF16 reads; frozen sources only."""
import argparse
import json
from pathlib import Path
import struct
import sys

ROOT = Path(__file__).resolve().parents[1]
if not __package__:
    sys.path.insert(0, str(ROOT))
    __package__ = 'tools'
from . import client72_string_equals_successor as prefix

frozen = prefix.prior
sha = prefix.sha
OUT = ROOT / 'local/migration72/d02-parallel-20261109/string-equals-content'
BASE = 'local/migration72/d02-parallel-20261109/'
HELPER = BASE + 'string-equals-helper-window1/manifest.private.json'
OLD = BASE + 'string-equals-successor/'
PINS = {
    HELPER: 'a6b2d2073afea9835f328d45f24ba697da3f29b90a78c3225b6fd69c4049057a',
    OLD + 'first.private.json': '32fd295fc85a82165def82fb990b0eba915fce80767e6c39768ebd9e49701fcc',
    OLD + 'repeat.private.json': '32fd295fc85a82165def82fb990b0eba915fce80767e6c39768ebd9e49701fcc',
    OLD + 'main-review.private.json': '56ed0eb0ea9e9fb0e865ac2fdc7f6fd929981dddca40f7471e76b682c17978d9',
    OLD + 'source-closure-audit.private.json': '5d9cc546fa35f0f3f6dacf41d4e22f9c8df7513256a5deb427e62c07175a2429',
    'tools/client72_string_equals_successor.py': 'f48f428c1d0d59310331eddb1f100c07a1e329f819211ad17dc2a6efb65f0059',
}
LEFT, RIGHT = prefix.LEFT, prefix.RIGHT
BLOCKED = {0x159aac3c6, 0x159aac3cb, 0x159aac3d0}
CASES = [
    ('empty-distinct', '', '', 1),
    ('one-equal', 'A', 'A', 1),
    ('one-diff', 'A', 'B', 0),
    ('BMP-equal', '\u4e2d\u6587', '\u4e2d\u6587', 1),
    ('BMP-diff', '\u4e2d\u6587', '\u4e2d\u95ee', 0),
    ('supplementary-equal', '\U0001f642', '\U0001f642', 1),
    ('embeddedNul-diff', 'A\0B', 'A\0C', 0),
    ('12unit-equal', 'abcdefghijkl', 'abcdefghijkl', 1),
    ('13unit-equal', 'abcdefghijklm', 'abcdefghijklm', 1),
    ('24unit-equal', 'abcdefghijklmnopqrstuvwx', 'abcdefghijklmnopqrstuvwx', 1),
    ('25unit-diff', 'abcdefghijklmnopqrstuvwxA', 'abcdefghijklmnopqrstuvwxB', 0),
    ('35unit-mid-diff', 'a' * 35, 'a' * 16 + 'b' + 'a' * 18, 0),
    ('owned-source-key', 'Weapon_Sword_Metal', 'Weapon_Sword_Metal', 1),
    ('near-key', 'Weapon_Sword_Metal', 'Weapon_Sword_MetaL', 0),
]


def load_sources():
    capture, original, rows, pins, static = prefix.load_sources()
    sources = {}
    for path, digest in PINS.items():
        raw = (ROOT / path).read_bytes()
        if sha(raw) != digest:
            raise ValueError('Frozen content source differs: ' + path)
        pins[path] = digest
        if path.endswith('.json'):
            sources[path] = json.loads(raw)
    closure = sources[OLD + 'source-closure-audit.private.json']
    for path, digest in closure['pins'].items():
        if sha((ROOT / path).read_bytes()) != digest:
            raise ValueError('Frozen source closure differs: ' + path)
        pins[path] = digest
    if (not sources[OLD + 'main-review.private.json']['passed'] or
            sources[OLD + 'first.private.json'] != sources[OLD + 'repeat.private.json']):
        raise ValueError('Predecessor review/two fresh reports differ')
    helper = sources[HELPER]
    directory = Path(HELPER).parent
    code = (ROOT / directory / 'code.bin').read_bytes()
    pdata = (ROOT / directory / 'pdata.bin').read_bytes()
    if (helper['fixedEXESHA256'] != frozen.EXE_SHA or helper['start'] != '0x159aac320' or
            helper['end'] != '0x159aac3d1' or helper['bytes'] != 177 or len(code) != 177 or
            code.hex() != helper['bytesHex'] or sha(code) != helper['sha256'] or
            helper['pdataCount'] != 1 or helper['pdataBytes'] != 12 or
            pdata.hex() != helper['pdataBytesHex'] or len(pdata) != 12 or
            struct.unpack('<III', pdata) != (0x19aac320, 0x19aac3d1, 0x5427194)):
        raise ValueError('Approved exact177B helper/pdata differs')
    cursor = prefix.TARGET
    for row in helper['instructions']:
        if int(row['va'], 16) != cursor:
            raise ValueError('Helper original instructions not contiguous')
        cursor += len(bytes.fromhex(row['bytesHex']))
    if (cursor != 0x159aac3d1 or
            b''.join(bytes.fromhex(r['bytesHex']) for r in helper['instructions']) != code):
        raise ValueError('Helper original full decode differs')
    for name, raw in (('code.bin', code), ('pdata.bin', pdata)):
        pins[str(directory / name)] = sha(raw)
    # Only the reachable48B of the incomplete10339 prefix is executable.
    windows = [(original[0], original[0] + 48, original[2][:48], sha(original[2][:48]), original[4]),
        (prefix.TARGET, 0x159aac3d1, code, sha(code), HELPER)]
    all_rows = rows + helper['instructions']
    static.update(helper=dict(start=helper['start'], end=helper['end'], bytes=177,
        sha256=sha(code), pdataBytesHex=pdata.hex(), pdataSHA256=sha(pdata),
        completePdataExtent=True, decodedInstructions=len(helper['instructions']),
        blockedBeforeExecution=[hex(a) for a in sorted(BLOCKED)], unreviewedCallee='0x1404c2be0'),
        executableWindows=[dict(start=hex(a), end=hex(z), bytes=len(b), sha256=h) for a,z,b,h,_ in windows])
    static['tailTransfer']['executed'] = True
    return capture, windows, all_rows, pins, static


def fixtures(left, right):
    result = []
    for address, text in ((LEFT, left), (RIGHT, right)):
        raw = text.encode('utf-16-le')
        result.append(dict(address=hex(address), lengthUnits=len(raw) // 2, utf16Hex=raw.hex(),
            lengthField=hex(address + 16), firstChar=hex(address + 20),
            terminator=hex(address + 20 + len(raw)), terminatorBytesHex='0000',
            terminatorSource='explicit-owned-two-byte-field; natural-allocator-unverified',
            headerProvided=False, stringClassProvided=False))
    return result


class ContentNative(frozen.FrozenNative):
    def __init__(self, libraries, memory, windows, module, collection, rows, owned, fixture):
        self.allowed = {int(r['va'], 16): len(bytes.fromhex(r['bytesHex'])) for r in rows}
        self.instruction_rows = {int(r['va'], 16): r for r in rows}
        self.owned_known = {a+i: b for a, raw in owned for i,b in enumerate(raw)}
        self.fixture, self.owned_reads, self.transitions, self.instruction_path = fixture, [], [], []
        super().__init__(libraries, memory, windows, module, collection)

    def code(self, uc, address, width, data):
        preferred = address - self.module + frozen.PREFERRED
        if preferred in BLOCKED:
            self.failure = 'unreviewed-helper-null-callee-or-trap'
            uc.emu_stop()
            return
        if self.allowed.get(preferred) != width:
            self.failure = 'unapproved-content-instruction'
            uc.emu_stop()
            return
        if preferred in (prefix.TAIL, prefix.TARGET):
            self.transitions.append(dict(preferred=hex(preferred),
                registers={k: hex(self.reg(k)) for k in ('rcx', 'rdx', 'r8', 'r9', 'rsp')}))
        super().code(uc, address, width, data)
        if self.failure is None:
            self.instruction_path.append(dict(self.instruction_rows[preferred],
                runtime=hex(address), rspBefore=hex(self.reg('rsp'))))

    def read(self, uc, access, address, width, value, data):
        if frozen.prior.OBJECTS <= address < frozen.prior.OBJECTS + 0x1000:
            if not all(address+i in self.owned_known for i in range(width)):
                self.failure = 'unprovided-owned-header-or-field'
                self.missing.append(dict(address=hex(address), width=width, reader=hex(self.reg('rip')), owned=True))
                uc.emu_stop()
                return
            actual = bytes(uc.mem_read(address, width))
            expected = bytes(self.owned_known[address+i] for i in range(width))
            error = None if actual == expected else 'owned-field-drift'
            overlap = []
            for f in self.fixture:
                start = int(f['terminator'], 16)
                cells = [a for a in range(address, address+width) if start <= a < start+2]
                if cells:
                    overlap.append(dict(stringAddress=f['address'], addresses=[hex(a) for a in cells],
                        bytesHex=bytes(actual[a-address] for a in cells).hex(), source='explicit-owned-terminator'))
            row = self.memory.note('native.owned-field-read', address, width, actual, error, [],
                reader=hex(self.reg('rip')), preferredReader=hex(self.reg('rip')-self.module+frozen.PREFERRED),
                terminatorOverlap=overlap)
            self.owned_reads.append(row)
            if error:
                self.failure = error
                uc.emu_stop()
                return
        super().read(uc, access, address, width, value, data)

    def write(self, uc, access, address, width, value, data):
        super().write(uc, access, address, width, value, data)
        if not frozen.prior.STACK <= address <= address+width <= frozen.prior.STACK+0x20000:
            self.failure = 'unexpected-nonstack-write'
            uc.emu_stop()


def replay(libraries):
    capture, windows, rows, pins, static = load_sources()
    collection = capture['collection']
    memory = frozen.prior.ExactBytes(collection['records'])
    audit = frozen.audit_capture(capture, memory)
    module, string = collection['moduleBase'], collection['string']
    native, first_failure = [], None
    for name, left, right, expected in CASES:
        fixture = fixtures(left, right)
        owned = ((LEFT+16, prefix.owned_fields(left)), (RIGHT+16, prefix.owned_fields(right)))
        engine = ContentNative(libraries, memory, windows, module, collection, rows, owned, fixture)
        registers = dict(rcx=string['default'], rdx=LEFT, r8=RIGHT,
            r9=string['equalsMethodInfo'], rax=0xdeadbeef0000cc00)
        read_start = len(memory.reads)
        result = engine.run_fresh(name, string['equalsFunction'], registers, owned)
        al = int(result['registers']['rax'], 16) & 255 if result['returned'] else None
        result.update(expectedAL=expected, returnAL=al, stopReason=engine.stop_reason,
            fixture=fixture, transitions=engine.transitions, ownedReads=engine.owned_reads,
            originalInstructionPath=engine.instruction_path,
            hostReadOrdinals=[r['ordinal'] for r in memory.reads[read_start:]],
            ownedHeaderKnown=False, ownedStringClassProvided=False, naturalBusinessKeys=False,
            knownOwnedFieldsOnly=True, naturalAllocatorTerminatorVerified=False)
        native.append(result)
        if (not result['returned'] or al != expected or result['failure'] is not None or
                result['emulatorError'] is not None or not result['capturedReadbackEqual']):
            first_failure = name
            break
    return dict(task='E-01/R3-ITEM-ENTRY-STRING-EQUALS-CONTENT', sourceSHA256=sha(Path(__file__).read_bytes()),
        fixedSourcePins=pins, fixedEXESHA256=frozen.EXE_SHA, rawSHA256=frozen.RAW_SHA,
        moduleBase=hex(module), inputAudit=audit, static=static, native=native,
        expectedCaseIDs=[c[0] for c in CASES], firstFailure=first_failure,
        hostBudget=dict(reads=len(memory.reads), requestedBytes=memory.requested, maxReads=4096,
            maxBytes=65536, maxSingleRead=208, records=memory.reads),
        callerBudget=dict(reads=sum(n['boundaryHostBudget']['reads'] for n in native),
            requestedBytes=sum(n['boundaryHostBudget']['requestedBytes'] for n in native), maxReads=16, maxBytes=128),
        cpuBudget=dict(calls=len(native), observations=sum(n['observations'] for n in native),
            completedOriginalInstructions=sum(n['completedOriginalInstructions'] for n in native),
            perContinuousCall=2000000, formalGroupsAllowed=2, totalCallsAllowed=28,
            developmentNativeCalls=0, allFailuresPreserved=True),
        rawCapturedReadbackEqual=all(n['capturedReadbackEqual'] for n in native),
        prefixIsCompleteFunction=False, targetHelperExecuted=any(n['transitions'] for n in native),
        oldGetterOrAllocatorCalls=0, helperMethodInfoInvented=False, inputR8ChangedByHarness=False,
        ownedClassOrHeaderInvented=False, naturalAllocatorTerminatorVerified=False,
        newPEExtraction=False, fullStringComparer=False, fullFindEntry=False,
        naturalBusinessKeys=False, naturalCallerObserved=False, defaultCreationVerified=False,
        configAbilityApproved=False, d02Complete=False, stockDiskRead=False,
        clientOrDebuggerStarted=False, successModelsSupplied=[])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--python-libs', type=Path, default=ROOT/'local/migration72/python')
    args = parser.parse_args()
    output = args.output.resolve()
    if not output.is_relative_to(OUT.resolve()) or output.exists():
        parser.error('Fresh assigned private output required')
    result = replay(args.python_libs)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.with_suffix('.source.py').write_bytes(Path(__file__).read_bytes())
    with output.open('x', encoding='utf8') as stream:
        json.dump(result, stream, indent=2)
        stream.write('\n')
    print(json.dumps(dict(sha256=sha(output.read_bytes()), firstFailure=result['firstFailure'],
        cases=[dict(name=n['name'], AL=n['returnAL'], observations=n['observations'], failure=n['failure']) for n in result['native']],
        hostReads=result['hostBudget']['reads'], hostBytes=result['hostBudget']['requestedBytes'])))
    return int(result['firstFailure'] is not None)


if __name__ == '__main__':
    sys.exit(main())
