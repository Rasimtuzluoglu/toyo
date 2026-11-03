package tr.com.servisdefteri.appointment;

import lombok.Getter;

@Getter
public enum AppointmentStatus {

    PLANLANDI("Planlandı", "secondary"),
    GELDI("Geldi", "success"),
    IPTAL("İptal", "danger");

    private final String label;
    private final String badgeClass;

    AppointmentStatus(String label, String badgeClass) {
        this.label = label;
        this.badgeClass = badgeClass;
    }
}
