@echo off
setlocal
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0..\ps\find_tv_ip.ps1"
endlocal
