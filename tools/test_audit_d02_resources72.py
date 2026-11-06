import copy
import unittest

from tools.audit_d02_resources72 import audit, FILES


class StartingAvatarAuditTest(unittest.TestCase):
    def setUp(self):
        self.tables = {
            FILES[0]: {10000005: {'initialWeapon': 11101, 'skillDepotId': 501},
                       10000007: {'initialWeapon': 11101, 'skillDepotId': 701}},
            FILES[1]: {i: {'skills': [10, 0], 'subSkills': [11]} for i in (501, 504, 701, 704)},
            FILES[2]: {10: {}, 11: {}}, FILES[3]: {11101: {}}}

    def test_both_characters_and_zero_sentinel(self):
        rows = audit(self.tables)
        self.assertEqual([10000005, 10000007], [r['id'] for r in rows])
        self.assertEqual([10], rows[0]['depots'][0]['references']['skills'])
        self.assertEqual([11], rows[1]['depots'][1]['references']['subSkills'])

    def test_missing_skill_and_weapon_are_not_silent(self):
        data = copy.deepcopy(self.tables)
        del data[FILES[2]][11]
        with self.assertRaisesRegex(ValueError, 'Missing skills'):
            audit(data)
        data = copy.deepcopy(self.tables)
        del data[FILES[3]][11101]
        with self.assertRaisesRegex(ValueError, 'Missing starting weapon'):
            audit(data)

    def test_bad_depot_and_negative_reference_rejected(self):
        data = copy.deepcopy(self.tables)
        data[FILES[0]][10000005]['skillDepotId'] = 701
        with self.assertRaisesRegex(ValueError, 'Unexpected starting depot'):
            audit(data)
        data = copy.deepcopy(self.tables)
        data[FILES[1]][704]['subSkills'] = [-1]
        with self.assertRaisesRegex(ValueError, 'Invalid skill reference'):
            audit(data)


if __name__ == '__main__':
    unittest.main()
