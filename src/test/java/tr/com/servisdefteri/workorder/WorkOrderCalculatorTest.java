package tr.com.servisdefteri.workorder;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WorkOrderCalculatorTest {

    @Test
    void lineTotalRoundsHalfUpToTwoDecimals() {
        assertThat(WorkOrderCalculator.lineTotal(new BigDecimal("2.5"), new BigDecimal("100.50")))
                .isEqualByComparingTo("251.25");
        assertThat(WorkOrderCalculator.lineTotal(new BigDecimal("3"), new BigDecimal("0.335")))
                .isEqualByComparingTo("1.01");
    }

    @Test
    void totalsSeparateLaborAndParts() {
        WorkOrderItem labor = item(WorkOrderItemType.HIZMET, "1", "10000.00");
        WorkOrderItem part = item(WorkOrderItemType.PARCA, "2", "1250.50");
        WorkOrderCalculator.Totals totals = WorkOrderCalculator.totals(List.of(labor, part));

        assertThat(totals.labor()).isEqualByComparingTo("10000.00");
        assertThat(totals.parts()).isEqualByComparingTo("2501.00");
        assertThat(totals.total()).isEqualByComparingTo("12501.00");
    }

    @Test
    void emptyItemsProduceZeroTotals() {
        WorkOrderCalculator.Totals totals = WorkOrderCalculator.totals(new ArrayList<>());
        assertThat(totals.labor()).isEqualByComparingTo("0.00");
        assertThat(totals.parts()).isEqualByComparingTo("0.00");
        assertThat(totals.total()).isEqualByComparingTo("0.00");
    }

    private WorkOrderItem item(WorkOrderItemType type, String quantity, String unitPrice) {
        WorkOrderItem item = new WorkOrderItem();
        item.setType(type);
        item.setQuantity(new BigDecimal(quantity));
        item.setUnitPrice(new BigDecimal(unitPrice));
        item.setLineTotal(WorkOrderCalculator.lineTotal(item.getQuantity(), item.getUnitPrice()));
        return item;
    }
}
