import copy
import json
from pathlib import Path
import unittest

from tools.trace_client72_marshal import validate_summary


class MarshalStorageTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.report = json.loads((Path(__file__).resolve().parents[1] /
                                 'protocol/cn-7.2/marshal-storage-observations.json').read_text('utf8'))

    def test_native_conversion_and_declared_storage_contract(self):
        self.assertEqual(validate_summary(self.report), [])

    def test_null_empty_and_embedded_nul_remain_distinct(self):
        rows = {row['name']:row for row in self.report['results']}
        self.assertIsNone(rows['null']['resultLength'])
        self.assertEqual(rows['empty']['resultLength'], 0)
        self.assertEqual(bytes.fromhex(rows['embedded-nul']['resultUtf16Hex']), b'a\0b\0c\0')
        self.assertEqual(rows['embedded-nul']['allocationEvents'], [])

    def test_non_ascii_and_surrogate_pair_use_utf16_code_units(self):
        row = next(row for row in self.report['results'] if row['name'] == 'unicode')
        text = bytes.fromhex(row['inputHex']).decode('utf8')
        self.assertEqual(bytes.fromhex(row['resultUtf16Hex']), text.encode('utf-16-le'))
        self.assertEqual(row['resultLength'], len(text) + 1)

    def test_inline_to_heap_boundary_and_tracked_free(self):
        rows = {row['name']:row for row in self.report['results']}
        self.assertEqual(rows['ascii-7']['allocationEvents'], [])
        for name in ('ascii-8', 'ascii-16', 'ascii-256', 'ascii-1024'):
            self.assertEqual([event['operation'] for event in rows[name]['allocationEvents']],
                             ['allocate', 'allocation-size-query', 'free'])
            self.assertEqual(rows[name]['liveCrtAllocations'], 0)

    def test_storage_models_cannot_claim_live_or_native_allocator_success(self):
        for key, value in [('runtimeStatus', 'passed'), ('liveMarshalObserved', True),
                           ('nativeCrtAllocatorExecuted', True), ('nativeManagedAllocatorExecuted', True),
                           ('nativeMarshalExecuted', False), ('inputSHA256', {})]:
            report = copy.deepcopy(self.report)
            report[key] = value
            self.assertTrue(validate_summary(report), key)

    def test_wrong_tls_source_conversion_or_ownership_is_rejected(self):
        for field, value in [('crtAllocatorTlsOffset', 0x19D0), ('managedAllocatorTlsOffset', 0x1A50)]:
            report = copy.deepcopy(self.report)
            report['allocatorInputs'][field] = value
            self.assertTrue(validate_summary(report))
        for key, value in [('resultUtf16Hex', ''), ('resultLength', 1), ('liveCrtAllocations', 1), ('allocationEvents', [])]:
            report = copy.deepcopy(self.report)
            report['results'][3][key] = value
            self.assertTrue(validate_summary(report))


if __name__ == '__main__':
    unittest.main()
