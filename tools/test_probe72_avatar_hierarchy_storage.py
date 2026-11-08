"""Exact typed-storage boundaries and original publication regressions."""
import json
import os
from pathlib import Path
import struct
from types import SimpleNamespace
import unittest

from tools import probe72_avatar_hierarchy_storage as card


class Memory:
    def __init__(self):self.bytes={};self.stopped=False
    def mem_write(self,address,raw):
        self.bytes.update({address+i:value for i,value in enumerate(raw)})
    def mem_read(self,address,width):return bytes(self.bytes[address+i] for i in range(width))
    def emu_stop(self):self.stopped=True


class Engine:
    def __init__(self):
        self.uc=Memory();self.regs={};self.failure=None;self.cursor=0x60000001000
        self.owner,self.klass,self.generic,self.canonical=0x17000001000,0x50000001000,0x68000001000,0x7ff00001000
        self.source=0x50000003000
        self.extra_layer=SimpleNamespace(methods=SimpleNamespace(classes={self.klass:400}),snapshot={
            'reads':{'cold.class371.hierarchy':dict(address=self.source)},
            'data':{'cold.class371.hierarchy':struct.pack('<Q',0x50000009900)}})
        self.write(0x145bc8ad0,self.owner)
        self.write(self.klass+0x48,0);self.write(self.klass+0x68,self.generic);self.write(self.klass+0xc4,2,'B')
        self.uc.mem_write(self.generic,struct.pack('<I4xQQQ',1234,self.canonical,0,self.klass))
        self.uc.mem_write(self.source,self.extra_layer.snapshot['data']['cold.class371.hierarchy'])
        self.regs.update(rcx=self.owner,rdx=16,rsi=self.klass,rsp=0x70000001000)
        self.write(self.regs['rsp'],0x14051f2ff)
    def reg(self,name):return self.regs[name]
    def write(self,address,value,fmt='Q'):self.uc.mem_write(address,struct.pack('<'+fmt,value))
    def alloc(self,width):
        result=self.cursor;self.cursor+=(width+15)&~15;self.uc.mem_write(result,bytes(width));return result


def allocated():
    engine=Engine();storage=card.HierarchyStorage(engine,engine.canonical)
    storage.allocate(0x14051f2ff)
    return engine,storage


def cleared():
    engine,storage=allocated()
    engine.regs.update(rcx=storage.pointer,edx=0,r8=16)
    storage.clear(0x14051f30f);storage.clear(0x14051f321)
    return engine,storage


class StorageTests(unittest.TestCase):
    def test_dirty16_exact_size_and_nonzero_tail(self):
        engine,storage=allocated()
        self.assertEqual(engine.uc.mem_read(storage.pointer,16),b'\xcc'*16)
        self.assertEqual(engine.uc.mem_read(storage.pointer+16,8),struct.pack('<Q',card.CANARY))
        self.assertNotIn(storage.pointer+24,engine.uc.bytes)
        self.assertEqual(len(storage.calls),1)

    def test_wrong_caller_and_width_zero8_24_32_are_rejected_before_allocation(self):
        for caller,width in ((0x14051f2fe,16),(0x14051f2ff,0),(0x14051f2ff,8),
                             (0x14051f2ff,24),(0x14051cb6d,32)):
            engine=Engine();storage=card.HierarchyStorage(engine,engine.canonical);engine.regs['rdx']=width
            with self.subTest(caller=caller,width=width),self.assertRaises(ValueError):storage.allocate(caller)
            self.assertIsNone(storage.pointer)
            self.assertFalse(storage.calls)

    def test_wrong_owner_high64_and_foreign_class_rejected(self):
        for kind in ('owner','class'):
            engine=Engine();storage=card.HierarchyStorage(engine,engine.canonical)
            engine.regs['rcx' if kind=='owner' else 'rsi']+=1<<32
            with self.subTest(kind=kind),self.assertRaises(ValueError):storage.allocate(0x14051f2ff)
            self.assertIsNone(storage.pointer)

    def test_definition_depth_context_nonnull_and_backref_drift_rejected(self):
        for kind in ('definition','depth','context','nonnull','method','backref'):
            engine=Engine();storage=card.HierarchyStorage(engine,engine.canonical)
            if kind=='definition':engine.write(engine.generic,1239,'I')
            elif kind=='depth':engine.write(engine.klass+0xc4,3,'B')
            elif kind=='context':engine.write(engine.generic+8,engine.canonical&0xffffffff)
            elif kind=='nonnull':engine.write(engine.klass+0x48,0x8000000100000001)
            elif kind=='method':engine.write(engine.generic+16,0x100000001)
            else:engine.write(engine.generic+24,engine.klass+(1<<32))
            with self.subTest(kind=kind),self.assertRaises(ValueError):storage.allocate(0x14051f2ff)
            self.assertFalse(storage.calls)

    def test_two_original_clear_callers_and_dirty_becomes_zero(self):
        engine,storage=cleared()
        self.assertEqual(engine.uc.mem_read(storage.pointer,16),bytes(16))
        self.assertEqual([row['callerReturn'] for row in storage.calls],
                         ['0x14051f2ff','0x14051f30f','0x14051f321'])
        self.assertEqual(storage.zero_count,2)

    def test_clear_caller_width_value_or_pointer_drift_rejected(self):
        for kind in ('caller','width','value','pointer'):
            engine,storage=allocated();engine.regs.update(rcx=storage.pointer,edx=0,r8=16)
            caller=0x14051f30f
            if kind=='caller':caller=0x14051f321
            elif kind=='width':engine.regs['r8']=24
            elif kind=='value':engine.regs['edx']=1
            else:engine.regs['rcx']+=8
            with self.subTest(kind=kind),self.assertRaises(ValueError):storage.clear(caller)
            self.assertEqual(engine.uc.mem_read(storage.pointer,16),b'\xcc'*16)

    def test_changed_tail_rejected_before_clear_and_copy(self):
        engine,storage=allocated();engine.regs.update(rcx=storage.pointer,edx=0,r8=16)
        engine.write(storage.pointer+16,card.CANARY^1)
        with self.assertRaisesRegex(ValueError,'tail'):storage.clear(0x14051f30f)
        engine,storage=cleared();engine.regs.update(rcx=storage.pointer,rdx=engine.source,r8=8)
        engine.write(storage.pointer+16,0)
        with self.assertRaisesRegex(ValueError,'tail'):storage.copy(0x14051f35c)

    def test_exact_parent_copy_and_original_publication_required(self):
        engine,storage=cleared();engine.regs.update(rcx=storage.pointer,rdx=engine.source,r8=8)
        storage.native_write(engine.uc,storage.pointer+8,8,engine.klass,0x14051f333)
        engine.write(storage.pointer+8,engine.klass)
        storage.copy(0x14051f35c)
        with self.assertRaisesRegex(ValueError,'publication'):storage.publication()
        engine.write(engine.klass+0x48,storage.pointer)
        storage.publication()
        self.assertEqual(storage.published['bytesHex'],
            (engine.extra_layer.snapshot['data']['cold.class371.hierarchy']+struct.pack('<Q',engine.klass)).hex())
        self.assertEqual(len(storage.calls),4)
        self.assertEqual(storage.published['tail'],hex(card.CANARY))

    def test_copy_wrong_source_width_caller_or_source_bytes_rejected(self):
        for kind in ('source','width','caller','bytes'):
            engine,storage=cleared();engine.regs.update(rcx=storage.pointer,rdx=engine.source,r8=8)
            caller=0x14051f35c
            if kind=='source':engine.regs['rdx']+=8
            elif kind=='width':engine.regs['r8']=16
            elif kind=='caller':caller-=1
            else:engine.write(engine.source,0x8000000100000001)
            with self.subTest(kind=kind),self.assertRaises(ValueError):storage.copy(caller)
            self.assertEqual(len(storage.calls),3)

    def test_native_tail_wrong_writer_or_span_rejected(self):
        for offset,width,writer in ((16,8,0x14051f333),(8,16,0x14051f333),(8,8,0x14051f334),(0,8,0x14051f333)):
            engine,storage=allocated()
            storage.native_write(engine.uc,storage.pointer+offset,width,engine.klass,writer)
            self.assertTrue(engine.uc.stopped)
            self.assertTrue(storage.rejected)

    def test_model_and_readout_budget_cumulative(self):
        engine,storage=allocated()
        storage.read_bytes=512
        with self.assertRaisesRegex(ValueError,'readout'):storage.read(storage.pointer,1,'owned negative')
        storage.read_bytes=0;storage.reads=[None]*64
        with self.assertRaisesRegex(ValueError,'readout'):storage.read(storage.pointer,1,'owned negative')
        storage.calls=[None]*4
        with self.assertRaisesRegex(ValueError,'model-call'):storage.call('owned negative',0x14051f35c)


class ContinuousTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        report=os.environ.get('CLIENT72_AVATAR_HIERARCHY_STORAGE_REPORT',str(card.RAW/'first.json'))
        cls.report=json.loads(Path(report).read_bytes());cls.cont=cls.report['continuous']

    def test_current_source_and_all_old_pins_preserved(self):
        self.assertEqual(self.report['sourceSHA256'],card.replay.probe.sha(Path(card.__file__).read_bytes()))
        for path,expected in self.report['sourcePins'].items():
            self.assertEqual(card.replay.probe.sha((card.ROOT/path).read_bytes()),expected,path)

    def test_exact_four_boundaries_original_store_xchg_and_tail(self):
        storage=self.cont['hierarchyStorage']
        self.assertEqual([x['action'] for x in storage['calls']],['allocate-dirty16','clear16','clear16','copy-parent8'])
        self.assertEqual([x['callerReturn'] for x in storage['calls']],
                         ['0x14051f2ff','0x14051f30f','0x14051f321','0x14051f35c'])
        published=storage['publication']
        pointer,klass=int(published['pointer'],16),int(published['klass'],16)
        self.assertEqual(published['tail'],'0xfedcba9876543210')
        self.assertEqual(bytes.fromhex(published['bytesHex'])[8:],struct.pack('<Q',klass))
        self.assertEqual(storage['originalNativeStores'],[dict(address=hex(pointer+8),width=8,
                                                              writer='0x14051f333',value=hex(klass))])
        self.assertEqual(published['originalPublication'],'0x14051f361')
        self.assertFalse(storage['rejected'])

    def test_old_six_prefixes_exactly_preserved_and_seventh_continuous(self):
        reference=json.loads((card.ROOT/card.REPLAY_REPORT).read_bytes())['continuous']
        self.assertEqual(len(self.cont['native']),7)
        for new,old in zip(self.cont['native'][:6],reference['native'][:6]):
            for key in ('name','failure','error','regs'):self.assertEqual(new[key],old[key])
            self.assertEqual(new['trace']['pathSHA256'],old['trace']['pathSHA256'])
        self.assertEqual(self.cont['hierarchyExtension']['factoryEntries'],2)
        self.assertEqual(self.cont['hierarchyExtension']['frozenPostconditionObsolete'],
                         'Continuous case did not reach the exact bounded storage gap')

    def test_next32_request_has_no_storage_and_no_new_window_or_success(self):
        native=self.cont['native'][-1];request=self.cont['hierarchyExtension']['nextUnprovidedRequest']
        self.assertEqual((native['failure'],native['error']),('Unprovided class+80 storage allocation',None))
        self.assertEqual((request['callerReturn'],request['width'],request['count'],request['templateCount']),
                         ('0x14051cb6d',32,2,2))
        self.assertEqual(request['fieldValueBefore'],'0x0')
        self.assertFalse(request['newStorageSupplied'])
        self.assertFalse(self.cont['extension']['windowRequests'])
        self.assertFalse(self.cont['models']['callbackFailures'])
        self.assertFalse(self.cont['captureExtension']['capturedWriteAttempts'])

    def test_cumulative_budgets_and_complete_boundaries(self):
        budget=self.cont['hierarchyStorage']['budget']
        self.assertEqual(budget['modelCalls'],4)
        self.assertLessEqual(budget['readoutCalls'],64)
        self.assertLessEqual(budget['readoutBytes'],512)
        self.assertEqual(budget['readoutCalls'],len(self.cont['hierarchyStorage']['hostReadout']))
        self.assertEqual(budget['readoutBytes'],sum(x['width'] for x in self.cont['hierarchyStorage']['hostReadout']))
        for invoke in self.cont['extension']['invocations']:
            self.assertEqual(invoke['enforcedLimit'],2000000)
            self.assertLess(invoke['steps'],2000000)
        self.assertEqual(self.report['newNaturalBytesSupplied'],0)
        for key in ('completeColdComparer','completeClassInitialization','defaultComparerWriteExecuted',
                    'completeAvatarHandler','targetProtocolReady','d02Complete'):
            self.assertFalse(self.report[key])

    def test_next_contract_is_precise32_storage_not_approved_success(self):
        contract=self.report['nextInputContract']
        self.assertEqual(contract['minimumStorageBytes'],32)
        self.assertFalse(contract['storageSupplied'])
        self.assertEqual([x['callerReturn'] for x in contract['possibleFutureBoundaries']],
                         ['0x14051cb6d','0x14051cb7d','0x14051cb8f'])
        self.assertFalse(contract['followingRowAndConsumerClosureVerified'])
        self.assertEqual(contract['newNaturalBytesForAllocationOnly'],0)


if __name__=='__main__':unittest.main()
