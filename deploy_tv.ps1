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
    [string]$Lang = 'auto',
    [ValidateSet('sideload', 'play')]
    [string]$Flavor = 'sideload'
)

$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$repoRoot = $PSScriptRoot
Set-Location $repoRoot

# Setup tools/logs directory and log file
$logsDir = Join-Path $repoRoot "tools\logs"
if (-not (Test-Path $logsDir)) {
    New-Item -ItemType Directory -Path $logsDir -Force | Out-Null
}
$timestamp = Get-Date -Format "yyyy-MM-dd_HH-mm-ss"
$logFile = Join-Path $logsDir "deploy_${timestamp}.log"

function Log-Message([string]$text) {
    $now = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
    Add-Content -LiteralPath $logFile -Value "[$now] $text" -Encoding UTF8 -ErrorAction SilentlyContinue
}

# Single-line interactive status updater (Zero-clutter terminal)
function Update-Status([string]$text, [string]$color = "Cyan") {
    Log-Message $text
    $padded = $text.PadRight(80, ' ')
    Write-Host "`r$padded" -NoNewline -ForegroundColor $color
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
        StepAdb         = "[1/5] Kiem tra cong cu ADB..."
        StepFindIp      = "[2/5] Dang tim Android TV tren mang LAN..."
        TvConnected     = "[2/5] Da ket noi TV: {0} ({1})"
        NoTvFound       = "[!] Khong tim thay TV Box nao mo port 5555."
        PromptIp        = "Nhap IP Android TV Box (vi du: 192.168.1.100): "
        NoIpAbort       = "Chua xac dinh duoc IP cua TV Box. Huy bo."
        Connecting      = "[2/5] Dang ket noi ADB toi {0}:5555..."
        WaitAuth        = "[!] Màn hinh TV dang cho xac nhan debugging. Bam 'Cho phep (Allow)' tren TV..."
        StepGenHeader   = "[3/5] Dang sinh Native Secrets Header cho NDK..."
        StepBuild       = "[4/5] Dang bien dich Release APK voi Gradle..."
        FixingJava      = "[*] Dang tu do tim JDK tu Android Studio..."
        FixingSdk       = "[*] Dang cau hinh sdk.dir tu dong..."
        GradleFailed    = "Loi bien dich Gradle. Dang retry voi --no-daemon..."
        StepInstall     = "[5/5] Dang cai dat APK {0} len TV Box ({1})..."
        SigConflict     = "[!] Xung dot chu ky. Dang go ban cu va cai lai..."
        GrantPerms      = "[5/5] Dang cap quyen he thong..."
        LaunchApp       = "[5/5] Dang khoi chay NextTV tren TV..."
        Success         = "[5/5] HOAN TAT! Da cai dat va khoi chay NextTV thanh cong!"
        CrashWarning    = "Canh bao: Phat hien crash ung dung tu Logcat!"
        ErrorOccurred   = "CO LOI XAY RA: {0}"
        LogLocation     = "Chi tiet log da duoc luu tai: {0}"
        PressEnterExit  = "Nhan Enter de dong cua so..."
    }
    en = @{
        StepAdb         = "[1/5] Checking ADB platform-tools..."
        StepFindIp      = "[2/5] Scanning Android TV on LAN..."
        TvConnected     = "[2/5] Connected to TV: {0} ({1})"
        NoTvFound       = "[!] No Android TV found on port 5555."
        PromptIp        = "Enter Android TV Box IP address: "
        NoIpAbort       = "TV Box IP not determined. Aborting."
        Connecting      = "[2/5] Connecting ADB to {0}:5555..."
        WaitAuth        = "[!] TV is waiting for authorization. Please click 'Allow' on TV screen..."
        StepGenHeader   = "[3/5] Generating Native Secrets Header for NDK..."
        StepBuild       = "[4/5] Building Release APK with Gradle..."
        FixingJava      = "[*] Auto-detecting JDK from Android Studio..."
        FixingSdk       = "[*] Setting up sdk.dir automatically..."
        GradleFailed    = "Gradle build failed. Retrying with --no-daemon..."
        StepInstall     = "[5/5] Installing APK {0} on TV Box ({1})..."
        SigConflict     = "[!] Signature mismatch. Reinstalling..."
        GrantPerms      = "[5/5] Granting runtime permissions..."
        LaunchApp       = "[5/5] Starting NextTV on TV..."
        Success         = "[5/5] SUCCESS: NextTV installed and running on TV Box!"
        CrashWarning    = "Warning: Potential app crash detected from Logcat!"
        ErrorOccurred   = "ERROR OCCURRED: {0}"
        LogLocation     = "Detailed logs saved at: {0}"
        PressEnterExit  = "Press Enter to exit..."
    }
}
$msg = $M[$Lang]

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
                Log-Message "Created local.properties -> $cand"
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
            Log-Message "JAVA_HOME set to: $c"
            return
        }
    }
}

try {
    # 1. Ensure ADB platform-tools
    Update-Status $msg.StepAdb "Cyan"
    $adbOut = & "$repoRoot\tools\ps\ensure_adb.ps1" 2>&1
    Log-Message ($adbOut | Out-String)

    # 2. Find TV Box IP
    Update-Status $msg.StepFindIp "Cyan"
    if (-not $TvHost) {
        $scanOutput = & "$repoRoot\tools\ps\find_tv_ip.ps1" 2>&1
        Log-Message ($scanOutput | Out-String)
        foreach ($line in $scanOutput) {
            if ($line -match "__FOUND_IP__:(.*)") {
                $TvHost = $Matches[1].Trim()
                break
            }
        }
        if (-not $TvHost) {
            Write-Host ""
            Write-Host $msg.NoTvFound -ForegroundColor Yellow
            $TvHost = Read-Host $msg.PromptIp
        }
    }

    if (-not $TvHost) {
        throw $msg.NoIpAbort
    }

    $deviceTarget = "${TvHost}:5555"

    # 3. Connect ADB and handle authorization / offline
    Update-Status ($msg.Connecting -f $TvHost) "Cyan"
    $connOut = & adb connect $deviceTarget 2>&1
    Log-Message ($connOut | Out-String)
    Start-Sleep -Milliseconds 600

    # Polling device status up to 10 seconds
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
            Write-Host ""
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

    # Query device CPU ABI
    $deviceAbi = (& adb -s $deviceTarget shell getprop ro.product.cpu.abi 2>$null)
    if ($deviceAbi) { $deviceAbi = $deviceAbi.Trim() } else { $deviceAbi = "unknown" }
    Log-Message "TV Architecture ABI: $deviceAbi"
    Update-Status ($msg.TvConnected -f $TvHost, $deviceAbi) "Green"
    Start-Sleep -Milliseconds 400

    # 4. Generate Native Header & Build APK
    if (-not $SkipBuild) {
        Update-Status $msg.StepGenHeader "Cyan"
        $headerOut = & "$repoRoot\tools\ps\generate_header.ps1" 2>&1
        Log-Message ($headerOut | Out-String)

        Ensure-BuildEnvironment

        Update-Status $msg.StepBuild "Cyan"
        $gradleCmd = "$repoRoot\gradlew.bat"
        $flavorCap = (Get-Culture).TextInfo.ToTitleCase($Flavor)
        $buildTarget = "assemble${flavorCap}Release"
        $buildOut = & $gradleCmd $buildTarget --console=plain 2>&1
        Log-Message ($buildOut | Out-String)

        if ($LASTEXITCODE -ne 0) {
            Update-Status $msg.GradleFailed "Yellow"
            & $gradleCmd --stop 2>$null | Out-Null
            Get-ChildItem -Path "$repoRoot\.gradle" -Recurse -Filter "*.lock" -ErrorAction SilentlyContinue | Remove-Item -Force -ErrorAction SilentlyContinue
            $buildOutRetry = & $gradleCmd $buildTarget --no-daemon --console=plain 2>&1
            Log-Message ($buildOutRetry | Out-String)
            if ($LASTEXITCODE -ne 0) {
                throw "Gradle $buildTarget failed! Check details in $logFile"
            }
        }
    }

    # 5. Select optimal APK based on TV ABI
    $apkDir = Join-Path $repoRoot "app\build\outputs\apk\$Flavor\release"
    if (-not (Test-Path $apkDir)) {
        $apkDir = Join-Path $repoRoot "app\build\outputs\apk\release"
    }
    $apk = $null
    $chosenType = "universal"

    $arm64Candidate = Get-ChildItem -Path $apkDir -Filter "*arm64*.apk" -ErrorAction SilentlyContinue | Select-Object -First 1
    $arm32Candidate = Get-ChildItem -Path $apkDir -Filter "*armeabi*.apk" -ErrorAction SilentlyContinue | Select-Object -First 1
    $uniCandidate = Get-ChildItem -Path $apkDir -Filter "*universal*.apk" -ErrorAction SilentlyContinue | Select-Object -First 1

    if ($deviceAbi -like "arm64*" -and $arm64Candidate) {
        $apk = $arm64Candidate.FullName
        $chosenType = "arm64-v8a"
    } elseif ($deviceAbi -like "armeabi*" -and $arm32Candidate) {
        $apk = $arm32Candidate.FullName
        $chosenType = "armeabi-v7a"
    } elseif ($uniCandidate) {
        $apk = $uniCandidate.FullName
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

    # 6. Install APK on TV Box
    Update-Status ($msg.StepInstall -f $chosenType, $TvHost) "Cyan"
    $installOutput = & adb -s $deviceTarget install -r "$apk" 2>&1 | Out-String
    Log-Message $installOutput

    if ($installOutput -match "INSTALL_FAILED_UPDATE_INCOMPATIBLE|INSTALL_FAILED_VERSION_DOWNGRADE|INSTALL_FAILED_SHARED_USER_INCOMPATIBLE") {
        Update-Status $msg.SigConflict "Yellow"
        & adb -s $deviceTarget uninstall com.next.tv.launcher 2>&1 | Out-Null
        $installOutput = & adb -s $deviceTarget install -r "$apk" 2>&1 | Out-String
        Log-Message $installOutput
    }

    if ($installOutput -notmatch "Success") {
        throw "ADB install failed: $installOutput"
    }

    # 7. Grant Permissions & Launch App
    Update-Status $msg.GrantPerms "Cyan"
    & adb -s $deviceTarget shell pm grant com.next.tv.launcher android.permission.ACCESS_FINE_LOCATION 2>$null | Out-Null
    & adb -s $deviceTarget shell pm grant com.next.tv.launcher android.permission.ACCESS_COARSE_LOCATION 2>$null | Out-Null
    & adb -s $deviceTarget shell pm grant com.next.tv.launcher android.permission.READ_EXTERNAL_STORAGE 2>$null | Out-Null
    & adb -s $deviceTarget shell pm grant com.next.tv.launcher android.permission.WRITE_EXTERNAL_STORAGE 2>$null | Out-Null

    Update-Status $msg.LaunchApp "Cyan"
    & adb -s $deviceTarget shell logcat -c 2>$null | Out-Null
    & adb -s $deviceTarget shell am start -n com.next.tv.launcher/.MainActivity 2>&1 | Out-Null

    if ($SetDefaultHome) {
        & adb -s $deviceTarget shell cmd package set-home-activity com.next.tv.launcher/.MainActivity 2>$null | Out-Null
    }

    # 8. Check for crash logs within 2 seconds
    Start-Sleep -Seconds 2
    $recentLogs = & adb -s $deviceTarget shell "logcat -d" 2>$null | Select-String -Pattern "FATAL EXCEPTION"
    if ($recentLogs) {
        Write-Host ""
        Write-Host $msg.CrashWarning -ForegroundColor Red
        Log-Message "Crash log: $recentLogs"
        Write-Host ($msg.LogLocation -f $logFile) -ForegroundColor DarkYellow
        Write-Host ""
        Read-Host $msg.PressEnterExit
        exit 1
    }

    # Success: Print clean completion message on same line and auto-exit after 2s
    Update-Status $msg.Success "Green"
    Write-Host ""
    Log-Message "Deploy finished successfully."
    Start-Sleep -Seconds 2
    exit 0
} catch {
    # Failure: Break line, display red error, point to log file, and pause
    Write-Host ""
    Write-Host ""
    Write-Host ($msg.ErrorOccurred -f $_) -ForegroundColor Red
    Log-Message "ERROR: $_"
    Write-Host ""
    Write-Host ($msg.LogLocation -f $logFile) -ForegroundColor DarkYellow
    Write-Host ""
    Read-Host $msg.PressEnterExit
    exit 1
}