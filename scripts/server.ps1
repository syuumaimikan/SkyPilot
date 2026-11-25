param(
    [string]$Action = "menu",
    [string]$SubAction = "status"
)
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8
# SkyPilot Server Manager (Windows)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$dataDir = Join-Path $projectRoot 'data'
$logsDir = Join-Path $projectRoot 'logs'
$pidFile = Join-Path $dataDir 'server.pid'
$webPidFile = Join-Path $dataDir 'web.pid'
$logFile = Join-Path $logsDir 'server.log'
$webLogFile = Join-Path $logsDir 'web.log'
$webErrFile = Join-Path $logsDir 'web_err.log'
$runKey = "HKCU:\Software\Microsoft\Windows\CurrentVersion\Run"
$runVal = "SkyPilotServer"

if (-not (Test-Path -LiteralPath $dataDir)) { New-Item -ItemType Directory -Path $dataDir -Force | Out-Null }
if (-not (Test-Path -LiteralPath $logsDir)) { New-Item -ItemType Directory -Path $logsDir -Force | Out-Null }

# Ensure common user/system toolchain directories are available even under Windows Auto-Start or minimal shells.
$extraPaths = @(
    "$env:USERPROFILE\.cargo\bin",
    "$env:APPDATA\npm",
    "$env:LOCALAPPDATA\pnpm",
    "$env:LOCALAPPDATA\pnpm\bin",
    "C:\Program Files\nodejs",
    "C:\Program Files (x86)\nodejs",
    "$env:LOCALAPPDATA\Programs\nodejs"
)
foreach ($ep in $extraPaths) {
    if ($ep -and (Test-Path -LiteralPath $ep) -and ($env:PATH -notlike "*$ep*")) {
        $env:PATH = "$ep;$env:PATH"
    }
}

function Resolve-CargoExe {
    $candidates = @(
        "$env:USERPROFILE\.cargo\bin\cargo.exe"
    )
    $cmd = Get-Command cargo.exe -ErrorAction SilentlyContinue
    if ($cmd) { $candidates = @($cmd.Source) + $candidates }
    foreach ($c in $candidates) {
        if ($c -and (Test-Path -LiteralPath $c)) { return $c }
    }
    throw "Cargo not found. Please install Rust from https://rustup.rs/."
}

function Resolve-NodeExe {
    $cmd = Get-Command node.exe -ErrorAction SilentlyContinue
    if ($cmd -and (Test-Path -LiteralPath $cmd.Source)) { return $cmd.Source }
    foreach ($c in @("C:\Program Files\nodejs\node.exe", "C:\Program Files (x86)\nodejs\node.exe", "$env:LOCALAPPDATA\Programs\nodejs\node.exe")) {
        if (Test-Path -LiteralPath $c) { return $c }
    }
    throw "Node.js not found. Please install Node.js 20.9+ from https://nodejs.org/."
}

function Resolve-PnpmCmd {
    $candidates = @(
        "$env:APPDATA\npm\pnpm.cmd",
        "$env:LOCALAPPDATA\pnpm\pnpm.cmd",
        "$env:LOCALAPPDATA\pnpm\bin\pnpm.cmd",
        "C:\Program Files\nodejs\pnpm.cmd"
    )
    $cmd = Get-Command pnpm.cmd -ErrorAction SilentlyContinue
    if ($cmd) { $candidates = @($cmd.Source) + $candidates }
    $psCmd = Get-Command pnpm -ErrorAction SilentlyContinue
    if ($psCmd -and $psCmd.Source) {
        if ($psCmd.Source.EndsWith(".ps1", [System.StringComparison]::OrdinalIgnoreCase)) {
            $candidates = @([System.IO.Path]::ChangeExtension($psCmd.Source, ".cmd")) + $candidates
        } else {
            $candidates = @($psCmd.Source) + $candidates
        }
    }
    foreach ($c in $candidates) {
        if ($c -and (Test-Path -LiteralPath $c)) { return $c }
    }
    $corepack = Get-Command corepack.cmd -ErrorAction SilentlyContinue
    if ($corepack -and (Test-Path -LiteralPath $corepack.Source)) { return $corepack.Source }
    throw "pnpm not found. Run 'npm install -g pnpm' or enable corepack."
}

function Find-ApiExe {
    $releaseExe = Join-Path $projectRoot 'target\release\skypilot-api.exe'
    $debugExe = Join-Path $projectRoot 'target\debug\skypilot-api.exe'
    if (Test-Path -LiteralPath $releaseExe) { return $releaseExe }
    if (Test-Path -LiteralPath $debugExe) { return $debugExe }
    return $null
}

function Build-ApiExe {
    # Windows locks a running binary during linking (LNK1104); stop any running API first.
    $running = Get-RunningProcess
    if ($running) {
        Write-Host "[SkyPilot] Stopping running API server before rebuild (PID: $($running.Id))..." -ForegroundColor Yellow
        Stop-Process -Id $running.Id -Force -ErrorAction SilentlyContinue
        Start-Sleep -Milliseconds 500
    }
    Write-Host "[SkyPilot] Building skypilot-api binary (release)..." -ForegroundColor Cyan
    $cargo = Resolve-CargoExe
    Push-Location $projectRoot
    try {
        & $cargo build --release -p skypilot-api
        if ($LASTEXITCODE -ne 0) { throw "Cargo build failed with exit code $LASTEXITCODE." }
    } finally {
        Pop-Location
    }
    $exe = Find-ApiExe
    if (-not $exe) { throw "skypilot-api.exe could not be found after build." }
    return $exe
}

function Get-ServerPort {
    if ($env:API_BIND) {
        $parts = $env:API_BIND -split ':'
        return [int]$parts[-1]
    }
    $envPath = Join-Path $projectRoot '.env'
    if (Test-Path -LiteralPath $envPath) {
        $envText = [IO.File]::ReadAllText($envPath)
        if ($envText -match '(?m)^API_BIND\s*=\s*"?([^"\r\n]+)"?') {
            $bind = $matches[1].Trim()
            $parts = $bind -split ':'
            return [int]$parts[-1]
        }
    }
    return 8080
}

function Test-ServerHealth([int]$port) {
    try {
        $resp = Invoke-RestMethod -Uri "http://127.0.0.1:$port/api/health" -Method Get -TimeoutSec 2 -ErrorAction Stop
        if ($resp -and ($resp.ok -eq $true -or $resp.status -eq "ok")) { return $true }
    } catch {
        return $false
    }
    return $false
}

function Get-LocalLanIp {
    try {
        $addrs = Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue |
            Where-Object {
                $_.InterfaceAlias -notlike "*Loopback*" -and
                $_.InterfaceAlias -notlike "*WSL*" -and
                $_.InterfaceAlias -notlike "*vEthernet*" -and
                $_.IPAddress -notlike "169.254.*" -and
                $_.IPAddress -notlike "127.*" -and
                $_.IPAddress -notlike "192.168.56.*"
            } |
            Sort-Object { if ($_.InterfaceAlias -like "*Wi-Fi*" -or $_.InterfaceAlias -like "*イーサネット*") { 0 } else { 1 } }
        $ipList = @($addrs)
        if ($ipList.Count -gt 0) {
            return $ipList[0].IPAddress
        }
    } catch {}
    return $null
}

function Test-WebHealth {
    try {
        $resp = Invoke-WebRequest -Uri "http://127.0.0.1:3000/" -Method Get -TimeoutSec 3 -UseBasicParsing -ErrorAction Stop
        if ($resp.StatusCode -ge 200 -and $resp.StatusCode -lt 400) { return $true }
    } catch {
        if ($_.Exception.Response -and $_.Exception.Response.StatusCode) {
            $code = [int]$_.Exception.Response.StatusCode
            if ($code -ge 200 -and $code -lt 500) { return $true }
        }
    }
    return $false
}

function Get-RunningProcess {
    if (Test-Path -LiteralPath $pidFile) {
        $pidContent = (Get-Content -LiteralPath $pidFile -Raw).Trim()
        if ($pidContent -match '^\d+$') {
            $p = Get-Process -Id ([int]$pidContent) -ErrorAction SilentlyContinue
            if ($p -and $p.ProcessName -like '*skypilot-api*') { return $p }
        }
        Remove-Item -LiteralPath $pidFile -Force -ErrorAction SilentlyContinue
    }
    $p = Get-Process -Name 'skypilot-api' -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($p) {
        Set-Content -LiteralPath $pidFile -Value $p.Id
        return $p
    }
    return $null
}

function Get-PortListenerProcess([int]$port) {
    $conns = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
    if ($conns) {
        foreach ($c in $conns) {
            if ($c.OwningProcess -gt 0) {
                $p = Get-Process -Id $c.OwningProcess -ErrorAction SilentlyContinue
                if ($p) { return $p }
            }
        }
    }
    return $null
}

function Get-RunningWebProcess {
    $listener = Get-PortListenerProcess 3000
    if ($listener -and $listener.ProcessName -ne 'skypilot-api') {
        Set-Content -LiteralPath $webPidFile -Value $listener.Id
        return $listener
    }
    if (Test-Path -LiteralPath $webPidFile) {
        $pidContent = (Get-Content -LiteralPath $webPidFile -Raw).Trim()
        if ($pidContent -match '^\d+$') {
            $p = Get-Process -Id ([int]$pidContent) -ErrorAction SilentlyContinue
            if ($p -and $p.ProcessName -eq 'node') { return $p }
        }
        Remove-Item -LiteralPath $webPidFile -Force -ErrorAction SilentlyContinue
    }
    return $null
}

function Test-WebBuildValid {
    $webDir = Join-Path $projectRoot 'apps\web'
    $buildId = Join-Path $webDir '.next\BUILD_ID'
    if (-not (Test-Path -LiteralPath $buildId)) { return $false }
    $reqFiles = Join-Path $webDir '.next\required-server-files.json'
    if (Test-Path -LiteralPath $reqFiles) {
        try {
            $txt = [IO.File]::ReadAllText($reqFiles)
            if ($txt -match '"output"\s*:\s*"standalone"') { return $false }
        } catch {}
    }
    return $true
}

function Build-WebFrontend {
    $pnpmCmd = Resolve-PnpmCmd
    $webDir = Join-Path $projectRoot 'apps\web'
    $nextBin = Join-Path $webDir 'node_modules\next\dist\bin\next'
    Push-Location $projectRoot
    try {
        if (-not (Test-Path -LiteralPath $nextBin)) {
            Write-Host "[SkyPilot] Installing web dependencies..." -ForegroundColor Cyan
            if ($pnpmCmd -like '*corepack*') { & $pnpmCmd pnpm install } else { & $pnpmCmd install }
            if ($LASTEXITCODE -ne 0) { throw "pnpm install failed with exit code $LASTEXITCODE." }
        }
        Write-Host "[SkyPilot] Building Next.js Web Application..." -ForegroundColor Cyan
        $prevStandalone = $env:NEXT_STANDALONE
        $env:NEXT_STANDALONE = "false"
        try {
            if ($pnpmCmd -like '*corepack*') { & $pnpmCmd pnpm --filter @skypilot/web build } else { & $pnpmCmd --filter @skypilot/web build }
            if ($LASTEXITCODE -ne 0) { throw "Next.js build failed with exit code $LASTEXITCODE." }
        } finally {
            $env:NEXT_STANDALONE = $prevStandalone
        }
    } finally {
        Pop-Location
    }
}

function Start-ApiServer {
    $existing = Get-RunningProcess
    $port = Get-ServerPort
    if ($existing) {
        if (Test-ServerHealth $port) {
            Write-Host "[SkyPilot] API server is already running (PID: $($existing.Id), Port: $port)." -ForegroundColor Yellow
            return
        }
        Write-Host "[SkyPilot] Found unresponsive API process (PID: $($existing.Id)); restarting..." -ForegroundColor Yellow
        Stop-Process -Id $existing.Id -Force -ErrorAction SilentlyContinue
        Start-Sleep -Milliseconds 400
    }

    $exe = Find-ApiExe
    if (-not $exe) {
        $exe = Build-ApiExe
    }

    Write-Host "[SkyPilot] Starting SkyPilot API backend (Port: $port, native without Docker)..." -ForegroundColor Cyan
    $env:API_BIND = "0.0.0.0:$port"
    $env:SKYPILOT_ROOT = $projectRoot
    $env:SKYPILOT_LOG_FILE = $logFile
    $env:SKYPILOT_DETACH = "1"

    $proc = $null
    $apiStarted = $false
    try {
        $startup = New-CimInstance -ClassName Win32_ProcessStartup -Property @{ ShowWindow = [uint16]0 } -ClientOnly
        $apiCmd = "cmd.exe /d /c `"set API_BIND=0.0.0.0:$port&& set SKYPILOT_ROOT=$projectRoot&& set SKYPILOT_LOG_FILE=$logFile&& set SKYPILOT_DETACH=1&& cd /d `"$projectRoot`" && `"$exe`" serve --detach`""
        $res = Invoke-CimMethod -ClassName Win32_Process -MethodName Create -Arguments @{
            CommandLine = $apiCmd
            CurrentDirectory = $projectRoot
            ProcessStartupInformation = $startup
        }
        if ($res.ReturnValue -eq 0 -and $res.ProcessId -gt 0) {
            $apiStarted = $true
        }
    } catch {
        $apiStarted = $false
    }

    if (-not $apiStarted) {
        $psi = New-Object System.Diagnostics.ProcessStartInfo
        $psi.FileName = $exe
        $psi.Arguments = "serve --detach"
        $psi.WorkingDirectory = $projectRoot
        $psi.UseShellExecute = $true
        $psi.WindowStyle = [System.Diagnostics.ProcessWindowStyle]::Hidden
        $proc = [System.Diagnostics.Process]::Start($psi)
        if ($proc) {
            Set-Content -LiteralPath $pidFile -Value $proc.Id
        }
    }

    $healthy = $false
    for ($i = 0; $i -lt 30; $i++) {
        Start-Sleep -Milliseconds 500
        if (Test-ServerHealth $port) {
            $healthy = $true
            break
        }
        if ($proc -and $proc.HasExited) {
            Write-Host "[SkyPilot] API backend exited unexpectedly (ExitCode: $($proc.ExitCode))." -ForegroundColor Red
            if (Test-Path -LiteralPath $logFile) {
                Write-Host "--- Recent API log ($logFile) ---" -ForegroundColor DarkYellow
                Get-Content -LiteralPath $logFile -Tail 25
            }
            return
        }
    }

    if ($healthy) {
        $runningApi = Get-RunningProcess
        $serverProcessId = if ($runningApi) { $runningApi.Id } elseif ($proc) { $proc.Id } else { 0 }
        if ($serverProcessId -gt 0) { Set-Content -LiteralPath $pidFile -Value $serverProcessId }
        Write-Host "[SkyPilot] API backend started successfully!" -ForegroundColor Green
        Write-Host "  PID:       $serverProcessId"
        Write-Host "  API Port:  $port (http://localhost:$port/api/)"
    } else {
        Write-Host "[SkyPilot] API backend started, still initializing (may be syncing NEU/Hypixel data). Check $logFile." -ForegroundColor Yellow
    }
}

function Start-WebFrontend {
    $existingWeb = Get-RunningWebProcess
    if ($existingWeb -and (Test-WebHealth)) {
        Write-Host "[SkyPilot] Web frontend is already running (PID: $($existingWeb.Id), Port: 3000)." -ForegroundColor Yellow
        return
    }
    if ($existingWeb) {
        Write-Host "[SkyPilot] Stopping unresponsive process on port 3000 (PID: $($existingWeb.Id))..." -ForegroundColor Yellow
        Stop-Process -Id $existingWeb.Id -Force -ErrorAction SilentlyContinue
        Start-Sleep -Milliseconds 400
    }

    $nodeExe = Resolve-NodeExe
    $webDir = Join-Path $projectRoot 'apps\web'
    $nextBin = Join-Path $webDir 'node_modules\next\dist\bin\next'

    if (-not (Test-Path -LiteralPath $nextBin) -or -not (Test-WebBuildValid)) {
        Build-WebFrontend
    }

    Write-Host "[SkyPilot] Starting Next.js Web App on http://localhost:3000/..." -ForegroundColor Cyan
    $port = Get-ServerPort
    $apiUrl = "http://127.0.0.1:$port"

    # Launch detached via cmd.exe + WMI Win32_Process (fallback: Start-Process) so Next.js survives
    # terminal closure AND captures both stdout and stderr into logs\web.log and logs\web_err.log.
    $cmdLine = "cmd.exe /d /c `"set SKYPILOT_API_URL=$apiUrl&& set NODE_ENV=production&& cd /d `"$webDir`" && `"$nodeExe`" `"$nextBin`" start -H 0.0.0.0 --port 3000 >> `"$webLogFile`" 2>> `"$webErrFile`"`""
    $started = $false
    try {
        $startup = New-CimInstance -ClassName Win32_ProcessStartup -Property @{ ShowWindow = [uint16]0 } -ClientOnly
        $res = Invoke-CimMethod -ClassName Win32_Process -MethodName Create -Arguments @{
            CommandLine = $cmdLine
            CurrentDirectory = $webDir
            ProcessStartupInformation = $startup
        }
        if ($res.ReturnValue -eq 0 -and $res.ProcessId -gt 0) {
            $started = $true
        }
    } catch {
        $started = $false
    }

    if (-not $started) {
        $env:SKYPILOT_API_URL = $apiUrl
        $env:NODE_ENV = "production"
        $proc = Start-Process -FilePath $nodeExe `
            -ArgumentList "`"$nextBin`" start -H 0.0.0.0 --port 3000" `
            -WorkingDirectory $webDir `
            -WindowStyle Hidden `
            -RedirectStandardOutput $webLogFile `
            -RedirectStandardError $webErrFile `
            -PassThru
        if ($proc) {
            Set-Content -LiteralPath $webPidFile -Value $proc.Id
        }
    }

    for ($i = 0; $i -lt 30; $i++) {
        Start-Sleep -Milliseconds 500
        if (Test-WebHealth) {
            $listener = Get-PortListenerProcess 3000
            if ($listener) { Set-Content -LiteralPath $webPidFile -Value $listener.Id }
            $pidText = if ($listener) { " (PID: $($listener.Id))" } else { "" }
            Write-Host "[SkyPilot] Web UI is ready at http://localhost:3000/$pidText !" -ForegroundColor Green
            return
        }
    }

    Write-Host "[SkyPilot] Web UI did not respond within 15s. Check $webLogFile and $webErrFile." -ForegroundColor Red
    if (Test-Path -LiteralPath $webErrFile) {
        Write-Host "--- Recent Web error log ($webErrFile) ---" -ForegroundColor DarkYellow
        Get-Content -LiteralPath $webErrFile -Tail 25
    }
}

function Start-ServerAction {
    Start-ApiServer
    Start-WebFrontend
    $lanIp = Get-LocalLanIp
    $port = Get-ServerPort
    Write-Host ""
    Write-Host "===================================================" -ForegroundColor Green
    Write-Host "  SkyPilot is fully running!" -ForegroundColor Green
    Write-Host "  Local URL:   http://localhost:3000/" -ForegroundColor Cyan
    if ($lanIp) {
        Write-Host "  Network URL: http://$($lanIp):3000/" -ForegroundColor Cyan
    }
    Write-Host "  API:         http://localhost:$port/api/" -ForegroundColor White
    Write-Host "===================================================" -ForegroundColor Green
}

function Stop-ServerAction {
    $p = Get-RunningProcess
    if ($p) {
        Write-Host "[SkyPilot] Stopping API server (PID: $($p.Id))..." -ForegroundColor Cyan
        Stop-Process -Id $p.Id -Force -ErrorAction SilentlyContinue
    }
    $all = Get-Process -Name 'skypilot-api' -ErrorAction SilentlyContinue
    if ($all) { $all | Stop-Process -Force -ErrorAction SilentlyContinue }
    if (Test-Path -LiteralPath $pidFile) { Remove-Item -LiteralPath $pidFile -Force -ErrorAction SilentlyContinue }

    $w = Get-RunningWebProcess
    if ($w) {
        Write-Host "[SkyPilot] Stopping Web frontend (PID: $($w.Id))..." -ForegroundColor Cyan
        Stop-Process -Id $w.Id -Force -ErrorAction SilentlyContinue
    }
    $conns = Get-NetTCPConnection -LocalPort 3000 -State Listen -ErrorAction SilentlyContinue
    if ($conns) {
        foreach ($c in $conns) {
            if ($c.OwningProcess -gt 0) {
                Stop-Process -Id $c.OwningProcess -Force -ErrorAction SilentlyContinue
            }
        }
    }
    if (Test-Path -LiteralPath $webPidFile) { Remove-Item -LiteralPath $webPidFile -Force -ErrorAction SilentlyContinue }

    Write-Host "[SkyPilot] Services stopped." -ForegroundColor Green
}

function Restart-ServerAction {
    Stop-ServerAction
    Start-Sleep -Seconds 1
    Start-ServerAction
}

function Rebuild-ServerAction {
    Stop-ServerAction
    Start-Sleep -Seconds 1
    Build-ApiExe | Out-Null
    Build-WebFrontend
    Start-ServerAction
}

function Show-StatusAction {
    $p = Get-RunningProcess
    $port = Get-ServerPort
    $healthy = Test-ServerHealth $port

    $w = Get-RunningWebProcess
    $webHealthy = Test-WebHealth
    $lanIp = Get-LocalLanIp

    $autostartCmd = (Get-ItemProperty -Path $runKey -Name $runVal -ErrorAction SilentlyContinue).$runVal

    Write-Host "---------------- SkyPilot System Status ----------------" -ForegroundColor Cyan
    if ($p -and $healthy) {
        Write-Host "API Backend:   RUNNING (Healthy) - PID: $($p.Id), Port: $port" -ForegroundColor Green
    } elseif ($p) {
        Write-Host "API Backend:   STARTING / UNRESPONSIVE - PID: $($p.Id), Port: $port" -ForegroundColor Yellow
    } else {
        Write-Host "API Backend:   STOPPED" -ForegroundColor DarkGray
    }

    if ($w -and $webHealthy) {
        Write-Host "Web Frontend:  RUNNING (Online) - PID: $($w.Id)" -ForegroundColor Green
        Write-Host "  - Local:     http://localhost:3000/" -ForegroundColor Cyan
        if ($lanIp) {
            Write-Host "  - Network:   http://$($lanIp):3000/" -ForegroundColor Cyan
        }
    } elseif ($w) {
        Write-Host "Web Frontend:  PORT 3000 IN USE (PID: $($w.Id))" -ForegroundColor Yellow
    } else {
        Write-Host "Web Frontend:  STOPPED" -ForegroundColor DarkGray
    }

    if ($autostartCmd) {
        Write-Host "Auto-Start:    ENABLED (Windows Logon)" -ForegroundColor Green
    } else {
        Write-Host "Auto-Start:    DISABLED" -ForegroundColor DarkGray
    }
    Write-Host "API Log:       $logFile"
    Write-Host "Web Log:       $webLogFile"
    Write-Host "Web Err Log:   $webErrFile"
    Write-Host "--------------------------------------------------------" -ForegroundColor Cyan
}

function Set-AutostartAction([string]$cmdAction) {
    switch ($cmdAction.ToLower()) {
        "enable" {
            $scriptPath = Join-Path $PSScriptRoot "server.ps1"
            $cmd = "powershell.exe -WindowStyle Hidden -ExecutionPolicy Bypass -File `"$scriptPath`" start"
            Set-ItemProperty -Path $runKey -Name $runVal -Value $cmd
            Write-Host "[SkyPilot] Auto-start on Windows login is now ENABLED." -ForegroundColor Green
            Write-Host "Command: $cmd"
        }
        "disable" {
            Remove-ItemProperty -Path $runKey -Name $runVal -ErrorAction SilentlyContinue
            Write-Host "[SkyPilot] Auto-start on Windows login is now DISABLED." -ForegroundColor Yellow
        }
        "status" {
            $cmd = (Get-ItemProperty -Path $runKey -Name $runVal -ErrorAction SilentlyContinue).$runVal
            if ($cmd) {
                Write-Host "[SkyPilot] Auto-start is ENABLED." -ForegroundColor Green
                Write-Host "Command: $cmd"
            } else {
                Write-Host "[SkyPilot] Auto-start is DISABLED." -ForegroundColor Yellow
            }
        }
        default {
            Write-Host "Usage: .\scripts\server.ps1 autostart [enable|disable|status]" -ForegroundColor Red
        }
    }
}

function Open-DashboardAction {
    $url = "http://localhost:3000/"
    Write-Host "[SkyPilot] ブラウザでWebサイトを開きます: $url" -ForegroundColor Cyan
    Start-Process $url
}

function Show-Menu {
    [Console]::OutputEncoding = [System.Text.Encoding]::UTF8
    $OutputEncoding = [System.Text.Encoding]::UTF8

    while ($true) {
        $port = Get-ServerPort
        $p = Get-RunningProcess
        $healthy = if ($p) { Test-ServerHealth $port } else { $false }
        $w = Get-RunningWebProcess
        $webHealthy = Test-WebHealth
        $lanIp = Get-LocalLanIp
        $autostartCmd = (Get-ItemProperty -Path $runKey -Name $runVal -ErrorAction SilentlyContinue).$runVal

        Write-Host ""
        Write-Host "===================================================" -ForegroundColor Cyan
        Write-Host "          SkyPilot Server Manager (Windows)        " -ForegroundColor White -BackgroundColor DarkBlue
        Write-Host "===================================================" -ForegroundColor Cyan

        if ($p -and $healthy) {
            Write-Host " [API Backend]  稼働中 (RUNNING) - Port: $port" -ForegroundColor Green
        } else {
            Write-Host " [API Backend]  停止中 (STOPPED)" -ForegroundColor DarkGray
        }

        if ($w -and $webHealthy) {
            $webStr = "http://localhost:3000/"
            if ($lanIp) { $webStr += " (LAN: http://$($lanIp):3000/)" }
            Write-Host " [Webサイト]    稼働中 (RUNNING) - $webStr" -ForegroundColor Green
        } else {
            Write-Host " [Webサイト]    停止中 (STOPPED)" -ForegroundColor DarkGray
        }

        if ($autostartCmd) {
            Write-Host " [自動起動]     有効 (ENABLED - Windows起動時に自動実行)" -ForegroundColor Green
        } else {
            Write-Host " [自動起動]     無効 (DISABLED)" -ForegroundColor DarkGray
        }

        Write-Host "===================================================" -ForegroundColor Cyan
        Write-Host " 1. フル起動 (APIサーバー + Webサイト本体)" -ForegroundColor White
        Write-Host " 2. APIサーバーのみ起動 (軽量・MOD連携のみ)" -ForegroundColor White
        Write-Host " 3. すべて停止 (Stop All)" -ForegroundColor White
        Write-Host " 4. 再起動 (Restart)" -ForegroundColor White
        Write-Host " 5. 状態確認 (Status)" -ForegroundColor White
        Write-Host " 6. Windows起動時の自動起動 有効化 (Enable Auto-Start)" -ForegroundColor White
        Write-Host " 7. Windows起動時の自動起動 無効化 (Disable Auto-Start)" -ForegroundColor White
        Write-Host " 8. ブラウザでWebサイトを開く (http://localhost:3000/)" -ForegroundColor Cyan
        Write-Host " 9. 再ビルドして起動 (Rebuild & Start)" -ForegroundColor Yellow
        Write-Host " 0. 終了 (Exit)" -ForegroundColor Gray
        Write-Host "===================================================" -ForegroundColor Cyan

        $rawChoice = Read-Host "選択してください (0-9)"
        $choice = if ($rawChoice) { $rawChoice.Trim() } else { "" }
        switch ($choice) {
            "1" { Start-ServerAction }
            "2" { Start-ApiServer }
            "3" { Stop-ServerAction }
            "4" { Restart-ServerAction }
            "5" { Show-StatusAction }
            "6" { Set-AutostartAction "enable" }
            "7" { Set-AutostartAction "disable" }
            "8" { Open-DashboardAction }
            "9" { Rebuild-ServerAction }
            "0" { return }
            default {
                Write-Host "0-9 の番号を入力してください (Please enter 0-9):" -ForegroundColor Yellow
            }
        }
    }
}

# Map numeric arguments if passed from command line
switch ($Action.ToLower()) {
    "1" { $Action = "start" }
    "2" { $Action = "api" }
    "3" { $Action = "stop" }
    "4" { $Action = "restart" }
    "5" { $Action = "status" }
    "6" { $Action = "autostart"; $SubAction = "enable" }
    "7" { $Action = "autostart"; $SubAction = "disable" }
    "8" { $Action = "dashboard" }
    "9" { $Action = "rebuild" }
    "0" { return }
}

switch ($Action.ToLower()) {
    "start"     { Start-ServerAction }
    "api"       { Start-ApiServer }
    "stop"      { Stop-ServerAction }
    "restart"   { Restart-ServerAction }
    "rebuild"   { Rebuild-ServerAction }
    "status"    { Show-StatusAction }
    "autostart" { Set-AutostartAction $SubAction }
    "dashboard" { Open-DashboardAction }
    "menu"      { Show-Menu }
    default     { Show-Menu }
}
