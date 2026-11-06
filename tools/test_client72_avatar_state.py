"""Independent state/enums/consumer assertions over fixed native observations."""
import hashlib
import json
import os
from pathlib import Path
import unittest

from tools.client72_avatar_state import EVIDENCE_SHA, NATIVE
from tools.extract_client72 import HASHES


class AvatarStateConsumerTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.root = Path(__file__).resolve().parents[1]
        path = Path(os.environ.get('CLIENT72_AVATAR_STATE_OBSERVATIONS', cls.root / 'protocol/cn-7.2/avatar-state-observations.json'))
        cls.report = json.loads(path.read_text('utf8')); cls.rows = {x['name']: x for x in cls.report['fixtures']}

    def test_fixed_inputs_and_incomplete_effect_scope(self):
        self.assertEqual(HASHES, self.report['inputSHA256'])
        self.assertEqual(EVIDENCE_SHA, self.report['inputContracts'])
        self.assertEqual('F-03d3f4a', self.report['task'])
        self.assertEqual(26, len(self.rows))
        for key in ('completeAvatarHandler', 'completeEntityEffects', 'ifixExecutionVerified', 'targetProtocolReady'):
            self.assertIs(False, self.report[key])
        self.assertEqual('not-tested', self.report['runtimeStatus'])
        self.assertEqual('not-established', self.report['producer']['businessRequiredForEntry'])
        for path, digest in self.report['sourceSHA256'].items():
            self.assertEqual(digest, hashlib.sha256((self.root / path).read_bytes()).hexdigest())

    def test_target_parameter_enum_and_named_events_are_independent_sources(self):
        declared = self.report['declarations']
        enums = {x['typeIndex']: x for x in declared['enums']}
        self.assertEqual({'LIFE_NONE': 0, 'LIFE_ALIVE': 1, 'LIFE_DEAD': 2, 'LIFE_REVIVE': 3},
                         {x['symbol']: x['value'] for x in enums[57376]['values']})
        self.assertEqual({'RefreshAvatarData': 16, 'AvatarLifeStateChanged': 371},
                         {x['symbol']: x['value'] for x in enums[75464]['values']})
        self.assertEqual({'EvtKill': 75, 'EvtRevive': 82}, {x['symbol']: x['value'] for x in enums[74697]['values']})
        self.assertEqual(57376, declared['stateField']['type']['typeDefinition'])
        for entry in ('0x151849670', '0x151840630'):
            method = next(x for x in declared['methods'] if x['method']['registeredAddress'] == entry)
            self.assertEqual(57376, method['parameters'][0]['type']['typeDefinition'])

    def test_alive_dead_revive_state_and_event_payload(self):
        for name, raw, state in [('alive-one', 1, 1), ('dead-two', 2, 2), ('revive-three', 3, 1)]:
            row = self.rows[name]; step = row['steps'][0]
            self.assertEqual(raw, row['rawState']); self.assertEqual(state, step['state'])
            self.assertEqual([{'id': 371, 'guid': 9007199254740993, 'state': state}, {'id': 16, 'value': 65535}], step['events'])
            self.assertEqual([{'source': '0x15184064b', 'width': 4, 'value': state}], step['writes'])
            self.assertEqual('0x15184e202', step['trace']['stop'])

    def test_omission_zero_unknown_do_not_reset_controlled_prior_state(self):
        for name in ('omitted', 'none-zero', 'unknown-seven', 'unknown-max-u32', 'explicit-zero-last'):
            step = self.rows[name]['steps'][0]
            self.assertEqual(2, step['state']); self.assertEqual([], step['writes'])
            self.assertEqual([{'id': 16, 'value': 65535}], step['events'])
        self.assertEqual(0xffffffff, self.rows['unknown-max-u32']['rawState'])
        self.assertEqual(0, self.rows['explicit-zero-last']['rawState'])
        self.assertEqual(3, self.rows['duplicate-last-revive']['rawState'])
        self.assertEqual(1, self.rows['duplicate-last-revive']['steps'][0]['state'])

    def test_same_value_repeats_state_event_with_full_guid(self):
        for name, guid, state in [('same-alive-repeated', 9223372036854775809, 1),
                                   ('same-dead-repeated', 0xffffffffffffffff, 2)]:
            row = self.rows[name]; self.assertEqual(2, len(row['steps']))
            for step in row['steps']:
                self.assertEqual(state, step['priorState']); self.assertEqual(state, step['state'])
                self.assertEqual({'id': 371, 'guid': guid, 'state': state}, step['events'][0])
                self.assertEqual(1, len(step['writes']))
        self.assertEqual(0, self.rows['zero-guid-state-event']['steps'][0]['events'][0]['guid'])

    def test_missing_unusable_and_fallback_entities_have_distinct_native_paths(self):
        for name in ('missing-entity-1', 'missing-entity-2', 'missing-entity-3', 'entity-unusable'):
            step = self.rows[name]['steps'][0]
            self.assertEqual([0x100001], step['sceneQueries'])
            self.assertIsNone(step['effectAtBoundary'])
            self.assertEqual('0x15184e202', step['trace']['stop'])
            self.assertIn('0x1517ab3a0', step['trace']['nativeEntries'])
        fallback = self.rows['fallback-entity']['steps'][0]
        self.assertEqual(82, fallback['effectAtBoundary']['eventId'])
        self.assertEqual('0x14e4e1000', fallback['trace']['stop'])

    def test_actual_entity_virtual_prefix_stops_before_deeper_effect(self):
        for name in ('alive-entity-flag-clear', 'revive-entity-flag-clear'):
            step = self.rows[name]['steps'][0]
            self.assertEqual(1, step['state']); self.assertEqual(1, step['entityFlag4fa'])
            self.assertEqual('0x148d74b38', step['trace']['stop'])
            self.assertIn('0x148d74b00', step['trace']['nativeEntries'])
            self.assertIsNone(step['effectAtBoundary'])
            self.assertEqual(1, len(step['events']))
        declared = self.report['declarations']
        self.assertEqual((98, '0x148d74b00'), (declared['entityVirtual']['virtualSlot'], declared['entityVirtual']['registeredAddress']))
        self.assertEqual('call qword ptr [rax + 0x4b8]', declared['nativeSites']['0x148d74b38']['instruction'])

    def test_real_kill_and_revive_construction_stops_before_delivery(self):
        for name, index, event in [('dead-entity-guard-set', 21751, 75), ('revive-entity-flag-set', 15410, 82)]:
            step = self.rows[name]['steps'][0]; effect = step['effectAtBoundary']
            self.assertEqual((index, event, 0x100001), (effect['typeIndex'], effect['eventId'], effect['entityId']))
            self.assertEqual('0x14e4e1000', step['trace']['stop'])
            self.assertNotIn('0x14e4e1000', step['trace']['models'])
        self.assertEqual(2, len(self.rows['revive-entity-flag-set']['steps'][0]['events']))
        pools = self.report['declarations']['pools']
        self.assertEqual({15410, 21751}, {x['arguments'][0]['typeDefinition'] for x in pools})
        self.assertEqual({645855}, {x['methodDefinition'] for x in pools})

    def test_ifix_branches_do_not_run_unknown_wrappers(self):
        for name, stop, state, count in [('ifix-state', '0x151849abc', 2, 0),
                                         ('ifix-setter', '0x151840710', 2, 0),
                                         ('ifix-entity', '0x148d74b5e', 1, 1)]:
            step = self.rows[name]['steps'][0]
            self.assertEqual(stop, step['trace']['stop']); self.assertEqual(state, step['state'])
            self.assertEqual(count, len(step['events'])); self.assertIsNone(step['effectAtBoundary'])

    def test_native_application_and_state_bodies_not_replaced(self):
        native = {hex(x) for x in NATIVE}
        for row in self.rows.values():
            for step in row['steps']:
                trace = step['trace']
                self.assertEqual('0x15184e1e4', trace['entry'])
                self.assertIn('0x151849670', trace['nativeEntries'])
                self.assertFalse(native & set(trace['models']))
        self.assertEqual((48178, 4, 'uint32'), (self.report['semanticEvidence']['typeIndex'],
                                              self.report['semanticEvidence']['tag'], self.report['semanticEvidence']['rawType']))

    def test_errors_keep_reader_and_runtime_boundaries_separate(self):
        rows = {x['name']: x for x in self.report['rejections']}; self.assertEqual(5, len(rows))
        self.assertEqual('native-reader-error-helper', rows['negative-avatar-length']['scope'])
        self.assertEqual('explicit-input-model', rows['state-varint-overflow']['scope'])
        self.assertEqual('native-null-error-helper-boundary', rows['null-scene-dictionary']['scope'])
        self.assertEqual('0x1404c2be0', rows['null-scene-dictionary']['trace']['stop'])


if __name__ == '__main__':
    unittest.main()
