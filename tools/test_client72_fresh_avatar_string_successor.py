"""Independent fresh-epoch successor readout; tests never execute native calls."""
import copy
import json
import os
from pathlib import Path
import struct
import unittest
from unittest.mock import patch

from . import client72_fresh_avatar_string_successor as card


class FreshInputTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.capture, cls.windows, cls.pins = card.load_sources()

    def test_frozen_sources_need_no_stock_gate(self):
        with patch.object(card.prior.static, 'read_verified', side_effect=AssertionError('stock access')):
            capture, windows, pins = card.load_sources()
        self.assertEqual(card.sha((card.ROOT / self.pins_path()).read_bytes()), card.RAW_SHA)
        self.assertEqual(len(windows), 5)
        self.assertEqual(capture['collection']['branches'], dict.fromkeys(('string', 'list', 'ordinary', 'comparison'), 'candidate'))

    @classmethod
    def pins_path(cls):
        return json.loads((card.ROOT / card.PLAN).read_bytes())['outputPath']

    def test_all_original_native_bytes_bind_exact_extents(self):
        wanted = {
            0x15746c870: (383, 'fb37ad9d367cb55a85501b2ad6d8520a095aa0c40d9feaf37ade46cf8c103ef1'),
            0x1405336c0: (420, 'c6b7251e6025e0accc5fc41e2083852d59a67671875a338738bd3c922250f1a2'),
            0x1405269f0: (5, 'bb3bd836fbc8d866dc73ea8958b74331d207192b579c09e99d87e8637454f8ad'),
            0x15a4d6b60: (2833, '2ae927cf60f8d3e5a4a1ba9595f41e61bc9a6d325d84bfc1bace8c6c27d2fff1'),
            0x15183fe60: (1661, '1b6d394af556f8647e6ee911cd32de784078ab64e29fb9fa37d3368019cd0f5e'),
        }
        for start, end, raw, digest, source in self.windows:
            self.assertEqual((end - start, card.sha(raw)), wanted[start])
            self.assertEqual(digest, wanted[start][1])
            self.assertIn(source, self.pins)

    def test_instruction_source_rejects_byte_and_extent_drift(self):
        window = json.loads((card.ROOT / card.GETTER).read_bytes())['newWindow']
        bad = copy.deepcopy(window)
        bad['instructions'][0]['bytes'] = '57'
        with self.assertRaisesRegex(ValueError, 'hash'):
            card.instruction_bytes(bad)
        bad = copy.deepcopy(window)
        bad['instructions'][1]['address'] = '0x15746c873'
        with self.assertRaisesRegex(ValueError, 'contiguous'):
            card.instruction_bytes(bad)

    def test_full_fresh_collector_and_clock_projection(self):
        memory = card.prior.ExactBytes(self.capture['collection']['records'])
        audit = card.audit_capture(self.capture, memory)
        self.assertTrue(audit['fullTemporalProjectionEqual'])
        self.assertEqual((len(memory.reads), memory.requested, len(memory.exact)), (138, 5408, 34))
        self.assertFalse(memory.aliases)

    def test_omitting_fresh_comparison_cell_does_not_use_zero_page(self):
        collection = self.capture['collection']
        address = collection['moduleBase'] + 0x5bc96f8
        rows = [row for row in collection['records'] if row['address'] != address]
        memory = card.prior.ExactBytes(rows)
        self.assertEqual(memory.lookup(address, 8), (b'', 'unprovided-captured-byte', []))
        with self.assertRaisesRegex(ValueError, 'projection'):
            card.audit_capture(self.capture, memory)
        self.assertTrue(any(r['error'] for r in memory.reads))


class OriginalSuccessorTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.path = Path(os.environ.get('CLIENT72_FRESH_AVATAR_STRING_SUCCESSOR_REPORT', card.OUT / 'first.private.json'))
        cls.report = json.loads(cls.path.read_bytes())
        plan = json.loads((card.ROOT / card.PLAN).read_bytes())
        cls.capture = json.loads((card.ROOT / plan['outputPath']).read_bytes())
        cls.collection = cls.capture['collection']
        cls.module = cls.collection['moduleBase']
        cls.cases = {n['name']: n for n in cls.report['native']}
        cls.getter = cls.cases['string-warm-getter']
        cls.allocator = cls.cases['avatar-allocator-ordinary-comparison']

    def test_original_warm_getter_returns_exact_natural_default(self):
        actual = self.collection['string']
        self.assertTrue(self.getter['returned'])
        self.assertIsNone(self.getter['failure'])
        self.assertIsNone(self.getter['emulatorError'])
        self.assertEqual(self.getter['registers']['rax'], hex(actual['default']))
        self.assertEqual(self.getter['initialRegisters']['rcx'], hex(actual['getterMethodInfo']['pointer']))
        self.assertEqual((self.getter['observations'], self.getter['completedOriginalInstructions']), (25, 25))
        self.assertEqual(self.getter['writes'], [])
        self.assertEqual(self.getter['executedPreferred'][-1], '0x15746c935')

    def test_getter_original_reads_flags_rgctx_and_actual_default_cell(self):
        rows = [r for r in self.report['hostBudget']['records'] if r['purpose'] == 'native.read'
            and r['reader'] in self.getter['executedAddresses']]
        actual = self.collection['string']
        self.assertEqual(len(rows), 7)
        self.assertEqual(int(rows[-1]['address'], 16), actual['owner']['staticArea'])
        self.assertEqual(int.from_bytes(bytes.fromhex(rows[-1]['bytesHex']), 'little'), actual['default'])
        self.assertTrue(all(r['captureOrdinals'] and r['error'] is None for r in rows))
        cold = {'0x140517680', '0x140538230', '0x159b6e720', '0x140523160', '0x140524010'}
        self.assertFalse(cold.intersection(self.getter['executedPreferred']))

    def test_complete_original_avatar_prefix_then_exact_allocator_callers(self):
        prior = json.loads((card.ROOT / card.PREDECESSOR).read_bytes())
        self.assertEqual(self.allocator['executedPreferred'][:10], prior['native'][0]['executedAddresses'])
        first, second = self.allocator['boundaries']
        self.assertEqual(first['preferredEntry'], '0x1405336c0')
        self.assertEqual(first['returnAddress'], hex(self.module + 0x1183fe89))
        self.assertEqual(first['registers']['rcx'], hex(self.collection['list']['pointer']))
        self.assertEqual(second['preferredEntry'], '0x1405269f0')
        self.assertEqual(second['returnAddress'], hex(self.module + 0x533721))
        self.assertEqual(second['registers']['rcx'], hex(1255))

    def test_fresh_ordinary_lookup_return_and_real_comparison_continue(self):
        path = self.allocator['executedPreferred']
        end = path.index('0x15a4d7670')
        self.assertEqual(path[end + 1], '0x140533721')
        self.assertIn('0x140533728', path)
        rows = [r for r in self.report['hostBudget']['records'] if r['purpose'] == 'native.read']
        needed = (self.module + 0x5bc8f18, self.collection['ordinary']['root'] + 1255 * 8,
            self.module + 0x5bc96f8)
        selected = [r for r in rows if int(r['address'], 16) in needed]
        self.assertEqual([int(r['address'], 16) for r in selected], list(needed))
        values = [int.from_bytes(bytes.fromhex(r['bytesHex']), 'little') for r in selected]
        self.assertEqual(values, [self.collection['ordinary']['root'], self.collection['ordinary']['slot'], self.collection['comparison']['value']])
        self.assertNotEqual(values[1], self.collection['list']['pointer'])
        self.assertNotEqual(values[1], values[2])
        self.assertIn('0x14053379f', path)

    def test_exact_first_unprovided_loader_tls_read_preserved(self):
        self.assertEqual(self.allocator['preferredStop'], '0x1405337b9')
        self.assertEqual(self.allocator['failure'], 'unprovided-captured-byte')
        self.assertIn('UC_ERR_READ_UNMAPPED', self.allocator['emulatorError'])
        self.assertEqual(self.allocator['missing'], [dict(address=hex(self.module + 0x5eef080), width=4,
            reader=hex(self.module + 0x5337b9), fetch=False)])
        self.assertEqual((self.allocator['observations'], self.allocator['completedOriginalInstructions']), (106, 105))
        failures = [r for r in self.report['hostBudget']['records'] if r['purpose'] == 'native.invalid-read']
        self.assertEqual(len(failures), 1)
        self.assertEqual(failures[0]['captureOrdinals'], [])
        self.assertEqual(failures[0]['bytesHex'], '')

    def test_actual_equals_selected_and_bound_but_unexecuted(self):
        selected = self.report['actualEqualsSelection']
        self.assertEqual((selected['methodDefinition'], selected['derivedDefinition']), (10339, 1243))
        self.assertEqual(selected['preferredFunction'], '0x159b6f300')
        self.assertTrue(selected['metadataAotBound'])
        self.assertTrue(selected['functionEqualsMethodInfoField'])
        self.assertFalse(selected['bodyBytesAvailable'])
        self.assertFalse(selected['bodyExecuted'])
        self.assertTrue(self.report['findEntryMethodInfoStillZero'])

    def test_cumulative_host_caller_and_cpu_budgets(self):
        budget = self.report['hostBudget']
        self.assertEqual((budget['reads'], budget['requestedBytes']), (224, 8194))
        self.assertEqual(sum(r['width'] for r in budget['records']), 8194)
        self.assertEqual(sum(r['purpose'] == 'native.final-readback' for r in budget['records']), 68)
        self.assertTrue(all(0 < r['width'] <= 208 for r in budget['records']))
        self.assertLessEqual(budget['reads'], 4096)
        self.assertLessEqual(budget['requestedBytes'], 65536)
        self.assertEqual(self.report['cpuBudget']['calls'], 2)
        self.assertEqual(self.report['cpuBudget']['observations'], 131)
        self.assertEqual(self.report['cpuBudget']['completedOriginalInstructions'], 130)
        self.assertEqual(sum(n['boundaryHostBudget']['reads'] for n in self.report['native']), 3)
        self.assertEqual(sum(n['boundaryHostBudget']['requestedBytes'] for n in self.report['native']), 24)
        self.assertTrue(all(n['perCallLimit'] == 2_000_000 for n in self.report['native']))

    def test_fresh_aslr_preserves_all_input_bytes_without_host_success(self):
        self.assertTrue(self.report['fullAvatarEntryUsed'])
        self.assertTrue(self.report['rawCapturedSourceUnchanged'])
        self.assertEqual(len(self.allocator['writes']), 2)
        self.assertEqual([int(w['address'], 16) for w in self.allocator['writes']],
            [card.prior.AVATAR + 0x284, card.prior.AVATAR + 0x2b0])
        for key in ('stockDiskRead', 'oldCapturedHeapImported', 'OSModelsImported', 'allocationsReturnedByHost',
                'hostDefaultReturned', 'capturedWritesAllowed', 'managedAllocationReturned', 'completeStringComparer',
                'completeAvatarConstructor', 'completeAvatarHandler', 'naturalCallerObserved', 'g2Verified', 'd02Complete'):
            self.assertIs(self.report[key], False, key)
        self.assertEqual(self.report['successModelsSupplied'], [])
        for n in self.report['native']:
            self.assertTrue(n['capturedReadbackEqual'])
            self.assertTrue(n['nativeCodeAtCapturedASLR'])
            self.assertFalse(n['capturedPointersChanged'])
            self.assertFalse(n['successModelsSupplied'])


if __name__ == '__main__':
    unittest.main()
