"""Independent exact-input, tree-coverage, loss and reference controls for R3 audit."""

import copy
import hashlib
import json
import struct
import tempfile
import unittest
from pathlib import Path

from tools import client72_gadget_ability_candidate_audit as audit


class GadgetAbilityCandidateAuditTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.report = audit.audit()
        cls.gadget_bytes = (audit.ROOT / audit.RUN / "requests/Q05/body.bin").read_bytes()
        cls.ability_bytes = (audit.ROOT / audit.RUN / "requests/Q06/body.bin").read_bytes()
        cls.gadgets = json.loads(cls.gadget_bytes)
        cls.abilities = json.loads(cls.ability_bytes)
        cls.declarations = audit.verify_java_fields(audit.verify_inputs(audit.ROOT))

    def test_fixed_complete_raw_sizes_and_git_blobs(self):
        for data, size, blob in [
            (self.gadget_bytes, 361168, "ce2b119da8d74e43eeffc93308bfe91919aeee5b"),
            (self.ability_bytes, 3745, "7b1bc8ca167a40b39539d887661619e1ba005dc3"),
        ]:
            self.assertEqual(size, len(data))
            self.assertEqual(blob, hashlib.sha1(b"blob %d\0" % size + data).hexdigest())
        self.assertEqual(34, len(self.gadgets))
        self.assertEqual(3, len(self.abilities))

    def test_full_selected_tree_has_no_unclassified_or_duplicate_leaf(self):
        # Separate traversal from producer: compare full source leaf pointer sets.
        def enumerate_pointers(value, path):
            pending = [(value, path)]
            result = set()
            while pending:
                item, pointer = pending.pop()
                if isinstance(item, dict) and item:
                    pending.extend((child, pointer + "/" + str(key).replace("~", "~0").replace("/", "~1"))
                                   for key, child in item.items())
                elif isinstance(item, list) and item:
                    pending.extend((child, pointer + "/" + str(index)) for index, child in enumerate(item))
                else:
                    result.add(pointer)
            return result

        for source, root, entries in [
            (self.gadgets["Weapon_Sword_Metal"], "/Weapon_Sword_Metal", self.report["selectedConfigFields"]),
            (self.abilities[0]["Default"], "/0/Default", self.report["selectedAbilityFields"]),
        ]:
            actual = [row["pointer"] for row in entries]
            self.assertEqual(len(actual), len(set(actual)))
            self.assertEqual(enumerate_pointers(source, root), set(actual))
        self.assertEqual(255, len(self.report["selectedConfigFields"]))
        self.assertEqual(5, len(self.report["selectedAbilityFields"]))

    def test_exact_seven_config_fields_keep_nonzero_values_and_types(self):
        rows = {row["pointer"].removeprefix("/Weapon_Sword_Metal/"): row
                for row in self.report["selectedConfigFields"] if row["status"] == "declared-java-field"}
        expected = {
            "abilities/0/abilityName": ("String", "Avatar_Weapon_Metal"),
            "abilities/0/abilityID": ("String", "Metal"),
            "combat/property/HP": ("float", 9999999.0),
            "combat/property/isInvincible": ("boolean", True),
            "combat/property/isLockHP": ("boolean", True),
            "combat/property/useCreatorProperty": ("boolean", True),
            "combat/die/dieForceDisappearTime": ("double", 0.009999999776482582),
        }
        self.assertEqual(set(expected), set(rows))
        for path, (kind, value) in expected.items():
            self.assertEqual(kind, rows[path]["java"]["type"])
            self.assertEqual(value, rows[path]["predictedJavaValue"])
        self.assertEqual("00000040e17a843f", struct.pack("<d", rows["combat/die/dieForceDisappearTime"]["value"]).hex())

    def test_unknown_ability_object_retained_and_not_called_empty(self):
        rows = self.report["selectedAbilityFields"]
        unknown = {row["pointer"]: row["value"] for row in rows if row["status"] == "unmapped-java-input"}
        self.assertEqual({
            "/0/Default/LKLPDIMKLKI/elementDurability": 100,
            "/0/Default/LKLPDIMKLKI/minus_one": -1,
            "/0/Default/LKLPDIMKLKI/IAKPBCLBNKF": -1,
            "/0/Default/LKLPDIMKLKI/isLimitedProperties": True,
        }, unknown)
        self.assertFalse(self.report["boundaries"]["emptyAbilityApproved"])
        self.assertFalse(self.report["boundaries"]["unknownFieldsOmissionApproved"])

    def test_defense_and_common_are_not_silently_aliased(self):
        rows = {row["pointer"]: row for row in self.report["selectedConfigFields"]}
        self.assertEqual("unmapped-java-input", rows["/Weapon_Sword_Metal/combat/property/defense"]["status"])
        self.assertEqual(999999, rows["/Weapon_Sword_Metal/combat/property/defense"]["value"])
        self.assertTrue(all(row["status"] == "unmapped-java-input" for pointer, row in rows.items()
                            if pointer.startswith("/Weapon_Sword_Metal/common/")))
        self.assertEqual(0.0, self.report["javaDefaultObservations"][0]["predictedJavaValue"])
        self.assertIsNone(self.report["javaDefaultObservations"][1]["predictedJavaValue"])

    def test_reference_closure_is_exact_name_and_missing_reference_fails(self):
        self.assertTrue(self.report["references"]["selectedExactNameClosure"])
        self.assertEqual([], self.report["references"]["wholeFileMissing"])
        mutated = copy.deepcopy(self.gadgets)
        mutated["Weapon_Sword_Metal"]["abilities"][0]["abilityName"] = "Avatar_Weapon_Metal_MISSING"
        result = audit.compare(mutated, self.abilities, self.declarations)
        self.assertFalse(result["references"]["selectedExactNameClosure"])
        self.assertEqual(["Avatar_Weapon_Metal_MISSING"], result["references"]["selectedMissing"])

    def test_empty_reference_list_does_not_close_selected_contract(self):
        mutated = copy.deepcopy(self.gadgets)
        mutated["Weapon_Sword_Metal"]["abilities"] = []
        self.assertFalse(audit.compare(mutated, self.abilities, self.declarations)["references"]["selectedExactNameClosure"])

    def test_missing_or_duplicate_metal_definition_is_rejected(self):
        for source in [self.abilities[1:], self.abilities + [self.abilities[0]]]:
            with self.assertRaisesRegex(ValueError, "exactly one complete Metal"):
                audit.compare(self.gadgets, source, self.declarations)

    def test_unknown_new_field_remains_unknown(self):
        mutated = copy.deepcopy(self.gadgets)
        mutated["Weapon_Sword_Metal"]["futureUnknown"] = {"big": 18355363353325362624, "items": [0, -1]}
        rows = audit.compare(mutated, self.abilities, self.declarations)["selectedConfigFields"]
        extra = {row["pointer"]: row for row in rows if "/futureUnknown/" in row["pointer"]}
        self.assertEqual(3, len(extra))
        self.assertEqual(18355363353325362624, extra["/Weapon_Sword_Metal/futureUnknown/big"]["value"])
        self.assertTrue(all(row["status"] == "unmapped-java-input" for row in extra.values()))

    def test_input_byte_drift_rejected_before_parsing(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "AGENTS.md").write_bytes(b"different input")
            with self.assertRaisesRegex(ValueError, "SHA-256 mismatch: AGENTS.md"):
                audit.audit(root)

    def test_pinned_java_declaration_drift_fails(self):
        files = audit.verify_inputs(audit.ROOT)
        path = audit.CONFIG + "fields/ConfigCombatProperty.java"
        files[path] = files[path].replace(b"float defence;", b"float defense;")
        with self.assertRaisesRegex(ValueError, "Java declaration absent"):
            audit.verify_java_fields(files)

    def test_no_execution_or_parent_ready_claim_and_deterministic(self):
        self.assertTrue(all(value is False for value in self.report["boundaries"].values()))
        self.assertEqual(self.report, audit.audit())
        self.assertEqual(13, len(self.report["javaCallSites"]))


if __name__ == "__main__":
    unittest.main()
