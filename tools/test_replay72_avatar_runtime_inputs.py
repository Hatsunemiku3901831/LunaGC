"""Exact source/alias/guard rejection and actual warm/cold native evidence."""
import copy
import json
import os
from pathlib import Path
import struct
from types import SimpleNamespace
import unittest

from tools import replay72_avatar_runtime_inputs as card


def row(address,raw,ordinal=1):
    return dict(address=address,requested=len(raw),bytesRead=len(raw),bytesHex=raw.hex(),error=None,ordinal=ordinal)


class FakeUC:
    def __init__(self,values=None):self.values=values or {};self.stopped=False;self.callbacks={};self.reads=[]
    def emu_stop(self):self.stopped=True
    def mem_read(self,address,width):
        self.reads.append((address,width));return bytes(self.values.get(address+i,0) for i in range(width))
    def hook_add(self,kind,callback):self.callbacks[kind]=callback;return kind
    def hook_del(self,hook):pass


def alias_memory():
    module=0x7ff700000000
    rvas=[0x5bc8c18,0x5bc8c38,0x5bc8c40,0x5bc7290,0x5bc72b0,0x5bc72b8,0x5bc8f20]
    values=[127,0x8000000100000011,2,127,0x9000000200000022,2,0x500005f5c58]
    records=[row(module+rva,struct.pack('<Q',value),i+1) for i,(rva,value) in enumerate(zip(rvas,values))]
    memory=card.ExactMemory(records,card.HostBudget());return module,memory


class ExactMemoryTests(unittest.TestCase):
    def test_nonzero64bit_actual208_bytes_and_origins_are_preserved(self):
        address=0x8000000100000001;raw=bytes(range(208));m=card.ExactMemory([row(address,raw)],card.HostBudget())
        actual,error,origins=m.lookup(address+16,16)
        self.assertEqual((actual,error,origins),(raw[16:32],None,[1]));self.assertEqual(m.values[address],raw[0])

    def test_contradictory_overlap_and_short_error_or_oversize_source_rejected(self):
        with self.assertRaisesRegex(ValueError,'overlap'):
            card.ExactMemory([row(0x1000,b'abcdefgh'),row(0x1004,b'Xfgh',2)],card.HostBudget())
        for kind in ('short','error','oversize'):
            r=row(0x1000,bytes(209 if kind=='oversize' else 8))
            if kind=='short':r['bytesRead']=7
            if kind=='error':r['error']=299
            with self.subTest(kind=kind),self.assertRaises(ValueError):card.ExactMemory([r],card.HostBudget())

    def test_hole_on_mapped_page_has_no_zero_fallback_and_no_uc_read(self):
        m=card.ExactMemory([row(0x1000,b'abcd'),row(0x1008,b'ijkl',2)],card.HostBudget());uc=FakeUC()
        out=m.audit_uc(uc,0x1000,12,'negative',0x140000000)
        self.assertEqual(out['error'],'unprovided-captured-byte');self.assertFalse(uc.reads)

    def test_emulator_drift_is_retained_and_rejected(self):
        m=card.ExactMemory([row(0x1000,b'abcdefgh')],card.HostBudget());uc=FakeUC({0x1000+i:v for i,v in enumerate(b'abcXefgh')})
        out=m.audit_uc(uc,0x1000,8,'negative',0x140000000)
        self.assertEqual(out['error'],'captured-emulator-bytes-differ');self.assertEqual(out['bytesHex'],b'abcXefgh'.hex())

    def test_only_seven_root_storage_aliases_and_no_pointer_rewrite(self):
        module,m=alias_memory();before=dict(m.values);aliases=m.add_root_aliases(module)
        self.assertEqual(len(aliases),7)
        self.assertEqual({int(x['rva'],16) for x in aliases},{0x5bc8c18,0x5bc8c38,0x5bc8c40,0x5bc7290,0x5bc72b0,0x5bc72b8,0x5bc8f20})
        for a in aliases:
            original,target=int(a['capturedAddress'],16),int(a['nativeCell'],16)
            self.assertEqual(m.lookup(target,8)[0],bytes(before[original+i] for i in range(8)))
        self.assertTrue(all(m.values[a]==value for a,value in before.items()))

    def test_conflicting_preferred_root_alias_is_rejected(self):
        module,m=alias_memory();m.add(0x145bc8c18,bytes(8),{99})
        with self.assertRaisesRegex(ValueError,'overlap'):m.add_root_aliases(module)

    def test_old_source_memory_still_rejects208_and_is_not_patched(self):
        r=dict(address=0x1000,requested=208,bytesRead=208,bytesHex=bytes(208).hex(),error=None)
        with self.assertRaises(ValueError):card.class80.hierarchy.replay.SourceMemory([r])
        self.assertEqual(card.class80.hierarchy.replay.SourceMemory.__name__,'SourceMemory')

    def test_host_read_count_bytes_last_item_and208_limit_are_enforced(self):
        budget=card.HostBudget();budget.records=[None]*(card.MAX_HOST_READS-1);budget.requested=card.MAX_HOST_BYTES-208
        budget.record('edge',0x1000,208,bytes(208))
        with self.assertRaisesRegex(ValueError,'budget'):budget.record('over',0x2000,1,b'')
        self.assertEqual((len(budget.records),budget.requested),(4096,65536))
        with self.assertRaises(ValueError):card.HostBudget().record('wide',1,209,bytes(209))


class SourceAndGuardTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.capture=json.loads((card.ROOT/card.CAPTURE).read_bytes());cls.monitor=json.loads((card.ROOT/card.MONITOR).read_bytes())
    def test_exact_collector_projection_uses_this_epoch_and_clock(self):
        _,audit=card.audit_capture(self.capture,self.monitor,card.HostBudget())
        self.assertTrue(audit['collectorProjectionEquivalent']);self.assertEqual(audit['rawRequests'],231)
        self.assertEqual(audit['sameEpochCanonical'],self.capture['warmDefault']['dependency']['canonical'])

    def test_wrong_epoch_module_creation_or_source_rejected_before_cpu(self):
        for kind in ('module','creation','source'):
            c=copy.deepcopy(self.capture)
            if kind=='module':c['moduleBase']+=1<<32
            elif kind=='creation':c['creation']['filetime']+=1
            else:c['sourceObservationSHA256']='bad'
            with self.subTest(kind=kind),self.assertRaises(ValueError):card.audit_capture(c,self.monitor,card.HostBudget())

    def test_context_index_definition_and_flags_false_claims_rejected(self):
        for kind in ('context','index','definition','flags'):
            c=copy.deepcopy(self.capture)
            if kind=='context':c['warmDefault']['dependency']['canonical']&=0xffffffff
            elif kind=='index':c['cacheCollector']['methods'][1]['returnedIndex']=0
            elif kind=='definition':c['warmDefault']['identity']['classInfo']['definition']=1234
            else:c['warmDefault']['getter']['class']['observedInitializedFlag']=False
            with self.subTest(kind=kind),self.assertRaisesRegex(ValueError,'projection'):card.audit_capture(c,self.monitor,card.HostBudget())

    def test_claim_or_final_repeat_drift_cannot_be_accepted(self):
        c=copy.deepcopy(self.capture);c['atomicSnapshot']=True
        with self.assertRaises(ValueError):card.audit_capture(c,self.monitor,card.HostBudget())
        c=copy.deepcopy(self.capture);r=c['records'][-1];raw=bytearray.fromhex(r['bytesHex']);raw[0]^=1;r['bytesHex']=raw.hex()
        with self.assertRaisesRegex(ValueError,'overlap'):card.audit_capture(c,self.monitor,card.HostBudget())

    def test_warm_guard_allows_owned_stack_but_stops_external_write_and_helper(self):
        warm=card.WarmNative.__new__(card.WarmNative);warm.writes=[];warm.failure=None;warm.steps=0;warm.addresses=[]
        warm.reg=lambda name:card.GETTER[0];uc=FakeUC()
        warm.write(uc,0,warm.STACK+8,8,123,None);self.assertFalse(uc.stopped)
        warm.write(uc,0,0x50000001000,8,123,None)
        self.assertTrue(uc.stopped);self.assertEqual(warm.failure,'unexpected-warm-external-write')
        uc.stopped=False;warm.code(uc,0x140523160,1,None)
        self.assertTrue(uc.stopped);self.assertEqual(warm.failure,'unreviewed-warm-helper-edge')

    def test_cold_guard_stops_source_write_or_unknown_byte_after_vtable_start(self):
        module,m=alias_memory();flag=0x50001016b91;m.add(flag,b'\x84',{100});spans=m.spans;m.add_root_aliases(module)
        state=dict(active=True,factoryEntries=2,vtableStarted=True,invocations=[],nativeWrites=[],capturedWriteAttempts=[],
            priorControlledOverlapReads=[],capturedReadOrdinals=[],invalid=[])
        uc=FakeUC(dict(m.values));e=SimpleNamespace(uc=uc,U=SimpleNamespace(UC_HOOK_CODE='code',UC_HOOK_MEM_READ='read',
            UC_HOOK_MEM_WRITE='write',UC_HOOK_MEM_INVALID='invalid'),hooks=['old'],failure=None,
            extra_layer=SimpleNamespace(snapshot={'reads':{'cold.class371.header':dict(address=flag-0xc9,length=208)}}),reg=lambda name:0x14051cdd6)
        adapter=card.CaptureAdapter(uc,e,m,spans,state);adapter.callback=lambda *a:None;adapter.install()
        uc.callbacks['write'](uc,0,flag,1,0,None);self.assertTrue(uc.stopped);self.assertTrue(state['capturedWriteAttempts'])
        uc.stopped=False;uc.callbacks['read'](uc,0,flag-1,1,0,None)
        self.assertTrue(uc.stopped);self.assertEqual(e.failure,'Unprovided current-epoch captured input byte')
        state['vtableStarted']=False;uc.stopped=False;uc.callbacks['read'](uc,0,flag-1,1,0,None)
        self.assertFalse(uc.stopped);self.assertTrue(state['priorControlledOverlapReads'])


class NativeReportTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        path=os.environ.get('CLIENT72_AVATAR_RUNTIME_REPLAY_REPORT',str(card.RAW/'first.json'))
        cls.d=json.loads(Path(path).read_bytes());cls.c=cls.d['continuous']['result']
        cls.capture=json.loads((card.ROOT/card.CAPTURE).read_bytes())

    def test_current_source_all_pins_and_normal_exit_has_no_page_claim(self):
        self.assertEqual(self.d['sourceSHA256'],card.cache.sha(Path(card.__file__).read_bytes()))
        for p,expected in self.d['sourcePins'].items():self.assertEqual(card.cache.sha((card.ROOT/p).read_bytes()),expected,p)
        self.assertTrue(self.d['normalExitConfirmed']);self.assertFalse(self.d['loginPageExplicitlyConfirmed'])

    def test_real_warm_ret_exact_default_high64_no_getter_models(self):
        w=self.d['warm'];expected=self.capture['warmDefault']['getter']['default']
        self.assertTrue(w['actualRetObserved']);self.assertTrue(w['returnedDefaultMatches'])
        self.assertEqual(int(w['returnedDefault'],16),expected);self.assertGreater(expected,1<<32)
        self.assertEqual(w['steps'],25);self.assertFalse(w['failure']);self.assertIsNone(w['error'])
        self.assertEqual(w['modelsSupplied'],[]);self.assertFalse(w['externalWrites']);self.assertEqual(len(w['hostReadOrdinals']),7)
        self.assertIn('0x157d7ff55',w['executedAddresses']);self.assertNotIn('0x157d7ff48',w['executedAddresses'])

    def test_original_six_prefixes_and_both_cpu_budgets(self):
        previous=json.loads((card.ROOT/card.collector.THIRD).read_bytes())['continuous']
        for current,old in zip(self.c['native'][:6],previous['native'][:6]):
            for key in ('name','failure','error','regs'):self.assertEqual(current[key],old[key])
            self.assertEqual(current['trace']['pathSHA256'],old['trace']['pathSHA256'])
        self.assertEqual([x['steps'] for x in self.d['continuous']['state']['invocations']],[13,13,13,13,185,466,8066])
        self.assertEqual((self.d['cpuBudget']['calls'],self.d['cpuBudget']['instructions']),(8,8794))
        self.assertTrue(all(x['enforcedLimit']==2000000 for x in self.d['continuous']['state']['invocations']))

    def test_four_original_method_and_function_stores_preserve_captured_pointers(self):
        state=self.d['continuous']['state'];klass=int(self.c['newClasses'][1]['pointer'],16)
        writes=state['nativeWrites'];methods=[x for x in writes if x['writer']=='0x14051cdee']
        code=[x for x in writes if x['writer']=='0x14051cce6']
        self.assertEqual(len(methods),4);self.assertEqual(len(code),4)
        for i,candidate in enumerate(self.capture['ordinaryMethods']['methods']):
            self.assertEqual((int(methods[i]['address'],16),int(methods[i]['value'],16)),(klass+0x130+i*8,candidate['pointer']))
            self.assertEqual((int(code[i]['address'],16),int(code[i]['value'],16)),
                (klass+0xd0+i*8,struct.unpack_from('<Q',bytes.fromhex(candidate['methodInfoHex']),8)[0]))
        self.assertFalse(self.d['continuous']['capturedFunctionPointersRewritten'])

    def test_exact_unprovided_ordinary10294_slot_and_no_inflated_substitute(self):
        n=self.d['nextInputContract'];gap=n['currentGap'];root=self.capture['ordinaryMethods']['root']
        self.assertEqual((gap['reader'],gap['methodIndex'],int(gap['address'],16),gap['width']),('0x140526fcf',10294,root+10294*8,8))
        self.assertEqual(gap['error'],'Invalid memory read (UC_ERR_READ_UNMAPPED)')
        self.assertEqual(gap['actualEntry']['callerReturn'],'0x14051cdc7')
        self.assertTrue(n['inflatedMethodInfoIsNotOrdinarySlot']);self.assertFalse(n['nextBytesPresentInCapture'])
        self.assertEqual([x['ordinal'] for x in self.c['class80Extension']['vtableReads']],[0,1,2,3,4])
        self.assertFalse(self.c['extension']['windowRequests']);self.assertIsNone(self.d['continuous']['error'])

    def test_storage_capture_budget_and_all77_final_input_reads(self):
        b=self.d['inheritedStorage']['cumulativeBudget'];self.assertEqual((b['modelCalls'],b['readoutCalls'],b['readoutBytes']),(7,72,569))
        host=self.d['hostBudget'];self.assertLessEqual(host['reads'],4096);self.assertLessEqual(host['requestedBytes'],65536)
        self.assertEqual(host['reads'],len(host['records']));self.assertEqual(host['requestedBytes'],sum(x['width'] for x in host['records']))
        self.assertEqual(len(self.d['continuous']['state']['inputReadbackOrdinals']),77)
        self.assertTrue(self.d['inputReadbackVerified']);self.assertTrue(self.d['capturedInputReadOnlyVerified'])
        self.assertFalse(self.d['continuous']['state']['capturedWriteAttempts']);self.assertFalse(self.c['models']['callbackFailures'])
        self.assertTrue(self.d['continuous']['frozenCacheReaderAccounted']);self.assertFalse(self.d['continuous']['oldSourceMemoryLimitChanged'])

    def test_seven_root_aliases_and_canonical_all_dynamic_records_match_this_capture(self):
        self.assertEqual(len(self.d['continuous']['rootAliases']),7)
        canonical=self.capture['cacheCollector']['canonicalContext']['pointer']
        for r in self.c['genericRecords']:self.assertEqual(struct.unpack_from('<Q',bytes.fromhex(r['bytesHex']),8)[0],canonical)
        self.assertEqual(self.d['continuous']['state']['factoryEntries'],2)
        self.assertTrue(self.d['continuous']['state']['ordinaryRootPublishedAtSeventh'])
        self.assertFalse(self.d['continuous']['capturedOwnerPointersRewritten'])

    def test_natural_and_complete_runtime_boundaries_are_still_false(self):
        self.assertTrue(self.d['warmGetterReturnVerified'])
        for key in ('atomicSnapshot','naturalLookupObserved','naturalCallerObserved','defaultOriginalWriteExecuted',
                'completeColdClassInitialization','completeAvatarHandler','g2Verified','d02Complete'):
            self.assertFalse(self.d[key],key)
        self.assertFalse(self.d['continuous']['state']['initializationFailure'])


if __name__=='__main__':unittest.main()
