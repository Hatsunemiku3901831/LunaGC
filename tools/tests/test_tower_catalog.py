import importlib.util
import json
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location("tower_export", ROOT / "tools/export_tower_catalog.py")
EXPORT = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(EXPORT)


class TowerCatalogTest(unittest.TestCase):
    def test_nested_monster_table_is_not_split_at_positions(self):
        body = 'monsters = {{config_id=1, monster_id=2, pos={x=1,y=-2,z=3}, affix={6105}}, {config_id=2, monster_id=3}}\nsuites = {{monsters={1,2}}}'
        rows = list(EXPORT.entries(EXPORT.table(body, 'monsters')))
        self.assertEqual(2, len(rows))
        self.assertEqual({'x': 1., 'y': -2., 'z': 3.}, EXPORT.vector(rows[0], 'pos'))
        self.assertEqual(2, EXPORT.integer(rows[0], 'monster_id'))

    def test_catalogue_reproduces_checked_in_evidence(self):
        result = EXPORT.export(ROOT)
        committed = json.loads((ROOT / 'src/main/resources/abyss/catalog.json').read_text(encoding='utf8'))
        self.assertEqual(result, committed)

    def test_no_period_promoted_from_preview_only(self):
        catalog = json.loads((ROOT / 'src/main/resources/abyss/catalog.json').read_text(encoding='utf8'))
        for period in catalog['periods']:
            if period['status'] != 'scripted':
                self.assertTrue(period['missing'])
                continue
            self.assertEqual(5, len(period['floorIds']))
            for floor_id in period['floorIds']:
                floor = catalog['floors'][str(floor_id)]
                self.assertEqual([1, 2, 3], [c['chamber'] for c in floor['chambers']])
                for chamber in floor['chambers']:
                    scene = catalog['scenes'][str(chamber['sceneId'])]
                    self.assertFalse(scene['missing'])
                    self.assertTrue(any(g['monsters'] for g in scene['groups']))


if __name__ == '__main__':
    unittest.main()
