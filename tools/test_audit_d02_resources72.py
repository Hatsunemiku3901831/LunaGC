import copy
import unittest

from tools.audit_d02_resources72 import audit, audit_extended, FILES, EXTRA_FILES


class StartingAvatarAuditTest(unittest.TestCase):
    def setUp(self):
        self.tables = {
            FILES[0]: {10000005: {'initialWeapon': 11101, 'skillDepotId': 501},
                       10000007: {'initialWeapon': 11101, 'skillDepotId': 701}},
            FILES[1]: {i: {'skills': [10, 0], 'subSkills': [11]} for i in (501, 504, 701, 704)},
            FILES[2]: {10: {}, 11: {}}, FILES[3]: {11101: {'gadgetId': 50011101}}}

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

    def test_level_one_curves_promotion_talents_and_proud_groups(self):
        self.tables[FILES[1]][501]['talents'] = list(range(71, 77))
        self.tables[FILES[1]][501]['inherentProudSkillOpens'] = [
            {'proudSkillGroupId': 721}, {'proudSkillGroupId': 722}]
        self.tables[FILES[1]][501]['skills'] = [10, 12, 13, 14]
        for skill, group in zip((10, 12, 13, 14), (730, 731, 732, 739)):
            self.tables[FILES[2]][skill] = {'proudSkillGroupId': group}
        avatar_curve = [{'level': 1, 'curveInfos': [{'type': k, 'value': 1} for k in
                         ('GROW_CURVE_HP_S4', 'GROW_CURVE_ATTACK_S4')]}]
        weapon_curve = [{'level': 1, 'curveInfos': [{'type': k, 'value': 1} for k in
                         ('GROW_CURVE_ATTACK_101', 'GROW_CURVE_CRITICAL_101')]}]
        extra = dict(zip(EXTRA_FILES, [avatar_curve, [{'avatarPromoteId': 12}],
                     [{'talentId': x} for x in range(71, 77)],
                     [{'proudSkillId': x * 100 + 1, 'proudSkillGroupId': x}
                      for x in (721, 722, 730, 731, 732, 739)],
                     weapon_curve, [{'weaponPromoteId': 11101}]]))
        gadgets = {50011101: {}}
        checks = audit_extended(self.tables, extra, gadgets)
        self.assertEqual(21, len(checks))
        with self.assertRaisesRegex(ValueError, 'Missing starting weapon gadget'):
            audit_extended(self.tables, extra, {})
        extra[EXTRA_FILES[3]].pop()
        with self.assertRaisesRegex(ValueError, 'proud group level 1 739'):
            audit_extended(self.tables, extra, gadgets)
        extra[EXTRA_FILES[0]][0]['curveInfos'].pop()
        with self.assertRaisesRegex(ValueError, 'avatar level 1:GROW_CURVE_ATTACK_S4'):
            audit_extended(self.tables, extra, gadgets)


if __name__ == '__main__':
    unittest.main()
