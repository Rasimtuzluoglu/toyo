-- Yedekleme calisma kayitlari
CREATE TABLE backup_runs (
    id BIGSERIAL PRIMARY KEY,
    started_at TIMESTAMP NOT NULL,
    finished_at TIMESTAMP,
    status VARCHAR(20) NOT NULL,
    file_name VARCHAR(200),
    file_size_bytes BIGINT,
    message VARCHAR(2000),
    triggered_by VARCHAR(50) NOT NULL,
    CONSTRAINT ck_backup_runs_status CHECK (status IN ('BASARILI', 'BASARISIZ', 'DEVAM_EDIYOR'))
);
CREATE INDEX idx_backup_runs_started_at ON backup_runs (started_at DESC);
