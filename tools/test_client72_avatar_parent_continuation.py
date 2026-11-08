"""Independent boundaries, original native outputs and future-read scope checks."""
import copy
import hashlib
import json
import os
import struct
import unittest
from pathlib import Path

from tools import client72_avatar_parent_continuation as card


class MemoryEngine:
    """Only the external memory/register ABI used by the bounded storage model."""
    def __init__(self):
        self.uc=self;self.memory={};self.registers=dict(rsp=0x1000,rcx=0,rdx=0,r8=0)
        self.cursor=0x9000000100000000

    def reg(self,name):return self.registers[name]
    def mem_write(self,address,raw):self.memory.update({address+i:value for i,value in enumerate(raw)})
    def mem_read(self,address,width):return bytes(self.memory[address+i] for i in range(width))
    def write(self,address,value):self.mem_write(address,struct.pack('<Q',value))
    def read(self,address):return struct.unpack('<Q',self.mem_read(address,8))[0]
    def alloc(self,width):
        pointer=self.cursor;self.cursor+=width+16;self.mem_write(pointer,bytes(width));return pointer
    def call(self,caller,**registers):
        self.write(0x1000,caller);self.registers.update(registers)


class StorageBoundaryTests(unittest.TestCase):
    def test_exact_vector_allocation_and_high64_tail(self):
        e=MemoryEngine();storage=card.VectorStorage(e)
        e.call(0x1404e561b,rcx=8);pointer=storage.allocate()
        self.assertGreater(pointer,1<<63)
        self.assertEqual(e.mem_read(pointer,8),b'\xcc'*8)
        self.assertEqual(e.read(pointer+8),0xfedcba9876543210)
        for caller,width in ((0x1404e561a,8),(0x1404e561b,0),(0x1404e561b,9),(0x1404e561b,32)):
            e.call(caller,rcx=width)
            with self.subTest(caller=caller,width=width),self.assertRaises(ValueError):storage.allocate()

    def test_nonzero_copy_is_exact_and_rejects_unowned_or_excess_bytes(self):
        e=MemoryEngine();storage=card.VectorStorage(e)
        e.call(0x1404e561b,rcx=8);old=storage.allocate();e.write(old,0x8000000100000001)
        e.call(0x1404e561b,rcx=16);new=storage.allocate()
        e.call(0x1404e567f,rcx=new,rdx=old,r8=8);storage.copy()
        self.assertEqual(e.read(new),0x8000000100000001)
        self.assertEqual(e.read(new+16),card.CANARY)
        for caller,dst,src,width in ((0x1404e567e,new,old,8),(0x1404e567f,new,0xdead,8),
                                     (0x1404e567f,new+12,old,8),(0x1404e567f,new,old,16)):
            e.call(caller,rcx=dst,rdx=src,r8=width)
            with self.subTest(caller=caller,dst=dst,src=src,width=width),self.assertRaises(ValueError):storage.copy()

    def test_exact_free_rejects_size_drift_and_tail_corruption(self):
        e=MemoryEngine();storage=card.VectorStorage(e)
        e.call(0x1404e561b,rcx=8);pointer=storage.allocate()
        e.call(0x1404e56b4,rcx=pointer,rdx=16)
        with self.assertRaises(ValueError):storage.free()
        e.write(pointer+8,card.CANARY^1);e.call(0x1404e56b4,rcx=pointer,rdx=8)
        with self.assertRaisesRegex(ValueError,'tail changed'):storage.free()
        e.write(pointer+8,card.CANARY);storage.free();self.assertEqual(storage.live,{})
        with self.assertRaises(ValueError):storage.free()

    def test_pool16_has_exact_caller_owner_and_width(self):
        e=MemoryEngine();layer=card.ModelLayer(e,{'data':{'cold.registry.cell':struct.pack('<Q',0x123456789abcdef0)}})
        def rejected():raise ValueError('inherited callback rejected')
        callback=layer.wrap(0x1405091d0,rejected)
        e.call(0x1404eda7c,rcx=0x123456789abcdef0,rdx=16)
        pointer=callback();self.assertEqual(e.mem_read(pointer,16),b'\xcc'*16)
        for caller,owner,width in ((0x1404eda7b,0x123456789abcdef0,16),
                                   (0x1404eda7c,0,16),(0x1404eda7c,0x123456789abcdef0,8)):
            e.call(caller,rcx=owner,rdx=width)
            with self.subTest(caller=caller,owner=owner,width=width),self.assertRaises(ValueError):callback()

    def test_distinct_critical_extension_only_accepts_known_single_thread_sequence(self):
        e=MemoryEngine();layer=card.ModelLayer(e,{})
        critical=0x1111222233334444;class_lock=0xaaaabbbbccccdddd
        critical_stack=[class_lock,class_lock];critical_events=[]
        def enter_critical():
            retained=(critical,class_lock,critical_stack,critical_events)
            raise ValueError('old check rejected '+str(len(retained)))
        callback=layer.wrap(0x620001020,enter_critical)
        e.call(0x14051fba2,rcx=critical)
        with self.assertRaises(ValueError):callback()
        self.assertEqual(critical_stack,[class_lock,class_lock])
        e.call(0x14051fba3,rcx=0xdead)
        with self.assertRaises(ValueError):callback()
        e.call(0x14051fba3,rcx=critical);callback()
        self.assertEqual(critical_stack,[class_lock,class_lock,critical])
        self.assertEqual(layer.locks,[dict(caller='0x14051fba3',pointer=hex(critical),depth=3)])

    def test_fresh_descriptor_hash_and_kind_fail_before_native_execution(self):
        fresh=json.loads((card.REPO/card.FRESH).read_text('utf8'))
        old_source=json.loads((card.REPO/'local/migration72/d02-active/main/cold-arrays-tool/live-elevated1.json').read_text('utf8'))
        header=next(row for row in old_source['reads'] if row['label']=='cold.class1239.header')
        old={'data':{'cold.class1239.header':bytes.fromhex(header['bytesHex'])}}
        bad=copy.deepcopy(fresh);bad['status']='partial'
        with self.assertRaisesRegex(ValueError,'incomplete'):card.relocate_parent(bad,old,None,None,None,None)
        bad=copy.deepcopy(fresh);row=next(x for x in bad['reads'] if x['name']=='parent.descriptor');row['sha256']='0'*64
        with self.assertRaisesRegex(ValueError,'span changed'):card.relocate_parent(bad,old,None,None,None,None)
        bad=copy.deepcopy(fresh);row=next(x for x in bad['reads'] if x['name']=='parent.descriptor')
        raw=bytearray.fromhex(row['bytesHex']);raw[10]=18;row['bytesHex']=raw.hex();row['sha256']=hashlib.sha256(raw).hexdigest()
        with self.assertRaisesRegex(ValueError,'kind21'):card.relocate_parent(bad,old,None,None,None,None)


class ContinuationReportTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        path=Path(os.environ.get('CLIENT72_AVATAR_PARENT_CONTINUATION_REPORT',card.REPO/card.RAW_ROOT/'first.json'))
        cls.report=json.loads(path.read_text('utf8'));cls.cold=cls.report['cold']

    def test_static_parent_binding_is_not_a_cross_process_pointer_claim(self):
        actual=self.report['relocation']
        self.assertEqual((actual['kind'],actual['staticGenericIndex'],actual['instance'],actual['definition']),
                         (21,405416,54605,1234))
        self.assertFalse(actual['sameProcessReplay'])
        self.assertNotEqual(self.report['freshSHA256'],'63d3aaf80a37ca1f4f724a73ba2ee84354da94846acde16f27deccd116eef8c0')
        self.assertEqual(bytes.fromhex(actual['descriptorHex'])[4:8],bytes(4))

    def test_fixed_parameter_row_and_exact_two_byte_reader(self):
        row=self.report['genericParameter']
        self.assertEqual((row['index'],row['fileOffset'],row['width']),(366,'0x4fb25b8',14))
        self.assertEqual(row['sha256'],'a51d4766320e925c4dd7ee3105eb77f25a940b851075d483255b89cde829a113')
        reads=self.cold['models']['genericParameterReads']
        self.assertEqual(reads,[dict(address=hex(int(row['address'],16)+6),width=2,reader='0x1404eda27')])

    def test_native_continuation_has_a_real_missing_class_read_and_no_empty_error_success(self):
        last=self.cold['native'][-1]
        self.assertIsNone(last['stop'])
        self.assertEqual(last['failure'],'Uncaptured natural-address memory read')
        self.assertIsNone(last['error'])
        self.assertEqual(last['regs']['rip'],'0x15a4d6bf1')
        self.assertEqual(last['regs']['rcx'],'0x4d2')
        self.assertEqual(len(last['uncapturedReads']),1)
        self.assertEqual(last['uncapturedReads'][0]['size'],8)
        self.assertEqual(self.cold['firstMissing']['parameter'],1234)
        self.assertFalse(self.cold['firstMissing']['naturalAddressReusable'])
        self.assertEqual(self.cold['models']['callbackFailures'],[])

    def test_new_pool_wrote_all_sixteen_dirty_bytes(self):
        rows=self.cold['models']['pool16'];self.assertEqual(len(rows),1)
        self.assertEqual((rows[0]['width'],rows[0]['caller']),(16,'0x1404eda7c'))
        self.assertEqual(rows[0]['bytesHex'],self.report['relocation']['descriptorHex'])
        self.assertNotIn('cc',rows[0]['bytesHex'])

    def test_original_startup_producers_and_array_tail_remain_valid(self):
        cases=self.cold['native']
        self.assertEqual([x['producer']['index'] for x in cases if 'producer' in x],[0,2072,405417,411432])
        for row in cases:self.assertIn(row['tailCanary'],(None,'0xfedcba9876543210'))
        self.assertEqual(cases[-2]['regs']['rax'],'0x142377940')

    def test_original_native_vector_append_copy_free_and_uint64_values(self):
        first,second=self.report['vectorControls']
        self.assertEqual(first['values'],['0xfedcba9876543210'])
        self.assertEqual(second['values'],['0xfedcba9876543210','0x8000000100000001'])
        self.assertEqual((first['bytes'],first['capacity'],second['bytes'],second['capacity']),(8,8,16,16))
        self.assertEqual(second['tail'],'0xfedcba9876543210')
        self.assertEqual([(x['action'],x['width']) for x in second['events']],
                         [('allocate',16),('copy',8),('free',8)])

    def test_known_distinct_critical_section_is_released_but_class_lock_not_unwound(self):
        acquired=self.cold['models']['distinctCriticalAcquisitions']
        self.assertEqual(len(acquired),1);self.assertEqual(acquired[0]['caller'],'0x14051fba3')
        self.assertEqual(acquired[0]['depth'],3)
        self.assertEqual(len(self.cold['criticalAtStop']),2)
        self.assertEqual(self.cold['criticalAtStop'][0],self.cold['criticalAtStop'][1])
        self.assertNotIn(acquired[0]['pointer'],self.cold['criticalAtStop'])
        self.assertFalse(self.cold['sharedHeldAtStop'])
        self.assertFalse(self.cold['firstMissing']['unwindExecuted'])

    def test_generic_class_is_still_under_construction_and_no_default_was_written(self):
        self.assertEqual(len(self.cold['genericRecords']),1)
        raw=bytes.fromhex(self.cold['genericRecords'][0]['bytesHex'])
        self.assertEqual(struct.unpack_from('<I',raw)[0],1239)
        self.assertEqual(struct.unpack_from('<Q',raw,8)[0],0x142377940)
        self.assertEqual(raw[24:32],bytes(8))
        self.assertEqual([x['width'] for x in self.cold['newClasses']],[400])
        for name in ('completeColdComparer','defaultComparerWriteExecuted','completeAvatarHandler','targetProtocolReady','d02Complete'):
            self.assertFalse(self.report[name])

    def test_minimum_future_reads_use_only_metadata_proven_parent_types(self):
        contract=self.report['nextSamplingContract']
        self.assertEqual([(x['index'],x['name'],x['namespace']) for x in contract['parentLineage']],
                         [(1234,'EqualityComparer`1','System.Collections.Generic'),(371,'Object','System')])
        self.assertIsNone(contract['parentLineage'][1]['parent'])
        self.assertEqual([x['index'] for x in contract['classReads']],[1234,371])
        self.assertEqual([(x['index'],x['interfaceCount']) for x in contract['metadata']],[(1234,2),(371,0)])
        self.assertFalse(contract['deferredClassArrays']['requiredForCurrent1234ContinuationVerified'])
        self.assertFalse(contract['includesPoolContract']);self.assertFalse(contract['liveExecution'])
        budget=contract['budget']
        self.assertEqual((budget['classClosureAdditionalReads'],budget['classClosureAdditionalBytes']),(10,636))
        self.assertEqual((budget['totalWithMainline30Read1714ByteContract'],budget['totalBytesWithMainline30Read1714ByteContract']),(40,2350))

    def test_native_config_only_class_heads_with_exact_int32_static_identities(self):
        contract=self.report['nextSamplingContract']
        self.assertEqual(contract['nativeConfigStaticGenericIndices'],{'2147':[12184],'1255':[2089]})
        for row,owner in zip(contract['nativeConfigSourceFields'],(2147,1255)):
            self.assertEqual(row['type']['typeDefinition'],owner)
            self.assertEqual(row['type']['instanceIndex'],6)
            self.assertEqual([arg['kind'] for arg in row['type']['arguments']],['int32'])
        self.assertEqual([x['classHeaderBytes'] for x in contract['nativeConfigClassHeads']],[208,208])
        self.assertFalse(self.report['naturalNativeConfigTypedIdentityVerified'])
        self.assertFalse(self.report['naturalNativeConfigPredicateAtConsumptionVerified'])

    def test_exact_new_reader_and_import_windows_are_bound_to_original_bytes(self):
        windows={x['name']:x for x in self.report['nextSamplingContract']['additionalReaderWindows']}
        self.assertEqual(windows['definitionHeaderAndDeclaringDescriptorReader']['sha256'],
                         '7f004e2bc2bdc301f8fde1da759b3d3bc6b610dc013d334fbf4264959b22a3d2')
        self.assertEqual(windows['distinctCriticalEntry']['sha256'],
                         'afc163595caa6d7cb0a650a00384657367507a6bbcf2dbeead31aa03f1e33a41')
        for row in windows.values():
            self.assertEqual(hashlib.sha256(bytes.fromhex(row['bytesHex'])).hexdigest(),row['sha256'])


if __name__=='__main__':unittest.main()
