"""Exercise the Compose launcher without touching the real Docker daemon."""

import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest


@unittest.skipUnless(os.name == "nt", "Windows Compose launcher")
class RuntimeTest(unittest.TestCase):
    def setUp(self):
        repo = Path(__file__).resolve().parents[1]
        debug = repo / "debug"
        debug.mkdir(exist_ok=True)
        self.directory = tempfile.TemporaryDirectory(prefix="compose runtime ", dir=debug)
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name).resolve()
        (self.root / "tools").mkdir()
        shutil.copy2(repo / "tools/runtime.ps1", self.root / "tools/runtime.ps1")
        (self.root / "compose.yaml").write_text("name: lunagc\nservices: {}\n", encoding="utf-8")
        fake_bin = self.root / "fake bin"
        fake_bin.mkdir()
        fake_python = fake_bin / "docker.py"
        fake_python.write_text(
            "import json, os, pathlib, sys\n"
            "with open(os.environ['RUNTIME_CALL_LOG'], 'a', encoding='utf-8') as f:\n"
            "    f.write(json.dumps({'args': sys.argv[1:], 'cwd': os.getcwd()}) + '\\n')\n"
            "sys.exit(19 if os.environ.get('RUNTIME_FAIL_COMMAND') in sys.argv[1:] else 0)\n",
            encoding="utf-8",
        )
        (fake_bin / "docker.cmd").write_text(
            f'@"{sys.executable}" "{fake_python}" %*\r\n', encoding="utf-8"
        )
        self.call_log = self.root / "calls.jsonl"
        self.guard_log = self.root / "guard.jsonl"
        (self.root / "tools/client_patch_guard.ps1").write_text(
            "param([string] $Mode)\n"
            "$calls = @(Get-Content -LiteralPath $env:RUNTIME_CALL_LOG)\n"
            "@{ mode = $Mode; composeCalls = $calls.Count } | ConvertTo-Json -Compress | "
            "Add-Content -LiteralPath $env:RUNTIME_GUARD_LOG\n"
            "if ($env:RUNTIME_GUARD_FAIL -eq '1') { exit 7 }\n",
            encoding="utf-8",
        )
        self.env = os.environ.copy()
        self.env["PATH"] = str(fake_bin) + os.pathsep + self.env["PATH"]
        self.env["RUNTIME_CALL_LOG"] = str(self.call_log)
        self.env["RUNTIME_GUARD_LOG"] = str(self.guard_log)
        self.env.pop("RUNTIME_FAIL_COMMAND", None)
        self.env.pop("RUNTIME_GUARD_FAIL", None)
        self.env.pop("LUNAGC_CLIENT_PATCH_GUARD", None)

    def launch(self, mode, fail=None):
        if fail:
            self.env["RUNTIME_FAIL_COMMAND"] = fail
        result = subprocess.run(
            ["powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File",
             str(self.root / "tools/runtime.ps1"), "-Mode", mode],
            cwd=self.root.parent, env=self.env, capture_output=True, text=True, timeout=30,
        )
        calls = [json.loads(line) for line in self.call_log.read_text("utf-8").splitlines()]
        for call in calls:
            args = call["args"]
            self.assertEqual("compose", args[0])
            self.assertEqual("lunagc", args[args.index("--project-name") + 1])
            self.assertEqual(str(self.root), args[args.index("--project-directory") + 1])
            self.assertEqual(str(self.root / "compose.yaml"), args[args.index("--file") + 1])
            self.assertEqual(self.root, Path(call["cwd"]))
            self.assertNotIn("down", args)
            self.assertNotIn("--volumes", args)
            self.assertNotIn("-v", args)
        return result, [call["args"][7:] for call in calls]

    def test_start_builds_then_detaches_and_waits_for_health(self):
        result, calls = self.launch("start")
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertEqual(["build", "server"], calls[0])
        self.assertEqual(2, len(calls))
        self.assertEqual("up", calls[1][0])
        self.assertIn("--detach", calls[1])
        self.assertIn("--wait", calls[1])
        self.assertIn("--wait-timeout", calls[1])
        self.assertEqual(["mongo", "server"], calls[1][-2:])

    def test_build_failure_does_not_start_services(self):
        result, calls = self.launch("start", fail="build")
        self.assertNotEqual(0, result.returncode)
        self.assertEqual([["build", "server"]], calls)

    def test_stop_only_stops_the_game_service(self):
        result, calls = self.launch("stop")
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertEqual([["stop", "server"]], calls)

    def test_status_reports_stopped_services_too(self):
        result, calls = self.launch("status")
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertEqual([["ps", "--all"]], calls)

    def guard_calls(self):
        if not self.guard_log.exists():
            return []
        return [json.loads(line) for line in self.guard_log.read_text("utf-8-sig").splitlines()]

    def test_start_starts_patch_guard_after_services_are_healthy(self):
        result, _ = self.launch("start")
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertEqual([{"mode": "start", "composeCalls": 2}], self.guard_calls())

    def test_unhealthy_compose_does_not_start_patch_guard(self):
        result, _ = self.launch("start", fail="up")
        self.assertNotEqual(0, result.returncode)
        self.assertEqual([], self.guard_calls())

    def test_patch_guard_failure_is_reported(self):
        self.env["RUNTIME_GUARD_FAIL"] = "1"
        result, calls = self.launch("start")
        self.assertNotEqual(0, result.returncode)
        self.assertEqual(2, len(calls))

    def test_server_only_mode_can_disable_patch_guard(self):
        self.env["LUNAGC_CLIENT_PATCH_GUARD"] = "0"
        result, _ = self.launch("start")
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertEqual([], self.guard_calls())

    def test_status_also_reports_patch_guard(self):
        result, _ = self.launch("status")
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertEqual([{"mode": "status", "composeCalls": 1}], self.guard_calls())

    def test_server_stop_keeps_patch_guard_for_late_client_exit(self):
        result, _ = self.launch("stop")
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertEqual([], self.guard_calls())


if __name__ == "__main__":
    unittest.main()
