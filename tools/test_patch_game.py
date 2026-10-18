"""Exercise client patch recovery against synthetic DLLs, never the real client."""

import base64
import hashlib
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import time
import unittest


STOCK_KEY = (
    b"-----BEGIN PUBLIC KEY-----\n"
    b"MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDDvekdPMHN3AYhm/vktJT+YJr7cI5Dc"
    b"sNKqdsx5DZX0gDuWFuIjzdwButrIYPNmRJ1G8ybDIF7oDW2eEpm5sMbL9zs9ExXCdvqrn"
    b"51qELbqj0XxtMTIpaCHFSI50PfPpTFV9Xt/hmyVwokoOXFlAEgCn+QCgGs52bFoYMtyi+x"
    b"EQIDAQAB\n-----END PUBLIC KEY-----"
)


def ps_quote(value):
    return "'" + str(value).replace("'", "''") + "'"


def ps_command(source):
    return ["powershell.exe", "-NoProfile", "-OutputFormat", "Text", "-ExecutionPolicy", "Bypass",
            "-EncodedCommand", base64.b64encode(source.encode("utf-16le")).decode("ascii")]


@unittest.skipUnless(os.name == "nt", "Windows client patcher")
class PatchGameTest(unittest.TestCase):
    def setUp(self):
        repo = Path(__file__).resolve().parents[1]
        (repo / "debug").mkdir(exist_ok=True)
        self.directory = tempfile.TemporaryDirectory(prefix="patch fixture ", dir=repo / "debug")
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name).resolve()
        (self.root / "tools").mkdir()
        shutil.copy2(repo / "tools/patch_game.ps1", self.root / "tools/patch_game.ps1")
        self.game = self.root / "fake client"
        self.plugins = self.game / "YuanShen_Data/Plugins"
        self.plugins.mkdir(parents=True)
        (self.game / "YuanShen.exe").write_bytes(b"fixture, not executable")
        (self.root / "tools/game_path.ps1").write_text(
            "Write-Output " + ps_quote(self.game), encoding="utf-8")
        self.ext = self.root / "patch/target/release/ext.dll"
        self.ext.parent.mkdir(parents=True)
        self.ext.write_bytes(b"MZ fixture patch Astrolabe_orig.dll latest")
        key = self.root / "src/main/resources/keys/passport_1024.der"
        key.parent.mkdir(parents=True)
        shutil.copy2(repo / "src/main/resources/keys/passport_1024.der", key)
        self.ast = self.plugins / "Astrolabe.dll"
        self.plat = self.plugins / "AccountPlatNative.dll"
        self.ast_stock = b"MZ fixture original Astrolabe v1"
        self.plat_stock = (b"MZ fixture original SDK v1\0https://passport-api.mihoyo.com\0"
                           b"https://pre-passport-api.mihoyo.com\0" + STOCK_KEY)
        self.ast.write_bytes(self.ast_stock)
        self.plat.write_bytes(self.plat_stock)
        self.running = self.root / "running-client.txt"

    def command(self, mode, recover_stranded=False):
        # A process fixture is scoped to this child shell. The copied patcher still
        # invokes its real Get-Process checks; no process or installed client is touched.
        source = (
            "$ErrorActionPreference = 'Stop'; "
            "function Get-Process { param([string[]] $Name, $ErrorAction) "
            f"if (Test-Path -LiteralPath {ps_quote(self.running)}) {{ "
            f"$runningName = Get-Content -LiteralPath {ps_quote(self.running)}; "
            "if ($Name -contains $runningName) { [pscustomobject]@{ Id=12345; ProcessName=$runningName } } } }; "
            f"& {ps_quote(self.root / 'tools/patch_game.ps1')} -Mode {mode}"
            + (" -RecoverStranded" if recover_stranded else "")
            + "; exit $LASTEXITCODE"
        )
        return ps_command(source)

    def invoke(self, mode, success=True, recover_stranded=False):
        result = subprocess.run(self.command(mode, recover_stranded), cwd=self.root.parent,
                                capture_output=True, text=True, timeout=45)
        if success:
            self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        else:
            self.assertNotEqual(0, result.returncode, result.stdout + result.stderr)
        return result

    def snapshot(self):
        return {p.name: p.read_bytes() for p in self.plugins.iterdir() if p.is_file()}

    def test_pristine_apply_status_and_reset_round_trip(self):
        self.invoke("apply")
        self.invoke("status")
        self.assertEqual(self.ext.read_bytes(), self.ast.read_bytes())
        self.assertIn(b"127.0.0.1:8088", self.plat.read_bytes())
        self.assertNotIn(STOCK_KEY, self.plat.read_bytes())
        self.invoke("reset")
        self.assertEqual(self.ast_stock, self.ast.read_bytes())
        self.assertEqual(self.plat_stock, self.plat.read_bytes())

    def test_status_rejects_missing_proxy_then_apply_repairs(self):
        self.invoke("apply")
        (self.plugins / "Astrolabe_orig.dll").unlink()
        self.invoke("status", success=False)
        self.invoke("apply")
        self.invoke("status")

    def test_status_rejects_old_installed_patch_then_apply_updates(self):
        self.invoke("apply")
        self.ext.write_bytes(self.ext.read_bytes() + b" newer build")
        self.invoke("status", success=False)
        self.invoke("apply")
        self.invoke("status")

    def test_pid_parked_copy_is_stranded_even_with_patched_slots(self):
        self.invoke("apply")
        for name in ("Astrolabe.dll", "AccountPlatNative.dll"):
            parked = self.plugins / (name + ".lunagc-live.90001")
            parked.write_bytes((self.plugins / name).read_bytes())
            self.invoke("status", success=False)
            self.invoke("apply")
            self.assertFalse(parked.exists())
        self.invoke("status")

    def test_client_update_refreshes_both_original_backups_without_losing_old_versions(self):
        self.invoke("apply")
        new_ast = self.ast_stock + b" updated client"
        new_plat = self.plat_stock + b" updated client"
        self.ast.write_bytes(new_ast)
        self.plat.write_bytes(new_plat)
        self.invoke("apply")
        self.invoke("status")
        files = self.snapshot()
        self.assertEqual(new_ast, files["Astrolabe.dll.lunagc-bak"])
        self.assertEqual(new_plat, files["AccountPlatNative.dll.lunagc-bak"])
        self.assertEqual(new_ast, files["Astrolabe_orig.dll"])
        self.assertIn(self.ast_stock, files.values())
        self.assertIn(self.plat_stock, files.values())
        self.invoke("reset")
        self.assertEqual(new_ast, self.ast.read_bytes())
        self.assertEqual(new_plat, self.plat.read_bytes())

    def test_reapply_does_not_capture_patch_as_original(self):
        self.invoke("apply")
        self.invoke("apply")
        self.assertEqual(self.ast_stock, (self.plugins / "Astrolabe.dll.lunagc-bak").read_bytes())
        self.assertEqual(self.plat_stock, (self.plugins / "AccountPlatNative.dll.lunagc-bak").read_bytes())

    def test_delayed_legacy_recovery_does_not_undo_explicit_reset(self):
        self.invoke("apply")
        # The old monitor samples this live copy, then the user resets before
        # its delayed recovery reaches the shared installer mutation lock.
        (self.plugins / "Astrolabe.dll.lunagc-live.90001").write_bytes(self.ast.read_bytes())
        self.ast.write_bytes(self.ast_stock)
        self.invoke("reset")
        before = self.snapshot()
        result = self.invoke("apply", success=False, recover_stranded=True)
        self.assertEqual(3, result.returncode, result.stdout + result.stderr)
        self.assertEqual(before, self.snapshot())
        self.assertEqual(self.ast_stock, self.ast.read_bytes())
        self.assertEqual(self.plat_stock, self.plat.read_bytes())

    def test_legacy_recovery_repairs_remaining_stranded_patch(self):
        self.invoke("apply")
        for slot, stock in ((self.ast, self.ast_stock), (self.plat, self.plat_stock)):
            parked = slot.with_name(slot.name + ".lunagc-live.90001")
            parked.write_bytes(slot.read_bytes())
            slot.write_bytes(stock)
        self.invoke("apply", recover_stranded=True)
        self.invoke("status")
        self.assertFalse(list(self.plugins.glob("*.lunagc-live*")))
        self.assertEqual(self.ext.read_bytes(), self.ast.read_bytes())

    def test_both_client_process_names_block_all_mutations(self):
        for name in ("YuanShen", "GenshinImpact"):
            self.running.write_text(name, encoding="ascii")
            before = self.snapshot()
            for mode in ("apply", "reset"):
                with self.subTest(name=name, mode=mode):
                    self.invoke(mode, success=False)
                    self.assertEqual(before, self.snapshot())

    def test_mutations_wait_for_per_game_lock_and_recheck_process_after_acquiring(self):
        normalized = str(self.game).rstrip("\\/").lower()
        digest = hashlib.sha256(normalized.encode("utf-8")).hexdigest()
        mutex = "Local\\LunaGC.ClientPatch.Mutation." + digest
        held = self.root / "mutex-held"
        release = self.root / "release-mutex"
        holder_source = (
            f"$mutex = New-Object System.Threading.Mutex($false, {ps_quote(mutex)}); "
            "$owned = $mutex.WaitOne(10000); try { if (-not $owned) { exit 9 }; "
            f"[IO.File]::WriteAllText({ps_quote(held)}, 'held'); "
            f"while (-not (Test-Path -LiteralPath {ps_quote(release)})) {{ Start-Sleep -Milliseconds 50 }} "
            "} finally { if ($owned) { $mutex.ReleaseMutex() }; $mutex.Dispose() }"
        )
        holder = subprocess.Popen(ps_command(holder_source), stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        try:
            deadline = time.monotonic() + 10
            while not held.exists() and time.monotonic() < deadline:
                time.sleep(0.05)
            self.assertTrue(held.exists(), "fixture did not acquire the mutation lock")
            before = self.snapshot()
            worker = subprocess.Popen(self.command("apply"), stdout=subprocess.PIPE, stderr=subprocess.PIPE)
            try:
                time.sleep(2)
                self.assertIsNone(worker.poll(), "apply bypassed the mutation lock")
                self.assertEqual(before, self.snapshot(), "files changed while another owner held the lock")
                self.running.write_text("YuanShen", encoding="ascii")
                release.write_text("release", encoding="ascii")
                output, error = worker.communicate(timeout=15)
                self.assertNotEqual(0, worker.returncode, output + error)
                self.assertEqual(before, self.snapshot(), "process state was not checked again after acquiring")
            finally:
                if worker.poll() is None:
                    worker.kill()
                    worker.communicate()
        finally:
            release.write_text("release", encoding="ascii")
            holder.communicate(timeout=15)
        self.running.unlink()
        self.invoke("apply")  # The failed attempt must release its mutex.
        self.invoke("status")


if __name__ == "__main__":
    unittest.main()
