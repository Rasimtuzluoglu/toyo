@echo off
setlocal EnableExtensions EnableDelayedExpansion
title Toyota Servis Kurulum

net session >nul 2>&1
if errorlevel 1 (
    echo Yonetici izni isteniyor...
    powershell -NoProfile -Command "Start-Process -FilePath '%~f0' -Verb RunAs"
    exit /b
)

set "PATH=%ProgramFiles%\Docker\Docker\resources\bin;%PATH%"
set "ROOT=%~dp0"
if defined TOYOTA_INSTALL_DIR (
    set "INSTALL_DIR=%TOYOTA_INSTALL_DIR%"
) else (
    set "INSTALL_DIR=C:\ToyotaServis"
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

echo ============================================
echo  Toyota Plaza Oztoprak - Servis Kurulumu
echo ============================================
echo.

if not exist "%INSTALL_DIR%" mkdir "%INSTALL_DIR%"
echo [%date% %time%] Kurulum basladi >> "%LOG%"

echo [1/6] Docker Desktop kontrol ediliyor...
call :ensure_docker
if errorlevel 1 goto fail
docker compose version >nul 2>&1
if errorlevel 1 (
    echo HATA: Docker Compose bulunamadi. Docker Desktop'i guncelleyin.
    goto fail
)

echo [2/6] Kurulum dosyalari hazirlaniyor...
if /I not "%ROOT%"=="%INSTALL_DIR%\" (
    copy /Y "%ROOT%docker-compose.dist.yml" "%COMPOSE%" >nul
    copy /Y "%ROOT%ToyotaServis-Baslat.cmd" "%INSTALL_DIR%\ToyotaServis-Baslat.cmd" >nul
    copy /Y "%ROOT%ToyotaServis-Durdur.cmd" "%INSTALL_DIR%\ToyotaServis-Durdur.cmd" >nul
    copy /Y "%ROOT%ToyotaServis-Guncelle.cmd" "%INSTALL_DIR%\ToyotaServis-Guncelle.cmd" >nul
    if exist "%ROOT%OKUBENI.md" copy /Y "%ROOT%OKUBENI.md" "%INSTALL_DIR%\OKUBENI.md" >nul
)
if not exist "%COMPOSE%" (
    echo HATA: docker-compose.dist.yml bulunamadi.
    goto fail
)
if not exist "%INSTALL_DIR%\ToyotaServis-Baslat.cmd" (
    echo HATA: Baslatma betigi kopyalanamadi.
    goto fail
)
if not exist "%ROOT%ToyotaServis-1.0.0.tar" (
    echo HATA: ToyotaServis-1.0.0.tar bulunamadi.
    echo Kurulum klasorunu komple kopyaladiginizdan emin olun.
    goto fail
)

if not defined TOYOTA_BACKUP_DIR set "TOYOTA_BACKUP_DIR=%USERPROFILE%\ToyotaServis-Yedekler"
echo.
echo Yedeklerin tutulacagi klasoru secin (bosluk kullanmadan).
set /p "BACKUP_INPUT=Yedek klasoru [%TOYOTA_BACKUP_DIR%]: "
if defined BACKUP_INPUT set "TOYOTA_BACKUP_DIR=%BACKUP_INPUT%"
if not exist "%TOYOTA_BACKUP_DIR%" mkdir "%TOYOTA_BACKUP_DIR%" >nul 2>&1
set "BACKUP_ENV_DIR=%TOYOTA_BACKUP_DIR:\=/%"

if not exist "%ENVFILE%" (
    for /f "usebackq delims=" %%P in (`powershell -NoProfile -Command "[guid]::NewGuid().ToString('N').Substring(0,24)"`) do set "DBPASS=%%P"
    if not defined DBPASS (
        echo HATA: Veritabani parolasi olusturulamadi.
        goto fail
    )
    > "%ENVFILE%" echo POSTGRES_DB=servisdefteri
    >> "%ENVFILE%" echo POSTGRES_USER=servisdefteri
    >> "%ENVFILE%" echo POSTGRES_PASSWORD=!DBPASS!
    >> "%ENVFILE%" echo APP_PORT=%APP_PORT%
    >> "%ENVFILE%" echo BACKUP_RETENTION_DAYS=14
    >> "%ENVFILE%" echo BACKUP_HOST_DIR=%BACKUP_ENV_DIR%
    echo .env olusturuldu.
) else (
    echo Mevcut .env kullaniliyor.
    for /f "tokens=1,* delims==" %%A in ('findstr /B /C:"APP_PORT=" "%ENVFILE%" 2^>nul') do set "APP_PORT=%%B"
    if not defined APP_PORT set "APP_PORT=18080"
    findstr /B /C:"BACKUP_HOST_DIR=" "%ENVFILE%" >nul 2>&1
    if errorlevel 1 >> "%ENVFILE%" echo BACKUP_HOST_DIR=%BACKUP_ENV_DIR%
)

if defined TOYOTA_SKIP_START (
    echo TOYOTA_SKIP_START=1: servisler baslatilmadi.
    goto finish_no_start
)

echo [3/6] Uygulama imaji yukleniyor...
docker load -i "%ROOT%ToyotaServis-1.0.0.tar"
if errorlevel 1 (
    echo HATA: Uygulama imaji yuklenemedi.
    goto fail
)

echo [4/6] Servisler baslatiliyor...
docker compose --project-directory "%INSTALL_DIR%" --env-file "%ENVFILE%" -f "%COMPOSE%" up -d
if errorlevel 1 (
    echo HATA: Servisler baslatilamadi.
    docker compose --project-directory "%INSTALL_DIR%" --env-file "%ENVFILE%" -f "%COMPOSE%" logs --tail 80
    goto fail
)

echo [5/6] Uygulama saglik kontrolu...
for /l %%i in (1,1,60) do (
    powershell -NoProfile -Command "try { $r=Invoke-WebRequest -UseBasicParsing http://localhost:%APP_PORT%/actuator/health -TimeoutSec 3; if ($r.StatusCode -eq 200) { exit 0 } } catch {}; exit 1" >nul 2>&1 && goto healthy
    timeout /t 5 >nul
)
echo HATA: Uygulama zamaninda hazir olmadi.
docker compose --project-directory "%INSTALL_DIR%" --env-file "%ENVFILE%" -f "%COMPOSE%" logs --tail 100
goto fail

:healthy
echo [6/6] Kisayollar hazirlaniyor...

powershell -NoProfile -ExecutionPolicy Bypass -Command "$ws=New-Object -ComObject WScript.Shell; $desktop=[Environment]::GetFolderPath('Desktop'); $s=$ws.CreateShortcut((Join-Path $desktop 'Toyota Servis.url')); $s.TargetPath='http://localhost:%APP_PORT%'; $s.Save(); $startup=[Environment]::GetFolderPath('Startup'); $l=$ws.CreateShortcut((Join-Path $startup 'Toyota Servis.lnk')); $l.TargetPath='%INSTALL_DIR%\ToyotaServis-Baslat.cmd'; $l.WorkingDirectory='%INSTALL_DIR%'; $l.WindowStyle=7; $l.Save()"

echo.
echo ============================================
echo  KURULUM TAMAMLANDI
echo ============================================
echo  Yerel adres : http://localhost:%APP_PORT%
echo  Ilk kurulum : http://localhost:%APP_PORT%/setup
echo  Giris       : http://localhost:%APP_PORT%/login
echo.
echo  Ilk kurulumda /setup ekranindan PATRON hesabini olusturun.
echo  Veritabani parolasi: %INSTALL_DIR%\.env
echo  Baslat/Durdur: %INSTALL_DIR%\ToyotaServis-Baslat.cmd
echo.
start "" "http://localhost:%APP_PORT%/setup"
pause
exit /b 0

:finish_no_start
echo Kurulum dosyalari hazirlandi.
echo Baslatmak icin: %INSTALL_DIR%\ToyotaServis-Baslat.cmd
pause
exit /b 0

:fail
if exist "%COMPOSE%" if exist "%ENVFILE%" docker compose --project-directory "%INSTALL_DIR%" --env-file "%ENVFILE%" -f "%COMPOSE%" logs --tail 120 >> "%LOG%" 2>&1
echo.
echo Kurulum tamamlanamadi.
echo Docker Desktop ve WSL2/sanallastirmayi kontrol edin.
echo Ayrintilar icin: %INSTALL_DIR%\kurulum.log
pause
exit /b 1

:ensure_docker
set "PATH=%ProgramFiles%\Docker\Docker\resources\bin;%PATH%"
docker --version >nul 2>&1
if not errorlevel 1 (
    docker info >nul 2>&1
    if not errorlevel 1 exit /b 0
    call :start_docker
    call :wait_docker
    if not errorlevel 1 exit /b 0
    exit /b 1
)

echo Docker Desktop bulunamadi; otomatik kurulum deneniyor...
where winget >nul 2>&1
if not errorlevel 1 (
    winget install --id Docker.DockerDesktop -e --silent --accept-package-agreements --accept-source-agreements
)
set "PATH=%ProgramFiles%\Docker\Docker\resources\bin;%PATH%"
if exist "C:\Program Files\Docker\Docker\Docker Desktop.exe" (
    call :start_docker
    call :wait_docker
    if not errorlevel 1 exit /b 0
    exit /b 1
)

echo Docker Desktop indiriliyor...
where curl.exe >nul 2>&1
if not errorlevel 1 (
    curl.exe -L --fail --output "%TEMP%\DockerDesktopInstaller.exe" "https://desktop.docker.com/win/main/amd64/Docker%%20Desktop%%20Installer.exe"
) else (
    powershell -NoProfile -Command "[Net.ServicePointManager]::SecurityProtocol=[Net.SecurityProtocolType]::Tls12; $p=Join-Path $env:TEMP 'DockerDesktopInstaller.exe'; Invoke-WebRequest -Uri 'https://desktop.docker.com/win/main/amd64/Docker%%20Desktop%%20Installer.exe' -OutFile $p"
)
if exist "%TEMP%\DockerDesktopInstaller.exe" (
    echo Docker Desktop kuruluyor. Bu islem birkac dakika surebilir...
    start /wait "" "%TEMP%\DockerDesktopInstaller.exe" install --quiet --accept-license
)
set "PATH=%ProgramFiles%\Docker\Docker\resources\bin;%PATH%"
if exist "C:\Program Files\Docker\Docker\Docker Desktop.exe" (
    call :start_docker
    call :wait_docker
    if not errorlevel 1 exit /b 0
)
echo HATA: Docker Desktop kurulamadi veya baslatilamadi.
echo Bilgisayari yeniden baslatip bu kurulum dosyasini tekrar calistirin.
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
    timeout /t 5 >nul
)
echo Docker Desktop zamaninda hazir olmadi.
echo WSL2 ve sanallastirma acik olmalidir. Gerekirse "wsl --install" calistirin ve yeniden baslatin.
exit /b 1
