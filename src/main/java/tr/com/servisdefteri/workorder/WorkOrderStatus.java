package tr.com.servisdefteri.workorder;

import lombok.Getter;

import java.util.List;

@Getter
public enum WorkOrderStatus {

    BEKLIYOR("Bekliyor", "secondary", "Beklemeye Al"),
    ISLEMDE("İşlemde", "info", "İşleme Al"),
    TAMAMLANDI("Tamamlandı", "success", "Tamamla"),
    TESLIM_EDILDI("Teslim Edildi", "primary", "Teslim Et"),
    IPTAL("İptal", "danger", "İptal Et");

    private final String label;
    private final String badgeClass;
    private final String actionLabel;

    WorkOrderStatus(String label, String badgeClass, String actionLabel) {
        this.label = label;
        this.badgeClass = badgeClass;
        this.actionLabel = actionLabel;
    }

    public boolean canTransitionTo(WorkOrderStatus target) {
        if (target == null || target == this) {
            return false;
        }
        return switch (this) {
            case BEKLIYOR -> target == ISLEMDE || target == IPTAL;
            case ISLEMDE -> target == TAMAMLANDI || target == IPTAL;
            case TAMAMLANDI -> target == TESLIM_EDILDI || target == IPTAL;
            case TESLIM_EDILDI, IPTAL -> false;
        };
    }

    public List<WorkOrderStatus> allowedTransitions() {
        return List.of(values()).stream().filter(this::canTransitionTo).toList();
    }

    public boolean isOpen() {
        return this == BEKLIYOR || this == ISLEMDE;
    }

    public boolean isCompleted() {
        return this == TAMAMLANDI || this == TESLIM_EDILDI;
    }
}
