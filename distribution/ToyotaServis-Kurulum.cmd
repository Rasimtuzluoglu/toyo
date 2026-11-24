@echo off
setlocal EnableExtensions EnableDelayedExpansion
title Toyota Servis Kurulum

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
set "INSTALL_DIR=C:\ToyotaServis"
call :ensure_install_dir
if not errorlevel 1 goto install_dir_ready

echo.
echo UYARI: C:\ToyotaServis olusturulamadi veya yazilamiyor.
echo Alternatif kurulum klasoru deneniyor: %ProgramData%\ToyotaServis
set "INSTALL_DIR=%ProgramData%\ToyotaServis"
call :ensure_install_dir
if not errorlevel 1 goto install_dir_ready

echo.
echo UYARI: ProgramData klasoru de kullanilamiyor.
echo Alternatif kurulum klasoru deneniyor: %PUBLIC%\ToyotaServis
set "INSTALL_DIR=%PUBLIC%\ToyotaServis"
call :ensure_install_dir
if not errorlevel 1 goto install_dir_ready

echo.
echo UYARI: Public klasoru de kullanilamiyor.
echo Alternatif kurulum klasoru deneniyor: %USERPROFILE%\ToyotaServis
set "INSTALL_DIR=%USERPROFILE%\ToyotaServis"
call :ensure_install_dir
if not errorlevel 1 goto install_dir_ready

echo.
echo HATA: Kurulum klasoru olusturulamadi.
echo C:\, ProgramData ve Public klasorlerine yazma izni yok.
echo Yonetici olarak calistirdiginizdan ve antivirusun engellemediginden emin olun.
echo Log: %TEMP%\ToyotaServis-Kurulum.log
if not defined TOYOTA_NONINTERACTIVE pause
exit /b 1

:use_custom_install_dir
set "INSTALL_DIR=%TOYOTA_INSTALL_DIR%"
call :ensure_install_dir
if errorlevel 1 (
    echo.
    echo HATA: Belirtilen kurulum klasoru olusturulamadi veya yazilamiyor:
    echo %INSTALL_DIR%
    echo Yonetici izni ve antivirus ayarlarini kontrol edin.
    if not defined TOYOTA_NONINTERACTIVE pause
    exit /b 1
)
goto install_dir_ready

:install_dir_ready
if "%INSTALL_DIR:~-1%"=="\" set "INSTALL_DIR=%INSTALL_DIR:~0,-1%"
if defined TOYOTA_APP_PORT (
    set "APP_PORT=%TOYOTA_APP_PORT%"
) else (
    set "APP_PORT=18080"
)
set "ENVFILE=%INSTALL_DIR%\.env"
set "COMPOSE=%INSTALL_DIR%\docker-compose.dist.yml"
set "LOG=%INSTALL_DIR%\kurulum.log"
set "EARLY_LOG=%TEMP%\ToyotaServis-Kurulum.log"

echo ============================================
echo  Toyota Plaza Oztoprak - Servis Kurulumu
echo ============================================
echo  Kurulum klasoru: %INSTALL_DIR%
echo.

echo [%date% %time%] Kurulum basladi >> "%LOG%"
echo [%date% %time%] INSTALL_DIR=%INSTALL_DIR% >> "%LOG%"
echo [%date% %time%] Kurulum basladi (INSTALL_DIR=%INSTALL_DIR%) >> "%EARLY_LOG%"

echo [1/6] Docker Desktop kontrol ediliyor...
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
    echo Ardindan kurulumu tekrar calistirin.
    goto fail
)

echo [2/6] Kurulum dosyalari hazirlaniyor...
if /I not "%ROOT%"=="%INSTALL_DIR%\" (
    copy /Y "%ROOT%docker-compose.dist.yml" "%COMPOSE%" >nul
    copy /Y "%ROOT%docker-compose.dist.yml" "%INSTALL_DIR%\docker-compose.yml" >nul
    copy /Y "%ROOT%ToyotaServis-Baslat.cmd" "%INSTALL_DIR%\ToyotaServis-Baslat.cmd" >nul
    copy /Y "%ROOT%ToyotaServis-Durdur.cmd" "%INSTALL_DIR%\ToyotaServis-Durdur.cmd" >nul
    copy /Y "%ROOT%ToyotaServis-Guncelle.cmd" "%INSTALL_DIR%\ToyotaServis-Guncelle.cmd" >nul
    if exist "%ROOT%OKUBENI.md" copy /Y "%ROOT%OKUBENI.md" "%INSTALL_DIR%\OKUBENI.md" >nul
    if exist "%ROOT%BENIOKU.txt" copy /Y "%ROOT%BENIOKU.txt" "%INSTALL_DIR%\BENIOKU.txt" >nul
    if exist "%ROOT%scripts" (
        if not exist "%INSTALL_DIR%\scripts" mkdir "%INSTALL_DIR%\scripts"
        xcopy /E /I /Y "%ROOT%scripts" "%INSTALL_DIR%\scripts" >nul
    )
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

if defined TOYOTA_BACKUP_DIR (
    call :ensure_backup_dir "%TOYOTA_BACKUP_DIR%"
    if errorlevel 1 (
        echo UYARI: Belirtilen yedek klasoru kullanilamiyor: %TOYOTA_BACKUP_DIR%
        set "TOYOTA_BACKUP_DIR="
    ) else (
        goto backup_dir_ready
    )
)

set "TOYOTA_BACKUP_DIR=%USERPROFILE%\ToyotaServis-Yedekler"
call :ensure_backup_dir "%TOYOTA_BACKUP_DIR%"
if not errorlevel 1 goto backup_dir_ready

echo UYARI: Kullanici profilindeki yedek klasoru kullanilamiyor.
set "TOYOTA_BACKUP_DIR=%PUBLIC%\ToyotaServis-Yedekler"
call :ensure_backup_dir "%TOYOTA_BACKUP_DIR%"
if not errorlevel 1 goto backup_dir_ready

echo UYARI: Public yedek klasoru kullanilamiyor; kurulum klasoru kullanilacak.
set "TOYOTA_BACKUP_DIR=%INSTALL_DIR%\yedekler"
call :ensure_backup_dir "%TOYOTA_BACKUP_DIR%"
if not errorlevel 1 goto backup_dir_ready

echo HATA: Yedek klasoru olusturulamadi veya yazilamiyor.
echo Antivirus / Controlled Folder Access ayarlarini kontrol edin.
if not defined TOYOTA_NONINTERACTIVE pause
exit /b 1

:backup_dir_ready
set "BACKUP_ENV_DIR=%TOYOTA_BACKUP_DIR:\=/%"
echo Yedek klasoru otomatik secildi: %TOYOTA_BACKUP_DIR%

rem Batch parser: parenli PowerShell komutlarini dis if blogunun icine koymayin.
rem Aksi halde "Substring(0,24)" icindeki ')' blogu erken kapatir.
for /f "usebackq delims=" %%P in (`powershell -NoProfile -Command "[guid]::NewGuid().ToString('N').Substring(0,24)"`) do set "NEW_DBPASS=%%P"
for /f "usebackq delims=" %%P in (`powershell -NoProfile -Command "[guid]::NewGuid().ToString('N').Substring(0,24)"`) do set "NEW_SETUP_TOKEN=%%P"

if not exist "%ENVFILE%" (
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
    >> "%ENVFILE%" echo BACKUP_RETENTION_DAYS=14
    >> "%ENVFILE%" echo BACKUP_HOST_DIR="%BACKUP_ENV_DIR%"
    >> "%ENVFILE%" echo BACKUP_OFFSITE_DIR=
    >> "%ENVFILE%" echo SETUP_TOKEN=!NEW_SETUP_TOKEN!
    >> "%ENVFILE%" echo APP_UPDATE_ENABLED=false
    echo .env olusturuldu.
) else (
    echo Mevcut .env kullaniliyor.
    for /f "tokens=1,* delims==" %%A in ('findstr /B /C:"APP_PORT=" "%ENVFILE%" 2^>nul') do set "APP_PORT=%%B"
    if not defined APP_PORT set "APP_PORT=18080"
    findstr /V /B /C:"BACKUP_HOST_DIR=" "%ENVFILE%" > "%ENVFILE%.tmp"
    >> "%ENVFILE%.tmp" echo BACKUP_HOST_DIR="%BACKUP_ENV_DIR%"
    move /Y "%ENVFILE%.tmp" "%ENVFILE%" >nul
    findstr /B /C:"BACKUP_COPY_DIR=" "%ENVFILE%" >nul 2>&1
    if not errorlevel 1 (
        powershell -NoProfile -Command "$p='%ENVFILE%'; (Get-Content -LiteralPath $p) -replace '^BACKUP_COPY_DIR=.*','BACKUP_COPY_DIR=' | Set-Content -LiteralPath $p -Encoding ASCII"
    )
    findstr /B /C:"APP_UPDATE_ENABLED=" "%ENVFILE%" >nul 2>&1
    if errorlevel 1 >> "%ENVFILE%" echo APP_UPDATE_ENABLED=false
    for /f "tokens=1,* delims==" %%A in ('findstr /B /C:"SETUP_TOKEN=" "%ENVFILE%" 2^>nul') do set "SETUP_TOKEN=%%B"
    if not defined SETUP_TOKEN (
        set "SETUP_TOKEN=!NEW_SETUP_TOKEN!"
        >> "%ENVFILE%" echo SETUP_TOKEN=!SETUP_TOKEN!
    )
)

if not exist "%ENVFILE%" (
    echo HATA: .env dosyasi olusturulamadi.
    goto fail
)

if exist "%INSTALL_DIR%\scripts\harden-env.ps1" (
    powershell -NoProfile -ExecutionPolicy Bypass -File "%INSTALL_DIR%\scripts\harden-env.ps1" >> "%LOG%" 2>&1
    if errorlevel 1 echo UYARI: .env izinleri sikilastirilamadi.
)

if defined TOYOTA_SKIP_START (
    echo TOYOTA_SKIP_START=1: servisler baslatilmadi.
    goto finish_no_start
)

echo [3/6] Eski konteynerler temizleniyor...
docker compose --project-directory "%INSTALL_DIR%" --env-file "%ENVFILE%" -f "%COMPOSE%" down --remove-orphans >nul 2>&1
docker rm -f toyota-servis-app toyota-servis-db >nul 2>&1
powershell -NoProfile -Command "try { Get-NetTCPConnection -LocalPort %APP_PORT% -State Listen -ErrorAction Stop | Out-Null; exit 0 } catch { exit 1 }"
if not errorlevel 1 (
    echo.
    echo HATA: %APP_PORT% portu baska bir uygulama tarafindan kullaniliyor.
    echo Farkli port icin: set TOYOTA_APP_PORT=18082
    goto fail
)

echo [3/6] Uygulama ve veritabani imajlari yukleniyor...
docker load -i "%ROOT%ToyotaServis-1.0.0.tar" > "%TEMP%\ToyotaServis-docker-load.log" 2>&1
if errorlevel 1 (
    echo HATA: Docker imajlari yuklenemedi.
    type "%TEMP%\ToyotaServis-docker-load.log"
    goto fail
)

echo [4/6] Servisler baslatiliyor...
docker compose --project-directory "%INSTALL_DIR%" --env-file "%ENVFILE%" -f "%COMPOSE%" up -d > "%TEMP%\ToyotaServis-compose-up.log" 2>&1
if errorlevel 1 (
    echo HATA: Servisler baslatilamadi.
    type "%TEMP%\ToyotaServis-compose-up.log"
    docker compose --project-directory "%INSTALL_DIR%" --env-file "%ENVFILE%" -f "%COMPOSE%" logs --tail 80
    goto fail
)

echo [5/6] Uygulama saglik kontrolu...
for /l %%i in (1,1,60) do (
    powershell -NoProfile -Command "try { $r=Invoke-WebRequest -UseBasicParsing http://localhost:%APP_PORT%/actuator/health -TimeoutSec 3; if ($r.StatusCode -eq 200) { exit 0 } } catch {}; exit 1" >nul 2>&1 && goto healthy
    ping -n 6 127.0.0.1 >nul
)
echo HATA: Uygulama zamaninda hazir olmadi.
docker compose --project-directory "%INSTALL_DIR%" --env-file "%ENVFILE%" -f "%COMPOSE%" logs --tail 100
goto fail

:healthy
if defined TOYOTA_SKIP_SHORTCUTS (
    echo [6/6] Kisayollar atlandi - TOYOTA_SKIP_SHORTCUTS=1.
    goto installed
)
echo [6/6] Kisayollar hazirlaniyor...

powershell -NoProfile -ExecutionPolicy Bypass -Command "$ws=New-Object -ComObject WScript.Shell; $desktop=[Environment]::GetFolderPath('Desktop'); $s=$ws.CreateShortcut((Join-Path $desktop 'Toyota Servis.url')); $s.TargetPath='http://localhost:%APP_PORT%'; $s.Save(); $startup=[Environment]::GetFolderPath('Startup'); $l=$ws.CreateShortcut((Join-Path $startup 'Toyota Servis.lnk')); $l.TargetPath='%INSTALL_DIR%\ToyotaServis-Baslat.cmd'; $l.WorkingDirectory='%INSTALL_DIR%'; $l.WindowStyle=7; $l.Save()"

if exist "%INSTALL_DIR%\scripts\install-health-check.ps1" (
    powershell -NoProfile -ExecutionPolicy Bypass -File "%INSTALL_DIR%\scripts\install-health-check.ps1" >> "%LOG%" 2>&1
    if errorlevel 1 (
        echo UYARI: Saglik kontrolu gorevi kurulamadi.
    ) else (
        echo Saglik kontrolu gorevi kuruldu: 15 dakikada bir.
    )
)

:installed
echo.
echo ============================================
echo  KURULUM TAMAMLANDI
echo ============================================
echo  Yerel adres : http://localhost:%APP_PORT%
echo  Ilk kurulum : http://localhost:%APP_PORT%/setup
echo  Giris       : http://localhost:%APP_PORT%/login
echo.
echo  Ilk kurulumda /setup ekranindan PATRON hesabini olusturun.
echo  Kurulum tokeni: %INSTALL_DIR%\.env icindeki SETUP_TOKEN
echo  Yedekler    : %TOYOTA_BACKUP_DIR%
echo  Veritabani parolasi: %INSTALL_DIR%\.env
echo  Baslat/Durdur: %INSTALL_DIR%\ToyotaServis-Baslat.cmd
echo.
echo  Bu paket kaynak kod icermez; uygulama derlenmis Docker imaji olarak calisir.
echo.
if defined TOYOTA_NO_BROWSER (
    echo Tarayici otomatik acilmadi - TOYOTA_NO_BROWSER=1.
) else (
    start "" "http://localhost:%APP_PORT%/setup?token=%SETUP_TOKEN%"
)
if not defined TOYOTA_NONINTERACTIVE pause
exit /b 0

:finish_no_start
echo Kurulum dosyalari hazirlandi.
echo Baslatmak icin: %INSTALL_DIR%\ToyotaServis-Baslat.cmd
if not defined TOYOTA_NONINTERACTIVE pause
exit /b 0

:fail
if exist "%COMPOSE%" if exist "%ENVFILE%" docker compose --project-directory "%INSTALL_DIR%" --env-file "%ENVFILE%" -f "%COMPOSE%" logs --tail 120 >> "%LOG%" 2>&1
echo.
echo Kurulum tamamlanamadi.
echo Docker Desktop ve WSL2/sanallastirmayi kontrol edin.
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

:ensure_backup_dir
if not exist "%~1" mkdir "%~1" >nul 2>&1
if errorlevel 1 exit /b 1
set "BACKUP_TEST=%~1\.yazma-testi-%RANDOM%%RANDOM%"
> "%BACKUP_TEST%" echo test 2>nul
if not exist "%BACKUP_TEST%" exit /b 1
del "%BACKUP_TEST%" >nul 2>&1
exit /b 0

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
    ping -n 6 127.0.0.1 >nul
)
echo Docker Desktop zamaninda hazir olmadi.
echo WSL2 ve sanallastirma acik olmalidir. Gerekirse "wsl --install" calistirin ve yeniden baslatin.
exit /b 1
