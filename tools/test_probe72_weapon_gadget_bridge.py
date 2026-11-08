"""Contract checks over original native Weapon -> named Gadget bridge results."""
import hashlib
import json
import os
from pathlib import Path
import unittest

from tools import probe72_weapon_gadget_bridge as card


class WeaponGadgetBridgeTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        path = Path(os.environ.get('D02_WEAPON_GADGET_REPORT', str(card.RAW / 'first.json')))
        cls.report = json.loads(path.read_bytes())
        cls.cases = {case['name']: case for case in cls.report['cases']}

    def test_three_exact_typed_windows(self):
        q = self.report['staticContract']
        self.assertEqual((3, 10295), (q['newWindowCount'], q['newWindowBytes']))
        self.assertEqual(['0x14c424890', '0x14c4248e0', '0x15153dd80'], [w['start'] for w in q['newWindows']])
        self.assertEqual(['ShowCodexWeapon', 'ChangeCodexWeaponModel'],
                         [w['typedMethods'][0]['name'] for w in q['newWindows'][:2]])
        for window in q['newWindows'][:2]:
            self.assertEqual(87097, window['typedMethods'][0]['parameters'][0]['type']['typeDefinition'])
        self.assertEqual(['uint32', 'uint32'],
                         [p['type']['kind'] for p in q['newWindows'][2]['typedMethods'][0]['parameters'][:2]])

    def test_managed_backing_and_original_field_type(self):
        q = self.report['staticContract']['fields']
        self.assertEqual(88968, q['backing']['type']['typeDefinition'])
        self.assertEqual((442107, 0x2c, 58209),
                         (q['field2c']['index'], q['field2c']['offset'], q['field2c']['type']['typeDefinition']))
        self.assertEqual(40472, q['gadgetCache']['type']['arguments'][1]['typeDefinition'])

    def test_named_original_gadget_contract_is_not_id_equality(self):
        q = self.report['staticContract']
        self.assertEqual('Data/_ExcelBinOutput/GadgetExcelConfigData', q['namedGadgetLiteral']['value'])
        self.assertEqual({'0x152e10fc0', '0x152e110c0'}, {x['entry'] for x in q['reusedNamedQueryMethods']})
        self.assertEqual({54132}, {x['owner'] for x in q['reusedNamedQueryMethods']})

    def test_real_weapon_spans_values_and_encoded_words(self):
        rows = self.report['selectedWeaponRows']
        self.assertEqual([11101, 11501], [r['key'] for r in rows])
        self.assertEqual([(4, 400), (20031, 20463)], [(r['source']['begin'], r['source']['end']) for r in rows])
        self.assertEqual([50011101, 50011501], [r['field2c']['logicalValue'] for r in rows])
        self.assertEqual(['f294755f', 'f2943567'], [r['field2c']['encodedBits'] for r in rows])
        for row in rows:
            self.assertTrue(row['field2c']['gadgetIdAliasApproved'])
            self.assertEqual(hex(card.RETURN), row['constructor']['stop'])
            self.assertEqual(hex(card.RETURN), row['parser']['stop'])
            self.assertGreater(row['readoutInstructionHookVisits'], 0)

    def test_actual_gadget_parse_and_original_cache_insertion(self):
        q = self.report['gadget']
        self.assertEqual(50011101, q['key'])
        self.assertEqual((432396, 432488), (q['source']['begin'], q['source']['end']))
        self.assertEqual('eaf903171c14cfd27367b0bc7012ef7036671d4b7bb0d6c3723d237e6a17b9ad', q['source']['sha256'])
        self.assertTrue(q['originalSceneLogicalValuesEqual'])
        self.assertFalse(q['constructorExecuted'])
        self.assertEqual('0x14e62b290', q['parser']['entry'])
        self.assertEqual('0x1578b6a20', q['insertion']['entry'])
        self.assertEqual([None, 40472], [x.get('typeDefinition') for x in q['cacheType']['arguments']])
        self.assertTrue(q['controlledNonStreaming'])
        self.assertFalse(q['naturalCachePublication'])

    def test_all_six_field_cases_execute_original_named_query(self):
        for case in self.report['cases'][:6]:
            self.assertTrue(case['independentSlices'])
            self.assertFalse(case['continuousNaturalFactory'])
            self.assertFalse(case['entityLifecycleExecuted'])
            self.assertEqual(('0x14c424b6f', '0x14c424bb1'), (case['field']['entry'], case['field']['stop']))
            self.assertEqual(('0x14c424d7c', '0x15153dde5'),
                             (case['callEdgeAndPrologue']['entry'], case['callEdgeAndPrologue']['stop']))
            self.assertEqual(('0x15153dfd0', '0x15153e008'), (case['query']['entry'], case['query']['stop']))
            self.assertTrue({'0x152e10fc0', '0x152e110c0', '0x1578b66f0'} <= set(case['query']['executedAddresses']))

    def test_original_positive_and_selected_cache_negative(self):
        q = self.cases['original-11101']
        self.assertTrue(q['actualParsedGadgetSamePointer'])
        self.assertEqual(self.report['gadget']['pointer'], q['returnedGadgetPointer'])
        missing = self.cases['original-11501-selected-cache-miss']
        self.assertEqual(50011501, missing['decodedId'])
        self.assertEqual('0x0', missing['returnedGadgetPointer'])
        self.assertTrue(missing['expectedMissing'])
        self.assertTrue(missing['selectedCacheOnly'])

    def test_original_encoded_word_exchange_changes_the_lookup(self):
        left = self.cases['11101-encoded-from-11501']
        right = self.cases['11501-encoded-from-11101']
        self.assertEqual(('673594f2', 50011501, '0x0'),
                         (left['suppliedEncodedWord'], left['decodedId'], left['returnedGadgetPointer']))
        self.assertEqual(('5f7594f2', 50011101, self.report['gadget']['pointer']),
                         (right['suppliedEncodedWord'], right['decodedId'], right['returnedGadgetPointer']))

    def test_high_u32_and_empty_cache_are_original_misses(self):
        q = self.cases['uint32-max-missing']
        self.assertEqual(0xffffffff, q['decodedId'])
        self.assertEqual('0x14aefa090', q['encoder']['entry'])
        self.assertTrue(q['expectedMissing'])
        missing = self.cases['original-11101-empty-cache']
        self.assertEqual(50011101, missing['decodedId'])
        self.assertTrue(missing['emptyCache'])
        self.assertEqual('0x0', missing['returnedGadgetPointer'])

    def test_actual_native_prologue_frame_and_argument_are_preserved(self):
        for case in self.report['cases'][:6]:
            frame = case['originalFactoryFrame']
            self.assertEqual(case['decodedId'], frame['originalSecondU32'])
            self.assertEqual(case['decodedId'], int.from_bytes(bytes.fromhex(frame['slotBytes']), 'little'))
            self.assertEqual(hex(int(frame['rbp'], 16) + 0x298), frame['slot'])
            self.assertEqual(frame['rbp'], case['query']['liveIn']['rbp'])
            self.assertEqual(frame['rsp'], case['query']['liveIn']['rsp'])
            self.assertEqual(0, int(frame['rsp'], 16) % 16)
            self.assertTrue(frame['preservedAcrossDeclaredQuery'])
            self.assertFalse(case['query']['stackReturnWritten'])
            factory = next(r for r in case['callEdgeAndPrologue']['observedEntries'] if r['entry'] == '0x15153dd80')
            self.assertEqual(case['decodedId'], int(factory['rdx'], 16))

    def test_later_slot_reuse_is_retained_and_not_a_global_claim(self):
        q = self.report['staticContract']
        self.assertEqual('0x15153ddd9', q['onlyDirectWriteBeforeFirstQuery'])
        self.assertIn('0x15153f785', [x['address'] for x in q['factorySlotWrites']])
        self.assertTrue(q['laterSlotReuseExplicit'])
        self.assertFalse(q['naturalFullFactoryControlFlowVerified'])

    def test_null_weapon_stops_at_original_error(self):
        q = self.cases['null-weapon']
        self.assertEqual('0x1404c2be0', q['trace']['stop'])
        self.assertFalse(q['exceptionHelperExecuted'])
        self.assertFalse(q['resourceDefaultApproved'])
        self.assertNotIn('0x15153dd80', q['trace']['executedAddresses'])

    def test_unique_native_accounting_and_budget(self):
        q = self.report
        self.assertEqual(7, len(q['cases']))
        self.assertEqual(q['nativeInstructionHookVisits'], sum(x['instructions'] for x in q['nativeCalls']))
        self.assertEqual(q['nativeInstructionHookVisits'], sum(x['instructionHookVisits'] for x in q['nativeCallTotals'].values()))
        self.assertLessEqual(q['nativeInstructionHookVisits'], 500_000)
        for trace in q['nativeCalls']:
            self.assertIsNone(trace['failure'])
            self.assertLessEqual(trace['instructions'], trace['countLimit'])
            self.assertLessEqual(trace['countLimit'], 2_000_000)
            self.assertEqual(trace['instructions'], sum(trace['executedAddresses'].values()))
            self.assertFalse({'0x152e10fc0', '0x152e110c0', '0x1578b66f0', '0x1578b6a20', '0x14aef9db0'} & set(trace['models']))
            self.assertNotIn('0x148ba9780', trace['executedAddresses'])

    def test_minimal_java_gadget_fragment_has_no_configuration_permission(self):
        fragments = self.report['javaProjectionFragments']
        self.assertEqual(1, len(fragments))
        q = fragments[0]
        self.assertEqual({'id': 11101, 'gadgetId': 50011101}, q['javaFields'])
        self.assertEqual((88968, 442107, '0x2c', 58209),
                         tuple(q['fieldSource'][k] for k in ('owner', 'fieldIndex', 'offset', 'type')))
        self.assertTrue(q['fragmentOnly'])
        for key in ('completeDeclaredJavaInputRow', 'actualJavaLoaded', 'gadgetConfigurationApproved', 'abilityInputsApproved'):
            self.assertFalse(q[key])

    def test_source_pins_and_runtime_boundaries(self):
        q = self.report
        self.assertEqual(q['sourceSHA256'], hashlib.sha256(Path(card.__file__).read_bytes()).hexdigest())
        self.assertEqual(q['testSourceSHA256'], hashlib.sha256(Path(__file__).read_bytes()).hexdigest())
        for name, expected in card.PINS.items():
            self.assertEqual(expected, q['sourcePins'][name])
            self.assertEqual(expected, hashlib.sha256((card.ROOT / name).read_bytes()).hexdigest())
        self.assertTrue(q['gadgetIdAliasApproved'])
        self.assertTrue(q['fiveInputsRecheckedAfterNative'])
        for key in ('fullFactoryExecuted', 'naturalCodexFlowExecuted', 'entityLifecycleExecuted',
                    'fullTableParsingNewlyExecuted', 'completeItemInputRow', 'javaResourcesLoaded',
                    'activeRevisionVerified', 'resourceReady', 'd02Complete'):
            self.assertFalse(q[key], key)


if __name__ == '__main__':
    unittest.main()
