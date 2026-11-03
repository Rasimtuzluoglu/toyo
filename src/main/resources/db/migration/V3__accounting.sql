-- Cari hesap, tahsilat ve tahsilat dagitimi
CREATE SEQUENCE payment_receipt_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE payments (
    id BIGSERIAL PRIMARY KEY,
    customer_id BIGINT NOT NULL REFERENCES customers (id),
    receipt_no VARCHAR(30) NOT NULL,
    amount NUMERIC(14, 2) NOT NULL,
    payment_date DATE NOT NULL,
    method VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'AKTIF',
    description VARCHAR(500),
    created_by BIGINT REFERENCES users (id),
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    cancelled_at TIMESTAMP,
    cancelled_by BIGINT REFERENCES users (id),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_payments_receipt_no UNIQUE (receipt_no),
    CONSTRAINT ck_payments_amount CHECK (amount > 0),
    CONSTRAINT ck_payments_method CHECK (method IN ('NAKIT', 'BANKA', 'KART')),
    CONSTRAINT ck_payments_status CHECK (status IN ('AKTIF', 'IPTAL'))
);

CREATE TABLE account_transactions (
    id BIGSERIAL PRIMARY KEY,
    customer_id BIGINT NOT NULL REFERENCES customers (id),
    type VARCHAR(10) NOT NULL,
    amount NUMERIC(14, 2) NOT NULL,
    transaction_date DATE NOT NULL,
    description VARCHAR(500) NOT NULL,
    work_order_id BIGINT REFERENCES work_orders (id),
    payment_id BIGINT REFERENCES payments (id),
    reversal_of_id BIGINT REFERENCES account_transactions (id),
    created_by BIGINT REFERENCES users (id),
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT ck_account_transactions_type CHECK (type IN ('BORC', 'ALACAK')),
    CONSTRAINT ck_account_transactions_amount CHECK (amount > 0)
);

CREATE TABLE payment_allocations (
    id BIGSERIAL PRIMARY KEY,
    payment_id BIGINT NOT NULL REFERENCES payments (id),
    transaction_id BIGINT NOT NULL REFERENCES account_transactions (id),
    amount NUMERIC(14, 2) NOT NULL,
    cancelled BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_payment_allocations UNIQUE (payment_id, transaction_id),
    CONSTRAINT ck_payment_allocations_amount CHECK (amount > 0)
);
