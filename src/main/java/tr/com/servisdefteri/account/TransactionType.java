package tr.com.servisdefteri.account;

import lombok.Getter;

@Getter
public enum TransactionType {

    BORC("Borç"),
    ALACAK("Alacak");

    private final String label;

    TransactionType(String label) {
        this.label = label;
    }
}
