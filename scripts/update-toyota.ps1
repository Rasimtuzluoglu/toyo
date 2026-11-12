param(
    [string]$RequestedCommit = ""
)

$ErrorActionPreference = "Continue"

# GitHub'daki son commit'i çeker, Docker imajını yeniden oluşturur ve sağlık kontrolü yapar.
# Uygulama içindeki "Güncelle ve Yeniden Başlat" düğmesi bu betiği host ajanı üzerinden tetikler.
# Kullanım: powershell -ExecutionPolicy Bypass -File .\scripts\update-toyota.ps1

$projectRoot = Split-Path -Parent $PSScriptRoot
$updatesDir = Join-Path $projectRoot "updates"
New-Item -ItemType Directory -Force -Path $updatesDir | Out-Null
$statusFile = Join-Path $updatesDir "update.status"
$logFile = Join-Path $updatesDir "update.log"
$envPath = Join-Path $projectRoot ".env"

$mutex = New-Object System.Threading.Mutex($false, "Global\ToyotaServisUpdate")
if (-not $mutex.WaitOne(0)) {
    Write-Host "Başka bir güncelleme zaten çalışıyor."
    exit 1
}

function Write-Utf8($path, $text) {
    [System.IO.File]::WriteAllText($path, $text, [System.Text.UTF8Encoding]::new($false))
}

function Write-Status($state, $message, $commit, $startedAt) {
    $finishedAt = if ($state -eq "running" -or $state -eq "queued") {
        $null
    } else {
        (Get-Date).ToString("o")
    }
    $payload = [ordered]@{
        state      = $state
        message    = $message
        commit     = $commit
        startedAt  = $startedAt
        finishedAt = $finishedAt
    }
    Write-Utf8 $statusFile ($payload | ConvertTo-Json -Compress)
}

function Write-Log($message) {
    $line = "$(Get-Date -Format o) $message"
    Add-Content -Path $logFile -Value $line -Encoding UTF8
    Write-Host $line
}

$startedAt = (Get-Date).ToString("o")
$previousCommit = ""
$rollbackReady = $false

try {
    Set-Location $projectRoot
    Write-Log "Güncelleme kontrolü başladı. İstenen commit: '$RequestedCommit'"

    # Docker Desktop açılışta hemen hazır olmayabilir; hazır olmadan yedek/build denenirse
    # güncelleme sessizce başarısız olur.
    Write-Log "Docker hazırlığı kontrol ediliyor..."
    $dockerDeadline = (Get-Date).AddMinutes(3)
    while ($true) {
        docker info *> $null
        if ($LASTEXITCODE -eq 0) {
            break
        }
        if ((Get-Date) -ge $dockerDeadline) {
            throw "Docker Desktop hazır değil. Docker'ı başlatıp tekrar deneyin."
        }
        Start-Sleep -Seconds 5
    }

    # Yerel değişiklikler sessizce üretime alınmamalı.
    $dirty = git status --porcelain
    if ($LASTEXITCODE -ne 0) {
        throw "Git durumu okunamadı."
    }
    if ($dirty) {
        throw "Çalışma klasöründe kaydedilmemiş değişiklikler var; güncelleme durduruldu."
    }

    git fetch origin main
    if ($LASTEXITCODE -ne 0) {
        throw "git fetch başarısız."
    }

    $remoteCommit = (git rev-parse origin/main).Trim()

    # İmzasız commit'lerin host'ta çalıştırılması RCE kanalıdır. Varsayılan olarak
    # yalnızca imzalı commit kabul edilir; geliştirme için .env'de
    # UPDATE_REQUIRE_SIGNED=false yapılabilir.
    $requireSigned = $true
    if (Test-Path $envPath) {
        $signedLine = Get-Content $envPath | Where-Object { $_ -match '^UPDATE_REQUIRE_SIGNED=' } | Select-Object -Last 1
        if ($signedLine) {
            $signedValue = $signedLine.Substring($signedLine.IndexOf('=') + 1).Trim().Trim('"')
            if ($signedValue -eq "false") {
                $requireSigned = $false
            }
        }
    }
    if ($requireSigned) {
        git verify-commit $remoteCommit *>> $logFile
        if ($LASTEXITCODE -ne 0) {
            throw "Güvenlik: GitHub'daki commit imzalı değil. Güncelleme yalnızca imzalı commit'lerle yapılabilir. Geliştirme için .env içinde UPDATE_REQUIRE_SIGNED=false ayarlayın."
        }
        Write-Log "Commit imzası doğrulandı: $remoteCommit"
    }

    $deployedFile = Join-Path $updatesDir "deployed-commit"
    $deployedCommit = ""
    if (Test-Path $deployedFile) {
        $deployedCommit = (Get-Content $deployedFile -Raw).Trim()
    }

    if ($RequestedCommit -and $remoteCommit -ne $RequestedCommit) {
        throw "GitHub'daki son commit beklenenden farklı. Sayfayı yenileyip tekrar deneyin."
    }

    if ($deployedCommit -and $remoteCommit.StartsWith($deployedCommit)) {
        Write-Status "success" "Sistem zaten güncel ($($remoteCommit.Substring(0, 7)))." $deployedCommit $startedAt
        Write-Log "Güncelleme gerekmedi; kurulu commit zaten en son sürüm."
        exit 0
    }

    $previousCommit = (git rev-parse HEAD).Trim()
    if ($previousCommit -ne $remoteCommit) {
        Write-Log "Yerel repo güncelleniyor: $previousCommit -> $remoteCommit"
        git pull --ff-only origin main
        if ($LASTEXITCODE -ne 0) {
            throw "git pull başarısız. Yerel değişiklikleri kontrol edin."
        }
    }

    $newCommit = (git rev-parse --short HEAD).Trim()
    $env:GIT_COMMIT = $newCommit

    # APP_PORT .env'den okunur; sabit 18080 kullanmak özel portta yanlış "başarısız" sonucu üretiyordu.
    $appPort = "18080"
    if (Test-Path $envPath) {
        $portLine = Get-Content $envPath | Where-Object { $_ -match '^APP_PORT=' } | Select-Object -Last 1
        if ($portLine) {
            $candidate = $portLine.Substring($portLine.IndexOf('=') + 1).Trim().Trim('"')
            if ($candidate -match '^\d+$') {
                $appPort = $candidate
            }
        }
    }
    $healthUrl = "http://localhost:$appPort/actuator/health"

    $backupName = "pre-update-" + (Get-Date -Format "yyyyMMdd-HHmmss") + ".dump"
    $backupHostDir = "./backups"
    if (Test-Path $envPath) {
        $backupLine = Get-Content $envPath | Where-Object { $_ -match '^BACKUP_HOST_DIR=' } | Select-Object -Last 1
        if ($backupLine) {
            $backupHostDir = $backupLine.Substring($backupLine.IndexOf('=') + 1).Trim().Trim('"')
        }
    }
    if (-not [System.IO.Path]::IsPathRooted($backupHostDir)) {
        $backupHostDir = Join-Path $projectRoot $backupHostDir
    }
    New-Item -ItemType Directory -Force -Path $backupHostDir | Out-Null
    $hostBackupPath = Join-Path $backupHostDir $backupName

    # Yedek, uygulama ayakta olmasa da alınabilmeli: app yerine db konteynerinden dump alınır.
    Write-Log "Güncelleme öncesi veritabanı yedeği alınıyor: $backupName"
    docker compose up -d db *>> $logFile
    if ($LASTEXITCODE -ne 0) {
        throw "Veritabanı konteyneri başlatılamadı; güncelleme durduruldu."
    }
    docker compose exec -T db sh -lc "PGPASSWORD=`$POSTGRES_PASSWORD pg_dump -h 127.0.0.1 -p 5432 -U `$POSTGRES_USER -Fc -f /tmp/$backupName `$POSTGRES_DB" *>> $logFile
    if ($LASTEXITCODE -ne 0) {
        throw "Güncelleme öncesi yedek alınamadı; güncelleme durduruldu."
    }
    docker cp "servisdefteri-db:/tmp/$backupName" $hostBackupPath *>> $logFile
    if ($LASTEXITCODE -ne 0) {
        throw "Güncelleme öncesi yedek seçilen klasöre kopyalanamadı; güncelleme durduruldu."
    }
    docker compose exec -T db rm -f "/tmp/$backupName" *>> $logFile
    if (-not (Test-Path $hostBackupPath)) {
        throw "Güncelleme öncesi yedek dosyası oluşmadı; güncelleme durduruldu."
    }
    Write-Log "Güncelleme öncesi yedek host klasörüne kaydedildi: $hostBackupPath"

    # Sağlık kontrolü başarısız olursa dönülebilmesi için mevcut imajı sakla.
    docker image inspect servisdefteri:1.0.0 *> $null
    if ($LASTEXITCODE -eq 0) {
        docker tag servisdefteri:1.0.0 servisdefteri:previous *>> $logFile
        $rollbackReady = $true
    }

    Write-Status "running" "Docker imajı yeniden oluşturuluyor ($newCommit)..." $newCommit $startedAt
    Write-Log "Docker imajı yeniden oluşturuluyor: $newCommit"

    docker compose up -d --build *>> $logFile
    if ($LASTEXITCODE -ne 0) {
        throw "docker compose başarısız. Ayrıntı: updates/update.log"
    }

    Write-Log "Sağlık kontrolü bekleniyor: $healthUrl"
    $deadline = (Get-Date).AddMinutes(4)
    $healthy = $false
    while ((Get-Date) -lt $deadline) {
        try {
            $response = Invoke-WebRequest -UseBasicParsing $healthUrl -TimeoutSec 4
            if ($response.StatusCode -eq 200) {
                $healthy = $true
                break
            }
        } catch {
            Start-Sleep -Seconds 5
        }
    }
    if (-not $healthy) {
        throw "Uygulama zamanında sağlıklı duruma gelmedi."
    }

    $rollbackReady = $false
    Write-Utf8 $deployedFile $newCommit
    Write-Status "success" "Güncelleme tamamlandı." $newCommit $startedAt
    Write-Log "Güncelleme başarılı: $newCommit"
    exit 0
} catch {
    $message = $_.Exception.Message

    if ($rollbackReady) {
        Write-Log "Güncelleme başarısız; önceki imaja dönülüyor..."
        docker tag servisdefteri:previous servisdefteri:1.0.0 *>> $logFile
        if ($previousCommit) {
            git reset --hard $previousCommit *>> $logFile
        }
        docker compose up -d --force-recreate app *>> $logFile
        if ($LASTEXITCODE -eq 0) {
            $message = "$message Önceki sürüme dönüldü."
        } else {
            $message = "$message Önceki sürüme dönüş de başarısız oldu; updates/update.log dosyasını kontrol edin."
        }
    }

    Write-Status "error" $message "" $startedAt
    Write-Log "Güncelleme hatası: $message"
    exit 1
} finally {
    $mutex.ReleaseMutex()
    $mutex.Dispose()
}
