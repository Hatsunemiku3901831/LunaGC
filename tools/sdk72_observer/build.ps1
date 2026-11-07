[CmdletBinding()]
param([Parameter(Mandatory=$true)][string] $OutputDirectory)

$ErrorActionPreference = 'Stop'
$repo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$local = [IO.Path]::GetFullPath((Join-Path $repo 'local'))
$output = [IO.Path]::GetFullPath((Join-Path $repo $OutputDirectory))
if (-not $output.StartsWith($local + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'OutputDirectory must be inside this checkout local/.'
}
if (Test-Path -LiteralPath $output) { throw 'OutputDirectory must be new.' }
$vswhere = Join-Path ${env:ProgramFiles(x86)} 'Microsoft Visual Studio/Installer/vswhere.exe'
if (-not (Test-Path -LiteralPath $vswhere -PathType Leaf)) { throw 'Visual Studio discovery tool is missing.' }
$installation = & $vswhere -latest -products '*' -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 -property installationPath
if ($LASTEXITCODE -ne 0 -or -not $installation) { throw 'MSVC x64 tools were not found.' }
$vcvars = Join-Path $installation 'VC/Auxiliary/Build/vcvars64.bat'
if (-not (Test-Path -LiteralPath $vcvars -PathType Leaf)) { throw 'MSVC environment script is missing.' }
New-Item -ItemType Directory -Path $output | Out-Null
$source = [IO.Path]::GetFullPath($PSScriptRoot)
$command = 'call "{0}" >nul && ml64.exe /nologo /c /Foabi_check.obj "{1}\abi_check.asm" && cl.exe /nologo /W4 /WX /O2 /std:c11 /I "{1}" "{1}\observer.c" "{1}\fixture.c" abi_check.obj /Fe:fixture.exe' -f $vcvars, $source
Push-Location -LiteralPath $output
try {
    & $env:ComSpec /d /s /c $command
    if ($LASTEXITCODE -ne 0) { throw "Compiler returned $LASTEXITCODE" }
} finally {
    Pop-Location
}
Get-FileHash -Algorithm SHA256 -LiteralPath (Join-Path $output 'fixture.exe')
