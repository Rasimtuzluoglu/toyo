@echo off
setlocal EnableExtensions
title Toyota Servis - Teshis
set "DIR=%~dp0"
set "RAPOR=%TEMP%\ToyotaServis-Tanila.txt"

> "%RAPOR%" echo ===== TOYOTA SERVIS TANILAMA RAPORU =====
>> "%RAPOR%" echo Tarih: %date% %time%
>> "%RAPOR%" echo.

>> "%RAPOR%" echo [1/6] docker version
docker version >> "%RAPOR%" 2>&1

>> "%RAPOR%" echo.
>> "%RAPOR%" echo [2/6] docker info
docker info >> "%RAPOR%" 2>&1

>> "%RAPOR%" echo.
>> "%RAPOR%" echo [3/6] wsl
wsl --status >> "%RAPOR%" 2>&1
wsl -l -v >> "%RAPOR%" 2>&1

>> "%RAPOR%" echo.
>> "%RAPOR%" echo [4/6] docker ps -a
docker ps -a >> "%RAPOR%" 2>&1

>> "%RAPOR%" echo.
>> "%RAPOR%" echo [5/6] compose ps
docker compose --project-directory "%DIR%" --env-file "%DIR%.env" -f "%DIR%docker-compose.dist.yml" ps -a >> "%RAPOR%" 2>&1

>> "%RAPOR%" echo.
>> "%RAPOR%" echo [6/6] konteyner loglari
docker logs toyota-servis-app --tail 200 >> "%RAPOR%" 2>&1
docker logs toyota-servis-db --tail 200 >> "%RAPOR%" 2>&1

>> "%RAPOR%" echo.
>> "%RAPOR%" echo [kurulum.log]
if exist "%DIR%kurulum.log" type "%DIR%kurulum.log" >> "%RAPOR%" 2>&1

echo.
echo Rapor olusturuldu:
echo %RAPOR%
echo.
echo Bu dosyayi destek ekibiyle paylasin.
echo.
pause
