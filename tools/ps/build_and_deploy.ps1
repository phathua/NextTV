# NextTV - Build Release APK and Deploy to Android TV
# Redirects cleanly to the root deploy_tv.ps1 engine
param(
    [string]$TvHost = $env:TV_IP,
    [switch]$SetDefaultHome,
    [switch]$SkipBuild,
    [string]$Lang = 'auto'
)

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
& "$repoRoot\deploy_tv.ps1" -TvHost $TvHost -SetDefaultHome:$SetDefaultHome -SkipBuild:$SkipBuild -Lang $Lang
exit $LASTEXITCODE
