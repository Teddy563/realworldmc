param([Parameter(Mandatory)][string]$Jar, [Parameter(Mandatory)][string]$ServiceDirectory, [Parameter(Mandatory)][string]$OutputJar)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$service = (Resolve-Path -LiteralPath $ServiceDirectory).Path
$output = [IO.Path]::GetFullPath($OutputJar)
if ([IO.Path]::GetFullPath($Jar) -eq $output) { throw 'Bundle into a separate release JAR.' }
$bundle = "$output.service.zip"
try {
    $zip = [IO.Compression.ZipFile]::Open($bundle, 'Create')
    try {
        # Explicitly exclude credentials, caches and logs.
        foreach ($name in @('src','preview','node_modules','runtime','package.json','package-lock.json','config.example.json','streetview.config.example.json')) {
            $item = Get-Item -LiteralPath (Join-Path $service $name)
            $files = if ($item.PSIsContainer) { Get-ChildItem -LiteralPath $item.FullName -File -Recurse } else { @($item) }
            foreach ($file in $files) {
                $relative = $file.FullName.Substring($service.Length + 1).Replace('\','/')
                [void][IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, $file.FullName, $relative)
            }
        }
    } finally { $zip.Dispose() }
    $hash = (Get-FileHash -LiteralPath $bundle -Algorithm SHA256).Hash.ToLowerInvariant()
    Copy-Item -LiteralPath $Jar -Destination $output -Force
    $release = [IO.Compression.ZipFile]::Open($output, 'Update')
    try {
        [void][IO.Compression.ZipFileExtensions]::CreateEntryFromFile($release, $bundle, 'realworldmc-service.zip', [IO.Compression.CompressionLevel]::NoCompression)
        $entry = $release.CreateEntry('realworldmc-service.sha256')
        $writer = [IO.StreamWriter]::new($entry.Open(), [Text.UTF8Encoding]::new($false))
        try { $writer.Write($hash) } finally { $writer.Dispose() }
    } finally { $release.Dispose() }
} finally {
    if (Test-Path -LiteralPath $bundle) { Remove-Item -LiteralPath $bundle }
}
Write-Output $output
