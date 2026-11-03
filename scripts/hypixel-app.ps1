# SkyPilot - Hypixel Developers Persistent API Key Application Assistant
# https://developer.hypixel.net/dashboard

[CmdletBinding()]
param(
    [string]$ApiKey = ""
)

$ErrorActionPreference = "Continue"

Write-Host ""
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "  SkyPilot - Hypixel Developers 永続APIキー申請アシスタント  " -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host ""

$ProjectName = "SkyPilot"
$Category = "In-game Mod / Companion Tool / SkyBlock Utility"
$Description = @"
SkyPilot is an open-source Fabric client mod and self-hosted server companion for Hypixel SkyBlock. It displays in-game information (market values, wardrobe previews, puzzle directions, and progression roadmaps) without automating player actions.

Hypixel API policy compliance:
- Strictly read-only: zero player gameplay actions or botting.
- Aggressive caching: 120s+ for player profiles, 30s+ for bazaar/auctions, 1hr+ for item definitions.
- RateLimit-* headers are honored with automatic exponential backoff and circuit breakers.
- One persistent key used exclusively for the user's self-hosted server without rotation.
- Fully open-source on GitHub.
"@

# Copy description to clipboard
try {
    Set-Clipboard -Value $Description
    Write-Host "[OK] 申請用のプロジェクト説明文をクリップボードにコピーしました！" -ForegroundColor Green
} catch {
    Write-Host "[!] クリップボードへの自動コピーをスキップしました。" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "【申請手順】" -ForegroundColor Yellow
Write-Host "  1. ブラウザで Hypixel Developer Dashboard を開きます (自動で開きます)"
Write-Host "  2. Minecraft / Hypixel フォーラムのアカウントでログインします"
Write-Host "  3. 'Create Application' をクリックします"
Write-Host "  4. プロジェクト名: " -NoNewline
Write-Host $ProjectName -ForegroundColor Cyan
Write-Host "  5. プロジェクト説明: " -NoNewline
Write-Host "クリップボードの内容 (Ctrl+V) を貼り付けます" -ForegroundColor Cyan
Write-Host "  6. 作成完了後、発行された API Key (UUID形式) をコピーします"
Write-Host ""

# Open browser
$DashboardUrl = "https://developer.hypixel.net/dashboard"
Write-Host "ブラウザで Hypixel Developer Dashboard を開いています: $DashboardUrl" -ForegroundColor Gray
Start-Process $DashboardUrl

if (-not $ApiKey) {
    Write-Host ""
    $ApiKey = Read-Host "発行された Hypixel 永続APIキーを入力してください (Ctrl+V で貼り付け)"
}

$ApiKey = $ApiKey.Trim()

if (-not $ApiKey) {
    Write-Host ""
    Write-Host "キーが入力されませんでした。後からランチャーの設定画面や .env からも設定できます。" -ForegroundColor Yellow
    exit 0
}

# Validate UUID format
$CleanUuid = $ApiKey -replace "-", ""
if ($CleanUuid.Length -ne 32 -or $CleanUuid -notmatch "^[0-9a-fA-F]{32}$") {
    Write-Host "[!] 入力されたキーは有効なUUID形式ではありません (32桁の16進数): $ApiKey" -ForegroundColor Red
    Write-Host "再度確認して入力してください。" -ForegroundColor Yellow
    exit 1
}

Write-Host ""
Write-Host "Hypixel API に接続してキーを検証中…" -ForegroundColor Cyan

# Test API key with Hypixel API
$Valid = $false
try {
    $TestUrl = "https://api.hypixel.net/v2/skyblock/news"
    $Headers = @{ "API-Key" = $ApiKey }
    $Response = Invoke-RestMethod -Uri $TestUrl -Headers $Headers -Method Get -TimeoutSec 10 -ErrorAction Stop
    if ($Response.success -eq $true) {
        $Valid = $true
        Write-Host "[OK] Hypixel API によるキー検証に成功しました！" -ForegroundColor Green
    }
} catch {
    $StatusCode = $_.Exception.Response.StatusCode.value__
    if ($StatusCode -eq 403) {
        Write-Host "[X] Hypixel API がキーを拒否しました (HTTP 403 Forbidden)。" -ForegroundColor Red
        Write-Host "キーが正しくコピーされているか、ダッシュボードで承認されているかご確認ください。" -ForegroundColor Yellow
    } else {
        Write-Host "[!] ネットワーク確認: $($_.Exception.Message)" -ForegroundColor Yellow
        Write-Host "直接保存を続行します。" -ForegroundColor Gray
        $Valid = $true
    }
}

if ($Valid) {
    # 1. Update .env
    $EnvPath = Join-Path $PSScriptRoot "..\.env"
    if (Test-Path $EnvPath) {
        $Lines = Get-Content $EnvPath
        $Found = $false
        $NewLines = @()
        foreach ($line in $Lines) {
            if ($line.Trim().StartsWith("HYPIXEL_API_KEY=")) {
                $NewLines += "HYPIXEL_API_KEY=$ApiKey"
                $Found = $true
            } else {
                $NewLines += $line
            }
        }
        if (-not $Found) {
            $NewLines += "HYPIXEL_API_KEY=$ApiKey"
        }
        $NewLines | Set-Content $EnvPath -Encoding UTF8
        Write-Host "[OK] .env に HYPIXEL_API_KEY を保存しました。" -ForegroundColor Green
    }

    # 2. Update running server via /api/settings/apis if reachable
    $ServerUrl = "http://127.0.0.1:3000/api/settings/apis"
    try {
        $Body = @{ hypixelKey = $ApiKey } | ConvertTo-Json
        $PutResp = Invoke-RestMethod -Uri $ServerUrl -Method Put -Body $Body -ContentType "application/json" -TimeoutSec 3 -ErrorAction SilentlyContinue
        if ($PutResp) {
            Write-Host "[OK] 起動中の SkyPilot サーバーへキーを即座に反映しました！(再起動不要)" -ForegroundColor Green
        }
    } catch {
        # Server might not be running right now, which is fine since .env is updated
    }

    Write-Host ""
    Write-Host "============================================================" -ForegroundColor Green
    Write-Host "  Hypixel 永続APIキーの設定が正常に完了しました！             " -ForegroundColor Green
    Write-Host "============================================================" -ForegroundColor Green
    Write-Host ""
}

