package tr.com.servisdefteri.common;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Ayni formun tekrar gonderilmesini engelleyen tek kullanimlik oturum belirteci.
 */
@Component
public class FormTokenService {

    private static final String SESSION_KEY = "servisdefteri.formTokens";
    private static final int MAX_TOKENS = 40;
    private final SecureRandom random = new SecureRandom();

    @SuppressWarnings("unchecked")
    public String issue(HttpSession session) {
        Set<String> tokens = (Set<String>) session.getAttribute(SESSION_KEY);
        if (tokens == null) {
            tokens = new LinkedHashSet<>();
            session.setAttribute(SESSION_KEY, tokens);
        }
        if (tokens.size() >= MAX_TOKENS) {
            tokens.clear();
        }
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        tokens.add(token);
        return token;
    }

    @SuppressWarnings("unchecked")
    public void consume(HttpSession session, String token) {
        Set<String> tokens = (Set<String>) session.getAttribute(SESSION_KEY);
        if (tokens == null || token == null || !tokens.remove(token)) {
            throw new BusinessRuleException("Form daha önce gönderilmiş veya oturum süresi dolmuş. "
                    + "Lütfen sayfayı yenileyip tekrar deneyin.");
        }
    }
}
