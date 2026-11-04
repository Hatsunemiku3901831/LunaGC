import unittest
from tools.client72_reader import WireInput
from tools.trace_client72_responses import bind_fields


class ReaderInputTest(unittest.TestCase):
    def test_independent_nonzero_signed_and_64bit_wire(self):
        r = WireInput(bytes.fromhex('38818080808080801070ffffffffffffffffff01'))
        self.assertEqual(56, r.tag()); self.assertEqual(9007199254740993, r.varint())
        self.assertEqual(112, r.tag()); self.assertEqual((1 << 64) - 1, r.varint())
        self.assertEqual(0, r.tag())

    def test_length_consumption_does_not_lose_varint_position(self):
        # Regression for exploratory cursor's `pos += read_length()` ordering bug.
        r = WireInput(bytes.fromhex('0a034142431001'))
        self.assertEqual(10, r.tag()); r.skip()
        self.assertEqual(16, r.tag()); self.assertEqual(1, r.varint()); self.assertEqual(0, r.tag())

    def test_fixed_values_and_skip(self):
        r = WireInput(bytes.fromhex('09000000000000f43f150000a03f1802'))
        self.assertEqual(9, r.tag()); self.assertEqual(0x3ff4000000000000, r.double_bits())
        self.assertEqual(21, r.tag()); r.skip()
        self.assertEqual(24, r.tag()); r.skip(); self.assertEqual(0, r.tag())

    def test_invalid_tag_and_groups_are_rejected(self):
        for data in ('00', '01', '0b', '0c', '0e', '8080808010'):
            with self.subTest(data=data), self.assertRaises(ValueError): WireInput(bytes.fromhex(data)).tag()

    def test_varint_bounds(self):
        for data in ('80', 'ffffffffffffffffff02', '8080808080808080808000'):
            with self.subTest(data=data), self.assertRaises(ValueError): WireInput(bytes.fromhex(data)).varint()

    def test_truncated_and_negative_lengths(self):
        for data in ('0a034142', '0a80', '090000', '150000'):
            r = WireInput(bytes.fromhex(data)); r.tag()
            with self.subTest(data=data), self.assertRaises(ValueError): r.skip()
        with self.assertRaises(ValueError): WireInput(b'').take(-1)
        with self.assertRaises(ValueError): WireInput(b'').skip()

    def test_repeated_tags_not_misclassified_as_invalid(self):
        r = WireInput(bytes.fromhex('08010802'))
        self.assertEqual([8, 1, 8, 2, 0], [r.tag(), r.varint(), r.tag(), r.varint(), r.tag()])

    def test_binding_requires_every_field_and_unique_tag(self):
        row = {'fields': [dict(name='x', offset=24, offsetKind='instance', type={'kind': 'uint32'})]}
        sample = {'events': [dict(objectOffset=24, key=80, source='0x123')]}
        f = bind_fields(row, [sample])[0]
        self.assertEqual((10, 'unresolved'), (f['tag'], f['semanticStatus']))
        for samples in ([], [sample, {'events': [dict(objectOffset=24, key=88, source='0x123')]}],
                        [{'events': [dict(objectOffset=24, key=82, source='0x123')]}]):
            with self.assertRaises(ValueError): bind_fields(row, samples)

    def test_nested_binding_does_not_claim_content(self):
        row = {'fields': [dict(name='child', offset=24, offsetKind='instance',
                              type=dict(kind='class', name='UnknownChild', typeDefinition=123))]}
        result = bind_fields(row, [{'events': [dict(objectOffset=24, key=10, source='0x123')]}])[0]
        self.assertEqual('not-traced', result['contentStatus'])
        self.assertEqual(123, result['nestedTypeIndex'])

    def test_int32_storage_does_not_imply_varint_wire(self):
        row = {'fields': [dict(name='signed', offset=24, offsetKind='instance', type={'kind': 'int32'})]}
        sample = lambda key: {'events': [dict(objectOffset=24, key=key, source='0x123')]}
        self.assertEqual('sfixed32', bind_fields(row, [sample(125)])[0]['type'])
        self.assertEqual('int32', bind_fields(row, [sample(120)])[0]['type'])
        with self.assertRaises(ValueError): bind_fields(row, [sample(125), sample(120)])


if __name__ == '__main__': unittest.main()
