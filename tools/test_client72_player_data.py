import json
from pathlib import Path
import unittest
from types import SimpleNamespace
from tools.extract_client72 import HASHES
from tools.trace_client72_player_data import ROOT, VALUE, fixtures, verify


class PlayerDataRawTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.report = json.loads((Path(__file__).resolve().parents[1] /
            'protocol/cn-7.2/player-data-wire-observations.json').read_text('utf8'))
        cls.cases = {x['name']: x for x in cls.report['fixtures']}

    def test_target_inputs_and_candidate_boundary(self):
        self.assertEqual(HASHES, self.report['inputSHA256'])
        self.assertEqual('unresolved', self.report['semanticIdentityStatus'])
        self.assertFalse(self.report['targetProtocolReady'])
        self.assertEqual('not-tested', self.report['runtimeStatus'])
        self.assertEqual([(7193, ROOT, 'BIMKHOFCKHB')], [(x['cmdId'], x['typeIndex'], x['clientType']) for x in self.report['commands']])

    def test_complete_raw_layout_and_bounded_scan(self):
        self.assertEqual(256, self.report['scan']['nativeFixtures'])
        raw = {(x['typeIndex'], x['tag'], x['wireType']) for x in self.report['scan']['fields']}
        self.assertEqual({(ROOT, 5, 0), (ROOT, 6, 2), (ROOT, 7, 0), (ROOT, 8, 2), (ROOT, 13, 0),
                          (VALUE, 1, 0), (VALUE, 2, 0), (VALUE, 3, 5), (VALUE, 4, 0)}, raw)
        for row in self.report['scan']['fields']:
            self.assertTrue(row['stores'] or row['result'])
        child = next(x for x in self.report['messages'] if x['typeIndex'] == VALUE)
        self.assertEqual([(2, 2), (3, 3)], [(x['tag'], x['discriminatorValue']) for x in child['fields'] if 'discriminatorValue' in x])

    def test_independent_high64_wire_and_noncanonical_bool(self):
        row = self.cases['outer-nonzero-high64']
        self.assertEqual('280138014203416263688180808080808010', row['wire'])
        self.assertEqual(9007199254740993, row['result']['KFICMDLCEHE'])
        upper = self.cases['outer-max-and-bool-normalization']['result']
        self.assertEqual(18446744073709551615, upper['KFICMDLCEHE'])
        self.assertEqual(4294967295, upper['PLMNNCGEMIN'])
        self.assertIs(True, upper['OMFPDOKCLNL'])

    def test_signed_and_float_oneof_keep_zero_presence(self):
        self.assertEqual({'BNJKCHONGGE_int64': 0}, self.cases['value-oneof-explicit-zero']['result'])
        self.assertEqual({'BNJKCHONGGE_float': 0.0}, self.cases['value-float-explicit-zero']['result'])
        self.assertEqual({'BNJKCHONGGE_int64': 2}, self.cases['value-oneof-switch-and-return']['result'])
        self.assertEqual({'BNJKCHONGGE_float': -1.25}, self.cases['value-float']['result'])
        self.assertEqual({'BNJKCHONGGE_int64': 9223372036854775807, 'AEJAFPNBBHO': -9223372036854775808},
                         self.cases['value-signed-boundaries']['result'])

    def test_nonempty_map_native_path_and_value_replacement(self):
        first = self.cases['nonempty-map-int64']
        self.assertEqual({'IKIFIBAEDGD': {'7': {'NOEEBKJAFAG': 42, 'BNJKCHONGGE_int64': -1}}}, first['result'])
        self.assertTrue(any(x.get('typeIndex') == VALUE for x in first['entered']))
        replace = self.cases['map-duplicate-replaces-value']
        self.assertEqual({'IKIFIBAEDGD': {'7': {'BNJKCHONGGE_float': 2.5}}}, replace['result'])
        self.assertTrue(replace['mapStores'])
        self.assertEqual({'IKIFIBAEDGD': {'1': {'BNJKCHONGGE_int64': 1}, '2': {}}},
                         self.cases['map-two-keys-and-empty-default']['result'])

    def test_concrete_aot_arguments_and_shared_native_body(self):
        rows = self.report['aotBindings']
        concrete = next(x for x in rows if (x['owner'], x['classInstance'], x['method']) == (66210, 9505, 'FNNADLCNGFO'))
        shared = next(x for x in rows if (x['owner'], x['classInstance'], x['method']) == (66210, 1541, 'FNNADLCNGFO'))
        self.assertEqual('uint32', concrete['arguments'][0]['kind'])
        self.assertEqual(VALUE, concrete['arguments'][1]['typeDefinition'])
        self.assertEqual('0x156d35a40', concrete['address'])
        self.assertEqual(shared['address'], concrete['address'])

    def test_defaults_duplicates_wrong_wire_and_model_errors(self):
        self.assertEqual({}, self.cases['empty']['result'])
        self.assertEqual({}, self.cases['outer-explicit-zero']['result'])
        self.assertEqual({'PLMNNCGEMIN': 2}, self.cases['outer-duplicates-and-wrong-wire']['result'])
        self.assertEqual(4, len(self.report['rejections']))
        self.assertTrue(all(x['scope'] == 'explicit-input-model' for x in self.report['rejections']))

    def test_result_drift_fails_before_publishing(self):
        bad = SimpleNamespace(run_graph=lambda *args: {'result': {'invented': True}})
        with self.assertRaisesRegex(ValueError, 'native graph differs'):
            verify(bad)
        self.assertEqual(len(fixtures()), self.report['nativeFixtures'])


if __name__ == '__main__': unittest.main()
