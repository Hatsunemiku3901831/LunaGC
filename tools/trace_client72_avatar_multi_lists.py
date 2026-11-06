"""F-03d3e7: independent same-shape message lists in two middle nodes."""
try:
    from .trace_client72_avatar_large_nodes import (AvatarLargeNodes72, message_list_aot, arguments, inputs, write_reports, message, scalar,
        install_types, initialize_types, scan_types, dependency_audit, Offline72, HASHES, json, varint)
    from .trace_client72_avatar_large_nodes import fixtures as large_fixtures
    from .trace_client72_avatar_primitive_leaves import fixtures as primitive_fixtures
except ImportError:
    from trace_client72_avatar_large_nodes import (AvatarLargeNodes72, message_list_aot, arguments, inputs, write_reports, message, scalar,
        install_types, initialize_types, scan_types, dependency_audit, Offline72, HASHES, json, varint)
    from trace_client72_avatar_large_nodes import fixtures as large_fixtures
    from trace_client72_avatar_primitive_leaves import fixtures as primitive_fixtures

TYPES = {63008, 22404}
CHILDREN = {58639, 64949, 68918, 38218, 67498, 31113}


class AvatarMultiLists72(AvatarLargeNodes72):
    def __init__(self, engine, directory, cells, bindings, items, strings):
        super().__init__(engine, directory, cells, bindings, items, strings)
        install_types(self, cells, TYPES); self.multi_initializers = initialize_types(self, TYPES)

    def new_message(self, index, address=None):
        if index in TYPES: return self.new_subtree(index, address)
        return super().new_message(index, address)

    def snapshot(self, pointer, depth=0):
        if pointer and self.objects[pointer] in TYPES: return self.subtree_snapshot(pointer, depth)
        return super().snapshot(pointer, depth)


def fixtures():
    known = {x['name']: x for x in large_fixtures() + primitive_fixtures()}; large = known['all-fields-58639']; pair = known['all-fields-68918']
    small_wire = scalar(1, 11) + scalar(2, 22) + scalar(4, 44) + scalar(5, 55)
    small_wire += message(3, bytes.fromhex(large['wire'])) + message(3, scalar(1, 77))
    small_wire += message(6, bytes.fromhex(pair['wire'])) + message(6, scalar(1, -9) + scalar(2, 10))
    small_wire += scalar(7, -1) + message(7, varint((-2147483648) & ((1 << 64) - 1)) + varint(2147483647) + b'\0')
    small = {'ANKEOLKBECN': 11, 'MMLHCEFAFCB': 22, 'GHKAMHCCDNI': 44, 'DKPBBKFJONI': 55,
        'MKFKJIAMCKJ': [large['expected'], {'ANKEOLKBECN': 77}], 'AOMDFCPMDOP': [pair['expected'], {'CJMJBINEBAG': -9, 'ALOKJBKPLGA': 10}],
        'MLKKEOCOAKN': [-1, -2147483648, 2147483647, 0]}
    outer_wire = b''; outer = {}
    for tag, name, value in [(1, 'HOPABANFAJI', 9007199254740993), (2, 'NIHNNHMGENL', 18446744073709551615), (6, 'ALKNGMBFIKO', 66),
        (7, 'ICMFFOLOBDL', True), (8, 'IAHPPLKIIBK', 88), (9, 'PLGFFEKCPHF', 99), (10, 'EBAHDMALONH', 110), (11, 'NMOKEODKCGA', 121),
        (12, 'PAADIACEFKF', 132), (14, 'DOGHDICAPGE', 154), (20, 'IBEBCEIBAEC', -220)]:
        outer_wire += scalar(tag, value); outer[name] = value
    for tag, name, value in [(3, 'HIKFHOJMFCA', 101), (4, 'DPPEDFMHBAI', 202), (18, 'NNIPFIBDCEN', 303)]:
        outer_wire += message(tag, bytes.fromhex(large['wire']) + scalar(1, value)) + message(tag, scalar(1, value + 1))
        outer[name] = [dict(large['expected'], ANKEOLKBECN=value), {'ANKEOLKBECN': value + 1}]
    triple = known['all-fields-64949']; outer_wire += message(5, bytes.fromhex(triple['wire'])); outer['BGKOAMHLPPK'] = [triple['expected']]
    outer_wire += message(13, small_wire); outer['KJGJGKIHMCO'] = [small]
    for tag, name, values in [(15, 'JNIMENFGFHC', [(15, 16), (17, 18)]), (16, 'GNKIDBAOAKI', [(25, 26), (27, 28)])]:
        outer[name] = []
        for a, b in values:
            outer_wire += message(tag, scalar(1, a) + scalar(2, b)); outer[name].append({'AFKJIKCEOKA': a, 'GNHFNECPAHD': b})
    leaf = known['all-fields-31113']; outer_wire += message(17, bytes.fromhex(leaf['wire'])); outer['GOFCLCNCBDC'] = [leaf['expected']]
    outer_wire += scalar(19, 0) + message(19, bytes.fromhex('01ffffffff0f')); outer['KPDMPKFJHED'] = [0, 1, 4294967295]
    outer_wire += scalar(21, -1) + message(21, b'\0\x80\x01'); outer['DELNNOKFLAP'] = [-1, 0, 128]
    leaf = known['all-fields-38218']; outer_wire += message(22, bytes.fromhex(leaf['wire'])); outer['CDAJPPGBAJC'] = leaf['expected']
    cases = [dict(name='all-fields-63008', typeIndex=63008, wire=small_wire.hex(), expected=small),
        dict(name='all-fields-22404', typeIndex=22404, wire=outer_wire.hex(), expected=outer),
        dict(name='three-same-shape-lists-append-independently', typeIndex=22404,
            wire=(message(3, scalar(1, 101)) + message(4, scalar(1, 202)) + message(18, scalar(1, 303)) + message(4, scalar(1, 204)) + message(3, b'')).hex(),
            expected={'HIKFHOJMFCA': [{'ANKEOLKBECN': 101}, {}], 'DPPEDFMHBAI': [{'ANKEOLKBECN': 202}, {'ANKEOLKBECN': 204}], 'NNIPFIBDCEN': [{'ANKEOLKBECN': 303}]}),
        dict(name='two-same-shape-leaf-lists-append-independently', typeIndex=22404,
            wire=(message(15, scalar(1, 11) + scalar(2, 12)) + message(16, scalar(1, 21) + scalar(2, 22)) + message(15, scalar(1, 13))).hex(),
            expected={'JNIMENFGFHC': [{'AFKJIKCEOKA': 11, 'GNHFNECPAHD': 12}, {'AFKJIKCEOKA': 13}], 'GNKIDBAOAKI': [{'AFKJIKCEOKA': 21, 'GNHFNECPAHD': 22}]}),
        dict(name='singular-leaf-merges-with-zero', typeIndex=22404,
            wire=(message(22, bytes.fromhex(leaf['wire'])) + message(22, scalar(1, 0) + scalar(2, 77))).hex(),
            expected={'CDAJPPGBAJC': {'ILJLGHBLKMP': 77, 'PBIBHHJEOPE': 4294967295, 'GOKKMOKPCEE': -2147483648}}),
        dict(name='two-uint64-high-bits-swapped', typeIndex=22404, wire=(scalar(1, 18446744073709551615) + scalar(2, 9223372036854775808)).hex(), expected={'HOPABANFAJI': 18446744073709551615, 'NIHNNHMGENL': 9223372036854775808}),
        dict(name='u32-packed-unpacked', typeIndex=22404, wire=(scalar(19, 0) + message(19, bytes.fromhex('01ffffffff0f'))).hex(), expected={'KPDMPKFJHED': [0, 1, 4294967295]})]
    for index, tag, field in [(63008, 7, 'MLKKEOCOAKN'), (22404, 21, 'DELNNOKFLAP')]:
        cases.append(dict(name='signed-packed-unpacked-' + str(index), typeIndex=index,
            wire=(scalar(tag, -1) + message(tag, varint((-2147483648) & ((1 << 64) - 1)) + varint(2147483647)) + scalar(tag, 0)).hex(), expected={field: [-1, -2147483648, 2147483647, 0]}))
        cases.append(dict(name='signed-empty-packed-' + str(index), typeIndex=index, wire=(scalar(tag, -17) + message(tag, b'')).hex(), expected={field: [-17]}))
        cases.append(dict(name='empty-' + str(index), typeIndex=index, wire='', expected={}))
    cases.append(dict(name='duplicate-signed-bool-u64-zero', typeIndex=22404, wire=b''.join(scalar(t, 1) + scalar(t, 0) for t in (1, 2, 6, 7, 20)).hex(), expected={}))
    return cases


def verify(graph):
    good = []
    for case in fixtures():
        row = graph.run_graph(case['typeIndex'], bytes.fromhex(case['wire']))
        if row['result'] != case['expected']: raise ValueError(case['name'] + ': multi list native result differs ' + str(row['result']))
        good.append(dict(case, observation=row))
    rejected = []
    for name, index, wire, scope, limit in [('nonempty-child-51050', 34045, '2a020801', 'explicit-scope-refusal', 100),
        ('invalid-tag', 22404, '00', 'explicit-input-model', 100), ('signed-overflow', 22404, 'a801ffffffffffffffffff02', 'explicit-input-model', 100),
        ('signed-packed-truncated', 63008, '3a0180', 'explicit-input-model', 100), ('uint64-overflow', 22404, '10ffffffffffffffffff02', 'explicit-input-model', 100),
        ('negative-middle-list-length', 22404, '1affffffff0f', 'native-error-entry', 100), ('truncated-middle-list', 63008, '1a030801', 'native-error-entry', 100),
        ('middle-list-recursion-limit', 22404, '920100', 'native-error-entry', 0), ('deep-large-list-recursion-limit', 22404, '6a021a00', 'native-error-entry', 1)]:
        try: graph.run_graph(index, bytes.fromhex(wire), limit)
        except ValueError as error:
            text = str(error)
            if scope == 'explicit-scope-refusal' and 'Nonempty child outside F-03d3e2' not in text: raise
            if scope == 'native-error-entry' and 'Native error branch:' not in text: raise
            rejected.append(dict(name=name, typeIndex=index, wire=wire, scope=scope, error=text))
        else: raise ValueError('Invalid/out-of-scope multi list input accepted: ' + name)
    return good, rejected


def main():
    a, paths = arguments(__doc__); b, m, s, records, cells, bindings, strings, items = inputs(a)
    aot = message_list_aot(b, m, s, records, {6112: 68918, 18455: 58639, 18456: 64949, 18458: 67498, 9680: 31113, 18457: 63008})
    audit = dependency_audit(b, records, cells, TYPES, CHILDREN, 29, a.python_libs)
    e = Offline72(b, a.python_libs, records, trace_instructions=False); graph = AvatarMultiLists72(e, a.directory, cells, bindings, items, strings)
    try: scan = scan_types(graph, TYPES, 29); good, rejected = verify(graph)
    finally: graph.close(); e.close()
    context = dict(schemaVersion=1, task='F-03d3e7', inputSHA256=HASHES, runtimeStatus='not-tested', targetProtocolReady=False,
        semanticIdentityStatus='unresolved', fullAvatarRawClosure=False, fullComplexRawClosure=False, blockedTypeIndices=[51050],
        aotBindings=aot, initializers=graph.multi_initializers, dependencyScope=audit,
        messages=[graph.schemas[i] for i in sorted(TYPES)], scan=scan, nativeFixtures=len(good), rejections=rejected,
        limitations=['Two middle nodes only; nonempty leaves and large nodes reuse e3/e4/e6;51050 nonempty remains refused',
            'Managed metadata/allocation, primitive inputs/codecs/delegates, boxing, and fixed64 arrays remain explicit models',
            'No full complex/Avatar graph, business semantic promotion, live acceptance or production integration'])
    write_reports(paths, context, good)
    print(json.dumps(dict(messages=2, wireFields=29, scanFixtures=scan['nativeFixtures'], nativeFixtures=len(good), rejections=len(rejected))))


if __name__ == '__main__': main()
