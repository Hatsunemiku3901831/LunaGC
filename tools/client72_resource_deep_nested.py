"""Independent follow-up: native depot-to-special-condition array parser."""
import argparse
import hashlib
import json
import struct
from pathlib import Path

try:
    from .client72_resource_deep import ResourceRuntime, DEPOT, SPECIAL, RETURN, U32, HASHES, read_verified, extract, special_bytes, compact
    from .trace_client72_player_store import type_cells
except ImportError:
    from client72_resource_deep import ResourceRuntime, DEPOT, SPECIAL, RETURN, U32, HASHES, read_verified, extract, special_bytes, compact
    from trace_client72_player_store import type_cells

PRIOR = {
    'tools/client72_resource_deep.py': '95787d9e4cf3b941bb17045986ffa6234352818bb89e0d02ece9094b1c78e265',
    'protocol/cn-7.2/resource-deep-observations.json': '5d0f7487de6bc4c1a5b5d43460d6e4c70152d6d8613ba16c764a2359246c69d7',
}


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--client', required=True, type=Path)
    p.add_argument('--output', required=True, type=Path)
    p.add_argument('--summary', required=True, type=Path)
    p.add_argument('--python-libs', type=Path, default=Path('local/migration72/python'))
    a = p.parse_args()
    if a.output.resolve() == a.summary.resolve() or any(x.resolve().is_relative_to(a.client.resolve()) for x in (a.output, a.summary)):
        p.error('Distinct outputs outside client required')
    for path, expected in PRIOR.items():
        if hashlib.sha256(Path(path).read_bytes()).hexdigest() != expected:
            raise ValueError('Frozen predecessor changed: ' + path)
    binary, metadata, startup = [read_verified(a.client / n, h) for n, h in HASHES.items()]
    records, _ = extract(binary, metadata, startup=startup, structures=True)
    cells = type_cells(binary, metadata, startup, records)
    selected = [cells[x] for x in (0x1458A5698, 0x1458B97E8, 0x1458973E8)]
    if [x['type'].get('typeDefinition', x['type'].get('elementType', {}).get('typeDefinition')) for x in selected] != [40302, 40302, 58209]:
        raise ValueError('Nested parser allocation type cells changed')
    method = next(x for x in records[40302]['methods'] if x['registeredAddress'] == hex(SPECIAL))
    if method['virtualSlot'] != 4:
        raise ValueError('Element parser virtual slot changed')
    rt = NestedRuntime(binary, a.python_libs, records)
    try:
        cases, rejections = run_nested(rt)
        report = dict(schemaVersion=1, task='E-01/R3-resource-deep-nested', inputSHA256=HASHES, priorInputs=PRIOR,
                      allocationTypeCells=selected, elementParser=method, cases=cases, rejections=rejections,
                      allocationModels=rt.allocations, objectModels=rt.object_models, typeCheckModels=rt.type_checks,
                      resourceReady=False, targetProtocolReady=False, runtimeStatus='not-tested',
                      JSONToNativeFieldBridgeVerified=False, aliasesApproved=[],
                      limitations=[
                          'Native depot parser, array wrapper, virtual element parser and per-element query execute; no client process or file IO.',
                          'Managed allocation/vtable/type-registry storage is a fixture bound to independently decoded class/array cells.',
                          'Runtime type compatibility is an exact fixture comparison with a deliberate wrong-class negative; IL2CPP type helper internals are not executed.',
                          'Synthetic binary missing/empty objects do not establish candidate JSON serialization or actual installed resource values.',
                          'Element group output is only a neutral value label; no proudSkillGroupId/energySkill/BGH alias is approved.'
                      ])
    finally:
        rt.close()
    names = ['tools/client72_resource_deep_nested.py', 'tools/client72_resource_deep.py', 'tools/client72_emulator.py',
             'tools/client72_session_runtime.py', 'tools/extract_client72.py', 'tools/trace_client72_player_store.py']
    report['sourceSHA256'] = {n: hashlib.sha256(Path(n).read_bytes()).hexdigest() for n in names}
    for path, result in ((a.output, report), (a.summary, compact(report))):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(result, indent=2) + '\n', 'utf8')
    print(json.dumps(dict(cases=len(cases), rejections=len(rejections))))


class NestedRuntime(ResourceRuntime):
    def __init__(self, binary, libraries, records):
        super().__init__(binary, libraries, records)
        e = self.e
        e.allow(0x15113CE30)
        self.row_class = e.alloc(512)
        self.wrong_class = e.alloc(512)
        self.array_class = e.alloc(512)
        self.value_array_class = e.alloc(512)
        for klass in (self.row_class, self.wrong_class):
            e.write(klass + 0xf0, SPECIAL)
        e.write(0x1458B97E8, self.row_class)
        e.write(0x1458A5698, self.array_class)
        e.write(0x1458973E8, self.value_array_class)
        registry = e.alloc(64)
        self.expected_type = registry + 16
        registry_slot = e.alloc()
        e.write(registry_slot, registry)
        e.write(0x145BC8AD0, registry_slot)
        e.write(self.array_class + 0xac, 16, 'I')
        self.mode = 'normal'
        self.object_models = []
        self.type_checks = []
        e.models[0x1405336C0] = self.new_row
        e.models[0x14051B7B0] = self.assignable

    def allocate_array(self):
        e = self.e
        if e.read(e.reg('rsp')) - 5 != 0x15113CEA3:
            return super().allocate_array()
        count = e.reg('rdx')
        if e.reg('rcx') != self.array_class or count > 2:
            raise ValueError('Outside declared class-array fixture')
        self.allocations.append(dict(site='0x15113cea3', count=count, element='class40302', width=8,
                                     scope='Managed reference-array storage model from decoded type cell'))
        if self.mode == 'null-array':
            return 0
        result = self.array(count, 8)
        e.write(result, self.array_class)
        return result

    def new_row(self):
        e = self.e
        if e.reg('rcx') != self.row_class:
            raise ValueError('Unexpected object allocation type')
        if self.mode == 'null-object':
            return 0
        result = e.alloc(32)
        klass = self.wrong_class if self.mode == 'wrong-class' else self.row_class
        e.write(result, klass)
        self.object_models.append(dict(type=40302, deliberateWrongClass=self.mode == 'wrong-class'))
        return result

    def assignable(self):
        e = self.e
        if e.reg('rcx') != self.expected_type:
            raise ValueError('Unexpected element type reference')
        actual = e.reg('rdx')
        if actual not in (self.row_class, self.wrong_class):
            raise ValueError('Undeclared class compatibility check')
        result = actual == self.row_class
        self.type_checks.append(dict(expectedType=40302, fixtureClassMatches=result))
        return int(result)

    def call(self, entry, stops=(), **registers):
        return super().call(entry, (*stops, 0x1404C2C70), **registers)


def nested_bytes(rows=None):
    mask = 0 if rows is None else 0x20
    out = struct.pack('<I', (mask - 0x51fee905) & U32)
    if rows is not None:
        out += struct.pack('<I', ((len(rows) ^ 0x5961d79e) - 0x46b6c271) & U32)
        out += b''.join(special_bytes(*row) for row in rows)
    return out


def run_nested(rt):
    e = rt.e
    cases = []
    for name, rows in (
        ('omitted', None), ('empty', []), ('two-default-elements', [(None, None, None), (None, None, None)]),
        ('nonzero-and-highbit', [(901, [30302, 0x80000001], 1), (U32, [U32], 1)]),
        ('nonmatching-and-empty-condition', [(17, [30302], 777), (18, [], 1)]),
    ):
        data = nested_bytes(rows)
        obj, position, trace = rt.parse(DEPOT, data)
        trace['nativeEntryVisits'] = {hex(v): trace['executedAddresses'].get(hex(v), 0) for v in (DEPOT, 0x15113CE30, SPECIAL)}
        array = e.read(obj + 16)
        result = []
        for i in range(e.read(array + 24, 'I')):
            row = e.read(array + 32 + i * 8)
            params = e.read(row + 16)
            values = [rt.decode(params + 32 + j * 4) for j in range(e.read(params + 24, 'I'))]
            group, kind = rt.decode(row + 28), e.read(row + 24, 'I')
            lookup = rt.call(0x15113CF70, rcx=row)
            result.append(dict(group=group, params=values, kind=kind, queriedParameter=e.reg('eax'), query=lookup))
        expected = [dict(group=g or 0, params=p or [], kind=k or 0,
                         queriedParameter=p[0] if k == 1 and p else 0) for g, p, k in rows or []]
        if [{k: v for k, v in x.items() if k != 'query'} for x in result] != expected or position != len(data) or trace['stop'] != hex(RETURN):
            raise ValueError('Complete nested parser differs from independent expected rows')
        cases.append(dict(name=name, expected=expected, actual=result, bytesConsumed=position, binaryHex=data.hex(), trace=trace))
    errors = []
    for mode, expected in (('wrong-class', '0x1404c2c70'), ('null-object', '0x1404c2be0'), ('null-array', '0x1404c2be0')):
        rt.mode = mode
        _, position, trace = rt.parse(DEPOT, nested_bytes([(901, [30302], 1)]))
        if trace['stop'] != expected:
            raise ValueError('Array error did not reach declared native helper')
        errors.append(dict(name=mode, bytesConsumed=position, trace=trace, exceptionHelperExecuted=False))
    rt.mode = 'normal'
    _, position, trace = rt.parse(DEPOT, nested_bytes([(901, [30302], 1)])[:8])
    if trace['stop'] != '0x1404c2650':
        raise ValueError('Truncated child did not reach bounds helper')
    errors.append(dict(name='truncated-child', bytesConsumed=position, trace=trace, exceptionHelperExecuted=False))
    return cases, errors


if __name__ == '__main__':
    main()
