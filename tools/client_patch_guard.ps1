[CmdletBinding()]
param(
    [ValidateSet('start', 'check', 'status', 'stop', 'watch')]
    [string] $Mode = 'status',
    [string] $GamePath,
    [string] $InstanceId
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'client_patch_guard_lib.ps1')
Invoke-ClientPatchGuard -Mode $Mode -GamePath $GamePath -InstanceId $InstanceId `
    -Repo (Split-Path -Parent $PSScriptRoot) -EntryScript $PSCommandPath
