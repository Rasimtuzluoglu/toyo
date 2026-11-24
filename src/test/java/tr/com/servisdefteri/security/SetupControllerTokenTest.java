package tr.com.servisdefteri.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SetupControllerTokenTest {

    @Test
    void tokenIsRequiredWhenConfigured() {
        SetupController controller = new SetupController(null, "gizli-token");

        assertThat(controller.tokenMatches(null, "127.0.0.1")).isFalse();
        assertThat(controller.tokenMatches("yanlis", "127.0.0.1")).isFalse();
        assertThat(controller.tokenMatches("gizli-token", "127.0.0.1")).isTrue();
    }

    @Test
    void emptyConfigurationAllowsOnlyLoopbackSetup() {
        SetupController controller = new SetupController(null, "");

        assertThat(controller.tokenMatches(null, "127.0.0.1")).isTrue();
        assertThat(controller.tokenMatches("", "::1")).isTrue();
        assertThat(controller.tokenMatches(null, "192.168.1.25")).isFalse();
        assertThat(controller.tokenMatches(null, null)).isFalse();
    }
}
