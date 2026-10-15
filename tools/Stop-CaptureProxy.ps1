param([switch]$WifiRestored)
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
$taskWork = Join-Path $taskRoot 'work/proxy'
$taskManualWifi = Join-Path $taskWork 'manual-wifi.json'
if ((Test-Path -LiteralPath $taskManualWifi) -and !$WifiRestored) {
    throw 'Restore the Wi-Fi proxy on the phone first, then rerun with -WifiRestored. Stopping now would leave the phone pointing to an unavailable proxy.'
}
$taskPhoneState = Join-Path $taskWork 'phone-proxy.json'
if (Test-Path -LiteralPath $taskPhoneState) {
    $taskSaved = Get-Content -LiteralPath $taskPhoneState -Raw | ConvertFrom-Json
    foreach ($taskProperty in $taskSaved.previous.PSObject.Properties) {
        $taskCurrent = (& adb -s $taskSaved.serial shell settings get global $taskProperty.Name).Trim()
        if ($LASTEXITCODE -ne 0) { throw 'Unable to verify phone settings; retaining snapshot' }
        if ($taskCurrent -eq $taskProperty.Value) { continue }
        if ($taskProperty.Value -eq 'null' -or $taskProperty.Value -eq '') {
            & adb -s $taskSaved.serial shell settings delete global $taskProperty.Name | Out-Null
        } else { & adb -s $taskSaved.serial shell settings put global $taskProperty.Name $taskProperty.Value }
        if ($LASTEXITCODE -ne 0) { throw 'Phone restore failed; keeping the snapshot for retry' }
    }
    & adb -s $taskSaved.serial reverse --remove "tcp:$($taskSaved.port)"
    if ($LASTEXITCODE -ne 0) { throw 'USB mapping removal failed; keeping snapshot' }
    Remove-Item -LiteralPath $taskPhoneState
}
if (Test-Path -LiteralPath $taskManualWifi) { Remove-Item -LiteralPath $taskManualWifi }
$taskVpnState = Join-Path $taskWork 'vpn-route.json'
if (Test-Path -LiteralPath $taskVpnState) {
    $taskVpn = Get-Content -LiteralPath $taskVpnState -Raw | ConvertFrom-Json
    & adb -s $taskVpn.serial shell am force-stop $taskVpn.app
    if ($LASTEXITCODE -ne 0) { throw 'Unable to stop the app-scoped VPN; keeping session state' }
    & adb -s $taskVpn.serial reverse --remove "tcp:$($taskVpn.socksPort)"
    if ($LASTEXITCODE -ne 0) { throw 'Unable to remove VPN USB mapping' }
    $taskSocksState = Join-Path $taskRoot 'work/proxy-socks/process.json'
    if (Test-Path -LiteralPath $taskSocksState) {
        $taskSocks = Get-Content -LiteralPath $taskSocksState -Raw | ConvertFrom-Json
        $taskSocksProcess = Get-Process -Id $taskSocks.processId -ErrorAction SilentlyContinue
        if ($taskSocksProcess -and $taskSocksProcess.Path -eq $taskSocks.executable) { $taskSocksProcess.Kill($true) }
        Remove-Item -LiteralPath $taskSocksState
    }
    Remove-Item -LiteralPath $taskVpnState
}
$taskProcessState = Join-Path $taskWork 'process.json'
if (Test-Path -LiteralPath $taskProcessState) {
    $taskSavedProcess = Get-Content -LiteralPath $taskProcessState -Raw | ConvertFrom-Json
    $taskProcess = Get-Process -Id $taskSavedProcess.processId -ErrorAction SilentlyContinue
    if ($taskProcess -and $taskProcess.Path -eq $taskSavedProcess.executable) { $taskProcess.Kill($true) }
    Remove-Item -LiteralPath $taskProcessState
}
Write-Output 'Restored saved global proxy settings and stopped the recorded local proxy. Manual Wi-Fi proxy restoration must be done on the phone. Remove the test CA in Android Settings after testing.'
