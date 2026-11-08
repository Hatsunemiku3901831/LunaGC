import json
import struct
import subprocess
import unittest
from . import verify_sdk72_config_early_proxy as audit


class EarlyBridgeAuditTests(unittest.TestCase):
    def make(self,event=2,any_seen=None,caller_seen=None):
        modules={name:dict(base=base) for name,base in zip(('Astrolabe.dll','YuanShen.exe','Astrolabe_orig.dll'),
            (0x180000000,0x140000000,0x500000000))}
        arrays=audit.frozen.source_arrays((audit.ROOT/(audit.frozen.ILHOOK+'x64.rs')).read_text())
        windows=[(r,audit.MOVED+b'\x90'*(n-20) if r==audit.ENTRY_RVA else b'\x90'*n) for r,n in audit.EXE_WINDOWS]
        contract=dict(stateRva=0x1000,tableRva=0x2000,callbackRva=0x3000,callbackCode=b'X'*38,
            arrays=arrays,exeImageSize=0x1b662000,exeWindows=windows,
            callerWindow=(audit.CALLER_WINDOW[0],b'\x90'*audit.CALLER_WINDOW[1]))
        proxy,exe,stock=(modules[name]['base'] for name in ('Astrolabe.dll','YuanShen.exe','Astrolabe_orig.dll'))
        any_seen=int(event!=0) if any_seen is None else any_seen
        caller_seen=int(event!=0) if caller_seen is None else caller_seen
        state=struct.pack('<6IQ10I',1,72,5,0,0,1,exe,contract['exeImageSize'],1,1,1,
            any_seen,caller_seen,event,0,int(event==2),int(event==2))
        memory={proxy+contract['stateRva']:state,proxy+contract['tableRva']:struct.pack('<47Q',*(stock+n*16 for n in range(47))),
            proxy+contract['callbackRva']:contract['callbackCode']}
        for rva,code in [*contract['exeWindows'],contract['callerWindow']]:memory[exe+rva]=code
        target=0x800000000;original=windows[0][1]
        memory[exe+audit.ENTRY_RVA]=bytes.fromhex('ff2500000000')+struct.pack('<Q',target)+original[14:]
        memory[target]=audit.trampoline(arrays,exe+audit.ENTRY_RVA,proxy+contract['callbackRva'],0x123456789abcdef0)
        def read(address,width):return memory.get(address,b'')[:width],None
        return modules,contract,memory,read

    def emitter(self):
        arrays=audit.frozen.source_arrays((audit.ROOT/(audit.frozen.ILHOOK+'x64.rs')).read_text())
        entry,callback,userdata,target=0x180000000,0x300000000,0x123456789abcdef0,0x800000000
        original=audit.MOVED+b'\x90'*76
        return dict(entry=entry,callback=callback,userdata=userdata,trampoline=target,patchKind='far',
            originalHex=original.hex(),patchedHex=(bytes.fromhex('ff2500000000')+struct.pack('<Q',target)+original[14:]).hex(),
            trampolineHex=audit.trampoline(arrays,entry,callback,userdata).hex(),trampolineBytes=284,
            dropRestored=True,calleeExecuted=False,callbackExecuted=False)

    def test_complete_moved20_and_entry_plus20_oracle(self):
        row=self.emitter()
        self.assertTrue(audit.verify_owned_emitter(row))
        for offset in (0,140,250,269,270,283):
            changed=dict(row);raw=bytearray.fromhex(row['trampolineHex']);raw[offset]^=1
            changed['trampolineHex']=raw.hex()
            with self.assertRaises(ValueError):audit.verify_owned_emitter(changed)

    def test_owned_stdout_stderr_whitelist(self):
        raw=json.dumps(dict(audit.EXPECTED_OWNED,ownedExePrefix=self.emitter())).encode()
        self.assertTrue(audit.check_owned(subprocess.CompletedProcess([],0,raw,b''))['passed'])
        for code,stdout,stderr in ((1,raw,b''),(0,raw,b'PRIVATE_SENTINEL'),(0,b'PRIVATE_SENTINEL',b''),
            (0,json.dumps(dict(audit.EXPECTED_OWNED,ownedExePrefix=self.emitter(),unknown='PRIVATE_SENTINEL')).encode(),b'')):
            result=audit.check_owned(subprocess.CompletedProcess([],code,stdout,stderr))
            self.assertFalse(result['passed']);self.assertNotIn('PRIVATE_SENTINEL',json.dumps(result))

    def test_exact_eat_and_confined_output(self):
        definition=audit.def_text()
        self.assertEqual(definition.count('@'),51)
        self.assertIn('Sdk72ConfigEarlyProxyState @48 DATA',definition)
        self.assertIn('sdk72_config_inner @50',definition)
        self.assertIn('sdk72_key_input_inner @51',definition)
        with self.assertRaises(ValueError):audit.confined(audit.ROOT/'outside.private.json')

    def test_installed_bound_and_exact_nine_read_budget(self):
        modules,contract,memory,read=self.make()
        result=audit.collect(read,modules,contract,[n*16 for n in range(47)])
        self.assertEqual(result['status'],'installed-early-code-bound')
        self.assertEqual(result['budget']['reads'],9);self.assertEqual(result['budget']['bytes'],1450)
        self.assertEqual(result['event'],dict(status='managed-input-classified',anyEntrySeen=True,expectedCallerSeen=True,
            env='cn-env-0',appKeyMatches=True,comboUrlMatches=True))
        self.assertNotIn('env',result['metadata'])
        self.assertFalse(result['payloadReadByCollector']);self.assertFalse(result['referenceReadByCollector'])
        self.assertFalse(result['nativeExportObserved'])

    def test_no_event_distinguishes_no_entry_and_unknown_caller(self):
        for seen in (0,1):
            modules,contract,memory,read=self.make(0,seen,0)
            result=audit.collect(read,modules,contract,[n*16 for n in range(47)])
            self.assertEqual(result['status'],'installed-early-code-bound')
            self.assertEqual(result['event'],dict(status='no-exact-caller-attempt',anyEntrySeen=bool(seen),expectedCallerSeen=False))

    def test_copy_in_progress_or_unstable_state_is_partial(self):
        modules,contract,memory,read=self.make(1)
        self.assertEqual(audit.collect(read,modules,contract,[n*16 for n in range(47)])['status'],'partial')
        modules,contract,memory,read=self.make()
        address=modules['Astrolabe.dll']['base']+contract['stateRva'];calls=0
        def changing(a,n):
            nonlocal calls
            if a==address:
                calls+=1
                if calls==2:return bytes(n),None
            return read(a,n)
        self.assertEqual(audit.collect(changing,modules,contract,[n*16 for n in range(47)])['status'],'partial')

    def test_corrupt_caller_or_moved_code_cannot_pass(self):
        for location in ('caller','trampoline'):
            modules,contract,memory,read=self.make()
            address=modules['YuanShen.exe']['base']+contract['callerWindow'][0] if location=='caller' else 0x800000000
            data=bytearray(memory[address]);data[-1]^=1;memory[address]=bytes(data)
            self.assertEqual(audit.collect(read,modules,contract,[n*16 for n in range(47)])['status'],'partial')

    def test_short_read_or_error_stays_fixed_and_partial(self):
        modules,contract,memory,read=self.make();address=modules['Astrolabe.dll']['base']+contract['stateRva']
        memory[address]=memory[address][:-1]
        result=audit.collect(read,modules,contract,[n*16 for n in range(47)])
        self.assertEqual(result['status'],'partial');self.assertEqual(len(result['reads']),1)
        def failed(a,n):raise OSError('PRIVATE_SENTINEL')
        result=audit.collect(failed,modules,contract,[n*16 for n in range(47)])
        self.assertNotIn('PRIVATE_SENTINEL',json.dumps(result))

    def test_failure_events_only_project_fixed_states(self):
        for event in range(3,11):
            modules,contract,memory,read=self.make(event)
            result=audit.collect(read,modules,contract,[n*16 for n in range(47)])
            self.assertEqual(set(result['event']),{'status','anyEntrySeen','expectedCallerSeen'})

    def test_impossible_facts_or_unknown_classification_are_rejected(self):
        for values in (dict(event=2,env=3,app_key_matches=1,combo_url_matches=1,any_entry_seen=1,expected_caller_seen=1),
            dict(event=0,env=0,app_key_matches=0,combo_url_matches=0,any_entry_seen=0,expected_caller_seen=1),
            dict(event=9,env=0,app_key_matches=0,combo_url_matches=0,any_entry_seen=1,expected_caller_seen=0)):
            with self.assertRaises(ValueError):audit.event_output(values)


if __name__=='__main__':unittest.main()
