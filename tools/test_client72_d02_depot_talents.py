import copy
import unittest
from unittest.mock import patch

from tools import client72_d02_depot_talents as task


class DepotTalentsProjectionTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.inputs = task.read_inputs()

    def table(self, inputs):
        return next(row for row in inputs["mapping"]["native"]["parsedTables"] if row["name"] == "AvatarSkillDepot")

    def row(self, inputs, key=501):
        return next(row for row in self.table(inputs)["selected"] if row["key"] == key)

    def field(self, inputs, key=501):
        return next(row for row in self.row(inputs, key)["fields"] if row["fieldIndex"] == 105357)

    def test_real_lists_and_partial_boundary(self):
        result = task.project(self.inputs)
        self.assertTrue(result["partial"])
        self.assertFalse(result["completeAvatar"])
        self.assertFalse(result["resourceReady"])
        self.assertFalse(result["d02Complete"])
        self.assertEqual([[0] * 6, [0] * 6, [71, 72, 73, 74, 75, 76]],
                         [row["javaFields"]["talents"] for row in result["depotFragments"]])
        self.assertEqual([501, 701, 704], [row["id"] for row in result["mergedDepots"]])
        self.assertEqual(self.inputs["avatar"], result["birthAvatarInputs"])
        for row in result["mergedDepots"]:
            self.assertEqual({"id", "talents", "skills", "energySkill"}, set(row))

    def test_null_missing_and_invalid_java_int_values_rejected(self):
        for value in (None, [True], [1.5], [-1], [0x80000000], ["1"]):
            inputs = copy.deepcopy(self.inputs)
            self.field(inputs)["value"] = value
            with self.subTest(value=value), self.assertRaisesRegex(ValueError, "invalid talents list"):
                task.project(inputs)
        inputs = copy.deepcopy(self.inputs)
        del self.field(inputs)["value"]
        with self.assertRaises((ValueError, KeyError)):
            task.project(inputs)

    def test_empty_and_duplicate_elements_are_preserved(self):
        for values in ([], [0, 0, 0], [71, 71, 76]):
            inputs = copy.deepcopy(self.inputs)
            self.field(inputs)["value"] = values
            self.assertEqual(values, task.project(inputs)["depotFragments"][0]["javaFields"]["talents"])

    def test_wrong_typed_identity_or_missing_field_rejected(self):
        for mutation in ("owner", "index", "offset", "rank", "element"):
            inputs = copy.deepcopy(self.inputs)
            field = self.field(inputs)
            if mutation == "owner": field["owner"] = 1
            elif mutation == "index": field["fieldIndex"] = 1
            elif mutation == "offset": field["offset"] = "0x30"
            elif mutation == "rank": field["type"]["rank"] = 2
            else: field["type"]["elementType"]["typeDefinition"] = 1
            with self.subTest(mutation=mutation), self.assertRaises(ValueError):
                task.project(inputs)

    def test_source_span_duplicate_row_and_wrong_key_rejected(self):
        for mutation in ("source", "duplicate", "key", "payload"):
            inputs = copy.deepcopy(self.inputs)
            table = self.table(inputs)
            if mutation == "source": self.row(inputs)["source"]["begin"] += 1
            elif mutation == "duplicate": table["selected"].append(copy.deepcopy(self.row(inputs)))
            elif mutation == "key": self.row(inputs)["key"] = 500
            else: inputs["payload"] = b"changed"
            with self.subTest(mutation=mutation), self.assertRaises(ValueError):
                task.project(inputs)

    def test_bridge_same_source_and_exact_field_set_required(self):
        for mutation in ("source", "field", "id"):
            inputs = copy.deepcopy(self.inputs)
            row = next(row for row in inputs["mapping"]["javaProjectionFragments"]
                       if row["table"] == "AvatarSkillDepot" and row["key"] == 501)
            if mutation == "source": row["source"]["end"] += 1
            elif mutation == "field": row["javaFields"]["talents"] = []
            else: row["javaFields"]["id"] = 701
            with self.subTest(mutation=mutation), self.assertRaises(ValueError):
                task.project(inputs)

    def test_duplicate_nontarget_full_table_key_rejected(self):
        inputs = copy.deepcopy(self.inputs)
        rows = [row for row in self.table(inputs)["rows"] if row["key"] not in task.KEYS]
        rows[1]["key"] = rows[0]["key"]
        with self.assertRaisesRegex(ValueError, "complete-table duplicate or invalid key"):
            task.project(inputs)

    def test_report_sha_mismatch_rejected(self):
        inputs = dict(task.INPUTS)
        inputs["mapping"] = (inputs["mapping"][0], "0" * 64)
        with patch.object(task, "INPUTS", inputs), self.assertRaisesRegex(ValueError, "SHA-256 differs"):
            task.read_inputs()


if __name__ == "__main__":
    unittest.main()
