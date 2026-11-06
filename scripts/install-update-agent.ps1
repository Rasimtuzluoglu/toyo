$ErrorActionPreference = "Stop"

# Güncelleme ajanını Windows Görev Zamanlayıcı'ya ekler ve hemen başlatır.
# Görev, oturum açılışında çalışır ve beklenmedik şekilde kapanırsa yeniden başlatılır.
# Kullanım: powershell -ExecutionPolicy Bypass -File .\scripts\install-update-agent.ps1
$projectRoot = Split-Path -Parent $PSScriptRoot
$agentScript = Join-Path $projectRoot "scripts\update-agent.ps1"
$launcherScript = Join-Path $projectRoot "scripts\update-agent-launcher.vbs"
$taskName = "ToyotaServisUpdateAgent"
$startupFolder = [Environment]::GetFolderPath("Startup")
$legacyShortcut = Join-Path $startupFolder "ToyotaServisUpdateAgent.cmd"

if (-not (Test-Path $agentScript)) {
    Write-Error "Güncelleme ajanı bulunamadı: $agentScript"
    exit 1
}

if (-not (Test-Path $launcherScript)) {
    Write-Error "Güncelleme ajanı başlatıcısı bulunamadı: $launcherScript"
    exit 1
}

if (Test-Path $legacyShortcut) {
    Remove-Item $legacyShortcut -Force
    Write-Host "Eski Startup kısayolu kaldırıldı."
}

$action = New-ScheduledTaskAction -Execute "wscript.exe" `
    -Argument "//B //Nologo `"$launcherScript`""
$trigger = New-ScheduledTaskTrigger -AtLogOn -User "$env:USERDOMAIN\$env:USERNAME"
$settings = New-ScheduledTaskSettingsSet `
    -AllowStartIfOnBatteries `
    -DontStopIfGoingOnBatteries `
    -DontStopOnIdleEnd `
    -StartWhenAvailable `
    -RestartCount 999 `
    -RestartInterval (New-TimeSpan -Minutes 1) `
    -ExecutionTimeLimit (New-TimeSpan -Days 3650)

try {
    Register-ScheduledTask -TaskName $taskName -Action $action -Trigger $trigger `
        -Settings $settings `
        -Description "Toyota servis yönetimi GitHub güncelleme ajanı" -Force | Out-Null
    Start-ScheduledTask -TaskName $taskName
    Write-Host "Güncelleme ajanı Görev Zamanlayıcı'ya eklendi ve başlatıldı: $taskName"
    Write-Host "Ajan beklenmedik şekilde kapanırsa Windows 1 dakika içinde yeniden başlatır."
} catch {
    Write-Warning "Görev Zamanlayıcı kaydı yapılamadı: $($_.Exception.Message)"
    Write-Host "Startup klasörü yöntemine geri dönülüyor."

    $content = @"
@echo off
start "" /b wscript.exe //B //Nologo "$launcherScript"
"@
    Set-Content -Path $legacyShortcut -Value $content -Encoding ASCII
    Start-Process -FilePath "wscript.exe" `
        -ArgumentList "//B", "//Nologo", $launcherScript `
        -WindowStyle Hidden
    Write-Host "Ajan Startup klasöründen başlatıldı. Otomatik yeniden başlatma yok."
}

Write-Host ""
Write-Host "Ayarlar ekranında 'Ajan hazır' görünmelidir."
Write-Host "Otomatik güncelleme için: UPDATE_AUTO=true ortam değişkenini tanımlayıp ajanı yeniden başlatın."
Write-Host "Docker Desktop'ın da açılışta başlaması gerekir."
