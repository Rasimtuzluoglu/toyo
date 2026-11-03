package tr.com.servisdefteri.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tr.com.servisdefteri.common.NotFoundException;

@Service
public class CurrentUserService {

    private final UserRepository userRepository;

    public CurrentUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public AppUserPrincipal principal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AppUserPrincipal principal)) {
            throw new IllegalStateException("Oturum bulunamadı");
        }
        return principal;
    }

    public Long id() {
        return principal().getId();
    }

    /**
     * Oturum yoksa null doner. Zamanlanmis gorevler ve sistem islemleri icin kullanilir.
     */
    public Long idOrNull() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AppUserPrincipal principal) {
            return principal.getId();
        }
        return null;
    }

    public String username() {
        return principal().getUsername();
    }

    @Transactional(readOnly = true)
    public User user() {
        return userRepository.findById(id())
                .orElseThrow(() -> NotFoundException.of("Kullanıcı", id()));
    }

    public boolean hasRole(Role role) {
        return principal().getRole() == role;
    }

    public boolean canManageFinances() {
        Role role = principal().getRole();
        return role == Role.PATRON || role == Role.MUHASEBE;
    }

    public boolean canOperateWorkOrders() {
        Role role = principal().getRole();
        return role == Role.PATRON || role == Role.SERVIS;
    }

    public boolean isPatron() {
        return hasRole(Role.PATRON);
    }
}
