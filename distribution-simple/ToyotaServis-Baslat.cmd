@echo off
setlocal EnableExtensions
set "PATH=%ProgramFiles%\Docker\Docker\resources\bin;%PATH%"
set "DIR=%~dp0"
set "PROJECT=%DIR:~0,-1%"
set "COMPOSE=%DIR%docker-compose.dist.yml"
set "ENVFILE=%DIR%.env"

if not exist "%COMPOSE%" (
    echo HATA: docker-compose.dist.yml bulunamadi.
    pause
    exit /b 1
)
if not exist "%ENVFILE%" (
    echo HATA: .env bulunamadi. Kurulum dosyasini yeniden calistirin.
    pause
    exit /b 1
)

for /f "tokens=1,* delims==" %%A in ('findstr /B /C:"APP_PORT=" "%ENVFILE%" 2^>nul') do set "APP_PORT=%%B"
if not defined APP_PORT set "APP_PORT=18080"

docker info >nul 2>&1
if errorlevel 1 (
    if exist "C:\Program Files\Docker\Docker\Docker Desktop.exe" (
        echo Docker Desktop baslatiliyor...
        start "" "C:\Program Files\Docker\Docker\Docker Desktop.exe"
    ) else (
        echo HATA: Docker Desktop bulunamadi.
        pause
        exit /b 1
    )
)

for /l %%i in (1,1,60) do (
    docker info >nul 2>&1 && goto docker_ready
    timeout /t 5 >nul
)
echo HATA: Docker Desktop zamaninda hazir olmadi.
pause
exit /b 1

:docker_ready
docker compose version >nul 2>&1
if errorlevel 1 (
    echo HATA: Docker Compose bulunamadi. Docker Desktop'i guncelleyin.
    pause
    exit /b 1
)
docker compose --project-directory "%PROJECT%" --env-file "%ENVFILE%" -f "%COMPOSE%" up -d
if errorlevel 1 (
    echo HATA: Toyota Servis baslatilamadi.
    pause
    exit /b 1
)
echo Toyota Servis calisiyor: http://localhost:%APP_PORT%
