"""Independent typed32B bounds, fixed-input rejection and original-path evidence."""
import json
import os
from pathlib import Path
import struct
from types import SimpleNamespace
import unittest

from tools import continue72_avatar_class80_storage as card


class Memory:
    def __init__(self):self.bytes={};self.stopped=False
    def mem_write(self,address,raw):self.bytes.update({address+i:value for i,value in enumerate(raw)})
    def mem_read(self,address,width):return bytes(self.bytes[address+i] for i in range(width))
    def emu_stop(self):self.stopped=True


class Engine:
    def __init__(self):
        self.uc=Memory();self.failure=None;self.cursor=0x612000730
        self.klass,self.owner,self.template=0x500f0001000,0x17000001000,0x500010ea220
        self.hierarchy_storage=SimpleNamespace(klass=self.klass,published={'typed':True})
        self.extra_layer=SimpleNamespace(snapshot={'reads':{
            'cold.freshAncestor.class1234.header':dict(address=self.template)}})
        self.regs=dict(rdx=32,rcx=self.owner,r12=self.template,rbp=0x6000ffdd0,rip=0)
        self.write(0x145bc8ad0,self.owner);self.write(self.regs['rbp']+0x18,self.klass)
        self.write(self.klass+0x80,0);self.write(self.klass+0xc0,2,'H');self.write(self.template+0xc0,2,'H')
    def reg(self,name):return self.regs[name]
    def write(self,address,value,fmt='Q'):self.uc.mem_write(address,struct.pack('<'+fmt,value))
    def alloc(self,width):
        pointer=self.cursor;self.cursor+=(width+15)&~15;self.uc.mem_write(pointer,bytes(width));return pointer


def allocated():
    e=Engine();s=card.Class80Storage(e,dict(rows=[dict(signedOffset=4),dict(signedOffset=6)]))
    s.allocate(0x14051cb6d);return e,s


class StorageTests(unittest.TestCase):
    def test_dirty32_and_exact_nonzero8_tail(self):
        e,s=allocated();self.assertEqual(e.uc.mem_read(s.pointer,32),b'\xcc'*32)
        self.assertEqual(e.uc.mem_read(s.pointer+32,8),struct.pack('<Q',card.CANARY))
        self.assertNotIn(s.pointer+40,e.uc.bytes)
        self.assertEqual(e.uc.mem_read(e.klass+0x80,8),bytes(8))

    def test_width_and_caller_refused_before_allocation(self):
        for width,caller in ((0,0x14051cb6d),(16,0x14051cb6d),(24,0x14051cb6d),(48,0x14051cb6d),(32,0x14051cb6c)):
            e=Engine();e.regs['rdx']=width;s=card.Class80Storage(e,{})
            with self.subTest(width=width,caller=caller),self.assertRaises(ValueError):s.allocate(caller)
            self.assertIsNone(s.pointer);self.assertFalse(s.calls)

    def test_full64_owner_class_template_and_parent_publication(self):
        for kind in ('owner','class','template','parent'):
            e=Engine();s=card.Class80Storage(e,{})
            if kind=='owner':e.regs['rcx']+=1<<32
            elif kind=='class':e.write(e.regs['rbp']+0x18,e.klass+(1<<32))
            elif kind=='template':e.regs['r12']+=1<<32
            else:e.hierarchy_storage.published=None
            with self.subTest(kind=kind),self.assertRaises(ValueError):s.allocate(0x14051cb6d)
            self.assertFalse(s.calls)

    def test_initial_field_and_both_real_counts(self):
        for kind in ('nonnull','current-count','template-count'):
            e=Engine();s=card.Class80Storage(e,{})
            if kind=='nonnull':e.write(e.klass+0x80,0x8000000100000001)
            else:e.write((e.klass if kind=='current-count' else e.template)+0xc0,3,'H')
            with self.subTest(kind=kind),self.assertRaises(ValueError):s.allocate(0x14051cb6d)
            self.assertIsNone(s.pointer)

    def test_two_exact_clears_preserve_tail_and_forbid_reallocation(self):
        e,s=allocated();e.regs.update(rcx=s.pointer,edx=0,r8=32)
        for caller in (0x14051cb7d,0x14051cb8f):self.assertEqual(s.clear(caller),s.pointer)
        self.assertEqual(e.uc.mem_read(s.pointer,32),bytes(32));s.tail()
        with self.assertRaises(ValueError):s.clear(0x14051cb8f)
        with self.assertRaises(ValueError):s.allocate(0x14051cb6d)
        self.assertEqual(len(s.calls),3)

    def test_wrong_clear_order_pointer_width_value_and_tail(self):
        for kind in ('order','pointer','width','value','tail'):
            e,s=allocated();e.regs.update(rcx=s.pointer,edx=0,r8=32);caller=0x14051cb7d
            if kind=='order':caller=0x14051cb8f
            elif kind=='pointer':e.regs['rcx']+=16
            elif kind=='width':e.regs['r8']=40
            elif kind=='value':e.regs['edx']=1
            else:e.write(s.pointer+32,0)
            with self.subTest(kind=kind),self.assertRaises(ValueError):s.clear(caller)
            self.assertEqual(e.uc.mem_read(s.pointer,32),b'\xcc'*32)

    def test_only_actual_return_pointer_and_original_writers(self):
        e,s=allocated();s.class_results=[dict(ordinal=0,pointer=hex(0x500e0000000))]
        for address,width,value,writer in ((e.klass+0x80,8,s.pointer,0x14051cb93),
                (s.pointer+8,4,4,0x14051cc3e),(s.pointer,8,0x500e0000000,0x14051cbee)):
            s.native_write(e.uc,address,width,value,writer)
        self.assertFalse(e.uc.stopped);self.assertEqual(len(s.stores),3)
        self.assertEqual(e.uc.mem_read(e.klass+0x80,8),bytes(8))

    def test_tail_wide_store_wrong_writer_or_unobserved_return_stops(self):
        for offset,width,value,writer in ((32,8,0,0x14051cbee),(8,8,4,0x14051cc3e),
                (0,8,0x500e0000000,0x14051cbee),(8,4,6,0x14051cc3e),(24,4,6,0x14051cc3f)):
            e,s=allocated();s.native_write(e.uc,s.pointer+offset,width,value,writer)
            self.assertTrue(e.uc.stopped);self.assertTrue(s.rejected)

    def test_native_offset_sign_extension_is_dword(self):
        e,s=allocated();s.static['rows'][1]['signedOffset']=-32768
        s.native_write(e.uc,s.pointer+24,4,0xffff8000,0x14051cc3e)
        self.assertFalse(e.uc.stopped)

    def test_publication_readout_requires_original_field_and_zero_payload(self):
        e,s=allocated();e.regs.update(rcx=s.pointer,edx=0,r8=32)
        s.clear(0x14051cb7d);s.clear(0x14051cb8f)
        with self.assertRaisesRegex(ValueError,'publication'):s.observe(0x14051cb9a)
        e.write(e.klass+0x80,s.pointer);s.observe(0x14051cb9a)
        self.assertEqual(s.publication['writer'],'0x14051cb93')

    def test_cumulative_read_and_model_budgets(self):
        e,s=allocated();s.read_bytes=512
        with self.assertRaisesRegex(ValueError,'readout'):s.read(s.pointer,1,'negative')
        s.read_bytes=0;s.reads=[None]*64
        with self.assertRaisesRegex(ValueError,'readout'):s.read(s.pointer,1,'negative')
        s.calls=[None]*3
        with self.assertRaisesRegex(ValueError,'model-call'):s.call('negative',0)


class FixedInputsTests(unittest.TestCase):
    def setUp(self):
        self.e,self.s=allocated();self.start=0x1715130d460;self.row=0x1714d0c0976
        words=[0x60000f1e,0x60000f20,0x60000f21,0x60000f24,0x60002836,0x60002835,
            0x60002831,0x60002832,0,0,0x60002833,0x60002834]
        raw=struct.pack('<12I',*words);self.e.uc.mem_write(self.start,raw)
        rows=[dict(address=hex(self.row),bytesHex='c1c706000400'),dict(address=hex(self.row+6),bytesHex='e1710a000600')]
        self.e.uc.mem_write(self.row,bytes.fromhex('c1c706000400e1710a000600'))
        self.state=dict(metadataReads=[],vtableReads=[],metadataWriteAttempts=[])
        self.fixed=card.FixedMetadataInputs(self.e,self.s,rows,dict(address=hex(self.start),bytesHex=raw.hex()),self.state)
    def read(self,address,width,reader):
        self.e.regs['rip']=reader;self.fixed.read(self.e.uc,0,address,width,0,None)

    def test_two_sixbyte_rows_only_the_original_field_widths(self):
        for start in (self.row,self.row+6):
            self.read(start,4,0x14051cc35);self.read(start+4,2,0x14051cc39)
        self.assertFalse(self.e.uc.stopped);self.assertEqual(len(self.state['metadataReads']),4)

    def test_twelve_vtable_words_include_both_source_zeroes(self):
        for i in range(12):self.read(self.start+i*4,4,0x14051cd3a)
        self.assertFalse(self.e.uc.stopped)
        self.assertEqual([r['payload'] for r in self.state['vtableReads']],
            [3870,3872,3873,3876,10294,10293,10289,10290,0,0,10291,10292])
        self.read(self.start,4,0x14051cd3a);self.assertTrue(self.e.uc.stopped)

    def test_vtable_width_alignment_reader_and_neighbor_rejected(self):
        for offset,width,reader in ((0,8,0x14051cd3a),(1,4,0x14051cd3a),(0,4,0x14051cd3b),
                (-4,4,0x14051cd3a),(48,4,0x14051cd3a),(46,4,0x14051cd3a)):
            self.e.uc.stopped=False;self.read(self.start+offset,width,reader)
            self.assertTrue(self.e.uc.stopped);self.assertFalse(self.state['vtableReads'])

    def test_class80_row_width_reader_and_neighbor_rejected(self):
        for address,width,reader in ((self.row,6,0x14051cc35),(self.row,4,0x14051cc39),
                (self.row-1,1,0x14051cc35),(self.row+12,1,0x14051cc39)):
            self.e.uc.stopped=False;self.read(address,width,reader);self.assertTrue(self.e.uc.stopped)

    def test_changed_fixed_source_bytes_rejected(self):
        for address,reader in ((self.row,0x14051cc35),(self.start,0x14051cd3a)):
            self.e.uc.stopped=False;self.e.uc.mem_write(address,b'\xff');self.read(address,4,reader)
            self.assertTrue(self.e.uc.stopped)

    def test_any_static_page_write_is_rejected_without_substituting_bytes(self):
        for address in (self.start,self.start+48,self.row-1):
            self.e.uc.stopped=False;self.fixed.write(self.e.uc,0,address,1,0,None)
            self.assertTrue(self.e.uc.stopped)
        self.assertEqual(len(self.state['metadataWriteAttempts']),3)


class NativeReportTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        path=os.environ.get('CLIENT72_AVATAR_CLASS80_NATIVE_REPORT',str(card.RAW/'first.json'))
        cls.report=json.loads(Path(path).read_bytes());cls.c=cls.report['continuous']

    def test_current_source_and_fixed_pins(self):
        self.assertEqual(self.report['sourceSHA256'],card.prepared.sha(Path(card.__file__).read_bytes()))
        for path,expected in self.report['sourcePins'].items():
            self.assertEqual(card.prepared.sha((card.ROOT/path).read_bytes()),expected,path)

    def test_original_stores_two_actual_returns_and_no_success_inflation(self):
        s=self.c['class80Storage']
        self.assertEqual([x['callerReturn'] for x in s['calls']],['0x14051cb6d','0x14051cb7d','0x14051cb8f'])
        self.assertEqual([x['writer'] for x in s['originalStores']],
            ['0x14051cb93','0x14051cc3e','0x14051cbee','0x14051cc3e','0x14051cbee'])
        self.assertEqual([r['signedOffset'] for r in s['entries']],[4,6])
        self.assertEqual([r['paddingHex'] for r in s['entries']],['00000000']*2)
        self.assertTrue(self.c['class80InterfaceIdentityVerified'])
        self.assertEqual(s['publication']['tail'],hex(card.CANARY))
        self.assertEqual(len(s['actualInflationReturns']),2);self.assertEqual(len(s['actualClassReturns']),2)
        self.assertTrue(all(not x['returnModelSupplied'] for x in s['actualInflationReturns']+s['actualClassReturns']))
        self.assertFalse(s['rejected']);self.assertEqual(s['successfulInflationModelsSupplied'],0)

    def test_original_six_prefixes_and_seven_cumulative_call_limits(self):
        parent=json.loads((card.ROOT/card.prepared.PARENT).read_bytes())['continuous']
        self.assertEqual(len(self.c['native']),7)
        for current,old in zip(self.c['native'][:6],parent['native'][:6]):
            for k in ('name','failure','error','regs'):self.assertEqual(current[k],old[k])
            self.assertEqual(current['trace']['pathSHA256'],old['trace']['pathSHA256'])
        self.assertEqual([x['steps'] for x in self.c['extension']['invocations']], [13,13,13,13,185,466,7828])
        self.assertTrue(all(x['enforcedLimit']==2000000 for x in self.c['extension']['invocations']))

    def test_60_source_bytes_only_first_vtable_word_consumed_then_exact_root_gap(self):
        x=self.c['class80Extension'];v=x['vtableBinding'];last=self.c['native'][-1]
        self.assertEqual((v['fileOffset'],v['bytes'],v['count'],v['firstIndex']),('0x4b1d3d0',48,12,8489))
        self.assertEqual(v['sha256'],card.VTABLE_SHA)
        self.assertEqual(v['sourceFields']['countRawHex'],'0c00')
        self.assertEqual(len(x['metadataReads']),4);self.assertEqual(sum(r['width'] for r in x['metadataReads']),12)
        self.assertEqual([(r['ordinal'],r['payload']) for r in x['vtableReads']],[(0,3870)])
        self.assertEqual(x['ordinaryGetterEntries'],[dict(entry='0x140526fb0',callerReturn='0x14051cdc7',methodIndex=3870,modelSupplied=False)])
        self.assertEqual((last['failure'],last['error'],last['regs']['rip']),('Unbound runtime global cell read',None,'0x140526fc5'))
        self.assertEqual(self.c['runtimeGlobalReads'][-1],dict(address='0x145bc8f20',width=8,reader='0x140526fc5',supplied=False))
        self.assertFalse(x['invalidReads']);self.assertFalse(x['metadataWriteAttempts'])

    def test_cumulative_models_host_reads_and_new_native_windows(self):
        a,b=self.c['class80Storage']['budget'],self.c['cumulativeStorageBudget']
        self.assertEqual((a['modelCalls'],a['readoutCalls'],a['readoutBytes']),(3,22,188))
        self.assertEqual((b['modelCalls'],b['readoutCalls'],b['readoutBytes']),(7,64,521))
        self.assertEqual(a['readoutBytes'],sum(x['width'] for x in self.c['class80Storage']['hostReadout']))
        self.assertFalse(self.c['extension']['windowRequests']);self.assertFalse(self.c['models']['callbackFailures'])
        self.assertFalse(self.c['captureExtension']['capturedWriteAttempts']);self.assertFalse(self.c['captureExtension']['newUnknownReads'])

    def test_next_minimum_and_separate_warm_default_contract_are_not_runtime_claims(self):
        n=self.report['nextInputContract'];self.assertEqual((n['minimumNextInput']['moduleRVA'],n['minimumNextInput']['width']),('0x5bc8f20',8))
        natural=n['alternativeNaturalOwner'];a=natural['phaseA']
        self.assertEqual((a['firstPassReads'],a['firstPassBytes'],a['withExactSpanRepeatReads'],a['withExactSpanRepeatBytes']),(4,256,8,512))
        self.assertEqual(sum(x['width'] for x in a['newReads']),256)
        self.assertFalse(natural['samplingStarted']);self.assertFalse(natural['samplingAuthorizedByThisCard'])
        self.assertFalse(natural['completeAvatarInputContract']);self.assertFalse(natural['initializedOrLoadedAllAssumed'])
        self.assertTrue(self.report['completeClass80Rows']);self.assertTrue(self.report['sourceBoundControlledGraph'])
        for key in ('completeClassInitialization','defaultComparerWriteExecuted','completeAvatarHandler',
                'targetProtocolReady','d02Complete','atomicSnapshot','naturalLookupObserved','newInitializedOrLoadedAllFlagsSupplied'):
            self.assertFalse(self.report[key],key)


if __name__=='__main__':unittest.main()
