import json
import struct
import unittest
from . import observe_client72_config_proxy as observer
from . import verify_sdk72_config_proxy as audit

class ObserverTests(unittest.TestCase):
    def make(self,event=2):
        modules={name:dict(base=base) for name,base in zip(('Astrolabe.dll','AccountPlatNative.dll','YuanShen.exe','Astrolabe_orig.dll'),
                    (0x180000000,0x300000000,0x140000000,0x500000000))}
        arrays=observer.frozen.source_arrays((audit.ROOT/(observer.frozen.ILHOOK+'x64.rs')).read_text())
        windows=[(rva,observer.MOVED+b'\x90'*(n-15) if rva==observer.ENTRY_RVA else b'\x90'*n) for rva,n in audit.SDK_WINDOWS]
        contract=dict(stateRva=0x1000,tableRva=0x2000,callbackRva=0x3000,callbackCode=b'X'*38,
            arrays=arrays,sdkImageSize=0x932000,exeImageSize=0x1b662000,
            sdkWindows=windows,exeWindows=[(rva,b'\x90'*n) for rva,n in audit.EXE_WINDOWS])
        sdk,exe,proxy,stock=(modules[name]['base'] for name in ('AccountPlatNative.dll','YuanShen.exe','Astrolabe.dll','Astrolabe_orig.dll'))
        state=struct.pack('<6I2Q10I',1,80,5,0,0,1,sdk,exe,contract['sdkImageSize'],contract['exeImageSize'],1,1,1,1,event,0,1 if event==2 else 0,1 if event==2 else 0)
        memory={proxy+contract['stateRva']:state,proxy+contract['tableRva']:struct.pack('<47Q',*(stock+n*16 for n in range(47))),
                proxy+contract['callbackRva']:contract['callbackCode']}
        for name,base in [('sdk',sdk),('exe',exe)]:
            for rva,code in contract[name+'Windows']:memory[base+rva]=code
        target=0x800000000
        original=windows[0][1]
        memory[sdk+observer.ENTRY_RVA]=bytes.fromhex('ff2500000000')+struct.pack('<Q',target)+original[14:]
        memory[target]=observer.trampoline(contract,sdk+observer.ENTRY_RVA,proxy+contract['callbackRva'],0x123456789abcdef0)
        def read(address,width):return memory.get(address,b'')[:width],None
        return modules,contract,memory,read

    def test_installed_exact_fixed_code_budget_and_redaction(self):
        modules,contract,memory,read=self.make()
        result=observer.collect(read,modules,contract,[n*16 for n in range(47)])
        self.assertEqual(result['status'],'installed-code-bound')
        self.assertEqual(result['budget']['reads'],11)
        self.assertEqual(result['budget']['bytes'],1429)
        self.assertEqual(result['event'],dict(status='classified',env='cn-env-0',appKeyMatches=True,comboUrlMatches=True))
        self.assertNotIn('env',result['metadata'])
        self.assertFalse(result['payloadReadByCollector'])
        self.assertFalse(result['referenceReadByCollector'])

    def test_unknown_no_event_is_explicit(self):
        modules,contract,memory,read=self.make(0)
        result=observer.collect(read,modules,contract,[n*16 for n in range(47)])
        self.assertEqual(result['event'],dict(status='no-matching-event'))
        self.assertEqual(result['status'],'installed-code-bound')

    def test_copying_is_partial(self):
        modules,contract,memory,read=self.make(1)
        result=observer.collect(read,modules,contract,[n*16 for n in range(47)])
        self.assertEqual(result['status'],'partial')
        self.assertEqual(result['event'],dict(status='copying'))

    def test_hook_failure_requires_original_code_and_exact_state(self):
        modules,contract,memory,read=self.make(0)
        sdk,exe,proxy=(modules[name]['base'] for name in ('AccountPlatNative.dll','YuanShen.exe','Astrolabe.dll'))
        state=struct.pack('<6I2Q10I',1,80,6,113,0,0,sdk,exe,contract['sdkImageSize'],contract['exeImageSize'],1,1,0,0,0,0,0,0)
        memory[proxy+contract['stateRva']]=state
        memory[sdk+observer.ENTRY_RVA]=contract['sdkWindows'][0][1]
        result=observer.collect(read,modules,contract,[n*16 for n in range(47)])
        self.assertEqual(result['status'],'failed-original-code-bound')
        corrupted=bytearray(state);struct.pack_into('<I',corrupted,52,0)
        memory[proxy+contract['stateRva']]=bytes(corrupted)
        result=observer.collect(read,modules,contract,[n*16 for n in range(47)])
        self.assertEqual(result['stateKind'],'invalid')
        self.assertEqual(result['status'],'partial')

    def test_unsupported_event_is_fixed_failure(self):
        for event in range(3,9):
            modules,contract,memory,read=self.make(event)
            result=observer.collect(read,modules,contract,[n*16 for n in range(47)])
            self.assertEqual(set(result['event']),{'status'})

    def test_short_read_stops_without_unknown_bytes(self):
        modules,contract,memory,read=self.make()
        state_address=modules['Astrolabe.dll']['base']+contract['stateRva']
        memory[state_address]=memory[state_address][:-1]
        result=observer.collect(read,modules,contract,[n*16 for n in range(47)])
        self.assertEqual(result['status'],'partial')
        self.assertEqual(len(result['reads']),1)
        self.assertNotIn('metadata',result)

    def test_wrong_trampoline_rejected(self):
        modules,contract,memory,read=self.make()
        memory[0x800000000]=memory[0x800000000][:-1]+b'X'
        self.assertEqual(observer.collect(read,modules,contract,[n*16 for n in range(47)])['status'],'partial')

    def test_unstable_event_never_asserts_complete(self):
        modules,contract,memory,read=self.make()
        state_address=modules['Astrolabe.dll']['base']+contract['stateRva'];calls=0
        def changing(address,width):
            nonlocal calls
            if address==state_address:
                calls+=1
                if calls==2:return bytes(width),None
            return read(address,width)
        self.assertEqual(observer.collect(changing,modules,contract,[n*16 for n in range(47)])['status'],'partial')

    def test_invalid_env_or_bool_rejected_without_value_echo(self):
        for fields in (dict(event=2,env=3,app_key_matches=1,combo_url_matches=1),dict(event=2,env=0,app_key_matches=2,combo_url_matches=1)):
            with self.assertRaises(ValueError):observer.event_output(fields)

if __name__=='__main__':unittest.main()
