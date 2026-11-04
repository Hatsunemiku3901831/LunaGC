"""Synthetic installer manifests; never read or mutate a real client."""
import hashlib
import json
from pathlib import Path
import tempfile
import unittest

from tools.verify_client_manifest import verify_manifest, safe_member


class ManifestTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)

    def manifest(self, rows):
        path = self.root / "pkg_version"
        path.write_text("\n".join(json.dumps(row) for row in rows), encoding="utf-8")
        return path

    def row(self, name="data/a.bin", content=b"abc"):
        return dict(remoteName=name, fileSize=len(content), md5=hashlib.md5(content).hexdigest())

    def test_checks_digest_not_only_size_and_preserves_input(self):
        path = self.root / "data/a.bin"
        path.parent.mkdir()
        path.write_bytes(b"xyz")
        result = verify_manifest(self.root, self.manifest([self.row()]))
        self.assertEqual("hash-mismatch", result["entries"][0]["status"])
        self.assertEqual(b"xyz", path.read_bytes())

    def test_full_valid_and_leading_zero_md5(self):
        # Some shipped manifests render MD5 as a variable-width hex integer.
        content = next(str(i).encode() for i in range(100) if hashlib.md5(str(i).encode()).hexdigest().startswith("0"))
        (self.root / "data").mkdir()
        (self.root / "data/a.bin").write_bytes(content)
        row = self.row(content=content)
        row["md5"] = row["md5"].lstrip("0")
        result = verify_manifest(self.root, self.manifest([row]))
        self.assertEqual("matched", result["entries"][0]["status"])
        self.assertEqual(1, result["counts"]["matched"])

    def test_missing_size_and_invalid_rows_are_distinct(self):
        (self.root / "short").write_bytes(b"a")
        result = verify_manifest(self.root, self.manifest([
            self.row("absent"), self.row("short"), self.row("../outside"),
            dict(remoteName="bad", fileSize=-1, md5="z"),
        ]))
        self.assertEqual(["missing", "size-mismatch", "invalid-entry", "invalid-entry"],
                         [entry["status"] for entry in result["entries"]])

    def test_duplicate_windows_path_fails(self):
        result = verify_manifest(self.root, self.manifest([self.row("Data/A"), self.row("data/a")]))
        self.assertEqual("duplicate-path", result["entries"][1]["status"])

    def test_rejects_windows_escape_stream_and_trailing_dot(self):
        for name in ("../x", "/x", "C:/x", "C:x", "\\\\server\\x", "a:stream", "a/../x", "a./x", "a /x"):
            with self.subTest(name=name), self.assertRaises(ValueError):
                safe_member(self.root, name)

    def test_truncated_json_is_reported(self):
        path = self.manifest([self.row()])
        with path.open("a") as stream:
            stream.write("\n{broken")
        result = verify_manifest(self.root, path)
        self.assertEqual("invalid-entry", result["entries"][-1]["status"])

    def test_empty_manifest_is_not_complete(self):
        result = verify_manifest(self.root, self.manifest([]))
        self.assertFalse(result["complete"])


if __name__ == "__main__":
    unittest.main()
