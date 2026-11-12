# Mimari

## Genel Yaklaşım

Toyota Plaza Öztoprak servis yönetimi **modüler monolit** olarak tasarlanmıştır. Tek Spring Boot uygulaması, tek PostgreSQL veritabanı ve sunucu tarafında üretilen Thymeleaf HTML kullanılır. Mikroservis, ayrı SPA veya mesaj kuyruğu yoktur; bunlar ilk sürümün karmaşıklığını gereksiz artırırdı.

Modüller paket sınırlarıyla ayrılmıştır:

| Paket | Sorumluluk |
|---|---|
| `customer` | Müşteri kayıtları, arama, cari ekranlarla bağlantı |
| `vehicle` | Araç, sahiplik geçmişi, kilometre |
| `workorder` | İş emri, kalem, durum akışı, günlük iş panosu, kabul/teslim belgeleri |
| `appointment` | Günlük ve haftalık randevu planlama |
| `account` | Cari hareket, bakiye, ekstre, ters kayıt |
| `payment` | Tahsilat, tahsilat dağıtımı |
| `dashboard` | Operasyonel ve finansal özet |
| `report` | Rapor sorguları, CSV ve Excel çıktıları |
| `export` | Müşteri, araç, iş emri ve tahsilat listelerinin CSV/Excel çıktıları |
| `pdf` | İş emri ve tahsilat makbuzu PDF üretimi |
| `demo` | Yalnızca `demo` profilinde çalışan örnek veri yükleyici |
| `audit` | Denetim kaydı |
| `backup` | pg_dump ve yedek geçmişi |
| `security` | Kullanıcı, rol, oturum, ilk kurulum |
| `common` | Hata yönetimi, doğrulama, formatlama, form belirteci |
| `config` | Güvenlik zinciri, Clock, ilk işletme sahibi |

Her modülde controller, service, repository, entity ve gerektiğinde DTO bulunur. Controller'lar HTTP ve model bağlama ile sınırlıdır; iş kuralları service katmanındadır. Entity'ler doğrudan formdan kabul edilmez.

## Katmanlar

```text
Tarayıcı
  │  HTML form / GET
  ▼
Thymeleaf Controller  ──►  Form DTO + Bean Validation
  │
  ▼
Service  ──►  iş kuralları, transaction sınırı, yetki kontrolü
  │
  ▼
Repository (Spring Data JPA)  ──►  PostgreSQL 16
```

- `spring.jpa.open-in-view=false` kullanılır. Lazy ilişkiler service transaction'ı içinde fetch join veya DTO dönüşümüyle çözülür; şablonlarda oturum dışı lazy yükleme yapılmaz.
- Büyük listelerde Spring Data `Pageable` kullanılır. Cari ekstre gibi bakiye sırası gerektiren dökümlerde tarih aralığı zorunludur.
- Tüm parasal alanlar `BigDecimal` ve veritabanında `NUMERIC(14,2)`'dir. `double`/`float` kullanılmaz.

## Cari Hesap Modeli

Hareket tabanlı model kullanılır. Bakiye ayrı bir "bakiye" kolonunda tutulmaz; hareketlerden türetilir:

```text
Bakiye = Σ(BORÇ) − Σ(ALACAK)
```

- İş emri tamamlandığında tek bir `BORC` hareketi oluşur.
- Tahsilat oluşturulduğunda tek bir `ALACAK` hareketi oluşur.
- Tahsilat iptalinde alacak hareketi silinmez; `reversal_of_id` ile bağlı `BORC` ters kaydı oluşur.
- İş emri iptalinde borç hareketi silinmez; `ALACAK` ters kaydı oluşur.
- Ters kayıtlar orijinal hareketi ve denetim izini korur.

`PaymentAllocation` tablosu, bir tahsilatın hangi borç hareketlerine ne kadar dağıtıldığını gösterir. Bakiye hesabının tek kaynağı cari hareketlerdir; dağıtım tablosu borç kalanını ve ekstre detayını besler.

### Mükerrer borçlandırma koruması

İki katman birlikte kullanılır:

1. `AccountService.postWorkOrderDebt` iş emri için mevcut **aktif** `BORC` hareketini kontrol eder.
2. PostgreSQL kısmi benzersiz indeks:

   ```sql
   CREATE UNIQUE INDEX uk_account_tx_work_order_active_debit
     ON account_transactions (work_order_id)
     WHERE work_order_id IS NOT NULL
       AND type = 'BORC'
       AND reversal_of_id IS NULL
       AND active_debit;
   ```

Bu indeks, eşzamanlı iki isteğin aynı iş emrini borçlandırmasını veritabanı seviyesinde de engeller. Tamamlama geri alındığında eski borç `active_debit = FALSE` yapılır ve ters kayıt oluşturulur; iş emri yeniden tamamlanırsa yeni bir aktif borç hareketi açılabilir.

### Eşzamanlılık

Tahsilat oluşturma ve borçlandırma işlemlerinde müşteri satırı `SELECT ... FOR UPDATE` ile kilitlenir. Böylece aynı müşteri için eşzamanlı tahsilat dağıtımları birbirinin kalan borç hesabını bozamaz. `Payment` ve `WorkOrder` entity'lerinde ayrıca `@Version` optimistic lock bulunur.

## İş Emri Durum Akışı

```text
ISLEMDE ──► TAMAMLANDI ──► TESLIM_EDILDI
    │            │
    └────────────┴──► IPTAL
```

- `TAMAMLANDI`: toplam sunucuda yeniden hesaplanır; tutar > 0 ise cari borç oluşturulur ve `debited=true` işaretlenir.
- `TESLIM_EDILDI`: yalnızca `TAMAMLANDI` durumundan geçilebilir. Borçlandırılmamış tutarlı iş emri teslim edilemez.
- `IPTAL`: yalnızca PATRON/MUHASEBE yapabilir. Borç varsa ters kayıt oluşturulur.
- Diğer durum geçişleri (`ISLEMDE`, `TAMAMLANDI`, `TESLIM_EDILDI`) PATRON/SERVIS yetkisindedir. MUHASEBE iş emrini görüntüler ve iptal edebilir; ilerletemez.
- İptal edilmiş veya borçlandırılmış iş emrinde kalem değiştirilemez.
- İş emri iptalinden önce ona bağlı aktif tahsilat varsa işlem reddedilir; önce tahsilat iptal edilmelidir. Bu kural, tahsil edilmiş bir işin sessizce yok edilmesini engeller.

## Günlük İş Panosu

Ayrı bir “gün” tablosu veya kopyalama işi yoktur. Pano her açılışta mevcut iş emirlerini sorgular:

- **Devam eden işler:** `status = 'ISLEMDE'` olan tüm işler. `opened_at` tarihinden bugüne kadar geçen gün sayısı “kaç gündür açık” etiketiyle gösterilir. İş kapanana kadar her gün listede kalır.
- **Bugün biten işler:** `status IN ('TAMAMLANDI','TESLIM_EDILDI')` ve `closed_at` veya `delivered_at` değeri bugünün 00:00–24:00 aralığında olan işler. Ertesi gün bu listeden çıkar.
- Aynı mantık ana paneldeki “Bugün tamamlanan işler” tablosunda da kullanılır.

Bu yaklaşım, günlük listeyi veritabanında çoğaltmadan ve gece yarısı çalışan bir göreve bağlı kalmadan devir davranışını sağlar.

## Güvenlik

- Spring Security form login; BCrypt strength 12.
- CSRF tüm formlarda açık.
- Rol kontrolleri URL seviyesinde (`SecurityFilterChain`), controller metotlarında (`@PreAuthorize`) ve kritik işlemlerde service seviyesinde (ör. tahsilat iptali, iş emri durum geçişi) uygulanır.
- Roller: **PATRON** tüm modüller; **MUHASEBE** tahsilat/cari/rapor ve iş emri iptali; **SERVIS** müşteri/araç/iş emri operasyonu. Finans ekranları SERVIS'e kapalıdır.
- İlk kullanıcı yoksa `/setup`; varsa `/login`. Varsayılan parola yoktur.
- Oturum sabitleme: `migrateSession`; varsayılan oturum süresi 2 saat (`SESSION_TIMEOUT`); HttpOnly + SameSite=Lax çerez.
- Her rol kendi parolasını değiştirebilir; PATRON ayrıca kullanıcı parolası sıfırlayabilir.
- Yedek indirme yalnızca PATRON; dosya adı desen kontrolü ve dizin dışına çıkma engeli vardır.
- Denetim kaydına parola, oturum belirteci veya gereksiz kişisel veri yazılmaz.

## Form Güvenliği

Finansal ve kayıt oluşturan formlarda tek kullanımlık `FormToken` kullanılır. Token oturumda tutulur, POST sırasında tüketilir. CSRF token'ı tek başına aynı formun geri/ileri ile tekrar gönderilmesini engellemez; FormToken bu boşluğu kapatır.

## Hata Yönetimi

- `BusinessRuleException` → 400, kullanıcı dostu mesaj.
- `NotFoundException` → 404.
- `DataIntegrityViolationException` → 409, çakışma mesajı.
- Diğer hatalar → 500; teknik ayrıntı kullanıcıya gösterilmez, sunucu loguna yazılır.
- Form doğrulama hataları alan bazında gösterilir; kullanıcı girdileri korunur.

## Zaman, Para ve Yerelleştirme

- Para: `BigDecimal`, `HALF_UP`, 2 ondalık; gösterim `tr-TR` (`1.234,56 ₺`).
- Tarih/saat: `LocalDate` / `LocalDateTime`; gösterim `dd.MM.yyyy HH:mm`; iş saat dilimi `Europe/Istanbul`.
- Uygulama sabit `tr_TR` locale ile çalışır.
- CSV: UTF-8 + BOM, `;` ayırıcı; Türkçe Excel uyumu hedeflenir.

## Yedekleme

- `BackupService` `pg_dump -Fc` çalıştırır; dosya adı zaman damgalıdır.
- Sonuç `backup_runs` tablosuna yazılır; başarı/başarısızlık denetim kaydına işlenir.
- Saklama süresi dolan `.dump` dosyaları silinir; veritabanı kayıtları silinmez.
- Otomatik yedek, geri yüklemenin doğrulandığı anlamına gelmez. Geri yükleme testi ayrı yapılmalıdır.

## Bilinçli Tasarım Kararları

1. **Ayrı frontend yok:** İlk sürümde Thymeleaf, dağıtım ve bakım maliyetini düşük tutar.
2. **Hareket tabanlı cari:** Bakiye kolonu yerine türetilmiş bakiye, denetlenebilir düzeltme sağlar.
3. **Silme yerine iptal/ters kayıt:** Finansal kayıtlar hard delete edilmez.
4. **Kısmi benzersiz indeks:** Mükerrer borçlandırmayı uygulama kontrolüne ek olarak veritabanında engeller.
5. **Pessimistic lock:** Aynı müşteride eşzamanlı tahsilat dağıtımında tutarlılık sağlar.
6. **Flyway + validate:** Prod'da otomatik `ddl-auto=update` yok; şema yalnızca migration ile değişir.
7. **Tek kullanımlık form belirteci:** Finansal formların kazara iki kez gönderilmesini engeller.
8. **Tek satırlık işletme ayarı:** İşletme adı ve iletişim bilgileri ayrı tabloda tutulur; çıktılarda kullanılır.
9. **Docker volume:** PostgreSQL kalıcı volume üzerindedir; `down -v` veritabanını siler. Yedekler host klasörüne (bind mount) yazılır ve `down -v` ile silinmez.
