import copy
import json
from pathlib import Path
import unittest
from tools.validate_protocol72 import validate_key_selection


class KeySelectionTests(unittest.TestCase):
    def setUp(self):
        root=Path(__file__).resolve().parents[1]/'protocol/cn-7.2'
        self.mapping=json.loads((root/'mappings.json').read_text('utf8'))
        self.profile=json.loads((root/'profile.json').read_text('utf8'))
        self.summary=json.loads((root/'key-selection-observations.json').read_text('utf8'))
        self.wire=json.loads((root/'token-wire-observations.json').read_text('utf8'))

    def check(self, summary):
        return validate_key_selection(self.mapping,self.profile,summary,self.wire)

    def test_three_sources_and_differential_crypto_support_mapping(self):
        self.assertEqual(self.check(self.summary),[])

    def test_field_cannot_be_renamed_or_moved(self):
        for key,value in [('tag',965),('objectOffset',0x90),('nativeValue',3),('semanticField','account_type')]:
            row=copy.deepcopy(self.summary);row['fieldBinding'][key]=value
            self.assertTrue(self.check(row),key)
        field=next(f for s in self.mapping['schemas'] if s['semanticName']=='GetPlayerTokenReq' for f in s['fields'] if f['tag']==1184)
        field['semanticField']='key_version'
        self.assertTrue(self.check(self.summary))

    def test_named_argument_and_native_request_cannot_be_substituted(self):
        for branch,key,value in [('urlArgument','literal',{'index':61981,'value':'&account_type='}),
                                 ('urlArgument','value',3),('urlArgument','formatterExecuted',True)]:
            row=copy.deepcopy(self.summary);row[branch][key]=value
            self.assertTrue(self.check(row))
        row=copy.deepcopy(self.summary);row['requestResults'][0]['selectorWire']='804a03'
        self.assertTrue(self.check(row))

    def test_wrong_key_cannot_install_session_or_be_reported_successful(self):
        for index,key,value in [(3,'sessionKeyInstalled',True),(4,'nativeOperations',{'101':0,'103':0}),
                                (5,'javaByteComparison',True),(0,'keyChecks',[])]:
            row=copy.deepcopy(self.summary);row['results'][index][key]=value
            self.assertTrue(self.check(row),key)
        row=copy.deepcopy(self.summary);row['publicKeyFiles']['2']['matchesNativePrivate']=True
        self.assertTrue(self.check(row))

    def test_offline_trust_scope_cannot_be_reported_live(self):
        for key,value in [('wholeExchangeExecuted',True),('runtimeStatus','passed'),('keyVerificationForced',True),
                          ('trustInputReplacements',[]),('inputSHA256',{})]:
            row=copy.deepcopy(self.summary);row[key]=value
            self.assertTrue(self.check(row),key)


if __name__=='__main__':unittest.main()
