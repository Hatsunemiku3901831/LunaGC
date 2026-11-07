import struct
import unittest
from tools.sample_client72_initialization import collect


class InitializationSnapshotTests(unittest.TestCase):
    def test_manager_filename_uses_actual_deque_slot_and_stops_at_first_nul(self):
        base = 0x140000000
        state = bytearray(0x68)
        struct.pack_into('<Q', state, 0x10, 0x2000)
        struct.pack_into('<Q', state, 0x18, 38)
        struct.pack_into('<Q', state, 0x58, 425)
        node = bytearray(0x30)
        struct.pack_into('<Q', node, 0, 0x2000)
        struct.pack_into('<I', node, 0x10, 0x80000002)
        descriptor = bytearray(0x30)
        for offset, value in ((0x10, 0x4000), (0x18, 8), (0x20, 15), (0x28, 20)):
            struct.pack_into('<Q', descriptor, offset, value)
        memory = {
            (base + 0x55bc4b8, 0x20): struct.pack('<4Q', 0, 0x1000, 0xdead, 0),
            (0x1000, 0x68): bytes(state), (0x2000, 16): struct.pack('<QQ', 0x3000, 0),
            (0x3000, 0x30): bytes(node), (base + 0x5756ec0 + 0xc0, 0x30): bytes(descriptor),
            (0x4000, 8): struct.pack('<Q', 0x5000), (0x5008, 8): struct.pack('<Q', 0x6000),
            (0x6000, 1): b'X', (0x6001, 1): b'\0',
        }
        calls = []
        def read(address, size):
            calls.append((address, size))
            if 0x6002 <= address < 0x6100:
                self.fail('Filename sampling continued beyond the first NUL')
            return memory.get((address, size), bytes(size)), None
        result = collect(read, base)
        row = next(row for row in result['reads'] if row['label'].endswith('.filename'))
        self.assertEqual(row['bytesHex'], '5800')
        self.assertTrue(row['terminated'])
        self.assertEqual(row['readUnitBytes'], 1)
        self.assertIn((0x5008, 8), calls)
        self.assertTrue(all(size <= 256 for _, size in calls))

    def test_decode_window_is_bounded_and_requires_complete_array_extent(self):
        base = 0x140000000
        calls = []
        length = 2047
        def read(address, size):
            calls.append((address, size))
            memory = {
                (base + 0x586b600, 8): struct.pack('<Q', 0x100000),
                (0x100000 + 0xe970, 8): struct.pack('<Q', 0x200000),
                (0x200018, 4): struct.pack('<I', length),
            }
            return memory.get((address, size), bytes(size)), None
        result = collect(read, base)
        self.assertFalse(any(row['label'].startswith('resourceDecode.quarter') for row in result['reads']))
        length = 2048
        result = collect(read, base)
        quarters = [row for row in result['reads'] if row['label'].startswith('resourceDecode.quarter')]
        self.assertEqual([(row['address'], row['length']) for row in quarters],
                         [(0x200420 + index * 256, 256) for index in range(4)])
        self.assertNotIn((0x200020, 1024), calls)

    def test_archive_scope_reads_only_the_sourced_cell_and_table(self):
        calls = []
        def read(address, size):
            calls.append((address, size))
            return (struct.pack('<Q', 0x9000), None) if len(calls) == 1 else (bytes(range(256)), None)
        result = collect(read, 0x140000000, 'archive-state')
        self.assertEqual(calls, [(0x145eed350, 8), (0x9000, 256)])
        self.assertFalse(result['archiveDecodeExecuted'])

    def test_occupied_bucket_is_read_even_with_tombstone_bit(self):
        base = 0x140000000
        pack = lambda value: struct.pack('<Q', value)
        descriptor = struct.pack('<QQ', 20060, 17 << 16)
        header = bytearray(0x68)
        struct.pack_into('<Q', header, 8, 3)
        struct.pack_into('<Q', header, 0x28, 0xa000)
        struct.pack_into('<Q', header, 0x60, 0xb000)
        memory = {
            (base + 0x5a30968, 8): pack(0x1000), (0x1000, 16): struct.pack('<QQ', 0x2000, 0),
            (0x2030, 8): pack(0x3000), (0x3010, 8): pack(0x4000),
            (0x4000, 16): struct.pack('<QQ', 0x5000, 0), (0x5030, 8): pack(0x6000),
            (0x6018, 8): pack(0x7000), (0x7000, 16): descriptor,
            (base + 0x5bc9068, 8): pack(0x9000), (0x9000, 0x68): bytes(header),
            (0xa000, 32): struct.pack('<QQQQ', 0xc000, 3, 2, 0),
            (0xc010, 16): struct.pack('<QQ', 0xd000, 1), (0xd000, 16): descriptor,
            (0xb008, 8): pack(0xe000), (0xe000, 0x20): bytes(0x20),
        }
        def read(address, size):
            return memory.get((address, size), bytes(size)), None
        result = collect(read, base)
        labels = {row['label']: row for row in result['reads']}
        self.assertEqual(labels['runtimeTypeCache.firstEntry']['address'], 0xc010)
        self.assertEqual(labels['runtimeTypeCache.firstValue']['address'], 0xb008)

    def test_missing_and_null_state_does_not_follow_descendants(self):
        calls = []
        def read(address, size):
            calls.append((address, size))
            return (bytes(size), None) if len(calls) != 2 else (b'', {'winerror': 299})
        result = collect(read, 0x140000000)
        self.assertEqual(len(calls), 14)
        self.assertIsNone(result['enumConstructorMatchesSharedBody'])
        self.assertIsNone(result['defaultComparerPointer'])
        self.assertFalse(result['naturalConstructorExecuted'])
        self.assertEqual(result['reads'][1]['error'], {'winerror': 299})

    def test_exact_shared_constructor_and_options_use_full_pointer(self):
        base = 0x7ff600000000
        memory = {
            (base + 0x5bc8ef0, 8): struct.pack('<Q', base + 0x22f9988),
            (base + 0x22f9988, 8): bytes.fromhex('0101000007000000'),
            (base + 0x5a30968, 8): struct.pack('<Q', 0x1000),
            (0x1000, 16): struct.pack('<QQ', 0x2000, 0x3000),
            (0x2030, 8): struct.pack('<Q', 0x4000),
            (0x4018, 8): struct.pack('<Q', 0x5000),
            (0x5000, 16): struct.pack('<QQ', 0x2000, base + 0x16fbc0b0),
        }
        def read(address, size):
            return (memory[(address, size)], None) if (address, size) in memory else (b'', 299)
        result = collect(read, base)
        self.assertTrue(result['optionsMatchFixedRecord'])
        self.assertTrue(result['enumConstructorMatchesSharedBody'])
        self.assertFalse(result['targetProtocolReady'])
        memory[(0x5000, 16)] = struct.pack('<QQ', 0x2000, base + 0x16fbc0b1)
        self.assertFalse(collect(read, base)['enumConstructorMatchesSharedBody'])

    def test_partial_method_read_stops_that_chain(self):
        calls = []
        def read(address, size):
            calls.append((address, size))
            if address == 0x140000000 + 0x5a30968:
                return struct.pack('<Q', 0x8000000000001000), None
            if address == 0x8000000000001000:
                return b'\x01\x02', {'winerror': 299, 'bytesRead': 2}
            return bytes(size), None
        result = collect(read, 0x140000000)
        self.assertIn((0x8000000000001000, 16), calls)
        self.assertEqual(len(calls), 15)
        self.assertEqual(result['runtimeTypeCacheResult'], 'header-not-readable-or-not-initialized')


if __name__ == '__main__':
    unittest.main()
