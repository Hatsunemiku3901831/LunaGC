import copy
import json
from pathlib import Path
import unittest
from tools.validate_protocol72 import validate_handler_core


class HandlerCoreMappingTests(unittest.TestCase):
    def setUp(self):
        root=Path(__file__).resolve().parents[1]/'protocol/cn-7.2'
        self.document=json.loads((root/'mappings.json').read_text('utf8'))
        self.profile=json.loads((root/'profile.json').read_text('utf8'))
        self.handler=json.loads((root/'handler-core-observations.json').read_text('utf8'))
        self.wire=json.loads((root/'response-wire-observations.json').read_text('utf8'))

    def test_uid_wire_store_and_prefs_provenance_match_mapping(self):
        self.assertEqual(validate_handler_core(self.document,self.profile,self.handler,self.wire),[])
        for key,value in [('tag',15),('type','int32'),('semanticField','account_uid'),('sessionOffset',0x98),('prefsKey','unknown')]:
            row=copy.deepcopy(self.handler);row['fieldBinding'][key]=value
            self.assertTrue(validate_handler_core(self.document,self.profile,row,self.wire))

    def test_failed_warmup_or_bounded_handler_cannot_be_promoted_to_success(self):
        for key,value in [('wholeHandlerExecuted',True),('runtimeStatus','passed'),('inputSHA256',{})]:
            row=copy.deepcopy(self.handler);row[key]=value
            self.assertTrue(validate_handler_core(self.document,self.profile,row,self.wire))
        for i in (1,2):
            row=copy.deepcopy(self.handler);row['results'][i]['warmup'][0]['return']=0
            self.assertTrue(validate_handler_core(self.document,self.profile,row,self.wire))
        row=copy.deepcopy(self.handler);row['results'][0]['tokenPointerPreserved']=False
        self.assertTrue(validate_handler_core(self.document,self.profile,row,self.wire))


if __name__=='__main__':unittest.main()
