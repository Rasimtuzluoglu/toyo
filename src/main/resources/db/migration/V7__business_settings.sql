-- Isletme kimligi: yazdirma ve makbuzlarda kullanilir.
CREATE TABLE business_settings (
    id BIGINT PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    phone VARCHAR(30),
    email VARCHAR(150),
    address VARCHAR(500),
    tax_office VARCHAR(100),
    tax_number VARCHAR(20),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_by BIGINT REFERENCES users (id),
    CONSTRAINT ck_business_settings_single_row CHECK (id = 1)
);

INSERT INTO business_settings (id, name, updated_at)
VALUES (1, 'ServisDefteri', now());
