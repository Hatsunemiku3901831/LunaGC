# Shared, side-effect-free checks for the portable runtime. Never evaluate .env as code.
function Get-SourceVersion([string] $Repository) {
    $source = Get-Content -LiteralPath (Join-Path $Repository 'build.gradle') -Raw
    if ($source -notmatch '(?m)^version\s*=\s*[''"]([0-9]+\.[0-9]+\.[0-9]+)[''"]\s*$') {
        throw 'Cannot read the numeric version from build.gradle.'
    }
    return $Matches[1]
}

function Get-PortableJarName([string] $Repository) {
    $name = $env:LUNAGC_JAR
    if ([string]::IsNullOrWhiteSpace($name)) { $name = 'LunaGC-' + (Get-SourceVersion $Repository) + '.jar' }
    # Also keep metacharacters out of the gradlew.bat interpreter boundary.
    if ($name -notmatch '^[A-Za-z0-9][A-Za-z0-9._-]*\.jar$') {
        throw 'LUNAGC_JAR must be a simple .jar filename in this checkout (letters, digits, dot, dash, underscore).'
    }
    return $name
}

function Resolve-PortablePath([string] $Repository, [string] $Value) {
    if ([string]::IsNullOrWhiteSpace($Value)) { throw 'Missing folderStructure path in config.json.' }
    if (-not [IO.Path]::IsPathRooted($Value)) { $Value = Join-Path $Repository $Value }
    return [IO.Path]::GetFullPath($Value).TrimEnd('\', '/')
}

function Resolve-ClientTrustBundlePath([string] $Repository) {
    $selected = $env:LUNAGC_TRUST_BUNDLE
    if ([string]::IsNullOrWhiteSpace($selected)) {
        $selected = 'local/migration72/trust/client72-trust.json'
    }
    $path = Resolve-PortablePath $Repository $selected
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw 'CN72 trust bundle is missing. Generate it with tools/generate_client72_trust_bundle.py before client patching or launch.'
    }
    return $path
}

function Assert-PortableConfig([string] $Repository, $Configuration, [int] $MongoPort) {
    $root = [IO.Path]::GetFullPath($Repository).TrimEnd('\', '/') + '\'
    foreach ($name in @('resources', 'data')) {
        $path = Resolve-PortablePath $Repository $Configuration.folderStructure.$name
        $externalResources = $name -eq 'resources' -and $env:LUNAGC_RESOURCES_DIR -and
            $path -eq (Resolve-PortablePath $Repository $env:LUNAGC_RESOURCES_DIR)
        if (-not $path.StartsWith($root, [StringComparison]::OrdinalIgnoreCase) -and -not $externalResources) {
            throw "folderStructure.$name points outside this checkout: $path. Review copied config.json before starting; data is never moved automatically. External resources require matching LUNAGC_RESOURCES_DIR."
        }
        if (-not (Test-Path -LiteralPath $path -PathType Container)) {
            throw "folderStructure.$name directory is missing: $path"
        }
    }
    foreach ($name in @('server', 'game')) {
        $uri = $Configuration.databaseInfo.$name.connectionUri
        if ($uri -notmatch ('^mongodb://(localhost|127\.0\.0\.1):' + $MongoPort + '/?$')) {
            throw "databaseInfo.$name.connectionUri must target the portable MongoDB on 127.0.0.1:$MongoPort. Review copied config.json; database contents are never migrated automatically."
        }
    }
}

function Assert-ClientVersion([string] $Repository, [string] $GamePath) {
    $ini = Join-Path $GamePath 'config.ini'
    if (-not (Test-Path -LiteralPath $ini)) { throw "Cannot verify client version: missing $ini" }
    $text = Get-Content -LiteralPath $ini -Raw
    if ($text -notmatch '(?m)^game_version\s*=\s*(\d+\.\d+)\.\d+\s*$') {
        throw "Cannot verify game_version in $ini"
    }
    $client = $Matches[1]
    $server = (Get-SourceVersion $Repository) -replace '\.\d+$', ''
    if ($client -ne $server) {
        throw "Client version $client does not match server protocol $server. Adapt and verify protocol, resources and patch before switching clients; changing a version string is insufficient."
    }
}
