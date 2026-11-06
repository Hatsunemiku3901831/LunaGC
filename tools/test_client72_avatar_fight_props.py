"""Independent bits, clear semantics and target-named FightProp consumers."""
import hashlib
import json
import os
from pathlib import Path
import unittest

from tools.client72_avatar_fight_props import EVIDENCE_SHA, IDS, NATIVE
from tools.extract_client72 import HASHES


class AvatarFightPropsTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.root = Path(__file__).resolve().parents[1]
        path = Path(os.environ.get('CLIENT72_AVATAR_FIGHT_PROPS_OBSERVATIONS', cls.root / 'protocol/cn-7.2/avatar-fight-props-observations.json'))
        cls.report = json.loads(path.read_text('utf8')); cls.rows = {x['name']: x for x in cls.report['fixtures']}

    def test_fixed_sources_and_no_full_battle_or_entry_promotion(self):
        self.assertEqual(HASHES, self.report['inputSHA256']); self.assertEqual(EVIDENCE_SHA, self.report['inputContracts'])
        self.assertEqual('F-03d3f4b', self.report['task']); self.assertEqual(23, len(self.rows))
        for key in ('completeAvatarHandler', 'completeEntityEffects', 'allFightPropIdsApproved', 'targetProtocolReady'):
            self.assertIs(False, self.report[key])
        self.assertEqual('not-tested', self.report['runtimeStatus'])
        self.assertEqual('not-established', self.report['producer']['businessRequiredForEntry'])
        for path, digest in self.report['sourceSHA256'].items():
            self.assertEqual(digest, hashlib.sha256((self.root / path).read_bytes()).hexdigest())

    def test_target_enum_is_bound_to_real_getter_and_callback_parameters(self):
        methods = {x['method']['registeredAddress']: x for x in self.report['declarations']['methods']}
        for entry in ('0x152f80950', '0x15184cca0', '0x151845780'):
            self.assertEqual(32419, methods[entry]['parameters'][0]['type']['typeDefinition'])
        enum = next(x for x in self.report['declarations']['enums'] if x['typeIndex'] == 32419)
        self.assertEqual({v: k for k, v in IDS.items()}, {x['symbol']: x['value'] for x in enum['values']})
        self.assertEqual('CheckAvatarProp', methods['0x152585120']['method']['name'])
        self.assertEqual('set_hpRatio', methods['0x14f44f530']['method']['name'])

    def test_float_codec_and_actual_aot_are_distinct_from_normal_double_properties(self):
        declared = self.report['declarations']
        self.assertEqual([32758, 30710], [x['typeDefinition'] for x in declared['encodedDictionary']['type']['arguments']])
        self.assertEqual({'0x15720a600', '0x158f3f170', '0x158f3f4c0'}, {x['address'] for x in declared['concreteAOT']})
        row = self.rows['seven-nonzero-properties']; trace = row['steps'][0]['apply']
        self.assertTrue({'0x152e90fe0', '0x152e90a60', '0x152f7e830', '0x151852cf0'} <= set(trace['nativeEntries']))
        self.assertFalse({'0x152e90fe0', '0x152e90a60'} & set(trace['models']))

    def test_seven_distinct_nonzero_fields_reach_getter_and_named_watcher(self):
        row = self.rows['seven-nonzero-properties']; step = row['steps'][0]
        expected = {'1010': '42f70000', '2000': '43fa0000', '1': '43c80000', '4': '42740000',
                    '7': '41a40000', '2001': '42b60000', '2002': '42220000'}
        self.assertEqual(expected, row['rawNodeBits'])
        reads = {str(x['id']): x['bits'] for x in step['reads']}
        self.assertEqual(expected, {k: reads[k] for k in expected})
        for watch in step['watcher']:
            self.assertTrue(watch['equal']['result']); self.assertFalse(watch['different']['result'])
            self.assertTrue({'0x152585120', '0x14e9b1040', '0x152f80950'} <= set(watch['equal']['trace']['nativeEntries']))

    def test_omission_clears_preexisting_values_and_partial_map_does_not_keep_max_hp(self):
        step = self.rows['omitted-map-clears']['steps'][0]
        self.assertTrue(step['outputDictionaryIsNull']); self.assertEqual([], step['inputs']); self.assertEqual([], step['events'])
        self.assertEqual([{'nativeCount': 0, 'entriesZero': True}], step['returns'])
        self.assertEqual([{'elementWidth': 4, 'index': 0, 'count': 64}, {'elementWidth': 40, 'index': 0, 'count': 3}], step['clears'])
        self.assertEqual({'00000000'}, {x['bits'] for x in step['reads']})
        partial = self.rows['current-hp-only']['steps'][0]
        self.assertEqual('00000000', next(x['bits'] for x in partial['reads'] if x['id'] == 2000))
        self.assertEqual('00000000', partial['namedHp']['ratioBits'])

    def test_duplicate_map_key_and_explicit_zero_are_applied_once_per_final_key(self):
        row = self.rows['duplicate-current-hp-last-wins']; step = row['steps'][0]
        self.assertEqual({'1010': '42f70000', '2000': '43fa0000'}, row['rawNodeBits'])
        self.assertEqual([1010, 2000], [x['id'] for x in step['inputs']])
        zero = self.rows['explicit-zero-overwrite']
        self.assertEqual('00000000', zero['rawNodeBits']['1010'])
        self.assertEqual([374, 374, 375, 376], [x['id'] for x in zero['steps'][0]['events']])

    def test_same_map_reapplies_clear_and_full_64_bit_event_guid(self):
        row = self.rows['same-map-reapplied']; self.assertEqual(2, len(row['steps']))
        self.assertEqual(3, row['steps'][0]['clears'][1]['count']); self.assertEqual(7, row['steps'][1]['clears'][1]['count'])
        for step in row['steps']:
            self.assertEqual({9223372036854775809}, {x['guid'] for x in step['events']})
        self.assertEqual({0}, {x['guid'] for x in self.rows['zero-guid-events']['steps'][0]['events']})
        self.assertEqual({0xffffffffffffffff}, {x['guid'] for x in self.rows['max-guid-events']['steps'][0]['events']})

    def test_actual_hp_ratio_named_ui_and_life_state_suppression(self):
        live = self.rows['seven-nonzero-properties']['steps'][0]
        dead = self.rows['dead-state-ratio-suppressed']['steps'][0]
        self.assertEqual('3e7ced91', live['namedHp']['ratioBits'])
        self.assertEqual('00000000', dead['namedHp']['ratioBits']); self.assertEqual('3e7ced91', dead['rawRatioBits'])
        self.assertTrue(dead['ratioWatcher']['result'])
        self.assertEqual('3f800000', self.rows['ratio-clamps-above-one']['steps'][0]['namedHp']['ratioBits'])
        self.assertEqual('00000000', self.rows['ratio-clamps-below-zero']['steps'][0]['namedHp']['ratioBits'])
        self.assertEqual('0x14f44f530', live['namedHp']['trace']['stop']); self.assertTrue(live['namedHp']['receiverMatches'])

    def test_float_bit_boundaries_are_preserved_without_gameplay_approval(self):
        expected = {'positive-zero': '00000000', 'negative-zero': '80000000', 'min-subnormal': '00000001',
                    'max-finite': '7f7fffff', 'negative-fraction': 'c1240000', 'positive-infinity': '7f800000',
                    'negative-infinity': 'ff800000', 'quiet-nan-payload': '7fc12345'}
        for name, bits in expected.items():
            row = self.rows['float-' + name]; self.assertEqual(bits, row['rawNodeBits']['7'])
            self.assertEqual(bits, next(x['bits'] for x in row['steps'][0]['reads'] if x['id'] == 7))
        nan = next(x for x in self.rows['float-quiet-nan-payload']['steps'][0]['watcher'] if x['id'] == 7)
        self.assertFalse(nan['equal']['result'])

    def test_wrong_wire_and_unknown_keys_keep_raw_scope(self):
        outer = self.rows['wrong-outer-wire-skipped']['steps'][0]
        self.assertTrue(outer['outputDictionaryIsNull']); self.assertEqual([], outer['inputs'])
        inner = self.rows['wrong-float-wire-defaults-zero']; self.assertEqual({'1010': '00000000'}, inner['rawNodeBits'])
        self.assertEqual([0, 4242, 0xffffffff], self.report['semanticEvidence']['unknownRawIds'])
        self.assertEqual({'0': '3f800000', '4242': '40200000', '4294967295': 'c0600000'}, self.rows['unknown-keys-remain-raw']['rawNodeBits'])

    def test_complete_application_fragment_and_native_model_boundary(self):
        native = {hex(x) for x in NATIVE}
        for row in self.rows.values():
            for step in row['steps']:
                trace = step['apply']; self.assertEqual(('0x15184e3bf', '0x15184e3f3'), (trace['entry'], trace['stop']))
                self.assertTrue({'0x1518517c0', '0x152f7f190', '0x151852cf0', '0x15720a600'} <= set(trace['nativeEntries']))
                self.assertFalse(native & set(trace['models']))
                self.assertTrue(step['missingAvatarWatcher']['result'])

    def test_raw_error_consistency_failure_and_runtime_null_are_distinct(self):
        errors = {x['name']: x for x in self.report['rejections']}; self.assertEqual(5, len(errors))
        self.assertEqual('native-reader-error-helper', errors['negative-map-length']['scope'])
        self.assertEqual('explicit-input-model', errors['invalid-tag']['scope'])
        self.assertEqual('0x152e90d75', errors['corrupted-float-storage']['trace']['stop'])
        self.assertEqual('native-consistency-failure-branch-boundary', errors['corrupted-float-storage']['scope'])
        self.assertTrue(errors['null-map-after-clear']['dictionaryCleared'])
        self.assertEqual('0x1404c2be0', errors['null-map-after-clear']['trace']['stop'])


if __name__ == '__main__': unittest.main()
