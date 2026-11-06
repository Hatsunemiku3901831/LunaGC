"""Bound the known BGH consumer to target GAME_TIME_TICK, without aliasing it."""
import argparse
import hashlib
import json
from pathlib import Path

try:
    from .client72_resource_deep import ResourceRuntime, CONDITION, RETURN, HASHES, read_verified, extract, condition_bytes, compact
    from .client72_resource_deep_nested import PRIOR
    from .extract_client72 import PE, HEADER_VA, BODY, Names, TypeResolver, number
    from .extract_client72_signatures import parameter_start, parameter_words
except ImportError:
    from client72_resource_deep import ResourceRuntime, CONDITION, RETURN, HASHES, read_verified, extract, condition_bytes, compact
    from client72_resource_deep_nested import PRIOR
    from extract_client72 import PE, HEADER_VA, BODY, Names, TypeResolver, number
    from extract_client72_signatures import parameter_start, parameter_words


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
            raise ValueError('Frozen predecessor changed')
    binary, metadata, startup = [read_verified(a.client / n, h) for n, h in HASHES.items()]
    records, extraction = extract(binary, metadata, startup=startup, structures=True)
    declarations = typed_parameters(binary, metadata, startup, records, extraction)
    rt = ResourceRuntime(binary, a.python_libs, records)
    try:
        cases = selectors(rt)
    finally:
        rt.close()
    report = dict(schemaVersion=1, task='E-01/R3-resource-deep-quest-consumer', inputSHA256=HASHES, priorInputs=PRIOR,
                  declarations=declarations, cases=cases, resourceReady=False, targetProtocolReady=False, runtimeStatus='not-tested',
                  aliasesApproved=[], luaNotifyEvaluationExecuted=False,
                  limitations=['Actual condition parser and selector execute over explicit managed parent/array storage.',
                               'Static caller links selector to comma-string consumer after an additional unevaluated predicate.',
                               'Selection excludes enum20 in the tested array; this is not proof of absence of every LuaNotify consumer.',
                               'No JSON conversion, automatic acceptance rules, game-time or Lua gameplay evaluation is implemented.'])
    paths = ['tools/client72_resource_deep_quest.py', 'tools/client72_resource_deep.py', 'tools/client72_resource_deep_nested.py',
             'tools/client72_emulator.py', 'tools/client72_session_runtime.py', 'tools/extract_client72.py', 'tools/extract_client72_signatures.py']
    report['sourceSHA256'] = {n: hashlib.sha256(Path(n).read_bytes()).hexdigest() for n in paths}
    for path, result in ((a.output, report), (a.summary, compact(report))):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(result, indent=2) + '\n', 'utf8')
    print(json.dumps(dict(selectorCases=len(cases))))


def typed_parameters(binary, metadata, startup, records, extraction):
    pe = PE(binary)
    h = pe.offset(HEADER_VA)
    resolver = TypeResolver(pe, startup, records, h)
    names = Names(metadata, int(extraction['stringBase'], 16))
    mb = BODY + (number(binary, h + 0xb0) ^ 0x51324a1f)
    pb = BODY + ((number(binary, h + 0x174) + 0xae6bcd68) & 0xffffffff)
    result = []
    for owner, address in ((55330, '0x1487cbb80'), (56410, '0x149987a80')):
        method = next(x for x in records[owner]['methods'] if x['registeredAddress'] == address)
        first = parameter_start(method['index'], number(metadata, mb + method['index'] * 26 + 4))
        params = []
        for i in range(first, first + method['parameterCount']):
            name, typ = parameter_words(i, number(metadata, pb + i * 8), number(metadata, pb + i * 8 + 4))
            params.append(dict(name=names.decode(name), type=resolver.resolve_index(typ)))
        if not any(x['type'].get('typeDefinition') == 69472 for x in params):
            raise ValueError('Condition consumer declared type changed')
        result.append(dict(owner=owner, method=method, parameters=params))
    # Decode only two direct call instructions at the already located actual caller.
    sites = []
    for site, target in ((0x149988628, 0x1487CBB80), (0x14998865C, 0x149987A80)):
        raw = binary[pe.offset(site, 5):pe.offset(site, 5) + 5]
        if raw[0] != 0xe8 or site + 5 + int.from_bytes(raw[1:], 'little', signed=True) != target:
            raise ValueError('Condition caller edge changed')
        sites.append(dict(site=hex(site), target=hex(target), bytesSHA256=hashlib.sha256(raw).hexdigest()))
    return dict(typedMethods=result, caller='0x1499882f0', directCalls=sites,
                interveningPredicate='0x1487ca860; not executed', interpretation='static conditional caller, not a full runtime path')


def selectors(rt):
    e = rt.e
    result = []
    for name, kinds, missing_parent, missing_array in (
        ('lua-only', [20], False, False), ('time-only', [18], False, False),
        ('lua-then-time', [20, 18], False, False), ('first-of-two-time', [18, 18], False, False),
        ('empty', [], False, False), ('null-parent', [], True, False),
        ('null-array', [], False, True), ('null-element', [None], False, False),
    ):
        value = e.alloc(16)
        parent = e.alloc(256)
        arr = rt.array(len(kinds), 8)
        pointers = []
        for i, kind in enumerate(kinds):
            if kind is None:
                pointers.append(0)
            else:
                obj, _, trace = rt.parse(CONDITION, condition_bytes('1,24', [i + 1], kind, 1))
                if trace['stop'] != hex(RETURN):
                    raise ValueError('Real condition construction failed')
                pointers.append(obj)
            e.write(arr + 32 + i * 8, pointers[-1])
        e.write(parent + 0x48, 0 if missing_array else arr)
        e.write(value + 8, 0 if missing_parent else parent)
        output = e.alloc()
        e.write(output, 0x777)
        trace = rt.call(0x1487CBB80, rcx=value, rdx=output)
        expected_index = kinds.index(18) if 18 in kinds else None
        expected_stop = '0x1404c2be0' if missing_array or None in kinds else hex(RETURN)
        chosen = e.read(output)
        if trace['stop'] != expected_stop or chosen != (pointers[expected_index] if expected_index is not None else 0):
            raise ValueError('Native selector output/throw differs')
        if expected_stop == hex(RETURN) and e.reg('al') != int(expected_index is not None):
            raise ValueError('Native selector boolean differs')
        result.append(dict(name=name, enumValues=kinds, selectedIndex=expected_index, selected=chosen != 0,
                           outputClearedBeforeSearch=True, trace=trace, errorHelperExecuted=False))
    return result


if __name__ == '__main__':
    main()
