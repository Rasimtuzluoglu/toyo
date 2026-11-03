package tr.com.servisdefteri.report;

import java.math.BigDecimal;

public record AgingRow(Long customerId, String name, String phone,
                       BigDecimal current, BigDecimal mid, BigDecimal old) {

    public BigDecimal total() {
        return current.add(mid).add(old);
    }
}
