package tr.com.servisdefteri.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NormalizerTest {

    @Test
    void phoneNormalizationHandlesTurkishPrefixes() {
        assertThat(PhoneNormalizer.normalize("0532 123 45 67")).isEqualTo("5321234567");
        assertThat(PhoneNormalizer.normalize("+90 (532) 123-45-67")).isEqualTo("5321234567");
        assertThat(PhoneNormalizer.normalize("905321234567")).isEqualTo("5321234567");
        assertThat(PhoneNormalizer.normalize("5321234567")).isEqualTo("5321234567");
        assertThat(PhoneNormalizer.isValid("0555 000 00 00")).isTrue();
        assertThat(PhoneNormalizer.isValid("123")).isFalse();
        assertThat(PhoneNormalizer.normalizeForSearch("0555")).isEqualTo("555");
        assertThat(PhoneNormalizer.normalizeForSearch("0547 789")).isEqualTo("547789");
    }

    @Test
    void plateNormalizationIsUppercaseAndStripsSeparators() {
        assertThat(PlateNormalizer.normalize(" 34 abc 123 ")).isEqualTo("34ABC123");
        assertThat(PlateNormalizer.normalize("34-AB-123")).isEqualTo("34AB123");
        assertThat(PlateNormalizer.isValid("34 abc 123")).isTrue();
        assertThat(PlateNormalizer.isValid("ABC123")).isFalse();
        assertThat(PlateNormalizer.normalize("06 ŞĞÜÖÇ 123")).isEqualTo("06SGUOC123");
    }
}
