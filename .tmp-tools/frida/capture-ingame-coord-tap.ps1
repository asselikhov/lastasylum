# Capture Frida IL2CPP hooks + logcat while user taps in-game coordinates.
param(
    [string]$HostAddr = '127.0.0.1:27042',
    [string]$Package = 'com.phs.global',
    [int]$DurationSec = 180,
    [string]$OutDir = 'C:\Projects\LastAsylum\.tmp-tools\frida'
)

$ErrorActionPreference = 'Continue'
$ts = Get-Date -Format 'yyyyMMdd-HHmmss'
$fridaLog = Join-Path $OutDir "ingame-coord-frida-$ts.log"
$logcatLog = Join-Path $OutDir "ingame-coord-logcat-$ts.log"
$hook = Join-Path $OutDir 'hook_game_cmds.js'

adb forward tcp:27042 tcp:27042 | Out-Null
adb shell "rm -f /sdcard/Download/la_hook.log" 2>$null | Out-Null
adb logcat -c 2>$null | Out-Null

$gamePid = (adb shell pidof $Package 2>$null).Trim()
"Started $(Get-Date -Format o) package=$Package pid=$gamePid duration=${DurationSec}s" | Out-File $fridaLog -Encoding utf8

# -q -t inf: stay attached in quiet mode; --eternalize: hooks survive if client disconnects.
$fridaErr = Join-Path $OutDir "ingame-coord-frida-err-$ts.log"
$fridaProc = Start-Process -FilePath 'frida' -ArgumentList @(
    '-H', $HostAddr, '-n', 'Gadget', '-l', $hook, '--runtime=v8',
    '-q', '-t', 'inf', '-o', $fridaLog, '--eternalize'
) -RedirectStandardError $fridaErr -PassThru -NoNewWindow

$deadline = (Get-Date).AddSeconds(30)
while ((Get-Date) -lt $deadline) {
    if ((Test-Path $fridaLog) -and (Select-String -Path $fridaLog -Pattern 'hooks ready' -Quiet)) { break }
    Start-Sleep -Seconds 1
}
if (-not (Select-String -Path $fridaLog -Pattern 'hooks ready' -Quiet)) {
    Write-Warning "Frida hooks not ready yet - check $fridaLog"
}

$logcatArgs = if ($gamePid) { @('-d', '--pid=' + $gamePid) } else { @() }
Start-Job -Name 'LogcatCapture' -ScriptBlock {
    param($logPath, $pkg, $sec)
    $end = (Get-Date).AddSeconds($sec)
    adb logcat -v time 2>&1 | ForEach-Object {
        $line = $_
        if ($line -match $pkg -or $line -match 'globalphs|DeepLink|deeplink|phslink|flyWorld|FormatKXY|SimpleInstr|InvokeDeepLink|Unity') {
            Add-Content -Path $logPath -Value $line
        }
        if ((Get-Date) -gt $end) { break }
    }
} -ArgumentList $logcatLog, $Package, ($DurationSec + 5) | Out-Null

Write-Host "Frida PID $($fridaProc.Id) -> $fridaLog"
Write-Host "Logcat -> $logcatLog"
Write-Host "Tap in-game coordinates now ($DurationSec sec)..."

Start-Sleep -Seconds $DurationSec

Stop-Process -Id $fridaProc.Id -Force -ErrorAction SilentlyContinue
Stop-Job -Name 'LogcatCapture' -ErrorAction SilentlyContinue
Remove-Job -Name 'LogcatCapture' -Force -ErrorAction SilentlyContinue

adb pull /sdcard/Download/la_hook.log (Join-Path $OutDir "ingame-coord-device-$ts.log") 2>$null | Out-Null

Write-Host "Done. Pull device log if present."
