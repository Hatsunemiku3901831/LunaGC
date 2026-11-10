"""Artificial retained-primary GC Default adapter checks; no Windows calls."""
from copy import deepcopy
import importlib.util
from itertools import product
from pathlib import Path
import unittest
from unittest.mock import patch

from tools import client72_d02_gc_default_inputs as gc
from tools import client72_d02_tls_inputs as core
from tools import client72_d02_tls_pool_inputs as pool
from tools import client72_d02_avatar_block_inputs as block
from tools import client72_d02_avatar_ctor_inputs as ctor
from tools import client72_d02_second_allocator_inputs as second
from tools import observe_client72_tls_inputs as adapter
from tools.test_client72_d02_gc_default_inputs import Graph, I, F, AVX, SSE, DEFAULT
from tools.test_client72_d02_second_allocator_inputs import SecondMemory, D, H2
from tools.test_client72_d02_tls_inputs import TEB
from tools.test_observe_client72_tls_inputs import ArtificialBackend, basic, epoch

SOURCES = ('tools/client72_d02_tls_inputs.py', 'tools/observe_client72_tls_inputs.py',
           'tools/client72_d02_tls_pool_inputs.py', 'tools/client72_d02_avatar_block_inputs.py',
           'tools/client72_d02_avatar_ctor_inputs.py', 'tools/client72_d02_second_allocator_inputs.py',
           'tools/client72_d02_gc_default_inputs.py')
FLAGS = dict(include_pool=True, include_allocator_inputs=True, include_ctor_inputs=True,
             include_second_allocator_inputs=True, include_gc_default_inputs=True)
OLD_PATH = core.ROOT / 'local/migration72/d02-resume-20261110b/a-gc-default-inputs/adapter-main/observe_client72_tls_inputs.before.py'


def artificial_epoch(count=7):
    e = epoch()
    e['module']['size'] = 0x6d19144
    e['implementationPins'] = {name: core.digest((core.ROOT / name).read_bytes()) for name in SOURCES[:count]}
    return e


def sample(module, memory, e, flags, mutations=None):
    backend = ArtificialBackend(); backend.memory = memory
    ids = [deepcopy({name: e[name] for name in ('pid', 'image', 'processCreationTime', 'module')}) for _ in range(2)]
    calls = []
    if mutations:
        mutations(backend, ids)
    def identity(handle, pid):
        calls.append((handle, pid)); return ids.pop(0)
    result = module.sample_primary(backend, e, identity, now=lambda: 'artificial-fixed', **flags)
    return result, backend, calls


class GCAdapterTests(unittest.TestCase):
    def setUp(self):
        self.e = artificial_epoch()
        self.gate = patch.object(gc, 'verify_sources', return_value={'artificialSourceGateMock': True}).start()
        self.addCleanup(patch.stopall)

    def run_case(self, memory=None, flags=FLAGS, mutations=None):
        return sample(adapter, memory or Graph(), self.e, flags, mutations)

    def assert_entry(self, result, backend, identities):
        self.assertEqual((result['status'], result['failureStage']), ('partial', 'entry'))
        self.assertEqual(identities, []); self.assertEqual(backend.calls, ['CloseHandle-primary'])
        self.assertEqual(backend.memory.calls, []); self.assertEqual(backend.closed, [818])

    def test_full_graph_profile_pins_identity_and_budgets(self):
        r, b, ids = self.run_case(); c = r['collection']; budget = c['budget']
        self.assertEqual(r['status'], 'candidate'); self.assertEqual(r['profile'], gc.PROFILE)
        self.assertEqual(r['implementationPins'], self.e['implementationPins']); self.assertEqual(len(r['implementationPins']), 7)
        self.gate.assert_called_once_with(); self.assertEqual(ids, [(919,37), (919,37)])
        self.assertEqual(r['identityObservations'][1]['value'], {k:self.e[k] for k in ('pid','image','processCreationTime','module')})
        self.assertGreater(self.e['processCreationTime'], 0xffffffff); self.assertGreater(r['threadBefore']['decoded']['teb'], 0xffffffff)
        self.assertTrue(r['endIdentityVerified']); self.assertEqual(r['apiCounts'], adapter.API_LIMITS)
        self.assertEqual(r['apiRequests'], adapter.API_LIMITS); self.assertEqual(r['threadBasicOutputBytes'], 96)
        self.assertEqual(b.closed, [818]); self.assertEqual(c['ctorInputs']['default']['value'], D)
        self.assertEqual(tuple(budget[k] for k in ('maxFirstPassReads','maxFirstPassBytes','maxReads','maxBytes','maxSingleRead')), (49,1209,98,2418,208))
        self.assertEqual(tuple(budget[k] for k in ('firstPassReads','firstPassBytes','reads','requestedBytes')), (49,889,98,1778))
        self.assertEqual(len(b.memory.calls), 98); self.assertEqual(b.memory.calls[:49], b.memory.calls[49:])
        self.assertEqual(len(set(b.memory.calls[:49])), 49); self.assertEqual(c['inputConsistency']['verifiedSpans'],49)
        self.assertIn((AVX,4), b.memory.calls); self.assertNotIn((SSE,4), b.memory.calls)
        self.assertNotIn((D,8), b.memory.calls); self.assertFalse(r['liveApproved']); self.assertFalse(r['canReplayPop'])
        self.assertFalse(c['ctorInputs']['gcHelper']['naturalReturnObserved']); self.assertFalse(c['ctorInputs']['wholeConstructorComplete'])

    def test_pin_missing_or_drift_precedes_gate(self):
        for name, kind in product(SOURCES, ('missing','drift')):
            with self.subTest(name=name,kind=kind):
                self.e = artificial_epoch(); self.gate.reset_mock()
                if kind == 'missing': del self.e['implementationPins'][name]
                else: self.e['implementationPins'][name] = '0'*64
                self.assert_entry(*self.run_case()); self.gate.assert_not_called()

    def test_flag_boolean_and_missing_dependencies(self):
        for value in (None,1,0,'true'):
            with self.subTest(value=value): self.assert_entry(*self.run_case(flags={**FLAGS,'include_gc_default_inputs':value}))
        for values in product((False,True),repeat=4):
            if all(values): continue
            with self.subTest(dependencies=values):
                self.assert_entry(*self.run_case(flags=dict(zip(FLAGS,(*values,True)))))
        self.gate.assert_not_called()

    def test_source_gate_failure_before_identity_or_thread(self):
        self.gate.side_effect = ValueError('artificial source failure')
        r,b,ids = self.run_case(); self.assert_entry(r,b,ids)
        self.assertIn('artificial source failure',r['reason']); self.gate.assert_called_once_with()

    def test_new_module_boundaries_and_old_scope(self):
        for size,base,accepted in ((0x6d19143,0x100000000,False),(0x6d19144,0x100000000,True),
                                   (0x6d19144,core.U64-0x6d19144,True),(0x6d19144,core.U64-0x6d19144+1,False)):
            with self.subTest(size=size,base=base):
                self.e=artificial_epoch(); self.e['module'].update(size=size,base=base)
                r,b,ids=self.run_case()
                self.assertEqual(r['failureStage']=='entry',not accepted)
                if not accepted: self.assert_entry(r,b,ids)
        for size,base,accepted in ((0x5eef083,0x100000000,False),(0x5eef084,0x100000000,True),
                                   (0x5eef084,core.U64-0x5eef084,True),(0x5eef084,core.U64-0x5eef084+1,False)):
            with self.subTest(oldsize=size,base=base):
                self.e=artificial_epoch(6); self.e['module'].update(size=size,base=base)
                with patch.object(second,'verify_sources',return_value={'artificialSourceGateMock':True}):
                    r,b,ids=self.run_case(memory=SecondMemory(),flags={k:v for k,v in FLAGS.items() if k!='include_gc_default_inputs'})
                self.assertEqual(r['failureStage']=='entry',not accepted)
                if not accepted: self.assert_entry(r,b,ids)

    def test_first_and_final_short_error_drift_preserve_evidence(self):
        baseline,_,_=self.run_case(); rows=baseline['collection']['records']
        changed=bytearray.fromhex(rows[48]['bytesHex']); changed[0]^=1
        first_changed=bytearray.fromhex(rows[0]['bytesHex']); first_changed[0]^=1
        for ordinal,fault in ((1,(b'',None)),(1,(b'',{'winerror':5})),(98,(b'',None)),
                              (98,(b'',{'winerror':5})),(50,(bytes(first_changed),None)),(98,(bytes(changed),None))):
            with self.subTest(ordinal=ordinal,fault=fault):
                m=Graph(); m.faults[ordinal]=fault; r,b,ids=self.run_case(memory=m)
                self.assertEqual((r['status'],r['failureStage']),('partial','collection'))
                self.assertEqual(len(m.calls),ordinal); self.assertEqual(r['collection']['budget']['reads'],ordinal)
                self.assertEqual(r['collection']['records'][-1]['bytesHex'],fault[0].hex())
                self.assertEqual(ids,[(919,37),(919,37)]); self.assertEqual(r['apiCounts'],adapter.API_LIMITS); self.assertEqual(b.closed,[818])

    def test_after_identity_teb_and_close_failures_keep_raw(self):
        for scenario in ('identity','teb','close','collection-close'):
            with self.subTest(scenario=scenario):
                def mutate(b,ids):
                    if scenario=='identity': ids[1]['processCreationTime']+=1
                    if scenario=='teb': b.basics[1]=basic(teb=TEB+0x1000)
                    if scenario in ('close','collection-close'): b.fail['CloseHandle-primary']={'attempted':True,'result':0,'lastError':6,'handle':818}
                    if scenario=='collection-close': b.memory.faults[98]=(b'',{'winerror':5})
                r,b,_=self.run_case(mutations=mutate)
                self.assertEqual(r['status'],'partial'); self.assertEqual(r['failureStage'],{'identity':'identity-after','teb':'thread-after','close':'cleanup','collection-close':'collection'}[scenario])
                self.assertEqual(b.closed,[818]); self.assertEqual(len(b.memory.calls),98)
                if scenario=='identity': self.assertEqual(r['identityObservations'][1]['value']['processCreationTime'],self.e['processCreationTime']+1)
                if scenario=='teb': self.assertEqual(r['threadAfter']['decoded']['teb'],TEB+0x1000)
                if scenario in ('close','collection-close'): self.assertEqual(r['primaryClose']['lastError'],6)

    def test_gc_semantic_partial_still_reads_independent_default(self):
        m=Graph(); m.put(F,b'\x02'); r,b,_=self.run_case(memory=m)
        self.assertEqual(r['status'],'partial'); self.assertIn('external-tail',r['collection']['ctorInputs']['gcHelper']['failure'])
        self.assertIn((DEFAULT,8),m.calls); self.assertEqual(r['collection']['ctorInputs']['default']['value'],D)
        self.assertNotIn((AVX,4),m.calls); self.assertNotIn((SSE,4),m.calls)
        self.assertFalse(r['collection']['ctorInputs']['gcHelper']['naturalReturnObserved'])
        self.assertFalse(r['collection']['ctorInputs']['wholeConstructorComplete'])


class OldAdapterComparison(unittest.TestCase):
    def test_old_five_profiles_on_identical_artificial_inputs(self):
        spec=importlib.util.spec_from_file_location('tools.artificial_old_adapter',OLD_PATH)
        old=importlib.util.module_from_spec(spec); spec.loader.exec_module(old)
        profiles=[({},core,2),({'include_pool':True},pool,3),
                  ({'include_pool':True,'include_allocator_inputs':True},block,4),
                  ({'include_pool':True,'include_allocator_inputs':True,'include_ctor_inputs':True},ctor,5),
                  ({k:v for k,v in FLAGS.items() if k!='include_gc_default_inputs'},second,6)]
        for flags, selected, count in profiles:
            for shape in ('normal','short-shape','flags8'):
                with self.subTest(profile=count,shape=shape):
                    e=artificial_epoch(count)
                    m1=SecondMemory(length1=32,length2=64) if shape=='short-shape' else SecondMemory()
                    m2=SecondMemory(length1=32,length2=64) if shape=='short-shape' else SecondMemory()
                    if shape=='flags8':
                        for m in (m1,m2):
                            raw=bytearray(m.spans[H2]); raw[8]=8; m.put(H2,raw)
                    with patch.object(selected,'verify_sources',return_value={'artificialSourceGateMock':True}):
                        a,ba,_=sample(old,m1,e,flags); b,bb,_=sample(adapter,m2,e,flags)
                    self.assertEqual(ba.memory.calls,bb.memory.calls); self.assertEqual(a,b)
                    self.assertEqual(a['status'],'partial' if count==6 and shape=='flags8' else 'candidate')


if __name__=='__main__': unittest.main()
