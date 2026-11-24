@echo off
setlocal EnableExtensions
set "PATH=%ProgramFiles%\Docker\Docker\resources\bin;%PATH%"
set "DIR=%~dp0"
set "PROJECT=%DIR:~0,-1%"
set "COMPOSE=%DIR%docker-compose.dist.yml"
set "ENVFILE=%DIR%.env"
set "NEW=%DIR%ToyotaServis-Yeni.tar"

if not exist "%COMPOSE%" (
    echo HATA: docker-compose.dist.yml bulunamadi.
    pause
    exit /b 1
)
if not exist "%ENVFILE%" (
    echo HATA: .env bulunamadi.
    pause
    exit /b 1
)
if not exist "%NEW%" (
    echo HATA: ToyotaServis-Yeni.tar bulunamadi.
    echo Yeni imaj dosyasini bu klasore kopyalayip tekrar deneyin.
    pause
    exit /b 1
)

for /f "tokens=1,* delims==" %%A in ('findstr /B /C:"APP_PORT=" "%ENVFILE%" 2^>nul') do set "APP_PORT=%%B"
if not defined APP_PORT set "APP_PORT=18080"

docker info >nul 2>&1
if errorlevel 1 (
    echo HATA: Docker Desktop calismiyor.
    pause
    exit /b 1
)

echo Guncelleme oncesi veritabani yedegi aliniyor...
docker compose --project-directory "%PROJECT%" --env-file "%ENVFILE%" -f "%COMPOSE%" exec -T app sh -lc "PGPASSWORD=$DB_PASSWORD pg_dump -h $DB_HOST -p $DB_PORT -U $DB_USERNAME -Fc -f /app/backups/pre-update-$(date +%%Y%%m%%d-%%H%%M%%S).dump $DB_NAME"
if errorlevel 1 (
    echo HATA: Yedek alinamadi. Guncelleme durduruldu.
    pause
    exit /b 1
)

docker tag servisdefteri:1.0.0 servisdefteri:previous >nul 2>&1
echo Yeni imaj yukleniyor...
docker load -i "%NEW%"
if errorlevel 1 (
    echo HATA: Yeni imaj yuklenemedi. Eski surum calismaya devam ediyor.
    pause
    exit /b 1
)

echo Uygulama yeni surumle baslatiliyor...
docker compose --project-directory "%PROJECT%" --env-file "%ENVFILE%" -f "%COMPOSE%" up -d --force-recreate app
if errorlevel 1 goto rollback

for /l %%i in (1,1,60) do (
    powershell -NoProfile -Command "try { $r=Invoke-WebRequest -UseBasicParsing http://localhost:%APP_PORT%/actuator/health -TimeoutSec 3; if ($r.StatusCode -eq 200) { exit 0 } } catch {}; exit 1" >nul 2>&1 && goto healthy
    timeout /t 5 >nul
)

:rollback
echo HATA: Yeni surum saglikli baslamadi. Eski surume donuluyor...
docker tag servisdefteri:previous servisdefteri:1.0.0 >nul 2>&1
docker compose --project-directory "%PROJECT%" --env-file "%ENVFILE%" -f "%COMPOSE%" up -d --force-recreate app
pause
exit /b 1

:healthy
echo Guncelleme tamamlandi: http://localhost:%APP_PORT%
pause
