package com.axora.model;

import java.time.LocalDate;

/**
 * A spending limit. categoryId == null means an overall budget (all expense categories).
 * WEEKLY/MONTHLY budgets repeat over calendar periods; CUSTOM uses startDate..endDate.
 */
public record Budget(long id, String name, Long categoryId, String categoryName,
                     long amountCents, Period period, LocalDate startDate, LocalDate endDate) {

    public enum Period {
        WEEKLY("Weekly"), MONTHLY("Monthly"), CUSTOM("Custom");
        private final String label;
        Period(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }

    public boolean isOverall() { return categoryId == null; }
}
