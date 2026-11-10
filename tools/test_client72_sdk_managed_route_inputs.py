"""Artificial sparse callbacks, not target managed objects or GC evidence."""
from contextlib import redirect_stdout, redirect_stderr
from copy import deepcopy
import io
import json
import struct
import unittest
from unittest.mock import patch

from tools import client72_sdk_managed_route_inputs as core

B,S,C,U,T,KC,KU,KT = [0xcafe000000000000 + i*0x10000 for i in range(8)]
SECRET = 'ARTIFICIAL_SECRET_48d70c'


class Memory:
    def __init__(self, route=0, env=0, raw=None, length=None, base=B):
        content = list(core.BASES)[route] if raw is None else raw
        self.spans={}
        for address,value in ((base+0x586b600,S),(base+0x5887680,KC),(S+0xc18,C),(C,KC),
                (base+0x5893d50,KU),(S+0xb80,U),(U,KU),(U+0xe0,T),(base+0x5886040,KT),(T,KT)):
            self.spans[address]=struct.pack('<Q',value)
        self.spans[C+0x58]=struct.pack('<i',env)
        self.spans[T+0x10]=struct.pack('<i',len(content)//2 if length is None else length)
        self.spans[T+0x14]=content
        self.calls=[]; self.faults={}

    def read(self,address,width):
        self.calls.append((address,width))
        answer=self.faults.get(len(self.calls))
        if isinstance(answer,Exception): raise answer
        return answer if answer is not None else (self.spans.get(address,b'')[:width],None)


def assert_private(test, report):
    encoded=json.dumps(report,ensure_ascii=False)
    test.assertNotIn(SECRET,encoded)
    test.assertNotIn(SECRET.encode('utf-16-le').hex(),encoded)
    test.assertNotIn(str(T),encoded)
    test.assertFalse(report['sensitiveRawSealed'])
    test.assertNotIn('requestedBytes',report['budget'])
    test.assertNotIn('firstPassBytes',report['budget'])
    for row in report['records']:
        if row['ordinal'] in core.SENSITIVE:
            test.assertEqual(set(row), {'ordinal','name','phase','beginUTC','endUTC','error','outcome','redacted'})


class InputsTests(unittest.TestCase):
    def test_all_eight_exact_bases_actual_raw_and_budget_known_replay(self):
        for index,env in enumerate(core.ENVS):
            memory=Memory(route=index,env=env)
            before=deepcopy(memory.spans)
            pending=core.collect(memory.read,B)
            self.assertEqual(str(pending),'<PendingManagedRoute: memory-only>')
            with self.assertRaises(TypeError): json.dumps(pending)
            report=pending.seal(True)
            self.assertEqual(report['status'],'candidate')
            self.assertEqual(report['summary']['currentEnvAndBaseRouteAgree'],True)
            self.assertEqual(report['budget']['actualReads'],26)
            self.assertEqual(report['budget']['requestedBytes'],176+2*len(list(core.BASES)[index]))
            self.assertLessEqual(report['budget']['requestedBytes'],452)
            self.assertEqual(report['budget']['structuralRequestedBytes'],136)
            self.assertEqual(memory.calls[:13],memory.calls[13:])
            self.assertEqual(bytes.fromhex(report['records'][12]['bytesHex']),memory.spans[T+0x14])
            self.assertEqual(core.replay(report)['scope'],'actual-whitelisted-content-and-structure')
            self.assertEqual(memory.spans,before)

    def test_two_known_categories_can_disagree_without_faking_selection(self):
        report=core.collect(Memory(route=1,env=0).read,B).seal(True)
        self.assertEqual(report['status'],'candidate')
        self.assertFalse(report['summary']['currentEnvAndBaseRouteAgree'])
        self.assertFalse(report['managedEnvSelectionObserved'])

    def test_pending_known_is_redacted_until_explicit_outer_seal(self):
        pending=core.collect(Memory().read,B)
        assert_private(self,pending.seal())
        assert_private(self,pending.seal(False))
        self.assertTrue(pending.seal(True)['sensitiveRawSealed'])

    def test_lengths_negative_zero_63_64_69_70_and_unknown_env(self):
        for length in (-1,0,63,64,69,70,0x7fffffff):
            memory=Memory(raw=(SECRET+'x'*80).encode('utf-16-le'),length=length)
            report=core.collect(memory.read,B).seal(True)
            assert_private(self,report)
            self.assertEqual(report['status'],'partial')
            self.assertEqual(any(a==T+0x14 for a,w in memory.calls),length in (64,69))
            self.assertTrue(core.replay(report)['passed'])
        for env in (-0x80000000,0x7fffffff,3):
            report=core.collect(Memory(env=env).read,B).seal(True)
            self.assertEqual(report['summary']['envNumber'],env)
            assert_private(self,report)

    def test_no_normalization_no_question_mark_strip_or_add_no_utf16_decode(self):
        known=list(core.BASES)[0]
        variants=[known+b'?\0',known.replace(b'h\0',b'H\0',1),known[:10]+b'\0\0'+known[12:],
                  b'\x00\xd8'+known[2:],(SECRET+'x'*64).encode('utf-16-le')[:128]]
        for content in variants:
            report=core.collect(Memory(raw=content).read,B).seal(True)
            assert_private(self,report)
            self.assertEqual(report['status'],'partial')

    def test_each_null_and_wrong_raw_class_stops_without_fallback_or_untag(self):
        for address in (B+0x586b600,B+0x5887680,S+0xc18,B+0x5893d50,S+0xb80,U+0xe0,B+0x5886040):
            memory=Memory(); memory.spans[address]=bytes(8)
            report=core.collect(memory.read,B).seal(True)
            assert_private(self,report)
            self.assertEqual(report['status'],'partial')
        for address,value in ((C,KC|1),(U,KU|1),(T,KT|1)):
            memory=Memory(); memory.spans[address]=struct.pack('<Q',value)
            report=core.collect(memory.read,B).seal(True)
            assert_private(self,report)
            self.assertTrue(report['outcome'].endswith('class-mismatch'))

    def test_each_first_ordinal_short_api_error_exception_stops_and_never_retries_failed_span(self):
        for ordinal in range(1,14):
            for answer in ((b'',None),(b'\x01',{'winerror':299,'message':SECRET}),RuntimeError(SECRET)):
                memory=Memory(); memory.faults[ordinal]=answer
                stdout,stderr=io.StringIO(),io.StringIO()
                with redirect_stdout(stdout),redirect_stderr(stderr):
                    report=core.collect(memory.read,B).seal(True)
                assert_private(self,report)
                self.assertEqual(report['budget']['firstPassReads'],ordinal)
                self.assertEqual(len(memory.calls),ordinal+ordinal-1)
                self.assertEqual(memory.calls[ordinal:],memory.calls[:ordinal-1])
                self.assertNotIn(SECRET,stdout.getvalue()+stderr.getvalue())

    def test_each_final_ordinal_short_error_drift_stops_original_sequence(self):
        for ordinal in range(1,14):
            baseline=Memory(); first=core.collect(baseline.read,B).seal(True)['records'][ordinal-1]
            changed=bytearray.fromhex(first['bytesHex']); changed[0]^=1
            for answer in ((b'',None),(b'',{'lastError':299,'detail':SECRET}),(bytes(changed),None)):
                memory=Memory(); memory.faults[13+ordinal]=answer
                report=core.collect(memory.read,B).seal(True)
                assert_private(self,report)
                self.assertEqual(len(memory.calls),13+ordinal)
                self.assertEqual(memory.calls[13:],memory.calls[:ordinal])
                self.assertTrue(core.replay(report)['passed'])

    def test_first_known_final_unknown_content_and_length_are_redacted_in_full_output(self):
        for ordinal,raw in ((12,struct.pack('<i',63)),(13,(SECRET+'x'*64).encode('utf-16-le')[:128])):
            memory=Memory(); memory.faults[13+ordinal]=(raw,None)
            report=core.collect(memory.read,B).seal(True)
            assert_private(self,report)
            self.assertEqual(report['outcome'],'final-drift')

    def test_high_u64_range_and_invalid_entry_have_zero_or_bounded_reads(self):
        for base in (0,-1,True,core.U64):
            memory=Memory(); report=core.collect(memory.read,base).seal(True)
            self.assertEqual(memory.calls,[]); self.assertEqual(report['status'],'partial')
        memory=Memory(); memory.spans[U+0xe0]=struct.pack('<Q',core.U64)
        report=core.collect(memory.read,B).seal(True)
        self.assertEqual(report['outcome'],'address-range'); assert_private(self,report)
        self.assertLessEqual(len(memory.calls),26)

    def test_known_and_structural_replay_reject_content_or_sensitive_disclosure(self):
        report=core.collect(Memory().read,B).seal(True)
        changed=deepcopy(report); changed['records'][12]['bytesHex']='00'*64
        with self.assertRaises(ValueError): core.replay(changed)
        for field,value in (('ordinal',2),('phase','final')):
            changed=deepcopy(report); changed['records'][0][field]=value
            with self.assertRaises(ValueError): core.replay(changed)
        changed=deepcopy(report); changed['budget']['requestedBytes']+=1
        with self.assertRaises(ValueError): core.replay(changed)
        report=core.collect(Memory(raw=b'X\0'*64).read,B).seal(True)
        changed=deepcopy(report); changed['records'][8]['requested']=8
        with self.assertRaises(ValueError): core.replay(changed)
        changed=deepcopy(report); changed['records'][0]['address']+=1
        with self.assertRaises(ValueError): core.replay(changed)

    def test_real_source_gate_hashes_only_and_frozen_pin_drift_rejected(self):
        self.assertEqual(core.verify_sources()['auditedInputCount'],21)
        original=core.file_sha
        with patch.object(core,'file_sha',side_effect=lambda path:'0'*64 if path==core.ROOT/core.SPEC else original(path)):
            with self.assertRaises(ValueError): core.verify_sources()


if __name__=='__main__': unittest.main()
