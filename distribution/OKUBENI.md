# Toyota Plaza Öztoprak · Kurulum Paketi

Bu klasör, uygulamayı müşteri bilgisayarına **kaynak kod olmadan** kurmak için hazırlanmıştır.
Uygulama Docker imajı olarak gelir; Java/Maven/PostgreSQL kurmanız gerekmez.

## Paket içeriği

| Dosya | Görev |
|---|---|
| `ToyotaServis-Kurulum.cmd` | Yönetici olarak çalıştırılan otomatik kurulum |
| `ToyotaServis-1.0.0.tar` | Uygulama + PostgreSQL Docker imajları |
| `docker-compose.dist.yml` | Çalışma zamanı compose dosyası |
| `ToyotaServis-Baslat.cmd` | Docker ve uygulamayı başlatır |
| `ToyotaServis-Durdur.cmd` | Uygulamayı durdurur (veriler korunur) |
| `ToyotaServis-Guncelle.cmd` | Yeni imaj paketiyle güncelleme |
| `scripts/` | Yedek kopyası, parola rotasyonu ve `.env` izin araçları |
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
2. `ToyotaServis-Kurulum.cmd` dosyasına sağ tıklayıp **Yönetici olarak çalıştır** seçin.
3. UAC izin ekranını onaylayın.
4. Kurulum şunları otomatik yapar:
   - Docker Desktop yoksa kurmayı dener (winget veya resmi installer).
   - Docker Desktop'ı başlatır ve hazır olmasını bekler.
   - Uygulama imajını Docker'a yükler.
   - `C:\ToyotaServis` altında çalışma dosyalarını, rastgele veritabanı parolasını ve tek kullanımlık kurulum tokenını oluşturur.
   - Birincil ve ikinci yedek klasörlerini sorar (ikinci kopya için harici disk önerilir).
   - Veritabanını ve uygulamayı başlatır.
   - Windows güvenlik duvarında ek bir port açmaz; uygulama yalnızca bu bilgisayardan kullanılır.
   - `.env` dosyasının erişim izinlerini sıkılaştırır.
   - Masaüstüne **Toyota Servis** kısayolunu ve oturum açılışında başlatan görevi ekler.
5. Kurulum sonunda tarayıcıda **token içeren `/setup` adresi** açılır.
6. İlk PATRON (işletme sahibi) hesabını oluşturun. Bu ekran yalnızca bir kez açılır. Token `C:\ToyotaServis\.env` içindeki `SETUP_TOKEN` değeridir.

Docker Desktop kurulumu yeniden başlatma isterse: bilgisayarı yeniden başlatın ve
`ToyotaServis-Kurulum.cmd` dosyasını tekrar çalıştırın. Kurulu adımlar atlanır.

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
- Kurulum sırasında seçtiğiniz klasöre otomatik yedek yazılır (varsayılan: `%USERPROFILE%\ToyotaServis-Yedekler`).
- İkinci kopya da yazılır (varsayılan: `%USERPROFILE%\ToyotaServis-Yedekler-2`); harici disk/NAS için kurulumda o yolu seçin.
- Harici diski sonradan bağlarsanız:
  ```powershell
  powershell -ExecutionPolicy Bypass -File C:\ToyotaServis\scripts\setup-backup-copy.ps1 -DriveLetter E
  ```
- Diskte kaçırılan yedekleri tamamlamak için:
  ```powershell
  powershell -ExecutionPolicy Bypass -File C:\ToyotaServis\scripts\offline-backup.ps1
  ```
- Yedek klasörünü değiştirmek için `.env` içindeki `BACKUP_HOST_DIR` / `BACKUP_COPY_HOST_DIR` değerlerini güncelleyip servisleri yeniden başlatın.
- Veritabanı verisi `toyota-servis-pgdata` volume'undadır.

> `C:\ToyotaServis\.env` dosyasını kaybetmeyin. Veritabanı parolası ve kurulum tokenı burada tutulur.
> Bu dosyayı yedekleyin; üçüncü kişilerle paylaşmayın. İzinleri sıkılaştırmak için:
> `powershell -ExecutionPolicy Bypass -File C:\ToyotaServis\scripts\harden-env.ps1`
> Parola değiştirmek için: `...\scripts\rotate-db-password.ps1`

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
| 18080 portu kullanımda | Başka bir uygulamayı kapatın veya `C:\ToyotaServis\.env` içinde `APP_PORT` değiştirip yeniden başlatın |
| Uygulama açılmıyor | `docker compose -f C:\ToyotaServis\docker-compose.dist.yml --env-file C:\ToyotaServis\.env logs app` |
| Kurulum ekranı açılmıyor | Adresin sonundaki `?token=...` bölümünün `.env` içindeki `SETUP_TOKEN` ile aynı olduğunu kontrol edin |

Kurulum günlüğü: `C:\ToyotaServis\kurulum.log`
