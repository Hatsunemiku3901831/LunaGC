"""F-03d3e6: two large Avatar nodes with signed lists and distinct children."""
try:
    from .trace_client72_avatar_middle_trees import (AvatarMiddleTrees72, message_list_aot, arguments, inputs, write_reports, message, scalar,
        install_types, initialize_types, scan_types, dependency_audit, Offline72, HASHES, json, varint)
    from .trace_client72_avatar_primitive_leaves import fixtures as primitive_fixtures
except ImportError:
    from trace_client72_avatar_middle_trees import (AvatarMiddleTrees72, message_list_aot, arguments, inputs, write_reports, message, scalar,
        install_types, initialize_types, scan_types, dependency_audit, Offline72, HASHES, json, varint)
    from trace_client72_avatar_primitive_leaves import fixtures as primitive_fixtures

TYPES = {58639, 64949}
CHILDREN = {51614, 48088, 68918, 21518}


class AvatarLargeNodes72(AvatarMiddleTrees72):
    def __init__(self, engine, directory, cells, bindings, items, strings):
        super().__init__(engine, directory, cells, bindings, items, strings)
        install_types(self, cells, TYPES); self.large_initializers = initialize_types(self, TYPES)

    def new_message(self, index, address=None):
        if index in TYPES: return self.new_subtree(index, address)
        return super().new_message(index, address)

    def snapshot(self, pointer, depth=0):
        if pointer and self.objects[pointer] in TYPES: return self.subtree_snapshot(pointer, depth)
        return super().snapshot(pointer, depth)


def fixtures():
    known = {x['name']: x for x in primitive_fixtures()}
    child = known['all-fields-51614']; small = known['all-fields-21518']; pair = known['all-fields-68918']
    big_wire = message(4, scalar(1, -1) + scalar(2, 2)) + message(5, scalar(1, -3) + scalar(2, 4))
    big = {'HHAFHFEPLPD': {'CJMJBINEBAG': -1, 'ALOKJBKPLGA': 2}, 'GFKJKLIHGNK': {'CJMJBINEBAG': -3, 'ALOKJBKPLGA': 4}}
    for tag, name, value in [(1, 'ANKEOLKBECN', 11), (2, 'KOCFLGMCFJF', 22), (3, 'LCIJBEMNLCM', True), (6, 'DACMFIDFIJA', -66),
        (7, 'DPILFJFOJMD', 77), (8, 'FAJGLPCKIBH', 88), (9, 'JDMBGGLHOMJ', 99), (10, 'FLLMIPCPEDO', 110), (12, 'CAFCELAAGCF', -132),
        (14, 'LNHPMJOIBHP', 154), (15, 'EAOFBBOACPF', 165), (16, 'LBPOACLHJPD', -176)]:
        big_wire += scalar(tag, value); big[name] = value
    for tag, name, case, alt, expected in [(11, 'ELLMCOFKKPA', child, '08011002', {'NOEEBKJAFAG': 1, 'ANKEOLKBECN': 2}),
        (13, 'BGGPFKNPLGL', small, '082c10371842', {'OGJOJBLIGCB': 44, 'JDMBGGLHOMJ': 55, 'HDDPFKNMJJG': 66}),
        (17, 'AOMDFCPMDOP', pair, '08071008', {'CJMJBINEBAG': 7, 'ALOKJBKPLGA': 8})]:
        big_wire += message(tag, bytes.fromhex(case['wire'])) + message(tag, bytes.fromhex(alt)); big[name] = [case['expected'], expected]
    signed = [-1, -2147483648, 2147483647, 0, 128]
    big_wire += scalar(18, signed[0]) + message(18, b''.join(varint(x & ((1 << 64) - 1)) for x in signed[1:])); big['MLKKEOCOAKN'] = signed
    triple_wire = b''; triple = {}
    for tag, name, value in [(1, 'GBHHCKCBGEO', -1), (2, 'HOPABANFAJI', 9007199254740993), (3, 'NIHNNHMGENL', 9223372036854775808),
        (4, 'EBPLPBLFIMM', True), (5, 'KAGJILAKCJF', 55), (6, 'IELKPEEIMGI', 66), (9, 'DACMFIDFIJA', -99), (10, 'AANCEPCPHKJ', 110),
        (11, 'JDMBGGLHOMJ', 121), (12, 'FLLMIPCPEDO', 132), (13, 'EAOFBBOACPF', 143), (14, 'ELLPEBNIHND', True),
        (16, 'CLLPLJKHDLG', 176), (17, 'LBPOACLHJPD', -187), (18, 'OOKFBIPCHBO', 18446744073709551615)]:
        triple_wire += scalar(tag, value); triple[name] = value
    for tag, name, a, b in [(7, 'HHAFHFEPLPD', -1, 2), (8, 'GFKJKLIHGNK', -3, 4), (15, 'EDKKGCIAMOA', -5, 6)]:
        triple_wire += message(tag, scalar(1, a) + scalar(2, b)); triple[name] = {'CJMJBINEBAG': a, 'ALOKJBKPLGA': b}
    merged = dict(triple); merged['HHAFHFEPLPD'] = {'CJMJBINEBAG': -1, 'ALOKJBKPLGA': 22}; merged['GFKJKLIHGNK'] = {'ALOKJBKPLGA': 4}
    cases = [dict(name='all-fields-58639', typeIndex=58639, wire=big_wire.hex(), expected=big),
        dict(name='all-fields-64949', typeIndex=64949, wire=triple_wire.hex(), expected=triple),
        dict(name='three-same-shape-children-merge-independently', typeIndex=64949,
            wire=(triple_wire + message(7, scalar(2, 22)) + message(8, scalar(1, 0)) + message(15, b'')).hex(), expected=merged),
        dict(name='signed-mixed-packed-unpacked', typeIndex=58639,
            wire=(scalar(18, -1) + message(18, varint((-2147483648) & ((1 << 64) - 1)) + varint(2147483647)) + scalar(18, 0)).hex(), expected={'MLKKEOCOAKN': [-1, -2147483648, 2147483647, 0]}),
        dict(name='signed-empty-packed-keeps-prior', typeIndex=58639, wire=(scalar(18, -17) + message(18, b'')).hex(), expected={'MLKKEOCOAKN': [-17]}),
        dict(name='large-list-append-and-empty-element', typeIndex=58639,
            wire=(message(11, bytes.fromhex(child['wire'])) + message(11, b'') + message(13, bytes.fromhex(small['wire'])) + message(17, bytes.fromhex(pair['wire']))).hex(),
            expected={'ELLMCOFKKPA': [child['expected'], {}], 'BGGPFKNPLGL': [small['expected']], 'AOMDFCPMDOP': [pair['expected']]}),
        dict(name='three-u64-high-bits-swapped', typeIndex=64949,
            wire=(scalar(2, 18446744073709551615) + scalar(3, 9007199254740993) + scalar(18, 9223372036854775808)).hex(),
            expected={'HOPABANFAJI': 18446744073709551615, 'NIHNNHMGENL': 9007199254740993, 'OOKFBIPCHBO': 9223372036854775808}),
        dict(name='all-three-explicit-empty-children', typeIndex=64949, wire='3a0042007a00', expected={'HHAFHFEPLPD': {}, 'GFKJKLIHGNK': {}, 'EDKKGCIAMOA': {}}),
        dict(name='signed-list-wrong-fixed32-skipped', typeIndex=58639, wire=(varint(18 << 3 | 5) + bytes(4) + scalar(18, -1)).hex(), expected={'MLKKEOCOAKN': [-1]})]
    for index, tags in [(58639, (1, 3, 6, 12, 16)), (64949, (1, 2, 3, 4, 14, 17, 18))]:
        cases.append(dict(name='duplicate-zero-' + str(index), typeIndex=index, wire=b''.join(scalar(t, 1) + scalar(t, 0) for t in tags).hex(), expected={}))
        cases.append(dict(name='empty-' + str(index), typeIndex=index, wire='', expected={}))
    return cases


def verify(graph):
    good = []
    for case in fixtures():
        row = graph.run_graph(case['typeIndex'], bytes.fromhex(case['wire']))
        if row['result'] != case['expected']: raise ValueError(case['name'] + ': large node native result differs ' + str(row['result']))
        good.append(dict(case, observation=row))
    rejected = []
    for name, index, wire, scope, limit in [('nonempty-child-51050', 34045, '2a020801', 'explicit-scope-refusal', 100),
        ('invalid-tag', 58639, '00', 'explicit-input-model', 100), ('signed-overflow', 58639, '9001ffffffffffffffffff02', 'explicit-input-model', 100),
        ('signed-packed-truncated', 58639, '92010180', 'explicit-input-model', 100), ('uint64-overflow', 64949, '9001ffffffffffffffffff02', 'explicit-input-model', 100),
        ('negative-list-length', 58639, '5affffffff0f', 'native-error-entry', 100), ('truncated-child', 64949, '3a030801', 'native-error-entry', 100),
        ('large-list-recursion-limit', 58639, '5a00', 'native-error-entry', 0), ('large-child-recursion-limit', 64949, '7a00', 'native-error-entry', 0)]:
        try: graph.run_graph(index, bytes.fromhex(wire), limit)
        except ValueError as error:
            text = str(error)
            if scope == 'explicit-scope-refusal' and 'Nonempty child outside F-03d3e2' not in text: raise
            if scope == 'native-error-entry' and 'Native error branch:' not in text: raise
            rejected.append(dict(name=name, typeIndex=index, wire=wire, scope=scope, error=text))
        else: raise ValueError('Invalid/out-of-scope large node input accepted: ' + name)
    return good, rejected


def main():
    a, paths = arguments(__doc__); b, m, s, records, cells, bindings, strings, items = inputs(a)
    aot = message_list_aot(b, m, s, records, {38014: 51614, 6112: 68918, 17573: 21518})
    audit = dependency_audit(b, records, cells, TYPES, CHILDREN, 36, a.python_libs)
    e = Offline72(b, a.python_libs, records, trace_instructions=False); graph = AvatarLargeNodes72(e, a.directory, cells, bindings, items, strings)
    try: scan = scan_types(graph, TYPES, 36); good, rejected = verify(graph)
    finally: graph.close(); e.close()
    context = dict(schemaVersion=1, task='F-03d3e6', inputSHA256=HASHES, runtimeStatus='not-tested', targetProtocolReady=False,
        semanticIdentityStatus='unresolved', fullAvatarRawClosure=False, fullComplexRawClosure=False, blockedTypeIndices=[51050],
        aotBindings=aot, initializers=graph.large_initializers, dependencyScope=audit,
        messages=[graph.schemas[i] for i in sorted(TYPES)], scan=scan, nativeFixtures=len(good), rejections=rejected,
        limitations=['Two large nodes only; concrete signed list reuses e4 and leaf messages reuse e3;51050 nonempty remains refused',
            'Managed metadata/allocation, primitive inputs/codecs/delegates, boxing, and fixed64 arrays remain explicit models',
            'No full complex/Avatar graph, business semantic promotion, live acceptance or production integration'])
    write_reports(paths, context, good)
    print(json.dumps(dict(messages=2, wireFields=36, scanFixtures=scan['nativeFixtures'], nativeFixtures=len(good), rejections=len(rejected))))


if __name__ == '__main__': main()
