"""Read-only module-window scope and compiled transparent thunk checks."""
import struct
import unittest
from unittest.mock import patch

from tools import observe_client72_clean_loader as card


class CleanModuleObservationTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.proxy = (card.RAW/'target/release/examples/sdk72_clean_proxy.dll').read_bytes()
        cls.table = card.pointer_table(cls.proxy)

    def test_compiled_exports_point_to_one_contiguous_table(self):
        pe = card.PE(self.proxy)
        rows = pe.exports()
        self.assertEqual(len(rows), 48)
        self.assertEqual((rows[-1]['ordinal'], rows[-1]['name']), (48, 'Sdk72CleanInspectLoadedSdk'))
        self.assertEqual([x['ordinal'] for x in rows[:47]], list(range(1, 48)))
        for index, row in enumerate(rows[:47]):
            raw = pe.bytes_at(row['rva'], 6)
            self.assertEqual(raw[:2], b'\xff\x25')
            self.assertEqual(row['rva']+6+struct.unpack_from('<i', raw, 2)[0], self.table+8*index)

    def test_changed_thunk_is_rejected_before_process_read(self):
        pe = card.PE(self.proxy)
        changed = bytearray(self.proxy)
        changed[pe.offset(pe.exports()[19]['rva'], 1)] = 0x90
        with self.assertRaisesRegex(ValueError, 'reviewed RIP-relative'):
            card.pointer_table(bytes(changed))

    def fixtures(self):
        modules = {'Astrolabe.dll': {'base': 0x8000000100000000},
                   'Astrolabe_orig.dll': {'base': 0x9000000200000000},
                   'AccountPlatNative.dll': {'base': 0xa000000300000000}}
        original_rvas = [0x41000+0x101*i for i in range(47)]
        windows = {(rva, width): bytes((rva+i*17)%251 for i in range(width))
                   for rva, width in ((card.ENTRY_RVA, card.ENTRY_LENGTH), *card.CALLER_WINDOWS)}
        spans = {modules['Astrolabe.dll']['base']+self.table:
                 struct.pack('<47Q', *(modules['Astrolabe_orig.dll']['base']+x for x in original_rvas))}
        spans.update({modules['AccountPlatNative.dll']['base']+rva: data for (rva, width), data in windows.items()})
        class FixedPE:
            def exports(self):
                return [{'rva': rva} for rva in original_rvas]
            def bytes_at(self, rva, width):
                return windows[rva, width]
        real_pe = card.PE
        def image(data):
            return real_pe(data) if data == self.proxy else FixedPE()
        return modules, spans, image

    def inspect(self, spans, modules, image):
        calls = []
        def read(address, width):
            calls.append((address, width))
            return spans[address]
        with patch.object(card, 'PE', side_effect=image):
            result = card.inspect_windows(read, modules, self.proxy, b'stock', b'sdk')
        return result, calls

    def test_nonzero_high64_targets_and_exact_four_read_budget(self):
        modules, spans, image = self.fixtures()
        result, calls = self.inspect(spans, modules, image)
        self.assertEqual(result['status'], 'bound')
        self.assertTrue(all(x['equal'] for x in result['windows']))
        self.assertEqual([width for address, width in calls], [376, 253, 76, 76])
        self.assertEqual(sum(width for address, width in calls), 781)
        self.assertTrue(all(address > 1<<63 for address, width in calls))
        self.assertFalse(result['targetFunctionsCalled'])
        self.assertFalse(result['hookInstalled'])
        self.assertFalse(result['naturalCallerObserved'])
        self.assertFalse(result['d02Passed'])

    def test_wrong_single_forward_target_does_not_appear_bound(self):
        modules, spans, image = self.fixtures()
        address = modules['Astrolabe.dll']['base']+self.table
        wrong = bytearray(spans[address]); wrong[-1] ^= 1; spans[address] = bytes(wrong)
        result, calls = self.inspect(spans, modules, image)
        self.assertEqual(result['status'], 'window-or-target-mismatch')
        self.assertFalse(result['windows'][0]['equal'])
        self.assertTrue(all(x['equal'] for x in result['windows'][1:]))

    def test_each_exact_sdk_code_window_detects_independent_drift(self):
        for index, (rva, width) in enumerate(((card.ENTRY_RVA, card.ENTRY_LENGTH), *card.CALLER_WINDOWS), 1):
            with self.subTest(rva=rva):
                modules, spans, image = self.fixtures()
                address = modules['AccountPlatNative.dll']['base']+rva
                wrong = bytearray(spans[address]); wrong[width-1] ^= 1; spans[address] = bytes(wrong)
                result, calls = self.inspect(spans, modules, image)
                self.assertEqual([x['equal'] for x in result['windows']], [i != index for i in range(4)])
                self.assertEqual(result['status'], 'window-or-target-mismatch')

    def test_short_pointer_read_is_not_padded_with_zero(self):
        modules, spans, image = self.fixtures()
        address = modules['Astrolabe.dll']['base']+self.table
        spans[address] = spans[address][:-1]
        with self.assertRaises(struct.error):
            self.inspect(spans, modules, image)

    def test_failed_process_read_is_retained_without_other_reads(self):
        modules, spans, image = self.fixtures()
        calls = []
        def read(address, width):
            calls.append((address, width))
            raise OSError(299, 'Bounded module window read failed')
        with patch.object(card, 'PE', side_effect=image), self.assertRaises(OSError):
            card.inspect_windows(read, modules, self.proxy, b'stock', b'sdk')
        self.assertEqual(len(calls), 1)


if __name__ == '__main__':
    unittest.main()
