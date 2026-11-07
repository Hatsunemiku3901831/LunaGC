"""Execute only the local synthetic fixture; never access a target process or network."""
import os
import subprocess
import unittest
from pathlib import Path

FIXTURE = Path(os.environ.get('SDK72_OBSERVER_FIXTURE', str(Path(__file__).resolve().parents[1] / 'local/migration72/d02-active/sdk-observer/fixture.exe')))

class SdkObserverFixtureTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        result = subprocess.run([str(FIXTURE)], capture_output=True, text=True, check=True)
        cls.output = result.stdout
        cls.rows = [line for line in cls.output.splitlines() if line.startswith('tick=')]

    def test_forwarding_return_last_error_and_nonvolatile_registers(self):
        self.assertIn('failures=0', self.output)
        self.assertIn('nonvolatile_gpr_and_fixture=1', self.output)
        self.assertEqual(len(self.rows), 8)
        ticks = [int(line.split()[0][5:]) for line in self.rows]
        self.assertEqual(ticks, sorted(ticks))

    def test_only_numeric_allowlisted_output_no_sentinels(self):
        for secret in ('SENTINEL_SECRET', 'password', 'token', 'header', 'sdk-static', 'unknown', '\u79d8\u5bc6'):
            self.assertNotIn(secret.lower(), self.output.lower())
        for line in self.rows:
            self.assertRegex(line, r'^tick=\d+ event=[123] host=[012] method=[012] route=[0123] length=\d+ status=\d+ has_status=[01]$')
        self.assertTrue(any('event=3' in line and 'status=207 has_status=1' in line for line in self.rows))
        for line in self.rows:
            if 'event=2' in line and 'route=0' in line:
                self.assertIn('method=0', line)

if __name__ == '__main__': unittest.main()
