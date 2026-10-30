@echo off
setlocal
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0..\ps\ensure_adb.ps1"
endlocal
