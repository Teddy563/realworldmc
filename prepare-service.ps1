param([Parameter(Mandatory)][string]$ServiceDirectory)
$ErrorActionPreference = 'Stop'
$service = [IO.Path]::GetFullPath($ServiceDirectory)
if (-not (Test-Path (Join-Path $service 'package-lock.json'))) { throw 'Missing service lockfile.' }
Push-Location $service
try {
    & npm.cmd ci --omit=dev --ignore-scripts --no-audit --no-fund
    if ($LASTEXITCODE -ne 0) { throw 'Could not install locked service dependencies.' }
} finally { Pop-Location }
# Pin the official Windows runtime and verify before extracting or executing it.
$version = '24.21.0'
$expected = '158f7685b44de51f6c0df1d153526cbcd3e1bc739a8dfc607721cef75de9e541'
$archive = Join-Path $service "node-v$version-win-x64.zip"
$destination = Join-Path $service 'runtime/win-x64'
New-Item -ItemType Directory -Force $destination | Out-Null
Invoke-WebRequest -Uri "https://nodejs.org/dist/v$version/node-v$version-win-x64.zip" -OutFile $archive
if ((Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash.ToLowerInvariant() -ne $expected) { throw 'Node runtime checksum mismatch.' }
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [IO.Compression.ZipFile]::OpenRead($archive)
try {
    foreach ($name in @('node.exe','LICENSE')) {
        $entry = $zip.GetEntry("node-v$version-win-x64/$name")
        if (-not $entry) { throw "Node archive is missing $name" }
        [IO.Compression.ZipFileExtensions]::ExtractToFile($entry, (Join-Path $destination $name), $true)
    }
} finally { $zip.Dispose() }
Remove-Item -LiteralPath $archive
[IO.File]::WriteAllText((Join-Path $destination 'version.txt'), "Node.js $version; official win-x64 runtime; SHA256 $expected`n")
