"""Source-bound Gadget key routing, real row controls and first-gap assertions."""
import hashlib
import json
import os
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
RAW = ROOT / 'local/migration72/d02-resume-20261109/r3-gadget-config-key'
REPORT = Path(os.environ.get('D02_GADGET_CONFIG_KEY_REPORT', str(RAW / 'first.json')))


class GadgetConfigKeyTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.q = json.loads(REPORT.read_bytes())
        cls.cases = {c['name']: c for c in cls.q['cases']}

    def test_three_exact_windows_and_leaf_extent(self):
        s = self.q['staticContract']
        self.assertEqual((s['newWindowCount'], s['newWindowBytes']), (3, 2330))
        self.assertEqual([(w['start'], w['end'], w['sha256']) for w in s['newWindows']], [
            ('0x158b48be0', '0x158b49025', 'cfe5db886ab9aeed283b61c4b91736a3177960faef816c492b60d47c157adfc9'),
            ('0x15787cdc0', '0x15787d205', '8c079fcdb87ef972aa7337c9740c160eea022393dbd9196692551120f43d4b33'),
            ('0x159a8da20', '0x159a8dab0', '3f0da425909eb1ae65e34d69ec879c612bfd6355a306b7b1394c066579566e97')])
        self.assertEqual(s['newWindows'][-1]['extentSource'], 'metadata-next-registered-method')
        self.assertEqual(s['stringHashMethod']['index'], 2922)
        self.assertEqual(s['stringHashMethod']['virtualSlot'], 2)

    def test_old_direct_callees_reused_without_new_claim(self):
        s = self.q['staticContract']
        self.assertEqual(s['reusedConfigSourceSHA256'], 'bebaeba56c57e8335ddfe14e778dec41df765d85393fd5ec46f9d29d57c750c8')
        self.assertEqual([(x['start'], x['bytes']) for x in s['reusedCalleesNotNew']],
                         [('0x14aa9b8a0', 379), ('0x1524e1f80', 207)])

    def test_exact_string_field_declarations(self):
        fields = self.q['staticContract']['fields']
        for label, index, offset in (('field50', 199648, 0x50), ('field58', 199649, 0x58)):
            self.assertEqual((fields[label]['index'], fields[label]['offset'], fields[label]['type']['kind']),
                             (index, offset, 'string'))
        self.assertFalse(self.q['field50AliasApproved'])

    def test_three_typed_configuration_cache_roles(self):
        f = self.q['staticContract']['fields']
        self.assertEqual(f['keyToAsset']['type']['instanceIndex'], 3744)
        self.assertEqual([a['kind'] for a in f['keyToAsset']['type']['arguments']], ['string', 'uint64'])
        self.assertEqual(f['assetToOwner']['type']['instanceIndex'], 11691)
        self.assertEqual(f['assetToOwner']['type']['arguments'][1]['typeDefinition'], 17591)
        self.assertEqual(f['ownerToConfig']['type']['instanceIndex'], 11687)
        self.assertEqual(f['ownerToConfig']['type']['arguments'][1]['typeDefinition'], 25270)
        s = self.q['staticContract']
        self.assertEqual({(r['address'], r['classInstance'], r['methodDefinition']) for r in s['dictionarySharedAot']},
            {('0x15787cdc0', 16, 10194), ('0x158b48be0', 3742, 10194), ('0x156f6f510', 59, 10194)})
        self.assertTrue(any(r['classInstance'] == 4 and r['arguments'][0]['kind'] == 'string'
                            for r in s['equalityComparerSharedAot']))

    def test_two_real_rows_and_full_old_readout(self):
        rows = self.q['rows']
        self.assertEqual([(r['key'], r['source']) for r in rows], [
            (50011101, dict(begin=432396, end=432488, sha256='eaf903171c14cfd27367b0bc7012ef7036671d4b7bb0d6c3723d237e6a17b9ad')),
            (90000004, dict(begin=1451356, end=1451410, sha256='3288b3a44679bcb4ff775fc52fcb5274e20818c9b58c3ce9bc5d33892198283a'))])
        for row in rows:
            self.assertEqual(len(row['values']), 29)
            self.assertTrue(row['sourceLogicalReadoutEqual'])
            self.assertFalse(row['constructorExecuted'])
            self.assertEqual(row['parser']['entry'], '0x14e62b290')
        first = {f['field']['offset']: f['value'] for f in rows[0]['values']}
        self.assertEqual(first[0x40], 8431959023108435415)

    def test_actual_factory_slice_and_key_pointer(self):
        for case in self.cases.values():
            trace = case['trace']
            self.assertEqual(trace['entry'], '0x15154bff9')
            self.assertEqual(trace['liveIn']['rbx'], case['rowPointer'])
            observed = next(e for e in trace['observedEntries'] if e['entry'] == '0x14aa9c630')
            self.assertEqual(observed['rcx'], case['suppliedField58Pointer'])
            self.assertTrue(case['independentFactorySlice'])
            self.assertFalse(case['fullFactoryExecuted'])
            self.assertFalse(trace['stackReturnWritten'])

    def test_original_real_keys_and_native_hash(self):
        expected = {'original-50011101': ('Weapon_Sword_Metal', 3705035964), 'original-90000004': ('MPLevel', 1870589383)}
        for name, (text, value) in expected.items():
            case = self.cases[name]
            self.assertEqual(case['selectedText'], text)
            self.assertEqual(case['suppliedField58Pointer'], case['originalField58Pointer'])
            self.assertEqual(case['nativeStringHash'], value)
            self.assertEqual(case['trace']['nativeStringHashes'][0]['value'], case['nativeStringHash'])
            self.assertIn('0x159a8da20', case['trace']['executedAddresses'])

    def test_real_pointer_exchanges_follow_content(self):
        a, b = self.cases['original-50011101'], self.cases['original-90000004']
        x, y = self.cases['50011101-key-from-90000004'], self.cases['90000004-key-from-50011101']
        self.assertEqual(x['suppliedField58Pointer'], b['originalField58Pointer'])
        self.assertEqual(y['suppliedField58Pointer'], a['originalField58Pointer'])
        self.assertEqual((x['nativeStringHash'], y['nativeStringHash']), (b['nativeStringHash'], a['nativeStringHash']))
        self.assertEqual((x['sourceKey'], y['sourceKey']), (50011101, 90000004))

    def test_field50_control_does_not_approve_alias(self):
        case = self.cases['50011101-field50-used-as58']
        self.assertEqual(case['selectedText'], 'Lv1_Weapon')
        self.assertEqual(case['nativeStringHash'], 867824404)
        self.assertNotEqual(case['suppliedField58Pointer'], case['originalField58Pointer'])
        self.assertTrue(case['stoppedBeforeUnprovenComparer'])
        self.assertFalse(self.q['field50AliasApproved'])

    def test_controlled_empty_cache_original_null_return(self):
        case = self.cases['original-50011101-empty-cache']
        self.assertTrue(case['controlledCacheEmpty'])
        self.assertTrue(case['nativeReturnedNull'])
        self.assertEqual(case['trace']['stop'], '0x15154c002')
        self.assertEqual(case['trace']['nativeStringHashes'], [])
        for entry in ('0x14aa9c630', '0x14aa9b8a0', '0x14aa97730', '0x14aa9af10', '0x158b48be0'):
            self.assertIn(entry, case['trace']['executedAddresses'])

    def test_null_field58_original_boundary(self):
        case = self.cases['null-field58']
        self.assertIsNone(case['selectedText'])
        self.assertEqual(case['suppliedField58Pointer'], '0x0')
        self.assertTrue(case['nativeReturnedNull'])
        self.assertEqual(case['trace']['stop'], '0x15154c002')
        self.assertNotIn('0x158b48be0', case['trace']['executedAddresses'])

    def test_first_comparer_gap_is_not_executed_or_filled(self):
        for case in list(self.cases.values())[:5]:
            self.assertEqual(case['trace']['stop'], '0x158b48cf6')
            self.assertTrue(case['stoppedBeforeUnprovenComparer'])
        gap = self.q['firstUnresolvedInput']
        self.assertEqual(gap['stoppedBefore'], '0x158b48cf6')
        self.assertFalse(gap['comparerFactoryCodeWindowOpened'])
        self.assertFalse(gap['defaultComparerPointeeInvented'])
        self.assertNotIn('0x15746c870', self.q['nativeSources'])

    def test_no_config_value_or_successful_storage_prefill(self):
        fixture = self.q['ownedCacheFixture']
        self.assertEqual(fixture['successfulEntriesPrefilled'], 0)
        self.assertTrue(fixture['comparerExplicitlyNull'])
        self.assertTrue(fixture['comparerCarrierNotSupplied'])
        for key in ('originalDictionaryInsertExecuted', 'configured17591OwnerCreated', 'opaque25270ValueCreated', 'naturalInitializationObserved'):
            self.assertFalse(fixture[key])
        self.assertFalse(self.q['opaque25270ValueInstalled'])

    def test_unique_native_accounting_and_limits(self):
        q = self.q
        self.assertEqual(len(q['cases']), 7)
        self.assertEqual(len(q['nativeCalls']), sum(x['calls'] for x in q['nativeCallTotals'].values()))
        self.assertEqual(q['nativeInstructionHookVisits'], sum(x['instructions'] for x in q['nativeCalls']))
        self.assertEqual(q['nativeInstructionHookVisits'], sum(x['instructionHookVisits'] for x in q['nativeCallTotals'].values()))
        self.assertLess(q['nativeInstructionHookVisits'], q['hookLimit'])
        for trace in q['nativeCalls']:
            self.assertIsNone(trace['failure'])
            self.assertLessEqual(trace['instructions'], trace['countLimit'])
            self.assertLessEqual(trace['countLimit'], q['eachCallLimit'])
            self.assertEqual(trace['instructions'], sum(trace['executedAddresses'].values()))
            self.assertTrue(trace['unexecutedStopHookIncluded'])

    def test_queries_have_no_success_models(self):
        for case in self.cases.values():
            self.assertEqual(case['trace']['models'], [])
            self.assertFalse(case['configurationHitVerified'])
        self.assertTrue(self.q['inheritedNativeConfigModeFixture']['bucketsExplicitlyNull'])
        self.assertFalse(self.q['inheritedNativeConfigModeFixture']['actualNativeInitializationObserved'])

    def test_fixed_local_and_upstream_candidate_scope(self):
        c = self.q['resourceCandidates']
        self.assertEqual(c['upstreamCommit'], '6cfecfb55f889f8e3b7156adb12809608db4f74b')
        self.assertEqual(c['externalFilesDownloaded'], 0)
        self.assertEqual([r['selectedKey'] for r in c['localCandidates']], ['Weapon_Sword_Metal', 'Lv1_Weapon'])
        for row in c['localCandidates']:
            self.assertTrue(row['keyAlreadyLocal'])
            self.assertFalse(row['fullBlobSameAsUpstream'])
            self.assertFalse(row['targetCompatibilityApproved'])
        self.assertEqual(c['abilityCandidate']['name'], 'Avatar_Weapon_Metal')
        self.assertFalse(c['abilityCandidate']['nativeTargetSourceVerified'])
        self.assertFalse(c['license']['exactLicenseTextCached'])

    def test_source_and_test_hashes_match_frozen_report(self):
        self.assertEqual(hashlib.sha256((ROOT / 'tools/probe72_gadget_config_key.py').read_bytes()).hexdigest(), self.q['sourceSHA256'])
        self.assertEqual(hashlib.sha256(Path(__file__).read_bytes()).hexdigest(), self.q['testSourceSHA256'])
        for key, digest in self.q['resourceCandidates']['sourcePins'].items():
            self.assertEqual(hashlib.sha256((ROOT / key).read_bytes()).hexdigest(), digest)
        self.assertTrue(self.q['fiveInputsRecheckedAfterNative'])

    def test_permissions_remain_exactly_bounded(self):
        self.assertTrue(self.q['sourceConfigurationKeyRoutingVerified'])
        self.assertEqual(self.q['javaProjectionFragments'], [])
        for key in ('nativeConfigurationHitVerified', 'javaJsonNameAliasApproved', 'configurationSourceBytesVerified',
                    'configurationOwnerLookupExecuted', 'finalConfigurationLookupExecuted', 'defaultComparerExecuted',
                    'abilityInputsApproved', 'fullFactoryExecuted', 'completeGadgetInputRow', 'javaResourcesLoaded',
                    'activeRevisionVerified', 'resourceReady', 'd02Complete'):
            self.assertFalse(self.q[key], key)


if __name__ == '__main__':
    unittest.main()
