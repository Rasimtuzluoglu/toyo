package tr.com.servisdefteri.common;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Excel'in Turkce karakterleri dogru gormesi icin UTF-8 BOM ekleyen CSV yardimcisi.
 */
public final class CsvUtil {

    private static final byte[] UTF8_BOM = new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    private CsvUtil() {
    }

    public static String money(BigDecimal value) {
        if (value == null) {
            return "0,00";
        }
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(Locale.forLanguageTag("tr-TR"));
        DecimalFormat format = new DecimalFormat("0.00", symbols);
        format.setRoundingMode(RoundingMode.HALF_UP);
        return format.format(value);
    }

    public static String escape(Object value) {
        if (value == null) {
            return "";
        }
        String text = value.toString();
        if (text.contains("\"") || text.contains(";") || text.contains("\n") || text.contains("\r")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }

    public static byte[] toUtf8Bom(String csv) {
        byte[] content = csv.getBytes(StandardCharsets.UTF_8);
        byte[] result = new byte[UTF8_BOM.length + content.length];
        System.arraycopy(UTF8_BOM, 0, result, 0, UTF8_BOM.length);
        System.arraycopy(content, 0, result, UTF8_BOM.length, content.length);
        return result;
    }

    public static String row(Object... values) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                sb.append(';');
            }
            sb.append(escape(values[i]));
        }
        return sb.append('\n').toString();
    }
}
