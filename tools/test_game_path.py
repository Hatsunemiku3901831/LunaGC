"""Resolve the client consistently from Task and detached PowerShell helpers."""

import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest


@unittest.skipUnless(os.name == "nt", "Windows game path resolver")
class GamePathTest(unittest.TestCase):
    def setUp(self):
        repo = Path(__file__).resolve().parents[1]
        (repo / "debug").mkdir(exist_ok=True)
        temporary = tempfile.TemporaryDirectory(prefix="game path ", dir=repo / "debug")
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name).resolve()
        (self.root / "tools").mkdir()
        shutil.copy2(repo / "tools/game_path.ps1", self.root / "tools/game_path.ps1")
        self.env = os.environ.copy()
        self.env.pop("GAME_PATH", None)

    def game(self, name):
        game = self.root / name
        plugins = game / "YuanShen_Data/Plugins"
        plugins.mkdir(parents=True)
        (plugins / "Astrolabe.dll").write_bytes(b"fixture")
        return str(game)

    def resolve(self):
        return subprocess.run(
            ["powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File",
             str(self.root / "tools/game_path.ps1")],
            cwd=self.root.parent, env=self.env, text=True, capture_output=True, timeout=15,
        )

    def test_direct_helper_reads_local_env_before_env(self):
        local = self.game("local client")
        base = self.game("base client")
        (self.root / ".env.local").write_text(f'GAME_PATH="{local}"\n', encoding="utf-8")
        (self.root / ".env").write_text(f"GAME_PATH={base}\n", encoding="utf-8")
        result = self.resolve()
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(local, result.stdout.strip())

    def test_explicit_environment_wins(self):
        explicit = self.game("explicit client")
        local = self.game("local client")
        self.env["GAME_PATH"] = explicit
        (self.root / ".env.local").write_text(f"GAME_PATH={local}\n", encoding="utf-8")
        result = self.resolve()
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(explicit, result.stdout.strip())

    def test_blank_local_path_falls_back_to_env_file(self):
        base = self.game("base client")
        (self.root / ".env.local").write_text("GAME_PATH=\n", encoding="utf-8")
        (self.root / ".env").write_text(f"  GAME_PATH = '{base}'\n", encoding="utf-8")
        result = self.resolve()
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(base, result.stdout.strip())

    def test_env_content_is_not_executed(self):
        game = self.game("client")
        marker = self.root / "must-not-exist"
        (self.root / ".env").write_text(
            f"IGNORED=$(New-Item '{marker}')\nGAME_PATH={game}\n", encoding="utf-8"
        )
        result = self.resolve()
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertFalse(marker.exists())
        self.assertEqual(game, result.stdout.strip())

    def test_invalid_configured_client_is_not_silently_replaced(self):
        self.env["GAME_PATH"] = str(self.root / "missing client")
        (self.root / ".env").write_text(f"GAME_PATH={self.game('valid client')}\n", encoding="utf-8")
        result = self.resolve()
        self.assertNotEqual(0, result.returncode)
        self.assertIn("missing client", result.stderr)


if __name__ == "__main__":
    unittest.main()
