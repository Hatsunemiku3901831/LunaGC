"""Independent evidence checks for the bounded entering-scene contract."""
import copy
import json
from pathlib import Path
import unittest
from tools import client72_enter_scene_contract as subject

ROOT = Path(__file__).resolve().parents[1]


class EnterSceneContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.summary = json.loads((ROOT / 'protocol/cn-7.2/enter-scene-contract-observations.json').read_text('utf8'))

    def test_fixed_raw_family_and_semantic_scope(self):
        subject.validate_summary(self.summary)
        self.assertEqual(len(self.summary['rawSchemas']), 8)
        self.assertEqual(sum(len(m['fields']) for m in self.summary['rawSchemas']), 44)
        self.assertFalse(self.summary['completeSenderContract'])

    def test_entering_and_current_scene_are_not_conflated(self):
        rows = self.summary['sceneIdObservations']
        self.assertEqual([r['sceneId'] for r in rows], [0, 1, 3, 0x80000000, 0xFFFFFFFF])
        self.assertEqual(rows[0]['raw'], 0xFFFF121E)
        self.assertEqual(rows[2]['raw'], 0xFFFF1223)
        for row in rows:
            self.assertNotEqual(row['currentSceneId'], row['sceneId'])
            self.assertEqual(row['namedDiagnosticValues'], [row['currentSceneId'], row['sceneId']])

    def test_vector_uses_target_unboxed_xyz_layout(self):
        self.assertEqual(self.summary['vectorAxes'], {'x': 16, 'y': 20, 'z': 24})
        rows = self.summary['positionObservations']
        self.assertEqual(rows[0]['taskVectorXYZBits'], [0x3FA00000, 0xC0200000, 0x40700000])
        self.assertEqual(rows[1]['taskVectorXYZBits'], [0x80000000, 1, 0x7F7FFFFF])
        self.assertEqual(rows[-1]['name'], 'missing-position-original-null')
        self.assertEqual(rows[-1]['stop'], '0x1404c2be0')

    def test_actual_notify_modes_preserve_position_through_queue_and_receiver(self):
        rows = [r for r in self.summary['positionConsumptionObservations'] if r['kind'] == 'notify-mode']
        self.assertEqual(len(rows), 6)
        self.assertEqual([r['mode'] for r in rows], [0, 3, 0, 3, 0, 3])
        self.assertEqual([r['modeName'] for r in rows[:2]], ['Default', 'DirectlySetPos'])
        for row in rows:
            self.assertTrue(row['originalCarrierQueued'])
            self.assertEqual(row['queueCount'], 1)
            self.assertEqual(row['receiverVectorXYZBits'], row['vectorXYZBits'])
            self.assertEqual(row['stops'][2], '0x14b031540')

    def test_independent_quick_position_names_and_negative_zero_are_preserved(self):
        naming = self.summary['positionNaming']
        self.assertEqual(naming['parameters'], ['pos1', 'pos2', 'yaw'])
        self.assertEqual(naming['event']['symbol'], 'EvtTransmitAvatar')
        self.assertEqual(naming['classComparison'], '0x14b030582')
        rows = [r for r in self.summary['positionConsumptionObservations'] if r['kind'] == 'independent-quickv3-naming']
        self.assertEqual(len(rows), 3)
        self.assertEqual(rows[0]['namedPos2Bits'], [0x3FF4000000000000, 0xC004000000000000, 0x400E000000000000])
        self.assertEqual(rows[1]['centerAfterBits'][0], 0x8000000000000000)
        for row in rows:
            self.assertFalse(row['notifyPath'])
            self.assertEqual(row['mode'], 7)
            self.assertEqual(row['namedPos2Bits'], row['centerAfterBits'])
            self.assertNotEqual(row['namedPos1Bits'], row['namedPos2Bits'])

    def test_minimum_position_binding_keeps_runtime_and_other_roles_unverified(self):
        self.assertEqual(self.summary['minimumSenderFieldContract'], ['scene_id', 'type', 'enter_scene_token', 'pos'])
        binding = next(b for b in self.summary['semanticBindings'] if b['name'] == 'pos')
        self.assertEqual((binding['tag'], binding['objectOffset'], binding['nestedTypeIndex']), (14, 24, 84343))
        self.assertTrue(binding['requiredNonNull'])
        self.assertFalse(binding['runtimePositionApplied'])
        row = copy.deepcopy(self.summary); row['positionConsumptionObservations'][2]['notifyPath'] = True
        with self.assertRaisesRegex(ValueError, 'named position branch'): subject.validate_summary(row)
        row = copy.deepcopy(self.summary); row['positionConsumptionObservations'][0]['receiverVectorXYZBits'][0] = 0
        with self.assertRaisesRegex(ValueError, 'position receiver proof'): subject.validate_summary(row)

    def test_enter_enum_targets_and_unknown_diagnostic(self):
        values = self.summary['identity']['enum']['values']
        self.assertEqual([(v['symbol'], v['value']) for v in values[:4]],
                         [('EnterNone', 0), ('EnterSelf', 1), ('EnterGoto', 2), ('EnterJump', 3)])
        rows = {r['enterType']: r for r in self.summary['enterTypeObservations']}
        self.assertEqual(rows[1]['destination'], '0x14cd637b0')
        self.assertEqual(rows[2]['destination'], '0x14cd67990')
        self.assertEqual(rows[14]['destination'], '0x14cd637b0')
        for value in (0, -1, 15, 0x7FFFFFFF): self.assertEqual(rows[value]['destination'], '0x147161b60')

    def test_unknown_uint64_and_uint32_consumers_preserve_boundaries(self):
        rows = self.summary['unboundConsumerObservations']
        self.assertEqual(rows[2]['observed']['static145873388'], 0xFEDCBA9876543210)
        self.assertEqual(rows[3]['observed']['static145873388'], 0xFFFFFFFFFFFFFFFF)
        for row in rows:
            self.assertEqual(row['observed']['manager100'], row['value32'])
            self.assertEqual(row['observed']['player1d8'], row['value32'])
            self.assertFalse(row['productionAllowed'])
        self.assertTrue(all(not field['productionAllowed'] for field in self.summary['unresolvedSenderFields']))

    def test_changed_hash_or_wire_field_is_rejected(self):
        row = copy.deepcopy(self.summary); row['inputSHA256']['YuanShen.exe'] = '0' * 64
        with self.assertRaisesRegex(ValueError, 'inputs changed'): subject.validate_summary(row)
        row = copy.deepcopy(self.summary); row['rawSchemas'][0]['fields'][0]['tag'] += 1
        with self.assertRaisesRegex(ValueError, 'wire field changed'): subject.validate_summary(row)

    def test_scalar_candidates_are_not_promoted(self):
        row = copy.deepcopy(self.summary); row['semanticBindings'].append(dict(typeIndex=46311, tag=2, name='world_level'))
        with self.assertRaisesRegex(ValueError, 'semantics promoted'): subject.validate_summary(row)
        row = copy.deepcopy(self.summary); row['unresolvedSenderFields'][1]['productionAllowed'] = True
        with self.assertRaisesRegex(ValueError, 'sender values enabled'): subject.validate_summary(row)
        row = copy.deepcopy(self.summary); row['unboundConsumerObservations'][-1]['observed']['static145873388'] &= 0xFFFFFFFF
        with self.assertRaisesRegex(ValueError, 'Uint64 boundary truncated'): subject.validate_summary(row)


if __name__ == '__main__': unittest.main()
