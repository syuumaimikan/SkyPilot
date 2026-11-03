@echo off
setlocal
cd /d "%~dp0"

if not "%1"=="" (
    powershell.exe -ExecutionPolicy Bypass -File "scripts\server.ps1" %*
    goto end
)

:menu
echo ===================================================
echo               SkyPilot Server Manager
echo ===================================================
echo  1. Start Server (Docker不要・ネイティブ起動)
echo  2. Stop Server  (サーバー停止)
echo  3. Restart Server (再起動)
echo  4. Server Status (状態確認)
echo  5. Enable Windows Auto-Start (Windows起動時の自動起動 有効化)
echo  6. Disable Windows Auto-Start (Windows起動時の自動起動 無効化)
echo  7. Exit
echo ===================================================
set /p choice="Select an option (1-7): "

if "%choice%"=="1" powershell.exe -ExecutionPolicy Bypass -File "scripts\server.ps1" start
if "%choice%"=="2" powershell.exe -ExecutionPolicy Bypass -File "scripts\server.ps1" stop
if "%choice%"=="3" powershell.exe -ExecutionPolicy Bypass -File "scripts\server.ps1" restart
if "%choice%"=="4" powershell.exe -ExecutionPolicy Bypass -File "scripts\server.ps1" status
if "%choice%"=="5" powershell.exe -ExecutionPolicy Bypass -File "scripts\server.ps1" autostart enable
if "%choice%"=="6" powershell.exe -ExecutionPolicy Bypass -File "scripts\server.ps1" autostart disable
if "%choice%"=="7" goto end

echo.
pause

:end
endlocal

