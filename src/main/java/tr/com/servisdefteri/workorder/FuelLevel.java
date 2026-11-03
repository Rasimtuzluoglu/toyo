package tr.com.servisdefteri.workorder;

import lombok.Getter;

@Getter
public enum FuelLevel {

    BOS("Boş"),
    CEYREK("1/4"),
    YARIM("1/2"),
    UCDORT("3/4"),
    DOLU("Dolu");

    private final String label;

    FuelLevel(String label) {
        this.label = label;
    }
}
