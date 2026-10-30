# NextTV - Ensure Android ADB (Platform Tools) is Available on Windows
param(
    [switch]$ForceDownload
)

$ErrorActionPreference = 'Stop'

function Test-AdbWorking {
    try {
        $out = & adb version 2>&1
        if ($LASTEXITCODE -eq 0 -or $out -match "Android Debug Bridge") {
            return $true
        }
    } catch {}
    return $false
}

$toolsDir = Split-Path -Parent $PSScriptRoot
$localAdbDir = Join-Path $toolsDir "platform-tools"
$localAdbExe = Join-Path $localAdbDir "adb.exe"

# 1. Check if adb is already functioning in system PATH
if (-not $ForceDownload -and (Test-AdbWorking)) {
    return
}

# 2. Check if adb exists in local tools/platform-tools
if (Test-Path $localAdbExe) {
    if ($env:PATH -notlike "*$localAdbDir*") {
        $env:PATH = "$localAdbDir;$env:PATH"
    }
    if (Test-AdbWorking) {
        return
    }
}

# 3. Download Google official platform-tools for Windows
Write-Host "========================================================" -ForegroundColor Cyan
Write-Host " [*] ADB chua co tren may. Dang tu dong tai platform-tools tu Google..." -ForegroundColor Yellow
Write-Host "========================================================" -ForegroundColor Cyan

$zipUrl = "https://dl.google.com/android/repository/platform-tools-latest-windows.zip"
$zipFile = Join-Path $toolsDir "platform-tools-latest-windows.zip"

try {
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
    Write-Host ">>> Downloading: $zipUrl" -ForegroundColor Gray
    
    # Use WebClient for faster progress download without PowerShell progress buffer overhead
    $client = New-Object System.Net.WebClient
    $client.DownloadFile($zipUrl, $zipFile)
    $client.Dispose()

    Write-Host ">>> Extracting platform-tools to: $toolsDir" -ForegroundColor Gray
    if (Test-Path $localAdbDir) {
        Remove-Item -Recurse -Force $localAdbDir
    }
    
    Expand-Archive -Path $zipFile -DestinationPath $toolsDir -Force
    Remove-Item -Force $zipFile

    if (Test-Path $localAdbExe) {
        Write-Host "[+] Cai dat ADB thanh cong tai: $localAdbDir" -ForegroundColor Green
        $env:PATH = "$localAdbDir;$env:PATH"

        # Try to persist in User Environment PATH for convenience
        try {
            $userPath = [Environment]::GetEnvironmentVariable("Path", "User")
            if ($userPath -notlike "*$localAdbDir*") {
                [Environment]::SetEnvironmentVariable("Path", "$userPath;$localAdbDir", "User")
                Write-Host "[+] Da luu duong dan ADB vao User PATH de su dung lau dai." -ForegroundColor Green
            }
        } catch {}
    } else {
        throw "Khong tim thay adb.exe sau khi giai nen!"
    }
} catch {
    Write-Host "[-] Loi khi tai ADB tu dong: $_" -ForegroundColor Red
    if (Test-Path $zipFile) { Remove-Item -Force $zipFile }
    exit 1
}
