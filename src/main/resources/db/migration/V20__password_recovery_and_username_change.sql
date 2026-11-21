-- Kullanıcı adı değiştirme ve tek kullanımlık kurtarma kodu desteği.
alter table users add column recovery_code_hash varchar(100);
alter table users add column recovery_code_expires_at timestamp;
alter table users add column recovery_code_created_at timestamp;
