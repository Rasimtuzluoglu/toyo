-- Veri bütünlüğü ve rapor performansı için eksik unique/bileşik index'ler.
-- Uygulama seviyesindeki kontrollerin yarış koşullarında da geçerli olmasını sağlar.

-- Fiş numarası büyük/küçük harf farkıyla mükerrer olamaz.
create unique index if not exists uk_work_orders_order_no_lower
    on work_orders (lower(order_no));

-- Aynı telefon + isim (harf büyüklüğünden bağımsız) mükerrer müşteri oluşturamaz.
create unique index if not exists uk_customers_phone_name
    on customers (phone_normalized, lower(name));

-- Aynı araca aynı saatte, iptal edilmemiş birden fazla randevu verilemez.
create unique index if not exists uk_appointments_vehicle_slot
    on appointments (vehicle_id, starts_at)
    where status <> 'IPTAL';

-- Sık kullanılan rapor/filtre sorguları için bileşik index'ler.
create index if not exists idx_appointments_vehicle_start
    on appointments (vehicle_id, starts_at);
create index if not exists idx_audit_action_created
    on audit_logs (action, created_at desc);
create index if not exists idx_payments_status_date
    on payments (status, payment_date);
create index if not exists idx_work_orders_status_opened
    on work_orders (status, opened_at);
create index if not exists idx_account_tx_customer_date
    on account_transactions (customer_id, transaction_date);
create index if not exists idx_account_tx_reversal
    on account_transactions (reversal_of_id);
create index if not exists idx_work_orders_closed
    on work_orders (closed_at);
create index if not exists idx_work_orders_delivered
    on work_orders (delivered_at);
