package tr.com.servisdefteri.common;

import java.util.Locale;

public final class PhoneNormalizer {

    private PhoneNormalizer() {
    }

    /**
     * Telefon numarasini yalnizca rakamlardan olusan ortak bir forma getirir.
     * 90 ve 0 ile baslayan Turk numaralari 10 haneye indirgenir.
     */
    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        String digits = raw.replaceAll("\\D", "");
        if (digits.length() == 12 && digits.startsWith("90")) {
            digits = digits.substring(2);
        } else if (digits.length() == 11 && digits.startsWith("0")) {
            digits = digits.substring(1);
        }
        return digits;
    }

    /**
     * Kismi telefon aramalarinda da basindaki 0'i kaldirir.
     * Ornek: "0555" -> "555", "0547 789" -> "547789".
     */
    public static String normalizeForSearch(String raw) {
        String normalized = normalize(raw);
        while (normalized.startsWith("0")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }

    public static boolean isValid(String raw) {
        String normalized = normalize(raw);
        return normalized.length() >= 10 && normalized.length() <= 15;
    }

    public static String format(String normalized) {
        if (normalized == null || normalized.length() != 10) {
            return normalized == null ? "" : normalized;
        }
        return normalized.substring(0, 3) + " " + normalized.substring(3, 6) + " "
                + normalized.substring(6, 8) + " " + normalized.substring(8);
    }

    public static String upper(String value) {
        return value == null ? null : value.toUpperCase(Locale.forLanguageTag("tr-TR"));
    }
}
