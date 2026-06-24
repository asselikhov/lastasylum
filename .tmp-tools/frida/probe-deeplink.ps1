# Fire one isolated deep-link probe into the patched (debuggable) game and tail the internal bridge log.
# Usage: powershell -File probe-deeplink.ps1 -Url "globalphslink://map" [-Clip "X:485 Y:495"]
param(
    [Parameter(Mandatory = $true)][string]$Url,
    [string]$Clip = 'X:485 Y:495',
    [int]$WaitSec = 9,
    [switch]$ClearLog
)

$pkg = 'com.phs.global'
$dir = "/data/data/$pkg/files"
$json = '{"clip":"' + $Clip + '","url":"' + $Url + '"}'
$b64 = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes($json))

if ($ClearLog) {
    adb shell "run-as $pkg sh -c 'echo -n > $dir/la_map_fly_bridge.log'"
}
adb shell "run-as $pkg sh -c 'echo $b64 | base64 -d > $dir/squadrelay_probe.json'"
Write-Host "Fired probe url=$Url clip=$Clip" -ForegroundColor Cyan
Start-Sleep -Seconds $WaitSec
Write-Host '--- internal bridge log (probe lines) ---' -ForegroundColor Green
adb shell "run-as $pkg sh -c 'tail -n 200 $dir/la_map_fly_bridge.log'" |
    Select-String -Pattern 'probe|ARMED|Action NEW|DoString|runLua|lua|fly via|fly path|SET by game' |
    Select-Object -Last 50
