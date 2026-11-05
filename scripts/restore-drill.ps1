$ErrorActionPreference = "Stop"

# Bu betik yedegi ayri bir veritabanina geri yukler ve temel tablolari dogrular.
# Kullanim: powershell -ExecutionPolicy Bypass -File .\scripts\restore-drill.ps1
$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location $projectRoot

$backupFile = "restore-drill.dump"
$testDatabase = "servisdefteri_restore_test"

Write-Host "1/5 Yedek aliniyor..."
docker compose exec -T app sh -lc "PGPASSWORD=`$DB_PASSWORD pg_dump -h `$DB_HOST -p `$DB_PORT -U `$DB_USERNAME -Fc -f /app/backups/$backupFile `$DB_NAME"
if ($LASTEXITCODE -ne 0) { throw "Yedek alinamadi." }

Write-Host "2/5 Test veritabani hazirlaniyor..."
docker compose exec -T db psql -U servisdefteri -d postgres -c "DROP DATABASE IF EXISTS $testDatabase;"
docker compose exec -T db psql -U servisdefteri -d postgres -c "CREATE DATABASE $testDatabase;"
if ($LASTEXITCODE -ne 0) { throw "Test veritabani olusturulamadi." }

Write-Host "3/5 Yedek geri yukleniyor..."
docker compose exec -T app sh -lc "PGPASSWORD=`$DB_PASSWORD pg_restore -h `$DB_HOST -p `$DB_PORT -U `$DB_USERNAME -d $testDatabase /app/backups/$backupFile"
if ($LASTEXITCODE -ne 0) { throw "Geri yukleme basarisiz." }

Write-Host "4/5 Dogrulama yapiliyor..."
docker compose exec -T db psql -U servisdefteri -d $testDatabase -c "select (select count(*) from users) users, (select count(*) from customers) customers, (select count(*) from vehicles) vehicles, (select count(*) from work_orders) work_orders, (select count(*) from business_settings) settings;"
if ($LASTEXITCODE -ne 0) { throw "Dogrulama sorgusu basarisiz." }

Write-Host "5/5 Test veritabani temizleniyor..."
docker compose exec -T db psql -U servisdefteri -d postgres -c "DROP DATABASE IF EXISTS $testDatabase;"
docker compose exec -T app sh -lc "rm -f /app/backups/$backupFile"

Write-Host "Yedek geri yukleme tatbikati BASARILI."
