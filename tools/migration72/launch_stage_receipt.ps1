# Self-owned launch coordination only. No RunAs, target APIs, or process termination.
# Match local_windows.ps1 when Windows PowerShell inherits PowerShell 7's module path.
Import-Module (Join-Path $PSHOME 'Modules/Microsoft.PowerShell.Utility/Microsoft.PowerShell.Utility.psd1') -ErrorAction Stop
function New-LaunchRequest {
    param([string]$RunDirectory, [long]$StartQpc, [long]$Frequency,
          [bool]$SelfStub, [string]$StubCase, [bool]$UserReady)
    if (Test-Path -LiteralPath $RunDirectory) { throw 'Run directory exists; no repeat' }
    $null = New-Item -ItemType Directory -Path $RunDirectory
    $request = [pscustomobject]@{
        runDirectory=[IO.Path]::GetFullPath($RunDirectory); runID=[Guid]::NewGuid().ToString('N');
        startQpc=$StartQpc; deadlineQpc=($StartQpc+90L*$Frequency); frequency=$Frequency;
        selfStub=$SelfStub; stubCase=$StubCase; userReady=$UserReady
    }
    Write-LaunchStage $request 'request' @{selfStub=$SelfStub;stubCase=$StubCase;userReady=$UserReady}
    return $request
}

function Read-LaunchRequest {
    param([string]$Path)
    $saved = Get-Content -LiteralPath $Path -Raw | ConvertFrom-Json
    $start = [long]$saved.startQpc
    $deadline = [long]$saved.deadlineQpc
    $frequency = [long]$saved.frequency
    if ($saved.task -ne 'R3-PROUD-CACHE1-LAUNCH-TIMEOUT' -or $saved.stage -ne 'request' -or
        $frequency -ne [Diagnostics.Stopwatch]::Frequency -or $deadline-$start -ne 90L*$frequency -or
        -not $saved.runID -or $saved.data.stubCase -notin @('success','denied','blocked','late-helper','preflight-late','cancel','')) {
        throw 'Invalid launch request contract'
    }
    return [pscustomobject]@{
        runDirectory=(Split-Path -Parent ([IO.Path]::GetFullPath($Path)));runID=$saved.runID;
        startQpc=$start;deadlineQpc=$deadline;frequency=$frequency;
        selfStub=[bool]$saved.data.selfStub;stubCase=[string]$saved.data.stubCase;userReady=[bool]$saved.data.userReady
    }
}

function Write-LaunchStage {
    param($Request, [string]$Stage, [hashtable]$Data)
    $record = [ordered]@{
        task='R3-PROUD-CACHE1-LAUNCH-TIMEOUT';runID=$Request.runID;stage=$Stage;
        utc=[DateTime]::UtcNow.ToString('o');qpc=([Diagnostics.Stopwatch]::GetTimestamp().ToString());
        startQpc=$Request.startQpc.ToString();deadlineQpc=$Request.deadlineQpc.ToString();
        frequency=$Request.frequency.ToString();totalSeconds=90;data=$Data
    }
    $path = Join-Path $Request.runDirectory ($Stage+'.private.json')
    $pending = $path+'.pending'
    $stream = [IO.File]::Open($pending,[IO.FileMode]::CreateNew,[IO.FileAccess]::Write,[IO.FileShare]::None)
    try {
        $bytes = [Text.Encoding]::UTF8.GetBytes(($record | ConvertTo-Json -Depth 10)+[Environment]::NewLine)
        $stream.Write($bytes,0,$bytes.Length)
    } finally { $stream.Dispose() }
    # Publish only after close. Move refuses an existing final file; evidence is never overwritten.
    [IO.File]::Move($pending,$path)
}

function Get-LaunchAdmission {
    param($Request, [long]$NowQpc=([Diagnostics.Stopwatch]::GetTimestamp()))
    if (Test-Path -LiteralPath (Join-Path $Request.runDirectory 'cancel.private.json')) { return 'cancelled' }
    if ($NowQpc -ge $Request.deadlineQpc) { return 'deadline' }
    return 'allowed'
}

function Get-LaunchPhase {
    param($Request)
    foreach ($stage in @('launch-result','launch-admission','launch-entry','helper-stopped','helper-entry','dispatch-failed','dispatch-returned','dispatch-pending','request')) {
        if (Test-Path -LiteralPath (Join-Path $Request.runDirectory ($stage+'.private.json'))) { return $stage }
    }
    return 'unrecorded'
}

function Get-LaunchOutcome {
    param($Request, [long]$NowQpc=([Diagnostics.Stopwatch]::GetTimestamp()))
    $admission = Get-LaunchAdmission $Request $NowQpc
    if ($admission -ne 'allowed') {
        return [pscustomobject]@{status=$admission;exitCode=124;phase=(Get-LaunchPhase $Request)}
    }
    foreach ($stage in @('dispatch-failed','helper-stopped','launch-result')) {
        $path = Join-Path $Request.runDirectory ($stage+'.private.json')
        if (Test-Path -LiteralPath $path) {
            $saved = Get-Content -LiteralPath $path -Raw | ConvertFrom-Json
            $ok = $stage -eq 'launch-result' -and $saved.data.status -eq 'launched'
            return [pscustomobject]@{status=$(if($ok){'launched'}else{'stopped'});exitCode=$(if($ok){0}else{1});phase=$stage}
        }
    }
    return [pscustomobject]@{status='pending';exitCode=$null;phase=(Get-LaunchPhase $Request)}
}
