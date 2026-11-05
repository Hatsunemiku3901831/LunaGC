import copy
import json
from pathlib import Path
import unittest
from tools.trace_client72_dispatch_token import validate_summary

class DispatchTokenTests(unittest.TestCase):
    def setUp(self):
        root=Path(__file__).resolve().parents[1]/'protocol/cn-7.2'
        self.report=json.loads((root/'dispatch-token-observations.json').read_text('utf8'))
        self.mappings=json.loads((root/'mappings.json').read_text('utf8'))
        self.wire=json.loads((root/'token-wire-observations.json').read_text('utf8'))

    def test_raw_source_chain_has_bounded_evidence(self):
        self.assertEqual(validate_summary(self.report,self.mappings,self.wire),[])

    def test_unknown_source_association_and_semantics_cannot_be_promoted(self):
        for key,value in [('sourceAssociationStatus','verified'),('semanticStatus','verified'),('runtimeStatus','passed'),('wholeDispatchExecuted',True)]:
            report=copy.deepcopy(self.report);report[key]=value
            self.assertTrue(validate_summary(report,self.mappings,self.wire))
        for schema in self.mappings['schemas']:
            if schema['semanticName']=='GetPlayerTokenReq':
                next(f for f in schema['fields'] if f['tag']==965)['status']='verified'
        self.assertTrue(validate_summary(self.report,self.mappings,self.wire))

    def test_source_destination_and_default_wire_cannot_be_swapped(self):
        for key,value in [('sourceTag',2),('sessionOffset',0xf8),('requestTag',2)]:
            report=copy.deepcopy(self.report);report['rawBinding'][key]=value
            self.assertTrue(validate_summary(report,self.mappings,self.wire))
        for i in (0,1):
            report=copy.deepcopy(self.report);report['results'][i]['expectedFieldWire']='00'
            self.assertTrue(validate_summary(report,self.mappings,self.wire))
        report=copy.deepcopy(self.report);report['results'][4]['unchanged']=False
        self.assertTrue(validate_summary(report,self.mappings,self.wire))
        self.wire['inputSHA256']={}
        self.assertTrue(validate_summary(self.report,self.mappings,self.wire))

if __name__=='__main__':unittest.main()
