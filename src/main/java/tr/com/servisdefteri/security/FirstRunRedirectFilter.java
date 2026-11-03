package tr.com.servisdefteri.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Hic kullanici yokken kok sayfa ve giris ekrani yerine ilk kurulum ekranina yonlendirir.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class FirstRunRedirectFilter extends OncePerRequestFilter {

    private final UserService userService;

    public FirstRunRedirectFilter(UserService userService) {
        this.userService = userService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        boolean firstRunPath = path.isEmpty() || "/".equals(path) || "/login".equals(path);
        if (firstRunPath && "GET".equalsIgnoreCase(request.getMethod()) && !userService.isInitialized()) {
            response.sendRedirect(request.getContextPath() + "/setup");
            return;
        }
        filterChain.doFilter(request, response);
    }
}
