package tr.com.servisdefteri.workorder;

import java.math.BigDecimal;

public record WorkOrderStatusSummary(WorkOrderStatus status, long count, BigDecimal total) {
}
