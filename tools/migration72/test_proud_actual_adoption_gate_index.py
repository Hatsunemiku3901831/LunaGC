import struct
import unittest

from tools.migration72 import proud_actual_adoption_gate_index as card


class GateIndexTests(unittest.TestCase):
    def test_signed_calls_and_jumps_only_exact_target(self):
        va = card.QUERY + 0x100
        data = b'\xe8' + struct.pack('<i', -0x105) + b'\xe9' + struct.pack('<i', -0x10a)
        self.assertEqual([v['address'] for v in card.scan_section(data, va)], [hex(va), hex(va + 5)])
        self.assertEqual(card.scan_section(b'\xe8' + struct.pack('<i', -0x104), va), [])
        self.assertEqual(card.scan_section(data[:4], va), [])

    def test_load_requires_rex_w_mod10_non_sib_and_complete_displacement(self):
        for raw in ['488bb0b8120200', '4f8bbfb8120200']:
            self.assertEqual(card.scan_section(bytes.fromhex(raw), 0x1000)[0]['bytes'], 7)
        for raw in ['408bb0b8120200', '4889b0b8120200', '488b30b8120200',
                    '488bb4b8120200', '488bb0b81202', 'b8120200']:
            self.assertEqual(card.scan_section(bytes.fromhex(raw), 0x1000), [], raw)

    def test_sections_do_not_combine_partial_instructions(self):
        self.assertEqual(card.scan_section(bytes.fromhex('488bb0b812'), 0x1000), [])
        self.assertEqual(card.scan_section(bytes.fromhex('0200'), 0x1005), [])

    def test_128_accepted_129_preserved_without_prefix_selection(self):
        rows = [dict(kind='load-displacement-212b8') for _ in range(128)]
        self.assertTrue(card.inventory(rows, 128)['withinCandidateLimit'])
        report = card.inventory(rows + rows[:1], 128)
        self.assertEqual(report['totalCandidates'], 129)
        self.assertFalse(report['withinCandidateLimit'])
        self.assertEqual(report['candidates'], [])

    def test_unique_missing_and_overlapping_exception_records(self):
        candidates = [dict(address=hex(v)) for v in [0x1011, 0x1021, 0x1031]]
        pdata = b''.join(struct.pack('<III', *v) for v in [(0x10, 0x20, 5), (0x20, 0x30, 6), (0x21, 0x25, 7)])
        card.bind_containing(candidates, pdata, 0x1000, 0x500, {0x1010: [dict(owner=7)]})
        self.assertEqual([v['uniqueContainingRecord'] for v in candidates], [True, False, False])
        self.assertEqual(candidates[0]['registeredOwners'], [dict(owner=7)])
        self.assertEqual(candidates[1]['registeredOwners'], [])
        self.assertEqual(len(candidates[1]['containingRecords']), 2)


if __name__ == '__main__':
    unittest.main()
