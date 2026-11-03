package tr.com.servisdefteri.security;

import lombok.Getter;

@Getter
public enum Role {

    PATRON("İşletme Sahibi"),
    MUHASEBE("Muhasebe"),
    SERVIS("Servis");

    private final String label;

    Role(String label) {
        this.label = label;
    }
}
