package tr.com.servisdefteri.security;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Kurulumun bir kez tamamlandığını kalıcı olarak tutar.
 * Kullanıcı tablosu sonradan boşalsa bile /setup yeniden açılmaz.
 */
@Entity
@Table(name = "installation_state")
@Getter
@Setter
@NoArgsConstructor
public class InstallationState {

    public static final short SINGLETON_ID = 1;

    @Id
    private Short id = SINGLETON_ID;

    @Column(name = "setup_completed", nullable = false)
    private boolean setupCompleted;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;
}
