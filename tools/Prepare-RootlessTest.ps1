param(
    [ValidateSet('Lab', 'Qidian')][string]$Target = 'Lab',
    [switch]$InjectDex,
    [switch]$WithoutProbe,
    [string]$SdkPath = "$env:LOCALAPPDATA/Android/Sdk",
    [string]$JavaPath = 'C:/Program Files/Android/Android Studio/jbr/bin/java.exe'
)
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
$taskPatcher = Join-Path $taskRoot 'work/tools/lspatch-v1.2-487-release.jar'
$taskModule = Join-Path $taskRoot 'app/build/outputs/apk/debug/app-debug.apk'
$taskInput = if ($Target -eq 'Lab') {
    Join-Path $taskRoot 'hooklab/build/outputs/apk/debug/hooklab-debug.apk'
} else { Join-Path $taskRoot 'work/device/qidian-7.9.480-original.apk' }
foreach ($taskFile in @($taskPatcher, $taskModule, $taskInput, $JavaPath)) {
    if (!(Test-Path -LiteralPath $taskFile)) { throw "Missing input: $taskFile" }
}
if ((Get-FileHash -LiteralPath $taskPatcher -Algorithm SHA256).Hash.ToLowerInvariant() -ne
    'd238fdc414d121b7fa454d8b4ccf420df3a8c97d563761861ff92bd9c5da2165') { throw 'Patcher SHA-256 mismatch' }
$taskWork = Join-Path $taskRoot "work/packaging/$Target"
$taskOut = Join-Path $taskRoot 'outputs'
New-Item -ItemType Directory -Force -Path $taskWork, $taskOut | Out-Null
$taskPatchLog = Join-Path $taskWork 'patch.log'
$taskPatchArgs = @('-jar', $taskPatcher, '-f', '-l', '2', '--name', "QDBlocker $Target Test", '-o', $taskWork)
if (!$WithoutProbe) { $taskPatchArgs += @('-m', $taskModule) }
if ($InjectDex) { $taskPatchArgs += '--injectdex' }
$taskPatchArgs += $taskInput
& $JavaPath @taskPatchArgs *> $taskPatchLog
if ($LASTEXITCODE -ne 0) { throw "Patching failed; see $taskPatchLog" }
$taskPatched = Join-Path $taskWork (([IO.Path]::GetFileNameWithoutExtension($taskInput)) + '-487-lspatched.apk')
$taskAligned = Join-Path $taskWork 'aligned.apk'
$taskZipalign = Join-Path $SdkPath 'build-tools/37.0.0/zipalign.exe'
$taskSigner = Join-Path $SdkPath 'build-tools/37.0.0/apksigner.bat'
& $taskZipalign -f -P 16 4 $taskPatched $taskAligned
if ($LASTEXITCODE -ne 0) { throw 'APK alignment failed' }
$taskFinalName = if ($Target -eq 'Lab') { 'QDBlocker-HookLab-rootless.apk' }
    elseif ($WithoutProbe) { 'QDReader-7.9.480-baseline-rootless.apk' }
    elseif ($InjectDex) { 'QDReader-7.9.480-probe-injectdex.apk' }
    else { 'QDReader-7.9.480-probe-rootless.apk' }
# Qidian packaging is currently known to fail startup on the connected device.
# Keep experimental packages out of user-facing outputs until runtime compatibility is resolved.
$taskFinal = Join-Path $(if ($Target -eq 'Qidian') { $taskWork } else { $taskOut }) $taskFinalName
& $taskSigner sign --ks "$env:USERPROFILE/.android/debug.keystore" --ks-key-alias androiddebugkey --ks-pass pass:android --key-pass pass:android --alignment-preserved true --out $taskFinal $taskAligned
if ($LASTEXITCODE -ne 0) { throw 'Test APK signing failed' }
& $taskSigner verify --min-sdk-version 28 $taskFinal
if ($LASTEXITCODE -ne 0) { throw 'Test APK signature verification failed' }
& $taskZipalign -c -P 16 4 $taskFinal
if ($LASTEXITCODE -ne 0) { throw 'Final APK alignment verification failed' }
Add-Type -AssemblyName System.IO.Compression.FileSystem
$taskArchive = [IO.Compression.ZipFile]::OpenRead($taskFinal)
try {
    $taskEntry = $taskArchive.GetEntry('assets/lspatch/modules/com.haoyang.qdblocker.apk')
    if ($WithoutProbe) {
        if ($null -ne $taskEntry) { throw 'Baseline unexpectedly contains a module' }
    } else {
    if ($null -eq $taskEntry) { throw 'Embedded module missing' }
    $taskStream = $taskEntry.Open()
    try {
        $taskSha = [Security.Cryptography.SHA256]::Create()
        try { $taskEmbeddedHash = [BitConverter]::ToString($taskSha.ComputeHash($taskStream)).Replace('-', '') }
        finally { $taskSha.Dispose() }
    } finally { $taskStream.Dispose() }
    if ($taskEmbeddedHash -ne (Get-FileHash -LiteralPath $taskModule -Algorithm SHA256).Hash) {
        throw 'Embedded module does not match the current build'
    }
    }
} finally { $taskArchive.Dispose() }
Copy-Item -LiteralPath $taskModule -Destination (Join-Path $taskOut 'QDBlocker-0.1.0-debug.apk') -Force
Get-FileHash -LiteralPath $taskFinal -Algorithm SHA256 | Select-Object Path, Hash
Write-Output 'Prepared locally; no phone app has been installed, replaced, or removed by this script.'
