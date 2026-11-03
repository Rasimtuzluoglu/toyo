package tr.com.servisdefteri.security;

import org.junit.jupiter.api.Test;
import tr.com.servisdefteri.common.BusinessRuleException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordPolicyTest {

    @Test
    void strongPasswordRequiresLengthAndCharacterClasses() {
        assertThat(PasswordPolicy.isStrong("AdminPass1234")).isTrue();
        assertThat(PasswordPolicy.isStrong("short1A")).isFalse();
        assertThat(PasswordPolicy.isStrong("alllowercase123")).isFalse();
        assertThat(PasswordPolicy.isStrong("ALLUPPERCASE123")).isFalse();
        assertThat(PasswordPolicy.isStrong("NoDigitsHereAA")).isFalse();
        assertThat(PasswordPolicy.isStrong(null)).isFalse();
    }

    @Test
    void requireStrongThrowsBusinessRuleException() {
        assertThatThrownBy(() -> PasswordPolicy.requireStrong("weak"))
                .isInstanceOf(BusinessRuleException.class);
    }
}
