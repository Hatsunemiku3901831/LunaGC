import copy
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

from tools.client72_d02_c_resource_load import project

REPORT = Path("local/migration72/d02-resume-20261108/r3-promote/main-third.json")


class ProjectionTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.report = json.loads(REPORT.read_text(encoding="utf-8"))

    def test_rows_and_zero_slots_preserved(self):
        rows = project(self.report)
        self.assertEqual(100, len(rows["AvatarCurve"]))
        for row in self.report["javaProjection"]["completeBirthPromoteInputRows"]:
            self.assertEqual([row["javaFields"]], rows[row["table"]])
        self.assertEqual([r["javaFields"] for r in self.report["javaProjection"]["completeAvatarCurveInputRows"]], rows["AvatarCurve"])

    def test_missing_curve_and_invalid_arithmetic_rejected(self):
        for mutation in ("missing", "arith", "duplicate"):
            report = copy.deepcopy(self.report)
            infos = report["javaProjection"]["completeAvatarCurveInputRows"][9]["javaFields"]["curveInfos"]
            if mutation == "missing": infos.pop()
            elif mutation == "arith": infos[0]["arith"] = "UNKNOWN"
            else: infos[1]["type"] = infos[0]["type"]
            with self.subTest(mutation=mutation), self.assertRaises(ValueError): project(report)

    def test_missing_level_and_lost_zero_slot_rejected(self):
        for mutation in ("level", "slot"):
            report = copy.deepcopy(self.report)
            p = report["javaProjection"]
            if mutation == "level": p["completeAvatarCurveInputRows"].pop()
            else: p["completeBirthPromoteInputRows"][0]["javaFields"]["costItems"].pop()
            with self.subTest(mutation=mutation), self.assertRaises(ValueError): project(report)

    def test_changed_report_is_rejected_before_output_creation(self):
        with tempfile.TemporaryDirectory(dir="local/migration72/d02-resume-20261110/c") as directory:
            root = Path(directory)
            altered = root / "altered.json"
            altered.write_bytes(REPORT.read_bytes() + b" ")
            output = root / "output"
            result = subprocess.run(
                [sys.executable, "tools/client72_d02_c_resource_load.py", "--report", str(altered), "--output", str(output)],
                capture_output=True, text=True,
            )
            self.assertNotEqual(0, result.returncode)
            self.assertIn("report SHA-256 mismatch", result.stderr)
            self.assertFalse(output.exists())


if __name__ == "__main__": unittest.main()
