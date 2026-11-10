"""Owned fixed-byte collector tests. No Windows backend, target or compiler."""
import copy
import struct
import tempfile
from pathlib import Path
from types import SimpleNamespace
import unittest
from unittest.mock import patch

from . import verify_sdk72_config_stage_proxy as audit

def state_bytes(*,phase=5,event=2,mask=255,facts=1023,failure=0,error=0,env=22,trampolines=None):
    if trampolines is None:trampolines=tuple(0x8000000000+n*0x1000 if mask&(1<<n) else 0 for n in range(8))
    raw=struct.pack('<6I2Q10I',2,256,phase,failure,error,mask,0x140000000,0x180000000,
        459665408,0x932000,1,1,1,int(mask>3),event,facts,env,0)
    return raw+struct.pack('<8Q',*trampolines)+bytes(112)

def make_fixture():
    frozen,_=audit.frozen_inputs();mat=audit.materials(frozen)
    arrays=audit.frozen.source_arrays((audit.ROOT/(audit.frozen.ILHOOK+'x64.rs')).read_text(encoding='utf8'))
    modules={n:dict(base=b) for n,b in [('YuanShen.exe',0x140000000),('AccountPlatNative.dll',0x180000000),('Astrolabe.dll',0x200000000),('Astrolabe_orig.dll',0x300000000)]}
    contract=dict(stateRva=0x2000,tableRva=0x4000,callbackRva=0x800,innerRva=0x900,
        callbackCode=bytes(range(38)),arrays=arrays,exeImageSize=459665408,sdkImageSize=0x932000,
        exeWindows=[(audit.EXE_WINDOWS[0][0],mat['bridge']),(audit.EXE_WINDOWS[1][0],mat['caller'])],
        sdkWindows=[(r,bytes((n+1,))*w if n<4 else mat['writer']) for n,(r,w) in enumerate(audit.SDK_WINDOWS)],
        siteOriginals=[bytes.fromhex(row['originalHex']) for row in frozen['sites']])
    memory={};targets=tuple(0x8000000000+n*0x1000 for n in range(8))
    memory[0x200002000]=state_bytes(trampolines=targets)
    memory[0x200004000]=struct.pack('<47Q',*(0x300000000+n*16 for n in range(47)))
    memory[0x140000000+0x57b4d60]=struct.pack('<Q',0x18013d5b0)
    memory[0x200000800]=contract['callbackCode']
    for windows,base,indices in ((contract['exeWindows'],0x140000000,range(2)),(contract['sdkWindows'],0x180000000,range(2,8))):
        for start,expected in windows:
            actual=bytearray(expected)
            for n in indices:
                offset=audit.SITE_RVAS[n]-start
                if 0<=offset<len(actual):actual[offset:offset+14]=bytes.fromhex('ff2500000000')+struct.pack('<Q',targets[n])
            memory[base+start]=bytes(actual)
    for n,target in enumerate(targets):
        base=0x140000000 if n<2 else 0x180000000
        memory[target]=audit.trampoline(arrays,n,contract['siteOriginals'][n],base+audit.SITE_RVAS[n],target,0x200000800,0x9000000000+n*16)[0]
    rows=[]
    def read(address,width):
        raw=next((data[address-start:address-start+width] for start,data in memory.items() if start<=address and address+width<=start+len(data)),b'')
        rows.append((address,width));return raw,None
    return modules,contract,memory,read,rows

class StageCollectorTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):cls.original=make_fixture()
    def setUp(self):
        self.modules,self.contract,self.memory,unused,unused2=copy.deepcopy(self.original[:3])+self.original[3:]
        self.rows=[]
    def read(self,address,width):
        raw=next((data[address-start:address-start+width] for start,data in self.memory.items() if start<=address and address+width<=start+len(data)),b'')
        self.rows.append((address,width));return raw,None
    def collect(self,reader=None):return audit.collect(reader or self.read,self.modules,self.contract,[n*16 for n in range(47)])
    def test_full_exact_ledger_and_same_read_replay(self):
        raw=[]
        def read(address,width):
            data,error=self.read(address,width);raw.append(dict(ordinal=len(raw)+1,address=address,requested=width,bytesRead=len(data),bytesHex=data.hex(),error=error));return data,error
        result=self.collect(read)
        self.assertEqual(result['status'],'installed-stage-code-bound')
        self.assertEqual((len(self.rows),sum(w for a,w in self.rows),max(w for a,w in self.rows)),(30,10415,512))
        self.assertEqual(audit.replay(dict(reads=raw),self.modules,self.contract,[n*16 for n in range(47)]),result)
        self.assertTrue(result['event']['normalEpilogueThenNativeReturned']);self.assertFalse(result['naturalInitOrRoutePassed'])
    def test_each_ordinal_error_or_short_stops_at_exact_prefix(self):
        for ordinal in range(1,31):
            for short in (False,True):
                self.rows=[]
                def read(address,width):
                    data,error=self.read(address,width)
                    if len(self.rows)==ordinal:return (data[:-1],None) if short else (b'',dict(winerror=5))
                    return data,error
                result=self.collect(read)
                self.assertEqual(result['status'],'partial');self.assertEqual(len(self.rows),ordinal)
    def test_first_data_exits_and_optional_sdk_absence(self):
        for args,status in [(dict(phase=2,event=0,mask=3,facts=0,env=0),'no-p0'),
            (dict(phase=6,event=3,mask=3,facts=0,env=0,failure=202),'rejected'),
            (dict(phase=3,event=1,mask=3,facts=1,env=0),'partial')]:
            self.memory[0x200002000]=state_bytes(**args);self.rows=[]
            absent=self.modules.pop('AccountPlatNative.dll',None)
            self.assertEqual(self.collect()['status'],status);self.assertEqual(self.rows,[(0x200002000,256)])
            if absent:self.modules['AccountPlatNative.dll']=absent
    def test_reserved_invalid_bits_env_and_dependency_rejected(self):
        for offset,value in [(0,3),(4,72),(76,1),(144,1),(68,1024),(72,2),(64,99)]:
            raw=bytearray(state_bytes());struct.pack_into('<I',raw,offset,value)
            self.memory[0x200002000]=bytes(raw);self.rows=[]
            self.assertEqual(self.collect()['reason'],'invalid-data');self.assertEqual(len(self.rows),1)
        for bits in (1|(1<<9),1|(1<<5),1|(1<<3)):
            with self.assertRaises(ValueError):audit.decode_state(state_bytes(facts=bits,env=0))
    def test_data_addresses_cannot_add_arbitrary_reads(self):
        raw=bytearray(state_bytes());struct.pack_into('<Q',raw,80,0xdeadbeef)
        self.memory[0x200002000]=bytes(raw)
        result=self.collect();self.assertEqual(result['reason'],'entry-data-trampoline-differs')
        self.assertEqual(len(self.rows),3);self.assertNotIn(0xdeadbeef,[a for a,w in self.rows])
    def test_full_bridge_original_c3_c6_c9_and_zero_tails(self):
        key=0x140000000+0x6d80910
        raw=bytearray(self.memory[key]);raw[0x6d809c3-0x6d80910]^=1;self.memory[key]=bytes(raw)
        self.assertEqual(self.collect()['reason'],'full-code-window-differs')
        self.setUp();target=0x8000000000+7*0x1000;raw=bytearray(self.memory[target]);raw[-1]=1;self.memory[target]=bytes(raw)
        self.assertEqual(self.collect()['reason'],'full-trampoline-or-zero-tail-differs');self.assertEqual(len(self.rows),29)
    def test_state_after_inequality_is_partial_and_no_retry(self):
        def read(address,width):
            data,error=self.read(address,width)
            if len(self.rows)==30:return data[:-1]+b'\x01',None
            return data,error
        result=self.collect(read);self.assertEqual(result['reason'],'state-changed');self.assertEqual(len(self.rows),30)
    def test_far_mover_original_call_target_and_p0_c3(self):
        raw=self.contract['siteOriginals'][0];entry=0x140000000+audit.SITE_RVAS[0];dest=0x80000000fa
        moved,kind=audit.moved_code(0,raw,entry,dest)
        self.assertEqual(kind,'far');self.assertEqual(moved[:6],raw[:6]);self.assertEqual(moved[6:8],b'\xff\x15')
        displacement=struct.unpack_from('<i',moved,8)[0]
        self.assertEqual(struct.unpack_from('<Q',moved,12+displacement)[0],0x140534480)
        full,length,kind=audit.trampoline(self.contract['arrays'],0,raw,entry,dest-250,0x200000800,1)
        self.assertEqual(struct.unpack_from('<Q',full,length-8)[0],0x146d809c3);self.assertEqual(full[length:],bytes(384-length))
    def test_source_material_is_fixed_and_binding_caps_exact(self):
        frozen,pins=audit.frozen_inputs();mat=audit.materials(frozen)
        self.assertGreater(len(pins),86);self.assertEqual([r['stolenBytes'] for r in frozen['sites']],list(audit.WIDTHS))
        self.assertEqual(sum(n for r,n in audit.EXE_WINDOWS+audit.SDK_WINDOWS),6409)
        source=audit.binding_source(frozen,mat)
        self.assertNotIn(b'reference',source);self.assertIn(b'[Window<\'static>; 2]',source);self.assertIn(b'[Window<\'static>; 5]',source)
    def test_owned_failed_stdout_stderr_and_real_attempt_metadata_are_preserved(self):
        private=audit.RAW/'wrapper-negative-owned';private.mkdir(parents=True,exist_ok=True)
        with tempfile.TemporaryDirectory(dir=private) as directory:
            root=Path(directory);fixture=root/'owned.exe';fixture.write_bytes(b'owned negative file only')
            process=SimpleNamespace(pid=12345,returncode=1,communicate=lambda timeout:(b'not-json-first-failure',b'owned-error'))
            with patch.object(audit.subprocess,'Popen',return_value=process):result=audit.run_owned_process(fixture,root/'run1')
            self.assertFalse(result['passed']);self.assertEqual(result['rawProcess']['exitCode'],1);self.assertEqual(result['rawProcess']['pid'],12345)
            self.assertEqual((root/'run1/stdout.private.bin').read_bytes(),b'not-json-first-failure')
            self.assertEqual((root/'run1/stderr.private.bin').read_bytes(),b'owned-error')
            self.assertTrue((root/'run1/argv.private.json').exists());self.assertTrue((root/'run1/process.private.json').exists())

if __name__=='__main__':unittest.main()
