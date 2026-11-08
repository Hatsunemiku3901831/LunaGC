"""Independent source, native-control, epoch and first-runtime-gap assertions."""
import copy
import hashlib
import json
import os
import struct
import subprocess
import sys
import unittest
from pathlib import Path

from tools import client72_avatar_fresh_ancestor as card


class MemoryABI:
    def __init__(self):
        self.uc=self;self.memory={};self.registers=dict(rsp=0x1000,rcx=0,rdx=0,r8=0,r14=0)
        self.heap_cursor=0x9000000100000000

    def reg(self,name):
        return self.registers['rdx']&0xffffffff if name=='edx' else self.registers[name]

    def mem_write(self,address,raw):self.memory.update({address+i:value for i,value in enumerate(raw)})
    def mem_read(self,address,width):return bytes(self.memory[address+i] for i in range(width))
    def write(self,address,value,fmt='Q'):self.mem_write(address,struct.pack('<'+fmt,value))
    def read(self,address,fmt='Q'):return struct.unpack('<'+fmt,self.mem_read(address,struct.calcsize('<'+fmt)))[0]
    def alloc(self,width):
        pointer=self.heap_cursor;self.heap_cursor+=(width+15)&~15
        self.mem_write(pointer,bytes(width));return pointer
    def call(self,caller,**values):self.write(0x1000,caller);self.registers.update(values)


class BoundaryTests(unittest.TestCase):
    def interface_fixture(self,definition):
        e=MemoryABI();owner=0x8123456789abcdef;klass=0x3000;generic=0x4000
        e.write(klass+0x68,generic);e.write(generic,definition,'I')
        model=card.InterfaceStorage(e,{'data':{'cold.registry.cell':struct.pack('<Q',owner)}})
        model.classes[klass]=400
        e.registers['r14']=klass
        return e,model,owner

    def test_zero_byte_pool_interface_allocation_preserves_cursor_and_nonzero_tail(self):
        e,model,owner=self.interface_fixture(1239);pointer=e.heap_cursor
        e.write(pointer,0x8000000100000001)
        e.call(0x14051f3ce,rcx=owner,rdx=0)
        self.assertEqual(model.allocate(),pointer)
        self.assertEqual(e.heap_cursor,pointer)
        e.call(0x14051f3de,rcx=pointer,rdx=0,r8=0);model.clear()
        self.assertEqual(e.read(pointer),0x8000000100000001)
        self.assertEqual([row['width'] for row in model.events],[0,0])

    def test_nonzero_interface_array_has_exact_width_and_independent_tail(self):
        e,model,owner=self.interface_fixture(1234)
        e.call(0x14051f3ce,rcx=owner,rdx=16);pointer=model.allocate()
        self.assertGreater(pointer,1<<63)
        self.assertEqual(e.mem_read(pointer,16),b'\xcc'*16)
        for caller in (0x14051f3de,0x14051f3f8):
            e.call(caller,rcx=pointer,rdx=0,r8=16);model.clear()
            self.assertEqual(e.mem_read(pointer,16),bytes(16))
            self.assertEqual(e.read(pointer+16),0xfedcba9876543210)

    def test_interface_allocator_rejects_wrong_caller_owner_class_and_declaration_width(self):
        e,model,owner=self.interface_fixture(1234)
        cases=((0x14051f3cd,owner,16,0x3000),(0x14051f3ce,owner^1,16,0x3000),
               (0x14051f3ce,owner,8,0x3000),(0x14051f3ce,owner,0,0x3000),
               (0x14051f3ce,owner,16,0x3001))
        for caller,rcx,width,klass in cases:
            e.call(caller,rcx=rcx,rdx=width,r14=klass)
            with self.subTest(caller=caller,owner=rcx,width=width,klass=klass),self.assertRaises(ValueError):model.allocate()
        self.assertEqual(model.events,[])

    def test_interface_clear_rejects_unowned_bytes_size_value_and_tail_corruption(self):
        e,model,owner=self.interface_fixture(1234)
        e.call(0x14051f3ce,rcx=owner,rdx=16);pointer=model.allocate()
        for caller,p,n,value in ((0x14051f3dd,pointer,16,0),(0x14051f3de,pointer+8,16,0),
                                 (0x14051f3de,pointer,8,0),(0x14051f3de,pointer,16,1)):
            e.call(caller,rcx=p,rdx=value,r8=n)
            with self.subTest(caller=caller,pointer=p,width=n,value=value),self.assertRaises(ValueError):model.clear()
        e.write(pointer+16,card.CANARY^1);e.call(0x14051f3de,rcx=pointer,rdx=0,r8=16)
        with self.assertRaisesRegex(ValueError,'tail changed'):model.clear()

    def test_new_vector_cleanup_only_releases_the_owned_eight_byte_span(self):
        e=MemoryABI();model=card.FreshVectorStorage(e)
        e.call(0x1404e561b,rcx=8);pointer=model.allocate();e.write(pointer,0x8000000100000001)
        for caller,p,n in ((0x1404edbbe,pointer,8),(0x1404edbbf,pointer+8,8),(0x1404edbbf,pointer,16)):
            e.call(caller,rcx=p,rdx=n)
            with self.subTest(caller=caller,pointer=p,width=n),self.assertRaises(ValueError):model.free()
        e.write(pointer+8,card.CANARY^1);e.call(0x1404edbbf,rcx=pointer,rdx=8)
        with self.assertRaisesRegex(ValueError,'tail changed'):model.free()
        e.write(pointer+8,card.CANARY);model.free()
        self.assertEqual(e.read(pointer),0x8000000100000001)
        self.assertEqual(model.live,{})
        with self.assertRaises(ValueError):model.free()

    def test_overlapping_captured_spans_reject_a_changed_boundary_byte(self):
        snapshot={'reads':{},'data':{}}
        card.add_span(snapshot,'cold.a',0x8000000100000000,b'\x10\x20\x30\x40')
        card.add_span(snapshot,'cold.b',0x8000000100000002,b'\x30\x40\x80')
        with self.assertRaisesRegex(ValueError,'Conflicting'):card.add_span(snapshot,'cold.bad',0x8000000100000003,b'\x41')
        self.assertNotIn('cold.bad',snapshot['reads'])


class FreshIngressTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):cls.fresh=json.loads((card.REPO/card.FRESH).read_bytes())

    def changed_header(self,name,offset,raw):
        fresh=copy.deepcopy(self.fresh);row=next(x for x in fresh['reads'] if x['name']==name)
        value=bytearray.fromhex(row['bytesHex']);value[offset:offset+len(raw)]=raw
        row['bytesHex']=value.hex();row['sha256']=hashlib.sha256(value).hexdigest()
        return fresh

    def test_exact_partial_reason_is_accepted_without_losing_failed_contract_status(self):
        rows,data=card.validate_fresh(self.fresh)
        self.assertEqual((len(rows),sum(len(raw) for raw in data.values())),(36,2232))
        self.assertEqual(self.fresh['status'],'partial')
        self.assertEqual(int.from_bytes(data['ancestor.371.header'][16:24],'little'),371)
        self.assertEqual(data['ancestor.371.header'][26],28)

    def test_object_kind18_and_unrelated_class_kind28_are_both_rejected(self):
        for name,value in (('ancestor.371.header',18),('ancestor.1234.header',28),('parent.class1239.header',28)):
            with self.subTest(name=name,value=value),self.assertRaisesRegex(ValueError,'identity/kind'):
                card.validate_fresh(self.changed_header(name,26,bytes([value])))

    def test_object_identity_is_not_generalized_to_any_descriptor_data(self):
        fresh=self.changed_header('ancestor.371.header',16,(372).to_bytes(8,'little'))
        with self.assertRaisesRegex(ValueError,'identity/kind'):card.validate_fresh(fresh)

    def test_hash_error_unknown_status_and_nonzero_root_parent_are_rejected(self):
        bad=copy.deepcopy(self.fresh);bad['reads'][0]['sha256']='0'*64
        with self.assertRaisesRegex(ValueError,'span changed'):card.validate_fresh(bad)
        bad=copy.deepcopy(self.fresh);bad['status']='completed'
        with self.assertRaisesRegex(ValueError,'epoch/source/status'):card.validate_fresh(bad)
        bad=copy.deepcopy(self.fresh);bad['reads'][0]['error']='synthetic read error'
        with self.assertRaisesRegex(ValueError,'span changed'):card.validate_fresh(bad)
        bad=self.changed_header('ancestor.371.header',0xb0,(0x80000001).to_bytes(4,'little'))
        with self.assertRaisesRegex(ValueError,'compact offsets'):card.validate_fresh(bad)


class OutputGateTests(unittest.TestCase):
    def test_outside_raw_output_is_rejected_before_client_input_access(self):
        path=card.REPO/'local/migration72/d02-resume-20261108/r1-fresh-ancestor-output-rejected.json'
        self.assertFalse(path.exists())
        run=subprocess.run([sys.executable,'-m','tools.client72_avatar_fresh_ancestor',
            '--client','nonexistent-client-must-not-be-opened','--output',str(path)],
            cwd=card.REPO,capture_output=True,text=True)
        self.assertEqual(run.returncode,2)
        self.assertIn('dedicated r1-fresh-ancestor directory',run.stderr)
        self.assertNotIn('Traceback',run.stderr);self.assertFalse(path.exists())

    def test_existing_raw_is_rejected_without_modifying_it_or_accessing_client(self):
        path=card.REPO/card.RAW_ROOT/'probe6.json';before=hashlib.sha256(path.read_bytes()).hexdigest()
        run=subprocess.run([sys.executable,'-m','tools.client72_avatar_fresh_ancestor',
            '--client','nonexistent-client-must-not-be-opened','--output',str(path)],
            cwd=card.REPO,capture_output=True,text=True)
        self.assertEqual(run.returncode,2);self.assertIn('Existing raw/source output is frozen',run.stderr)
        self.assertNotIn('Traceback',run.stderr)
        self.assertEqual(hashlib.sha256(path.read_bytes()).hexdigest(),before)


class NativeReportTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        path=Path(os.environ.get('CLIENT72_AVATAR_FRESH_ANCESTOR_REPORT',card.REPO/card.RAW_ROOT/'first.json'))
        cls.report=json.loads(path.read_bytes());cls.cold=cls.report['cold'];cls.sources=cls.report['objectSources']

    def test_fixed_object_tag_dispatch_and_named_producer_are_independently_pinned(self):
        expected={'inflationTable':'8adc773c8dc549d86d7af68d1e41d08ae486a87a5f70adb1606478e1d92b892e',
                  'classTable':'ceb7bb6b6a0ad51e73270046bc46f585007c7ffef0ead72ac240a9cf13e82739',
                  'namedProducer':'047ce1848ac2ffa204b83eb5055f3c344954a60e55507caf360e814902741615',
                  'canonicalReader':'baa2d561413af54257e840a514325a5d7fd4431721dd92fc75768d30d05fb758',
                  'poolAllocator':'ac16fc03b8d01353be4d6c2764dee25a7c5511902458adbc2fcce829d27e06c8'}
        for key,digest in expected.items():
            row=self.sources['windows'][key]
            self.assertEqual(row['sha256'],digest)
            self.assertEqual(hashlib.sha256(bytes.fromhex(row['bytesHex'])).hexdigest(),digest)
        self.assertEqual((self.sources['typeDefinition'],self.sources['kind'],self.sources['fixedReferenceIndex']),(371,28,1))
        self.assertEqual(self.sources['strings'],['System','Object'])
        self.assertFalse(self.sources['new371MetadataRequiredByTheseBranches'])

    def test_original_kind28_ignores_high64_data_but_kind18_has_separate_lookup(self):
        controls=self.report['objectControls']
        self.assertEqual([row['data'] for row in controls['kind28']],['0x173','0xfedcba9876543210'])
        for row in controls['kind28']:
            self.assertEqual(row['classConversion']['rax'],'0x8000000100000001')
            self.assertIn('0x140517843',row['classConversion']['executed'])
            self.assertIn(dict(address='0x145bc95e0',width=8,reader='0x140517843'),row['classConversion']['reads'])
            self.assertEqual(row['inflation']['tail'],'0xfedcba9876543210')
            self.assertIn('0x1404edd40',row['inflation']['executed'])
        self.assertEqual(controls['kind18DistinctBranch']['stop'],'0x1405269f0')
        self.assertEqual(controls['kind18DistinctBranch']['rcx'],'0x173')
        self.assertEqual(controls['lockEvents'],['enter','leave','enter','leave'])

    def test_native_nullable_comparison_uses_all64_bits_and_both_branches(self):
        cases=self.report['objectControls']['nullableIdentityComparisons']
        self.assertEqual([row['trace']['stop'] for row in cases],['0x140517c50','0x140517c25'])
        a,b=(int(row['input'],16) for row in cases)
        self.assertEqual(a&0xffffffff,b&0xffffffff)
        self.assertNotEqual(a,b)
        model=self.sources['nullableIdentity']
        self.assertEqual((model['definition'],model['name']),(368,'Nullable`1'))
        self.assertFalse(model['observedCanonicalCell']);self.assertFalse(model['headerProvided'])
        before=self.report['beforeStaticIdentityBinding']
        self.assertEqual(before['native'][-1]['failure'],'Unbound runtime global cell read')
        self.assertIsNone(before['native'][-1]['error'])
        self.assertEqual([row for row in before['runtimeGlobalReads'] if not row['supplied']],
                         [dict(address='0x145bc96f8',width=8,reader='0x140517c1c',supplied=False)])

    def test_original_pool_zero_and_nonzero_cursor_semantics_preserve_payload(self):
        cases=self.report['objectControls']['poolFastPath']
        self.assertEqual([row['width'] for row in cases],[0,8,16])
        self.assertEqual(cases[0]['before'],cases[0]['after'])
        for row in cases:
            self.assertGreater(int(row['before'],16),1<<32)
            self.assertEqual(int(row['after'],16)-int(row['before'],16),row['width'])
            self.assertEqual(row['trace']['rax'],row['before'])
            self.assertTrue(row['originalPayloadPreserved'])

    def test_epoch_bridge_rewrites_only_metadata_base_and_does_not_claim_same_process(self):
        relocation=self.report['relocation']
        self.assertEqual(relocation['freshSHA256'],card.FRESH_SHA)
        self.assertNotEqual(relocation['freshSHA256'],relocation['oldSnapshotSHA256'])
        self.assertFalse(relocation['sameProcessReplay']);self.assertFalse(self.report['sameProcessReplay'])
        self.assertEqual([row['type'] for row in relocation['classProjections']],[1239,1234,371])
        self.assertEqual(relocation['staticParent'],dict(kind=21,index=405416,instance=54605,definition=1234))
        self.assertEqual(bytes.fromhex(relocation['objectParent']['descriptorHex']),struct.pack('<QII',371,28<<16,0))
        self.assertFalse(relocation['objectCanonicalBinding']['observedCanonicalCell'])

    def test_original_cold_producers_and_both_cached_class_publications_execute(self):
        self.assertEqual([row['producer']['index'] for row in self.cold['native'] if 'producer' in row],[0,2072,405417,411432])
        self.assertEqual(self.cold['native'][-2]['regs']['rax'],'0x142377940')
        self.assertEqual([row['width'] for row in self.cold['newClasses']],[400,400])
        for row,klass,definition in zip(self.cold['genericRecords'],self.cold['newClasses'],(1239,1234)):
            raw=bytes.fromhex(row['bytesHex'])
            self.assertEqual(struct.unpack_from('<I',raw)[0],definition)
            self.assertEqual(struct.unpack_from('<Q',raw,8)[0],0x142377940)
            self.assertEqual(struct.unpack_from('<Q',raw,24)[0],int(klass['pointer'],16))
        executed=self.cold['native'][-1]['trace']['executedAddresses']
        self.assertEqual(executed['0x140517c6f'],2)
        self.assertEqual(executed['0x140517843'],1)
        self.assertTrue(all(row['tailCanary'] in (None,'0xfedcba9876543210') for row in self.cold['native']))

    def test_interface_source_and_runtime_storage_are_exact_before_first_element(self):
        row=self.report['fixedInterfaceRows'][1]
        self.assertEqual((row['definition'],row['count'],row['first'],row['fileOffset']),(1234,2,535,'0x2974198'))
        self.assertEqual(row['referenceIndices'],[444353,448407])
        self.assertEqual(row['sha256'],'978411c87efa257ff172f38cf8133be49fc7f9e9bf8ddbc985893c54a51d6dc4')
        self.assertEqual([x['typeDefinition'] for x in row['interfaces']],[1169,1249])
        storage=self.cold['interfaceStorage']
        self.assertEqual([(x['action'],x['width']) for x in storage['events']],
                         [('allocate',0),('clear',0),('allocate',16),('clear',16),('clear',16)])
        self.assertEqual(storage['allocations'][0]['bytesHex'],'00'*16)
        self.assertEqual(storage['allocations'][0]['tail'],'0xfedcba9876543210')
        self.assertEqual(self.cold['models']['vectorEvents'][-1]['caller'],'0x1404edbbf')
        self.assertEqual(self.cold['models']['vectorsAtStop'],{})

    def test_real_runtime_frontier_is_cache1169_without_empty_error_or_false_completion(self):
        last=self.cold['native'][-1]
        self.assertEqual(last['failure'],'Uncaptured natural-address memory read')
        self.assertIsNone(last['error']);self.assertIsNone(last['stop'])
        self.assertEqual(last['regs']['rip'],'0x15a4d6bf1');self.assertEqual(last['regs']['rcx'],'0x491')
        self.assertEqual(last['uncapturedReads'],[dict(address='0x50000548c40',size=8,site='0x15a4d6bf1')])
        self.assertEqual(self.cold['models']['callbackFailures'],[])
        self.assertTrue(all(row['supplied'] for row in self.cold['runtimeGlobalReads']))
        self.assertEqual(len(self.cold['criticalAtStop']),1);self.assertFalse(self.cold['sharedHeldAtStop'])
        for name in ('completeClassInitialization','completeColdComparer','defaultComparerWriteExecuted',
                     'completeAvatarHandler','targetProtocolReady','d02Complete'):
            self.assertFalse(self.report[name])

    def test_next_contract_uses_two_proved_definitions_and_keeps_sampling_disabled(self):
        contract=self.report['nextSamplingContract']
        self.assertFalse(contract['liveExecution']);self.assertFalse(contract['includesPoolOrNativeConfig'])
        self.assertEqual(contract['firstMissing']['definition'],1169)
        self.assertEqual([(row['definition'],row['headerIdentity']['kind']) for row in contract['classes']],[(1169,18),(1249,18)])
        self.assertEqual([row['fixedReferenceIndex'] for row in contract['classes']],[444353,448461])
        self.assertEqual([row['metadata']['interfaceCount'] for row in contract['classes']],[0,0])
        self.assertTrue(all(row['parent'] is None for row in contract['classes']))
        budget=contract['mandatoryRuntimeBudget']
        self.assertEqual((budget['additionalCacheAndHeaderReads'],budget['additionalCacheAndHeaderBytes']),(4,432))
        self.assertEqual((budget['conservativeAdditionalMaxReads'],budget['conservativeAdditionalMaxBytes']),(10,636))
        self.assertEqual(contract['staticOnlyNextParameter']['index'],362)
        self.assertFalse(contract['staticOnlyNextParameter']['liveReadRequired'])

    def test_source_and_frozen_dependencies_are_bound_to_actual_bytes(self):
        self.assertEqual(self.report['sourceSHA256'],hashlib.sha256(Path(card.__file__).read_bytes()).hexdigest())
        for path,digest in self.report['sourcePins'].items():
            self.assertEqual(hashlib.sha256((card.REPO/path).read_bytes()).hexdigest(),digest,path)


if __name__=='__main__':unittest.main()
