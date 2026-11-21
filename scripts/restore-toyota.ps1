param(
    [Parameter(Mandatory = $true)]
    [string]$BackupFile,
    [string]$RelativeDirectory = "",
    [string]$RequestedBy = "patron"
)

$ErrorActionPreference = "Stop"

# Host tarafı geri yükleme betiği. Uygulama durdurulur, güvenlik yedeği alınır,
# seçilen dump ayrı bir veritabanına değil mevcut veritabanına geri yüklenir.
# Hata durumunda güvenlik yedeğine otomatik dönülür.
# Doğrudan çalıştırmak yerine update-agent.ps1 üzerinden tetiklenmesi önerilir.

$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location $projectRoot
$updatesDir = Join-Path $projectRoot "updates"
New-Item -ItemType Directory -Force -Path $updatesDir | Out-Null
$statusFile = Join-Path $updatesDir "restore.status"
$logFile = Join-Path $updatesDir "restore.log"
$envPath = Join-Path $projectRoot ".env"

function Write-Utf8($path, $text) {
    [System.IO.File]::WriteAllText($path, $text, [System.Text.UTF8Encoding]::new($false))
}

function Write-Status($state, $message, $startedAt) {
    $finishedAt = if ($state -eq "running") { $null } else { (Get-Date).ToString("o") }
    $payload = [ordered]@{
        state      = $state
        message    = $message
        backupFile = $BackupFile
        requestedBy = $RequestedBy
        startedAt  = $startedAt
        finishedAt = $finishedAt
    }
    Write-Utf8 $statusFile ($payload | ConvertTo-Json -Compress)
}

function Write-Log($message) {
    Add-Content -Path $logFile -Value "$(Get-Date -Format o) $message" -Encoding UTF8
    Write-Host $message
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

function Invoke-Compose($composeFile, $arguments) {
    & docker compose --env-file .env -f $composeFile @arguments
}

function Wait-Db($container, $dbUser) {
    $deadline = (Get-Date).AddMinutes(2)
    while ((Get-Date) -lt $deadline) {
        docker exec $container pg_isready -U $dbUser *> $null
        if ($LASTEXITCODE -eq 0) {
            return
        }
        Start-Sleep -Seconds 3
    }
    throw "Veritabanı hazır olmadı."
}

function Restore-Dump($container, $dbUser, $dbName, $dumpInsideContainer) {
    $terminateSql = "select pg_terminate_backend(pid) from pg_stat_activity where datname = '" + $dbName + "' and pid <> pg_backend_pid();"
    $dropSql = 'drop database if exists "' + $dbName + '";'
    $createSql = 'create database "' + $dbName + '";'
    docker exec $container psql -U $dbUser -d postgres -v ON_ERROR_STOP=1 -c $terminateSql *>> $logFile
    if ($LASTEXITCODE -ne 0) { throw "Veritabanı bağlantıları sonlandırılamadı." }
    docker exec $container psql -U $dbUser -d postgres -v ON_ERROR_STOP=1 -c $dropSql *>> $logFile
    if ($LASTEXITCODE -ne 0) { throw "Veritabanı silinemedi." }
    docker exec $container psql -U $dbUser -d postgres -v ON_ERROR_STOP=1 -c $createSql *>> $logFile
    if ($LASTEXITCODE -ne 0) { throw "Veritabanı oluşturulamadı." }
    docker exec $container pg_restore -U $dbUser -d $dbName --no-owner --no-privileges --single-transaction --exit-on-error $dumpInsideContainer *>> $logFile
    if ($LASTEXITCODE -ne 0) { throw "pg_restore başarısız (çıkış kodu: $LASTEXITCODE)." }
}

function Wait-App($appPort) {
    $deadline = (Get-Date).AddMinutes(4)
    while ((Get-Date) -lt $deadline) {
        try {
            $response = Invoke-WebRequest -UseBasicParsing "http://localhost:$appPort/actuator/health" -TimeoutSec 5
            if ($response.StatusCode -eq 200) {
                return
            }
        } catch {
            Start-Sleep -Seconds 5
        }
    }
    throw "Uygulama zamanında sağlıklı duruma gelmedi."
}

$startedAt = (Get-Date).ToString("o")
$preRestoreName = "pre-restore-" + (Get-Date -Format "yyyyMMdd-HHmmss") + ".dump"
$preRestoreHostPath = $null
$dbTouched = $false
$appStopped = $false

try {
    $composeFile = if (Test-Path (Join-Path $projectRoot "docker-compose.yml")) {
        "docker-compose.yml"
    } elseif (Test-Path (Join-Path $projectRoot "docker-compose.dist.yml")) {
        "docker-compose.dist.yml"
    } else {
        throw "docker-compose dosyası bulunamadı."
    }

    $dbUser = Read-EnvValue "POSTGRES_USER" "servisdefteri"
    $dbName = Read-EnvValue "POSTGRES_DB" "servisdefteri"
    $dbPassword = Read-EnvValue "POSTGRES_PASSWORD" ""
    $appPort = Read-EnvValue "APP_PORT" "18080"
    if ($dbUser -notmatch '^[A-Za-z0-9_]+$' -or $dbName -notmatch '^[A-Za-z0-9_]+$') {
        throw "Veritabanı adı/kullanıcısı geçersiz."
    }

    $backupRoot = Read-EnvValue "BACKUP_HOST_DIR" "./backups"
    if (-not [System.IO.Path]::IsPathRooted($backupRoot)) {
        $backupRoot = Join-Path $projectRoot $backupRoot
    }
    $backupRoot = [System.IO.Path]::GetFullPath($backupRoot)

    $relative = ($RelativeDirectory -replace '\\', '/').Trim('/')
    if ($relative -match '(^|/)\.\.(/|$)' -or $relative -match '^[A-Za-z]:') {
        throw "Yedek alt klasörü geçersiz."
    }
    $backupDir = if ($relative) { Join-Path $backupRoot $relative } else { $backupRoot }
    $backupDir = [System.IO.Path]::GetFullPath($backupDir)
    if (-not $backupDir.StartsWith($backupRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Yedek yolu kök yedek klasörünün dışında."
    }
    if ($BackupFile -notmatch '^(servisdefteri|pre-update)-[A-Za-z0-9._-]+\.dump$') {
        throw "Yedek dosya adı geçersiz."
    }
    $sourceFile = Join-Path $backupDir $BackupFile
    if (-not (Test-Path $sourceFile -PathType Leaf)) {
        throw "Yedek dosyası bulunamadı: $sourceFile"
    }
    if ((Get-Item $sourceFile).Length -eq 0) {
        throw "Yedek dosyası boş."
    }

    Write-Status "running" "Yedek geri yükleniyor; uygulama kısa süre erişilemez." $startedAt
    Write-Log "Geri yükleme başladı: $BackupFile (isteyen: $RequestedBy)"

    Invoke-Compose $composeFile @("up", "-d", "db") *>> $logFile
    if ($LASTEXITCODE -ne 0) { throw "Veritabanı konteyneri başlatılamadı." }
    $dbContainer = (& docker compose --env-file .env -f $composeFile ps -q db).Trim()
    if (-not $dbContainer) { throw "Veritabanı konteyneri bulunamadı." }
    Wait-Db $dbContainer $dbUser

    Write-Log "Güvenlik yedeği alınıyor: $preRestoreName"
    docker exec $dbContainer sh -lc "PGPASSWORD=`$POSTGRES_PASSWORD pg_dump -h 127.0.0.1 -p 5432 -U `$POSTGRES_USER -Fc -f /tmp/$preRestoreName `$POSTGRES_DB" *>> $logFile
    if ($LASTEXITCODE -ne 0) { throw "Güvenlik yedeği alınamadı; geri yükleme durduruldu." }
    $preRestoreHostPath = Join-Path $backupRoot $preRestoreName
    docker cp "${dbContainer}:/tmp/$preRestoreName" $preRestoreHostPath *>> $logFile
    if ($LASTEXITCODE -ne 0) { throw "Güvenlik yedeği host klasörüne kopyalanamadı." }
    docker exec $dbContainer rm -f "/tmp/$preRestoreName" *>> $logFile

    Write-Log "Uygulama durduruluyor..."
    Invoke-Compose $composeFile @("stop", "app") *>> $logFile
    $appStopped = $true

    docker cp $sourceFile "${dbContainer}:/tmp/restore-target.dump" *>> $logFile
    if ($LASTEXITCODE -ne 0) { throw "Yedek veritabanı konteynerine kopyalanamadı." }

    Write-Log "Veritabanı geri yükleniyor..."
    $dbTouched = $true
    Restore-Dump $dbContainer $dbUser $dbName "/tmp/restore-target.dump"

    Invoke-Compose $composeFile @("up", "-d", "--force-recreate", "app") *>> $logFile
    if ($LASTEXITCODE -ne 0) { throw "Uygulama yeniden başlatılamadı." }
    Wait-App $appPort

    docker exec $dbContainer rm -f "/tmp/restore-target.dump" *>> $logFile
    Write-Status "success" "Yedek başarıyla geri yüklendi: $BackupFile" $startedAt
    Write-Log "Geri yükleme tamamlandı: $BackupFile"
    exit 0
} catch {
    $message = $_.Exception.Message
    Write-Log "Geri yükleme hatası: $message"

    if ($dbTouched -and $preRestoreHostPath -and (Test-Path $preRestoreHostPath)) {
        try {
            Write-Log "Güvenlik yedeğine dönülüyor..."
            if (-not $dbContainer) {
                $dbContainer = (& docker compose --env-file .env -f $composeFile ps -q db).Trim()
            }
            docker cp $preRestoreHostPath "${dbContainer}:/tmp/pre-restore-rollback.dump" *>> $logFile
            Restore-Dump $dbContainer $dbUser $dbName "/tmp/pre-restore-rollback.dump"
            Invoke-Compose $composeFile @("up", "-d", "--force-recreate", "app") *>> $logFile
            Wait-App $appPort
            $message = "$message Güvenlik yedeğine geri dönüldü."
        } catch {
            $message = "$message Güvenlik yedeğine dönüş de başarısız: $($_.Exception.Message)"
        }
    } elseif ($appStopped) {
        try {
            Invoke-Compose $composeFile @("up", "-d", "--force-recreate", "app") *>> $logFile
            Wait-App $appPort
        } catch {
            $message = "$message Uygulama yeniden başlatılamadı: $($_.Exception.Message)"
        }
    }

    Write-Status "error" $message $startedAt
    exit 1
}
