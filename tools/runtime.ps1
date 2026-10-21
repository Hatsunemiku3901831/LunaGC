<#
Run the persistent Docker Compose stack from this checkout, independent of the caller's directory.
The start command builds first, starts detached, and returns after both services are healthy.
#>
[CmdletBinding()]
param(
    [ValidateSet('start', 'stop', 'status', 'build', 'logs')]
    [string] $Mode = 'status',
    [ValidateRange(30, 1800)]
    [int] $StartupTimeoutSeconds = 300
)

$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
$runtime = $env:LUNAGC_RUNTIME
if ([string]::IsNullOrWhiteSpace($runtime)) {
    foreach ($envName in @('.env.local', '.env')) {
        $envFile = Join-Path $repo $envName
        if (-not (Test-Path -LiteralPath $envFile)) { continue }
        foreach ($line in Get-Content -LiteralPath $envFile) {
            if ($line -match '^\s*LUNAGC_RUNTIME\s*=\s*["'']?([A-Za-z]+)["'']?\s*$') {
                $runtime = $Matches[1]
                break
            }
        }
        if ($runtime) { break }
    }
}
if ($runtime -eq 'portable') {
    function Invoke-Portable([string] $Action) {
        & powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $repo 'tools\local_windows.ps1') -Mode $Action
        if ($LASTEXITCODE -ne 0) { throw "Portable LunaGC action failed: $Action" }
    }
    switch ($Mode) {
        'start' {
            Invoke-Portable 'stop'
            Invoke-Portable 'build'
            Invoke-Portable 'start'
        }
        'stop' { Invoke-Portable 'stop' }
        'status' { Invoke-Portable 'status' }
        'build' { Invoke-Portable 'build' }
        'logs' { Get-Content -LiteralPath (Join-Path $repo 'local\logs\server-out.log') -Tail 100 -Wait }
    }
    exit 0
}
$composeFile = Join-Path $repo 'compose.yaml'
if (-not (Test-Path -LiteralPath $composeFile -PathType Leaf)) {
    throw "Compose configuration not found: $composeFile"
}
Set-Location -LiteralPath $repo

function Invoke-Compose {
    param([string[]] $Arguments)
    $composeArguments = @(
        'compose', '--project-name', 'lunagc',
        '--project-directory', $repo, '--file', $composeFile
    )
    & docker @composeArguments @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Docker Compose $($Arguments[0]) failed (exit $LASTEXITCODE)."
    }
}

function Invoke-ClientPatchGuard([string] $GuardMode) {
    if ($env:LUNAGC_CLIENT_PATCH_GUARD -match '^(0|false|off)$') {
        Write-Host 'Client patch guard disabled by LUNAGC_CLIENT_PATCH_GUARD.'
        return
    }
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File `
        (Join-Path $repo 'tools\client_patch_guard.ps1') -Mode $GuardMode
    if ($LASTEXITCODE -ne 0) {
        throw "Client patch guard $GuardMode failed (exit $LASTEXITCODE). Compose services remain running; inspect the patch guard log."
    }
}

switch ($Mode) {
    'start' {
        Invoke-Compose -Arguments @('build', 'server')
        Invoke-Compose -Arguments @(
            'up', '--detach', '--wait', '--wait-timeout', "$StartupTimeoutSeconds", 'mongo', 'server'
        )
        Invoke-ClientPatchGuard 'start'
        Write-Host 'LunaGC is running in Docker Compose. Closing this terminal does not stop it.'
    }
    'build' { Invoke-Compose -Arguments @('build', 'server') }
    'stop' { Invoke-Compose -Arguments @('stop', 'server') }
    'status' {
        Invoke-Compose -Arguments @('ps', '--all')
        Invoke-ClientPatchGuard 'status'
    }
    'logs' { Invoke-Compose -Arguments @('logs', '--follow', '--tail', '100', 'server') }
}
