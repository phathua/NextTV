@echo off
setlocal
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0..\ps\generate_header.ps1"
endlocal
