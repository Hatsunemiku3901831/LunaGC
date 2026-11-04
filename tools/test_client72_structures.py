"""Malformed structure tests and explicit 7.2 table ordering, not wire claims."""
import struct
import unittest

from tools.extract_client72 import TypeResolver, recover_parents72, recover_layouts72


class FakePE:
    def __init__(self, data):
        self.data = data

    def offset(self, address, size=1, **kwargs):
        offset = address - 0x1000
        if offset < 0 or size < 0 or offset + size > len(self.data):
            raise ValueError('Unbacked test address')
        return offset


class Structures72Test(unittest.TestCase):
    def setUp(self):
        self.binary = bytearray(0x1000)
        self.startup = bytearray(32)
        self.records = [dict(index=0, namespace='System', name='Object'),
                        dict(index=1, namespace='Example', name='Box`1'),
                        dict(index=2, namespace='Example', name='Derived')]
        # Header at 0, registration at 0x300, refs at 0x500, GenericInst at 0x700.
        struct.pack_into('<I', self.binary, 0xD0, (-0xE1492096) & 0xFFFFFFFF)
        struct.pack_into('<I', self.binary, 0x44, (16 - 0xE42E197F) & 0xFFFFFFFF)
        struct.pack_into('<Q', self.binary, 0x300, 0x1500)
        struct.pack_into('<I', self.binary, 0x38C, (4 - 0xCE67355C) & 0xFFFFFFFF)
        struct.pack_into('<Q', self.binary, 0x370, 0x1700)
        # 7.2 startup record is (instance index, definition index), not reversed.
        struct.pack_into('<IIII', self.startup, 0, 0, 1, 1, 1)
        struct.pack_into('<QQQQ', self.binary, 0x700, 1, 0x1800, 1, 0x1808)
        struct.pack_into('<QQ', self.binary, 0x800, 0x1500, 0x1510)
        self.ref(0, 9, 0)
        self.ref(1, 21, 0)
        self.ref(2, 21, 1)
        self.ref(3, 28, 0)

    def ref(self, index, kind, value):
        struct.pack_into('<QI', self.binary, 0x500 + index * 16, value, kind << 16)

    def resolver(self):
        return TypeResolver(FakePE(self.binary), self.startup, self.records, 0, registration=0x1300)

    def test_nested_generic_arguments_and_new_record_order(self):
        r = self.resolver().resolve_index(2)
        self.assertEqual('Box`1', r['name'])
        self.assertEqual('Box`1', r['arguments'][0]['name'])
        self.assertEqual('uint32', r['arguments'][0]['arguments'][0]['kind'])

    def test_recursive_generic_and_array_arguments_are_rejected(self):
        self.ref(0, 21, 1)
        with self.assertRaisesRegex(ValueError, 'recursive'):
            self.resolver().resolve_index(2)
        self.ref(0, 29, 0x1500)
        with self.assertRaisesRegex(ValueError, 'recursive'):
            self.resolver().resolve_index(0)

    def test_generic_definition_and_argument_pointer_bounds(self):
        struct.pack_into('<I', self.startup, 4, 3)
        with self.assertRaises(ValueError):
            self.resolver()
        struct.pack_into('<I', self.startup, 4, 1)
        struct.pack_into('<Q', self.binary, 0x800, 0x1501)
        with self.assertRaises(ValueError):
            self.resolver()

    def test_array_element_is_a_reference_not_a_type_definition(self):
        self.ref(2, 29, 0x1500)
        result = self.resolver().resolve_index(2)
        self.assertEqual('uint32', result['elementType']['kind'])
        self.assertEqual(1, result['rank'])

    def test_byref_flag_is_separate_from_element_kind_and_attributes(self):
        struct.pack_into('<I', self.binary, 0x508, 0x40090002)
        result = self.resolver().resolve_index(0)
        self.assertEqual('uint32', result['kind'])
        self.assertEqual('0x2', result['attributes'])
        self.assertTrue(result['byRef'])

    def test_parent_links_and_cycle_detection(self):
        metadata = bytearray(3 * 70)
        for index, parent in enumerate((0xFFFFFFFF, 3, 1)):
            struct.pack_into('<I', metadata, index * 70, (parent - 0xA0A35767) & 0xFFFFFFFF)
        report = {}
        recover_parents72(metadata, 0, self.records, self.resolver(), report)
        self.assertIsNone(self.records[0]['parent'])
        self.assertEqual(0, self.records[1]['parent']['typeDefinition'])
        self.assertEqual(1, self.records[2]['parent']['typeDefinition'])
        struct.pack_into('<I', metadata, 70, (1 - 0xA0A35767) & 0xFFFFFFFF)
        with self.assertRaisesRegex(ValueError, 'Cyclic'):
            recover_parents72(metadata, 0, self.records, self.resolver(), {})

    def test_layout_marks_runtime_literal_and_zero_instance_offsets_unknown(self):
        data = bytearray(0x300)
        for offset, constant, target in ((0x168, 0xB01C236D, 0x220),
                                        (0x14C, 0xDE642132, 0x230), (0x154, 0x936A8A40, 0x250)):
            struct.pack_into('<I', self.binary, offset, (target - 0x210 - constant) & 0xFFFFFFFF)
        struct.pack_into('<i', data, 0x220, 0)
        struct.pack_into('<III', data, 0x230, 32, 0x80000, 0)
        struct.pack_into('<IIIII', data, 0x250, 24, 0x4001680, 0, 0, 0)
        record = dict(index=0, fieldCount=5, fields=[{'type': {'attributes': hex(a)}} for a in (1, 0x31, 0x8051, 1, 0x11)])
        recover_layouts72(FakePE(self.binary), data, 0, [record], {})
        self.assertEqual(['instance', 'runtime', 'literal', 'runtime', 'static'],
                         [f['offsetKind'] for f in record['fields']])
        self.assertEqual([24, None, None, None, 0], [f['offset'] for f in record['fields']])
        struct.pack_into('<i', data, 0x220, 0xFFFFFF)
        with self.assertRaises(ValueError):
            recover_layouts72(FakePE(self.binary), data, 0, [record], {})


if __name__ == '__main__':
    unittest.main()
