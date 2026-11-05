-- Tamamlama geri alındığında aynı iş emri için yeniden borç kaydı açılabilmesi.
-- Eski borç ters kayıtla kapatılır ve active_debit = false yapılır.
ALTER TABLE account_transactions ADD COLUMN active_debit BOOLEAN NOT NULL DEFAULT TRUE;

UPDATE account_transactions t
SET active_debit = FALSE
WHERE t.type = 'BORC'
  AND t.reversal_of_id IS NULL
  AND EXISTS (
      SELECT 1 FROM account_transactions r WHERE r.reversal_of_id = t.id
  );

DROP INDEX uk_account_tx_work_order_debit;

CREATE UNIQUE INDEX uk_account_tx_work_order_active_debit
    ON account_transactions (work_order_id)
    WHERE work_order_id IS NOT NULL
      AND type = 'BORC'
      AND reversal_of_id IS NULL
      AND active_debit;
