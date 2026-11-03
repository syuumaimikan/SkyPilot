[CmdletBinding()]
param(
    [Parameter(Position = 0)]
    [ValidateSet("start", "stop", "restart", "status", "autostart")]
    [string]$Action = "status",

    [Parameter(Position = 1)]
    [string]$SubAction = "status"
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$dataDir = Join-Path $projectRoot 'data'
$logsDir = Join-Path $projectRoot 'logs'
$pidFile = Join-Path $dataDir 'server.pid'
$logFile = Join-Path $logsDir 'server.log'
$runKey = "HKCU:\Software\Microsoft\Windows\CurrentVersion\Run"
$runVal = "SkyPilotServer"

if (-not (Test-Path -LiteralPath $dataDir)) { New-Item -ItemType Directory -Path $dataDir -Force | Out-Null }
if (-not (Test-Path -LiteralPath $logsDir)) { New-Item -ItemType Directory -Path $logsDir -Force | Out-Null }

function Find-ApiExe {
    $releaseExe = Join-Path $projectRoot 'target\release\skypilot-api.exe'
    $debugExe = Join-Path $projectRoot 'target\debug\skypilot-api.exe'
    $candidates = @()
    if (Test-Path -LiteralPath $releaseExe) { $candidates += (Get-Item -LiteralPath $releaseExe) }
    if (Test-Path -LiteralPath $debugExe) { $candidates += (Get-Item -LiteralPath $debugExe) }
    if ($candidates.Count -eq 0) { return $null }
    return ($candidates | Sort-Object LastWriteTime -Descending | Select-Object -First 1).FullName
}

function Build-ApiExe {
    Write-Host "[SkyPilot] Building skypilot-api binary..." -ForegroundColor Cyan
    $cargo = "$env:USERPROFILE\.cargo\bin\cargo.exe"
    if (-not (Test-Path -LiteralPath $cargo)) {
        $cargoCmd = Get-Command cargo -ErrorAction SilentlyContinue
        if ($cargoCmd) { $cargo = $cargoCmd.Source }
        else { throw "Cargo not found. Please install Rust from https://rustup.rs/." }
    }
    & $cargo build -p skypilot-api
    if ($LASTEXITCODE -ne 0) { throw "Cargo build failed with exit code $LASTEXITCODE." }
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
            $p = [int]$parts[-1]
            $conn = Get-NetTCPConnection -LocalPort $p -State Listen -ErrorAction SilentlyContinue
            if ($conn) {
                $proc = Get-Process -Id $conn[0].OwningProcess -ErrorAction SilentlyContinue
                if ($proc -and $proc.ProcessName -ne 'skypilot-api') {
                    return 8080
                }
            }
            return $p
        }
    }
    return 3000
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

function Get-RunningProcess {
    if (Test-Path -LiteralPath $pidFile) {
        $pidContent = (Get-Content -LiteralPath $pidFile -Raw).Trim()
        if ($pidContent -match '^\d+$') {
            $p = Get-Process -Id ([int]$pidContent) -ErrorAction SilentlyContinue
            if ($p -and $p.ProcessName -eq 'skypilot-api') {
                return $p
            }
        }
        Remove-Item -LiteralPath $pidFile -Force -ErrorAction SilentlyContinue
    }
    $procs = Get-Process -Name 'skypilot-api' -ErrorAction SilentlyContinue
    if ($procs) {
        return $procs[0]
    }
    return $null
}

switch ($Action) {
    "start" {
        $existing = Get-RunningProcess
        $port = Get-ServerPort
        if ($existing) {
            Write-Host "[SkyPilot] Server is already running (PID: $($existing.Id), Port: $port)." -ForegroundColor Yellow
            return
        }

        $exe = Find-ApiExe
        if (-not $exe) {
            $exe = Build-ApiExe
        }

        Write-Host "[SkyPilot] Starting SkyPilot API server (Port: $port, native without Docker)..." -ForegroundColor Cyan
        $env:API_BIND = "0.0.0.0:$port"
        $env:SKYPILOT_ROOT = $projectRoot
        $env:SKYPILOT_LOG_FILE = $logFile
        $env:SKYPILOT_DETACH = "1"

        $proc = Start-Process -FilePath $exe -ArgumentList "serve" -WorkingDirectory $projectRoot -PassThru -WindowStyle Hidden
        Set-Content -LiteralPath $pidFile -Value $proc.Id

        # Wait up to 10 seconds for server health
        $healthy = $false
        for ($i = 0; $i -lt 20; $i++) {
            Start-Sleep -Milliseconds 500
            if (Test-ServerHealth $port) {
                $healthy = $true
                break
            }
            if ($proc.HasExited) {
                break
            }
        }

        if ($healthy) {
            Write-Host "[SkyPilot] Server successfully started!" -ForegroundColor Green
            Write-Host "  PID: $($proc.Id)"
            Write-Host "  Port: $port"
            Write-Host "  URL:  http://127.0.0.1:$port"
            Write-Host "  Log:  $logFile"
        } else {
            if ($proc.HasExited) {
                Write-Host "[SkyPilot] Server exited unexpectedly (Exit code: $($proc.ExitCode)). Check $logFile for errors." -ForegroundColor Red
            } else {
                Write-Host "[SkyPilot] Server started (PID: $($proc.Id)), waiting for initialization. Check $logFile." -ForegroundColor Yellow
            }
        }
    }

    "stop" {
        $p = Get-RunningProcess
        if (-not $p) {
            Write-Host "[SkyPilot] Server is not running." -ForegroundColor Yellow
            if (Test-Path -LiteralPath $pidFile) { Remove-Item -LiteralPath $pidFile -Force }
            return
        }
        Write-Host "[SkyPilot] Stopping server (PID: $($p.Id))..." -ForegroundColor Cyan
        Stop-Process -Id $p.Id -Force -ErrorAction SilentlyContinue
        # Also clean up any lingering instances
        $all = Get-Process -Name 'skypilot-api' -ErrorAction SilentlyContinue
        if ($all) {
            $all | Stop-Process -Force -ErrorAction SilentlyContinue
        }
        if (Test-Path -LiteralPath $pidFile) { Remove-Item -LiteralPath $pidFile -Force }
        Write-Host "[SkyPilot] Server stopped." -ForegroundColor Green
    }

    "restart" {
        & $MyInvocation.MyCommand.Path -Action stop
        Start-Sleep -Seconds 1
        & $MyInvocation.MyCommand.Path -Action start
    }

    "status" {
        $p = Get-RunningProcess
        $port = Get-ServerPort
        $healthy = Test-ServerHealth $port
        $autostartCmd = (Get-ItemProperty -Path $runKey -Name $runVal -ErrorAction SilentlyContinue).$runVal

        Write-Host "---------------- SkyPilot Server Status ----------------" -ForegroundColor Cyan
        if ($p -and $healthy) {
            Write-Host "Status:        RUNNING (Healthy)" -ForegroundColor Green
            Write-Host "PID:           $($p.Id)"
            Write-Host "Port:          $port"
            Write-Host "Health URL:    http://127.0.0.1:$port/api/health"
        } elseif ($p) {
            Write-Host "Status:        STARTING / UNRESPONSIVE" -ForegroundColor Yellow
            Write-Host "PID:           $($p.Id)"
            Write-Host "Port:          $port"
        } else {
            Write-Host "Status:        STOPPED" -ForegroundColor DarkGray
        }

        if ($autostartCmd) {
            Write-Host "Auto-Start:    ENABLED (Windows Logon)" -ForegroundColor Green
        } else {
            Write-Host "Auto-Start:    DISABLED" -ForegroundColor DarkGray
        }
        Write-Host "Log:           $logFile"
        Write-Host "--------------------------------------------------------" -ForegroundColor Cyan
    }

    "autostart" {
        switch ($SubAction.ToLower()) {
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
}
