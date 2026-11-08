"""Independent assertions on the same-core/original-SDK bridge evidence."""
import json
import os
from pathlib import Path
import unittest

from tools.client72_sdk_key_native_bridge import ROOT, RAW, CORE_SHA, SDK_SHA, digest


class SDKKeyNativeBridgeTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        path = Path(os.environ.get('SDK72_KEY_NATIVE_REPORT',str(RAW/'final-v2-first.json')))
        cls.raw = path.read_bytes()
        cls.report = json.loads(cls.raw)
        cls.rows = {row['name']:row for row in cls.report['nativeCases']}

    def test_fixed_five_inputs_and_actual_helper_core(self):
        r = self.report
        self.assertEqual(r['sourceSHA256'],digest((ROOT/'tools/client72_sdk_key_native_bridge.py').read_bytes()))
        self.assertEqual(r['helperSourceSHA256'],digest((ROOT/'patch/examples/sdk72_key_native_bridge.rs').read_bytes()))
        for path,expected in r['fixedSources'].items():
            self.assertEqual(digest((ROOT/path).read_bytes()),expected,path)
        self.assertEqual(len(r['fiveDiskInputs']),5)
        self.assertEqual(r['fiveDiskInputs']['YuanShen_Data/Plugins/AccountPlatNative.dll'],SDK_SHA)
        self.assertEqual(r['helperHandshake']['coreSHA256'],CORE_SHA)
        self.assertTrue(r['helperHandshake']['corePrepared'])
        self.assertFalse(r['helperHandshake']['clientLoaded'])
        self.assertFalse(r['helperHandshake']['hookInstalled'])
        self.assertEqual(r['helperCoreInvocations'],19)

    def test_both_exact_native_call_instructions_and_returns(self):
        self.assertEqual(self.report['helperHandshake']['callers'],[0x34eeb7,0x34ef88])
        windows = self.report['codeWindows']
        self.assertEqual(set(windows),{'0x34ee6b','0x34ef3c'})
        for key,call,ret in [('0x34ee6b','0x34eeb4','0x34eeb7'),('0x34ef3c','0x34ef85','0x34ef88')]:
            self.assertEqual(windows[key]['callRva'],call)
            self.assertEqual(windows[key]['returnRva'],ret)
            self.assertEqual(windows[key]['instruction'],['call','qword ptr [rax + 0x18]'])
        self.assertEqual({r['callerRva'] for r in self.rows.values()},{None,'0x34eeb7','0x34ef88'})

    def test_actual_rust_selection_has_explicit_host_to_emulator_translation(self):
        for row in self.rows.values():
            t = row['translation']
            self.assertEqual(t['selection'],'core-local' if row['supplied'] else 'original')
            self.assertEqual(t['coreInvocations'],1)
            for key in ['hostStackTranslated','hostThisTranslated','hostKeyTranslated',
                        'hostPointerNeverWrittenToEmulator','hostPublicSourceUnchanged',
                        'hostThisUnchanged','opaqueInputPointerUnchanged']:
                self.assertTrue(t[key],(row['name'],key))
            self.assertEqual(t['copiedCorePublicIntoEmulator'],row['supplied'])
            self.assertFalse(t['inputBytesRead'])

    def test_full_entry_registers_flags_and_owned_stack_preserved(self):
        for row in self.rows.values():
            self.assertTrue(row['nativeEntryEqualsBaseline'])
            t = row['translation']
            for key in ['registerFieldsUnchanged','hostStackOnlyFifthChanged',
                        'emulatorGpr16Preserved','emulatorXmm16Preserved','emulatorFlagsPreserved',
                        'emulatorStack128OnlyFifthChanged','emulatorEntryLastErrorPreserved',
                        'emulatorThisLengthInputAndPublicUnchanged']:
                self.assertTrue(t[key],(row['name'],key))

    def test_original_native_call_return_ownership_and_nonvolatile_abi(self):
        for row in self.rows.values():
            self.assertEqual(row['originalCalls'],1)
            for key in ['originalOutputPointerReturned64','nativeNonvolatileGprPreserved',
                        'nativeXmm6to15Preserved','nativeReturnLastErrorEqualsBaseline',
                        'outputOwnedCopyReleased','executedOnlySDKCode']:
                self.assertTrue(row[key],(row['name'],key))
            if row['callerRva'] is not None:
                self.assertTrue(row['getterOwnedCopyReleased'])
            self.assertGreater(row['nativeSteps'],100)
            self.assertGreater(row['cleanupNativeSteps'],0)

    def test_same_core_outputs_are_mathematically_decryptable_by_selected_server(self):
        self.assertTrue(self.report['publicNumbersEqualSelectedServer'])
        expected = {'account-one','password-nonzero','account-boundary117','account-high64',
                    'password-high64-boundary117','account-empty','password-embeddedNul','account-utf8'}
        self.assertEqual({name for name,row in self.rows.items() if row['supplied'] and row['nativeSuccess']},expected)
        for name in expected:
            row = self.rows[name]
            self.assertTrue(row['localPrivateDecryptEqual'])
            self.assertFalse(row['baseline']['localPrivateDecryptEqual'])

    def test_every_native_success_passes_independent_padding_rsa_base64_oracles(self):
        for row in self.rows.values():
            self.assertEqual(row['encodedBytes'],172 if row['nativeSuccess'] else 0)
            if row['nativeSuccess']:
                for key in ['pkcs1Type2Valid','integerRSAEqual','nativeRSAEqualsBase64Decoded']:
                    self.assertTrue(row[key],(row['name'],key))
                self.assertEqual(row['cipherBytes'],128)
                self.assertGreaterEqual(row['paddingNonzeroBytes'],8)
                stages = row['nativeCryptoStagesExecuted']
                for rva in ['0x3950c0','0x395d00','0x4ba050','0x4ed800','0x4c9dd0','0x3b6a00']:
                    self.assertTrue(stages[rva],(row['name'],rva))

    def test_native_118_boundary_is_rejected_after_actual_core_supply(self):
        row = self.rows['password-oversize118']
        self.assertTrue(row['supplied'])
        self.assertFalse(row['nativeSuccess'])
        native = {r['rva']:r for r in row['nativeCalls']}
        self.assertEqual(native['0x4ed800']['returnSigned32'],0)
        self.assertEqual(native['0x4c9dd0']['returnSigned32'],-1)
        self.assertFalse(row['nativeCryptoStagesExecuted']['0x3b6a00'])
        self.assertEqual(row['baseline']['encodedBytes'],0)

    def test_bad_public_material_keeps_real_native_parser_rejection(self):
        for name in ['malformed-key','empty-key','null-key','truncated-key','stock-followed-by-extra-byte']:
            row = self.rows[name]
            self.assertFalse(row['supplied'])
            self.assertEqual([(r['rva'],r['returnZero']) for r in row['nativeCalls']],
                             [('0x4ba050',True),('0x4ba010',True)])
            self.assertFalse(row['nativeSuccess'])

    def test_unknown_identity_passes_through_complete_original_native_output(self):
        for name in ['unknown-caller','unknown-binding','unknown-vtable']:
            row = self.rows[name]
            self.assertFalse(row['supplied'])
            self.assertTrue(row['nativeSuccess'])
            self.assertTrue(row['passThroughNativeOutputEqualsBaseline'])
            self.assertFalse(row['localPrivateDecryptEqual'])
        row = self.rows['already-local-key']
        self.assertFalse(row['supplied'])
        self.assertTrue(row['localPrivateDecryptEqual'])

    def test_high64_lengths_and_raw_byte_content_remain_distinct(self):
        self.assertEqual(self.rows['account-high64']['lengthStorage64'],0x100000001)
        self.assertEqual(self.rows['password-high64-boundary117']['lengthStorage64'],0xfedcba9800000075)
        self.assertEqual(self.rows['account-boundary117']['inputBytes'],117)
        self.assertEqual(self.rows['password-embeddedNul']['inputBytes'],3)
        self.assertEqual(self.rows['account-utf8']['inputBytes'],7)

    def test_null_this_rejection_does_not_claim_native_execution(self):
        row = self.report['coreOnlyNullThisRejection']
        self.assertEqual(row['selection'],'original')
        self.assertEqual(row['coreInvocations'],1)
        self.assertNotIn('nativeSuccess',row)
        self.assertNotIn('null-this',self.rows)

    def test_models_never_replace_parser_padding_rsa_or_base64(self):
        forbidden = {'0x3950c0','0x395d00','0x3951c0','0x4ba050','0x4ba010','0x4c9dd0','0x4ed800','0x3b6a00'}
        for row in self.rows.values():
            self.assertGreater(row['processGlobalAllocationsRetained'],0)
            self.assertTrue(all(r['boundary'] not in forbidden for r in row['modelCalls']))
        self.assertEqual(len(self.report['models']),6)
        self.assertFalse(self.report['clientLoaded'])
        self.assertFalse(self.report['hookInstalled'])
        self.assertFalse(self.report['productionRegistered'])
        self.assertEqual(len(self.report['notCovered']),7)

    def test_no_pipe_pem_plain_cipher_or_payload_hash_is_serialized(self):
        self.assertNotIn(b'-----BEGIN',self.raw)
        self.assertNotIn(b'selectedPublic',self.raw)
        self.assertNotIn(b'keySnapshot',self.raw)
        self.assertNotIn(chr(0x1f600),self.raw.decode('utf-8'))
        for row in self.rows.values():
            self.assertTrue(set(row).isdisjoint({'plain','plaintext','ciphertext','body','token','account','password'}))
            self.assertFalse(any('hash' in key.lower() or 'sha256' in key.lower() for key in row))


if __name__=='__main__':
    unittest.main()
