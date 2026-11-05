$ErrorActionPreference = "Stop"

# Güncelleme ajanını Windows açılışına ekler ve hemen başlatır.
# Kullanım: powershell -ExecutionPolicy Bypass -File .\scripts\install-update-agent.ps1
$projectRoot = Split-Path -Parent $PSScriptRoot
$agentScript = Join-Path $projectRoot "scripts\update-agent.ps1"
$startupFolder = [Environment]::GetFolderPath("Startup")
$shortcutFile = Join-Path $startupFolder "ToyotaServisUpdateAgent.cmd"

if (-not (Test-Path $agentScript)) {
    Write-Error "Güncelleme ajanı bulunamadı: $agentScript"
    exit 1
}

$content = @"
@echo off
powershell -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File "$agentScript"
"@

Set-Content -Path $shortcutFile -Value $content -Encoding ASCII
Write-Host "Güncelleme ajanı açılışa eklendi:"
Write-Host $shortcutFile

# Ajan zaten çalışıyorsa ikinci kopya açılmaz; kendi mutex'i bunu engeller.
Start-Process -FilePath "powershell.exe" `
    -ArgumentList "-NoProfile", "-ExecutionPolicy", "Bypass", "-WindowStyle", "Hidden", "-File", $agentScript `
    -WindowStyle Hidden

Write-Host ""
Write-Host "Ajan şimdi başlatıldı. Ayarlar ekranında 'Ajan hazır' görünmelidir."
Write-Host "Otomatik güncelleme için: UPDATE_AUTO=true ortam değişkenini tanımlayıp ajanı yeniden başlatın."
Write-Host "Docker Desktop'ın da açılışta başlaması gerekir."
