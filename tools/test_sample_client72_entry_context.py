import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from tools import sample_client72_entry_context as s

B = 0x1800000000
R = 0x2200000000

def u64(value):
    return value.to_bytes(8, 'little')

class SnapshotTests(unittest.TestCase):
    def fixture(self):
        memory = {B + 0x586ceb2: b'\x01', B + 0x586b600: u64(R)}
        for n, offset in enumerate((0x60e8, 0x6100, 0x5d60, 0xc608, 0xc6d0, 0xc698)):
            obj = 0x3300000000 + n * 0x1000
            memory[R + offset] = u64(obj)
            if n < 2:
                memory[obj] = bytes([n + 1]) * (40 if n == 0 else 32)
            else:
                klass = 0x4400000000 + n * 0x1000
                memory[obj] = u64(klass)
                if n < 5:
                    memory[klass] = bytes([n]) * 0xd0
        return memory

    def collect(self, memory, failure=None):
        calls = []
        def read(address, size):
            calls.append((address, size))
            if address == failure:
                return b'\x01', {'bytesRead': 1}
            return memory[address], None
        return s.collect(read, B), calls

    def test_full_high_pointers_and_budget(self):
        result, calls = self.collect(self.fixture())
        self.assertEqual((len(calls), sum(n for _, n in calls), max(n for _, n in calls)), (17, 785, 208))
        self.assertEqual(result['modeValue'], 1)
        self.assertEqual(result['status'], 'completed')
        self.assertEqual(calls[2][0], R + 0x60e8)
        self.assertFalse(result['poolAllocateSlotObserved'])
        for name in ('completeCtorVerified', 'naturalRegistryVerified', 'worldInitializationVerified',
                     'sdkRequestVerified', 'targetProtocolReady'):
            self.assertFalse(result[name])
        self.assertNotIn('World', result)

    def test_null_root_and_mode_independent(self):
        memory = self.fixture(); memory[B + 0x586b600] = u64(0)
        result, calls = self.collect(memory)
        self.assertEqual(len(calls), 2)
        self.assertEqual(result['modeInterpretation'], 'true')

    def test_null_slot_and_class_stop(self):
        memory = self.fixture(); memory[R + 0x60e8] = u64(0)
        pool = int.from_bytes(memory[R + 0x5d60], 'little'); memory[pool] = u64(0)
        result, calls = self.collect(memory)
        self.assertEqual(len(calls), 15)
        self.assertNotIn('hashSet.header', [r['name'] for r in result['reads']])
        self.assertNotIn('pool.classHeader', [r['name'] for r in result['reads']])

    def test_partial_does_not_follow_and_is_partial(self):
        memory = self.fixture()
        result, calls = self.collect(memory, R + 0xc608)
        self.assertEqual(result['status'], 'partial')
        self.assertEqual(len(calls), 15)
        self.assertNotIn('registryStringToUint.klass', [r['name'] for r in result['reads']])

    def test_mode_unknown_and_failed(self):
        memory = self.fixture(); memory[B + 0x586ceb2] = b'\x02'
        result, _ = self.collect(memory)
        self.assertEqual((result['modeValue'], result['modeInterpretation']), (2, 'unknown'))
        result, _ = self.collect(memory, B + 0x586ceb2)
        self.assertIsNone(result['modeValue']); self.assertEqual(result['status'], 'partial')

    def test_output_scope_and_overwrite(self):
        with tempfile.TemporaryDirectory() as tmp:
            with patch.object(s, 'OUTPUT_ROOT', Path(tmp) / 'local'), patch.object(s, 'ROOT', Path(tmp)):
                allowed = Path(tmp) / 'local' / 'new.json'
                allowed.parent.mkdir()
                self.assertEqual(s.output_path(allowed), allowed.resolve())
                with self.assertRaises(ValueError): s.output_path(allowed, Path(tmp))
                allowed.touch()
                with self.assertRaises(ValueError): s.output_path(allowed)
                with self.assertRaises(ValueError): s.output_path(Path(tmp).parent / 'escape.json')

    def test_contract_and_code_drift_rejected(self):
        with patch.object(s, 'CONTRACTS', ((s.CONTRACTS[0][0], '0' * 64),)):
            with self.assertRaisesRegex(ValueError, 'Contract hash mismatch'): s.verify_contracts(b'')
        class FakePE:
            base = 0
            def __init__(self, binary): pass
            def offset(self, address, size, executable=False): return 0
        with patch.object(s, 'PE', FakePE):
            with self.assertRaisesRegex(ValueError, 'Code window mismatch'): s.verify_contracts(bytes(256))

if __name__ == '__main__': unittest.main()
