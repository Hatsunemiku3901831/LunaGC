"""Exercise the portable trust path entry point without starting any real app."""
import base64
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest


def quote(value):
    return "'" + str(value).replace("'", "''") + "'"


@unittest.skipUnless(os.name == 'nt', 'Windows portable launcher')
class TrustLauncherTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix='cn72 trust launcher ')
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.repo = Path(__file__).resolve().parents[1]
        (self.root / 'tools').mkdir()
        for name in ('local_windows.ps1', 'portable_settings.ps1', 'game_path.ps1'):
            shutil.copy2(self.repo / 'tools' / name, self.root / 'tools' / name)
        (self.root / 'build.gradle').write_text("version = '7.2.0'\n", 'utf8')
        self.game = self.root / 'game'
        (self.game / 'YuanShen_Data/Plugins').mkdir(parents=True)
        (self.game / 'YuanShen_Data/Plugins/Astrolabe.dll').write_bytes(b'never loaded')
        (self.game / 'config.ini').write_text('[General]\ngame_version=7.2.0\n', 'utf8')
        self.env = {k: v for k, v in os.environ.items()
                    if not k.startswith('LUNAGC_') and k != 'GAME_PATH'}
        self.env['GAME_PATH'] = str(self.game)
        self.bundle = self.root / 'local/migration72/trust/client72-trust.json'

    def run_ps(self, source):
        encoded = base64.b64encode(source.encode('utf-16le')).decode('ascii')
        return subprocess.run(['powershell.exe', '-NoProfile', '-OutputFormat', 'Text',
                               '-ExecutionPolicy', 'Bypass', '-EncodedCommand', encoded],
                              env=self.env, cwd=self.root.parent, capture_output=True,
                              text=True, timeout=30)

    def resolve(self):
        return self.run_ps("$ErrorActionPreference='Stop'; "
                           f". {quote(self.root / 'tools/portable_settings.ps1')}; "
                           f"Resolve-ClientTrustBundlePath {quote(self.root)}")

    def create_bundle_file(self):
        self.bundle.parent.mkdir(parents=True, exist_ok=True)
        # These tests check path supply; material cryptography belongs to the native loader.
        self.bundle.write_text('{}', 'ascii')

    def test_missing_default_bundle_is_reported_without_creating_it(self):
        result = self.resolve()
        self.assertNotEqual(0, result.returncode)
        self.assertIn('trust bundle', result.stderr)
        self.assertFalse(self.bundle.exists())

    def test_default_path_is_resolved_against_checkout_with_spaces(self):
        self.create_bundle_file()
        result = self.resolve()
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(str(self.bundle), result.stdout.strip())

    def test_relative_override_is_resolved_against_checkout_not_shell_directory(self):
        self.create_bundle_file()
        self.env['LUNAGC_TRUST_BUNDLE'] = 'local/migration72/trust/client72-trust.json'
        result = self.resolve()
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(str(self.bundle), result.stdout.strip())

    def test_explicit_absolute_override_is_preserved(self):
        self.create_bundle_file()
        self.env['LUNAGC_TRUST_BUNDLE'] = str(self.bundle)
        result = self.resolve()
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(str(self.bundle), result.stdout.strip())

    def test_directory_is_not_a_bundle_file(self):
        self.bundle.mkdir(parents=True)
        result = self.resolve()
        self.assertNotEqual(0, result.returncode)
        self.assertIn('trust bundle', result.stderr)

    def test_resolved_environment_reaches_a_new_powershell_child(self):
        self.create_bundle_file()
        result = self.run_ps(
            "$ErrorActionPreference='Stop'; "
            f". {quote(self.root / 'tools/portable_settings.ps1')}; "
            f"$env:LUNAGC_TRUST_BUNDLE=Resolve-ClientTrustBundlePath {quote(self.root)}; "
            "& powershell.exe -NoProfile -Command '[Console]::WriteLine($env:LUNAGC_TRUST_BUNDLE)'; "
            "exit $LASTEXITCODE")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(str(self.bundle), result.stdout.strip())

    def test_patch_entry_passes_the_absolute_default_path_to_its_child(self):
        self.create_bundle_file()
        marker = self.root / 'patch-child-env.txt'
        (self.root / 'tools/patch_game.ps1').write_text(
            "param([string] $Mode)\n"
            "if ($Mode -ne 'apply') { throw 'Unexpected patch action' }\n"
            f"[IO.File]::WriteAllText({quote(marker)}, $env:LUNAGC_TRUST_BUNDLE)\n", 'utf8')
        result = self.run_ps(
            f"& {quote(self.root / 'tools/local_windows.ps1')} -Mode patch; exit $LASTEXITCODE")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(str(self.bundle), marker.read_text('utf8'))

    def test_client_affecting_modes_reject_missing_bundle_before_service_or_patch(self):
        for mode in ('start', 'play', 'patch'):
            with self.subTest(mode=mode):
                result = self.run_ps(
                    "$ErrorActionPreference='Stop'; "
                    "function Start-Process { throw 'UNEXPECTED_PROCESS_LAUNCH' }; "
                    f"& {quote(self.root / 'tools/local_windows.ps1')} -Mode {mode}; exit $LASTEXITCODE")
                self.assertNotEqual(0, result.returncode)
                self.assertIn('trust bundle', result.stderr)
                self.assertNotIn('UNEXPECTED_PROCESS_LAUNCH', result.stderr)

    def test_version_guard_still_runs_before_missing_bundle(self):
        (self.game / 'config.ini').write_text('[General]\ngame_version=7.3.0\n', 'utf8')
        result = self.run_ps(
            f"& {quote(self.root / 'tools/local_windows.ps1')} -Mode play; exit $LASTEXITCODE")
        self.assertNotEqual(0, result.returncode)
        self.assertIn('Client version 7.3', result.stderr)
        self.assertNotIn('trust bundle', result.stderr)


if __name__ == '__main__':
    unittest.main()
