param(
    [Parameter(Mandatory = $true)]
    [string]$Username,
    [string]$NewPassword = ""
)

$ErrorActionPreference = "Stop"

# PATRON veya başka bir kullanıcının parolasını, arayüze giriş yapmadan sıfırlar.
# Yalnızca sunucuya fiziksel/yerel erişimi olan kişi çalıştırmalıdır.
# Kullanım: powershell -ExecutionPolicy Bypass -File .\scripts\reset-user-password.ps1 -Username patron
# Parola verilmezse güçlü bir geçici parola üretilir ve ekranda gösterilir.

$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location $projectRoot

$container = $null
foreach ($candidate in @("servisdefteri-app", "toyota-servis-app")) {
    $found = docker ps --filter "name=^/$candidate$" --format "{{.Names}}"
    if ($found -eq $candidate) {
        $container = $candidate
        break
    }
}
if (-not $container) {
    throw "Çalışan uygulama konteyneri bulunamadı (servisdefteri-app veya toyota-servis-app)."
}

$args = @(
    "exec", $container,
    "java",
    "-Dloader.main=tr.com.servisdefteri.security.PasswordResetCli",
    "-cp", "/app/app.jar",
    "org.springframework.boot.loader.launch.PropertiesLauncher",
    $Username
)
if (-not [string]::IsNullOrWhiteSpace($NewPassword)) {
    $args += $NewPassword
}

Write-Host "Parola sıfırlanıyor: $Username (konteyner: $container)"
& docker @args
if ($LASTEXITCODE -ne 0) {
    throw "Parola sıfırlama başarısız (çıkış kodu: $LASTEXITCODE)."
}

# In-memory oturumları temizlemek için uygulamayı yeniden başlat.
$composeFile = Join-Path $projectRoot "docker-compose.yml"
if (-not (Test-Path $composeFile)) {
    $composeFile = Join-Path $projectRoot "docker-compose.dist.yml"
}
if (Test-Path $composeFile) {
    Write-Host "Uygulama yeniden başlatılıyor (açık oturumlar kapanacak)..."
    docker compose --project-directory $projectRoot --env-file (Join-Path $projectRoot ".env") `
        -f $composeFile restart app | Out-Null
}
Write-Host "Tamamlandı. Yeni parolayla giriş yapabilirsiniz."
