param(
    [switch]$SkipDocker
)

$ErrorActionPreference = "Continue"

# Yerel CI: testleri ve Docker imaj derlemesini sırayla çalıştırır.
# Kullanım: powershell -ExecutionPolicy Bypass -File .\scripts\ci-local.ps1
$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location $projectRoot

$logDir = Join-Path $projectRoot "logs"
New-Item -ItemType Directory -Force -Path $logDir | Out-Null
$logFile = Join-Path $logDir "ci-local.log"

function Write-Log($message) {
    $line = "$(Get-Date -Format o) $message"
    Add-Content -Path $logFile -Value $line -Encoding UTF8
    Write-Host $line
}

if (-not $env:JAVA_HOME) {
    $candidates = @(
        "C:\Program Files\Eclipse Adoptium\jdk-21.0.13.9-hotspot",
        "C:\Program Files\Eclipse Adoptium\jdk-21.0.14.7-hotspot"
    )
    foreach ($candidate in $candidates) {
        if (Test-Path (Join-Path $candidate "bin\java.exe")) {
            $env:JAVA_HOME = $candidate
            break
        }
    }
}
if (-not $env:JAVA_HOME -or -not (Test-Path (Join-Path $env:JAVA_HOME "bin\java.exe"))) {
    Write-Log "HATA: Java 21 bulunamadı. JAVA_HOME ayarlayın."
    exit 1
}

Write-Log "Yerel CI başladı. JAVA_HOME=$env:JAVA_HOME"
& java -version 2>&1 | ForEach-Object { Write-Log $_ }

if (-not $SkipDocker) {
    docker info *> $null
    if ($LASTEXITCODE -ne 0) {
        Write-Log "HATA: Docker Desktop çalışmıyor. Testcontainers testleri ve imaj derlemesi için gerekli."
        exit 1
    }
}

Write-Log "Testler ve paketleme başlıyor: mvn -B clean package"
& mvn -B clean package 2>&1 | Tee-Object -FilePath $logFile -Append
if ($LASTEXITCODE -ne 0) {
    Write-Log "CI BAŞARISIZ: testler veya paketleme hata verdi."
    exit 1
}
Write-Log "Testler ve paketleme başarılı."

if (-not $SkipDocker) {
    Write-Log "Docker imajı derleniyor: servisdefteri:ci"
    & docker build -t servisdefteri:ci . 2>&1 | Tee-Object -FilePath $logFile -Append
    if ($LASTEXITCODE -ne 0) {
        Write-Log "CI BAŞARISIZ: Docker imajı derlenemedi."
        exit 1
    }
    Write-Log "Docker imajı başarılı: servisdefteri:ci"
}

Write-Log "CI BAŞARILI."
exit 0
