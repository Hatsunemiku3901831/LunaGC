"""Owned warm usage graph, primitive semantics and complete final-repeat bounds."""
import copy
import json
import os
from pathlib import Path
import struct
import unittest
from unittest.mock import patch

from tools import collect72_avatar_warm_usages as card
from tools import test_collect72_avatar_runtime_inputs as legacy_fixture

MODULE=legacy_fixture.MODULE
U32_CONTEXT=0x47000001000
LIST=0x48000001000
HASH_U32=0x48000002000
COMPARER_U32=0x48000003000
DEFAULT_U32_CLASS=0x48000004000
HASH_ENUM=0x48000005000
CARRIER=0x49000001000
CTOR=0x49000002000
CTOR_TARGET=0x8000000100001234
DEFAULT_U32=0x4a000001000
U32_STATICS=0x4a000002000
ENUM_SLOTS=0x4a000003000
STRING_CONTEXT=0x57000001000
STRING_OWNER=0x58000001000
STRING_DERIVED=0x58000002000
STRING_MI_OWNER=0x58000003000
STRING_GETTER=0x59000001000
STRING_EQUALS=0x59000002000
STRING_DEFAULT=0x5a000001000
STRING_STATICS=0x5a000002000
STRING_SLOTS=0x5a000003000
STRING_FUNCTION=0x9000000100001234
STRING_MI_CODE=0x9000000200005678
EXPECTED_STRING_EQUALS={1235:(1235,10296),1237:(1237,10310),1239:(1239,10324),
    1240:(1239,10324),1241:(1239,10324),1242:(1242,10333),1243:(1243,10339)}


def fixture():
    m=legacy_fixture.fixture()
    m.put(U32_CONTEXT,struct.pack('<I4xQ',1,U32_CONTEXT+0x100));m.number(U32_CONTEXT+0x100,U32_CONTEXT+0x200)
    m.put(U32_CONTEXT+0x200,struct.pack('<QII',0xfedcba9876543210,9<<16,0))
    for pointer,definition,context in ((LIST,1255,U32_CONTEXT),(HASH_U32,2147,U32_CONTEXT),
            (COMPARER_U32,1234,U32_CONTEXT),(DEFAULT_U32_CLASS,1235,U32_CONTEXT),(HASH_ENUM,2147,legacy_fixture.CANONICAL)):
        header=bytearray(208);struct.pack_into('<I',header,0x18,21<<16);header[0xc8]=1
        struct.pack_into('<Q',header,0x68,pointer+0x10000)
        if pointer==COMPARER_U32:struct.pack_into('<Q',header,0x38,U32_STATICS)
        if pointer==HASH_ENUM:struct.pack_into('<Q',header,0x30,ENUM_SLOTS)
        m.put(pointer,header);m.put(pointer+0x10000,struct.pack('<I4xQQQ',definition,context,0,pointer))
    for name,pointer in (('listU32',LIST),('listEmptyItems',0x4b000001000),('hashSetU32',HASH_U32),
            ('u32Comparer',COMPARER_U32),('hashSetEnum',HASH_ENUM),('enumCarrier',CARRIER)):
        m.number(MODULE+card.ROOTS[name],pointer)
    m.number(U32_STATICS,DEFAULT_U32);m.number(DEFAULT_U32,DEFAULT_U32_CLASS)
    for pointer,index in ((CARRIER,15590),(CTOR,15591)):
        raw=bytearray(56);struct.pack_into('<QQ',raw,0,HASH_ENUM,CTOR_TARGET);struct.pack_into('<I',raw,24,index);m.put(pointer,raw)
    m.put(ENUM_SLOTS+0x10,struct.pack('<QQ',0x25000001000,CTOR))
    m.number(MODULE+card.ROOTS['stringGetter'],0)
    return m


def string_fixture(derived=1240):
    m=fixture();m.number(MODULE+card.ROOTS['stringGetter'],STRING_GETTER)
    m.put(STRING_CONTEXT,struct.pack('<I4xQ',1,STRING_CONTEXT+0x100));m.number(STRING_CONTEXT+0x100,STRING_CONTEXT+0x200)
    m.put(STRING_CONTEXT+0x200,struct.pack('<QII',0x8000000100000001,(14<<16)|0xff,0xffffffff))
    equals_owner,method=EXPECTED_STRING_EQUALS[derived];owner_pointer=STRING_MI_OWNER if equals_owner!=derived else STRING_DERIVED
    for pointer,definition in ((STRING_OWNER,1234),(STRING_DERIVED,derived),(owner_pointer,equals_owner)):
        header=bytearray(208);header[0xc8]=1;struct.pack_into('<H',header,0xbe,12)
        if definition==1243:struct.pack_into('<QI',header,0x10,1243,18<<16)
        else:
            struct.pack_into('<I',header,0x18,21<<16);struct.pack_into('<Q',header,0x68,pointer+0x10000)
            m.put(pointer+0x10000,struct.pack('<I4xQQQ',definition,STRING_CONTEXT,0,pointer))
        if pointer==STRING_OWNER:struct.pack_into('<QQ',header,0x30,STRING_SLOTS,STRING_STATICS)
        m.put(pointer,header)
    m.number(STRING_SLOTS+0x10,STRING_OWNER);m.number(STRING_STATICS,STRING_DEFAULT);m.number(STRING_DEFAULT,STRING_DERIVED)
    m.number(STRING_DERIVED+0x110,STRING_FUNCTION);m.number(STRING_DERIVED+0x110+12*8,STRING_EQUALS)
    for pointer,owner,index,code in ((STRING_GETTER,STRING_OWNER,10288,0x900000030000abcd),
            (STRING_EQUALS,owner_pointer,method,STRING_MI_CODE)):
        raw=bytearray(56);struct.pack_into('<QQ',raw,0,owner,code);struct.pack_into('<I',raw,24,index);m.put(pointer,raw)
    return m


def run(memory,read=None):return card.collect(read or memory.read,MODULE,now=lambda:'owned-clock')


class UsageTests(unittest.TestCase):
    def test_positive_all_actual_types_and_constructor_pointer_full64(self):
        m=fixture();r=run(m);self.assertEqual(r['status'],'completed');self.assertTrue(all(r['candidateInputs'].values()))
        self.assertEqual(r['listU32']['class']['definition'],1255);self.assertEqual(r['hashSetU32']['class']['definition'],2147)
        self.assertEqual(r['u32Default']['identity']['classInfo']['definition'],1235)
        self.assertEqual(r['u32Default']['identity']['classInfo']['observedDefaultName'],'GenericEqualityComparer`1')
        self.assertEqual(r['enumCaller']['constructor']['methodDefinition'],15591)
        self.assertEqual(r['enumCaller']['constructor']['functionPointer'],CTOR_TARGET)
        self.assertFalse(r['enumCaller']['constructor']['functionPointerRewritten'])
        self.assertFalse(r['enumCaller']['constructor']['executableTargetValidated'])
        self.assertLessEqual(r['budget']['newFirstPassReads'],42);self.assertLessEqual(r['budget']['newFirstPassBytes'],2296)

    def test_only_cache_base_result_preserved_and_no_ordinary_four_read(self):
        one,two=fixture(),fixture();old=card.cache.collect(one.read,MODULE);r=run(two)
        self.assertEqual(old,r['cacheCollector']);self.assertEqual(two.calls[:len(one.calls)],one.calls)
        self.assertFalse(any(a==MODULE+0x5bc8f20 for a,n in two.calls))
        self.assertEqual(card.cache.sha(Path(card.cache.__file__).read_bytes()),'6053f724185e2b2c7e75503021beb2f5cbde18319c7aac6edb1dfec7ed7d8544')

    def test_primitive_data_and_attributes_do_not_become_definition_or_hash_input(self):
        a=card.type_semantics(struct.pack('<QII',0x13f,9<<16,0),'u32')
        b=card.type_semantics(struct.pack('<QII',0x8000000100000001,(9<<16)|0xffff,0xffffffff),'u32')
        self.assertEqual(a['typeHash'],b['typeHash']);self.assertFalse(a['dataUsedByOriginal']);self.assertFalse(b['dataUsedByOriginal'])
        for data in (0,0x13f,0xffffffffffffffff):
            m=fixture();m.number(U32_CONTEXT+0x200,data);self.assertTrue(run(m)['candidateInputs']['u32Default'])

    def test_wrong_kind_byref_count_argument_array_or_type_pointer_stops_source_path(self):
        for kind in ('kind','byref','count','array','type'):
            m=fixture()
            if kind=='kind':m.number(U32_CONTEXT+0x200+8,17<<16,'I')
            elif kind=='byref':m.number(U32_CONTEXT+0x200+8,(9<<16)|0x40000000,'I')
            elif kind=='count':m.number(U32_CONTEXT,2,'I')
            elif kind=='array':m.number(U32_CONTEXT+8,0)
            else:m.number(U32_CONTEXT+0x100,0)
            r=run(m);self.assertFalse(r['candidateInputs']['listClass']);self.assertTrue(r['candidateInputs']['enumWarmGetter'])
            self.assertFalse(any(a==0 for a,n in m.calls))

    def test_null_roots_stop_their_paths_without_blocking_independent_warm(self):
        for name in ('listU32','hashSetU32','u32Comparer','listEmptyItems'):
            m=fixture();m.number(MODULE+card.ROOTS[name],0);r=run(m)
            self.assertEqual(r['u32Default' if name=='u32Comparer' else name]['status'],'partial');self.assertTrue(r['candidateInputs']['enumWarmGetter'])
            self.assertFalse(any(a==0 for a,n in m.calls))

    def test_definition_backref_method_inst_or_flag_errors_do_not_get_followed(self):
        for kind in ('definition','backref','method','flag'):
            m=fixture()
            if kind=='definition':m.number(LIST+0x10000,2147,'I')
            elif kind=='backref':m.number(LIST+0x10000+24,LIST+(1<<32))
            elif kind=='method':m.number(LIST+0x10000+16,0x8000000100000001)
            else:m.number(LIST+0xc8,0,'B')
            r=run(m);self.assertFalse(r['candidateInputs']['listClass']);self.assertTrue(r['candidateInputs']['u32Default'])

    def test_default_actual_derived_definition_is_read_not_borrowed_from_enum(self):
        m=fixture();m.number(DEFAULT_U32_CLASS+0x10000,1237,'I');r=run(m)
        self.assertTrue(r['candidateInputs']['u32Default'])
        self.assertEqual(r['u32Default']['identity']['classInfo']['observedDefaultName'],'ObjectEqualityComparer`1')
        self.assertFalse(r['u32Default']['factorySelectionProved'])
        m=fixture();m.number(DEFAULT_U32_CLASS+0x10000,1236,'I');r=run(m)
        self.assertFalse(r['candidateInputs']['u32Default']);self.assertEqual(r['u32Default']['getterStatus'],'collected')

    def test_uint32_default_null_static_default_or_derived_context_and_identity_flags(self):
        for kind in ('static','default','class','context','flag'):
            m=fixture()
            if kind=='static':m.number(COMPARER_U32+0x38,0)
            elif kind=='default':m.number(U32_STATICS,0)
            elif kind=='class':m.number(DEFAULT_U32,0)
            elif kind=='context':m.number(DEFAULT_U32_CLASS+0x10000+8,U32_CONTEXT+(1<<32))
            else:m.number(DEFAULT_U32_CLASS+0xc8,0,'B')
            r=run(m);self.assertFalse(r['candidateInputs']['u32Default']);self.assertTrue(r['candidateInputs']['enumCaller'])

    def test_distinct_uint32_instances_with_same_low32_are_not_aliases(self):
        m=fixture();other=U32_CONTEXT+(1<<32)
        for off,width in ((0,16),(0x100,8),(0x200,16)):
            raw,_=m.read(U32_CONTEXT+off,width);m.put(other+off,raw)
        m.number(other+8,other+0x100);m.number(other+0x100,other+0x200)
        m.number(HASH_U32+0x10000+8,other);r=run(m)
        self.assertTrue(r['candidateInputs']['hashSetU32Class'])
        self.assertEqual(r['hashSetU32']['class']['context'],other);self.assertEqual(r['listU32']['class']['context'],U32_CONTEXT)

    def test_enum_canonical_and_carrier_owner_low32_collisions_rejected(self):
        for kind in ('context','carrier','ctor'):
            m=fixture()
            if kind=='context':m.number(HASH_ENUM+0x10000+8,legacy_fixture.CANONICAL+(1<<32))
            elif kind=='carrier':m.number(CARRIER,HASH_ENUM+(1<<32))
            else:m.number(CTOR,HASH_ENUM+(1<<32))
            r=run(m);self.assertFalse(r['candidateInputs']['enumCaller']);self.assertTrue(r['candidateInputs']['u32Default'])

    def test_lazy_null_slots_wrong_getter_ctor_index_or_function_pointer(self):
        for kind in ('rgctx','getter','ctor','index','code'):
            m=fixture()
            if kind=='rgctx':m.number(HASH_ENUM+0x30,0)
            elif kind=='getter':m.number(ENUM_SLOTS+0x10,0x25000001000+(1<<32))
            elif kind=='ctor':m.number(ENUM_SLOTS+0x18,0)
            elif kind=='index':m.number(CTOR+24,15590,'I')
            else:m.number(CTOR+8,0)
            r=run(m);self.assertFalse(r['candidateInputs']['enumCaller']);self.assertTrue(r['candidateInputs']['enumWarmGetter'])

    def test_enum_default_null_does_not_create_comparer_or_read_ctor(self):
        m=fixture();m.number(legacy_fixture.STATICS,0);r=run(m)
        self.assertFalse(r['candidateInputs']['enumCaller']);self.assertFalse(r['candidateInputs']['enumWarmGetter'])
        self.assertFalse(any(x['name']=='enumCarrier.constructorMethodInfo' for x in r['records']))

    def test_every_successful_unique_is_repeated_after_all_new_acquisition(self):
        r=run(fixture());rows=r['records'];before={(x['address'],x['requested']) for x in rows if x['phase']!='final' and x['error'] is None}
        final=[x for x in rows if x['phase']=='final'];new=[x for x in rows if x['phase']=='new']
        self.assertEqual({(x['address'],x['requested']) for x in final},before)
        self.assertEqual(len(final),len(before));self.assertGreater(final[0]['ordinal'],new[-1]['ordinal'])
        self.assertTrue(all(x['status']=='unchanged' for x in r['inputConsistency']['finalRepeats']))

    def test_final_old_and_new_span_drift_rejected_without_aborting_read_list(self):
        for kind in ('old','new'):
            m=fixture();address=MODULE+card.cache.ROOTS['genericInst'][0] if kind=='old' else LIST;count=0
            def read(a,n):
                nonlocal count
                raw,error=m.read(a,n)
                if a==address and n==(8 if kind=='old' else 208):
                    count+=1
                    if count==(4 if kind=='old' else 2):return bytes([raw[0]^1])+raw[1:],None
                return raw,error
            r=run(m,read);self.assertEqual(r['inputConsistency']['status'],'failed');self.assertFalse(any(r['candidateInputs'].values()))
            self.assertEqual(len(r['inputConsistency']['finalRepeats']),r['inputConsistency']['uniqueSpans'])

    def test_short_error_oserror_and_overlap_are_retained_and_counted(self):
        for kind in ('short','error','oserror','overlap'):
            m=fixture()
            def read(a,n):
                raw,error=m.read(a,n)
                if a==LIST and n==208:
                    if kind=='short':return raw[:-1],None
                    if kind=='error':return b'',299
                    if kind=='oserror':raise OSError(5,'owned')
                if kind=='overlap' and a==U32_CONTEXT and n==16:return struct.pack('<I',2)+raw[4:],None
                return raw,error
            r=run(m,read);self.assertEqual(r['budget']['reads'],len(r['records']))
            self.assertEqual(r['budget']['requestedBytes'],sum(x['requested'] for x in r['records']))
            self.assertFalse(r['candidateInputs']['listClass'])
            if kind=='oserror':self.assertEqual(next(x for x in r['records'] if x['name']=='listU32.header')['error'],{'winerror':5})

    def test_no_input_write_or_runtime_success_claim(self):
        m=fixture();before=dict(m.values);r=run(m);self.assertEqual(before,m.values)
        for key in ('atomicSnapshot','naturalLookupObserved','naturalCallerObserved','naturalWriterExcluded','externalWritesExecuted',
                'remoteCallExecuted','nativeCpuExecuted','initializedOrLoadedAllAssumed','prototypePointersModified','ordinaryFourMethodsRequested',
                'fullAvatarAllocationVerified','completeAvatarConstructor','completeAvatarHandler','g2Verified','d02Complete'):
            self.assertFalse(r[key],key)


class BudgetTests(unittest.TestCase):
    def test_exact_cache_only_conservative_bound(self):
        self.assertEqual((card.MAX_READS,card.MAX_BYTES,card.MAX_SINGLE),(3432+64+1716+64,58944+4096+29472+4096,208))
        self.assertEqual((6+1+6+13+3+6+7,288+8+288+584+248+376+504),(42,2296))
        self.assertEqual((42+17,2296+920),(59,3216))
    def test_last_request_at_total_boundary_and_no_overrun(self):
        calls=[]
        def read(a,n):calls.append((a,n));return bytes(n),None
        ledger=card.Ledger(read,lambda:'owned');ledger.records=[None]*(card.MAX_READS-1);ledger.requested=card.MAX_BYTES-208
        ledger.get('last',1,208,'final')
        with self.assertRaises(card.ReadStop):ledger.get('extra',1,1,'final')
        self.assertEqual(calls,[(1,208)])
    def test_new_stage_count_bytes_and_maxsingle(self):
        ledger=card.Ledger(lambda a,n:(bytes(n),None),lambda:'owned');ledger.new_reads=64
        with self.assertRaises(card.ReadStop):ledger.get('extra',1,1)
        ledger.new_reads=0;ledger.new_bytes=4096
        with self.assertRaises(card.ReadStop):ledger.get('extra',1,1)
        ledger.new_bytes=0
        with self.assertRaises(card.ReadStop):ledger.get('wide',1,209)
        self.assertFalse(ledger.records)

    def test_true_partial_overlap_and_local_invalid_address_are_retained(self):
        calls=[]
        def read(a,n):
            calls.append((a,n));return bytes([1 if n==8 else 2])*n,None
        ledger=card.Ledger(read,lambda:'owned');ledger.get('first',0x1010,8)
        with self.assertRaises(card.ReadStop):ledger.get('overlapping-header',0x1000,208)
        with self.assertRaises(card.ReadStop):ledger.get('invalid',1<<64,8)
        self.assertEqual(len(calls),2);self.assertEqual(ledger.records[-1]['error'],'address-outside-uint64')
        self.assertEqual(ledger.conflicts[0]['address'],0x1010)
        self.assertEqual(ledger.final_repeat()['status'],'failed')


class OptionalStringTests(unittest.TestCase):
    def test_inherited_owner_and_actual_function_pointers_are_preserved(self):
        r=run(string_fixture());s=r['optionalString'];self.assertEqual(s['status'],'collected')
        self.assertTrue(r['optionalStringCandidate']);self.assertTrue(all(r['candidateInputs'].values()))
        self.assertEqual((s['identity']['definition'],s['equals']['ownerClass']['definition']),(1240,1239))
        self.assertEqual(s['equalsFunction'],STRING_FUNCTION);self.assertEqual(s['equals']['functionPointer'],STRING_MI_CODE)
        self.assertFalse(s['equals']['functionEqualsSlot']);self.assertFalse(s['equals']['functionPointerRewritten'])
        self.assertEqual(s['equalsMethodInfoSlot'],STRING_DERIVED+0x110+12*8)
        self.assertFalse(s['comparisonExecuted']);self.assertFalse(s['executableTargetValidated'])
        rows=[x for x in r['records'] if x['phase']=='new' and (x['name'].startswith('string.') or x['name']=='stringGetter.usage')]
        self.assertEqual((len(rows),sum(x['requested'] for x in rows)),(17,920))

    def test_generic_and_ordinary_actual_templates_have_distinct_type_rules(self):
        for derived in EXPECTED_STRING_EQUALS:
            r=run(string_fixture(derived));s=r['optionalString'];self.assertTrue(r['optionalStringCandidate'],s)
            self.assertEqual(s['identity']['definition'],derived)
            if derived==1243:
                self.assertEqual(s['identity']['headerKind'],18);self.assertEqual(s['identity']['genericClass'],0)
                self.assertFalse(any(x['name']=='string.derived.genericClass' for x in r['records']))

    def test_string_data_attributes_and_high64_are_ignored_by_original_type_hash(self):
        for data in (0,0x125,0xffffffffffffffff):
            m=string_fixture();m.number(STRING_CONTEXT+0x200,data);r=run(m)
            arg=r['optionalString']['owner']['arguments'];self.assertEqual(int(arg['argumentInstanceHash'],16),7292816085)
            self.assertFalse(arg['argument']['dataUsedByOriginal']);self.assertTrue(r['optionalStringCandidate'])

    def test_string_cannot_reuse_uint32_or_enum_context_or_low32_alias(self):
        for context in (U32_CONTEXT,legacy_fixture.CANONICAL,STRING_CONTEXT+(1<<32)):
            m=string_fixture();m.number(STRING_OWNER+0x10000+8,context);r=run(m)
            self.assertFalse(r['optionalStringCandidate']);self.assertTrue(all(r['candidateInputs'].values()))

    def test_string_wrong_count_kind_byref_or_null_argument_stops_without_new_pointee(self):
        for kind in ('count','kind','byref','array','type'):
            m=string_fixture()
            if kind=='count':m.number(STRING_CONTEXT,2,'I')
            elif kind=='kind':m.number(STRING_CONTEXT+0x208,9<<16,'I')
            elif kind=='byref':m.number(STRING_CONTEXT+0x208,(14<<16)|0x40000000,'I')
            elif kind=='array':m.number(STRING_CONTEXT+8,0)
            else:m.number(STRING_CONTEXT+0x100,0)
            r=run(m);self.assertFalse(r['optionalStringCandidate']);self.assertTrue(all(r['candidateInputs'].values()))

    def test_string_null_or_unwarm_paths_stop_locally(self):
        for kind in ('root','MIowner','MIindex','flag','rgctx','static','slot','default','class','generic','backref','method'):
            m=string_fixture()
            if kind=='root':m.number(MODULE+card.ROOTS['stringGetter'],0)
            elif kind=='MIowner':m.number(STRING_GETTER,0)
            elif kind=='MIindex':m.number(STRING_GETTER+24,10289,'I')
            elif kind=='flag':m.number(STRING_OWNER+0xc8,0,'B')
            elif kind=='rgctx':m.number(STRING_OWNER+0x30,0)
            elif kind=='static':m.number(STRING_OWNER+0x38,0)
            elif kind=='slot':m.number(STRING_SLOTS+0x10,STRING_OWNER+(1<<32))
            elif kind=='default':m.number(STRING_STATICS,0)
            elif kind=='class':m.number(STRING_DEFAULT,0)
            elif kind=='generic':m.number(STRING_OWNER+0x68,0)
            elif kind=='backref':m.number(STRING_OWNER+0x10000+24,STRING_OWNER+(1<<32))
            else:m.number(STRING_OWNER+0x10000+16,1)
            r=run(m);self.assertFalse(r['optionalStringCandidate']);self.assertTrue(all(r['candidateInputs'].values()),kind)

    def test_string_ordinary_does_not_borrow_unknown_generic_or_data(self):
        for kind in ('definition','generic','flag'):
            m=string_fixture(1243)
            if kind=='definition':m.number(STRING_DERIVED+0x10,1244)
            elif kind=='generic':m.number(STRING_DERIVED+0x68,0x8000000100001234)
            else:m.number(STRING_DERIVED+0xc8,0,'B')
            r=run(m);self.assertFalse(r['optionalStringCandidate']);self.assertTrue(all(r['candidateInputs'].values()))

    def test_vtable_count_function_and_companion_slot_are_not_filled(self):
        for kind in ('count','function','companion','MIowner','MIindex','MIcontext'):
            m=string_fixture()
            if kind=='count':m.number(STRING_DERIVED+0xbe,8,'H')
            elif kind=='function':m.number(STRING_DERIVED+0x110,0)
            elif kind=='companion':m.number(STRING_DERIVED+0x110+12*8,0)
            elif kind=='MIowner':m.number(STRING_EQUALS,0)
            elif kind=='MIindex':m.number(STRING_EQUALS+24,10325,'I')
            else:m.number(STRING_MI_OWNER+0x10000+8,STRING_CONTEXT+(1<<32))
            r=run(m);self.assertFalse(r['optionalStringCandidate']);self.assertTrue(all(r['candidateInputs'].values()))

    def test_abstract_or_same_string_sibling_is_not_a_companion_parent(self):
        for kind in ('base','sibling'):
            m=string_fixture()
            if kind=='base':m.number(STRING_EQUALS,STRING_OWNER);m.number(STRING_EQUALS+24,10289,'I')
            else:m.number(STRING_MI_OWNER+0x10000,1237,'I');m.number(STRING_EQUALS+24,10310,'I')
            r=run(m);self.assertFalse(r['optionalStringCandidate']);self.assertEqual(r['optionalString']['failure'],'string:equals-owner-parent-or-index-mismatch')

    def test_string_read_failures_remain_raw_without_erasing_consistent_main(self):
        for kind in ('short','error','oserror'):
            m=string_fixture()
            def read(a,n):
                raw,error=m.read(a,n)
                if a==STRING_DERIVED and n==208:
                    if kind=='short':return raw[:-1],None
                    if kind=='error':return b'',299
                    raise OSError(5,'owned-string')
                return raw,error
            r=run(m,read);self.assertFalse(r['optionalStringCandidate']);self.assertTrue(all(r['candidateInputs'].values()))
            self.assertEqual(r['mainInputConsistency']['status'],'repeated-unchanged');self.assertEqual(r['inputConsistency']['status'],'failed')
            row=next(x for x in r['records'] if x['name']=='string.derived.header');self.assertIsNotNone(row['error'])

    def test_string_final_drift_is_repeated_and_does_not_reclassify_main(self):
        m=string_fixture();count=0
        def read(a,n):
            nonlocal count
            raw,error=m.read(a,n)
            if a==STRING_DERIVED and n==208:
                count+=1
                if count==2:return raw[:0xc8]+bytes([raw[0xc8]^1])+raw[0xc9:],None
            return raw,error
        r=run(m,read);self.assertFalse(r['optionalStringCandidate']);self.assertFalse(any(r['candidateInputs'].values()))
        self.assertTrue(r['inputConsistency']['overlapConflicts'])

    def test_cross_graph_overlapping_header_conflict_rejects_main_and_string(self):
        m=string_fixture();m.number(STRING_GETTER,LIST);count=0
        def read(a,n):
            nonlocal count
            raw,error=m.read(a,n)
            if a==LIST and n==208:
                count+=1
                if count==2:return raw[:0xc8]+bytes([raw[0xc8]^1])+raw[0xc9:],None
            return raw,error
        r=run(m,read);self.assertFalse(r['optionalStringCandidate']);self.assertFalse(any(r['candidateInputs'].values()))
        self.assertTrue(r['inputConsistency']['overlapConflicts']);self.assertEqual(r['inputConsistency']['status'],'failed')


class OwnedReportTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        p=os.environ.get('CLIENT72_AVATAR_WARM_USAGES_REPORT',str(card.RAW/'owned-first.json'))
        cls.d=json.loads(Path(p).read_bytes())
    def test_source_and_all_frozen_dependencies_exact(self):
        self.assertEqual(self.d['sourceSHA256'],card.cache.sha(Path(card.__file__).read_bytes()))
        for p,h in self.d['fixedSources']['sourcePins'].items():self.assertEqual(card.cache.sha((card.ROOT/p).read_bytes()),h,p)
    def test_fixed_metadata_actual_family_names_and_no_whole_graph(self):
        source=self.d['fixedSources'];f=source['defaultFamilies']
        self.assertEqual(f['parameter0Paths']['1235'],[1235,1234]);self.assertEqual(f['parameter0Paths']['1240'],[1240,1239,1234])
        self.assertEqual(f['definitions']['1235']['name'],'GenericEqualityComparer`1');self.assertFalse(f['wholeGenericGraphScanned'])
        self.assertEqual(source['newNativeWindows'],0);self.assertFalse(source['nativeCpuExecuted'])
    def test_owned_all_fields_but_no_natural_or_constructor_claim(self):
        r=self.d['result'];self.assertEqual(r['status'],'completed');self.assertTrue(all(r['candidateInputs'].values()))
        self.assertTrue(self.d['ownedArtificialInput']);self.assertFalse(self.d['liveSamplingStarted'])
        self.assertFalse(self.d['windowsAdapterImplemented']);self.assertFalse(r['completeAvatarConstructor'])
        self.assertTrue(r['optionalStringCandidate']);self.assertEqual(r['optionalString']['equals']['methodDefinition'],10324)

    def test_source_verifier_rejects_changed_dependency_without_writing_it(self):
        original=Path.read_bytes;selected=Path(card.cache.__file__).resolve()
        def read(path):
            raw=original(path)
            return raw+b'changed-owned-verifier-input' if path.resolve()==selected else raw
        with patch.object(Path,'read_bytes',read),self.assertRaisesRegex(ValueError,'Fixed warm usage dependency differs'):
            card.verify_sources(b'',b'',b'')

    def test_source_verifier_rejects_wrong_fixed_binary_with_pin_reuse(self):
        with self.assertRaisesRegex(ValueError,'Fixed warm usage input differs'):
            card.verify_sources(b'',b'',b'',verified_sources=self.d['fixedSources']['sourcePins'])


if __name__=='__main__':unittest.main()
