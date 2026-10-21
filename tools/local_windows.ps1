[CmdletBinding()]
param(
    [ValidateSet('start', 'stop', 'status', 'play', 'patch', 'reset', 'gm', 'build', 'buildpatch')]
    [string] $Mode = 'status'
)

$ErrorActionPreference = 'Stop'
# Task may inherit PowerShell 7's module path while invoking Windows PowerShell.
# Load the matching utility module explicitly, including its Get-FileHash function.
Import-Module (Join-Path $PSHOME 'Modules\Microsoft.PowerShell.Utility\Microsoft.PowerShell.Utility.psd1') -ErrorAction Stop
$repo = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $repo

# Read literal settings only. A checkout's .env is data, never PowerShell code.
foreach ($name in @('.env', '.env.local')) {
    $file = Join-Path $repo $name
    if (-not (Test-Path -LiteralPath $file)) { continue }
    foreach ($line in Get-Content -LiteralPath $file -Encoding UTF8) {
        if ($line -notmatch '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*?)\s*$') { continue }
        $key = $Matches[1]
        $value = $Matches[2]
        if ($value.Length -ge 2 -and (($value.StartsWith('"') -and $value.EndsWith('"')) -or
            ($value.StartsWith("'") -and $value.EndsWith("'")))) {
            $value = $value.Substring(1, $value.Length - 2)
        }
        [Environment]::SetEnvironmentVariable($key, $value, 'Process')
    }
}

$local = Join-Path $repo 'local'
$logDir = Join-Path $local 'logs'
$jar = Join-Path $repo 'LunaGC-7.1.0.jar'
$configFile = Join-Path $repo 'config.json'
$httpPort = if ($env:LUNAGC_HTTP_PORT) { [int]$env:LUNAGC_HTTP_PORT } else { 8088 }
$gamePort = if ($env:LUNAGC_GAME_PORT) { [int]$env:LUNAGC_GAME_PORT } else { 22101 }
$mongoPort = 27017
$baseUrl = 'http://127.0.0.1:' + $httpPort
New-Item -ItemType Directory -Path $logDir -Force | Out-Null

function Get-Java {
    $jdk = Get-ChildItem -LiteralPath (Join-Path $local 'runtime') -Directory |
        Where-Object Name -Like 'jdk-17*' | Sort-Object Name -Descending | Select-Object -First 1
    if (-not $jdk) { throw 'Portable Java 17 is missing under local\runtime.' }
    $java = Join-Path $jdk.FullName 'bin\java.exe'
    if (-not (Test-Path -LiteralPath $java)) { throw "Java is missing: $java" }
    $env:JAVA_HOME = $jdk.FullName
    $env:PATH = (Join-Path $jdk.FullName 'bin') + ';' + $env:PATH
    return $java
}

function Get-Mongod {
    $mongo = Get-ChildItem -LiteralPath (Join-Path $local 'runtime') -Directory |
        Where-Object Name -Like 'mongodb-*' | Sort-Object Name -Descending | Select-Object -First 1
    if (-not $mongo) { throw 'Portable MongoDB is missing under local\runtime.' }
    return (Join-Path $mongo.FullName 'bin\mongod.exe')
}

function Get-Listener([int] $port) {
    $connection = Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if (-not $connection) { return $null }
    return (Get-CimInstance Win32_Process -Filter ('ProcessId=' + $connection.OwningProcess))
}

function Get-Server {
    $process = Get-Listener $httpPort
    if ($process -and ($process.Name -ne 'java.exe' -or
        $process.CommandLine.IndexOf($jar, [StringComparison]::OrdinalIgnoreCase) -lt 0)) {
        throw "Port $httpPort belongs to another application (PID $($process.ProcessId))."
    }
    return $process
}

function Get-Health {
    try { return (Invoke-RestMethod -Uri ($baseUrl + '/status/server') -TimeoutSec 3) }
    catch { return $null }
}

function Get-Config {
    if (-not (Test-Path -LiteralPath $configFile)) {
        $stateData = Join-Path $local 'data'
        if (-not (Test-Path -LiteralPath $stateData)) {
            Copy-Item -LiteralPath (Join-Path $repo 'data') -Destination $stateData -Recurse
        }
        $configuration = [ordered]@{
            version = 14
            folderStructure = @{ resources = (Join-Path $repo 'resources'); data = $stateData }
            databaseInfo = @{
                server = @{ connectionUri = 'mongodb://127.0.0.1:27017'; collection = 'grasscutter' }
                game = @{ connectionUri = 'mongodb://127.0.0.1:27017'; collection = 'grasscutter' }
            }
            language = @{ language = 'zh_CN'; fallback = 'en_US'; document = 'CHS' }
            account = @{ autoCreate = $false; EXPERIMENTAL_RealPassword = $false }
            server = @{
                http = @{ bindAddress = '127.0.0.1'; bindPort = $httpPort; accessAddress = '127.0.0.1';
                    encryption = @{ useEncryption = $false; useInRouting = $false } }
                game = @{ bindAddress = '127.0.0.1'; bindPort = $gamePort; accessAddress = '127.0.0.1'; enableConsole = $false }
                gm = @{ loopbackOnly = $true; accessToken = [Guid]::NewGuid().ToString('N') }
            }
        }
        $json = $configuration | ConvertTo-Json -Depth 12
        [IO.File]::WriteAllText($configFile, $json, (New-Object Text.UTF8Encoding($false)))
    }
    return (Get-Content -Raw -LiteralPath $configFile -Encoding UTF8 | ConvertFrom-Json)
}

function Invoke-GmCommand([string] $command) {
    $configuration = Get-Config
    $headers = @{ Authorization = 'Bearer ' + $configuration.server.gm.accessToken }
    return (Invoke-RestMethod -Uri ($baseUrl + '/gm/api/command') -Method Post -Headers $headers `
        -ContentType 'application/json' -Body (@{ command = $command } | ConvertTo-Json -Compress) -TimeoutSec 10)
}

function Start-LocalServer {
    $existing = Get-Server
    if ($existing) {
        $health = Get-Health
        if (-not $health.status.health.healthy) { throw "Existing LunaGC is not healthy. Check $logDir." }
        Write-Host "LunaGC is already running (PID $($existing.ProcessId))."
        return
    }
    $java = Get-Java
    $mongod = Get-Mongod
    if (-not (Test-Path -LiteralPath $jar)) { throw 'LunaGC-7.1.0.jar is missing.' }
    $resource = Join-Path $repo 'resources\ExcelBinOutput\AvatarExcelConfigData.json'
    if (-not (Test-Path -LiteralPath $resource)) { throw 'Resources are missing. Initialize the resources submodule first.' }
    if ((Get-Content -LiteralPath $resource -TotalCount 1) -match '^version https://git-lfs') {
        throw 'Resources are LFS pointers. Run git -C resources lfs pull first.'
    }
    $configuration = Get-Config
    if ($configuration.server.http.bindPort -ne $httpPort -or $configuration.server.game.bindPort -ne $gamePort) {
        throw 'The .env ports and config.json ports must match.'
    }
    $db = Join-Path $repo 'mongodata'
    New-Item -ItemType Directory -Path $db -Force | Out-Null
    $mongo = Get-Listener $mongoPort
    if ($mongo) {
        if ($mongo.ExecutablePath -ne $mongod -or $mongo.CommandLine.IndexOf($db, [StringComparison]::OrdinalIgnoreCase) -lt 0) {
            throw 'Port 27017 belongs to a different database. Refusing to reuse it.'
        }
    } else {
        $mongo = Start-Process -FilePath $mongod -ArgumentList @('--dbpath', ('"' + $db + '"'),
            '--bind_ip', '127.0.0.1', '--port', $mongoPort, '--wiredTigerCacheSizeGB', '1',
            '--logpath', ('"' + (Join-Path $logDir 'mongo.log') + '"'), '--logappend') `
            -WorkingDirectory $repo -WindowStyle Hidden -PassThru
        $deadline = (Get-Date).AddSeconds(45)
        while (-not (Get-Listener $mongoPort)) {
            if ($mongo.HasExited) { throw "MongoDB exited. Check $logDir\mongo.log." }
            if ((Get-Date) -gt $deadline) { throw 'MongoDB did not become ready in 45 seconds.' }
            Start-Sleep -Milliseconds 500
        }
    }
    $socketDir = Join-Path $local 'java-sockets'
    New-Item -ItemType Directory -Path $socketDir -Force | Out-Null
    $jarHash = (Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash
    $server = Start-Process -FilePath $java -ArgumentList @('-Xms512m', '-Xmx6g', '-Dfile.encoding=UTF-8',
        ('"-Djdk.net.unixdomain.tmpdir=' + $socketDir + '"'), '-jar', ('"' + $jar + '"'), '-noconsole') `
        -WorkingDirectory $repo -WindowStyle Hidden -RedirectStandardOutput (Join-Path $logDir 'server-out.log') `
        -RedirectStandardError (Join-Path $logDir 'server-err.log') -PassThru
    $state = [ordered]@{ started = (Get-Date).ToString('o'); pid = $server.Id; jar = $jar;
        jarSHA256 = $jarHash; gamePath = $env:GAME_PATH }
    $state | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $local 'running.json') -Encoding UTF8
    Write-Host 'Loading LunaGC resources. The first start may take several minutes...'
    $deadline = (Get-Date).AddMinutes(5)
    do {
        if ($server.HasExited) { throw "LunaGC exited. Check $logDir\server-err.log and logs\." }
        $health = Get-Health
        if ($health.status.health.healthy) {
            $gameListener = Get-NetUDPEndpoint -LocalPort $gamePort -ErrorAction SilentlyContinue |
                Where-Object OwningProcess -EQ $server.Id
            if (-not $gameListener) { throw 'HTTP is healthy but the game UDP listener is missing.' }
            Write-Host "LunaGC is ready: $baseUrl (PID $($server.Id)), game UDP $gamePort."
            return
        }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $deadline)
    throw "LunaGC did not become healthy. Check $logDir."
}

function Stop-LocalServer {
    $server = Get-Server
    if (-not $server) { Write-Host 'LunaGC is already stopped.'; return }
    Write-Host 'Saving player data and stopping LunaGC...'
    try { Invoke-GmCommand 'stop' | Out-Null } catch { }
    $deadline = (Get-Date).AddSeconds(60)
    while (Get-Process -Id $server.ProcessId -ErrorAction SilentlyContinue) {
        if ((Get-Date) -gt $deadline) {
            throw 'Graceful shutdown has not finished. Check the logs; no process was force-stopped.'
        }
        Start-Sleep -Milliseconds 500
    }
    Write-Host 'LunaGC stopped. MongoDB remains running; saved data stays in mongodata\.'
}

function Set-ClientPatch([string] $action) {
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'patch_game.ps1') -Mode $action
    if ($LASTEXITCODE -ne 0) { throw "Client patch action failed: $action" }
}

function Start-ClientPatchGuard {
    if ($env:LUNAGC_CLIENT_PATCH_GUARD -match '^(0|false|off)$') { return }
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'client_patch_guard.ps1') -Mode start
    if ($LASTEXITCODE -ne 0) { throw 'Could not start client patch recovery.' }
}

$mutex = New-Object Threading.Mutex($false, ('Local\LunaGCNative-' + ($repo -replace '[^A-Za-z0-9]', '_')))
$locked = $false
try {
    if ($Mode -in @('start', 'stop', 'play', 'gm', 'build')) {
        $locked = $mutex.WaitOne(0)
        if (-not $locked) { throw 'Another local LunaGC server action is still in progress.' }
    }
    switch ($Mode) {
        'start' { Start-LocalServer; Start-ClientPatchGuard }
        'stop' { Stop-LocalServer }
        'status' {
            $server = Get-Server
            $health = Get-Health
            $mongo = Get-Listener $mongoPort
            Write-Host "Game path: $env:GAME_PATH"
            Write-Host "MongoDB: $(if ($mongo) { 'running (PID ' + $mongo.ProcessId + ')' } else { 'stopped' })"
            Write-Host "LunaGC: $(if ($server) { 'running (PID ' + $server.ProcessId + ')' } else { 'stopped' })"
            Write-Host "Healthy: $([bool]$health.status.health.healthy)"
            Write-Host "Logs: $logDir"
            & powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'patch_game.ps1') -Mode status
            if ($LASTEXITCODE -ne 0 -or -not $health.status.health.healthy) { exit 1 }
        }
        'patch' { Set-ClientPatch 'apply' }
        'reset' {
            & powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'client_patch_guard.ps1') -Mode stop
            if ($LASTEXITCODE -ne 0) { throw 'Could not stop the client patch guard.' }
            Set-ClientPatch 'reset'
        }
        'play' {
            Start-LocalServer
            $game = & powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'game_path.ps1')
            if ($LASTEXITCODE -ne 0) { throw 'The game directory could not be resolved.' }
            if (Get-Process -Name YuanShen -ErrorAction SilentlyContinue) { throw 'The game is already running.' }
            Set-ClientPatch 'apply'
            Start-ClientPatchGuard
            Start-Process -FilePath (Join-Path $game 'YuanShen.exe') -WorkingDirectory $game | Out-Null
            Write-Host 'Game launched. Use the local account listed in LOCAL-RUN.txt.'
        }
        'gm' {
            Start-LocalServer
            $configuration = Get-Config
            Start-Process ($baseUrl + '/gm?token=' + [Uri]::EscapeDataString($configuration.server.gm.accessToken))
        }
        'build' {
            if (Get-Server) { throw 'Stop LunaGC before rebuilding its JAR.' }
            Get-Java | Out-Null
            $env:GRADLE_USER_HOME = Join-Path $local 'gradle'
            & (Join-Path $repo 'gradlew.bat') --no-daemon jar -PskipHandbook=1
            if ($LASTEXITCODE -ne 0) { throw 'Server build failed.' }
        }
        'buildpatch' {
            $env:CARGO_HOME = Join-Path $local 'cargo'
            $env:RUSTUP_HOME = Join-Path $local 'rustup'
            $cargo = Join-Path $env:CARGO_HOME 'bin\cargo.exe'
            if (-not (Test-Path -LiteralPath $cargo)) { throw 'Portable Rust is missing under local\cargo.' }
            Push-Location -LiteralPath (Join-Path $repo 'patch')
            try {
                & $cargo build --release --locked
                if ($LASTEXITCODE -ne 0) { throw 'Client patch build failed.' }
            } finally { Pop-Location }
        }
    }
} catch {
    Write-Error $_
    exit 1
} finally {
    if ($locked) { $mutex.ReleaseMutex() }
    $mutex.Dispose()
}
