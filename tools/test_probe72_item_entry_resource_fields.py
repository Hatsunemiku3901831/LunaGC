"""Source-bound regression for the limited Item11101 affix fragment."""
import copy
import json
import os
from pathlib import Path
import unittest

from tools import probe72_item_entry_resource_fields as probe


class ItemEntryResourceFieldsTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        path = Path(os.environ.get('D02_ITEM_ENTRY_REPORT', str(probe.RAW / 'first.json')))
        cls.report = json.loads(path.read_bytes())
        cls.row = next(x for x in cls.report['selectedRows'] if x['key'] == 11101)
        cls.other = next(x for x in cls.report['selectedRows'] if x['key'] == probe.CONTROL_ITEM)

    def test_birth_zeros_are_source_slots(self):
        self.assertEqual(self.row['affixArray']['logicalValues'], [0, 0])
        self.assertEqual(self.row['affixArray']['count'], 2)
        self.assertTrue(self.row['affixArray']['present'])
        fragment = probe.project_item(self.row)
        self.assertEqual(fragment['javaFields'], {'id': 11101, 'skillAffix': [0, 0]})
        self.assertEqual(fragment['gameItemPositiveAffixes'], [])

    def test_independent_actual_nonzero_order(self):
        values = self.other['affixArray']['logicalValues']
        self.assertTrue(any(x > 0 for x in values))
        reached = [x['group'] for x in self.report['affixConsumers'] if x['itemId'] == probe.CONTROL_ITEM]
        self.assertEqual(reached, values)
        self.assertEqual(len(values), self.other['affixArray']['count'])

    def test_actual_native_array_abi(self):
        fixture = self.report['arrayInterfaceFixture']
        self.assertEqual([x['type']['typeDefinition'] for x in fixture['interfaces']], [1244, 1250])
        self.assertEqual([x['native'] for x in fixture['interfaces']], [hex(probe.COUNT), hex(probe.ITEM)])
        self.assertEqual((fixture['countMethodDefinition'], fixture['itemMethodDefinition']), (991, 1004))
        self.assertFalse(fixture['pythonCountOrIndexCallback'])
        self.assertFalse(fixture['naturalCLRInitializationExecuted'])
        for case in self.report['affixConsumers']:
            for entry in (probe.COUNT, probe.ITEM, probe.ARRAY_GET, 0x14aef9db0):
                self.assertIn(hex(entry), case['trace']['executedAddresses'])
            self.assertEqual(case['trace']['stop'], hex(probe.GROUP_GETTER))
            self.assertTrue(case['stoppedBeforeGroupLookup'])

    def test_aot_method_arguments_are_actual_encoded_type(self):
        self.assertEqual([x['methodDefinition'] for x in self.report['arrayMethodAOT']], [999, 1004])
        for row in self.report['arrayMethodAOT']:
            self.assertEqual(row['classInstance'], -1)
            self.assertEqual(row['methodArgumentInstance'], 3826)
            self.assertEqual([x['typeDefinition'] for x in row['arguments']], [58209])

    def test_gadget_candidate_is_not_alias(self):
        self.assertEqual(self.row['field2c']['logicalValue'], 50011101)
        self.assertFalse(self.row['field2c']['gadgetIdAliasApproved'])
        self.assertEqual(self.row['field2c']['getter']['entry'], hex(probe.FIELD2C_GETTER))
        self.assertNotIn('gadgetId', probe.project_item(self.row)['javaFields'])
        self.assertFalse(self.report['gadgetIdAliasApproved'])
        self.assertEqual(self.report['staticEvidence']['field2cDirectCallers'], [])
        self.assertTrue(self.report['staticEvidence']['filterIsNotExhaustiveProof'])

    def test_missing_array_is_not_zero_list(self):
        row = copy.deepcopy(self.row)
        row['affixArray']['present'] = False
        with self.assertRaises(ValueError):
            probe.project_item(row)

    def test_null_array_is_not_zero_list(self):
        row = copy.deepcopy(self.row)
        row['affixArray']['logicalValues'] = None
        with self.assertRaises(ValueError):
            probe.project_item(row)

    def test_empty_array_is_not_observed_zero_slots(self):
        row = copy.deepcopy(self.row)
        row['affixArray']['logicalValues'] = []
        with self.assertRaises(ValueError):
            probe.project_item(row)

    def test_positive_group_requires_real_reference(self):
        row = copy.deepcopy(self.row)
        row['affixArray']['logicalValues'] = [0, 53101]
        with self.assertRaisesRegex(ValueError, 'Positive affix reference'):
            probe.project_item(row)

    def test_uint32_high_id_is_not_signed_alias(self):
        row = copy.deepcopy(self.row)
        row['affixArray']['logicalValues'] = [0xffffffff, 0]
        with self.assertRaisesRegex(ValueError, 'Java int'):
            probe.project_item(row)

    def test_bool_is_not_id(self):
        row = copy.deepcopy(self.row)
        row['affixArray']['logicalValues'] = [False, 0]
        with self.assertRaisesRegex(ValueError, 'Java int'):
            probe.project_item(row)

    def test_independent_item_not_exported(self):
        with self.assertRaisesRegex(ValueError, 'Only source-approved'):
            probe.project_item(self.other)

    def test_exact_source_spans_and_raw_fields(self):
        expected = {11101: (4, 400, 'c4ef8b265f69900cf3330ea83666d039d7f5d753266994e6056039ca36202e9a'),
                    11501: (20031, 20463, 'cce091376d371895ccc7be0b0f137a3eb8f9885e7ffe0d5768b3ad2258fae6a3')}
        for row in self.report['selectedRows']:
            span = row['source']
            self.assertEqual((span['begin'], span['end'], span['sha256']), expected[row['key']])
            self.assertEqual(len(row['affixArray']['encodedBits']), row['affixArray']['count'])
            self.assertTrue(row['fields'])
            self.assertFalse(row['fullSourceTokenPresenceRecovered'])

    def test_uint64_enum_float_bits_retained(self):
        back = next(x for x in self.other['fields'] if x['offset'] == '0x20')['value']
        raw = next(x['value'] for x in back if x['offset'] == '0x88')
        self.assertEqual(raw, {'bits': 'febb59b80e2e5dc0', 'decimal': '18355363353325362624'})
        self.assertGreater(int(raw['decimal']), 2**63)
        back = next(x for x in self.row['fields'] if x['offset'] == '0x20')['value']
        kind = next(x['value'] for x in back if x['offset'] == '0x38')
        self.assertEqual((kind['enumType'], kind['value'], kind['symbols']), (34819, 4, ['ITEM_WEAPON']))
        prop = next(x['value'] for x in back if x['offset'] == '0x68')[0]
        self.assertEqual(next(x['value'] for x in prop if x['offset'] == '0x14')['bits'], '41b9f5c3')

    def test_native_negatives_do_not_license_omission(self):
        negatives = self.report['controlledNegatives']
        self.assertEqual(len(negatives), 6)
        self.assertEqual(negatives[0]['trace']['stop'], '0x15075964d')
        self.assertEqual(negatives[1]['trace']['stop'], '0x1404c2650')
        self.assertEqual(negatives[2]['trace']['stop'], '0x15075965f')
        for case in negatives[3:]:
            self.assertTrue(case['returnedNull'])
            self.assertFalse(case['omissionPermission'])
            self.assertFalse(case['actualPositiveReferenceClosed'])

    def test_all_native_calls_accounted_and_bounded(self):
        totals = self.report['nativeCallTotals']
        self.assertEqual(sum(x['instructionHookVisits'] for x in totals.values()),
                         self.report['nativeCallInstructionHookVisits'])
        self.assertEqual(sum(x['instructions'] for x in self.report['nativeCalls']),
                         self.report['nativeCallInstructionHookVisits'])
        self.assertEqual(sum(x['calls'] for x in totals.values()), len(self.report['nativeCalls']))
        self.assertEqual(sum(self.report['experimentGroupInstructionHookVisits'].values()),
                         self.report['nativeCallInstructionHookVisits'])
        self.assertEqual(sum(x['completedNativeCalls'] for x in self.report['preliminaryExecutions']), 291)
        self.assertEqual(sum(x['completedInstructionHookVisits'] for x in self.report['preliminaryExecutions']), 28555)
        self.assertEqual(self.report['eachCallLimit'], 2_000_000)
        self.assertEqual(self.report['nativeExperimentGroups'], 3)
        self.assertEqual(self.report['newDirectFunctionWindows'], 8)
        calls = self.report['nativeCalls']
        self.assertTrue(all(x['instructions'] <= 2_000_000 for x in calls))

    def test_fragment_keeps_full_entry_unapproved(self):
        for key in ('fullTableParsingNewlyExecuted', 'fullAffixConsumerExecuted',
                    'actualPositiveAffixReferenceLoaded', 'completeItemInputRow',
                    'javaResourcesLoaded', 'resourceReady', 'activeRevisionVerified', 'd02Complete'):
            self.assertIs(self.report[key], False)
        fragment = self.report['javaProjectionFragments'][0]
        self.assertTrue(fragment['fragmentOnly'])
        self.assertTrue(fragment['omissionsAreNotTargetDefaults'])
        self.assertEqual(self.report['sourceSHA256'], probe.sha(Path(probe.__file__).read_bytes()))
        self.assertEqual(self.report['testSourceSHA256'], probe.sha(Path(__file__).read_bytes()))


if __name__ == '__main__':
    unittest.main()
