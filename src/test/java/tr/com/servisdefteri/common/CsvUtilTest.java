package tr.com.servisdefteri.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CsvUtilTest {

    @Test
    void neutralizesFormulaCells() {
        assertThat(CsvUtil.row("=SUM(A1:A2)")).contains("'=SUM(A1:A2)");
        assertThat(CsvUtil.row("+90 532 000 00 00")).contains("'+90 532 000 00 00");
        assertThat(CsvUtil.row("@SUM(A1:A2)")).contains("'@SUM(A1:A2)");
        assertThat(CsvUtil.row("-cmd|' /C calc'!A0")).contains("'-cmd|' /C calc'!A0");
    }

    @Test
    void keepsNumericNegativeValuesAsNumbers() {
        assertThat(CsvUtil.row("-1234,56")).contains("-1234,56");
        assertThat(CsvUtil.row("-1234.56")).contains("-1234.56");
    }

    @Test
    void quotesSeparatorsAndQuotes() {
        assertThat(CsvUtil.row("Örnek; müşteri")).contains("\"Örnek; müşteri\"");
        assertThat(CsvUtil.row("Örnek \"müşteri\"")).contains("\"Örnek \"\"müşteri\"\"\"");
    }
}
