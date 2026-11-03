package tr.com.servisdefteri.common;

import java.util.Locale;

/**
 * Arama metinlerini Turkce karakter farklarindan bagimsiz hale getirir.
 * Veritabani C locale kullansa bile "İSTANBUL", "istanbul" ve "Istanbul" ayni sonucu verir.
 */
public final class SearchNormalizer {

    /**
     * PostgreSQL translate() icin kaynak ve hedef karakter kumeleri.
     * Ayni sirada olmalari zorunludur.
     */
    public static final String TURKISH_SOURCE = "İIıŞşĞğÜüÖöÇç";
    public static final String TURKISH_TARGET = "iiissgguuoocc";

    private SearchNormalizer() {
    }

    public static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String text = value.trim();
        StringBuilder result = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char character = text.charAt(i);
            result.append(switch (character) {
                case 'İ', 'I', 'ı' -> 'i';
                case 'Ş', 'ş' -> 's';
                case 'Ğ', 'ğ' -> 'g';
                case 'Ü', 'ü' -> 'u';
                case 'Ö', 'ö' -> 'o';
                case 'Ç', 'ç' -> 'c';
                default -> Character.toLowerCase(character);
            });
        }
        return result.toString().toLowerCase(Locale.ROOT);
    }

    /**
     * LIKE sorgusu icin %normalize% dondurur; bos aramada null.
     */
    public static String contains(String value) {
        String normalized = normalize(value);
        return normalized.isEmpty() ? null : "%" + normalized + "%";
    }
}
