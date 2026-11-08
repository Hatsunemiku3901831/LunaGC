"""Assertions over the actual bounded original-code critical installer report."""
import hashlib
import json
import os
from pathlib import Path
import struct
import unittest

from tools import probe72_avatar_critical_installer as card


class CriticalInstallerContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        path = Path(os.environ.get('CLIENT72_CRITICAL_INSTALLER_REPORT', str(card.RAW / 'first.json')))
        cls.report = json.loads(path.read_bytes())
        cls.cases = {row['name']: row for row in cls.report['cases']}

    def test_target_original_enum_and_java_are_both_bound(self):
        q = self.report['staticContract']
        self.assertEqual(32419, q['enumType'])
        self.assertEqual({20: 'FIGHT_PROP_CRITICAL', 22: 'FIGHT_PROP_CRITICAL_HURT'},
                         {f['property']: f['symbol'] for f in q['fields']})
        for field in q['fields']:
            self.assertEqual(field['property'], field['javaEnumValue'])
            self.assertEqual(63287, field['sourceOwner'])
            self.assertEqual(54724, field['sourceField']['type']['typeDefinition'])

    def test_four_exact_windows_and_shared_frame_are_source_bound(self):
        q = self.report['staticContract']
        self.assertEqual(4, q['semanticNewWindows'])
        self.assertEqual(2922, sum(x['bytes'] for x in q['windows']))
        self.assertEqual({'0x1498e2200', '0x150854240', '0x1498e56c0', '0x1498e7a60'},
                         {x['start'] for x in q['windows']})
        self.assertEqual('a66deffc6ab5d432cea85fb2a591b4bf8c30b97e553e4efa81c0ee31d6849e5b', q['sharedCallerSHA256'])

    def test_two_independent_nonzero_inputs_and_exchange(self):
        first, second = [self.cases[name] for name in ('independent-nonzero', 'independent-swapped')]
        self.assertEqual(['3e000000', '3ec00000', '3fc00000'], first['inputs'])
        self.assertEqual(['3fc00000', '3ec00000', '3e000000'], second['inputs'])
        for case in (first, second):
            self.assertEqual([case['inputs'][0], case['inputs'][2]], [x['bits'] for x in case['namedReadback']])
            self.assertFalse(case['middlePropertyAliasApproved'])
            self.assertEqual(hex(card.RETURN), case['installer']['stop'])

    def test_every_positive_uses_full_typed_dispatcher_and_original_getter(self):
        for case in self.report['cases'][:-1]:
            self.assertEqual([20, 22], [x['property'] for x in case['namedReadback']])
            for row in case['namedReadback']:
                trace = row['trace']
                self.assertEqual(hex(card.DISPATCHER), trace['entry'])
                self.assertEqual(hex(card.RETURN), trace['stop'])
                self.assertEqual(row['expectedBits'], row['bits'])
                self.assertIn(hex(card.GETTERS[row['property']]), trace['executedAddresses'])
                self.assertIn('0x152e90a60', trace['executedAddresses'])

    def test_three_real_parsers_and_independent_source_spans(self):
        rows = self.report['selectedRows']
        self.assertEqual([10000005, 10000007, 10000136], [x['key'] for x in rows])
        self.assertEqual(3, len({x['source']['sha256'] for x in rows}))
        for row in rows:
            self.assertEqual(hex(card.RETURN), row['constructor']['stop'])
            self.assertEqual(hex(card.RETURN), row['parser']['stop'])
            self.assertEqual('0x14f9935d0', row['parser']['entry'])
            self.assertFalse(row['wholeTableNewlyExecuted'])
            self.assertGreater(row['nativeReadoutInstructionHookVisits'], 0)

    def test_resource_originals_match_frozen_float32_bits(self):
        for aid in (10000005, 10000007, 10000136):
            case = self.cases['original-' + str(aid)]
            self.assertEqual(['3d4ccccd', '3f000000'], [r['bits'] for r in case['namedReadback']])
            self.assertTrue(case['shared63287FragmentOnly'])
            self.assertFalse(case['completeAvatarNaturalPath'])
            self.assertEqual('0x1505f5cc5', case['trace']['entry'])
            self.assertEqual('0x1505f5d30', case['trace']['stop'])
            self.assertEqual(0, int(case['trace']['liveIn']['rsp'], 16) % 16)

    def test_encoded_field_exchange_changes_named_properties(self):
        q = self.cases['birth5-swapped']
        self.assertEqual('swap', q['mutation'])
        self.assertEqual(['3f000000', '3d4ccccd'], [r['bits'] for r in q['namedReadback']])

    def test_real_zero_is_distinct_from_missing_receiver(self):
        q = self.cases['birth5-zero']
        self.assertEqual(['00000000', '3f000000'], [r['bits'] for r in q['namedReadback']])
        missing = self.cases['absent-receiver']
        self.assertTrue(missing['controlledMalformedStorage'])
        self.assertFalse(missing['resourceDefaultApproved'])
        self.assertEqual(['00000000', '00000000'], [r['bits'] for r in missing['results']])
        for row in missing['results']:
            self.assertNotIn(hex(card.GETTERS[row['property']]), row['trace']['executedAddresses'])

    def test_typed_modifiers_are_controlled_nonnull_and_disabled(self):
        for case in self.report['cases'][:-1]:
            store = case['storage']
            self.assertNotEqual(0, store['receiver'])
            self.assertNotEqual(0, store['holder'])
            self.assertNotEqual(0, store['component'])
            self.assertEqual({'receiver': 24887, 'holder': 59769, 'component': 32376},
                             {name: row['type']['typeDefinition'] for name, row in store['classes'].items()})
            for row in store['classes'].values():
                self.assertGreater(row['allocatedBytes'], 0)
            self.assertFalse(store['modifierFlag'])
            self.assertFalse(store['naturalConstructorExecuted'])
        self.assertFalse(self.report['modifierBranchExecuted'])

    def test_existing_codec_model_is_separate_and_counted(self):
        q = self.report['codecModel']
        self.assertEqual((5, 7, 0x12345678), (q['multiplier'], q['shift'], q['randomNext']))
        self.assertTrue(q['resourceScalarCodecPreserved'])
        self.assertTrue(q['resourceAllocationModelsRestored'])
        self.assertFalse(q['naturalRandomObserved'])
        self.assertFalse(q['newSuccessModel'])
        self.assertEqual(['0xbed0', '0x31e60', '0x25a50'], q['aliases'])
        self.assertGreater(q['nativeInitializationHooks'], 0)
        self.assertIn('0x14b770850', self.report['nativeCallTotals'])

    def test_all_native_costs_are_in_the_one_budget(self):
        q = self.report
        self.assertEqual(q['sourceSHA256'], hashlib.sha256(Path(card.__file__).read_bytes()).hexdigest())
        self.assertEqual(q['testSourceSHA256'], hashlib.sha256(Path(__file__).read_bytes()).hexdigest())
        self.assertEqual(8, len(q['cases']))
        self.assertEqual(q['nativeInstructionHookVisits'], sum(r['instructions'] for r in q['nativeCalls']))
        self.assertEqual(q['nativeInstructionHookVisits'], sum(r['instructionHookVisits'] for r in q['nativeCallTotals'].values()))
        self.assertLessEqual(q['nativeInstructionHookVisits'], 500_000)
        for row in q['nativeCalls']:
            self.assertGreater(row['instructions'], 0)
            self.assertLessEqual(row['instructions'], row['countLimit'])
            self.assertLessEqual(row['countLimit'], 2_000_000)
            self.assertIsNone(row['failure'])
            self.assertEqual(row['instructions'], sum(row['executedAddresses'].values()))

    def test_original_entries_and_unknown_modifier_are_not_success_models(self):
        native = {'0x1498e2200', '0x150854240', '0x1498e56c0', '0x1498e7a60', '0x152e90fe0', '0x152e90a60'}
        forbidden = {'0x1518bda40', '0x14c09f510', '0x14dbf1710', '0x14dd204b0', '0x14d868b30'}
        for row in self.report['nativeCalls']:
            self.assertFalse(native & set(row['models']))
            self.assertFalse(forbidden & set(row['executedAddresses']))

    def test_birth_java_fragments_preserve_bits_and_their_limit(self):
        fragments = self.report['javaProjectionFragments']
        self.assertEqual([10000005, 10000007], [f['javaFields']['id'] for f in fragments])
        for fragment in fragments:
            self.assertEqual({'id', 'critical', 'criticalHurt'}, set(fragment['javaFields']))
            self.assertEqual(['3d4ccccd', '3f000000'], [x['bits'] for x in fragment['fields']])
            for field in fragment['fields']:
                value = fragment['javaFields'][field['javaField']]
                self.assertEqual(field['bits'], f'{struct.unpack("<I", struct.pack("<f", value))[0]:08x}')
            self.assertTrue(fragment['fragmentOnly'])
            self.assertFalse(fragment['fullDeclaredJavaInputRow'])
            self.assertFalse(fragment['actualJavaLoaded'])
            self.assertFalse(fragment['naturalAvatarConsumerExecuted'])

    def test_source_pins_and_complete_states_are_not_promoted(self):
        q = self.report
        for name, digest in card.PINS.items():
            self.assertEqual(digest, q['sourcePins'][name])
            self.assertEqual(digest, hashlib.sha256((card.ROOT / name).read_bytes()).hexdigest())
        for key in ('completeAvatarNaturalPath', 'actualRuntimeConstructors', 'targetDefaultApproved',
                    'completeAvatarInputRow', 'javaResourcesLoaded', 'activeRevisionVerified', 'resourceReady', 'd02Complete'):
            self.assertFalse(q[key], key)
        self.assertTrue(q['sourceFieldAliasesApproved'])
        self.assertTrue(q['fiveInputsRecheckedAfterNative'])


if __name__ == '__main__':
    unittest.main()
