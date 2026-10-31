<#
.SYNOPSIS
    NextTV Launcher - 1-Click Auto Build & Deploy to Android TV
.DESCRIPTION
    Tự động chuẩn bị ADB, quét TV Box trên mạng LAN, sinh C++ NDK header,
    dò tìm Java/Gradle/SDK, biên dịch Release APK, kiểm tra ABI TV, cài đặt,
    tự phục hồi khi lệch chữ ký, cấp quyền và xác minh khởi chạy.
#>
param(
    [string]$TvHost = $env:TV_IP,
    [switch]$SetDefaultHome,
    [switch]$SkipBuild,
    [ValidateSet('vi', 'en', 'auto')]
    [string]$Lang = 'auto'
)

$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$repoRoot = $PSScriptRoot
Set-Location $repoRoot

# Render TrueColor ANSI Banner if banner.ans exists
$bannerPath = Join-Path $repoRoot "tools\banner.ans"
if (Test-Path $bannerPath) {
    try {
        Get-Content $bannerPath -Raw -Encoding UTF8 | Write-Host
    } catch {}
}

# Determine display language
if ($Lang -eq 'auto') {
    try {
        $culture = (Get-UICulture).TwoLetterISOLanguageName.ToLower()
        $Lang = if ($culture -eq 'vi') { 'vi' } else { 'en' }
    } catch {
        $Lang = 'en'
    }
}

$M = @{
    vi = @{
        BannerTitle     = "NextTV Launcher - Tự động Biên dịch & Cài đặt Release"
        StepAdb         = "[1/5] Kiểm tra và chuẩn bị ADB Platform-Tools..."
        StepFindIp      = "[2/5] Dò tìm Android TV Box trên mạng LAN..."
        FoundTv         = "[+] Đã tìm thấy TV Box tại IP: {0}"
        NoTvFound       = "[!] Không tìm thấy TV Box nào mở port 5555 trên mạng LAN."
        PromptIp        = "Nhập địa chỉ IP của Android TV Box (ví dụ: 192.168.1.100): "
        NoIpAbort       = "[-] Chưa xác định được IP của TV Box. Hủy bỏ tác vụ."
        Connecting      = "[*] Đang kết nối ADB tới {0}:5555..."
        DeviceStatus    = "[*] Trạng thái thiết bị: {0}"
        WaitAuth        = "[!] Màn hình TV đang chờ xác nhận gỡ lỗi (unauthorized). Vui lòng nhìn lên TV và bấm 'Cho phép (Allow)'..."
        StepGenHeader   = "[3/5] Sinh Native Secrets Header cho NDK..."
        StepBuild       = "[4/5] Biên dịch Release APK với Gradle..."
        FixingJava      = "[*] Không tìm thấy JAVA_HOME hợp lệ. Đang tự dò JDK từ Android Studio..."
        FixingSdk       = "[*] Chưa có local.properties. Đang cấu hình sdk.dir tự động..."
        GradleFailed    = "[-] Lỗi biên dịch Gradle. Đang dọn dẹp daemon và khóa file cache để thử lại..."
        ApkFound        = "[+] Đã chọn file APK tối ưu ({0}): {1}"
        StepInstall     = "[5/5] Đang cài đặt lên TV Box ({0})..."
        SigConflict     = "[!] Phát hiện xung đột chữ ký hoặc hạ cấp phiên bản (INSTALL_FAILED_UPDATE_INCOMPATIBLE). Đang gỡ bản cũ..."
        GrantPerms      = "[*] Cấp quyền chạy nền và hệ thống..."
        LaunchApp       = "[*] Khởi chạy NextTV Launcher..."
        SetHome         = "[*] Đặt NextTV làm Launcher mặc định..."
        CrashWarning    = "[!] Cảnh báo: Phát hiện lỗi crash khi khởi động (Startup Crash) từ Logcat!"
        Success         = "[+] THÀNH CÔNG: NextTV Release đã được cài đặt và khởi chạy!"
        ErrorOccurred   = "[-] CÓ LỖI XẢY RA: {0}"
        PressEnterExit  = "Nhấn Enter để đóng cửa sổ..."
    }
    en = @{
        BannerTitle     = "NextTV Launcher - Auto Build & Deploy Release"
        StepAdb         = "[1/5] Checking and preparing ADB Platform-Tools..."
        StepFindIp      = "[2/5] Discovering Android TV Box on LAN..."
        FoundTv         = "[+] Found Android TV Box at IP: {0}"
        NoTvFound       = "[!] No Android TV Box listening on port 5555 found."
        PromptIp        = "Enter Android TV Box IP address (e.g. 192.168.1.100): "
        NoIpAbort       = "[-] TV Box IP not determined. Aborting."
        Connecting      = "[*] Connecting ADB to {0}:5555..."
        DeviceStatus    = "[*] Device status: {0}"
        WaitAuth        = "[!] TV is waiting for debugging authorization (unauthorized). Please allow USB debugging on your TV screen..."
        StepGenHeader   = "[3/5] Generating Native Secrets Header for NDK..."
        StepBuild       = "[4/5] Building Release APK with Gradle..."
        FixingJava      = "[*] Valid JAVA_HOME not found. Auto-detecting JDK from Android Studio..."
        FixingSdk       = "[*] Missing local.properties. Setting up sdk.dir automatically..."
        GradleFailed    = "[-] Gradle build failed. Stopping hung daemons and cleaning locks before retry..."
        ApkFound        = "[+] Selected optimal APK ({0}): {1}"
        StepInstall     = "[5/5] Installing on TV Box ({0})..."
        SigConflict     = "[!] Signature mismatch or downgrade detected (INSTALL_FAILED_UPDATE_INCOMPATIBLE). Uninstalling existing package..."
        GrantPerms      = "[*] Granting system and runtime permissions..."
        LaunchApp       = "[*] Starting NextTV Launcher..."
        SetHome         = "[*] Setting NextTV as default Home launcher..."
        CrashWarning    = "[!] Warning: Potential startup crash detected in Logcat!"
        Success         = "[+] SUCCESS: NextTV Release installed and running successfully!"
        ErrorOccurred   = "[-] ERROR OCCURRED: {0}"
        PressEnterExit  = "Press Enter to exit..."
    }
}
$msg = $M[$Lang]

Write-Host "========================================================" -ForegroundColor Cyan
Write-Host "      $($msg.BannerTitle)   " -ForegroundColor Yellow
Write-Host "========================================================" -ForegroundColor Cyan
Write-Host ""

function Ensure-BuildEnvironment {
    # 1. Ensure local.properties if missing
    $localProp = Join-Path $repoRoot "local.properties"
    if (-not (Test-Path $localProp)) {
        $sdkCandidates = @(
            $env:ANDROID_HOME,
            $env:ANDROID_SDK_ROOT,
            (Join-Path $env:LOCALAPPDATA "Android\Sdk"),
            (Join-Path $env:ProgramFiles "Android\Android Studio\sdk")
        )
        foreach ($cand in $sdkCandidates) {
            if ($cand -and (Test-Path $cand)) {
                $escaped = $cand.Replace('\', '\\').Replace(':', '\:')
                [System.IO.File]::WriteAllText($localProp, "sdk.dir=$escaped`n", [System.Text.Encoding]::UTF8)
                Write-Host $msg.FixingSdk -ForegroundColor Yellow
                Write-Host "[+] Created local.properties pointing to: $cand" -ForegroundColor Green
                break
            }
        }
    }

    # 2. Ensure JAVA_HOME
    try {
        $v = & java -version 2>&1
        if ($LASTEXITCODE -eq 0 -and $env:JAVA_HOME -and (Test-Path $env:JAVA_HOME)) {
            return
        }
    } catch {}

    Write-Host $msg.FixingJava -ForegroundColor Yellow
    $candidates = @(
        "D:\DevEnv\JDK",
        (Join-Path $env:ProgramFiles "Android\Android Studio\jbr"),
        (Join-Path $env:ProgramFiles "Android\Android Studio\jre"),
        (Join-Path $env:LOCALAPPDATA "Programs\Android Studio\jbr")
    )
    foreach ($c in $candidates) {
        if ($c -and (Test-Path (Join-Path $c "bin\java.exe"))) {
            $env:JAVA_HOME = $c
            $env:PATH = "$c\bin;$env:PATH"
            Write-Host "[+] JAVA_HOME set to: $c" -ForegroundColor Green
            return
        }
    }
}

try {
    # 1. Ensure ADB platform-tools
    Write-Host $msg.StepAdb -ForegroundColor Cyan
    & "$repoRoot\tools\ps\ensure_adb.ps1"

    # 2. Find TV Box IP
    Write-Host $msg.StepFindIp -ForegroundColor Cyan
    if (-not $TvHost) {
        $scanOutput = & "$repoRoot\tools\ps\find_tv_ip.ps1"
        foreach ($line in $scanOutput) {
            if ($line -match "__FOUND_IP__:(.*)") {
                $TvHost = $Matches[1].Trim()
                break
            }
        }
        if ($TvHost) {
            Write-Host ($msg.FoundTv -f $TvHost) -ForegroundColor Green
        } else {
            Write-Host $msg.NoTvFound -ForegroundColor Yellow
            $TvHost = Read-Host $msg.PromptIp
        }
    }

    if (-not $TvHost) {
        Write-Host $msg.NoIpAbort -ForegroundColor Red
        Read-Host $msg.PressEnterExit
        exit 1
    }

    $deviceTarget = "${TvHost}:5555"

    # 3. Connect ADB and handle authorization / offline
    Write-Host ($msg.Connecting -f $TvHost) -ForegroundColor Cyan
    & adb connect $deviceTarget | Out-Null
    Start-Sleep -Milliseconds 800

    # Polling device status up to 15 seconds
    $isAuthorized = $false
    for ($attempt = 1; $attempt -le 10; $attempt++) {
        $devicesOutput = & adb devices 2>&1
        $matchedState = $null
        foreach ($dLine in $devicesOutput) {
            if ($dLine -match "^$([regex]::Escape($deviceTarget))\s+(\w+)") {
                $matchedState = $Matches[1]
                break
            }
        }

        if ($matchedState -eq 'device') {
            $isAuthorized = $true
            break
        } elseif ($matchedState -eq 'unauthorized') {
            Write-Host $msg.WaitAuth -ForegroundColor Yellow
            try { [Console]::Beep(1000, 300) } catch {}
            Start-Sleep -Seconds 2
        } elseif ($matchedState -eq 'offline') {
            & adb disconnect $deviceTarget 2>&1 | Out-Null
            & adb connect $deviceTarget 2>&1 | Out-Null
            Start-Sleep -Seconds 1
        } else {
            Start-Sleep -Seconds 1
        }
    }

    if (-not $isAuthorized) {
        Write-Host "[-] Device connection state: $matchedState" -ForegroundColor Yellow
    }

    # Query device CPU ABI to select the best APK
    $deviceAbi = (& adb -s $deviceTarget shell getprop ro.product.cpu.abi 2>$null)
    if ($deviceAbi) { $deviceAbi = $deviceAbi.Trim() }
    Write-Host "[*] TV Architecture ABI: $deviceAbi" -ForegroundColor DarkGray

    # 4. Generate Native Header & Build APK
    if (-not $SkipBuild) {
        Write-Host $msg.StepGenHeader -ForegroundColor Cyan
        & "$repoRoot\tools\ps\generate_header.ps1"

        Ensure-BuildEnvironment

        Write-Host $msg.StepBuild -ForegroundColor Cyan
        & "$repoRoot\gradlew.bat" assembleRelease --console=plain
        if ($LASTEXITCODE -ne 0) {
            Write-Host $msg.GradleFailed -ForegroundColor Yellow
            & "$repoRoot\gradlew.bat" --stop 2>$null
            Get-ChildItem -Path "$repoRoot\.gradle" -Recurse -Filter "*.lock" -ErrorAction SilentlyContinue | Remove-Item -Force -ErrorAction SilentlyContinue
            Write-Host "[*] Retrying Gradle build with --no-daemon..." -ForegroundColor Cyan
            & "$repoRoot\gradlew.bat" assembleRelease --no-daemon --console=plain
            if ($LASTEXITCODE -ne 0) {
                throw "Gradle assembleRelease failed after retry!"
            }
        }
    }

    # 5. Select the most suitable APK based on target ABI
    $apkDir = Join-Path $repoRoot "app\build\outputs\apk\release"
    $apk = $null
    $chosenType = "universal"

    if ($deviceAbi -like "arm64*" -and (Test-Path "$apkDir\app-arm64-v8a-release.apk")) {
        $apk = "$apkDir\app-arm64-v8a-release.apk"
        $chosenType = "arm64-v8a"
    } elseif ($deviceAbi -like "armeabi*" -and (Test-Path "$apkDir\app-armeabi-v7a-release.apk")) {
        $apk = "$apkDir\app-armeabi-v7a-release.apk"
        $chosenType = "armeabi-v7a"
    } elseif (Test-Path "$apkDir\app-universal-release.apk") {
        $apk = "$apkDir\app-universal-release.apk"
        $chosenType = "universal"
    } else {
        $firstApk = Get-ChildItem -Path $apkDir -Filter "*.apk" -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($firstApk) {
            $apk = $firstApk.FullName
            $chosenType = $firstApk.Name
        }
    }

    if (-not $apk -or -not (Test-Path $apk)) {
        throw "Could not locate any release APK in $apkDir"
    }

    Write-Host ($msg.ApkFound -f $chosenType, $apk) -ForegroundColor Green

    # 6. Install APK on TV Box
    Write-Host ($msg.StepInstall -f $TvHost) -ForegroundColor Cyan
    $installOutput = & adb -s $deviceTarget install -r "$apk" 2>&1 | Out-String
    Write-Host $installOutput -ForegroundColor Gray

    # Check for signature mismatch or downgrade error
    if ($installOutput -match "INSTALL_FAILED_UPDATE_INCOMPATIBLE|INSTALL_FAILED_VERSION_DOWNGRADE|INSTALL_FAILED_SHARED_USER_INCOMPATIBLE") {
        Write-Host $msg.SigConflict -ForegroundColor Yellow
        & adb -s $deviceTarget uninstall com.next.tv.launcher 2>&1 | Out-Null
        $installOutput = & adb -s $deviceTarget install -r "$apk" 2>&1 | Out-String
        Write-Host $installOutput -ForegroundColor Gray
    }

    if ($installOutput -notmatch "Success") {
        throw "ADB install failed: $installOutput"
    }

    # 7. Grant Permissions
    Write-Host $msg.GrantPerms -ForegroundColor Cyan
    & adb -s $deviceTarget shell pm grant com.next.tv.launcher android.permission.ACCESS_FINE_LOCATION 2>$null
    & adb -s $deviceTarget shell pm grant com.next.tv.launcher android.permission.ACCESS_COARSE_LOCATION 2>$null
    & adb -s $deviceTarget shell pm grant com.next.tv.launcher android.permission.READ_EXTERNAL_STORAGE 2>$null
    & adb -s $deviceTarget shell pm grant com.next.tv.launcher android.permission.WRITE_EXTERNAL_STORAGE 2>$null

    # 8. Launch App & Startup Verification
    Write-Host $msg.LaunchApp -ForegroundColor Cyan
    & adb -s $deviceTarget shell logcat -c 2>$null
    & adb -s $deviceTarget shell am start -n com.next.tv.launcher/.MainActivity 2>&1 | Out-Null

    if ($SetDefaultHome) {
        Write-Host $msg.SetHome -ForegroundColor Cyan
        & adb -s $deviceTarget shell cmd package set-home-activity com.next.tv.launcher/.MainActivity 2>$null
    }

    # Check for crash logs within 2 seconds
    Start-Sleep -Seconds 2
    $recentLogs = & adb -s $deviceTarget shell "logcat -d | grep -i 'FATAL EXCEPTION'" 2>$null
    if ($recentLogs -and $recentLogs.ToString().Trim().Length -gt 0) {
        Write-Host $msg.CrashWarning -ForegroundColor Red
        Write-Host $recentLogs -ForegroundColor DarkYellow
    }

    Write-Host ""
    Write-Host "========================================================" -ForegroundColor Green
    Write-Host $msg.Success -ForegroundColor Green
    Write-Host "========================================================" -ForegroundColor Green
} catch {
    Write-Host ""
    Write-Host ($msg.ErrorOccurred -f $_) -ForegroundColor Red
}

Write-Host ""
# Only prompt to wait if launched via double-click in interactive console
if ([Environment]::UserInteractive) {
    Read-Host $msg.PressEnterExit
}
