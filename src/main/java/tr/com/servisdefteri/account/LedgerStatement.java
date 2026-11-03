package tr.com.servisdefteri.account;

import tr.com.servisdefteri.customer.Customer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record LedgerStatement(Customer customer,
                              LocalDate start,
                              LocalDate end,
                              BigDecimal openingBalance,
                              List<LedgerRow> rows,
                              BigDecimal closingBalance,
                              BigDecimal totalDebit,
                              BigDecimal totalCredit) {
}
