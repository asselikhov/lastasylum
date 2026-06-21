# Live deep-link probe: Frida hooks + adb intents (Last Asylum v1.0.77 + Gadget).
param(
    [string]$HostAddr = '127.0.0.1:27042',
    [string]$Package = 'com.phs.global',
    [int]$WaitReadySec = 120,
    [int]$AfterIntentSec = 4
)

$ErrorActionPreference = 'Continue'
$root = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$hook = Join-Path $PSScriptRoot 'hook_game_cmds.js'
$log = Join-Path $PSScriptRoot "live-probe-$(Get-Date -Format 'yyyyMMdd-HHmmss').log"

if (-not (Test-Path $hook)) { throw "Missing $hook" }

adb shell killall frida-server 2>$null | Out-Null
adb forward tcp:27042 tcp:27042 | Out-Null
adb shell monkey -p $Package -c android.intent.category.LAUNCHER 1 2>&1 | Out-Null
Start-Sleep -Seconds 4

$frida = Start-Process -FilePath 'frida' -ArgumentList @(
    '-H', $HostAddr, '-n', 'Gadget', '-l', $hook, '--runtime=v8'
) -RedirectStandardOutput $log -RedirectStandardError $log -PassThru -NoNewWindow

$deadline = (Get-Date).AddSeconds($WaitReadySec)
while ((Get-Date) -lt $deadline) {
    if ((Test-Path $log) -and (Select-String -Path $log -Pattern 'hooks ready' -Quiet)) { break }
    Start-Sleep -Seconds 1
}
if (-not (Select-String -Path $log -Pattern 'hooks ready' -Quiet)) {
    Stop-Process -Id $frida.Id -Force -ErrorAction SilentlyContinue
    throw 'Frida hooks not ready'
}

Start-Sleep -Seconds 10  # game load

$probes = @(
    @{ Label = 'map_path_server'; Url = 'globalphslink://map/512/384/19'; Clip = 'X:512 Y:384' },
    @{ Label = 'map_xy_param'; Url = 'globalphslink://map?xy=512,384'; Clip = 'X:512 Y:384' },
    @{ Label = 'coordinate_param'; Url = 'globalphslink://coordinate?512,384'; Clip = 'X:512 Y:384' },
    @{ Label = 'coordinate_path'; Url = 'globalphslink://coordinate/512/384'; Clip = 'X:512 Y:384' },
    @{ Label = 'world_path'; Url = 'globalphslink://world/512/384'; Clip = 'X:512 Y:384' },
    @{ Label = 'map_only'; Url = 'globalphslink://map'; Clip = 'X:512 Y:384' },
    @{ Label = 'world_only'; Url = 'globalphslink://world'; Clip = 'X:512 Y:384' },
    @{ Label = 'search_panel'; Url = 'globalphslink://search'; Clip = 'ProbeNick' },
    @{ Label = 'search_type'; Url = 'globalphslink://search?type=player'; Clip = 'ProbeNick' },
    @{ Label = 'search_player_path'; Url = 'globalphslink://search/player/ProbeNick/19'; Clip = 'ProbeNick' },
    @{ Label = 'search_player_name'; Url = 'globalphslink://search/player/ProbeNick'; Clip = 'ProbeNick' },
    @{ Label = 'player_search_path'; Url = 'globalphslink://player/search/ProbeNick/19'; Clip = 'ProbeNick' },
    @{ Label = 'profile_player'; Url = 'globalphslink://profile/player/ProbeNick/19'; Clip = 'ProbeNick' },
    @{ Label = 'player_profile'; Url = 'globalphslink://player/profile/ProbeNick/19'; Clip = 'ProbeNick' },
    @{ Label = 'role_name'; Url = 'globalphslink://role/ProbeNick/19'; Clip = 'ProbeNick' },
    @{ Label = 'map_player'; Url = 'globalphslink://map/player/ProbeNick/19'; Clip = 'ProbeNick' },
    @{ Label = 'goto_player'; Url = 'globalphslink://goto/player/ProbeNick/19'; Clip = 'ProbeNick' },
    @{ Label = 'union_search'; Url = 'globalphslink://search/alliance/ProbeGuild/19'; Clip = 'ProbeGuild' },
    @{ Label = 'union_profile'; Url = 'globalphslink://profile/alliance/ProbeGuild/19'; Clip = 'ProbeGuild' }
)

Add-Content -Path $log -Value "`n=== PROBE RUN $(Get-Date -Format o) ===`n"

foreach ($p in $probes) {
    Add-Content -Path $log -Value "`n--- PROBE $($p.Label) ---"
    adb shell cmd clipboard set "$($p.Clip)" 2>&1 | Out-Null
    adb shell am start -a android.intent.action.VIEW -d "$($p.Url)" $Package 2>&1 | Out-Null
    Add-Content -Path $log -Value "intent=$($p.Url) clip=$($p.Clip)"
    Start-Sleep -Seconds $AfterIntentSec
}

Add-Content -Path $log -Value "`n=== PROBE DONE ==="
Start-Sleep -Seconds 2
Stop-Process -Id $frida.Id -Force -ErrorAction SilentlyContinue
Write-Host "Log: $log"
Get-Content $log | Select-String -Pattern '\[deepLink\]|\[DEEPLINK|\[CMD_NEW\]|\[SimpleInstrSend|\[RequireLua\]|\[FormatKXY\]|PROBE' | ForEach-Object { $_.Line }
