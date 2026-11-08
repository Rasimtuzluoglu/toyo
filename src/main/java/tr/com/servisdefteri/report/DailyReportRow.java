package tr.com.servisdefteri.report;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record DailyReportRow(Long orderId,
                             String orderNo,
                             LocalDateTime openedAt,
                             String customerName,
                             String phone,
                             String plate,
                             String vehicle,
                             String complaint,
                             String workDone,
                             String assignedName,
                             String statusLabel,
                             BigDecimal total,
                             BigDecimal paid,
                             BigDecimal remaining) {
}
