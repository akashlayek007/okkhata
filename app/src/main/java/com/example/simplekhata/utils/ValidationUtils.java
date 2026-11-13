package com.example.simplekhata.utils;

public final class ValidationUtils {
    public static final int MAX_NAME_LENGTH = 100;
    public static final int MAX_PHONE_LENGTH = 20;
    public static final int MAX_NOTE_LENGTH = 300;

    private ValidationUtils() {
    }

    public static boolean isValidCustomerName(String name) {
        if (name == null) {
            return false;
        }
        String trimmed = name.trim();
        return !trimmed.isEmpty() && trimmed.length() <= MAX_NAME_LENGTH;
    }

    /**
     * Phone is optional. If empty/blank, it is valid.
     * If provided, allows optional leading '+' and digits/spaces/hyphens/parentheses,
     * with 7 to 15 total digits and max raw length 20.
     */
    public static boolean isValidOptionalPhone(String phone) {
        if (phone == null) {
            return true;
        }
        String trimmed = phone.trim();
        if (trimmed.isEmpty()) {
            return true;
        }
        if (trimmed.length() > MAX_PHONE_LENGTH) {
            return false;
        }
        if (!trimmed.matches("^[+]?[0-9\\s\\-()]+$")) {
            return false;
        }
        String digitsOnly = trimmed.replaceAll("[^0-9]", "");
        return digitsOnly.length() >= 7 && digitsOnly.length() <= 15;
    }

    public static String normalizeOptionalText(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim();
    }

    public static String normalizePickedPhone(String raw) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        boolean hasLeadingPlus = trimmed.startsWith("+");
        String digits = trimmed.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) {
            return "";
        }
        String combined = hasLeadingPlus ? "+" + digits : digits;
        if (combined.length() > MAX_PHONE_LENGTH) {
            combined = combined.substring(0, MAX_PHONE_LENGTH);
        }
        return combined;
    }
}
