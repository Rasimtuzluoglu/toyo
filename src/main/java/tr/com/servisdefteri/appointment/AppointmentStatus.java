package tr.com.servisdefteri.appointment;

import lombok.Getter;

@Getter
public enum AppointmentStatus {

    PLANLANDI("Planlandı", "neutral"),
    GELDI("Geldi", "aktif"),
    IPTAL("İptal", "iptal");

    private final String label;
    private final String badgeClass;

    AppointmentStatus(String label, String badgeClass) {
        this.label = label;
        this.badgeClass = badgeClass;
    }
}
