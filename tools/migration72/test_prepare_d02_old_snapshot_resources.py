import os
import unittest
from pathlib import Path
import prepare_d02_old_snapshot_resources as tool


class OldSnapshotResourcesTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.fixed, cls.source, _, _, cls.assets = tool.load_inputs(
            Path(os.environ['D02_RESOURCE_REPO']),
            tool.ROOT / 'local/instances/d02-old-restore/evidence')

    def test_preserves_cohorts_and_provisional_scope(self):
        rows, report = tool.build(self.fixed, dict(self.source), self.assets)
        self.assertEqual((121, 12254, 10727, 471), tuple(report['counts'][x] for x in ('avatars', 'itemsTotal', 'activeItems', 'equipped')))
        self.assertEqual({'Weapon': 121, 'Reliquary': 350}, report['counts']['equippedFamilyCounts'])
        self.assertEqual(12254, len(report['itemLedger']))
        self.assertEqual(121, len(report['avatarLedger']))
        self.assertEqual([{'kind': 'not-runtime-validated', 'detail': 'real ResourceLoader and Player/AvatarStorage/Inventory cold save/load have not run; candidate client asset identities are not target-verified'}], report['gaps'])
        skills = {x['id']: x for x in rows['AvatarSkill']}
        self.assertEqual((200, 100), (skills[11065]['specialEnergyMin'], skills[11065]['specialEnergyMax']))
        self.assertEqual((100, 50), (skills[11145]['specialEnergyMin'], skills[11145]['specialEnergyMax']))
        self.assertEqual(10, len(report['serverProvisional']))

    def test_missing_required_item_curve_and_config_are_reported(self):
        sources = dict(self.source)
        sources['Weapon'] = [x for x in sources['Weapon'] if x['id'] != 11101]
        sources['AvatarCurve'] = [x for x in sources['AvatarCurve'] if x['level'] != 100]
        assets = dict(self.assets)
        del assets['BinOutput/Avatar/ConfigAvatar_Ayaka.json']
        _, report = tool.build(self.fixed, sources, assets)
        kinds = {x['kind'] for x in report['gaps']}
        self.assertIn('item-family-missing-or-ambiguous', kinds)
        self.assertIn('missing-avatar-config', kinds)
        self.assertTrue(any(x['kind'] == 'missing-or-duplicate-row' and x['table'] == 'AvatarCurve' and x['key'] == 100 for x in report['gaps']))
        self.assertFalse(report['ready'])


if __name__ == '__main__':
    unittest.main()
