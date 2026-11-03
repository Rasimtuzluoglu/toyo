-- Yeni rol yapisi: PATRON (tum islemler), MUHASEBE (tahsilat/cari), SERVIS (arac ve is emri).
ALTER TABLE users DROP CONSTRAINT ck_users_role;

UPDATE users SET role = 'PATRON' WHERE role = 'ADMIN';
UPDATE users SET role = 'SERVIS' WHERE role = 'CALISAN';

ALTER TABLE users ADD CONSTRAINT ck_users_role CHECK (role IN ('PATRON', 'MUHASEBE', 'SERVIS'));
