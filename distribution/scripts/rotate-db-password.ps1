param(
    [string]$NewPassword = ""
)

$ErrorActionPreference = "Stop"

# Veritabanı rol parolasını değiştirir, .env dosyasını günceller ve app'i yeniden başlatır.
# Önce güncelleme öncesi yedek almanız önerilir.
# Kullanım: powershell -ExecutionPolicy Bypass -File .\scripts\rotate-db-password.ps1

$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location $projectRoot
$envPath = Join-Path $projectRoot ".env"
if (-not (Test-Path $envPath)) {
    throw ".env bulunamadı."
}

function Read-EnvValue($key, $defaultValue) {
    $line = Get-Content $envPath | Where-Object { $_ -match "^$key=" } | Select-Object -Last 1
    if (-not $line) {
        return $defaultValue
    }
    return $line.Substring($line.IndexOf('=') + 1).Trim().Trim('"')
}

$dbUser = Read-EnvValue "POSTGRES_USER" "servisdefteri"
$appPort = Read-EnvValue "APP_PORT" "18080"
$oldPassword = Read-EnvValue "POSTGRES_PASSWORD" ""

if ([string]::IsNullOrWhiteSpace($NewPassword)) {
    $chars = (48..57) + (65..90) + (97..122)
    $NewPassword = -join (1..28 | ForEach-Object { [char]($chars | Get-Random) })
}

$backupPath = "$envPath.bak-" + (Get-Date -Format "yyyyMMdd-HHmmss")
Copy-Item $envPath $backupPath
Write-Host "Eski .env yedeği: $backupPath"

try {
    Write-Host "Veritabanı parolası değiştiriliyor..."
    $sql = "ALTER ROLE `"$dbUser`" WITH PASSWORD '$NewPassword';"
    docker compose exec -T db psql -U $dbUser -d postgres -c $sql | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "ALTER ROLE başarısız."
    }

    $lines = Get-Content $envPath
    $updated = $false
    for ($i = 0; $i -lt $lines.Count; $i++) {
        if ($lines[$i] -match '^POSTGRES_PASSWORD=') {
            $lines[$i] = "POSTGRES_PASSWORD=$NewPassword"
            $updated = $true
        }
    }
    if (-not $updated) {
        $lines += "POSTGRES_PASSWORD=$NewPassword"
    }
    [System.IO.File]::WriteAllLines($envPath, $lines, [System.Text.UTF8Encoding]::new($false))
    Write-Host ".env güncellendi."
} catch {
    if ($oldPassword) {
        $restoreSql = "ALTER ROLE `"$dbUser`" WITH PASSWORD '$oldPassword';"
        docker compose exec -T db psql -U $dbUser -d postgres -c $restoreSql | Out-Null
    }
    throw
}

Write-Host "Uygulama yeni parolayla yeniden başlatılıyor..."
docker compose up -d --force-recreate app | Out-Null
if ($LASTEXITCODE -ne 0) {
    throw "Uygulama yeniden başlatılamadı. .env yedeği: $backupPath"
}

$deadline = (Get-Date).AddMinutes(3)
do {
    try {
        $response = Invoke-WebRequest -UseBasicParsing "http://localhost:$appPort/actuator/health" -TimeoutSec 5
        if ($response.StatusCode -eq 200) {
            Write-Host "Parola değiştirildi ve uygulama sağlıklı çalışıyor."
            Write-Host "Eski .env yedeğini güvenli şekilde silin: $backupPath"
            exit 0
        }
    } catch {
        Start-Sleep -Seconds 5
    }
} while ((Get-Date) -lt $deadline)

throw "Uygulama zamanında sağlıklı duruma gelmedi. .env yedeği: $backupPath"
