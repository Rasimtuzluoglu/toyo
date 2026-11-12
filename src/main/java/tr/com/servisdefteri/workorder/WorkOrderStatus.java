package tr.com.servisdefteri.workorder;

import lombok.Getter;

import java.util.List;
import java.util.Locale;

@Getter
public enum WorkOrderStatus {

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

    /**
     * CSS sınıf adları için ASCII küçük harf. Türkçe yerel ayarda "ISLEMDE"
     * ifadesinin "ıslemde" olmasını engeller.
     */
    public String getCssName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public boolean canTransitionTo(WorkOrderStatus target) {
        if (target == null || target == this) {
            return false;
        }
        return switch (this) {
            case ISLEMDE -> target == TAMAMLANDI || target == IPTAL;
            case TAMAMLANDI -> target == TESLIM_EDILDI || target == IPTAL;
            case TESLIM_EDILDI, IPTAL -> false;
        };
    }

    public List<WorkOrderStatus> allowedTransitions() {
        return List.of(values()).stream().filter(this::canTransitionTo).toList();
    }

    public boolean isOpen() {
        return this == ISLEMDE;
    }

    public boolean isCompleted() {
        return this == TAMAMLANDI || this == TESLIM_EDILDI;
    }
}
