package tr.com.servisdefteri.security;

import tr.com.servisdefteri.common.BusinessRuleException;

public final class PasswordPolicy {

    public static final int MIN_LENGTH = 10;

    private PasswordPolicy() {
    }

    public static boolean isStrong(String password) {
        if (password == null || password.length() < MIN_LENGTH) {
            return false;
        }
        boolean upper = false;
        boolean lower = false;
        boolean digit = false;
        for (char c : password.toCharArray()) {
            if (Character.isUpperCase(c)) {
                upper = true;
            } else if (Character.isLowerCase(c)) {
                lower = true;
            } else if (Character.isDigit(c)) {
                digit = true;
            }
        }
        return upper && lower && digit;
    }

    public static void requireStrong(String password) {
        if (!isStrong(password)) {
            throw new BusinessRuleException(
                    "Parola en az " + MIN_LENGTH + " karakter olmalı; büyük harf, küçük harf ve rakam içermelidir.");
        }
    }
}
