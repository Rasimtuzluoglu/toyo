-- Is emirleri ve kalemleri
CREATE SEQUENCE work_order_no_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE work_orders (
    id BIGSERIAL PRIMARY KEY,
    order_no VARCHAR(30) NOT NULL,
    customer_id BIGINT NOT NULL REFERENCES customers (id),
    vehicle_id BIGINT NOT NULL REFERENCES vehicles (id),
    assigned_user_id BIGINT REFERENCES users (id),
    opened_at TIMESTAMP NOT NULL,
    complaint VARCHAR(2000),
    diagnosis VARCHAR(2000),
    work_done VARCHAR(2000),
    mileage INTEGER,
    status VARCHAR(20) NOT NULL,
    notes VARCHAR(2000),
    closed_at TIMESTAMP,
    delivered_at TIMESTAMP,
    labor_total NUMERIC(14, 2) NOT NULL DEFAULT 0,
    parts_total NUMERIC(14, 2) NOT NULL DEFAULT 0,
    total_amount NUMERIC(14, 2) NOT NULL DEFAULT 0,
    debited BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    created_by BIGINT REFERENCES users (id),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_work_orders_order_no UNIQUE (order_no),
    CONSTRAINT ck_work_orders_status CHECK (status IN ('BEKLIYOR', 'ISLEMDE', 'TAMAMLANDI', 'TESLIM_EDILDI', 'IPTAL')),
    CONSTRAINT ck_work_orders_mileage CHECK (mileage IS NULL OR mileage >= 0),
    CONSTRAINT ck_work_orders_totals CHECK (labor_total >= 0 AND parts_total >= 0 AND total_amount >= 0)
);

CREATE TABLE work_order_items (
    id BIGSERIAL PRIMARY KEY,
    work_order_id BIGINT NOT NULL REFERENCES work_orders (id) ON DELETE CASCADE,
    type VARCHAR(20) NOT NULL,
    description VARCHAR(300) NOT NULL,
    quantity NUMERIC(12, 2) NOT NULL,
    unit_price NUMERIC(14, 2) NOT NULL,
    line_total NUMERIC(14, 2) NOT NULL,
    CONSTRAINT ck_work_order_items_type CHECK (type IN ('HIZMET', 'PARCA')),
    CONSTRAINT ck_work_order_items_quantity CHECK (quantity > 0),
    CONSTRAINT ck_work_order_items_unit_price CHECK (unit_price >= 0),
    CONSTRAINT ck_work_order_items_line_total CHECK (line_total >= 0)
);
