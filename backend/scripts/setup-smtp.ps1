# Interactive SMTP setup for SquadRelay backend (.env + Render copy-paste block).
# Run from repo root:  .\backend\scripts\setup-smtp.ps1
# Or from backend:    .\scripts\setup-smtp.ps1

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$backendDir = Split-Path -Parent $scriptDir
$envPath = Join-Path $backendDir ".env"

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

Write-Host ""
Write-Host "=== SquadRelay: настройка SMTP ===" -ForegroundColor Cyan
Write-Host @"

Нужен SMTP-провайдер (аккаунт создаёте вы, ~5 мин):
  • Brevo (рекомендуется): https://www.brevo.com → SMTP & API → ключ
  • SendGrid: smtp.sendgrid.net, USER=apikey, PASS=API key
  • Gmail: smtp.gmail.com + пароль приложения (не обычный пароль)

"@

$provider = Read-Host "Провайдер [1=Brevo 2=SendGrid 3=Gmail 4=Свой] (Enter=1)"
if ([string]::IsNullOrWhiteSpace($provider)) { $provider = "1" }

$defaults = switch ($provider) {
    "2" { @{ Host = "smtp.sendgrid.net"; Port = "587"; Secure = "false"; User = "apikey" } }
    "3" { @{ Host = "smtp.gmail.com"; Port = "587"; Secure = "false"; User = "" } }
    "4" { @{ Host = ""; Port = "587"; Secure = "false"; User = "" } }
    default { @{ Host = "smtp-relay.brevo.com"; Port = "587"; Secure = "false"; User = "" } }
}

$hostVal = Read-Host "SMTP_HOST [$($defaults.Host)]"
if ([string]::IsNullOrWhiteSpace($hostVal)) { $hostVal = $defaults.Host }
$portVal = Read-Host "SMTP_PORT [$($defaults.Port)]"
if ([string]::IsNullOrWhiteSpace($portVal)) { $portVal = $defaults.Port }
$secureVal = Read-Host "SMTP_SECURE (true для порта 465) [$($defaults.Secure)]"
if ([string]::IsNullOrWhiteSpace($secureVal)) { $secureVal = $defaults.Secure }
$userVal = Read-Host "SMTP_USER [$($defaults.User)]"
if ([string]::IsNullOrWhiteSpace($userVal)) { $userVal = $defaults.User }
$passVal = Read-Host "SMTP_PASS (ввод скрыт)" -AsSecureString
$passPlain = [Runtime.InteropServices.Marshal]::PtrToStringAuto(
    [Runtime.InteropServices.Marshal]::SecureStringToBSTR($passVal)
)
$fromVal = Read-Host "SMTP_FROM (например SquadRelay <noreply@yourdomain.com>)"
$appName = Read-Host "APP_PUBLIC_NAME [SquadRelay]"
if ([string]::IsNullOrWhiteSpace($appName)) { $appName = "SquadRelay" }

Set-EnvKey "SMTP_HOST" $hostVal
Set-EnvKey "SMTP_PORT" $portVal
Set-EnvKey "SMTP_SECURE" $secureVal
Set-EnvKey "SMTP_USER" $userVal
Set-EnvKey "SMTP_PASS" $passPlain
Set-EnvKey "SMTP_FROM" $fromVal
Set-EnvKey "APP_PUBLIC_NAME" $appName

Write-Host ""
Write-Host "Saved to backend/.env" -ForegroundColor Green

$testTo = Read-Host "Отправить тестовое письмо на email (Enter = пропустить)"
if (-not [string]::IsNullOrWhiteSpace($testTo)) {
    Push-Location $backendDir
    try {
        node scripts/test-smtp.mjs $testTo
    } finally {
        Pop-Location
    }
}

Write-Host ""
Write-Host "=== Скопируйте в Render → lastasylum-backend → Environment ===" -ForegroundColor Cyan
Write-Host @"
SMTP_HOST=$hostVal
SMTP_PORT=$portVal
SMTP_SECURE=$secureVal
SMTP_USER=$userVal
SMTP_PASS=<тот же пароль, что ввели выше>
SMTP_FROM=$fromVal
APP_PUBLIC_NAME=$appName
"@ -ForegroundColor White
Write-Host ""
Write-Host "После Save → дождитесь redeploy. Проверка: forgot-password в приложении или:" -ForegroundColor Yellow
Write-Host '  curl -X POST https://lastasylum-backend.onrender.com/auth/forgot-password -H "Content-Type: application/json" -d "{\"email\":\"...\"}"' -ForegroundColor DarkGray
Write-Host ""
