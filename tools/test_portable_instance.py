"""Execute the portable entry in disposable instances; no real service can start."""
import base64
import copy
import hashlib
import json
import os
from pathlib import Path
import subprocess
import unittest

from tools import test_local_windows as default_launcher


@unittest.skipUnless(os.name == 'nt', 'Windows portable launcher')
class PortableInstanceTest(unittest.TestCase):
    def setUp(self):
        fixture = default_launcher.PortableLauncherTest()
        fixture.setUp()
        self.addCleanup(fixture.doCleanups)
        self.root, self.env = fixture.root, fixture.env
        self.instance = self.root/'local/instances/d02 test'
        self.instance.mkdir(parents=True)
        (self.instance/'resources/ExcelBinOutput').mkdir(parents=True)
        (self.instance/'resources/ExcelBinOutput/AvatarExcelConfigData.json').write_text('[]', 'utf8')
        (self.instance/'local/data').mkdir(parents=True)
        (self.instance/'LunaGC-7.1.0.jar').write_bytes(b'isolated fixture; never executed')
        self.config = copy.deepcopy(fixture.config)
        self.config['folderStructure'] = {'resources': 'resources', 'data': 'local/data'}
        for row in self.config['databaseInfo'].values():
            row['connectionUri'] = 'mongodb://127.0.0.1:27027'
        self.original_config = b'root config must never be read or replaced'
        (self.root/'config.json').write_bytes(self.original_config)
        (self.root/'mongodata').mkdir()
        (self.root/'mongodata/original').write_bytes(b'original DB sentinel')
        (self.root/'local/data/original').write_bytes(b'original data sentinel')

    def run_mode(self, mode='start', instance=None, extra=''):
        (self.instance/'config.json').write_text(json.dumps(self.config), 'utf8')
        quote = lambda value: "'"+str(value).replace("'", "''")+"'"
        calls = self.root/'launches.json'
        java = self.root/'local/runtime/jdk-17-fixture/bin/java.exe'
        mongo = self.root/'local/runtime/mongodb-fixture/bin/mongod.exe'
        source = '''
$global:d02InstanceLaunches = @(); $global:d02InstanceMongoStarted = $false
function Get-NetTCPConnection {
    param($State,$LocalPort,$ErrorAction)
    if ($LocalPort -eq 27027 -and $global:d02InstanceMongoStarted) { [pscustomobject]@{OwningProcess=4321} }
}
function Get-CimInstance { param($ClassName,$Filter) [pscustomobject]@{ProcessId=4321} }
function Start-Process {
    param($FilePath,$ArgumentList,$WorkingDirectory,$WindowStyle,[switch]$PassThru,$RedirectStandardOutput,$RedirectStandardError)
    $global:d02InstanceLaunches += [pscustomobject]@{exe=$FilePath;arguments=@($ArgumentList);cwd=$WorkingDirectory}
    $global:d02InstanceLaunches | ConvertTo-Json -Depth 5 | Set-Content -Encoding UTF8 -LiteralPath CALLS
    if ($FilePath -eq MONGO) { $global:d02InstanceMongoStarted=$true; return [pscustomobject]@{Id=4321;HasExited=$false} }
    if ($FilePath -eq JAVA) { throw 'FIXTURE_SERVER_LAUNCH_CAPTURED' }
    throw 'UNEXPECTED_PROCESS_LAUNCH'
}
'''.replace('CALLS', quote(calls)).replace('MONGO', quote(mongo)).replace('JAVA', quote(java))
        source += extra
        source += ("\ntry { & "+quote(self.root/'tools/local_windows.ps1')+' -Mode '+mode+
                   ' -InstanceRoot '+quote(instance or self.instance)+
                   '; exit $LASTEXITCODE } catch { [Console]::Error.WriteLine($_.Exception.Message); exit 1 }')
        encoded = base64.b64encode(source.encode('utf-16le')).decode('ascii')
        result = subprocess.run(['powershell.exe', '-NoProfile', '-OutputFormat', 'Text',
            '-ExecutionPolicy', 'Bypass', '-EncodedCommand', encoded], env=self.env,
            capture_output=True, text=True, timeout=30)
        self.assertEqual((self.root/'config.json').read_bytes(), self.original_config)
        self.assertEqual((self.root/'mongodata/original').read_bytes(), b'original DB sentinel')
        self.assertEqual((self.root/'local/data/original').read_bytes(), b'original data sentinel')
        recorded = json.loads(calls.read_text('utf-8-sig')) if calls.exists() else []
        return result, recorded if isinstance(recorded, list) else [recorded]

    def existing_mongo(self, database):
        executable = self.root/'local/runtime/mongodb-fixture/bin/mongod.exe'
        command = ('"'+str(executable)+'" --dbpath "'+str(database)+'" --port 27027').replace("'", "''")
        return ("\nfunction Get-NetTCPConnection { param($State,$LocalPort,$ErrorAction) "
                "if ($LocalPort -eq 27027) { [pscustomobject]@{OwningProcess=4321} } }; "
                "function Get-CimInstance { param($ClassName,$Filter) "
                "[pscustomobject]@{Name='mongod.exe';ProcessId=4321;ExecutablePath='"+
                str(executable).replace("'", "''")+"';CommandLine='"+command+"'} }")

    def test_existing_mongo_with_same_path_prefix_is_rejected(self):
        result, calls = self.run_mode(extra=self.existing_mongo(self.instance/'mongodata-old'))
        self.assertIn('different database', result.stderr)
        self.assertEqual(calls, [])

    def test_existing_mongo_with_exact_database_is_reused(self):
        result, calls = self.run_mode(extra=self.existing_mongo(self.instance/'mongodata'))
        self.assertIn('FIXTURE_SERVER_LAUNCH_CAPTURED', result.stderr)
        self.assertEqual(len(calls), 1)
        self.assertIn('java.exe', calls[0]['exe'])

    def test_mongo_and_java_use_selected_instance_and_shared_toolchain(self):
        result, calls = self.run_mode()
        self.assertIn('FIXTURE_SERVER_LAUNCH_CAPTURED', result.stderr)
        self.assertEqual(len(calls), 2)
        self.assertEqual([row['cwd'] for row in calls], [str(self.instance)]*2)
        self.assertIn('mongodb-fixture', calls[0]['exe'])
        args = calls[0]['arguments']
        self.assertEqual(int(args[args.index('--port')+1]), 27027)
        self.assertEqual(args[args.index('--dbpath')+1].strip('"'), str(self.instance/'mongodata'))
        self.assertIn('jdk-17-fixture', calls[1]['exe'])
        self.assertEqual(calls[1]['arguments'][-2].strip('"'), str(self.instance/'LunaGC-7.1.0.jar'))
        self.assertTrue((self.instance/'local/java-sockets').is_dir())

    def test_original_database_uri_is_rejected_before_any_process(self):
        self.config['databaseInfo']['game']['connectionUri'] = 'mongodb://127.0.0.1:27017'
        result, calls = self.run_mode()
        self.assertIn('27027', result.stderr)
        self.assertEqual(calls, [])
        self.assertFalse((self.instance/'mongodata').exists())

    def test_original_resources_cannot_be_reused_by_external_override(self):
        self.config['folderStructure']['resources'] = str(self.root/'resources')
        self.env['LUNAGC_RESOURCES_DIR'] = str(self.root/'resources')
        result, calls = self.run_mode()
        self.assertIn('outside this checkout', result.stderr)
        self.assertEqual(calls, [])

    def test_original_data_cannot_be_reused(self):
        self.config['folderStructure']['data'] = str(self.root/'local/data')
        result, calls = self.run_mode()
        self.assertIn('outside this checkout', result.stderr)
        self.assertEqual(calls, [])

    def test_sibling_prefix_is_rejected_before_directory_creation(self):
        selected = self.root/'local/instances-other/d02'
        result, calls = self.run_mode(instance=selected)
        self.assertIn('local/instances/', result.stderr)
        self.assertEqual(calls, [])
        self.assertFalse(selected.exists())

    def test_missing_instance_config_does_not_generate_or_copy_original_data(self):
        selected = self.root/'local/instances/missing-config'
        selected.mkdir()
        result, calls = self.run_mode(instance=selected)
        self.assertIn('prepared config.json', result.stderr)
        self.assertEqual(calls, [])
        self.assertFalse((selected/'config.json').exists())
        self.assertFalse((selected/'local/data').exists())

    def test_another_instances_server_is_not_stopped(self):
        command = 'java.exe -jar "'+str(self.root/'LunaGC-7.1.0.jar')+'" -noconsole'
        extra = ("\nfunction Get-NetTCPConnection { param($State,$LocalPort,$ErrorAction) "
                 "if ($LocalPort -eq 8088) { [pscustomobject]@{OwningProcess=1234} } }; "
                 "function Get-CimInstance { param($ClassName,$Filter) "
                 "[pscustomobject]@{Name='java.exe';ProcessId=1234;CommandLine='"+
                 command.replace("'", "''")+"'} }; function Invoke-RestMethod { throw 'UNEXPECTED_GM_COMMAND' }")
        result, calls = self.run_mode('stop', extra=extra)
        self.assertIn('belongs to another application', result.stderr)
        self.assertNotIn('UNEXPECTED_GM_COMMAND', result.stderr)
        self.assertEqual(calls, [])

    def test_build_with_instance_is_rejected_without_staging_a_jar(self):
        result, calls = self.run_mode('build')
        self.assertIn('checkout', result.stderr)
        self.assertEqual(calls, [])
        self.assertFalse((self.root/'build-args.txt').exists())

    def test_instance_status_uses_its_running_jar_record_and_rejects_changed_bytes(self):
        jar = self.instance/'LunaGC-7.1.0.jar'
        state = {'pid': 1234, 'jar': str(jar), 'jarSHA256': hashlib.sha256(jar.read_bytes()).hexdigest()}
        (self.instance/'local/running.json').write_text(json.dumps(state), 'utf8')
        command = ('java.exe -jar "'+str(jar)+'" -noconsole').replace("'", "''")
        extra = ("\nfunction Get-NetTCPConnection { param($State,$LocalPort,$ErrorAction) "
                 "if ($LocalPort -eq 8088) { [pscustomobject]@{OwningProcess=1234} } }; "
                 "function Get-CimInstance { param($ClassName,$Filter) "
                 "[pscustomobject]@{Name='java.exe';ProcessId=1234;CommandLine='"+command+"'} }; "
                 "function Invoke-RestMethod { [pscustomobject]@{status=@{health=@{healthy=$true}}} }")
        result, calls = self.run_mode('status', extra=extra)
        self.assertEqual(result.returncode, 0, result.stdout+result.stderr)
        self.assertIn(str(self.instance/'local/logs'), result.stdout)
        self.assertEqual(calls, [])
        jar.write_bytes(b'changed artifact')
        result, calls = self.run_mode('status', extra=extra)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('Running JAR differs', result.stderr)
        self.assertEqual(calls, [])

    def test_instance_does_not_bypass_client_version_guard(self):
        self.env.pop('LUNAGC_CLIENT_PATCH_GUARD')
        (self.root/'build.gradle').write_text("version = '7.2.0'\n", 'utf8')
        game = self.root/'fixture-client'
        game.mkdir()
        (game/'config.ini').write_text('game_version=7.3.0\n', 'utf8')
        quoted = str(game).replace("'", "''")
        (self.root/'tools/game_path.ps1').write_text("Write-Output '"+quoted+"'\n", 'utf8')
        result, calls = self.run_mode()
        self.assertIn('does not match server protocol', result.stderr)
        self.assertEqual(calls, [])


if __name__ == '__main__':
    unittest.main()
