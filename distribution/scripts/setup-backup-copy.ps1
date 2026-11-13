param(
    [string]$DriveLetter = ""
)

$ErrorActionPreference = "Stop"

# Yedeklerin ikinci kopyası için harici disk/NAS klasörünü yapılandırır.
# Şifreleme YAPMAZ; diski fiziksel olarak güvenli tutun.
# Kullanım: powershell -ExecutionPolicy Bypass -File .\scripts\setup-backup-copy.ps1 -DriveLetter E

$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location $projectRoot
$envPath = Join-Path $projectRoot ".env"
if (-not (Test-Path $envPath)) {
    throw ".env bulunamadı."
}

$candidates = Get-Volume | Where-Object {
    $_.DriveLetter -and $_.DriveLetter -ne "C" -and $_.FileSystem -eq "NTFS" -and $_.DriveType -in @("Removable", "Fixed")
}

if ([string]::IsNullOrWhiteSpace($DriveLetter)) {
    if (-not $candidates) {
        Write-Host "C: dışında NTFS bir disk bulunamadı. Harici diski takıp tekrar çalıştırın."
        exit 1
    }
    Write-Host "Kullanılabilir diskler:"
    $candidates | Select-Object DriveLetter, FileSystemLabel, DriveType,
        @{n = 'FreeGB'; e = { [math]::Round($_.SizeRemaining / 1GB, 1) } } | Format-Table -AutoSize
    $DriveLetter = (Read-Host "İkinci kopya için sürücü harfi (ör. E)").Trim().TrimEnd(':')
}

$letter = $DriveLetter.ToUpper()
$volume = Get-Volume -DriveLetter $letter -ErrorAction SilentlyContinue
if (-not $volume) {
    throw "Sürücü bulunamadı: ${letter}:"
}
if ($volume.FileSystem -ne "NTFS") {
    throw "${letter}: NTFS değil. NTFS bir disk kullanın."
}
if ($letter -eq "C") {
    throw "C: diski ikinci kopya için kullanılamaz; harici disk veya NAS seçin."
}

$hostDir = "${letter}:\ToyotaServis-Yedekler"
New-Item -ItemType Directory -Force -Path $hostDir | Out-Null

$lines = Get-Content $envPath
$hostUpdated = $false
$containerUpdated = $false
for ($i = 0; $i -lt $lines.Count; $i++) {
    if ($lines[$i] -match '^BACKUP_COPY_HOST_DIR=') {
        $lines[$i] = "BACKUP_COPY_HOST_DIR=$($hostDir.Replace('\', '/'))"
        $hostUpdated = $true
    }
    if ($lines[$i] -match '^BACKUP_COPY_DIR=') {
        $lines[$i] = "BACKUP_COPY_DIR=/app/backup-copy"
        $containerUpdated = $true
    }
}
if (-not $hostUpdated) {
    $lines += "BACKUP_COPY_HOST_DIR=$($hostDir.Replace('\', '/'))"
}
if (-not $containerUpdated) {
    $lines += "BACKUP_COPY_DIR=/app/backup-copy"
}
[System.IO.File]::WriteAllLines($envPath, $lines, [System.Text.UTF8Encoding]::new($false))

Write-Host "Mevcut yedekler ikinci kopyaya aktarılıyor..."
robocopy (Join-Path $projectRoot "backups") $hostDir *.dump /XO /R:1 /W:1 | Out-Null

Write-Host "Uygulama yeni yedek klasörüyle yeniden başlatılıyor..."
docker compose up -d --force-recreate app | Out-Null
if ($LASTEXITCODE -ne 0) {
    throw "Uygulama yeniden başlatılamadı."
}

$portLine = Get-Content $envPath | Where-Object { $_ -match '^APP_PORT=' } | Select-Object -Last 1
$appPort = if ($portLine) { $portLine.Substring($portLine.IndexOf('=') + 1).Trim() } else { "18080" }
$deadline = (Get-Date).AddMinutes(3)
do {
    try {
        $response = Invoke-WebRequest -UseBasicParsing "http://localhost:$appPort/actuator/health" -TimeoutSec 5
        if ($response.StatusCode -eq 200) {
            Write-Host "Tamamlandı. İkinci kopya klasörü: $hostDir" -ForegroundColor Green
            Write-Host "Disk takılı ve erişilebilir olduğu sürece her yedek buraya da yazılır." -ForegroundColor Green
            exit 0
        }
    } catch {
        Start-Sleep -Seconds 5
    }
} while ((Get-Date) -lt $deadline)

throw "Uygulama zamanında sağlıklı duruma gelmedi."
