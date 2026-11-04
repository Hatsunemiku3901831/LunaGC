import unittest
from tools.trace_client72_token import parse_wire, varint, bind_wire, fixture_values


class ProtectedWireTest(unittest.TestCase):
    def test_independent_wire_parser_and_large_tags(self):
        self.assertEqual([(1, 2, b'AB'), (6, 0, 4294967297)], parse_wire(bytes.fromhex('0a024142308180808010')))
        self.assertEqual([(1730, 2, b'x')], parse_wire(bytes.fromhex('926c0178')))
        self.assertEqual(bytes.fromhex('ffffffffffffffffff01'), varint((1 << 64) - 1))

    def test_malformed_tags_lengths_varints_and_duplicate_rejected(self):
        for wire in ('00', '0a0241', '0880', '088080808080808080808002', '08010802', '0d00000000'):
            with self.subTest(wire=wire), self.assertRaises(ValueError):
                parse_wire(bytes.fromhex(wire))

    def test_controlled_identity_and_full_field_coverage(self):
        fields = [dict(name='A', offset=24, offsetKind='instance', type={'kind': 'uint32'}),
                  dict(name='B', offset=32, offsetKind='instance', type={'kind': 'string'})]
        values = {24: 300, 32: b'AB'}
        mapped = bind_wire(fields, values, bytes.fromhex('08ac0212024142'))
        self.assertEqual([(1, 'A'), (2, 'B')], [(r['tag'], r['clientField']) for r in mapped])
        for wire in ('08ac02', '08ad0212024142'):
            with self.assertRaises(ValueError):
                bind_wire(fields, values, bytes.fromhex(wire))
        self.assertEqual({24: 0, 32: b''}, fixture_values(fields, 'zero'))


if __name__ == '__main__':
    unittest.main()
