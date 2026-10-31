# NextTV - Build Google Play App Bundle (.aab)
param(
    [switch]$SkipHeaderGen
)

$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
Set-Location $repoRoot

Write-Host "========================================================" -ForegroundColor Cyan
Write-Host "      NextTV - Google Play App Bundle Builder (.aab)    " -ForegroundColor Green
Write-Host "========================================================" -ForegroundColor Cyan
Write-Host ""

if (-not $SkipHeaderGen) {
    Write-Host "[1/2] Generating Native Secrets Header..." -ForegroundColor Cyan
    & "$PSScriptRoot\generate_header.ps1"
}

Write-Host "[2/2] Building bundlePlayRelease with Gradle..." -ForegroundColor Cyan
& "$repoRoot\gradlew.bat" bundlePlayRelease --console=plain

if ($LASTEXITCODE -ne 0) {
    throw "Gradle bundlePlayRelease failed!"
}

$bundleDir = Join-Path $repoRoot "app\build\outputs\bundle\playRelease"
$bundleFile = Get-ChildItem -Path $bundleDir -Filter "*.aab" -ErrorAction SilentlyContinue | Select-Object -First 1

if (-not $bundleFile) {
    throw "Could not locate .aab bundle file in $bundleDir!"
}

Write-Host ""
Write-Host "========================================================" -ForegroundColor Green
Write-Host " SUCCESS! Google Play App Bundle is ready:" -ForegroundColor Green
Write-Host " Path: $($bundleFile.FullName)" -ForegroundColor Yellow
Write-Host " Size: $([math]::Round($bundleFile.Length / 1MB, 2)) MB" -ForegroundColor Yellow
Write-Host "========================================================" -ForegroundColor Green
