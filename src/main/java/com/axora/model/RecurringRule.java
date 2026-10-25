package com.axora.model;

import java.time.LocalDate;

/**
 * A schedule that produces income/expense transactions. generateFrom is the earliest
 * occurrence date that may still be generated (moved forward on resume/schedule edits so
 * paused or superseded periods are never back-filled).
 */
public record RecurringRule(long id, Transaction.Type type, long amountCents,
                            long categoryId, String categoryName,
                            long accountId, String accountName,
                            String description, String merchant, Frequency frequency,
                            LocalDate startDate, LocalDate endDate, LocalDate generateFrom,
                            boolean paused) {

    public enum Frequency {
        DAILY("Daily"), WEEKLY("Weekly"), MONTHLY("Monthly"), YEARLY("Yearly");
        private final String label;
        Frequency(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }
}
