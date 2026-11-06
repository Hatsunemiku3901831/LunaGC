"""Independent assertions for target depot/resource/selected-Team consumers."""
import hashlib
import json
import os
from pathlib import Path
import unittest

from tools.client72_avatar_skill_depot import EVIDENCE_SHA, NATIVE, RESOURCE_COMMIT, RESOURCE_ROOT
from tools.extract_client72 import HASHES


class AvatarSkillDepotTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.root = Path(__file__).resolve().parents[1]
        path = Path(os.environ.get('CLIENT72_AVATAR_SKILL_DEPOT_OBSERVATIONS', cls.root / 'protocol/cn-7.2/avatar-skill-depot-observations.json'))
        cls.report = json.loads(path.read_text('utf8')); cls.rows = {x['name']: x for x in cls.report['fixtures']}

    def test_fixed_sources_and_limited_semantic_scope(self):
        self.assertEqual(HASHES, self.report['inputSHA256']); self.assertEqual(EVIDENCE_SHA, self.report['inputContracts'])
        self.assertEqual('F-03d3f4c', self.report['task']); self.assertEqual(22, len(self.rows))
        self.assertEqual(109, sum(len(r['steps']) for r in self.rows.values()))
        self.assertEqual(42, sum(len(r['identities']) for r in self.rows.values()))
        for field in ('completeAvatarHandler', 'completeTeamHandler', 'resourceFileIOExecuted', 'targetProtocolReady'):
            self.assertIs(False, self.report[field])
        self.assertIs(True, self.report['completeDerivedTeamConsumer'])
        self.assertEqual('not-tested', self.report['runtimeStatus'])
        self.assertEqual('not-established', self.report['producer']['businessRequiredForEntry'])
        for path, digest in self.report['sourceSHA256'].items():
            self.assertEqual(digest, hashlib.sha256((self.root / path).read_bytes()).hexdigest())

    def test_independent_target_literal_typed_cache_and_parser_allocations(self):
        resources = self.report['declarations']['resources']
        self.assertEqual([22284, 88602], [r['rowType'] for r in resources])
        self.assertEqual(['0x40212b8', '0x4016c70'], [r['cache']['rawOffset'] for r in resources])
        self.assertEqual(['Data/_ExcelBinOutput/AvatarSkillDepotExcelConfigData', 'Data/_ExcelBinOutput/AvatarSkillExcelConfigData'],
                         [r['literal']['value'] for r in resources])
        for r in resources:
            self.assertEqual('uint32', r['cache']['type']['arguments'][0]['kind'])
            self.assertEqual(r['rowType'], r['cache']['type']['arguments'][1]['typeDefinition'])
        cells = self.report['declarations']['resourceParserCells']
        self.assertEqual([1222, 22284, 1222, 88602], [x['type']['typeDefinition'] for x in cells])
        for path in self.rows['two-depots-two-teams']['paths']:
            self.assertEqual('0x14d6e1570', path['loader']['stop']); self.assertIs(False, path['fileIOExecuted'])

    def test_named_consumer_and_actual_enum_are_independent_from_obfuscated_names(self):
        decl = self.report['declarations']; methods = {x['method']['registeredAddress']: x for x in decl['methods']}
        self.assertEqual('GetAvatarElementType', methods['0x153217960']['method']['name'])
        self.assertEqual('avatarConfigId', methods['0x153217960']['parameters'][0]['name'])
        for address in ('0x14d6d70f0', '0x14eb02d30', '0x153217960'):
            self.assertEqual('uint32', methods[address]['parameters'][0]['type']['kind'])
        self.assertEqual((108, 58209), (decl['depotSkillReference']['offset'], decl['depotSkillReference']['type']['typeDefinition']))
        self.assertEqual((84, 74813), (decl['skillEnumField']['offset'], decl['skillEnumField']['type']['typeDefinition']))
        self.assertEqual(74813, decl['teamDerivedList']['type']['arguments'][0]['typeDefinition'])
        symbols = {v['value']: v['symbol'] for v in decl['elementEnum']['values']}
        self.assertEqual(['None', 'Fire', 'Water', 'Wind'], [symbols[i] for i in (0, 1, 2, 7)])

    def test_nonzero_wire_field_native_codec_and_high_bits_survive(self):
        decl = self.report['declarations']; self.assertEqual('BLBHOJMCOJN', decl['rawField']['name'])
        self.assertEqual((48178, 11, 196, 'uint32'), tuple(self.report['semanticEvidence'][k] for k in ('typeIndex', 'tag', 'objectOffset', 'rawType')))
        row = self.rows['high-u32-depot-and-skill-keys']
        self.assertEqual([0x80000001, 0xffffffff], [i['depot'] for i in row['identities']])
        self.assertEqual([9007199254740993, 9223372036854775809], [i['guid'] for i in row['identities']])
        self.assertEqual([0x80000001, 0x80000101, 0xffffffff, 0xfffffffe], [q['id'] for q in row['steps'][0]['resourceQueries']])
        self.assertEqual([1, 2], row['steps'][0]['elements'])
        for identity in row['identities']:
            self.assertEqual(777, identity['priorDepot']); self.assertIn('0x14b1049f0', identity['decode']['nativeEntries'])

    def test_current_team_selects_full_guid_and_repeated_selection_rebuilds(self):
        row = self.rows['two-depots-two-teams']
        self.assertEqual([1, 1, 2, 9, 1], [s['teamId'] for s in row['steps']])
        self.assertEqual([[1, 2], [1, 2], [2], [], [1, 2]], [s['elements'] for s in row['steps']])
        self.assertEqual([9223372036854775809], row['steps'][2]['members'])
        selected = self.rows['only-selected-team-resources']['steps'][0]
        self.assertEqual([701, 201], [q['id'] for q in selected['resourceQueries']])
        self.assertEqual([2], selected['elements'])

    def test_omission_zero_duplicate_and_wrong_wire_have_distinct_behavior(self):
        for name in ('omitted-overwrites-prior', 'explicit-zero-last', 'wrong-depot-wire-skipped'):
            row = self.rows[name]
            self.assertEqual(0, row['identities'][0]['depot']); self.assertEqual(777, row['identities'][0]['priorDepot'])
            self.assertEqual([2], row['steps'][0]['elements'])
            self.assertNotIn(0, [q['id'] for q in row['steps'][0]['resourceQueries']])
        duplicate = self.rows['duplicate-field-last-wins']
        self.assertEqual(701, duplicate['identities'][0]['depot']); self.assertEqual([2, 2], duplicate['steps'][0]['elements'])
        self.assertEqual([1, 1], self.rows['same-depot-distinct-guids']['steps'][0]['elements'])
        self.assertEqual([2], self.rows['same-guid-latest-model-field']['steps'][0]['elements'])

    def test_missing_resource_null_zero_and_unknown_enum_do_not_become_success_stubs(self):
        for name in ('missing-depot-row', 'null-depot-row', 'zero-skill-reference', 'missing-skill-row', 'null-skill-row', 'zero-skill-enum'):
            self.assertEqual([2], self.rows[name]['steps'][0]['elements'])
        for name in ('absent-depot-cache', 'absent-skill-cache', 'no-owned-avatar'):
            self.assertEqual([], self.rows[name]['steps'][0]['elements'])
        self.assertEqual([777, 2], self.rows['unknown-enum-remains-raw']['steps'][0]['elements'])
        self.assertEqual([2], self.rows['missing-and-zero-member-guids']['steps'][0]['elements'])
        zero = self.rows['zero-skill-reference']['steps'][0]
        self.assertEqual([501, 701, 201], [q['id'] for q in zero['resourceQueries']])

    def test_named_default_depot_and_runtime_wire_depot_are_not_conflated(self):
        row = self.rows['duplicate-field-last-wins']
        named = row['namedAvatarElements'][0]
        self.assertEqual((501, 1), (named['defaultDepot'], named['element']))
        self.assertEqual([2, 2], row['steps'][0]['elements'])
        self.assertEqual([('avatar', 1001), ('depot', 501), ('skill', 101)], [(q['resource'], q['id']) for q in named['queries']])
        self.assertEqual([360, 108, 84], [r['offset'] for r in named['resourceReads']])
        zero = self.rows['zero-skill-reference']['namedAvatarElements'][0]
        self.assertEqual(1, zero['element']); self.assertEqual(0, zero['queries'][-1]['id'])

    def test_actual_candidate_initial_rows_remain_partial_zero_default_projection(self):
        source = self.report['candidateSource']; row = self.rows['pinned-candidate-initial-501-701']
        self.assertEqual((RESOURCE_COMMIT, RESOURCE_ROOT), (source['commit'], source['rootTree']))
        self.assertEqual([{'id': 10000005, 'skillDepotId': 501}, {'id': 10000007, 'skillDepotId': 701}], source['avatarRows'])
        self.assertEqual([501, 701], [r['id'] for r in source['depotRows']])
        for depot in source['depotRows']:
            self.assertEqual({'id'}, {k for k, v in depot.items() if type(v) in (int, float, bool)})
            self.assertEqual([{}, {}], depot['LHFOOPMJHBN'])
        for flag in ('targetResourceDeserializationVerified', 'targetConstructorDefaultsVerified', 'englishEnergySkillAliasVerified'):
            self.assertIs(False, source[flag])
        self.assertEqual([[], [], [], [], []], [s['elements'] for s in row['steps']])
        self.assertEqual([501, 701], [q['id'] for q in row['steps'][0]['resourceQueries']])
        self.assertEqual([0, 0], [q['element'] for q in row['namedAvatarElements']])
        canonical = json.dumps(source['depotRows'], sort_keys=True, separators=(',', ':')).encode()
        self.assertEqual(hashlib.sha256(canonical).hexdigest(), source['depotRowsCanonicalSHA256'])
        self.assertEqual(4, len(source['tableSHA256']))

    def test_sibling_unknown_array_is_not_a_scalar_alias_or_tested_read(self):
        sibling = self.report['siblingField']
        self.assertEqual((22284, 'LHFOOPMJHBN', 16, 40302), tuple(sibling[k] for k in ('owner', 'name', 'offset', 'arrayElementType')))
        self.assertEqual('unknown', sibling['businessSemantic'])
        self.assertIs(False, sibling['readByTestedTeamConsumer']); self.assertIs(False, sibling['specialProudSkillOpensAliasApproved'])
        for row in self.rows.values():
            for step in row['steps']:
                self.assertTrue(all(r['offset'] == (108 if r['resource'] == 'depot' else 84) for r in step['resourceReads']))

    def test_complete_bounded_native_function_and_managed_lookup_model_are_explicit(self):
        for row in self.rows.values():
            for step in row['steps']:
                trace = step['trace']
                self.assertEqual('0x151a9ec40', trace['entry']); self.assertEqual('0x620000100', trace['stop'])
                self.assertTrue({'0x151a9ec40', '0x151a9bd20'} <= set(trace['nativeEntries']))
                self.assertFalse({hex(x) for x in NATIVE} & set(trace['models']))
                self.assertEqual(step['elements'], [w['value'] for w in step['elementWrites']])
                self.assertTrue(all(w['source'] == '0x151a9ecf5' and w['width'] == 4 for w in step['elementWrites']))
            for named in row['namedAvatarElements']:
                self.assertIn('0x153217960', named['trace']['nativeEntries'])
                self.assertFalse({hex(x) for x in NATIVE} & set(named['trace']['models']))

    def test_raw_error_resource_corruption_and_ifix_boundaries_remain_separate(self):
        errors = {e['name']: e for e in self.report['rejections']}; self.assertEqual(6, len(errors))
        for name in ('truncated-depot-varint', 'depot-varint-overflow'):
            self.assertEqual('explicit-input-model', errors[name]['scope'])
        self.assertEqual('native-reader-error-helper', errors['negative-avatar-length']['scope'])
        self.assertEqual('0x1404c2be0', errors['null-depot-entry-array']['trace']['stop'])
        self.assertEqual('0x1404c2650', errors['out-of-bounds-resource-position']['trace']['stop'])
        self.assertIn('fixture corruption', errors['out-of-bounds-resource-position']['scope'])
        self.assertEqual('0x151a9ef29', errors['ifix-consumer-boundary']['trace']['stop'])
        self.assertEqual([], errors['ifix-consumer-boundary']['trace']['models'])


if __name__ == '__main__': unittest.main()
