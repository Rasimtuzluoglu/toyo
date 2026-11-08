package tr.com.servisdefteri.account;

import java.math.BigDecimal;

/**
 * Tek bir iş emrinin aktif borç özeti. Servis tahsilatı ve günlük döküm için kullanılır.
 */
public record WorkOrderDebt(Long transactionId,
                            BigDecimal amount,
                            BigDecimal paid,
                            BigDecimal remaining) {
}
