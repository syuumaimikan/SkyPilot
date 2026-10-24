[CmdletBinding()]
param([switch]$Check)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$envPath = Join-Path $projectRoot '.env'
if (-not (Test-Path -LiteralPath $envPath)) { throw 'Create .env from .env.example first.' }
$envText = [IO.File]::ReadAllText($envPath)
$keyPattern = '(?m)^HYPIXEL_API_KEY\s*=.*$'
$definitions = [regex]::Matches($envText, $keyPattern)
if ($definitions.Count -gt 1) { throw 'Keep only one HYPIXEL_API_KEY entry in .env before continuing.' }

$composeFiles = @('-f', (Join-Path $projectRoot 'docker-compose.yml'))
if ($Check) {
    if ($definitions.Count -eq 0) { throw 'HYPIXEL_API_KEY is not configured.' }
    $key = ($definitions[0].Value -split '=', 2)[1].Trim().Trim('"').Trim("'")
} else {
    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) { throw 'Start Docker Desktop first.' }
    # Inspect only in memory. Never print Docker's environment (it contains secrets).
    $containerId = & docker compose @composeFiles ps -q api
    if ($LASTEXITCODE -ne 0 -or -not $containerId) { throw 'Start the SkyPilot API before updating its key.' }
    $container = (& docker inspect $containerId | ConvertFrom-Json)[0]
    if ($LASTEXITCODE -ne 0) { throw 'Could not inspect the running API.' }
    $apiEnv = @{}
    foreach ($entry in $container.Config.Env) {
        $pair = $entry -split '=', 2
        $apiEnv[$pair[0]] = $pair[1]
    }
    if ($apiEnv.PUBLIC_WEB_URL -match '^https://[A-Za-z0-9.-]+\.ts\.net(?::\d+)?/?$') {
        $statePath = Join-Path $projectRoot '.runtime/tailscale.json'
        if (-not (Test-Path -LiteralPath $statePath)) { throw 'Tailscale recovery settings are missing. Run scripts/tailscale.ps1 first.' }
        $privateSetup = Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json
        if ($privateSetup.Url -ne $apiEnv.PUBLIC_WEB_URL -or -not $privateSetup.Login -or -not $privateSetup.GatewayPort) {
            throw 'Tailscale settings do not match the running API. Run scripts/tailscale.ps1 first.'
        }
        $env:SKYPILOT_TAILSCALE_URL = $privateSetup.Url
        $env:SKYPILOT_TAILSCALE_LOGIN = $privateSetup.Login
        $env:SKYPILOT_TAILSCALE_PORT = [string]$privateSetup.GatewayPort
        $composeFiles += @('-f', (Join-Path $projectRoot 'docker-compose.tailscale.yml'))
    }
    Write-Host 'Obtain a valid key at https://developer.hypixel.net/ and enter it here. It will not be displayed.'
    $secret = Read-Host 'Hypixel API key' -AsSecureString
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secret)
    try { $key = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer).Trim() }
    finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
        $secret.Dispose()
    }
}

try {
    if ($key -notmatch '^[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}$') {
        throw 'Enter a Hypixel API key in UUID format. No settings were changed.'
    }
    Add-Type -AssemblyName System.Net.Http
    $client = New-Object System.Net.Http.HttpClient
    $client.Timeout = [TimeSpan]::FromSeconds(20)
    $status = $null
    try {
        $client.DefaultRequestHeaders.Add('API-Key', $key)
        # A valid empty UUID tests authorization without requesting anyone's profile.
        $reply = $client.GetAsync('https://api.hypixel.net/v2/skyblock/profiles?uuid=00000000000000000000000000000000').GetAwaiter().GetResult()
        try {
            $status = [int]$reply.StatusCode
            if ($status -eq 403) { throw 'Hypixel rejected this key (403). Obtain a valid key; no settings were changed.' }
            if ($status -ne 200) { throw "Hypixel returned HTTP $status. Try later; no settings were changed." }
            $body = $reply.Content.ReadAsStringAsync().GetAwaiter().GetResult() | ConvertFrom-Json
            if ($body.success -ne $true) { throw 'Hypixel did not confirm this key. No settings were changed.' }
        } finally { $reply.Dispose() }
    } catch {
        # Do not include request headers or response bodies in diagnostics.
        if ($status -eq 403) { throw 'Hypixel rejected this key (403). Obtain a valid key; no settings were changed.' }
        if ($status -and $status -ne 200) { throw "Hypixel returned HTTP $status. No settings were changed." }
        throw 'Could not verify this key with Hypixel. No settings were changed.'
    } finally { $client.Dispose() }

    if ($Check) { Write-Output 'Hypixel accepted the configured key. No settings were changed.'; return }
    if ([IO.File]::ReadAllText($envPath) -cne $envText) { throw '.env changed while the key was being entered. Run this script again; no settings were changed.' }
    $newline = if ($envText.Contains("`r`n")) { "`r`n" } else { "`n" }
    if ($definitions.Count -eq 1) {
        $updated = [regex]::Replace($envText, $keyPattern, ('HYPIXEL_API_KEY=' + $key + $(if ($definitions[0].Value.EndsWith("`r")) { "`r" } else { '' })))
    } else {
        $updated = $envText.TrimEnd("`r", "`n") + $newline + 'HYPIXEL_API_KEY=' + $key + $newline
    }
    [IO.File]::WriteAllText($envPath, $updated, (New-Object Text.UTF8Encoding($false)))
    & docker compose @composeFiles up -d --no-deps --force-recreate api
    if ($LASTEXITCODE -ne 0) { throw 'The verified key was saved, but the API could not restart. Run scripts/tailscale.ps1 again for a Tailscale setup.' }
    Write-Output 'Key verified and saved. API restarted with its existing connection settings preserved.'
    Write-Output 'Allow the API to finish starting, then reload SkyPilot and choose your profile.'
} finally {
    $key = $null
    $updated = $null
    $envText = $null
    $apiEnv = $null
    $container = $null
}
