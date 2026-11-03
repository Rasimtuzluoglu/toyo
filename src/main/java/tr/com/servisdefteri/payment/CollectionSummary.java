package tr.com.servisdefteri.payment;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CollectionSummary(LocalDate date, PaymentMethod method, BigDecimal total, long count) {
}
