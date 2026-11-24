# Yedekleme ve Geri Yükleme

## Genel Bakış

Toyota Plaza Öztoprak servis yönetimi, PostgreSQL yedeğini `pg_dump` ile **custom format** (`-Fc`) alır.

- Otomatik: varsayılan her gün 02:00 (`BACKUP_CRON=0 0 2 * * *`)
- Manuel: **Ayarlar → Sunucuda Yedekle** veya **Yedek Al ve İndir** (yalnızca PATRON)
- Dosya adı: `servisdefteri-YYYYMMdd-HHmmss.dump`
- Konum: `.env` içindeki **bilgisayardaki yedek klasörü** (`BACKUP_HOST_DIR`); Docker içinde `/app/backups` yoluna bağlanır
- İkinci kopya: `BACKUP_COPY_HOST_DIR` ile seçilen host klasörü (harici disk/NAS) container içinde `/app/backup-copy` yoluna bağlanır; her başarılı yedek buraya da yazılır
- Offsite kopya: `BACKUP_OFFSITE_DIR` doluysa sağlık gözetmeni her 15 dakikada yeni `.dump` dosyalarını bu hedefe kopyalar
- Saklama: varsayılan 14 gün (`BACKUP_RETENTION_DAYS`); süresi dolan `.dump` dosyaları silinir, ancak en yeni 5 yedek (`BACKUP_MIN_COPIES`) her zaman korunur, veritabanındaki `backup_runs` kayıtları kalır
- Her yedek, başarılı sayılmadan önce `pg_restore --list` ile okunabilirlik açısından doğrulanır; yarım kalan dosyalar silinir
- Sonuçlar `backup_runs` tablosuna ve denetim kaydına yazılır
- Bilgisayar 02:00'de kapalıysa, sonraki açılışta son başarılı yedek 36 saatten eskiyse **telafi yedeği** otomatik alınır
- Yedek dosyaları web kökünde değildir; indirme yalnızca PATRON oturumuyla yapılır

> **Önemli:** Otomatik yedek alınması, geri yüklemenin başarılı olduğunu kanıtlamaz. Aşağıdaki geri yükleme testi düzenli olarak yapılmalıdır.

## Tatbikat Sonucu

- Tarih: **24.11.2026** (önceki tatbikat: 12.11.2026)
- Yöntem: `scripts/restore-drill.ps1` ile üretim veritabanından yedek alındı, `servisdefteri_restore_test` veritabanına `pg_restore` ile geri yüklendi. Betik üretim ve geri yüklenen veritabanının kayıt sayılarını ve cari toplamını otomatik karşılaştırır.
- Doğrulama: `customers=1/1`, `vehicles=1/1`, `work_orders=1/1`, `payments=1/1`, `account_transactions=1/1`, cari toplam `1555.00/1555.00` (üretim/geri yüklenen). Test veritabanı ve geçici dump `finally` bloğunda temizlendi.
- Sonuç: **BAŞARILI**

> Bu tatbikat, yedeğin geri yüklenebildiğini ve kayıt sayıları ile cari toplamın korunduğunu gösterir; üretim verisiyle gerçek geri yükleme öncesinde yine yedek alınmalıdır.

## Manuel Yedek (Arayüz)

1. PATRON hesabıyla giriş yapın.
2. **Ayarlar** → yedekleme kartı.
3. **Yedek Al ve İndir** düğmesine basın. Tarayıcı “Farklı kaydet” penceresi açarak yedeği bilgisayarınızda istediğiniz klasöre kaydetmenizi sağlar.
4. Yalnızca sunucuda yedek almak isterseniz **Sunucuda Yedekle** düğmesini kullanın.
5. Yedek geçmişinde `Başarılı` durumunu ve dosya boyutunu kontrol edin.
6. Sunucuda alınan yedekleri daha sonra geçmişteki **İndir** bağlantısıyla bilgisayarınıza alabilirsiniz.

### Yedek klasörünü değiştirme

Yedek klasörü `.env` içindeki `BACKUP_HOST_DIR` değeriyle belirlenir.
Değiştirmek için:

```powershell
notepad .env
# BACKUP_HOST_DIR=C:/ToyotaServis-Yedekler
docker compose up -d
```

- Klasör yoksa otomatik oluşturulur.
- Yol Windows tarafında gerçek bir klasördür; container içindeki `/app/backups` bu klasöre bağlanır.
- Boş bırakılırsa `./backups` kullanılır.
- Ayarlar → Yedekleme ekranı seçili klasörü salt okunur gösterir.

## Manuel Yedek (Komut Satırı)

Uygulamanın kendi yedeğini beklemeden, veritabanı container'ından:

```bash
stamp=$(date +%Y%m%d-%H%M%S)
docker compose exec -T db pg_dump -U servisdefteri -d servisdefteri -Fc -f /tmp/manual.dump
docker compose cp db:/tmp/manual.dump "./servisdefteri-manual-$stamp.dump"
docker compose exec -T db rm -f /tmp/manual.dump
```

PowerShell'de:

```powershell
$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
docker compose exec -T db pg_dump -U servisdefteri -d servisdefteri -Fc -f /tmp/manual.dump
docker compose cp db:/tmp/manual.dump ".\servisdefteri-manual-$stamp.dump"
docker compose exec -T db rm -f /tmp/manual.dump
```

> `>` ile doğrudan yönlendirme yapmayın; PowerShell metin kodlaması binary dump dosyasını bozabilir.

> Yedek dosyaları kişisel ve finansal veri içerir. Erişim izinlerini kısıtlayın, e-posta/WhatsApp ile paylaşmayın.

## Yedek Dosyasını Dışarı Kopyalama

```bash
docker compose cp app:/app/backups/servisdefteri-20261028-020000.dump ./yedek/
```

Ayrı bir diske/NAS'a kopyalamak için aynı yöntemi kullanın. Yedek klasörü tek başına yeterli değildir; disk arızasına karşı ikinci kopya tutun.

### Windows klasörüne otomatik yedek

`.env` içinde `BACKUP_HOST_DIR` değerini Windows klasörüne ayarlayın:

```dotenv
BACKUP_HOST_DIR=C:/ToyotaServis-Yedekler
```

Ardından `docker compose up -d` çalıştırın. Otomatik yedekler doğrudan bu klasöre yazılır.

### Offsite (harici disk/NAS) kopya

`.env` içine hedef klasörü ekleyin:

```dotenv
BACKUP_OFFSITE_DIR=E:/ToyotaServis-Yedekler
```

Sağlık gözetmeni her 15 dakikada yeni `.dump` dosyalarını bu klasöre kopyalar. Elle
çalıştırmak için:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\backup-offsite.ps1
```

> Yedekler müşteri ve finansal veri içerir. Harici diski BitLocker ile şifreleyin. Bulut
> klasörünü hedef göstermeden önce verinin buluta çıkmasının işletme açısından uygun
> olduğundan emin olun.

## Arayüzden Geri Yükleme (PATRON)

PATRON, **Ayarlar → Yedekleme → Geri Yükle** ekranından başarılı bir yedeği uygulama içinden geri yükleyebilir.

Gereksinimler:

- Sunucuda `update-agent.ps1` çalışıyor olmalı (Ayarlar ekranı ajanın çevrimiçi olduğunu gösterir).
- Ajan betiği `restore.request` dosyasını tanıyacak sürümde olmalı; eski ajan sürümünde geri yükleme isteği işlenmez.
- Aynı anda bekleyen güncelleme veya geri yükleme isteği olmamalı.

Akış:

1. PATRON yedeği seçer ve onay alanına `GERI YUKLE` yazar.
2. İstek `updates/restore.request` dosyasına yazılır; ajan `scripts/restore-toyota.ps1` betiğini çalıştırır.
3. Betik uygulamayı durdurur, **mevcut veritabanının güvenlik yedeğini** alır ve seçilen yedeği geri yükler.
4. Geri yükleme başarısız olursa güvenlik yedeğine otomatik dönülür; sonuç `restore.status` üzerinden ekranda gösterilir.

> Geri yükleme sırasında uygulama kısa süre erişilemez olur. İşlem öncesi güncel bir yedeği bilgisayarınıza indirmeniz önerilir.

## Geri Yükleme Testi (Önerilen Yöntem)

Üretim veritabanına dokunmadan, ayrı bir veritabanına geri yükleyip doğrulayın.

```bash
# 1) Yedeği container'a kopyala
docker compose cp ./yedek/servisdefteri-20261028-020000.dump db:/tmp/restore.dump

# 2) Test veritabanı oluştur
docker compose exec db createdb -U servisdefteri servisdefteri_restore

# 3) Geri yükle
docker compose exec db pg_restore -U servisdefteri -d servisdefteri_restore \
  --no-owner /tmp/restore.dump

# 4) Kayıtları doğrula
docker compose exec db psql -U servisdefteri -d servisdefteri_restore -c "
  select
    (select count(*) from customers) as musteri,
    (select count(*) from vehicles) as arac,
    (select count(*) from work_orders) as is_emri,
    (select count(*) from payments) as tahsilat,
    (select count(*) from account_transactions) as cari_hareket;
"

# 5) Flyway sürümünü doğrula
docker compose exec db psql -U servisdefteri -d servisdefteri_restore -c "
  select version, description, success from flyway_schema_history order by installed_rank;
"

# 6) Bakiye tutarlılığı örneği
docker compose exec db psql -U servisdefteri -d servisdefteri_restore -c "
  select customer_id,
         sum(case when type='BORC' then amount else -amount end) as bakiye
  from account_transactions
  group by customer_id
  order by customer_id;
"
```

Test veritabanını işiniz bitince silin:

```bash
docker compose exec db dropdb -U servisdefteri servisdefteri_restore
```

## Gerçek Geri Yükleme (Veri Kaybı Riski)

Aşağıdaki işlem mevcut veritabanının yerine yedeği koyar. **Önce mevcut veritabanının yedeğini alın.**

```bash
# 0) Mevcut durumun yedeği
docker compose exec -T db pg_dump -U servisdefteri -d servisdefteri -Fc \
  > servisdefteri-onceki-durum.dump

# 1) Uygulamayı durdur (açık bağlantı kalmasın)
docker compose stop app

# 2) Yedeği container'a kopyala
docker compose cp ./yedek/servisdefteri-20261028-020000.dump db:/tmp/restore.dump

# 3) Yeni veritabanına yükle
docker compose exec db createdb -U servisdefteri servisdefteri_restore
docker compose exec db pg_restore -U servisdefteri -d servisdefteri_restore \
  --no-owner /tmp/restore.dump

# 4) Doğrula (yukarıdaki sorgular)
docker compose exec db psql -U servisdefteri -d servisdefteri_restore -c "select count(*) from users;"

# 5) Veritabanlarını değiştir
docker compose exec db psql -U servisdefteri -d postgres -c \
  "ALTER DATABASE servisdefteri RENAME TO servisdefteri_old;"
docker compose exec db psql -U servisdefteri -d postgres -c \
  "ALTER DATABASE servisdefteri_restore RENAME TO servisdefteri;"

# 6) Uygulamayı başlat
docker compose start app
docker compose logs -f app
```

Doğrulama başarılıysa eski veritabanını daha sonra silebilirsiniz:

```bash
docker compose exec db dropdb -U servisdefteri servisdefteri_old
```

> `servisdefteri_old` veritabanını hemen silmeyin; yeni kurulumun doğru çalıştığından emin olun.

## Geri Yükleme Sonrası Kontrol Listesi

- [ ] `docker compose ps` → db `healthy`, app `Up`
- [ ] `/actuator/health` → `{"status":"UP"}`
- [ ] Giriş yapılabiliyor
- [ ] Dashboard müşteri/araç/iş emri sayıları makul
- [ ] Bir müşteri ekstresi açılıyor, bakiye doğru
- [ ] Tahsilat listesi ve makbuz açılıyor
- [ ] Flyway sürümü beklenen sürümde
- [ ] Yedek geçmişinde yeni kayıtlar oluşuyor

## Sık Yapılan Hatalar

| Hata | Neden / Çözüm |
|---|---|
| `pg_dump: command not found` | Uygulama imajında `postgresql16-client` eksik; Dockerfile'ı yeniden derleyin. |
| `password authentication failed` | `DB_PASSWORD` / `POSTGRES_PASSWORD` uyuşmuyor. |
| `database "servisdefteri_restore" already exists` | Önce `dropdb` ile silin veya farklı ad kullanın. |
| `pg_restore: error: could not execute query` | Sürüm uyumsuzluğu veya kısmi yedek; `pg_restore --verbose` ile inceleyin. |
| Uygulama açılmıyor | Yanlış veritabanı adı/şema; `docker compose logs app` ve Flyway loglarını kontrol edin. |
| Yedek dosyası 0 bayt | Disk dolu veya `pg_dump` başarısız; `backup_runs.message` alanına bakın. |

## Otomatik Yedeklemenin Sınırları

- Container/volume kaybına karşı tek başına korumaz; dış kopya gerekir.
- Yedek şifrelenmez; disk ve erişim güvenliği sizin sorumluluğunuzdadır.
- Geri yükleme prova edilmedikçe yedek "doğrulanmış" sayılmaz.
- Yedek saklama süresi dolduğunda dosyalar otomatik silinir; arşiv gereksinimi varsa dış kopya alın.

