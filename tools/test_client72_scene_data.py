"""Independent vectors and tamper tests for the bounded data-consumer evidence."""
import copy
import unittest
from tools.client72_scene_data import DATA, FIRST, CACHE, UNKNOWN
from tools.client72_scene_nested import CTORS, READERS
from tools.trace_client72_scene_data import (SPECS, ORDER, ERROR_SITES, RETURN, SCENE_RETURN, NULL, PATCH, PATCH_LIST,
                                            wire_for, validate_fixtures)


# Kept independent of the native input generator and its SPECS/expected_graph.
INPUTS = {
    'default': (0, [], None, 0),
    'null-message': (17, [1, 2147483648, 4294967295], 'full', 0),
    'zero-key-nonempty': (0, [1, 2147483648, 4294967295], 'full', 1),
    'empty-list': (17, [], 'empty', 0),
    'nonempty-false': (17, [1, 2147483648, 4294967295], 'full', 0),
    'nonempty-true': (17, [1, 2147483648, 4294967295], 'full', 1),
    'high-key': (2147483649, [1, 2147483648, 4294967295], 'full', 1),
    'maximum-key-duplicates': (4294967295, [7, 7, 0, 2147483648, 4294967295], 'full', 0),
    'missing-cache-dictionary': (17, [1, 2147483648, 4294967295], 'full', 0),
    'null-cache-entry': (17, [1, 2147483648, 4294967295], 'full', 1),
    'data-ifix': (17, [1, 2147483648, 4294967295], 'full', 1),
    'first-ifix': (17, [1, 2147483648, 4294967295], 'full', 0),
    'cache-ifix': (17, [7, 7, 0, 2147483648, 4294967295], 'full', 1),
    'scene-zero-key': (0, [1, 2147483648, 4294967295], 'full', 0),
    'scene-nonempty': (23, [7, 7, 0, 2147483648, 4294967295], 'full', 0),
    'scene-high-key': (2147483649, [1, 2147483648, 4294967295], 'full', 0),
}


def valid_evidence():
    defaults = [dict(typeIndex=i, executedAddresses={hex(CTORS[i]): 1}) for i in ORDER]
    rows = []
    for name, (key, items, child, flag) in INPUTS.items():
        nested = None if child is None else dict(typeIndex=25866, fields={
            '0x18': [11, 2147483648, 11] if child == 'full' else [],
            '0x20': [0, 4294967295] if child == 'full' else [],
            '0x28': [23, 23, 2147483649] if child == 'full' else []})
        graph = dict(typeIndex=69612, fields={'0x18': items[:], '0x20': nested, '0x28': key})
        before = dict(pointer='0x1000', arrayPointer='0x2000', size=2, version=9, capacity=8,
                      items=[99, 88], rawArray=[99, 88, 0, 0, 0, 0, 0, 0])
        early = name in ('default', 'null-message', 'zero-key-nonempty', 'scene-zero-key')
        stopped = early or name in ERROR_SITES
        stop = NULL if name in ('missing-cache-dictionary', 'null-cache-entry') else PATCH if name == 'data-ifix' else PATCH_LIST if name in ('first-ifix', 'cache-ifix') else SCENE_RETURN if name == 'scene-zero-key' else RETURN if early else UNKNOWN
        after = copy.deepcopy(before)
        if not stopped:
            after.update(size=len(items), version=11, items=items[:])
            after['rawArray'][:len(items)] = items
        ingress = [dict(entry=hex(DATA), ownerMatches=True, messageMatches=name != 'null-message', flag=flag)]
        if not early and name != 'data-ifix':
            ingress.append(dict(entry=hex(FIRST), ownerMatches=True, rawKey=key, sourceListMatches=True, flag=flag))
        if not early and name not in ('data-ifix', 'first-ifix'):
            ingress.append(dict(entry=hex(CACHE), ownerMatches=True, rawKey=key, sourceListMatches=True, flag=flag))
        addresses = {hex(a): 1 for a in (DATA, FIRST, CACHE, 0x14CD67260, 0x153A18E80, 0x153A3CB70, 0x15881F800)}
        if name in ERROR_SITES: addresses[hex(ERROR_SITES[name])] = 1
        readers = {hex(READERS[69612]): 1, hex(READERS[25866]): 1, hex(CTORS[25866]): 1}
        calls = [dict(kind='Count', dataList=True, count=len(items))] if not stopped else []
        if not stopped and items: calls.append(dict(kind='CopyTo', dataList=True, count=len(items), index=0))
        rows.append(dict(name=name, stop=hex(stop), trace=dict(stop=hex(stop), executedAddresses=addresses, models=[]),
                         reader=dict(storedObject=dict(fields={'0x48': copy.deepcopy(graph)}) if name.startswith('scene-') else copy.deepcopy(graph), executedAddresses=readers, models=[]),
                         graphBefore=graph, graphAfter=copy.deepcopy(graph), child25866Pointer='0x0' if child is None else '0x5000',
                         messagePointer='0x3000', originalMessage48Pointer='0x3000', prefix=dict(normalPrefix=dict(messageContentUnchanged=True)),
                         cacheBefore=before, cacheAfter=after, ingress=ingress, interfaceCalls=calls,
                         nativeListWrites=[dict(offset='0x18', size=4, value=0, source='0x153a3d86e')],
                         dictionary=[dict(hash=key & 0x7FFFFFFF, next=-1, key=key, value='0x1000')],
                         unknownArguments=None if stopped else dict(cacheListMatches=True, listPointer='0x1000', rawKey=key, items=items[:])))
    return rows, defaults


class SceneDataEvidenceTests(unittest.TestCase):
    def reject(self, name, mutate):
        rows, defaults = valid_evidence(); mutate(next(r for r in rows if r['name'] == name))
        with self.assertRaises(ValueError): validate_fixtures(rows, defaults)

    def test_complete_fixture_catalog_and_constructor_order(self):
        rows, defaults = valid_evidence(); validate_fixtures(rows, defaults)
        self.assertEqual({s[0]: s[1:] for s in SPECS}, INPUTS)
        with self.assertRaises(ValueError): validate_fixtures(rows[:-1], defaults)
        with self.assertRaises(ValueError): validate_fixtures(rows, defaults[::-1])
        defaults[0]['executedAddresses'].clear()
        with self.assertRaises(ValueError): validate_fixtures(rows, defaults)

    def test_fixed_wire_vectors_and_distinct_nonempty_cases(self):
        cases = {s[0]: s for s in SPECS}
        self.assertEqual(wire_for(cases['default']), b'')
        self.assertEqual(wire_for(cases['empty-list']).hex(), '6a0058110a00')
        self.assertEqual(wire_for(cases['nonempty-false']).hex(),
                         '6a0b018080808008ffffffff0f58110a1a0a070b80808080080b3a0600ffffffff0f320717178180808008')
        self.assertEqual(wire_for(cases['maximum-key-duplicates'])[:17].hex(), '68076a070700808080800868ffffffff0f')

    def test_real_nested_readers_and_original_child_are_required(self):
        self.reject('nonempty-false', lambda r: r['reader']['executedAddresses'].pop(hex(READERS[69612])))
        self.reject('empty-list', lambda r: r['reader']['models'].append(hex(READERS[25866])))
        self.reject('nonempty-false', lambda r: r.__setitem__('child25866Pointer', '0x0'))
        self.reject('nonempty-false', lambda r: r['reader']['storedObject']['fields'].__setitem__('0x28', 0))

    def test_high_bits_duplicates_and_source_graph_survive(self):
        self.reject('high-key', lambda r: r['graphAfter']['fields'].__setitem__('0x28', 1))
        self.reject('maximum-key-duplicates', lambda r: r['cacheAfter']['rawArray'].__setitem__(1, 0))
        self.reject('nonempty-false', lambda r: r['graphBefore']['fields']['0x20']['fields']['0x28'].__setitem__(2, 1))

    def test_boolean_and_original_raw_pointer_are_forwarded(self):
        self.reject('nonempty-true', lambda r: r['ingress'][1].__setitem__('flag', 0))
        self.reject('nonempty-false', lambda r: r['ingress'][2].__setitem__('sourceListMatches', False))
        self.reject('scene-nonempty', lambda r: r.__setitem__('originalMessage48Pointer', '0x9000'))
        self.reject('scene-zero-key', lambda r: r['prefix']['normalPrefix'].__setitem__('messageContentUnchanged', False))

    def test_actual_cache_identity_contents_version_and_dictionary(self):
        self.reject('nonempty-false', lambda r: r['cacheAfter'].__setitem__('pointer', '0x9000'))
        self.reject('nonempty-false', lambda r: r['cacheAfter'].__setitem__('arrayPointer', '0x9000'))
        self.reject('nonempty-true', lambda r: r['cacheAfter'].__setitem__('version', 10))
        self.reject('high-key', lambda r: r['dictionary'][0].__setitem__('key', 1))
        self.reject('empty-list', lambda r: r['cacheAfter'].__setitem__('rawArray', [0] * 8))

    def test_native_clear_and_managed_copy_observations(self):
        self.reject('nonempty-false', lambda r: r.__setitem__('nativeListWrites', []))
        self.reject('nonempty-true', lambda r: r.__setitem__('interfaceCalls', []))
        self.reject('nonempty-false', lambda r: r['trace']['models'].append(hex(CACHE)))

    def test_original_errors_ifix_and_early_return_leave_cache(self):
        for name, site in ERROR_SITES.items():
            self.reject(name, lambda r, a=site: r['trace']['executedAddresses'].pop(hex(a)))
        self.reject('zero-key-nonempty', lambda r: r['cacheAfter'].__setitem__('size', 0))
        self.reject('null-cache-entry', lambda r: r['cacheAfter'].__setitem__('version', 10))

    def test_next_body_and_child_consumer_cannot_be_reported_executed(self):
        self.reject('nonempty-false', lambda r: r['trace']['executedAddresses'].__setitem__(hex(UNKNOWN + 1), 1))
        self.reject('nonempty-false', lambda r: r['trace']['executedAddresses'].__setitem__('0x14fcdcab0', 1))
        self.reject('nonempty-false', lambda r: r['unknownArguments'].__setitem__('rawKey', 0))
        self.reject('scene-high-key', lambda r: r['unknownArguments'].__setitem__('items', []))

    def test_same_cpu_normal_query_list_prefix_is_required(self):
        self.reject('scene-nonempty', lambda r: r['trace']['executedAddresses'].pop('0x153a18e80'))
        self.reject('scene-high-key', lambda r: r['trace']['executedAddresses'].pop('0x153a3cb70'))


if __name__ == '__main__': unittest.main()
