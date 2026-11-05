package tr.com.servisdefteri.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import tr.com.servisdefteri.audit.AuditAction;
import tr.com.servisdefteri.audit.AuditService;

import java.io.IOException;

@Component
public class LoginFailureHandler implements AuthenticationFailureHandler {

    private final AuditService auditService;
    private final LoginAttemptService loginAttemptService;

    public LoginFailureHandler(AuditService auditService, LoginAttemptService loginAttemptService) {
        this.auditService = auditService;
        this.loginAttemptService = loginAttemptService;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException, ServletException {
        String username = request.getParameter("username");
        if (username != null && username.length() > 50) {
            username = username.substring(0, 50);
        }
        loginAttemptService.failure(username);
        auditService.recordAs(null, username == null ? "bilinmiyor" : username, AuditAction.LOGIN_FAILURE,
                "Oturum", null, "Başarısız giriş denemesi");
        String target = exception instanceof LockedException ? "/login?locked" : "/login?error";
        response.sendRedirect(request.getContextPath() + target);
    }
}
