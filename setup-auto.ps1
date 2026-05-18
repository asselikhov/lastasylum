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

function Resolve-LocalJdkHome {
    $candidates = @(
        (Join-Path $env:ProgramFiles "Eclipse Adoptium\jdk-17.0.18.8-hotspot"),
        (Join-Path $env:ProgramFiles "Android\Android Studio\jbr")
    )
    foreach ($p in $candidates) {
        $java = Join-Path $p "bin\java.exe"
        if (Test-Path $java) {
            return $p
        }
    }
    return ""
}

function Ensure-AndroidGradleJdk([string]$jdkHome) {
    if ([string]::IsNullOrWhiteSpace($jdkHome)) { return }

    $androidLocalProps = Join-Path $root "mobile-android\local.properties"
    if (-not (Test-Path $androidLocalProps)) {
        return
    }

    $text = Get-Content -Path $androidLocalProps -Raw -Encoding UTF8
    if ($text -match "(?m)^org\.gradle\.java\.home\s*=") {
        return
    }

    $escaped = $jdkHome.Replace("\", "\\")
    Add-Content -Path $androidLocalProps -Value "" -Encoding UTF8
    Add-Content -Path $androidLocalProps -Value "org.gradle.java.home=$escaped" -Encoding UTF8
}

$backendDir = Join-Path $root "backend"
$backendEnv = Join-Path $backendDir ".env"
$localPropsRoot = Join-Path $root "local.properties"
$localPropsAndroid = Join-Path $root "mobile-android\local.properties"

if (-not (Test-Path $backendDir)) {
    throw "Папка backend не найдена: $backendDir"
}

function Set-LocalProperty {
    param(
        [string]$FilePath,
        [string]$Key,
        [string]$Value
    )
    $lines = @()
    if (Test-Path $FilePath) {
        $lines = @(Get-Content -Path $FilePath -Encoding UTF8)
    }
    $escapedKey = [regex]::Escape($Key)
    $filtered = @($lines | Where-Object { $_ -notmatch "^\s*$escapedKey\s*=" })
    $filtered += "$Key=$Value"
    $dir = Split-Path -Parent $FilePath
    if ($dir -and -not (Test-Path $dir)) {
        New-Item -ItemType Directory -Path $dir -Force | Out-Null
    }
    Set-Content -Path $FilePath -Value ($filtered -join "`n") -Encoding UTF8
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
Write-Host "Создан backend/.env (не коммитьте в git)" -ForegroundColor Green

Set-LocalProperty -FilePath $localPropsRoot -Key "squadrelay.api.baseUrl" -Value $ApiBaseUrl
Set-LocalProperty -FilePath $localPropsAndroid -Key "squadrelay.api.baseUrl" -Value $ApiBaseUrl
Write-Host "Записан squadrelay.api.baseUrl в local.properties (не коммитьте)" -ForegroundColor Green

$jdkHome = Resolve-LocalJdkHome
if (-not [string]::IsNullOrWhiteSpace($jdkHome)) {
    $env:JAVA_HOME = $jdkHome
    $env:Path = (Join-Path $jdkHome "bin") + ";" + $env:Path
    Ensure-AndroidGradleJdk -jdkHome $jdkHome
    Write-Host "Настроен JDK для Gradle: $jdkHome" -ForegroundColor Green
} else {
    Write-Host "JDK не найден автоматически. Установи Temurin 17 или Android Studio, либо выставь JAVA_HOME." -ForegroundColor Yellow
}

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
Write-Host "2) Android API URL set in local.properties (tracked Gradle files unchanged)."
Write-Host ""
Write-Host "Next run backend:" -ForegroundColor Cyan
Write-Host "   cd backend; npm run start:dev"
Write-Host ""
Write-Host "Then open mobile-android in Android Studio and run app."
Write-Host "Linux/macOS: ./setup.sh" -ForegroundColor DarkGray
