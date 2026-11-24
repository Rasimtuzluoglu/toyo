@echo off
setlocal EnableExtensions EnableDelayedExpansion
title Toyota Servis Kurulum (Basit)

if not defined TOYOTA_SKIP_ADMIN (
    net session >nul 2>&1
    if errorlevel 1 (
        echo Yonetici izni gerekli. UAC penceresini onaylayin...
        powershell -NoProfile -ExecutionPolicy Bypass -Command "try { Start-Process -FilePath '%~f0' -Verb RunAs -Wait -ErrorAction Stop } catch { Write-Host 'Yonetici izni verilmedi veya UAC iptal edildi.'; Read-Host 'Kapatmak icin Enter tusuna basin'; exit 1 }"
        exit /b
    )
)

set "PATH=%ProgramFiles%\Docker\Docker\resources\bin;%PATH%"
set "ROOT=%~dp0"

if defined TOYOTA_INSTALL_DIR goto use_custom_install_dir

rem Varsayilan: paketin cikarildigi klasor. C: diski zorunlu degil.
set "INSTALL_DIR=%ROOT:~0,-1%"
if "%INSTALL_DIR:~1%"==":" set "INSTALL_DIR=%INSTALL_DIR%\ToyotaServis"
goto install_dir_ready_check

:use_custom_install_dir
set "INSTALL_DIR=%TOYOTA_INSTALL_DIR%"

:install_dir_ready_check
call :ensure_install_dir
if errorlevel 1 (
    echo.
    echo HATA: Kurulum klasoru olusturulamadi veya yazilamiyor:
    echo %INSTALL_DIR%
    echo Paketi yazilabilir bir klasore cikarin ve yonetici olarak calistirin.
    if not defined TOYOTA_NONINTERACTIVE pause
    exit /b 1
)

if "%INSTALL_DIR:~-1%"=="\" set "INSTALL_DIR=%INSTALL_DIR:~0,-1%"
if defined TOYOTA_APP_PORT (
    set "APP_PORT=%TOYOTA_APP_PORT%"
) else (
    set "APP_PORT=18080"
)

set "ENVFILE=%INSTALL_DIR%\.env"
set "COMPOSE=%INSTALL_DIR%\docker-compose.dist.yml"
set "LOG=%INSTALL_DIR%\kurulum.log"
set "EARLY_LOG=%TEMP%\ToyotaServis-Basit-Kurulum.log"

echo ============================================
echo  Toyota Plaza Oztoprak - Basit Kurulum
echo ============================================
echo  Kurulum klasoru: %INSTALL_DIR%
echo  Yedekleme: KAPALI
echo.

echo [%date% %time%] Kurulum basladi >> "%LOG%"
echo [%date% %time%] INSTALL_DIR=%INSTALL_DIR% >> "%LOG%"

if not exist "%COMPOSE%" (
    echo HATA: docker-compose.dist.yml bulunamadi.
    echo Paket dosyalarini eksiksiz cikardiginizdan emin olun.
    goto fail
)
if not exist "%ROOT%ToyotaServis-1.0.0.tar" (
    echo HATA: ToyotaServis-1.0.0.tar bulunamadi.
    goto fail
)

echo [1/5] Docker Desktop kontrol ediliyor...
call :ensure_docker
if errorlevel 1 goto fail
docker compose version >nul 2>&1
if errorlevel 1 (
    echo HATA: Docker Compose bulunamadi. Docker Desktop'i guncelleyin.
    goto fail
)
for /f "delims=" %%T in ('docker info --format "{{.OSType}}" 2^>nul') do set "DOCKER_OS=%%T"
if /I not "%DOCKER_OS%"=="linux" (
    echo.
    echo HATA: Docker Desktop Linux containers modunda degil.
    echo Docker Desktop tepsi simgesine sag tiklayip "Switch to Linux containers" secin.
    goto fail
)

echo [2/5] Ortam dosyasi hazirlaniyor...
if exist "%ENVFILE%" (
    for /f "tokens=1,* delims==" %%A in ('findstr /B /C:"APP_PORT=" "%ENVFILE%" 2^>nul') do set "APP_PORT=%%B"
    if not defined APP_PORT set "APP_PORT=18080"
    findstr /V /B /C:"BACKUP_" /C:"APP_UPDATE_ENABLED=" "%ENVFILE%" > "%ENVFILE%.tmp"
    >> "%ENVFILE%.tmp" echo BACKUP_ENABLED=false
    >> "%ENVFILE%.tmp" echo APP_UPDATE_ENABLED=false
    move /Y "%ENVFILE%.tmp" "%ENVFILE%" >nul
    findstr /B /C:"SETUP_TOKEN=" "%ENVFILE%" >nul 2>&1
    if errorlevel 1 (
        for /f "usebackq delims=" %%P in (`powershell -NoProfile -Command "[guid]::NewGuid().ToString('N').Substring(0,24)"`) do set "NEW_SETUP_TOKEN=%%P"
        >> "%ENVFILE%" echo SETUP_TOKEN=!NEW_SETUP_TOKEN!
    )
    echo Mevcut .env kullaniliyor; yedekleme ayarlari kaldirildi.
) else (
    set "NEW_ENV=1"
    for /f "usebackq delims=" %%P in (`powershell -NoProfile -Command "[guid]::NewGuid().ToString('N').Substring(0,24)"`) do set "NEW_DBPASS=%%P"
    for /f "usebackq delims=" %%P in (`powershell -NoProfile -Command "[guid]::NewGuid().ToString('N').Substring(0,24)"`) do set "NEW_SETUP_TOKEN=%%P"
    if not defined NEW_DBPASS (
        echo HATA: Veritabani parolasi olusturulamadi.
        goto fail
    )
    if not defined NEW_SETUP_TOKEN (
        echo HATA: Kurulum tokeni olusturulamadi.
        goto fail
    )
    > "%ENVFILE%" echo POSTGRES_DB=servisdefteri
    >> "%ENVFILE%" echo POSTGRES_USER=servisdefteri
    >> "%ENVFILE%" echo POSTGRES_PASSWORD=!NEW_DBPASS!
    >> "%ENVFILE%" echo APP_PORT=%APP_PORT%
    >> "%ENVFILE%" echo SETUP_TOKEN=!NEW_SETUP_TOKEN!
    >> "%ENVFILE%" echo BACKUP_ENABLED=false
    >> "%ENVFILE%" echo APP_UPDATE_ENABLED=false
    echo .env olusturuldu - yedekleme kapali.
)

if exist "%INSTALL_DIR%\scripts\harden-env.ps1" (
    powershell -NoProfile -ExecutionPolicy Bypass -File "%INSTALL_DIR%\scripts\harden-env.ps1" >> "%LOG%" 2>&1
    if errorlevel 1 echo UYARI: .env izinleri sikilastirilamadi.
)

for /f "tokens=1,* delims==" %%A in ('findstr /B /C:"SETUP_TOKEN=" "%ENVFILE%" 2^>nul') do set "SETUP_TOKEN=%%B"

if defined TOYOTA_SKIP_START (
    echo TOYOTA_SKIP_START=1: servisler baslatilmadi.
    goto finish_no_start
)

echo [3/5] Eski konteynerler temizleniyor...
docker compose --project-directory "%INSTALL_DIR%" --env-file "%ENVFILE%" -f "%COMPOSE%" down --remove-orphans >nul 2>&1
docker rm -f toyota-servis-app toyota-servis-db >nul 2>&1
if defined NEW_ENV docker volume rm toyota-servis-pgdata >nul 2>&1
powershell -NoProfile -Command "try { Get-NetTCPConnection -LocalPort %APP_PORT% -State Listen -ErrorAction Stop | Out-Null; exit 0 } catch { exit 1 }"
if not errorlevel 1 (
    echo.
    echo HATA: %APP_PORT% portu baska bir uygulama tarafindan kullaniliyor.
    echo Farkli port icin: set TOYOTA_APP_PORT=18082
    goto fail
)

if not exist "%ROOT%ToyotaServis-1.0.0.tar" (
    echo.
    echo HATA: Imaj dosyasi bulunamadi: %ROOT%ToyotaServis-1.0.0.tar
    echo Kurulum dosyalari, cikarildigi diskten silinmis veya disk cikarilmis olabilir.
    echo Paketi sabit bir diske cikarip tekrar calistirin. Ornek: C:\ToyotaServis-Kurulum
    goto fail
)

echo [3/5] Uygulama ve veritabani imajlari yukleniyor...
echo    Not: 340 MB imaj yukleniyor, 1-2 dakika surebilir. Pencereyi kapatmayin.
docker load -i "%ROOT%ToyotaServis-1.0.0.tar" > "%TEMP%\ToyotaServis-Basit-docker-load.log" 2>&1
if errorlevel 1 (
    echo HATA: Docker imajlari yuklenemedi.
    type "%TEMP%\ToyotaServis-Basit-docker-load.log"
    goto fail
)

echo [4/5] Servisler baslatiliyor...
docker compose --project-directory "%INSTALL_DIR%" --env-file "%ENVFILE%" -f "%COMPOSE%" up -d > "%TEMP%\ToyotaServis-Basit-compose-up.log" 2>&1
if errorlevel 1 (
    echo HATA: Servisler baslatilamadi.
    type "%TEMP%\ToyotaServis-Basit-compose-up.log"
    docker compose --project-directory "%INSTALL_DIR%" --env-file "%ENVFILE%" -f "%COMPOSE%" logs --tail 80
    goto fail
)

echo [5/5] Uygulama saglik kontrolu...
echo    Not: Ilk baslatma 1-2 dakika surebilir. Pencereyi kapatmayin.
for /l %%i in (1,1,60) do (
    powershell -NoProfile -Command "try { $r=Invoke-WebRequest -UseBasicParsing http://localhost:%APP_PORT%/actuator/health -TimeoutSec 3; if ($r.StatusCode -eq 200) { exit 0 } } catch {}; exit 1" >nul 2>&1 && goto healthy
    ping -n 6 127.0.0.1 >nul
)
echo HATA: Uygulama zamaninda hazir olmadi.
echo ---- Konteynerler ----
docker ps -a --format "table {{.Names}}\t{{.Status}}"
echo ---- Uygulama loglari ----
docker compose --project-directory "%INSTALL_DIR%" --env-file "%ENVFILE%" -f "%COMPOSE%" logs --tail 100 app
echo ---- Veritabani loglari ----
docker compose --project-directory "%INSTALL_DIR%" --env-file "%ENVFILE%" -f "%COMPOSE%" logs --tail 100 db
goto fail

:healthy
if not defined TOYOTA_SKIP_SHORTCUTS (
    echo [5/5] Masaustu kisayolu hazirlaniyor...
    powershell -NoProfile -ExecutionPolicy Bypass -Command "$ws=New-Object -ComObject WScript.Shell; $desktop=[Environment]::GetFolderPath('Desktop'); $s=$ws.CreateShortcut((Join-Path $desktop 'Toyota Servis.url')); $s.TargetPath='http://localhost:%APP_PORT%'; $s.Save()" >> "%LOG%" 2>&1
)

:finish
echo.
echo ============================================
echo  KURULUM TAMAMLANDI
echo ============================================
echo  Yerel adres : http://localhost:%APP_PORT%
if defined SETUP_TOKEN (
    echo  Ilk kurulum : http://localhost:%APP_PORT%/setup?token=%SETUP_TOKEN%
) else (
    echo  Ilk kurulum : http://localhost:%APP_PORT%/setup
)
echo  Giris       : http://localhost:%APP_PORT%/login
echo.
echo  Ilk kurulumda /setup ekranindan PATRON hesabini olusturun.
echo  Kurulum tokeni: %INSTALL_DIR%\.env icindeki SETUP_TOKEN
echo  Baslat/Durdur: %INSTALL_DIR%\ToyotaServis-Baslat.cmd
echo.
echo  Yedekleme   : KAPALI (otomatik veya manuel yedek yok)
echo  Otomatik guncelleme: KAPALI
echo  Otomatik saglik gorevi: YOK
echo.
echo  UYARI: Bu pakette yedekleme yoktur. Disk arizasi veya yanlis silme
echo  durumunda veri kurtarma imkani yoktur.
echo.
if defined TOYOTA_NO_BROWSER (
    echo Tarayici otomatik acilmadi - TOYOTA_NO_BROWSER=1.
) else (
    if defined SETUP_TOKEN (
        start "" "http://localhost:%APP_PORT%/setup?token=%SETUP_TOKEN%"
    ) else (
        start "" "http://localhost:%APP_PORT%/setup"
    )
)
exit /b 0

:finish_no_start
echo.
echo Kurulum dosyalari hazirlandi (servisler baslatilmadi).
echo Baslatmak icin: %INSTALL_DIR%\ToyotaServis-Baslat.cmd
exit /b 0

:fail
echo.
echo Kurulum tamamlanamadi.
echo Ayrintilar icin: %INSTALL_DIR%\kurulum.log
if exist "%EARLY_LOG%" echo Erken kurulum gunlugu: %EARLY_LOG%
if not defined TOYOTA_NONINTERACTIVE pause
exit /b 1

:ensure_install_dir
if not exist "%INSTALL_DIR%" mkdir "%INSTALL_DIR%" >nul 2>&1
if errorlevel 1 exit /b 1
set "WRITE_TEST=%INSTALL_DIR%\.yazma-testi-%RANDOM%%RANDOM%"
> "%WRITE_TEST%" echo test 2>nul
if not exist "%WRITE_TEST%" exit /b 1
del "%WRITE_TEST%" >nul 2>&1
exit /b 0

:ensure_docker
docker --version >nul 2>&1
if errorlevel 1 goto docker_not_installed
docker info >nul 2>&1
if not errorlevel 1 exit /b 0
call :start_docker
call :wait_docker
if not errorlevel 1 exit /b 0
exit /b 1

:docker_not_installed
echo.
echo HATA: Docker Desktop kurulu degil.
echo Docker Desktop'i kurun, Linux containers modunda baslatin ve kurulumu tekrar calistirin.
exit /b 1

:start_docker
set "DOCKER_EXE="
if exist "C:\Program Files\Docker\Docker\Docker Desktop.exe" set "DOCKER_EXE=C:\Program Files\Docker\Docker\Docker Desktop.exe"
if not defined DOCKER_EXE if exist "%LOCALAPPDATA%\Docker\Docker Desktop.exe" set "DOCKER_EXE=%LOCALAPPDATA%\Docker\Docker Desktop.exe"
if defined DOCKER_EXE (
    echo Docker Desktop baslatiliyor...
    start "" "%DOCKER_EXE%"
)
exit /b 0

:wait_docker
for /l %%i in (1,1,60) do (
    docker info >nul 2>&1 && exit /b 0
    ping -n 6 127.0.0.1 >nul
)
echo Docker Desktop zamaninda hazir olmadi.
echo WSL2 ve sanallastirma acik olmalidir. Gerekirse "wsl --install" calistirin ve yeniden baslatin.
exit /b 1
