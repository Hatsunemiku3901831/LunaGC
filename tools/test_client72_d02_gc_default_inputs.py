"""Synthetic graphs: no target, native execution, or captured-data writes."""
from copy import deepcopy
import struct
import unittest
from unittest.mock import patch

from tools import client72_d02_gc_default_inputs as gc
from tools import client72_d02_tls_inputs as core
from tools.test_client72_d02_tls_inputs import MODULE, TEB
from tools.test_client72_d02_avatar_block_inputs import HEAD
from tools.test_client72_d02_second_allocator_inputs import SecondMemory, H2, R2, U, P, D

I, T0, T1, F = (MODULE + n for n in (0x56afdd0, 0x56afdd8, 0x56afde0, 0x5eeeb08))
SSE, AVX, DEFAULT = (MODULE + n for n in (0x6d19140, 0x6d190f4, 0x594fd78))


class Graph(SecondMemory):
    def __init__(self, *, shared=False):
        super().__init__(length1=32, length2=64, shared=shared)
        for address in (HEAD, H2):
            raw = bytearray(self.spans[address]); raw[8] = 8; self.put(address, raw)
        self.put(I, struct.pack('<I', 3)); self.qword(T0, 47); self.qword(T1, 48)
        self.put(F, b'\x00'); self.put(SSE, struct.pack('<I', 0x01fdfd35))
        self.put(AVX, struct.pack('<I', 0x01fdfb94))


def run(mem):
    return gc.collect(mem.read, MODULE, TEB, now=lambda: 'fixed')


class GCTests(unittest.TestCase):
    def test_avx_actual_shape_and_final(self):
        mem = Graph(); initial = deepcopy(mem.spans); result = run(mem)
        self.assertEqual(result['status'], 'candidate')
        self.assertEqual(result['ctorInputs']['gcHelper']['dispatch'], 'AVX')
        self.assertEqual(tuple(result['budget'][k] for k in ('firstPassReads','firstPassBytes','reads','requestedBytes')), (49,889,98,1778))
        self.assertEqual(mem.calls[:49], mem.calls[49:]); self.assertEqual(initial, mem.spans)
        self.assertNotIn((SSE,4), mem.calls); self.assertNotIn((D,8), mem.calls)
        self.assertEqual(result['inputConsistency']['verifiedSpans'], 49)
        self.assertEqual(tuple(result['budget'][k] for k in ('maxFirstPassReads','maxFirstPassBytes','maxReads','maxBytes','maxSingleRead')), (49,1209,98,2418,208))
        self.assertFalse(result['ctorInputs']['gcHelper']['naturalReturnObserved'])
        self.assertFalse(result['ctorInputs']['wholeConstructorComplete'])

    def test_unsigned_thresholds_and_exclusive_tables(self):
        for index, t0, t1, flag_read in [(2,47,0,True),(2,48,0,False),(2,core.U64,0,False),
                                        (3,47,47,False),(3,47,48,True),(3,47,core.U64,True),
                                        (3,48,core.U64,False),(0xffffffff,core.U64,0,False)]:
            with self.subTest(index=index,t0=t0,t1=t1):
                m=Graph(); m.put(I,struct.pack('<I',index)); m.qword(T0,t0); m.qword(T1,t1)
                r=run(m); self.assertEqual(r['status'],'candidate')
                self.assertEqual((F,1) in m.calls,flag_read)
                self.assertEqual((T1,8) in m.calls,index>=3 and t0<48)
                self.assertEqual((SSE,4) in m.calls,index<3); self.assertEqual((AVX,4) in m.calls,index>=3)

    def test_external_and_unexpected_independent_default(self):
        for index in (2,3):
            for mode in ('flag','wrong','window'):
                with self.subTest(index=index,mode=mode):
                    m=Graph(); m.put(I,struct.pack('<I',index)); table=SSE if index<3 else AVX
                    if mode=='flag': m.put(F,b'\x02')
                    else: m.put(table,struct.pack('<I',0 if mode=='wrong' else 0x01fdf9c0))
                    r=run(m); h=r['ctorInputs']['gcHelper']
                    self.assertEqual(r['status'],'partial'); self.assertEqual(h['status'],'partial')
                    self.assertIn((DEFAULT,8),m.calls); self.assertEqual(r['ctorInputs']['default']['value'],D)
                    self.assertEqual((table,4) in m.calls,mode!='flag')
                    self.assertEqual(h['stopPC'],('0x141fdfc80' if index<3 else '0x141fdfaac') if mode=='flag' else ('0x141fdfd08' if index<3 else '0x141fdfb54'))

    def test_unsupported_and_v1_no_helper(self):
        for kind in ('length','alignment'):
            m=Graph()
            if kind=='length': m.qword(R2+0x18,56); m.put(H2,m.spans[H2][:56])
            else: m.qword(R2+0x20,H2+1); m.put(H2+1,m.spans[H2])
            r=run(m); self.assertEqual(r['status'],'partial'); self.assertIn('unsupported',r['ctorInputs']['gcHelper']['failure'])
            self.assertNotIn((I,4),m.calls); self.assertIn((DEFAULT,8),m.calls)
            self.assertEqual(r['ctorInputs']['gcHelper']['r8'], 40 if kind=='length' else 48)
        m=Graph(); raw=bytearray(m.spans[H2]); raw[8]=0; m.put(H2,raw)
        r=run(m); self.assertEqual(r['status'],'candidate'); self.assertEqual(r['ctorInputs']['gcHelper']['status'],'not-required')
        self.assertNotIn((I,4),m.calls)

    def test_default_frontiers(self):
        for address, failure, forbidden in ((DEFAULT,'usage-null',(0x38,8)),(U+0x38,'pointer-null',(0,8)),(P,'cold-unclosed',(0,8))):
            m=Graph(); m.qword(address,0); r=run(m)
            self.assertEqual(r['status'],'partial'); self.assertIn(failure,r['ctorInputs']['default']['failure'])
            self.assertNotIn(forbidden,m.calls); self.assertNotIn((D,8),m.calls)
        m=Graph(); m.qword(P,core.U64); self.assertEqual(run(m)['ctorInputs']['default']['value'],core.U64)

    def test_new_first_failure_short_error_and_final_drift(self):
        base=run(Graph()); rows=base['records']; count=base['budget']['firstPassReads']
        first=[r for r in rows[:count] if r['name'].startswith(('gcHelper.','default.'))]
        self.assertEqual(len(first),8)
        for row in first:
            n=row['ordinal']
            for fault in ((b'',None),(b'\x12',{'kind':'failure'})):
                m=Graph(); m.faults[n]=fault; r=run(m)
                self.assertEqual(r['status'],'partial'); self.assertEqual(r['budget']['firstPassReads'],n)
                self.assertEqual(r['records'][n-1]['bytesHex'],fault[0].hex())
                if row['name'].startswith('gcHelper.'): self.assertNotIn((DEFAULT,8),m.calls)
            changed=bytearray.fromhex(row['bytesHex']); changed[0]^=1
            for fault in ((b'',None),(b'',{'kind':'failure'}),(bytes(changed),None)):
                m=Graph(); m.faults[count+n]=fault; r=run(m)
                self.assertEqual(r['status'],'partial'); self.assertEqual(len(m.calls),count+n)
                self.assertEqual(r['inputConsistency']['status'],'partial')

    def test_reuse_and_independent_partial_causes(self):
        m=Graph(shared=True); r=run(m)
        self.assertEqual(r['status'],'candidate')
        self.assertTrue(r['ctorInputs']['hashSet']['reusedSpans'])
        count=r['budget']['firstPassReads']
        self.assertLess(count,49); self.assertEqual(m.calls[:count],m.calls[count:])
        m=Graph(); m.put(F,b'\x02'); m.qword(DEFAULT,0); r=run(m)
        self.assertIn('external-tail',r['ctorInputs']['gcHelper']['failure'])
        self.assertIn('usage-null',r['ctorInputs']['default']['failure'])
        self.assertEqual(r['status'],'partial')
        base=run(Graph()); row=next(x for x in base['records'] if x['name']=='second.completeHead')
        m=Graph(); m.faults[row['ordinal']]=(b'',None); r=run(m)
        self.assertEqual(r['status'],'partial'); self.assertNotIn((I,4),m.calls); self.assertNotIn((DEFAULT,8),m.calls)

    def test_source_gate_and_old_registry(self):
        proof=gc.verify_sources(); self.assertEqual(len(proof['auditedGC64InputPins']),10)
        registry=core.READ_PROFILES.copy(); run(Graph()); self.assertEqual(core.READ_PROFILES,registry)
        original=gc.ctor._file_sha256
        for name in (gc.SPEC,gc.AUDIT,'tools/client72_d02_second_allocator_inputs.py',next(iter(proof['auditedGC64InputPins']))):
            with patch.object(gc.ctor,'_file_sha256',side_effect=lambda p: '0'*64 if p==core.ROOT/name else original(p)):
                with self.assertRaises(ValueError): gc.verify_sources()


if __name__=='__main__': unittest.main()
