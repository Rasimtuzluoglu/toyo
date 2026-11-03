-- Yedekleme icin kullanici tarafindan secilebilen dizin ve yedek dosyasinin tam yolu.
ALTER TABLE business_settings ADD COLUMN backup_directory VARCHAR(500);
ALTER TABLE backup_runs ADD COLUMN directory VARCHAR(500);
