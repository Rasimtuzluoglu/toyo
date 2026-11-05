package tr.com.servisdefteri.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import tr.com.servisdefteri.audit.AuditAction;
import tr.com.servisdefteri.audit.AuditService;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDateTime;

@Component
public class LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final UserRepository userRepository;
    private final AuditService auditService;
    private final LoginAttemptService loginAttemptService;
    private final Clock clock;

    public LoginSuccessHandler(UserRepository userRepository, AuditService auditService,
                               LoginAttemptService loginAttemptService, Clock clock) {
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.loginAttemptService = loginAttemptService;
        this.clock = clock;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        AppUserPrincipal principal = (AppUserPrincipal) authentication.getPrincipal();
        loginAttemptService.success(principal.getUsername());
        userRepository.updateLastLogin(principal.getId(), LocalDateTime.now(clock));
        auditService.recordAs(principal.getId(), principal.getUsername(), AuditAction.LOGIN_SUCCESS,
                "Oturum", null, "Başarılı giriş");
        String target = principal.getRole() == Role.SERVIS ? "/work-orders/board" : "/";
        response.sendRedirect(request.getContextPath() + target);
    }
}
