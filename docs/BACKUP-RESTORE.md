# Yedekleme ve Geri Yükleme

## Genel Bakış

Toyota Plaza Öztoprak servis yönetimi, PostgreSQL yedeğini `pg_dump` ile **custom format** (`-Fc`) alır.

- Otomatik: varsayılan her gün 02:00 (`BACKUP_CRON=0 0 2 * * *`)
- Manuel: **Ayarlar → Sunucuda Yedekle** veya **Yedek Al ve İndir** (yalnızca PATRON)
- Dosya adı: `servisdefteri-YYYYMMdd-HHmmss.dump`
- Konum: Ayarlar’daki **Yedek klasörü** alanı; boşsa `BACKUP_DIR` (Docker'da `/app/backups`)
- Saklama: varsayılan 14 gün (`BACKUP_RETENTION_DAYS`); süresi dolan `.dump` dosyaları silinir, veritabanındaki `backup_runs` kayıtları kalır
- Sonuçlar `backup_runs` tablosuna ve denetim kaydına yazılır
- Yedek dosyaları web kökünde değildir; indirme yalnızca PATRON oturumuyla yapılır

> **Önemli:** Otomatik yedek alınması, geri yüklemenin başarılı olduğunu kanıtlamaz. Aşağıdaki geri yükleme testi düzenli olarak yapılmalıdır.

## Tatbikat Sonucu

- Tarih: **05.11.2026**
- Yöntem: `scripts/restore-drill.ps1` ile yeni yedek alındı, `servisdefteri_restore_test` veritabanına `pg_restore` ile geri yüklendi.
- Doğrulama: `users=1`, `customers=0`, `vehicles=0`, `work_orders=0`, `business_settings=1`; test veritabanı temizlendi.
- Sonuç: **BAŞARILI**

> Bu tatbikat, yedeğin geri yüklenebildiğini gösterir; üretim verisiyle gerçek geri yükleme öncesinde yine yedek alınmalıdır.

## Manuel Yedek (Arayüz)

1. PATRON hesabıyla giriş yapın.
2. **Ayarlar** → yedekleme kartı.
3. **Yedek Al ve İndir** düğmesine basın. Tarayıcı “Farklı kaydet” penceresi açarak yedeği bilgisayarınızda istediğiniz klasöre kaydetmenizi sağlar.
4. Yalnızca sunucuda yedek almak isterseniz **Sunucuda Yedekle** düğmesini kullanın.
5. Yedek geçmişinde `Başarılı` durumunu ve dosya boyutunu kontrol edin.
6. Sunucuda alınan yedekleri daha sonra geçmişteki **İndir** bağlantısıyla bilgisayarınıza alabilirsiniz.

### Yedek klasörünü değiştirme

**Ayarlar → Yedek klasörü** alanına sunucu/container içindeki bir yol yazıp **Kaydet** deyin.

- Yol yoksa otomatik oluşturulur; yazılamıyorsa hata gösterilir.
- Boş bırakılırsa `BACKUP_DIR` ortam değişkeni kullanılır.
- Docker’da yazılan yol **container içindedir**. Container yeniden oluşturulduğunda kaybolmaması için bu yolun bir volume olarak bağlanması gerekir.
- Bilgisayarınızdaki bir klasöre kaydetmek için yol yazmak yerine **Yedek Al ve İndir** kullanın.

## Manuel Yedek (Komut Satırı)

Uygulamanın kendi yedeğini beklemeden, veritabanı container'ından:

```bash
docker compose exec -T db pg_dump -U servisdefteri -d servisdefteri -Fc \
  > servisdefteri-manual-$(date +%Y%m%d-%H%M%S).dump
```

PowerShell'de tarih formatı farklıdır:

```powershell
$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
docker compose exec -T db pg_dump -U servisdefteri -d servisdefteri -Fc > "servisdefteri-manual-$stamp.dump"
```

> Yedek dosyaları kişisel ve finansal veri içerir. Erişim izinlerini kısıtlayın, şifreli bir diskte saklayın, e-posta/WhatsApp ile paylaşmayın.

## Yedek Dosyasını Dışarı Kopyalama

```bash
docker compose cp app:/app/backups/servisdefteri-20261028-020000.dump ./yedek/
```

Ayrı bir diske/NAS'a kopyalamak için aynı yöntemi kullanın. `servisdefteri-backups` volume'u tek başına yeterli değildir; disk arızasına karşı ikinci kopya tutun.

### Windows klasörüne otomatik yedek

Docker'da sunucu yolu container içindedir. Windows'taki bir klasöre otomatik yedek almak için `docker-compose.yml` içindeki `app` servisine bir bağlama ekleyin:

```yaml
services:
  app:
    volumes:
      - backups:/app/backups
      - D:/ToyotaYedek:/app/yedek
```

Ardından **Ayarlar → Yedek klasörü** alanına `/app/yedek` yazın. Klasörün Windows'ta mevcut olduğundan emin olun.

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

