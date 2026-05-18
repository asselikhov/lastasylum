# One-shot Firebase setup (repo root). Run: .\setup-firebase.ps1
Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
$repoRoot = $PSScriptRoot
Push-Location (Join-Path $repoRoot "backend")
try {
    node scripts/apply-firebase-from-google-services.mjs
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    if (Test-Path "firebase-service-account.json") {
        node scripts/test-fcm-config.mjs
    }
} finally {
    Pop-Location
}
Write-Host ""
Write-Host "Rebuild APK:  cd mobile-android; .\gradlew assembleDevDebug" -ForegroundColor Cyan
