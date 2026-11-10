import copy
import hashlib
import json
import unittest
from unittest.mock import patch

from tools import client72_d02_old_talent_refs as subject


def fixture():
    snapshot = {'player': {'missing': [], 'fields': {'_id': 1, 'teamManager': {
        'missing': [], 'currentRegularTeam': {'missing': [], 'fields': {'avatars': [10]}}}}},
        'avatars': [{'missing': [], 'fields': {'ownerId': 1, 'avatarId': 10,
                   'skillDepotId': 20, 'talentIdList': [0, 2, 2, 9]}},
                  {'missing': [], 'fields': {'ownerId': 1, 'avatarId': 11,
                   'skillDepotId': 21, 'talentIdList': [4]}}]}
    def sample(aid, depot, values):
        return {'avatarId': aid, 'key': depot, 'source': {'begin': 4, 'end': 8, 'sha256': 'a' * 64},
                'fields': [{'fieldIndex': 105363, 'value': depot},
                           {'fieldIndex': 105357, 'owner': 22284, 'offset': '0x28',
                            'type': {'kind': 'szarray', 'rank': 1, 'elementType': {
                                'kind': 'valuetype', 'typeDefinition': 58209}}, 'value': values}]}
    return snapshot, {'samples': [sample(10, 20, [2, 0, 2, 3]), sample(11, 21, [5])]}


class TalentReferencesTest(unittest.TestCase):
    def test_conditions_set_semantics_and_original_values(self):
        snapshot, report = fixture()
        result = subject.project(snapshot, report, 2)
        one, two = result['avatars']
        self.assertEqual(one['currentDepotUnlocked'], [0, 2])
        self.assertEqual(one['savedOutsideCurrentDepot'], [9])
        self.assertEqual(one['depotTalentValues'], [2, 0, 2, 3])
        self.assertEqual(one['savedTalentValues'], [0, 2, 2, 9])
        self.assertTrue(one['activeRegularTeam'])
        self.assertFalse(two['activeRegularTeam'])
        self.assertEqual(two['currentDepotUnlocked'], [])
        self.assertEqual(two['savedOutsideCurrentDepot'], [4])
        self.assertEqual(result['currentDepotTalentLookupKeys'], [0, 2])

    def test_order_independent_and_deterministic(self):
        snapshot, report = fixture()
        expected = subject.project(snapshot, report, 2)
        snapshot['avatars'].reverse()
        report['samples'].reverse()
        self.assertEqual(expected, subject.project(snapshot, report, 2))

    def test_missing_and_duplicate_rows_rejected(self):
        for change in ('missing_saved', 'missing_report', 'duplicate_saved', 'duplicate_report', 'duplicate_depot', 'wrong_join', 'foreign'):
            with self.subTest(change=change):
                snapshot, report = fixture()
                if change == 'missing_saved': snapshot['avatars'].pop()
                if change == 'missing_report': report['samples'].pop()
                if change == 'duplicate_saved': snapshot['avatars'][1] = copy.deepcopy(snapshot['avatars'][0])
                if change == 'duplicate_report': report['samples'][1] = copy.deepcopy(report['samples'][0])
                if change == 'duplicate_depot': report['samples'][1]['key'] = report['samples'][0]['key']
                if change == 'wrong_join': snapshot['avatars'][0]['fields']['skillDepotId'] = 99
                if change == 'foreign': snapshot['avatars'][0]['fields']['ownerId'] = 2
                with self.assertRaises(ValueError): subject.project(snapshot, report, 2)

    def test_missing_or_duplicate_typed_fields_and_mismatched_id(self):
        for change in ('missing', 'duplicate', 'id', 'type', 'source'):
            with self.subTest(change=change):
                snapshot, report = fixture()
                sample = report['samples'][0]
                if change == 'missing': sample['fields'].pop()
                if change == 'duplicate': sample['fields'].append(copy.deepcopy(sample['fields'][-1]))
                if change == 'id': sample['fields'][0]['value'] = 999
                if change == 'type': sample['fields'][1]['type']['rank'] = 2
                if change == 'source': sample['source']['sha256'] = 'x' * 64
                with self.assertRaises(ValueError): subject.project(snapshot, report, 2)

    def test_invalid_values_and_team(self):
        for value in (None, [None], [-1], [True], [0x80000000]):
            snapshot, report = fixture()
            report['samples'][0]['fields'][1]['value'] = value
            with self.subTest(value=value), self.assertRaises(ValueError): subject.project(snapshot, report, 2)
        snapshot, report = fixture()
        snapshot['player']['fields']['teamManager']['currentRegularTeam']['fields']['avatars'] = [999]
        with self.assertRaises(ValueError): subject.project(snapshot, report, 2)

    def test_pin_mismatch_rejected(self):
        with patch.object(subject, 'INPUTS', {'dependencies': (subject.INPUTS['dependencies'][0], '0' * 64)}):
            with self.assertRaisesRegex(ValueError, 'SHA-256 mismatch'): subject.read_inputs()

    def test_fixed_inputs_independent_intersection(self):
        inputs = subject.read_inputs()
        snapshot, report = inputs['dependencies'], inputs['depotReport']
        result = subject.project(snapshot, report)
        self.assertEqual(len(result['avatars']), 121)
        rows = {row['fields']['avatarId']: row['fields'] for row in snapshot['avatars']}
        samples = {row['avatarId']: row for row in report['samples']}
        active = snapshot['player']['fields']['teamManager']['currentRegularTeam']['fields']['avatars']
        all_lookup = set()
        for item in result['avatars']:
            aid = item['avatarId']
            original = rows[aid]
            decoded = samples[aid]
            values = next(field['value'] for field in decoded['fields'] if field['fieldIndex'] == 105357)
            expected = sorted({x for x in values if x in original['talentIdList']})
            outside = sorted({x for x in original['talentIdList'] if x not in values})
            self.assertEqual(item['currentDepotId'], original['skillDepotId'])
            self.assertEqual(item['currentDepotId'], decoded['key'])
            self.assertEqual(item['depotSource'], decoded['source'])
            self.assertEqual(item['currentDepotUnlocked'], expected)
            self.assertEqual(item['savedOutsideCurrentDepot'], outside)
            self.assertEqual(item['activeRegularTeam'], aid in active)
            all_lookup.update(expected)
        self.assertEqual(result['currentDepotTalentLookupKeys'], sorted(all_lookup))
        encoded = lambda x: (json.dumps(x, sort_keys=True, ensure_ascii=False, indent=2) + '\n').encode()
        self.assertEqual(hashlib.sha256(encoded(result)).digest(), hashlib.sha256(encoded(subject.project(snapshot, report))).digest())


if __name__ == '__main__':
    unittest.main()
