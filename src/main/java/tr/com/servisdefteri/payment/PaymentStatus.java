package tr.com.servisdefteri.payment;

import lombok.Getter;

@Getter
public enum PaymentStatus {

    AKTIF("Aktif"),
    IPTAL("İptal");

    private final String label;

    PaymentStatus(String label) {
        this.label = label;
    }
}
