"""Owned lifecycle fixtures for the source-bound runtime-input adapter; never opens a process."""
import copy
import ctypes as C
import json
from pathlib import Path
import struct
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import Mock, patch

from tools import sample_client72_avatar_runtime_inputs as sampler


class LifecycleTests(unittest.TestCase):
    def setUp(self):
        temp = tempfile.TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        self.root = Path(temp.name)
        game = self.root / 'game'
        game.mkdir()
        target = game / 'YuanShen.exe'
        target.write_bytes(b'owned image')
        (game / 'metadata.dat').write_bytes(b'owned metadata')
        profile = self.root / 'protocol/cn-7.2/profile.json'
        profile.parent.mkdir(parents=True)
        profile.write_text(json.dumps({'files': {'metadata': {'path': 'metadata.dat'}}}))
        self.base = 0x18000000000
        self.module = dict(name='YuanShen.exe', path=str(target), base=self.base, size=0x20000000)
        self.modules = dict(error=None, items=[self.module])
        self.inputs = dict(profileSHA256='owned', files={'binary': 'owned'},
            config={name: dict(exists=False) for name in ('.env', '.env.local', 'config.json')})
        self.source = dict(status='completed', finalStatus=dict(alive=True), elapsedSeconds=90.1,
            sourceSHA256=sampler.environment.MONITOR_SHA, pid=3210,
            processCreationTime=dict(filetime=0x123456789abc, winerror=None),
            launch=dict(cwd=str(game), executable=str(target)), image=str(target),
            inputs=copy.deepcopy(self.inputs), samples=[dict(modules=self.modules)])
        self.observation = self.root / 'owned-observation.json'
        self.backend = Mock()
        self.backend.k.OpenProcess.return_value = 77
        self.backend.creation_time.return_value = self.source['processCreationTime']
        self.backend.image.return_value = str(target)
        self.backend.status.return_value = dict(alive=True)
        self.backend.snapshot.return_value = copy.deepcopy(self.modules)
        def rpm(handle, address, buffer, width, count):
            if address in (self.base + sampler.GATES['registered'], self.base + sampler.GATES['once']):
                self.assertEqual(width, 1)
                raw = b'\x01' if address == self.base + sampler.GATES['registered'] else b'\x00'
            else:
                self.assertEqual(address, self.base + 0x5bc7290)
                self.assertEqual(width, 8)
                raw = struct.pack('<Q', 0x1234567800)
            C.memmove(buffer, raw, width)
            C.cast(count, C.POINTER(C.c_size_t))[0] = width
            return True
        self.backend.k.ReadProcessMemory.side_effect = rpm
        pe = bytearray(0x100)
        struct.pack_into('<I', pe, 0x3c, 0x40)
        struct.pack_into('<I', pe, 0x40 + 24 + 56, self.module['size'])
        def collect(read, base):
            raw, error = read(base + 0x5bc7290, 8)
            self.assertIsNone(error)
            return dict(status='completed', records=[dict(bytesHex=raw.hex())],
                budget=dict(reads=1, requestedBytes=8), naturalLookupObserved=False,
                completeAvatarHandler=False, d02Complete=False)
        for context in (
            patch.object(sampler, 'ROOT', self.root), patch.object(sampler, 'SOURCE_PINS', {}),
            patch.object(sampler.environment, 'verified_inputs', return_value=self.inputs),
            patch.object(sampler.probe, 'verify_sources', return_value=({}, [], {})),
            patch.object(sampler.environment, 'PE', return_value=SimpleNamespace(data=pe)),
            patch.object(sampler.environment, 'WindowsBackend', return_value=self.backend),
            patch.object(sampler.runtime, 'verify_sources', return_value={'owned': True}),
            patch.object(sampler, 'verify_gate_sources', return_value={'owned': True}),
            patch.object(sampler.runtime, 'collect', side_effect=collect)):
            context.start()
            self.addCleanup(context.stop)

    def run_owned(self):
        self.observation.write_text(json.dumps(self.source))
        return sampler.execute(self.observation)

    def test_exact_rights_creation_module_and_read_lifetime(self):
        result = self.run_owned()
        self.backend.k.OpenProcess.assert_called_once_with(0x101010, False, 3210)
        self.assertEqual(self.backend.k.ReadProcessMemory.call_count, 5)
        self.backend.close.assert_called_once_with(77)
        self.assertEqual(result['status'], 'completed')
        self.assertTrue(result['endIdentityVerified'])
        self.assertEqual(result['records'][0]['bytesHex'], '0078563412000000')
        self.assertFalse(result['naturalLookupObserved'])
        self.assertEqual(result['combinedBudget']['reads'], 5)
        self.assertEqual(result['combinedBudget']['requestedBytes'], 12)
        self.assertEqual([x['classification'] for x in result['configurationGates']['records']], ['true','false','true','false'])
        self.assertFalse(result['configurationGates']['pastConfigSyncProved'])
        self.assertFalse(result['configurationGates']['atomicSnapshot'])

    def test_source_short_monitor_or_changed_config_refuses_open(self):
        for mutation in ('source', 'short', 'config'):
            with self.subTest(mutation=mutation):
                original = copy.deepcopy(self.source)
                if mutation == 'source': self.source['sourceSHA256'] = 'different'
                elif mutation == 'short': self.source['elapsedSeconds'] = 89.99
                else: self.source['inputs']['config']['.env'] = dict(exists=True, sha256='different')
                with self.assertRaises(ValueError): self.run_owned()
                self.source = original
        self.backend.k.OpenProcess.assert_not_called()

    def test_reused_pid_refuses_first_read_and_closes_handle(self):
        self.backend.creation_time.return_value = dict(filetime=1, winerror=None)
        with self.assertRaisesRegex(ValueError, 'creation'): self.run_owned()
        self.backend.k.ReadProcessMemory.assert_not_called()
        self.backend.close.assert_called_once_with(77)

    def test_module_rebase_refuses_first_read(self):
        current = copy.deepcopy(self.modules)
        current['items'][0]['base'] += 0x10000
        self.backend.snapshot.return_value = current
        with self.assertRaisesRegex(ValueError, 'module binding'): self.run_owned()
        self.backend.k.ReadProcessMemory.assert_not_called()
        self.backend.close.assert_called_once_with(77)

    def test_exit_after_capture_retains_raw_as_partial(self):
        self.backend.status.side_effect = [dict(alive=True), dict(alive=False)]
        result = self.run_owned()
        self.assertEqual(result['status'], 'partial')
        self.assertEqual(result['failure'], 'end-process-identity-or-lifetime')
        self.assertEqual(len(result['records']), 1)
        self.assertFalse(result['endIdentityVerified'])
        self.backend.close.assert_called_once_with(77)

    def test_sampler_source_drift_refuses_process(self):
        (self.root / 'owned.py').write_bytes(b'changed')
        with patch.object(sampler, 'SOURCE_PINS', {'owned.py': sampler.digest(b'original')}):
            with self.assertRaisesRegex(ValueError, 'source differs'): self.run_owned()
        self.backend.k.OpenProcess.assert_not_called()


class ScalarTests(unittest.TestCase):
    def test_exact_four_scalar_reads_and_non_bool_raw_are_preserved(self):
        read = Mock(side_effect=[(b'\x02',None),(b'\x00',None),(b'\x01',None),(b'\x00',None)])
        first = sampler.read_gates(read, 0x18000000000, 'before')
        second = sampler.read_gates(read, 0x18000000000, 'after')
        self.assertEqual(read.call_args_list, [unittest.mock.call(0x1800586dbb0,1), unittest.mock.call(0x1800586ba00,1)] * 2)
        self.assertEqual([r['classification'] for r in first + second], ['non-bool','false','true','false'])
        self.assertEqual(first[0]['bytesHex'], '02')
        self.assertTrue(all(r['requested']==1 and r['bytesRead']==1 for r in first + second))

    def test_short_read_and_os_error_are_recorded_without_pointer_reads(self):
        read = Mock(side_effect=[(b'',None), OSError(5, 'owned unreadable')])
        rows = sampler.read_gates(read, 0x18000000000, 'owned')
        self.assertEqual(read.call_count, 2)
        self.assertEqual([r['classification'] for r in rows], ['unreadable','unreadable'])
        self.assertEqual(rows[0]['error'], 'partial-read')
        self.assertEqual(rows[1]['error'], {'winerror':5})
        self.assertEqual([r['bytesHex'] for r in rows], ['', ''])

    def test_gate_source_drift_rejects_before_backend(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp); target=root/sampler.GATE_PROOF; target.parent.mkdir(parents=True); target.write_bytes(b'changed')
            with patch.object(sampler, 'ROOT', root):
                with self.assertRaisesRegex(ValueError, 'scalar source proof differs'):
                    sampler.verify_gate_sources(b'owned')


if __name__ == '__main__':
    unittest.main()
