"""Isolated patch recovery tests; only disposable fake client files are touched."""

import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import time
import unittest


@unittest.skipUnless(os.name == "nt", "Windows client patch guard")
class ClientPatchGuardTest(unittest.TestCase):
    def setUp(self):
        repo = Path(__file__).resolve().parents[1]
        self.directory = tempfile.TemporaryDirectory(prefix="patch guard ", dir=repo / "debug")
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        tools = self.root / "tools"
        tools.mkdir()
        shutil.copy2(repo / "tools/client_patch_guard_lib.ps1", tools)
        self.game = self.root / "fake client with spaces"
        self.plugins = self.game / "YuanShen_Data/Plugins"
        self.plugins.mkdir(parents=True)
        for name in ("Astrolabe.dll", "AccountPlatNative.dll"):
            (self.plugins / name).write_text("stock", encoding="ascii")
        ext = self.root / "patch/target/release/ext.dll"
        ext.parent.mkdir(parents=True)
        ext.write_text("fake patch", encoding="ascii")
        (tools / "game_path.ps1").write_text(
            "if (-not $env:GAME_PATH) { [Console]::Error.WriteLine('Client not installed'); exit 2 }; "
            "if ($env:GAME_PATH -eq 'invalid') { [Console]::Error.WriteLine('Invalid configured path'); exit 1 }; "
            "$env:GAME_PATH\n", encoding="utf-8"
        )
        (tools / "patch_game.ps1").write_text(
            "param([string] $Mode)\n"
            "$p = Join-Path $env:GAME_PATH 'YuanShen_Data/Plugins'\n"
            "Add-Content -LiteralPath (Join-Path $env:GAME_PATH 'calls.txt') -Value $Mode\n"
            "if ($Mode -eq 'status') {\n"
            "  if ((Get-Content -LiteralPath (Join-Path $p 'Astrolabe.dll')) -eq 'patched' "
            "-and -not (Test-Path (Join-Path $p 'Astrolabe.dll.lunagc-live'))) { exit 0 }; exit 1\n"
            "}\n"
            "if (Test-Path (Join-Path $env:GAME_PATH 'fail.apply')) { exit 23 }\n"
            "if (Test-Path (Join-Path $env:GAME_PATH 'ineffective.apply')) { exit 0 }\n"
            "Set-Content -LiteralPath (Join-Path $p 'Astrolabe.dll') -Value 'patched'\n"
            "Remove-Item -LiteralPath (Join-Path $p 'Astrolabe.dll.lunagc-live') -ErrorAction SilentlyContinue\n"
            "exit 0\n",
            encoding="utf-8",
        )
        self.wrapper = tools / "client_patch_guard.ps1"
        self.wrapper.write_text(
            "param([string] $Mode = 'status', [string] $GamePath, [string] $InstanceId)\n"
            ". (Join-Path $PSScriptRoot 'client_patch_guard_lib.ps1')\n"
            "function Get-ClientGameProcesses {\n"
            "  if (Test-Path (Join-Path $script:FixtureGame 'game.running')) { [pscustomobject]@{ Id = 42 } }\n"
            "}\n"
            "$script:FixtureGame = $GamePath\n"
            "Invoke-ClientPatchGuard -Mode $Mode -GamePath $GamePath -InstanceId $InstanceId "
            "-Repo (Split-Path $PSScriptRoot -Parent) -EntryScript $PSCommandPath\n",
            encoding="utf-8",
        )
        self.env = os.environ.copy()
        self.env.pop("GAME_PATH", None)
        self.addCleanup(self.stop)

    def run_guard(self, mode, include_game=True, host="powershell"):
        args = [host, "-NoProfile", "-ExecutionPolicy", "Bypass", "-File",
                str(self.wrapper), "-Mode", mode]
        if include_game:
            args += ["-GamePath", str(self.game)]
        return subprocess.run(args, cwd=self.root.parent, env=self.env,
                              capture_output=True, text=True, timeout=45,
                              encoding="utf-8" if host == "pwsh" else None, errors="replace")

    def stop(self):
        if self.wrapper.exists():
            self.run_guard("stop")

    def calls(self):
        path = self.game / "calls.txt"
        return path.read_text("utf-8-sig").splitlines() if path.exists() else []

    def read_state(self):
        paths = list((self.root / "debug/client-patch").glob("*/status.json"))
        self.assertEqual(1, len(paths))
        return json.loads(paths[0].read_text("utf-8-sig"))

    def assert_ok(self, result):
        errors = "\n".join(path.read_text("utf-8-sig", errors="replace")
                           for path in self.root.glob("debug/client-patch/*/*.err.log"))
        self.assertEqual(0, result.returncode, result.stdout + result.stderr + errors)

    def test_ready_client_is_checked_without_rewriting(self):
        (self.plugins / "Astrolabe.dll").write_text("patched", encoding="ascii")
        self.assert_ok(self.run_guard("check"))
        self.assertEqual(["status"], self.calls())
        self.assertEqual("ready", self.read_state()["State"])

    @unittest.skipUnless(shutil.which("pwsh"), "PowerShell 7 is not installed")
    def test_check_from_powershell_seven_uses_windows_powershell_child(self):
        (self.plugins / "Astrolabe.dll").write_text("patched", encoding="ascii")
        self.assert_ok(self.run_guard("check", host="pwsh"))
        self.assertEqual(["status"], self.calls())

    @unittest.skipUnless(shutil.which("pwsh"), "PowerShell 7 is not installed")
    def test_powershell_seven_recognizes_and_reuses_windows_watcher_heartbeat(self):
        self.assert_ok(self.run_guard("start"))
        original = self.read_state()
        result = self.run_guard("status", host="pwsh")
        self.assert_ok(result)
        self.assertTrue(json.loads(result.stdout)["Watching"])
        self.assert_ok(self.run_guard("start", host="pwsh"))
        self.assertEqual(original["InstanceId"], self.read_state()["InstanceId"])
        self.assertEqual(original["Pid"], self.read_state()["Pid"])

    def test_stranded_patch_is_recovered_and_revalidated(self):
        (self.plugins / "Astrolabe.dll.lunagc-live").write_text("patched", encoding="ascii")
        self.assert_ok(self.run_guard("check"))
        self.assertEqual(["status", "apply", "status"], self.calls())
        self.assertFalse((self.plugins / "Astrolabe.dll.lunagc-live").exists())
        self.assertEqual("ready", self.read_state()["State"])

    def test_running_game_skips_every_patch_operation(self):
        (self.game / "game.running").touch()
        self.assert_ok(self.run_guard("check"))
        self.assertEqual([], self.calls())
        self.assertEqual("game-running", self.read_state()["State"])
        self.assertEqual("stock", (self.plugins / "Astrolabe.dll").read_text("ascii"))

    def test_apply_failure_propagates_and_records_error(self):
        (self.game / "fail.apply").touch()
        result = self.run_guard("check")
        self.assertNotEqual(0, result.returncode)
        self.assertEqual(["status", "apply"], self.calls())
        self.assertEqual("error", self.read_state()["State"])
        self.assertIn("23", self.read_state()["Message"])

    def test_apply_success_is_not_accepted_without_passing_status(self):
        (self.game / "ineffective.apply").touch()
        self.assertNotEqual(0, self.run_guard("check").returncode)
        self.assertEqual(["status", "apply", "status"], self.calls())
        self.assertEqual("error", self.read_state()["State"])

    def test_missing_build_is_reported_without_building_or_applying(self):
        (self.root / "patch/target/release/ext.dll").unlink()
        self.assertNotEqual(0, self.run_guard("check").returncode)
        self.assertNotIn("apply", self.calls())
        self.assertIn("build:patch", self.read_state()["Message"])

    def test_start_without_any_client_skips_for_server_only_install(self):
        self.assert_ok(self.run_guard("start", include_game=False))
        self.assertEqual([], self.calls())
        self.assertFalse((self.root / "debug/client-patch").exists())

    def test_failed_initial_repair_does_not_claim_watcher_started(self):
        (self.game / "fail.apply").touch()
        self.assertNotEqual(0, self.run_guard("start").returncode)
        self.assertFalse(self.read_state()["Watching"])

    def test_invalid_configured_path_is_not_silently_skipped(self):
        self.env["GAME_PATH"] = "invalid"
        result = self.run_guard("start", include_game=False)
        self.assertNotEqual(0, result.returncode)
        self.assertIn("Configured game path is invalid", result.stderr)

    def test_watcher_survives_caller_and_reuses_single_instance(self):
        self.assert_ok(self.run_guard("start"))
        first = self.read_state()
        self.assertTrue(first["Watching"])
        self.assert_ok(self.run_guard("start"))
        second = self.read_state()
        self.assertEqual(first["InstanceId"], second["InstanceId"])
        self.assertEqual(first["Pid"], second["Pid"])
        count = len(self.calls())
        time.sleep(4)
        self.assertEqual(count, len(self.calls()), "idle watcher should not rehash DLLs each poll")
        # The starter has exited. Simulate a client dying with its DLL parked.
        (self.plugins / "Astrolabe.dll").write_text("stock", encoding="ascii")
        (self.plugins / "Astrolabe.dll.lunagc-live").write_text("patched", encoding="ascii")
        deadline = time.monotonic() + 15
        while (self.plugins / "Astrolabe.dll.lunagc-live").exists() and time.monotonic() < deadline:
            time.sleep(0.25)
        self.assertFalse((self.plugins / "Astrolabe.dll.lunagc-live").exists())
        self.assertEqual("patched", (self.plugins / "Astrolabe.dll").read_text("ascii").strip())
        self.assert_ok(self.run_guard("stop"))
        self.assertFalse(self.read_state()["Watching"])

    def test_watcher_recovers_after_game_exits_and_retries_failed_repair(self):
        (self.game / "game.running").touch()
        self.assert_ok(self.run_guard("start"))
        (self.game / "fail.apply").touch()
        (self.game / "game.running").unlink()
        deadline = time.monotonic() + 20
        while self.read_state()["State"] != "error" and time.monotonic() < deadline:
            time.sleep(0.25)
        self.assertEqual("error", self.read_state()["State"])
        (self.game / "fail.apply").unlink()
        deadline = time.monotonic() + 25
        while self.read_state()["State"] != "ready" and time.monotonic() < deadline:
            time.sleep(0.25)
        self.assertEqual("ready", self.read_state()["State"])
        self.assertGreaterEqual(self.calls().count("apply"), 2)

    def test_source_update_replaces_watcher_and_check_preserves_loaded_version(self):
        self.assert_ok(self.run_guard("start"))
        original = self.read_state()
        self.assertTrue(original["SourceStamp"])
        for path in (self.root / "tools/client_patch_guard_lib.ps1", self.wrapper):
            with path.open("a", encoding="utf-8") as source:
                source.write("\n# Fixture source update\n")
            self.assert_ok(self.run_guard("check"))
            checked = self.read_state()
            self.assertEqual(original["SourceStamp"], checked["SourceStamp"],
                             "check cannot claim the old watcher has loaded new code")
            self.assert_ok(self.run_guard("start"))
            updated = self.read_state()
            self.assertTrue(updated["Watching"])
            self.assertNotEqual(original["SourceStamp"], updated["SourceStamp"])
            self.assertNotEqual(original["InstanceId"], updated["InstanceId"])
            self.assert_ok(self.run_guard("start"))
            self.assertEqual(updated["InstanceId"], self.read_state()["InstanceId"])
            original = updated


if __name__ == "__main__":
    unittest.main()
