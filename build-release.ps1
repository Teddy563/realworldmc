param([ValidateSet('1.20.1','1.21.1','26.2','26.3')][string]$MinecraftVersion = '1.21.1', [string]$OutputDirectory = (Join-Path $PSScriptRoot 'release-output'))
$ErrorActionPreference = 'Stop'
$module = if ($MinecraftVersion -ne '1.21.1') { "mod-$MinecraftVersion" } else { 'mod' }
$versionLine = Get-Content (Join-Path $PSScriptRoot "$module/gradle.properties") | Where-Object { $_ -match '^version=' }
$version = ($versionLine -split '=',2)[1].Trim()
if ($version -notmatch '^\d+\.\d+\.\d+(?:-[a-zA-Z0-9.-]+)?$') { throw 'Invalid project version.' }
$jarName = if ($MinecraftVersion -ne '1.21.1') { "realworldmc-mc$MinecraftVersion-$version.jar" } else { "realworldmc-$version.jar" }
# Refresh the distributable even when a previous release JAR already exists.
# Client test tasks compile classes but do not rebuild the distributable JAR.
& (Join-Path $PSScriptRoot "$module/gradlew.bat") -p (Join-Path $PSScriptRoot $module) --no-daemon assemble
if ($LASTEXITCODE -ne 0) { throw "Failed to build the release JAR for $MinecraftVersion" }
$jar = Join-Path $PSScriptRoot "$module/build/libs/$jarName"
if (-not (Test-Path $jar)) { throw "Build $module first. Missing $jarName" }
$out = [IO.Path]::GetFullPath($OutputDirectory)
New-Item -ItemType Directory -Force $out | Out-Null
$stage = Join-Path $out ("stage-" + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory $stage | Out-Null
# Deliberate allowlist: never ship personal config, logs, worlds or caches.
foreach ($name in @('README.md','LICENSE','NOTICE','CHANGELOG.md','CODE_OF_CONDUCT.md','start-service.bat','start-streetview.bat','STREET-VIEW.md')) {
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot $name) -Destination $stage
}
foreach ($name in @('docs','LICENSES','modpack')) { Copy-Item -LiteralPath (Join-Path $PSScriptRoot $name) -Destination $stage -Recurse }
foreach ($name in @('build-pack.ps1','install.ps1')) {
    $body = [IO.File]::ReadAllText((Join-Path $PSScriptRoot $name)).Replace('1.0.0', $version)
    if ($MinecraftVersion -ne '1.21.1' -and $name -eq 'install.ps1') { $body = $body.Replace("`$MinecraftVersion = '1.21.1'", "`$MinecraftVersion = '$MinecraftVersion'") }
    [IO.File]::WriteAllText((Join-Path $stage $name), $body)
}
foreach ($index in Get-ChildItem (Join-Path $stage 'modpack') -Filter '*.json') {
    $body = [IO.File]::ReadAllText($index.FullName).Replace('1.0.0', $version)
    [IO.File]::WriteAllText($index.FullName, $body)
}
New-Item -ItemType Directory (Join-Path $stage 'service') | Out-Null
foreach ($name in @('src','preview','package.json','package-lock.json','config.example.json','streetview.config.example.json')) {
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot "service/$name") -Destination (Join-Path $stage 'service') -Recurse
}
Copy-Item -LiteralPath $jar -Destination (Join-Path $stage $jarName)
& (Join-Path $PSScriptRoot 'prepare-service.ps1') -ServiceDirectory (Join-Path $stage 'service')
& (Join-Path $PSScriptRoot 'bundle-jar.ps1') -Jar $jar -ServiceDirectory (Join-Path $stage 'service') -OutputJar (Join-Path $out "RealWorldMC-$version-mc$MinecraftVersion.jar") | Out-Null
& (Join-Path $stage 'build-pack.ps1') -MinecraftVersion $MinecraftVersion -OutputDirectory $stage | Out-Null
Copy-Item -Path (Join-Path $stage '*.mrpack') -Destination $out
$zip = Join-Path $out "RealWorldMC-$version-mc$MinecraftVersion.zip"
Compress-Archive -Path (Join-Path $stage '*') -DestinationPath $zip -Force
Get-ChildItem $out -File | Where-Object { $_.Extension -in '.zip','.jar','.mrpack' } | ForEach-Object {
    $hash = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
    [IO.File]::WriteAllText(($_.FullName + '.sha256'), "$hash  $($_.Name)`n")
}
# Keep staging for inspection; it contains only the release allowlist.
Write-Output $zip
