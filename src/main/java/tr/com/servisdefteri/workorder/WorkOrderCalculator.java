package tr.com.servisdefteri.workorder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Is emri tutarlarini yalnizca sunucu tarafinda, BigDecimal ile hesaplar.
 */
public final class WorkOrderCalculator {

    private WorkOrderCalculator() {
    }

    public record Totals(BigDecimal labor, BigDecimal parts, BigDecimal total) {
    }

    public static BigDecimal lineTotal(BigDecimal quantity, BigDecimal unitPrice) {
        if (quantity == null || unitPrice == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return quantity.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP);
    }

    public static Totals totals(List<WorkOrderItem> items) {
        BigDecimal labor = BigDecimal.ZERO;
        BigDecimal parts = BigDecimal.ZERO;
        if (items != null) {
            for (WorkOrderItem item : items) {
                BigDecimal line = item.getLineTotal() == null
                        ? lineTotal(item.getQuantity(), item.getUnitPrice())
                        : item.getLineTotal().setScale(2, RoundingMode.HALF_UP);
                if (item.getType() == WorkOrderItemType.HIZMET) {
                    labor = labor.add(line);
                } else {
                    parts = parts.add(line);
                }
            }
        }
        labor = labor.setScale(2, RoundingMode.HALF_UP);
        parts = parts.setScale(2, RoundingMode.HALF_UP);
        return new Totals(labor, parts, labor.add(parts).setScale(2, RoundingMode.HALF_UP));
    }
}
