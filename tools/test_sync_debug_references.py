"""Reference retrieval contracts against real, isolated local Git repositories."""
import contextlib
import io
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

import sync_debug_references as sync


class ReferenceSyncTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix="lunagc-reference-sync-")
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name).resolve()
        self.origin = self.root / "source"
        self.origin.mkdir()
        self.git("init", self.origin)
        self.git("-C", self.origin, "config", "user.name", "Reference fixture")
        self.git("-C", self.origin, "config", "user.email", "fixture@example.invalid")
        self.write("README.md", "Reference fixture\n")
        self.write("pkg/core/stacks/refresh.go", "package stacks\n")
        self.write("internal/characters/fixture/skill.go", "package fixture\n")
        self.git("-C", self.origin, "add", ".")
        self.git("-C", self.origin, "commit", "-m", "fixture")
        self.head = self.git("-C", self.origin, "rev-parse", "HEAD")
        self.workspace = self.root / "workspace"
        self.workspace.mkdir()

    @staticmethod
    def git(*args):
        return subprocess.run(["git", *map(str, args)], check=True, capture_output=True,
                              encoding="utf-8").stdout.strip()

    def write(self, name, value):
        file = self.origin / name
        file.parent.mkdir(parents=True, exist_ok=True)
        file.write_text(value, encoding="utf-8")

    def checkout(self, name="gcsim"):
        target = self.workspace / "debug" / name
        self.git("clone", "--sparse", self.origin.as_posix(), target)
        self.git("-C", target, "sparse-checkout", "set", "pkg/core/stacks")
        return target

    def run_sync(self, *arguments, name="gcsim", url=None):
        with patch.object(sync, "ROOT", self.workspace), \
                patch.object(sync, "REFERENCES", {name: url or self.origin.as_posix()}), \
                patch.object(sync, "ADAPTED_SOURCES", {}), \
                patch.object(sys, "argv", ["sync_debug_references.py", *arguments]), \
                contextlib.redirect_stdout(io.StringIO()):
            sync.main()
        return json.loads((self.workspace / "debug/references.json").read_text(encoding="utf-8"))

    def assert_complete(self, target, manifest):
        self.assertTrue((target / "internal/characters/fixture/skill.go").is_file())
        self.assertEqual("false", self.git("-C", target, "config", "--bool", "--default",
                                            "false", "--get", "core.sparseCheckout"))
        self.assertEqual(self.head, self.git("-C", target, "rev-parse", "HEAD"))
        self.assertEqual("", self.git("-C", target, "status", "--porcelain"))
        self.assertIs(manifest["sources"][0].get("checkout_complete"), True)
        self.assertNotIn("checkout_scope", manifest["sources"][0])

    def test_new_reference_materializes_the_whole_tree(self):
        manifest = self.run_sync()
        self.assert_complete(self.workspace / "debug/gcsim", manifest)

    def test_existing_clean_sparse_reference_expands_without_changing_head(self):
        target = self.checkout()
        self.assertFalse((target / "internal/characters/fixture/skill.go").exists())
        manifest = self.run_sync()
        self.assert_complete(target, manifest)

    def test_dirty_sparse_reference_is_preserved_and_reported_as_incomplete(self):
        target = self.checkout()
        (target / "README.md").write_text("Local edit\n", encoding="utf-8")
        (target / "local-note.txt").write_text("Untracked note\n", encoding="utf-8")
        status = self.git("-C", target, "status", "--porcelain")
        with self.assertRaises(SystemExit) as failure:
            self.run_sync()
        self.assertEqual(1, failure.exception.code)
        self.assertEqual(self.head, self.git("-C", target, "rev-parse", "HEAD"))
        self.assertEqual(status, self.git("-C", target, "status", "--porcelain"))
        self.assertEqual("Local edit\n", (target / "README.md").read_text())
        self.assertEqual("Untracked note\n", (target / "local-note.txt").read_text())
        self.assertFalse((target / "internal/characters/fixture/skill.go").exists())
        manifest = json.loads((self.workspace / "debug/references.json").read_text())
        self.assertEqual(1, len(manifest["errors"]))
        self.assertIn("local changes", manifest["errors"][0]["error"])

    def test_manifest_only_keeps_sparse_checkout_and_explicitly_reports_it(self):
        target = self.checkout()
        manifest = self.run_sync("--manifest-only")
        self.assertIs(manifest["sources"][0].get("checkout_complete"), False)
        self.assertEqual(["pkg/core/stacks"], manifest["sources"][0]["checkout_scope"])
        self.assertFalse((target / "internal/characters/fixture/skill.go").exists())
        self.assertEqual(self.head, self.git("-C", target, "rev-parse", "HEAD"))

    def test_manifest_reports_sparse_scope_for_any_reference(self):
        target = self.checkout("alternate")
        manifest = self.run_sync("--manifest-only", name="alternate")
        self.assertIs(manifest["sources"][0].get("checkout_complete"), False)
        self.assertEqual(["pkg/core/stacks"], manifest["sources"][0]["checkout_scope"])
        self.assertFalse((target / "internal/characters/fixture/skill.go").exists())

    def test_wrong_origin_is_rejected_before_expansion(self):
        target = self.checkout()
        with self.assertRaises(SystemExit):
            self.run_sync(url=(self.root / "different-source").as_posix())
        self.assertFalse((target / "internal/characters/fixture/skill.go").exists())
        self.assertEqual(self.head, self.git("-C", target, "rev-parse", "HEAD"))
        self.assertEqual("", self.git("-C", target, "status", "--porcelain"))

    def test_manifest_only_reports_deleted_tracked_files_without_restoring_them(self):
        target = self.checkout()
        self.git("-C", target, "sparse-checkout", "disable")
        missing = target / "internal/characters/fixture/skill.go"
        missing.unlink()
        manifest = self.run_sync("--manifest-only")
        record = manifest["sources"][0]
        self.assertIs(record["checkout_complete"], False)
        self.assertIs(record["worktree_clean"], False)
        self.assertEqual(["internal/characters/fixture/skill.go"], record["missing_tracked_files"])
        self.assertFalse(missing.exists())

    def test_full_checkout_with_edits_keeps_content_and_records_dirty_provenance(self):
        target = self.checkout()
        self.git("-C", target, "sparse-checkout", "disable")
        local = target / "README.md"
        local.write_text("Locally reviewed reference\n", encoding="utf-8")
        manifest = self.run_sync("--manifest-only")
        self.assertIs(manifest["sources"][0]["checkout_complete"], True)
        self.assertIs(manifest["sources"][0]["worktree_clean"], False)
        self.assertEqual("Locally reviewed reference\n", local.read_text())
        self.assertEqual(self.head, self.git("-C", target, "rev-parse", "HEAD"))


if __name__ == "__main__":
    unittest.main()
