param([int]$Port = 18080, [ValidateSet('http', 'socks5')][string]$Mode = 'http')
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
$taskWork = Join-Path $taskRoot $(if ($Mode -eq 'socks5') { 'work/proxy-socks' } else { 'work/proxy' })
$taskExe = Join-Path $taskRoot 'work/proxy-venv/Scripts/mitmdump.exe'
$taskState = Join-Path $taskWork 'process.json'
New-Item -ItemType Directory -Force -Path $taskWork | Out-Null
if (Test-Path -LiteralPath $taskState) {
    $taskExisting = Get-Content -LiteralPath $taskState -Raw | ConvertFrom-Json
    if (Get-Process -Id $taskExisting.processId -ErrorAction SilentlyContinue) { throw 'Proxy session already running; stop it before starting another.' }
}
if (Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue) { throw "Port $Port is already in use" }
$taskCaDir = (Join-Path $taskRoot 'work/proxy/ca').Replace('\', '/')
$taskAddon = (Join-Path $taskRoot $(if ($Mode -eq 'socks5') { 'proxy/qd_capture_socks.py' } else { 'proxy/qd_capture.py' })).Replace('\', '/')
$taskArgs = @('-q', '--listen-host', '127.0.0.1', '--listen-port', "$Port", '--set', "confdir=$taskCaDir",
    '--set', 'flow_detail=0', '--set', 'connection_strategy=lazy', '--allow-hosts', '(^|.*\.)(qidian\.com|qdmm\.com|yuewen\.com)(:[0-9]+)?$', '-s', $taskAddon)
if ($Mode -eq 'socks5') { $taskArgs += @('--mode', 'socks5') }
# Start-Process uses a Windows command line; quote individual arguments with spaces.
$taskQuoted = $taskArgs | ForEach-Object { '"' + $_.Replace('"', '\"') + '"' }
$taskProcess = Start-Process -FilePath $taskExe -ArgumentList $taskQuoted -WorkingDirectory $taskRoot -WindowStyle Hidden -PassThru `
    -RedirectStandardOutput (Join-Path $taskWork 'stdout.log') -RedirectStandardError (Join-Path $taskWork 'stderr.log')
@{processId=$taskProcess.Id; executable=$taskExe; port=$Port; startedAt=(Get-Date).ToString('o')} |
    ConvertTo-Json | Set-Content -LiteralPath $taskState -Encoding utf8
Write-Output "Started localhost-only proxy, PID $($taskProcess.Id), port $Port. Phone settings have not changed."
