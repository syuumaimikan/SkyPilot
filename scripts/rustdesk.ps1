[CmdletBinding()]
param([ValidateSet('Start', 'Status', 'Stop')][string]$Action = 'Status')
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$runtimeRoot = Join-Path $projectRoot '.runtime/rustdesk'
$statePath = Join-Path $runtimeRoot 'state.json'
$configPath = Join-Path $env:APPDATA 'RustDesk/config/RustDesk2.toml'
$exePath = 'C:\Program Files\RustDesk\rustdesk.exe'
$tailscaleExe = 'C:\Program Files\Tailscale\tailscale.exe'
$ruleName = 'SkyPilot-RustDesk-Tailscale'

function Set-Options([string]$source, [hashtable]$changes) {
    $lines = [Collections.Generic.List[string]]::new()
    foreach ($line in ($source -split '\r?\n')) { $lines.Add($line) }
    $start = -1
    for ($i = 0; $i -lt $lines.Count; $i++) { if ($lines[$i] -eq '[options]') { $start = $i; break } }
    if ($start -lt 0) { $lines.Add('[options]'); $start = $lines.Count - 1 }
    $end = $lines.Count
    for ($i = $start + 1; $i -lt $lines.Count; $i++) { if ($lines[$i] -match '^\[') { $end = $i; break } }
    foreach ($option in $changes.Keys) {
        $pattern = '^' + [regex]::Escape($option) + '\s*='
        for ($i = $end - 1; $i -gt $start; $i--) { if ($lines[$i] -match $pattern) { $lines.RemoveAt($i); $end-- } }
        if ($null -ne $changes[$option]) {
            $value = [string]$changes[$option]
            if ($value -notmatch '^[A-Za-z0-9.,:/ -]*$') { throw 'Invalid RustDesk option value.' }
            $lines.Insert($end, "$option = '$value'"); $end++
        }
    }
    return $lines -join "`r`n"
}

function Read-Option([string]$source, [string]$name) {
    $pattern = '(?m)^' + [regex]::Escape($name) + '\s*=\s*[''"]([^''"]*)[''"]\s*$'
    $match = [regex]::Match($source, $pattern)
    if ($match.Success) { return $match.Groups[1].Value }
    return $null
}

function Managed-Process($state) {
    if (-not $state -or -not $state.ProcessId) { return $null }
    $process = Get-Process -Id $state.ProcessId -ErrorAction SilentlyContinue
    if ($process -and $process.Path -eq $exePath -and $process.StartTime.ToUniversalTime().Ticks -eq $state.StartTicks) { return $process }
    return $null
}

$savedState = if (Test-Path -LiteralPath $statePath) { Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json } else { $null }
if ($Action -eq 'Status') {
    $options = if (Test-Path -LiteralPath $configPath) { Get-Content -LiteralPath $configPath -Raw } else { '' }
    $listener = @(Get-NetTCPConnection -LocalPort 21118 -State Listen -ErrorAction SilentlyContinue)
    [pscustomobject]@{ Installed = (Test-Path -LiteralPath $exePath); Managed = ($null -ne (Managed-Process $savedState)); Listening = ($listener.Count -gt 0); DirectAccess = (Read-Option $options 'direct-server'); AllowedIPs = (Read-Option $options 'whitelist'); Target = $savedState.Target }
    exit
}
if (-not [Security.Principal.WindowsPrincipal]::new([Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) { throw 'Run this script in an administrator PowerShell.' }
if ($Action -eq 'Stop') {
    if (-not $savedState) { throw 'No managed RustDesk setup to stop.' }
    if (-not $savedState.ProcessId) { Write-Output 'SkyPilot RustDesk is already stopped.'; exit }
    $managed = Managed-Process $savedState
    if ($managed) { Stop-Process -Id $managed.Id }
    # Restore only options this script owned; preserve other RustDesk changes and credentials.
    $restore = @{}
    foreach ($property in $savedState.Previous.PSObject.Properties) { $restore[$property.Name] = $property.Value }
    $content = Set-Options (Get-Content -LiteralPath $configPath -Raw) $restore
    [IO.File]::WriteAllText($configPath, $content, [Text.UTF8Encoding]::new($false))
    Get-NetFirewallRule -Name $ruleName -ErrorAction SilentlyContinue | Remove-NetFirewallRule
    $savedState.ProcessId = 0
    $savedState | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $statePath -Encoding utf8
    Write-Output 'SkyPilot RustDesk stopped and its previous settings restored.'
    exit
}
if (-not (Test-Path -LiteralPath $exePath) -or -not (Test-Path -LiteralPath $configPath)) { throw 'Install and configure RustDesk first.' }
if (Managed-Process $savedState) { throw 'RustDesk is already running. Use Status, or Stop before refreshing allowed devices.' }
if (Get-Process rustdesk -ErrorAction SilentlyContinue) { throw 'Close RustDesk before applying this setup; active sessions are not interrupted automatically.' }
$tailnetText = & $tailscaleExe status --json
if ($LASTEXITCODE -ne 0) { throw 'Could not read Tailscale status.' }
$tailnet = $tailnetText | ConvertFrom-Json
if ($tailnet.BackendState -ne 'Running' -or -not $tailnet.Self.UserID) { throw 'Connect Tailscale first.' }
$ownPeers = @($tailnet.Peer.PSObject.Properties.Value | Where-Object { $_.UserID -eq $tailnet.Self.UserID })
$allowed = @(@($tailnet.Self.TailscaleIPs) + @($ownPeers | ForEach-Object { $_.TailscaleIPs }) | Where-Object { $_ -match '^100\.(?:[6-9][0-9]|1[01][0-9]|12[0-7])\.\d{1,3}\.\d{1,3}$' } | Sort-Object -Unique)
$localIp = @($tailnet.Self.TailscaleIPs | Where-Object { $_ -match '^100\.' })[0]
if (-not $localIp -or $allowed.Count -lt 2) { throw 'No other devices belonging to this Tailscale account were found.' }
$source = Get-Content -LiteralPath $configPath -Raw
$changes = @{ 'direct-server' = 'Y'; 'direct-access-port' = '21118'; 'whitelist' = ($allowed -join ','); 'stop-service' = 'N' }
$previous = @{}
foreach ($option in $changes.Keys) { $previous[$option] = Read-Option $source $option }
New-Item -ItemType Directory -Path $runtimeRoot -Force | Out-Null
# The full recovery copy stays local in the ignored runtime folder and is never served.
Copy-Item -LiteralPath $configPath -Destination (Join-Path $runtimeRoot "RustDesk2-$([DateTime]::UtcNow.ToString('yyyyMMddHHmmss')).toml")
$process = $null
try {
    [IO.File]::WriteAllText($configPath, (Set-Options $source $changes), [Text.UTF8Encoding]::new($false))
    Get-NetFirewallRule -Name $ruleName -ErrorAction SilentlyContinue | Remove-NetFirewallRule
    New-NetFirewallRule -Name $ruleName -DisplayName 'SkyPilot RustDesk (own Tailscale devices)' -Direction Inbound -Action Allow -Protocol TCP -LocalPort 21118 -LocalAddress $localIp -RemoteAddress $allowed -Program $exePath -Profile Any | Out-Null
    $process = Start-Process -FilePath $exePath -ArgumentList '--server' -WindowStyle Hidden -PassThru
    $deadline = [DateTime]::UtcNow.AddSeconds(20)
    do {
        $listener = @(Get-NetTCPConnection -LocalPort 21118 -State Listen -ErrorAction SilentlyContinue)
        if ($listener.Count) { break }
        if ($process.HasExited) { throw 'RustDesk exited before opening direct access.' }
        Start-Sleep -Milliseconds 400
    } while ([DateTime]::UtcNow -lt $deadline)
    if (-not $listener.Count) { throw 'RustDesk did not open direct access within 20 seconds.' }
    $state = @{ Target = "${localIp}:21118"; AllowedIPs = $allowed; ProcessId = $process.Id; StartTicks = $process.StartTime.ToUniversalTime().Ticks; Previous = $previous }
    $state | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $statePath -Encoding utf8
    [pscustomobject]@{ Target = $state.Target; Listening = $true; AllowedDevices = $allowed.Count - 1 }
} catch {
    if ($process -and -not $process.HasExited) { Stop-Process -Id $process.Id }
    [IO.File]::WriteAllText($configPath, $source, [Text.UTF8Encoding]::new($false))
    Get-NetFirewallRule -Name $ruleName -ErrorAction SilentlyContinue | Remove-NetFirewallRule
    throw
}
