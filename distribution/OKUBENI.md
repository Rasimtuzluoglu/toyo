# Toyota Plaza Öztoprak · Kurulum Paketi

Bu klasör, uygulamayı müşteri bilgisayarına **kaynak kod olmadan** kurmak için hazırlanmıştır.
Uygulama yalnızca **derlenmiş Docker imajı** olarak gelir; `.java`, `pom.xml`, testler veya
proje kaynakları pakette bulunmaz. Java/Maven/PostgreSQL kurmanız gerekmez.

> Docker imajı teknik olarak incelenebilir; bu nedenle "kaynak kod pakette yoktur" denir,
> ancak derlenmiş uygulama dosyaları Docker erişimi olan biri tarafından görülebilir.

## Paket içeriği

| Dosya | Görev |
|---|---|
| `KURULUM.cmd` | **Tek tıkla kurulum** (yönetici olarak çalıştırın) |
| `BENIOKU.txt` | 3 adımlık hızlı başlangıç |
| `ToyotaServis-Kurulum.cmd` | Kurulumun ana betiği |
| `ToyotaServis-1.0.0.tar` | Uygulama + PostgreSQL Docker imajları |
| `docker-compose.dist.yml` | Çalışma zamanı compose dosyası |
| `ToyotaServis-Baslat.cmd` | Docker ve uygulamayı başlatır |
| `ToyotaServis-Durdur.cmd` | Uygulamayı durdurur (veriler korunur) |
| `ToyotaServis-Guncelle.cmd` | Yeni imaj paketiyle güncelleme |
| `scripts/` | Sağlık gözetmeni, offsite yedek, parola rotasyonu ve `.env` izin araçları |
| `OKUBENI.md` | Bu dosya |

## Gereksinimler

- Windows 10/11 64-bit
- Yönetici yetkisi
- Sanallaştırma (BIOS) ve WSL2
- En az 8 GB RAM, 10 GB boş disk
- Docker Desktop kurulumu için internet (uygulama çalışırken internet gerekmez)

> Docker Desktop, Docker Inc. tarafından sunulan üçüncü taraf bir üründür. Büyük ölçekli
> ticari kullanımda Docker Desktop aboneliği gerekebilir; lisans koşullarını kontrol edin.

## Kurulum

1. Bu klasörün **tamamını** müşteri bilgisayarına kopyalayın (USB/klasör).
2. `KURULUM.cmd` dosyasına çift tıklayın; UAC izin ekranını onaylayın.
3. Kurulum şunları otomatik yapar:
   - Docker Desktop yoksa kurmayı dener (winget veya resmi installer).
   - Docker Desktop'ı başlatır ve hazır olmasını bekler.
   - Docker Desktop'ın **Linux containers** modunda olduğunu kontrol eder; değilse uyarır.
   - Uygulama ve veritabanı imajlarını Docker'a yükler.
   - `C:\ToyotaServis` altında çalışma dosyalarını, rastgele veritabanı parolasını ve tek kullanımlık kurulum tokenını oluşturur.
   - **Hiçbir klasör yolu sormaz.** Yedekler otomatik olarak `%USERPROFILE%\ToyotaServis-Yedekler` klasörüne yazılır.
   - Veritabanını ve uygulamayı başlatır.
   - Windows güvenlik duvarında ek bir port açmaz; uygulama yalnızca bu bilgisayardan kullanılır.
   - `.env` dosyasının erişim izinlerini sıkılaştırır.
   - Masaüstüne **Toyota Servis** kısayolunu ve oturum açılışında başlatan görevi ekler.
   - **15 dakikalık sağlık gözetmeni** kurar; uygulama yanıt vermezse yeniden başlatır.
4. Kurulum sonunda tarayıcıda **token içeren `/setup` adresi** açılır.
5. İlk PATRON (işletme sahibi) hesabını oluşturun. Bu ekran yalnızca bir kez açılır. Token `C:\ToyotaServis\.env` içindeki `SETUP_TOKEN` değeridir.

Docker Desktop kurulumu yeniden başlatma isterse: bilgisayarı yeniden başlatın ve
`KURULUM.cmd` dosyasını tekrar çalıştırın. Kurulu adımlar atlanır.

## Giriş adresleri

| Kullanım | Adres |
|---|---|
| Bu bilgisayar | `http://localhost:18080` |
| İlk kurulum | `http://localhost:18080/setup?token=...` (token `.env` içinde) |
| Giriş | `http://localhost:18080/login` |

Uygulama yalnızca bu bilgisayardan (`127.0.0.1`) erişilebilir. Yerel ağa veya internete
açılmaz; bu nedenle telefon/tablet ile uzaktan kullanım desteklenmez.

> **Güvenlik:** Uygulama HTTP üzerinde yerel olarak çalışır ve yalnızca `127.0.0.1` adresine
> bağlanır. Uygulamayı yerel ağa açmayın; uzaktan erişim gerekiyorsa TLS sonlandıran bir
> reverse proxy ve `SESSION_COOKIE_SECURE=true` yapılandırması ayrıca ele alınmalıdır.

## Günlük kullanım

- Masaüstündeki **Toyota Servis** kısayolu uygulamayı açar.
- Bilgisayar açıldığında `ToyotaServis-Baslat.cmd` otomatik çalışır; Docker Desktop ve
  uygulama arka planda başlar.
- Uygulamayı durdurmak için `C:\ToyotaServis\ToyotaServis-Durdur.cmd`.
- Yeniden başlatmak için `C:\ToyotaServis\ToyotaServis-Baslat.cmd`.

## Güncelleme

1. Yeni sürümün `ToyotaServis-Yeni.tar` dosyasını `C:\ToyotaServis` klasörüne kopyalayın.
2. `ToyotaServis-Guncelle.cmd` dosyasını çalıştırın.
3. Güncelleme öncesi veritabanı yedeği otomatik alınır.
4. Yeni imaj yüklenir, uygulama yeniden başlatılır ve sağlık kontrolü yapılır.
5. Yeni sürüm sağlıklı başlamazsa eski imaja otomatik geri dönülür. Veritabanı şeması
   değiştiyse imaj geri dönüşü tek başına yeterli olmaz; güncelleme öncesi yedeği geri
   yüklemeniz gerekir.

## Yedekleme

- Uygulama her gün saat 02:00'de otomatik yedek alır.
- Bilgisayar 02:00'de kapalıysa sonraki açılışta 36 saatten eski yedek telafi edilir.
- Ayarlar → Yedekleme ekranından manuel yedek alabilir ve yedeği indirebilirsiniz.
- Güncelleme ajanı kuruluysa PATRON, **Ayarlar → Yedekleme → Geri Yükle** ile başarılı bir yedeği uygulama içinden geri yükleyebilir; işlem öncesi güvenlik yedeği otomatik alınır.
- **Tek yedek klasörü:** `%USERPROFILE%\ToyotaServis-Yedekler`. Kurulum hiçbir yol sormaz; klasör yoksa otomatik oluşturulur.
- Veritabanı verisi `toyota-servis-pgdata` Docker volume'ündedir.

### Offsite (harici disk/NAS) yedek

Varsayılan yedekler bilgisayarın C: diskindedir. Disk arızasına karşı ikinci bir kopya için:

1. Harici diski/NAS paylaşımını bağlayın.
2. `C:\ToyotaServis\.env` içine hedef yolu ekleyin (örnek):
   `BACKUP_OFFSITE_DIR=E:\ToyotaServis-Yedekler`
3. Sağlık gözetmeni her 15 dakikada yeni `.dump` dosyalarını hedefe kopyalar.
4. Elle kopyalamak için:
   `powershell -ExecutionPolicy Bypass -File C:\ToyotaServis\scripts\backup-offsite.ps1`

> Veritabanı yedeği müşteri ve araç bilgileri içerir. Harici diski BitLocker ile şifreleyin,
> yalnızca yetkili kişilerin erişebildiği bir yerde saklayın. Bulut klasörünü (Google Drive,
> OneDrive vb.) hedef göstermeden önce verinin buluta çıkmasının işletme açısından uygun
> olduğundan emin olun.

### Sağlık gözetmeni

- Görev adı: `ToyotaServisHealthCheck` (15 dakikada bir).
- Uygulama sağlığı, son yedek yaşı (36 saat) ve boş disk (5 GB) kontrol edilir.
- Sorun varsa uygulama yeniden başlatılır, uyarı penceresi açılır ve günlük yazılır.
- Durum: `C:\ToyotaServis\updates\health.status`
- Günlük: `C:\ToyotaServis\logs\health-check.log`
- Görevi yeniden kurmak için:
  `powershell -ExecutionPolicy Bypass -File C:\ToyotaServis\scripts\install-health-check.ps1`

> Yedek klasörünü ileri düzeyde değiştirmek isterseniz `C:\ToyotaServis\.env` içindeki
> `BACKUP_HOST_DIR` değerini düzenleyip `C:\ToyotaServis\ToyotaServis-Baslat.cmd` ile
> yeniden başlatın. Normal kullanımda buna gerek yoktur.

> `C:\ToyotaServis\.env` dosyasını kaybetmeyin. Veritabanı parolası ve kurulum tokenı burada tutulur.
> Bu dosyayı yedekleyin; üçüncü kişilerle paylaşmayın. İzinleri sıkılaştırmak için:
> `powershell -ExecutionPolicy Bypass -File C:\ToyotaServis\scripts\harden-env.ps1`
> Parola değiştirmek için: `...\scripts\rotate-db-password.ps1`

## Acil parola sıfırlama

PATRON veya başka bir kullanıcının parolası bilinmiyorsa, sunucuya yerel erişimi olan kişi
aşağıdaki komutla parolayı sıfırlayabilir:

```powershell
powershell -ExecutionPolicy Bypass -File C:\ToyotaServis\scripts\reset-user-password.ps1 -Username patron
```

Parola verilmezse güçlü bir geçici parola üretilir ve ekranda gösterilir. İşlem sonunda
uygulama yeniden başlatılır ve açık oturumlar kapanır. Yeni parolayla giriş yaptıktan sonra
parolayı değiştirin. Bu betiği yalnızca yetkili kişiler çalıştırmalıdır.

## Kaldırma

Uygulamayı durdurmak verileri silmez:

```bat
C:\ToyotaServis\ToyotaServis-Durdur.cmd
```

Tamamen kaldırmak için önce durdurun, sonra Docker Desktop'ta şu volume'u silin:

- `toyota-servis-pgdata`

Yedekler Docker volume'unda değil, `.env` içindeki `BACKUP_HOST_DIR` klasöründe tutulur.
**Volume'u silmek veritabanını kalıcı olarak siler.** Gerekirse önce yedek indirin.
Ardından `C:\ToyotaServis` klasörünü kaldırabilirsiniz.

## Kaynak kod ve güvenlik

- Bu paket **kaynak kod içermez**; yalnızca çalıştırılabilir Docker imajı, compose dosyası
  ve kurulum betikleri vardır.
- Kaynak deponuzu müşteriyle paylaşmayın. GitHub deposunu **private** yapmanız önerilir;
  aksi halde kaynak kod GitHub üzerinden görülebilir.
- Uygulama internete açılmaz; yalnızca bu bilgisayarda (`127.0.0.1`) çalışır.
- İlk kurulumdan sonra PATRON hesabının parolasını güçlü tutun.
- Docker imajı teknik olarak incelenebilir; Java kaynak dosyaları derlenmiş olarak gelir.

## Sorun giderme

| Sorun | Çözüm |
|---|---|
| Docker Desktop açılmıyor | Bilgisayarı yeniden başlatın; WSL2 ve sanallaştırmanın açık olduğunu kontrol edin |
| `docker` komutu bulunamadı | Docker Desktop'ı açın veya bilgisayarı yeniden başlatın |
| C:\ToyotaServis yazılamıyor | Kurulum otomatik olarak `C:\ProgramData\ToyotaServis` veya `C:\Users\Public\ToyotaServis` dener. EXE'yi yönetici olarak çalıştırın, antivirüsü kontrol edin. Log: `%TEMP%\ToyotaServis-Kurulum.log` |
| Yedek klasörü oluşturulamıyor | Kurulum sırasıyla `%USERPROFILE%`, `%PUBLIC%` ve kurulum klasörünü dener; hiçbiri olmazsa hata verir. |
| 18080 portu kullanımda | Başka bir uygulamayı kapatın veya `C:\ToyotaServis\.env` içinde `APP_PORT` değiştirip yeniden başlatın |
| Uygulama açılmıyor | `docker compose -f C:\ToyotaServis\docker-compose.dist.yml --env-file C:\ToyotaServis\.env logs app` |
| Kurulum ekranı açılmıyor | Adresin sonundaki `?token=...` bölümünün `.env` içindeki `SETUP_TOKEN` ile aynı olduğunu kontrol edin |
| Sağlık uyarısı alıyorum | `C:\ToyotaServis\updates\health.status` ve `logs\health-check.log` dosyalarına bakın; disk/yedek/uygulama durumunu gösterir |
| Offsite kopya oluşmuyor | `.env` içindeki `BACKUP_OFFSITE_DIR` yolunun bağlı ve yazılabilir olduğunu kontrol edin; `backup-offsite.ps1` çıktısına bakın |
| PATRON parolası unutuldu | `scripts\reset-user-password.ps1 -Username patron` ile yerel olarak sıfırlayın |

Kurulum günlüğü: `C:\ToyotaServis\kurulum.log`
