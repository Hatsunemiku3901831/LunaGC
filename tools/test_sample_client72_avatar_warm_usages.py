"""Owned lifecycle checks; no process, client, database or port API is used."""
import contextlib
import copy
import io
from pathlib import Path
import unittest
from unittest.mock import patch

from tools import sample_client72_avatar_warm_usages as card


class FakeBackend:
    def __init__(self, modules):
        self.modules = [copy.deepcopy(modules), copy.deepcopy(modules)]
        self.creations = [dict(filetime=1234567890, winerror=None)]*2
        self.statuses = [dict(alive=True)]*3
        self.closed = []
        self.reads = []
        self.opens = []
        self.image_path = modules['items'][0]['path']
        self.responses = [(b'abcdefgh', None), (b'1234', None)]
        self.available = True

    def open(self, pid):
        self.opens.append(pid)
        return 919 if self.available else 0

    def close(self, handle):
        self.closed.append(handle)

    def status(self, handle):
        return self.statuses.pop(0)

    def creation_time(self, handle):
        return self.creations.pop(0)

    def image(self, handle):
        return self.image_path

    def snapshot(self, *args):
        return self.modules.pop(0)

    def read(self, handle, address, width):
        self.reads.append((handle, address, width))
        value = self.responses.pop(0)
        if isinstance(value, Exception):
            raise value
        return value


class WarmSamplerTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.client = card.warm.RAW/'adapter-owned/root-fixture-v1'
        plugins = cls.client/'YuanShen_Data/Plugins'
        plugins.mkdir(parents=True, exist_ok=True)
        cls.identities = {}
        cls.mods = dict(error=None, items=[])
        for i, name in enumerate(card.early.MODULES):
            path = cls.client/name if i == 0 else plugins/name
            raw = ('owned metadata only '+name).encode()
            if path.exists():
                if path.read_bytes() != raw:
                    raise ValueError('Preserve a conflicting owned fixture')
            else:
                path.write_bytes(raw)
            cls.identities[name] = dict(path=str(path), bytes=len(raw),
                sha256=card.digest(raw), imageSize=4096)
            cls.mods['items'].append(dict(name=name, path=str(path), base=0x190000000+i*0x10000, size=4096))

    def setUp(self):
        self.evidence = dict(pid=37, processCreationTime=dict(filetime=1234567890, winerror=None),
            finalStatus=dict(alive=True), samples=[dict(modules=copy.deepcopy(self.mods))])
        self.backend = FakeBackend(self.mods)

    @staticmethod
    def two_reads(read, base):
        a = read(base+8, 8)
        b = read(base+32, 4)
        return dict(status='completed' if a == (b'abcdefgh', None) and b == (b'1234', None) else 'partial',
            candidateGraphOnly=True, candidateInputs=dict(mainWarm=a[1] is None and b[1] is None))

    def run_sample(self, collector=None):
        return card.sample(self.backend, self.client, self.evidence, self.identities,
            collector=collector or self.two_reads)

    def test_normal_high64_module_same_reads_and_final_close(self):
        report = self.run_sample()
        self.assertEqual(report['status'], 'completed')
        self.assertTrue(report['endIdentityVerified'])
        self.assertEqual(self.backend.opens, [37])
        self.assertEqual(self.backend.closed, [919])
        self.assertEqual(self.backend.reads, [(919,0x190000008,8), (919,0x190000020,4)])
        self.assertEqual([r['bytesHex'] for r in report['backendRecords']], ['6162636465666768','31323334'])
        self.assertEqual([r['ordinal'] for r in report['backendRecords']], [1,2])
        self.assertEqual((report['backendBudget']['reads'], report['backendBudget']['requestedBytes']), (2,12))
        self.assertFalse(report['naturalCallerObserved'])
        self.assertFalse(report['completeAvatarHandler'])
        self.assertFalse(report['d02Complete'])

    def test_target_already_exited_never_opens(self):
        self.evidence['finalStatus']['alive'] = False
        report = self.run_sample()
        self.assertEqual(report['reason'], 'target-exited-before-open')
        self.assertEqual(self.backend.opens, [])
        self.assertEqual(self.backend.reads, [])

    def test_open_failure_does_not_close_non_handle(self):
        self.backend.available = False
        report = self.run_sample()
        self.assertEqual(report['reason'], 'process-unavailable')
        self.assertEqual(self.backend.closed, [])

    def test_start_module_base_drift_zero_reads(self):
        self.backend.modules[0]['items'][0]['base'] += 0x1000
        report = self.run_sample()
        self.assertEqual(report['status'], 'partial')
        self.assertEqual(report['reason'], 'end-module-binding-differs')
        self.assertEqual(self.backend.reads, [])
        self.assertEqual(self.backend.closed, [919])

    def test_start_creation_drift_zero_reads(self):
        self.backend.creations[0] = dict(filetime=1234567891, winerror=None)
        report = self.run_sample()
        self.assertEqual(report['status'], 'partial')
        self.assertEqual(report['reason'], 'start-process-creation-differs')
        self.assertEqual(self.backend.reads, [])
        self.assertEqual(self.backend.closed, [919])

    def test_end_creation_drift_retains_all_raw(self):
        self.backend.creations[1] = dict(filetime=1234567891, winerror=None)
        report = self.run_sample()
        self.assertEqual(report['reason'], 'end-process-creation-differs')
        self.assertEqual(report['status'], 'partial')
        self.assertEqual(len(report['backendRecords']), 2)
        self.assertFalse(report['endIdentityVerified'])
        self.assertEqual(self.backend.closed, [919])

    def test_exit_after_file_hash_work_rejects_success(self):
        self.backend.statuses[-1] = dict(alive=False)
        report = self.run_sample()
        self.assertEqual(report['reason'], 'end-target-exited')
        self.assertEqual(report['status'], 'partial')
        self.assertEqual(len(report['backendRecords']), 2)

    def test_short_read_recorded_once_without_fallback(self):
        self.backend.responses[0] = (b'abc', dict(kind='partial-read'))
        report = self.run_sample()
        self.assertEqual(report['status'], 'partial')
        self.assertEqual(report['backendRecords'][0]['bytesRead'], 3)
        self.assertEqual(report['backendRecords'][0]['bytesHex'], '616263')
        self.assertEqual(len(self.backend.reads), 2)

    def test_read_os_error_preserves_numeric_failure_and_closes(self):
        self.backend.responses[0] = OSError(5, 'Do not log arbitrary exception text')
        report = self.run_sample()
        self.assertEqual(report['status'], 'partial')
        self.assertEqual(report['backendRecords'][0]['error'], dict(kind='read-os-error',winerror=5))
        self.assertEqual(report['backendRecords'][0]['bytesRead'], 0)
        self.assertNotIn('arbitrary', str(report))
        self.assertEqual(self.backend.closed, [919])

    def test_unexpected_collector_failure_keeps_prior_same_read(self):
        def fail(read, base):
            read(base+8, 8)
            raise ValueError('This body must not be persisted')
        report = self.run_sample(fail)
        self.assertEqual(report['reason'], 'fixed-collection-failure')
        self.assertEqual(len(report['backendRecords']), 1)
        self.assertNotIn('persisted', str(report))
        self.assertEqual(self.backend.closed, [919])

    def test_single_read_bound_blocks_before_backend(self):
        def oversize(read, base):
            read(base, 209)
        report = self.run_sample(oversize)
        self.assertEqual(report['status'], 'partial')
        self.assertEqual(self.backend.reads, [])
        self.assertEqual(report['backendBudget']['requestedBytes'], 0)

    def test_source_gate_blocks_unfrozen_epoch_before_preflight(self):
        with patch.object(card, 'SOURCE_PINS', {}), patch.object(card.early, 'preflight') as gate:
            with self.assertRaises(ValueError):
                card.source_gate(self.client, Path('unused'), Path('unused'))
            gate.assert_not_called()

    def test_no_ready_cli_rejects_before_source_gate_or_backend(self):
        with patch.object(card, 'source_gate') as gate, patch.object(card.early,'ReadOnlyBackend') as backend:
            with contextlib.redirect_stderr(io.StringIO()), self.assertRaises(SystemExit):
                card.main(['--client', str(self.client),'--production','unused','--observation','unused',
                    '--output',str(card.RAW/'unwritten.private.json')])
            gate.assert_not_called()
            backend.assert_not_called()


if __name__ == '__main__':
    unittest.main()
