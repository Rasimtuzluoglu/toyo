package tr.com.servisdefteri.security;

import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.stereotype.Service;

/**
 * Kullanıcı pasife alındığında, rolü değiştiğinde veya parolası sıfırlandığında
 * açık oturumların bir sonraki istekte sonlandırılmasını sağlar. Oturumdaki
 * yetkiler giriş anında kopyalandığı için bu adım olmadan eski yetkilerle
 * çalışmaya devam edilebiliyordu.
 */
@Service
public class SessionRevocationService {

    private final SessionRegistry sessionRegistry;

    public SessionRevocationService(SessionRegistry sessionRegistry) {
        this.sessionRegistry = sessionRegistry;
    }

    public void revokeAllSessions(String username) {
        if (username == null || username.isBlank()) {
            return;
        }
        for (Object principal : sessionRegistry.getAllPrincipals()) {
            if (principal instanceof AppUserPrincipal appUser
                    && appUser.getUsername().equalsIgnoreCase(username)) {
                for (SessionInformation information : sessionRegistry.getAllSessions(principal, false)) {
                    information.expireNow();
                }
            }
        }
    }
}
