@echo off
setlocal enabledelayedexpansion

title NextTV - Push Release APK to TV Box

echo ========================================================
echo       NextTV Launcher - Auto Build and Deploy Release
echo ========================================================
echo.

cd /d "%~dp0"

if not "%~1"=="" (
    set "TV_IP=%~1"
) else if not "%TV_IP%"=="" (
    rem Su dung bien moi truong TV_IP da thiet lap
) else (
    echo [*] Dang tu dong quet tim Android TV Box tren mang LAN (Port 5555)...
    
    rem Dung PowerShell (mac dinh cua Windows) quet song song 254 IP trong ~1.5 giay
    for /f "delims=" %%i in ('powershell -NoProfile -Command "$ip = (Get-NetIPAddress -AddressFamily IPv4 | Where-Object { $_.InterfaceAlias -notmatch 'Loopback|vEthernet|WSL' -and $_.IPAddress -notmatch '^169\.254' } | Select-Object -First 1).IPAddress; if ($ip) { $base = $ip.Substring(0, $ip.LastIndexOf('.') + 1); $tasks = 1..254 | ForEach-Object { $target = $base + $_; $tcp = New-Object System.Net.Sockets.TcpClient; @{ IP = $target; Task = $tcp.ConnectAsync($target, 5555); Client = $tcp } }; Start-Sleep -Milliseconds 1400; foreach ($t in $tasks) { if ($t.Client.Connected) { Write-Output $t.IP; break } } }"') do set "TV_IP=%%i"

    if not "!TV_IP!"=="" (
        echo [+] Da tim thay TV Box tai IP: !TV_IP!
    ) else (
        echo [!] Khong tu dong tim thay TV Box nao dang mo port 5555.
        set /p "TV_IP=Nhap dia chi IP cua Android TV Box (vi du: 192.168.1.100): "
    )
)

if "!TV_IP!"=="" (
    echo [!] Chua xac dinh duoc dia chi IP cua TV Box. Huy bo.
    exit /b 1
)

echo [*] Checking ADB connection to !TV_IP!:5555...
adb connect !TV_IP!:5555 >nul 2>&1

adb devices | findstr /i "!TV_IP!:5555" >nul
if errorlevel 1 (
    echo [!] TV Box is not connected!
    echo [*] Retrying ADB connect...
    adb connect !TV_IP!:5555
    timeout /t 2 >nul
)

echo.
echo [*] Generating Native Secrets Header...
if exist "tools\bat\generate_header.bat" (
    call "tools\bat\generate_header.bat"
) else if exist "tools\python\generate_native_secrets.py" (
    python "tools\python\generate_native_secrets.py"
)
if errorlevel 1 (
    echo [-] Secret generation failed!
    exit /b 1
)

echo.
echo [*] Building Release APK with Gradle...
call gradlew.bat assembleRelease --console=plain
if errorlevel 1 (
    echo [-] Gradle assembleRelease failed!
    exit /b 1
)

echo.
set "APK_PATH="
if exist "app\build\outputs\apk\release\app-armeabi-v7a-release.apk" (
    set "APK_PATH=app\build\outputs\apk\release\app-armeabi-v7a-release.apk"
) else if exist "app\build\outputs\apk\release\app-arm64-v8a-release.apk" (
    set "APK_PATH=app\build\outputs\apk\release\app-arm64-v8a-release.apk"
) else if exist "app\build\outputs\apk\release\app-universal-release.apk" (
    set "APK_PATH=app\build\outputs\apk\release\app-universal-release.apk"
)

if "%APK_PATH%"=="" (
    echo [-] Could not find any release APK in app\build\outputs\apk\release!
    exit /b 1
)

echo [+] Selected APK: %APK_PATH%
echo.
echo [*] Installing on TV Box (%TV_IP%)...
adb -s %TV_IP%:5555 install -r "%APK_PATH%"
if errorlevel 1 (
    echo [!] Direct IP install failed, trying default ADB target...
    adb install -r "%APK_PATH%"
)

echo.
echo [*] Granting runtime permissions...
adb -s %TV_IP%:5555 shell pm grant com.next.tv.launcher android.permission.ACCESS_FINE_LOCATION >nul 2>&1
adb -s %TV_IP%:5555 shell pm grant com.next.tv.launcher android.permission.ACCESS_COARSE_LOCATION >nul 2>&1
adb -s %TV_IP%:5555 shell pm grant com.next.tv.launcher android.permission.READ_EXTERNAL_STORAGE >nul 2>&1
adb -s %TV_IP%:5555 shell pm grant com.next.tv.launcher android.permission.WRITE_EXTERNAL_STORAGE >nul 2>&1

echo.
echo [*] Starting NextTV Launcher...
adb -s %TV_IP%:5555 shell am start -n com.next.tv.launcher/.MainActivity

echo.
echo ========================================================
echo [+] SUCCESS: NextTV Release is deployed and running!
echo ========================================================
echo.
