"""Hash-bound, offline CN72 resource parser research; never loads game resources."""
import argparse
import hashlib
import json
import struct
from pathlib import Path

try:
    from .extract_client72 import HASHES, PE, read_verified, extract
    from .extract_client72_enums import recover as recover_enums
    from .extract_client72_literals import Literals72
    from .client72_emulator import Offline72, GLOBAL, STACK, END
    from .client72_session_runtime import fragment, permutation_table
    from .audit_resource72 import read_client_evidence
except ImportError:
    from extract_client72 import HASHES, PE, read_verified, extract
    from extract_client72_enums import recover as recover_enums
    from extract_client72_literals import Literals72
    from client72_emulator import Offline72, GLOBAL, STACK, END
    from client72_session_runtime import fragment, permutation_table
    from audit_resource72 import read_client_evidence

RETURN = END + 0x100
DEPOT = 0x148328B60
SPECIAL = 0x15113CBB0
CONDITION = 0x14EF42DD0
ENCODE, DECODE = 0x14AEFA090, 0x14AEF9DB0
U32 = 0xffffffff
INPUTS = {
    'local/migration72/d02-r3-candidate/ExcelBinOutput/QuestExcelConfigData.json': 'c8561299f543456573b0fa16404e210bcd2ecf84ce5006eb63dd4b2845137a87',
    'local/migration72/d02-r3-candidate/BinOutput/Quest/303.json': 'aaf6901da4c30528047c1210bc636f810b368d5fb2f19aad6369bbb956dd3cca',
    'protocol/cn-7.2/avatar-skill-depot-observations.json': 'a8a7b4ce71869c4af88e3bf8164413ed109c7d6e051478d1e682b19e5d0e1a95',
}


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--client', required=True, type=Path)
    p.add_argument('--output', required=True, type=Path)
    p.add_argument('--summary', required=True, type=Path)
    p.add_argument('--python-libs', type=Path, default=Path('local/migration72/python'))
    a = p.parse_args()
    if a.output.resolve() == a.summary.resolve() or any(x.resolve().is_relative_to(a.client.resolve()) for x in (a.output, a.summary)):
        p.error('Distinct output files outside the client are required')
    for path, expected in INPUTS.items():
        if hashlib.sha256(Path(path).read_bytes()).hexdigest() != expected:
            raise ValueError('Input changed: ' + path)
    binary, metadata, startup = [read_verified(a.client / n, h) for n, h in HASHES.items()]
    records, _ = extract(binary, metadata, startup=startup, structures=True)
    declarations = selected_declarations(binary, metadata, records)
    engine = ResourceRuntime(binary, a.python_libs, records)
    try:
        native = run_native(engine)
    finally:
        engine.close()
    _, revisions = read_client_evidence(a.client)
    report = dict(schemaVersion=1, task='E-01/R3-resource-deep', inputSHA256=HASHES,
                  priorInputs=INPUTS, declarations=declarations, native=native,
                  allocationModels=engine.allocations, stringConversionModels=engine.string_conversions,
                  candidate=selected_candidate(), bundledRevisions=revisions,
                  runtimeStatus='not-tested', resourceReady=False, targetProtocolReady=False,
                  limitations=[
                      'Fresh target metadata and CPU execution; no client process, resource file IO, Java loadAll, database or deployment.',
                      'Binary parser fixtures are independently authored, not extracted installed resource blobs or JSON-to-binary conversions.',
                      'Managed arrays/strings and memory-library operations are explicit models; native field transformations, branches and stores execute.',
                      'String conversion helper is modeled at its observed UTF8 buffer/cursor boundary; no Lua condition evaluator is modeled.',
                      'Absent binary flag is not proof that a missing candidate JSON key maps to that flag.',
                      'Default constructor RET does not initialize encoded integers; logical zero is established by the binary parser.',
                      'No English energySkill/attackModeSkill or Quest LogicType/acceptCond/BGH alias is approved.',
                      'Candidate release-label versus installed hot-update resource alignment remains unknown.'
                  ])
    source_names = ['tools/client72_resource_deep.py', 'tools/client72_emulator.py',
                    'tools/client72_session_runtime.py', 'tools/extract_client72.py',
                    'tools/extract_client72_enums.py', 'tools/extract_client72_literals.py',
                    'tools/audit_resource72.py']
    report['sourceSHA256'] = {n: hashlib.sha256(Path(n).read_bytes()).hexdigest() for n in source_names}
    for path, value in ((a.output, report), (a.summary, compact(report))):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(value, indent=2, ensure_ascii=False) + '\n', 'utf8')
    print(json.dumps({k: len(v) for k, v in native.items()}))


class ResourceRuntime:
    """Small local model of byte-reader storage; all parser entries execute."""
    def __init__(self, binary, libraries, records):
        self.e = e = Offline72(binary, libraries, records)
        e.uc.mem_map(END, 4096)
        e.uc.mem_write(RETURN, b'\xc3')
        for entry in (DEPOT, SPECIAL, CONDITION, ENCODE, DECODE, 0x148328B50,
                      0x15113CF70, 0x149987A80, 0x1487CBB80):
            e.allow(entry)
        table = e.alloc(2080)
        e.write(table + 24, 2048, 'I')
        raw = permutation_table(13, 29)
        e.uc.mem_write(table + 32, raw[:1024] + raw[2048:3072])
        e.write(GLOBAL + 0xe970, table)
        e.write(0x145872D30, 0x12345678, 'I')
        self.strings = {}
        self.string_conversions = []
        self.allocations = []
        # These runtime singleton slots are read only on omitted/empty branches.
        empty_array = self.array(0, 4)
        for cell in (0x145b744f8, 0x145b74530, 0x145b74618, 0x145b749b0, 0x145b749b8):
            e.write(cell, empty_array)
        e.write(0x145ac9e50, self.string(''))
        e.models[0x140514EE0] = self.allocate_array
        e.models[0x141FDF9C0] = self.memset
        e.models[0x141FDF310] = self.memcpy
        e.models[0x14F0DE820] = self.convert_string

    def close(self):
        self.e.close()

    def array(self, count, width):
        e = self.e
        result = e.alloc(32 + count * width)
        e.write(result + 24, count, 'I')
        return result

    def allocate_array(self):
        e = self.e
        count = e.reg('rdx')
        if count > 16:
            raise ValueError('Outside the declared fixture array size')
        site = e.read(e.reg('rsp')) - 5
        element, width = {
            0x15113CCCA: ('encoded uint32 type58209', 4),
            0x14EF42EA3: ('int32', 4),
            0x149987ADC: ('char', 2),
        }[site]
        self.allocations.append(dict(site=hex(site), count=count, element=element, width=width,
                                     scope='Managed array storage model; element type follows this reviewed caller, not a live type object'))
        return self.array(count, width)

    def string(self, text):
        e = self.e
        data = text.encode('utf-16-le')
        result = e.alloc(24 + len(data))
        e.write(result + 16, len(data) // 2, 'I')
        if data:
            e.uc.mem_write(result + 20, data)
        self.strings[result] = text
        return result

    def memset(self):
        e = self.e
        e.uc.mem_write(e.reg('rcx'), bytes([e.reg('edx') & 255]) * e.reg('r8'))
        return e.reg('rcx')

    def memcpy(self):
        e = self.e
        e.uc.mem_write(e.reg('rcx'), bytes(e.uc.mem_read(e.reg('rdx'), e.reg('r8'))))
        return e.reg('rcx')

    def convert_string(self):
        e = self.e
        data = bytes(e.uc.mem_read(e.reg('r8'), e.reg('edx')))
        text = data.decode('utf8')
        self.string_conversions.append(dict(plainHex=data.hex(), text=text))
        reader = e.reg('rcx')
        e.write(reader + 28, e.read(reader + 28, 'I') + len(data), 'I')
        return self.string(text)

    def call(self, entry, stops=(), **registers):
        e = self.e
        e.write(STACK + 0x100008, RETURN)
        return fragment(e, entry, {RETURN, 0x1404C2BE0, 0x1404C2650, *stops},
                        dict(rsp=STACK + 0x100008, **registers))

    def parse(self, entry, data, obj=None, length=None):
        e = self.e
        obj = obj or e.alloc(256)
        reader = e.alloc()
        array = e.alloc(32 + len(data))
        e.write(reader + 16, array)
        e.write(array + 24, len(data) if length is None else length, 'I')
        e.uc.mem_write(array + 32, data)
        trace = self.call(entry, rcx=obj, rdx=reader)
        return obj, e.read(reader + 28, 'I'), trace

    def decode(self, ptr):
        trace = self.call(DECODE, rcx=ptr)
        if trace['stop'] != hex(RETURN):
            raise ValueError('Integer decode failed')
        return self.e.reg('eax')


def depot_bytes(first=None, second=None):
    mask = (0x40 if first is not None else 0) | (0x10 if second is not None else 0)
    out = struct.pack('<I', (mask - 0x51fee905) & U32)
    if first is not None:
        out += struct.pack('<I', first ^ 0x2a999229)
    if second is not None:
        out += struct.pack('<I', ((second - 0x5bd78ff) & U32) ^ 0x3361bcab)
    return out


def special_bytes(group=None, params=None, kind=None):
    flags = (0x10 if group is not None else 0) | (1 if params is not None else 0) | (2 if kind is not None else 0)
    out = struct.pack('<H', (flags + 0x6e29) & 65535)
    if group is not None:
        out += struct.pack('<I', (group - 0xca5541cb) & U32)
    if params is not None:
        out += struct.pack('<I', len(params) ^ 0x3cd8aec2)
        out += b''.join(struct.pack('<I', n ^ 0xba816412) for n in params)
    if kind is not None:
        out += struct.pack('<I', (kind - 0x628e0c0c) & U32)
    return out


def condition_bytes(text=None, values=None, kind=20, count=None):
    # Bit 0x10 omits the enum; all fixture enum values are explicit.
    flags = (0x40 if text is not None else 0) | (1 if values is not None else 0) | (4 if count is not None else 0)
    out = bytes([flags])
    if values is not None:
        out += struct.pack('<I', (len(values) - 0xff1a571f) & U32)
        out += b''.join(struct.pack('<I', (n & U32) ^ 0x92aa26ab) for n in values)
    if text is not None:
        raw = text.encode('utf8')
        out += struct.pack('<H', (len(raw) - 0xe3c6) & 65535)
        key = struct.pack('<Q', 0x1ddafe8070dbe3c6)
        out += bytes(x ^ key[i % 8] for i, x in enumerate(raw))
    out += struct.pack('<I', kind ^ 0x46410f90)
    if count is not None:
        out += struct.pack('<I', count ^ 0x43c36bba)
    return out


def run_native(rt):
    e = rt.e
    depots, specials, conditions, errors, consumers = [], [], [], [], []
    for first, second in ((None, None), (0, 0), (123, None), (None, 456), (U32, 0x80000001)):
        obj = e.alloc()
        for off in (0x68, 0x6c):
            rt.call(ENCODE, rcx=obj + off, rdx=777)
        before = bytes(e.uc.mem_read(obj, 120))
        ctor = rt.call(0x148328B50, rcx=obj)
        if before != bytes(e.uc.mem_read(obj, 120)):
            raise ValueError('Constructor no longer leaves existing storage unchanged')
        data = depot_bytes(first, second)
        _, position, trace = rt.parse(DEPOT, data, obj)
        decoded = [rt.decode(obj + off) for off in (0x68, 0x6c)]
        if decoded != [first or 0, second or 0] or position != len(data) or trace['stop'] != hex(RETURN):
            raise ValueError('Depot scalar independent expectation failed')
        depots.append(dict(first=first, second=second, binaryHex=data.hex(), decoded=decoded,
                           bytesConsumed=position, priorLogicalValue=777, ctor=ctor, trace=trace))
    for group, params, kind in ((None, None, None), (0, [], 0), (901, [30302, 0x80000001], 1), (U32, [U32], 1), (17, [30302], 777)):
        data = special_bytes(group, params, kind)
        obj, position, trace = rt.parse(SPECIAL, data)
        arr = e.read(obj + 16)
        actual_params = [rt.decode(arr + 32 + i * 4) for i in range(e.read(arr + 24, 'I'))] if arr else []
        actual = dict(group=rt.decode(obj + 28), params=actual_params, kind=e.read(obj + 24, 'I'))
        if actual != dict(group=group or 0, params=params or [], kind=kind or 0) or position != len(data):
            raise ValueError('Nested special row independent expectation failed')
        lookup = rt.call(0x15113CF70, rcx=obj)
        expected = params[0] if kind == 1 and params else 0
        if e.reg('eax') != expected or lookup['stop'] != hex(RETURN):
            raise ValueError('Quest-finish parameter query differs')
        specials.append(dict(input=dict(group=group, params=params, kind=kind), binaryHex=data.hex(),
                             actual=actual, queriedParameter=expected, bytesConsumed=position, trace=trace, lookup=lookup))
    for text, values, kind, count in ((None, None, 20, None), ('', [], 20, 0), ('2101001', [-1, 0, 2147483647], 20, U32),
                                      ('1,24', [17], 18, 2), ('\u4efb\u52a1\0x', [30302, 0], 20, 1)):
        data = condition_bytes(text, values, kind, count)
        obj, position, trace = rt.parse(CONDITION, data)
        arr = e.read(obj + 16)
        actual_values = [e.read(arr + 32 + i * 4, 'i') for i in range(e.read(arr + 24, 'I'))] if arr else []
        string = e.read(obj + 24)
        actual = dict(text=rt.strings.get(string, ''), values=actual_values, kind=e.read(obj + 36, 'I'), count=e.read(obj + 32, 'I'))
        if actual != dict(text=text or '', values=values or [], kind=kind, count=count or 0) or position != len(data):
            raise ValueError('Condition parser independent expectation failed')
        conditions.append(dict(input=dict(text=text, values=values, kind=kind, count=count), binaryHex=data.hex(),
                               actual=actual, bytesConsumed=position, trace=trace))
        if text == '1,24':
            # Stop at the genuine System.String.Split call, before any library model.
            out = e.alloc()
            consume = rt.call(0x149987A80, {0x159AACF80}, rcx=e.alloc(), rdx=obj, r8=out)
            separator = e.read(e.reg('rdx') + 32, 'H')
            if consume['stop'] != '0x159aacf80' or e.reg('rcx') != string or separator != ord(','):
                raise ValueError('Actual condition text consumer differs')
            consumers.append(dict(name='condition-string-comma-split-boundary', text=text, separator=separator, trace=consume,
                                  gameTimeEvaluationExecuted=False, luaNotifyEvaluationExecuted=False))
    for name, entry, data, length in (
        ('depot-empty', DEPOT, b'\0', 0), ('depot-missing-scalar', DEPOT, depot_bytes(123)[:4], 4),
        ('special-missing-id', SPECIAL, special_bytes(901)[:2], 2),
        ('condition-missing-enum', CONDITION, b'\0', 1)):
        _, position, trace = rt.parse(entry, data, length=length)
        if trace['stop'] != '0x1404c2650':
            raise ValueError('Truncated resource did not hit native bounds helper')
        errors.append(dict(name=name, bytesConsumed=position, trace=trace, exceptionHelperExecuted=False))
    trace = rt.call(DEPOT, rcx=e.alloc(), rdx=0)
    if trace['stop'] != '0x1404c2be0':
        raise ValueError('Null reader did not reach native null helper')
    errors.append(dict(name='depot-null-reader', trace=trace, exceptionHelperExecuted=False))
    return dict(depotCases=depots, specialCases=specials, conditionCases=conditions, consumerCases=consumers, rejectionCases=errors)


def selected_declarations(binary, metadata, records):
    ids = (22284, 40302, 69472, 87244, 33481, 21108)
    types = []
    for i in ids:
        r = records[i]
        fields = [dict(name=f['name'], offset=f['offset'], kind=f['type']['kind'],
                       typeDefinition=f['type'].get('typeDefinition'), elementType=f['type'].get('elementType')) for f in r['fields']]
        types.append(dict(index=i, name=r['name'], fields=fields, methods=r['methods']))
    if next(f for f in records[69472]['fields'] if f['name'] == 'BGHPAKBEPND')['offset'] != 24:
        raise ValueError('Condition string field changed')
    enums = recover_enums(binary, metadata, records, indices=(36721, 85159, 75148, 56833))['enums']
    literals = Literals72(binary, metadata)
    selected = []
    quest_excel = []
    for item in literals.group(literals.global_group):
        text = literals.decode(item['index'])
        if text == 'Data/_ExcelBinOutput/QuestExcelConfigData':
            quest_excel.append(item)
        if text in ('Data/_ExcelBinOutput/AvatarSkillDepotExcelConfigData', 'Data/_BinOutput/IndexDic/MainQuestIndex'):
            selected.append(dict(item, value=text))
    pe = PE(binary)
    entries = sorted({int(m['registeredAddress'], 16) for r in records for m in r['methods'] if m['registeredAddress'] != '0x0'})
    ends = dict(zip(entries, entries[1:]))
    functions = []
    for va in (DEPOT, SPECIAL, CONDITION, ENCODE, DECODE, 0x148328B50, 0x15113CF70, 0x149987A80, 0x1487CBB80):
        size = ends[va] - va
        raw = binary[pe.offset(va, size):pe.offset(va, size) + size]
        functions.append(dict(entry=hex(va), nextRegisteredEntry=hex(ends[va]), bytes=size, sha256=hashlib.sha256(raw).hexdigest()))
    return dict(types=types, enums=enums, selectedLiterals=selected,
                exactQuestExcelLiteralInGlobalGroup=quest_excel,
                negativeSearchScope='Exact string in the decoded global literal usage group only; not all dynamically formed paths',
                nativeFunctions=functions)


def selected_candidate():
    quests = json.loads(Path(next(iter(INPUTS))).read_text('utf8'))
    row = next(x for x in quests if x['subId'] == 30302)
    chapter = json.loads(Path(list(INPUTS)[1]).read_text('utf8'))
    child = next(x for x in chapter['subQuests'] if x['subId'] == 30302)
    return dict(commit='d54f6f0d423ded9d055bb740a3e5f3aa8f2667f1', rootTree='5b334e711319726c504da95dbfe6ebb26baaa7d3',
                releaseLabel='CNRELWin7.2.0_R49086080_S49210135_D49230962',
                selectedSubId=30302, excelCanonicalSHA256=hashlib.sha256(json.dumps(row, sort_keys=True, separators=(',', ':')).encode()).hexdigest(),
                binCanonicalSHA256=hashlib.sha256(json.dumps(child, sort_keys=True, separators=(',', ':')).encode()).hexdigest(),
                excelShapes={k: type(row.get(k)).__name__ if k in row else 'absent' for k in ('acceptCondComb', 'finishCondComb', 'failCondComb', 'acceptCond')},
                binAcceptCondPresent='acceptCond' in child, JSONToNativeFieldBridgeVerified=False,
                automaticAcceptanceRecoverableFromTheseInputs=False, aliasesApproved=[])


def compact(value):
    if isinstance(value, list):
        return [compact(x) for x in value]
    if isinstance(value, dict):
        return {k: compact(v) for k, v in value.items() if k not in ('executedAddresses', 'liveIn', 'binaryHex')}
    return value


if __name__ == '__main__':
    main()
