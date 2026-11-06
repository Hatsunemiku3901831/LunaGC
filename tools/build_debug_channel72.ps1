param([string]$OutputDirectory = 'local/migration72/debug-channel')
$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
$directory = [IO.Path]::GetFullPath((Join-Path $repo $OutputDirectory))
$localRoot = [IO.Path]::GetFullPath((Join-Path $repo 'local')) + [IO.Path]::DirectorySeparatorChar
if (-not $directory.StartsWith($localRoot, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Synthetic binaries and build evidence must stay under ignored local/.'
}
$target = Join-Path $directory 'channel_fixture.exe'
if (Get-CimInstance Win32_Process -Filter "Name='channel_fixture.exe'") {
    throw 'A synthetic fixture is still running; inspect it before rebuilding.'
}
$vswhere = Join-Path ${env:ProgramFiles(x86)} 'Microsoft Visual Studio/Installer/vswhere.exe'
$installation = & $vswhere -latest -products '*' -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 -property installationPath
if (-not $installation) { throw 'Existing MSVC BuildTools were not found.' }
$environmentScript = Join-Path $installation 'VC/Auxiliary/Build/vcvars64.bat'
$source = Join-Path $repo 'tools/DebuggerChannelFixture.cpp'
New-Item -ItemType Directory -Path $directory -Force | Out-Null
$object = Join-Path $directory 'channel_fixture.obj'
$symbols = Join-Path $directory 'channel_fixture.pdb'
$script = Join-Path $directory 'build.cmd'
$lines = @(
    '@echo off'
    ('call "' + $environmentScript + '" >nul')
    ('cl /nologo /std:c++17 /W4 /Od /MT /EHsc /Zi /Fe:"' + $target + '" /Fo:"' + $object + '" /Fd:"' + $symbols + '" "' + $source + '" /link /SUBSYSTEM:WINDOWS /DEBUG /INCREMENTAL:NO shell32.lib')
    'exit /b %errorlevel%'
)
$lines | Set-Content -LiteralPath $script -Encoding ASCII
& $script > (Join-Path $directory 'build.log') 2>&1
if ($LASTEXITCODE -ne 0) { throw 'Synthetic fixture compilation failed; inspect local build.log.' }
$manifest = [ordered]@{
    schemaVersion = 1
    scope = 'Owned synthetic target only; no game/library loading'
    source = 'tools/DebuggerChannelFixture.cpp'
    sourceSHA256 = (Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash.ToLowerInvariant()
    target = $target
    targetSHA256 = (Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash.ToLowerInvariant()
    targetBytes = (Get-Item -LiteralPath $target).Length
    compilerEnvironment = $environmentScript
    options = @('/std:c++17', '/Od', '/MT', '/EHsc', '/Zi', '/SUBSYSTEM:WINDOWS', '/DEBUG', '/INCREMENTAL:NO')
}
$manifest | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $directory 'build.json') -Encoding UTF8
[pscustomobject]@{ TargetSHA256 = $manifest.targetSHA256; Bytes = $manifest.targetBytes; GameLoaded = $false }
