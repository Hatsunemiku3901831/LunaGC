"""Pure protocol/source/lifecycle mocks; never start Java, Mongo, HTTP or native emulation."""
import contextlib
import copy
import io
import json
import os
from pathlib import Path
import queue
import subprocess
import tempfile
import time
from types import SimpleNamespace
import unittest
from unittest.mock import Mock, patch

from tools import verify_client72_combo_http as v


def seed():
    return dict(schema='combo-http-v1', phase='seed', uid=v.UID,
                session_token='owned-private-session-never-output', url='http://127.0.0.1:43123'+v.ROUTE)


def frame(label):
    success = label in ('success', 'repeat_success')
    result = dict(schema='combo-http-v1', phase='response', case=label, http_status=200,
        body='owned-private-response-'+label,
        checks={name: True for name in v.COMMON_CHECKS | (v.SUCCESS_CHECKS if success else v.FAILURE_CHECKS)})
    if success:
        result['expected_combo_token'] = 'owned-private-combo-'+label
    return result


def build(request_seed, label):
    return ('owned-private-request-'+label+'-'+request_seed['session_token']).encode(), dict(native=True)


class ExchangeTests(unittest.TestCase):
    def test_six_frames_preserve_raw_bytes_and_keep_secrets_out_of_proofs(self):
        requests = []
        def transact(request):
            requests.append(request)
            return frame(request['case'])
        rows, secrets = v.exchange(seed(), transact, build, lambda reply, request_seed: dict(native=True))
        self.assertEqual([row['case'] for row in rows], list(v.CASES))
        self.assertTrue(all(request['request_body'].encode() == build(seed(), request['case'])[0] for request in requests))
        self.assertTrue(all(secret not in json.dumps(rows) for secret in secrets))
        self.assertTrue(all(row['httpStatus'] == 200 for row in rows))

    def test_seed_type_identity_phase_and_ephemeral_route_gate(self):
        mutations = ({'uid': int(v.UID)}, {'phase': 'wrong'}, {'schema': 'wrong'}, {'session_token': ''},
                     {'url': 'http://127.0.0.1:8088'+v.ROUTE}, {'url': 'https://127.0.0.1:43123'+v.ROUTE},
                     {'url': 'http://example.invalid:43123'+v.ROUTE}, {'url': 'http://127.0.0.1:43123/wrong'},
                     {'url': 'http://127.0.0.1:43123'+v.ROUTE+'?extra=1'})
        for mutation in mutations:
            value = seed(); value.update(mutation); transact = Mock()
            with self.subTest(keys=list(mutation)), self.assertRaises(ValueError):
                v.exchange(value, transact, build, Mock())
            transact.assert_not_called()

    def test_wrong_frame_status_order_schema_and_check_witness_rejected(self):
        mutations = ({'http_status': 500}, {'case': 'repeat_success'}, {'phase': 'seed'}, {'schema': 'wrong'},
                     {'checks': {}}, {'checks': {name: 'true' for name in v.COMMON_CHECKS | v.SUCCESS_CHECKS}},
                     {'expected_combo_token': ''})
        for mutation in mutations:
            reply = frame('success'); reply.update(mutation)
            with self.subTest(keys=list(mutation)), self.assertRaises(ValueError):
                v.exchange(seed(), lambda request: reply, build, Mock())

    def test_failure_requires_bson_and_no_token_witness(self):
        def transact(request):
            reply = frame(request['case'])
            if request['case'] == 'wrong_token':
                reply['checks']['failureBsonUnchanged'] = False
            return reply
        with self.assertRaisesRegex(ValueError, 'domain assertions'):
            v.exchange(seed(), transact, build, Mock())
        def extra_token(request):
            reply = frame(request['case'])
            if request['case'] == 'wrong_token':
                reply['expected_combo_token'] = 'owned-forbidden-failure-token'
            return reply
        with self.assertRaisesRegex(ValueError, 'failure lacks'):
            v.exchange(seed(), extra_token, build, Mock())

    def test_native_builder_must_return_raw_bytes_without_reencoding(self):
        with self.assertRaisesRegex(ValueError, 'raw UTF-8 bytes'):
            v.exchange(seed(), Mock(), lambda request_seed, label: ('not-bytes', {}), Mock())


class PipeTests(unittest.TestCase):
    def fake_process(self, stdout=b'', stderr=b''):
        return SimpleNamespace(stdin=io.BytesIO(), stdout=io.BytesIO(stdout), stderr=io.BytesIO(stderr),
            returncode=0, wait=Mock(return_value=0), terminate=Mock(), kill=Mock())

    def test_only_memory_pipes_and_utf8_protocol(self):
        raw = json.dumps(seed(), ensure_ascii=False).encode()+b'\n'
        process = self.fake_process(raw, b'owned startup diagnostics')
        with patch.object(v.subprocess, 'Popen', return_value=process) as popen:
            child = v.PipeChild(['java-fixture', '--pipe'], Path('.'))
            received = child.receive()
            child.close()
        self.assertTrue(received == seed())
        self.assertTrue(all(popen.call_args.kwargs[name] is subprocess.PIPE for name in ('stdin', 'stdout', 'stderr')))
        self.assertTrue(bytes(child.stderr) == b'owned startup diagnostics')
        process.terminate.assert_not_called(); process.kill.assert_not_called()

    def test_malformed_stdout_is_not_exposed_in_exception(self):
        secret = 'owned-private-session-never-output'
        process = self.fake_process(secret.encode()+b'\n')
        with patch.object(v.subprocess, 'Popen', return_value=process):
            child = v.PipeChild(['java-fixture'], Path('.'))
            try:
                with self.assertRaises(ValueError) as caught:
                    child.receive()
                self.assertTrue(secret not in str(caught.exception))
            finally:
                child.close()

    def test_timeout_and_eof_use_fixed_diagnostics(self):
        child = v.PipeChild.__new__(v.PipeChild)
        child.lines = queue.Queue(); child.deadline = time.monotonic()-1
        with self.assertRaisesRegex(RuntimeError, 'timed out'):
            child.receive()
        child.lines.put(b'')
        with self.assertRaisesRegex(ValueError, 'ended'):
            child.receive()

    def test_timeout_cleanup_only_touches_spawned_owned_child(self):
        process = self.fake_process()
        process.wait.side_effect = [subprocess.TimeoutExpired('owned-fixture', 10), 0]
        with patch.object(v.subprocess, 'Popen', return_value=process):
            child = v.PipeChild(['owned-java'], Path('.')); child.close()
        process.terminate.assert_called_once(); process.kill.assert_not_called()


class SourceAndReportTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(); self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)

    def test_compiled_class_binding_absence_ambiguity_and_hash(self):
        first, second = self.root/'first', self.root/'second'
        name = 'emu/grasscutter/Owned.class'
        target = first/name; target.parent.mkdir(parents=True); target.write_bytes(b'owned-class')
        with patch.object(v, 'ROOT', self.root), patch.object(v, 'CLASS_NAMES', (name,)):
            binding = v.class_binding(str(first))
            self.assertEqual(binding[name]['sha256'], v.file_hash(target))
            with self.assertRaises(ValueError):
                v.class_binding(str(second))
            duplicate = second/name; duplicate.parent.mkdir(parents=True); duplicate.write_bytes(b'owned-class')
            with self.assertRaises(ValueError):
                v.class_binding(str(first)+os.pathsep+str(second))

    def test_frozen_source_gate_precedes_import_or_native_dependencies(self):
        with patch.object(v, 'file_hash', return_value='wrong'), patch.object(v.importlib, 'import_module') as load:
            with self.assertRaisesRegex(ValueError, 'identity changed'):
                v.load_native(self.root)
            load.assert_not_called()

    def mocked_execute(self, diagnostic=b'', returncode=0, trailing=b'', consumer=None):
        path = self.root/'cp.txt'; path.write_text('owned-classpath')
        child = Mock()
        child.receive.side_effect = [seed(), dict(schema='combo-http-v1', phase='complete', cases=6,
                                                uuidDatabaseDropped=True, httpStopped=True)]
        child.transact.side_effect = lambda request: frame(request['case'])
        child.stderr = bytearray(diagnostic); child.process.returncode = returncode
        child.lines = queue.Queue(); child.lines.put(trailing)
        args = SimpleNamespace(client=self.root, java=self.root/'java', classpath_file=path, working_directory=self.root)
        with contextlib.ExitStack() as stack:
            stack.enter_context(patch.dict(os.environ, {'LUNAGC_RUNTIME': 'portable'}))
            stack.enter_context(patch.object(v, 'load_native', return_value=(None, None, dict(fixed=True))))
            stack.enter_context(patch.object(v, 'class_binding', return_value=dict(owned='code-only')))
            stack.enter_context(patch.object(v, 'PipeChild', return_value=child))
            stack.enter_context(patch.object(v, 'native_request', side_effect=lambda sig, pe, value, label: build(value, label)))
            stack.enter_context(patch.object(v, 'native_response', side_effect=consumer or (lambda sig, pe, reply, value: dict(native=True))))
            return v.execute(args), child

    def test_public_report_contains_only_proofs_and_code_identities(self):
        report, child = self.mocked_execute()
        self.assertEqual(report['status'], 'completed')
        self.assertEqual(len(report['cases']), 6)
        self.assertFalse(report['fullComboCallbackExecuted'])
        self.assertFalse(report['naturalSdkLoginVerified'])
        self.assertFalse(report['requestResponseOrCredentialHashPersisted'])
        self.assertTrue('owned-private-' not in json.dumps(report))
        child.close.assert_called_once()

    def test_diagnostic_secret_child_failure_trailing_stdout_are_rejected(self):
        for options in (dict(diagnostic=seed()['session_token'].encode()), dict(returncode=2), dict(trailing=b'extra')):
            with self.subTest(keys=list(options)), self.assertRaises(ValueError):
                self.mocked_execute(**options)

    def test_report_secret_guard_rejects_native_proof_with_credential(self):
        with self.assertRaisesRegex(ValueError, 'Secret entered public report'):
            self.mocked_execute(consumer=lambda sig, pe, reply, value: dict(forbidden=value['session_token']))

    def test_cli_error_report_and_stdout_do_not_leak_exception_text(self):
        secret = 'owned-private-body-never-output'
        output = self.root/'safe.json'; printed = io.StringIO()
        argv = ['bridge', '--client', str(self.root), '--java', 'java', '--classpath-file', 'cp',
                '--working-directory', str(self.root), '--output', str(output)]
        with patch.object(v, 'RAW', self.root), patch.object(v, 'execute', side_effect=ValueError(secret)), \
                patch.object(v.sys, 'argv', argv), contextlib.redirect_stdout(printed):
            with self.assertRaises(SystemExit):
                v.main()
        self.assertTrue(secret not in output.read_text() and secret not in printed.getvalue())
        self.assertEqual(json.loads(output.read_bytes())['failureType'], 'ValueError')

    def test_java_scope_uses_real_default_auth_save_and_loopback_no_skips(self):
        harness = (v.ROOT/v.JAVA_SOURCES[2]).read_text(encoding='utf-8')
        tests = (v.ROOT/v.JAVA_SOURCES[0]).read_text(encoding='utf-8')
        helper = (v.ROOT/v.JAVA_SOURCES[1]).read_text(encoding='utf-8')
        self.assertIn('new DefaultAuthentication()', harness)
        self.assertIn('Morphia.createDatastore', harness)
        self.assertIn('app.start("127.0.0.1", 0)', harness)
        self.assertIn('before.equals(after)', harness)
        self.assertNotIn('Assumptions', tests+harness)
        self.assertNotIn('assertEquals', tests+harness)
        self.assertNotIn('printStackTrace', helper)
        self.assertNotIn('failure.getMessage', helper)


if __name__ == '__main__':
    unittest.main()
