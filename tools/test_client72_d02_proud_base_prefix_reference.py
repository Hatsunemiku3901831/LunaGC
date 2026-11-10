"""Independent synthetic fixtures for the conditional file-prefix reference."""
import importlib.util
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch


MODULE_PATH = Path(__file__).with_name('client72_d02_proud_base_prefix_reference.py')
SPEC = importlib.util.spec_from_file_location('proud_prefix_reference', MODULE_PATH)
reference = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(reference)


def tokens(result, phase):
    return [token for token in result['tokens'] if token['phase'] == phase]


class PrefixReferenceTests(unittest.TestCase):
    def test_absent_prefix_and_explicit_zero_counts(self):
        absent = reference.parse(bytes.fromhex('276c'))
        self.assertEqual((absent['parseStatus'], absent['stoppedCursor']), ('complete_prefix', 2))
        self.assertEqual([t['phase'] for t in absent['tokens']],
                         ['base-header', 'base-list', 'base-block', 'elements'])
        self.assertTrue(all(t['span'] is None and t['wireBytes'] == 0
                            for t in absent['tokens'][1:]))
        # All three masks set: count and length are independently zero.
        zero = reference.parse(bytes.fromhex('166c 01d8c549 1579 9919ef7a'))
        self.assertEqual((zero['parseStatus'], zero['next63831InputCursor']), ('complete_prefix', 12))
        self.assertEqual(tokens(zero, 'base-list-count')[0]['resultBits'], '00000000')
        self.assertEqual(tokens(zero, 'base-block-length')[0]['resultBits'], '0000')
        self.assertEqual(tokens(zero, 'element-count')[0]['resultBits'], '00000000')
        self.assertIn('field55097 Empty', tokens(zero, 'elements')[0]['defaultOrigin'])
        self.assertEqual(tokens(zero, 'base-list-item'), [])
        self.assertEqual(tokens(zero, 'element-header'), [])

    def test_two_elements_presence_and_32_bit_header_carry(self):
        fixtures = [
            # Two defaults, sum 0x10000 and low 16 bits 0.
            ('376c 9b19ef7a a090 a090', 10, ['00010000', '00010000'],
             [False, False], [False, False], [[6, 8], [8, 10]]),
            # Enum-only then float-only: each consumes 6 bytes.
            ('376c 9b19ef7a e090 cc1c113a 2091 1dca311b', 18,
             ['00010040', '00010080'], [True, False], [False, True], [[6, 8], [12, 14]]),
            # Both present in each: each consumes 10 bytes.
            ('376c 9b19ef7a 6091 cc1c113a 29d8711b 6091 cc1c113a 29d8f1e4',
             26, ['000100c0', '000100c0'], [True, True], [True, True], [[6, 8], [16, 18]]),
        ]
        for data, end, sums, enums, floats, header_spans in fixtures:
            with self.subTest(data=data):
                result = reference.parse(bytes.fromhex(data))
                self.assertEqual((result['parseStatus'], result['stoppedCursor']), ('complete_prefix', end))
                headers = tokens(result, 'element-header')
                self.assertEqual([h['span'] for h in headers], header_spans)
                self.assertEqual([h['cursorAfter'] for h in headers], [s[1] for s in header_spans])
                self.assertEqual([h['resultBits'] for h in headers], sums)
                self.assertEqual([h['headerSumU32'] for h in headers], [int(s, 16) for s in sums])
                self.assertEqual([h['headerLow16'] for h in headers], [int(s, 16) & 0xffff for s in sums])
                enum_tokens, float_tokens = tokens(result, 'element-enum'), tokens(result, 'element-float')
                self.assertEqual([t['present'] for t in enum_tokens], enums)
                self.assertEqual([t['present'] for t in float_tokens], floats)
                self.assertEqual([t['resultBits'] for t in enum_tokens if t['present']],
                                 ['fedcba98'] * sum(enums))
                for t in enum_tokens:
                    self.assertEqual(t['conditionalReceiverWrite'], {
                        'pc': '14ee1ea9a', 'offset': 0, 'width': 4,
                        'bits': t['resultBits'], 'nativeExecuted': False,
                    })
                for t in enum_tokens + float_tokens:
                    if not t['present']:
                        self.assertIsNone(t['span'])
                        self.assertEqual(t['wireBytes'], 0)
                for t in float_tokens:
                    self.assertIsNone(t['storedEncodedDWORD'])
                    self.assertEqual(t['storedEncodedStatus'], 'unknown-missing-leaf-body')
                for key in ('naturalBackendProven', 'naturalModeObserved', 'actualIndexFilled',
                            'actualRowSpanApproved', 'typedAllocatorReturnProven',
                            'typedReceiverOutputApproved', 'nativeParserExecuted',
                            'completeProudReference', 'resourceReady', 'completeAvatar', 'd02Complete'):
                    self.assertFalse(result[key], key)
        self.assertEqual([t['floatInputBits'] for t in tokens(reference.parse(bytes.fromhex(fixtures[1][0])), 'element-float')],
                         ['00000000', '7fc01234'])
        self.assertEqual([t['floatInputBits'] for t in tokens(reference.parse(bytes.fromhex(fixtures[2][0])), 'element-float')],
                         ['7f800000', '80000000'])

    def test_base_list_transform_including_u32_carry(self):
        # n=(0x49c5d803+0xb63a27ff) mod 2^32 = 2.
        result = reference.parse(bytes.fromhex('076c 03d8c549 df4287e5 9b71a574'))
        self.assertEqual((result['parseStatus'], result['stoppedCursor']), ('complete_prefix', 14))
        self.assertEqual(tokens(result, 'base-list-count')[0]['resultBits'], '00000002')
        items = tokens(result, 'base-list-item')
        self.assertEqual([x['span'] for x in items], [[6, 10], [10, 14]])
        self.assertEqual([x['floatInputBits'] for x in items], ['11223344', '80000000'])
        self.assertTrue(all(x['storedEncodedDWORD'] is None for x in items))

    def test_string_independent_qwords_and_remainder(self):
        # First group overflows U64; no carry is applied to the second group.
        result = reference.parse(bytes.fromhex('266c 0779 ffffffffffffffff 0000000000000000 ffff'))
        self.assertEqual((result['parseStatus'], result['stoppedCursor']), ('complete_prefix', 22))
        block = tokens(result, 'base-block')[0]
        self.assertEqual(block['span'], [4, 22])
        self.assertEqual(block['rawHex'], 'ffffffffffffffff0000000000000000ffff')
        self.assertEqual(block['transformedHex'], '1479e39e268e169a1579e39e268e169a1479')
        self.assertIsNone(block['stringEncoding'])
        self.assertFalse(block['nativeStringReturned'])

    def test_original_start_passes_but_float_width_unavailable(self):
        # Header and enum succeeded; float has only two of the four required bytes.
        result = reference.parse(bytes.fromhex('376c 9819ef7a 6091 cc1c113a 29d8'))
        self.assertEqual(result['parseStatus'], 'partial')
        self.assertIn('physical-width-unavailable', result['stopReason'])
        self.assertEqual(result['stoppedCursor'], 12)
        self.assertIsNone(result['next63831InputCursor'])
        enum = tokens(result, 'element-enum')[0]
        self.assertEqual(enum['resultBits'], 'fedcba98')
        self.assertEqual(enum['conditionalReceiverWrite'], {
            'pc': '14ee1ea9a', 'offset': 0, 'width': 4,
            'bits': 'fedcba98', 'nativeExecuted': False,
        })
        self.assertEqual(enum['cursorAfter'], 12)
        failed = tokens(result, 'element-float')[0]
        self.assertEqual(failed['span'], [12, 16])
        self.assertEqual(failed['cursorBefore'], 12)
        self.assertEqual(failed['cursorAfter'], 12)
        self.assertTrue(failed['originalStartCheck'])
        self.assertFalse(failed['physicalFullWidthCheck'])
        self.assertIsNone(failed['rawHex'])

    def test_start_failure_and_candidate_boundary_are_separate(self):
        failed = reference.parse(bytes.fromhex('376c 9819ef7a'))
        self.assertIn('original-start-check-failed', failed['stopReason'])
        self.assertFalse(tokens(failed, 'element-header')[0]['originalStartCheck'])
        complete = reference.parse(bytes.fromhex('376c 9819ef7a a090'), candidate_end=7)
        self.assertEqual((complete['parseStatus'], complete['stoppedCursor']), ('complete_prefix', 8))
        self.assertTrue(tokens(complete, 'element-header')[0]['candidateBoundaryCrossed'])
        self.assertTrue(tokens(complete, 'element-header')[0]['physicalFullWidthCheck'])

    def test_high_counts_remain_partial(self):
        fixtures = [
            ('076c 01d8c5c9', 'unapproved-high-base-list-count', 'base-list-item'),
            ('376c 9919effa', 'unapproved-high-element-count-signed-loop', 'element-header'),
        ]
        for data, reason, item_phase in fixtures:
            with self.subTest(reason=reason):
                result = reference.parse(bytes.fromhex(data))
                self.assertEqual(result['parseStatus'], 'partial')
                self.assertEqual(result['stopReason'], reason)
                self.assertEqual(result['stoppedCursor'], 6)
                self.assertIsNone(result['next63831InputCursor'])
                self.assertEqual(tokens(result, item_phase), [])

    def test_existing_output_rejected_without_preflight_or_modification(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            directory = root / 'isolated'
            directory.mkdir()
            target = directory / 'existing.json'
            target.write_bytes(b'preserve\x00fixture')
            with patch.object(reference, 'ROOT', root), patch.object(reference, 'PRIVATE', Path('isolated')), \
                    patch.object(reference, 'preflight', side_effect=AssertionError('must not read sources')):
                with self.assertRaises(FileExistsError):
                    reference.main(['--output', target.name])
            self.assertEqual(target.read_bytes(), b'preserve\x00fixture')


if __name__ == '__main__':
    unittest.main()
