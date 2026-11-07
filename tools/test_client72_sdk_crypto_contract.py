"""Independent result assertions for original SDK RSA/config execution."""
import json
import os
from pathlib import Path
import unittest

from tools.client72_sdk_crypto_contract import ROOT, RAW, INPUT_MANIFEST_SHA, digest, public_numbers


class SDKCryptoContractTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        path=Path(os.environ.get('SDK72_CRYPTO_REPORT',str(RAW/'final-first.json')))
        cls.raw=path.read_bytes()
        cls.report=json.loads(cls.raw)
        cls.samples={r['name']:r for r in cls.report['nativeEncrypt']}

    def test_fixed_source_inputs_and_parent_epochs(self):
        r=self.report
        self.assertEqual(r['sourceSHA256'],digest((ROOT/'tools/client72_sdk_crypto_contract.py').read_bytes()))
        self.assertEqual(r['inputManifestSHA256'],INPUT_MANIFEST_SHA)
        self.assertEqual(len(r['moduleSHA256']),13)
        for path,row in r['fixedInputs'].items():
            self.assertEqual(digest((ROOT/path).read_bytes()),row['sha256'],path)

    def test_rtti_exact_crypto_interface(self):
        rows=[r for r in self.report['staticRTTI'] if r['subobjectOffset']==0]
        self.assertEqual(len(rows),1)
        self.assertEqual(rows[0]['vtableRva'],0x6d1cb8)

    def test_native_key_modes_and_pointer_width(self):
        rows=self.report['nativeKeys']
        self.assertEqual([(r['mode64'],r['modulusBits'],r['pemBytes']) for r in rows],
                         [(0,1024,268),(1,2048,444),(2,1024,268),(0x100000001,2048,444)])
        self.assertTrue(all(r['returnedOutputPointer64'] and r['publicExponent']==65537 for r in rows))
        self.assertTrue(all(r['ownedGetterCopyReleased'] for r in rows))

    def test_native_keys_do_not_match_existing_server_or_managed_keys(self):
        for row in self.report['nativeKeys']:
            self.assertEqual(set(row['localPublicEqual']),
                             {'private_key.der','auth_private-key.der','SigningKey.der','passport_1024.der'})
            self.assertFalse(any(row['localPublicEqual'].values()))
            self.assertFalse(row['patchXMLPublicEqual'])

    def test_native_rsa_padding_and_base64_independent_oracles(self):
        for name in ['one','boundary117','empty','embeddedNul','utf8','lengthHigh32','secondary245','localKeyOne','localKey117']:
            row=self.samples[name]
            self.assertTrue(row['pkcs1Type2Valid'] and row['integerRSAEqual'] and row['nativeRSAEqualsBase64Decoded'])
            self.assertGreaterEqual(row['paddingNonzeroBytes'],8)
            self.assertIn(row['cipherBytes'],[128,256])
            self.assertEqual(row['encodedBytes'],4*((row['cipherBytes']+2)//3))

    def test_exact_native_padding_errors_reach_original_return(self):
        for name in ['oversize118','secondaryOversize246']:
            row=self.samples[name]
            calls={r['rva']:r for r in row['nativeCalls']}
            self.assertEqual(row['encodedBytes'],0)
            self.assertEqual(calls['0x4ed800']['returnSigned32'],0)
            self.assertEqual(calls['0x4c9dd0']['returnSigned32'],-1)
            self.assertTrue(row['returnedOutputPointer64'])

    def test_bad_truncated_null_keys_execute_both_native_parsers(self):
        for name in ['malformedKey','truncatedKey','nullKey']:
            row=self.samples[name]
            self.assertEqual([(r['rva'],r['returnZero']) for r in row['nativeCalls']],
                             [('0x4ba050',True),('0x4ba010',True)])
            self.assertEqual(row['encodedBytes'],0)

    def test_lengths_are_bytes_and_low32_at_rsa_boundary(self):
        self.assertEqual(self.samples['utf8']['inputBytes'],7)
        self.assertEqual(self.samples['embeddedNul']['inputBytes'],3)
        self.assertEqual(self.samples['lengthHigh32']['lengthStorage64'],0x100000001)
        self.assertEqual(self.samples['lengthHigh32']['encodedBytes'],172)
        self.assertTrue(self.samples['lengthHigh32']['integerRSAEqual'])

    def test_existing_local_key_can_interoperate_without_crypto_override(self):
        for name in ['localKeyOne','localKey117']:
            self.assertTrue(self.samples[name]['localPrivateDecryptEqual'])
            self.assertTrue(self.samples[name]['integerRSAEqual'])

    def test_output_ownership_and_model_boundaries(self):
        for row in self.samples.values():
            self.assertTrue(row['outputOwnedCopyReleased'])
            self.assertTrue(row['executedOnlySDKCode'])
            self.assertGreater(row['nativeSteps'],100)
            for event in row['modelCalls']:
                self.assertNotIn(event['boundary'],['0x3950c0','0x395d00','0x4ba050','0x4ba010','0x4c9dd0','0x4ed800','0x3b6a00'])
        self.assertGreater(self.report['retainedState']['processGlobalAllocationsRetained'],0)

    def test_original_route_initializer_and_actual_reader_selection(self):
        row=self.report['nativeRoutes']
        self.assertEqual(row['initializerRva'],0x107fe0)
        self.assertEqual(row['globalRva'],0x7dbf70)
        self.assertEqual([(r['tableCode'],r['host']) for r in row['tables']],
                         [(0,'passport-api.mihoyo.com'),(1,'pre-passport-api.mihoyo.com'),
                          (2,'ue-account.mihoyo.com'),(3,'devapi-takumi.mihoyo.com'),
                          (4,'sandbox-sdk.mihoyo.com'),(5,'ptsapi-takumi.mihoyo.com')])
        self.assertTrue(all(r['fixedRouteEqual'] and r['returnedOutputPointer64'] for r in row['selectedReaders']))
        self.assertEqual({r['path'] for r in row['tables']},{'/account/ma-cn-passport/app/loginByPassword'})

    def test_native_config_defaults_override_and_error(self):
        row=self.report['nativeRoutes']
        self.assertTrue(row['invalidCodeKeepsPrevious'])
        self.assertTrue(row['explicitOverrideZero'])
        self.assertTrue(row['unknownKeyUsesLoopbackFallback'])
        self.assertTrue(row['emptyConfigDefaultsCodeZero'])
        self.assertEqual(row['naturalSelection'],'unobserved')

    def test_managed_hook_is_separate_from_native_crypto_and_natural_boundary(self):
        r=self.report
        self.assertEqual(r['managedHook']['entryRva'],0x1997c290)
        self.assertTrue(r['managedHook']['patternUnique'])
        self.assertEqual(r['naturalInitializationOnly']['moduleBinding'],'file-entry-matches')
        self.assertEqual(r['naturalInitializationOnly']['sdkResult'],'roots-sampled-not-a-request')
        self.assertFalse(r['naturalInitializationOnly']['naturalCryptoCallObserved'])

    def test_payloads_keys_and_payload_hashes_are_not_serialized(self):
        self.assertNotIn(b'-----BEGIN',self.raw)
        for row in self.samples.values():
            self.assertTrue(set(row).isdisjoint({'plaintext','ciphertext','plainSHA256','cipherSHA256','body','token','account'}))
            self.assertFalse(any('hash' in key.lower() or 'sha256' in key.lower() for key in row))
        self.assertNotIn(chr(0x1f600),self.raw.decode('utf-8'))

    def test_independent_public_parser_rejects_bad_format(self):
        for data in [b'',b'not-a-key',b'-----BEGIN RSA PUBLIC KEY-----\nAA==\n-----END RSA PUBLIC KEY-----']:
            with self.assertRaises((ValueError,IndexError)):
                public_numbers(data)


if __name__=='__main__':
    unittest.main()
