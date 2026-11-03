-- Arama ve raporlama indeksleri, is kurali benzersizlik kisitlari
CREATE INDEX idx_customers_name_lower ON customers (lower(name));
CREATE INDEX idx_customers_phone_normalized ON customers (phone_normalized);
CREATE INDEX idx_customers_active ON customers (active);

CREATE INDEX idx_vehicles_customer ON vehicles (customer_id);
CREATE INDEX idx_vehicles_active ON vehicles (active);
CREATE UNIQUE INDEX uk_vehicles_vin ON vehicles (vin) WHERE vin IS NOT NULL AND vin <> '';

CREATE INDEX idx_vehicle_ownership_vehicle ON vehicle_ownership_history (vehicle_id);

CREATE INDEX idx_work_orders_customer ON work_orders (customer_id);
CREATE INDEX idx_work_orders_vehicle ON work_orders (vehicle_id);
CREATE INDEX idx_work_orders_status ON work_orders (status);
CREATE INDEX idx_work_orders_opened_at ON work_orders (opened_at DESC);
CREATE INDEX idx_work_order_items_order ON work_order_items (work_order_id);

CREATE INDEX idx_account_transactions_customer ON account_transactions (customer_id);
CREATE INDEX idx_account_transactions_date ON account_transactions (transaction_date);
CREATE INDEX idx_account_transactions_work_order ON account_transactions (work_order_id);

-- Bir is emri yalnizca bir kez borclandirilabilir.
CREATE UNIQUE INDEX uk_account_tx_work_order_debit
    ON account_transactions (work_order_id)
    WHERE work_order_id IS NOT NULL AND type = 'BORC' AND reversal_of_id IS NULL;

-- Bir tahsilat yalnizca bir kez alacak kaydi uretir.
CREATE UNIQUE INDEX uk_account_tx_payment_credit
    ON account_transactions (payment_id)
    WHERE payment_id IS NOT NULL;

CREATE INDEX idx_payments_customer ON payments (customer_id);
CREATE INDEX idx_payments_date ON payments (payment_date);
CREATE INDEX idx_payments_status ON payments (status);

CREATE INDEX idx_payment_allocations_payment ON payment_allocations (payment_id);
CREATE INDEX idx_payment_allocations_transaction ON payment_allocations (transaction_id);

CREATE INDEX idx_audit_logs_created_at ON audit_logs (created_at DESC);
CREATE INDEX idx_audit_logs_entity ON audit_logs (entity_type, entity_id);
