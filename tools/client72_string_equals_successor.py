"""Execute only the approved64B String Equals10339 prefix with owned fields."""
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
from . import client72_fresh_avatar_string_successor as prior

OUT = ROOT / 'local/migration72/d02-parallel-20261109/string-equals-successor'
BASE = 'local/migration72/d02-parallel-20261109/'
MANIFEST = BASE + 'native-successor-windows-main1/string-equals10339/manifest.private.json'
APPROVAL = BASE + 'fresh-successor-replay/main-approved-string-common-input.private.json'
METADATA = 'local/migration72/d02-resume-20261108/r3-equipment/metadata.private.pkl'
SIGNATURES = 'local/migration72/evidence/client72-signatures.json'
PINS = {
    MANIFEST: 'ba0c1db3122ce7a391bf80fdc67eccef1c2a41ef8369de42a167a22d7b03c6d7',
    APPROVAL: '07494634d3d46ee26807e0f96672b3378bd50fa7451786801c988a86b0bea08e',
    METADATA: 'df52eb70fe8e70dfecd1059e898d15e288cfa98bcefc78bcaabf753ae150349f',
    SIGNATURES: '28377e7a8d3c891cd61be97ddb74f8502a22f2f2f039d6a91dba3b092d7822fe',
    'tools/client72_fresh_avatar_string_successor.py': '80d41fb8c5d4c317bf17d9d3c0a2a84cd0abd88b379a614d4a3f4b47922c30ad',
    'tools/client72_avatar_warm_continuation.py': '7981c1e22609d4ac6c5927354e508cfc02b5aa242e33e0c213ea16883e8993ef',
    'tools/trace_client72_token.py': 'f2fbc4728f73447dff4f7320beb3a7a31fb546287272b62ccd88a8e940312fa0',
}
ENTRY, TAIL, TARGET = 0x159b6f300, 0x159b6f31e, 0x159aac320
LEFT, RIGHT = prior.prior.OBJECTS + 0x600, prior.prior.OBJECTS + 0x680


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


def load_sources():
    loaded, pins = {}, {}
    for path, digest in {**PINS, prior.PLAN: prior.PINS[prior.PLAN]}.items():
        raw = (ROOT / path).read_bytes()
        if sha(raw) != digest:
            raise ValueError('Frozen source differs: ' + path)
        pins[path] = digest
        if path.endswith('.json'):
            loaded[path] = json.loads(raw)
        elif path == METADATA:
            loaded[path] = pickle.loads(raw)[0]
    plan = loaded[prior.PLAN]
    capture = prior.load_pin(plan['outputPath'], prior.RAW_SHA, pins)
    manifest = loaded[MANIFEST]
    code_path = str(Path(MANIFEST).parent / 'code.bin')
    code = (ROOT / code_path).read_bytes()
    if (manifest['fixedModuleSHA256'] != prior.EXE_SHA or manifest['start'] != hex(ENTRY) or
            manifest['bytes'] != 64 or len(code) != 64 or code.hex() != manifest['bytesHex'] or
            sha(code) != manifest['sha256'] or manifest['pdata']['recordPresent'] is not False or
            manifest['functionExtentKnown'] is not False):
        raise ValueError('Only the main-approved exact64B prefix is allowed')
    pins[code_path] = sha(code)
    pdata = (ROOT / Path(MANIFEST).parent / 'pdata.bin').read_bytes()
    if pdata != b'' or sha(pdata) != manifest['pdata']['sha256']:
        raise ValueError('Missing original pdata must remain missing')
    rows = [r for r in manifest['instructions'] if int(r['va'], 16) < 0x159b6f330]
    if b''.join(bytes.fromhex(r['bytesHex']) for r in rows) != code[:48]:
        raise ValueError('Entry reachable instruction bytes differ')
    records = loaded[METADATA]
    string = next(r for r in records if r['index'] == 293)
    fields = [r for r in string['fields'] if r['name'] in ('m_stringLength', 'm_firstChar')]
    if [(f['name'], f['offset'], f['type']['kind']) for f in fields] != [
            ('m_stringLength', 16, 'int32'), ('m_firstChar', 20, 'char')]:
        raise ValueError('Owned String field layout lacks fixed metadata proof')
    helper = next(r for r in string['methods'] if r['index'] == 2908)
    signatures = loaded[SIGNATURES]
    helper_signature = next(r for r in signatures['methods'] if r['index'] == 2908)
    equals_signature = next(r for r in signatures['methods'] if r['index'] == 10339)
    descriptor = signatures['typeReferences']['33']
    if (helper['registeredAddress'] != hex(TARGET) or helper['flags'] != '0x91' or
            helper_signature['registeredAddress'] != hex(TARGET) or
            [p['typeIndex'] for p in helper_signature['parameters']] != [33, 33] or
            [p['typeIndex'] for p in equals_signature['parameters']] != [33, 33] or
            descriptor != dict(kind='string', attributes='0x0', data='0x125', byRef=False)):
        raise ValueError('Selected Equals/helper metadata signature differs')
    current = capture['collection']['string']
    module = capture['collection']['moduleBase']
    if (current['equalsFunction'] != module + ENTRY - prior.PREFERRED or
            current['equals']['methodDefinition'] != 10339 or current['identity']['definition'] != 1243):
        raise ValueError('Actual natural Equals selection differs')
    static = dict(prefixBytes=64, reachableBytesUsed=48, pdataRecordPresent=False,
        functionExtentKnown=False, incompleteTail=dict(preferred='0x159b6f33e', bytesHex=code[62:].hex(), executed=False),
        originalStringFields=fields, equalsSignature=equals_signature, targetMethod=helper,
        targetSignature=helper_signature, parameterType33=descriptor,
        tailTransfer=dict(site=hex(TAIL), bytesHex=code[30:35].hex(), target=hex(TARGET),
            preTransferAbi=dict(rcx='original RDX / owned string x', rdx='original R8 / owned string y',
                r8='original R8 unchanged; not relabeled as helper MethodInfo', r9='actual original Equals companion MethodInfo unchanged'),
            stack='tail jump keeps original caller return; no new call frame', executed=False),
        returnTypeMetadataStatus=signatures['report']['returnTypesStatus'])
    return capture, (ENTRY, ENTRY + 64, code, sha(code), MANIFEST), rows, pins, static


def owned_fields(text):
    raw = text.encode('utf-16-le')
    return struct.pack('<i', len(raw) // 2) + raw + bytes(2)


class PrefixNative(prior.FrozenNative):
    def __init__(self, libraries, memory, window, module, collection, rows, owned):
        self.allowed = {int(r['va'], 16): len(bytes.fromhex(r['bytesHex'])) for r in rows}
        self.owned_known = {a + i: b for a, raw in owned for i, b in enumerate(raw)}
        self.transfer = None
        super().__init__(libraries, memory, [window], module, collection)

    def code(self, uc, address, width, data):
        preferred = address - self.module + prior.PREFERRED
        if preferred == TAIL:
            self.stop_reason = 'unapproved-tail-transfer-before-jmp'
            self.transfer = dict(site=hex(address), preferredSite=hex(TAIL),
                target=hex(self.module + TARGET - prior.PREFERRED), preferredTarget=hex(TARGET),
                executed=False, registers={k: hex(self.reg(k)) for k in ('rcx', 'rdx', 'r8', 'r9', 'rsp')})
            uc.emu_stop()
            return
        if self.allowed.get(preferred) != width:
            self.failure = 'unapproved-prefix-instruction'
            uc.emu_stop()
            return
        super().code(uc, address, width, data)

    def read(self, uc, access, address, width, value, data):
        if prior.prior.OBJECTS <= address < prior.prior.OBJECTS + 0x1000:
            if not all(address + i in self.owned_known for i in range(width)):
                self.failure = 'unprovided-owned-header-or-field'
                self.missing.append(dict(address=hex(address), width=width, reader=hex(self.reg('rip')), owned=True))
                uc.emu_stop()
                return
            expected = bytes(self.owned_known[address + i] for i in range(width))
            actual = bytes(uc.mem_read(address, width))
            error = None if expected == actual else 'owned-field-drift'
            self.memory.note('native.owned-field-read', address, width, actual, error, [], reader=hex(self.reg('rip')))
            if error:
                self.failure = error
                uc.emu_stop()
                return
        super().read(uc, access, address, width, value, data)


def replay(libraries):
    capture, window, rows, pins, static = load_sources()
    collection = capture['collection']
    memory = prior.prior.ExactBytes(collection['records'])
    audit = prior.audit_capture(capture, memory)
    module, string = collection['moduleBase'], collection['string']
    cases = [
        ('null-null', 0, 0, ()),
        ('same-owned-pointer', LEFT, LEFT, ((LEFT + 16, owned_fields('owned')),)),
        ('owned-length-mismatch', LEFT, RIGHT, ((LEFT + 16, owned_fields('x')), (RIGHT + 16, owned_fields('long')))),
        ('owned-equal-length-distinct-tail-boundary', LEFT, RIGHT,
            ((LEFT + 16, owned_fields('key')), (RIGHT + 16, owned_fields('key')))),
    ]
    native = []
    for name, left, right, owned in cases:
        engine = PrefixNative(libraries, memory, window, module, collection, rows, owned)
        registers = dict(rcx=string['default'], rdx=left, r8=right, r9=string['equalsMethodInfo'], rax=0xdeadbeef0000cc00)
        result = engine.run_fresh(name, string['equalsFunction'], registers, owned)
        result.update(stopReason=engine.stop_reason, transfer=engine.transfer,
            returnAL=(int(result['registers']['rax'], 16) & 255) if result['returned'] else None,
            ownedHeaderKnown=False, ownedStringClassProvided=False,
            knownOwnedFieldsOnly=True, naturalBusinessKeys=False)
        native.append(result)
    return dict(task='E-01/R3-ITEM-ENTRY-STRING-EQUALS-SUCCESSOR', sourceSHA256=sha(Path(__file__).read_bytes()),
        fixedSourcePins=pins, fixedEXESHA256=prior.EXE_SHA, rawSHA256=prior.RAW_SHA, inputAudit=audit,
        static=static, native=native, prefixIsCompleteFunction=False, newPEExtraction=False,
        hostBudget=dict(reads=len(memory.reads), requestedBytes=memory.requested, maxReads=4096,
            maxBytes=65536, maxSingleRead=208, records=memory.reads),
        callerBudget=dict(reads=sum(n['boundaryHostBudget']['reads'] for n in native),
            requestedBytes=sum(n['boundaryHostBudget']['requestedBytes'] for n in native), maxReads=16, maxBytes=128),
        cpuBudget=dict(calls=len(native), observations=sum(n['observations'] for n in native),
            completedOriginalInstructions=sum(n['completedOriginalInstructions'] for n in native), perContinuousCall=2000000),
        rawCapturedReadbackEqual=all(n['capturedReadbackEqual'] for n in native),
        oldGetterOrAllocatorCalls=0, naturalBusinessKeys=False, targetHelperExecuted=False,
        ownedClassOrHeaderInvented=False, fullStringComparer=False, fullFindEntry=False,
        naturalCallerObserved=False, configAbilityApproved=False, d02Complete=False,
        stockDiskRead=False, clientOrDebuggerStarted=False, successModelsSupplied=[])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--python-libs', type=Path, default=ROOT / 'local/migration72/python')
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
    print(json.dumps(dict(sha256=sha(output.read_bytes()), cases=[dict(name=n['name'], returned=n['returned'],
        AL=n['returnAL'], observations=n['observations'], stop=n['preferredStop'], reason=n['stopReason']) for n in result['native']],
        hostReads=result['hostBudget']['reads'], hostBytes=result['hostBudget']['requestedBytes'])))


if __name__ == '__main__':
    main()
