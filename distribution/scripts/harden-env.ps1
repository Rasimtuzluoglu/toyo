$ErrorActionPreference = "Stop"

# .env ve yedek klasörünün Windows erişim izinlerini sıkılaştırır.
# Yalnızca geçerli kullanıcı, SYSTEM ve Administrators erişebilir.
# Kullanım: powershell -ExecutionPolicy Bypass -File .\scripts\harden-env.ps1

$projectRoot = Split-Path -Parent $PSScriptRoot
$envPath = Join-Path $projectRoot ".env"
$backupDir = Join-Path $projectRoot "backups"
$currentUser = [System.Security.Principal.WindowsIdentity]::GetCurrent().Name
$targets = @()

if (Test-Path $envPath) {
    $targets += $envPath
}
if (Test-Path $backupDir) {
    $targets += $backupDir
}

if ($targets.Count -eq 0) {
    Write-Host ".env veya yedek klasörü bulunamadı; önce kurulumu tamamlayın."
    exit 1
}

foreach ($target in $targets) {
    $isDirectory = Test-Path $target -PathType Container
    if ($isDirectory) {
        # Klasör: alt öğelere de miras verilir.
        $grants = @(
            "${currentUser}:(OI)(CI)F",
            "SYSTEM:(OI)(CI)F",
            "Administrators:(OI)(CI)F"
        )
    } else {
        # Dosya: (OI)(CI) kullanılırsa ACE yalnızca alt öğeler için geçerli olur
        # ve dosyanın kendisine erişim kalmaz.
        $grants = @(
            "${currentUser}:F",
            "SYSTEM:F",
            "Administrators:F"
        )
    }

    $args = @($target, "/inheritance:r", "/grant:r") + $grants
    & icacls @args | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "Erişim izinleri ayarlanamadı: $target"
    }
    Write-Host "İzinler sıkılaştırıldı: $target"
}

# Kendi dosyamızı okuyabildiğimizi doğrula; aksi halde Docker Compose .env okuyamaz.
if (Test-Path $envPath) {
    try {
        [System.IO.File]::ReadAllText($envPath) | Out-Null
    } catch {
        throw ('.env okunamıyor. Erişimi geri almak için: icacls .env /grant:r "' + $currentUser + ':F"')
    }
}

Write-Host "Tamamlandı. .env yalnızca $currentUser, SYSTEM ve Administrators tarafından okunabilir."
Write-Host "Not: Dosyayı paylaşmayın; yedeklerini şifreli bir harici diskte tutun."
