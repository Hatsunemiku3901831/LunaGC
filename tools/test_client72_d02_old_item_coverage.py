import hashlib
import tempfile
import unittest
from pathlib import Path

from tools import client72_d02_old_item_coverage as m


def item(item_id, count=1, deleted=False, **extra):
    fields = {"itemId": item_id, "count": count, "deleted": deleted, "level": 20,
              "promoteLevel": 1, "equipCharacter": 42}
    fields.update(extra)
    return {"fields": fields, "missing": []}


def target(keys):
    return {"tables": [{"name": "Weapon", "count": len(keys), "rows": [{"key": k} for k in keys]}]}


class CoverageTest(unittest.TestCase):
    def test_active_inactive_and_key_only(self):
        rows = [item(111, count=2), item(222, count=0), item(333, deleted=True), item(111, level=40)]
        report = m.analyze({"items": rows}, target([111, 999]), {"Weapon": [{"id": 111}]})
        self.assertEqual((4, 2, 2), tuple(report["summary"][k] for k in ("savedRows", "activeRows", "inactiveRows")))
        self.assertEqual((1, 2), tuple(report["summary"][k] for k in ("targetWeaponMatchedIds", "targetWeaponMatchedRows")))
        self.assertEqual([1, 2], [r["rowIndex"] for r in report["inactiveAudit"]])
        self.assertEqual(rows[1], report["inactiveAudit"][0]["projection"])
        self.assertEqual([20, 40], [x["value"] for x in report["groups"][0]["distribution"]["level"]])
        self.assertEqual("present", report["groups"][0]["candidateStatus"])

    def test_missing_fields_and_absent_candidate_are_retained(self):
        row = item(444)
        del row["fields"]["equipCharacter"]
        row["missing"] = ["equipCharacter"]
        report = m.analyze({"items": [row]}, target([]), {"Material": []})
        self.assertEqual({"equipCharacter": 1}, report["summary"]["missingFields"])
        self.assertEqual("unknown", report["groups"][0]["candidateStatus"])
        self.assertEqual("<missing>", report["groups"][0]["distribution"]["equipCharacter"][0]["value"])
        row["missing"] = []
        with self.assertRaises(ValueError):
            m.analyze({"items": [row]}, target([]), {})

    def test_duplicate_candidate_and_multiple_families_are_ambiguous(self):
        report = m.analyze({"items": [item(7), item(8)]}, target([]),
                           {"Weapon": [{"id": 7}, {"id": 7}, {"id": 8}], "Material": [{"id": 8}]})
        self.assertEqual(["ambiguous", "ambiguous"], [g["candidateStatus"] for g in report["groups"]])
        self.assertEqual(2, report["groups"][0]["candidateMatches"][0]["rows"])
        self.assertEqual(2, len(report["groups"][1]["candidateMatches"]))

    def test_duplicate_target_and_invalid_candidate_rejected(self):
        with self.assertRaises(ValueError):
            m.target_keys(target([3, 3]))
        with self.assertRaises(ValueError):
            m.candidate_index({"Material": [{}]})
        with self.assertRaises(ValueError):
            m.strict_json(b'{"id":1,"id":2}')

    def test_pin_rejects_sha_or_blob_drift(self):
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / "input"
            path.write_bytes(b"abc")
            sha = hashlib.sha256(b"abc").hexdigest()
            blob = hashlib.sha1(b"blob 3\0abc").hexdigest()
            self.assertEqual(b"abc", m.pinned(path, sha, blob))
            with self.assertRaises(ValueError):
                m.pinned(path, "0" * 64, blob)
            with self.assertRaises(ValueError):
                m.pinned(path, sha, "0" * 40)
            path.write_bytes(b"abd")
            with self.assertRaises(ValueError):
                m.pinned(path, sha, blob)

    def test_count_rules_and_large_values(self):
        rows = [item(2**40, count=2**35), item(12, count=-1), item(13, count=True)]
        report = m.analyze({"items": rows}, target([2**40]), {"Material": [{"id": 2**40}]})
        self.assertEqual(1, report["summary"]["activeRows"])
        self.assertEqual(2, report["summary"]["inactiveRows"])
        self.assertEqual(2**40, report["groups"][0]["itemId"])


if __name__ == "__main__":
    unittest.main()
