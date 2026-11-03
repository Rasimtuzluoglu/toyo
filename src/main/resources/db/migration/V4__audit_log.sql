-- Denetim kaydi
CREATE TABLE audit_logs (
    id BIGSERIAL PRIMARY KEY,
    actor_id BIGINT REFERENCES users (id),
    actor_username VARCHAR(50) NOT NULL,
    action VARCHAR(50) NOT NULL,
    entity_type VARCHAR(50) NOT NULL,
    entity_id BIGINT,
    details VARCHAR(4000),
    ip_address VARCHAR(45),
    created_at TIMESTAMP NOT NULL DEFAULT now()
);
