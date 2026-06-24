# Patch com.phs.global base.apk with Frida Gadget (no root). Re-sign all splits for sideload.
param(
    [string]$Package = 'com.phs.global',
    [string]$FridaVersion = '17.15.1',
    [switch]$Install,
    [switch]$SkipPull,
    # Required with -Install: patched APK uses a debug signature and Android must uninstall the store build first (game data is lost).
    [switch]$ConfirmDataLoss
)

$ErrorActionPreference = 'Stop'
$Root = Split-Path $PSScriptRoot -Parent
$PatchDir = Join-Path $Root '.tmp-tools\frida-patch'
$ApkDir = Join-Path $PatchDir 'apks'
$WorkDir = Join-Path $PatchDir 'decoded'
$OutDir = Join-Path $PatchDir 'out'
$Keystore = Join-Path $PatchDir 'debug.keystore'
$ApktoolJar = Join-Path $PatchDir 'apktool.jar'
$UberSignerJar = Join-Path $PatchDir 'uber-apk-signer.jar'
$GadgetSo = Join-Path $PatchDir 'libfrida-gadget.so'
$GadgetCfg = Join-Path $PatchDir 'libfrida-gadget.config.so'
$HookScript = Join-Path $Root '.tmp-tools\frida\map_fly_bridge.js'
$HookScriptLegacy = Join-Path $Root '.tmp-tools\frida\hook_game_cmds.js'

function Ensure-Dir([string]$Path) {
    if (-not (Test-Path $Path)) { New-Item -ItemType Directory -Force -Path $Path | Out-Null }
}

function Download-IfMissing([string]$Url, [string]$Dest) {
    if (Test-Path $Dest) { return }
    Write-Host "Downloading $Url ..."
    Invoke-WebRequest -Uri $Url -OutFile $Dest -UseBasicParsing
}

function Ensure-Keystore([string]$Path) {
    if (Test-Path $Path) { return }
    Write-Host 'Creating debug.keystore ...'
    $dname = 'CN=LastAsylum Debug, OU=Dev, O=LastAsylum, L=Local, ST=Local, C=RU'
    & keytool -genkeypair -v -keystore $Path -storepass android -alias androiddebugkey `
        -keypass android -keyalg RSA -keysize 2048 -validity 10000 -dname $dname
}

function Get-InstalledGameVersion([string]$Package) {
    $raw = adb shell dumpsys package $Package 2>$null
    foreach ($line in $raw) {
        if ($line -match 'versionName=(\S+)') {
            return $matches[1].Trim()
        }
    }
    return 'unknown'
}

function Get-ApkVersionName([string]$ApkPath) {
    if (-not (Test-Path $ApkPath)) { return 'unknown' }
    $aapt = @(
        "$env:LOCALAPPDATA\Android\Sdk\build-tools\*\aapt.exe",
        "$env:ANDROID_HOME\build-tools\*\aapt.exe"
    ) | ForEach-Object { Resolve-Path $_ -ErrorAction SilentlyContinue } | Sort-Object -Descending | Select-Object -First 1
    if (-not $aapt) { return 'unknown' }
    $line = & $aapt.Source dump badging $ApkPath 2>$null | Select-String 'versionName=' | Select-Object -First 1
    if ($line -match "versionName='([^']+)'") { return $matches[1].Trim() }
    return 'unknown'
}

function Inject-MapBridgeMeta([string]$ManifestPath, [string]$GameVersion, [string]$BridgeVersion = '1') {
    if (-not (Test-Path $ManifestPath)) {
        throw "Manifest not found: $ManifestPath"
    }
    $text = Get-Content $ManifestPath -Raw
    if ($text -notmatch 'android:debuggable="true"') {
        $text = $text -replace '(<application\b)', '$1 android:debuggable="true"'
        Write-Host 'Injected android:debuggable="true"'
    }
    if ($text -match 'com\.lastasylum\.alliance\.map_bridge_game_version') {
        $text = $text -replace 'android:name="com\.lastasylum\.alliance\.map_bridge_game_version" android:value="[^"]*"', "android:name=`"com.lastasylum.alliance.map_bridge_game_version`" android:value=`"$GameVersion`""
        $utf8NoBom = New-Object System.Text.UTF8Encoding $false
        [System.IO.File]::WriteAllText($ManifestPath, $text, $utf8NoBom)
        Write-Host "Updated map bridge game version meta-data to $GameVersion"
        return
    }
    $meta = @"
        <meta-data android:name="com.lastasylum.alliance.map_bridge" android:value="1"/>
        <meta-data android:name="com.lastasylum.alliance.map_bridge_version" android:value="$BridgeVersion"/>
        <meta-data android:name="com.lastasylum.alliance.map_bridge_game_version" android:value="$GameVersion"/>
"@
    $patched = $text -replace '(<application\b[^>]*>)', "`$1`r`n$meta"
    if ($patched -eq $text) {
        throw 'Failed to inject map bridge meta-data into AndroidManifest.xml'
    }
    $utf8NoBom = New-Object System.Text.UTF8Encoding $false
    [System.IO.File]::WriteAllText($ManifestPath, $patched, $utf8NoBom)
    Write-Host "Injected map bridge meta-data for game version $GameVersion"
}

function Inject-GadgetSmali([string]$SmaliPath) {
    if (-not (Test-Path $SmaliPath)) {
        throw "Smali not found: $SmaliPath"
    }
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
        Write-Host 'Moved gadget load from attachBaseContext to onCreate.'
    }
    if ($text -match 'onCreate\(\)V[\s\S]*?frida-gadget') {
        if ($text -match 'if-nez v0, :cond_skip[\s\S]*?frida-gadget') {
            $text = $text -replace 'if-nez v0, :cond_skip', 'if-eqz v0, :cond_skip'
            Write-Host 'Fixed inverted main-process guard in onCreate.'
        } else {
            Write-Host 'Gadget load already present in onCreate.'
        }
        $utf8NoBom = New-Object System.Text.UTF8Encoding $false
        [System.IO.File]::WriteAllText($SmaliPath, $text, $utf8NoBom)
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
    if ($patched -eq $text) {
        throw 'Smali patch failed (onCreate insert mismatch).'
    }
    $utf8NoBom = New-Object System.Text.UTF8Encoding $false
    [System.IO.File]::WriteAllText($SmaliPath, $patched, $utf8NoBom)
    Write-Host "Patched $SmaliPath (onCreate loads frida-gadget)"
}

Ensure-Dir $PatchDir
Ensure-Dir $ApkDir
Ensure-Dir $OutDir
Ensure-Keystore $Keystore

Download-IfMissing "https://github.com/iBotPeaches/Apktool/releases/download/v2.11.1/apktool_2.11.1.jar" $ApktoolJar
Download-IfMissing "https://github.com/patrickfav/uber-apk-signer/releases/download/v1.3.0/uber-apk-signer-1.3.0.jar" $UberSignerJar
Download-IfMissing "https://github.com/frida/frida/releases/download/$FridaVersion/frida-gadget-$FridaVersion-android-arm64.so.xz" (Join-Path $PatchDir "frida-gadget.so.xz")

if (-not (Test-Path $GadgetSo)) {
    Write-Host 'Extracting frida-gadget.so ...'
    $xzPath = Join-Path $PatchDir 'frida-gadget.so.xz'
    $py = @(
        "$env:LocalAppData\Programs\Python\Python312\python.exe",
        "$env:LocalAppData\Programs\Python\Python311\python.exe"
    ) | Where-Object { Test-Path $_ } | Select-Object -First 1
    if ($py -and (Test-Path $xzPath)) {
        & $py -c "import lzma; open(r'$GadgetSo','wb').write(lzma.open(r'$xzPath').read())"
    } else {
        $sevenZip = @(
            "${env:ProgramFiles}\7-Zip\7z.exe",
            "${env:ProgramFiles(x86)}\7-Zip\7z.exe"
        ) | Where-Object { Test-Path $_ } | Select-Object -First 1
        if ($sevenZip) {
            & $sevenZip e $xzPath "-o$PatchDir" -y | Out-Null
            $extracted = Join-Path $PatchDir "frida-gadget-$FridaVersion-android-arm64.so"
            if (Test-Path $extracted) { Move-Item -Force $extracted $GadgetSo }
        } else {
            Download-IfMissing "https://github.com/frida/frida/releases/download/$FridaVersion/frida-gadget-$FridaVersion-android-arm64.so" $GadgetSo
        }
    }
}
if (-not (Test-Path $GadgetSo)) { throw 'frida-gadget.so not found - install 7-Zip or extract manually.' }

if (-not $SkipPull) {
    Write-Host 'Pulling APK splits from device ...'
    adb shell pm path $Package | ForEach-Object {
        if ($_ -match 'package:(.+)') {
            $remote = $matches[1].Trim()
            $name = Split-Path $remote -Leaf
            & adb pull $remote (Join-Path $ApkDir $name) 2>&1 | Out-Null
        }
    }
}

$baseApk = Join-Path $ApkDir 'base.apk'
if (-not (Test-Path $baseApk)) {
    $baseApk = Join-Path $PatchDir 'base.apk'
}
if (-not (Test-Path $baseApk)) { throw "Missing $baseApk - connect device and re-run." }

$gameVersion = Get-InstalledGameVersion $Package
if ($gameVersion -eq 'unknown') {
    $gameVersion = Get-ApkVersionName (Join-Path $ApkDir 'base.apk')
}
Write-Host "Target game version: $gameVersion"

if (Test-Path $WorkDir) { Remove-Item $WorkDir -Recurse -Force }
Ensure-Dir $WorkDir

Write-Host 'Decoding base.apk ...'
& java -jar $ApktoolJar d $baseApk -o $WorkDir -f | Out-Null

$libDir = Join-Path $WorkDir 'lib\arm64-v8a'
Ensure-Dir $libDir
Copy-Item $GadgetSo (Join-Path $libDir 'libfrida-gadget.so') -Force
Copy-Item $HookScript (Join-Path $libDir 'libmapflybridge.so') -Force
$gadgetConfig = @'
{
  "interaction": {
    "type": "script",
    "path": "libmapflybridge.so"
  }
}
'@
$utf8NoBom = New-Object System.Text.UTF8Encoding $false
[System.IO.File]::WriteAllText((Join-Path $libDir 'libfrida-gadget.config.so'), $gadgetConfig.TrimEnd(), $utf8NoBom)

$smali = Join-Path $WorkDir 'smali\com\games37\sdk\SdkMultiDexApplication.smali'
if (-not (Test-Path $smali)) {
    $smali = Get-ChildItem $WorkDir -Recurse -Filter 'SdkMultiDexApplication.smali' | Select-Object -First 1 -ExpandProperty FullName
}
Inject-GadgetSmali $smali

$mapFlySmaliSrc = Join-Path $PatchDir 'map-bridge-smali\com\lastasylum\alliance\game\MapFlyReceiver.smali'
$mapFlySmaliDst = Join-Path $WorkDir 'smali\com\lastasylum\alliance\game\MapFlyReceiver.smali'
if (-not (Test-Path $mapFlySmaliSrc)) {
    throw "Missing $mapFlySmaliSrc"
}
Ensure-Dir (Split-Path $mapFlySmaliDst -Parent)
Copy-Item $mapFlySmaliSrc $mapFlySmaliDst -Force
Write-Host 'Copied MapFlyReceiver.smali'

$manifest = Join-Path $WorkDir 'AndroidManifest.xml'
Inject-MapBridgeMeta $manifest $gameVersion

$unsignedBase = Join-Path $OutDir 'base-unsigned.apk'
if (Test-Path $unsignedBase) { Remove-Item $unsignedBase -Force }
Write-Host 'Building patched base.apk ...'
& java -jar $ApktoolJar b $WorkDir -o $unsignedBase | Out-Null

Write-Host 'Signing all APK splits ...'
$signedDir = Join-Path $OutDir 'signed'
if (Test-Path $signedDir) { Remove-Item $signedDir -Recurse -Force }
Ensure-Dir $signedDir

$deviceSplits = @(Get-ChildItem $ApkDir -Filter 'split_*.apk' -ErrorAction SilentlyContinue)
$toSign = @($unsignedBase) + ($deviceSplits | ForEach-Object { $_.FullName })
if ($toSign.Count -eq 1) {
    Write-Host 'No split_*.apk in apks/ - pull game from device first (run without -SkipPull).' -ForegroundColor Yellow
}
# Never mix in stale split_*.apk from PatchDir root (wrong versionCode).
Copy-Item $toSign $signedDir -Force

$prevEap = $ErrorActionPreference
$ErrorActionPreference = 'Continue'
& java -jar $UberSignerJar -a $signedDir --allowResign --overwrite --ks $Keystore --ksAlias androiddebugkey --ksPass android --ksKeyPass android 2>&1 | Out-Host
$ErrorActionPreference = $prevEap

$signedApks = Get-ChildItem $signedDir -Filter '*-aligned-debugSigned.apk' -ErrorAction SilentlyContinue
if (-not $signedApks) {
    $signedApks = Get-ChildItem $signedDir -Filter '*.apk'
}
Write-Host "`nSigned APKs:" -ForegroundColor Green
$signedApks | ForEach-Object { Write-Host "  $($_.FullName)" }

if ($Install) {
    if (-not $ConfirmDataLoss) {
        Write-Host 'Refusing to install: patched APK needs a different signature.' -ForegroundColor Red
        Write-Host 'Android will uninstall the store build first - all local game data is lost.' -ForegroundColor Red
        Write-Host 'Re-run with: -Install -ConfirmDataLoss' -ForegroundColor Yellow
        exit 1
    }
    $installList = ($signedApks | ForEach-Object { "`"$($_.FullName)`"" }) -join ' '
    Write-Host 'Uninstalling store build (required - different signature, data will be lost) ...' -ForegroundColor Yellow
    adb uninstall $Package | Out-Null
    $installOut = Invoke-Expression "adb install-multiple --no-incremental -r $installList 2>&1" | Out-String
    Write-Host $installOut
    if ($LASTEXITCODE -ne 0 -or $installOut -match 'Failure|INSTALL_FAILED|error') {
        Write-Host 'Install FAILED. Game was uninstalled - reinstall from Play Store.' -ForegroundColor Red
        exit 1
    }
    $installed = adb shell pm path $Package 2>$null
    if (-not $installed) {
        Write-Host 'Install reported success but package is missing - reinstall from Play Store.' -ForegroundColor Red
        exit 1
    }
    Write-Host 'Installed. Map fly bridge auto-loads on game start.' -ForegroundColor Green
    Write-Host "  Optional RE hooks: frida -H 127.0.0.1:27042 -n Gadget -l `"$HookScriptLegacy`""
}

Write-Host "`nDone. To sideload: -Install -ConfirmDataLoss (uninstalls store build, data loss)." -ForegroundColor Green
