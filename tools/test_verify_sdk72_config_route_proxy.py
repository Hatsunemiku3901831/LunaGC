"""Owned fixed-byte collector tests. No Windows backend, target or compiler."""
import copy
import json
import struct
import tempfile
from pathlib import Path
from types import SimpleNamespace
import unittest
from unittest.mock import patch

from . import verify_sdk72_config_route_proxy as audit

def state_bytes(*,phase=5,event=2,mask=31,facts=1023,failure=0,error=0,route_class=0x100,trampolines=None):
    if trampolines is None:trampolines=tuple(0x8000000000+n*0x1000 if mask&(1<<n) else 0 for n in range(5))
    raw=struct.pack('<6I2Q10I',3,256,phase,failure,error,mask,0x140000000,0x180000000,
        459665408,0x932000,1,1,1,int(mask>3),event,facts,route_class,0)
    return raw+struct.pack('<5Q',*trampolines)+bytes(136)

def make_fixture():
    frozen,_=audit.frozen_inputs();mat=audit.materials(frozen)
    arrays=audit.frozen.source_arrays((audit.ROOT/(audit.frozen.ILHOOK+'x64.rs')).read_text(encoding='utf8'))
    modules={n:dict(base=b) for n,b in [('YuanShen.exe',0x140000000),('AccountPlatNative.dll',0x180000000),('Astrolabe.dll',0x200000000),('Astrolabe_orig.dll',0x300000000)]}
    contract=dict(stateRva=0x2000,tableRva=0x4000,callbackRva=0x800,innerRva=0x900,
        callbackCode=bytes(range(38)),arrays=arrays,exeImageSize=459665408,sdkImageSize=0x932000,
        exeWindows=[(audit.EXE_WINDOWS[0][0],mat['bridge']),(audit.EXE_WINDOWS[1][0],mat['caller'])],
        sdkWindows=[(r,bytes((n+1,))*w if n==0 else (mat['writer'] if n==1 else mat['mapWriter'])) for n,(r,w) in enumerate(audit.SDK_WINDOWS)],
        siteOriginals=[bytes.fromhex(row['originalHex']) for row in frozen['sites']])
    memory={};targets=tuple(0x8000000000+n*0x1000 for n in range(5))
    memory[0x200002000]=state_bytes(trampolines=targets)
    memory[0x200004000]=struct.pack('<47Q',*(0x300000000+n*16 for n in range(47)))
    memory[0x140000000+0x57b4d60]=struct.pack('<Q',0x18013d5b0)
    memory[0x200000800]=contract['callbackCode']
    for windows,base,indices in ((contract['exeWindows'],0x140000000,range(2)),(contract['sdkWindows'],0x180000000,range(2,5))):
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

class RouteCollectorTests(unittest.TestCase):
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
        self.assertEqual(result['status'],'installed-route-code-bound')
        self.assertEqual((len(self.rows),sum(w for a,w in self.rows),max(w for a,w in self.rows)),(25,9262,512))
        self.assertEqual(audit.replay(dict(reads=raw),self.modules,self.contract,[n*16 for n in range(47)]),result)
        self.assertTrue(result['event']['normalEpilogueThenNativeReturned']);self.assertFalse(result['naturalInitOrRoutePassed'])
    def test_each_ordinal_error_or_short_stops_at_exact_prefix(self):
        for ordinal in range(1,26):
            for short in (False,True):
                self.rows=[]
                def read(address,width):
                    data,error=self.read(address,width)
                    if len(self.rows)==ordinal:return (data[:-1],None) if short else (b'',dict(winerror=5))
                    return data,error
                result=self.collect(read)
                self.assertEqual(result['status'],'partial');self.assertEqual(len(self.rows),ordinal)
    def test_first_data_exits_and_optional_sdk_absence(self):
        for args,status in [(dict(phase=2,event=0,mask=3,facts=0,route_class=0),'no-p0'),
            (dict(phase=6,event=3,mask=3,facts=0,route_class=0,failure=202),'rejected'),
            (dict(phase=3,event=1,mask=3,facts=1,route_class=0),'partial')]:
            self.memory[0x200002000]=state_bytes(**args);self.rows=[]
            absent=self.modules.pop('AccountPlatNative.dll',None)
            self.assertEqual(self.collect()['status'],status);self.assertEqual(self.rows,[(0x200002000,256)])
            if absent:self.modules['AccountPlatNative.dll']=absent
    def test_reserved_invalid_bits_env_and_dependency_rejected(self):
        for offset,value in [(0,4),(4,72),(76,1),(120,1),(68,1024),(72,2),(64,99)]:
            raw=bytearray(state_bytes());struct.pack_into('<I',raw,offset,value)
            self.memory[0x200002000]=bytes(raw);self.rows=[]
            self.assertEqual(self.collect()['reason'],'invalid-data');self.assertEqual(len(self.rows),1)
        for bits in (1|(1<<8),1|(1<<5),1|(1<<3)):
            with self.assertRaises(ValueError):audit.decode_state(state_bytes(facts=bits,route_class=0))
    def test_data_addresses_cannot_add_arbitrary_reads(self):
        raw=bytearray(state_bytes());struct.pack_into('<Q',raw,80,0xdeadbeef)
        self.memory[0x200002000]=bytes(raw)
        result=self.collect();self.assertEqual(result['reason'],'entry-data-trampoline-differs')
        self.assertEqual(len(self.rows),3);self.assertNotIn(0xdeadbeef,[a for a,w in self.rows])
    def test_full_bridge_original_c3_c6_c9_and_zero_tails(self):
        key=0x140000000+0x6d80910
        raw=bytearray(self.memory[key]);raw[0x6d809c3-0x6d80910]^=1;self.memory[key]=bytes(raw)
        self.assertEqual(self.collect()['reason'],'full-code-window-differs')
        self.setUp();target=0x8000000000+4*0x1000;raw=bytearray(self.memory[target]);raw[-1]=1;self.memory[target]=bytes(raw)
        self.assertEqual(self.collect()['reason'],'full-trampoline-or-zero-tail-differs');self.assertEqual(len(self.rows),24)
    def test_state_after_inequality_is_partial_and_no_retry(self):
        def read(address,width):
            data,error=self.read(address,width)
            if len(self.rows)==25:return data[:-1]+b'\x01',None
            return data,error
        result=self.collect(read);self.assertEqual(result['reason'],'state-changed');self.assertEqual(len(self.rows),25)
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
        self.assertEqual(sum(n for r,n in audit.EXE_WINDOWS+audit.SDK_WINDOWS),6408)
        source=audit.binding_source(frozen,mat)
        self.assertNotIn(b'reference',source);self.assertIn(b'[Window<\'static>; 2]',source);self.assertIn(b'[Window<\'static>; 3]',source)
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

def map_image(key=b'comboLogin',value=None,count=1,depth=1):
    if value is None:value=next(iter(audit.natural.ALLOWED_ROUTES))
    obj,head,node=0x8000001000,0x8000002000,0x8000010000
    memory={obj:struct.pack('<QQ',head,count),head:struct.pack('<3Q',head,node if count else head,head)+b'\0\1'+bytes(6)}
    def header(text,address,force=False):
        if len(text)<=15 and not force:return text+bytes(16-len(text))+struct.pack('<QQ',len(text),15)
        memory[address]=text;return struct.pack('<Q',address)+bytes(8)+struct.pack('<QQ',len(text),max(16,len(text)))
    for index in range(depth):
        address=node+index*0x1000;selected=key if index==depth-1 else b'a'*64
        links=struct.pack('<3Q',head,head,address+0x1000 if index<depth-1 else head)+bytes(8)
        memory[address]=links+header(selected,0x9000000000+index*0x1000,depth>1)+header(value,0xa000000000)
    return obj,head,node,memory

class MapOracle:
    """Artificial byte model over the frozen existing Python map algorithm."""
    def __init__(self,memory,fault=0,short=False):self.memory=memory;self.rows=[];self.fault=fault;self.short=short
    def read(self,address,width):
        if len(self.rows)>=69 or sum(w for a,w in self.rows)+width>4440 or width>256:raise audit.natural.ContractStop('map-budget')
        self.rows.append((address,width))
        if len(self.rows)==self.fault:raise audit.natural.ContractStop('short-read' if self.short else 'read-error')
        for start,data in self.memory.items():
            if start<=address and address+width<=start+len(data):return data[address-start:address-start+width]
        raise audit.natural.ContractStop('unmapped-owned-span')
    def classify(self,object):
        self.read(0xb000000000,8) # Same new ledger's M0 caller, then frozen lookup.
        value=audit.natural.combo_value(self,object)
        if value is None:return 1
        if value==b'':return 2
        return next((0x100+n for n,known in enumerate(audit.natural.ALLOWED_ROUTES) if value==known),3)

class RouteMapArtificialTests(unittest.TestCase):
    def oracle(self,key=b'comboLogin',value=None,count=1,depth=1):
        obj,head,node,memory=map_image(key,value,count,depth);memory[0xb000000000]=struct.pack('<Q',0x180379882)
        return obj,head,node,MapOracle(memory)
    def test_absent_empty_all_cn_and_exact_unknown(self):
        for key,value,count,expected in [(b'comboLogin',b'x',0,1),(b'comboLogin',b'',1,2),
            *[(b'comboLogin',value,1,0x100+n) for n,value in enumerate(audit.natural.ALLOWED_ROUTES)],
            (b'comboLogin',next(iter(audit.natural.ALLOWED_ROUTES))+b'?a=1',1,3),
            (b'comboLogin',next(iter(audit.natural.ALLOWED_ROUTES))+b'/',1,3)]:
            obj,head,node,m=self.oracle(key,value,count);self.assertEqual(m.classify(obj),expected)
    def test_key64_65_value256_257_and_non_target_value_never_read(self):
        for width in (64,65):
            obj,head,node,m=self.oracle(b'a'*width,b'UNREAD_PRIVATE_NON_TARGET')
            if width==64:self.assertEqual(m.classify(obj),1)
            else:
                with self.assertRaises(audit.natural.ContractStop):m.classify(obj)
            self.assertNotIn(node+64,[a for a,w in m.rows])
        for width in (256,257):
            obj,head,node,m=self.oracle(value=b'x'*width)
            if width==256:self.assertEqual(m.classify(obj),3)
            else:
                with self.assertRaises(audit.natural.ContractStop):m.classify(obj)
    def test_count_and_lengths_use_full_qword_sentinel_and_null_reject(self):
        for count in (305,306,0x100000000):
            obj,head,node,m=self.oracle(count=count)
            if count==305:self.assertEqual(m.classify(obj),0x100)
            else:
                with self.assertRaises(audit.natural.ContractStop):m.classify(obj)
        for field,value in ((48,0x10000000a),(56,0),(80,0x100000001),(64,0)):
            obj,head,node,m=self.oracle();raw=bytearray(m.memory[node]);struct.pack_into('<Q',raw,field,value);m.memory[node]=bytes(raw)
            with self.assertRaises(audit.natural.ContractStop):m.classify(obj)
        obj,head,node,m=self.oracle();raw=bytearray(m.memory[head]);raw[25]=0;m.memory[head]=bytes(raw)
        with self.assertRaises(audit.natural.ContractStop):m.classify(obj)
    def test_every_69_ordinal_short_error_prefix_and_cumulative_cap(self):
        obj,head,node,m=self.oracle(value=b'x'*256,count=305,depth=32)
        self.assertEqual(m.classify(obj),3);self.assertEqual(len(m.rows),69);self.assertLessEqual(sum(w for a,w in m.rows),4440)
        with self.assertRaises(audit.natural.ContractStop):m.read(obj,8)
        for ordinal in range(1,70):
            for short in (False,True):
                error=MapOracle(m.memory,ordinal,short)
                with self.assertRaises(audit.natural.ContractStop):error.classify(obj)
                self.assertEqual(len(error.rows),ordinal)
    def test_cycle_depth_and_unsigned_key_order(self):
        obj,head,node,m=self.oracle(key=b'a',value=b'UNREAD');raw=bytearray(m.memory[node]);struct.pack_into('<Q',raw,16,node);m.memory[node]=bytes(raw)
        with self.assertRaises(audit.natural.ContractStop):m.classify(obj)
        obj,head,node,m=self.oracle(key=b'\xff',value=b'UNREAD');raw=bytearray(m.memory[node]);struct.pack_into('<Q',raw,16,0xdeadbeef);m.memory[node]=bytes(raw)
        self.assertEqual(m.classify(obj),1) # high unsigned key takes left=head.
        obj,head,node,m=self.oracle(key=b'a'*64,depth=32)
        raw=bytearray(m.memory[node+31*0x1000]);struct.pack_into('<Q',raw,16,0xdeadbeef);m.memory[node+31*0x1000]=bytes(raw)
        with self.assertRaises(audit.natural.ContractStop):m.classify(obj)
    def test_unknown_route_never_exports_text_length_hash_or_stored_env(self):
        unknown=b'PRIVATE_UNKNOWN_ROUTE_SENTINEL';obj,head,node,m=self.oracle(value=unknown);self.assertEqual(m.classify(obj),3)
        state=audit.decode_state(state_bytes(route_class=3,facts=1023&~(1<<8)));event=audit.event_output(state);encoded=json.dumps(event)
        self.assertNotIn(unknown.decode(),encoded);self.assertFalse(event['storedEnvObserved']);self.assertFalse(event['naturalComboRouteUseObserved'])
        self.assertFalse(any(k in event for k in ('urlLength','urlHash','urlAddress','urlRaw','storedEnv')))
    def test_historical_manifest_is_immutable_not_current_manifest_gate(self):
        prior=audit.RAW/'historical-stage/Cargo.toml'
        self.assertEqual(audit.digest(prior.read_bytes()),'dff85e2794449214859a88dbc5f013c72b9be9d6ab250210de38c3307a309dce')
        self.assertNotEqual(prior.read_bytes(),(audit.ROOT/'patch/Cargo.toml').read_bytes())
        audit.frozen_inputs()

if __name__=='__main__':unittest.main()
