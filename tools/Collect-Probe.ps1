param([string]$Serial = '')
$ErrorActionPreference = 'Stop'
if (!$Serial) {
    $taskDevices = @(& adb devices | Where-Object { $_ -match '^\S+\s+device$' } | ForEach-Object { ($_ -split '\s+')[0] })
    if ($taskDevices.Count -ne 1) { throw 'Specify -Serial when there is not exactly one authorized device.' }
    $Serial = $taskDevices[0]
}
$taskRoot = Split-Path -Parent $PSScriptRoot
$taskOut = Join-Path $taskRoot 'work/device'
New-Item -ItemType Directory -Force -Path $taskOut | Out-Null
& adb -s $Serial logcat -d -v threadtime -s 'QDBlocker:I' '*:S' |
    Set-Content -LiteralPath (Join-Path $taskOut 'probe-logcat.txt') -Encoding utf8
if ($LASTEXITCODE -ne 0) { throw 'Failed to read probe logs' }
& adb -s $Serial shell run-as com.haoyang.qdblocker cat shared_prefs/qdblocker.xml |
    Set-Content -LiteralPath (Join-Path $taskOut 'probe-store.xml') -Encoding utf8
if ($LASTEXITCODE -ne 0) { throw 'Open QDBlocker and run a test before collecting its private diagnostics' }
Write-Output "Saved QDBlocker diagnostics to $taskOut"
