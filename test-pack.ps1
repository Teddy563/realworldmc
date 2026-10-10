param([Parameter(Mandatory)][string]$Pack)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$packPath = (Resolve-Path -LiteralPath $Pack).Path
$zip = [IO.Compression.ZipFile]::OpenRead($packPath)
try {
    $names = @($zip.Entries | ForEach-Object { $_.FullName })
    foreach ($name in $names) {
        if ($name -match '(^/|^[A-Za-z]:|(^|/)\.\.(/|$)|\\)') { throw 'Unsafe pack path.' }
        if ($name -match '^overrides/realworldmc-service/(config.json|streetview.config.json|cache/|.*\.log$)') { throw 'Private service state in pack.' }
    }
    foreach ($name in @('modrinth.index.json','overrides/realworldmc-service/src/launcher.mjs','overrides/realworldmc-service/runtime/win-x64/node.exe','overrides/realworldmc-service/runtime/win-x64/LICENSE','overrides/realworldmc-service/node_modules/jpeg-js/package.json')) {
        if ($name -notin $names) { throw "Incomplete portable pack: $name" }
    }
} finally { $zip.Dispose() }
# Extract to a new instance with spaces. Never read personal API credentials.
$instance = Join-Path ([IO.Path]::GetTempPath()) ('RWMC clean import ' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory $instance | Out-Null
[IO.Compression.ZipFile]::ExtractToDirectory($packPath, $instance)
$service = Join-Path $instance 'overrides/realworldmc-service'
function Get-FreePort {
    $listener = New-Object Net.Sockets.TcpListener([Net.IPAddress]::Loopback, 0)
    $listener.Start()
    try { return $listener.LocalEndpoint.Port } finally { $listener.Stop() }
}
$mapPort = Get-FreePort
do { $photoPort = Get-FreePort } while ($mapPort -eq $photoPort)
[IO.File]::WriteAllText((Join-Path $service 'streetview.config.json'), '{"tilesKey":"TEST_ONLY_NOT_A_REAL_KEY"}')
$start = New-Object Diagnostics.ProcessStartInfo
$start.FileName = Join-Path $service 'runtime/win-x64/node.exe'
$start.Arguments = 'src/launcher.mjs'
$start.WorkingDirectory = $service
$start.UseShellExecute = $false
$start.CreateNoWindow = $true
$start.RedirectStandardInput = $true
$start.RedirectStandardOutput = $true
$start.RedirectStandardError = $true
foreach ($key in @($start.EnvironmentVariables.Keys)) {
    if ($key -match '^(RWMC_|REALWORLDMC_|EARTHCRAFT_)') { $start.EnvironmentVariables.Remove($key) }
}
$start.EnvironmentVariables['PATH'] = '' # Prove no installed Node/npm is required.
$start.EnvironmentVariables['RWMC_MANAGED'] = '1'
$start.EnvironmentVariables['RWMC_INSTANCE_ID'] = 'pack-smoke'
$start.EnvironmentVariables['RWMC_PORT'] = [string]$mapPort
$start.EnvironmentVariables['RWMC_STREETVIEW_PORT'] = [string]$photoPort
$child = [Diagnostics.Process]::Start($start)
$stdout = $child.StandardOutput.ReadToEndAsync()
$stderr = $child.StandardError.ReadToEndAsync()
try {
    $ready = $false
    for ($i=0; $i -lt 50; $i++) {
        if ($child.HasExited) { throw "Pack service exited: $($stderr.Result)" }
        try {
            $map = Invoke-RestMethod "http://127.0.0.1:$mapPort/health" -TimeoutSec 1
            $photo = Invoke-RestMethod "http://127.0.0.1:$photoPort/health" -TimeoutSec 1
            if ($map.instanceId -eq 'pack-smoke' -and $photo.instanceId -eq 'pack-smoke') { $ready=$true; break }
        } catch { Start-Sleep -Milliseconds 200 }
    }
    if (-not $ready) { throw 'Pack services never became ready.' }
    $settings = Invoke-RestMethod "http://127.0.0.1:$photoPort/settings"
    if ($settings.browserKey -or $map.hasKey -or -not $photo.hasKey) { throw 'Credential isolation check failed.' }
    $child.StandardInput.Close()
    if (-not $child.WaitForExit(5000)) { throw 'Services survived the Minecraft pipe closing.' }
    Write-Output 'Clean import passed: bundled runtime, both services, private key isolation, and automatic shutdown. No provider calls.'
} finally {
    if (-not $child.HasExited) { $child.Kill(); $child.WaitForExit() }
    $child.Dispose()
    # Preserve this isolated instance for inspection, never touch real profiles.
    Write-Output "Smoke-test instance: $instance"
}
