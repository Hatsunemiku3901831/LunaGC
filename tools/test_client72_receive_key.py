import copy
import json
from pathlib import Path
import unittest

from tools.trace_client72_receive_key import fixture_cases, validate_summary


class ReceiveKeyTests(unittest.TestCase):
    def setUp(self):
        self.report = json.loads((Path(__file__).resolve().parents[1] /
            'protocol/cn-7.2/receive-key-observations.json').read_text('utf8'))

    def test_native_key_receiver_and_consumer_evidence(self):
        self.assertEqual(validate_summary(self.report), [])

    def test_state_continuity_is_not_runtime_control_flow(self):
        for key, value in [('runtimeStatus', 'passed'), ('wholeHandlerReturned', True),
                           ('sameRuntimeState', False), ('continuousControlFlow', True),
                           ('keySuppliedToReceiver', True), ('models', [])]:
            changed = copy.deepcopy(self.report)
            changed[key] = value
            self.assertTrue(validate_summary(changed), key)

    def test_selector_and_key_pointer_are_not_fixture_replacements(self):
        for key, value in [('selectorBefore', 1), ('selectorAfter', 1),
                           ('keyPointerPreserved', False), ('keyBytesPreserved', False),
                           ('selectorWrites', [{'instruction': 'fixture', 'value': 0}])]:
            changed = copy.deepcopy(self.report)
            changed['results'][0][key] = value
            self.assertTrue(validate_summary(changed), key)

    def test_wrong_key_and_invalid_frame_do_not_reach_factory_or_queue(self):
        for name in ('dispatch-key-rejected', 'truncated-frame', 'bad-start'):
            changed = copy.deepcopy(self.report)
            row = next(r for r in changed['results'] if r['name'] == name)
            row['enqueued'] = True
            self.assertTrue(validate_summary(changed), name)

    def test_native_factory_reader_and_callback_cannot_be_opaque(self):
        for entry in ('0x1472cf280', '0x14a446cc0', '0x151a853d0', '0x15585b760', '0x14b89ec90'):
            changed = copy.deepcopy(self.report)
            changed['results'][0]['nativeEntries'].remove(entry)
            self.assertTrue(validate_summary(changed), entry)
            changed = copy.deepcopy(self.report)
            changed['results'][0]['models'].append(entry)
            self.assertTrue(validate_summary(changed), entry)

    def test_independent_wire_boundaries_and_consumer_pointer(self):
        cases = {c['name']: c for c in fixture_cases()}
        self.assertEqual(cases['nonzero-64-error']['body'].hex(), '28818080808080801040ffffffffffffffffff01')
        self.assertEqual(cases['message-list']['body'].hex(), 'd23f0a6dffffffff7d2c010000d23f056d01000000f2220408011002f222020803')
        changed = copy.deepcopy(self.report)
        changed['results'][0]['independentObjectMatch'] = False
        self.assertTrue(validate_summary(changed))
        changed = copy.deepcopy(self.report)
        next(r for r in changed['results'] if r['name'] == 'resource-consumer')['consumer']['messagePointerPreserved'] = False
        self.assertTrue(validate_summary(changed))


if __name__ == '__main__':
    unittest.main()
