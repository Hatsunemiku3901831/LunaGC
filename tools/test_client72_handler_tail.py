import copy
import json
from pathlib import Path
import unittest
from tools.trace_client72_handler_tail import expected_input,validate_summary


class HandlerTailTests(unittest.TestCase):
    def setUp(self):
        self.report=json.loads((Path(__file__).resolve().parents[1]/'protocol/cn-7.2/handler-tail-observations.json').read_text('utf8'))

    def test_native_handler_hash_evidence(self):
        self.assertEqual(validate_summary(self.report),[])

    def test_unknown_names_and_partial_scope_stay_explicit(self):
        for key,value in [('wholeHandlerExecuted',True),('continuousLoginExecuted',True),('runtimeStatus','passed')]:
            r=copy.deepcopy(self.report);r[key]=value;self.assertTrue(validate_summary(r))
        r=copy.deepcopy(self.report);r['fieldBindings'][0]['semanticStatus']='verified'
        self.assertTrue(validate_summary(r))

    def test_wrong_wire_encoding_or_hash_cannot_validate(self):
        for branch,key,value in [('declarations','encoding','UTF8Encoding'),('declarations','hash','SHA256Managed'),
                                 ('output','loginTag',658)]:
            r=copy.deepcopy(self.report);r[branch][key]=value;self.assertTrue(validate_summary(r))
        r=copy.deepcopy(self.report);r['fieldBindings'][1]['tag']=623
        self.assertTrue(validate_summary(r))

    def test_empty_and_block_boundary_cases_cannot_be_omitted_or_swapped(self):
        for index,key,value in [(1,'robotPointerPreserved',False),(2,'nativeSHA1',''),(3,'versionHash',''),
                                (4,'versionSourceCalls',0),(5,'nativeHashInputHex','')]:
            r=copy.deepcopy(self.report);r['results'][index][key]=value;self.assertTrue(validate_summary(r))
        r=copy.deepcopy(self.report);r['results'].pop();self.assertTrue(validate_summary(r))

    def test_bad_signature_must_not_change_tail_globals(self):
        for key,value in [('tailEntries',1),('priorGlobalsUnchanged',False),('stop','0x15a4e0dc7')]:
            r=copy.deepcopy(self.report);r['results'][6][key]=value;self.assertTrue(validate_summary(r))

    def test_oracle_keeps_nul_and_replaces_utf16_surrogate_units(self):
        version,raw,_=expected_input(('V','unused','unused',None,None,None),'\u5bc6\u94a5\xe9\U0001f511\0')
        self.assertEqual(version,'V');self.assertEqual(raw,b'V?????\0mhy2020')
        self.assertEqual(expected_input((None,)*6,'')[1],b'mhy2020')


if __name__=='__main__':unittest.main()
