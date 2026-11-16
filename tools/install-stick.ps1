[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$Device,
    [string]$Apk = (Join-Path $PSScriptRoot '../dist/MyFrame.apk')
)
$ErrorActionPreference = 'Stop'
$adb = Join-Path $env:LOCALAPPDATA 'Android/Sdk/platform-tools/adb.exe'
if (!(Test-Path -LiteralPath $adb)) { throw 'Install Android SDK Platform Tools in Android Studio first.' }
if (!(Test-Path -LiteralPath $Apk)) { throw "APK not found: $Apk" }
if ($Device -match '^\d{1,3}(\.\d{1,3}){3}:\d+$') {
    & $adb connect $Device
    if ($LASTEXITCODE -ne 0) { throw 'Could not connect. Check Wi-Fi and debugging on the TV.' }
}
$state = & $adb -s $Device get-state 2>$null
if ($LASTEXITCODE -ne 0 -or $state.Trim() -ne 'device') { throw 'Authorize this computer on the TV and try again.' }
Write-Host "Installing on $Device. Existing photos and settings are kept when the signing key matches."
& $adb -s $Device install -r (Resolve-Path -LiteralPath $Apk).Path
if ($LASTEXITCODE -ne 0) { throw 'Installation failed. Check storage and signing key. No existing app was removed.' }
& $adb -s $Device shell am start -n 'com.myframe.tv/.MainActivity'
if ($LASTEXITCODE -ne 0) { throw 'Installed, but Android could not open MyFrame.' }
Write-Host 'MyFrame is installed. See docs/XIAOMI_STICK_SETUP.md to choose Home or art startup.'
