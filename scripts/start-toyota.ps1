$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location $projectRoot

Write-Host "Docker kontrol ediliyor..."
docker info *> $null
if ($LASTEXITCODE -ne 0) {
    Write-Error "Docker Desktop calismiyor. Lutfen Docker Desktop'i baslatin."
    exit 1
}

Write-Host "Toyota Plaza Oztoprak servis yonetimi baslatiliyor..."
docker compose up -d
if ($LASTEXITCODE -ne 0) {
    Write-Error "Uygulama baslatilamadi."
    exit 1
}

$deadline = (Get-Date).AddSeconds(120)
$ready = $false
while ((Get-Date) -lt $deadline) {
    try {
        $response = Invoke-WebRequest -UseBasicParsing "http://localhost:18080/actuator/health" -TimeoutSec 3
        if ($response.StatusCode -eq 200) {
            $ready = $true
            break
        }
    } catch {
        Start-Sleep -Seconds 3
    }
}

if (-not $ready) {
    Write-Error "Uygulama zamaninda acilmadi. 'docker compose logs app' ile kontrol edin."
    exit 1
}

Start-Process "http://localhost:18080"
Write-Host "Uygulama hazir: http://localhost:18080"
