"""Portable launcher regressions using disposable files and no real services."""

import base64
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest


@unittest.skipUnless(os.name == "nt", "Windows portable launcher")
class PortableLauncherTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="lunagc portable ")
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        (self.root / "tools").mkdir()
        repo = Path(__file__).resolve().parents[1]
        shutil.copy2(repo / "tools/local_windows.ps1", self.root / "tools/local_windows.ps1")
        helper = repo / "tools/portable_settings.ps1"
        if helper.exists():
            shutil.copy2(helper, self.root / "tools/portable_settings.ps1")
        (self.root / "build.gradle").write_text("version = '7.1.0'\n", encoding="utf-8")
        for name in ("jdk-17-fixture/bin/java.exe", "mongodb-fixture/bin/mongod.exe"):
            path = self.root / "local/runtime" / name
            path.parent.mkdir(parents=True)
            path.write_bytes(b"fixture, never executed")
        (self.root / "LunaGC-7.1.0.jar").write_bytes(b"fixture")
        resource = self.root / "resources/ExcelBinOutput/AvatarExcelConfigData.json"
        resource.parent.mkdir(parents=True)
        resource.write_text("[]", encoding="utf-8")
        (self.root / "local/data").mkdir()
        (self.root / "gradlew.bat").write_text(
            '@echo %* > "%~dp0build-args.txt"\n@exit /b 0\n', encoding="utf-8")
        self.config = {
            "folderStructure": {"resources": str(self.root / "resources"),
                                "data": str(self.root / "local/data")},
            "databaseInfo": {key: {"connectionUri": "mongodb://127.0.0.1:27017",
                                   "collection": "grasscutter"} for key in ("game", "server")},
            "server": {"http": {"bindPort": 8088}, "game": {"bindPort": 22101}},
        }
        self.env = {k: v for k, v in os.environ.items()
                    if not k.startswith("LUNAGC_") and k != "GAME_PATH"}
        self.env["LUNAGC_CLIENT_PATCH_GUARD"] = "0"
        self.listener = "function Get-NetTCPConnection { param($State,$LocalPort,$ErrorAction) }; "

    def run_mode(self, mode):
        (self.root / "config.json").write_text(json.dumps(self.config), encoding="utf-8")
        path = str(self.root / "tools/local_windows.ps1").replace("'", "''")
        # Any attempted process launch is a failure: fixtures must never run services.
        source = (
            "$ProgressPreference='SilentlyContinue'; "
            + self.listener +
            "function Start-Process { throw 'UNEXPECTED_PROCESS_LAUNCH' }; "
            f"try {{ & '{path}' -Mode {mode}; exit $LASTEXITCODE }} "
            "catch { [Console]::Error.WriteLine($_.Exception.Message); exit 1 }"
        )
        encoded = base64.b64encode(source.encode("utf-16le")).decode("ascii")
        return subprocess.run(
            ["powershell.exe", "-NoProfile", "-OutputFormat", "Text", "-ExecutionPolicy", "Bypass",
             "-EncodedCommand", encoded], env=self.env, capture_output=True, text=True, timeout=30)

    def test_custom_jar_name_is_passed_to_gradle(self):
        self.env["LUNAGC_JAR"] = "LunaGC-test.jar"
        result = self.run_mode("build")
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertIn("-PjarFilename=LunaGC-test", (self.root / "build-args.txt").read_text())

    def test_default_jar_name_follows_gradle_version(self):
        (self.root / "build.gradle").write_text("version = '7.2.0'\n", encoding="utf-8")
        result = self.run_mode("build")
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertIn("-PjarFilename=LunaGC-7.2.0", (self.root / "build-args.txt").read_text())

    def test_jar_path_cannot_escape_checkout(self):
        self.env["LUNAGC_JAR"] = "../other.jar"
        result = self.run_mode("build")
        self.assertNotEqual(0, result.returncode)
        self.assertFalse((self.root / "build-args.txt").exists())

    def assert_start_rejected(self, message):
        result = self.run_mode("start")
        self.assertNotEqual(0, result.returncode)
        self.assertIn(message, result.stderr)
        self.assertNotIn("UNEXPECTED_PROCESS_LAUNCH", result.stderr)

    def test_copied_resource_path_is_rejected_before_starting_database(self):
        self.config["folderStructure"]["resources"] = str(self.root.parent / "old-checkout/resources")
        self.assert_start_rejected("resources")

    def test_copied_data_path_is_rejected_before_starting_database(self):
        self.config["folderStructure"]["data"] = str(self.root.parent / "old-checkout/local/data")
        self.assert_start_rejected("data")

    def test_config_cannot_silently_connect_to_another_database(self):
        self.config["databaseInfo"]["game"]["connectionUri"] = "mongodb://example.invalid:27017"
        self.assert_start_rejected("connectionUri")

    def test_valid_local_paths_reach_process_start(self):
        result = self.run_mode("start")
        self.assertIn("UNEXPECTED_PROCESS_LAUNCH", result.stderr)

    def test_explicit_external_resources_are_allowed(self):
        with tempfile.TemporaryDirectory(prefix="lunagc resources ") as resource_root:
            resources = Path(resource_root)
            (resources / "ExcelBinOutput").mkdir()
            (resources / "ExcelBinOutput/AvatarExcelConfigData.json").write_text("[]")
            self.config["folderStructure"]["resources"] = resource_root
            self.env["LUNAGC_RESOURCES_DIR"] = resource_root
            result = self.run_mode("start")
            self.assertIn("UNEXPECTED_PROCESS_LAUNCH", result.stderr)

    def test_path_with_same_prefix_is_not_part_of_checkout(self):
        self.config["folderStructure"]["data"] = str(self.root) + "-other/local/data"
        self.assert_start_rejected("outside this checkout")

    def test_missing_data_is_not_silently_created(self):
        missing = self.root / "missing-save"
        self.config["folderStructure"]["data"] = str(missing)
        self.assert_start_rejected("directory is missing")
        self.assertFalse(missing.exists())

    def mock_running_jar(self, path):
        command = ('java.exe -jar "' + str(path) + '" -noconsole').replace("'", "''")
        self.listener = (
            "function Get-NetTCPConnection { param($State,$LocalPort,$ErrorAction) "
            "[pscustomobject]@{OwningProcess=1234} }; "
            "function Get-CimInstance { param($ClassName,$Filter) "
            f"[pscustomobject]@{{Name='java.exe';ProcessId=1234;CommandLine='{command}'}} }}; "
        )

    def test_old_jar_in_same_checkout_is_recognized_during_version_switch(self):
        self.mock_running_jar(self.root / "LunaGC-7.1.0.jar")
        self.env["LUNAGC_JAR"] = "LunaGC-next.jar"
        self.assert_start_rejected("Stop LunaGC before switching")

    def test_another_checkout_is_never_reused(self):
        self.mock_running_jar(self.root.parent / "another/LunaGC-7.1.0.jar")
        self.assert_start_rejected("belongs to another application")

    def test_modified_jar_is_not_reported_as_healthy(self):
        self.mock_running_jar(self.root / "LunaGC-7.1.0.jar")
        (self.root / "local/running.json").write_text(json.dumps({
            "pid": 1234, "jar": str(self.root / "LunaGC-7.1.0.jar"),
            "jarSHA256": "old-artifact-hash"}), encoding="utf-8")
        result = self.run_mode("status")
        self.assertNotEqual(0, result.returncode)
        self.assertIn("Running JAR differs", result.stderr)


if __name__ == "__main__":
    unittest.main()
