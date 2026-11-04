import struct
import unittest

from tools.extract_client72_wire import recover_field


class Code:
    def __init__(self, data):
        self.data = data

    def offset(self, address, size=1, **kwargs):
        offset = address - 0x15182C047
        if offset < 0 or offset + size > len(self.data):
            raise ValueError('Out of test code')
        return offset


class Wire72ExtractionTest(unittest.TestCase):
    def setUp(self):
        # Independent reviewed target fragment: tag1, load object+30, varint64.
        self.data = bytearray.fromhex('c6440120084c8b4730803dc5e5faf3000f85f20300004889f14c89c2e8f8a8f3ff')
        self.field = dict(name='LBEAJJBNBIF', offsetKind='instance', offset=0x30,
                          type=dict(kind='uint64', attributes='0x6'))

    def recover(self, fields=None):
        return recover_field(Code(self.data), fields or [self.field], 0x15182C047, 0x15182C063)

    def test_target_fragment_joins_tag_field_and_helper(self):
        result = self.recover()
        self.assertEqual((1, 'uint64', 0x30), (result['tag'], result['type'], result['objectOffset']))
        self.assertEqual('unresolved', result['semanticStatus'])

    def test_wrong_wire_type_and_unreviewed_helper_fail(self):
        self.data[4] = 0x0D
        with self.assertRaisesRegex(ValueError, 'wire type'):
            self.recover()
        self.data[4] = 0x08
        self.data[-1] ^= 1
        with self.assertRaisesRegex(ValueError, 'Unreviewed'):
            self.recover()

    def test_ambiguous_or_wrong_layout_fails(self):
        with self.assertRaisesRegex(ValueError, 'ambiguous'):
            self.recover([self.field, self.field])
        self.field['offset'] = 0x28
        with self.assertRaisesRegex(ValueError, 'absent'):
            self.recover()

    def test_scalar_width_zero_tag_and_incomplete_key_fail(self):
        self.field['type']['kind'] = 'uint32'
        with self.assertRaisesRegex(ValueError, 'scalar'):
            self.recover()
        for key in (0, 0x80):
            self.data[4] = key
            with self.assertRaisesRegex(ValueError, 'single-byte'):
                self.recover()


if __name__ == '__main__':
    unittest.main()
