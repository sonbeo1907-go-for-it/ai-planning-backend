package com.codegym.aiplanning.common.utils;

public class StringMaskUtils {

    private StringMaskUtils() {
        // utility class
    }

    public static String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "***";
        }
        int atIndex = email.indexOf("@");
        if (atIndex <= 3) {
            return "***" + email.substring(atIndex);
        }
        return email.substring(0, 3) + "***" + email.substring(atIndex);
    }

    public static String maskSecret(String secret) {
        if (secret == null || secret.isBlank()) {
            return null;
        }
        int visibleCharacters = Math.min(4, secret.length());
        return "****" + secret.substring(secret.length() - visibleCharacters);
    }
}
