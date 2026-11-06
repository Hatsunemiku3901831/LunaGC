"""Offline guards for stop ownership and evidence association; never load a target."""
import copy
import json
from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parent))
from observe_client72_startup import exit_log_codes, matching_returns, register_action, stop_action, summarize


class StartupObserverTests(unittest.TestCase):
    def exception(self, code=0xC0000005, first=True):
        return {'event': 'EVENT_EXCEPTION', 'data': {
            'ExceptionCode': code, 'ExceptionAddress': 0x1234, 'dwFirstChance': first}}

    def expressions(self, event):
        data = event['data']
        return {'code': [data['ExceptionCode'], True], 'addr': [data['ExceptionAddress'], True],
                'firstchance': [int(data['dwFirstChance']), True]}

    def classify(self, event, values=None, cip=0x1234, points=None, ep=0x9000, tls=()):
        return stop_action(event, values or {}, cip, points or {}, ep, set(tls))

    def test_only_matched_known_first_chance_is_delivered(self):
        for code in (0xC0000005, 0xE06D7363):
            event = self.exception(code)
            self.assertEqual(self.classify(event, self.expressions(event)),
                             'deliver-current-application-first-chance')
            for key in ('code', 'addr', 'firstchance'):
                values = self.expressions(event); values[key][0] += 1
                self.assertEqual(self.classify(event, values), 'stop-exception-mismatch')
        event = self.exception(first=False)
        self.assertEqual(self.classify(event, self.expressions(event)), 'stop-second-chance')

    def test_unknown_traps_are_never_automatically_consumed(self):
        for code in (0x80000003, 0x80000004, 0xC0000409):
            event = self.exception(code)
            self.assertEqual(self.classify(event, self.expressions(event)), 'stop-unknown-exception')
        self.assertEqual(self.classify(None), 'stop-missing-event')
        self.assertEqual(self.classify({'event': 'EVENT_PAUSE_DEBUG', 'data': None}),
                         'stop-unrequested-pause')

    def test_owned_stop_ignores_stale_exception_state(self):
        event = {'event': 'EVENT_BREAKPOINT', 'data': {
            'addr': 0x1234, 'name': 'cn72_r2_http', 'singleshoot': False}}
        stale = self.expressions(self.exception())
        self.assertEqual(self.classify(event, stale, points={0x1234: 'cn72_r2_http'}),
                         'observe-owned-point')
        self.assertEqual(self.classify(event, stale, points={0x1234: 'other'}),
                         'stop-unowned-breakpoint')
        self.assertEqual(self.classify(event, stale, cip=0x1235, points={0x1234: 'cn72_r2_http'}),
                         'stop-breakpoint-address-mismatch')

    def test_tls_name_alone_is_not_ownership(self):
        event = {'event': 'EVENT_BREAKPOINT', 'data': {
            'addr': 0x1234, 'name': 'TLS Callback 1', 'singleshoot': True}}
        self.assertEqual(self.classify(event), 'stop-unowned-breakpoint')
        self.assertEqual(self.classify(event, tls=(0x1234,)), 'consume-verified-tls-callback')
        self.assertEqual(self.classify(event, ep=0x1234), 'consume-main-entrypoint')

    def test_return_requires_thread_stack_and_address(self):
        origin = {'address': 0x1234, 'expectedRsp': 0x9008, 'tid': 72}
        snap = {'registers': {'cip': 0x1234, 'rsp': 0x9008}, 'tid': 72}
        self.assertEqual(matching_returns([origin], snap), [origin])
        for key in ('cip', 'rsp'):
            changed = copy.deepcopy(snap); changed['registers'][key] += 8
            self.assertEqual(matching_returns([origin], changed), [])
        changed = copy.deepcopy(snap); changed['tid'] += 1
        self.assertEqual(matching_returns([origin], changed), [])

    def test_every_stop_rechecks_both_apis_and_active_hardware(self):
        values = dict(cip=0x1000, rsp=0x9000, eflags=0x244,
                      dr0=0, dr1=0, dr2=0, dr3=0, dr6=0, dr7=0)
        snap = dict(registers=dict(values), registerExpressions=dict(values))
        self.assertIsNone(register_action(snap))
        snap['registers']['dr0'] = 0x1234
        self.assertEqual(register_action(snap), 'stop-register-api-mismatch')
        snap['registerExpressions']['dr0'] = 0x1234
        self.assertEqual(register_action(snap), 'stop-hardware-state-changed')
        for group in snap.values():
            group.update(dr0=0, dr6=0xffff0ff0, dr7=0x400)
        self.assertIsNone(register_action(snap))
        for group in snap.values():
            group['dr6'] |= 1
        self.assertEqual(register_action(snap), 'stop-hardware-status-changed')

    def test_localized_exit_log_needs_stop_save_line(self):
        self.assertEqual(exit_log_codes(['localized 0x80000003 (STATUS_BREAKPOINT) save C:/x.dd64']),
                         ['80000003'])
        self.assertEqual(exit_log_codes(['ExceptionCode: 80000003 (STATUS_BREAKPOINT)',
                                        '0x80000003 (STATUS_BREAKPOINT)']), [])

    def test_public_summary_excludes_raw_identity_and_strings(self):
        raw = {'events': [], 'observations': [], 'inputSHA256': {}, 'debuggerFiles': {},
               'observationBudgetSeconds': 120, 'pid': 555, 'session': 'secret-machine-path',
               'logMessages': ['private-token'], 'ownedPointsRemaining': {'12345': 'private-point'},
               'finalState': {'debugging': False, 'running': True}}
        summary = summarize(raw)
        encoded = json.dumps(summary)
        for marker in ('555', 'secret-machine-path', 'private-token', '12345', 'private-point'):
            self.assertNotIn(marker, encoded)
        self.assertEqual(summary['softwarePointsRemaining'], 0)
        self.assertFalse(summary['sdkAuthenticationVerified'])
        self.assertFalse(summary['loginOrWorldVerified'])


if __name__ == '__main__':
    unittest.main()
