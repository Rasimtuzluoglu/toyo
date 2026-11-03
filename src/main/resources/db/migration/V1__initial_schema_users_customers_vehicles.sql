-- ServisDefteri ilk sema: kullanicilar, musteriler, araclar
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(50) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    full_name VARCHAR(100) NOT NULL,
    role VARCHAR(20) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    last_login_at TIMESTAMP,
    CONSTRAINT uk_users_username UNIQUE (username),
    CONSTRAINT ck_users_role CHECK (role IN ('ADMIN', 'PATRON', 'CALISAN'))
);

CREATE TABLE customers (
    id BIGSERIAL PRIMARY KEY,
    type VARCHAR(20) NOT NULL,
    name VARCHAR(200) NOT NULL,
    phone VARCHAR(30) NOT NULL,
    phone_normalized VARCHAR(20) NOT NULL,
    email VARCHAR(150),
    address VARCHAR(500),
    tax_office VARCHAR(100),
    tax_number VARCHAR(20),
    note VARCHAR(1000),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    created_by BIGINT REFERENCES users (id),
    CONSTRAINT ck_customers_type CHECK (type IN ('BIREYSEL', 'FIRMA'))
);

CREATE TABLE vehicles (
    id BIGSERIAL PRIMARY KEY,
    customer_id BIGINT NOT NULL REFERENCES customers (id),
    plate VARCHAR(20) NOT NULL,
    plate_normalized VARCHAR(20) NOT NULL,
    brand VARCHAR(50),
    model VARCHAR(50),
    model_year INTEGER,
    vin VARCHAR(30),
    current_mileage INTEGER NOT NULL DEFAULT 0,
    note VARCHAR(1000),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    created_by BIGINT REFERENCES users (id),
    CONSTRAINT uk_vehicles_plate UNIQUE (plate_normalized),
    CONSTRAINT ck_vehicles_mileage CHECK (current_mileage >= 0),
    CONSTRAINT ck_vehicles_model_year CHECK (model_year IS NULL OR (model_year >= 1940 AND model_year <= 2100))
);

-- Arac sahibi degistiginde gecmis is emirleri eski musteride kalir; degisiklik burada izlenir.
CREATE TABLE vehicle_ownership_history (
    id BIGSERIAL PRIMARY KEY,
    vehicle_id BIGINT NOT NULL REFERENCES vehicles (id),
    previous_customer_id BIGINT REFERENCES customers (id),
    new_customer_id BIGINT NOT NULL REFERENCES customers (id),
    changed_by BIGINT REFERENCES users (id),
    changed_at TIMESTAMP NOT NULL DEFAULT now(),
    note VARCHAR(500)
);
