param([Parameter(Mandatory=$true)][ValidatePattern('^[A-Za-z0-9_-]+$')][string]$BuildId)
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$private = Join-Path $root 'local/migration72/d02-resume-20261110b'
$inventoryPath = Join-Path $private 'a-clear48-backend/native-msvc-inventory.private.json'
if ((Get-FileHash -LiteralPath $inventoryPath -Algorithm SHA256).Hash.ToLowerInvariant() -ne 'b74b6c1b2e82538cfd57f2ccdb9ddd695827c2b83a0b5351c02a747ac074bc00') { throw 'inventory drift' }
$inventory = Get-Content -LiteralPath $inventoryPath -Raw | ConvertFrom-Json
foreach ($tool in $inventory.tools) {
    if ((Get-Item -LiteralPath $tool.path).Length -ne $tool.bytes -or (Get-FileHash -LiteralPath $tool.path -Algorithm SHA256).Hash.ToLowerInvariant() -ne $tool.sha256) { throw "tool drift: $($tool.name)" }
}
$buildParent = Join-Path $private 'a-clear48-native-implementation'
if (-not (Test-Path -LiteralPath $buildParent -PathType Container)) { throw 'implementation parent missing' }
$out = Join-Path $buildParent "build-$BuildId"
if (Test-Path -LiteralPath $out) { throw 'build output already exists' }
$null = New-Item -ItemType Directory -Path $out
$cl = ($inventory.tools | Where-Object name -eq 'cl.exe').path
$ml = ($inventory.tools | Where-Object name -eq 'ml64.exe').path
$link = ($inventory.tools | Where-Object name -eq 'link.exe').path
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'fixture.c') -Destination (Join-Path $out 'fixture.c')
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'fixture.asm') -Destination (Join-Path $out 'fixture.asm')
# Each command executes in the private directory, in a child shell with append-options cleared.
$cmd = @"
@echo off
setlocal
set CL=
set _CL_=
set LINK=
set _LINK_=
call "$($inventory.vcvars)" > vcvars.log 2>&1
if errorlevel 1 exit /b 1
set > environment.private.log
"$ml" /nologo /c /Fo"fixture-asm.obj" "fixture.asm" > asm.log 2>&1
if errorlevel 1 exit /b 2
"$cl" /nologo /c /Od /Oi /W4 /WX /GS /MT /Z7 /FC /showIncludes /Bt+ /FAs /Fa"fixture-c.asm" /Fo"fixture-c.obj" "fixture.c" > compile.log 2>&1
if errorlevel 1 exit /b 3
"$link" /nologo /MACHINE:X64 /INCREMENTAL:NO /OPT:NOICF /DEBUG /MAP:fixture.map /VERBOSE:LIB /OUT:fixture.exe fixture-c.obj fixture-asm.obj > link.log 2>&1
if errorlevel 1 exit /b 4
exit /b 0
"@
Set-Content -LiteralPath (Join-Path $out 'build.cmd') -Value $cmd -Encoding Ascii
Push-Location -LiteralPath $out
try {
    & $env:ComSpec /d /c build.cmd
    $ownedBuildExitCode = $LASTEXITCODE
} finally {
    Pop-Location
}
$record = [ordered]@{ utc = [DateTime]::UtcNow.ToString('o'); exit = $ownedBuildExitCode; inventorySha256 = (Get-FileHash -LiteralPath $inventoryPath -Algorithm SHA256).Hash.ToLowerInvariant(); tools = $inventory.tools; files = @(); includeDependencies = @(); libraryDependencies = @(); compilerDllCandidates = @(); dependencyProofComplete = $false }
foreach ($file in Get-ChildItem -LiteralPath $out -File) { $record.files += [ordered]@{ name=$file.Name; bytes=$file.Length; sha256=(Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash.ToLowerInvariant() } }
if (Test-Path -LiteralPath (Join-Path $out 'compile.log')) {
    $includes = Select-String -LiteralPath (Join-Path $out 'compile.log') -Pattern '^Note: including file:\s*(.+)$' | ForEach-Object { $_.Matches[0].Groups[1].Value.Trim() } | Select-Object -Unique
    foreach ($path in $includes) { if (Test-Path -LiteralPath $path -PathType Leaf) { $record.includeDependencies += [ordered]@{path=$path; sha256=(Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant()} } }
}
# /VERBOSE:LIB and /Bt+ logs remain authoritative; names resolved here are candidates until reviewed.
if (Test-Path -LiteralPath (Join-Path $out 'link.log')) {
    $paths = Select-String -LiteralPath (Join-Path $out 'link.log') -Pattern '(?i)(?:searching|loaded)\s+(.+?\.lib)' | ForEach-Object { $_.Matches[0].Groups[1].Value.Trim() } | Select-Object -Unique
    foreach ($path in $paths) { if (Test-Path -LiteralPath $path -PathType Leaf) { $record.libraryDependencies += [ordered]@{path=$path; sha256=(Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant()} } }
}
foreach ($name in @('c1.dll','c1xx.dll','c2.dll','mspdb140.dll')) {
    $path = Join-Path (Split-Path $cl) $name
    if (Test-Path -LiteralPath $path -PathType Leaf) { $record.compilerDllCandidates += [ordered]@{path=$path; sha256=(Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant()} }
}
$record | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $out 'build-record.private.json') -Encoding UTF8
if ($ownedBuildExitCode -ne 0) { throw "build failed with exit $($ownedBuildExitCode); logs preserved" }
Write-Output "Build completed in $out; dependencyProofComplete=false requires independent review before execution."
