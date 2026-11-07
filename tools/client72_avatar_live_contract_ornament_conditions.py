"""Native Ornament condition arrays; explicit typed storage, no alias guesses."""
import argparse
import hashlib
import json
import struct
from pathlib import Path

try:
    from . import client72_avatar_live_contract_ornament as outer
    from .client72_resource_deep import ResourceRuntime, RETURN, U32
    from .extract_client72_enums import recover as recover_enums
except ImportError:
    import client72_avatar_live_contract_ornament as outer
    from client72_resource_deep import ResourceRuntime, RETURN, U32
    from extract_client72_enums import recover as recover_enums

base = outer.base
ARRAY, ELEMENT = 0x14E7E6570, 0x14E7E66B0
ARRAY_CELL = 0x14E7E65D0 + 7 - 0x8F3558F
ROW_CELL = 0x14E7E65F0 + 7 - 0x8F355A7
STRINGS_CELL = 0x14E7E6779 + 7 - 0x8F60660


def main():
    p = argparse.ArgumentParser(description=__doc__)
    for name in ('client', 'output', 'observations'):
        p.add_argument('--' + name, type=Path, required=True)
    p.add_argument('--python-libs', type=Path, default=Path('local/migration72/python'))
    p.add_argument('--candidate', type=Path, default=Path('local/migration72/d02-active/resources/candidate/ExcelBinOutput'))
    a = p.parse_args()
    if a.output.resolve() == a.observations.resolve() or any(x.resolve().is_relative_to(a.client.resolve()) for x in (a.output, a.observations)):
        p.error('Distinct output paths outside client required')
    if hashlib.sha256(Path('tools/client72_avatar_live_contract_ornament.py').read_bytes()).hexdigest() != 'e810efee76f619b1b911c5f0aec77cb3fd654afdfbf34b4a6e4aecb6da8c3f89':
        raise ValueError('Frozen Ornament predecessor changed')
    candidate = a.candidate / 'AvatarOrnamentExcelConfigData.json'
    raw = candidate.read_bytes()
    sha256, blob = outer.CANDIDATES[candidate.name]
    if hashlib.sha256(raw).hexdigest() != sha256 or hashlib.sha1(b'blob ' + str(len(raw)).encode() + b'\0' + raw).hexdigest() != blob:
        raise ValueError('Fixed candidate changed')
    b, m, s = [base.read_verified(a.client / name, digest) for name, digest in base.HASHES.items()]
    records, _ = base.extract(b, m, startup=s, structures=True)
    cells = base.type_cells(b, m, s, records)
    declared = declarations(b, m, records, cells, a.python_libs)
    a.output.parent.mkdir(parents=True, exist_ok=True)
    a.output.with_suffix('.static.json').write_text(json.dumps(declared, indent=2) + '\n', 'utf8')
    rt = ConditionRuntime(b, a.python_libs, records)
    try:
        result = probe(rt, json.loads(raw), declared, a)
        models = dict(allocations=rt.allocations, objectAllocations=rt.objects, typeChecks=rt.type_checks,
                      stringConversions=rt.string_conversions)
    finally:
        rt.close()
    names = ('tools/client72_avatar_live_contract_ornament_conditions.py', 'tools/client72_avatar_live_contract_ornament.py',
             'tools/client72_resource_deep.py', 'tools/client72_resource_deep_nested.py', 'tools/client72_emulator.py',
             'tools/client72_session_runtime.py', 'tools/extract_client72.py', 'tools/trace_client72_player_store.py')
    report = dict(task='F-03d3f4d2-ornament-conditions', inputSHA256=base.HASHES, declarations=declared,
        candidateInput=dict(sha256=sha256, gitBlob=blob, bytes=len(raw)), native=result, models=models,
        sourceSHA256={name: hashlib.sha256(Path(name).read_bytes()).hexdigest() for name in names},
        selectedNativeArrayParserComplete=True, candidateJSONAliasesVerified=False, aliasesApproved=[],
        conditionEvaluationVerified=False, completeCacheBuilder=False, candidateRuntimeRevisionVerified=False,
        completeAvatarHandler=False, runtimeStatus='not-tested', targetProtocolReady=False,
        limitations=['Binary fixtures are independently authored. Candidate param/type/unlockCond names are a labelled projection hypothesis, not an approved exporter or consumer bridge.',
            'Object/reference-array/string-array allocations and strict class compatibility reuse the reviewed nested-parser storage model with independently decoded58937 cells.',
            'Native Ornament parser, array wrapper, virtual element parser, string transforms and stores execute. Type compatibility helper internals and OS resource loading do not.',
            'Target enum symbols establish the raw selector identity; ITEM_COUNT_GREATER_EQUAL evaluation/item ownership/resource revision are not executed.',
            'Only short strings and declared at-most-five-element fixtures are admitted by the allocation model; this is not a client resource limit.'])
    a.output.write_text(json.dumps(report, indent=2, ensure_ascii=False) + '\n', 'utf8')
    a.observations.parent.mkdir(parents=True, exist_ok=True)
    a.observations.write_text(json.dumps(outer.compact(report), indent=2, ensure_ascii=False) + '\n', 'utf8')
    print(json.dumps(dict(cases=len(result), selectedNativeArrayParserComplete=True, resourceReady=False)))


def declarations(binary, metadata, records, cells, libraries):
    base.load_emulator(libraries)
    from capstone import Cs, CS_ARCH_X86, CS_MODE_64
    selected = [cells[cell] for cell in (ARRAY_CELL, ROW_CELL, STRINGS_CELL)]
    if selected[0]['type']['elementType']['typeDefinition'] != 58937 or selected[1]['type']['typeDefinition'] != 58937 or selected[2]['type']['elementType']['kind'] != 'string':
        raise ValueError('Typed allocation cells changed')
    method = next(method for method in records[58937]['methods'] if method['registeredAddress'] == hex(ELEMENT))
    if method['virtualSlot'] != 4:
        raise ValueError('Exact element virtual parser changed')
    pe = base.PE(binary)
    optional = base.number(binary, 0x3c) + 24
    rva, size = struct.unpack_from('<II', binary, optional + 112 + 3 * 8)
    ranges = {pe.base + begin: pe.base + end for begin, end, _ in struct.iter_unpack('<III', binary[pe.offset(pe.base + rva):pe.offset(pe.base + rva) + size])}
    decoder = Cs(CS_ARCH_X86, CS_MODE_64)
    functions = {}
    for entry in (ARRAY, ELEMENT):
        end = ranges[entry]
        raw = binary[pe.offset(entry):pe.offset(entry) + end - entry]
        functions[hex(entry)] = dict(end=hex(end), nativeBytesSHA256=hashlib.sha256(raw).hexdigest(),
            instructions=[dict(address=hex(ins.address), instruction=ins.mnemonic + ' ' + ins.op_str) for ins in decoder.disasm(raw, entry)])
    return dict(allocationTypeCells=selected, rowType=records[58937], elementParser=method,
        conditionEnum=recover_enums(binary, metadata, records, indices=(82900,))['enums'][0], functions=functions)


class ConditionRuntime(ResourceRuntime):
    def __init__(self, binary, libraries, records):
        super().__init__(binary, libraries, records)
        e = self.e
        for entry in (outer.ORNAMENT, ARRAY, ELEMENT):
            e.allow_unwind(entry)
        self.row_class, self.wrong_class, self.array_class, self.strings_class = [e.alloc(512) for _ in range(4)]
        for klass in (self.row_class, self.wrong_class):
            e.write(klass + 0xf0, ELEMENT)
        for cell, klass in ((ROW_CELL, self.row_class), (ARRAY_CELL, self.array_class), (STRINGS_CELL, self.strings_class)):
            e.write(cell, klass)
        registry, slot = e.alloc(64), e.alloc(8)
        self.expected_type = registry + 16
        e.write(slot, registry)
        e.write(0x145BC8AD0, slot)
        e.write(self.array_class + 0xac, 16, 'I')
        e.write(0x145B74D50, self.array(0, 8))
        e.write(0x145B744F8, self.array(0, 8))
        self.mode = 'normal'
        self.objects, self.type_checks = [], []
        e.models[0x1405336C0] = self.new_row
        e.models[0x14051B7B0] = self.assignable

    def allocate_array(self):
        e = self.e
        site = e.read(e.reg('rsp')) - 5
        if site not in (0x14E7E65DD, 0x14E7E6786):
            return super().allocate_array()
        expected = self.array_class if site == 0x14E7E65DD else self.strings_class
        count = e.reg('rdx')
        if e.reg('rcx') != expected or count > 5:
            raise ValueError('Outside exact typed condition-array fixture')
        self.allocations.append(dict(site=hex(site), count=count, element='58937' if site == 0x14E7E65DD else 'string', width=8))
        if self.mode == 'null-array' and site == 0x14E7E65DD:
            return 0
        result = self.array(count, 8)
        e.write(result, expected)
        return result

    def new_row(self):
        e = self.e
        if e.reg('rcx') != self.row_class:
            raise ValueError('Unexpected Ornament condition allocation type')
        if self.mode == 'null-object':
            return 0
        pointer = e.alloc(32)
        e.write(pointer, self.wrong_class if self.mode == 'wrong-class' else self.row_class)
        self.objects.append(dict(type=58937, deliberateWrongClass=self.mode == 'wrong-class'))
        return pointer

    def assignable(self):
        e = self.e
        if e.reg('rcx') != self.expected_type or e.reg('rdx') not in (self.row_class, self.wrong_class):
            raise ValueError('Unexpected condition-array type comparison')
        equal = e.reg('rdx') == self.row_class
        self.type_checks.append(dict(expectedType=58937, fixtureClassMatches=equal))
        return int(equal)

    def call(self, entry, stops=(), **registers):
        return super().call(entry, (*stops, 0x1404C2C70), **registers)


def element_bytes(params=None, kind=None):
    flags = (0x10 if params is not None else 0) | (0x80 if kind is not None else 0)
    out = struct.pack('<H', (flags - 0x7881) & 65535)
    if params is not None:
        out += struct.pack('<I', (len(params) - 0x50e7c0e3) & U32)
        key = struct.pack('<Q', 0x9e1ff050437dd6b8)
        for value in params:
            raw = value.encode('utf8')
            out += struct.pack('<H', (len(raw) - 0xd6b8) & 65535)
            out += bytes(value ^ key[index % 8] for index, value in enumerate(raw))
    if kind is not None:
        out += struct.pack('<I', kind ^ 0xb04e4f35)
    return out


def binary_input(values, rows):
    data = outer.ornament_bytes(values, condition_array=rows is not None)
    if rows is None:
        return data
    offset = 2 + (4 if values.get('id') is not None else 0)
    if values.get('text') is not None:
        offset += 2 + len(values['text'].encode('utf8'))
    packed = struct.pack('<I', (len(rows) - 0xeddfd92d) & U32) + b''.join(element_bytes(*row) for row in rows)
    return data[:offset] + packed + data[offset:]


def probe(rt, candidates, declared, args):
    e = rt.e
    symbols = {row['symbol']: row['value'] for row in declared['conditionEnum']['values']}
    cases = [('omitted-array', {}, None), ('empty-array', {}, []),
        ('two-omitted-elements', {}, [(None, None), (None, None)]),
        ('two-independent-nonzero-elements', dict(id=77), [(['220141', '1', '风'], 73), (['4294967295'], U32)]),
        ('five-explicit-empty-parameter-arrays', {}, [([], 0)] * 5)]
    for candidate in candidates:
        rows = [(row.get('param'), symbols[row['type']] if 'type' in row else None) for row in candidate['unlockCond']]
        values = dict(id=candidate['PBMHHJCEJPJ'], text=candidate['DENCEMCMLHM'])
        cases.append(('candidate-shape-projection-' + str(candidate['PBMHHJCEJPJ']), values, rows))
    result = []
    try:
        for name, values, rows in cases:
            data = binary_input(values, rows)
            obj, cursor, trace = rt.parse(outer.ORNAMENT, data)
            array = e.read(obj + 0x10)
            actual = []
            for index in range(e.read(array + 24, 'I')):
                row = e.read(array + 32 + index * 8)
                params = e.read(row + 16)
                actual.append(dict(kind=e.read(row + 24, 'I'),
                    params=[rt.strings[e.read(params + 32 + j * 8)] for j in range(e.read(params + 24, 'I'))]))
            result.append(dict(name=name, input=rows, binaryHex=data.hex(), cursor=cursor, actual=actual,
                outerId=rt.decode(obj + 0x2c), outerText=rt.strings[e.read(obj + 0x18)], trace=trace))
        for mode, stop in (('wrong-class', '0x1404c2c70'), ('null-object', '0x1404c2be0'), ('null-array', '0x1404c2be0')):
            rt.mode = mode
            _, cursor, trace = rt.parse(outer.ORNAMENT, binary_input({}, [(['x'], 73)]))
            if trace['stop'] != stop:
                raise ValueError('Wrong exact array rejection boundary')
            result.append(dict(name=mode, cursor=cursor, trace=trace))
        rt.mode = 'normal'
        for name, data in (('truncated-first-element', binary_input({}, [(None, None)])[:6]),
                           ('truncated-nonzero-kind', binary_input({}, [(None, 73)])[:-4])):
            _, cursor, trace = rt.parse(outer.ORNAMENT, data)
            result.append(dict(name=name, cursor=cursor, trace=trace))
        return result
    except Exception:
        args.output.with_suffix('.failure.json').write_text(json.dumps(dict(completed=result, calls=e.calls,
            registers={name: hex(e.reg(name)) for name in ('rip', 'rcx', 'rdx', 'r8', 'rsp')}), indent=2) + '\n', 'utf8')
        raise


if __name__ == '__main__':
    main()
