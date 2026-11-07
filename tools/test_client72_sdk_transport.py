"""Independent PE fixtures and assertions over an actually executed CN72 report."""
import json
import os
from pathlib import Path
import struct
import unittest

from tools.client72_sdk_transport import Image, MODULE_HASHES, NATURAL_SHA, ROOT, digest


def synthetic_pe():
    data = bytearray(0x2200)
    data[:2] = b'MZ'
    struct.pack_into('<I', data, 0x3c, 0x80)
    data[0x80:0x84] = b'PE\0\0'
    struct.pack_into('<HH', data, 0x84, 0x8664, 1)
    struct.pack_into('<H', data, 0x94, 0xf0)
    optional = 0x98
    struct.pack_into('<H', data, optional, 0x20b)
    struct.pack_into('<Q', data, optional + 24, 0x180000000)
    struct.pack_into('<I', data, optional + 108, 16)
    section = optional + 0xf0
    data[section:section+8] = b'.fixture'
    struct.pack_into('<III', data, section+12, 0x1000, 0x2000, 0x200)
    struct.pack_into('<I', data, section+36, 0x60000020)
    # Independent literal layout. Same spelling in separate DLL descriptors is
    # kept separate; raw ordinal 0x123 cannot be silently converted to a name.
    struct.pack_into('<II', data, optional+112+8, 0x1300, 40)
    struct.pack_into('<5I', data, 0x500, 0x1400, 0, 0, 0x1380, 0x1440)
    data[0x580:0x589] = b'ONE.dll\0\0'
    struct.pack_into('<3Q', data, 0x600, 0x1500, 0x8000000000000123, 0)
    struct.pack_into('<H', data, 0x700, 7)
    data[0x702:0x712] = b'GetProcAddress\0\0'
    struct.pack_into('<II', data, optional+112+13*8, 0x1600, 64)
    struct.pack_into('<8I', data, 0x800, 1, 0x1680, 0x1690, 0x16c0, 0x16a0, 0, 0, 0)
    data[0x880:0x889] = b'TWO.dll\0\0'
    struct.pack_into('<2Q', data, 0x8a0, 0x1500, 0)
    return data


class PEFixtureTests(unittest.TestCase):
    def test_normal_delay_and_64bit_ordinal_are_distinct(self):
        image = Image(bytes(synthetic_pe()))
        rows = image.imports()
        self.assertEqual(image.base, 0x180000000)
        self.assertEqual([(r['kind'], r['dll']) for r in rows], [('normal', 'ONE.dll'), ('delay', 'TWO.dll')])
        self.assertEqual(rows[0]['functions'], [
            {'name': 'GetProcAddress', 'ordinal': None, 'iatRva': 0x1440, 'iltRva': 0x1400},
            {'name': None, 'ordinal': 0x123, 'iatRva': 0x1448, 'iltRva': 0x1408}])
        self.assertEqual(rows[1]['functions'][0]['iatRva'], 0x16c0)

    def test_unterminated_descriptor_is_rejected(self):
        data = synthetic_pe()
        struct.pack_into('<I', data, 0x98+112+8+4, 20)
        with self.assertRaisesRegex(ValueError, 'lack terminator'):
            Image(bytes(data)).imports()

    def test_unsupported_delay_va_is_not_misread_as_rva(self):
        data = synthetic_pe()
        struct.pack_into('<I', data, 0x800, 0)
        with self.assertRaisesRegex(ValueError, 'RVA-based'):
            Image(bytes(data)).imports()

    def test_bad_name_rva_is_rejected(self):
        data = synthetic_pe()
        struct.pack_into('<I', data, 0x500+12, 0xfffffff0)
        with self.assertRaises(ValueError):
            Image(bytes(data)).imports()

    def test_immediate_byte_call_false_positive_is_rejected(self):
        data = synthetic_pe()
        struct.pack_into('<II', data, 0x98+112+3*8, 0x1700, 24)
        struct.pack_into('<6I', data, 0x900, 0x1100, 0x1106, 0, 0x1120, 0x112b, 0)
        data[0x300] = 0xe8
        struct.pack_into('<i', data, 0x301, 0x1200-0x1105)
        data[0x305] = 0xc3
        data[0x320:0x322] = b'\x48\xb8'
        data[0x322] = 0xe8
        struct.pack_into('<i', data, 0x323, 0x1200-0x1127)
        data[0x32a] = 0xc3
        rows = Image(bytes(data)).direct_callers(0x1200)
        self.assertEqual([r['siteRva'] for r in rows], [0x1100])


class ActualReportTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        path = Path(os.environ.get('SDK72_TRANSPORT_REPORT',
            str(ROOT/'local/migration72/d02-active/sdk-transport-astra/final-first.json')))
        cls.report = json.loads(path.read_bytes())
        cls.sdk = next(m for m in cls.report['modules'] if m['path'].endswith('AccountPlatNative.dll'))
        cls.exe = next(m for m in cls.report['modules'] if m['path'] == 'YuanShen.exe')

    def test_fixed_source_inputs_and_independent_rows(self):
        self.assertEqual(self.report['naturalSHA256'], NATURAL_SHA)
        self.assertEqual(self.report['sourceSHA256'], digest((ROOT/'tools/client72_sdk_transport.py').read_bytes()))
        self.assertEqual({Path(m['path']).name:m['sha256'] for m in self.report['modules']}, MODULE_HASHES)
        self.assertEqual(sum(m['independentImportRows'] for m in self.report['modules']), 2812)
        for name, expected in self.report['dependencySHA256'].items():
            self.assertEqual(digest((ROOT/'tools'/name).read_bytes()), expected)

    def test_account_winhttp_imports_are_proxy_only(self):
        api = next(d for d in self.sdk['imports'] if d['dll'] == 'WINHTTP.dll')
        self.assertEqual({f['name'] for f in api['functions']}, {'WinHttpGetProxyForUrl','WinHttpSetTimeouts',
            'WinHttpCloseHandle','WinHttpGetIEProxyConfigForCurrentUser','WinHttpOpen'})
        self.assertFalse(any(d['kind']=='delay' for d in self.sdk['imports']))

    def test_exact_curl_compatible_entry_and_four_callers(self):
        self.assertEqual(self.sdk['setoptPdata'], [0x41e6f0, 0x41e71e])
        self.assertEqual(self.sdk['setoptEntryBytes'],
            '895424104c894424184c894c24204883ec284885c975088d412b4883c428c34c8d442440e8879d00004883c428c3')
        rows = self.sdk['selectedCallers']['0x41e6f0']
        self.assertEqual(len(rows), 34)
        self.assertEqual({r['function'][0] for r in rows}, {0x2f0940,0x2f3360,0x2f3940,0x2f5ae0})
        self.assertTrue(all(r['status']=='instruction-boundary-verified' for r in rows))

    def test_real_option_error_and_64bit_results(self):
        rows = {s['name']:s for s in self.sdk['nativeSamples']}
        self.assertEqual(len(rows), 14)
        self.assertEqual(rows['null-handle']['return'],43)
        self.assertEqual(rows['unknown-option']['return'],48)
        self.assertEqual(rows['int64-above-2pow53']['stored'],9007199254740993)
        self.assertEqual(rows['int64-minus-one']['stored'],0xffffffffffffffff)
        self.assertEqual(rows['timeout-nonzero']['stored'],37000)

    def test_native_url_copy_clear_and_alloc_failure(self):
        rows = {s['name']:s for s in self.sdk['nativeSamples']}
        for name in ['url-ascii','url-empty','url-utf8','url-nul','url-long','url-syntax-not-validated-here']:
            self.assertEqual(rows[name]['return'],0)
            self.assertEqual(rows[name]['clearReturn'],0)
            self.assertTrue(rows[name]['copyTraceSHA256'])
        self.assertEqual(rows['url-allocation-failure']['return'],27)
        self.assertEqual(rows['url-replace-then-allocation-failure']['return'],27)

    def test_metadata_parameters_and_rtti_are_exact(self):
        owner = self.exe['managedSDK'][0]
        self.assertEqual((owner['namespace'],owner['name']),('MiHoYo.SDK','AccountPlatNative'))
        init = next(m for m in owner['methods'] if m['name']=='HoYoSDK_InitGlobalEnv')
        self.assertEqual([p['name'] for p in init['parameters']],
                         ['strCurDir','strProductName','strCompanyName','strDataDir','nClientType'])
        self.assertEqual([p['type']['kind'] for p in init['parameters']],['string']*4+['int32'])
        self.assertEqual([r['typeName'] for r in self.sdk['staticRTTI']],
                         ['.?AVMiHoYoSDKDll@mihoyo_sdk@@','.?AVHoYoNetworkEngine@mihoyo_sdk@@'])


if __name__ == '__main__':
    unittest.main()
