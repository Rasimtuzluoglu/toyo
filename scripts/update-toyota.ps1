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

$mutex = New-Object System.Threading.Mutex($false, "Global\ToyotaServisUpdate")
if (-not $mutex.WaitOne(0)) {
    Write-Host "Başka bir güncelleme zaten çalışıyor."
    exit 1
}

function Write-Utf8($path, $text) {
    [System.IO.File]::WriteAllText($path, $text, [System.Text.UTF8Encoding]::new($false))
}

function Write-Status($state, $message, $commit, $startedAt) {
    $payload = [ordered]@{
        state      = $state
        message    = $message
        commit     = $commit
        startedAt  = $startedAt
        finishedAt = (Get-Date).ToString("o")
    }
    Write-Utf8 $statusFile ($payload | ConvertTo-Json -Compress)
}

function Write-Log($message) {
    $line = "$(Get-Date -Format o) $message"
    Add-Content -Path $logFile -Value $line -Encoding UTF8
    Write-Host $line
}

$startedAt = (Get-Date).ToString("o")

try {
    Set-Location $projectRoot
    Write-Log "Güncelleme kontrolü başladı. İstenen commit: '$RequestedCommit'"

    git fetch origin main
    if ($LASTEXITCODE -ne 0) {
        throw "git fetch başarısız."
    }

    $remoteCommit = (git rev-parse origin/main).Trim()
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

    $localCommit = (git rev-parse HEAD).Trim()
    if ($localCommit -ne $remoteCommit) {
        Write-Log "Yerel repo güncelleniyor: $localCommit -> $remoteCommit"
        git pull --ff-only origin main
        if ($LASTEXITCODE -ne 0) {
            throw "git pull başarısız. Yerel değişiklikleri kontrol edin."
        }
    }

    $newCommit = (git rev-parse --short HEAD).Trim()
    $env:GIT_COMMIT = $newCommit
    Write-Status "running" "Docker imajı yeniden oluşturuluyor ($newCommit)..." $newCommit $startedAt
    Write-Log "Docker imajı yeniden oluşturuluyor: $newCommit"

    docker compose up -d --build *>> $logFile
    if ($LASTEXITCODE -ne 0) {
        throw "docker compose başarısız. Ayrıntı: updates/update.log"
    }

    Write-Log "Sağlık kontrolü bekleniyor..."
    $deadline = (Get-Date).AddMinutes(4)
    $healthy = $false
    while ((Get-Date) -lt $deadline) {
        try {
            $response = Invoke-WebRequest -UseBasicParsing "http://localhost:18080/actuator/health" -TimeoutSec 4
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

    Write-Utf8 $deployedFile $newCommit
    Write-Status "success" "Güncelleme tamamlandı ($newCommit)." $newCommit $startedAt
    Write-Log "Güncelleme başarılı: $newCommit"
    exit 0
} catch {
    $message = $_.Exception.Message
    Write-Status "error" $message "" $startedAt
    Write-Log "Güncelleme hatası: $message"
    exit 1
} finally {
    $mutex.ReleaseMutex()
    $mutex.Dispose()
}
