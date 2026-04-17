param(
    [string]$MongoUri = "",
    [string]$ApiBaseUrl = ""
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function New-RandomSecret([int]$length = 64) {
    $chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789!@#$%^&*()_-+=[]{}"
    -join ((1..$length) | ForEach-Object { $chars[(Get-Random -Minimum 0 -Maximum $chars.Length)] })
}

function Ask-IfEmpty([string]$value, [string]$prompt) {
    if ([string]::IsNullOrWhiteSpace($value)) {
        return Read-Host $prompt
    }
    return $value
}

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$backendDir = Join-Path $root "backend"
$androidGradle = Join-Path $root "mobile-android\app\build.gradle.kts"
$backendEnv = Join-Path $backendDir ".env"

if (-not (Test-Path $backendDir)) {
    throw "Папка backend не найдена: $backendDir"
}
if (-not (Test-Path $androidGradle)) {
    throw "Файл Android build.gradle.kts не найден: $androidGradle"
}

Write-Host ""
Write-Host "=== SquadRelay: автоматическая настройка ===" -ForegroundColor Cyan
Write-Host ""

$MongoUri = Ask-IfEmpty $MongoUri "Вставь MONGODB_URI (из MongoDB Atlas):"
$ApiBaseUrl = Ask-IfEmpty $ApiBaseUrl "Вставь API URL (Render), например https://app.onrender.com/"
if ([string]::IsNullOrWhiteSpace($MongoUri)) {
    throw "MONGODB_URI обязателен."
}
if ([string]::IsNullOrWhiteSpace($ApiBaseUrl)) {
    throw "API URL обязателен."
}
if (-not $ApiBaseUrl.EndsWith("/")) {
    $ApiBaseUrl = "$ApiBaseUrl/"
}
$jwtSecret = New-RandomSecret
$jwtRefreshSecret = New-RandomSecret

$envContent = @"
PORT=3000
MONGODB_URI=$MongoUri
MONGODB_DB_NAME=last_asylum
JWT_SECRET=$jwtSecret
JWT_EXPIRES_IN=7d
JWT_REFRESH_SECRET=$jwtRefreshSecret
JWT_REFRESH_EXPIRES_IN=30d
"@

Set-Content -Path $backendEnv -Value $envContent -Encoding UTF8
Write-Host "Создан backend/.env" -ForegroundColor Green

$gradleText = Get-Content -Path $androidGradle -Raw -Encoding UTF8
$escapedUrl = $ApiBaseUrl.Replace('\', '\\')
$replacement = 'buildConfigField("String", "API_BASE_URL", "\"{0}\"")' -f $escapedUrl
$gradleText = [regex]::Replace(
    $gradleText,
    'buildConfigField\("String",\s*"API_BASE_URL",\s*"[^"]*"\)',
    $replacement
)
Set-Content -Path $androidGradle -Value $gradleText -Encoding UTF8
Write-Host "Обновлен API_BASE_URL в mobile-android/app/build.gradle.kts" -ForegroundColor Green

Push-Location $backendDir
try {
    Write-Host ""
    Write-Host "Устанавливаю зависимости backend..." -ForegroundColor Yellow
    npm install
    if ($LASTEXITCODE -ne 0) { throw "npm install завершился с ошибкой." }

    Write-Host "Проверяю backend (lint/test/build)..." -ForegroundColor Yellow
    npm run lint
    if ($LASTEXITCODE -ne 0) { throw "npm run lint завершился с ошибкой." }
    npm run test
    if ($LASTEXITCODE -ne 0) { throw "npm run test завершился с ошибкой." }
    npm run build
    if ($LASTEXITCODE -ne 0) { throw "npm run build завершился с ошибкой." }
}
finally {
    Pop-Location
}

Write-Host ""
Write-Host "Done." -ForegroundColor Green
Write-Host "1) Backend configured and validated."
Write-Host "2) Android API URL updated."
Write-Host ""
Write-Host "Next run backend:" -ForegroundColor Cyan
Write-Host "   cd backend; npm run start:dev"
Write-Host ""
Write-Host "Then open mobile-android in Android Studio and run app."
