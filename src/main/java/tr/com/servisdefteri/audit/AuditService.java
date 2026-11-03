package tr.com.servisdefteri.audit;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tr.com.servisdefteri.security.AppUserPrincipal;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository repository;
    private final Clock clock;

    public AuditService(AuditLogRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public void record(AuditAction action, String entityType, Long entityId, String details) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AppUserPrincipal principal) {
            save(principal.getId(), principal.getUsername(), action, entityType, entityId, details);
        } else {
            save(null, "anonim", action, entityType, entityId, details);
        }
    }

    /**
     * Giris denemesi gibi henuz kimligi dogrulanmamis olaylar icin kullanilir.
     * Parola gibi hassas veriler bu metoda asla verilmemelidir.
     */
    @Transactional
    public void recordAs(Long actorId, String actorUsername, AuditAction action,
                         String entityType, Long entityId, String details) {
        save(actorId, actorUsername, action, entityType, entityId, details);
    }

    private void save(Long actorId, String actorUsername, AuditAction action,
                      String entityType, Long entityId, String details) {
        try {
            AuditLog entry = new AuditLog();
            entry.setActorId(actorId);
            entry.setActorUsername(limit(actorUsername == null ? "bilinmiyor" : actorUsername, 50));
            entry.setAction(action.name());
            entry.setEntityType(limit(entityType, 50));
            entry.setEntityId(entityId);
            entry.setDetails(limit(details, 4000));
            entry.setIpAddress(clientIp());
            entry.setCreatedAt(LocalDateTime.now(clock));
            repository.save(entry);
        } catch (RuntimeException ex) {
            // Denetim kaydi is akisini durdurmamali; ancak hata loglanir.
            log.error("Denetim kaydi yazilamadi: action={}, entity={}", action, entityType);
        }
    }

    private String clientIp() {
        try {
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes == null) {
                return null;
            }
            HttpServletRequest request = attributes.getRequest();
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return limit(forwarded.split(",")[0].trim(), 45);
            }
            return limit(request.getRemoteAddr(), 45);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private String limit(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
