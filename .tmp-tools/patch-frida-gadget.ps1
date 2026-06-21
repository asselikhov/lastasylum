# Patch com.phs.global base.apk with Frida Gadget (no root). Re-sign all splits for sideload.
param(
    [string]$Package = 'com.phs.global',
    [string]$FridaVersion = '17.15.1',
    [switch]$Install,
    [switch]$SkipPull
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
$HookScript = Join-Path $Root '.tmp-tools\frida\hook_game_cmds.js'

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

function Inject-GadgetSmali([string]$SmaliPath) {
    if (-not (Test-Path $SmaliPath)) {
        throw "Smali not found: $SmaliPath"
    }
    $text = Get-Content $SmaliPath -Raw
    if ($text -match 'frida-gadget') {
        Write-Host 'Gadget load already present in smali.'
        return
    }
    $needle = @'
.method protected attachBaseContext(Landroid/content/Context;)V
    .locals 0

    .line 1
    invoke-super {p0, p1}, Lcom/lapslg/sdk/core/LAASGlobalApplication;->attachBaseContext(Landroid/content/Context;)V

    return-void
.end method
'@
    $replacement = @'
.method protected attachBaseContext(Landroid/content/Context;)V
    .locals 1

    const-string v0, "frida-gadget"

    invoke-static {v0}, Ljava/lang/System;->loadLibrary(Ljava/lang/String;)V

    invoke-super {p0, p1}, Lcom/lapslg/sdk/core/LAASGlobalApplication;->attachBaseContext(Landroid/content/Context;)V

    return-void
.end method
'@
    if ($text -notmatch [regex]::Escape('.method protected attachBaseContext(Landroid/content/Context;)V')) {
        throw 'Unexpected SdkMultiDexApplication.smali layout - patch manually.'
    }
    $patched = $text -replace '(?s)\.method protected attachBaseContext\(Landroid/content/Context;\)V.*?\.end method', $replacement.TrimEnd()
    if ($patched -eq $text) {
        throw 'Smali patch failed (pattern mismatch).'
    }
    $utf8NoBom = New-Object System.Text.UTF8Encoding $false
    [System.IO.File]::WriteAllText($SmaliPath, $patched, $utf8NoBom)
    Write-Host "Patched $SmaliPath"
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
            adb pull $remote (Join-Path $ApkDir $name) | Out-Null
        }
    }
}

$baseApk = Join-Path $ApkDir 'base.apk'
if (-not (Test-Path $baseApk)) {
    $baseApk = Join-Path $PatchDir 'base.apk'
}
if (-not (Test-Path $baseApk)) { throw "Missing $baseApk - connect device and re-run." }

if (Test-Path $WorkDir) { Remove-Item $WorkDir -Recurse -Force }
Ensure-Dir $WorkDir

Write-Host 'Decoding base.apk ...'
& java -jar $ApktoolJar d $baseApk -o $WorkDir -f | Out-Null

$libDir = Join-Path $WorkDir 'lib\arm64-v8a'
Ensure-Dir $libDir
Copy-Item $GadgetSo (Join-Path $libDir 'libfrida-gadget.so') -Force
Copy-Item $GadgetCfg (Join-Path $libDir 'libfrida-gadget.config.so') -Force

$smali = Join-Path $WorkDir 'smali\com\games37\sdk\SdkMultiDexApplication.smali'
if (-not (Test-Path $smali)) {
    $smali = Get-ChildItem $WorkDir -Recurse -Filter 'SdkMultiDexApplication.smali' | Select-Object -First 1 -ExpandProperty FullName
}
Inject-GadgetSmali $smali

$unsignedBase = Join-Path $OutDir 'base-unsigned.apk'
if (Test-Path $unsignedBase) { Remove-Item $unsignedBase -Force }
Write-Host 'Building patched base.apk ...'
& java -jar $ApktoolJar b $WorkDir -o $unsignedBase | Out-Null

Write-Host 'Signing all APK splits ...'
$signedDir = Join-Path $OutDir 'signed'
if (Test-Path $signedDir) { Remove-Item $signedDir -Recurse -Force }
Ensure-Dir $signedDir

$toSign = @($unsignedBase) + (Get-ChildItem $ApkDir -Filter '*.apk' -ErrorAction SilentlyContinue | Where-Object { $_.Name -ne 'base.apk' } | ForEach-Object { $_.FullName })
if ($toSign.Count -eq 1) {
    $toSign += Get-ChildItem $PatchDir -Filter 'split_*.apk' | ForEach-Object { $_.FullName }
}
Copy-Item $toSign $signedDir -Force

& java -jar $UberSignerJar -a $signedDir --allowResign --overwrite --ks $Keystore --ksAlias androiddebugkey --ksPass android --ksKeyPass android | Out-Host

$signedApks = Get-ChildItem $signedDir -Filter '*-aligned-debugSigned.apk' -ErrorAction SilentlyContinue
if (-not $signedApks) {
    $signedApks = Get-ChildItem $signedDir -Filter '*.apk'
}
Write-Host "`nSigned APKs:" -ForegroundColor Green
$signedApks | ForEach-Object { Write-Host "  $($_.FullName)" }

if ($Install) {
    Write-Host 'Uninstalling store build (required - different signature) ...' -ForegroundColor Yellow
    adb uninstall $Package | Out-Null
    $installList = ($signedApks | ForEach-Object { "`"$($_.FullName)`"" }) -join ' '
    Invoke-Expression "adb install-multiple $installList"
    Write-Host 'Installed. Forward gadget port and connect Frida:' -ForegroundColor Green
    Write-Host '  adb forward tcp:27042 tcp:27042'
    Write-Host "  frida -H 127.0.0.1:27042 -n Gadget -l `"$HookScript`""
}

Write-Host "`nDone. Re-run with -Install to sideload (game data will be lost on uninstall)." -ForegroundColor Green
