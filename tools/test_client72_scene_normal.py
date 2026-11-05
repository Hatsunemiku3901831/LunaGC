"""Pure evidence-validator tests; actual native runs use the trace CLI separately."""
import unittest
from tools.trace_client72_scene_normal import NAMES, NATIVE, UNWIND, ENTRY, TASK, NEXT, NULL, RECYCLE, validate_fixtures


def valid_rows():
    rows = []
    for name in NAMES:
        decoded = {'uint32-high-bit': [0x80000000, 0x80000001, 0xFEDCBA98], 'uint32-maximum': [0xFFFFFFFF] * 3,
                   'decoded-zero': [0, 0, 0], 'explicit-raw-zero': [0x5889 ^ 0xE011, 0x2D03 + 0xC0E3, 0x45A5 ^ 0x6B98]}.get(name, [3, 17, 42])
        raw = [((decoded[0] ^ 0xE011) - 0x5889) & 0xFFFFFFFF, ((decoded[1] - 0xC0E3) & 0xFFFFFFFF) ^ 0x2D03,
               ((decoded[2] ^ 0x6B98) - 0x45A5) & 0xFFFFFFFF]
        nulls = {'missing-dictionary': 0x14CD5FB46, 'missing-pool': 0x14CD5FB4B,
                 'pool-null-result': 0x146E05718, 'missing-three-float': 0x14CD676D4, 'missing-next-owner': 0x14CD676D9}
        stop = hex(NULL if name in nulls else RECYCLE if name == 'existing-key-boundary' else NEXT)
        addresses = {hex(a): 1 for a in NATIVE + UNWIND + (0x14C8AE9A0,)}
        if name in nulls: addresses[hex(nulls[name])] = 1
        seed = dict(key=20, hash=20, value='0x2000') if name == 'collision-preserves-entry' else None
        entries = ([dict(seed, next=-1)] if seed else []) + [dict(key=decoded[0], hash=decoded[0] & 0x7FFFFFFF, next=0 if seed else -1, value='0x1000')]
        rows.append(dict(name=name, decoded=decoded, raw94_7c_a0=raw, stop=stop,
                         trace=dict(stop=stop, executedAddresses=addresses, models=[]), messageContentUnchanged=True,
                         messagePointerPreserved=True, nextArguments=dict(receiverMatched=True, decoded7c=decoded[1]),
                         currentTaskPointer='0x1000', previousTaskPointer='0x2000' if name == 'current-task-slot' else '0x0',
                         taskPointer='0x1000', oldTaskPointer='0x2000', borrowed=1,
                         taskRaw=dict(key58=decoded[0], value28=decoded[2], floatBits=[0x3FA00000, 0xC0200000, 0x40700000],
                                      stringPointerMatched=True, resetWords={'0x30': 0}, flags40_41=[0, 0] if name == 'current-task-slot' else [1, 1]),
                         dictionary=dict(seed=seed, entries=entries, lookupIndex=len(entries) - 1),
                         stage=dict(writes=[dict(index=10, size=4, bits=0x42F64000, source='0x14c8aea1e')] if name == 'stage-enabled' else [],
                                    clockCalls=['context', 'double-value'] if name == 'stage-enabled' else []),
                         reader=dict(storedObject=dict(rawStoredScalars={'0x94': raw[0], '0x7c': raw[1], '0xa0': raw[2], '0x68': 0xFEDCBA9876543210},
                                                       repeatedUint32=[1, 0x80000000, 0xFFFFFFFF], stringUTF8Hex='正常场景\U0001f319\0'.encode('utf8').hex()))))
    return rows


class SceneNormalEvidenceTests(unittest.TestCase):
    def reject(self, change, name='nonzero'):
        rows = valid_rows(); change(next(r for r in rows if r['name'] == name))
        with self.assertRaises(ValueError): validate_fixtures(rows)

    def test_complete_evidence_shape_and_missing_sample(self):
        validate_fixtures(valid_rows())
        with self.assertRaises(ValueError): validate_fixtures(valid_rows()[:-1])

    def test_independent_decode_and_wire_values_reject_drift(self):
        self.reject(lambda r: r['decoded'].__setitem__(0, 4))
        self.reject(lambda r: r['reader']['storedObject']['rawStoredScalars'].__setitem__('0x94', 0))

    def test_float_reordering_and_reference_copy_cannot_drift(self):
        self.reject(lambda r: r['taskRaw'].__setitem__('floatBits', [0x3FA00000, 0x40700000, 0xC0200000]))
        self.reject(lambda r: r['taskRaw'].__setitem__('stringPointerMatched', False))

    def test_uint64_and_list_truncation_rejected(self):
        self.reject(lambda r: r['reader']['storedObject']['rawStoredScalars'].__setitem__('0x68', 0x76543210))
        self.reject(lambda r: r['reader']['storedObject'].__setitem__('repeatedUint32', [1, 0, 0xFFFFFFFF]))

    def test_native_methods_cannot_be_stubbed_or_omitted(self):
        self.reject(lambda r: r['trace']['models'].append(hex(TASK)))
        self.reject(lambda r: r['trace']['executedAddresses'].pop(hex(UNWIND[-1])))

    def test_null_branch_and_next_call_arguments_remain_bounded(self):
        self.reject(lambda r: r.__setitem__('stop', hex(NEXT)), 'missing-pool')
        self.reject(lambda r: r['trace']['executedAddresses'].pop('0x14cd5fb46'), 'missing-dictionary')
        self.reject(lambda r: r['nextArguments'].__setitem__('decoded7c', 0))

    def test_task_reset_and_previous_slot_are_independent(self):
        self.reject(lambda r: r['taskRaw']['resetWords'].__setitem__('0x30', 1))
        self.reject(lambda r: r.__setitem__('previousTaskPointer', '0x0'), 'current-task-slot')
        self.reject(lambda r: r['taskRaw'].__setitem__('flags40_41', [1, 1]), 'current-task-slot')

    def test_dictionary_relookup_and_collision_preserve_old_entry(self):
        self.reject(lambda r: r['dictionary'].__setitem__('lookupIndex', 0), 'collision-preserves-entry')
        self.reject(lambda r: r['dictionary']['entries'][0].__setitem__('value', '0x1000'), 'collision-preserves-entry')

    def test_stage_clock_and_message_pointer_are_not_substituted(self):
        self.reject(lambda r: r['stage'].__setitem__('clockCalls', []), 'stage-enabled')
        self.reject(lambda r: r.__setitem__('messagePointerPreserved', False))


if __name__ == '__main__': unittest.main()
