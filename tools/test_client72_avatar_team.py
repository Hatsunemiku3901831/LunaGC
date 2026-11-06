"""Regressions for independently authored wire and actual Team consumers."""
import hashlib
import json
import os
from pathlib import Path
import unittest

from tools.client72_avatar_team import EVIDENCE_SHA, NATIVE
from tools.extract_client72 import HASHES


class AvatarTeamConsumerTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.root = Path(__file__).resolve().parents[1]
        path = Path(os.environ.get('CLIENT72_AVATAR_TEAM_OBSERVATIONS', cls.root / 'protocol/cn-7.2/avatar-team-observations.json'))
        cls.report = json.loads(path.read_text('utf8'))
        cls.rows = {x['name']: x for x in cls.report['fixtures']}

    def test_fixed_evidence_and_explicit_scope(self):
        self.assertEqual(HASHES, self.report['inputSHA256'])
        self.assertEqual(EVIDENCE_SHA, self.report['inputContracts'])
        self.assertEqual('F-03d3f3', self.report['task'])
        self.assertEqual(13, len(self.rows))
        for key in ('completeAvatarHandler', 'root3CurrentControlGuidApproved', 'persistenceVerified', 'targetProtocolReady'):
            self.assertIs(False, self.report[key])
        self.assertEqual('not-tested', self.report['runtimeStatus'])
        for path, digest in self.report['sourceSHA256'].items():
            self.assertEqual(digest, hashlib.sha256((self.root / path).read_bytes()).hexdigest())

    def test_named_event_and_concrete_u64_aot_cross_sources(self):
        declared = self.report['declarations']
        symbols = {x['symbol']: x['value'] for x in declared['namedEvent']['values']}
        self.assertEqual({'TeamNameRefresh': 363, 'CurrentTeamChanged': 364, 'AvatarRenameInfoReady': 29}, symbols)
        growth = [x for x in declared['uint64ListAOT'] if x['method'] == 'AddWithResize']
        self.assertEqual(1, len(growth))
        self.assertEqual(('0x157ee1fe0', 'uint64'), (growth[0]['address'], growth[0]['arguments'][0]['kind']))
        sites = declared['nativeSites']
        self.assertEqual('mov dword ptr [rsp + 0x30], 0x16b', sites['0x151a9d51c']['instruction'])
        self.assertEqual('mov dword ptr [rsp + 0x20], 0x16c', sites['0x151a9e0f0']['instruction'])
        fields = {(x['typeIndex'], x['tag']) for x in self.report['semanticEvidence']['fields']}
        self.assertEqual({(30509, 5), (30509, 12), (26081, 2), (26081, 12)}, fields)

    def test_two_nonempty_teams_share_high_guid_without_collision(self):
        row = self.rows['two-teams-high-guids']
        a, b = 9007199254740993, 9223372036854775809
        self.assertEqual({'1': {'name': 'Alpha', 'guids': [a, b]}, '2': {'name': 'Beta', 'guids': [b]}}, row['actualTeams'])
        self.assertEqual([a, b, b], [x['guid'] for x in row['memberStores']])
        collision = self.rows['same-low32-distinct-guids']
        self.assertEqual([0x100000001], collision['actualTeams']['3']['guids'])
        self.assertEqual([0x200000001], collision['actualTeams']['4']['guids'])
        self.assertEqual([0xffffffffffffffff], self.rows['max-team-and-member']['actualTeams']['4294967295']['guids'])

    def test_current_team_switch_event_and_member_query_are_native(self):
        row = self.rows['two-teams-high-guids']
        self.assertEqual(9007199254740993, row['rootHeroGuid'])
        # Root Hero differs from the selected Team's sole member; prefix does not assert membership.
        self.assertEqual([9223372036854775809], row['switches'][0]['members'])
        for step in row['switches']:
            self.assertEqual([] if step['fromTeam'] == step['toTeam'] else [{'id': 364, 'boxedBoolean': None}], step['events'])
            self.assertIn('0x151a9e080', step['change']['nativeEntries'])
            self.assertIn('0x151a9bd20', step['getter']['nativeEntries'])
            if step['members']:
                self.assertTrue({'0x14e9b2c60', '0x156f6f510'} <= set(step['consumer']['nativeEntries']))
                self.assertEqual('0x151a9ed62', step['consumer']['stop'])

    def test_missing_team_and_member_paths_do_not_invent_avatar(self):
        missing = self.rows['missing-current-team']
        self.assertEqual((7, [], [], None, '0x620000100'),
                         (missing['switches'][0]['toTeam'], missing['switches'][0]['members'],
                          missing['switches'][0]['lookedUpGuids'], missing['switches'][0]['matchedGuid'], missing['switches'][0]['consumer']['stop']))
        query = next(x for x in missing['queries'] if x['teamId'] == 7)
        self.assertFalse(query['present']); self.assertIsNone(query['name'])
        step = self.rows['missing-members-before-valid']['switches'][0]
        self.assertEqual([999, 0, 9223372036854775809], step['lookedUpGuids'])
        self.assertEqual(9223372036854775809, step['matchedGuid'])
        for step in self.rows['no-owned-avatar-members']['switches']:
            self.assertIsNone(step['matchedGuid'])
            self.assertEqual('0x620000100', step['consumer']['stop'])

    def test_zero_identifiers_and_explicit_zero_overwrite(self):
        row = self.rows['zero-team-and-member']
        self.assertEqual([0], row['actualTeams']['0']['guids'])
        self.assertIsNone(row['switches'][0]['matchedGuid'])
        self.assertEqual([0], row['switches'][0]['lookedUpGuids'])
        self.assertEqual('0x620000100', row['switches'][0]['consumer']['stop'])
        self.assertEqual([0, 9007199254740993], row['avatarIndexChecks'])
        zero = self.rows['explicit-zero-overwrites']
        self.assertEqual(0, zero['rootHeroGuid'])
        self.assertEqual(0, zero['switches'][0]['toTeam'])
        self.assertNotIn('LFMIIOCDKNK', zero['readerResult'])
        self.assertNotIn('OCIKDBJPOAJ', zero['readerResult'])

    def test_wire_duplicate_key_and_member_dedup_are_separate(self):
        duplicate = self.rows['duplicate-team-key-replaces']
        self.assertEqual({'name': 'New', 'guids': [9223372036854775809]}, duplicate['actualTeams']['1'])
        self.assertEqual([9223372036854775809, 9223372036854775809], duplicate['readerResult']['CNCCIILAJNJ']['1']['PIDCNPCLBCE'])
        mixed = self.rows['packed-unpacked-members']
        self.assertEqual([9007199254740993, 0, 9223372036854775809], mixed['actualTeams']['1']['guids'])
        self.assertEqual('\u961f\u4f0d\0\U0001f680', self.rows['empty-team-and-unicode-name']['actualTeams']['2']['name'])
        self.assertEqual({'name': '', 'guids': []}, self.rows['empty-team-and-unicode-name']['actualTeams']['1'])

    def test_native_growth_preserves_full_qwords(self):
        row = self.rows['managed-list-growth-nine']
        self.assertEqual([4, 8, 16, 4], row['arrayAllocations'])
        row = self.rows['managed-list-capacity-64']
        self.assertEqual([4, 8, 16, 32, 64, 4], row['arrayAllocations'])
        self.assertEqual([0x100000000 + i for i in range(64)], row['actualTeams']['1']['guids'])
        self.assertEqual({8}, {x['width'] for x in row['memberStores']})
        self.assertEqual({'0x151a9eb63', '0x157ee208a'}, {x['source'] for x in row['memberStores']})

    def test_full_native_prefix_and_precise_unknown_stop(self):
        native = {hex(x) for x in NATIVE}
        for row in self.rows.values():
            self.assertEqual(row['expectedTeams'], row['actualTeams'])
            self.assertEqual('0x1507cf4a1', row['dispatch']['stop'])
            self.assertEqual('0x1507cf606', row['rootSelection']['entry'])
            self.assertEqual('0x1507cf627', row['rootSelection']['stop'])
            self.assertTrue({'0x1507cc240', '0x1507cee50', '0x151a9d430', '0x151a9ea20', '0x157ee1fe0'} <= set(row['dispatch']['nativeEntries']))
            traces = [row['dispatch'], row['rootSelection']]
            traces += [step[key] for step in row['switches'] for key in ('change', 'getter', 'consumer')]
            for trace in traces: self.assertFalse(native & set(trace['models']))
        self.assertEqual('movups xmm0, xmmword ptr [rbp + 0x2b8]', self.report['declarations']['nativeSites']['0x151a9ed62']['instruction'])

    def test_errors_distinguish_native_throw_boundary_and_fixture(self):
        errors = {x['name']: x for x in self.report['rejections']}
        self.assertEqual(9, len(errors))
        self.assertEqual('native-reader-error-helper', errors['negative-team-map-length']['scope'])
        self.assertEqual('native-reader-error-helper', errors['team-recursion-limit']['scope'])
        self.assertEqual('explicit-input-model-or-capacity', errors['member-capacity-65']['scope'])
        self.assertEqual('native-null-error-helper-boundary', errors['null-typed-input']['scope'])
        self.assertEqual('0x1404c2be0', errors['null-typed-input']['trace']['stop'])


if __name__ == '__main__':
    unittest.main()
