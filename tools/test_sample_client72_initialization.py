import struct
import subprocess
import sys
import unittest
from pathlib import Path
from tools.sample_client72_initialization import collect


class InitializationSnapshotTests(unittest.TestCase):
    def test_output_scope_guard_rejects_cross_scope_before_process_access(self):
        root=Path(__file__).resolve().parents[1]; script=root/'tools/sample_client72_initialization.py'
        for scope,output in [('cold-arrays','local/migration72/d02-active/plain/rejected-cold.json'),
                             ('archive-state','local/migration72/d02-active/main/cold-arrays-tool/rejected-archive.json')]:
            run=subprocess.run([sys.executable,str(script),'--observation','unused','--scope',scope,'--output',output],capture_output=True,text=True,cwd=root)
            self.assertEqual(run.returncode,2)
            self.assertIn('selected scope local directory',run.stderr)
    def test_cold_scope_reads_only_bounded_cells_when_null(self):
        calls = []
        def read(address, size):
            calls.append((address, size)); return bytes(size), None
        result = collect(read, 0x7ff600000000, 'cold-arrays')
        self.assertEqual(result['scope'], 'cold-arrays')
        self.assertFalse(result['atomicSnapshot'])
        self.assertFalse(result['targetProtocolReady'])
        self.assertEqual(result['typeArrayCacheResult'], 'type314-header-unknown')
        self.assertTrue(all(size == 8 for _, size in calls))
        self.assertEqual(len(calls), 10)
        self.assertFalse(any(row['label'].startswith(('fileRouter', 'archiveDecode')) for row in result['reads']))

    def test_cold_first_bucket_uses_two_pointers_and_retains_tombstone(self):
        base=0x7ff600000000; table=0x2000000000; key=0x3000000000; value=0x4000000000
        blocks=0x5000000000; packed=0x6000000000; meta=0x7000000000; interfaces=0x8000000000; hierarchy=0x9000000000
        q=lambda x:struct.pack('<Q',x)
        header=bytearray(0xd0); struct.pack_into('<I',header,0x10,314);struct.pack_into('<I',header,0x18,18<<16)
        for off,val in ((0x48,hierarchy),(0x58,meta),(0x88,interfaces)):struct.pack_into('<Q',header,off,val)
        header[0xc4]=2
        row=bytearray(70); row[0x43]=0x25^3
        memory={(base+0x5bc8f18,8):q(table),(table+314*8,8):q(key),(key,0xd0):bytes(header),
                (meta,70):bytes(row),(interfaces,24):q(1)+q(2)+q(3),(hierarchy,16):q(4)+q(5),
                (base+0x5bc7160,8):q(63),(base+0x5bc7180,8):q(blocks),(base+0x5bc7188,8):q(64),
                (blocks,32):struct.pack('<QQQQ',packed,(1<<1)|(1<<4)|(1<<60),1<<60,0),
                (packed+32,16):struct.pack('<QQ',key,value),(value,0xd0):bytes(0xd0)}
        calls=[]
        def read(address,size):calls.append((address,size));return memory.get((address,size),bytes(size)),None
        result=collect(read,base,'cold-arrays'); labels={r['label']:r for r in result['reads']}
        self.assertEqual(labels['cold.typeArrayCache.firstEntry']['address'],packed+32)
        self.assertEqual(labels['cold.typeArrayCache.firstKey.header']['address'],key)
        self.assertEqual(labels['cold.typeArrayCache.firstValue.header']['address'],value)
        self.assertTrue(result['typeArrayCandidateTombstone'])
        self.assertTrue(result['typeArrayCandidateKeyMatches'])
        self.assertEqual(result['typeArrayCacheResult'],'first-candidate-recorded-not-a-complete-lookup')
        self.assertEqual(result['classSampling']['cold.class314']['interfaces'],'read')
        self.assertEqual(result['classSampling']['cold.class314']['hierarchy'],'read')
        self.assertEqual(labels['cold.class314.metadata']['length'],70)
        self.assertNotIn((meta,112),calls)
        self.assertTrue(all(size<=256 for _,size in calls))
        self.assertNotIn((value+0x58,8),calls)

    def test_cold_partial_and_bounds_do_not_follow_children(self):
        base=0x140000000;table=0x100000000; key=0x200000000;meta=0x300000000
        q=lambda x:struct.pack('<Q',x)
        header=bytearray(0xd0);struct.pack_into('<Q',header,0x58,meta);struct.pack_into('<Q',header,0x88,0x400000000);struct.pack_into('<Q',header,0x48,0x500000000);header[0xc4]=17
        row=bytearray(70);row[0x43]=0x25^17
        memory={(base+0x5bc8f18,8):q(table),(table+117*8,8):q(key),(key,0xd0):bytes(header),(meta,70):bytes(row),
                (table+314*8,8):q(0x600000000)}
        calls=[]
        def read(address,size):
            calls.append((address,size))
            if address==0x600000000:return b'\x01\x02',{'winerror':299,'bytesRead':2}
            return memory.get((address,size),bytes(size)),None
        result=collect(read,base,'cold-arrays'); states=result['classSampling']
        self.assertEqual(states['cold.class117']['interfaces'],'over-limit-unknown')
        self.assertEqual(states['cold.class117']['hierarchy'],'over-limit-unknown')
        self.assertEqual(states['cold.class314']['status'],'header-unreadable')
        self.assertNotIn((0x400000000,136),calls);self.assertNotIn((0x500000000,136),calls)
        self.assertEqual(result['typeArrayCacheResult'],'type314-header-unknown')
        self.assertFalse(any(r['label'].startswith('cold.typeArrayCache.first') for r in result['reads']))

    def test_cold_sdk_entry_gate_and_exact_root_bounds(self):
        base=0x140000000;sdk=0x7ffa00000000;engine=0x200000000;pool=0x300000000;aggregate=0x400000000
        q=lambda x:struct.pack('<Q',x)
        entry=bytes.fromhex('895424104c894424184c894c24204883ec284885c975088d412b4883c428c34c8d442440e8879d00004883c428c3')
        memory={(sdk+0x41e6f0,46):entry,(sdk+0x7acd88,8):q(engine),(engine,16):q(sdk+0x6bf300)+q(5),
                (engine+0x70,8):q(pool),(pool+0x70,32):struct.pack('<4Q',0x100,4,2,1),
                (sdk+0x7acc80,8):q(aggregate),(aggregate,16):q(0x555000000)+q(4)}
        calls=[]
        def read(address,size):calls.append((address,size));return memory.get((address,size),bytes(size)),None
        result=collect(read,base,'cold-arrays',sdk)
        self.assertEqual(result['sdkResult'],'roots-sampled-not-a-request')
        self.assertTrue(result['sdkRoots']['engine']['vtableMatches'])
        self.assertNotIn('vtableMatches',result['sdkRoots']['aggregate'])
        self.assertIn((pool+0x70,32),calls)
        self.assertNotIn((0x555000000,16),calls)
        calls.clear();memory[(engine,16)]=q(sdk+0x6bf301)+q(5)
        result=collect(read,base,'cold-arrays',sdk)
        self.assertFalse(result['sdkRoots']['engine']['vtableMatches'])
        self.assertNotIn((engine+0x70,8),calls)
        calls.clear();memory[(sdk+0x41e6f0,46)]=b'\x00'*46
        self.assertEqual(collect(read,base,'cold-arrays',sdk)['sdkResult'],'entry-mismatch')
        self.assertNotIn((sdk+0x7acd88,8),calls)

    def test_cold_default_classes_and_underlying_are_bounded(self):
        base=0x140000000;table=0x200000000; enum=0x300000000; runtime=0x400000000
        slot=0x500000000; registry=0x600000000; defaults=0x700000000
        q=lambda x:struct.pack('<Q',x)
        enum_header=bytearray(0xd0);struct.pack_into('<I',enum_header,0xac,0x250)
        memory={(base+0x5bc8f18,8):q(table),(table+20060*8,8):q(enum),(enum,0xd0):bytes(enum_header),
                (table+284*8,8):q(runtime),(runtime,0xd0):bytes(0xd0),
                (runtime+0x178,8):q(0x123),(runtime+0x360,8):q(0x456),
                (base+0x5bc8ad0,8):q(slot),(slot,8):q(registry),(registry+0x250,0xd0):bytes(0xd0),
                (base+0x5bc9868,8):q(defaults),(defaults,0xd0):bytes(0xd0)}
        calls=[]
        def read(address,size):calls.append((address,size));return memory.get((address,size),bytes(size)),None
        result=collect(read,base,'cold-arrays')
        self.assertIn((registry+0x250,0xd0),calls)
        self.assertIn((runtime+0x178,8),calls);self.assertIn((runtime+0x360,8),calls)
        self.assertIn((defaults,0xd0),calls)
        self.assertEqual(result['classSampling']['cold.enumUnderlying']['status'],'header-only')
        self.assertNotIn((registry+0x250+0x58,8),calls)

    def test_cold_mismatch_or_unreadable_entry_never_invents_hit(self):
        base=0x140000000;table=0x200000000;key=0x300000000;blocks=0x400000000;packed=0x500000000
        q=lambda x:struct.pack('<Q',x)
        header=bytearray(0xd0);struct.pack_into('<I',header,0x10,314);struct.pack_into('<I',header,0x18,18<<16)
        memory={(base+0x5bc8f18,8):q(table),(table+314*8,8):q(key),(key,0xd0):bytes(header),
                (base+0x5bc7160,8):q(63),(base+0x5bc7180,8):q(blocks),(base+0x5bc7188,8):q(64),
                (blocks,32):struct.pack('<QQQQ',packed,1<<60,0,0),(packed,16):struct.pack('<QQ',key+1,0x700000000)}
        def read(address,size):return memory.get((address,size),bytes(size)),None
        self.assertEqual(collect(read,base,'cold-arrays')['typeArrayCacheResult'],'first-candidate-mismatch-unknown')
        struct.pack_into('<I',header,0x18,17<<16);memory[(key,0xd0)]=bytes(header)
        self.assertEqual(collect(read,base,'cold-arrays')['typeArrayCacheResult'],'type314-descriptor-mismatch')

    def test_cold_partial_entry_and_null_packed_stop_before_pointers(self):
        base=0x140000000;table=0x200000000;key=0x300000000;blocks=0x400000000;packed=0x500000000
        q=lambda x:struct.pack('<Q',x)
        hdr=bytearray(0xd0);struct.pack_into('<I',hdr,0x10,314);struct.pack_into('<I',hdr,0x18,18<<16)
        memory={(base+0x5bc8f18,8):q(table),(table+314*8,8):q(key),(key,0xd0):bytes(hdr),
                (base+0x5bc7160,8):q(63),(base+0x5bc7180,8):q(blocks),(base+0x5bc7188,8):q(64),
                (blocks,32):struct.pack('<QQQQ',packed,1<<60,0,0)}
        calls=[]
        def read(address,size):
            calls.append((address,size))
            if address==packed:return b'\x01'*8,{'winerror':299,'bytesRead':8}
            return memory.get((address,size),bytes(size)),None
        result=collect(read,base,'cold-arrays')
        self.assertEqual(result['typeArrayCacheResult'],'first-entry-unreadable')
        self.assertFalse(any(r['label'].startswith('cold.typeArrayCache.firstKey') for r in result['reads']))
        memory[(blocks,32)]=struct.pack('<QQQQ',0,1<<60,0,0);calls.clear()
        self.assertEqual(collect(read,base,'cold-arrays')['typeArrayCacheResult'],'first-packed-entries-null')
        self.assertNotIn((packed,16),calls)
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
