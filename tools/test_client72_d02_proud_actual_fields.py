"""Regression checks for actual-source and typed-field evidence boundaries."""
import copy
import json
import unittest

from tools import client72_d02_proud_actual_fields as audit


class ProudActualFieldEvidenceTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.actual = json.loads(audit.read_pinned(audit.ROOT / audit.ACTUAL, audit.PINS[audit.ACTUAL]))
        cls.payload = audit.read_pinned(audit.ROOT / audit.PAYLOAD,
            'fda5ac5875581bf6c04a269333197d41decea728ecc9f075e4add7780745b14b')

    def field(self, actual):
        return next(f for f in actual['samples'][0]['fields'] if f['fieldIndex'] == 105355)

    def test_real_zero_slots_and_presence_remain_explicit(self):
        result = audit.actual_leaves(self.actual, self.payload)
        leaves = [leaf for sample in result for leaf in sample['orderedLeaves']]
        self.assertEqual((len(result), len(leaves), sum(leaf['field404854'] == 0 for leaf in leaves)), (4, 20, 5))
        self.assertTrue(all(sample['inputPresence'].startswith('not observed') for sample in result))

    def test_same_named_leaf_from_other_type_is_rejected(self):
        actual = copy.deepcopy(self.actual)
        self.field(actual)['value'][0][1]['owner'] = 40302
        with self.assertRaisesRegex(ValueError, 'Wrong typed leaf'):
            audit.actual_leaves(actual, self.payload)

    def test_reordered_typed_leaves_are_rejected(self):
        actual = copy.deepcopy(self.actual)
        self.field(actual)['value'][0].reverse()
        with self.assertRaisesRegex(ValueError, 'Wrong typed leaf'):
            audit.actual_leaves(actual, self.payload)

    def test_getter_trace_requires_the_fixed_return_address(self):
        actual = copy.deepcopy(self.actual)
        self.field(actual)['getters'][0]['trace']['stop'] = 'RETURN'
        with self.assertRaisesRegex(ValueError, 'getter values or RETURN'):
            audit.actual_leaves(actual, self.payload)

    def test_source_span_drift_is_rejected(self):
        actual = copy.deepcopy(self.actual)
        actual['samples'][0]['source'] = actual['samples'][0]['source'] | {'begin': 0}
        with self.assertRaisesRegex(ValueError, 'source span'):
            audit.actual_leaves(actual, self.payload)

    def test_wrong_exception_extent_is_rejected(self):
        report = json.loads(audit.read_pinned(audit.OUT / 'map-source1/extraction.private.json',
            audit.PRIVATE_PINS['map-source1/extraction.private.json']))
        with self.assertRaisesRegex(ValueError, 'Window identity, extent'):
            audit.frozen_window(audit.OUT / 'map-source1', report, 0x14d7ae340, 0x14d7ae3f0, 37)


if __name__ == '__main__':
    unittest.main()
