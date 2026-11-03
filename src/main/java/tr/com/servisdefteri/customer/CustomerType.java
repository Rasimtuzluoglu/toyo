package tr.com.servisdefteri.customer;

import lombok.Getter;

@Getter
public enum CustomerType {

    BIREYSEL("Bireysel"),
    FIRMA("Firma");

    private final String label;

    CustomerType(String label) {
        this.label = label;
    }
}
