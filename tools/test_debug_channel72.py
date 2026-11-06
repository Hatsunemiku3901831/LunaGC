import copy
import hashlib
import json
from pathlib import Path
import unittest

from tools.check_debug_channel72 import relevant_processes, validate_summary


class DebuggerChannelTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.report = json.loads((Path(__file__).resolve().parents[1] /
                                 'protocol/cn-7.2/debug-channel-observations.json').read_text('utf8'))

    def test_five_live_synthetic_cases_agree(self):
        self.assertEqual(validate_summary(self.report), [])
        source = Path(__file__).with_name('DebuggerChannelFixture.cpp')
        self.assertEqual(hashlib.sha256(source.read_bytes()).hexdigest(), self.report['fixtureSourceSHA256'])

    def test_os_exit_cannot_be_inferred_from_pub_or_helper(self):
        for key, value in [('osExitCode', 0x80000004), ('osSignaled', False),
                           ('osHandleAvailable', False), ('freshCreateSystemStop', False),
                           ('debuggerStopped', False)]:
            report = copy.deepcopy(self.report)
            report['results'][0][key] = value
            self.assertTrue(validate_summary(report), key)

    def test_native_handlers_must_really_catch_both_exceptions(self):
        for key, value in [('avCaught', 0), ('cppCaught', 0), ('finalPhase', 20),
                           ('markerCalls', 1), ('allExceptionsMatchedAndFirstChance', False)]:
            report = copy.deepcopy(self.report)
            report['results'][0][key] = value
            self.assertTrue(validate_summary(report), key)

    def test_diagnostic_traps_must_match_the_owned_stop(self):
        for index, code in [(2, '80000003'), (4, '80000004')]:
            self.assertEqual(self.report['results'][index]['ownedTrapReclassified'],
                             [{'code': code, 'sameAddress': True}])
            for trap in [{'code': code, 'sameAddress': False}, {'code': 'c0000005', 'sameAddress': True}]:
                report = copy.deepcopy(self.report)
                report['results'][index]['ownedTrapReclassified'] = [trap]
                self.assertTrue(validate_summary(report))

    def test_missing_exit_or_log_is_rejected_even_with_os_zero(self):
        for event in ('EVENT_CREATE_PROCESS', 'EVENT_SYSTEMBREAKPOINT',
                      'EVENT_EXIT_PROCESS', 'EVENT_STOP_DEBUG', 'EVENT_EXCEPTION'):
            report = copy.deepcopy(self.report)
            del report['results'][0]['pubEventCounts'][event]
            self.assertTrue(validate_summary(report), event)
        for key, value in [('pubExitCodes', []), ('exitZeroLogged', False), ('logEvicted', 1)]:
            report = copy.deepcopy(self.report)
            report['results'][0][key] = value
            self.assertTrue(validate_summary(report), key)

    def test_hardware_evidence_requires_actual_address_and_cpu(self):
        for key, value in [('hardwareHit', False), ('hardwareSlot', 1),
                           ('hardwareHitAtOwnedAddress', False), ('hardwareCleared', False),
                           ('hardwareAPICleared', False), ('hardwareCleanupPreservedCPU', False)]:
            report = copy.deepcopy(self.report)
            report['results'][3][key] = value
            self.assertTrue(validate_summary(report), key)

    def test_synthetic_evidence_does_not_resolve_game_exit(self):
        for key, value in [('gameLoaded', True), ('realClientExitCause', 'debugger'),
                           ('finalDebugging', True), ('finalRelevantProcesses', 1),
                           ('debuggerFiles', {}), ('pythonClientVersion', 'unknown')]:
            report = copy.deepcopy(self.report)
            report[key] = value
            self.assertTrue(validate_summary(report), key)

    def test_process_guard_checks_game_directory_and_all_fixture_pids(self):
        base = Path.cwd().resolve()
        game = base / 'fixture-game-root'
        target = base / 'local/fixture/channel_fixture.exe'
        processes = [
            {'Name': 'YuanShen.exe', 'ExecutablePath': None, 'ProcessId': 1},
            {'Name': 'renamed.exe', 'ExecutablePath': str(game / 'sub/renamed.exe'), 'ProcessId': 2},
            {'Name': 'channel_fixture.exe', 'ExecutablePath': str(base / 'elsewhere/channel_fixture.exe'), 'ProcessId': 3},
            {'Name': 'unrelated.exe', 'ExecutablePath': str(base / 'unrelated.exe'), 'ProcessId': 4},
        ]
        self.assertEqual([row['ProcessId'] for row in relevant_processes(processes, game, target)], [1, 2, 3])


if __name__ == '__main__':
    unittest.main()
