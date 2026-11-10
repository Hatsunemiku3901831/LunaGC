"""Synthetic process doubles only. No EXE or Win32 API is executed."""
import argparse
import json
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch
from tools import client72_d02_clear48_full_run as runner


class ProcessDouble:
    pid = 12345
    _handle = 77

    def __init__(self, exits=(0,)):
        self.exits = iter(exits)
        self.kills = 0
        self.exit = None

    def wait(self, timeout):
        value = next(self.exits)
        if isinstance(value, Exception):
            raise value
        self.exit = value
        return value

    def poll(self):
        return self.exit

    def kill(self):
        self.kills += 1


class RootRunnerTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(dir=runner.OWNED)
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name).resolve()
        self.frozen = self.root/'synthetic-input'
        self.frozen.write_bytes(b'nonzero fixture')
        self.artifacts = {k: str(self.root/k) for k in (
            'parentExe','childExe','header','bootstrap','bootstrapMirror','helper','dependencyReview','contractDirectory')}
        self.args = argparse.Namespace(case=257, run_id='123456789abcdef0'*2, build=self.root/'build',
            bootstrap=self.root/'boot', contract_dir=runner.CONTRACT, helper=self.root/'helper',
            dependency_review=self.root/'review', output=self.root/'new-run')

    def invoke(self, process=None, preflight_error=None, snapshots=None, creation_error=None):
        process = process or ProcessDouble()
        with patch.object(runner, 'OWNED', self.root), \
             patch.object(runner, 'preflight', side_effect=preflight_error,
                          return_value=(self.artifacts,[self.frozen])), \
             patch.object(runner, 'creation_time', side_effect=creation_error, return_value='0123456789abcdef') as times, \
             patch.object(runner.subprocess, 'Popen', return_value=process) as spawn:
            if snapshots is None:
                code = runner.run(self.args)
            else:
                with patch.object(runner,'snapshot',side_effect=snapshots):
                    code = runner.run(self.args)
        return code, json.loads((self.args.output/'receipt.json').read_bytes()), spawn, times, process

    def test_success_exact_argv_and_owned_identity(self):
        code, receipt, spawn, times, process = self.invoke()
        self.assertEqual(code,0)
        expected = [self.artifacts['parentExe'],'--child-exe',self.artifacts['childExe'],'--bootstrap',
                    self.artifacts['bootstrap'],'--helper',self.artifacts['helper'],'--case','257',
                    '--run-id',self.args.run_id,'--output',str(self.args.output/'native')]
        self.assertEqual(spawn.call_args.args[0],expected)
        self.assertEqual(receipt['argv'],expected)
        self.assertEqual(receipt['parentPid'],12345)
        self.assertEqual(receipt['parentCreationFileTime'],'0123456789abcdef')
        self.assertIsNone(receipt['childCreated'])
        self.assertEqual(receipt['before'],receipt['after'])
        self.assertTrue(receipt['cleanupConfirmed'])
        self.assertEqual(process.kills,0)
        times.assert_called_once_with(77)
        self.assertEqual(spawn.call_args.kwargs['creationflags'],subprocess.CREATE_NO_WINDOW)

    def test_preflight_rejection_never_spawns(self):
        code, receipt, spawn, _, _ = self.invoke(preflight_error=ValueError('review rejected'))
        self.assertEqual(code,1); spawn.assert_not_called()
        self.assertFalse(receipt['parentCreated']); self.assertFalse(receipt['cleanupConfirmed'])
        self.assertEqual(receipt['error'],'review rejected')

    def test_input_drift_cannot_pass(self):
        code, receipt, _, _, _ = self.invoke(snapshots=[{'same':'1'*64},{'same':'2'*64}])
        self.assertEqual(code,1)
        self.assertFalse(receipt['cleanupConfirmed'])
        self.assertEqual(receipt['error'],'protected input drift')

    def test_nonzero_exit_is_preserved(self):
        code, receipt, _, _, process = self.invoke(ProcessDouble((90,)))
        self.assertEqual((code,receipt['parentExit'],process.kills),(1,90,0))
        self.assertFalse(receipt['cleanupConfirmed'])

    def test_timeout_terminates_only_created_process_double(self):
        p = ProcessDouble((subprocess.TimeoutExpired('synthetic-parent',12),90))
        code, receipt, _, _, p = self.invoke(p)
        self.assertEqual((code,p.kills,receipt['parentExit']),(1,1,90))
        self.assertTrue(receipt['timedOut']); self.assertFalse(receipt['cleanupConfirmed'])

    def test_creation_time_failure_stays_failed_after_exit(self):
        code, receipt, _, _, p = self.invoke(creation_error=OSError('metadata unavailable'))
        self.assertEqual((code,p.kills),(1,1))
        self.assertIn('metadata unavailable',receipt['error'])
        self.assertFalse(receipt['cleanupConfirmed'])

    def test_existing_output_preserved(self):
        self.args.output.mkdir(); keep=self.args.output/'keep'; keep.write_bytes(b'original')
        with patch.object(runner,'OWNED',self.root), patch.object(runner.subprocess,'Popen') as spawn:
            with self.assertRaisesRegex(ValueError,'new owned'):
                runner.run(self.args)
        spawn.assert_not_called(); self.assertEqual(keep.read_bytes(),b'original')

    def test_unknown_or_bool_case_rejected_before_file_reads(self):
        for value in (1,2,3,True,256,259):
            self.args.case=value
            with self.subTest(case=value), patch.object(Path,'read_bytes') as read:
                with self.assertRaisesRegex(ValueError,'fixed full-helper case'):
                    runner.preflight(self.args)
                read.assert_not_called()

    def test_bad_run_id_rejected_before_file_reads(self):
        for value in ('','a'*31,'A'*32,'g'*32):
            self.args.run_id=value
            with self.subTest(run_id=value), patch.object(Path,'read_bytes') as read:
                with self.assertRaisesRegex(ValueError,'run id'):
                    runner.preflight(self.args)
                read.assert_not_called()


if __name__ == '__main__':
    unittest.main()
