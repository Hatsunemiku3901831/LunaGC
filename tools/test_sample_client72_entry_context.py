import tempfile
import unittest
import hashlib
import json
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
                klass = 0x4400000000 + n * 0x1000
                memory[obj] = u64(klass) + bytes([n + 1]) * (32 if n == 0 else 24)
                memory[klass] = bytes([n]) * 0xd0
            else:
                klass = 0x4400000000 + n * 0x1000
                memory[obj] = u64(klass)
                if n < 5:
                    memory[klass] = bytes([n]) * 0xd0
                    if n == 2:
                        header = bytearray(0xd0)
                        header[0x10:0x18] = u64(11008)
                        header[0x18:0x1c] = (18 << 16).to_bytes(4, 'little')
                        memory[klass] = bytes(header)
                        memory[klass + 0xf0] = u64(B + 0x70687c0)
        memory[B + 0x70687c0] = bytes.fromhex('4889d1803d414f80fe007405e9bf204af9e9ba254af9662e0f1f840000000000')
        memory[B + 0x586d70b] = b'\x01'
        memory[B + 0x5897b48] = u64(0x9900000000)
        memory[0x9900000000] = bytes(0xd0)
        table, klass, slot, registry = (0x5500000000, 0x6600000000, 0x7700000000, 0x8800000000)
        header = bytearray(0xd0)
        header[0x10:0x18] = u64(1239)
        header[0x18:0x1c] = (18 << 16).to_bytes(4, 'little')
        header[0xb0:0xb4] = (0x1234).to_bytes(4, 'little')
        memory.update({B + 0x5bc8f18: u64(table), table + 1239 * 8: u64(klass),
                       klass: bytes(header), B + 0x5bc8ad0: u64(slot), slot: u64(registry),
                       registry + 0x1234 + 0x10: bytes(range(16))})
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
        self.assertEqual((len(calls), sum(n for _, n in calls), max(n for _, n in calls)), (30, 1714, 208))
        self.assertEqual(result['parentDescriptorStatus'], 'read')
        self.assertEqual(result['modeValue'], 1)
        self.assertEqual(result['status'], 'completed')
        self.assertEqual(calls[2][0], R + 0x60e8)
        self.assertTrue(result['poolAllocateSlotObserved'])
        self.assertTrue(result['poolAllocateBindingVerified'])
        for name in ('completeCtorVerified', 'naturalRegistryVerified', 'worldInitializationVerified',
                     'sdkRequestVerified', 'targetProtocolReady'):
            self.assertFalse(result[name])
        self.assertNotIn('World', result)

    def test_null_root_and_mode_independent(self):
        memory = self.fixture(); memory[B + 0x586b600] = u64(0)
        result, calls = self.collect(memory)
        self.assertEqual(len(calls), 11)
        self.assertEqual(result['modeInterpretation'], 'true')

    def test_null_slot_and_class_stop(self):
        memory = self.fixture(); memory[R + 0x60e8] = u64(0)
        pool = int.from_bytes(memory[R + 0x5d60], 'little'); memory[pool] = u64(0)
        result, calls = self.collect(memory)
        self.assertEqual(len(calls), 25)
        self.assertNotIn('hashSet.header', [r['name'] for r in result['reads']])
        self.assertNotIn('pool.classHeader', [r['name'] for r in result['reads']])

    def test_partial_does_not_follow_and_is_partial(self):
        memory = self.fixture()
        result, calls = self.collect(memory, R + 0xc608)
        self.assertEqual(result['status'], 'partial')
        self.assertEqual(len(calls), 28)
        self.assertNotIn('registryStringToUint.klass', [r['name'] for r in result['reads']])

    def test_mode_unknown_and_failed(self):
        memory = self.fixture(); memory[B + 0x586ceb2] = b'\x02'
        result, _ = self.collect(memory)
        self.assertEqual((result['modeValue'], result['modeInterpretation']), (2, 'unknown'))
        result, _ = self.collect(memory, B + 0x586ceb2)
        self.assertIsNone(result['modeValue']); self.assertEqual(result['status'], 'partial')

    def test_pool_unknown_target_and_identity_do_not_follow(self):
        memory = self.fixture()
        klass = 0x4400002000
        memory[klass + 0xf0] = u64(0xfedcba9876543210)
        result, calls = self.collect(memory)
        self.assertTrue(result['poolAllocateSlotObserved'])
        self.assertFalse(result['poolAllocateBindingVerified'])
        self.assertNotIn((0xfedcba9876543210, 32), calls)
        self.assertNotIn((B + 0x70687c0, 32), calls)
        memory = self.fixture()
        header = bytearray(memory[klass]); header[0x10:0x18] = u64(11009)
        memory[klass] = bytes(header)
        result, calls = self.collect(memory)
        self.assertFalse(result['poolAllocateSlotObserved'])
        self.assertNotIn((klass + 0xf0, 8), calls)

    def test_pool_code_mismatch_and_class_read_failure_remain_unverified(self):
        memory = self.fixture(); memory[B + 0x70687c0] = bytes(32)
        result, _ = self.collect(memory)
        self.assertFalse(result['poolAllocateBindingVerified'])
        result, calls = self.collect(self.fixture(), 0x4400000000)
        self.assertEqual(result['status'], 'partial')
        self.assertFalse(result['completeCtorVerified'])

    def test_parent_chain_failures_do_not_follow(self):
        for address in (B + 0x5bc8f18, 0x5500000000 + 1239 * 8,
                        0x6600000000, B + 0x5bc8ad0, 0x7700000000,
                        0x8800000000 + 0x1234 + 0x10):
            with self.subTest(address=address):
                result, calls = self.collect(self.fixture(), address)
                self.assertEqual(result['status'], 'partial')
                self.assertIn(result['parentDescriptorStatus'], ('read-failed', 'descriptor-unreadable'))
                if address != 0x8800000000 + 0x1234 + 0x10:
                    self.assertNotIn((0x8800000000 + 0x1234 + 0x10, 16), calls)

    def test_parent_null_mismatch_and_zero_offset(self):
        memory = self.fixture(); memory[B + 0x5bc8f18] = u64(0)
        result, calls = self.collect(memory)
        self.assertEqual(result['parentDescriptorStatus'], 'class-cache-null')
        self.assertEqual(result['status'], 'partial')
        self.assertNotIn((0x8800000000 + 0x1234 + 0x10, 16), calls)
        for address, expected in ((0x5500000000 + 1239 * 8, 'class-entry-null'),
                                  (B + 0x5bc8ad0, 'registry-slot-null'),
                                  (0x7700000000, 'registry-base-null')):
            memory = self.fixture(); memory[address] = u64(0)
            result, calls = self.collect(memory)
            self.assertEqual(result['parentDescriptorStatus'], expected)
            self.assertEqual(result['status'], 'partial')
            self.assertNotIn((0x8800000000 + 0x1234 + 0x10, 16), calls)
        for start, value, expected in ((0x10, u64(999), 'class-identity-mismatch'),
                                       (0x18, (17 << 16).to_bytes(4, 'little'), 'class-identity-mismatch'),
                                       (0xb0, bytes(4), 'parent-offset-zero')):
            memory = self.fixture(); header = bytearray(memory[0x6600000000])
            header[start:start + len(value)] = value; memory[0x6600000000] = bytes(header)
            result, calls = self.collect(memory)
            self.assertEqual(result['parentDescriptorStatus'], expected)
            self.assertEqual(result['status'], 'partial')
            self.assertNotIn((0x8800000000 + 0x1234 + 0x10, 16), calls)

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

    def test_parent_and_pool_window_drift_rejected(self):
        native = b'parent-reader'
        fake_contract = json.dumps({'codeWindows': {str(i): {'rva': hex(i), 'bytes': len(native),
                                    'sha256': hashlib.sha256(native).hexdigest()} for i in range(16)}}).encode()
        scene = b'{"windows": []}'
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp); (root / 'config.json').write_bytes(fake_contract); (root / 'scene.json').write_bytes(scene)
            contracts = (('config.json', hashlib.sha256(fake_contract).hexdigest()),
                         ('scene.json', hashlib.sha256(scene).hexdigest()))
            class FakePE:
                base = 0
                def __init__(self, binary): pass
                def offset(self, address, size, executable=False): return 0
            good = ((100, len(native), hashlib.sha256(native).hexdigest()),)
            with patch.object(s, 'ROOT', root), patch.object(s, 'CONTRACTS', contracts), patch.object(s, 'PE', FakePE), patch.object(s, 'POOL_WINDOWS', ((101, len(native), hashlib.sha256(native).hexdigest()),)):
                with patch.object(s, 'PARENT_WINDOWS', good): self.assertEqual(s.verify_contracts(native), 18)
                with patch.object(s, 'PARENT_WINDOWS', ((100, len(native), '0' * 64),)):
                    with self.assertRaisesRegex(ValueError, 'Code window mismatch at RVA 0x64'):
                        s.verify_contracts(native)
                with patch.object(s, 'PARENT_WINDOWS', good), patch.object(s, 'POOL_WINDOWS', ((101, len(native), '0' * 64),)):
                    with self.assertRaisesRegex(ValueError, 'Code window mismatch at RVA 0x65'):
                        s.verify_contracts(native)

if __name__ == '__main__': unittest.main()
