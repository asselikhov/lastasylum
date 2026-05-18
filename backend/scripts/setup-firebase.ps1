# Interactive Firebase / FCM setup for SquadRelay (backend .env + Android local.properties + Render block).
# Run from repo root:  .\backend\scripts\setup-firebase.ps1
#
# You must create the Firebase project yourself (Google account). This script does NOT call Firebase APIs.

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$backendDir = Split-Path -Parent $scriptDir
$repoRoot = Split-Path -Parent $backendDir
$envPath = Join-Path $backendDir ".env"
$localPropsRoot = Join-Path $repoRoot "local.properties"
$localPropsAndroid = Join-Path $repoRoot "mobile-android\local.properties"

$androidPackage = "com.lastasylum.alliance"

if (-not (Test-Path $envPath)) {
    Copy-Item (Join-Path $backendDir ".env.example") $envPath
    Write-Host "Created backend/.env from .env.example" -ForegroundColor Yellow
}

function Set-EnvKey {
    param([string]$Key, [string]$Value)
    $lines = @(Get-Content -Path $envPath -Encoding UTF8 -ErrorAction SilentlyContinue)
    $escaped = [regex]::Escape($Key)
    $filtered = @($lines | Where-Object { $_ -notmatch "^\s*$escaped\s*=" })
    $filtered += "$Key=$Value"
    Set-Content -Path $envPath -Value $filtered -Encoding UTF8
}

function Set-LocalProperty {
    param([string]$Path, [string]$Key, [string]$Value)
    if (-not (Test-Path $Path)) {
        if (Test-Path (Join-Path $repoRoot "local.properties.example")) {
            Copy-Item (Join-Path $repoRoot "local.properties.example") $Path
        } else {
            New-Item -ItemType File -Path $Path -Force | Out-Null
        }
    }
    $lines = @(Get-Content -Path $Path -Encoding UTF8 -ErrorAction SilentlyContinue)
    $escaped = [regex]::Escape($Key)
    $filtered = @($lines | Where-Object { $_ -notmatch "^\s*$escaped\s*=" })
    $filtered += "$Key=$Value"
    Set-Content -Path $Path -Value $filtered -Encoding UTF8
}

function Test-ServiceAccountJson {
    param([string]$JsonPath)
    $raw = Get-Content -Path $JsonPath -Raw -Encoding UTF8
    $obj = $raw | ConvertFrom-Json
    if (-not $obj.project_id) { throw "JSON missing project_id" }
    if (-not $obj.private_key) { throw "JSON missing private_key" }
    if (-not $obj.client_email) { throw "JSON missing client_email" }
    return ($raw | ConvertFrom-Json | ConvertTo-Json -Compress -Depth 20)
}

Write-Host ""
Write-Host "=== SquadRelay: настройка Firebase (FCM push) ===" -ForegroundColor Cyan
Write-Host @"

Создайте проект в Firebase Console (ваш Google-аккаунт):

  1. https://console.firebase.google.com/ → Add project (или откройте существующий)
  2. Project settings (шестерёнка) → вкладка General
  3. Your apps → Add app → Android
       Package name: $androidPackage
       (SHA-1 опционально сейчас; для release позже: mobile-android → ./gradlew signingReport)
  4. После регистрации приложения скопируйте с той же страницы:
       • Project ID
       • App ID (mobilesdk_app_id, вид 1:....:android:....)
       • Web API Key (в разделе SDK setup / Your apps)
  5. Скачайте google-services.json → положите в mobile-android/app/google-services.json
  6. Project settings → Service accounts → Generate new private key → скачайте .json
       (храните как секрет; в git не коммитить — это для Render, не путать с google-services.json)

"@ -ForegroundColor White

$projectId = Read-Host "Firebase Project ID"
$appId = Read-Host "Firebase Android App ID (1:...:android:...)"
$apiKey = Read-Host "Firebase Web API Key (AIza...)"
$jsonPath = Read-Host "Путь к скачанному service account JSON (например C:\Users\You\Downloads\....json)"

if (-not (Test-Path $jsonPath)) {
    throw "File not found: $jsonPath"
}

$serviceAccountOneLine = Test-ServiceAccountJson -JsonPath $jsonPath
Set-EnvKey "FIREBASE_SERVICE_ACCOUNT_JSON" $serviceAccountOneLine

Set-LocalProperty -Path $localPropsRoot -Key "squadrelay.firebase.projectId" -Value $projectId.Trim()
Set-LocalProperty -Path $localPropsRoot -Key "squadrelay.firebase.appId" -Value $appId.Trim()
Set-LocalProperty -Path $localPropsRoot -Key "squadrelay.firebase.apiKey" -Value $apiKey.Trim()

if ($localPropsRoot -ne $localPropsAndroid) {
    Set-LocalProperty -Path $localPropsAndroid -Key "squadrelay.firebase.projectId" -Value $projectId.Trim()
    Set-LocalProperty -Path $localPropsAndroid -Key "squadrelay.firebase.appId" -Value $appId.Trim()
    Set-LocalProperty -Path $localPropsAndroid -Key "squadrelay.firebase.apiKey" -Value $apiKey.Trim()
}

Write-Host ""
Write-Host "Saved:" -ForegroundColor Green
Write-Host "  backend/.env  → FIREBASE_SERVICE_ACCOUNT_JSON"
Write-Host "  local.properties (repo root + mobile-android) → squadrelay.firebase.*"
Write-Host ""
Write-Host "Пересоберите APK после изменения local.properties:" -ForegroundColor Yellow
Write-Host "  cd mobile-android; .\gradlew assembleDevDebug" -ForegroundColor DarkGray
Write-Host ""

Write-Host "=== Render → lastasylum-backend → Environment ===" -ForegroundColor Cyan
Write-Host @"

Добавьте ОДНУ переменную (значение — вся JSON-строка в одну линию, без переносов):

  Key:   FIREBASE_SERVICE_ACCOUNT_JSON
  Value: (скопируйте из backend/.env — строка после FIREBASE_SERVICE_ACCOUNT_JSON=)

В Render UI: вставьте значение целиком; кавычки снаружи обычно НЕ нужны.

После Save дождитесь redeploy. В логах при старте должно быть:
  Firebase Admin initialized for FCM

"@ -ForegroundColor White

Write-Host "=== Android local.properties (для сборки на ПК) ===" -ForegroundColor Cyan
Write-Host @"
squadrelay.firebase.projectId=$($projectId.Trim())
squadrelay.firebase.appId=$($appId.Trim())
squadrelay.firebase.apiKey=$($apiKey.Trim())
"@ -ForegroundColor DarkGray

Write-Host ""
Write-Host "Рекомендация: Google Cloud → Credentials → ограничьте API key по package $androidPackage + SHA-1." -ForegroundColor Yellow
Write-Host "Проверка push: зайдите в приложение на телефоне → в MongoDB у user должен появиться pushFcmTokens." -ForegroundColor Yellow
Write-Host ""
