param([ValidateSet('1.21.1','26.2','26.3')][string]$MinecraftVersion = '1.21.1', [switch]$NoOpen)
$ErrorActionPreference = 'Stop'
$pack = Join-Path $PSScriptRoot "RealWorldMC-1.0.0-mc$MinecraftVersion.mrpack"
if (-not (Test-Path -LiteralPath $pack)) { throw 'Download the ready-made .mrpack from Releases and import it into Modrinth or Prism.' }
Write-Host 'The pack includes its own Windows x64 map services and Node runtime. No installer is needed.'
Write-Host "Import this file into Modrinth or Prism: $pack"
if (-not $NoOpen) { Start-Process -FilePath $pack }
