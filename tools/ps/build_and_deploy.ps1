# NextTV - Build Release APK and Deploy to Android TV
param(
    [string]$TvHost = $env:TV_IP,
    [switch]$SetDefaultHome
)

$ErrorActionPreference = 'Stop'
$OutputEncoding = New-Object System.Text.UTF8Encoding $false

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
Set-Location $repoRoot

Write-Host ">>> [1/4] Generating Native Secrets Header..." -ForegroundColor Cyan
& "$PSScriptRoot\generate_header.ps1"

Write-Host ">>> [2/4] Building Release APK with Gradle..." -ForegroundColor Cyan
& .\gradlew.bat assembleRelease --console=plain
if ($LASTEXITCODE -ne 0) { throw 'Gradle build failed' }

# Locate APK
$apk = Join-Path $repoRoot "app\build\outputs\apk\release\app-armeabi-v7a-release.apk"
if (-not (Test-Path $apk)) {
    $apk = Join-Path $repoRoot "app\build\outputs\apk\release\app-universal-release.apk"
}
if (-not (Test-Path $apk)) {
    $apk = (Get-ChildItem -Path "$repoRoot\app\build\outputs\apk\release" -Filter "*.apk" | Select-Object -First 1).FullName
}
Write-Host ">>> Found APK: $apk" -ForegroundColor Green

# Discover or connect TV IP
if (-not $TvHost) {
    Write-Host ">>> Auto-scanning Android TV on LAN..." -ForegroundColor Cyan
    $discovered = & "$PSScriptRoot\find_tv_ip.ps1" | Select-Object -Last 1
    if ($discovered -and $discovered -match '^\d+\.\d+\.\d+\.\d+$') {
        $TvHost = $discovered
    }
}

if ($TvHost) {
    Write-Host ">>> Connecting to TV: $TvHost:5555..." -ForegroundColor Cyan
    adb connect "$TvHost:5555"
}

Write-Host ">>> [3/4] Installing NextTV APK..." -ForegroundColor Cyan
adb install -r "$apk"
if ($LASTEXITCODE -ne 0) { throw 'ADB install failed' }

Write-Host ">>> [4/4] Launching NextTV..." -ForegroundColor Green
adb shell am start -n com.next.tv.launcher/.MainActivity

if ($SetDefaultHome) {
    Write-Host ">>> Setting NextTV as default Home..." -ForegroundColor Yellow
    adb shell cmd package set-home-activity com.next.tv.launcher/.MainActivity
}

Write-Host ">>> Done! NextTV is active." -ForegroundColor Green
