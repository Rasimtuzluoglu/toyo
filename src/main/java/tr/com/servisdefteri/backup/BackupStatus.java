package tr.com.servisdefteri.backup;

import lombok.Getter;

@Getter
public enum BackupStatus {

    BASARILI("Başarılı"),
    BASARISIZ("Başarısız"),
    DEVAM_EDIYOR("Devam ediyor");

    private final String label;

    BackupStatus(String label) {
        this.label = label;
    }
}
