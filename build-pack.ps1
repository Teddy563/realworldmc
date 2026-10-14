param([ValidateSet('1.21.1','26.2','26.3')][string]$MinecraftVersion = '1.21.1', [string]$ServiceDirectory = '', [string]$OutputDirectory = $PSScriptRoot, [switch]$VisualPack)
$ErrorActionPreference = 'Stop'
$jarName = if ($MinecraftVersion -ne '1.21.1') { "realworldmc-mc$MinecraftVersion-1.0.0.jar" } else { 'realworldmc-1.0.0.jar' }
$jar = Join-Path $PSScriptRoot $jarName
if ($MinecraftVersion -ne '1.21.1' -and $VisualPack) { throw 'The visual pack is only available for 1.21.1.' }
if (-not (Test-Path -LiteralPath $jar -PathType Leaf)) { throw 'Missing realworldmc-1.0.0.jar. Extract the complete 1.0.0 release ZIP first.' }
$index = if ($VisualPack) { 'modpack\visual.index.json' } else { 'modpack\modrinth.index.json' }
if ($MinecraftVersion -ne '1.21.1') { $index = "modpack\$MinecraftVersion.index.json" }
$suffix = if ($VisualPack) { '-visual' } else { '' }
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
$pack = Join-Path ([IO.Path]::GetFullPath($OutputDirectory)) "RealWorldMC-1.0.0-mc$MinecraftVersion$suffix.mrpack"
$temporaryPack = "$pack.$([guid]::NewGuid().ToString('N')).tmp"
Add-Type -AssemblyName System.IO.Compression, System.IO.Compression.FileSystem
$zip = $null
function Add-TextEntry($Archive, [string]$Name, [string]$Text) {
    $entry = $Archive.CreateEntry($Name)
    $writer = New-Object IO.StreamWriter($entry.Open(), (New-Object Text.UTF8Encoding $false))
    try { $writer.Write($Text) } finally { $writer.Dispose() }
}
try {
    $zip = [IO.Compression.ZipFile]::Open($temporaryPack, 'Create')
    [void][IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, (Join-Path $PSScriptRoot $index), 'modrinth.index.json')
    [void][IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, $jar, "overrides/mods/$jarName")
    [void][IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, (Join-Path $PSScriptRoot 'LICENSE'), 'overrides/RealWorldMC-LICENSE.txt')
    [void][IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, (Join-Path $PSScriptRoot 'NOTICE'), 'overrides/NOTICE')
    foreach ($notice in Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'LICENSES') -File) {
        [void][IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, $notice.FullName, ('overrides/LICENSES/' + $notice.Name))
    }
    $readme = [IO.File]::ReadAllText((Join-Path $PSScriptRoot 'README.md')).Replace('(LICENSE)', '(RealWorldMC-LICENSE.txt)') -replace '(?m)^Developer notes:.*\r?\n?', ''
    Add-TextEntry $zip 'overrides/RealWorldMC-README.md' $readme
    $docsRoot = Join-Path $PSScriptRoot 'docs'
    foreach ($doc in Get-ChildItem -LiteralPath $docsRoot -File -Recurse | Where-Object { $_.Extension -in '.md','.png','.jpg','.webp','.gif','.mp4','.vtt' }) {
        $relative = $doc.FullName.Substring($docsRoot.Length + 1).Replace('\','/')
        if ($doc.Extension -eq '.md') {
            Add-TextEntry $zip ("overrides/docs/" + $relative) ([IO.File]::ReadAllText($doc.FullName).Replace('../README.md','../RealWorldMC-README.md'))
        } else {
            [void][IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, $doc.FullName, ("overrides/docs/" + $relative))
        }
    }
    $service = Join-Path $PSScriptRoot 'service'
    if (-not (Test-Path (Join-Path $service 'runtime/win-x64/node.exe')) -or -not (Test-Path (Join-Path $service 'node_modules'))) {
        throw 'Build the portable service first with prepare-service.ps1 -ServiceDirectory service.'
    }
    # Only distributable files: credentials and caches never enter the pack.
    foreach ($name in @('src','preview','node_modules','runtime','package.json','package-lock.json','config.example.json','streetview.config.example.json')) {
        $item = Get-Item -LiteralPath (Join-Path $service $name)
        $files = if ($item.PSIsContainer) { Get-ChildItem -LiteralPath $item.FullName -File -Recurse } else { @($item) }
        foreach ($file in $files) {
            $relative = $file.FullName.Substring($service.Length + 1).Replace('\','/')
            [void][IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, $file.FullName, "overrides/realworldmc-service/$relative")
        }
    }
    $config = [ordered]@{ serviceUrl = 'http://127.0.0.1:8765'; randomSpawn = $true; latitude = 44.427454; longitude = 26.087287 }
    $config.serviceDir = 'realworldmc-service'
    Add-TextEntry $zip 'overrides/config/realworldmc.json' ($config | ConvertTo-Json)
    Add-TextEntry $zip 'client-overrides/options.txt' "lang:en_us`n"
    $zip.Dispose(); $zip = $null
    Move-Item -LiteralPath $temporaryPack -Destination $pack -Force
} finally {
    if ($zip) { $zip.Dispose() }
    if (Test-Path -LiteralPath $temporaryPack) { Remove-Item -LiteralPath $temporaryPack -Force }
}
Write-Output $pack
