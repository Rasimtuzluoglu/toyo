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

### Yerel kullanım (yalnızca bu bilgisayar)
Bu kurulum yalnızca üzerinde çalıştığı bilgisayar için tasarlanmıştır. Uygulama `127.0.0.1` adresine bağlanır; yerel ağdan veya internetten erişilemez. PostgreSQL portu da dışarı yayınlanmaz.

- **Varsayılan ve önerilen:** `APP_BIND_ADDRESS=127.0.0.1`; uygulama yalnızca bu bilgisayardan açılır.
- Uygulamayı `0.0.0.0` adresine bağlayarak yerel ağa açmayın; HTTP üzerinde çalıştığı için oturum bilgileri ağda açık gider. Uzaktan erişim gerekiyorsa TLS sonlandıran bir reverse proxy ayrıca kurulmalıdır.
- **İlk kurulum:** `.env` içinde `SETUP_TOKEN` tanımlayın; `/setup` yalnızca `http://localhost:18080/setup?token=...` ile açılır. Token boşsa kurulum yalnızca doğrudan loopback bağlantısında açılır; Docker üzerinden erişimde token zorunludur.
- Windows Güvenlik Duvarı'nda 18080 portu için gelen kural açmayın; gerekmiyor.
- Modem/router üzerinde bu port için **port yönlendirme yapmayın**.
- Yedekleri aynı bilgisayarda tutmayın; harici disk/NAS için kaynak kurulumda `scripts/setup-backup-copy.ps1` çalıştırın veya **Yedek Al ve İndir** ile dışa alın.
- Dağıtım paketinde `BACKUP_OFFSITE_DIR` doldurulursa sağlık gözetmeni yedekleri her 15 dakikada harici hedefe kopyalar.
- Harici disk sonradan takıldıysa kaynak kurulumda kaçırılan yedekleri `scripts/offline-backup.ps1` ile kopyalayabilirsiniz.

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

### Yedek klasörü

Kaynak koddan kurulumda yedek konumu `.env` içindeki `BACKUP_HOST_DIR` değeriyle belirlenir ve Docker bu klasörü container içindeki `/app/backups` yoluna bağlar. Böylece hem manuel hem zamanlanmış yedekler doğrudan bilgisayardaki klasöre düşer.

Klasörü sonradan değiştirmek için:

```powershell
notepad .env
# BACKUP_HOST_DIR=C:/ToyotaServis-Yedekler
docker compose up -d
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

- **Manuel güncelleme:** Ayarlar → **Kontrol Et** → **Güncelle ve Yeniden Başlat**. Uygulama 1-3 dakika erişilemez olabilir ve oturumlar kapanır. Otomatik güncelleme varsayılan olarak **kapalıdır** (`APP_UPDATE_ENABLED=false`); açmadan önce aşağıdaki imza şartını okuyun.
- **Otomatik güncelleme:** `APP_UPDATE_ENABLED=true` ve ajan `UPDATE_AUTO=true` ile başlatılırsa 5 dakikada bir kontrol edilir. Yalnızca mesai dışında tercih edin.
- **Commit imzası zorunlu:** `UPDATE_REQUIRE_SIGNED=true` (varsayılan) iken ajan yalnızca `security/allowed_signers` içindeki güvenilen SSH anahtarıyla imzalanmış commit'leri uygular (`git -c gpg.format=ssh -c gpg.ssh.allowedSignersFile=... verify-commit`). İmzasız veya farklı anahtarla imzalanmış commit gelirse güncelleme durdurulur. Geliştirme ortamında `.env` içinde `UPDATE_REQUIRE_SIGNED=false` yapılabilir; üretimde yapmayın.
- Her güncellemeden önce seçilen yedek klasörüne `pre-update-YYYYMMDD-HHmmss.dump` yedeği otomatik alınır. Yedek, app kapalı olsa bile `db` konteynerinden alınır; alınamazsa güncelleme **durdurulur**.
- Ajan, yalnızca hızlı ileri alma (`git pull --ff-only`) yapar. Kirli çalışma ağacı varsa güncelleme **hata** olarak durur; önce commit/stash yapılmalıdır.
- Sağlık kontrolü başarısız olursa önceki imaja ve önceki commit'e otomatik dönülür. Veritabanı şeması değiştiyse imaj geri dönüşü tek başına yetmez; güncelleme öncesi yedeği geri yükleyin.
- GitHub deposu özel ise `APP_UPDATE_GITHUB_TOKEN` ortam değişkenini tanımlayın.
- Güncelleme günlükleri: `updates/update.log` ve `updates/agent.log`.

> Ajan host yetkisiyle çalışır; yalnızca güvenli ve yalnızca bu iş için kullanılan bilgisayarda çalıştırın.

### Parola ve .env güvenliği

- `.env` dosyasını yalnızca geçerli Windows kullanıcısı, SYSTEM ve Administrators okuyabilsin diye izinleri sıkılaştırın:
  ```powershell
  powershell -ExecutionPolicy Bypass -File .\scripts\harden-env.ps1
  ```
- Veritabanı parolasını belirli aralıklarla değiştirin (önce yedek alın):
  ```powershell
  powershell -ExecutionPolicy Bypass -File .\scripts\rotate-db-password.ps1
  ```
  Betik yeni parolayı üretir, `ALTER ROLE` ile veritabanında değiştirir, `.env` dosyasını günceller ve uygulamayı yeniden başlatıp sağlık kontrolü yapar. Eski `.env` yedeğini güvenli şekilde silin.
- PATRON veya başka bir kullanıcının parolası unutulursa, sunucuya yerel erişimle:
  ```powershell
  powershell -ExecutionPolicy Bypass -File .\scripts\reset-user-password.ps1 -Username patron
  ```
  Parola verilmezse güçlü bir geçici parola üretilir. İşlem açık oturumları kapatmak için uygulamayı yeniden başlatır.
- `.env` dosyasını e-posta/sohbet ile paylaşmayın; yedeklerini erişimi kısıtlı ayrı bir diskte tutun.

### Temel komutlar

```bash
docker compose ps
docker compose logs -f app
docker compose logs -f db
docker compose restart app
docker compose down
```

> **Uyarı:** `docker compose down -v` komutu `servisdefteri-pgdata` volume'ını siler; veritabanı kayıtları geri döndürülemez biçimde kaybolur. Yedekler `BACKUP_HOST_DIR` ile belirtilen host klasöründe tutulduğu için `down -v` bu dosyaları silmez. Normal kapatma için `docker compose down` kullanın.

### Port değiştirme

`.env` içinde:

```env
APP_PORT=8090
```

Uygulama container içinde 18080 dinlemeye devam eder; hostta 8090 açılır.

## 4. İlk Giriş

1. `http://localhost:18080` adresini açın.
2. İlk kurulumda `/setup` ekranına yönlendirilirsiniz. Bu ekran yalnızca bir kez açılır.
3. Ad soyad, kullanıcı adı, güçlü parola girin. Bu hesap **PATRON** rolüyle oluşturulur.
4. Giriş yaptıktan sonra **Kullanıcılar** ekranından **MUHASEBE** ve **SERVIS** hesaplarını oluşturun.

Parola politikası: en az 10 karakter, en az bir büyük harf, bir küçük harf ve bir rakam. Parolalar BCrypt strength 12 ile hashlenir. Her kullanıcı sağ üst menüden kendi parolasını değiştirebilir.

İlk girişten sonra **Ayarlar → İşletme bilgileri** bölümünde işletme adı, telefon, adres ve vergi bilgilerini girin; iş emri, makbuz ve ekstre çıktılarında bu bilgiler kullanılır.

## 5. Testler

```bash
mvn test
```

- Testcontainers gerçek PostgreSQL 16 container'ı başlatır; Docker çalışıyor olmalıdır.
- İlk çalıştırmada `postgres:16-alpine` imajı indirilir.
- Raporlar: `target/surefire-reports/`
- Sadece birim testleri: `mvn "-Dtest=*Test" test`
- Belirli bir sınıf: `mvn "-Dtest=ServisDefteriFlowIT" test`

Testler üretim veritabanınıza dokunmaz; ayrı container kullanır.

## 6. Sağlık Kontrolü

```bash
curl http://localhost:18080/actuator/health
```

Beklenen:

```json
{"status":"UP"}
```

Ayrıntılar güvenlik nedeniyle gizlenir. Uygulama container'ı, veritabanı `healthy` olmadan başlamaz (`depends_on: condition: service_healthy`).

Windows sağlık gözetmeni (15 dakikada bir):

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\install-health-check.ps1
```

Görev adı `ToyotaServisHealthCheck`; uygulama sağlığını, son başarılı yedeğin yaşını (36 saat) ve boş disk alanını (5 GB) kontrol eder. Sorun varsa uygulamayı yeniden başlatır, `msg.exe` ile yerel uyarı verir ve günlük yazar. Durum dosyası `updates/health.status`, günlük `logs/health-check.log`.

## 7. Üretim Benzeri Kullanım Notları

- Uygulama yerel ağ için HTTP üzerinde çalışır. İnternete açmayın.
- Dış erişim gerekiyorsa HTTPS sonlandıran bir ters proxy arkasında ve güvenlik duvarı kurallarıyla kullanın.
- TLS arkasında `SESSION_COOKIE_SECURE=true` ve güvenilen ters proxy kullanıyorsanız `FORWARD_HEADERS_STRATEGY=framework` ayarlayın.
- `APP_PORT` yalnızca gerekli arayüzlere açılmalıdır.
- PostgreSQL portunu yayınlamayın.
- `.env` dosya izinlerini kısıtlayın.
- Düzenli yedek alın ve geri yükleme testi yapın: `docs/BACKUP-RESTORE.md`.

## 8. Sorun Giderme

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
- `BACKUP_HOST_DIR` ile seçilen bilgisayar klasörü yazılabilir olmalı ve Docker Desktop bu sürücüye erişebilmelidir.
- Hata mesajı Ayarlar ekranındaki yedek geçmişinde ve uygulama logunda görünür.
