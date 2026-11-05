"""Evidence validator tests with a separate explicit input catalog."""
import unittest
from tools.trace_client72_scene_list import (DIRECT, SCENE, EXPECTED_ITEMS, ERROR_SITES, RETURN, ZERO_RETURN, NONZERO_RETURN,
                                           NULL, BOUNDS, ARGUMENT, IFIX, FALLBACK, items_for, validate_fixtures)
from tools.client72_scene_list import NONZERO, CONSTRUCTOR, INSERT_RANGE, TYPE_CHECK


def valid_rows():
    errors = {'nz-null-dictionary': NULL, 'nz-null-entry': NULL, 'nz-null-entries': NULL, 'nz-bounds-entry': BOUNDS,
              'ctor-null-target': NULL, 'ctor-null-source': ARGUMENT, 'nz-null-source': ARGUMENT, 'nz-ifix': IFIX,
              'ctor-noncollection': FALLBACK, 'nz-noncollection': FALLBACK}
    rows = []
    for name in DIRECT + SCENE:
        items = list(EXPECTED_ITEMS[name]); ctor = name.startswith('ctor-') or name.startswith('scene-zero')
        key = 0x80000001 if name == 'scene-nz-high' else 17
        stop = errors.get(name, ZERO_RETURN if name.startswith('scene-zero') else NONZERO_RETURN if name.startswith('scene-') else RETURN)
        before = None
        if name in ('nz-existing-grow', 'scene-nz-existing'):
            before = dict(pointer='0x1000', arrayPointer='0x2000', size=1, version=9, capacity=1, items=[99], rawArray=[99])
        elif name.startswith('nz-existing') or name in ('nz-null-entry', 'nz-null-source'):
            before = dict(pointer='0x1000', arrayPointer='0x2000', size=2, version=9, capacity=8, items=[99, 88], rawArray=[99, 88] + [0] * 6)
        capacity = before['capacity'] if before and before['capacity'] >= len(items) else len(items) if ctor or before else max(4, len(items)) if items else 0
        array = '0x2000' if before and before['capacity'] >= len(items) else '0x3000'
        version = 0 if ctor else 11 if before else 2
        after = dict(pointer='0x1000', arrayPointer=array, size=len(items), version=version, capacity=capacity,
                     items=items[:], rawArray=items + [0] * (capacity - len(items)))
        old_raw = before['rawArray'][:] if before else []
        if before and before['capacity'] >= len(items) and items: old_raw[:len(items)] = items
        if before and before['capacity'] >= len(items): after['rawArray'] = old_raw[:]
        if name == 'nz-null-entry': after = dict(before); old_raw = before['rawArray'][:]
        if name == 'nz-null-source':
            after = dict(before, size=0, items=[], version=10); old_raw = before['rawArray'][:]
        addresses = {hex(a): 1 for a in (CONSTRUCTOR, NONZERO, INSERT_RANGE, TYPE_CHECK, 0x14CD67260, 0x153A18E80)}
        if name in ERROR_SITES: addresses[hex(ERROR_SITES[name])] = 1
        stores = [dict(offset='0x18', size=4, value=len(items), source='0x15881e3c6')] if ctor and items else [] if ctor else [
            dict(offset='0x18', size=4, value=0, source='0x153a3cc57'), dict(offset='0x1c', size=4, value=version, source='0x15881fd1e')]
        calls = [dict(kind='ICollection.Count', count=len(items))]
        if items: calls.append(dict(kind='ICollection.CopyTo', count=len(items), destinationIndex=0))
        rows.append(dict(name=name, expectedItems=items[:], key=key, stop=hex(stop), trace=dict(stop=hex(stop), executedAddresses=addresses, models=[]),
                         sourceUnchanged=True, sourceArrayPointer='0x4000', nativeListWrites=stores,
                         reader=dict(storedObject=dict(repeatedUint32=items[:], rawStoredScalars={'0x68': 0xFEDCBA9876543210, '0x7c': ((key - 0xC0E3) & 0xFFFFFFFF) ^ 0x2D03})),
                         listBefore=before, listAfter=after, oldArrayAfter=old_raw,
                         dictionary=[dict(key=key, value='0x1000')], interfaceCalls=calls, normalPrefix=dict(messageContentUnchanged=True)))
    return rows


class SceneListEvidenceTests(unittest.TestCase):
    def reject(self, name, mutate):
        rows = valid_rows(); mutate(next(r for r in rows if r['name'] == name))
        with self.assertRaises(ValueError): validate_fixtures(rows)

    def test_independent_input_catalog_distinguishes_empty_nonempty_duplicates(self):
        self.assertEqual(items_for('ctor-empty'), [])
        self.assertEqual(items_for('ctor-nonempty'), [1, 0x80000000, 0xFFFFFFFF])
        self.assertEqual(items_for('nz-new-nonempty'), [1, 0x80000000, 0xFFFFFFFF])
        self.assertEqual(items_for('ctor-duplicates'), [7, 7, 0, 0x80000000, 7, 0xFFFFFFFF])
        self.reject('ctor-nonempty', lambda r: r['reader']['storedObject'].__setitem__('repeatedUint32', []))

    def test_complete_evidence_and_missing_case(self):
        validate_fixtures(valid_rows())
        with self.assertRaises(ValueError): validate_fixtures(valid_rows()[:-1])

    def test_order_duplicates_and_high_bits_are_not_lost(self):
        self.reject('ctor-duplicates', lambda r: r['listAfter'].__setitem__('items', [7, 0, 7, 0x80000000, 7, 0xFFFFFFFF]))
        self.reject('nz-new-nonempty', lambda r: r['listAfter'].__setitem__('items', [1, 0, 0x7FFFFFFF]))
        self.reject('nz-existing-capacity', lambda r: r['listAfter']['rawArray'].__setitem__(1, 0))

    def test_native_bodies_and_assignability_cannot_be_models(self):
        self.reject('ctor-nonempty', lambda r: r['trace']['models'].append(hex(CONSTRUCTOR)))
        self.reject('nz-existing-grow', lambda r: r['trace']['executedAddresses'].pop(hex(INSERT_RANGE)))

    def test_count_copy_and_native_size_stores_are_required(self):
        self.reject('ctor-nonempty', lambda r: r.__setitem__('interfaceCalls', []))
        self.reject('ctor-duplicates', lambda r: r.__setitem__('nativeListWrites', []))
        self.reject('nz-existing-grow', lambda r: r.__setitem__('nativeListWrites', []))

    def test_existing_list_identity_and_reusable_array_survive(self):
        self.reject('nz-existing-capacity', lambda r: r['listAfter'].__setitem__('pointer', '0x9000'))
        self.reject('nz-existing-capacity', lambda r: r['listAfter'].__setitem__('arrayPointer', '0x9000'))
        self.reject('nz-existing-capacity', lambda r: r['listAfter'].__setitem__('version', 10))

    def test_grow_replaces_array_and_preserves_old_storage(self):
        self.reject('nz-existing-grow', lambda r: r['listAfter'].__setitem__('arrayPointer', '0x2000'))
        self.reject('scene-nz-existing', lambda r: r.__setitem__('oldArrayAfter', [0]))

    def test_clear_does_not_zero_value_type_backing_array(self):
        self.reject('nz-existing-empty', lambda r: r.__setitem__('oldArrayAfter', [0] * 8))
        self.reject('nz-existing-empty', lambda r: r['listAfter'].__setitem__('size', 2))

    def test_null_source_keeps_actual_partial_clear_and_native_error(self):
        self.reject('nz-null-source', lambda r: r['listAfter'].__setitem__('version', 9))
        self.reject('nz-null-source', lambda r: r.__setitem__('stop', hex(RETURN)))
        self.reject('nz-null-entry', lambda r: r['listAfter'].__setitem__('version', 10))

    def test_original_ifix_errors_and_fallback_cannot_claim_completion(self):
        for name in ('nz-null-entries', 'nz-bounds-entry', 'nz-ifix', 'ctor-noncollection', 'nz-noncollection'):
            self.reject(name, lambda r: r.__setitem__('stop', hex(RETURN)))
        self.reject('nz-ifix', lambda r: r['trace']['executedAddresses'].pop('0x153a3cce8'))

    def test_constructor_array_is_independent_and_reader_unchanged(self):
        self.reject('ctor-nonempty', lambda r: r['listAfter'].__setitem__('arrayPointer', '0x4000'))
        self.reject('scene-nz-high', lambda r: r.__setitem__('sourceUnchanged', False))

    def test_same_reader_query_and_nonzero_key_are_required(self):
        self.reject('scene-nz-high', lambda r: r['reader']['storedObject']['rawStoredScalars'].__setitem__('0x7c', 0))
        self.reject('scene-nz-new', lambda r: r['trace']['executedAddresses'].pop('0x153a18e80'))


if __name__ == '__main__': unittest.main()
