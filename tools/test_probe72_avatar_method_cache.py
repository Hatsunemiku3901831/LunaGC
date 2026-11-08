"""Independent native outcomes and exact-boundary collector regressions."""
import json
import os
from pathlib import Path
import struct
import unittest

from tools import probe72_avatar_method_cache as card


def fixture(*, generic_miss=False, method_miss=False, wrong_return=False, module=0x180000000):
    memory = card.SparseMemory()
    context, array, typ = 0x21000010000, 0x21000011000, 0x21000012000
    memory.add('canonical-context',context,struct.pack('<I4xQ',1,array))
    memory.add('canonical-array',array,struct.pack('<Q',typ))
    memory.add('canonical-type',typ,bytes.fromhex('5c4e0000000000000000110000000000'))
    for table, blocks in (('genericInst',0x22000000000),('method',0x23000000000)):
        for rva,value in zip(card.ROOTS[table],(127,blocks,2)):
            memory.add(table+'-root',module+rva,struct.pack('<Q',value))
        slots = {}
        if table == 'genericInst' and not generic_miss:
            slots[card.enum_instance_hash()&127] = struct.pack('<Q',context)
        if table == 'method' and not method_miss:
            for ordinal,index in enumerate(range(10287,10295)):
                # Build an ordinary sparse table using an independent triangular
                # search, so all eight keys and any collisions remain present.
                seed = card.method_hash(index,context,0)&127
                candidates = [(seed+n*(n+1)//2)&127 for n in range(128)]
                slot = next(candidate for candidate in candidates if candidate not in slots)
                descriptor, value = 0x24000000000+ordinal*4096,0x25000000000+ordinal*4096
                slots[slot] = struct.pack('<QQ',descriptor,value)
                memory.add('method-key',descriptor,struct.pack('<I4xQQ',index,context,0))
                raw = bytearray(b'\xa5'*56)
                struct.pack_into('<I',raw,24,index+1 if wrong_return else index)
                memory.add('method-value',value,raw)
        for block in (0,1):
            entries = sorted((slot,raw) for slot,raw in slots.items() if slot//64 == block)
            packed = blocks+0x10000+block*4096
            occupied = sum(1 << (slot&63) for slot,_ in entries)
            memory.add(table+'-block',blocks+block*32,struct.pack('<QQQ',packed,occupied,0))
            if entries:
                memory.add(table+'-packed',packed,b''.join(raw for _,raw in entries))
    return memory,context,module


class CollectorTests(unittest.TestCase):
    def test_all_eight_nonzero_high64_keys_values_and_exact_repeat(self):
        memory,context,module = fixture()
        report = card.collect(memory.read,module)
        self.assertEqual((report['status'],report['failure']),('completed',None))
        self.assertEqual(report['canonicalContext']['pointer'],context)
        self.assertEqual([row['returnedIndex'] for row in report['methods']],list(range(10287,10295)))
        self.assertTrue(all(row['status'] == 'hit' for row in report['methods']))
        first = report['inputConsistency']['firstPassReads']
        self.assertEqual(report['inputConsistency']['status'],'repeated-unchanged')
        self.assertLessEqual(report['budget']['reads'],first*2)
        self.assertGreater(report['budget']['reads'],first)
        self.assertEqual(max(row['requested'] for row in report['records']),56)
        self.assertTrue(report['candidateGraphOnly'])
        for key in ('atomicSnapshot','naturalLookupObserved','naturalCallerObserved','naturalWriterExcluded',
                    'completeColdComparer','completeAvatarHandler','targetProtocolReady','d02Complete'):
            self.assertFalse(report[key],key)

    def test_canonical_miss_has_no_method_reads_and_never_guesses_registered_pointer(self):
        memory,_,module = fixture(generic_miss=True)
        report = card.collect(memory.read,module)
        self.assertEqual(report['failure'],'canonical-context:miss')
        self.assertFalse(report['methods'])
        self.assertFalse(any(row['name'].startswith('method') for row in report['records']))
        self.assertIsNone(report['canonicalContext']['pointer'])

    def test_eight_observed_empty_states_are_misses_only(self):
        memory,_,module = fixture(method_miss=True)
        report = card.collect(memory.read,module)
        self.assertEqual(report['status'],'completed')
        self.assertEqual([row['status'] for row in report['methods']],['miss']*8)
        self.assertFalse(any('.value' in row['name'] for row in report['records']))
        self.assertFalse(report['completeClassInitialization'])

    def test_null_blocks_are_partial_without_reading_null(self):
        memory,_,module = fixture()
        address = module+card.ROOTS['genericInst'][1]
        memory.spans = [(n,a,bytes(8) if a == address else b) for n,a,b in memory.spans]
        report = card.collect(memory.read,module)
        self.assertEqual(report['failure'],'genericInst:blocks-null')
        self.assertFalse(any(row['address'] == 0 for row in report['records']))

    def test_zero_mask_is_valid_one_slot_not_null(self):
        memory = card.SparseMemory()
        module,context,blocks = 0x180000000,0x21000010000,0x22000000000
        for rva,value in zip(card.ROOTS['genericInst'],(0,blocks,0)):
            memory.add('root',module+rva,struct.pack('<Q',value))
        card.owned_context(memory,context)
        memory.add('block',blocks,struct.pack('<QQQ',blocks+4096,1,0))
        memory.add('entry',blocks+4096,struct.pack('<Q',context))
        ledger = card.Ledger(memory.read)
        result = card.lookup(ledger,'genericInst',card.roots(ledger,module,'genericInst','test'),
                             card.enum_instance_hash())
        self.assertEqual((result['status'],result['pointer']),('hit',context))

    def test_failed_short_and_oserror_have_no_retry_or_zero_substitute(self):
        for short in (True,False):
            memory,_,module = fixture()
            first = module+card.ROOTS['genericInst'][0]
            calls = []
            def read(address,width):
                calls.append((address,width))
                if address == first:
                    if short:
                        return b'\x7f',None
                    raise OSError(5,'owned fixture')
                return memory.read(address,width)
            report = card.collect(read,module)
            self.assertEqual(report['status'],'partial')
            self.assertEqual(calls,[(first,8)])
            self.assertEqual(report['records'][0]['bytesRead'],1 if short else 0)

    def test_in_place_span_change_with_unchanged_roots_is_rejected(self):
        memory,context,module = fixture()
        counts = {}
        def read(address,width):
            raw,error = memory.read(address,width)
            counts[(address,width)] = counts.get((address,width),0)+1
            if address == context and counts[(address,width)] > 1:
                return struct.pack('<I',2)+raw[4:],error
            return raw,error
        report = card.collect(read,module)
        self.assertEqual(report['status'],'partial')
        self.assertIn(report['failure'],('sample-spans-disagree','sample-span-changed'))
        self.assertEqual(report['inputConsistency']['status'],'failed')
        self.assertFalse(report['naturalLookupObserved'])

    def test_final_root_change_is_rejected(self):
        memory,_,module = fixture()
        address = module+card.ROOTS['method'][0]
        count = 0
        def read(pointer,width):
            nonlocal count
            if pointer == address:
                count += 1
                if count > 1:
                    return struct.pack('<Q',255),None
            return memory.read(pointer,width)
        report = card.collect(read,module)
        self.assertEqual((report['status'],report['failure']),('partial','method:roots-changed'))

    def test_returned_method_index_mismatch_stops_dependent_methods(self):
        memory,_,module = fixture(wrong_return=True)
        report = card.collect(memory.read,module)
        self.assertEqual(report['failure'],'method10287:returned-index-mismatch')
        self.assertEqual(len(report['methods']),1)

    def test_probe_cap_never_becomes_native_miss(self):
        memory = card.SparseMemory()
        block,entry,descriptor = 0x21000000000,0x21000001000,0x21000002000
        memory.add('block',block,struct.pack('<QQQ',entry,1,0))
        memory.add('entry',entry,struct.pack('<QQ',descriptor,0x300000001))
        memory.add('key',descriptor,struct.pack('<I4xQQ',10288,0,0))
        ledger = card.Ledger(memory.read)
        report = card.lookup(ledger,'method',(0,block,0),0,10287)
        self.assertEqual(report['status'],'probe-budget')
        self.assertEqual(len(report['path']),32)
        self.assertEqual(len(ledger.records),96)

    def test_context_null_mismatch_does_not_follow_unprovided_pointee(self):
        memory = card.SparseMemory()
        block,entry,descriptor = 0x21000000000,0x21000001000,0x21000002000
        memory.add('block',block,struct.pack('<QQQ',entry,1,0))
        memory.add('entry',entry,struct.pack('<QQ',descriptor,0x300000001))
        memory.add('key',descriptor,struct.pack('<I4xQQ',10287,0x7ff000001,0x8ff000001))
        result = card.lookup(card.Ledger(memory.read),'method',(1,block,0),0,10287)
        self.assertEqual(result['status'],'miss')
        self.assertEqual(len(result['path']),2)

    def test_type_attributes_ignored_but_kind_byref_and_data_are_compared(self):
        self.assertTrue(card.same_enum_type(struct.pack('<QII',20060,(17<<16)|0xffff,0xffffffff)))
        for raw in (struct.pack('<QII',20061,17<<16,0),struct.pack('<QII',20060,18<<16,0),
                    struct.pack('<QII',20060,(17<<16)|0x40000000,0)):
            self.assertFalse(card.same_enum_type(raw))

    def test_budget_is_complete_two_pass_bound(self):
        self.assertEqual((card.FIRST_PASS_READS,card.FIRST_PASS_BYTES),(1716,29472))
        self.assertEqual((card.MAX_READS,card.MAX_BYTES,card.MAX_SINGLE),(3432,58944,56))
        ledger = card.Ledger(lambda a,n:(bytes(n),None))
        with self.assertRaisesRegex(card.ReadStop,'budget'):
            ledger.get('oversize',0x1000,57)
        self.assertEqual(ledger.requested,0)
        ledger.requested = card.MAX_BYTES-7
        with self.assertRaises(card.ReadStop):
            ledger.get('sum',0x1000,8)
        ledger.requested = 0
        ledger.records = [None]*card.MAX_READS
        with self.assertRaises(card.ReadStop):
            ledger.get('count',0x1000,1)

    def test_bad_address_mask_and_occupied_null_are_errors(self):
        ledger = card.Ledger(lambda a,n:(bytes(n),None))
        with self.assertRaises(card.ReadStop):
            ledger.get('overflow',(1<<64)-4,8)
        self.assertEqual(ledger.records[-1]['error'],'address-outside-uint64')
        memory = card.SparseMemory()
        memory.add('block',0x21000000000,struct.pack('<QQQ',0,1,0))
        with self.assertRaisesRegex(card.ReadStop,'packed-null'):
            card.lookup(card.Ledger(memory.read),'method',(0,0x21000000000,0),0,10287)


class FrozenNativeTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        path = os.environ.get('CLIENT72_AVATAR_METHOD_CACHE_REPORT',str(card.RAW/'first.json'))
        cls.report = json.loads(Path(path).read_bytes())
        cls.cases = {row['name']:row for row in cls.report['nativeControls']}

    def test_exact_pins_and_new_window_budget(self):
        report = self.report
        self.assertEqual(report['sourceSHA256'],card.sha(Path(card.__file__).read_bytes()))
        self.assertEqual(report['inputSHA256'],card.prior.frozen.FIVE_INPUTS)
        self.assertEqual([row['start'] for row in report['functions'] if row['new']],
                         ['0x1404f0640','0x14052d590','0x140526fb0'])
        self.assertEqual(sum(row['width'] for row in report['functions'] if row['new']),2269)
        for name,expected in card.PINS.items():
            self.assertEqual(card.sha((card.ROOT/name).read_bytes()),expected)
        self.assertTrue(report['priorContinuousStopUnchanged'])
        self.assertEqual(report['suppliedNaturalMethodCacheBytes'],0)

    def test_independent_original_hash_outputs_include_signed32_and_high64(self):
        expected = {'hash-10287-2147483649':'0x95c187fe8d8962af',
                    'hash-4294967295-6442450945':'0x85c6b2582aac18dc',
                    'hash-2147483648-4294967297':'0x2fd54479249406d3',
                    'hash-0-0':'0xa1d6143a84762ab4'}
        for name,value in expected.items():
            row = self.cases[name]
            self.assertEqual(row['regs']['rdi'],value)
            self.assertEqual(row['expectedHash'],value)
            self.assertEqual(row['stop'],'0x1404eeb80')
        row = self.cases['registered-enum-original-type-hash']
        self.assertEqual(row['regs']['rax'],row['expectedTypeHash'])

    def test_native_hit_miss_and_probe_limit_follow_actual_machine_paths(self):
        for name in ('deep-alias','occupied-beyond-limit','tombstone-continue','hit-null'):
            self.assertEqual(self.cases[name]['stop'],'0x1404efb1c')
        for name in ('element-mismatch','count-mismatch','empty','tombstone-limit'):
            self.assertEqual(self.cases[name]['stop'],'0x14052d590')
        self.assertEqual(self.cases['hit-null']['regs']['rsi'],'0x0')
        collision = self.cases['occupied-beyond-limit']
        self.assertEqual(collision['regs']['r14'],'0x1')
        self.assertEqual([p['slot'] for p in collision['collector']['path']],[93,94])
        self.assertTrue(any(r['reader'] == '0x1404eedb3' for r in collision['externalReads']))
        self.assertEqual(self.cases['missing-root']['regs']['rip'],'0x1404eeb80')
        self.assertEqual(self.cases['unprovided-context']['regs']['rip'],'0x1404f06a0')
        self.assertEqual(self.cases['empty-no-intern']['stop'],'0x140526fb0')

    def test_native_context_lists_nulls_and_second_element_failure(self):
        rows = [row for name,row in self.cases.items() if name.startswith('context-')]
        self.assertEqual(len(rows),8)
        for row in rows:
            self.assertEqual(int(row['regs']['rax'],16)&255,row['expectedEqual'])
            self.assertEqual(row['regs']['rip'],hex(card.Native.RETURN))
        second = self.cases['context-second-method-element-mismatch']
        self.assertEqual(sum(r['reader'] == '0x1404f0700' for r in second['externalReads']),2)

    def test_native_rank63_and_next_block_bit0_read_only_selected_entry(self):
        rank = self.cases['rank63']
        self.assertEqual(rank['collector']['path'][0]['rank'],63)
        self.assertEqual(rank['collector']['path'][0]['slot'],63)
        self.assertEqual(rank['stop'],'0x1404efb1c')
        selected = [row for row in rank['externalReads'] if row['reader'] == '0x1404eec44']
        self.assertEqual([(row['address'],row['width']) for row in selected],[('0x310002003f0',8)])
        cross = self.cases['occupied-cross-block']
        self.assertEqual([row['slot'] for row in cross['collector']['path']],[63,64])
        self.assertEqual(cross['stop'],'0x1404efb1c')

    def test_no_native_external_writes_models_or_budget_escape(self):
        self.assertEqual(len(self.cases),27)
        for row in self.cases.values():
            self.assertFalse(row['externalWrites'])
            self.assertLess(row['steps'],row['instructionLimit'])
            self.assertEqual(row['instructionLimit'],2000000)
            self.assertFalse(row['naturalMemory'])
            if row['name'] not in ('missing-root','unprovided-context'):
                self.assertIsNone(row['failure'],row['name'])
                self.assertIsNone(row['error'],row['name'])

    def test_contract_natural_and_replay_boundaries_remain_false(self):
        contract = self.report['nextSamplingContract']
        self.assertEqual(contract['selectedMethodIndices'],list(range(10287,10295)))
        self.assertEqual(contract['budgets']['maxBytes'],58944)
        self.assertTrue(contract['samplingSpecificationComplete'])
        self.assertTrue(contract['readOnlyCollectorImplemented'])
        for name in ('samplingReady','liveAdapterImplemented','naturalSamplingStarted','fullMissMaterializationBudgetClaimed',
                     'inputSufficientForCompleteAvatar','completeAvatarHandler','defaultComparerWriteExecuted'):
            self.assertFalse(contract[name],name)
        self.assertIn('fresh canonical',contract['afterCaptureReplay']['addressBinding'])
        source = self.report['staticContextSource']
        self.assertEqual(source['registeredInstanceRVA'],'0x2377940')
        self.assertFalse(source['canonicalPointerObserved'])


if __name__ == '__main__':
    unittest.main()
