-- Gorunen isletme markasi: Toyota Plaza Oztoprak.
-- Ayarlar ekranindan degistirilebilir; bu migration yalnizca baslangic degerini gunceller.
UPDATE business_settings
SET name = 'Toyota Plaza Öztoprak',
    updated_at = now()
WHERE id = 1;
