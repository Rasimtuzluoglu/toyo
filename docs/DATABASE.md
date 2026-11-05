# Veritabanı

## Genel Bilgiler

- PostgreSQL 16
- Şema adı: `public`
- Para birimi: TRY, tüm parasal alanlar `NUMERIC(14,2)`
- Zaman damgaları: `TIMESTAMP` (uygulama `Europe/Istanbul` saat dilimini kullanır)
- İş tarihleri: `DATE`
- Şema yönetimi: Flyway; üretimde `ddl-auto=validate`

## Tablolar

### `users`

| Kolon | Tip | Kısıt |
|---|---|---|
| `id` | BIGSERIAL | PK |
| `username` | VARCHAR(50) | UNIQUE, NOT NULL |
| `password_hash` | VARCHAR(100) | NOT NULL (BCrypt) |
| `full_name` | VARCHAR(100) | NOT NULL |
| `role` | VARCHAR(20) | CHECK: PATRON, MUHASEBE, SERVIS |
| `enabled` | BOOLEAN | NOT NULL |
| `created_at` | TIMESTAMP | NOT NULL |
| `last_login_at` | TIMESTAMP | NULL |

### `customers`

| Kolon | Tip | Kısıt |
|---|---|---|
| `id` | BIGSERIAL | PK |
| `type` | VARCHAR(20) | CHECK: BIREYSEL, FIRMA |
| `name` | VARCHAR(200) | NOT NULL |
| `phone` | VARCHAR(30) | NOT NULL (girilen biçim) |
| `phone_normalized` | VARCHAR(20) | NOT NULL (yalnız rakam, 10 hane hedefi) |
| `email` | VARCHAR(150) | NULL |
| `address` | VARCHAR(500) | NULL |
| `tax_office`, `tax_number` | VARCHAR | NULL |
| `note` | VARCHAR(1000) | NULL |
| `active` | BOOLEAN | NOT NULL |
| `created_at` | TIMESTAMP | NOT NULL |
| `created_by` | BIGINT | FK → users.id |

Mükerrer kayıt kontrolü uygulama seviyesinde **telefon + isim** birlikte yapılır. İsimler her zaman benzersiz kabul edilmez; aynı telefona farklı isim (aile bireyleri) izinlidir.

### `vehicles`

| Kolon | Tip | Kısıt |
|---|---|---|
| `id` | BIGSERIAL | PK |
| `customer_id` | BIGINT | FK → customers.id, NOT NULL |
| `plate` | VARCHAR(20) | NOT NULL |
| `plate_normalized` | VARCHAR(20) | UNIQUE, NOT NULL |
| `brand`, `model` | VARCHAR(50) | NULL |
| `model_year` | INTEGER | CHECK 1940–2100 veya NULL |
| `vin` | VARCHAR(30) | NULL, kısmi UNIQUE (boş değilse) |
| `current_mileage` | INTEGER | NOT NULL, CHECK ≥ 0 |
| `note` | VARCHAR(1000) | NULL |
| `active` | BOOLEAN | NOT NULL |
| `created_at`, `created_by` | | |

`plate_normalized` boşluk, tire ve Türkçe karakterlerden arındırılmış büyük harf formdur. Mükerrer plaka DB seviyesinde engellenir.

### `vehicle_ownership_history`

Araç sahibi değiştiğinde kayıt eklenir. `previous_customer_id`, `new_customer_id`, `changed_by`, `changed_at`, `note` alanlarını taşır. Geçmiş iş emirlerinin `customer_id` değeri değiştirilmez.

### `work_orders`

| Kolon | Tip | Kısıt |
|---|---|---|
| `id` | BIGSERIAL | PK |
| `order_no` | VARCHAR(30) | UNIQUE, NOT NULL (`IS-YYYY-NNNNNN`) |
| `customer_id`, `vehicle_id` | BIGINT | FK, NOT NULL |
| `assigned_user_id` | BIGINT | FK → users.id, NULL |
| `opened_at` | TIMESTAMP | NOT NULL |
| `complaint`, `diagnosis`, `work_done`, `notes` | VARCHAR(2000) | NULL |
| `mileage` | INTEGER | CHECK ≥ 0 veya NULL |
| `status` | VARCHAR(20) | CHECK: BEKLIYOR, ISLEMDE, TAMAMLANDI, TESLIM_EDILDI, IPTAL |
| `closed_at`, `delivered_at` | TIMESTAMP | NULL |
| `labor_total`, `parts_total`, `total_amount` | NUMERIC(14,2) | NOT NULL, CHECK ≥ 0 |
| `debited` | BOOLEAN | NOT NULL |
| `created_at`, `created_by` | | |
| `version` | BIGINT | Optimistic lock |

`order_no` üretimi `work_order_no_seq` sequence'ından yapılır. `customer_id`, iş emrinin açıldığı andaki müşteriyi temsil eder; araç sahibi sonradan değişse bile bu ilişki korunur.

Günlük iş panosu açık işleri `status IN ('BEKLIYOR','ISLEMDE')` ile, bugün biten işleri ise `closed_at` veya `delivered_at` değeri bugünün aralığında olan `TAMAMLANDI`/`TESLIM_EDILDI` kayıtlarıyla okur. Ayrı bir “gün” tablosu yoktur; devir, durum ve kapanış zaman damgalarından hesaplanır.

### `work_order_items`

| Kolon | Tip | Kısıt |
|---|---|---|
| `id` | BIGSERIAL | PK |
| `work_order_id` | BIGINT | FK, ON DELETE CASCADE |
| `type` | VARCHAR(20) | CHECK: HIZMET, PARCA |
| `description` | VARCHAR(300) | NOT NULL |
| `quantity` | NUMERIC(12,2) | CHECK > 0 |
| `unit_price` | NUMERIC(14,2) | CHECK ≥ 0 |
| `line_total` | NUMERIC(14,2) | CHECK ≥ 0 |

`line_total` sunucuda `quantity × unit_price`, `HALF_UP` ve 2 ondalık olarak hesaplanır. İstemciden gelen toplam kabul edilmez. Cascade yalnızca operasyonel kalemler içindir; finansal kayıtlar cascade ile silinmez.

### `work_order_item_templates`

Sık kullanılan işçilik/parça açıklamalarını iş emri formuna tek tıkla dolduran şablonlar.

| Kolon | Tip | Kısıt |
|---|---|---|
| `id` | BIGSERIAL | PK |
| `name` | VARCHAR(120) | NOT NULL |
| `type` | VARCHAR(20) | CHECK: HIZMET, PARCA |
| `description` | VARCHAR(300) | NOT NULL |
| `default_quantity` | NUMERIC(12,2) | CHECK > 0 |
| `default_unit_price` | NUMERIC(14,2) | CHECK ≥ 0 |
| `active` | BOOLEAN | Varsayılan true |
| `sort_order` | INTEGER | Liste sırası |

### `payments`

| Kolon | Tip | Kısıt |
|---|---|---|
| `id` | BIGSERIAL | PK |
| `customer_id` | BIGINT | FK, NOT NULL |
| `receipt_no` | VARCHAR(30) | UNIQUE (`TH-YYYY-NNNNNN`) |
| `amount` | NUMERIC(14,2) | CHECK > 0 |
| `payment_date` | DATE | NOT NULL |
| `method` | VARCHAR(20) | CHECK: NAKIT, BANKA, KART |
| `status` | VARCHAR(20) | CHECK: AKTIF, IPTAL |
| `description` | VARCHAR(500) | NULL |
| `created_by`, `created_at` | | |
| `cancelled_at`, `cancelled_by` | | |
| `version` | BIGINT | Optimistic lock |

Makbuz numarası `payment_receipt_seq` sequence'ından üretilir. İptal, satırı silmez; `status=IPTAL` yapar.

### `account_transactions`

| Kolon | Tip | Kısıt |
|---|---|---|
| `id` | BIGSERIAL | PK |
| `customer_id` | BIGINT | FK, NOT NULL |
| `type` | VARCHAR(10) | CHECK: BORC, ALACAK |
| `amount` | NUMERIC(14,2) | CHECK > 0 |
| `transaction_date` | DATE | NOT NULL |
| `description` | VARCHAR(500) | NOT NULL |
| `work_order_id` | BIGINT | FK, NULL |
| `payment_id` | BIGINT | FK, NULL |
| `reversal_of_id` | BIGINT | Self FK, NULL |
| `active_debit` | BOOLEAN | NOT NULL, varsayılan TRUE |
| `created_by`, `created_at` | | |

Kurallar:

- Bir iş emri için aynı anda en fazla bir **aktif** orijinal `BORC` hareketi (kısmi benzersiz indeks).
- Tamamlama geri alındığında eski borç ters kayıtla kapatılır ve `active_debit = FALSE` yapılır; yeniden tamamlanırsa yeni borç hareketi açılabilir.
- Bir tahsilat için en fazla bir orijinal `ALACAK` hareketi (kısmi benzersiz indeks).
- Ters kayıtlar `reversal_of_id` ile orijinal harekete bağlanır ve silinmez.

Bakiye sorgusu:

```sql
SELECT COALESCE(SUM(CASE WHEN type = 'BORC' THEN amount ELSE -amount END), 0)
FROM account_transactions
WHERE customer_id = :customerId;
```

### `payment_allocations`

| Kolon | Tip | Kısıt |
|---|---|---|
| `id` | BIGSERIAL | PK |
| `payment_id` | BIGINT | FK, NOT NULL |
| `transaction_id` | BIGINT | FK → account_transactions, NOT NULL |
| `amount` | NUMERIC(14,2) | CHECK > 0 |
| `cancelled` | BOOLEAN | NOT NULL |
| | | UNIQUE(payment_id, transaction_id) |

Bir tahsilat birden fazla borç hareketine dağıtılabilir. Aynı borca aynı tahsilatta iki kez dağıtım yapılamaz. Tahsilat iptalinde dağıtımlar `cancelled=true` yapılır; satırlar korunur. Kalan borç hesabında yalnızca `cancelled=false` ve `payment.status=AKTIF` dağıtımlar dikkate alınır.

### `audit_logs`

`actor_id` (FK, NULL olabilir), `actor_username` snapshot, `action`, `entity_type`, `entity_id`, `details`, `ip_address`, `created_at`. Parola ve oturum belirteçleri yazılmaz. Kayıtlar silinmez.

### `backup_runs`

Yedekleme denemelerinin sonucu: `started_at`, `finished_at`, `status` (BASARILI, BASARISIZ, DEVAM_EDIYOR), `file_name`, `file_size_bytes`, `directory`, `message`, `triggered_by` (MANUEL/ZAMANLANMIS). `directory`, dosyanın oluşturulduğu tam klasörü saklar; klasör sonradan değiştirilse bile eski yedekler indirilebilir.

### `business_settings`

Tek satırlık işletme kimliği (`id = 1`). `name`, `tagline`, `primary_color`, `logo_data`, `logo_content_type`, `phone`, `email`, `address`, `tax_office`, `tax_number`, `backup_directory`, `updated_at`, `updated_by` alanlarını taşır. İş emri, tahsilat makbuzu, cari ekstre ve tüm marka alanlarında kullanılır. Tek satır kuralı CHECK kısıtıyla korunur. `backup_directory` boşsa `BACKUP_DIR` ortam değişkeni kullanılır.

### `vehicle_intakes`

Her iş emri için tek araç kabul kaydı (`work_order_id` UNIQUE). `intake_at`, `fuel_level` (BOS/CEYREK/YARIM/UCDORT/DOLU), `damage_notes`, `valuables`, `signature_data` (PNG data URL), `created_by`, `created_at`, `updated_at`. Islak imza çıktı üzerinde alınır; dijital imza da saklanır.

### `vehicle_deliveries`

Her iş emri için tek teslim tutanağı (`work_order_id` UNIQUE). `delivered_at`, `receiver_name`, `mileage`, `fuel_level`, `notes`, `signature_data` (PNG data URL), `created_by`, `created_at`, `updated_at`. Yalnızca `TAMAMLANDI` veya `TESLIM_EDILDI` durumundaki iş emirleri için kaydedilir.

### `appointments`

Günlük randevu planı. `customer_id`, `vehicle_id`, `starts_at`, `status` (PLANLANDI/GELDI/IPTAL), `notes`, `created_by`, `created_at`, `updated_at`. Aynı araç için aynı saatte aktif randevu açılamaz.

## Sequence'lar

| Sequence | Kullanım |
|---|---|
| `work_order_no_seq` | İş emri numarası |
| `payment_receipt_seq` | Tahsilat makbuz numarası |

Sequence değerleri transaction rollback'inde geri alınmaz; bu nedenle numaralarda boşluk görülebilir. Bu, benzersizliği garanti etmek için bilinçli bir tercihtir.

## İndeksler

Öne çıkanlar:

- `uk_vehicles_plate` (benzersiz plaka)
- `uk_vehicles_vin` (kısmi, boş olmayan VIN)
- `idx_customers_name_lower`, `idx_customers_phone_normalized`
- `idx_work_orders_customer`, `idx_work_orders_vehicle`, `idx_work_orders_status`, `idx_work_orders_opened_at`
- `uk_account_tx_work_order_active_debit` (kısmi benzersiz)
- `uk_account_tx_payment_credit` (kısmi benzersiz)
- `idx_account_transactions_customer`, `idx_account_transactions_date`
- `idx_payments_customer`, `idx_payments_date`, `idx_payments_status`
- `idx_audit_logs_created_at`, `idx_audit_logs_entity`

## Migration Dosyaları

| Sürüm | Dosya | İçerik |
|---|---|---|
| V1 | `V1__initial_schema_users_customers_vehicles.sql` | Kullanıcı, müşteri, araç, sahiplik geçmişi |
| V2 | `V2__work_orders.sql` | İş emri, kalem, iş emri sequence |
| V3 | `V3__accounting.sql` | Tahsilat, cari hareket, dağıtım, makbuz sequence |
| V4 | `V4__audit_log.sql` | Denetim kaydı |
| V5 | `V5__indexes.sql` | İndeksler ve kısmi benzersizlikler |
| V6 | `V6__backup_runs.sql` | Yedekleme geçmişi |
| V7 | `V7__business_settings.sql` | İşletme bilgileri (çıktı başlıkları) |
| V8 | `V8__business_roles.sql` | Rol modeli: PATRON, MUHASEBE, SERVIS; eski ADMIN/CALISAN dönüşümü |
| V9 | `V9__toyota_brand.sql` | Görünen işletme adı: Toyota Plaza Öztoprak |
| V10 | `V10__work_order_item_templates.sql` | Hazır işçilik/parça kalem şablonları |
| V11 | `V11__intake_delivery_appointments.sql` | Araç kabul, teslim tutanağı ve randevular |
| V12 | `V12__backup_directory.sql` | Seçilebilir yedek klasörü ve yedek dosyası yolu |
| V13 | `V13__digital_signatures.sql` | Araç kabul ve teslim tutanağına dijital imza |
| V14 | `V14__branding_settings.sql` | White-label slogan, ana renk ve logo |
| V15 | `V15__work_order_debit_reactivation.sql` | Tamamlama geri alınınca yeniden borçlandırma |

Uygulama açılışında Flyway doğrular ve eksik migration'ları sırayla uygular. `flyway_schema_history` tablosu şema sürümünü tutar.

## Yedekleme Notu

`pg_dump -Fc` tüm şemayı, sequence durumlarını ve Flyway geçmişini içerir. Geri yükleme sonrası Flyway tekrar çalıştırıldığında mevcut sürüm görülür; şema yeniden oluşturulmaz. Geri yükleme testi için `docs/BACKUP-RESTORE.md` izlenmelidir.
