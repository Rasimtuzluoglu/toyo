-- İlk kurulum yalnızca bir kez yapılabilir.
-- Kullanıcı tablosu sonradan boşalsa bile /setup yeniden açılmaz.
CREATE TABLE installation_state (
    id SMALLINT PRIMARY KEY,
    setup_completed BOOLEAN NOT NULL DEFAULT FALSE,
    completed_at TIMESTAMP,
    CONSTRAINT ck_installation_state_single CHECK (id = 1)
);

INSERT INTO installation_state (id, setup_completed) VALUES (1, FALSE);

UPDATE installation_state
SET setup_completed = TRUE,
    completed_at = now()
WHERE EXISTS (SELECT 1 FROM users);
