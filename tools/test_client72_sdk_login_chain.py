"""Assertions over independently fixed native SDK action/queue experiments."""
import json
import os
from pathlib import Path
import unittest

from tools.client72_sdk_login_chain import ROOT, PARENT_SOURCE, PARENT_TEST, PARENT_RAW, ROOT_CONTRACT, digest


class SDKLoginChainTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        path=Path(os.environ.get('SDK72_LOGIN_CHAIN_REPORT',
            str(ROOT/'local/migration72/d02-active/sdk-login-chain-astra/final-v2-first.json')))
        cls.report=json.loads(path.read_bytes())

    def test_fixed_inputs_and_frozen_parent(self):
        self.assertEqual(self.report['sourceSHA256'],digest((ROOT/'tools/client72_sdk_login_chain.py').read_bytes()))
        expected={'tools/client72_sdk_transport.py':PARENT_SOURCE,
                  'tools/test_client72_sdk_transport.py':PARENT_TEST,
                  'local/migration72/d02-active/sdk-transport-astra/final-first.json':PARENT_RAW}
        for path,value in expected.items():
            self.assertEqual(digest((ROOT/path).read_bytes()),value)
        self.assertEqual(len(self.report['moduleSHA256']),13)
        for path,value in self.report['fixedInputs'].items():
            self.assertEqual(digest((ROOT/path).read_bytes()),value)

    def test_exact_rtti_identities(self):
        expected={'MiHoYoSDKNetworkManager':0x6d1f48,'HoYoNetworkEngine':0x6bf300,
                  'AccountPlatAccountLoginJobber':0x6c5390,'LoginManager':0x6c15e0,
                  'MiHoYoSDKRequestManager':0x6d2218,'AccountPlatLoginService':0x6c2ca8}
        for name,table in expected.items():
            matches=[t for t in self.report['staticRTTI'][name] if t['subobjectOffset']==0]
            self.assertEqual(len(matches),1)
            self.assertEqual(matches[0]['vtableRva'],table)

    def test_conditional_login_to_transport_vtable_edges(self):
        edges={(e['vtableRva'],e['slotByteOffset']):e['targetRva'] for e in self.report['staticVtableEdges']}
        self.assertEqual(len(edges),16)
        self.assertEqual(edges[(0x6c2ca8,0xb0)],0x32f460)
        self.assertEqual(edges[(0x6c5390,8)],0x34ea20)
        self.assertEqual(edges[(0x6c5390,0x70)],0x3508b0)
        self.assertEqual(edges[(0x6c5390,0x60)],0x34eca0)
        self.assertEqual(edges[(0x6d2218,0xb8)],0x3a57d0)
        self.assertEqual(edges[(0x6d1f48,0x18)],0x39c5e0)
        self.assertEqual(edges[(0x6bf300,0x58)],0x2f4290)
        self.assertEqual(edges[(0x6bf300,0x48)],0x2f0940)

    def test_cold_and_warm_singleton_native_producer(self):
        row=self.report['nativeSingleton']
        self.assertEqual(row['pointer'],0x600000000)
        self.assertTrue(row['sameWarmPointer'])
        self.assertEqual(row['allocationCount'],9)
        self.assertGreater(row['warmSteps'],5)
        self.assertEqual({e['rva'] for e in row['modelEvents'] if e['model']=='single-thread-static-init-epoch'},
                         {'0x482920','0x4828c0'})

    def test_native_action_registration_owners_and_targets(self):
        rows={r['key']:r for r in self.report['nativeRegistration']['nodes']}
        self.assertEqual(self.report['nativeRegistration']['count'],24)
        self.assertEqual(len(rows),24)
        for key,target in [('login_login',0x3cbe30),('login_account_plat',0x3cc220)]:
            self.assertEqual(rows[key]['targetRva'],target)
            self.assertEqual(rows[key]['owner'],0x400001000)
            self.assertEqual(rows[key]['vtableRva'],0x6d37a0)

    def test_native_invoke_preserves_callback64_and_index32(self):
        rows=self.report['nativeActionDispatch'][:2]
        for row,target in zip(rows,[0x3cbe30,0x3cc220]):
            self.assertEqual(len(row['hits']),1)
            hit=row['hits'][0]
            self.assertEqual(hit['targetRva'],target)
            self.assertEqual(hit['owner'],0x400001000)
            self.assertEqual(hit['argsPointer'],0x400003000)
            self.assertEqual(hit['callerCallback64'],0x123456789abcdef0)
            self.assertEqual(hit['callerIndex32'],0x12345678)
            self.assertEqual(hit['responseVtableRva'],0x694a80)
            self.assertEqual(hit['callerCallbackVtableRva'],0x6a84b0)

    def test_unknown_empty_and_state_blocked_actions_do_not_dispatch(self):
        rows=self.report['nativeActionDispatch'][2:]
        self.assertEqual([(r['key'],r['flagDB']) for r in rows],
                         [('unknown_fixture_action',0),('',0),('login_login',1)])
        self.assertTrue(all(r['hits']==[] for r in rows))

    def test_service_mode_uses_low32_and_exact_vtable(self):
        self.assertEqual([(r['mode64'],r['targetRva']) for r in self.report['nativeServiceSelection']],
            [(0,0x32efa0),(1,0x32f460),(3,0x342a10),(11,0x32f930),(99,0x331570),(0x100000001,0x32f460)])

    def test_native_queued_url_has_independent_owned_copy(self):
        rows={r['name']:r for r in self.report['nativeQueues']}
        self.assertEqual(set(rows),{'empty','standard','sso15','heap16','long1024','utf8'})
        for key,size in [('empty',0),('sso15',15),('heap16',16),('long1024',1024)]:
            row=rows[key]
            self.assertEqual(row['queued']['urlBytes'],size)
            self.assertTrue(row['queued']['ownsUrlCopy'])
            pointer=row['consumed']['hits'][0]['urlPointer']
            if size<16:
                self.assertEqual(pointer,row['queued']['request']+0x10)
            else:
                self.assertNotEqual(pointer,row['queued']['request']+0x10)

    def test_native_worker_pops_before_call_and_destroys_request(self):
        for row in self.report['nativeQueues']:
            self.assertEqual(row['queued']['count'],1)
            self.assertEqual(row['queued']['capacity'],8)
            self.assertEqual(row['queued']['requestVtableRva'],0x6bf1b0)
            self.assertEqual(row['consumed']['hits'][0]['queuedAtCall'],0)
            self.assertEqual(row['consumed']['hits'][0]['bodyBytes'],18)
            self.assertTrue(row['consumed']['requestFreed'])
            self.assertEqual(row['consumed']['finalQueueCount'],0)

    def test_stopped_queue_executes_native_throw_boundary(self):
        row=self.report['nativeStoppedQueue']
        self.assertEqual(row['errorBoundaryRva'],0x485540)
        self.assertTrue(row['queueUnchanged'])
        self.assertEqual(row['exceptionObjectRuntime'],'not-executed')

    def test_limited_native_easy_cleanup_releases_url_and_handle(self):
        row=self.report['nativeEasyCleanup']
        self.assertGreater(row['handle'],0xffffffff)
        self.assertNotEqual(row['handle'],row['ownedUrlPointer'])
        self.assertEqual(row['setoptReturn'],0)
        self.assertEqual(row['cleanupReturn'],0)
        self.assertEqual(row['remainingAllocations'],0)
        self.assertTrue(row['traceSHA256'])

    def test_readonly_roots_widths_and_non_vtable_objects(self):
        spec=self.report['readOnlyRootContract']
        self.assertEqual([(r['rva'],r['width']) for r in spec['roots']],
                         [('0x7acd88',8),('0x7e90d0',8),('0x7acc80',8)])
        self.assertEqual(spec['roots'][2]['vtableRva'],None)
        self.assertEqual(spec['engineThreadPool']['vtableRva'],None)
        self.assertEqual(spec['engineThreadPool']['offset'],0x70)
        self.assertEqual([(f['offset'],f['width']) for f in spec['queueFields']],[(0x70,8),(0x78,8),(0x80,8),(0x88,8)])
        self.assertEqual(spec['runFlag']['width'],1)
        self.assertEqual(spec['entry']['bytes'],46)
        self.assertEqual(digest((ROOT/'local/migration72/d02-active/sdk-login-chain-astra/rpm-roots-contract.json').read_bytes()),ROOT_CONTRACT)


if __name__=='__main__':
    unittest.main()
