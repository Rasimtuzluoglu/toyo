# Kurulum

## 1. Gereksinimler

| Bileşen | Sürüm / Not |
|---|---|
| Java | 21 LTS |
| Maven | 3.9+ |
| Docker | Engine / Desktop, Compose v2 |
| PostgreSQL | 16 (Docker ile gelir) |
| Tarayıcı | Güncel Chrome, Edge, Firefox veya Safari |

Yerel makinede Java ve Maven olmadan yalnızca Docker ile kurulum yapılabilir (çok aşamalı Dockerfile derlemeyi container içinde yapar).

## 2. Ortam Dosyası

```bash
cp .env.example .env
```

`.env` içinde en az şunları düzenleyin:

```env
POSTGRES_DB=servisdefteri
POSTGRES_USER=servisdefteri
POSTGRES_PASSWORD=<guclu-ve-benzersiz-parola>
```

İlk işletme sahibini (PATRON) otomatik oluşturmak için (opsiyonel):

```env
INITIAL_ADMIN_USERNAME=patron
INITIAL_ADMIN_PASSWORD=<en-az-10-karakter-Buyuk-kucuk-rakam>
INITIAL_ADMIN_FULL_NAME=Isletme Sahibi
```

> `INITIAL_ADMIN_*` değişken adları geriye dönük uyumluluk için korunmuştur; oluşturulan hesabın rolü **PATRON**'dur. Değerler boş bırakılırsa uygulama ilk açılışta kullanıcı oluşturmaz; `/setup` ekranından ilk PATRON hesabı oluşturulur. Herkesçe bilinen bir varsayılan parola yoktur.
>
> Kurulum **yalnızca bir kez** yapılabilir. İlk patron oluşturulduğunda `installation_state.setup_completed = TRUE` olur; `/setup` adresi bundan sonra doğrudan giriş ekranına yönlenir. Kullanıcı tablosu sonradan boşalsa bile kurulum ekranı yeniden açılmaz.
>
> `.env` dosyasını Git'e eklemeyin. `.gitignore` bunu engeller.

## 3. Docker ile Kurulum

```bash
docker compose up -d --build
docker compose ps
docker compose logs -f app
```

Beklenen durum:

- `servisdefteri-db`: `healthy`
- `servisdefteri-app`: `Up` (healthcheck bir süre `starting` gösterebilir)

Uygulama: `http://localhost:18080`

### Yerel ağda kullanım (VPS / internete açma yok)
Bu kurulum yalnızca yerel bilgisayar ve aynı ağdaki cihazlar için tasarlanmıştır. Uygulama internete açılmaz; PostgreSQL portu da dışarı yayınlanmaz.

- **Yalnızca bu bilgisayardan kullanılacaksa:** `.env` içinde `APP_BIND_ADDRESS=127.0.0.1` yapın.
- **Tablet/telefon aynı Wi-Fi üzerinden bağlanacaksa:** `APP_BIND_ADDRESS=0.0.0.0` bırakın.
- Sunucu bilgisayarın yerel IP'sini `ipconfig` ile bulun ve tabletten `http://192.168.x.x:18080` adresini açın.
- Windows Güvenlik Duvarı'nda yalnızca **özel ağ** profili için TCP **18080** portuna izin verin; genel ağ profiline izin vermeyin.
- Modem/router üzerinde bu port için **port yönlendirme yapmayın**. Dışarıdan erişim gerekirse VPS veya port açmak yerine VPN kullanın.
- Yerel ağda HTTPS yoktur; güvenilir ve parola korumalı bir Wi-Fi kullanın.
- TLS sonlandıran bir reverse proxy kullanırsanız `.env` içinde `SESSION_COOKIE_SECURE=true` yapın.
- Yedekleri aynı bilgisayarda tutmayın; **Yedek Al ve İndir** ile harici disk/NAS'a kopyalayın.

### Windows'ta otomatik başlatma

Tek seferlik kurulum:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\install-autostart.ps1
```

Bu betik, oturum açıldığında uygulamayı başlatan bir kısayolu Startup klasörüne ekler.
Docker Desktop'ta **Settings → General → Start Docker Desktop when you sign in** seçeneğini de açın.

Manuel başlatma:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\start-toyota.ps1
```

### Yedek geri yükleme tatbikatı

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\restore-drill.ps1
```

Betik yeni bir yedek alır, ayrı bir test veritabanına geri yükler, temel tabloları doğrular ve test veritabanını temizler.

### Yazılım güncelleme ajanı

Ayarlar ekranındaki **Güncelle ve Yeniden Başlat** düğmesi, container içinden Docker komutu çalıştırmaz. Bunun yerine `updates/update.request` dosyasına istek bırakır; host bilgisayarda çalışan ajan `git pull` ve `docker compose up -d --build` işlemini yapar.

Tek seferlik kurulum:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\install-update-agent.ps1
```

Bu betik ajanı **Windows Görev Zamanlayıcı**'ya ekler ve hemen başlatır. Görev oturum açılışında çalışır; ajan beklenmedik şekilde kapanırsa Windows 1 dakika içinde yeniden başlatır. Ajan her 10 saniyede `updates/agent.status` dosyasına heartbeat yazar; Ayarlar ekranında **Ajan hazır** görünür.

- **Manuel güncelleme:** Ayarlar → **Kontrol Et** → **Güncelle ve Yeniden Başlat**. Uygulama 1-3 dakika erişilemez olabilir ve oturumlar kapanır.
- **Otomatik güncelleme:** Ajanı `UPDATE_AUTO=true` ortam değişkeniyle başlatırsanız 5 dakikada bir GitHub kontrol edilir ve yeni commit otomatik uygulanır. Canlı kullanımda mesai saatleri dışında tercih edin.
- Her güncellemeden önce `/app/backups/pre-update-YYYYMMDD-HHmmss.dump` yedeği otomatik alınır. Yedek alınamazsa güncelleme **durdurulur**.
- Ajan, yalnızca hızlı ileri alma (`git pull --ff-only`) yapar. Yerel değişiklik varsa güncelleme **hata** olarak durur; önce commit/stash yapılmalıdır.
- GitHub deposu özel ise `APP_UPDATE_GITHUB_TOKEN` ortam değişkenini tanımlayın.
- Güncelleme günlükleri: `updates/update.log` ve `updates/agent.log`.

> Ajan host yetkisiyle çalışır; yalnızca güvenli ve yalnızca bu iş için kullanılan bilgisayarda çalıştırın.

### Temel komutlar

```bash
docker compose ps
docker compose logs -f app
docker compose logs -f db
docker compose restart app
docker compose down
```

> **Uyarı:** `docker compose down -v` komutu `servisdefteri-pgdata` ve `servisdefteri-backups` volume'larını siler. Veritabanı kayıtları ve yedekler geri döndürülemez biçimde kaybolur. Normal kapatma için `docker compose down` kullanın.

### Demo / sunum ortamı

Gerçek veriyi kirletmeden örnek verilerle göstermek için:

```bash
docker compose up -d --build
docker compose -f docker-compose.demo.yml up -d
```

Adres `http://localhost:18081`, kullanıcı `demo`, parola `DemoPass1234`. Demo veritabanı ayrı volume kullanır; `down -v` ile sıfırlanabilir.

### Port değiştirme

`.env` içinde:

```env
APP_PORT=8090
```

Uygulama container içinde 18080 dinlemeye devam eder; hostta 8090 açılır.

### Veritabanı portu

Ana `docker-compose.yml` dosyasında PostgreSQL portu **yayınlanmaz**. Yerel geliştirmede host üzerinden erişmek için:

```bash
docker compose -f docker-compose.yml -f compose.dev.yml up -d db
```

Bu komut `127.0.0.1:5432` bağlar; tüm ağa açmaz.

## 4. Yerel Geliştirme (Maven)

```bash
# PostgreSQL
docker compose -f docker-compose.yml -f compose.dev.yml up -d db

# Ortam değişkenleri (PowerShell)
$env:DB_URL="jdbc:postgresql://localhost:5432/servisdefteri"
$env:DB_USERNAME="servisdefteri"
$env:DB_PASSWORD="<POSTGRES_PASSWORD>"

# Uygulama
mvn spring-boot:run "-Dspring-boot.run.profiles=dev"
```

Linux/macOS:

```bash
export DB_URL=jdbc:postgresql://localhost:5432/servisdefteri
export DB_USERNAME=servisdefteri
export DB_PASSWORD="<POSTGRES_PASSWORD>"
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

Varsayılan `dev` profili:

- Thymeleaf cache kapalı
- Uygulama logları DEBUG
- `ddl-auto=validate` (şema yine Flyway ile)
- PostgreSQL `localhost:5432`

## 5. İlk Giriş

1. `http://localhost:18080` adresini açın.
2. İlk kurulumda `/setup` ekranına yönlendirilirsiniz. Bu ekran yalnızca bir kez açılır.
3. Ad soyad, kullanıcı adı, güçlü parola girin. Bu hesap **PATRON** rolüyle oluşturulur.
4. Giriş yaptıktan sonra **Kullanıcılar** ekranından **MUHASEBE** ve **SERVIS** hesaplarını oluşturun.

Parola politikası: en az 10 karakter, en az bir büyük harf, bir küçük harf ve bir rakam. Parolalar BCrypt strength 12 ile hashlenir. Her kullanıcı sağ üst menüden kendi parolasını değiştirebilir.

İlk girişten sonra **Ayarlar → İşletme bilgileri** bölümünde işletme adı, telefon, adres ve vergi bilgilerini girin; iş emri, makbuz ve ekstre çıktılarında bu bilgiler kullanılır.

## 6. Testler

```bash
mvn test
```

- Testcontainers gerçek PostgreSQL 16 container'ı başlatır; Docker çalışıyor olmalıdır.
- İlk çalıştırmada `postgres:16-alpine` imajı indirilir.
- Raporlar: `target/surefire-reports/`
- Sadece birim testleri: `mvn "-Dtest=*Test" test`
- Belirli bir sınıf: `mvn "-Dtest=ServisDefteriFlowIT" test`

Testler üretim veritabanınıza dokunmaz; ayrı container kullanır.

### Yerel CI

Test + Docker build (yerel CI):

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\ci-local.ps1
```

İsteğe bağlı pre-push hook:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\install-ci-hook.ps1
```

Hook, testler veya Docker build başarısızsa push'u durdurur. Log: `logs/ci-local.log`.

## 7. Sağlık Kontrolü

```bash
curl http://localhost:18080/actuator/health
```

Beklenen:

```json
{"status":"UP"}
```

Ayrıntılar güvenlik nedeniyle gizlenir. Uygulama container'ı, veritabanı `healthy` olmadan başlamaz (`depends_on: condition: service_healthy`).

## 8. Üretim Benzeri Kullanım Notları

- Uygulama yerel ağ için HTTP üzerinde çalışır. İnternete açmayın.
- Dış erişim gerekiyorsa HTTPS sonlandıran bir ters proxy arkasında ve güvenlik duvarı kurallarıyla kullanın.
- TLS arkasında `SESSION_COOKIE_SECURE=true` ayarlayın.
- `APP_PORT` yalnızca gerekli arayüzlere açılmalıdır.
- PostgreSQL portunu yayınlamayın.
- `.env` dosya izinlerini kısıtlayın.
- Düzenli yedek alın ve geri yükleme testi yapın: `docs/BACKUP-RESTORE.md`.

## 9. Sorun Giderme

### Uygulama başlamıyor: "Port 18080 was already in use"

8080 yerine bilinçli olarak 18080 kullanılır. 18080 de doluysa:

```bash
# Docker'da farklı host portu
APP_PORT=8090 docker compose up -d

# Yerelde farklı port
$env:SERVER_PORT="18081"
mvn spring-boot:run "-Dspring-boot.run.profiles=dev"
```

### Veritabanı bağlantı hatası

```bash
docker compose ps
docker compose logs db
docker compose exec db pg_isready -U servisdefteri -d servisdefteri
```

`.env` içindeki `POSTGRES_*` değerleriyle uygulamanın `DB_*` değerlerinin uyumlu olduğundan emin olun. Compose bu değerleri kendisi eşler.

### Flyway hatası

- Migration dosyaları değiştirilmişse `flyway_schema_history` ile checksum uyuşmaz.
- Üretimde migration dosyalarını geriye dönük düzenlemeyin; yeni sürüm ekleyin.
- Geliştirme veritabanını sıfırlamak için volume'u silmek gerekir: **veri kaybı uyarısı** ile `docker compose down -v`.

### "Kullanıcı yok" uyarısı

Uygulama logunda `Sistemde kullanıcı yok` mesajı varsa ve kurulum daha önce tamamlanmamışsa tarayıcıdan `/setup` açın veya `INITIAL_ADMIN_*` değişkenlerini tanımlayıp container'ı yeniden başlatın.

Kurulum bir kez tamamlandıysa `/setup` bilinçli olarak kapalıdır. Kullanıcılar silinmişse `/setup` yerine veritabanı yedeğini geri yükleyin.

### Yedekleme başarısız

- Container içinde `pg_dump` bulunmalıdır (Dockerfile `postgresql16-client` kurar).
- `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD` değerlerini kontrol edin.
- `BACKUP_DIR` yazılabilir olmalıdır (`/app/backups` volume).
- Hata mesajı Ayarlar ekranındaki yedek geçmişinde ve uygulama logunda görünür.
