package com.example.simplekhata.utils;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public final class MoneyUtils {
    public static final long MAX_PAISE = 100_000_000_000L; // ₹1,00,00,00,000 (100 Crore)

    private MoneyUtils() {
    }

    /**
     * Parses a user-entered rupee string (e.g., "1000", "250.5", "1,200.75") into integer paise.
     * Returns -1L if the input is invalid, non-positive, has more than 2 decimal places, or exceeds max.
     */
    public static long parseToPaise(String rawInput) {
        if (rawInput == null) {
            return -1L;
        }
        String cleaned = rawInput.replace(",", "").trim();
        if (cleaned.isEmpty() || cleaned.startsWith("-") || cleaned.startsWith("+")) {
            return -1L;
        }
        try {
            BigDecimal rupees = new BigDecimal(cleaned);
            if (rupees.scale() > 2) {
                return -1L;
            }
            BigDecimal paiseBd = rupees.movePointRight(2);
            long paise = paiseBd.longValueExact();
            if (paise <= 0L || paise > MAX_PAISE) {
                return -1L;
            }
            return paise;
        } catch (NumberFormatException | ArithmeticException e) {
            return -1L;
        }
    }

    public static String formatRupees(long paise) {
        String prefix = paise < 0 ? "-₹" : "₹";
        return prefix + formatIndianNumber(Math.abs(paise));
    }

    public static String formatAbsoluteRupees(long paise) {
        return "₹" + formatIndianNumber(Math.abs(paise));
    }

    public static String formatEditableAmount(long paise) {
        if (paise <= 0L) {
            return "";
        }
        long whole = paise / 100L;
        long fraction = paise % 100L;
        if (fraction == 0L) {
            return String.valueOf(whole);
        }
        return String.format(Locale.US, "%d.%02d", whole, fraction);
    }

    private static String formatIndianNumber(long absPaise) {
        long wholeRupees = absPaise / 100L;
        long fractionPaise = absPaise % 100L;
        String wholeFormatted = formatIndianInteger(wholeRupees);
        if (fractionPaise == 0L) {
            return wholeFormatted;
        }
        return wholeFormatted + String.format(Locale.US, ".%02d", fractionPaise);
    }

    private static String formatIndianInteger(long value) {
        if (value < 1000L) {
            return String.valueOf(value);
        }
        String s = String.valueOf(value);
        int len = s.length();
        String lastThree = s.substring(len - 3);
        String remaining = s.substring(0, len - 3);
        StringBuilder sb = new StringBuilder();
        int remLen = remaining.length();
        for (int i = 0; i < remLen; i++) {
            sb.append(remaining.charAt(i));
            int posFromRight = remLen - 1 - i;
            if (posFromRight > 0 && posFromRight % 2 == 0) {
                sb.append(',');
            }
        }
        sb.append(',').append(lastThree);
        return sb.toString();
    }

    public static String formatPlainRupees(long paise) {
        BigDecimal bd = BigDecimal.valueOf(Math.abs(paise), 2);
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
        DecimalFormat df = new DecimalFormat("0.##", symbols);
        return (paise < 0 ? "-₹" : "₹") + df.format(bd);
    }
}
