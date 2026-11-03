@echo off
chcp 65001 >nul
powershell.exe -ExecutionPolicy Bypass -File "%~dp0scripts\hypixel-app.ps1" %*
pause

