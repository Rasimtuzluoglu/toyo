package tr.com.servisdefteri.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SetupControllerTokenTest {

    @Test
    void tokenIsRequiredWhenConfigured() {
        SetupController controller = new SetupController(null, "gizli-token");

        assertThat(controller.tokenMatches(null)).isFalse();
        assertThat(controller.tokenMatches("yanlis")).isFalse();
        assertThat(controller.tokenMatches("gizli-token")).isTrue();
    }

    @Test
    void emptyConfigurationKeepsBackwardCompatibleBehaviour() {
        SetupController controller = new SetupController(null, "");

        assertThat(controller.tokenMatches(null)).isTrue();
        assertThat(controller.tokenMatches("")).isTrue();
    }
}
