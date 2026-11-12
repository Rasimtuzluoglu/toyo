$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location $projectRoot

$envPath = Join-Path $projectRoot ".env"
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
$appUrl = "http://localhost:$appPort"

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
        $response = Invoke-WebRequest -UseBasicParsing "$appUrl/actuator/health" -TimeoutSec 3
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

Start-Process $appUrl
Write-Host "Uygulama hazir: $appUrl"
