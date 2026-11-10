import copy
import hashlib
import tempfile
import unittest
from pathlib import Path

from tools.client72_d02_old_avatar_coverage import analyze, pinned_bytes


def fixture():
    source = {'begin': 4, 'end': 7, 'sha256': 'ab' * 32}
    tables = []
    for name, key in [('Avatar', 101), ('AvatarSkillDepot', 201), ('AvatarSkill', 301)]:
        tables.append({'name': name, 'count': 1, 'cursor': 7, 'payloadBytes': 7,
                       'rows': [{'key': key, 'source': copy.deepcopy(source)}]})
    avatar = {'fields': {'ownerId': 2**40, 'avatarId': 101, 'skillDepotId': 201, 'level': 100,
                         'promoteLevel': 6, 'talentIdList': [0, 401], 'proudSkillList': [501],
                         'skillLevelMap': {'301': 10, '302': 1}}, 'missing': []}
    snapshot = {'player': {'fields': {'_id': 2**40, 'teamManager': {'missing': [],
                    'currentRegularTeam': {'fields': {'avatars': [101, 102]}, 'missing': []}}}, 'missing': []},
                'avatars': [avatar]}
    report = {'native': {'parsedTables': tables}, 'javaProjectionFragments': [
        {'table': 'Avatar', 'key': 101, 'source': copy.deepcopy(source), 'fragmentOnly': True}]}
    return snapshot, report, {table['name']: 1 for table in tables}


class CoverageTest(unittest.TestCase):
    def test_nonzero_conditions_active_and_missing_references(self):
        snapshot, report, counts = fixture()
        result = analyze(snapshot, report, counts)
        avatar = result['avatars'][0]
        self.assertEqual((avatar['level'], avatar['promoteLevel']), (100, 6))
        self.assertTrue(avatar['activeRegularTeam'])
        self.assertTrue(avatar['avatarBridgeFragmentAvailable'])
        self.assertEqual(avatar['conditionalSaved'], {'talentIdList': [401], 'proudSkillList': [501]})
        self.assertIsNotNone(avatar['skillLevelReferences'][0]['source'])
        self.assertIsNone(avatar['skillLevelReferences'][1]['source'])
        self.assertEqual(result['missingActiveAvatarIds'], [102])
        self.assertFalse(result['resourceReady'])

    def test_uid_isolation(self):
        snapshot, report, counts = fixture()
        snapshot['avatars'][0]['fields']['ownerId'] += 1
        with self.assertRaisesRegex(ValueError, 'UID isolation'):
            analyze(snapshot, report, counts)

    def test_missing_saved_avatar_and_depot_are_reported(self):
        snapshot, report, counts = fixture()
        snapshot['avatars'][0]['fields'].update(avatarId=103, skillDepotId=203)
        avatar = analyze(snapshot, report, counts)['avatars'][0]
        self.assertIsNone(avatar['avatarSource'])
        self.assertIsNone(avatar['depotSource'])

    def test_table_and_fragment_drift_refused(self):
        snapshot, report, counts = fixture()
        report['native']['parsedTables'][0]['rows'][0]['source']['end'] += 1
        with self.assertRaises(ValueError):
            analyze(snapshot, report, counts)
        snapshot, report, counts = fixture()
        report['javaProjectionFragments'][0]['source']['sha256'] = '00' * 32
        with self.assertRaisesRegex(ValueError, 'fragment/source'):
            analyze(snapshot, report, counts)

    def test_hash_drift_refused(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'input'
            path.write_bytes(b'original')
            sha = hashlib.sha256(b'original').hexdigest()
            self.assertEqual(pinned_bytes(path, sha), b'original')
            path.write_bytes(b'changed')
            with self.assertRaisesRegex(ValueError, 'SHA-256 mismatch'):
                pinned_bytes(path, sha)


if __name__ == '__main__':
    unittest.main()
