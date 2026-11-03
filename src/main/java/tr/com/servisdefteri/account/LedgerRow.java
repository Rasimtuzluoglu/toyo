package tr.com.servisdefteri.account;

import java.math.BigDecimal;
import java.time.LocalDate;

public record LedgerRow(Long id,
                        LocalDate date,
                        TransactionType type,
                        String description,
                        BigDecimal debit,
                        BigDecimal credit,
                        BigDecimal balance,
                        Long workOrderId,
                        String workOrderNo,
                        boolean reversal) {
}
