"""Independent wire and branch regressions for the checked B-02e2 native runs."""
import copy
import json
from pathlib import Path
import unittest
from tools.client72_reader import WireInput
from tools.extract_client72 import HASHES
from tools.trace_client72_scene_wait import validate_fixtures
from tools.validate_protocol72 import validate_scene_wait, validate_observations

ROOT = Path(__file__).resolve().parents[1]


class SceneWaitTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.report = json.loads((ROOT / 'protocol/cn-7.2/scene-wait-observations.json').read_text('utf8'))

    def rows(self):
        return copy.deepcopy(self.report['observations'])

    def test_identity_scope_and_complete_field_coverage(self):
        report = self.report
        self.assertEqual(report['inputSHA256'], HASHES)
        self.assertEqual((report['declarations']['typeIndex'], report['declarations']['cmdId']), (46311, 27748))
        self.assertEqual(report['declarations']['semanticName'], 'PlayerEnterSceneNotify')
        self.assertFalse(report['closureComplete']); self.assertFalse(report['targetProtocolReady'])
        self.assertEqual(report['runtimeStatus'], 'not-tested')
        self.assertEqual((len(report['fields']), report['outerScanSamples'], report['fixtureCount']), (26, 128, 32))
        self.assertEqual({f['tag'] for f in report['fields']}, {1, 2, 5, 6, 7, 8, 10, 11, 12, 13, 14, 15, 46, 157, 293, 395, 739, 763, 941, 957, 1345, 1357, 1583, 1723, 1964, 2008})
        self.assertTrue(all(f['semanticStatus'] == 'unresolved' for f in report['fields']))
        validate_fixtures(self.rows())

    def test_independent_uint64_and_unicode_wire(self):
        row = next(r for r in self.rows() if r.get('name') == 'scalar-boundaries')
        reader = WireInput(bytes.fromhex(row['wire'])); values = {}
        while tag := reader.tag():
            self.assertEqual(tag & 7, 0); values[tag >> 3] = reader.varint()
        self.assertEqual(values[6], 0xFFFFFFFFFFFFFFFF)
        self.assertEqual(row['result']['storedObject']['rawStoredScalars']['0x68'], 0xFFFFFFFFFFFFFFFF)
        text = next(r for r in self.rows() if r.get('name') == 'unicode-nul')
        reader = WireInput(bytes.fromhex(text['wire'])); self.assertEqual(reader.tag(), 293 * 8 + 2)
        self.assertEqual(reader.blob().decode('utf8'), '旅人🚀\0')

    def test_position_layout_and_unknown_children_are_distinct(self):
        row = next(r for r in self.rows() if r.get('name') == 'nonempty-positions')
        self.assertEqual(row['result']['storedObject']['positionFloat32Bits']['0x18'], [0x3FA00000, 0x40700000, 0xC0200000])
        self.assertEqual({f['objectOffset']: f['tag'] for f in self.report['positionFields']}, {24: 1, 32: 2, 28: 3})
        children = [f for f in self.report['fields'] if f['type'] == 'message']
        self.assertEqual(sum(f['contentStatus'] == 'native-position-content-observed' for f in children), 3)
        self.assertEqual(sum(f['contentStatus'] == 'not-traced' for f in children), 5)
        self.assertIn('bytes f_28=1964;', self.report['fixtureProto'])
        self.assertNotIn('message ABJMLEPLFJG', self.report['fixtureProto'])

    def test_mixed_lists_nested_merge_and_source_mutation(self):
        row = next(r for r in self.rows() if r.get('name') == 'duplicate-scalar-mixed-list-nested-merge')
        self.assertEqual(row['result']['storedObject']['repeatedUint32'], [1, 2, 0xFFFFFFFF, 0])
        field = next(f for f in self.report['fields'] if f['objectOffset'] == 32)
        self.assertEqual(field['acceptedWireTypes'], [0, 2]); self.assertTrue(field['packed'])
        for name, expected in [('packed-list', ['uint32-list']), ('nonempty-positions', ['position-0x18', 'position-0x50', 'position-0x30'])]:
            row = next(r for r in self.rows() if r.get('name') == name)
            self.assertEqual(row['mutatedSourceCollections'], expected)
            self.assertTrue(row['cloneUnaffectedBySourceMutation'])

    def test_explicit_zero_is_not_an_absent_encoded_default(self):
        row = next(r for r in self.rows() if r.get('name') == 'explicit-wire-zero-replaces-encoded-constructor-defaults')
        self.assertEqual(row['result']['constructorDefaults']['rawStoredScalars']['0x94'], 0x8788)
        self.assertEqual(row['result']['constructorDefaults']['rawStoredScalars']['0x7c'], 0xFFFF121E)
        self.assertEqual(set(row['result']['storedObject']['rawStoredScalars'].values()), {0})
        waiting = next(r for r in self.rows() if r.get('name') == 'waiting-cache')
        self.assertEqual(waiting['senderD0'], (((0xFFFFFFFF + 0x5889) & 0xFFFFFFFF) ^ 0xE011))
        self.assertEqual(waiting['network1d0'], ((0x80000000 ^ 0x2D03) + 0xC0E3) & 0xFFFFFFFF)

    def test_native_error_branches_and_model_errors_remain_labeled(self):
        errors = {r['name']: r for r in self.rows() if r['kind'] == 'reader-rejection'}
        self.assertEqual(len(errors), 7)
        for name in ('varint-overflow', 'nested-recursion-limit'):
            self.assertEqual(errors[name]['errorScope'], 'native-branch')
        self.assertEqual(errors['zero-tag']['errorScope'], 'explicit-wire-or-storage-model')

    def test_wrapper_uses_real_absent_context_path_and_original_message(self):
        row = next(r for r in self.rows() if r.get('name') == 'wrapper-absent-context')
        self.assertEqual(row['logs'], [45993, 50090])
        self.assertTrue({'0x1507ad120', '0x1507fd7d0', '0x1517113f0', '0x1507ad6f0', '0x14cd67260', '0x152386c00', '0x152386cb0'} <= set(row['executedNativeEntries']))
        self.assertTrue(all(v['originalPointer'] for v in row['argumentObservations']))

    def test_cache_wait_cannot_claim_stage_completion_or_share_source_storage(self):
        for key, value in [('cachedCloneDistinct', False), ('cachedEqualsReader', False), ('finalBornFlags', [0, 0]), ('uiState', 3)]:
            rows = self.rows(); next(r for r in rows if r.get('name') == 'waiting-cache')[key] = value
            with self.assertRaises(ValueError): validate_fixtures(rows)
        rows = self.rows(); row = next(r for r in rows if r.get('name') == 'media-ready')
        row['argumentObservations'][1]['originalPointer'] = False
        with self.assertRaises(ValueError): validate_fixtures(rows)

    def test_config105_and_null_stop_before_cache_and_scalar_writes(self):
        for name in ('gate-config-105-boundary', 'null-message'):
            rows = self.rows(); row = next(r for r in rows if r.get('name') == name)
            self.assertFalse(row['cachedCloneDistinct']); self.assertEqual(row['senderD0'], 0)
            row['senderD0'] = 1
            with self.assertRaises(ValueError): validate_fixtures(rows)

    def test_missing_case_and_mutable_clone_claim_are_rejected(self):
        rows = self.rows(); rows.pop()
        with self.assertRaises(ValueError): validate_fixtures(rows)
        rows = self.rows(); next(r for r in rows if r.get('name') == 'packed-list')['cloneUnaffectedBySourceMutation'] = False
        with self.assertRaises(ValueError): validate_fixtures(rows)

    def test_mapping_identity_and_raw_tags_are_guarded_without_promoting_semantics(self):
        directory = ROOT / 'protocol/cn-7.2'
        read = lambda name: json.loads((directory / name).read_text('utf8'))
        document, profile = read('mappings.json'), read('profile.json')
        self.assertEqual(validate_scene_wait(document, profile, self.report), [])
        altered = copy.deepcopy(self.report); altered['inputSHA256']['YuanShen.exe'] = '0' * 64
        self.assertTrue(any('hash mismatch' in e for e in validate_scene_wait(document, profile, altered)))
        table = dict(inputSHA256=self.report['inputSHA256'], messages=[dict(clientType='BBECFDMDFJC', fields=self.report['fields'])])
        extra = [('scene-wait', table)]
        arguments = (profile, read('commands.json'), read('wire-observations.json'), read('token-wire-observations.json'), extra)
        self.assertEqual(validate_observations(document, *arguments), [])
        schema = next(r for r in document['schemas'] if r['semanticName'] == 'PlayerEnterSceneNotify')
        schema['fields'][0]['tag'] = 9
        self.assertTrue(any('wire binding mismatch' in e for e in validate_observations(document, *arguments)))
        schema['fields'][0]['status'] = 'verified'
        self.assertTrue(any('semantics promoted' in e for e in validate_scene_wait(document, profile, self.report)))


if __name__ == '__main__': unittest.main()
