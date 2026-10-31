@echo off
setlocal

title NextTV - Generate Native C++ Security Header

where pwsh >nul 2>&1
if %ERRORLEVEL% equ 0 (
    set "PS_BIN=pwsh"
) else (
    set "PS_BIN=powershell"
)

%PS_BIN% -NoProfile -ExecutionPolicy Bypass -File "%~dp0..\ps\generate_header.ps1" %*
set "EXIT_CODE=%ERRORLEVEL%"

if %EXIT_CODE% neq 0 (
    echo.
    echo [!] Script terminated with error code: %EXIT_CODE%
    echo %cmdcmdline% | find /i "%~0" >nul
    if %ERRORLEVEL% equ 0 pause
)

endlocal & exit /b %EXIT_CODE%
