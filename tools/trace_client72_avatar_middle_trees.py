"""F-03d3e5: five Avatar middle nodes, nonempty leaf lists and signed int64."""
import argparse
import hashlib
import json
from pathlib import Path
try:
    from .trace_client72_avatar_signed_trees import (AvatarSignedTrees72, install_types, initialize_types, scan_types, selected_aot, dependency_audit,
        HASHES, read_verified, extract, type_cells, core_aot, string_map_aot, INVENTORY_SHA, Offline72, varint)
    from .trace_client72_avatar_signed_trees import fixtures as signed_fixtures
    from .trace_client72_avatar_primitive_leaves import fixtures as primitive_fixtures
except ImportError:
    from trace_client72_avatar_signed_trees import (AvatarSignedTrees72, install_types, initialize_types, scan_types, selected_aot, dependency_audit,
        HASHES, read_verified, extract, type_cells, core_aot, string_map_aot, INVENTORY_SHA, Offline72, varint)
    from trace_client72_avatar_signed_trees import fixtures as signed_fixtures
    from trace_client72_avatar_primitive_leaves import fixtures as primitive_fixtures

TYPES = {87867, 57431, 29317, 45040, 63939}
CHILDREN = {21103, 89623, 65248, 19829, 66229, 12876}


def middle_aot(binary, metadata, startup, records):
    concrete = {17213: 21103, 6111: 89623, 14050: 19829, 34712: 66229, 6010: 12876}
    return message_list_aot(binary, metadata, startup, records, concrete)


def message_list_aot(binary, metadata, startup, records, concrete):
    rows = selected_aot(binary, metadata, startup, records, {(25060, x): ('.ctor', 'FNNADLCNGFO') for x in concrete})
    if len(rows) != len(concrete) * 2: raise ValueError('Middle message-list AOT count drift')
    for instance, child in concrete.items():
        hits = [x for x in rows if x['classInstance'] == instance and x['method'] == 'FNNADLCNGFO']
        if len(hits) != 1 or hits[0]['address'] != '0x158dc41f0' or hits[0]['arguments'][0].get('typeDefinition') != child: raise ValueError('Middle message-list AOT binding drift')
    return rows


class AvatarMiddleTrees72(AvatarSignedTrees72):
    def __init__(self, engine, directory, cells, bindings, items, strings):
        super().__init__(engine, directory, cells, bindings, items, strings)
        install_types(self, cells, TYPES); self.middle_initializers = initialize_types(self, TYPES)

    def new_message(self, index, address=None):
        if index in TYPES: return self.new_subtree(index, address)
        return super().new_message(index, address)

    def snapshot(self, pointer, depth=0):
        if pointer and self.objects[pointer] in TYPES: return self.subtree_snapshot(pointer, depth)
        return super().snapshot(pointer, depth)


def message(tag, payload): return varint(tag << 3 | 2) + varint(len(payload)) + payload


def scalar(tag, value): return varint(tag << 3) + varint(value & ((1 << 64) - 1))


def fixtures():
    known = {x['name']: x for x in signed_fixtures() + primitive_fixtures()}
    leaf = known['all-fields-66229']; leaf_wire = bytes.fromhex(leaf['wire']); leaf_value = leaf['expected']
    float_leaf = known['all-fields-12876']; float_wire = bytes.fromhex(float_leaf['wire']); float_value = float_leaf['expected']
    node_a = message(1, leaf_wire); value_a = {'OCICCDJIIEO': leaf_value}
    alternate_leaf = bytes.fromhex('080b1016182122020102'); alternate_value = {'KMBPJIKEDGD': 11, 'KBDNIGMHCBM': 22, 'PPJFLLGKNAH': 33, 'FEKDEEENNBB': [1, 2]}
    node_b = message(2, alternate_leaf) + message(2, leaf_wire); value_b = {'GPDEGHEDMHM': [alternate_value, leaf_value]}
    large_wire = b''; large = {}
    for tag, index, name, alt, expected in [(3, 89623, 'NKBMECCPKPJ', '080b4a0107', {'ANKEOLKBECN': 11, 'MLKKEOCOAKN': [7]}),
        (4, 19829, 'BGKOAMHLPPK', '1007', {'HOPABANFAJI': 7}),
        (8, 21103, 'PLPNCLOFNIC', '080b180c', {'PJAJDHBBMGB': 11, 'IAHPPLKIIBK': 12})]:
        child = known['all-fields-' + str(index)]; large_wire += message(tag, bytes.fromhex(child['wire'])) + message(tag, bytes.fromhex(alt)); large[name] = [child['expected'], expected]
    for tag, name, value in [(1, 'HOPABANFAJI', 9007199254740993), (2, 'NIHNNHMGENL', 18446744073709551615),
        (5, 'ICMFFOLOBDL', True), (7, 'IBEBCEIBAEC', -77), (9, 'ALKNGMBFIKO', -99), (11, 'JMKGJDGGBLM', -121),
        (12, 'KFKNPMLLODH', -132), (13, 'PBHALLIPDIP', 143), (14, 'IKGNHBFNDDK', 154), (15, 'EBEOEICOFOK', 165),
        (16, 'JMICLCAEOPO', 176), (17, 'PEDCHFIHFML', 187), (18, 'GFIJIOEBBAG', -198)]:
        large_wire += scalar(tag, value); large[name] = value
    large_wire += message(6, bytes.fromhex('008001ffffffff0f')) + message(10, bytes.fromhex('0a03018001'))
    large['KPDMPKFJHED'] = [0, 128, 4294967295]; large['HPHMLMEMKLB'] = {'HGFBPFCEACB': [1, 128]}
    cases = [dict(name='all-fields-29317', typeIndex=29317, wire=node_a.hex(), expected=value_a),
        dict(name='all-fields-45040', typeIndex=45040, wire=node_b.hex(), expected=value_b),
        dict(name='all-fields-57431', typeIndex=57431, wire=(message(1, node_a) + message(2, node_b)).hex(), expected={'DHLPKMNMONN': value_a, 'HAAGBOCHHBH': value_b}),
        dict(name='all-fields-63939', typeIndex=63939, wire=(scalar(1, -9223372036854775808) + message(2, float_wire) + message(2, bytes.fromhex('082c1201011a040000a0bf2058'))).hex(),
            expected={'ANKEOLKBECN': -9223372036854775808, 'OCJGKKLACBO': [float_value, {'NOEEBKJAFAG': 44, 'MDAMHOLGONG': [1], 'HMODGFNMPML': [-1.25], 'LPJMAOFKBPI': 88}]}),
        dict(name='all-fields-87867', typeIndex=87867, wire=large_wire.hex(), expected=large),
        dict(name='57431-repeated-singular-merges-and-list-appends', typeIndex=57431,
            wire=(message(1, node_a) + message(2, node_b) + message(1, message(1, alternate_leaf)) + message(2, message(2, b''))).hex(),
            expected={'DHLPKMNMONN': {'OCICCDJIIEO': dict(alternate_value, FEKDEEENNBB=[4, 128, 1, 2])}, 'HAAGBOCHHBH': {'GPDEGHEDMHM': [alternate_value, leaf_value, {}]}}),
        dict(name='87867-two-u64-high-bits-swapped', typeIndex=87867, wire=(scalar(1, 18446744073709551615) + scalar(2, 9223372036854775808)).hex(), expected={'HOPABANFAJI': 18446744073709551615, 'NIHNNHMGENL': 9223372036854775808}),
        dict(name='87867-u32-packed-unpacked', typeIndex=87867, wire='3000320601ffffffff0f', expected={'KPDMPKFJHED': [0, 1, 4294967295]}),
        dict(name='87867-duplicate-signed-bool-u64-zero', typeIndex=87867, wire=(b''.join(scalar(t, -1 if t == 7 else 1) + scalar(t, 0) for t in (1, 2, 5, 7, 13))).hex(), expected={}),
        dict(name='63939-wrong-int64-fixed64-skipped', typeIndex=63939, wire='090100000000000000', expected={}),
        dict(name='57431-explicit-empty-children', typeIndex=57431, wire='0a001200', expected={'DHLPKMNMONN': {}, 'HAAGBOCHHBH': {}})]
    for value in (-9223372036854775808, 9223372036854775807, -1, 9007199254740993): cases.append(dict(name='int64-' + str(value), typeIndex=63939, wire=scalar(1, value).hex(), expected={'ANKEOLKBECN': value}))
    cases.append(dict(name='int64-duplicate-zero', typeIndex=63939, wire=(scalar(1, -1) + scalar(1, 0)).hex(), expected={}))
    for index in sorted(TYPES): cases.append(dict(name='empty-' + str(index), typeIndex=index, wire='', expected={}))
    return cases


def verify(graph):
    good = []
    for case in fixtures():
        row = graph.run_graph(case['typeIndex'], bytes.fromhex(case['wire']))
        if row['result'] != case['expected']: raise ValueError(case['name'] + ': middle subtree native result differs ' + str(row['result']))
        good.append(dict(case, observation=row))
    rejected = []
    for name, index, wire, scope, limit in [('nonempty-child-51050', 34045, '2a020801', 'explicit-scope-refusal', 100),
        ('invalid-tag', 87867, '00', 'explicit-input-model', 100), ('int64-overflow', 63939, '08ffffffffffffffffff02', 'explicit-input-model', 100),
        ('middle-signed-child-truncated', 87867, '1a034a0180', 'explicit-input-model', 100),
        ('negative-middle-list-length', 87867, '1affffffff0f', 'native-error-entry', 100), ('truncated-middle-list', 87867, '1a030801', 'native-error-entry', 100),
        ('middle-list-recursion-limit', 63939, '1200', 'native-error-entry', 0), ('deep-singular-recursion-limit', 57431, '0a020a00', 'native-error-entry', 1)]:
        try: graph.run_graph(index, bytes.fromhex(wire), limit)
        except ValueError as error:
            text = str(error)
            if scope == 'explicit-scope-refusal' and 'Nonempty child outside F-03d3e2' not in text: raise
            if scope == 'native-error-entry' and 'Native error branch:' not in text: raise
            rejected.append(dict(name=name, typeIndex=index, wire=wire, scope=scope, error=text))
        else: raise ValueError('Invalid/out-of-scope middle subtree input accepted: ' + name)
    return good, rejected


def arguments(description):
    p = argparse.ArgumentParser(description=description); p.add_argument('--client', required=True, type=Path)
    p.add_argument('--output', required=True, type=Path); p.add_argument('--observations', required=True, type=Path)
    p.add_argument('--directory', type=Path, default=Path('protocol/cn-7.2')); p.add_argument('--python-libs', type=Path, default=Path('local/migration72/python')); a = p.parse_args()
    paths = [a.output.resolve(), a.observations.resolve()]
    if len(set(paths)) != 2 or any(x.is_relative_to(a.client.resolve()) for x in paths): p.error('Distinct outputs outside client required')
    return a, paths


def inputs(a):
    source = (a.directory / 'inventory-graph-observations.json').read_bytes()
    if hashlib.sha256(source).hexdigest() != INVENTORY_SHA: raise ValueError('Reused Item evidence drift')
    items = {x['typeIndex']: x for x in json.loads(source)['messages'] if x['typeIndex'] != 68537}
    b, m, s = [read_verified(a.client / name, digest) for name, digest in HASHES.items()]; records, _ = extract(b, m, startup=s, structures=True)
    cells = type_cells(b, m, s, records); bindings = core_aot(b, m, s, records); strings = string_map_aot(b, m, s, records)
    return b, m, s, records, cells, bindings, strings, items


def write_reports(paths, context, good):
    summary = dict(context, fixtures=[dict(name=x['name'], typeIndex=x['typeIndex'], wire=x['wire'], **{k: v for k, v in x['observation'].items() if k not in ('typeIndex', 'wire', 'nestedCalls')}) for x in good])
    for path, data in zip(paths, [dict(context, fixtures=good), summary]):
        path.parent.mkdir(parents=True, exist_ok=True); path.write_text(json.dumps(data, indent=2) + '\n', 'utf8')


def main():
    a, paths = arguments(__doc__); b, m, s, records, cells, bindings, strings, items = inputs(a)
    aot = middle_aot(b, m, s, records); audit = dependency_audit(b, records, cells, TYPES, CHILDREN, 24, a.python_libs)
    e = Offline72(b, a.python_libs, records, trace_instructions=False); graph = AvatarMiddleTrees72(e, a.directory, cells, bindings, items, strings)
    try: scan = scan_types(graph, TYPES, 24); good, rejected = verify(graph)
    finally: graph.close(); e.close()
    context = dict(schemaVersion=1, task='F-03d3e5', inputSHA256=HASHES, runtimeStatus='not-tested', targetProtocolReady=False,
        semanticIdentityStatus='unresolved', fullAvatarRawClosure=False, fullComplexRawClosure=False, blockedTypeIndices=[51050],
        aotBindings=aot, initializers=graph.middle_initializers, dependencyScope=audit,
        messages=[graph.schemas[i] for i in sorted(TYPES)], scan=scan, nativeFixtures=len(good), rejections=rejected,
        limitations=['Five middle nodes only; nonempty leaves reuse e3/e4;51050 nonempty remains refused',
            'Managed metadata/allocation, primitive inputs/codecs/delegates, boxing, and fixed64 arrays remain explicit models',
            'No full complex/Avatar graph, business semantic promotion, live acceptance or production integration'])
    write_reports(paths, context, good)
    print(json.dumps(dict(messages=5, wireFields=24, scanFixtures=scan['nativeFixtures'], nativeFixtures=len(good), rejections=len(rejected))))


if __name__ == '__main__': main()
