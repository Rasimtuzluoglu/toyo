package tr.com.servisdefteri.account;

import java.math.BigDecimal;

public record CustomerBalance(Long customerId, String name, String phone, BigDecimal balance) {

    public boolean isDebtor() {
        return balance != null && balance.signum() > 0;
    }

    public BigDecimal absoluteBalance() {
        return balance == null ? BigDecimal.ZERO : balance.abs();
    }
}
