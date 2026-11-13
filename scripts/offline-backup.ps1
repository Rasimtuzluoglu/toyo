$ErrorActionPreference = "Stop"

# Harici disk bağlıyken kaçırılan yedekleri ikinci kopya klasörüne aktarır.
# Uygulama her yedekte kopyayı kendisi yazar; bu betik disk sonradan takıldığında
# veya kopyalama başarısız olduğunda telafi için kullanılır.
# Kullanım: powershell -ExecutionPolicy Bypass -File .\scripts\offline-backup.ps1

$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location $projectRoot
$envPath = Join-Path $projectRoot ".env"

if (-not (Test-Path $envPath)) {
    throw ".env bulunamadı."
}

$line = Get-Content $envPath | Where-Object { $_ -match '^BACKUP_COPY_HOST_DIR=' } | Select-Object -Last 1
if (-not $line) {
    Write-Host "BACKUP_COPY_HOST_DIR tanımlı değil. Önce scripts/setup-backup-copy.ps1 çalıştırın."
    exit 1
}
$copyDir = $line.Substring($line.IndexOf('=') + 1).Trim().Trim('"')
if (-not [System.IO.Path]::IsPathRooted($copyDir)) {
    $copyDir = Join-Path $projectRoot $copyDir
}

if (-not (Test-Path $copyDir)) {
    Write-Host "İkinci kopya klasörü erişilebilir değil: $copyDir"
    Write-Host "Harici diski takın ve tekrar deneyin."
    exit 1
}

$logDir = Join-Path $projectRoot "logs"
New-Item -ItemType Directory -Force -Path $logDir | Out-Null
$logFile = Join-Path $logDir "offline-backup.log"

$source = Join-Path $projectRoot "backups"
if (-not (Test-Path $source)) {
    Write-Host "Yedek klasörü bulunamadı: $source"
    exit 1
}

$result = robocopy $source $copyDir *.dump /XO /R:1 /W:1 /LOG+:$logFile /NP
if ($LASTEXITCODE -ge 8) {
    Write-Host "Kopyalama sırasında hata oluştu. Ayrıntı: $logFile"
    exit 1
}

Write-Host "Kaçırılan yedekler kopyalandı: $copyDir" -ForegroundColor Green
Write-Host "Günlük: $logFile"
