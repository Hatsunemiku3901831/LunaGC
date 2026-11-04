import struct
import unittest

from tools.extract_client72_wire import recover_field, recover_read_field


class Code:
    def __init__(self, data):
        self.data = data

    def offset(self, address, size=1, **kwargs):
        offset = address - 0x15182C047
        if offset < 0 or offset + size > len(self.data):
            raise ValueError('Out of test code')
        return offset


class Wire72ExtractionTest(unittest.TestCase):
    def test_response_reader_uses_metadata_signedness(self):
        # Actual PingRsp tag comparison and read/store instruction shapes.
        code = bytearray.fromhex('83f858') + bytearray(24)
        call_va, store_va = 0x15182C057, 0x15182C05C
        code[16:21] = b'\xe8' + struct.pack('<i', 0x147520110 - call_va - 5)
        code[21:24] = bytes.fromhex('894720')
        fields = [dict(name='LDLIEKOAFBP', offsetKind='instance', offset=32, type={'kind': 'int32'})]
        result = recover_read_field(Code(code), fields, 0x15182C047, call_va, store_va)
        self.assertEqual((11, 'int32'), (result['tag'], result['type']))
        code[2] = 0x5a
        with self.assertRaises(ValueError):
            recover_read_field(Code(code), fields, 0x15182C047, call_va, store_va)

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
