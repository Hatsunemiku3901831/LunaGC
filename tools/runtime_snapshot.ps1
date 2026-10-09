<#
Capture a Docker server's immutable files and reconstruct a local recovery image.
Database and mounted state volumes are excluded by Docker export and remain separate.
#>
[CmdletBinding()]
param(
    [ValidateSet('capture','build','status')][string] $Mode='status',
    [string] $Container='',
    [string] $SnapshotPath='',
    [string] $Image=''
)
$ErrorActionPreference='Stop'
$repo=Split-Path -Parent $PSScriptRoot
Set-Location $repo

function Invoke-Docker {
    [CmdletBinding()]
    param([string[]] $DockerArguments,[string] $LogPath='')
    $savedPreference=$ErrorActionPreference
    try {
        $ErrorActionPreference='Continue'
        if($LogPath) {
            $output=& docker @DockerArguments 2>&1 | ForEach-Object { $_.ToString() } | Tee-Object -FilePath $LogPath
        } else { $output=& docker @DockerArguments 2>&1 }
        $code=$LASTEXITCODE
    } finally { $ErrorActionPreference=$savedPreference }
    if($code -ne 0) { throw ('Docker operation failed: '+$DockerArguments[0]) }
    return @($output | ForEach-Object { $_.ToString() })
}
function Write-Json($Value,[string] $Path) {
    $Value | ConvertTo-Json -Depth 20 | Set-Content -Encoding utf8 -LiteralPath $Path
}
function Digest([string] $Path) {
    return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
}
function Docker-Quoted([string] $Value) {
    if($Value.Contains("`r") -or $Value.Contains("`n") -or $Value.Contains([char]0)) {
        throw 'Multiline image configuration is unsupported'
    }
    return '"'+$Value.Replace('\','\\').Replace('"','\"').Replace('$','\$')+'"'
}
function Resolve-Snapshot([string] $Path) {
    if([string]::IsNullOrWhiteSpace($Path)) { throw 'Specify -SnapshotPath from the capture result' }
    if(-not [IO.Path]::IsPathRooted($Path)) { $Path=Join-Path $repo $Path }
    return [IO.Path]::GetFullPath($Path)
}

if($Mode -eq 'capture') {
    if([string]::IsNullOrWhiteSpace($Container)) {
        $Container=((Invoke-Docker @('compose','ps','--all','--quiet','server')) -join '').Trim()
    }
    if($Container -notmatch '^[A-Za-z0-9][A-Za-z0-9_.-]{0,127}$') { throw 'Expected one Docker server container' }
    $details=@(((Invoke-Docker @('inspect',$Container)) -join "`n") | ConvertFrom-Json)[0]
    if($details.State.Status -ne 'running') { throw 'Capture requires a running server for startup evidence' }
    if([string]::IsNullOrWhiteSpace($SnapshotPath)) {
        $SnapshotPath=Join-Path $repo ('debug/runtime-snapshots/'+(Get-Date -Format 'yyyyMMdd-HHmmss')+'-'+[guid]::NewGuid().ToString('N').Substring(0,8))
    }
    $SnapshotPath=Resolve-Snapshot $SnapshotPath
    if(Test-Path -LiteralPath $SnapshotPath) { throw 'Snapshot directory already exists; do not overwrite a recovery point' }
    New-Item -ItemType Directory -Path $SnapshotPath | Out-Null
    $metadata=[ordered]@{
        container_id=$details.Id; started_at=$details.State.StartedAt; image_id=$details.Image
        config=$details.Config; mounts=$details.Mounts; ports=$details.HostConfig.PortBindings
    }
    $metadataPath=Join-Path $SnapshotPath 'original-metadata.private.json'
    Write-Json $metadata $metadataPath
    Invoke-Docker @('cp',($details.Id+':/opt/lunagc/LunaGC.jar'),(Join-Path $SnapshotPath 'original.jar')) | Out-Null
    Invoke-Docker @('export','--output',(Join-Path $SnapshotPath 'original-rootfs.tar'),$details.Id) | Out-Null
    $manifest=[ordered]@{
        container_id=$details.Id; captured_at=[DateTime]::UtcNow.ToString('o')
        original_jar_sha256=Digest (Join-Path $SnapshotPath 'original.jar')
        rootfs_sha256=Digest (Join-Path $SnapshotPath 'original-rootfs.tar')
        metadata_sha256=Digest $metadataPath
        archive_bytes=(Get-Item -LiteralPath (Join-Path $SnapshotPath 'original-rootfs.tar')).Length
        mounted_volumes_included=$false
    }
    Write-Json $manifest (Join-Path $SnapshotPath 'capture.json')
    [pscustomobject]@{snapshot_path=$SnapshotPath;container_id=$details.Id;original_jar_sha256=$manifest.original_jar_sha256;mounted_volumes_included=$false} | ConvertTo-Json
    exit 0
}

$SnapshotPath=Resolve-Snapshot $SnapshotPath
$manifest=Get-Content -LiteralPath (Join-Path $SnapshotPath 'capture.json') -Raw | ConvertFrom-Json
$metadataPath=Join-Path $SnapshotPath 'original-metadata.private.json'
if((Digest $metadataPath) -ne $manifest.metadata_sha256) { throw 'Captured configuration changed' }
$metadata=Get-Content -LiteralPath $metadataPath -Raw | ConvertFrom-Json
if($metadata.container_id -ne $manifest.container_id) { throw 'Capture identity mismatch' }
$resultPath=Join-Path $SnapshotPath 'build-result.json'
if($Mode -eq 'status') {
    $result=if(Test-Path -LiteralPath $resultPath) { Get-Content -LiteralPath $resultPath -Raw | ConvertFrom-Json } else { $null }
    [pscustomobject]@{snapshot_path=$SnapshotPath;container_id=$manifest.container_id;archive_bytes=$manifest.archive_bytes;original_jar_sha256=$manifest.original_jar_sha256;image=$result.image;image_id=$result.image_id;mounted_volumes_included=$false} | ConvertTo-Json
    exit 0
}

if(Test-Path -LiteralPath $resultPath) {
    $cached=Get-Content -LiteralPath $resultPath -Raw | ConvertFrom-Json
    if([string]::IsNullOrWhiteSpace($Image)) { $Image=$cached.image }
    if($Image -notmatch '^[a-z0-9][a-z0-9._/:@-]{1,200}$') { throw 'Unsupported recovery image name' }
    if($Image -eq $cached.image -and $cached.configuration_verified -and
            $cached.metadata_sha256 -eq $manifest.metadata_sha256 -and
            $cached.rootfs_sha256 -eq $manifest.rootfs_sha256 -and
            $cached.original_jar_sha256 -eq $manifest.original_jar_sha256) {
        $actualId=((Invoke-Docker @('image','inspect','--format','{{.Id}}',$Image)) -join '').Trim()
        if($actualId -eq $cached.image_id) {
            $cached | Add-Member -NotePropertyName reused_verified_image -NotePropertyValue $true -Force
            $cached | ConvertTo-Json -Depth 8
            exit 0
        }
        throw 'Recovery image tag changed; inspect the new image before replacing this recovery point'
    }
}

if((Digest (Join-Path $SnapshotPath 'original-rootfs.tar')) -ne $manifest.rootfs_sha256 -or
        (Digest (Join-Path $SnapshotPath 'original.jar')) -ne $manifest.original_jar_sha256) {
    throw 'Captured files changed'
}
if([string]::IsNullOrWhiteSpace($Image)) {
    $Image='lunagc-server:recovery-'+$manifest.container_id.Substring(0,12)+'-'+(Digest (Join-Path $SnapshotPath 'capture.json')).Substring(0,8)
}
if($Image -notmatch '^[a-z0-9][a-z0-9._/:@-]{1,200}$') { throw 'Unsupported recovery image name' }
$config=$metadata.config
$lines=New-Object 'System.Collections.Generic.List[string]'
$lines.Add('FROM scratch')
$lines.Add('ADD original-rootfs.tar /')
foreach($entry in $config.Env) {
    $parts=$entry -split '=',2
    if($parts.Count -ne 2 -or $parts[0] -notmatch '^[A-Za-z_][A-Za-z0-9_]*$') { throw 'Invalid captured environment entry' }
    $lines.Add('ENV '+$parts[0]+'='+ (Docker-Quoted $parts[1]))
}
if($config.WorkingDir) { $lines.Add('WORKDIR '+(Docker-Quoted $config.WorkingDir)) }
if($config.User) {
    if($config.User -notmatch '^[A-Za-z0-9_-]+(:[A-Za-z0-9_-]+)?$') { throw 'Unsupported captured user' }
    $lines.Add('USER '+$config.User)
}
foreach($port in $config.ExposedPorts.PSObject.Properties.Name) {
    if($port -notmatch '^([0-9]{1,5})/(tcp|udp)$' -or [int]$Matches[1] -gt 65535) { throw 'Invalid exposed port' }
    $lines.Add('EXPOSE '+$port)
}
if($config.Volumes) {
    $lines.Add('VOLUME '+(ConvertTo-Json -InputObject @($config.Volumes.PSObject.Properties.Name) -Compress))
}
if($config.Entrypoint) { $lines.Add('ENTRYPOINT '+(ConvertTo-Json -InputObject @($config.Entrypoint) -Compress)) }
if($config.Cmd) { $lines.Add('CMD '+(ConvertTo-Json -InputObject @($config.Cmd) -Compress)) }
if($config.StopSignal) {
    if($config.StopSignal -notmatch '^(SIG[A-Z0-9]+|[0-9]+)$') { throw 'Invalid stop signal' }
    $lines.Add('STOPSIGNAL '+$config.StopSignal)
}
$health=$config.Healthcheck
if($health -and $health.Test.Count -gt 0) {
    if($health.Test[0] -eq 'NONE') { $lines.Add('HEALTHCHECK NONE') }
    else {
        $options=''
        foreach($field in @(@('Interval','interval'),@('Timeout','timeout'),@('StartPeriod','start-period'),@('StartInterval','start-interval'))) {
            $value=$health.($field[0])
            if($value -gt 0) { $options+=' --'+$field[1]+'='+[Math]::Ceiling($value/1000000)+'ms' }
        }
        if($health.Retries -gt 0) { $options+=' --retries='+$health.Retries }
        if($health.Test[0] -eq 'CMD') {
            $command=ConvertTo-Json -InputObject @($health.Test | Select-Object -Skip 1) -Compress
        } elseif($health.Test[0] -eq 'CMD-SHELL' -and $health.Test.Count -eq 2) {
            $command=$health.Test[1]
            if($command.Contains("`r") -or $command.Contains("`n")) { throw 'Multiline health command unsupported' }
        } else { throw 'Unsupported captured health command' }
        $lines.Add('HEALTHCHECK'+$options+' CMD '+$command)
    }
}
$lines.Add('LABEL lunagc.recovery.source-container='+ (Docker-Quoted $manifest.container_id))
$lines.Add('LABEL lunagc.recovery.original-jar-sha256='+ (Docker-Quoted $manifest.original_jar_sha256))
$lines | Set-Content -Encoding utf8 -LiteralPath (Join-Path $SnapshotPath 'Dockerfile')
@('*','!original-rootfs.tar') | Set-Content -Encoding ascii -LiteralPath (Join-Path $SnapshotPath '.dockerignore')
$buildOutput=Invoke-Docker -DockerArguments @('build','--tag',$Image,$SnapshotPath) -LogPath (Join-Path $SnapshotPath 'image-build.private.log')
$imageDetails=@(((Invoke-Docker @('image','inspect',$Image)) -join "`n") | ConvertFrom-Json)[0]
$actual=$imageDetails.Config
foreach($field in @('Env','Entrypoint','Cmd','User','WorkingDir','ExposedPorts','Volumes','Healthcheck','StopSignal')) {
    $wanted=ConvertTo-Json -InputObject $config.$field -Depth 8 -Compress
    $observed=ConvertTo-Json -InputObject $actual.$field -Depth 8 -Compress
    if($field -eq 'Env') {
        if(@(Compare-Object @($config.Env) @($actual.Env)).Count -ne 0) { throw 'Image environment differs from capture' }
    } elseif($wanted -ne $observed) { throw ('Image configuration differs: '+$field) }
}
$result=[ordered]@{
    image=$Image;image_id=$imageDetails.Id;container_id=$manifest.container_id
    rootfs_sha256=$manifest.rootfs_sha256;original_jar_sha256=$manifest.original_jar_sha256
    metadata_sha256=$manifest.metadata_sha256;configuration_verified=$true;mounted_volumes_included=$false
}
Write-Json $result $resultPath
[pscustomobject]$result | ConvertTo-Json
