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
$headerPath = Join-Path $PSScriptRoot 'shared.h'
$headerSHA = (Get-FileHash -LiteralPath $headerPath -Algorithm SHA256).Hash.ToLowerInvariant()
if ($headerSHA -ne '689c722e78d116d8de876238eea125958d289a1c2b7971ad7b34baa3cacb61b1') { throw 'shared header epoch drift' }
$buildParent = Join-Path $private 'a-clear48-trace-witness'
$out = Join-Path $buildParent "build-$BuildId"
if (Test-Path -LiteralPath $out) { throw 'build output already exists' }
$null = New-Item -ItemType Directory -Path $out
$sourcePins = @()
foreach ($name in @('shared.h','child.c','witness.asm','parent.c','build.ps1')) {
    $path = Join-Path $PSScriptRoot $name
    $sourcePins += [ordered]@{ name=$name; bytes=(Get-Item -LiteralPath $path).Length; sha256=(Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant() }
    Copy-Item -LiteralPath $path -Destination (Join-Path $out $name)
}
$sourcePins | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $out 'source-manifest.private.json') -Encoding UTF8
Set-Content -LiteralPath (Join-Path $out 'build-epoch.h') -Encoding Ascii -Value "#define C48_SHARED_HEADER_SHA256_HEX `"$headerSHA`""
$cl = ($inventory.tools | Where-Object name -eq 'cl.exe').path
$ml = ($inventory.tools | Where-Object name -eq 'ml64.exe').path
$link = ($inventory.tools | Where-Object name -eq 'link.exe').path
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
"$ml" /nologo /c /Fowitness.obj witness.asm > asm.log 2>&1
if errorlevel 1 exit /b 2
"$cl" /nologo /c /std:c11 /Od /Oi /W4 /WX /GS /MT /Z7 /FC /showIncludes /Bt+ /FAs /Fachild-listing.asm /Fochild.obj /FIbuild-epoch.h child.c > child-compile.log 2>&1
if errorlevel 1 exit /b 3
"$link" /nologo /MACHINE:X64 /INCREMENTAL:NO /OPT:NOICF /DEBUG /MAP:child.map /VERBOSE:LIB /OUT:child.exe child.obj witness.obj > child-link.log 2>&1
if errorlevel 1 exit /b 4
"$cl" /nologo /c /std:c11 /Od /Oi /W4 /WX /GS /MT /Z7 /FC /showIncludes /Bt+ /FAs /Faparent-listing.asm /Foparent.obj /FIbuild-epoch.h parent.c > parent-compile.log 2>&1
if errorlevel 1 exit /b 5
"$link" /nologo /MACHINE:X64 /INCREMENTAL:NO /OPT:NOICF /DEBUG /MAP:parent.map /VERBOSE:LIB /OUT:parent.exe parent.obj bcrypt.lib > parent-link.log 2>&1
if errorlevel 1 exit /b 6
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
$record = [ordered]@{ utc=[DateTime]::UtcNow.ToString('o'); exit=$ownedBuildExitCode; inventorySha256=(Get-FileHash -LiteralPath $inventoryPath -Algorithm SHA256).Hash.ToLowerInvariant(); source=$sourcePins; sharedHeaderSHA256=$headerSHA; tools=$inventory.tools; files=@(); dependencyProofComplete=$false; fixtureExecuted=$false; original904Linked=$false }
foreach ($file in Get-ChildItem -LiteralPath $out -File) { $record.files += [ordered]@{name=$file.Name; bytes=$file.Length; sha256=(Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash.ToLowerInvariant()} }
$record | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $out 'build-record.private.json') -Encoding UTF8
if ($ownedBuildExitCode -ne 0) { throw "build failed with exit $ownedBuildExitCode; logs preserved" }
Write-Output "Built own witness child/parent in $out; independent dependency/PE/source review required before execution."
