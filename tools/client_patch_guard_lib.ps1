# Client-only recovery runs on Windows independently of the Compose services.
# All entry points share a check mutex; a separate mutex owns watcher lifetime.
$ErrorActionPreference = 'Stop'

function Get-ClientPatchPowerShell {
    # The caller may be PowerShell 7; patch_game requires Windows PowerShell.
    $hostPath = Join-Path $env:SystemRoot 'System32\WindowsPowerShell\v1.0\powershell.exe'
    if (-not (Test-Path -LiteralPath $hostPath -PathType Leaf)) {
        throw "Windows PowerShell is not installed at $hostPath"
    }
    return $hostPath
}

function Get-ClientGameProcesses {
    @(Get-Process -Name YuanShen, GenshinImpact -ErrorAction SilentlyContinue)
}

function New-ClientPatchContext([string] $Repo, [string] $GamePath, [string] $EntryScript) {
    $game = [IO.Path]::GetFullPath($GamePath).TrimEnd('\', '/')
    $sha = [Security.Cryptography.SHA256]::Create()
    try {
        $identity = -join ($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($game.ToUpperInvariant())) |
            ForEach-Object { $_.ToString('x2') })
        $sourceHashes = foreach ($path in $EntryScript, (Join-Path $Repo 'tools\client_patch_guard_lib.ps1')) {
            -join ($sha.ComputeHash([IO.File]::ReadAllBytes($path)) | ForEach-Object { $_.ToString('x2') })
        }
        $sourceStamp = -join ($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($sourceHashes -join ':')) |
            ForEach-Object { $_.ToString('x2') })
    } finally { $sha.Dispose() }
    $directory = Join-Path $Repo "debug\client-patch\$identity"
    New-Item -ItemType Directory -Path $directory -Force | Out-Null
    return @{
        GamePath = $game; Repo = $Repo; Directory = $directory; SourceStamp = $sourceStamp
        MutexPrefix = "Local\LunaGC.ClientPatch.$identity"
        StatusPath = Join-Path $directory 'status.json'
        LogPath = Join-Path $directory 'guard.log'
        InstanceId = ''; Watching = $false; LastCheckUtc = $null
        State = 'unchecked'; Message = ''; LastFingerprint = $null
        GameWasRunning = $false; Failures = 0; RetryAt = [DateTime]::MinValue
    }
}

function Enter-ClientPatchMutex([string] $Name, [int] $TimeoutMs = 30000) {
    $mutex = [Threading.Mutex]::new($false, $Name)
    try {
        try { $acquired = $mutex.WaitOne($TimeoutMs) }
        catch [Threading.AbandonedMutexException] { $acquired = $true }
        if (-not $acquired) { $mutex.Dispose(); return $null }
        return $mutex
    } catch { $mutex.Dispose(); throw }
}

function Exit-ClientPatchMutex($Mutex) {
    if ($null -ne $Mutex) { $Mutex.ReleaseMutex(); $Mutex.Dispose() }
}

function Read-ClientPatchState($Context) {
    if (-not (Test-Path -LiteralPath $Context.StatusPath)) { return $null }
    try { Get-Content -LiteralPath $Context.StatusPath -Raw | ConvertFrom-Json }
    catch { return $null }
}

function Test-ClientPatchWatcher($Context) {
    $lock = Enter-ClientPatchMutex "$($Context.MutexPrefix).Watch" 0
    if ($null -ne $lock) { Exit-ClientPatchMutex $lock; return $false }
    $state = Read-ClientPatchState $Context
    if (-not $state -or -not $state.Watching) { return $false }
    try {
        # PowerShell 7 converts JSON ISO timestamps to DateTime automatically.
        # Parsing that object again stringifies away its UTC Kind and applies
        # the local offset twice. Preserve date objects; parse only strings.
        $heartbeat = $state.HeartbeatUtc
        if ($heartbeat -is [DateTimeOffset]) { $heartbeatUtc = $heartbeat.UtcDateTime }
        elseif ($heartbeat -is [DateTime]) { $heartbeatUtc = $heartbeat.ToUniversalTime() }
        else {
            $heartbeatUtc = [DateTimeOffset]::Parse([string] $heartbeat,
                [Globalization.CultureInfo]::InvariantCulture).UtcDateTime
        }
        return ([DateTime]::UtcNow - $heartbeatUtc).TotalSeconds -lt 120
    } catch { return $false }
}

function Save-ClientPatchState($Context) {
    $instance = $Context.InstanceId
    $watching = $Context.Watching
    $watchPid = $PID
    $sourceStamp = $Context.SourceStamp
    # An explicit check must not erase a concurrent watcher's identity.
    if (-not $watching -and (Test-ClientPatchWatcher $Context)) {
        $existing = Read-ClientPatchState $Context
        $instance = $existing.InstanceId
        $watchPid = $existing.Pid
        $watching = $true
        # Checking with newer code does not reload the existing daemon.
        $sourceStamp = $existing.SourceStamp
    }
    $state = [ordered]@{
        GamePath = $Context.GamePath; State = $Context.State; Message = $Context.Message
        Watching = $watching; Pid = $watchPid; InstanceId = $instance
        SourceStamp = $sourceStamp
        HeartbeatUtc = [DateTime]::UtcNow.ToString('o'); LastCheckUtc = $Context.LastCheckUtc
    }
    $temporary = "$($Context.StatusPath).$([Guid]::NewGuid().ToString('N')).tmp"
    [IO.File]::WriteAllText($temporary, ($state | ConvertTo-Json), [Text.UTF8Encoding]::new($false))
    try {
        if (Test-Path -LiteralPath $Context.StatusPath) {
            [IO.File]::Replace($temporary, $Context.StatusPath, [NullString]::Value)
        } else { [IO.File]::Move($temporary, $Context.StatusPath) }
    } finally {
        if (Test-Path -LiteralPath $temporary) { Remove-Item -LiteralPath $temporary }
    }
}

function Write-ClientPatchLog($Context, [string] $Message) {
    # Bound diagnostics without repeatedly reading or hashing large client DLLs.
    if ((Test-Path -LiteralPath $Context.LogPath) -and
        (Get-Item -LiteralPath $Context.LogPath).Length -gt 2MB) {
        Move-Item -LiteralPath $Context.LogPath -Destination "$($Context.LogPath).previous" -Force
    }
    [IO.File]::AppendAllText($Context.LogPath, "[$([DateTime]::UtcNow.ToString('o'))] $Message`r`n")
}

function Get-ClientPatchFingerprint($Context) {
    $plugins = Join-Path $Context.GamePath 'YuanShen_Data\Plugins'
    $files = @()
    if (Test-Path -LiteralPath $plugins) {
        $files += Get-ChildItem -LiteralPath $plugins -File |
            Where-Object { $_.Name -like 'Astrolabe*' -or $_.Name -like 'AccountPlatNative*' }
    }
    foreach ($relative in 'patch\target\release\ext.dll', 'src\main\resources\keys\passport_1024.der', 'tools\patch_game.ps1') {
        $path = Join-Path $Context.Repo $relative
        if (Test-Path -LiteralPath $path) { $files += Get-Item -LiteralPath $path }
    }
    return (($files | Sort-Object FullName | ForEach-Object {
        "$($_.FullName)|$($_.Length)|$($_.LastWriteTimeUtc.Ticks)"
    }) -join "`n")
}

function Invoke-ClientPatchProgram($Context, [string] $Mode) {
    $previousGamePath = $env:GAME_PATH
    $previousPreference = $ErrorActionPreference
    try {
        $env:GAME_PATH = $Context.GamePath
        $ErrorActionPreference = 'Continue'
        $output = & (Get-ClientPatchPowerShell) -NoProfile -ExecutionPolicy Bypass `
            -File (Join-Path $Context.Repo 'tools\patch_game.ps1') -Mode $Mode 2>&1
        $exitCode = $LASTEXITCODE
    } finally {
        $env:GAME_PATH = $previousGamePath
        $ErrorActionPreference = $previousPreference
    }
    Write-ClientPatchLog $Context ("patch $Mode exited $exitCode`r`n" + ($output -join "`r`n"))
    return $exitCode
}

function Invoke-ClientPatchCheck($Context) {
    $lock = Enter-ClientPatchMutex "$($Context.MutexPrefix).Check"
    if ($null -eq $lock) { throw 'Another client patch check did not finish in 30 seconds.' }
    try {
        $Context.LastCheckUtc = [DateTime]::UtcNow.ToString('o')
        if (@(Get-ClientGameProcesses).Count -gt 0) {
            $Context.State = 'game-running'
            $Context.Message = 'Game is running; no client files were changed. Recovery waits for exit.'
            return
        }
        if ((Invoke-ClientPatchProgram $Context 'status') -ne 0) {
            if (-not (Test-Path -LiteralPath (Join-Path $Context.Repo 'patch\target\release\ext.dll'))) {
                throw 'Patch build is missing. Run task build:patch, then task patch:guard.'
            }
            # A game may have launched while status was reading the DLLs.
            if (@(Get-ClientGameProcesses).Count -gt 0) {
                $Context.State = 'game-running'
                $Context.Message = 'Game started during the check; recovery waits for exit.'
                return
            }
            $applyCode = Invoke-ClientPatchProgram $Context 'apply'
            if ($applyCode -ne 0) { throw "Client patch apply failed (exit $applyCode). See $($Context.LogPath)" }
            $statusCode = Invoke-ClientPatchProgram $Context 'status'
            if ($statusCode -ne 0) { throw "Client patch validation failed after apply (exit $statusCode). See $($Context.LogPath)" }
        }
        $Context.State = 'ready'
        $Context.Message = 'Client patch is ready for the next launch.'
    } catch {
        $Context.State = 'error'; $Context.Message = $_.Exception.Message
        Write-ClientPatchLog $Context $Context.Message
        throw
    } finally {
        try { Save-ClientPatchState $Context }
        finally { Exit-ClientPatchMutex $lock }
    }
}

function Invoke-ClientPatchCycle($Context) {
    $running = @(Get-ClientGameProcesses).Count -gt 0
    if ($running) {
        $Context.GameWasRunning = $true
        $Context.State = 'game-running'
        $Context.Message = 'Game is running; recovery waits for exit.'
        Save-ClientPatchState $Context
        return
    }
    if ($Context.Failures -gt 0 -and -not $Context.GameWasRunning -and
        [DateTime]::UtcNow -lt $Context.RetryAt) {
        Save-ClientPatchState $Context
        return
    }
    $fingerprint = Get-ClientPatchFingerprint $Context
    $changed = $fingerprint -ne $Context.LastFingerprint
    $due = [DateTime]::UtcNow -ge $Context.RetryAt
    if ($Context.GameWasRunning -or ($due -and ($changed -or $Context.Failures -gt 0))) {
        $Context.GameWasRunning = $false
        try {
            Invoke-ClientPatchCheck $Context
            $Context.Failures = 0
            $Context.RetryAt = [DateTime]::MinValue
            $Context.LastFingerprint = Get-ClientPatchFingerprint $Context
        } catch {
            $Context.Failures++
            $delay = [Math]::Min(60, 3 * [Math]::Pow(2, [Math]::Min(5, $Context.Failures - 1)))
            $Context.RetryAt = [DateTime]::UtcNow.AddSeconds($delay)
            Write-ClientPatchLog $Context "Recovery will retry in $delay seconds."
        }
    }
    Save-ClientPatchState $Context
}

function Invoke-ClientPatchWatch($Context, [string] $InstanceId) {
    $watchLock = Enter-ClientPatchMutex "$($Context.MutexPrefix).Watch" 0
    if ($null -eq $watchLock) { return }
    $Context.InstanceId = if ($InstanceId) { $InstanceId } else { [Guid]::NewGuid().ToString('N') }
    $Context.Watching = $true
    $stopPath = Join-Path $Context.Directory "stop.$($Context.InstanceId)"
    try {
        # Publish the handshake before expensive status validation starts.
        $Context.Message = 'Client patch watcher started.'
        Save-ClientPatchState $Context
        Write-ClientPatchLog $Context "Watcher $($Context.InstanceId) started (PID $PID)."
        while (-not (Test-Path -LiteralPath $stopPath)) {
            try { Invoke-ClientPatchCycle $Context }
            catch {
                $Context.State = 'error'; $Context.Message = $_.Exception.Message
                $Context.Failures++
                $Context.RetryAt = [DateTime]::UtcNow.AddSeconds(30)
                # A transient filesystem failure must not silently end recovery.
                try { Write-ClientPatchLog $Context "Watcher check failed; retrying: $($Context.Message)" } catch { }
                try { Save-ClientPatchState $Context } catch { }
            }
            Start-Sleep -Seconds 3
        }
    } finally {
        $Context.Watching = $false
        $Context.State = 'stopped'; $Context.Message = 'Client patch watcher stopped.'
        # Release lifetime ownership before publishing stopped state.
        Exit-ClientPatchMutex $watchLock
        Save-ClientPatchState $Context
        if (Test-Path -LiteralPath $stopPath) { Remove-Item -LiteralPath $stopPath }
        Write-ClientPatchLog $Context "Watcher $($Context.InstanceId) stopped."
    }
}

function Start-ClientPatchWatch($Context, [string] $EntryScript) {
    $startLock = Enter-ClientPatchMutex "$($Context.MutexPrefix).Start"
    if ($null -eq $startLock) { throw 'Another watcher start did not finish in 30 seconds.' }
    try {
        if (Test-ClientPatchWatcher $Context) {
            $existing = Read-ClientPatchState $Context
            if ($existing.SourceStamp -eq $Context.SourceStamp) {
                Invoke-ClientPatchCheck $Context
                Write-Host "Client patch watcher already running for $($Context.GamePath)."
                return
            }
            Write-Host 'Client patch guard source changed; replacing the watcher cooperatively.'
            Stop-ClientPatchWatch $Context
        }
        $owner = Enter-ClientPatchMutex "$($Context.MutexPrefix).Watch" 0
        if ($null -eq $owner) { throw 'Existing patch watcher has no recent heartbeat; inspect guard.log before starting another.' }
        Exit-ClientPatchMutex $owner
        Invoke-ClientPatchCheck $Context
        $instance = [Guid]::NewGuid().ToString('N')
        # Windows paths cannot contain quotes; explicit quoting preserves spaces.
        $arguments = '-NoProfile -ExecutionPolicy Bypass -File "{0}" -Mode watch -GamePath "{1}" -InstanceId {2}' -f `
            $EntryScript, $Context.GamePath, $instance
        # Keep ShellExecute enabled: redirected streams can retain an inherited
        # caller pipe and prevent a captured task process from ever completing.
        # The watcher writes its own bounded guard.log and atomic status.json.
        $process = Start-Process -FilePath (Get-ClientPatchPowerShell) -ArgumentList $arguments `
            -WorkingDirectory $Context.Repo -WindowStyle Hidden -PassThru
        $deadline = [DateTime]::UtcNow.AddSeconds(20)
        do {
            Start-Sleep -Milliseconds 200
            $state = Read-ClientPatchState $Context
            if ($state -and $state.InstanceId -eq $instance -and (Test-ClientPatchWatcher $Context)) {
                Write-Host "Client patch watcher running independently (PID $($state.Pid)); status: $($Context.StatusPath)"
                return
            }
            if ($process.HasExited) { throw "Client patch watcher exited during startup (exit $($process.ExitCode)); inspect debug/client-patch logs." }
        } while ([DateTime]::UtcNow -lt $deadline)
        # Cooperative cancellation targets only the instance launched above.
        [IO.File]::WriteAllText((Join-Path $Context.Directory "stop.$instance"), '')
        throw 'Client patch watcher did not confirm startup within 20 seconds.'
    } finally { Exit-ClientPatchMutex $startLock }
}

function Stop-ClientPatchWatch($Context) {
    $lock = Enter-ClientPatchMutex "$($Context.MutexPrefix).Start"
    if ($null -eq $lock) { throw 'Another watcher operation did not finish in 30 seconds.' }
    try {
        $owner = Enter-ClientPatchMutex "$($Context.MutexPrefix).Watch" 0
        if ($null -ne $owner) {
            Exit-ClientPatchMutex $owner
            Write-Host 'Client patch watcher is not running.'
            return
        }
        $state = Read-ClientPatchState $Context
        if (-not $state -or $state.InstanceId -notmatch '^[a-f0-9]{32}$') {
            throw 'Cannot identify the active watcher safely; inspect debug/client-patch logs.'
        }
        [IO.File]::WriteAllText((Join-Path $Context.Directory "stop.$($state.InstanceId)"), '')
        $deadline = [DateTime]::UtcNow.AddSeconds(30)
        do {
            Start-Sleep -Milliseconds 200
            $owner = Enter-ClientPatchMutex "$($Context.MutexPrefix).Watch" 0
            if ($null -ne $owner) {
                Exit-ClientPatchMutex $owner
                $finalState = Read-ClientPatchState $Context
                if ($finalState -and -not $finalState.Watching) {
                    Write-Host 'Client patch watcher stopped; no game process was terminated.'
                    return
                }
            }
        } while ([DateTime]::UtcNow -lt $deadline)
        throw 'Watcher stop is pending; it may still be finishing a patch operation. No process was killed.'
    } finally { Exit-ClientPatchMutex $lock }
}

function Invoke-ClientPatchGuard {
    param([string] $Mode, [string] $GamePath, [string] $InstanceId, [string] $Repo, [string] $EntryScript)
    if (-not $GamePath) {
        $previousPreference = $ErrorActionPreference
        try {
            $ErrorActionPreference = 'Continue'
            $resolved = & (Get-ClientPatchPowerShell) -NoProfile -ExecutionPolicy Bypass `
                -File (Join-Path $Repo 'tools\game_path.ps1') 2>$null
            $resolveCode = $LASTEXITCODE
        } finally { $ErrorActionPreference = $previousPreference }
        if ($resolveCode -eq 0 -and $resolved) { $GamePath = ($resolved | Select-Object -Last 1).Trim() }
        elseif ($resolveCode -ne 2) {
            throw 'Configured game path is invalid. Check GAME_PATH in the environment, .env.local or .env.'
        }
    }
    if (-not $GamePath) {
        if ($Mode -in 'start', 'status', 'stop') {
            Write-Host 'No local game client found; client patch watcher skipped (Compose server remains independent).'
            return
        }
        throw 'No local game client found. Set GAME_PATH in .env.local or supply -GamePath.'
    }
    if (-not (Test-Path -LiteralPath (Join-Path $GamePath 'YuanShen_Data\Plugins') -PathType Container)) {
        throw "Game client Plugins directory not found: $GamePath"
    }
    $context = New-ClientPatchContext $Repo $GamePath $EntryScript
    switch ($Mode) {
        'start' { Start-ClientPatchWatch $context $EntryScript }
        'watch' { Invoke-ClientPatchWatch $context $InstanceId }
        'check' { Invoke-ClientPatchCheck $context; Write-Host $context.Message }
        'stop' { Stop-ClientPatchWatch $context }
        'status' {
            $state = Read-ClientPatchState $context
            if (-not $state) { Write-Host "Client patch watcher has not run for $GamePath."; return }
            $state.Watching = Test-ClientPatchWatcher $context
            $state | ConvertTo-Json
        }
        default { throw "Unknown guard mode: $Mode" }
    }
}
