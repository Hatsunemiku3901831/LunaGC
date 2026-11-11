import ctypes as C
from ctypes import wintypes as W
import os
import struct
import time
import unittest
from unittest.mock import patch

from tools.migration72 import proud_existing_standard_cache_sampler as s

class Fixture:
    def __init__(self, chain=4):
        self.base=0x180000000; self.root=0x200000000; self.cache=0x210000000
        self.entries=0x220000000; self.buckets=0x230000000; self.p=0x240000000; self.m=0x250000000; self.table=0x260000000; self.seed=0x13579bdf
        self.mem={}; self.calls=[]
        self.put(self.base+0x587c2c0,0,1); self.put(self.base+0x586b600,self.root,8)
        self.put(self.root+0x21f60,self.cache,8)
        self.mem[self.cache+0x18]=struct.pack('<QQQ',self.entries,self.buckets,0)
        self.put(self.buckets+0x18,7,4); self.put(self.entries+0x18,chain,4)
        self.put(self.buckets+0x20+4*(22101%7),1,4)
        for i in range(chain):
            self.mem[self.entries+0x20+24*i]=struct.pack('<IiI', 22101 if i==chain-1 else 1, i+1 if i<chain-1 else -1, 22101 if i==chain-1 else 2)
        self.put(self.entries+0x20+24*(chain-1)+0x10,self.p,8)
        self.put(self.p+0x40,self.m,8); self.put(self.m+0x84,0x89abcdef,4); self.put(self.m+0x90,0xfedcba98,4)
        self.put(self.root+0xe970,self.table,8); self.put(self.table+0x18,0x800,4); self.put(self.base+0x5872d30,self.seed,4)
        self.inverse_indices=[[0x49a,0x5fc,0x656,0x730],[0x4ed,0x58b,0x621,0x747]]
        for indices,values in zip(self.inverse_indices,([0x12,0x34,0x56,0x78],[0xa1,0xb2,0xc3,0xd4])):
            for index,value in zip(indices,values): self.put(self.table+0x20+index,value,1)
    def put(self,a,v,n): self.mem[a]=v.to_bytes(n,'little',signed=False)
    def read(self,a,w):
        self.calls.append((a,w)); return self.mem[a],None
    def run(self, read=None): return s.collect(s.Reader(read or self.read,time.monotonic()+60),self.base)

class LookupTests(unittest.TestCase):
    def test_four_entry_exact_budget_and_replay(self):
        f=Fixture(); r=f.run(); self.assertEqual(r['status'],'completed')
        self.assertEqual((r['reads'],r['requestedBytes'],r['returnedBytes']),(52,298,298))
        self.assertTrue(r['twoReadSetsEqual']); self.assertEqual(f.calls[:26],f.calls[26:])
        self.assertEqual(r['observedEncoded'],{'field203373':0x89abcdef,'field203376':0xfedcba98})
        self.assertEqual(r['conditionalGetterU32'],{'field203373':0x12345678,'field203376':0xa1b2c3d4}); self.assertIsNone(r['actualNativeGetterReturn']); self.assertEqual(f.calls[11],(f.entries+0x20+24*3+0x10,8))
    def test_match_only_reads_value_and_null_managed_stops(self):
        f=Fixture(1); f.put(f.p+0x40,0,8); r=f.run()
        self.assertEqual(r['status'],'managed-representation-null'); self.assertNotIn((f.m+0x84,4),f.calls)
        self.assertEqual(r['reads'],10)
    def test_collision_cap_and_native_loop_budget(self):
        f=Fixture(5); r=f.run(); self.assertEqual(r['status'],'collision-cap'); self.assertEqual(r['reads'],11)
        f=Fixture(1); f.mem[f.entries+0x20]=struct.pack('<IiI',1,0,2)
        r=f.run(); self.assertEqual(r['status'],'native-loop-budget-exhausted'); self.assertEqual(r['reads'],9)
    def test_empty_and_out_of_range_not_conflated(self):
        for bucket,expected in [(0,'empty-chain'),(3,'positive-index-out-of-range'),(0xffffffff,'negative-index')]:
            f=Fixture(1); f.put(f.buckets+0x20+4*(22101%7),bucket,4); self.assertEqual(f.run()['status'],expected)
        f=Fixture(1); f.mem[f.entries+0x20]=struct.pack('<IiI',1,-2,2); self.assertEqual(f.run()['status'],'negative-index')
    def test_bad_lengths_and_comparer_stop(self):
        for value,expected in [(0,'unsupported-bucket-length'),(0xffffffff,'unsupported-bucket-length')]:
            f=Fixture(); f.put(f.buckets+0x18,value,4); self.assertEqual(f.run()['status'],expected)
        f=Fixture(); f.put(f.entries+0x18,0xffffffff,4); self.assertEqual(f.run()['status'],'unsupported-entry-length')
        f=Fixture(); f.mem[f.cache+0x18]=struct.pack('<QQQ',f.entries,f.buckets,1); self.assertEqual(f.run()['status'],'unsupported-comparer'); self.assertEqual(len(f.calls),4)
    def test_all_reads_fail_short_or_error_without_followup(self):
        for pos in range(52):
            for err in (None,{'winerror':299}):
                f=Fixture()
                def read(a,w):
                    if len(f.calls)==pos:
                        f.calls.append((a,w)); return (b'' if w == 1 else b'\xa5'),err
                    return f.read(a,w)
                r=f.run(read); self.assertIn(r['status'],('short-read','read-error')); self.assertEqual(len(f.calls),pos+1)
                self.assertEqual(r['records'][-1]['returned'], 0 if f.calls[-1][1] == 1 else 1)
    def test_changed_replay_and_deadline(self):
        f=Fixture(1)
        def changed(a,w):
            data,err=f.read(a,w)
            return (bytes([data[0]^1])+data[1:],err) if len(f.calls)==39 else (data,err)
        r=f.run(changed); self.assertEqual(r['status'],'read-set-changed'); self.assertFalse(r['twoReadSetsEqual'])
        f=Fixture(); r=s.collect(s.Reader(f.read,time.monotonic()-1),f.base); self.assertEqual(r['status'],'deadline'); self.assertEqual(f.calls,[])
        r=s.collect(s.Reader(f.read,time.monotonic()+1,lambda:True),f.base); self.assertEqual(r['status'],'cancelled')
    def test_inverse_indices_and_each_field_gate(self):
        f=Fixture(1); r=f.run(); self.assertEqual(r['status'],'completed')
        inputs=r['observedCodecInputs']; self.assertEqual(inputs['seed'],0x13579bdf)
        self.assertEqual(inputs['fields']['field203373']['indices'],[0x49a,0x5fc,0x656,0x730])
        self.assertEqual(inputs['fields']['field203376']['indices'],[0x4ed,0x58b,0x621,0x747])
        self.assertEqual([a for a,w in f.calls if w==1 and f.table+0x20 <= a < f.table+0x820][:8],
                         [f.table+0x20+i for group in f.inverse_indices for i in group])
        for length,partial,reads in [(0x49a,{},0),(0x5fc,{},0),(0x656,{},0),(0x730,{},0),(0x731,{'field203373':0x12345678},4),(0x747,{'field203373':0x12345678},4)]:
            f=Fixture(1); f.put(f.table+0x18,length,4); r=f.run(); self.assertEqual(r['status'],'inverse-index-out-of-range')
            self.assertEqual(r.get('partialConditionalGetterU32',{}),partial)
            self.assertEqual(sum(row['name'].startswith('inverse-field') for row in r['records']),reads)
            self.assertNotIn('conditionalGetterU32',r)
        f=Fixture(1); f.put(f.table+0x18,0x748,4); self.assertEqual(f.run()['status'],'completed')
    def test_null_table_and_codec_replay_changes(self):
        f=Fixture(1); f.put(f.root+0xe970,0,8); r=f.run(); self.assertEqual(r['status'],'null-inverse-table')
        self.assertEqual(r['observedCodecInputs']['table'],0); self.assertNotIn('conditionalGetterU32',r)
        for target in ('table','length','seed','inverse'):
            f=Fixture(1); addresses={'table':f.root+0xe970,'length':f.table+0x18,'seed':f.base+0x5872d30,'inverse':f.table+0x20+0x49a}
            seen={'n':0}
            def read(a,w):
                data,err=f.read(a,w)
                if a==addresses[target]:
                    seen['n']+=1
                    if seen['n']==2: data=bytes([data[0]^1])+data[1:]
                return data,err
            r=f.run(read); self.assertEqual(r['status'],'read-set-changed'); self.assertFalse(r['twoReadSetsEqual'])
            self.assertNotIn('conditionalGetterU32',r)
    def test_pointer_null_stops(self):
        for which in ('root','cache','P'):
            f=Fixture(1); addr={'root':f.base+0x586b600,'cache':f.root+0x21f60,'P':f.entries+0x30}[which]
            f.put(addr,0,8); self.assertEqual(f.run()['status'],{'root':'cold-root','cache':'cold-cache','P':'null-P'}[which])

class FakeAPI:
    def __init__(self,target,size,fixture):
        self.target=target; self.size=size; self.fixture=fixture; self.closed=[]; self.creation=123; self.module_base=fixture.base
        self.fail_first=False; self.fail_close=False
    def open(self,rights,pid): return 'process'
    def times(self,h): return self.creation
    def image(self,h): return str(self.target)
    def wait(self,h): return 258
    def exitcode(self,h): return 259
    def snapshot(self,pid): return 'snapshot'
    def first(self,h):
        if self.fail_first: raise OSError('first failure')
        return {'name':'YuanShen.exe','path':str(self.target),'base':self.module_base,'size':self.size}
    def close(self,h): self.closed.append(h); return not self.fail_close
    def read(self,h,a,w): return self.fixture.read(a,w)

class IdentityTests(unittest.TestCase):
    def setup_fake(self):
        f=Fixture(); target=(s.ROOT/'fixture'/'YuanShen.exe').resolve(); api=FakeAPI(target,42,f)
        ledger=s.Ledger('identity',time.monotonic()+60); expected=s.identity(api,7,target,42,ledger)
        return api,target,expected,ledger
    def test_identity_exact_11_and_sample_exact_68(self):
        api,target,expected,i=self.setup_fake(); self.assertEqual(sum(i.calls.values()),11)
        ledger=s.Ledger('sample',time.monotonic()+60); r=s.sample(api,7,target,42,expected,ledger)
        self.assertEqual(r['status'],'completed'); self.assertEqual(sum(ledger.calls.values()),68)
        self.assertEqual(ledger.calls['ReadProcessMemory'],52); self.assertEqual(api.closed,['snapshot','process','snapshot','snapshot','process'])
        self.assertEqual(ledger.calls,s.LIMITS['sample'])
    def test_mismatch_and_first_failure_close_without_read(self):
        api,target,expected,_=self.setup_fake(); api.creation+=1; ledger=s.Ledger('sample',time.monotonic()+60)
        r=s.sample(api,7,target,42,expected,ledger); self.assertEqual(r['status'],'postcheck-failed'); self.assertNotIn('ReadProcessMemory',ledger.calls); self.assertEqual(api.closed[-1],'process')
        api,target,expected,_=self.setup_fake(); api.fail_first=True; ledger=s.Ledger('sample',time.monotonic()+60)
        r=s.sample(api,7,target,42,expected,ledger); self.assertEqual(r['status'],'postcheck-failed'); self.assertEqual(api.closed[-3:],['snapshot','snapshot','process']); self.assertNotIn('ReadProcessMemory',ledger.calls)
    def test_close_fail_not_reported_as_success(self):
        api,target,expected,_=self.setup_fake(); api.fail_close=True; ledger=s.Ledger('sample',time.monotonic()+60)
        r=s.sample(api,7,target,42,expected,ledger); self.assertEqual(r['status'],'process-close-failed'); self.assertFalse(r['cleanupObserved']); self.assertEqual(sum(x=='process' for x in api.closed),2)
        self.assertTrue(any(x['api']=='CloseProcess' and not x['returned'] for x in ledger.records))
    def test_cancel_during_sample_still_rechecks_and_closes(self):
        api,target,expected,_=self.setup_fake(); cancelled={'value':False}; original=api.read
        def read(h,a,w):
            value=original(h,a,w); cancelled['value']=True; return value
        api.read=read; ledger=s.Ledger('sample',time.monotonic()+60,lambda:cancelled['value'])
        r=s.sample(api,7,target,42,expected,ledger)
        self.assertEqual(r['status'],'cancelled'); self.assertEqual(ledger.calls['ReadProcessMemory'],1)
        self.assertEqual(ledger.calls['GetProcessTimes'],2); self.assertEqual(ledger.calls['Module32FirstW'],2)
        self.assertEqual(api.closed[-1],'process')
    def assert_late_stop(self, trigger):
        api,target,expected,_=self.setup_fake(); state={'cancel':False,'now':100.0,'times':0}
        read=api.read; times=api.times
        def trip():
            if trigger.endswith('cancel'): state['cancel']=True
            else: state['now']=200.0
        def tracked_read(h,a,w):
            value=read(h,a,w)
            if trigger.startswith('last-read') and len(api.fixture.calls)==52: trip()
            return value
        def tracked_times(h):
            value=times(h); state['times']+=1
            if trigger.startswith('post') and state['times']==2: trip()
            return value
        api.read=tracked_read; api.times=tracked_times
        with patch.object(s.time,'monotonic',side_effect=lambda:state['now']):
            ledger=s.Ledger('sample',200.0,lambda:state['cancel'])
            r=s.sample(api,7,target,42,expected,ledger)
        self.assertEqual(r['status'],'cancelled' if trigger.endswith('cancel') else 'deadline')
        self.assertNotIn('conditionalGetterU32',r)
        self.assertEqual(r['partialConditionalGetterU32'],{'field203373':0x12345678,'field203376':0xa1b2c3d4})
        self.assertEqual((r['reads'],r['requestedBytes'],r['returnedBytes']),(52,298,298))
        self.assertTrue(r['twoReadSetsEqual']); self.assertEqual(ledger.calls,s.LIMITS['sample'])
        self.assertEqual(api.closed[-3:],['snapshot','snapshot','process'])
        self.assertTrue(all(row['returned'] for row in ledger.records if row['api'].startswith('Close')))

    def test_cancel_at_last_read_rechecks_and_closes(self): self.assert_late_stop('last-read-cancel')
    def test_cancel_during_postcheck_rechecks_and_closes(self): self.assert_late_stop('post-cancel')
    def test_deadline_at_last_read_rechecks_and_closes(self): self.assert_late_stop('last-read-deadline')
    def test_deadline_during_postcheck_rechecks_and_closes(self): self.assert_late_stop('post-deadline')

    def test_post_identity_and_process_close_failure_withhold_complete_getter(self):
        api,target,expected,_=self.setup_fake(); calls={'n':0}
        def times(h):
            calls['n']+=1; return 123 if calls['n']==1 else 124
        api.times=times; ledger=s.Ledger('sample',time.monotonic()+60)
        r=s.sample(api,7,target,42,expected,ledger)
        self.assertEqual(r['status'],'postcheck-failed'); self.assertNotIn('conditionalGetterU32',r)
        self.assertTrue(r['twoReadSetsEqual']); self.assertEqual(ledger.calls['ReadProcessMemory'],52)
        api,target,expected,_=self.setup_fake(); original=api.close
        def close(h):
            original(h); return h!='process'
        api.close=close; ledger=s.Ledger('sample',time.monotonic()+60)
        r=s.sample(api,7,target,42,expected,ledger)
        self.assertEqual(r['status'],'process-close-failed'); self.assertNotIn('conditionalGetterU32',r)
        self.assertFalse(r['cleanupObserved']); self.assertEqual(ledger.calls['ReadProcessMemory'],52)
    def test_failed_rpm_records_false_bool_and_closes(self):
        api,target,expected,_=self.setup_fake(); api.read=lambda h,a,w: (b'',{'winerror':299})
        ledger=s.Ledger('sample',time.monotonic()+60); r=s.sample(api,7,target,42,expected,ledger)
        self.assertEqual(r['status'],'read-error'); self.assertEqual(r['records'][0]['error'],{'winerror':299})
        self.assertFalse(next(row for row in ledger.records if row['api']=='ReadProcessMemory')['returned'])
        self.assertEqual(api.closed[-1],'process')
    def test_budget_checked_before_api(self):
        ledger=s.Ledger('identity',time.monotonic()+60); calls=[]
        ledger.call('OpenProcess',lambda: calls.append(1))
        with self.assertRaises(s.Stop): ledger.call('OpenProcess',lambda: calls.append(2))
        self.assertEqual(calls,[1])

@unittest.skipUnless(os.name=='nt' and os.environ.get('PROUD_CACHE_SELF_TEST')=='1','explicit self-process only')
class Win32SelfTest(unittest.TestCase):
    def test_only_self_process_read_and_actual_close(self):
        kernel=C.WinDLL('kernel32',use_last_error=True); kernel.GetCurrentProcessId.restype=W.DWORD
        cell=C.create_string_buffer(b'\x78\x56\x34\x12'); api=s.WinAPI(); ledger=s.Ledger('sample',time.monotonic()+60)
        h=ledger.call('OpenProcess',api.open,0x101010,kernel.GetCurrentProcessId())
        try:
            reader=s.Reader(lambda a,w: ledger.call('ReadProcessMemory',api.read,h,a,w),time.monotonic()+60)
            self.assertEqual(reader.number('self',C.addressof(cell),4),0x12345678)
            self.assertEqual((len(reader.records),reader.records[0]['returned']),(1,4))
        finally:
            self.assertTrue(ledger.call('CloseProcess',api.close,h,mandatory=True))
        self.assertTrue(ledger.records[-1]['returned'])


    def test_self_process_full_two_pass_and_snapshot_first(self):
        kernel=C.WinDLL('kernel32',use_last_error=True); kernel.GetCurrentProcessId.restype=W.DWORD
        image=C.create_string_buffer(0x587c2c0-0x586b600+8)
        root=C.create_string_buffer(0x21f68); cache=C.create_string_buffer(0x30)
        entries=C.create_string_buffer(0x20+24*4); buckets=C.create_string_buffer(0x20+4*7)
        obj=C.create_string_buffer(0x48); managed=C.create_string_buffer(0x94); table=C.create_string_buffer(0x820)
        def put(buffer,off,value,width): C.memmove(C.addressof(buffer)+off,value.to_bytes(width,'little'),width)
        put(image,0,C.addressof(root),8); put(root,0x21f60,C.addressof(cache),8)
        put(cache,0x18,C.addressof(entries),8); put(cache,0x20,C.addressof(buckets),8)
        put(buckets,0x18,7,4); put(entries,0x18,4,4); put(buckets,0x20+4*(22101%7),1,4)
        for i in range(4):
            C.memmove(C.addressof(entries)+0x20+24*i,struct.pack('<IiI',22101 if i==3 else 1,-1 if i==3 else i+1,22101 if i==3 else 2),12)
        put(entries,0x20+24*3+0x10,C.addressof(obj),8); put(obj,0x40,C.addressof(managed),8)
        put(managed,0x84,0x89abcdef,4); put(managed,0x90,0xfedcba98,4)
        put(root,0xe970,C.addressof(table),8); put(table,0x18,0x800,4); put(image,0x5872d30-0x586b600,0x13579bdf,4)
        for indices,values in zip(([0x49a,0x5fc,0x656,0x730],[0x4ed,0x58b,0x621,0x747]),([0x12,0x34,0x56,0x78],[0xa1,0xb2,0xc3,0xd4])):
            for index,value in zip(indices,values): put(table,0x20+index,value,1)
        api=s.WinAPI(); ledger=s.Ledger('sample',time.monotonic()+60); pid=kernel.GetCurrentProcessId()
        h=ledger.call('OpenProcess',api.open,0x101010,pid)
        try:
            snap=ledger.call('CreateToolhelp32Snapshot',api.snapshot,pid)
            try:
                row=ledger.call('Module32FirstW',api.first,snap)
                self.assertTrue(row['name'].casefold().startswith('python'))
            finally:
                self.assertTrue(ledger.call('CloseSnapshot',api.close,snap,mandatory=True))
            reader=s.Reader(lambda a,w: ledger.call('ReadProcessMemory',api.read,h,a,w),time.monotonic()+60)
            r=s.collect(reader,C.addressof(image)-0x586b600)
            self.assertEqual(r['status'],'completed'); self.assertEqual((r['reads'],r['requestedBytes']),(52,298))
            self.assertEqual(r['observedEncoded'],{'field203373':0x89abcdef,'field203376':0xfedcba98}); self.assertEqual(r['conditionalGetterU32'],{'field203373':0x12345678,'field203376':0xa1b2c3d4})
        finally:
            self.assertTrue(ledger.call('CloseProcess',api.close,h,mandatory=True))
        self.assertEqual(ledger.calls['ReadProcessMemory'],52)
        self.assertTrue(all(row['returned'] for row in ledger.records if row['api'].startswith('Close')))

if __name__=='__main__': unittest.main()
