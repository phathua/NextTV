<#
.SYNOPSIS
    NextTV Launcher - 1-Click Auto Build & Deploy to Android TV
.DESCRIPTION
    Tự động chuẩn bị ADB (tải từ Google nếu máy chưa có), quét song song tìm TV Box
    trên mạng LAN (port 5555), sinh C++ NDK secret header, build bản Release APK
    với Gradle, và cài đặt/khởi chạy lên thiết bị.
#>
param(
    [string]$TvHost = $env:TV_IP,
    [switch]$SetDefaultHome
)

$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

Write-Host "========================================================" -ForegroundColor Cyan
Write-Host "      NextTV Launcher - Auto Build and Deploy Release   " -ForegroundColor Yellow
Write-Host "========================================================" -ForegroundColor Cyan
Write-Host ""

$repoRoot = $PSScriptRoot
Set-Location $repoRoot

try {
    # 1. Dam bao ADB Platform Tools san sang
    Write-Host "[1/5] Kiem tra cong cu ADB..." -ForegroundColor Cyan
    & "$repoRoot\tools\ps\ensure_adb.ps1"

    # 2. Xac dinh dia chi IP cua Android TV Box
    Write-Host "[2/5] Tim Android TV Box tren mang LAN..." -ForegroundColor Cyan
    if (-not $TvHost) {
        $scanOutput = & "$repoRoot\tools\ps\find_tv_ip.ps1"
        foreach ($line in $scanOutput) {
            if ($line -match "__FOUND_IP__:(.*)") {
                $TvHost = $Matches[1].Trim()
                break
            }
        }
        if ($TvHost) {
            Write-Host "[+] Da tim thay TV Box tai IP: $TvHost" -ForegroundColor Green
        } else {
            Write-Host "[!] Khong tu dong tim thay TV Box nao mo port 5555." -ForegroundColor Yellow
            $TvHost = Read-Host "Nhap dia chi IP cua Android TV Box (vi du: 192.168.1.100)"
        }
    }

    if (-not $TvHost) {
        Write-Host "[-] Chua xac dinh duoc IP cua TV Box. Huy bo." -ForegroundColor Red
        Read-Host "Nhan Enter de thoat..."
        exit 1
    }

    # 3. Ket noi ADB
    Write-Host "[*] Dang ket noi ADB toi ${TvHost}:5555..." -ForegroundColor Cyan
    & adb connect "${TvHost}:5555" | Out-Null
    Start-Sleep -Seconds 1

    # 4. Sinh Native Header & Build Release APK
    Write-Host "[3/5] Sinh Native Secrets Header..." -ForegroundColor Cyan
    & "$repoRoot\tools\ps\generate_header.ps1"

    Write-Host "[4/5] Bien dich Release APK voi Gradle..." -ForegroundColor Cyan
    & "$repoRoot\gradlew.bat" assembleRelease --console=plain
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle assembleRelease that bai!"
    }

    # 5. Xac dinh file APK Release
    $apk = Join-Path $repoRoot "app\build\outputs\apk\release\app-armeabi-v7a-release.apk"
    if (-not (Test-Path $apk)) {
        $apk = Join-Path $repoRoot "app\build\outputs\apk\release\app-arm64-v8a-release.apk"
    }
    if (-not (Test-Path $apk)) {
        $apk = Join-Path $repoRoot "app\build\outputs\apk\release\app-universal-release.apk"
    }
    if (-not (Test-Path $apk)) {
        $apk = (Get-ChildItem -Path "$repoRoot\app\build\outputs\apk\release" -Filter "*.apk" | Select-Object -First 1).FullName
    }

    if (-not $apk -or -not (Test-Path $apk)) {
        throw "Khong tim thay bat ky file APK nao trong app\build\outputs\apk\release!"
    }

    Write-Host "[+] Da tim thay APK: $apk" -ForegroundColor Green

    # 6. Cai dat len TV Box
    Write-Host "[5/5] Dang cai dat len TV Box ($TvHost)..." -ForegroundColor Cyan
    & adb -s "${TvHost}:5555" install -r "$apk"
    if ($LASTEXITCODE -ne 0) {
        Write-Host "[!] Thu cai dat truc tiep voi default ADB..." -ForegroundColor Yellow
        & adb install -r "$apk"
    }

    # Cap quyen runtime
    Write-Host "[*] Cap quyen he thong..." -ForegroundColor Cyan
    & adb -s "${TvHost}:5555" shell pm grant com.next.tv.launcher android.permission.ACCESS_FINE_LOCATION 2>$null
    & adb -s "${TvHost}:5555" shell pm grant com.next.tv.launcher android.permission.ACCESS_COARSE_LOCATION 2>$null
    & adb -s "${TvHost}:5555" shell pm grant com.next.tv.launcher android.permission.READ_EXTERNAL_STORAGE 2>$null
    & adb -s "${TvHost}:5555" shell pm grant com.next.tv.launcher android.permission.WRITE_EXTERNAL_STORAGE 2>$null

    # Khoi chay ung dung
    Write-Host "[*] Khoi chay NextTV Launcher..." -ForegroundColor Cyan
    & adb -s "${TvHost}:5555" shell am start -n com.next.tv.launcher/.MainActivity

    if ($SetDefaultHome) {
        & adb -s "${TvHost}:5555" shell cmd package set-home-activity com.next.tv.launcher/.MainActivity
    }

    Write-Host ""
    Write-Host "========================================================" -ForegroundColor Green
    Write-Host "[+] THANH CONG: NextTV Release da duoc cai dat va chay!" -ForegroundColor Green
    Write-Host "========================================================" -ForegroundColor Green
} catch {
    Write-Host ""
    Write-Host "[-] CO LOI XAY RA: $_" -ForegroundColor Red
}

Write-Host ""
Read-Host "Nhan Enter de dong cua so..."
