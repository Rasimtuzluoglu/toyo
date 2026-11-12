package tr.com.servisdefteri.security;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class LoginAttemptServiceTest {

    private final MutableClock clock = new MutableClock();
    private final LoginAttemptService service = new LoginAttemptService(clock);

    @Test
    void blocksAfterFiveFailures() {
        for (int i = 0; i < LoginAttemptService.MAX_FAILURES - 1; i++) {
            service.failure("demo");
        }
        assertThat(service.isBlocked("demo")).isFalse();

        service.failure("demo");

        assertThat(service.isBlocked("demo")).isTrue();
        assertThat(service.isBlocked("baska-kullanici")).isFalse();
    }

    @Test
    void lockExpiresAfterFifteenMinutes() {
        for (int i = 0; i < LoginAttemptService.MAX_FAILURES; i++) {
            service.failure("demo");
        }

        clock.advance(LoginAttemptService.LOCK_DURATION.minusSeconds(1));
        assertThat(service.isBlocked("demo")).isTrue();

        clock.advance(Duration.ofSeconds(1));
        assertThat(service.isBlocked("demo")).isFalse();
    }

    @Test
    void successResetsFailureCount() {
        for (int i = 0; i < LoginAttemptService.MAX_FAILURES - 1; i++) {
            service.failure("demo");
        }
        service.success("demo");
        for (int i = 0; i < LoginAttemptService.MAX_FAILURES - 1; i++) {
            service.failure("demo");
        }

        assertThat(service.isBlocked("demo")).isFalse();
    }

    @Test
    void blocksIpWhenDifferentUsernamesAreTried() {
        for (int i = 0; i < LoginAttemptService.MAX_FAILURES; i++) {
            service.failure("kullanici-" + i, "10.0.0.1");
        }

        assertThat(service.isBlocked("baska-kullanici", "10.0.0.1")).isTrue();
        assertThat(service.isBlocked("baska-kullanici", "10.0.0.2")).isFalse();
    }

    @Test
    void successResetsIpLock() {
        for (int i = 0; i < LoginAttemptService.MAX_FAILURES; i++) {
            service.failure("demo", "10.0.0.1");
        }
        service.success("demo", "10.0.0.1");

        assertThat(service.isBlocked("demo", "10.0.0.1")).isFalse();
    }

    @Test
    void blockedAttemptsDoNotExtendTheLock() {
        for (int i = 0; i < LoginAttemptService.MAX_FAILURES; i++) {
            service.failure("demo", "10.0.0.1");
        }
        clock.advance(Duration.ofMinutes(10));

        // Kilitliyken gelen denemeler kilidi uzatmamalı.
        service.failure("demo", "10.0.0.1");

        clock.advance(Duration.ofMinutes(5).plusSeconds(2));
        assertThat(service.isBlocked("demo", "10.0.0.1")).isFalse();
    }

    @Test
    void expiredLockStartsCountingFromZero() {
        for (int i = 0; i < LoginAttemptService.MAX_FAILURES; i++) {
            service.failure("demo", "10.0.0.1");
        }
        clock.advance(LoginAttemptService.LOCK_DURATION.plusSeconds(1));

        service.failure("demo", "10.0.0.1");

        assertThat(service.isBlocked("demo", "10.0.0.1")).isFalse();
    }

    @Test
    void fillingTheStoreDoesNotResetActiveLocks() {
        for (int i = 0; i < LoginAttemptService.MAX_FAILURES; i++) {
            service.failure("kurban", "10.0.0.1");
        }
        for (int i = 0; i < LoginAttemptService.MAX_ENTRIES + 50; i++) {
            service.failure("dolgu-" + i, "10.0.1." + (i % 250));
        }

        assertThat(service.isBlocked("kurban", "10.0.0.1")).isTrue();
    }

    @Test
    void blankUsernameIsIgnored() {
        service.failure(null);
        service.failure("  ");

        assertThat(service.isBlocked(null)).isFalse();
        assertThat(service.isBlocked("  ")).isFalse();
    }

    private static final class MutableClock extends Clock {

        private Instant instant = Instant.parse("2026-11-05T10:00:00Z");

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
