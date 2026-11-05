$ErrorActionPreference = "Stop"

# Host tarafı güncelleme ajanı.
# - updates/update.request dosyasını izler ve update-toyota.ps1 betiğini çalıştırır.
# - Her 10 saniyede heartbeat yazar; Ayarlar ekranı ajanın çalıştığını buradan görür.
# - UPDATE_AUTO=true ise 5 dakikada bir GitHub'ı kontrol edip yeni commit'i otomatik uygular.
#
# Kullanım: powershell -ExecutionPolicy Bypass -File .\scripts\update-agent.ps1
$projectRoot = Split-Path -Parent $PSScriptRoot
$updatesDir = Join-Path $projectRoot "updates"
New-Item -ItemType Directory -Force -Path $updatesDir | Out-Null
$requestFile = Join-Path $updatesDir "update.request"
$agentFile = Join-Path $updatesDir "agent.status"
$logFile = Join-Path $updatesDir "agent.log"
$updateScript = Join-Path $PSScriptRoot "update-toyota.ps1"

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
