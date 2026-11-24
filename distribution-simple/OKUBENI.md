# Toyota Plaza Öztoprak · Basit Kurulum

Bu paket, uygulamayı **yedekleme olmadan** kurar. Amaç en az sayıda otomatik işlem ve
en az hata kaynağıdır.

## Paket içeriği

| Dosya | Görev |
|---|---|
| `KURULUM.cmd` | Tek tıkla kurulum |
| `BENIOKU.txt` | Hızlı başlangıç |
| `ToyotaServis-Kurulum.cmd` | Kurulum betiği |
| `ToyotaServis-1.0.0.tar` | Uygulama + PostgreSQL imajları |
| `docker-compose.dist.yml` | Çalışma zamanı compose dosyası |
| `ToyotaServis-Baslat.cmd` | Uygulamayı başlatır |
| `ToyotaServis-Durdur.cmd` | Uygulamayı durdurur (veriler korunur) |
| `scripts/` | `.env` izinleri ve acil parola sıfırlama |

## Bu pakette OLMAYAN özellikler

- Otomatik yedekleme **yok**
- Manuel yedekleme **yok**
- Yedek klasörü / `BACKUP_HOST_DIR` **yok**
- Yedekten geri yükleme **yok**
- Offsite yedek **yok**
- Otomatik sağlık görevi **yok**
- Otomatik güncelleme **yok**
- Otomatik başlatma kısayolu **yok**
- Docker Desktop otomatik kurulumu **yok** (Docker kurulu olmalıdır)

> **Önemli:** Yedekleme olmadığı için disk arızası, yanlış silme veya volume kaybı
> durumunda veri kurtarma imkânı yoktur. Bu riski kabul ederek kullanın.

## Gereksinimler

- Windows 10/11 64-bit
- Yönetici yetkisi
- Docker Desktop (Linux containers modunda)
- WSL2 ve BIOS sanallaştırma
- En az 8 GB RAM, 10 GB boş disk

Docker Desktop kurulu değilse kurulum durur ve uyarı verir; otomatik indirme/kurulum yapmaz.

## Kurulum

1. ZIP’i yazılabilir bir klasöre çıkarın. Örnek: `D:\ToyotaServis`
2. `KURULUM.cmd` dosyasına çift tıklayın.
3. UAC iznini onaylayın.
4. Kurulum sonunda **token içeren** `/setup?token=...` ekranı açılır; PATRON hesabını oluşturun.
   Token olmadan `/setup` açılırsa güvenlik nedeniyle **403** döner. Token `.env` içindeki
   `SETUP_TOKEN` değeridir.

Kurulum yeri, `KURULUM.cmd` dosyasının bulunduğu klasördür. Farklı bir klasör için
`TOYOTA_INSTALL_DIR` kullanılabilir:

```bat
set TOYOTA_INSTALL_DIR=D:\ToyotaServis
KURULUM.cmd
```

## Günlük kullanım

```text
Başlat : D:\ToyotaServis\ToyotaServis-Baslat.cmd
Durdur : D:\ToyotaServis\ToyotaServis-Durdur.cmd
Adres  : http://localhost:18080
```

Bilgisayar açılışında uygulama **otomatik başlamaz**. Docker Desktop açıldıktan sonra
`ToyotaServis-Baslat.cmd` ile başlatabilirsiniz.

Docker Desktop'ın da oturum açılışında başlamasını istemiyorsanız:
Docker Desktop → **Settings → General → Start Docker Desktop when you sign in** seçeneğini kapatın.

## Acil parola sıfırlama

```powershell
powershell -ExecutionPolicy Bypass -File D:\ToyotaServis\scripts\reset-user-password.ps1 -Username patron
```

## Kaldırma

```bat
D:\ToyotaServis\ToyotaServis-Durdur.cmd
```

Tamamen kaldırmak için:

```powershell
docker volume rm toyota-servis-pgdata
```

> `docker volume rm toyota-servis-pgdata` veritabanını kalıcı olarak siler.
> Bu pakette yedek olmadığı için geri dönüş yoktur.

## Sorun giderme

| Sorun | Çözüm |
|---|---|
| Docker Desktop bulunamadı | Docker Desktop’ı kurun, Linux containers modunda açın |
| Docker Desktop hazır olmadı | `wsl --update`, `wsl --shutdown`, Docker Desktop’ı yeniden açın |
| Linux containers modunda değil | Tepsi simgesi → **Switch to Linux containers** |
| 18080 portu kullanımda | Başka uygulamayı kapatın veya `.env` içinde `APP_PORT` değiştirin |
| Uygulama açılmıyor | `kurulum.log` ve `%TEMP%\ToyotaServis-Basit-Kurulum.log` dosyalarına bakın |
| Ayarlarda yedekleme görünmüyor | Bu pakette yedekleme kapalıdır; beklenen davranış |
