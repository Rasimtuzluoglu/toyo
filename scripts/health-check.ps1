param(
    [switch]$AutoRestart
)

$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location $projectRoot
$envPath = Join-Path $projectRoot ".env"
$updatesDir = Join-Path $projectRoot "updates"
$logsDir = Join-Path $projectRoot "logs"
New-Item -ItemType Directory -Force -Path $updatesDir, $logsDir | Out-Null
$statusFile = Join-Path $updatesDir "health.status"
$logFile = Join-Path $logsDir "health-check.log"

function Write-Log($message) {
    Add-Content -Path $logFile -Value "$(Get-Date -Format o) $message" -Encoding UTF8
}

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

function Get-RunningContainer($candidates) {
    foreach ($candidate in $candidates) {
        $found = docker ps --filter "name=^/$candidate$" --format "{{.Names}}" 2>$null
        if ($found -eq $candidate) {
            return $candidate
        }
    }
    return $null
}

$appPort = Read-EnvValue "APP_PORT" "18080"
$appContainer = Get-RunningContainer @("servisdefteri-app", "toyota-servis-app")
$dbContainer = Get-RunningContainer @("servisdefteri-db", "toyota-servis-db")
$health = $false
$backupAgeHours = $null
$offsiteAgeHours = $null
$offsiteOk = $null
$freeGb = $null
$message = ""

if ($appContainer) {
    try {
        $response = Invoke-WebRequest -UseBasicParsing "http://127.0.0.1:$appPort/actuator/health" -TimeoutSec 5
        $health = $response.StatusCode -eq 200
    } catch {
        $message = "Sağlık uç noktası yanıt vermedi: $($_.Exception.Message)"
    }
} else {
    $message = "Çalışan uygulama konteyneri bulunamadı."
}

if (-not $health -and $AutoRestart) {
    $composeFile = Join-Path $projectRoot "docker-compose.yml"
    if (-not (Test-Path $composeFile)) {
        $composeFile = Join-Path $projectRoot "docker-compose.dist.yml"
    }
    Write-Log "Sağlık başarısız; servisler yeniden başlatılıyor."
    if (Test-Path $composeFile) {
        docker compose --project-directory $projectRoot --env-file $envPath -f $composeFile up -d *>> $logFile
    } else {
        Write-Log "Compose dosyası bulunamadı; yeniden başlatma atlandı."
    }
    Start-Sleep -Seconds 15
    try {
        $response = Invoke-WebRequest -UseBasicParsing "http://127.0.0.1:$appPort/actuator/health" -TimeoutSec 5
        $health = $response.StatusCode -eq 200
        $message = if ($health) { "Yeniden başlatma sonrası sağlık düzeldi." } else { $message }
    } catch {
        $message = "Yeniden başlatma sonrası hâlâ sağlıksız: $($_.Exception.Message)"
    }
}

if ($dbContainer) {
    try {
        $dbUser = Read-EnvValue "POSTGRES_USER" "servisdefteri"
        $dbName = Read-EnvValue "POSTGRES_DB" "servisdefteri"
        $backupText = docker exec $dbContainer psql -U $dbUser -d $dbName -t -A -c `
            "select coalesce(max(started_at)::text, '') from backup_runs where status = 'BASARILI';"
        if ($LASTEXITCODE -eq 0 -and $backupText.Trim()) {
            $backupAt = [datetime]::Parse($backupText.Trim(), [System.Globalization.CultureInfo]::InvariantCulture)
            $backupAgeHours = [math]::Round(((Get-Date) - $backupAt).TotalHours, 1)
        }
    } catch {
        $message = "$message Yedek yaşı okunamadı."
    }
}

try {
    $freeGb = [math]::Round((Get-PSDrive C).Free / 1GB, 1)
} catch {
    $message = "$message Disk alanı okunamadı."
}

$backupDirectory = Read-EnvValue "BACKUP_HOST_DIR" (Join-Path $projectRoot "backups")
if (-not [System.IO.Path]::IsPathRooted($backupDirectory)) {
    $backupDirectory = Join-Path $projectRoot $backupDirectory
}
$offsiteDirectory = Read-EnvValue "BACKUP_OFFSITE_DIR" ""
if ($offsiteDirectory) {
    try {
        if (-not (Test-Path $offsiteDirectory -PathType Container)) {
            throw "Hedef klasör yok: $offsiteDirectory"
        }
        robocopy $backupDirectory $offsiteDirectory *.dump /XO /R:2 /W:2 /NP /LOG+:$logFile | Out-Null
        if ($LASTEXITCODE -ge 8) {
            throw "Kopyalama hatası (robocopy kodu $LASTEXITCODE)"
        }
        $latestOffsite = Get-ChildItem $offsiteDirectory -Filter *.dump -File -ErrorAction SilentlyContinue |
            Sort-Object LastWriteTime -Descending | Select-Object -First 1
        if ($latestOffsite) {
            $offsiteAgeHours = [math]::Round(((Get-Date) - $latestOffsite.LastWriteTime).TotalHours, 1)
            $offsiteOk = $offsiteAgeHours -le 48
        } else {
            $offsiteOk = $false
            $message = "$message Offsite klasöründe yedek yok."
        }
    } catch {
        $offsiteOk = $false
        $message = "$message Offsite yedek hatası: $($_.Exception.Message)"
    }
}

$backupOk = $backupAgeHours -ne $null -and $backupAgeHours -le 36
$diskOk = $freeGb -ne $null -and $freeGb -ge 5
$ok = $health -and $backupOk -and $diskOk -and ($offsiteOk -ne $false)

$status = [ordered]@{
    timestamp        = (Get-Date).ToString("o")
    health           = $health
    appContainer     = $appContainer
    backupAgeHours   = $backupAgeHours
    backupOk         = $backupOk
    offsiteDirectory = $offsiteDirectory
    offsiteOk        = $offsiteOk
    offsiteAgeHours  = $offsiteAgeHours
    freeGb           = $freeGb
    diskOk           = $diskOk
    ok               = $ok
    message          = $message.Trim()
}
[System.IO.File]::WriteAllText($statusFile, ($status | ConvertTo-Json -Compress),
    [System.Text.UTF8Encoding]::new($false))

Write-Log ("health={0} backupAgeHours={1} offsiteOk={2} freeGb={3} ok={4} {5}" -f `
    $health, $backupAgeHours, $offsiteOk, $freeGb, $ok, $message)

if (-not $ok) {
    try {
        msg.exe * "Toyota Servis sağlık uyarısı: health=$health, yedek=${backupAgeHours}h, disk=${freeGb}GB. Ayrıntı: $logFile" 2>$null | Out-Null
    } catch {
        # msg.exe her Windows sürümünde yok; günlük ve health.status yeterli.
    }
    exit 1
}
