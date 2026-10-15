"""Exercise the Windows launcher in a disposable checkout with a real Java selector."""

import os
from pathlib import Path
import shutil
import socket
import subprocess
import tempfile
import unittest


@unittest.skipUnless(os.name == "nt", "Windows launcher integration")
class ServeTest(unittest.TestCase):
    def test_java_selector_starts_without_global_vm_options(self):
        self.start_selector(None)

    def test_java_selector_accepts_an_explicit_directory_with_spaces(self):
        self.start_selector("custom socket directory")

    def test_http_start_does_not_reuse_an_unrelated_udp_listener(self):
        self.start_selector(None, occupy_udp=True)

    def start_selector(self, override, occupy_udp=False):
        container = os.environ.get("LUNAGC_TEST_MONGO_CONTAINER")
        if not container:
            self.skipTest("Explicit isolated Mongo container required")
        repo = Path(__file__).resolve().parents[1]
        debug = (repo / "debug").resolve()
        debug.mkdir(exist_ok=True)
        with tempfile.TemporaryDirectory(prefix="s t", dir=debug) as directory:
            workspace = Path(directory).resolve()
            self.assertEqual(debug, workspace.parent)
            (workspace / "tools").mkdir()
            shutil.copy2(repo / "tools/serve.ps1", workspace / "tools/serve.ps1")
            (workspace / "SelectorServer.java").write_text(
                """import java.nio.channels.*;
import java.net.*;
class SelectorServer {
  public static void main(String[] args) throws Exception {
    System.out.println("pid=" + ProcessHandle.current().pid());
    try (var selector = Selector.open(); var server = ServerSocketChannel.open()) {
      server.bind(new InetSocketAddress("127.0.0.1", Integer.parseInt(System.getenv("LUNAGC_HTTP_PORT"))));
      System.out.println("socket-dir=" + System.getProperty("jdk.net.unixdomain.tmpdir", ""));
      System.out.flush();
      Thread.sleep(60000);
    }
  }
}
""",
                encoding="utf-8",
            )
            subprocess.run(["javac", "SelectorServer.java"], cwd=workspace, check=True, capture_output=True)
            subprocess.run(
                ["jar", "--create", "--file", "selector server.jar", "--main-class", "SelectorServer", "SelectorServer.class"],
                cwd=workspace, check=True, capture_output=True,
            )
            udp = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
            for _ in range(32):
                with socket.socket() as probe:
                    probe.bind(("127.0.0.1", 0))
                    port = probe.getsockname()[1]
                    try:
                        udp.bind(("0.0.0.0", port))
                    except OSError:
                        continue
                    break
            else:
                udp.close()
                self.fail("No unused TCP/UDP test port")
            if not occupy_udp:
                udp.close()
            env = os.environ.copy()
            for name in ("JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS"):
                env.pop(name, None)
            env.update(LUNAGC_JAR="selector server.jar", LUNAGC_HTTP_PORT=str(port),
                       LUNAGC_GAME_PORT=str(port), LUNAGC_MONGO=container)
            if override:
                env["LUNAGC_JAVA_SOCKET_DIR"] = str(workspace / override)
            else:
                env.pop("LUNAGC_JAVA_SOCKET_DIR", None)
            command = ["powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", "tools/serve.ps1", "-Mode"]
            try:
                result = subprocess.run(command + ["start"], cwd=workspace, env=env,
                                        capture_output=True, text=True, timeout=40)
                self.assertEqual(0, result.returncode, result.stdout + result.stderr)
                self.assertTrue((workspace / "start_stdout.log").is_file(), result.stdout + result.stderr)
                output = (workspace / "start_stdout.log").read_text("utf-8-sig")
                directory_line = next(line for line in output.splitlines() if line.startswith("socket-dir="))
                configured = directory_line.removeprefix("socket-dir=")
                self.assertTrue(configured, "The launcher must select a socket directory")
                socket_directory = Path(configured).resolve()
                self.assertTrue(socket_directory.is_relative_to(workspace))
                self.assertTrue(socket_directory.is_dir())
                if override:
                    self.assertEqual(workspace / override, socket_directory)
            finally:
                udp.close()
                output_file = workspace / "start_stdout.log"
                output = output_file.read_text("utf-8-sig") if output_file.exists() else ""
                pid_line = next((line for line in output.splitlines() if line.startswith("pid=")), None)
                if pid_line:
                    child_pid = int(pid_line.removeprefix("pid="))
                    jar_literal = str(workspace / "selector server.jar").replace("'", "''")
                    cleanup = (
                        f"$candidate=Get-CimInstance Win32_Process -Filter 'ProcessId={child_pid}'; "
                        f"if($candidate -and $candidate.CommandLine.Contains('{jar_literal}')) {{ "
                        f"$owners=@(Get-NetTCPConnection -State Listen -LocalPort {port} -ErrorAction SilentlyContinue | Select-Object -ExpandProperty OwningProcess); "
                        f"if($owners.Count -gt 0 -and @($owners | Where-Object {{ $_ -ne {child_pid} }}).Count -eq 0) "
                        "{ & ./tools/serve.ps1 -Mode stop } "
                        f"else {{ Stop-Process -Id {child_pid} }} }}"
                    )
                    subprocess.run(["powershell", "-NoProfile", "-Command", cleanup], cwd=workspace,
                                   env=env, capture_output=True, text=True, timeout=20, check=True)


if __name__ == "__main__":
    unittest.main()
