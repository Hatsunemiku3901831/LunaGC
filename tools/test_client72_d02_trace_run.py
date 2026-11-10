"""Synthetic-only supervisor tests: no real process or Windows API is called."""
import json
from pathlib import Path
import struct
import subprocess
import unittest
from types import SimpleNamespace
from unittest.mock import Mock, patch

from tools import client72_d02_trace_artifacts as a
from tools import client72_d02_trace_validate as v
from tools import client72_d02_trace_run as r
from tools import test_client72_d02_trace_artifacts as fixtures


class RunnerTests(unittest.TestCase):
    def setUp(self):
        self.fixture = fixtures.TraceArtifactTests('test_bootstrap_layout_and_identity')
        self.fixture.setUp()
        self.addCleanup(self.fixture.doCleanups)
        f = self.fixture
        # Build a synthetic PE/map/bootstrap with the existing offline fixture writer.
        a.prepare(f.build, f.build.parent / 'bootstrap')
        self.boot = f.build.parent / 'bootstrap/bootstrap.bin'
        self.mirror = f.build.parent / 'bootstrap/bootstrap.private.json'
        for name in ('parent.exe', 'parent.map'):
            (f.build / name).write_bytes(('synthetic ' + name).encode())
        (f.build / 'build-epoch.h').write_bytes(b'synthetic epoch')
        self.lifecycle_contract = f.root / 'synthetic-exit-contract.json'
        self.lifecycle_contract.write_bytes(b'{"syntheticOnly":true}\n')
        def entry(path):
            path = path.resolve()
            return dict(path=str(path), bytes=path.stat().st_size, sha256=r.digest(path))
        extra = []
        for group in ('includes', 'libraries', 'compilerDlls', 'buildLogs'):
            path = f.root / (group + '.synthetic')
            path.write_bytes(group.encode())
            extra.append((group, [entry(path)]))
        review = dict(schema='c48-trace-dependency-review-v1', independentlyReviewed=True,
                      actualBuildExit=0, peAndCallerUnwindReviewed=True,
                      artifacts={k: entry(f.build / filename) for k, filename in r.ARTIFACT_FILES.items()},
                      source=[entry(f.build / name) for name in (*a.BUILD_SOURCES, 'build-epoch.h')] +
                             [entry(self.lifecycle_contract)], **dict(extra))
        self.review = f.root / 'synthetic-review.json'
        self.review.write_text(json.dumps(review), encoding='utf-8')
        self.owned = f.build.parent
        self.args = SimpleNamespace(build=f.build, bootstrap=self.boot, bootstrap_mirror=self.mirror,
                                    dependency_review=self.review, output=self.owned / 'run', case=1, run_id='a'*32)
        self.patch = patch.multiple(r, OWNED=self.owned, SOURCE=f.source)
        self.patch.start()
        self.addCleanup(self.patch.stop)
        self.validator_patch = patch.multiple(v, HEADER=a.HEADER, PINS=a.CONTRACT_PINS,
                                             EXIT_LIFECYCLE_CONTRACT_PATH=self.lifecycle_contract,
                                             EXIT_LIFECYCLE_CONTRACT_SHA256=r.digest(self.lifecycle_contract))
        self.validator_patch.start()
        self.addCleanup(self.validator_patch.stop)

    def receipt(self):
        return json.loads((self.args.output / 'receipt.json').read_text(encoding='utf-8'))

    def process(self, exitcode=0):
        p = Mock(pid=456, _handle=789)
        p.wait.return_value = exitcode
        return p

    def execute(self, proc):
        with patch.object(r.subprocess, 'Popen', return_value=proc) as popen, patch.object(r, 'creation_time', return_value='0000000000001234') as times:
            code = r.run(self.args)
        times.assert_called_once_with(789)
        return code, popen

    def test_gate_failures_and_existing_output_never_spawn(self):
        self.review.write_text('{}', encoding='utf-8')
        with patch.object(r.subprocess, 'Popen') as popen:
            self.assertEqual(r.run(self.args), 1)
            popen.assert_not_called()
        result = self.receipt()
        self.assertFalse(result['childCreated'])
        self.assertFalse(result['parentCreated'])
        saved = (self.args.output / 'receipt.json').read_bytes()
        with patch.object(r.subprocess, 'Popen') as popen:
            with self.assertRaises(FileExistsError):
                r.run(self.args)
            popen.assert_not_called()
        self.assertEqual(saved, (self.args.output / 'receipt.json').read_bytes())

    def test_real_shape_argv_and_hash_coverage_on_mock(self):
        proc = self.process()
        code, popen = self.execute(proc)
        self.assertEqual(code, 0)
        receipt = self.receipt()
        artifacts = receipt['artifacts']
        self.assertEqual(set(artifacts), set(r.ARTIFACT_FILES) | {'header','bootstrap','bootstrapMirror','dependencyReview','interface','cases','eventSchema','frozenInputs'})
        expected = [artifacts['parentExe'], '--child-exe', artifacts['childExe'], '--bootstrap', artifacts['bootstrap'], '--case', '1', '--run-id', 'a'*32, '--output', str((self.args.output / 'native').resolve())]
        self.assertEqual(receipt['argv'], expected)
        self.assertEqual(popen.call_args.args[0], expected)
        self.assertFalse((self.args.output / 'native').exists())
        self.assertEqual(receipt['before'], receipt['after'])
        review = json.loads(self.review.read_text(encoding='utf-8'))
        for path in list(artifacts.values()) + [e['path'] for group in r.GROUPS for e in review[group]]:
            self.assertEqual(receipt['before'][path], r.digest(Path(path)))
        self.assertEqual(receipt['parentExit'], 0)
        self.assertEqual(receipt['parentCreationFileTime'], '0000000000001234')
        self.assertIsNone(receipt['childCreated'])

    def test_missing_or_changed_lifecycle_contract_never_spawns(self):
        original_review = self.review.read_bytes()
        original_contract = self.lifecycle_contract.read_bytes()
        for mode in ('missing', 'changed'):
            with self.subTest(mode=mode):
                review = json.loads(original_review)
                if mode == 'missing':
                    review['source'] = [e for e in review['source'] if e['path'] != str(self.lifecycle_contract.resolve())]
                else:
                    self.lifecycle_contract.write_bytes(b'{"syntheticOnly":true,"changed":true}\n')
                    entry = next(e for e in review['source'] if e['path'] == str(self.lifecycle_contract.resolve()))
                    entry.update(bytes=self.lifecycle_contract.stat().st_size, sha256=r.digest(self.lifecycle_contract))
                self.review.write_text(json.dumps(review), encoding='utf-8')
                self.args.output = self.owned / ('bad-lifecycle-' + mode)
                with patch.object(r.subprocess, 'Popen') as popen:
                    self.assertEqual(r.run(self.args), 1)
                    popen.assert_not_called()
                self.assertFalse(self.receipt()['parentCreated'])
                self.lifecycle_contract.write_bytes(original_contract)
        self.review.write_bytes(original_review)

    def test_drift_and_actual_exit_are_failures(self):
        proc = self.process(exitcode=17)
        def wait(timeout):
            (self.fixture.source / 'parent.c').write_bytes(b'drift')
            return 17
        proc.wait.side_effect = wait
        code, _ = self.execute(proc)
        self.assertEqual(code, 1)
        receipt = self.receipt()
        self.assertEqual(receipt['parentExit'], 17)
        self.assertNotEqual(receipt['before'], receipt['after'])
        self.assertIn('drift', receipt['error'])

    def test_creation_failure_kills_owned_parent_and_records_real_exit(self):
        proc = self.process(exitcode=99)
        with patch.object(r.subprocess, 'Popen', return_value=proc), patch.object(r, 'creation_time', side_effect=OSError('missing')):
            self.assertEqual(r.run(self.args), 1)
        proc.kill.assert_called_once_with()
        receipt = self.receipt()
        self.assertIsNone(receipt['parentCreationFileTime'])
        self.assertEqual(receipt['parentExit'], 99)

    def test_timeout_kills_only_popen_and_records_actual_exit(self):
        proc = self.process()
        proc.wait.side_effect = [subprocess.TimeoutExpired('parent', 12), 88]
        code, _ = self.execute(proc)
        self.assertEqual(code, 1)
        proc.kill.assert_called_once_with()
        self.assertEqual([c.kwargs['timeout'] for c in proc.wait.call_args_list], [12, 5])
        receipt = self.receipt()
        self.assertTrue(receipt['timedOut'])
        self.assertFalse(receipt['cleanupConfirmed'])
        self.assertEqual(receipt['parentExit'], 88)
        self.assertEqual(receipt['before'], receipt['after'])

    def test_reject_source_review_and_bootstrap_mismatch(self):
        for mutation in ('source', 'review', 'bootstrap'):
            with self.subTest(mutation=mutation):
                if mutation == 'source':
                    path = self.fixture.source / 'parent.c'
                    saved = path.read_bytes()
                    path.write_bytes(b'drift')
                elif mutation == 'review':
                    path = self.review
                    saved = path.read_bytes()
                    review = json.loads(saved)
                    review['source'] = []
                    path.write_text(json.dumps(review), encoding='utf-8')
                else:
                    path = self.boot
                    saved = path.read_bytes()
                    path.write_bytes(saved[:-1] + bytes([saved[-1] ^ 1]))
                self.args.output = self.owned / ('bad-' + mutation)
                with patch.object(r.subprocess, 'Popen') as popen:
                    self.assertEqual(r.run(self.args), 1)
                    popen.assert_not_called()
                self.assertFalse(self.receipt()['childCreated'])
                path.write_bytes(saved)

    def test_false_review_and_each_empty_group_rejected(self):
        original = self.review.read_bytes()
        for name in ('independentlyReviewed', *r.GROUPS):
            review = json.loads(original)
            review[name] = False if name == 'independentlyReviewed' else []
            self.review.write_text(json.dumps(review), encoding='utf-8')
            self.args.output = self.owned / ('reject-' + name)
            with patch.object(r.subprocess, 'Popen') as popen:
                self.assertEqual(r.run(self.args), 1)
                popen.assert_not_called()
            self.assertFalse(self.receipt()['childCreated'])
        self.review.write_bytes(original)

    def test_unexpected_exit_without_drift(self):
        code, _ = self.execute(self.process(exitcode=19))
        self.assertEqual(code, 1)
        receipt = self.receipt()
        self.assertEqual(receipt['parentExit'], 19)
        self.assertEqual(receipt['before'], receipt['after'])

    def test_expected_negative_exit_preserved(self):
        self.args.case = 3
        code, _ = self.execute(self.process(exitcode=30))
        self.assertEqual(code, 0)
        self.assertEqual(self.receipt()['parentExit'], 30)


if __name__ == '__main__':
    unittest.main()
