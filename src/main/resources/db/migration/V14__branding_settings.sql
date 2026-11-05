-- White-label marka ayarlari: slogan, ana renk ve logo.
ALTER TABLE business_settings ADD COLUMN tagline VARCHAR(120) NOT NULL DEFAULT 'Yetkili Servis ve Bakım';
ALTER TABLE business_settings ADD COLUMN primary_color VARCHAR(7) NOT NULL DEFAULT '#eb0a1e';
ALTER TABLE business_settings ADD COLUMN logo_data BYTEA;
ALTER TABLE business_settings ADD COLUMN logo_content_type VARCHAR(100);

UPDATE business_settings
SET tagline = 'Yetkili Servis ve Bakım',
    primary_color = '#eb0a1e'
WHERE id = 1;
