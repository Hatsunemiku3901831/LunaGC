"""Independent nonempty, type-rejection and boundary assertions."""
import json
import os
import unittest
from pathlib import Path


class OrnamentConditionsTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.report = json.loads(Path(os.environ.get('CLIENT72_ORNAMENT_CONDITIONS_REPORT',
            'local/migration72/d02-active/avatar/ornament/conditions/first.json')).read_text('utf8'))
        cls.rows = {row['name']: row for row in cls.report['native']}

    def test_three_exact_typed_cells_and_virtual_slot(self):
        d = self.report['declarations']
        cells = d['allocationTypeCells']
        self.assertEqual(['0x1458b1048', '0x1458b1050', '0x145886120'], [row['cell'] for row in cells])
        self.assertEqual(58937, cells[0]['type']['elementType']['typeDefinition'])
        self.assertEqual(58937, cells[1]['type']['typeDefinition'])
        self.assertEqual('string', cells[2]['type']['elementType']['kind'])
        self.assertEqual((489549, 4, '0x14e7e66b0'), tuple(d['elementParser'][key] for key in ('index', 'virtualSlot', 'registeredAddress')))

    def test_missing_empty_and_default_elements_have_distinct_bytes(self):
        for name, literal, size, count in (('omitted-array', '367c', 2, 0),
                ('empty-array', '307cd3262012', 6, 0), ('two-omitted-elements', '307cd52620127f877f87', 10, 2)):
            row = self.rows[name]
            self.assertEqual((literal, size, count), (row['binaryHex'], row['cursor'], len(row['actual'])))
            self.assertEqual([dict(kind=0, params=[])] * count, row['actual'])
            self.assertEqual('0x620000100', row['trace']['stop'])

    def test_nonzero_unicode_and_maximum_raw_enum(self):
        row = self.rows['two-independent-nonzero-elements']
        self.assertEqual([dict(kind=73, params=['220141', '1', '风']),
                          dict(kind=0xffffffff, params=['4294967295'])], row['actual'])
        self.assertEqual(77, row['outerId'])
        self.assertEqual(58, row['cursor'])

    def test_five_empty_arrays_are_five_elements(self):
        row = self.rows['five-explicit-empty-parameter-arrays']
        self.assertEqual([dict(kind=0, params=[])] * 5, row['actual'])
        self.assertEqual(5, row['trace']['executedAddresses']['0x14e7e66b0'])
        self.assertEqual(5, row['trace']['executedAddresses']['0x14e7e6663'])

    def test_raw_item_condition_symbol_is_independently_fixed(self):
        enum = self.report['declarations']['conditionEnum']
        self.assertEqual(82900, enum['typeIndex'])
        symbol = next(row for row in enum['values'] if row['symbol'] == 'ITEM_COUNT_GREATER_EQUAL')
        self.assertEqual((7201, 412715, 70954, 637872), tuple(symbol[key] for key in ('value', 'fieldIndex', 'defaultRow', 'dataIndex')))

    def test_candidate_shape_keeps_all_five_string_vectors(self):
        first = self.rows['candidate-shape-projection-101']
        second = self.rows['candidate-shape-projection-102']
        self.assertEqual([dict(kind=0, params=[''] * 5)] * 5, first['actual'])
        self.assertEqual(dict(kind=7201, params=['220141', '1', '', '', '']), second['actual'][0])
        self.assertEqual([dict(kind=0, params=[''] * 5)] * 4, second['actual'][1:])
        self.assertEqual((155, 167), (first['cursor'], second['cursor']))
        self.assertEqual((101, 102), (first['outerId'], second['outerId']))
        self.assertTrue(first['outerText'].endswith('Normal_Alpha'))
        self.assertTrue(second['outerText'].endswith('Special_Alpha'))

    def test_continuous_array_and_virtual_element_paths_are_native(self):
        allowed_models = {'0x140514ee0', '0x1405336c0', '0x14051b7b0',
                          '0x141fdf9c0', '0x141fdf310', '0x14f0de820'}
        for row in self.report['native'][:7]:
            self.assertEqual('0x620000100', row['trace']['stop'])
            self.assertTrue(set(row['trace']['models']).issubset(allowed_models))
            self.assertNotIn('0x14e7e6570', row['trace']['models'])
            self.assertNotIn('0x14e7e66b0', row['trace']['models'])
        for name in ('two-independent-nonzero-elements', 'candidate-shape-projection-102'):
            trace = self.rows[name]['trace']
            self.assertEqual(1, trace['executedAddresses']['0x14a6fe2f0'])
            self.assertEqual(1, trace['executedAddresses']['0x14e7e6570'])
            self.assertEqual(len(self.rows[name]['actual']), trace['executedAddresses']['0x14e7e66b0'])

    def test_wrong_class_null_and_truncated_input_stop(self):
        expected = {'wrong-class': '0x1404c2c70', 'null-object': '0x1404c2be0',
                    'null-array': '0x1404c2be0', 'truncated-first-element': '0x1404c2650',
                    'truncated-nonzero-kind': '0x1404c2650'}
        for name, stop in expected.items():
            trace = self.rows[name]['trace']
            self.assertEqual(stop, trace['stop'])
            self.assertNotIn(stop, trace['models'])
        self.assertEqual(1, sum(not x['fixtureClassMatches'] for x in self.report['models']['typeChecks']))
        self.assertEqual(1, sum(x['deliberateWrongClass'] for x in self.report['models']['objectAllocations']))

    def test_raw_parser_does_not_establish_alias_or_evaluation(self):
        self.assertEqual(12, len(self.rows))
        self.assertTrue(self.report['selectedNativeArrayParserComplete'])
        self.assertEqual([], self.report['aliasesApproved'])
        for key in ('candidateJSONAliasesVerified', 'conditionEvaluationVerified', 'completeCacheBuilder',
                    'candidateRuntimeRevisionVerified', 'completeAvatarHandler', 'targetProtocolReady'):
            self.assertFalse(self.report[key])
        self.assertEqual('not-tested', self.report['runtimeStatus'])


if __name__ == '__main__':
    unittest.main()
