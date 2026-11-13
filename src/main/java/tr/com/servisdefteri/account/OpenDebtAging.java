package tr.com.servisdefteri.account;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Yaşlandırma raporu için açık borcun müşteri, tarih ve kalan tutarı.
 */
public record OpenDebtAging(Long customerId, LocalDate date, BigDecimal remaining) {
}
