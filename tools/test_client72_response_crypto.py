import copy
import json
from pathlib import Path
import unittest
from tools.validate_protocol72 import validate_response_crypto


class ResponseCryptoMappingTests(unittest.TestCase):
    def setUp(self):
        root=Path(__file__).resolve().parents[1]/'protocol/cn-7.2'
        self.document=json.loads((root/'mappings.json').read_text('utf8'))
        self.profile=json.loads((root/'profile.json').read_text('utf8'))
        self.crypto=json.loads((root/'response-crypto-observations.json').read_text('utf8'))
        self.wire=json.loads((root/'response-wire-observations.json').read_text('utf8'))

    def test_current_wire_and_semantic_bindings_match(self):
        self.assertEqual(validate_response_crypto(self.document,self.profile,self.crypto,self.wire),[])

    def test_local_trust_or_crypto_rejection_cannot_be_promoted_to_stock_or_live_success(self):
        for key,value in [('wholeHandlerExecuted',True),('keyVerificationForced',True),('runtimeStatus','passed')]:
            row=copy.deepcopy(self.crypto);row[key]=value
            self.assertTrue(validate_response_crypto(self.document,self.profile,row,self.wire))
        for key,value in [('trust','stock'),('javaByteComparison',False)]:
            row=copy.deepcopy(self.crypto);row['results'][0][key]=value
            self.assertTrue(validate_response_crypto(self.document,self.profile,row,self.wire))
        row=copy.deepcopy(self.crypto);row['results'][-1]['javaByteComparison']=True
        self.assertTrue(validate_response_crypto(self.document,self.profile,row,self.wire))

    def test_ciphertext_and_signature_fields_cannot_be_swapped(self):
        row=copy.deepcopy(self.crypto);row['fieldBindings'][0]['semanticField']='sign'
        self.assertTrue(validate_response_crypto(self.document,self.profile,row,self.wire))
        row=copy.deepcopy(self.crypto);row['fieldBindings'][1]['tag']=477
        self.assertTrue(validate_response_crypto(self.document,self.profile,row,self.wire))
        row=copy.deepcopy(self.crypto);row['inputSHA256']={}
        self.assertTrue(validate_response_crypto(self.document,self.profile,row,self.wire))


if __name__=='__main__':unittest.main()
