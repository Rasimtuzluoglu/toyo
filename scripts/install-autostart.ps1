$ErrorActionPreference = "Stop"

# Windows acilisinda uygulamayi baslatan kisa yol olusturur.
# Kullanim: powershell -ExecutionPolicy Bypass -File .\scripts\install-autostart.ps1
$projectRoot = Split-Path -Parent $PSScriptRoot
$startScript = Join-Path $projectRoot "scripts\start-toyota.ps1"
$startupFolder = [Environment]::GetFolderPath("Startup")
$shortcutFile = Join-Path $startupFolder "ToyotaServisYonetimi.cmd"

$content = @"
@echo off
powershell -NoProfile -ExecutionPolicy Bypass -WindowStyle Minimized -File "$startScript"
"@

Set-Content -Path $shortcutFile -Value $content -Encoding ASCII
Write-Host "Otomatik baslatma kisa yolu olusturuldu:"
Write-Host $shortcutFile
Write-Host ""
Write-Host "Not: Docker Desktop'in de acilista baslamasi gerekir."
Write-Host "Docker Desktop -> Settings -> General -> 'Start Docker Desktop when you sign in' secenegini acin."
