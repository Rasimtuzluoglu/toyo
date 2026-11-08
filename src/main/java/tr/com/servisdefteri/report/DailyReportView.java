package tr.com.servisdefteri.report;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record DailyReportView(LocalDate date,
                              List<DailyReportRow> rows,
                              BigDecimal totalAmount,
                              BigDecimal totalPaid,
                              BigDecimal totalRemaining) {
}
