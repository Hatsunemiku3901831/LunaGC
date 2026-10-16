import copy
import importlib.util
import json
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location("dungeon_import", ROOT / "tools/import_reference_dungeon_drops.py")
IMPORTER = importlib.util.module_from_spec(spec)
spec.loader.exec_module(IMPORTER)
FIELDS = ("rankLevel", "equipType", "mainPropDepotId", "appendPropDepotId", "appendPropNum", "maxLevel")


def shape(item):
    return tuple(item.get(field, 0) for field in FIELDS)


class ReferenceDungeonRepairTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        fixture = IMPORTER.read(ROOT / "tools/tests/fixtures/dungeon_reference_repairs_7cc148d1.json")
        cls.reference = {row["dungeonId"]: row for row in fixture["rows"]}
        cls.ordinary = {row["dungeonId"]: row for row in IMPORTER.read(ROOT / "src/main/resources/defaults/data/DungeonDrop.json")}
        cls.plan = IMPORTER.read(ROOT / "tools/reference_dungeon_drop_repairs.json")["repairs"]
        cls.resources = {
            name: {row["id"]: row for row in IMPORTER.read(ROOT / f"resources/ExcelBinOutput/{name}ExcelConfigData.json")}
            for name in ("Dungeon", "RewardPreview", "DisplayItem", "Material", "Reliquary", "Weapon")
        }
        cls.items = {key: value for name in ("Material", "Reliquary", "Weapon") for key, value in cls.resources[name].items()}
        cls.catalogs = {"reference": cls.reference, "ordinary": cls.ordinary}

    def check_repair(self, specification):
        identifier = specification["dungeonId"]
        row = copy.deepcopy(self.reference[identifier])
        before = copy.deepcopy(row)
        expected_issues = IMPORTER.problems(row, self.resources["Dungeon"], self.resources["RewardPreview"], self.resources["DisplayItem"], self.items)
        self.assertEqual(specification["expectedIssues"], expected_issues)
        result, trace = IMPORTER.repair_row(row, specification, self.resources, self.catalogs)
        self.assertEqual([], IMPORTER.problems(result, self.resources["Dungeon"], self.resources["RewardPreview"], self.resources["DisplayItem"], self.items), f"Dungeon {identifier}")
        self.assertEqual(before, row, "The pinned input was mutated")
        virtual = lambda value: [group for group in value["drops"] if group["items"] and set(group["items"]).issubset({102, 105, 202})]
        self.assertEqual(virtual(before), virtual(result))
        self.assertEqual(specification["action"], trace["action"])
        if specification["action"] == "fill-empty":
            index = specification["groupIndex"]
            source = self.ordinary[specification["sourceDungeonId"]]["drops"][index]
            self.assertEqual(source["items"], result["drops"][index]["items"])
            for offset, group in enumerate(before["drops"]):
                if offset != index:
                    self.assertEqual(group, result["drops"][offset])
            self.assertEqual({k: v for k, v in before["drops"][index].items() if k != "items"},
                             {k: v for k, v in result["drops"][index].items() if k != "items"})
        else:
            source = self.reference[specification["sourceDungeonId"]]
            physical = lambda value: [group for group in value["drops"] if any(i in self.resources["Reliquary"] for i in group["items"])]
            original_groups, new_groups = physical(source), physical(result)
            self.assertEqual(len(original_groups), len(new_groups))
            for old, new in zip(original_groups, new_groups):
                self.assertEqual({k: v for k, v in old.items() if k != "items"}, {k: v for k, v in new.items() if k != "items"})
                self.assertEqual([shape(self.items[i]) for i in old["items"]], [shape(self.items[i]) for i in new["items"]])
                self.assertEqual(len(new["items"]), len(set(new["items"])))

    def test_an_unknown_action_is_rejected(self):
        plan = dict(self.plan[0], action="unknown")
        with self.assertRaises(ValueError):
            IMPORTER.repair_row(copy.deepcopy(self.reference[plan["dungeonId"]]), plan, self.resources, self.catalogs)

    def test_upstream_issue_drift_is_rejected(self):
        plan = dict(self.plan[0], expectedIssues=["different-source-issue"])
        with self.assertRaises(ValueError):
            IMPORTER.repair_row(copy.deepcopy(self.reference[plan["dungeonId"]]), plan, self.resources, self.catalogs)

    def test_a_different_prototype_level_is_rejected(self):
        context = dict(self.resources, Dungeon=dict(self.resources["Dungeon"]))
        context["Dungeon"][5125] = dict(context["Dungeon"][5125], showLevel=90)
        with self.assertRaises(ValueError):
            IMPORTER.repair_row(copy.deepcopy(self.reference[4480]), self.plan[0], context, self.catalogs)

    def target_candidate(self):
        group = next(group for group in self.reference[5125]["drops"] if any(i in self.resources["Reliquary"] for i in group["items"]))
        original = self.items[group["items"][0]]
        return next(item for item in self.resources["Reliquary"].values() if item.get("setId", item.get("setID")) == 10001 and shape(item) == shape(original))

    def test_an_ambiguous_target_item_is_rejected(self):
        context = dict(self.resources, Reliquary=dict(self.resources["Reliquary"]))
        candidate = self.target_candidate()
        context["Reliquary"][999999999] = dict(candidate, id=999999999)
        with self.assertRaises(ValueError):
            IMPORTER.repair_row(copy.deepcopy(self.reference[4480]), self.plan[0], context, self.catalogs)

    def test_a_missing_target_item_is_rejected(self):
        context = dict(self.resources, Reliquary=dict(self.resources["Reliquary"]))
        del context["Reliquary"][self.target_candidate()["id"]]
        with self.assertRaises(ValueError):
            IMPORTER.repair_row(copy.deepcopy(self.reference[4480]), self.plan[0], context, self.catalogs)

    def test_empty_group_quantity_drift_is_rejected(self):
        plan = next(row for row in self.plan if row["dungeonId"] == 5001)
        ordinary = copy.deepcopy(self.ordinary)
        ordinary[5001]["drops"][3]["counts"] = [999]
        with self.assertRaises(ValueError):
            IMPORTER.repair_row(copy.deepcopy(self.reference[5001]), plan, self.resources, dict(self.catalogs, ordinary=ordinary))

    def test_repaired_data_is_independent_of_the_input(self):
        plan = next(row for row in self.plan if row["dungeonId"] == 4686)
        row = copy.deepcopy(self.reference[4686])
        original = copy.deepcopy(row)
        result, _ = IMPORTER.repair_row(row, plan, self.resources, self.catalogs)
        result["drops"][0]["counts"][0] += 1
        self.assertEqual(original, row)

    def test_empty_group_accepts_explicit_uniform_item_weights(self):
        plan = next(row for row in self.plan if row["dungeonId"] == 5001)
        ordinary = copy.deepcopy(self.ordinary)
        ordinary[5001]["drops"][3]["itemProbabilities"] = None
        try:
            result, _ = IMPORTER.repair_row(copy.deepcopy(self.reference[5001]), plan, self.resources,
                                           dict(self.catalogs, ordinary=ordinary))
        except Exception as error:
            self.fail(f"A valid uniform item-weight definition was rejected: {error}")
        self.assertIsNone(result["drops"][3]["itemProbabilities"])


def repair_case(specification):
    def test(self):
        self.check_repair(specification)
    return test


PLAN = IMPORTER.read(ROOT / "tools/reference_dungeon_drop_repairs.json")["repairs"]
for case in PLAN:
    setattr(ReferenceDungeonRepairTest, f"test_repair_{case['dungeonId']}", repair_case(case))


if __name__ == "__main__":
    unittest.main()
