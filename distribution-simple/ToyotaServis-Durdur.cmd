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
    echo HATA: .env bulunamadi.
    pause
    exit /b 1
)

docker compose --project-directory "%PROJECT%" --env-file "%ENVFILE%" -f "%COMPOSE%" down
echo Toyota Servis durduruldu. Veriler korunuyor.
pause
