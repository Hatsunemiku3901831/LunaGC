"""Independent byte assertions for the bounded native service extraction."""
import hashlib
import json
import os
from pathlib import Path
import struct
import unittest

from tools import client72_sdk_native_account_local_route as target


class NativeAccountRouteStaticTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.directory = Path(os.environ.get('SDK72_NATIVE_ACCOUNT_ROUTE_REPORT', str(
            target.PRIVATE / 'static-first')))
        cls.report = json.loads((cls.directory / 'static-audit.private.json').read_bytes())
        cls.code = (cls.directory / 'code.bin').read_bytes()
        cls.assembly = json.loads((cls.directory / 'code.asm.private.json').read_bytes())

    def test_unique_service_identity_and_exact_extent(self):
        r = self.report
        self.assertEqual((r['entryRVA'], r['endRVAExclusive'], r['codeBytes']),
                         ('0x3413e0', '0x34180f', 1071))
        self.assertEqual(hashlib.sha256(self.code).hexdigest(),
                         'eea7fc7301072783039a2dcf7951c2a61e87dd3f935b63701b49fa2e6e32ef1a')
        self.assertEqual(struct.unpack('<III', (self.directory / 'pdata.bin').read_bytes()),
                         (0x3413E0, 0x34180F, 0x730A00))
        self.assertEqual([(s['service'], int(s['vtableRVA'], 16),
                           struct.unpack('<Q', bytes.fromhex(s['hex']))[0])
                          for s in r['serviceSlots']],
                         [('mdk', 0x6C2098, 0x1803413E0),
                          ('accountplat', 0x6C2CA8, 0x1803413E0)])

    def test_complete_instruction_bytes_and_ordinary_prolog(self):
        self.assertEqual(b''.join(bytes.fromhex(i['hex']) for i in self.assembly), self.code)
        cursor = 0x3413E0
        for i in self.assembly:
            self.assertEqual(int(i['rva'], 16), cursor)
            cursor += i['size']
        self.assertEqual(cursor, 0x34180F)
        self.assertEqual(self.code[:21].hex(), '40555356574154415541564157488dac24a8fcffff')
        self.assertEqual(self.code[21:28].hex(), '4881ec58040000')
        self.assertEqual(self.report['entryCoverage']['coveredBytes'], 21)
        self.assertEqual(self.report['entryCoverage']['resumeRVA'], '0x3413f5')
        self.assertEqual([i['mnemonic'] for i in self.assembly[:9]], ['push'] * 8 + ['lea'])
        self.assertEqual(self.code[-20:].hex(), '4881c458040000415f415e415d415c5f5e5b5dc3')

    def test_service_and_low32_mode_before_subdispatch(self):
        indexed = {int(i['rva'], 16): i for i in self.assembly}
        for rva, raw in [(0x341410, '418bf0'), (0x341416, '488bf9'),
                         (0x34168C, '448bc6'), (0x341692, '488bcf'),
                         (0x341695, 'ff9020010000')]:
            self.assertEqual(indexed[rva]['hex'], raw)
        self.assertLess(0x341416, 0x341695)
        self.assertEqual(self.report['existingREAEvidence'],
            'ev_6d7f10950d9744646b137e702ab759efd00c8a10c161a77721929e207140bdf9')

    def test_static_findings_do_not_promote_natural_route(self):
        r = self.report
        self.assertEqual(len(r['naturalElevenModuleDiskInputs']), 11)
        self.assertEqual(len(r['accountPlatCandidateRoutes']), 6)
        self.assertEqual(r['approvedNaturalRoute'], 'unknown')
        self.assertEqual(r['naturalServiceMode'], 'unknown')
        for key in ['targetedXdataBytes', 'neighborCalleeBytes', 'targetCPU', 'liveReads']:
            self.assertEqual(r[key], 0)
        for key in ['hookImplemented', 'serviceSelectionObserved', 'authenticationObserved', 'd02Passed']:
            self.assertIs(r[key], False)

    def test_rejects_overlapping_exception_extent_and_oversize(self):
        ambiguous = struct.pack('<6I', 100, 200, 300, 100, 210, 310)
        with self.assertRaisesRegex(ValueError, 'exception-extent-not-unique'):
            target.select_extent(target.extent_diagnostic(ambiguous, 100))
        too_large = struct.pack('<III', 100, 100 + 8193, 300)
        with self.assertRaisesRegex(ValueError, 'body-exceeds-approved-cap'):
            target.select_extent(target.extent_diagnostic(too_large, 100))
        with self.assertRaisesRegex(ValueError, 'exception-directory-misaligned'):
            target.extent_diagnostic(ambiguous + b'\0', 100)


if __name__ == '__main__':
    unittest.main()
