-- Tablet/masaustu dijital imza verisi (PNG data URL).
ALTER TABLE vehicle_intakes ADD COLUMN signature_data TEXT;
ALTER TABLE vehicle_deliveries ADD COLUMN signature_data TEXT;
