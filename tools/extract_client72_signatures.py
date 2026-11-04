"""Hash-bound 7.2 declared method parameters, including nested generic arguments.

Native parameter accessor: 0x140532210; decode loop 0x140532349..0x14053254C.
Return types and closed generic method substitutions are explicitly not inferred.
Output uses shared type references to avoid duplicating generic trees per method.
"""
import argparse
import json
from pathlib import Path

try:
    from .extract_client72 import HASHES, PE, Names, TypeResolver, HEADER_VA, BODY, MASK32, MASK64, number, span, read_verified, extract
except ImportError:
    from extract_client72 import HASHES, PE, Names, TypeResolver, HEADER_VA, BODY, MASK32, MASK64, number, span, read_verified, extract


def bounded(value):
    if type(value) is not int or not 0 <= value <= MASK32:
        raise ValueError('Expected uint32')
    return value


def parameter_start(method_index, encoded):
    bounded(method_index); bounded(encoded)
    key = (((method_index * 0x1639) ^ 0x5E01C03B) * 0x106B250C17F34CCB + 0x069FFDCBE218FAF4) & MASK64
    return (encoded - key + 0xC05565F9) & MASK32


def parameter_words(index, encoded_name, encoded_type):
    for value in (index, encoded_name, encoded_type):
        bounded(value)
    key = (((((index * 0x74B075FDA13 + 0x3362D8F50E637A) & MASK64) >> 22)
             ^ 0x599EEE90) + 0x214E875D) & MASK32
    return ((encoded_name + 0xA587B2CD) & MASK32) ^ key, ((encoded_type + 0xDB1D6B24) & MASK32) ^ key


def recover_parameters(metadata, records, method_base, parameter_base, parameter_end, names, resolver):
    span(metadata, parameter_base, parameter_end - parameter_base)
    if (parameter_end - parameter_base) % 8:
        raise ValueError('Unaligned parameter table extent')
    count = (parameter_end - parameter_base) // 8
    methods, used, cursor = [], set(), 0
    for row in records:
        for method in row['methods']:
            n = method['parameterCount']
            if not n:
                continue
            first = parameter_start(method['index'], number(metadata, method_base + method['index'] * 26 + 4))
            if first != cursor or first + n > count:
                raise ValueError(f"Noncontiguous/out-of-bounds parameters for method {method['index']}")
            parameters = []
            for index in range(first, first + n):
                name_index, type_index = parameter_words(index, number(metadata, parameter_base + index * 8),
                                                        number(metadata, parameter_base + index * 8 + 4))
                if type_index != MASK32:
                    resolver.resolve_index(type_index)
                    used.add(type_index)
                parameters.append(dict(index=index, name=names.decode(name_index),
                                       typeIndex=None if type_index == MASK32 else type_index))
            methods.append(dict(index=method['index'], declaringType=row['index'], name=method['name'],
                                registeredAddress=method['registeredAddress'], parameters=parameters))
            cursor += n
    if cursor != count:
        raise ValueError('Parameter spans do not cover the independently bounded table')
    return methods, {str(i): resolver.resolve_index(i) for i in sorted(used)}


def generic_position(index, encoded):
    bounded(index)
    if type(encoded) is not int or not 0 <= encoded <= 65535:
        raise ValueError('Expected generic position word')
    # Target VAR and MVAR inflate paths 0x1404ED9EE..43 / 0x1404EDC8B..E0.
    key = (((index * 0x42BB72A) & MASK32) >> 12) * -0x1980 + 0x5C80
    return ((encoded - 0x41F0) ^ key) & 65535


def annotate_generic_positions(reference, metadata, parameter_base):
    result = dict(reference)
    if reference['kind'] in ('var', 'mvar'):
        index = int(reference['data'], 16)
        result['genericParameterPosition'] = generic_position(index, number(metadata, parameter_base + index * 14 + 6, 'H'))
        result['genericParameterOwnerStatus'] = 'not-recovered'
    if 'arguments' in reference:
        result['arguments'] = [annotate_generic_positions(a, metadata, parameter_base) for a in reference['arguments']]
    if 'elementType' in reference:
        result['elementType'] = annotate_generic_positions(reference['elementType'], metadata, parameter_base)
    return result


def bind_class_parameters(reference, arguments):
    """Only for a method of this exact closed declaring class; never MVAR."""
    if reference['kind'] == 'mvar':
        raise ValueError('Method generic context is not recovered')
    if reference['kind'] == 'var':
        position = reference.get('genericParameterPosition')
        if type(position) is not int or not 0 <= position < len(arguments):
            raise ValueError('Generic parameter position outside class arguments')
        return dict(arguments[position], attributes=reference['attributes'], byRef=reference['byRef'])
    result = dict(reference)
    if 'arguments' in reference:
        result['arguments'] = [bind_class_parameters(a, arguments) for a in reference['arguments']]
    if 'elementType' in reference:
        result['elementType'] = bind_class_parameters(reference['elementType'], arguments)
    return result


def action_callbacks(methods, references):
    invokes = {}
    for method in methods:
        if method['name'] == 'Invoke':
            invokes.setdefault(method['declaringType'], []).append(method)
    result = []
    for index, reference in references.items():
        if reference['kind'] != 'genericinst' or reference.get('namespace') != 'System' or not reference.get('name', '').startswith('Action`'):
            continue
        candidates = invokes.get(reference['typeDefinition'], [])
        if len(candidates) != 1:
            raise ValueError('Action delegate requires one declared Invoke signature')
        method = candidates[0]
        parameters = [bind_class_parameters(references[str(p['typeIndex'])], reference['arguments']) for p in method['parameters']]
        result.append(dict(typeIndex=int(index), declaringType=reference['typeDefinition'], invokeMethod=method['index'],
                           parameters=parameters, returnTypeStatus='not-recovered'))
    return result


def recover(binary, metadata, startup):
    records, summary = extract(binary, metadata, members=True)
    pe = PE(binary); header = pe.offset(HEADER_VA, BODY)
    parameter_base = BODY + ((number(binary, header + 0x174) + 0xAE6BCD68) & MASK32)
    # The following independently decoded field table bounds this whole span.
    parameter_end = int(summary['fieldOffset'], 16)
    resolver = TypeResolver(pe, startup, records, header)
    methods, references = recover_parameters(metadata, records, int(summary['methodOffset'], 16),
        parameter_base, parameter_end, Names(metadata, int(summary['stringBase'], 16)), resolver)
    generic_base = BODY + ((number(binary, header + 0x94) + 0xD97E8A5C) & MASK32)
    references = {i: annotate_generic_positions(r, metadata, generic_base) for i, r in references.items()}
    callbacks = action_callbacks(methods, references)
    report = dict(inputSHA256=HASHES, parameterOffset=hex(parameter_base), parameterEnd=hex(parameter_end),
                  parameterCount=sum(len(m['parameters']) for m in methods), methodsWithParameters=len(methods),
                  distinctParameterTypeReferences=len(references), genericParameterOffset=hex(generic_base),
                  byRefParameterCount=sum(references[str(p['typeIndex'])]['byRef'] for m in methods for p in m['parameters'] if p['typeIndex'] is not None),
                  declaredActionCallbackTypeReferences=len(callbacks),
                  distinctActionInstantiations=len({references[str(c['typeIndex'])]['genericIndex'] for c in callbacks}),
                  returnTypesStatus='not-recovered',
                  genericMethodSubstitutionStatus='not-recovered', runtimeStatus='not-tested')
    return dict(report=report, types=[{k: r[k] for k in ('index', 'name', 'namespace')} for r in records],
                methods=methods, typeReferences=references, actionCallbacks=callbacks)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    root, output = args.client.resolve(), args.output.resolve()
    if output.is_relative_to(root):
        parser.error('Write outside the read-only client')
    files = [read_verified(root / name, sha) for name, sha in HASHES.items()]
    result = recover(*files)
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open('w', encoding='utf8') as stream:
        json.dump(result, stream, ensure_ascii=False, separators=(',', ':'))
        stream.write('\n')
    print(json.dumps(result['report'], indent=2))


if __name__ == '__main__':
    main()
