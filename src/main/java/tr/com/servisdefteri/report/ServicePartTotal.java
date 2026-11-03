package tr.com.servisdefteri.report;

import tr.com.servisdefteri.workorder.WorkOrderItemType;

import java.math.BigDecimal;

public record ServicePartTotal(WorkOrderItemType type, BigDecimal total) {
}
