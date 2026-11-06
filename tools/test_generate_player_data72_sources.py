"""Reject protocol drift and accidental promotion beyond the native consumption contract."""
import copy
import json
from pathlib import Path
import unittest

from tools.generate_player_data72_sources import render_sources, OPCODES, PROTO

ROOT = Path(__file__).resolve().parents[1]


class PlayerDataGeneratorTests(unittest.TestCase):
    def setUp(self):
        directory = ROOT / 'protocol/cn-7.2'
        self.inputs = [json.loads((directory / name).read_text('utf8')) for name in
                       ('mappings.json', 'profile.json', 'player-data-wire-observations.json',
                        'player-data-contract-observations.json')]
        self.opcodes = (ROOT / OPCODES).read_text('utf8')

    def render(self, inputs=None, source=None):
        return render_sources(*(self.inputs if inputs is None else inputs),
                              self.opcodes if source is None else source)

    def test_current_sources_are_exact_and_legacy_shared_value_is_outside_scope(self):
        outputs = self.render()
        self.assertEqual({OPCODES, PROTO / 'PlayerDataNotify.proto', PROTO / 'Client72PlayerDataNested.proto'}, set(outputs))
        for path, value in outputs.items():
            self.assertEqual((ROOT / path).read_bytes(), value.encode('utf8'))
        child = outputs[PROTO / 'Client72PlayerDataNested.proto']
        self.assertIn('oneof raw_value', child)
        self.assertIn('int64 AEJAFPNBBHO = 4;', child)
        self.assertIn('float BNJKCHONGGE_float = 3;', child)

    def test_unknown_field_cannot_be_promoted_using_an_old_business_name(self):
        modified = copy.deepcopy(self.inputs)
        schema = next(s for s in modified[0]['schemas'] if s['semanticName'] == 'PlayerDataNotify')
        schema['fields'][0].update(status='verified', semanticField='region_id', ambiguities=[])
        with self.assertRaisesRegex(ValueError, 'raw/semantic boundary'):
            self.render(modified)

    def test_raw_tag_drift_is_not_approved_by_editing_both_mapping_and_observation(self):
        modified = copy.deepcopy(self.inputs)
        modified[2]['messages'][0]['fields'][0]['tag'] = 9
        next(s for s in modified[0]['schemas'] if s['semanticName'] == 'PlayerDataNotify')['fields'][0]['tag'] = 9
        with self.assertRaisesRegex(ValueError, 'native raw field set changed'):
            self.render(modified)

    def test_changed_target_hash_or_live_claim_is_rejected(self):
        for key, value in (('inputSHA256', {}), ('runtimeStatus', 'passed')):
            with self.subTest(key=key):
                modified = copy.deepcopy(self.inputs); modified[2][key] = value
                with self.assertRaises(ValueError): self.render(modified)

    def test_oneof_tag4_must_remain_an_independent_field(self):
        modified = copy.deepcopy(self.inputs)
        schema = next(s for s in modified[0]['schemas'] if s['semanticName'] == 'PropValue')
        schema['fields'][3].update(presence='oneof', oneof='raw_value')
        with self.assertRaisesRegex(ValueError, 'raw/semantic boundary'):
            self.render(modified)

    def test_semantic_report_cannot_add_unexecuted_field_binding(self):
        modified = copy.deepcopy(self.inputs)
        modified[3]['fieldBindings'].append(dict(typeIndex=79263, tag=5,
            semanticField='region_id', status='verified'))
        with self.assertRaisesRegex(ValueError, 'unknown field promoted'):
            self.render(modified)

    def test_existing_positive_command_collision_or_missing_canonical_route_rejects(self):
        with self.assertRaisesRegex(ValueError, 'command collision'):
            self.render(source=self.opcodes + '\npublic static final int UnreviewedNotify = 7193;\n')
        with self.assertRaisesRegex(ValueError, 'one canonical'):
            self.render(source=self.opcodes.replace('public static final int PlayerDataNotify = 7193;', ''))


if __name__ == '__main__': unittest.main()
