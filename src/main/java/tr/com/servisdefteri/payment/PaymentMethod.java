package tr.com.servisdefteri.payment;

import lombok.Getter;

@Getter
public enum PaymentMethod {

    NAKIT("Nakit"),
    BANKA("Banka"),
    KART("Kredi/Banka Kartı");

    private final String label;

    PaymentMethod(String label) {
        this.label = label;
    }
}
