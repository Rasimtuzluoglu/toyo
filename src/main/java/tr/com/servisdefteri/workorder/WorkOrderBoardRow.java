package tr.com.servisdefteri.workorder;

/**
 * Günlük iş panosunda açık iş emrini kaç gündür açık olduğu bilgisiyle taşır.
 */
public record WorkOrderBoardRow(WorkOrder order, long daysOpen) {

    public String getAgeLabel() {
        if (daysOpen <= 0) {
            return "Bugün açıldı";
        }
        if (daysOpen == 1) {
            return "Dünden devir";
        }
        return daysOpen + " günden beri açık";
    }
}
