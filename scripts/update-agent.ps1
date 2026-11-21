$ErrorActionPreference = "Stop"

# Host tarafı güncelleme ve geri yükleme ajanı.
# - updates/update.request dosyasını izler ve update-toyota.ps1 betiğini çalıştırır.
# - updates/restore.request dosyasını izler ve restore-toyota.ps1 betiğini çalıştırır.
# - Her 10 saniyede heartbeat yazar; Ayarlar ekranı ajanın çalıştığını buradan görür.
# - UPDATE_AUTO=true ise 5 dakikada bir GitHub'ı kontrol edip yeni commit'i otomatik uygular.
#
# Kullanım: powershell -ExecutionPolicy Bypass -File .\scripts\update-agent.ps1
$projectRoot = Split-Path -Parent $PSScriptRoot
$updatesDir = Join-Path $projectRoot "updates"
New-Item -ItemType Directory -Force -Path $updatesDir | Out-Null
$requestFile = Join-Path $updatesDir "update.request"
$restoreRequestFile = Join-Path $updatesDir "restore.request"
$agentFile = Join-Path $updatesDir "agent.status"
$logFile = Join-Path $updatesDir "agent.log"
$updateScript = Join-Path $PSScriptRoot "update-toyota.ps1"
$restoreScript = Join-Path $PSScriptRoot "restore-toyota.ps1"

$mutex = New-Object System.Threading.Mutex($false, "Global\ToyotaServisUpdateAgent")
if (-not $mutex.WaitOne(0)) {
    Write-Host "Güncelleme ajanı zaten çalışıyor."
    exit 0
}

$auto = $env:UPDATE_AUTO -and ($env:UPDATE_AUTO.ToLower() -eq "true" -or $env:UPDATE_AUTO -eq "1")
$lastAutoCheck = [DateTime]::MinValue

function Write-Utf8($path, $text) {
    [System.IO.File]::WriteAllText($path, $text, [System.Text.UTF8Encoding]::new($false))
}

function Write-Heartbeat {
    $payload = [ordered]@{
        heartbeat = (Get-Date).ToUniversalTime().ToString("o")
        auto      = $auto
        pid       = $PID
    }
    Write-Utf8 $agentFile ($payload | ConvertTo-Json -Compress)
}

function Write-Log($message) {
    Add-Content -Path $logFile -Value "$(Get-Date -Format o) $message" -Encoding UTF8
}

Write-Log "Güncelleme ajanı başladı. Otomatik: $auto"

try {
    while ($true) {
        Write-Heartbeat

        if (Test-Path $restoreRequestFile) {
            try {
                $request = Get-Content $restoreRequestFile -Raw | ConvertFrom-Json
                Remove-Item $restoreRequestFile -Force
                if (-not $request.backupFile) {
                    throw "Geri yükleme isteğinde yedek dosya adı yok."
                }
                if ($request.requestedAt) {
                    $rawRequestedAt = ([string]$request.requestedAt) -replace '\.\d+$', ''
                    $requestedAt = [datetime]::Parse($rawRequestedAt,
                        [System.Globalization.CultureInfo]::InvariantCulture)
                    if ($requestedAt -lt (Get-Date).AddMinutes(-10)) {
                        throw "Geri yükleme isteği zaman aşımına uğradı; yeniden deneyin."
                    }
                }
                Write-Log "Geri yükleme isteği alındı: $($request.backupFile)"
                $restoreArgs = @{
                    BackupFile        = [string]$request.backupFile
                    RelativeDirectory = [string]$request.relativeDirectory
                    RequestedBy       = [string]$request.requestedBy
                }
                & $restoreScript @restoreArgs
                if ($LASTEXITCODE -ne 0) {
                    Write-Log "Geri yükleme betiği hata koduyla bitti: $LASTEXITCODE"
                }
            } catch {
                Write-Log "Geri yükleme isteği işlenemedi: $($_.Exception.Message)"
                $status = [ordered]@{
                    state      = "error"
                    message    = "Geri yükleme isteği işlenemedi: $($_.Exception.Message)"
                    startedAt  = (Get-Date).ToString("o")
                    finishedAt = (Get-Date).ToString("o")
                }
                Write-Utf8 (Join-Path $updatesDir "restore.status") ($status | ConvertTo-Json -Compress)
            }
            continue
        }

        if (Test-Path $requestFile) {
            try {
                $request = Get-Content $requestFile -Raw | ConvertFrom-Json
                Remove-Item $requestFile -Force
                Write-Log "Güncelleme isteği alındı: $($request.targetCommit)"
                & $updateScript -RequestedCommit $request.targetCommit
            } catch {
                Write-Log "İstek işlenemedi: $($_.Exception.Message)"
            }
        }

        if ($auto -and ((Get-Date) - $lastAutoCheck).TotalMinutes -ge 5) {
            $lastAutoCheck = Get-Date
            Write-Log "Otomatik güncelleme kontrolü."
            & $updateScript
        }

        Start-Sleep -Seconds 10
    }
} finally {
    $mutex.ReleaseMutex()
    $mutex.Dispose()
}
