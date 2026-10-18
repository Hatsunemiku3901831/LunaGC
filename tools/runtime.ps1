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

switch ($Mode) {
    'start' {
        Invoke-Compose -Arguments @('build', 'server')
        Invoke-Compose -Arguments @(
            'up', '--detach', '--wait', '--wait-timeout', "$StartupTimeoutSeconds", 'mongo', 'server'
        )
        Write-Host 'LunaGC is running in Docker Compose. Closing this terminal does not stop it.'
    }
    'build' { Invoke-Compose -Arguments @('build', 'server') }
    'stop' { Invoke-Compose -Arguments @('stop', 'server') }
    'status' { Invoke-Compose -Arguments @('ps', '--all') }
    'logs' { Invoke-Compose -Arguments @('logs', '--follow', '--tail', '100', 'server') }
}
