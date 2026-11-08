"""Independent assertions for the actual SDK resource and original fragments."""
import copy
import json
import os
from pathlib import Path
import struct
import unittest

from tools.client72_sdk_route_loader import RAW, read_string, serialized_config, classify_configs


class RouteLoaderReportTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.path = Path(os.environ.get('SDK72_ROUTE_LOADER_REPORT', RAW / 'final-v3-first.json'))
        cls.encoded = cls.path.read_bytes()
        cls.report = json.loads(cls.encoded)
        cls.artifacts = cls.path.with_suffix('')
        cls.configs = json.loads((cls.artifacts / 'server_pc.private.json').read_bytes())

    def test_fixed_client_and_installed_carrier_identity(self):
        fixed = self.report['fixedInputs']
        self.assertEqual(fixed['clientFiles']['YuanShen_Data/Plugins/AccountPlatNative.dll'],
                         'adecdf0bd5442e321b1b66b0ca9e98bef76d5067326aa4add5b75266055a6c25')
        self.assertEqual(fixed['bundleSHA256'], '8e33ae960729065d273928d029b03b72e72cdfbee823fc1f94e83d8ab8c59cda')
        self.assertTrue(fixed['packageEntrySizeAndMd5Match'])

    def test_typed_delegate_owner_is_independent_of_similar_name(self):
        item = self.report['declarations']['resourceDelegate']
        self.assertEqual((item['ownerType'], item['owner'], item['fieldIndex'], item['fieldName']),
                         (4013, 'SDKDelegate', 18453, 'LoadFile'))
        self.assertEqual(item['metadataEncodedOffset'], '0x4001530')
        self.assertEqual(item['staticOffset'], '0x1530')
        self.assertEqual(self.report['declarations']['delegateMethodUsage'],
                         [{'usageRow': 734367, 'tag': 3, 'methodDefinition': 71136, 'cell': '0x14597a3e8'}])

    def test_exact_resource_literal_and_asset_container_join(self):
        literals = {x['index']: x['value'] for x in self.report['declarations']['literals']}
        self.assertEqual(literals[14111], 'MiHoYoSDKRes/PC/mihoyo_sdk_res')
        self.assertEqual(literals[15092], 'Config/server_pc.json')
        asset = self.report['asset']
        self.assertEqual((asset['typeCount'], asset['objectCount']), (79, 13025))
        self.assertEqual(asset['container']['key'], 'assets/plugins/mihoyosdk/resources/config/server_pc.json')
        self.assertEqual(asset['container']['fileID'], 0)
        self.assertEqual(asset['textAsset']['pathID'], 10246702305728925693)
        self.assertGreater(asset['textAsset']['pathID'], 2**63)
        self.assertEqual(asset['container']['pathID'], asset['textAsset']['pathID'])
        self.assertEqual((asset['textAsset']['classID'], asset['textAsset']['textOffset'], asset['textAsset']['textBytes']),
                         (49, 5995140, 475950))

    def test_full_payload_native_prefix_and_independent_codec_coverage(self):
        payload = self.report['payload']
        self.assertEqual(payload['outputBytes'], 57849700)
        self.assertEqual(len(payload['blocks']), 442)
        self.assertEqual([x['index'] for x in payload['blocks']], list(range(442)))
        self.assertTrue(payload['allBlocksNativePrefixExecuted'])
        self.assertFalse(payload['allBlocksNativeCodecExecuted'])
        self.assertEqual([x['index'] for x in payload['nativeSamples']], [0, 441])
        self.assertTrue(all(x['referenceEqual'] for x in payload['nativeSamples']))
        self.assertTrue(all(not x['nativePrefixRun']['modeledCalls'] for x in payload['blocks']))

    def test_configuration_array_order_and_fixed_public_cn_route(self):
        routes = self.report['fixedComboRoutes']
        self.assertEqual([x['env'] for x in routes], [0, 1, 4, 6, 8, 9, 22, 19, 2, 3, 5, 7, 11, 12, 21, 20])
        self.assertEqual((routes[0]['host'], routes[0]['path']),
                         ('hk4e-sdk.mihoyo.com', '/hk4e_cn/combo/granter/login/v2/login'))
        self.assertEqual(routes[1]['host'], 'sandbox-sdk.mihoyo.com')
        self.assertTrue(all(x['urlEntryCount'] == 305 for x in routes))
        self.assertTrue(all(not x['naturalSelectionObserved'] for x in routes))

    def test_original_writer_and_reader_positive_samples(self):
        samples = self.report['nativeFragments'][:2]
        self.assertEqual([x['env'] for x in samples], [0, 1])
        for sample in samples:
            self.assertEqual(sample['mapSize'], 1)
            self.assertEqual(sample['producerEntries']['0x380e60'], 1)
            self.assertTrue(sample['nativeGetterValueEqual'])
            self.assertTrue(sample['nativeGetterReturnedPointer64'])
            self.assertTrue(sample['appKeyFieldEqual'])
            self.assertTrue(sample['nativeJsonCleanupReturned'])
            self.assertGreater(sample['urlFragmentInstructions'], 10000)
            self.assertFalse(sample['wholeInitReturned'])

    def test_object_cannot_replace_native_init_url_string(self):
        sample = self.report['nativeFragments'][2]
        self.assertEqual(sample['urlShape'], 'object')
        self.assertEqual(sample['mapSize'], 0)
        self.assertEqual(sample['producerEntries']['0x380e60'], 0)
        self.assertTrue(sample['nativeGetterValueEqual'])

    def test_direct_native_producer_has_no_disk_or_resource_callback(self):
        for sample in self.report['nativeFragments']:
            self.assertEqual(sample['producerEntries']['0x364910'], 0)
            self.assertEqual(sample['producerEntries']['0x140940'], 0)
            self.assertEqual(set(sample['modelCalls']), {'storage-allocate', 'storage-free', 'single-thread-mutex-condition'})
        self.assertEqual(len(self.report['static']['sdkGlobalReferences']), 8)
        self.assertFalse(self.report['static']['directRegistrationFound'])
        self.assertEqual(len(self.report['static']['reaExactGlobalXrefs']), 4)
        builder = next(x for x in self.report['static']['sdkWindows'] if x['startRva'] == '0x3be5c0')
        instructions = {x[0]: x[1:] for x in builder['instructions']}
        self.assertEqual(instructions['0x3beac6'], ['xor', 'r9d, r9d'])
        self.assertEqual(instructions['0x3bead4'], ['call', 'qword ptr [rax + 0x80]'])
        self.assertIn('0x3beada', instructions)

    def test_signing_source_does_not_claim_algorithm_or_natural_result(self):
        source = self.report['signingSource']
        self.assertEqual((source['configModelBaseOffset'], source['appKeyModelOffset'], source['appKeyConfigOffset']),
                         ('0xe8', '0x130', '0x218'))
        self.assertEqual((source['cryptoVtableRva'], source['slotByteOffset'], source['signerRva']),
                         ('0x6d1cb8', '0x58', '0x3958e0'))
        self.assertFalse(source['signatureAlgorithmExecuted'])
        for flag in ('wholeSdkInitExecuted', 'naturalRouteSelected', 'clientExecuted', 'productionConfigurationChanged', 'd02Passed'):
            self.assertFalse(self.report[flag])

    def test_signing_values_and_raw_configuration_do_not_escape_report(self):
        for row in self.configs:
            secret = row['app_key'].encode()
            if secret in self.encoded:
                self.fail('A private configuration signing value escaped into the report')
        self.assertFalse(self.report['asset']['configurationBytesOrHashRecordedInReport'])
        self.assertFalse(self.report['payload']['decodedConfigurationOrPayloadHashRecorded'])
        self.assertFalse(self.report['signingSource']['appKeyBytesOrHashRecorded'])

    def test_unrecognized_route_is_rejected_without_printing_input(self):
        values = copy.deepcopy(self.configs)
        values[0]['url']['comboLogin'] = 'https://fixture.invalid/other'
        with self.assertRaisesRegex(ValueError, 'Fixed public combo route classification differs'):
            classify_configs(values)


class SerializedBoundaryTests(unittest.TestCase):
    def test_aligned_nonzero_string_and_truncation(self):
        self.assertEqual(read_string(struct.pack('<I', 3) + b'abc\0', 0, 8), (b'abc', 8))
        self.assertEqual(read_string(bytes(4), 0, 4), (b'', 4))
        with self.assertRaises(ValueError):
            read_string(struct.pack('<I', 0xffffffff), 0, 4)
        with self.assertRaises(ValueError):
            read_string(bytes(3), 0, 3)

    def test_serialized_extent_version_and_wrong_endianness_reject(self):
        for data, extent in ((b'', 0), (bytes(20), 21),
                             (struct.pack('>4I', 0, 20, 16, 20) + bytes(4), 20),
                             (struct.pack('>4I', 0, 20, 17, 20) + b'\1\0\0\0', 20)):
            with self.assertRaises(ValueError):
                serialized_config(data, extent)


if __name__ == '__main__':
    unittest.main()
