import copy
import hashlib
import io
import struct
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
from tools.migration72 import scene_world_required_parent_payload as s

class Gates(unittest.TestCase):
    def test_pin_and_physical_drift(self):
        with tempfile.TemporaryDirectory() as d:
            root=Path(d);p=root/'source';p.write_bytes(b'abc');h=s.sha(b'abc');s.checked(p,3,h)
            for size,digest in ((4,h),(3,s.sha(b'abd'))):
                with self.assertRaises(ValueError):s.checked(p,size,digest)
            header=b'h'*13737;payload=header+b'body';p.write_bytes(payload);hdr=root/'header';hdr.mkdir();(hdr/'header-prefix.bin').write_bytes(header)
            source={'bytes':len(payload),'sha256':s.sha(payload),'md5':hashlib.md5(payload).hexdigest()}
            with patch.object(s,'PREFIX_END',len(payload)),patch.object(s,'HDR',hdr):
                self.assertEqual(s.physical_prefix(p,source),payload)
                with self.assertRaises(ValueError):s.physical_prefix(p,{**source,'sha256':'wrong'})
                with self.assertRaises(ValueError):s.physical_prefix(p,{**source,'md5':'wrong'})
                with patch.object(s,'PREFIX_END',len(payload)+1):
                    with self.assertRaises(ValueError):s.physical_prefix(p,source)

    def test_entry_identity_full64_and_bounds(self):
        raw=struct.pack('<I8siQQ',8,b'54c32b51',0,14619,3266);entry={'start':4,'endExclusive':36,'fileSize':3266}
        self.assertEqual(s.selected_entry(b'aaaa'+raw,entry),raw)
        for values in ((8,b'54c32b51',1,14619,3266),(8,b'54c32b51',0,14619+(1<<32),3266),(8,b'54c32b51',0,14619,3267),(8,b'54c32b52',0,14619,3266)):
            with self.assertRaises(ValueError):s.selected_entry(b'aaaa'+struct.pack('<I8siQQ',*values),entry)
        with self.assertRaises(ValueError):s.selected_entry(b'aaaa'+raw[:-1],entry)

    def test_object_count_nonzero_tail_and_boundaries(self):
        output=bytearray(131072);start=34592;struct.pack_into('<I',output,start,3266);output[start+4:start+3270]=b'x'*3266;output[start+3270:start+3272]=b'\x12\xfe'
        entry={'fileSize':3266};obj={'start':99780384,'endExclusive':99783656};block={'outputBegin':99745792}
        raw,payload,tail,details=s.extract(bytes(output),entry,obj,block)
        self.assertEqual((len(raw),len(payload),tail),(3272,3266,b'\x12\xfe'));self.assertEqual(details['tailHex'],'12fe')
        output[start]=0
        with self.assertRaises(ValueError):s.extract(bytes(output),entry,obj,block)
        with self.assertRaises(ValueError):s.extract(bytes(output[:-1]),entry,obj,block)
        with self.assertRaises(ValueError):s.extract(bytes(131072),entry,{**obj,'endExclusive':obj['endExclusive']+1},block)

    def test_independent_lz4_exact_and_invalid(self):
        from tools.client72_resource_payload import lz4_block
        self.assertEqual(lz4_block(b'\x50hello',5),b'hello')
        for data,size in ((b'\x50hell',5),(b'\x50hello',6),(b'\x50hello',4),(b'\x10A\x00\x00',5),(b'',5)):
            with self.assertRaises(ValueError):lz4_block(data,size)

    def test_prefix_failure_guards_models_limit_and_edges(self):
        proof={'al':1,'guardsIntact':True,'tailUnchanged':True,'protectedWriteEvents':0,'headerStorageProtectedBytes':13725,'callEdges':[],'observedExecution':{'steps':42,'callEdges':[]},'run':{'steps':42,'modeledCalls':[],'memoryModels':[],'failure':None,'error':None,'stop':'0x620000000'}}
        s.verify_prefix_proof(proof)
        for change in ({'al':0},{'guardsIntact':False},{'tailUnchanged':False},{'protectedWriteEvents':1},{'headerStorageProtectedBytes':13724}):
            with self.assertRaises(ValueError):s.verify_prefix_proof({**proof,**change})
        for key,value in (('modeledCalls',[1]),('memoryModels',[1]),('failure','unknown edge'),('error','failed'),('stop','0x0'),('steps',100001)):
            bad=copy.deepcopy(proof);bad['run'][key]=value
            if key=='steps':bad['observedExecution']['steps']=value
            with self.assertRaises(ValueError):s.verify_prefix_proof(bad)
        bad=copy.deepcopy(proof);bad['observedExecution']['callEdges']=[{'site':'0x1'}]
        with self.assertRaises(ValueError):s.verify_prefix_proof(bad)

    def test_source_failure_before_prefix(self):
        with tempfile.TemporaryDirectory() as d:
            with patch.object(s,'ingress',side_effect=ValueError('source drift')),patch.object(s.subprocess,'check_output') as game:
                with self.assertRaises(ValueError):s.collect(Path(d))
                game.assert_not_called()
            import json
            f=json.loads((Path(d)/'failure.private.json').read_bytes());self.assertEqual(f['stage'],'sources');self.assertEqual(f['nativePrefixCount'],0);self.assertFalse((Path(d)/'pre-execute.private.json').exists())

    def test_probe_run_recording_preserves_success_and_original_error(self):
        import json
        from types import SimpleNamespace
        import tools.client72_resource_provider as provider
        import tools.client72_resource_payload as payload_module
        import tools.migration72.scene_mplevel_actual_gadget_bin_metadata as metadata
        block={'index':761,'rawBegin':25741681,'rawEndExclusive':25777971,'rawBytes':36290,'outputBegin':99745792,'outputEndExclusive':99876864,'outputBytes':131072}
        entry={'fileSize':3266};obj={'start':99780384,'endExclusive':99783656};prefix=bytes(s.PREFIX_END);key=b'fixture key';real_sha=s.sha
        out=bytearray(131072);struct.pack_into('<I',out,34592,3266);out[34596:37862]=b'x'*3266;out[37862:37864]=b'\x12\xfe'
        class FakeUC:
            def __init__(self):self.removed=[]
            def hook_add(self,*args):return len(self.removed)+len(args)
            def hook_del(self,h):self.removed.append(h)
        class FakeProbe:
            def __init__(self,fail):
                self.fail=fail;self.engine=SimpleNamespace(models={},allowed=[],uc=FakeUC(),U=SimpleNamespace(UC_HOOK_CODE=1,UC_HOOK_MEM_WRITE=2));self.closed=False
                self.runs=[{'entry':'0x1415aa8c0','stop':'0x620000000','steps':0,'modeledCalls':[],'memoryModels':[],'failure':None,'error':None}]
                self.last={'al':1,'targets':['0x1415aa8c0'],'callEdges':[],'prefixWriteEvents':0,'protectedWriteEvents':0,'headerStorageProtectedBytes':13725,'guardsIntact':True,'tailUnchanged':True,'run':self.runs[-1]}
            def transform(self,blob,physical,index):
                if self.fail:raise RuntimeError('original prefix error')
                return physical[block['rawBegin']:block['rawEndExclusive']],self.runs[-1]
            def close(self):self.closed=True
        class FakeSnapshot:
            def __init__(self,*args):self.base=0
            def at(self,name,address,size):return bytes(8) if name.endswith('.cell') else key
        def fake_sha(data):return '789e5887f53e09404fcf8b4bf38b36565e6dc05a532460ca37ebe17f1f612297' if data==key else real_sha(data)
        for fail in (False,True):
            with self.subTest(prefix_raises=fail),tempfile.TemporaryDirectory() as d:
                root=Path(d);client=root/'client';client.mkdir();(client/'YuanShen.exe').write_bytes(b'PE');dest=root/'out';dest.mkdir();probe=FakeProbe(fail)
                stock={'YuanShen.exe':{'bytes':2,'sha256':real_sha(b'PE')}};req={'bounds':{'newBlocks':[block]}}
                args=(req,{},b'profile',{},stock,{'path':'fixture'},b'',{},[],entry,obj)
                with patch.object(s,'ingress',return_value=args),patch.object(s,'physical_prefix',return_value=prefix),patch.object(s.subprocess,'check_output',return_value=str(client)),patch.object(s,'sha',side_effect=fake_sha),patch.object(provider,'Snapshot',FakeSnapshot),patch.object(provider,'u64',return_value=1),patch.object(metadata,'make_probe',return_value=probe),patch.object(payload_module,'lz4_block',return_value=bytes(out)):
                    if fail:
                        with self.assertRaisesRegex(RuntimeError,'original prefix error'):s.collect(dest)
                    else:s.collect(dest)
                report=json.loads((dest/('failure.private.json' if fail else 'result.private.json')).read_bytes())
                self.assertFalse(hasattr(probe.engine,'runs'));self.assertEqual(report['observedExecution']['lastEngineRun'],probe.runs[-1]);self.assertEqual(len(probe.engine.uc.removed),2);self.assertTrue(probe.closed)
                if fail:
                    self.assertEqual(report['error'],'original prefix error');self.assertEqual(report['errorType'],'RuntimeError');self.assertEqual(report['independentLZ4Count'],0)
                else:self.assertTrue(report['passed']);self.assertEqual(report['byteArray']['tailHex'],'12fe')

    def test_fixed_ingress_pins_identity_and_block(self):
        req,pins,_,_,stock,source,_,_,allowed,entry,obj=s.ingress()
        self.assertGreaterEqual(len(pins),78);self.assertEqual(len(stock),5);self.assertEqual(len(allowed),29)
        self.assertEqual((entry['name'],entry['fileID'],entry['pathID'],entry['fileSize']),('54c32b51',0,14619,3266))
        self.assertEqual((obj['classID'],obj['typeIndex']),(1208,2));self.assertEqual(req['bounds']['newBlocks'][0]['index'],761)
        self.assertEqual(source['sha256'],'cb1a68de51f3baeba7afc771284d46410a7b6cb993aa3d085a0c8a7813e14768')

if __name__=='__main__':unittest.main()
