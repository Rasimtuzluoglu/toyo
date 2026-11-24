param(
    [string]$Target = "",
    [string]$Source = ""
)

$ErrorActionPreference = "Stop"

# Ana yedek klasörünü harici disk/NAS/ikinci konuma kopyalar.
# Kullanım:
#   powershell -ExecutionPolicy Bypass -File .\scripts\backup-offsite.ps1 -Target E:\ToyotaServis-Yedekler
# Hedef verilmezse .env içindeki BACKUP_OFFSITE_DIR kullanılır.

$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location $projectRoot
$envPath = Join-Path $projectRoot ".env"

function Read-EnvValue($key, $defaultValue) {
    if (-not (Test-Path $envPath)) {
        return $defaultValue
    }
    $line = Get-Content $envPath | Where-Object { $_ -match "^$key=" } | Select-Object -Last 1
    if (-not $line) {
        return $defaultValue
    }
    return $line.Substring($line.IndexOf('=') + 1).Trim().Trim('"')
}

if (-not $Source) {
    $Source = Read-EnvValue "BACKUP_HOST_DIR" (Join-Path $projectRoot "backups")
}
if (-not $Target) {
    $Target = Read-EnvValue "BACKUP_OFFSITE_DIR" ""
}
if (-not $Target) {
    throw "Hedef klasör belirtilmedi. -Target kullanın veya .env içine BACKUP_OFFSITE_DIR ekleyin."
}
if (-not (Test-Path $Source -PathType Container)) {
    throw "Yedek kaynak klasörü bulunamadı: $Source"
}
if (-not (Test-Path $Target -PathType Container)) {
    throw "Hedef klasör bulunamadı veya erişilemiyor: $Target"
}

$sourceFull = [System.IO.Path]::GetFullPath($Source).TrimEnd('\')
$targetFull = [System.IO.Path]::GetFullPath($Target).TrimEnd('\')
if ($targetFull.StartsWith($sourceFull, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "Hedef, kaynak yedek klasörünün içinde olamaz."
}

$logDir = Join-Path $projectRoot "logs"
New-Item -ItemType Directory -Force -Path $logDir | Out-Null
$logFile = Join-Path $logDir "backup-offsite.log"

Write-Host "Yedekler kopyalanıyor: $sourceFull -> $targetFull"
$result = robocopy $sourceFull $targetFull *.dump /XO /R:2 /W:2 /NP /LOG+:$logFile
if ($LASTEXITCODE -ge 8) {
    throw "Kopyalama başarısız. Ayrıntı: $logFile"
}

$count = (Get-ChildItem $targetFull -Filter *.dump -File | Measure-Object).Count
Write-Host "Tamamlandı. Hedefte $count yedek dosyası var. Günlük: $logFile"
