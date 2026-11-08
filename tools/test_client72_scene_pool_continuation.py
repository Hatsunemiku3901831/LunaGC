"""Independent source, exact branch and missing TLS-input checks."""
import copy
import json
import os
import struct
import unittest
from pathlib import Path

from tools import client72_scene_pool_continuation as card


class PoolPrefixTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        path = Path(os.environ.get('CLIENT72_POOL_PREFIX_REPORT', str(card.RAW/'first.json')))
        cls.report = json.loads(path.read_bytes())
        cls.sample = json.loads(card.SNAPSHOT.read_bytes())

    def test_fixed_capture_and_all_five_inputs(self):
        self.assertEqual(self.report['freshSnapshotSHA256'], card.SNAPSHOT_SHA)
        self.assertEqual(self.report['sourceSHA256'], card.digest(Path(card.__file__).read_bytes()))
        self.assertEqual(self.report['fixedInputs'], card.EXPECTED)
        self.assertEqual(self.report['static']['TLSdirectoryRVA'], '0x3a8dab0')
        self.assertEqual(self.report['static']['addressOfIndex'], '0x145eef080')
        self.assertEqual(len(self.report['static']['windows']), 3)

    def test_actual_nonzero_mode_reaches_first_loader_index_without_using_zero(self):
        row = self.report['actualModePrefix']
        self.assertEqual(row['mode'], 1)
        self.assertIn('0x1470687cc', row['nativeTrace'])
        self.assertIn('0x14050a890', row['nativeTrace'])
        self.assertNotIn('0x14050ad90', row['nativeTrace'])
        self.assertEqual(row['nativeTrace'][-1], '0x14050a8ae')
        self.assertEqual(row['stop'], {'reason': 'unprovided-loader-TLS-index', 'reader': '0x14050a8ae',
            'address': '0x145eef080', 'bytes': 4, 'instructionExecuted': False})
        self.assertEqual(row['providedGlobalReads'], [{'address': '0x14586d70b', 'bytes': 1, 'value': 1}])

    def test_controlled_zero_mode_is_a_distinct_forward_branch(self):
        row = self.report['controlledZeroMode']
        self.assertEqual(row['mode'], 0)
        self.assertIn('0x1470687d1', row['nativeTrace'])
        self.assertNotIn('0x14050a890', row['nativeTrace'])
        self.assertEqual(row['nativeTrace'][-1], '0x14050ad90')
        self.assertEqual(row['stop']['reason'], 'controlled-zero-mode-forward-target')
        self.assertIn('independent zero-mode control', row['modeSource'])

    def test_no_allocator_or_thread_success_model_and_no_world_claim(self):
        for row in (self.report['actualModePrefix'], self.report['controlledZeroMode']):
            self.assertTrue(row['classPointerPreserved64'])
            self.assertEqual(row['modelCalls'], 0)
            self.assertFalse(row['classHeaderConsumed'])
            self.assertFalse(row['allocatorReturned'])
        for key in ('allocatorCompleted', 'worldFactoryCompleted', 'naturalCallerObserved', 'd02Passed'):
            self.assertFalse(self.report[key])
        self.assertEqual(self.report['nextInput']['bytes'], 4)
        self.assertIn('allocating thread', self.report['nextInput']['laterThreadRequirement'])

    def altered(self, name, transform):
        sample = copy.deepcopy(self.sample)
        row = next(x for x in sample['reads'] if x['name'] == name)
        raw = transform(bytearray.fromhex(row['bytesHex']))
        row['bytesHex'] = bytes(raw).hex(); row['requested'] = len(raw); row['sha256'] = card.digest(raw)
        return sample

    def test_other_type_or_kind_is_rejected_even_with_consistent_span_hash(self):
        for offset, value, width in ((0x10, 46315, 8), (0x18, 21<<16, 4)):
            def change(raw):
                raw[offset:offset+width] = value.to_bytes(width, 'little'); return raw
            sample = self.altered('pool.entityClass46314.header', change)
            with self.subTest(offset=offset), self.assertRaisesRegex(ValueError, 'class46314'):
                card.fresh_inputs(sample)

    def test_unread_partial_or_false_mode_does_not_supply_a_default(self):
        for kind in ('read-error', 'partial', 'mode0'):
            sample = copy.deepcopy(self.sample)
            row = next(x for x in sample['reads'] if x['name'] == ('pool.mode' if kind == 'mode0' else 'pool.entityClass46314.header'))
            if kind == 'read-error':
                row['error'] = 'fixture-read-failed'
            elif kind == 'partial':
                row['bytesHex'] = row['bytesHex'][:-2]
            else:
                row['bytesHex'] = '00'; row['sha256'] = card.digest(b'\0')
            with self.subTest(kind=kind), self.assertRaises(ValueError):
                card.fresh_inputs(sample)


if __name__ == '__main__':
    unittest.main()
