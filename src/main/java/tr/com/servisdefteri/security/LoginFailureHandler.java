package tr.com.servisdefteri.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import tr.com.servisdefteri.audit.AuditAction;
import tr.com.servisdefteri.audit.AuditService;

import java.io.IOException;

@Component
public class LoginFailureHandler implements AuthenticationFailureHandler {

    private final AuditService auditService;

    public LoginFailureHandler(AuditService auditService) {
        this.auditService = auditService;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException, ServletException {
        String username = request.getParameter("username");
        if (username != null && username.length() > 50) {
            username = username.substring(0, 50);
        }
        auditService.recordAs(null, username == null ? "bilinmiyor" : username, AuditAction.LOGIN_FAILURE,
                "Oturum", null, "Başarısız giriş denemesi");
        response.sendRedirect(request.getContextPath() + "/login?error");
    }
}
