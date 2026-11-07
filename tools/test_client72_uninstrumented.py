"""No game or external process is started by these tests."""
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
from tools.observe_client72_uninstrumented import observe, interpretation, WindowsBackend


class Backend:
    def __init__(self, statuses, inventory=None, probe=None):
        self.statuses = iter(statuses); self.inventory = inventory or []; self.probe = probe or {'value': False}
        self.launched = []; self.closed = []; self.queries = []
    def processes(self, game): return {'error': None, 'items': self.inventory}
    def self_probe(self): return self.probe
    def launch(self, target, cwd):
        self.launched.append((target, cwd)); return 'handle', 123
    def image(self, handle): return str(self.launched[0][0])
    def creation_time(self, handle): return {'filetime': 987, 'winerror': None}
    def status(self, handle): return next(self.statuses)
    def sample(self, pid, allow):
        self.queries.append((pid, allow)); return {'threads': {'items': []}, 'modules': {'items': []}}
    def close(self, handle): self.closed.append(handle)


class Tests(unittest.TestCase):
    def test_sample_filters_snapshot_by_target_pid_before_opening_threads(self):
        class Kernel:
            def __init__(self): self.opened = []
            def OpenThread(self, access, inherit, tid):
                self.opened.append((access, inherit, tid)); return None
        backend = WindowsBackend.__new__(WindowsBackend)
        backend.k = Kernel(); snapshots = []
        def snapshot(flags, pid, struct, first, next_):
            snapshots.append((flags, pid))
            if flags == 4:
                rows = [{'tid': 111, 'ownerPid': 0}, {'tid': 222, 'ownerPid': 123}]
                return {'error': None, 'items': [row for row in rows if row['ownerPid'] == pid]}
            return {'error': None, 'items': []}
        backend.snapshot = snapshot
        result = backend.sample(123, True)
        self.assertIn((4, 123), snapshots)
        self.assertEqual(backend.k.opened, [(0x40, False, 222)])
        self.assertEqual([row['ownerPid'] for row in result['threads']['items']], [123])
        self.assertIsNone(result['threads']['items'][0]['hide']['value'])

    def test_sample_checks_opened_thread_owner_before_query(self):
        class Kernel:
            def OpenThread(self, access, inherit, tid): return tid
            def GetProcessIdOfThread(self, handle): return {222: 123, 333: 456}[handle]
            def CloseHandle(self, handle): pass
        backend = WindowsBackend.__new__(WindowsBackend); backend.k = Kernel()
        backend.snapshot = lambda flags, pid, *args: {'error': None, 'items':
            [{'tid': 222, 'ownerPid': 123}, {'tid': 333, 'ownerPid': 123}] if flags == 4 else []}
        queried = []
        backend.query = lambda handle: queried.append(handle) or {'value': False}
        result = backend.sample(123, True)
        self.assertEqual(queried, [222])
        self.assertEqual(result['threads']['items'][1]['hide']['error'], 'thread-owner-mismatch')
        self.assertIsNone(result['threads']['items'][1]['hide']['value'])

    def run_observe(self, backend, ticks):
        iterator = iter(ticks)
        with tempfile.TemporaryDirectory() as folder:
            game = Path(folder)
            result = observe(backend, game, {}, seconds=1, interval=.5, sleep=lambda _: None,
                             monotonic=lambda: next(iterator))
            return result, game

    def test_normal_original_launch_and_exit(self):
        b = Backend([{'alive': True}, {'alive': False, 'signaled': True, 'exitCode': 0x80000004}])
        result, game = self.run_observe(b, [0, 0, 0, .5])
        self.assertEqual(b.launched, [(game / 'YuanShen.exe', game)])
        self.assertEqual(result['launch']['arguments'], [])
        self.assertEqual(result['launch']['creationFlags'], 0)
        self.assertEqual(result['processCreationTime']['filetime'], 987)
        self.assertFalse(result['launch']['debugFlags'])
        self.assertEqual(result['finalStatus']['exitCode'], 0x80000004)
        self.assertEqual(b.closed, ['handle'])
        self.assertEqual(b.queries, [(123, True)])

    def test_surviving_process_is_not_terminated(self):
        b = Backend([{'alive': True}, {'alive': True, 'signaled': False, 'exitCode': 259}])
        result, _ = self.run_observe(b, [0, 0, 0, 1, 1])
        self.assertTrue(result['finalStatus']['alive'])
        self.assertEqual(b.closed, ['handle'])

    def test_existing_game_rejected_before_launch(self):
        b = Backend([], [{'pid': 44, 'name': 'YUANSHEN.EXE'}])
        with self.assertRaisesRegex(RuntimeError, 'Existing game'):
            self.run_observe(b, [0])
        self.assertEqual(b.launched, [])

    def test_unsupported_probe_does_not_query_threads(self):
        b = Backend([{'alive': True}, {'alive': False}], probe={'value': None})
        self.run_observe(b, [0, 0, 0, .5])
        self.assertEqual(b.queries, [(123, False)])

    def test_failed_or_non_boolean_query_is_unknown(self):
        self.assertIsNone(interpretation(0xc0000001, 1, 0))
        self.assertIsNone(interpretation(0, 0, 0))
        self.assertIsNone(interpretation(0, 1, 2))
        self.assertFalse(interpretation(0, 1, 0))
        self.assertTrue(interpretation(0, 1, 1))

    def test_wrong_image_does_not_continue_and_closes_handle(self):
        b = Backend([]); b.image = lambda _: 'C:/other/YuanShen.exe'
        with self.assertRaisesRegex(RuntimeError, 'image mismatch'):
            self.run_observe(b, [0])
        self.assertEqual(b.closed, ['handle'])

    def test_inaccessible_inventory_refuses_to_launch(self):
        b = Backend([]); b.processes = lambda _: {'error': 5, 'items': []}
        with self.assertRaisesRegex(RuntimeError, 'inventory'):
            self.run_observe(b, [])
        self.assertEqual(b.launched, [])

    def test_launch_error_is_propagated_without_retry(self):
        b = Backend([])
        def denied(target, cwd):
            b.launched.append((target, cwd))
            raise OSError(740, 'elevation required')
        b.launch = denied
        with self.assertRaises(OSError):
            self.run_observe(b, [0])
        self.assertEqual(len(b.launched), 1)

    def test_creation_time_failure_is_recorded(self):
        b = Backend([{'alive': False}]); b.creation_time = lambda _: {'filetime': None, 'winerror': 5}
        result, _ = self.run_observe(b, [0, 0])
        self.assertEqual(result['processCreationTime'], {'filetime': None, 'winerror': 5})


if __name__ == '__main__': unittest.main()
