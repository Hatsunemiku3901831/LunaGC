import json
from pathlib import Path
import subprocess
import struct
import unittest
from . import verify_sdk72_config_proxy as audit

class ConfigBuildAuditTests(unittest.TestCase):
    def test_reference_projection_private_only(self):
        rows=[dict(env=env,app_key='SECRET_KEY_SENTINEL',url={'comboLogin':'SECRET_URL_SENTINEL'})
              for env in (*audit.CN_ENVS,2,3,5,7,11,12,21,20)]
        private=audit.reference_bytes(json.dumps(rows).encode())
        self.assertEqual([row[0] for row in json.loads(private)],list(audit.CN_ENVS))
        self.assertIn(b'SECRET_KEY_SENTINEL',private)
        self.assertIn(b'SECRET_URL_SENTINEL',private)

    def test_reference_shape_rejected_with_fixed_error(self):
        for raw in (b'[]',b'{SECRET_KEY_SENTINEL',b'[null]',b'{}'):
            with self.assertRaises((ValueError,TypeError)) as raised:audit.reference_bytes(raw)
            self.assertNotIn('SECRET',str(raised.exception))

    def test_output_confined(self):
        with self.assertRaises(ValueError):audit.confined(audit.ROOT/'outside.private.json')

    def test_exact_export_def(self):
        definition=audit.def_text()
        self.assertEqual(definition.count('@'),50)
        self.assertIn('Sdk72ConfigProxyState @48 DATA',definition)
        self.assertIn('sdk72_config_inner @49',definition)
        self.assertIn('sdk72_key_input_inner @50',definition)
        self.assertNotIn('Activate',definition)

    def test_owned_report_whitelist(self):
        from . import observe_client72_config_proxy as observer
        arrays=observer.frozen.source_arrays((audit.ROOT/(observer.frozen.ILHOOK+'x64.rs')).read_text())
        entry,callback,userdata,target=0x180000000,0x300000000,0x123456789abcdef0,0x800000000
        original=observer.MOVED+b'\x90'*81
        emitted=dict(entry=entry,callback=callback,userdata=userdata,trampoline=target,patchKind='far',
            originalHex=original.hex(),patchedHex=(bytes.fromhex('ff2500000000')+struct.pack('<Q',target)+original[14:]).hex(),
            trampolineHex=observer.trampoline(dict(arrays=arrays),entry,callback,userdata).hex(),trampolineBytes=279,
            dropRestored=True,calleeExecuted=False,callbackExecuted=False)
        raw=json.dumps(dict(audit.EXPECTED_OWNED,ownedConfigPrefix=emitted)).encode()
        result=audit.check_owned(subprocess.CompletedProcess([],0,raw,b''))
        self.assertTrue(result['passed'])
        for code,stdout,stderr in ((1,raw,b''),(0,raw,b'SECRET_BODY_SENTINEL'),(0,b'SECRET_BODY_SENTINEL',b'')):
            result=audit.check_owned(subprocess.CompletedProcess([],code,stdout,stderr))
            self.assertFalse(result['passed'])
            self.assertNotIn('SECRET',json.dumps(result))
        emitted['trampolineHex']=emitted['trampolineHex'][:-2]+'ff'
        result=audit.check_owned(subprocess.CompletedProcess([],0,json.dumps(dict(audit.EXPECTED_OWNED,ownedConfigPrefix=emitted)).encode(),b''))
        self.assertFalse(result['passed'])
        self.assertIsNone(result['ownedEmitter'])

    def test_production_scope_and_exact_callback(self):
        entry=(audit.ROOT/'patch/examples/sdk72_config_proxy.rs').read_text()
        self.assertNotIn('loader::activate(',entry)
        self.assertNotIn('loader::install_prepared(',entry)
        self.assertNotIn('loader::prepare_key(',entry)
        self.assertEqual(entry.count('observer::install('),1)
        body=(audit.ROOT/'patch/examples/sdk72_config_proxy/observer.rs').read_text()
        self.assertIn('sub rsp, 0x48',body)
        self.assertIn('movdqu [rsp + 0x20], xmm4',body)
        self.assertIn('movdqu [rsp + 0x30], xmm5',body)
        self.assertNotIn('println!',body)
        self.assertNotIn('eprintln!',body)
        self.assertLess(body.index('!= self.caller'),body.index('checked_add(offset).and_then'))
        self.assertLess(body.index('if byte != *expected'),body.index('copy_payload(registers.rdx'))
        self.assertNotIn('catch_unwind',body)
        self.assertLess(entry.index('loader::bind_loaded(&policy)'),entry.index('loader::wait_loaded(&policy'))

if __name__=='__main__':unittest.main()
