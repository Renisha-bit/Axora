package com.axora.util;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

/** Currency formatting utilities for INR. */
public final class CurrencyUtil {

    private CurrencyUtil() {}

    /** Formats cents as "₹1,23,456.78" */
    public static String format(long cents) {
        BigDecimal amount = BigDecimal.valueOf(cents, 2);
        NumberFormat formatter = NumberFormat.getNumberInstance(Locale.of("en", "IN"));
        formatter.setMinimumFractionDigits(2);
        formatter.setMaximumFractionDigits(2);
        return "₹" + formatter.format(amount);
    }

    /** Formats cents with sign prefix for net cash flow. */
    public static String formatSigned(long cents) {
        return (cents >= 0 ? "+" : "") + format(cents);
    }

    /** Parses "1234.56" or "1,234.56" to cents. Returns -1 on parse error. */
    public static long parseCents(String text) {
        if (text == null || text.isBlank()) return -1;
        try {
            String cleaned = text.replace(",", "").trim();
            return new BigDecimal(cleaned).movePointRight(2).longValueExact();
        } catch (Exception e) {
            return -1;
        }
    }
}
