$ErrorActionPreference = "Stop"

# .env ve yedek klasörünün Windows erişim izinlerini sıkılaştırır.
# Yalnızca geçerli kullanıcı, SYSTEM ve Administrators okuyabilir.
# Kullanım: powershell -ExecutionPolicy Bypass -File .\scripts\harden-env.ps1

$projectRoot = Split-Path -Parent $PSScriptRoot
$envPath = Join-Path $projectRoot ".env"
$backupDir = Join-Path $projectRoot "backups"
$currentUser = [System.Security.Principal.WindowsIdentity]::GetCurrent().Name
$aclTargets = @()

if (Test-Path $envPath) {
    $aclTargets += $envPath
}
if (Test-Path $backupDir) {
    $aclTargets += $backupDir
} else {
    Write-Host "Yedek klasörü bulunamadı: $backupDir (daha sonra oluşturulursa tekrar çalıştırın)."
}

if ($aclTargets.Count -eq 0) {
    Write-Host ".env bulunamadı; önce kurulumu tamamlayın."
    exit 1
}

foreach ($target in $aclTargets) {
    & icacls $target /inheritance:r /grant:r "${currentUser}:(OI)(CI)F" "SYSTEM:(OI)(CI)F" "Administrators:(OI)(CI)F" | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "Erişim izinleri ayarlanamadı: $target"
    }
    Write-Host "İzinler sıkılaştırıldı: $target"
}

Write-Host "Tamamlandı. .env yalnızca $currentUser, SYSTEM ve Administrators tarafından okunabilir."
Write-Host "Not: Dosyayı paylaşmayın; yedeklerini şifreli bir harici diskte tutun."
