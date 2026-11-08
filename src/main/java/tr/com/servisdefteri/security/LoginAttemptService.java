package tr.com.servisdefteri.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Yerel agda da basit kaba kuvvet denemelerini sinirlar.
 * Kullanici adi ve istemci IP'si ayri ayri 5 hatali denemeden sonra
 * 15 dakika kilitlenir; boylece hem tek kullaniciya hem de tek IP'den
 * farkli kullanicilara yapilan denemeler sinirlanir.
 */
@Service
public class LoginAttemptService {

    static final int MAX_FAILURES = 5;
    static final Duration LOCK_DURATION = Duration.ofMinutes(15);
    private static final int MAX_ENTRIES = 10_000;

    private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();
    private final Clock clock;

    public LoginAttemptService(Clock clock) {
        this.clock = clock;
    }

    public boolean isBlocked(String username) {
        return isBlocked(username, currentIp());
    }

    public boolean isBlocked(String username, String ip) {
        return isBlockedKey(userKey(username)) || isBlockedKey(ipKey(ip));
    }

    public void failure(String username) {
        failure(username, currentIp());
    }

    public void failure(String username, String ip) {
        increment(userKey(username));
        increment(ipKey(ip));
    }

    public void success(String username) {
        success(username, currentIp());
    }

    public void success(String username, String ip) {
        remove(userKey(username));
        remove(ipKey(ip));
    }

    private boolean isBlockedKey(String key) {
        if (key == null) {
            return false;
        }
        Attempt attempt = attempts.get(key);
        if (attempt == null) {
            return false;
        }
        synchronized (attempt) {
            if (attempt.lockedUntil != null && Instant.now(clock).isBefore(attempt.lockedUntil)) {
                return true;
            }
            if (attempt.lockedUntil != null) {
                attempts.remove(key, attempt);
            }
            return false;
        }
    }

    private void increment(String key) {
        if (key == null) {
            return;
        }
        if (attempts.size() >= MAX_ENTRIES) {
            attempts.clear();
        }
        attempts.compute(key, (ignored, current) -> {
            Attempt attempt = current == null ? new Attempt() : current;
            synchronized (attempt) {
                attempt.failures++;
                if (attempt.failures >= MAX_FAILURES) {
                    attempt.lockedUntil = Instant.now(clock).plus(LOCK_DURATION);
                }
            }
            return attempt;
        });
    }

    private void remove(String key) {
        if (key != null) {
            attempts.remove(key);
        }
    }

    private String userKey(String username) {
        if (username == null || username.isBlank()) {
            return null;
        }
        return "user:" + username.trim().toLowerCase(Locale.ROOT);
    }

    private String ipKey(String ip) {
        if (ip == null || ip.isBlank()) {
            return null;
        }
        return "ip:" + ip.trim();
    }

    private String currentIp() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return null;
        }
        HttpServletRequest request = attributes.getRequest();
        return request == null ? null : request.getRemoteAddr();
    }

    private static final class Attempt {
        private int failures;
        private Instant lockedUntil;
    }
}
