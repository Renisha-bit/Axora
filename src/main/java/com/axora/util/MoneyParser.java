package com.axora.util;

import java.math.BigDecimal;

/** Strict parsing of user-entered money into integer cents (₹ × 100). */
public final class MoneyParser {
    private MoneyParser() {}

    /** Parses a strictly positive amount. Throws IllegalArgumentException with a user-facing message. */
    public static long parsePositiveCents(String text, String field) {
        long c = parseSignedCents(text, field);
        if (c <= 0) throw new IllegalArgumentException(field + " must be greater than zero.");
        return c;
    }

    /** Parses a possibly negative / zero amount. */
    public static long parseSignedCents(String text, String field) {
        if (text == null || text.isBlank()) throw new IllegalArgumentException(field + " is required.");
        BigDecimal v;
        try {
            v = new BigDecimal(text.replace(",", "").trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(field + " must be a valid number (e.g. 1500.00).");
        }
        if (v.precision() - v.scale() > 10) throw new IllegalArgumentException(field + " is too large.");
        try {
            return v.movePointRight(2).longValueExact();
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException(field + " cannot include fractions of a paisa.");
        }
    }
}
