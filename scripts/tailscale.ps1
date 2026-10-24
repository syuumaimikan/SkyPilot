[CmdletBinding()]
param(
    [ValidateSet('Start', 'Status', 'Stop')][string]$Action = 'Start',
    [ValidateRange(1024, 65535)][int]$GatewayPort = 3001,
    [ValidateRange(1, 65535)][int]$HttpsPort = 9443
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$runtimeRoot = Join-Path $projectRoot '.runtime'
$statePath = Join-Path $runtimeRoot 'tailscale.json'
$baseCompose = Join-Path $projectRoot 'docker-compose.yml'
$privateCompose = Join-Path $projectRoot 'docker-compose.tailscale.yml'
$tsCommand = Get-Command tailscale -ErrorAction SilentlyContinue
if (-not $tsCommand) { throw 'Install Tailscale on this PC first: https://tailscale.com/download/windows' }
$tsExe = $tsCommand.Source

function Invoke-TailscaleJson([string[]]$Arguments) {
    $result = & $tsExe @Arguments
    if ($LASTEXITCODE -ne 0) { throw 'Tailscale could not return its status.' }
    return ($result | ConvertFrom-Json)
}

function Invoke-Compose([string[]]$Arguments, [switch]$Private) {
    $files = @('-f', $baseCompose)
    if ($Private) { $files += @('-f', $privateCompose) }
    & docker compose @files @Arguments
    if ($LASTEXITCODE -ne 0) { throw 'Docker Compose failed. Check that Docker Desktop and SkyPilot are running.' }
}

$node = Invoke-TailscaleJson -Arguments @('status', '--json')
if ($Action -eq 'Status') {
    $recorded = if (Test-Path -LiteralPath $statePath) { Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json } else { $null }
    [PSCustomObject]@{
        TailscaleState = $node.BackendState
        Url = if ($recorded) { $recorded.Url } elseif ($node.Self.DNSName) { 'https://' + $node.Self.DNSName.TrimEnd('.') } else { $null }
    }
    & $tsExe serve status
    exit $LASTEXITCODE
}

if ($Action -eq 'Stop') {
    if (-not (Test-Path -LiteralPath $statePath)) { throw 'No SkyPilot Tailscale setup is recorded. No settings were changed.' }
    $saved = Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json
    $HttpsPort = $saved.HttpsPort
    $GatewayPort = $saved.GatewayPort
    $config = Invoke-TailscaleJson -Arguments @('serve', 'status', '--json')
    $site = $config.Web.PSObject.Properties | Where-Object Name -eq $saved.Site
    if ($site -and $site.Value.Handlers.'/'.Proxy -ne "http://127.0.0.1:$GatewayPort") {
        throw 'The Serve route has changed since setup. Stop it manually to avoid modifying another service.'
    }
    if ($site) {
        & $tsExe serve --https=$HttpsPort --set-path=/ off
        if ($LASTEXITCODE -ne 0) { throw 'Could not stop the SkyPilot Serve route.' }
    }
    $env:SKYPILOT_TAILSCALE_URL = $saved.Url
    $env:SKYPILOT_TAILSCALE_LOGIN = $saved.Login
    $env:SKYPILOT_TAILSCALE_PORT = [string]$GatewayPort
    Invoke-Compose -Private -Arguments @('stop', 'tailscale-gateway')
    Invoke-Compose -Arguments @('up', '-d', '--no-deps', 'proxy', 'api')
    Write-Output 'SkyPilot Tailscale sharing stopped. The standard local Compose configuration is restored.'
    exit 0
}

if ($node.BackendState -ne 'Running' -or -not $node.Self.DNSName) {
    if ($node.AuthURL) { Write-Output ("Sign in to Tailscale: " + $node.AuthURL) }
    throw 'Open the PC Tailscale app and connect, then run this script again. Connect Android to the same account.'
}
$owner = $node.User.PSObject.Properties | Where-Object Name -eq ([string]$node.Self.UserID)
$ownerLogin = [string]$owner.Value.LoginName
if (-not $ownerLogin -or $ownerLogin -match '[\s\r\n]') { throw 'Could not identify the PC owner. Tagged servers need an explicit access policy.' }
$domain = $node.Self.DNSName.TrimEnd('.')
if ($domain -notmatch '^[A-Za-z0-9.-]+\.ts\.net$') { throw 'Tailscale did not return a valid tailnet DNS name.' }
$url = 'https://' + $domain + $(if ($HttpsPort -ne 443) { ":$HttpsPort" } else { '' })
$siteName = "${domain}:$HttpsPort"
$config = Invoke-TailscaleJson -Arguments @('serve', 'status', '--json')
$existing = $config.Web.PSObject.Properties | Where-Object Name -eq $siteName
if ($existing -and (@($existing.Value.Handlers.PSObject.Properties).Count -ne 1 -or $existing.Value.Handlers.'/'.Proxy -ne "http://127.0.0.1:$GatewayPort")) {
    throw 'This HTTPS port already serves another app. Choose a different -HttpsPort to keep that app intact.'
}
if ($config.TCP.PSObject.Properties | Where-Object { $_.Name -eq [string]$HttpsPort -and -not $_.Value.HTTPS }) {
    throw 'This Tailscale port has another forwarding configuration. Choose a different -HttpsPort.'
}
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) { throw 'Docker Desktop is required for this SkyPilot setup.' }
try {
    $health = Invoke-WebRequest -Uri 'http://127.0.0.1:3000/api/health' -UseBasicParsing -TimeoutSec 10
    if ($health.StatusCode -ne 200) { throw 'API not ready' }
} catch { throw 'Start SkyPilot first: docker compose up -d. The Web and API must be available on localhost:3000.' }

$env:SKYPILOT_TAILSCALE_URL = $url
$env:SKYPILOT_TAILSCALE_LOGIN = $ownerLogin
$env:SKYPILOT_TAILSCALE_PORT = [string]$GatewayPort
New-Item -ItemType Directory -Path $runtimeRoot -Force | Out-Null
# Record recovery information before any container or Serve settings change.
@{ Url = $url; Site = $siteName; Login = $ownerLogin; GatewayPort = $GatewayPort; HttpsPort = $HttpsPort } |
    ConvertTo-Json | Set-Content -LiteralPath $statePath -Encoding utf8
# Compose derives API cookie/CORS/link settings from the private HTTPS origin.
Invoke-Compose -Private -Arguments @('up', '-d', '--no-deps', 'proxy', 'api', 'tailscale-gateway')
& $tsExe serve --bg --https=$HttpsPort --set-path=/ --yes "http://127.0.0.1:$GatewayPort"
if ($LASTEXITCODE -ne 0) {
    throw 'Serve could not start. If Tailscale printed an HTTPS enablement URL, complete that step and rerun the script.'
}
$verified = Invoke-TailscaleJson -Arguments @('serve', 'status', '--json')
$route = $verified.Web.PSObject.Properties | Where-Object Name -eq $siteName
$funnel = $verified.AllowFunnel.PSObject.Properties | Where-Object { $_.Name -eq $siteName -and $_.Value }
if (-not $route -or $route.Value.Handlers.'/'.Proxy -ne "http://127.0.0.1:$GatewayPort" -or $funnel) {
    throw 'The private Serve configuration could not be verified. Do not use Funnel for this app.'
}
$url | Set-Content -LiteralPath (Join-Path $runtimeRoot 'tailscale-url.txt') -Encoding utf8
Write-Output "SkyPilot private URL: $url"
Write-Output 'Choose Tailscale in the Android app and enter this URL. Both devices must be connected to the same account.'
