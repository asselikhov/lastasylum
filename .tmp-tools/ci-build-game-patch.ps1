# CI build of the patched game APK as a single universal APK (no device / no adb).
# Patches only base.apk (proven path from patch-frida-gadget.ps1), then merges the patched
# base with the stock splits into one universal APK via APKEditor and signs it with a fixed
# keystore. Avoids decoding the multi-hundred-MB asset split. Runs under PowerShell Core (pwsh)
# on a Linux GitHub Actions runner.
[CmdletBinding()]
param(
    [string]$Package = 'com.phs.global',
    [Parameter(Mandatory = $true)][string]$GameVersion,
    # Directory holding the stock split set: base.apk + split_*.apk (pulled from a device by the operator).
    [Parameter(Mandatory = $true)][string]$InputDir,
    [Parameter(Mandatory = $true)][string]$OutApk,
    [Parameter(Mandatory = $true)][string]$Keystore,
    [string]$KsAlias = 'androiddebugkey',
    [Parameter(Mandatory = $true)][string]$KsPass,
    [Parameter(Mandatory = $true)][string]$KsKeyPass,
    [string]$FridaVersion = '17.15.1',
    [string]$BridgeVersion = '4',
    [string]$ApktoolVersion = '2.11.1',
    [string]$UberSignerVersion = '1.3.0',
    [string]$ApkEditorVersion = '1.4.3'
)

$ErrorActionPreference = 'Stop'
$TmpTools = $PSScriptRoot
$ToolsDir = Join-Path $TmpTools 'ci-tools'
$WorkRoot = Join-Path $TmpTools 'ci-work'
$BaseWork = Join-Path $WorkRoot 'base-decoded'
$HookScript = Join-Path $TmpTools 'frida/map_fly_bridge.js'
$MapBridgeSmaliRoot = Join-Path $TmpTools 'frida-patch/map-bridge-smali'

$ApktoolJar = Join-Path $ToolsDir 'apktool.jar'
$UberSignerJar = Join-Path $ToolsDir 'uber-apk-signer.jar'
$ApkEditorJar = Join-Path $ToolsDir 'APKEditor.jar'
$GadgetSo = Join-Path $ToolsDir 'libfrida-gadget.so'

function Ensure-Dir([string]$Path) {
    if (-not (Test-Path $Path)) { New-Item -ItemType Directory -Force -Path $Path | Out-Null }
}

function Download-IfMissing([string]$Url, [string]$Dest) {
    if (Test-Path $Dest) { return }
    Write-Host "Downloading $Url"
    Invoke-WebRequest -Uri $Url -OutFile $Dest -UseBasicParsing
}

function Write-Utf8NoBom([string]$Path, [string]$Text) {
    $enc = New-Object System.Text.UTF8Encoding $false
    [System.IO.File]::WriteAllText($Path, $Text, $enc)
}

function Inject-MapBridgeMeta([string]$ManifestPath, [string]$Version, [string]$Bridge) {
    if (-not (Test-Path $ManifestPath)) { throw "Manifest not found: $ManifestPath" }
    $text = Get-Content $ManifestPath -Raw
    if ($text -notmatch 'android:debuggable="true"') {
        $text = $text -replace '(<application\b)', '$1 android:debuggable="true"'
    }
    # Стоковый base.apk нередко уже содержит наши meta-data: сплиты снимаются с устройства,
    # где стоял предыдущий патч. Тогда нужно ОБНОВИТЬ значения всех трёх тегов (особенно
    # map_bridge_version — иначе он «застревает» на старой версии и приложение вечно просит
    # обновление). Inject только тех тегов, которых нет.
    $hadAny = $text -match 'com\.lastasylum\.alliance\.map_bridge'
    if ($text -match 'android:name="com\.lastasylum\.alliance\.map_bridge_version"') {
        $text = $text -replace 'android:name="com\.lastasylum\.alliance\.map_bridge_version"\s+android:value="[^"]*"', "android:name=`"com.lastasylum.alliance.map_bridge_version`" android:value=`"$Bridge`""
    }
    if ($text -match 'android:name="com\.lastasylum\.alliance\.map_bridge_game_version"') {
        $text = $text -replace 'android:name="com\.lastasylum\.alliance\.map_bridge_game_version"\s+android:value="[^"]*"', "android:name=`"com.lastasylum.alliance.map_bridge_game_version`" android:value=`"$Version`""
    }
    if ($hadAny) {
        Write-Utf8NoBom $ManifestPath $text
        return
    }
    $meta = @"
        <meta-data android:name="com.lastasylum.alliance.map_bridge" android:value="1"/>
        <meta-data android:name="com.lastasylum.alliance.map_bridge_version" android:value="$Bridge"/>
        <meta-data android:name="com.lastasylum.alliance.map_bridge_game_version" android:value="$Version"/>
"@
    $patched = $text -replace '(<application\b[^>]*>)', "`$1`r`n$meta"
    if ($patched -eq $text) { throw 'Failed to inject map bridge meta-data' }
    Write-Utf8NoBom $ManifestPath $patched
}

function Inject-GadgetSmali([string]$SmaliPath) {
    if (-not (Test-Path $SmaliPath)) { throw "Smali not found: $SmaliPath" }
    $text = Get-Content $SmaliPath -Raw
    $stockAttach = @'
.method protected attachBaseContext(Landroid/content/Context;)V
    .locals 0

    .line 1
    invoke-super {p0, p1}, Lcom/lapslg/sdk/core/LAASGlobalApplication;->attachBaseContext(Landroid/content/Context;)V

    return-void
.end method
'@
    if ($text -match 'attachBaseContext[\s\S]*?frida-gadget') {
        $text = $text -replace '(?s)\.method protected attachBaseContext\(Landroid/content/Context;\)V.*?\.end method', $stockAttach.TrimEnd()
    }
    if ($text -match 'onCreate\(\)V[\s\S]*?frida-gadget') {
        if ($text -match 'if-nez v0, :cond_skip[\s\S]*?frida-gadget') {
            $text = $text -replace 'if-nez v0, :cond_skip', 'if-eqz v0, :cond_skip'
        }
        Write-Utf8NoBom $SmaliPath $text
        return
    }
    $onCreateMethod = @'

.method public onCreate()V
    .locals 3

    invoke-super {p0}, Lcom/lapslg/sdk/core/LAASGlobalApplication;->onCreate()V

    invoke-static {}, Landroid/app/ActivityThread;->currentProcessName()Ljava/lang/String;

    move-result-object v0

    invoke-virtual {p0}, Landroid/content/Context;->getPackageName()Ljava/lang/String;

    move-result-object v1

    invoke-virtual {v0, v1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v0

    if-eqz v0, :cond_skip

    const-string v0, "frida-gadget"

    invoke-static {v0}, Ljava/lang/System;->loadLibrary(Ljava/lang/String;)V

    new-instance v0, Lcom/lastasylum/alliance/game/MapFlyReceiver;

    invoke-direct {v0}, Lcom/lastasylum/alliance/game/MapFlyReceiver;-><init>()V

    new-instance v1, Landroid/content/IntentFilter;

    const-string v2, "com.lastasylum.alliance.action.MAP_FLY"

    invoke-direct {v1, v2}, Landroid/content/IntentFilter;-><init>(Ljava/lang/String;)V

    const/4 v2, 0x2

    invoke-virtual {p0, v0, v1, v2}, Landroid/app/Application;->registerReceiver(Landroid/content/BroadcastReceiver;Landroid/content/IntentFilter;I)Landroid/content/Intent;

    :cond_skip
    return-void
.end method
'@
    if ($text -notmatch [regex]::Escape('.method protected attachBaseContext(Landroid/content/Context;)V')) {
        throw 'Unexpected SdkMultiDexApplication.smali layout - patch manually.'
    }
    $patched = $text -replace '(?s)(\.method protected attachBaseContext\(Landroid/content/Context;\)V.*?\.end method)', "`$1$onCreateMethod"
    if ($patched -eq $text) { throw 'Smali patch failed (onCreate insert mismatch).' }
    Write-Utf8NoBom $SmaliPath $patched
}

# --- Setup ---
Ensure-Dir $ToolsDir
if (Test-Path $WorkRoot) { Remove-Item $WorkRoot -Recurse -Force }
Ensure-Dir $WorkRoot

Download-IfMissing "https://github.com/iBotPeaches/Apktool/releases/download/v$ApktoolVersion/apktool_$ApktoolVersion.jar" $ApktoolJar
Download-IfMissing "https://github.com/patrickfav/uber-apk-signer/releases/download/v$UberSignerVersion/uber-apk-signer-$UberSignerVersion.jar" $UberSignerJar
Download-IfMissing "https://github.com/REAndroid/APKEditor/releases/download/V$ApkEditorVersion/APKEditor-$ApkEditorVersion.jar" $ApkEditorJar

if (-not (Test-Path $GadgetSo)) {
    $xz = Join-Path $ToolsDir 'frida-gadget.so.xz'
    Download-IfMissing "https://github.com/frida/frida/releases/download/$FridaVersion/frida-gadget-$FridaVersion-android-arm64.so.xz" $xz
    Write-Host 'Extracting frida-gadget.so'
    & python3 -c "import lzma; open(r'$GadgetSo','wb').write(lzma.open(r'$xz').read())"
}
if (-not (Test-Path $GadgetSo)) { throw 'frida-gadget.so missing' }

$baseApk = Join-Path $InputDir 'base.apk'
if (-not (Test-Path $baseApk)) { throw "base.apk not found in $InputDir" }

# --- Decode + patch base.apk only (small; asset splits are left untouched) ---
Write-Host 'Decoding base.apk'
& java -jar $ApktoolJar d $baseApk -o $BaseWork -f | Out-Null

$libDir = Join-Path $BaseWork 'lib/arm64-v8a'
Ensure-Dir $libDir
Copy-Item $GadgetSo (Join-Path $libDir 'libfrida-gadget.so') -Force

Write-Host 'Compiling Frida bridge (frida-compile + frida-java-bridge)'
$FridaCompileCli = Join-Path $TmpTools 'node_modules/frida-compile/dist/cli.js'
if (-not (Test-Path $FridaCompileCli)) { throw "frida-compile not found ($FridaCompileCli). Run npm ci in .tmp-tools first." }
$compiled = Join-Path $WorkRoot 'map_fly_bridge.compiled.js'
Push-Location $TmpTools
try { & node $FridaCompileCli $HookScript -o $compiled -S } finally { Pop-Location }
if (-not (Test-Path $compiled) -or (Get-Item $compiled).Length -lt 1000) { throw 'frida-compile produced no/empty output' }
Copy-Item $compiled (Join-Path $libDir 'libmapflybridge.so') -Force
$gadgetConfig = @'
{
  "interaction": {
    "type": "script",
    "path": "libmapflybridge.so"
  }
}
'@
Write-Utf8NoBom (Join-Path $libDir 'libfrida-gadget.config.so') $gadgetConfig.TrimEnd()

$smali = Get-ChildItem $BaseWork -Recurse -Filter 'SdkMultiDexApplication.smali' | Select-Object -First 1 -ExpandProperty FullName
if (-not $smali) { throw 'SdkMultiDexApplication.smali not found in base.apk' }
Inject-GadgetSmali $smali

$bridgeDst = Join-Path $BaseWork 'smali/com/lastasylum/alliance/game'
Ensure-Dir $bridgeDst
Get-ChildItem $MapBridgeSmaliRoot -Recurse -Filter '*.smali' | ForEach-Object {
    Copy-Item $_.FullName (Join-Path $bridgeDst $_.Name) -Force
}
Write-Host 'Copied map bridge smali classes'

Inject-MapBridgeMeta (Join-Path $BaseWork 'AndroidManifest.xml') $GameVersion $BridgeVersion

$patchedBase = Join-Path $WorkRoot 'base-patched.apk'
Write-Host 'Building patched base.apk'
& java -jar $ApktoolJar b $BaseWork -o $patchedBase | Out-Null
if (-not (Test-Path $patchedBase)) { throw 'apktool build of base failed' }

# --- Merge patched base with stock splits into a universal APK ---
$splits = @(Get-ChildItem $InputDir -Filter '*.apk' | Where-Object { $_.Name -ne 'base.apk' })
$unsigned = Join-Path $WorkRoot 'universal-unsigned.apk'
if ($splits.Count -eq 0) {
    Write-Host 'No extra splits - patched base is already universal.'
    Copy-Item $patchedBase $unsigned -Force
} else {
    Write-Host "Merging patched base + $($splits.Count) stock split(s) via APKEditor"
    $mergeIn = Join-Path $WorkRoot 'merge-in'
    Ensure-Dir $mergeIn
    Copy-Item $patchedBase (Join-Path $mergeIn 'base.apk') -Force
    foreach ($s in $splits) { Copy-Item $s.FullName (Join-Path $mergeIn $s.Name) -Force }
    & java -Xmx4g -jar $ApkEditorJar m -i $mergeIn -o $unsigned -f
    if (-not (Test-Path $unsigned)) { throw 'APKEditor merge failed' }
}

# --- Sign (uber-apk-signer --overwrite keeps the original filename in place) ---
Write-Host 'Signing universal APK'
$signDir = Join-Path $WorkRoot 'signed'
Ensure-Dir $signDir
Copy-Item $unsigned $signDir -Force
& java -jar $UberSignerJar -a $signDir --allowResign --overwrite --ks $Keystore --ksAlias $KsAlias --ksPass $KsPass --ksKeyPass $KsKeyPass | Out-Host

$signed = Get-ChildItem $signDir -Filter '*.apk' | Select-Object -First 1
if (-not $signed) { throw 'Signing produced no APK' }

Ensure-Dir (Split-Path $OutApk -Parent)
Copy-Item $signed.FullName $OutApk -Force

$sha = (Get-FileHash $OutApk -Algorithm SHA256).Hash.ToLower()
$size = (Get-Item $OutApk).Length
Write-Host "PATCH_OUT=$OutApk"
Write-Host "PATCH_SHA256=$sha"
Write-Host "PATCH_SIZE=$size"

if ($env:GITHUB_OUTPUT) {
    Add-Content -Path $env:GITHUB_OUTPUT -Value "sha256=$sha"
    Add-Content -Path $env:GITHUB_OUTPUT -Value "size=$size"
    Add-Content -Path $env:GITHUB_OUTPUT -Value "apk=$OutApk"
}
