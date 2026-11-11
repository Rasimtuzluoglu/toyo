-- Yeni iş akışı: iş emirleri doğrudan ISLEMDE durumunda başlar.
-- Mevcut bekleyen kayıtlar işlemde durumuna taşınır.
UPDATE work_orders SET status = 'ISLEMDE' WHERE status = 'BEKLIYOR';

ALTER TABLE work_orders DROP CONSTRAINT ck_work_orders_status;
ALTER TABLE work_orders ADD CONSTRAINT ck_work_orders_status
    CHECK (status IN ('ISLEMDE', 'TAMAMLANDI', 'TESLIM_EDILDI', 'IPTAL'));
