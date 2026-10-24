[CmdletBinding()]
param([string]$Apk = '', [string]$SdkRoot = $env:ANDROID_HOME)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
if (-not $Apk) { $Apk = Join-Path $projectRoot 'apps/android/app/build/outputs/apk/debug/app-debug.apk' }
$Apk = (Resolve-Path -LiteralPath $Apk).Path
if (-not $SdkRoot) { $SdkRoot = Join-Path $env:LOCALAPPDATA 'Android/Sdk' }
$toolRoot = Join-Path $SdkRoot 'build-tools/35.0.0'
$aapt = Join-Path $toolRoot 'aapt.exe'
$signer = Join-Path $toolRoot 'apksigner.bat'
if (-not (Test-Path -LiteralPath $aapt) -or -not (Test-Path -LiteralPath $signer)) { throw 'Android SDK Build Tools 35.0.0 are required.' }
if (-not $env:JAVA_HOME) {
    $java = Get-Command java -ErrorAction SilentlyContinue
    if (-not $java) { throw 'Set JAVA_HOME to JDK 17 before publishing.' }
}
$badging = & $aapt dump badging $Apk
if ($LASTEXITCODE -ne 0) { throw 'APK manifest could not be read.' }
$packageLine = @($badging | Where-Object { $_ -match '^package:' })[0]
if ($packageLine -notmatch "name='dev\.skypilot\.android' versionCode='(\d+)' versionName='([0-9A-Za-z._-]+)'") { throw 'This is not a valid SkyPilot Android APK.' }
$code = [int]$Matches[1]
$name = $Matches[2]
$sdkLine = @($badging | Where-Object { $_ -match '^sdkVersion:' })[0]
if ($sdkLine -notmatch "sdkVersion:'(\d+)'") { throw 'Missing APK minimum SDK.' }
$minSdk = [int]$Matches[1]
$verification = & $signer verify --print-certs $Apk
if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed.' }
$certLine = @($verification | Where-Object { $_ -match '^Signer #1 certificate SHA-256 digest:' })[0]
if ($certLine -notmatch 'digest:\s*([0-9a-fA-F]{64})') { throw 'Could not identify APK signing certificate.' }
$cert = $Matches[1].ToLowerInvariant()
$length = (Get-Item -LiteralPath $Apk).Length
if ($length -le 0 -or $length -gt 64MB) { throw 'APK exceeds the update size limit.' }
$digest = (Get-FileHash -LiteralPath $Apk -Algorithm SHA256).Hash.ToLowerInvariant()
$releaseRoot = Join-Path $projectRoot '.runtime/android-releases'
New-Item -ItemType Directory -Path $releaseRoot -Force | Out-Null
$manifestPath = Join-Path $releaseRoot 'latest.json'
if (Test-Path -LiteralPath $manifestPath) {
    $previous = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
    if ($previous.signerSha256 -ne $cert) { throw 'The signing key differs from the published APK. Existing phones cannot update with this certificate.' }
    if ($code -lt $previous.versionCode -or ($code -eq $previous.versionCode -and $digest -ne $previous.sha256)) { throw 'Increase versionCode before publishing a changed APK; downgrades are rejected.' }
}
$filename = "SkyPilot-$name.apk"
$target = Join-Path $releaseRoot $filename
# Version names have been restricted above, so these targets cannot escape the release directory.
Copy-Item -LiteralPath $Apk -Destination "$target.partial" -Force
Move-Item -LiteralPath "$target.partial" -Destination $target -Force
$manifest = @{ versionCode=$code; versionName=$name; packageName='dev.skypilot.android'; minSdk=$minSdk; path="/api/android/$filename"; bytes=$length; sha256=$digest; signerSha256=$cert; publishedAt=[DateTime]::UtcNow.ToString('o') }
$json = $manifest | ConvertTo-Json
[IO.File]::WriteAllText("$manifestPath.partial", $json, (New-Object Text.UTF8Encoding($false)))
Move-Item -LiteralPath "$manifestPath.partial" -Destination $manifestPath -Force
Write-Output "Published SkyPilot Android $name (versionCode $code)."
Write-Output 'After the initial APK installation, phones check /api/android/latest.json automatically. Keep this signing key for all updates.'
