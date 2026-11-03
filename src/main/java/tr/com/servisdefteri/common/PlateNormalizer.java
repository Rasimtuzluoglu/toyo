package tr.com.servisdefteri.common;

import java.util.Locale;
import java.util.Map;

public final class PlateNormalizer {

    private static final Map<Character, Character> TR_CHARS = Map.ofEntries(
            Map.entry('İ', 'I'), Map.entry('ı', 'I'), Map.entry('Ş', 'S'), Map.entry('ş', 'S'),
            Map.entry('Ğ', 'G'), Map.entry('ğ', 'G'), Map.entry('Ü', 'U'), Map.entry('ü', 'U'),
            Map.entry('Ö', 'O'), Map.entry('ö', 'O'), Map.entry('Ç', 'C'), Map.entry('ç', 'C')
    );

    private PlateNormalizer() {
    }

    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        String upper = raw.trim().toUpperCase(Locale.forLanguageTag("tr-TR"));
        StringBuilder sb = new StringBuilder();
        for (char c : upper.toCharArray()) {
            Character replaced = TR_CHARS.get(c);
            char value = replaced == null ? c : replaced;
            if ((value >= 'A' && value <= 'Z') || (value >= '0' && value <= '9')) {
                sb.append(value);
            }
        }
        return sb.toString();
    }

    public static boolean isValid(String raw) {
        return normalize(raw).matches("^[0-9]{2}[A-Z]{1,4}[0-9]{2,5}$");
    }

    public static String format(String normalized) {
        if (normalized == null || normalized.length() < 5) {
            return normalized == null ? "" : normalized;
        }
        return normalized;
    }
}
