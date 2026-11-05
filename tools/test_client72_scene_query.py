"""Pure query evidence validation; native samples are run by the trace CLI."""
import unittest
from tools.trace_client72_scene_query import (DIRECT, SCENE, QUERY, GETTER, LOOKUP, ENUMERATOR, RETURN, ZERO, NONZERO,
                                            NULL, BOUNDS, CYCLE, QUERY_IFIX, GETTER_IFIX, INITIALIZE, DECODE, validate_fixtures)


def valid_rows():
    boundaries = {'missing-entries': NULL, 'out-of-bounds-entry': BOUNDS, 'cycle': CYCLE, 'query-ifix': QUERY_IFIX,
                  'getter-ifix': GETTER_IFIX, 'cold-no-source': INITIALIZE, 'cold-source-record': DECODE}
    sites = {'missing-entries': 0x156632F9C, 'out-of-bounds-entry': 0x153A18F2E, 'cycle': 0x156632FA1,
             'query-ifix': 0x153A18F1A, 'getter-ifix': 0x153A35A34, 'cold-no-source': 0x153A35846, 'cold-source-record': 0x153A3594F}
    rows = []
    for name in DIRECT + SCENE:
        key = {'high-key-value': 0x80000011, 'maximum-key-value': 0xFFFFFFFF, 'scene-high-key': 0x80000001}.get(name, 17)
        value = {'high-key-value': 0x80000001, 'maximum-key-value': 0xFFFFFFFF}.get(name, 0 if name in ('absent-buckets', 'absent-key', 'stored-zero', 'cold-empty-source') else 7)
        stop = boundaries.get(name, NONZERO if name in ('scene-nonzero', 'scene-high-key') else ZERO if name.startswith('scene-') else RETURN)
        addresses = {hex(a): 1 for a in (QUERY, GETTER, LOOKUP, ENUMERATOR, 0x14CD67260, 0x1578B6A20)}
        if name in sites: addresses[hex(sites[name])] = 1
        rows.append(dict(name=name, key=key, suppliedValue=value, stop=hex(stop), queryReturn=value if stop == RETURN else None,
                         trace=dict(stop=hex(stop), executedAddresses=addresses, models=[]), entriesUnchanged=True,
                         cachedDictionaryPreserved=name != 'cold-empty-source', allocations=[dict(kind='cache', pointer='0x1000')],
                         decodeRaw=0x12345678 if stop == DECODE else None,
                         decodeEnumeratedKey=key if stop == DECODE else None,
                         branchArguments=dict(receiverMatches=True, key=key, originalRepeatedMatches=True) if stop == NONZERO else
                                         dict(newListMatches=True, originalRepeatedMatches=True, third=0, newListArrayIsNull=True) if stop == ZERO else None,
                         normalPrefix=dict(stop=hex(stop), messageContentUnchanged=True,
                                           reader=dict(storedObject=dict(repeatedUint32=[1, 0x80000000, 0xFFFFFFFF],
                                                                         rawStoredScalars={'0x68': 0xFEDCBA9876543210, '0x7c': ((key - 0xC0E3) & 0xFFFFFFFF) ^ 0x2D03})))))
    return rows


class SceneQueryEvidenceTests(unittest.TestCase):
    def reject(self, name, change):
        rows = valid_rows(); change(next(r for r in rows if r['name'] == name))
        with self.assertRaises(ValueError): validate_fixtures(rows)

    def test_complete_scope_and_missing_case(self):
        validate_fixtures(valid_rows())
        with self.assertRaises(ValueError): validate_fixtures(valid_rows()[:-1])

    def test_query_value_is_independent_of_claimed_fixture_value(self):
        self.reject('stored-nonzero', lambda r: r.__setitem__('queryReturn', 0))
        self.reject('stored-zero', lambda r: r.__setitem__('queryReturn', 7))

    def test_high_and_maximum_uint32_cannot_be_truncated(self):
        self.reject('high-key-value', lambda r: r.__setitem__('queryReturn', 1))
        self.reject('maximum-key-value', lambda r: r.__setitem__('key', 0x7FFFFFFF))

    def test_original_errors_and_ifix_are_stops(self):
        for name in ('missing-entries', 'out-of-bounds-entry', 'cycle', 'query-ifix', 'getter-ifix'):
            self.reject(name, lambda r: r.__setitem__('stop', hex(RETURN)))
        self.reject('cycle', lambda r: r['trace']['executedAddresses'].pop('0x156632fa1'))

    def test_native_query_lookup_and_getter_are_not_models(self):
        self.reject('stored-nonzero', lambda r: r['trace']['models'].append(hex(LOOKUP)))
        self.reject('stored-zero', lambda r: r['trace']['executedAddresses'].pop(hex(GETTER)))

    def test_cold_source_initialization_and_record_remain_explicit(self):
        self.reject('cold-empty-source', lambda r: r.__setitem__('allocations', []))
        self.reject('cold-source-record', lambda r: r.__setitem__('decodeRaw', 0))
        self.reject('cold-no-source', lambda r: r.__setitem__('stop', hex(RETURN)))

    def test_collision_storage_is_read_only(self):
        self.reject('collision', lambda r: r.__setitem__('entriesUnchanged', False))

    def test_nonzero_consumer_receives_original_list_and_decoded_key(self):
        self.reject('scene-nonzero', lambda r: r['branchArguments'].__setitem__('originalRepeatedMatches', False))
        self.reject('scene-high-key', lambda r: r['branchArguments'].__setitem__('key', 1))

    def test_zero_branch_does_not_claim_list_constructor_completed(self):
        self.reject('scene-zero', lambda r: r['branchArguments'].__setitem__('newListArrayIsNull', False))
        self.reject('scene-missing-key', lambda r: r['branchArguments'].__setitem__('third', 1))

    def test_same_reader_wire_and_task_prefix_are_required(self):
        self.reject('scene-high-key', lambda r: r['normalPrefix']['reader']['storedObject']['rawStoredScalars'].__setitem__('0x7c', 0))
        self.reject('scene-nonzero', lambda r: r['trace']['executedAddresses'].pop('0x1578b6a20'))


if __name__ == '__main__': unittest.main()
