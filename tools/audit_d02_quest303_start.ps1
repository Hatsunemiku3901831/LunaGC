$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Set-Location $root
$jar = Join-Path $root 'LunaGC-7.1.0.jar'
if ((Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash -ne 'DFDEBBDA0352055540C44A54E2EEBFD4FFDFE679DB74270044D3D55AEB006EC3') { throw 'Fixed shutdown helper JAR changed' }
if (Get-NetTCPConnection -LocalPort 27027 -State Listen -ErrorAction SilentlyContinue) { throw '27027 is already in use' }
$jdk = Get-ChildItem local/runtime -Directory -Filter 'jdk-17*' | Select-Object -First 1
$mongo = (Resolve-Path 'local/runtime/mongodb-win32-x86_64-windows-7.0.45/bin/mongod.exe').Path
$work = Join-Path $root ('local/migration72/d02-r3-quest303-replay-' + [guid]::NewGuid().ToString('N'))
$dbName = 'lunagc_d02_quest303_' + [guid]::NewGuid().ToString('N')
New-Item -ItemType Directory -Path (Join-Path $work 'db') -Force | Out-Null
$utf8 = New-Object System.Text.UTF8Encoding $false
$config = @{version=14; folderStructure=@{resources="$root/resources"; scripts="$root/resources/Scripts"; data="$work/data"; plugins="$work/plugins"; cache="$work/cache"; packets="$work/packets"}; server=@{fastRequire=$true}}
[IO.File]::WriteAllText((Join-Path $work 'config.json'), ($config | ConvertTo-Json -Depth 5), $utf8)
[IO.File]::WriteAllText((Join-Path $work 'database.txt'), $dbName, $utf8)
$gradleWork = $work.Replace('\','/')
$init = @"
allprojects { tasks.withType(Test).configureEach {
 workingDir file('$gradleWork')
 systemProperty 'user.dir', file('$gradleWork').absolutePath
 systemProperty 'd02.repo', rootProject.rootDir.absolutePath
 systemProperty 'd02.database', '$dbName'
 environment 'LUNAGC_RESOURCES_DIR', rootProject.file('resources').absolutePath
 jvmArgs '-Xlog:class+init=info:file=$gradleWork/class-init.log'
} }
"@
[IO.File]::WriteAllText((Join-Path $work 'quest303.init.gradle'), $init, $utf8)
$java = @'
import com.mongodb.MongoException;
import com.mongodb.client.MongoClients;
import org.bson.Document;
public class ShutdownD02Mongo {
 public static void main(String[] args) {
  try (var client = MongoClients.create("mongodb://127.0.0.1:27027/?serverSelectionTimeoutMS=3000")) {
   try { System.out.println(client.getDatabase("admin").runCommand(new Document("shutdown", 1))); }
   catch (MongoException e) { System.out.println("shutdown response: " + e.getClass().getSimpleName()); }
  }
 }
}
'@
[IO.File]::WriteAllText((Join-Path $work 'ShutdownD02Mongo.java'), $java, $utf8)
[IO.File]::WriteAllText((Join-Path $work 'shutdown-logback.xml'), '<configuration><root level="OFF"/></configuration>', $utf8)
& (Join-Path $jdk.FullName 'bin/javac.exe') -cp $jar -d $work (Join-Path $work 'ShutdownD02Mongo.java')
if ($LASTEXITCODE -ne 0) { throw 'Shutdown helper compilation failed' }
$prior = @{}
foreach ($name in @('JAVA_HOME','GRADLE_USER_HOME','LUNAGC_RUNTIME','LUNAGC_TEST_MONGO_URI')) { $prior[$name] = [Environment]::GetEnvironmentVariable($name, 'Process') }
$env:JAVA_HOME = $jdk.FullName
$env:GRADLE_USER_HOME = (Resolve-Path 'local/gradle').Path
$env:LUNAGC_RUNTIME = 'portable'
$env:LUNAGC_TEST_MONGO_URI = 'mongodb://127.0.0.1:27027'
$process = $null
try {
 if (Get-NetTCPConnection -LocalPort 27027 -State Listen -ErrorAction SilentlyContinue) { throw '27027 became occupied' }
 $process = Start-Process -FilePath $mongo -ArgumentList @('--dbpath',('"'+(Join-Path $work 'db')+'"'),'--bind_ip','127.0.0.1','--port','27027','--logpath',('"'+(Join-Path $work 'mongo.log')+'"')) -WindowStyle Hidden -PassThru
 [IO.File]::WriteAllText((Join-Path $work 'pid.txt'), [string]$process.Id, $utf8)
 $listener = $null
 for ($i=0; $i -lt 60 -and -not $listener; $i++) {
  if ($process.HasExited) { throw 'Dedicated Mongo exited before listening' }
  $listener = Get-NetTCPConnection -LocalPort 27027 -State Listen -ErrorAction SilentlyContinue
  if (-not $listener) { Start-Sleep -Milliseconds 250 }
 }
 if (-not $listener -or $listener.OwningProcess -ne $process.Id) { throw 'Dedicated Mongo listener not established' }
 $started = Get-Date
 try {
  # PowerShell 5 reports native stderr as error records even for compiler notes.
  $ErrorActionPreference = 'Continue'
  & ./gradlew.bat --no-daemon test --tests emu.grasscutter.game.quest.D02Quest303StartMongoTest -PskipHandbook=1 -I (Join-Path $work 'quest303.init.gradle') --console=plain *> (Join-Path $work 'gradle.log')
  $testExit = $LASTEXITCODE
 } finally { $ErrorActionPreference = 'Stop' }
 $xml = 'build/test-results/test/TEST-emu.grasscutter.game.quest.D02Quest303StartMongoTest.xml'
 if ((Test-Path -LiteralPath $xml) -and (Get-Item -LiteralPath $xml).LastWriteTime -ge $started) { Copy-Item -LiteralPath $xml -Destination (Join-Path $work 'result.xml') }
 if ($testExit -ne 0) { throw "Targeted test failed, exit $testExit; evidence: $work" }
 if (-not (Test-Path -LiteralPath (Join-Path $work 'result.xml'))) { throw 'No fresh test result was produced' }
 [xml]$report = [IO.File]::ReadAllText((Join-Path $work 'result.xml'))
 if ($report.testsuite.tests -ne '2' -or $report.testsuite.failures -ne '0' -or $report.testsuite.errors -ne '0' -or $report.testsuite.skipped -ne '0') { throw 'Unexpected test result counts' }
 Write-Output "Test passed; evidence and database: $work"
} finally {
 $shutdownFailure = $null
 if ($process) {
  try {
   if ($process.HasExited) { throw "Dedicated Mongo exited before shutdown: PID $($process.Id)" }
   $listener = Get-NetTCPConnection -LocalPort 27027 -State Listen -ErrorAction SilentlyContinue
   $current = Get-CimInstance Win32_Process -Filter "ProcessId=$($process.Id)"
   if (-not $listener -or $listener.OwningProcess -ne $process.Id -or -not $current.CommandLine.Contains((Join-Path $work 'db'))) { throw "Mongo ownership could not be verified: PID $($process.Id)" }
   $logback = (Join-Path $work 'shutdown-logback.xml').Replace('\','/')
   & (Join-Path $jdk.FullName 'bin/java.exe') "-Dlogback.configurationFile=$logback" -cp "$work;$jar" ShutdownD02Mongo *> (Join-Path $work 'shutdown.log')
   if ($LASTEXITCODE -ne 0 -or -not $process.WaitForExit(10000) -or $process.ExitCode -ne 0) { throw "Mongo shutdown failed: PID $($process.Id)" }
   if (Get-NetTCPConnection -LocalPort 27027 -State Listen -ErrorAction SilentlyContinue) { throw '27027 remains in use after shutdown' }
   if (-not (Select-String -Path (Join-Path $work 'mongo.log') -Pattern 'mongod shutdown complete' -SimpleMatch -Quiet)) { throw 'Mongo shutdown log missing completion' }
  } catch { $shutdownFailure = $_ }
 }
 foreach ($name in $prior.Keys) { [Environment]::SetEnvironmentVariable($name, $prior[$name], 'Process') }
 if ($shutdownFailure) { throw $shutdownFailure }
}
