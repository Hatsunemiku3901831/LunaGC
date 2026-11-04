import unittest
import struct
from tools.extract_client72_signatures import parameter_start, parameter_words, recover_parameters, generic_position, bind_class_parameters


class Parameter72Test(unittest.TestCase):
    def test_generic_positions_from_target_and_context_boundaries(self):
        for index, encoded, expected in ((1, 0xFDF0, 0), (2, 0x5D70, 0), (3, 0xA371, 1), (4, 0x2F0, 0)):
            self.assertEqual(expected, generic_position(index, encoded))
        var = dict(kind='var', attributes='0x2', byRef=True, genericParameterPosition=0)
        nested = dict(kind='genericinst', name='Box', arguments=[var])
        self.assertEqual({'kind': 'uint64', 'attributes': '0x2', 'byRef': True},
                         bind_class_parameters(nested, [{'kind': 'uint64'}])['arguments'][0])
        with self.assertRaisesRegex(ValueError, 'position'):
            bind_class_parameters(var, [])
        with self.assertRaisesRegex(ValueError, 'Method generic'):
            bind_class_parameters(dict(var, kind='mvar'), [{'kind': 'uint64'}])

    def test_independent_parameter_words_and_type_widths(self):
        for index, name, type_word, expected in (
                (0, 0x10DC61B9, 0xDA46A95C, (0x03000027, 33)),
                (153983, 0x83ED7F6B, 0x473C83FF, (0x0B2CDD67, 124)),
                (153984, 0x83DDA23D, 0x475D7727, (0x0B1FB696, 471)),
                (153985, 0x842BB7FB, 0x477C1154, (0x0B2CDD72, 445378))):
            self.assertEqual(expected, parameter_words(index, name, type_word))

    def test_actual_first_parameter_and_table_bounds(self):
        class Names:
            def decode(self, index):
                if index != 0x03000027:
                    raise ValueError('Wrong independent name index')
                return 'msg'
        class Resolver:
            def resolve_index(self, index):
                if index != 33:
                    raise ValueError('Invalid type reference')
                return {'kind': 'string'}
        data = bytearray(64)
        struct.pack_into('<I', data, 30, 0x8AA6E091)
        struct.pack_into('<II', data, 40, 0x10DC61B9, 0xDA46A95C)
        method = dict(index=1, name='GetText', registeredAddress='0x159abc7e0', parameterCount=1)
        records = [dict(index=1, methods=[method])]
        def recover(end=48):
            return recover_parameters(data, records, 0, 40, end, Names(), Resolver())
        rows, refs = recover()
        self.assertEqual([{'index': 0, 'name': 'msg', 'typeIndex': 33}], rows[0]['parameters'])
        self.assertEqual({'33': {'kind': 'string'}}, refs)
        for end in (44, 56, 80):
            with self.assertRaises(ValueError):
                recover(end)
        method['parameterCount'] = 2
        with self.assertRaisesRegex(ValueError, 'bounds'):
            recover()
        method['parameterCount'] = 1
        data[44] ^= 1
        with self.assertRaisesRegex(ValueError, 'type reference'):
            recover()

    def test_independent_method_words_from_target(self):
        for index, encoded, expected in ((178945, 0x9F5C7B10, 153983), (563806, 0xBEE36651, 444015),
                                         (211117, 0xB37B4D97, 173554), (1773, 0x0BB188B7, 2642)):
            self.assertEqual(expected, parameter_start(index, encoded))

    def test_word_boundaries_reject_bool_negative_and_overflow(self):
        for bad in (True, -1, 1 << 32):
            with self.assertRaises(ValueError):
                parameter_start(bad, 0)
            with self.assertRaises(ValueError):
                parameter_words(0, 0, bad)


if __name__ == '__main__':
    unittest.main()
