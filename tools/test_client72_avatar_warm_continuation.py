"""Independent readout/frontier checks for the frozen warm continuation."""
import json
import os
from pathlib import Path
import struct
import unittest

from . import client72_avatar_warm_continuation as subject


def record(address, raw, ordinal=1):
    return dict(address=address,requested=len(raw),bytesRead=len(raw),bytesHex=raw.hex(),error=None,ordinal=ordinal)


class ExactSourceTests(unittest.TestCase):
    def test_nonzero_uint64_and_exact_boundary(self):
        pointer = 0xfedcba9876543210
        memory = subject.ExactBytes([record(pointer,struct.pack('<Q',pointer))])
        self.assertEqual(memory.read(pointer,8),(struct.pack('<Q',pointer),None))
        self.assertEqual(memory.read(pointer+7,1),(b'\xfe',None))
        self.assertEqual(memory.read(pointer+8,1),(b'','unprovided-captured-byte'))

    def test_low32_collision_not_alias(self):
        memory = subject.ExactBytes([record(0x100000001,b'\x11'),record(0x200000001,b'\x22',2)])
        self.assertEqual(memory.read(0x100000001,1)[0],b'\x11')
        self.assertEqual(memory.read(0x200000001,1)[0],b'\x22')

    def test_short_source_rejected(self):
        row = record(0x1000,b'\x01\x02')
        row['requested'] = 3
        with self.assertRaisesRegex(ValueError,'short'):
            subject.ExactBytes([row])

    def test_error_source_rejected(self):
        row = record(0x1000,b'\x00')
        row['error'] = {'winerror':299}
        with self.assertRaises(ValueError):
            subject.ExactBytes([row])

    def test_overlap_disagreement_rejected(self):
        with self.assertRaisesRegex(ValueError,'overlap'):
            subject.ExactBytes([record(0x1000,b'\x01\x02'),record(0x1001,b'\x03',2)])

    def test_source_address_wrap_rejected(self):
        with self.assertRaises(ValueError):
            subject.ExactBytes([record((1<<64)-1,b'\x00\x00')])

    def test_unprovided_zero_is_not_source(self):
        memory = subject.ExactBytes([record(0x1000,b'\x00')])
        self.assertEqual(memory.lookup(0x1000,1)[:2],(b'\x00',None))
        self.assertEqual(memory.lookup(0x1001,1)[:2],(b'','unprovided-captured-byte'))

    def test_host_budget_exact_end(self):
        memory = subject.ExactBytes([])
        memory.requested = subject.MAX_BYTES-1
        memory.note('last',1,1,b'\x01',None,[])
        with self.assertRaisesRegex(ValueError,'budget'):
            memory.note('over',2,1,b'\x02',None,[])


class NativeReadoutTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        path = Path(os.environ.get('CLIENT72_AVATAR_WARM_CONTINUATION_REPORT',
            subject.OUT/'final-first.private.json'))
        cls.report = json.loads(path.read_bytes())
        cls.raw = json.loads((subject.ROOT/subject.CAPTURE).read_bytes())['collection']
        cls.cases = {row['name']:row for row in cls.report['native']}

    def test_current_input_temporal_projection_and_readback(self):
        self.assertTrue(self.report['inputAudit']['fullTemporalProjectionEqual'])
        self.assertEqual(self.report['inputAudit']['sourceCaptureSHA256'],
            'cfea3e67d6795e791c541e2c140db8b3f19cc70d6c571f47e7ce84c3eb5a4204')
        self.assertTrue(all(row['capturedReadbackEqual'] for row in self.cases.values()))
        self.assertEqual(len(self.report['rootAliases']),7)
        self.assertEqual(sum(row['purpose']=='native.final-readback' for row in self.report['hostBudget']['records']),910)

    def test_u32_constructor_fresh_and_nonzero_entire_storage(self):
        for name,fill in [('u32-hashset-fresh',0),('u32-hashset-overwrite-nonzero',0xa5)]:
            row = self.cases[name]
            self.assertTrue(row['returned'])
            self.assertEqual(row['executedAddresses'][-1],'0x157d97752')
            expected = bytearray([fill]*56)
            struct.pack_into('<Q',expected,0,self.raw['hashSetU32']['pointer'])
            struct.pack_into('<Q',expected,16,self.raw['u32Default']['default'])
            expected[40:56] = bytes.fromhex('0000000000000000ffffffff00000000')
            self.assertEqual(bytes.fromhex(row['storageReadout']['hashSet']),bytes(expected))
            self.assertEqual([x['writer'] for x in row['writes']],['0x157d9773e','0x157d97749','0x157d97749'])

    def test_null_this_takes_original_throw_boundary(self):
        row = self.cases['u32-hashset-null-this']
        self.assertFalse(row['returned'])
        self.assertEqual(row['stop'],'0x1404c2be0')
        self.assertEqual(row['writes'],[])

    def test_list_fragment_uses_actual_empty_pointer_without_array_body(self):
        row = self.cases['list-inline-real-empty-pointer']
        self.assertEqual(row['stop'],'0x15183fea1')
        storage = bytes.fromhex(row['storageReadout']['list'])
        avatar = bytes.fromhex(row['storageReadout']['avatar'])
        self.assertEqual(struct.unpack_from('<Q',storage,16)[0],self.raw['listEmptyItems']['pointer'])
        self.assertEqual(struct.unpack_from('<Q',avatar,0x70)[0],subject.LIST)
        self.assertEqual(len(row['writes']),2)
        self.assertFalse(self.raw['listEmptyItems']['arrayBodyRead'])

    def test_warm_getter_real_return_current_epoch(self):
        row = self.cases['enum-warm-getter-current-epoch']
        self.assertTrue(row['returned'])
        self.assertEqual(int(row['registers']['rax'],16),self.raw['enumWarm']['getter']['default'])
        self.assertEqual(row['executedAddresses'][-1],'0x157d7ff55')
        self.assertEqual(row['writes'],[])

    def test_complete_entry_stops_at_allocator(self):
        row = self.cases['avatar-entry-before-managed-allocator']
        self.assertEqual(row['stop'],'0x1405336c0')
        self.assertEqual(int(row['registers']['rcx'],16),self.raw['listU32']['pointer'])
        self.assertEqual([(x['writer'],x['width'],x['value']) for x in row['writes']],
            [('0x15183fe6c',4,'0x1'),('0x15183fe76',1,'0x1')])
        self.assertFalse(row['allocationExecuted'])

    def test_enum_zero_slots_are_lazy_and_first_missing_is_lock_root(self):
        wrapper = self.cases['enum-zero-slots-original-wrapper']
        avatar = self.cases['avatar-enum-lazy-original-fragment']
        for row in (wrapper,avatar):
            self.assertIn('0x140524010',row['executedAddresses'])
            self.assertEqual(row['stop'],'0x14052402c')
            self.assertEqual(row['missing'],[dict(address='0x145bc95a8',width=8,reader='0x14052402c',fetch=False)])
            self.assertEqual(row['writes'],[])
        self.assertIn('0x151840340',avatar['executedAddresses'])
        self.assertNotIn('0x157d7fe90',avatar['executedAddresses'])

    def test_inner_slots_stop_at_exact_uncaptured_definition_pointer(self):
        address = self.raw['enumCaller']['owner']['rgctx']+8
        for slot in (0,1):
            row = self.cases['enum-lazy-inner-slot'+str(slot)]
            self.assertEqual(row['stop'],'0x1404ee712')
            self.assertEqual(row['missing'][0]['address'],hex(address))
            self.assertEqual(row['missing'][0]['width'],8)
            self.assertEqual(row['writes'],[])
        self.assertEqual([(x['slot'],x['methodDefinition']) for x in
            self.report['selectedStaticSources']['hashSetDefinition2147Slots']],[(0,10288),(1,15591)])

    def test_string_actual_carrier_metadata_and_unchanged_primary(self):
        source = self.report['selectedStaticSources']
        self.assertEqual(source['dictionaryDefinition'],1222)
        rows = {row['moduleRVA']:row for row in source['concreteUsageRows']}
        self.assertEqual((rows['0x59ae108']['methodSpec'],rows['0x59ae108']['methodDefinition'],rows['0x59ae108']['classInstance']),
            (32569,10200,3744))
        self.assertEqual(rows['0x596c848']['methodDefinition'],10288)
        self.assertEqual([(x['slot'],x['methodDefinition']) for x in source['dictionaryDefinitionSlots']],[(2,10288),(13,10194)])
        instructions = {x['address']:x['text'] for x in source['originalStringCarrierChain']}
        self.assertEqual(instructions['0x158b48d02'],'mov rax, qword ptr [rax + 0x20]')
        self.assertEqual(instructions['0x158b48d12'],'call 0x15746c870')
        self.assertEqual(instructions['0x14aa9b8ef'],'mov r8, qword ptr [rax + 0x78]')

    def test_string_first_block_absence_does_not_become_miss(self):
        sparse = self.report['stringSparseAvailability']
        self.assertEqual((sparse['queryHash'],sparse['slot'],sparse['width']),('0x1b2af8ad5',232149,24))
        self.assertEqual(sparse['failure'],'unprovided-captured-byte')
        self.assertFalse(sparse['cacheMissProved'])
        self.assertFalse(sparse['actualLookupExecuted'])

    def test_string_contract_budget_and_partial_boundary(self):
        contract = self.report['stringConsumerInputContract']
        self.assertEqual((len(contract['nodes']),sum(row['width'] for row in contract['nodes'])),(11,440))
        budget = contract['budgets']
        self.assertEqual((budget['fullWarmSuccessorFirstPassReads'],budget['fullWarmSuccessorFirstPassBytes']),(27,1352))
        self.assertEqual((budget['combinedWithOldR1MainReads'],budget['combinedWithOldR1MainBytes']),(69,3648))
        self.assertFalse(budget['fitsOld64NewReadCap'])
        self.assertEqual(contract['nativeCalls'],0)
        self.assertFalse(contract['sparseProbeAutoFallback'])

    def test_no_parent_promotion_or_success_models(self):
        for key in ('managedAllocatorExecuted','defaultOriginalWriteExecuted','completeAvatarConstructor',
                'completeColdClassInitialization','completeAvatarHandler','g2Verified','d02Complete'):
            self.assertIs(self.report[key],False)
        self.assertTrue(self.report['selectedStaticSources']['ordinary10294NotSubstituted'])
        self.assertTrue(all(row['successModelsSupplied']==[] for row in self.cases.values()))
        self.assertEqual(self.report['newNativeWindows'],0)
        self.assertEqual(self.report['cpuBudget']['calls'],10)
        self.assertEqual(self.report['cpuBudget']['instructions'],158)


if __name__ == '__main__':
    unittest.main()
