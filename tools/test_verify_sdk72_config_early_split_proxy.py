import json
import struct
import subprocess
import unittest
from unittest.mock import patch
from . import verify_sdk72_config_early_split_proxy as audit


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
        self.assertIn('Sdk72ConfigEarlySplitProxyState @48 DATA',definition)
        self.assertIn('sdk72_config_inner @50',definition)
        self.assertIn('sdk72_key_input_inner @51',definition)
        with self.assertRaises(ValueError):audit.confined(audit.ROOT/'outside.private.json')
        with self.assertRaises(ValueError):audit.confined(audit.LEGACY_RAW/'target/release/examples/sdk72_config_early_proxy.dll')

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
        for event in (3,4,6,7,8,9,10,11,12):
            modules,contract,memory,read=self.make(event)
            result=audit.collect(read,modules,contract,[n*16 for n in range(47)])
            self.assertEqual(set(result['event']),{'status','anyEntrySeen','expectedCallerSeen'})

    def test_split_limits_and_legacy_replay_have_separate_enums(self):
        from . import verify_sdk72_config_early_proxy as legacy
        self.assertEqual(set(audit.EVENTS),{0,1,2,3,4,6,7,8,9,10,11,12})
        for event,label in ((11,'payload-unit-scan-limit'),(12,'payload-utf8-limit')):
            state=dict(event=event,env=0,app_key_matches=0,combo_url_matches=0,any_entry_seen=1,expected_caller_seen=1)
            self.assertEqual(audit.event_output(state),dict(status=label,anyEntrySeen=True,expectedCallerSeen=True))
            for field in ('env','app_key_matches','combo_url_matches'):
                with self.assertRaises(ValueError):audit.event_output(dict(state,**{field:1}))
        state=dict(event=5,env=0,app_key_matches=0,combo_url_matches=0,any_entry_seen=1,expected_caller_seen=1)
        with self.assertRaises(ValueError):audit.event_output(state)
        self.assertEqual(legacy.event_output(state)['status'],'over-limit')
        modules,contract,memory,read=self.make(5)
        result=audit.collect(read,modules,contract,[n*16 for n in range(47)])
        self.assertEqual((result['status'],result['reason']),('partial','invalid-event'))
        self.assertEqual(len(result['reads']),1)

    def test_exact_split_source_transform_and_23_case_contract(self):
        result=audit.split_source_contract()
        self.assertEqual(result['fixtureCases'],23)
        self.assertEqual(len(audit.CASES),23)
        self.assertEqual(result['maxUnitsIncludingFirstNul'],32768)
        self.assertEqual(result['maxUtf8Bytes'],65535)
        self.assertEqual(result['extraNaturalReads'],0)
        self.assertEqual(audit.digest((audit.RAW/'legacy-cargo-manifest.private.toml').read_bytes()),
            audit.diagnosis.SOURCE_PINS['patch/Cargo.toml'])

    def test_source_contract_rejects_expansion_and_legacy_pin_drift(self):
        from pathlib import Path
        original=Path.read_text
        observer=audit.ROOT/'patch/examples/sdk72_config_early_split_proxy/observer.rs'
        def changed(path,*args,**kwargs):
            text=original(path,*args,**kwargs)
            return text.replace('MAX_UNITS: usize = 32768','MAX_UNITS: usize = 32769') if path==observer else text
        with patch.object(Path,'read_text',changed):
            with self.assertRaisesRegex(ValueError,'minimal transform'):audit.split_source_contract()
        with patch.dict(audit.diagnosis.SOURCE_PINS,{'patch/examples/sdk72_config_early_proxy/observer.rs':'0'*64}):
            with self.assertRaisesRegex(ValueError,'Frozen dependency differs'):audit.frozen_sources()

    def test_owned_python_boundary_projection_not_rust_execution(self):
        model=audit.diagnosis
        name,payload=0x8000000200000000,0x8000000300000000
        base=model.packet(ignored='')
        last=model.packet(ignored='a'*(model.MAX_UNITS-1-len(base)))
        late=model.packet(ignored='a'*(model.MAX_UNITS-len(base)))
        specifications=[('last-unit',model.managed(last),2,model.MAX_UNITS),
            ('next-slot',model.managed(late),11,model.MAX_UNITS),
            ('no-nul',model.managed(late,terminated=False),11,model.MAX_UNITS),
            ('last-byte',model.managed(model.utf8_boundary_packet(65535)),2,None),
            ('first-rejected-byte',model.managed(model.utf8_boundary_packet(65536)),12,None),
            ('surrogate-unit-limit',model.managed(units=[0xd800]*32768,terminated=False),11,model.MAX_UNITS)]
        traces={}
        for label,body,expected,units in specifications:
            memory=model.OwnedMemory([(name,model.managed('local_config')),(payload,body)])
            outcome=model.inspect_owned(memory,name,payload)
            projected={'payload-unit-scan-limit':11,'payload-utf8-limit':12}.get(outcome.origin,outcome.event)
            self.assertEqual(projected,expected)
            count=len(memory.reads)
            model.inspect_owned(memory,name,payload,terminal=True)
            self.assertEqual(len(memory.reads),count)
            trace=[a for a in memory.reads if a>=payload]
            if units is not None:self.assertEqual(len(trace),units)
            traces[label]=trace
        self.assertEqual(traces['next-slot'],traces['no-nul'])
        self.assertNotIn(payload+model.CHARS_OFFSET+model.MAX_UNITS*2,traces['next-slot'])
        for target in (65535,65536):
            text=model.utf8_boundary_packet(target)
            self.assertEqual(len(text.encode('utf8')),target)
            self.assertLessEqual(len(text.encode('utf-16-le'))//2+1,32768)

    def test_impossible_facts_or_unknown_classification_are_rejected(self):
        for values in (dict(event=2,env=3,app_key_matches=1,combo_url_matches=1,any_entry_seen=1,expected_caller_seen=1),
            dict(event=0,env=0,app_key_matches=0,combo_url_matches=0,any_entry_seen=0,expected_caller_seen=1),
            dict(event=9,env=0,app_key_matches=0,combo_url_matches=0,any_entry_seen=1,expected_caller_seen=0)):
            with self.assertRaises(ValueError):audit.event_output(values)


if __name__=='__main__':unittest.main()
