package tr.com.servisdefteri.settings;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "business_settings")
@Getter
@Setter
@NoArgsConstructor
public class BusinessSettings {

    @Id
    private Long id = 1L;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 30)
    private String phone;

    @Column(length = 150)
    private String email;

    @Column(length = 500)
    private String address;

    @Column(name = "tax_office", length = 100)
    private String taxOffice;

    @Column(name = "tax_number", length = 20)
    private String taxNumber;

    @Column(name = "backup_directory", length = 500)
    private String backupDirectory;

    @Column(nullable = false, length = 120)
    private String tagline = "Yetkili Servis ve Bakım";

    @Column(name = "primary_color", nullable = false, length = 7)
    private String primaryColor = "#eb0a1e";

    @Column(name = "logo_data", columnDefinition = "BYTEA")
    private byte[] logoData;

    @Column(name = "logo_content_type", length = 100)
    private String logoContentType;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Column(name = "updated_by")
    private Long updatedBy;

    public String getLogoUrl() {
        return logoData != null && logoData.length > 0 ? "/branding/logo" : "/images/toyota.png";
    }

    public String getPrimaryColorDark() {
        try {
            java.awt.Color color = java.awt.Color.decode(primaryColor);
            java.awt.Color darker = color.darker();
            return String.format("#%02x%02x%02x", darker.getRed(), darker.getGreen(), darker.getBlue());
        } catch (RuntimeException ex) {
            return "#c00718";
        }
    }
}
