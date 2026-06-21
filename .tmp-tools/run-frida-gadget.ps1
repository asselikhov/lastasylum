# Frida Gadget capture (no root): restart game, attach hooks, probe deep links.
$ErrorActionPreference = 'Continue'
$Root = Split-Path $PSScriptRoot -Parent
$Py = "$env:LocalAppData\Programs\Python\Python312\python.exe"
$Script = Join-Path $Root '.tmp-tools\frida_gadget_capture.py'
$Log = Join-Path $Root '.tmp-tools\frida\capture-gadget.log'

if (-not (Test-Path $Py)) { throw "Python 3.12 not found at $Py" }

& $Py $Script 2>&1 | Tee-Object -FilePath (Join-Path $Root '.tmp-tools\frida\capture-gadget-console.log')

Write-Host "`n=== Key lines ===" -ForegroundColor Cyan
if (Test-Path $Log) {
    Select-String -Path $Log -Pattern 'hooks ready|deepLink|DEEPLINK|CMD_NEW|LookPlayer|QueryUnion|WorldMapView|EnterWorldMap|Search|probe' |
        Select-Object -Last 40
    Write-Host "`nFull log: $Log" -ForegroundColor Green
}
