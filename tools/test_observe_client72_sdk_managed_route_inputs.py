"""B adapter artificial identities/source callbacks only; zero real APIs."""
from copy import deepcopy
import io
import json
from contextlib import redirect_stdout,redirect_stderr
import unittest
from unittest.mock import patch

from tools import observe_client72_sdk_managed_route_inputs as adapter
from tools.test_client72_sdk_managed_route_inputs import Memory,B,SECRET,assert_private
from tools.test_observe_client72_tls_inputs import ArtificialBackend,epoch


class AdapterTests(unittest.TestCase):
    def test_actual_b_tool_pin_drift_after_capture_rejects_seal(self):
        backend,memory,incoming,values,calls,identity,sources,source=self.setup_case()
        original=adapter.core.file_sha
        drift=[False]
        def checked():
            drift[0]=True
            return {'passed':True}
        with patch.object(adapter.core,'file_sha',side_effect=lambda path:'0'*64 if drift[0] else original(path)):
            report=adapter.prepare(backend,incoming,identity,source_check=checked).seal(True)
        self.assertEqual(len(memory.calls),26)
        self.assertFalse(report['sourceFinalVerified'])
        assert_private(self,report['collection'])

    def setup_case(self):
        backend=ArtificialBackend(); memory=Memory()
        backend.read=lambda handle,address,width: memory.read(address,width)
        incoming=epoch(); incoming['module']['base']=B
        incoming['implementationPins']={path:adapter.core.file_sha(adapter.core.ROOT/path) for path in adapter.TOOLS}
        values=[{key:deepcopy(incoming[key]) for key in ('pid','image','processCreationTime','module')} for _ in range(2)]
        calls=[]
        def identity(handle,pid):
            calls.append((handle,pid)); value=values.pop(0)
            if isinstance(value,Exception): raise value
            return value
        sources=[]
        def source(): sources.append(True); return {'passed':True,'artificialFixture':True}
        return backend,memory,incoming,values,calls,identity,sources,source

    def test_known_pending_two_identity_sources_separate_two_pins_and_no_handle_close(self):
        backend,memory,incoming,values,calls,identity,sources,source=self.setup_case()
        pending=adapter.prepare(backend,incoming,identity,source_check=source)
        with self.assertRaises(TypeError): json.dumps(pending)
        self.assertEqual(str(pending),'<PendingManagedRouteObservation: memory-only>')
        report=pending.seal(True)
        self.assertEqual(report['status'],'candidate'); self.assertEqual(calls,[(919,37)]*2)
        self.assertEqual(len(sources),2); self.assertEqual(backend.closed,[])
        self.assertEqual(report['identityCallbacks'],2)
        self.assertEqual(len(memory.calls),26)
        assert_private(self,pending.seal(False)['collection'])

    def test_entry_source_and_before_identity_refuse_zero_reads(self):
        for fault in ('pin','stock','pid','source','identity'):
            backend,memory,incoming,values,calls,identity,sources,source=self.setup_case()
            if fault=='pin': incoming['implementationPins'].pop(next(iter(incoming['implementationPins'])))
            if fault=='stock': incoming['stockInputPins']={}
            if fault=='pid': incoming['pid']+=1
            if fault=='identity': values[0]['processCreationTime']+=1
            if fault=='source':
                def source(): raise RuntimeError(SECRET)
            report=adapter.prepare(backend,incoming,identity,source_check=source).seal(True)
            self.assertEqual(memory.calls,[]); self.assertNotIn(SECRET,json.dumps(report))
            self.assertEqual(report['status'],'partial'); self.assertEqual(backend.closed,[])

    def test_each_identity_after_field_change_or_exception_redacts_first_known(self):
        for fault in ('pid','image','processCreationTime','module','exception'):
            backend,memory,incoming,values,calls,identity,sources,source=self.setup_case()
            if fault=='exception': values[1]=RuntimeError(SECRET)
            elif fault=='module': values[1]['module']['base']+=1
            elif fault=='image': values[1]['image']=SECRET
            else: values[1][fault]+=1
            stdout,stderr=io.StringIO(),io.StringIO()
            with redirect_stdout(stdout),redirect_stderr(stderr):
                report=adapter.prepare(backend,incoming,identity,source_check=source).seal(True)
            self.assertEqual(report['status'],'partial'); assert_private(self,report['collection'])
            self.assertNotIn(SECRET,json.dumps(report)+stdout.getvalue()+stderr.getvalue())

    def test_after_source_failure_and_outer_failure_never_publish_provisional_raw(self):
        for fail_after in (False,True):
            backend,memory,incoming,values,calls,identity,sources,source=self.setup_case()
            def checked():
                sources.append(True)
                if fail_after and len(sources)==2: raise RuntimeError(SECRET)
                return {'passed':True}
            pending=adapter.prepare(backend,incoming,identity,source_check=checked)
            report=pending.seal(not fail_after)
            if not fail_after: report=pending.seal(False)
            assert_private(self,report['collection'])
            self.assertNotIn(SECRET,json.dumps(report))

    def test_graph_partial_still_after_identity_source_but_not_complete_content_replay(self):
        backend,memory,incoming,values,calls,identity,sources,source=self.setup_case()
        memory.spans[next(address for address in memory.spans if address==B+0x586b600)]=bytes(8)
        report=adapter.prepare(backend,incoming,identity,source_check=source).seal(True)
        self.assertEqual(len(calls),2); self.assertEqual(len(sources),2)
        self.assertEqual(report['status'],'partial')
        self.assertEqual(adapter.core.replay(report['collection'])['scope'],'structural-only-no-content-reconstruction')


if __name__=='__main__': unittest.main()
