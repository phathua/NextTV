@echo off
setlocal

title NextTV - Push Release APK to TV Box

rem Chuyen sang chay truc tiep bang PowerShell de hien thi mau, xu ly unicode va tranh cac loi cu phap cua CMD
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0deploy_tv.ps1" %*

endlocal
