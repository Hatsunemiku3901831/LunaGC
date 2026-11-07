"""Independent assertions for fixed CN72 native response/session evidence."""
import json
import os
from pathlib import Path
import unittest

from tools.client72_sdk_response_contract import ROOT,RAW,INPUT_MANIFEST_SHA,digest,synthetic


class SDKResponseContractTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.raw=Path(os.environ.get('SDK72_RESPONSE_REPORT',str(RAW/'final-first.json'))).read_bytes()
        cls.report=json.loads(cls.raw)
        cls.groups={name:{r['case']:r for r in cls.report[name]} for name in ['login','users','exchange','formatter','combo','refresh']}

    def test_fixed_input_and_parent_epochs(self):
        r=self.report
        self.assertEqual(r['sourceSHA256'],digest((ROOT/'tools/client72_sdk_response_contract.py').read_bytes()))
        self.assertEqual(r['testSourceSHA256'],digest(Path(__file__).read_bytes()))
        self.assertEqual(r['inputManifestSHA256'],INPUT_MANIFEST_SHA)
        self.assertEqual(len(r['moduleSHA256']),13)
        for path,row in r['fixedInputs'].items():self.assertEqual(digest((ROOT/path).read_bytes()),row['sha256'],path)
        self.assertEqual(r['fixedInputs']['local/migration72/d02-active/sdk-crypto-contract-astra/final-first.json']['sha256'],
                         'c91ebc70a0afee16cb9379286fab86a10deea2a1ec8cef10a0c49fc9a7d17313')

    def test_exact_virtual_consumers(self):
        s=self.report['vtableSlots']
        self.assertEqual(s['0x6c2ca8']['0x198'],'0x342b10')
        self.assertEqual(s['0x6c2ca8']['0x1a0'],'0x338990')
        self.assertEqual(s['0x6d2218']['0xe0'],'0x3a5e00')
        self.assertEqual(s['0x6bf300']['0x90'],'0x2f6190')
        self.assertEqual(s['0x6c2fb8']['0x10'],'0x3409c0')
        self.assertEqual(s['0x6c27a0']['0x10'],'0x33f920')

    def test_full_original_machine_windows(self):
        for entry,w in self.report['codeWindows'].items():
            data=bytes.fromhex(w['machineCodeHex'])
            self.assertEqual(digest(data),w['sha256'])
            self.assertEqual(len(data),int(w['endRva'],16)-int(w['startRva'],16))
        self.assertEqual(self.report['codeWindows']['0x342b10']['pdata'],[0x342b10,0x342cb0])

    def test_callback_double_pointer_and_owner_forwarding(self):
        r=self.report['callbackForwarding']
        self.assertTrue(r['stoppedBeforeService'])
        self.assertFalse(r['externalCallbackReturnModeled'])
        self.assertEqual(r['visitedEntries'],{'0x34b5d0':1,'0x359f50':1,'0x340e90':1})
        self.assertEqual(len(r['hits']),1)
        self.assertTrue(all(r['hits'][0].values()))

    def test_login_success_executes_real_parser_and_guard_returns(self):
        r=self.groups['login']['complete']
        self.assertEqual((r['status'],r['tokenType'],r['stokenLength'],r['gameTokenLength']),(0,1,31,0))
        self.assertTrue(r['aidExact'] and r['stokenEqualsInput'])
        calls={x['entryRva']:x for x in r['nativeCalls']}
        for key in ['0x2f6190','0x338990','0x31f3f0']:self.assertEqual(calls[key]['value'],1)
        self.assertEqual(calls['0x342b10']['status'],0)

    def test_null_malformed_and_missing_status_fail_original_guard(self):
        g=self.groups['login']
        for name in ['null-body','empty-body','malformed','missing-retcode','retcode-string']:
            self.assertEqual(g[name]['status'],-1,name)
            self.assertEqual(g[name]['visitedEntries']['0x31f3f0'],0,name)
        self.assertEqual(g['retcode-nonzero']['status'],-101)
        self.assertEqual(g['retcode-fraction']['status'],0)

    def test_missing_data_and_user_have_distinct_schema_error(self):
        for name in ['missing-data','null-data','missing-user','null-user']:
            self.assertEqual(self.groups['login'][name]['status'],-2,name)
        # Actual permissive parser behavior; not a claim of completed login.
        for name in ['empty-user','string-user']:self.assertEqual(self.groups['login'][name]['status'],0,name)

    def test_token_defaults_and_storage_are_not_interchangeable(self):
        g=self.groups['login']
        self.assertEqual((g['missing-token']['tokenType'],g['missing-token']['stokenLength']),(0,0))
        self.assertEqual((g['token-default']['tokenType'],g['token-default']['stokenLength']),(1,31))
        self.assertEqual((g['token-wrong-type']['tokenType'],g['token-wrong-type']['stokenLength']),(1,0))
        self.assertEqual((g['token-zero']['tokenType'],g['token-zero']['stokenLength'],g['token-zero']['gameTokenLength']),(0,31,0))

    def test_verify_tokens_array_causes_empty_combo_token(self):
        a=self.groups['formatter']['login-type1'];b=self.groups['formatter']['verify-list-only']
        self.assertEqual((a['status'],b['status']),(0,0))
        self.assertEqual((a['typeAfterParse'],b['typeAfterParse']),(1,0))
        self.assertTrue(a['comboTokenEqualsInput'])
        self.assertFalse(b['comboTokenEqualsInput'])
        self.assertEqual((a['comboTokenLength'],b['comboTokenLength']),(31,0))
        self.assertTrue(a['openIdExact64Text'] and b['openIdExact64Text'])
        self.assertEqual(a['loginType'],0)

    def test_real_64bit_string_and_numeric_fields(self):
        r=self.groups['users']['full-nested-list']
        self.assertTrue(r['aidExact64Text'] and r['timestampExact64'] and r['loginTimeExact64'])
        self.assertEqual(r['channelId'],37)
        p=self.groups['users']['number-precision-boundary']
        self.assertTrue(p['above53TimestampRounded'] and p['above53LoginTimeRounded'])
        self.assertFalse(self.groups['users']['numeric-id']['aidExact64Text'])

    def test_native_nested_list_stride_and_destruction(self):
        r=self.groups['users']['full-nested-list']
        self.assertEqual(r['linksCount'],2)
        self.assertEqual([x['nicknameBytes'] for x in r['links']],[31,17])
        self.assertTrue(all(x['unionIdExact64Text'] for x in r['links']))
        self.assertTrue(all(x['vectorStorageFreed'] for x in self.report['users']))
        for name in ['list-wrong-type','list-empty']:self.assertEqual(self.groups['users'][name]['linksCount'],0)

    def test_boolean_and_email_default_semantics(self):
        g=self.groups['users']
        self.assertEqual((g['empty']['emailVerifiedByte'],g['empty']['persistentByte']),(1,1))
        self.assertEqual((g['wrong-bools']['guestByte'],g['wrong-bools']['persistentByte']),(0,1))
        self.assertEqual(g['false-bools']['persistentByte'],0)
        self.assertEqual(g['email-zero']['emailVerifiedByte'],0)
        self.assertEqual(g['email-string-zero']['emailVerifiedByte'],0)

    def test_refresh_has_distinct_new_token_consumer(self):
        g=self.groups['refresh']
        self.assertTrue(g['type1']['stokenReplaced'])
        self.assertFalse(g['type1']['gameTokenReplaced'])
        self.assertTrue(g['type0']['gameTokenReplaced'] and g['type0']['oldStokenRetained'])
        for name in ['missing','empty']:self.assertTrue(g[name]['oldStokenRetained'])

    def test_cookie_exchange_ignores_type_but_requires_nonempty_string(self):
        g=self.groups['exchange']
        for name in ['valid','different-type','type-omitted']:
            self.assertEqual(g[name]['reportedStatuses'],[0])
            self.assertEqual(g[name]['cacheAfter'],1)
            self.assertTrue(all(g[name]['cacheRows'][0].values()))
        for name in ['empty-token','missing-token','wrong-token','malformed','null']:
            self.assertEqual(g[name]['reportedStatuses'],[-1]);self.assertEqual(g[name]['cacheAfter'],0)

    def test_invalid_cookie_token_evicts_native_cache(self):
        r=self.groups['exchange']['invalidates-cache']
        self.assertEqual((r['cacheBefore'],r['cacheAfter']),(1,0))
        self.assertEqual(r['reportedStatuses'],[-100])
        self.assertEqual(self.groups['exchange']['error']['reportedStatuses'],[-101])

    def test_combo_nested_json_string_and_defaults(self):
        g=self.groups['combo'];a=g['complete']
        self.assertEqual(a['ret'],0);self.assertTrue(a['comboTokenExact'] and a['openIdExact64Text'])
        self.assertFalse(a['guest']);self.assertTrue(g['guest-true']['guest'])
        self.assertEqual(g['minimal']['accountType'],1)
        self.assertIsNone(g['wrong-types']['guest'])
        self.assertIsNone(g['minimal']['loginType'])

    def test_real_request_producers_at_no_return_boundaries(self):
        r=self.report['requestProducers'];self.assertFalse(r['transportExecuted'])
        self.assertEqual(len(r['routes']),3)
        a=r['sendBoundary'][0];b=r['verifyBoundary'][0]
        self.assertEqual(a['fieldNames'],['dst_token_type','mid','src_token'])
        self.assertEqual((a['destinationTokenType'],a['sourceTokenType']),(4,1))
        self.assertTrue(a['sourceTokenExact'] and a['midExact64Text'] and a['urlWhitelisted'])
        self.assertEqual(b['fieldNames'],['mid','refresh','token'])
        self.assertTrue(b['refresh'] and b['tokenExact'] and b['midExact64Text'] and b['urlWhitelisted'])

    def test_report_has_no_synthetic_identity_or_token_values(self):
        text=self.raw.decode()
        for seed in range(16):self.assertNotIn(synthetic(seed),text)
        for n in [17,19,21,23,31]:self.assertNotIn(str((1<<53)+n),text)
        self.assertNotIn('traceSHA256',text)


if __name__=='__main__':unittest.main()
