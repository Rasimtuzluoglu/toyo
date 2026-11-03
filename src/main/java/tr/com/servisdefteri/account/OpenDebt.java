package tr.com.servisdefteri.account;

import java.math.BigDecimal;
import java.time.LocalDate;

public record OpenDebt(Long id, LocalDate date, String description, BigDecimal amount, BigDecimal paid) {

    public BigDecimal getRemaining() {
        return amount.subtract(paid);
    }

    public boolean isOpen() {
        return getRemaining().signum() > 0;
    }
}
