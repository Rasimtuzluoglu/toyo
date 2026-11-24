param(
    [int]$IntervalMinutes = 15,
    [switch]$NoAutoRestart,
    [string]$TaskName = "ToyotaServisHealthCheck"
)

$ErrorActionPreference = "Stop"

# Sağlık kontrolünü belirli aralıkla çalıştıran Windows görevi oluşturur.
# Kullanım: powershell -ExecutionPolicy Bypass -File .\scripts\install-health-check.ps1

$projectRoot = Split-Path -Parent $PSScriptRoot
$script = Join-Path $projectRoot "scripts\health-check.ps1"
if ($env:TOYOTA_HEALTH_TASK_NAME) {
    $TaskName = $env:TOYOTA_HEALTH_TASK_NAME
}
$taskName = $TaskName

if (-not (Test-Path $script)) {
    throw "Sağlık kontrolü betiği bulunamadı: $script"
}
if ($IntervalMinutes -lt 5) {
    throw "Kontrol aralığı en az 5 dakika olmalıdır."
}

$arguments = "-NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File `"$script`""
if (-not $NoAutoRestart) {
    $arguments += " -AutoRestart"
}

$action = New-ScheduledTaskAction -Execute "powershell.exe" -Argument $arguments
$trigger = New-ScheduledTaskTrigger -Once -At (Get-Date).AddMinutes(1) `
    -RepetitionInterval (New-TimeSpan -Minutes $IntervalMinutes) `
    -RepetitionDuration (New-TimeSpan -Days 3650)
$settings = New-ScheduledTaskSettingsSet -StartWhenAvailable -AllowStartIfOnBatteries `
    -DontStopIfGoingOnBatteries -ExecutionTimeLimit (New-TimeSpan -Minutes 10)

Register-ScheduledTask -TaskName $taskName -Action $action -Trigger $trigger `
    -Settings $settings -Description "Toyota Servis sağlık, yedek ve disk kontrolü" -Force | Out-Null
Write-Host "Görev oluşturuldu: $taskName ($IntervalMinutes dakikada bir)"
Write-Host "Durum dosyası: $projectRoot\updates\health.status"
Write-Host "Günlük: $projectRoot\logs\health-check.log"
