import unittest
from tools.trace_client72_login import observed_fields
from tools.observed_proto72 import fixture_text, render


class LoginObservationTest(unittest.TestCase):
    def setUp(self):
        self.row = {'fields': [dict(name='number', offset=24, offsetKind='instance', type={'kind': 'uint32'}),
                               dict(name='flag', offset=28, offsetKind='instance', type={'kind': 'bool'})]}
        self.base = {'wire': '08ac02', 'values': {24: 300, 28: 0}}

    def test_bool_one_hot_and_metadata_binding(self):
        fields = observed_fields(self.row, self.base, {28: {'wire': '08ac023001'}})
        self.assertEqual([(1, 24, 'uint32'), (6, 28, 'bool')],
                         [(f['tag'], f['objectOffset'], f['type']) for f in fields])
        self.assertTrue(all(f['semanticStatus'] == 'unresolved' for f in fields))

    def test_bool_changes_to_other_fields_rejected(self):
        for wire in ('08ad023001', '08ac023002', '08ac0230013801', '3001', '08ac02'):
            with self.subTest(wire=wire), self.assertRaises(ValueError):
                observed_fields(self.row, self.base, {28: {'wire': wire}})

    def test_missing_duplicate_and_unknown_values_rejected(self):
        for wire in ('', '08ad02', '08ac0208ac02'):
            with self.subTest(wire=wire), self.assertRaises(ValueError):
                observed_fields(self.row, dict(self.base, wire=wire), {})
        with self.assertRaisesRegex(ValueError, 'Ambiguous'):
            observed_fields(self.row, dict(self.base, values={24: 300, 28: 300}), {})

    def test_nested_fixture_uses_original_inputs(self):
        common = dict(cardinality='singular', packed=False)
        child = dict(typeIndex=1, clientType='Child', fields=[dict(common, clientField='negative', objectOffset=24, type='int32', tag=1)])
        parent = dict(typeIndex=2, clientType='Parent', fields=[dict(common, clientField='case_11', objectOffset=24,
            type='message', tag=11, nestedType='Child', nestedTypeIndex=1, oneof='body', discriminatorValue=11)])
        # Intentionally opaque bytes: text is constructed from controlled object inputs, not decoded wire.
        leaf = dict(typeIndex=1, wire='abcd', values={'24': -1})
        sample = dict(typeIndex=2, case=11, values={'24': ['message', {'hex': 'abcd'}]})
        self.assertEqual('case_11 { negative: -1 }', fixture_text(sample, {1: child, 2: parent}, {(1, 'abcd'): leaf}))
        self.assertEqual('', fixture_text(dict(sample, case=0), {2: parent}, {}))
        source = render([child, parent])
        self.assertIn('oneof body {\n    Child case_11 = 11;', source)
        self.assertEqual(source, render([child, parent]))

    def test_bytes_list_and_identifier_safety(self):
        fields = [dict(clientField='raw', objectOffset=24, type='bytes', cardinality='singular'),
                  dict(clientField='list', objectOffset=32, type='uint64', cardinality='repeated')]
        sample = dict(typeIndex=1, values={'24': ['bytes', {'hex': '00ff'}], '32': ['list64', [1, 9007199254740993]]})
        self.assertEqual('raw: "\\000\\377" list: 1 list: 9007199254740993', fixture_text(sample, {1: {'fields': fields}}, {}))
        with self.assertRaises(ValueError):
            render([dict(clientType='invalid;type', fields=[])])


if __name__ == '__main__': unittest.main()
