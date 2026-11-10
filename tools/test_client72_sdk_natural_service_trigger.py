"""Independent bounds and original-byte checks for the service trigger study."""
import hashlib
import json
import os
from pathlib import Path
import struct
import unittest

from tools import client72_sdk_natural_service_trigger as target


class NaturalServiceTriggerTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.windows = Path(os.environ.get('SDK72_SERVICE_TRIGGER_WINDOWS', str(
            target.PRIVATE / 'windows-first')))
        cls.extraction = json.loads((cls.windows / 'extraction.private.json').read_bytes())
        cls.contract = json.loads(Path(os.environ.get('SDK72_SERVICE_TRIGGER_CONTRACT', str(
            target.PRIVATE / 'contract-green1.private.json'))).read_bytes())

    def test_three_exact_source_bound_bodies(self):
        expected = [(70135, 155, '0f7aa71b63ff733cd37d1711fa502d77baee5cd5c976569cb003dcb40660347e'),
                    (70155, 823, '44dacd31e7c7c89506e1e21031390070162896c1f3489ef4011ac67e04721008'),
                    (70166, 203, '76cd5ef82796d0f2a2b4f25b9dd396df1237f5f6fab4b4e983bab46eb9ff3c99')]
        self.assertEqual(sum(w['bytes'] for w in self.extraction['windows']), 1181)
        for method, size, digest in expected:
            directory = self.windows / str(method)
            raw = (directory / 'code.bin').read_bytes()
            self.assertEqual((len(raw), hashlib.sha256(raw).hexdigest()), (size, digest))
            assembly = json.loads((directory / 'code.asm.private.json').read_bytes())
            self.assertEqual(b''.join(bytes.fromhex(i['hex']) for i in assembly), raw)
            begin, end, _ = struct.unpack('<III', (directory / 'pdata.bin').read_bytes())
            self.assertEqual(end - begin, size)
            cursor = begin
            for row in assembly:
                self.assertEqual(int(row['rva'], 16), cursor)
                cursor += row['size']
            self.assertEqual(cursor, end)

    def test_original_direct_edges_without_invented_tail_return(self):
        windows = self.extraction['windows']
        self.assertEqual(windows[1]['originalPredecessorEdges'], [{
            'rva': '0x6e36356', 'hex': 'e925b3f4ff', 'kind': 'tail-jump',
            'targetVA': '0x146d81680', 'normalReturnRVA': None}])
        self.assertEqual([(e['rva'], e['normalReturnRVA']) for e in windows[2]['originalPredecessorEdges']],
                         [('0x6d8176b', '0x6d81770'), ('0x6d817d9', '0x6d817de')])

    def test_producer_coverage_excludes_original_call(self):
        producer = self.contract['reusedProducer']
        raw = bytes.fromhex(producer['hex'])
        self.assertEqual(len(raw), 19)
        self.assertEqual(hashlib.sha256(raw).hexdigest(),
                         '244138602bac3d7a6ddc8f36854f44837c9bd6583cd95dee2fca61398ed3adcc')
        self.assertEqual((producer['resumeRVA'], producer['originalCallRVA'], producer['originalReturnRVA']),
                         ('0x197fed4f', '0x197fed58', '0x197fed5e'))
        self.assertEqual(raw[7:10], bytes.fromhex('498b1e'))
        self.assertEqual(raw[10:], bytes.fromhex('48c744242000000000'))
        self.assertEqual(producer['originalCallHex'], 'ff93f0000000')

    def test_parameter_types_do_not_turn_args_into_dictionary(self):
        refs = self.contract['resolvedParameterTypes']
        self.assertEqual(refs['451887']['kind'], 'string')
        self.assertEqual(refs['457577']['name'], 'Action`1')
        self.assertEqual(refs['457577']['arguments'][0]['kind'], 'string')
        self.assertEqual(refs['474415']['name'], 'HoYoSDKNativeCallback')
        self.assertEqual(refs['70']['kind'], 'int32')

    def test_wide_module_narrow_export_and_native_call_identity(self):
        bridge = self.contract['nativeBridge']
        self.assertEqual((bridge['cellRVA'], bridge['originalCallRVA'], bridge['originalReturnRVA']),
                         ('0x57b4d40', '0x6d81a9f', '0x6d81aa5'))
        self.assertEqual([(n['value'], n['encoding'], n['bytes']) for n in bridge['declaredNames']],
                         [('AccountPlatNative', 'utf-16-le', 36), ('HoYoSDK_Invoke', 'ascii', 15)])
        self.assertEqual(self.contract['newStaticNameDataBytes'], 51)

    def test_unrelated_or_indirect_edge_and_ambiguous_extent_reject(self):
        fake = [{'rva': '0x6e36356', 'hex': 'ff10', 'mnemonic': 'call',
                 'operands': '0x146d81680'}]
        self.assertEqual(target.direct_edges(fake, 0x146D81680), [])
        fake[0]['hex'] = 'e800000000'
        self.assertEqual(target.direct_edges(fake, 0x146D81680), [])
        table = struct.pack('<6I', 100, 200, 300, 100, 210, 301)
        with self.assertRaisesRegex(ValueError, 'exception-extent-not-unique'):
            target.select_extent(target.extent_diagnostic(table, 100), 2048)
        with self.assertRaisesRegex(ValueError, 'body-exceeds-approved-cap'):
            target.select_extent(target.extent_diagnostic(struct.pack('<III', 100, 2149, 300), 100), 2048)

    def test_static_order_is_not_natural_execution(self):
        for data in [self.extraction, self.contract]:
            self.assertEqual(data['targetCPU'], 0)
            self.assertEqual(data['liveReads'], 0)
            self.assertFalse(data['observerInstalled'])
            self.assertEqual(data['naturalServiceMode'], 'unknown')
            self.assertEqual(data['naturalLoginUrl'], 'unknown')
            self.assertFalse(data['d02Passed'])


if __name__ == '__main__':
    unittest.main()
