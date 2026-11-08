"""Owned input graphs and independent read accounting/failure boundaries."""
import json
import os
from pathlib import Path
import struct
import unittest

from tools import collect72_avatar_runtime_inputs as card

MODULE=0x180000000
CANONICAL=0x21000010000
OWNER=0x41000001000
GENERIC=0x41000003000
SLOTS=0x41000004000
STATICS=0x41000005000
DEFAULT=0x42000001000
DERIVED=0x42000002000
DERIVED_GENERIC=0x42000003000
ORDINARY_TABLE=0x44000001000


class Memory:
    def __init__(self):self.values={};self.calls=[]
    def put(self,address,raw):self.values.update({address+i:value for i,value in enumerate(raw)})
    def number(self,address,value,fmt='Q'):self.put(address,struct.pack('<'+fmt,value))
    def read(self,address,width):
        self.calls.append((address,width))
        try:return bytes(self.values[address+i] for i in range(width)),None
        except KeyError:return b'','owned-unprovided-byte'
    def spans(self):
        result=[];start=previous=None;raw=bytearray()
        for address,value in sorted(self.values.items()):
            if previous is not None and address!=previous+1:
                result.append(dict(address=start,bytesHex=raw.hex()));raw=bytearray();start=None
            if start is None:start=address
            raw.append(value);previous=address
        if start is not None:result.append(dict(address=start,bytesHex=raw.hex()))
        return result


def fixture():
    """Legacy hash places owned keys; all new class fields are explicit inputs."""
    m=Memory();array,typ=CANONICAL+0x1000,CANONICAL+0x2000
    m.put(CANONICAL,struct.pack('<I4xQ',1,array));m.number(array,typ)
    m.put(typ,struct.pack('<QII',20060,17<<16,0))
    for table,blocks in (('genericInst',0x22000000000),('method',0x23000000000)):
        for rva,value in zip(card.cache.ROOTS[table],(127,blocks,2)):m.number(MODULE+rva,value)
        slots={}
        if table=='genericInst':slots[card.cache.enum_instance_hash()&127]=struct.pack('<Q',CANONICAL)
        else:
            for ordinal,index in enumerate(range(10287,10295)):
                seed=card.cache.method_hash(index,CANONICAL,0)&127
                slot=next((seed+n*(n+1)//2)&127 for n in range(128) if (seed+n*(n+1)//2)&127 not in slots)
                key,value=0x24000000000+ordinal*4096,0x25000000000+ordinal*4096
                slots[slot]=struct.pack('<QQ',key,value);m.put(key,struct.pack('<I4xQQ',index,CANONICAL,0))
                record=bytearray(b'\xa5'*56);struct.pack_into('<Q',record,0,OWNER);struct.pack_into('<I',record,24,index);m.put(value,record)
        for block in (0,1):
            entries=sorted((slot,raw) for slot,raw in slots.items() if slot//64==block)
            packed=blocks+0x10000+block*4096;occupied=sum(1<<(slot&63) for slot,_ in entries)
            m.put(blocks+block*32,struct.pack('<QQQ',packed,occupied,0))
            if entries:m.put(packed,b''.join(raw for _,raw in entries))
    m.number(MODULE+card.ORDINARY_RVA,ORDINARY_TABLE)
    for ordinal,index in enumerate(card.INDICES):
        value,owner=0x45000001000+ordinal*4096,0x43000001000+ordinal*4096
        m.number(ORDINARY_TABLE+index*8,value)
        record=bytearray(b'\x6d'*56);struct.pack_into('<Q',record,0,owner);struct.pack_into('<I',record,24,index)
        m.put(value,record);m.number(owner+0xc9,0,'B')
    for address,generic,definition in ((OWNER,GENERIC,1234),(DERIVED,DERIVED_GENERIC,1239)):
        header=bytearray(208);struct.pack_into('<I',header,0x18,21<<16);struct.pack_into('<Q',header,0x68,generic);header[0xc8]=1
        if address==OWNER:struct.pack_into('<QQ',header,0x30,SLOTS,STATICS)
        m.put(address,header);m.put(generic,struct.pack('<I4xQQQ',definition,CANONICAL,0,address))
    m.number(SLOTS+0x10,OWNER);m.number(STATICS,DEFAULT);m.number(DEFAULT,DERIVED)
    return m


def run(memory,read=None):return card.collect(read or memory.read,MODULE,now=lambda:'owned-clock')


class CollectorTests(unittest.TestCase):
    def test_positive_ordinary_warm_identity_all_full64_and20_new_requests(self):
        m=fixture();r=run(m)
        self.assertEqual(r['status'],'completed');self.assertTrue(r['ordinaryInputComplete'])
        self.assertTrue(r['warmGetterInputComplete']);self.assertTrue(r['warmObjectIdentityComplete'])
        self.assertEqual([x['returnedIndex'] for x in r['ordinaryMethods']['methods']],list(card.INDICES))
        self.assertEqual(r['warmDefault']['getter']['owner'],OWNER)
        self.assertEqual(r['warmDefault']['getter']['default'],DEFAULT)
        self.assertEqual(r['warmDefault']['identity']['classAddress'],DERIVED)
        self.assertEqual((r['budget']['newFirstPassReads'],r['budget']['newFirstPassBytes']),(20,772))
        self.assertEqual(len(m.calls),r['budget']['reads'])

    def test_original_collect_return_and_internal_read_sequence_are_unchanged(self):
        one,two=fixture(),fixture();old=card.cache.collect(one.read,MODULE);r=run(two)
        self.assertEqual(r['cacheCollector'],old)
        self.assertEqual(two.calls[:len(one.calls)],one.calls)
        self.assertEqual(r['records'][:len(one.calls)][-1]['phase'],'cache')
        self.assertEqual(card.cache.sha(Path(card.cache.__file__).read_bytes()),card.PINS['tools/probe72_avatar_method_cache.py'])

    def test_after_extension_final_pass_covers_every_successful_exact_span(self):
        r=run(fixture());rows=r['records'];new=[x for x in rows if x['phase']=='new'];final=[x for x in rows if x['phase']=='final']
        before={(x['address'],x['requested']) for x in rows if x['phase']!='final' and x['error'] is None}
        self.assertEqual({(x['address'],x['requested']) for x in final},before)
        self.assertEqual(len(final),len(before));self.assertGreater(final[0]['ordinal'],new[-1]['ordinal'])
        self.assertTrue(all(x['status']=='unchanged' for x in r['inputConsistency']['finalRepeats']))
        self.assertEqual(r['budget']['requestedBytes'],sum(x['requested'] for x in rows))
        self.assertEqual([x['ordinal'] for x in rows],list(range(1,len(rows)+1)))

    def test_null_ordinary_root_stops_only_ordinary_and_warm_still_collects(self):
        m=fixture();m.number(MODULE+card.ORDINARY_RVA,0);r=run(m)
        self.assertEqual(r['ordinaryMethods']['failure'],'ordinary:root-null')
        self.assertFalse(r['ordinaryMethods']['methods']);self.assertTrue(r['warmObjectIdentityComplete'])
        self.assertFalse(any(a==0 for a,_ in m.calls))

    def test_null_slot_wrong_index_and_null_owner_stop_only_one_index(self):
        for kind in ('slot','index','owner'):
            m=fixture();pointer=0x45000001000
            if kind=='slot':m.number(ORDINARY_TABLE+3870*8,0)
            elif kind=='index':m.number(pointer+24,3871,'I')
            else:m.number(pointer,0)
            r=run(m);methods=r['ordinaryMethods']['methods']
            self.assertEqual(methods[0]['status'],'partial');self.assertTrue(all(x['status']=='collected' for x in methods[1:]))
            self.assertTrue(r['warmObjectIdentityComplete'])
            self.assertFalse(any(a==0 for a,_ in m.calls))

    def test_owner_bit20_records_scalar_and_never_follows_inflation(self):
        m=fixture();m.number(0x43000001000+0xc9,0x20,'B');r=run(m)
        first=r['ordinaryMethods']['methods'][0]
        self.assertEqual(first['failure'],'ordinary:unprovided-owner-inflation')
        self.assertEqual(first['ownerFlag'],0x20);self.assertTrue(r['warmGetterInputComplete'])
        self.assertTrue(all(n in (1,8,16,24,32,56,208) for _,n in m.calls))

    def test_warm_owner_null_flags_kind_byref_or_generic_null_stop_before_pointee(self):
        for kind in ('owner','flag','kind','byref','generic'):
            m=fixture()
            if kind=='owner':m.number(0x25000001000,0)
            elif kind=='flag':m.number(OWNER+0xc8,0,'B')
            elif kind=='kind':m.number(OWNER+0x18,18<<16,'I')
            elif kind=='byref':m.number(OWNER+0x18,(21<<16)|0x40000000,'I')
            else:m.number(OWNER+0x68,0)
            r=run(m);self.assertFalse(r['warmGetterInputComplete']);self.assertTrue(r['ordinaryInputComplete'])
            self.assertFalse(any(x['name']=='warm.owner.genericClass' for x in r['records']))

    def test_generic_definition_full64_context_method_and_backref_are_strict(self):
        for kind in ('definition','context','method','backref'):
            m=fixture()
            if kind=='definition':m.number(GENERIC,1239,'I')
            elif kind=='context':m.number(GENERIC+8,CANONICAL&0xffffffff)
            elif kind=='method':m.number(GENERIC+16,1<<40)
            else:m.number(GENERIC+24,OWNER+(1<<32))
            r=run(m);self.assertFalse(r['warmGetterInputComplete']);self.assertTrue(r['ordinaryInputComplete'])
            self.assertFalse(any(x['name']=='warm.rgctx.classSlot' for x in r['records']))

    def test_wrong_rgctx_null_static_and_null_default_stop_only_warm_stage(self):
        for kind in ('rgctx','slot','static','default'):
            m=fixture()
            if kind=='rgctx':m.number(OWNER+0x30,0)
            elif kind=='slot':m.number(SLOTS+0x10,OWNER+(1<<32))
            elif kind=='static':m.number(OWNER+0x38,0)
            else:m.number(STATICS,0)
            r=run(m);self.assertFalse(r['warmGetterInputComplete']);self.assertTrue(r['ordinaryInputComplete'])
            self.assertFalse(any(x['name']=='warm.default.objectHeader' for x in r['records']))

    def test_identity_null_wrong_definition_context_type_or_flag_preserves_getter_stage(self):
        for kind in ('class','definition','context','type','flag','method','backref','generic'):
            m=fixture()
            if kind=='class':m.number(DEFAULT,0)
            elif kind=='definition':m.number(DERIVED_GENERIC,1237,'I')
            elif kind=='context':m.number(DERIVED_GENERIC+8,CANONICAL+(1<<32))
            elif kind=='type':m.number(DERIVED+0x18,18<<16,'I')
            elif kind=='flag':m.number(DERIVED+0xc8,0,'B')
            elif kind=='method':m.number(DERIVED_GENERIC+16,1<<40)
            elif kind=='backref':m.number(DERIVED_GENERIC+24,DERIVED+(1<<32))
            else:m.number(DERIVED+0x68,0)
            r=run(m);self.assertTrue(r['warmGetterInputComplete']);self.assertFalse(r['warmObjectIdentityComplete'])
            self.assertTrue(r['ordinaryInputComplete'])

    def test_old_return_index_and_canonical_type_errors_cannot_become_warm(self):
        for kind in ('index','type'):
            m=fixture()
            if kind=='index':m.number(0x25000001000+24,10287,'I')
            else:m.number(CANONICAL+0x2000,20061)
            r=run(m);self.assertEqual(r['cacheCollector']['status'],'partial')
            self.assertEqual(r['warmDefault']['getter']['failure'],'warm:cache-dependency-incomplete')
            self.assertFalse(any(x['name'].startswith('warm.') for x in r['records']))
            self.assertTrue(r['ordinaryInputComplete'])

    def test_same_semantics_different_key_pointer_is_not_same_canonical(self):
        m=fixture();alias=0x46000001000;m.put(alias,struct.pack('<I4xQ',1,CANONICAL+0x1000))
        m.number(0x24000001000+8,alias);r=run(m)
        self.assertEqual(r['cacheCollector']['status'],'completed')
        self.assertEqual(r['warmDefault']['getter']['failure'],'warm:selected-key-context-mismatch')
        self.assertTrue(r['ordinaryInputComplete'])

    def test_short_error_oserror_and_overlong_are_retained_and_counted(self):
        for kind in ('short','error','oserror','overlong'):
            m=fixture()
            def read(a,n):
                raw,error=m.read(a,n)
                if a==OWNER and n==208:
                    if kind=='short':return raw[:-1],None
                    if kind=='error':return b'',{'winerror':299}
                    if kind=='oserror':raise OSError(5,'owned')
                    return raw+b'\xff',None
                return raw,error
            r=run(m,read);row=next(x for x in r['records'] if x['name']=='warm.owner.header')
            self.assertIsNotNone(row['error']);self.assertEqual(row['requested'],208)
            self.assertEqual(row['bytesRead'],207 if kind=='short' else 209 if kind=='overlong' else 0)
            self.assertEqual(len(bytes.fromhex(row['bytesHex'])),row['bytesRead'])
            if kind=='oserror':
                self.assertEqual(row['error'],{'winerror':5})
                self.assertEqual(row['backendException']['errno'],5)
            self.assertEqual(r['budget']['reads'],len(r['records']));self.assertFalse(r['warmGetterInputComplete'])
            self.assertEqual(len(r['inputConsistency']['finalRepeats']),r['inputConsistency']['uniqueSpans'])

    def test_real_oserror_in_frozen_cache_keeps_old_error_and_records_actual_errno(self):
        first=MODULE+card.cache.ROOTS['genericInst'][0]
        def reader(memory):
            def read(a,n):
                if a==first:raise OSError(5,'owned')
                return memory.read(a,n)
            return read
        one,two=fixture(),fixture();old=card.cache.collect(reader(one),MODULE);r=run(two,reader(two))
        self.assertEqual(r['cacheCollector'],old)
        row=r['records'][0]
        self.assertEqual(row['error'],old['records'][0]['error'])
        self.assertEqual(row['backendException']['normalizedError'],5)
        self.assertTrue(row['backendException']['frozenLegacyErrorPreserved'])

    def test_final_root_drift_is_after_old_internal_repeat_and_clears_all_ready(self):
        m=fixture();root=MODULE+card.cache.ROOTS['genericInst'][0];count=0
        def read(a,n):
            nonlocal count
            raw,error=m.read(a,n)
            if a==root:
                count+=1
                if count==4:return struct.pack('<Q',255),None
            return raw,error
        r=run(m,read)
        self.assertEqual(r['cacheCollector']['status'],'completed');self.assertEqual(count,4)
        self.assertEqual(r['inputConsistency']['status'],'failed');self.assertFalse(r['ordinaryInputComplete'])
        self.assertFalse(r['warmGetterInputComplete'])
        self.assertEqual(len(r['inputConsistency']['finalRepeats']),r['inputConsistency']['uniqueSpans'])

    def test_old_internal_in_place_drift_blocks_warm_without_replacing_old_result(self):
        m=fixture();count=0
        def read(a,n):
            nonlocal count
            raw,error=m.read(a,n)
            if a==CANONICAL and n==16:
                count+=1
                if count>1:return struct.pack('<I',2)+raw[4:],error
            return raw,error
        r=run(m,read)
        self.assertEqual(r['cacheCollector']['status'],'partial')
        self.assertEqual(r['cacheCollector']['inputConsistency']['status'],'failed')
        self.assertEqual(r['warmDefault']['getter']['failure'],'warm:cache-dependency-incomplete')
        self.assertTrue(r['inputConsistency']['overlapConflicts'])
        self.assertFalse(any(x['name'].startswith('warm.') for x in r['records']))

    def test_final_new_span_error_does_not_abort_remaining_read_list(self):
        m=fixture();count=0
        def read(a,n):
            nonlocal count
            raw,error=m.read(a,n)
            if a==OWNER and n==208:
                count+=1
                if count==2:return b'',299
            return raw,error
        r=run(m,read);final=r['inputConsistency']['finalRepeats']
        self.assertEqual(len(final),r['inputConsistency']['uniqueSpans']);self.assertTrue(any(x['status']=='failed' for x in final))
        self.assertEqual(final[-1]['status'],'unchanged');self.assertFalse(r['warmObjectIdentityComplete'])

    def test_cross_width_overlap_disagreement_is_rejected(self):
        m=fixture();m.number(0x45000001000,OWNER)
        def read(a,n):
            raw,error=m.read(a,n)
            if a==OWNER and n==208:
                raw=bytearray(raw);raw[0xc9]=1;return bytes(raw),error
            return raw,error
        r=run(m,read)
        self.assertTrue(r['inputConsistency']['overlapConflicts'])
        self.assertIn('overlap-disagrees',r['warmDefault']['getter']['failure'])
        self.assertFalse(r['ordinaryInputComplete'])

    def test_old_invalid_address_local_reject_is_a_counted_non_backend_record(self):
        m=fixture();m.number(MODULE+card.cache.ROOTS['genericInst'][1],card.U64-2);r=run(m)
        local=[x for x in r['records'] if x['phase']=='cache' and not x['backendCalled']]
        self.assertTrue(local);self.assertTrue(all(x['error']=='address-outside-uint64' for x in local))
        self.assertEqual(r['budget']['reads'],len(m.calls)+len(local))
        self.assertEqual(r['budget']['requestedBytes'],sum(x['requested'] for x in r['records']))

    def test_input_is_never_written_and_all_runtime_claims_remain_false(self):
        m=fixture();before=dict(m.values);r=run(m);self.assertEqual(m.values,before)
        self.assertTrue(r['candidateGraphOnly'])
        for key in ('atomicSnapshot','naturalLookupObserved','naturalCallerObserved','naturalWriterExcluded',
                'externalWritesExecuted','remoteCallExecuted','nativeCpuExecuted','newInitializedOrLoadedAllFlagsSupplied',
                'completeColdComparer','completeClassInitialization','defaultComparerWriteExecuted','completeAvatarHandler',
                'targetProtocolReady','d02Complete'):
            self.assertFalse(r[key],key)


class BudgetAndSourceTests(unittest.TestCase):
    def test_independent_conservative_sum_and_max_single(self):
        self.assertEqual((card.MAX_READS,card.MAX_BYTES,card.MAX_SINGLE),(3432+20+1716+20,58944+772+29472+772,208))
        self.assertEqual((13+7,268+504),(card.NEW_READS,card.NEW_BYTES))

    def test_last_request_at_count_bytes_and_single_limit_then_no_overrun(self):
        calls=[]
        def read(a,n):calls.append((a,n));return bytes(n),None
        ledger=card.Ledger(read,lambda:'owned');ledger.records=[None]*(card.MAX_READS-1);ledger.requested=card.MAX_BYTES-208
        self.assertEqual(len(ledger.get('last',0x1000,208,'final')),208)
        with self.assertRaisesRegex(card.ReadStop,'budget'):ledger.get('extra',0x2000,1,'final')
        self.assertEqual(calls,[(0x1000,208)])
        ledger=card.Ledger(read,lambda:'owned')
        with self.assertRaisesRegex(card.ReadStop,'budget'):ledger.get('wide',0x1000,209)

    def test_new_stage_budget_and_invalid_address_are_accounted(self):
        ledger=card.Ledger(lambda a,n:(bytes(n),None),lambda:'owned');ledger.new_reads=20
        with self.assertRaisesRegex(card.ReadStop,'new-input-budget'):ledger.get('extra',1,1)
        ledger.new_reads=0;ledger.new_bytes=772
        with self.assertRaisesRegex(card.ReadStop,'new-input-budget'):ledger.get('extra',1,1)
        ledger.new_bytes=0
        with self.assertRaisesRegex(card.ReadStop,'unreadable'):ledger.get('overflow',card.U64-2,8)
        self.assertEqual((len(ledger.records),ledger.requested,ledger.records[0]['backendCalled']),(1,8,False))

    def test_bad_entry_and_backend_shape_are_rejected_without_target_operation(self):
        for module in (0,-1,True,1<<64):
            with self.assertRaises(ValueError):card.collect(lambda a,n:(bytes(n),None),module)
        with self.assertRaises(TypeError):card.collect(lambda a,n:('not bytes',None),MODULE)

    def test_wrong_binary_and_different_earlier_pin_are_rejected(self):
        with self.assertRaisesRegex(ValueError,'executable'):card.verify_sources(b'wrong')
        with self.assertRaisesRegex(ValueError,'Earlier source pin'):
            card.verify_sources(b'wrong',verified_sources={'tools/probe72_avatar_method_cache.py':'bad'})


class OwnedReportTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        path=os.environ.get('CLIENT72_AVATAR_RUNTIME_INPUTS_REPORT',str(card.RAW/'owned-first.json'))
        cls.report=json.loads(Path(path).read_bytes())
    def test_exact_current_source_fixed_pins_and_only_two_old_windows(self):
        d=self.report;self.assertEqual(d['sourceSHA256'],card.cache.sha(Path(card.__file__).read_bytes()))
        for path,expected in d['fixedSources']['sourcePins'].items():
            self.assertEqual(card.cache.sha((card.ROOT/path).read_bytes()),expected,path)
        self.assertEqual([x['start'] for x in d['fixedSources']['windows']],['0x140526fb0','0x157d7fe90'])
        self.assertEqual(d['fixedSources']['newNativeWindows'],0);self.assertFalse(d['fixedSources']['nativeCpuExecuted'])
    def test_formal_owned_proof_positive_and_no_natural_or_windows_claim(self):
        d=self.report;r=d['result'];self.assertEqual(r['status'],'completed')
        self.assertTrue(r['ordinaryInputComplete'] and r['warmGetterInputComplete'] and r['warmObjectIdentityComplete'])
        self.assertFalse(d['liveSamplingStarted']);self.assertFalse(d['naturalInputSupplied']);self.assertFalse(d['windowsAdapterImplemented'])
        self.assertEqual(r['budget']['newFirstPassBytes'],772);self.assertLessEqual(r['budget']['reads'],5188)


if __name__=='__main__':unittest.main()
