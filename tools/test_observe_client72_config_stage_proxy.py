"""Actual owned files and fake process backend around the real collector."""
import copy
from pathlib import Path
import tempfile
import unittest

from . import observe_client72_config_stage_proxy as observer
from . import test_verify_sdk72_config_stage_proxy as fixtures

class Backend:
    def __init__(self,image,snapshot,read):
        self.image_path=str(image);self.creation=dict(filetime=0x123456789abcdef0,winerror=None)
        self.snapshots=[copy.deepcopy(snapshot),copy.deepcopy(snapshot)];self.creations=[self.creation.copy(),self.creation.copy()]
        self.images=[self.image_path,self.image_path];self.statuses=[dict(alive=True) for n in range(3)]
        self.opened=[];self.closed=[];self.reads=[];self.reader=read;self.handle=1
    def open(self,pid):self.opened.append(pid);return self.handle
    def close(self,handle):self.closed.append(handle)
    def status(self,handle):return self.statuses.pop(0)
    def creation_time(self,handle):return self.creations.pop(0)
    def image(self,handle):return self.images.pop(0)
    def snapshot(self,*args):return self.snapshots.pop(0)
    def read(self,handle,address,width):self.reads.append((address,width));return self.reader(address,width)

class StageAdapterTests(unittest.TestCase):
    def setUp(self):
        private=observer.RAW/'adapter-owned';private.mkdir(parents=True,exist_ok=True)
        self.temp=tempfile.TemporaryDirectory(prefix='lifecycle-',dir=private);self.addCleanup(self.temp.cleanup)
        self.client=Path(self.temp.name);plugins=self.client/'YuanShen_Data/Plugins';plugins.mkdir(parents=True)
        self.modules,self.contract,self.memory,self.read,self.rows=fixtures.make_fixture();self.identities={}
        for n,name in enumerate(observer.MODULES):
            path=self.client/name if name=='YuanShen.exe' else plugins/name;raw=('owned lifecycle '+name).encode();path.write_bytes(raw)
            self.modules[name].update(name=name,path=str(path),size=10000+n)
            self.identities[name]=dict(path=str(path),bytes=len(raw),sha256=observer.audit.digest(raw),imageSize=10000+n)
        snapshot=dict(error=None,items=list(copy.deepcopy(self.modules).values()))
        self.backend=Backend(self.client/'YuanShen.exe',snapshot,self.read)
        self.evidence=dict(pid=1234,finalStatus=dict(alive=True),processCreationTime=self.backend.creation.copy(),samples=[dict(modules=copy.deepcopy(snapshot))])
        self.raw=[]
    def inspect(self):return observer.inspect_process(self.backend,self.client,self.evidence,self.identities,self.contract,[n*16 for n in range(47)],self.raw)
    def test_success_30_reads_same_sidecar_and_one_close(self):
        result=self.inspect();self.assertEqual(result['status'],'installed-stage-code-bound')
        self.assertEqual(self.backend.closed,[1]);self.assertEqual(result['closeCount'],1)
        self.assertEqual((len(self.raw),sum(r['requested'] for r in self.raw)),(30,10415))
        replay=observer.audit.replay(dict(reads=self.raw),self.modules,self.contract,[n*16 for n in range(47)])
        for key,value in replay.items():self.assertEqual(result[key],value)
    def test_start_identity_rejection_has_zero_rpm_and_closes(self):
        self.backend.creations[0]['filetime']+=1
        result=self.inspect();self.assertEqual(result['reason'],'start-process-creation-differs');self.assertEqual(self.raw,[]);self.assertEqual(self.backend.closed,[1])
    def test_module_drift_final_exit_and_one_close(self):
        self.backend.snapshots[1]['items'][0]['base']+=4096
        result=self.inspect();self.assertEqual(result['reason'],'end-module-binding-differs');self.assertEqual(len(self.raw),30);self.assertEqual(self.backend.closed,[1])
        self.setUp();self.backend.statuses[-1]['alive']=False
        self.assertEqual(self.inspect()['reason'],'end-target-exited');self.assertEqual(self.backend.closed,[1])
    def test_no_p0_without_sdk_is_one_data_read_and_full_final_lifecycle(self):
        self.memory[0x200002000]=fixtures.state_bytes(phase=2,event=0,mask=3,facts=0,env=0)
        for snap in [*self.backend.snapshots,self.evidence['samples'][0]['modules']]:snap['items']=[r for r in snap['items'] if r['name']!='AccountPlatNative.dll']
        result=self.inspect();self.assertEqual(result['status'],'no-p0');self.assertEqual(len(self.raw),1);self.assertEqual(self.backend.closed,[1]);self.assertTrue(result['statusAfterValidation']['alive'])
    def test_each_error_prefix_still_checks_identity_and_closes(self):
        for ordinal in (1,2,20,21,29,30):
            self.setUp();base_read=self.read
            def read(address,width):
                data,error=base_read(address,width)
                return (data[:-1],None) if len(self.rows)==ordinal else (data,error)
            self.backend.reader=read;result=self.inspect()
            self.assertEqual(result['status'],'partial');self.assertEqual(len(self.raw),ordinal);self.assertIn('identityAfter',result);self.assertEqual(self.backend.closed,[1])
    def test_exit_before_open_or_open_failure_no_reads(self):
        self.evidence['finalStatus']['alive']=False;self.assertEqual(self.inspect()['reason'],'target-exited');self.assertEqual(self.backend.opened,[])
        self.evidence['finalStatus']['alive']=True;self.backend.handle=0;self.assertEqual(self.inspect()['reason'],'process-unavailable');self.assertEqual(self.backend.closed,[])
    def test_acceptance_is_explicit_and_fails_closed_without_record(self):
        with self.assertRaises(ValueError):observer.accepted_proof(observer.RAW/'missing.private.json',None)

if __name__=='__main__':unittest.main()
