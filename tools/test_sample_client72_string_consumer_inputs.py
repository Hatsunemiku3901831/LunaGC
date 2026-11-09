"""Owned stock epoch and read lifecycle checks; no OS process access."""
import copy
from datetime import datetime,timezone,timedelta
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
from tools import sample_client72_string_consumer_inputs as card
from tools.test_sample_client72_avatar_warm_usages import FakeBackend

class EpochTest(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory();self.root=Path(self.tmp.name);self.addCleanup(self.tmp.cleanup)
        names=['tools/collect72_string_consumer_inputs.py','tools/sample_client72_string_consumer_inputs.py','tools/observe_client72_uninstrumented.py','tools/sample_client72_interface_context.py']
        self.pins={}
        for name in names:
            path=self.root/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_text(name);self.pins[name]=card.digest(path.read_bytes())
        self.patches=[patch.object(card,'MONITOR_SHA',self.pins[names[2]]),patch.object(card,'HELPER_SHA',self.pins[names[3]])]
        for p in self.patches:p.start();self.addCleanup(p.stop)
        self.raw=self.root/'local/migration72/d02-parallel-20261109/r3-string-consumer';self.raw.mkdir(parents=True)
        self.planpath=self.raw/'live-plan.private.json';self.readypath=self.raw/'live-ready.private.json'
        self.base=datetime(2026,11,9,tzinfo=timezone.utc);t=lambda sec:(self.base+timedelta(seconds=sec)).isoformat()
        self.monitor='local/migration72/d02-active/plain/d02-parallel-string-v2-owned.json'
        self.plan=dict(schema=1,runId='owned',createdUTC=t(0),monitorPath=self.monitor,outputPath='local/migration72/d02-parallel-20261109/r3-string-consumer/live/owned.json',sourcePins=self.pins,monitorSourceSHA256=self.pins[names[2]],budget=dict(maxReads=54,maxBytes=2704,maxSingleRead=208))
        ft=int(((self.base+timedelta(seconds=2))-datetime(1601,1,1,tzinfo=timezone.utc)).total_seconds()*10000000)
        self.source=dict(status='completed',elapsedSeconds=90,finalStatus=dict(alive=True),pid=37,samples=[{}],sourceSHA256=self.pins[names[2]],startUtc=t(2),launchTimeUtc=t(2),processCreationTime=dict(filetime=ft,winerror=None))
        self.ready=dict(schema=1,runId='owned',confirmedUTC=t(1),monitorPath=self.monitor)
        self.write()
    def write(self):
        self.planpath.write_text(json.dumps(self.plan));self.ready['planSHA256']=card.digest(self.planpath.read_bytes());self.readypath.write_text(json.dumps(self.ready))
        monitor=self.root/self.monitor;monitor.parent.mkdir(parents=True,exist_ok=True);monitor.write_text(json.dumps(self.source))
    def gate(self):return card.epoch_gate(self.planpath,self.readypath,now=self.base+timedelta(seconds=100),root=self.root)
    def test_valid(self):self.assertEqual(self.gate()[0]['runId'],'owned')
    def test_old_path_ready_creation_time_and_source_drift(self):
        for change in ('path','ready','creation','source','late','window'):
            self.setUp()
            if change=='path':self.plan['monitorPath']='local/old.json'
            if change=='ready':self.ready['runId']='old'
            if change=='creation':self.source['processCreationTime']['filetime']-=1000000000
            if change=='source':self.source['sourceSHA256']='old'
            if change=='late':self.source['startUtc']=(self.base+timedelta(seconds=301)).isoformat()
            if change=='window':self.source['launchTimeUtc']=(self.base-timedelta(seconds=100)).isoformat()
            self.write()
            with self.assertRaises(ValueError,msg=change):self.gate()
    def test_missing_receipt_and_pin_drift(self):
        self.readypath.unlink()
        with self.assertRaises(FileNotFoundError):self.gate()
        self.write();(self.root/'tools/collect72_string_consumer_inputs.py').write_text('drift')
        with self.assertRaises(ValueError):self.gate()

class LifecycleTest(unittest.TestCase):
    def setUp(self):
        self.mods=dict(error=None,items=[dict(name='YuanShen.exe',path='synthetic',base=0x180000000,size=4096)])
        self.b=FakeBackend(self.mods);self.b.creations=[dict(filetime=1,winerror=None)]*2
        self.inputs=dict(profileSHA256='profile',files={name:dict(sha256=name) for name in ('binary','metadata','startup','sdk','astrolabe')},config={name:dict(exists=False) for name in ('.env','.env.local','config.json')})
        self.e=dict(pid=37,processCreationTime=dict(filetime=1,winerror=None),finalStatus=dict(alive=True),inputs=self.inputs);self.obs=self.mods['items'][0]
    def run_sample(self,collector=None):
        with patch.object(card.env,'same_file',return_value=True),patch.object(card.env,'verified_inputs',return_value=copy.deepcopy(self.inputs)):
            return card.sample(self.b,Path('synthetic'),self.e,self.obs,collector=collector or (lambda read,base:dict(status='candidate',value=read(base+8,8))))
    def test_lifecycle(self):
        r=self.run_sample();self.assertEqual(r['status'],'candidate');self.assertTrue(r['endIdentityVerified']);self.assertEqual(self.b.closed,[919]);self.assertEqual(r['backendRecords'][0]['bytesHex'],'6162636465666768')
    def test_creation_change_and_oversize(self):
        self.b.creations[1]=dict(filetime=2,winerror=None);r=self.run_sample();self.assertEqual(r['status'],'partial');self.assertEqual(r['failureStage'],'after');self.assertEqual(r['identityObservations'][1]['creationTime']['filetime'],2);self.assertEqual(r['identityObservations'][1]['mainModule'],self.obs);self.assertEqual(self.b.closed,[919])
        self.setUp();r=self.run_sample(lambda read,base:read(base,209));self.assertEqual(r['status'],'partial');self.assertEqual(self.b.reads,[]);self.assertEqual(self.b.closed,[919])
    def test_end_stock_change_and_close(self):
        changed=copy.deepcopy(self.inputs);changed['files']['sdk']['sha256']='changed'
        with patch.object(card.env,'same_file',return_value=True),patch.object(card.env,'verified_inputs',return_value=changed):
            r=card.sample(self.b,Path('synthetic'),self.e,self.obs,collector=lambda read,base:dict(status='candidate',value=read(base+8,8)))
        self.assertEqual(r['failureStage'],'end-inputs');self.assertEqual(r['reason'],'end-inputs-differ');self.assertEqual(r['endInputs'],changed)
        self.assertTrue(r['finalStatus']['alive']);self.assertEqual(self.b.closed,[919]);self.assertEqual(r['backendBudget']['reads'],1)
    def test_end_inputs_then_final_exit(self):
        calls=[];original_status=self.b.status
        def status(handle):calls.append('status');return original_status(handle)
        def inputs(root,game):calls.append('inputs');return copy.deepcopy(self.inputs)
        self.b.status=status;self.b.statuses[-1]=dict(alive=False)
        with patch.object(card.env,'same_file',return_value=True),patch.object(card.env,'verified_inputs',side_effect=inputs):
            r=card.sample(self.b,Path('synthetic'),self.e,self.obs,collector=lambda read,base:dict(status='candidate',value=read(base+8,8)))
        self.assertEqual(calls,['status','status','inputs','status']);self.assertEqual(r['failureStage'],'final-alive');self.assertEqual(self.b.closed,[919])
    def test_end_inputs_os_error_sanitized(self):
        with patch.object(card.env,'same_file',return_value=True),patch.object(card.env,'verified_inputs',side_effect=OSError(5,'private failure')):
            r=card.sample(self.b,Path('synthetic'),self.e,self.obs,collector=lambda read,base:dict(status='candidate'))
        self.assertEqual(r['failureStage'],'end-inputs');self.assertEqual(r['error']['errno'],5);self.assertNotIn('private failure',str(r));self.assertEqual(self.b.closed,[919])
    def test_before_module_mismatch_preserves_snapshot(self):
        self.b.modules[0]['items'][0]['base']+=1;r=self.run_sample()
        self.assertEqual(r['failureStage'],'before');self.assertEqual(r['identityObservations'][0]['mainModule']['base'],self.obs['base']+1);self.assertEqual(self.b.closed,[919]);self.assertEqual(self.b.reads,[])
    def test_exit_and_error(self):
        self.b.statuses[-1]=dict(alive=False);r=self.run_sample();self.assertEqual(r['status'],'partial');self.assertEqual(self.b.closed,[919])
        self.setUp();self.b.responses[0]=OSError(5,'private');r=self.run_sample();self.assertEqual(r['backendRecords'][0]['error']['winerror'],5);self.assertNotIn('private',str(r))

if __name__=='__main__':unittest.main()
