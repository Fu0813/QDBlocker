param([string]$Serial = '')
$ErrorActionPreference = 'Stop'
if (!$Serial) {
    $taskDevices = @(& adb devices | Where-Object { $_ -match '^\S+\s+device$' } | ForEach-Object { ($_ -split '\s+')[0] })
    if ($taskDevices.Count -ne 1) { throw 'Specify -Serial when there is not exactly one authorized device.' }
    $Serial = $taskDevices[0]
}
$taskRoot = Split-Path -Parent $PSScriptRoot
$taskWork = Join-Path $taskRoot 'work/proxy'
$taskProcessState = Get-Content -LiteralPath (Join-Path $taskWork 'process.json') -Raw | ConvertFrom-Json
$taskProcess = Get-Process -Id $taskProcessState.processId -ErrorAction Stop
if ($taskProcess.Path -ne $taskProcessState.executable) { throw 'Proxy PID no longer belongs to the recorded executable' }
$taskPhoneState = Join-Path $taskWork 'phone-proxy.json'
$taskRetry = Test-Path -LiteralPath $taskPhoneState
if ($taskRetry) {
    $taskSaved = Get-Content -LiteralPath $taskPhoneState -Raw | ConvertFrom-Json
    if ($taskSaved.serial -ne $Serial -or $taskSaved.port -ne $taskProcessState.port) {
        throw 'Existing phone proxy snapshot belongs to another device or port'
    }
}
$taskReverse = & adb -s $Serial reverse --list
$taskPort = $taskProcessState.port
if (($taskReverse -match "tcp:$taskPort\s") -and !$taskRetry) { throw "Reverse mapping tcp:$taskPort already exists; refusing to overwrite" }
$taskPrevious = @{}
foreach ($taskKey in @('http_proxy', 'global_http_proxy_host', 'global_http_proxy_port', 'global_http_proxy_exclusion_list')) {
    $taskValue = & adb -s $Serial shell settings get global $taskKey
    if ($LASTEXITCODE -ne 0) { throw 'Unable to read current phone proxy settings' }
    $taskPrevious[$taskKey] = ($taskValue -join "`n").Trim()
}
if (!$taskRetry) {
    @{serial=$Serial; port=$taskPort; previous=$taskPrevious} | ConvertTo-Json -Depth 4 |
        Set-Content -LiteralPath $taskPhoneState -Encoding utf8
}
& adb -s $Serial reverse "tcp:$taskPort" "tcp:$taskPort"
if ($LASTEXITCODE -ne 0) { throw 'USB reverse mapping failed; run Stop-CaptureProxy.ps1 to restore the snapshot' }
& adb -s $Serial shell settings put global http_proxy "127.0.0.1:$taskPort"
if ($LASTEXITCODE -ne 0) { throw 'Proxy setting failed; run Stop-CaptureProxy.ps1 to restore the snapshot' }
$taskReadback = (& adb -s $Serial shell settings get global http_proxy).Trim()
if ($taskReadback -ne "127.0.0.1:$taskPort") { throw 'Phone did not retain requested proxy setting' }
Write-Output 'Phone proxy enabled over USB. Non-Qidian HTTPS hosts are tunneled without decryption.'
