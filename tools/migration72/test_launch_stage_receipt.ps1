param([Parameter(Mandatory=$true)][string]$OutputDirectory)
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'launch_stage_receipt.ps1')
if (Test-Path -LiteralPath $OutputDirectory) { throw 'Test output exists; retain it' }
$null = New-Item -ItemType Directory -Path $OutputDirectory
$frequency = [Diagnostics.Stopwatch]::Frequency
$cases = @()
function Assert-Contract([string]$Name,[bool]$Condition) {
    if (-not $Condition) { throw ('Failed: '+$Name) }
    $script:cases += @{name=$Name;passed=$true}
}
$request = New-LaunchRequest (Join-Path $OutputDirectory 'phases') (100L*$frequency) $frequency $true 'success' $false
Assert-Contract 'fixed-90-second-origin' ($request.deadlineQpc -eq 190L*$frequency)
Assert-Contract 'before-deadline' ((Get-LaunchAdmission $request (189L*$frequency)) -eq 'allowed')
Assert-Contract 'exact-deadline' ((Get-LaunchAdmission $request (190L*$frequency)) -eq 'deadline')
Assert-Contract 'after-deadline' ((Get-LaunchAdmission $request (191L*$frequency)) -eq 'deadline')
$parsed = Read-LaunchRequest (Join-Path $request.runDirectory 'request.private.json')
Assert-Contract 'cross-process-descriptor-keeps-origin' ($parsed.startQpc -eq $request.startQpc -and $parsed.deadlineQpc -eq $request.deadlineQpc)
Assert-Contract 'closed-request-readable' ((Get-Content -LiteralPath (Join-Path $request.runDirectory 'request.private.json') -Raw | ConvertFrom-Json).totalSeconds -eq 90)
Write-LaunchStage $request 'dispatch-pending' @{}
Assert-Contract 'request-distinct-from-dispatch' ((Get-LaunchPhase $request) -eq 'dispatch-pending')
Write-LaunchStage $request 'dispatch-returned' @{helperPid=4242}
Assert-Contract 'dispatch-return-is-not-helper-entry' ((Get-LaunchPhase $request) -eq 'dispatch-returned')
Write-LaunchStage $request 'helper-entry' @{}
Assert-Contract 'helper-entry-is-not-launch' ((Get-LaunchPhase $request) -eq 'helper-entry')
Write-LaunchStage $request 'launch-entry' @{}
Assert-Contract 'launch-entry-observable' ((Get-LaunchPhase $request) -eq 'launch-entry')
Write-LaunchStage $request 'launch-result' @{status='launched'}
Assert-Contract 'before-deadline-success' ((Get-LaunchOutcome $request (189L*$frequency)).status -eq 'launched')
Assert-Contract 'late-result-never-success' ((Get-LaunchOutcome $request (191L*$frequency)).status -eq 'deadline')
Write-LaunchStage $request 'cancel' @{reason='test'}
Assert-Contract 'cancel-prevents-admission' ((Get-LaunchAdmission $request (189L*$frequency)) -eq 'cancelled')
Assert-Contract 'cancel-never-success' ((Get-LaunchOutcome $request (189L*$frequency)).status -eq 'cancelled')
$old = (Get-FileHash -LiteralPath (Join-Path $request.runDirectory 'launch-result.private.json') -Algorithm SHA256).Hash
$rejected = $false
try { Write-LaunchStage $request 'launch-result' @{status='changed'} } catch [IO.IOException] { $rejected=$true }
Assert-Contract 'existing-result-not-overwritten' ($rejected -and $old -eq (Get-FileHash -LiteralPath (Join-Path $request.runDirectory 'launch-result.private.json') -Algorithm SHA256).Hash)
$rejected = $false
try { $null = New-LaunchRequest $request.runDirectory (191L*$frequency) $frequency $true 'success' $false } catch { $rejected=$true }
Assert-Contract 'existing-run-cannot-reset-origin' $rejected
$record = [ordered]@{passed=$true;caseCount=$cases.Count;cases=$cases;totalSeconds=90;realUACRequests=0;targetLaunches=0;targetRPM=0;childProcesses=0}
$output = Join-Path $OutputDirectory 'result.private.json'
[IO.File]::WriteAllText($output, (($record|ConvertTo-Json -Depth 6)+[Environment]::NewLine),[Text.UTF8Encoding]::new($false))
[pscustomobject]@{passed=$true;caseCount=$cases.Count;output=$output}|ConvertTo-Json
