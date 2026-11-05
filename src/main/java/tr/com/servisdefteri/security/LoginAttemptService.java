package tr.com.servisdefteri.security;

import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Yerel agda da basit kaba kuvvet denemelerini sinirlar.
 * 5 hatali denemeden sonra kullanici adi 15 dakika kilitlenir.
 */
@Service
public class LoginAttemptService {

    static final int MAX_FAILURES = 5;
    static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();
    private final Clock clock;

    public LoginAttemptService(Clock clock) {
        this.clock = clock;
    }

    public boolean isBlocked(String username) {
        String key = key(username);
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

    public void failure(String username) {
        String key = key(username);
        if (key == null) {
            return;
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

    public void success(String username) {
        String key = key(username);
        if (key != null) {
            attempts.remove(key);
        }
    }

    private String key(String username) {
        if (username == null || username.isBlank()) {
            return null;
        }
        return username.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static final class Attempt {
        private int failures;
        private Instant lockedUntil;
    }
}
