"""Offline source/window regressions for the fixed Proud row-reader contract."""
import copy
import json
import unittest
from unittest import mock

from tools import client72_d02_proud_row_reader as current
from tools import client72_d02_proud_row_source as prior


class ProudRowReaderTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.files = {name: current.pinned(current.OUT / 'reader1' / name, pin)
                     for name, pin in current.READER_PINS.items()}

    def test_real_contract_preserves_branch_and_unknown_boundaries(self):
        result = current.audit()
        handle = result['branchContract']['handleCache']
        cached = result['branchContract']['readerCache']
        self.assertEqual(1438, result['readerWindow']['codeBytes'])
        self.assertEqual('uint64; not a FileStream object', handle['cacheValueType'])
        self.assertFalse(handle['returnCountChecked'])
        self.assertFalse(cached['BIsUniversalSliceSize'])
        self.assertFalse(result['actualRowSpanApproved'])
        self.assertFalse(result['wholePayloadOffsetZeroIsRowApproved'])
        high = result['int32ToInt64Examples'][2]
        self.assertEqual(('0x80000000', -2147483648, '0xffffffff80000000'),
                         (high['u32Bits'], high['signedInt32'], high['signExtendedU64']))

    def test_source_drift_stops_before_metadata_or_semantics(self):
        first = next(iter(current.PINS))
        with mock.patch.dict(current.PINS, {first: '0' * 64}):
            with mock.patch.object(current.pickle, 'loads', side_effect=AssertionError('unexpected metadata load')):
                with self.assertRaisesRegex(ValueError, 'fixed-source-drift'):
                    current.audit()

    def test_original_code_drift_is_rejected(self):
        files = dict(self.files)
        files['code.bin'] = bytes([files['code.bin'][0] ^ 1]) + files['code.bin'][1:]
        with self.assertRaises(ValueError):
            current.reader_window(files, prior.window)

    def test_neighbor_method_is_not_an_extent(self):
        files = dict(self.files)
        report = json.loads(files['extraction.private.json'])
        report['endExclusive'] = '0x14bb5b650'
        files['extraction.private.json'] = json.dumps(report).encode()
        with self.assertRaisesRegex(ValueError, 'extent'):
            current.reader_window(files, prior.window)

    def test_duplicate_containing_pdata_is_rejected(self):
        files = dict(self.files)
        report = json.loads(files['extraction.private.json'])
        diagnostic = report['exceptionDirectory']['diagnostic']
        diagnostic['containing'].append(copy.deepcopy(diagnostic['containing'][0]))
        files['extraction.private.json'] = json.dumps(report).encode()
        with self.assertRaisesRegex(ValueError, 'unique exact pdata'):
            current.reader_window(files, prior.window)

    def test_wrong_original_aot_slot_is_rejected(self):
        files = dict(self.files)
        files['slot.bin'] = (0x14BB5B650).to_bytes(8, 'little')
        with self.assertRaisesRegex(ValueError, 'AOT'):
            current.reader_window(files, prior.window)


if __name__ == '__main__':
    unittest.main()
