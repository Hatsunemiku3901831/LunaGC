"""Read-only source/negative checks; never start the emulator or target."""
import copy
import unittest

from tools import client72_d02_a_string_frontier as card


class StringFrontierTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.sources = card.load_sources()
        cls.report = card.audit(cls.sources)

    def test_original_full_byte_hash_and_gap_reject(self):
        window = copy.deepcopy(self.sources[card.OLD + 'string-hash-window.private.json']['windows'][0])
        window['instructions'][0]['bytes'] = '440fb74910'
        with self.assertRaisesRegex(ValueError, 'hash differ'):
            card.verify_window(window)
        window = copy.deepcopy(self.sources[card.OLD + 'lookup-windows.private.json']['windows'][0])
        window['instructions'][1]['address'] = '0x158b48be3'
        with self.assertRaisesRegex(ValueError, 'Non-contiguous'):
            card.verify_window(window)

    def test_three_old_actual_hash_values_independent_of_old_oracle(self):
        expected = {'Weapon_Sword_Metal': 3705035964, 'MPLevel': 1870589383, 'Lv1_Weapon': 867824404}
        self.assertEqual({row['selectedText']: row['nativeStringHash']
                          for row in self.report['oldOwnedNativeHashEvidence']}, expected)

    def test_embedded_nul_is_hash_stop_not_equals_length(self):
        self.assertEqual(card.original_hash_prediction('A\0B'), card.original_hash_prediction('A'))
        self.assertEqual(card.original_hash_prediction('A\0C'), card.original_hash_prediction('A'))
        self.assertNotEqual(card.original_hash_prediction('AB'), card.original_hash_prediction('A'))
        self.assertFalse(self.report['hashSource']['lengthFieldRead'])
        self.assertFalse(self.report['hashSource']['naturalTerminatorVerified'])

    def test_actual_rip_roots_and_fresh_epoch(self):
        self.assertEqual(self.report['rootSources']['globalStoragePointerRVA'], '0x586b600')
        self.assertEqual(self.report['rootSources']['tryGetValueMIUsageRVA'], '0x59ae108')
        self.assertTrue(self.report['oldEpochNotImportedIntoFresh'])
        self.assertEqual(self.report['freshInputSHA256'], card.RAW_SHA)

    def test_graph_budget_and_no_hidden_success(self):
        graph = self.report['naturalFrontier']['firstReadGraph']
        self.assertEqual(len(graph), 13)
        self.assertEqual(sum(row[1] for row in graph), 408)
        self.assertEqual(max(row[1] for row in graph), 208)
        frontier = self.report['naturalFrontier']
        self.assertEqual(frontier['proposedIncludingUnifiedExactFinalBytes'], 2 * sum(row[1] for row in graph))
        for key in ('newBudgetApproved', 'firstMissingTupleObserved', 'naturalTriggerKnown',
                    'currentLoginPageRepeatJustified', 'atomicSnapshotClaimed'):
            self.assertFalse(frontier[key], key)
        for key in ('hostConsumerSuccess', 'naturalHashVerified', 'fullFindEntryVerified',
                    'configurationHitVerified', 'completeAvatar', 'g2Verified', 'd02Complete'):
            self.assertFalse(self.report[key], key)
        for key in ('newNativeCalls', 'newLiveReads', 'newPEExtraction', 'newStockOrGamePathReads'):
            self.assertEqual(self.report[key], 0, key)

    def test_fresh_tls_not_old_ordinary_method_info(self):
        self.assertEqual(self.report['TLSBoundary']['freshCompletedInstructions'], 105)
        self.assertEqual(self.report['TLSBoundary']['missing'], 'B+0x5eef080/4B')
        self.assertTrue(self.report['TLSBoundary']['oldColdOrdinary10294IsSeparate'])
        self.assertFalse(self.report['TLSBoundary']['TLSOrGCModeled'])
        self.assertEqual(self.report['TLSBoundary']['bothStateBranchesCall'], '0x140563cd0 at 0x1405337d8')
        self.assertFalse(self.report['TLSBoundary']['calleeOriginalWindowAvailableInThisCard'])
        self.assertEqual(self.report['TLSBoundary']['savedAllocatorBytesVerified'], 420)


if __name__ == '__main__':
    unittest.main()
